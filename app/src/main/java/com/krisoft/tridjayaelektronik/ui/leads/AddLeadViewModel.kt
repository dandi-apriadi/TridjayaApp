package com.krisoft.tridjayaelektronik.ui.leads

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krisoft.tridjayaelektronik.data.AuthRepository
import com.krisoft.tridjayaelektronik.data.AuthResult
import com.krisoft.tridjayaelektronik.data.CrmRepository
import com.krisoft.tridjayaelektronik.data.InventoryRepository
import com.krisoft.tridjayaelektronik.data.model.AssigneeDto
import com.krisoft.tridjayaelektronik.data.model.PipelineDto
import com.krisoft.tridjayaelektronik.domain.leads.CreateLeadOutcome
import com.krisoft.tridjayaelektronik.domain.leads.CreateLeadUseCase
import com.krisoft.tridjayaelektronik.domain.leads.GetAssigneesUseCase
import com.krisoft.tridjayaelektronik.domain.leads.GetPipelinesUseCase
import com.krisoft.tridjayaelektronik.domain.leads.MAX_BUKTI_DIMENSI
import com.krisoft.tridjayaelektronik.domain.leads.MAX_BUKTI_PROSPEK_BYTES
import com.krisoft.tridjayaelektronik.domain.leads.masalahUkuranBukti
import com.krisoft.tridjayaelektronik.domain.leads.peranEfektif
import com.krisoft.tridjayaelektronik.domain.leads.sampleSizeUntuk
import com.krisoft.tridjayaelektronik.domain.leads.wajibBuktiProspek
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import kotlin.math.max

// Fixed option lists mirroring the web's Submit Prospek form (ProspekSubmitForm.tsx).
val SUMBER_LEAD_OPTIONS = listOf(
    "WhatsApp", "Walk In", "Meta Ads", "Instagram", "TikTok",
    "Facebook", "Marketplace", "Website", "Mediator", "Event"
)
val KATEGORI_PRODUK_OPTIONS = listOf("Elektronik", "Sepeda Listrik", "Furniture", "Alat Tani", "Gadget")
val FINCOY_OPTIONS = listOf(
    "Cash", "KREDIVO", "SMF (Samsung Finance)", "AKULAKU", "FIF", "ADIRA", "SHOPEE",
    "INDODANA", "TOKOPEDIA", "HCI", "AEON", "SPEKTRA", "Yes Kredit", "Kredit Plus"
)

data class AddLeadUiState(
    val nama: String = "",
    val phone: String = "",
    val minatBarang: String = "",
    /** Saran nama produk dari cache inventory untuk dropdown Minat Barang (paritas form web). */
    val minatSuggestions: List<String> = emptyList(),
    val kategoriProduk: String = "",
    val keteranganFincoy: String = "",
    val sumber: String = "",
    val lokasi: String = "",
    val catatan: String = "",
    val estimatedValue: String = "",
    val pipelines: List<PipelineDto> = emptyList(),
    val selectedPipelineId: Long? = null,
    val isLoadingPipelines: Boolean = true,
    /** Assignable employees; null selection = "Saya sendiri" (the submitter). */
    val assignees: List<AssigneeDto> = emptyList(),
    val isLoadingAssignees: Boolean = true,
    val selectedAssignee: AssigneeDto? = null,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val createdLeadId: Long? = null,
    /** Path internal hasil `POST /prospek-harian/bukti` — bukan URL yang bisa dipasang di gambar. */
    val buktiUrl: String? = null,
    /** Nama berkas pilihan user, murni untuk ditampilkan sebagai "Terlampir: …". */
    val buktiNama: String = "",
    val mengunggahBukti: Boolean = false,
    val buktiError: String? = null,
    /** Trainee wajib melampirkan bukti; peran lain opsional. Menyetir label, bukan gerbangnya. */
    val wajibBukti: Boolean = false
)

@HiltViewModel
class AddLeadViewModel @Inject constructor(
    private val createLeadUseCase: CreateLeadUseCase,
    private val getPipelinesUseCase: GetPipelinesUseCase,
    private val getAssigneesUseCase: GetAssigneesUseCase,
    private val inventoryRepository: InventoryRepository,
    private val crmRepository: CrmRepository,
    private val authRepository: AuthRepository,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddLeadUiState())
    val uiState: StateFlow<AddLeadUiState> = _uiState.asStateFlow()

    init {
        loadPipelines()
        loadAssignees()
        val profil = authRepository.cachedUser
        _uiState.update {
            it.copy(wajibBukti = wajibBuktiProspek(peranEfektif(profil?.role, profil?.roles.orEmpty())))
        }
    }

    private fun loadAssignees() {
        viewModelScope.launch {
            when (val result = getAssigneesUseCase()) {
                is AuthResult.Success -> _uiState.update {
                    it.copy(isLoadingAssignees = false, assignees = result.data)
                }
                // Non-fatal: the form still works, assignment just falls back to "Saya sendiri".
                is AuthResult.Failure -> _uiState.update { it.copy(isLoadingAssignees = false) }
            }
        }
    }

    private fun loadPipelines() {
        viewModelScope.launch {
            when (val result = getPipelinesUseCase()) {
                is AuthResult.Success -> {
                    val default = result.data.firstOrNull { it.isDefault } ?: result.data.firstOrNull()
                    _uiState.update {
                        it.copy(
                            isLoadingPipelines = false,
                            pipelines = result.data,
                            selectedPipelineId = default?.id
                        )
                    }
                }
                is AuthResult.Failure -> _uiState.update {
                    it.copy(isLoadingPipelines = false, errorMessage = result.message)
                }
            }
        }
    }

    fun onNamaChange(value: String) = _uiState.update { it.copy(nama = value, errorMessage = null) }
    fun onPhoneChange(value: String) = _uiState.update { it.copy(phone = value, errorMessage = null) }
    private var suggestJob: Job? = null

    /** Ketik minat → cari saran produk dari cache inventory (segmen terakhir setelah koma, seperti web). */
    fun onMinatBarangChange(value: String) {
        _uiState.update { it.copy(minatBarang = value, errorMessage = null) }
        suggestJob?.cancel()
        val query = value.split(",").last().trim()
        if (query.length < 2) {
            _uiState.update { it.copy(minatSuggestions = emptyList()) }
            return
        }
        suggestJob = viewModelScope.launch {
            delay(250)
            val products = runCatching { inventoryRepository.searchProducts(query) }.getOrDefault(emptyList())
            _uiState.update { state ->
                state.copy(minatSuggestions = products.map { it.nama.trim() }.filter { it.isNotEmpty() }.distinct().take(6))
            }
        }
    }

    /** Pilih saran → ganti segmen terakhir dengan nama produk terpilih. */
    fun onMinatSuggestionPicked(nama: String) {
        val kept = _uiState.value.minatBarang.split(",").dropLast(1).map { it.trim() }.filter { it.isNotEmpty() }
        _uiState.update { it.copy(minatBarang = (kept + nama).joinToString(", "), minatSuggestions = emptyList()) }
    }
    fun onKategoriProdukSelected(value: String) = _uiState.update { it.copy(kategoriProduk = value, errorMessage = null) }
    fun onFincoySelected(value: String) = _uiState.update { it.copy(keteranganFincoy = value) }
    fun onSumberSelected(value: String) = _uiState.update { it.copy(sumber = value) }
    fun onLokasiChange(value: String) = _uiState.update { it.copy(lokasi = value) }
    fun onCatatanChange(value: String) = _uiState.update { it.copy(catatan = value) }
    /** Keep only digits — the field is a plain rupiah amount. */
    fun onEstimatedValueChange(value: String) = _uiState.update { it.copy(estimatedValue = value.filter { c -> c.isDigit() }) }
    fun onPipelineSelected(id: Long) = _uiState.update { it.copy(selectedPipelineId = id) }
    /** null = kembali ke "Saya sendiri". */
    fun onAssigneeSelected(assignee: AssigneeDto?) = _uiState.update { it.copy(selectedAssignee = assignee) }

    /**
     * Bukti dipilih → langsung diunggah, tidak ditunda sampai Simpan.
     *
     * Alasannya sama dengan galeri bukti raport: grant `Uri` dari Photo Picker
     * TIDAK persistable dan hilang begitu prosesnya dimatikan sistem. Menunda
     * pembacaannya berarti user yang berpindah app sebentar kembali ke form yang
     * mengaku punya bukti, lalu gagal saat Simpan tanpa cara memperbaikinya.
     */
    fun onBuktiPicked(uri: Uri, nama: String, ukuranBytes: Long) {
        masalahUkuranBukti(ukuranBytes)?.let { pesan ->
            _uiState.update { it.copy(buktiError = pesan) }
            return
        }
        _uiState.update { it.copy(mengunggahBukti = true, buktiError = null, errorMessage = null) }
        viewModelScope.launch {
            val bytes = withContext(Dispatchers.Default) { siapkanJpeg(uri) }
            if (bytes == null) {
                _uiState.update {
                    it.copy(
                        mengunggahBukti = false,
                        buktiError = "Gambar tidak terbaca. Coba pilih tangkapan layar lain."
                    )
                }
                return@launch
            }
            when (val hasil = crmRepository.uploadBuktiProspek(bytes, "bukti_prospek.webp")) {
                is AuthResult.Success -> _uiState.update {
                    it.copy(mengunggahBukti = false, buktiUrl = hasil.data, buktiNama = nama.ifBlank { "bukti.jpg" })
                }
                is AuthResult.Failure -> _uiState.update {
                    it.copy(mengunggahBukti = false, buktiError = hasil.message)
                }
            }
        }
    }

    /**
     * Lepas lampiran dari FORM saja — berkas di server sengaja tidak dihapus.
     *
     * Tak ada endpoint hapus, dan bukti yatim beberapa ratus KB jauh lebih murah
     * daripada menahan user di form yang salah pilih gambar.
     */
    fun onBuktiRemoved() = _uiState.update {
        it.copy(buktiUrl = null, buktiNama = "", buktiError = null)
    }

    /**
     * Dekode → betulkan rotasi EXIF → JPEG di bawah [MAX_BUKTI_PROSPEK_BYTES].
     *
     * Kode ulang ke JPEG BUKAN pilihan gaya: server memeriksa ekstensi × MIME ×
     * magic byte serentak, sedangkan yang dikirim klien ini selalu bernama `.jpg`
     * dengan `image/jpeg` — PNG apa adanya (bentuk paling lazim tangkapan layar)
     * ditolak 400 sesudah terkirim. Alasan lengkapnya di `BuktiProspekPlan.kt`.
     */
    private fun siapkanJpeg(uri: Uri): ByteArray? {
        val raw = runCatching {
            appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull() ?: return null
        if (raw.isEmpty()) return null

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(raw, 0, raw.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var bitmap = BitmapFactory.decodeByteArray(
            raw, 0, raw.size,
            BitmapFactory.Options().apply { inSampleSize = sampleSizeUntuk(bounds.outWidth, bounds.outHeight) }
        ) ?: return null

        val sisiTerpanjang = max(bitmap.width, bitmap.height)
        if (sisiTerpanjang > MAX_BUKTI_DIMENSI) {
            val skala = MAX_BUKTI_DIMENSI.toFloat() / sisiTerpanjang
            bitmap = Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * skala).toInt().coerceAtLeast(1),
                (bitmap.height * skala).toInt().coerceAtLeast(1),
                true
            )
        }

        // Kode ulang membuang EXIF, jadi rotasinya dipanggang ke piksel dulu —
        // tanpa ini foto kamera potret tersimpan miring dan mentor membacanya miring.
        val orientasi = runCatching {
            ExifInterface(ByteArrayInputStream(raw))
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val derajat = when (orientasi) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (derajat != 0f) {
            val matrix = Matrix().apply { postRotate(derajat) }
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }

        var kualitas = 90
        var out = ByteArrayOutputStream().apply { bitmap.compress(Bitmap.CompressFormat.JPEG, kualitas, this) }.toByteArray()
        while (out.size > MAX_BUKTI_PROSPEK_BYTES && kualitas > 40) {
            kualitas -= 15
            out = ByteArrayOutputStream().apply { bitmap.compress(Bitmap.CompressFormat.JPEG, kualitas, this) }.toByteArray()
        }
        // Masih kebesaran di kualitas terendah = server pasti menolak; lebih baik
        // gagal di sini, dengan pesan yang benar, daripada sesudah 8 MB terkirim.
        return out.takeIf { it.size <= MAX_BUKTI_PROSPEK_BYTES }
    }

    fun submit() {
        val state = _uiState.value
        _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
        viewModelScope.launch {
            when (
                val outcome = createLeadUseCase(
                    nama = state.nama,
                    phone = state.phone,
                    minatBarang = state.minatBarang,
                    kategoriProduk = state.kategoriProduk,
                    keteranganFincoy = state.keteranganFincoy,
                    pipelineId = state.selectedPipelineId,
                    sumber = state.sumber,
                    lokasi = state.lokasi,
                    catatan = state.catatan,
                    estimatedValue = state.estimatedValue.toDoubleOrNull(),
                    assignedTo = state.selectedAssignee?.id,
                    buktiUrl = state.buktiUrl
                )
            ) {
                is CreateLeadOutcome.Success -> _uiState.update {
                    it.copy(isSubmitting = false, createdLeadId = outcome.leadId)
                }
                is CreateLeadOutcome.ValidationError -> _uiState.update {
                    it.copy(isSubmitting = false, errorMessage = outcome.message)
                }
                is CreateLeadOutcome.Failure -> _uiState.update {
                    it.copy(isSubmitting = false, errorMessage = outcome.message)
                }
            }
        }
    }
}
