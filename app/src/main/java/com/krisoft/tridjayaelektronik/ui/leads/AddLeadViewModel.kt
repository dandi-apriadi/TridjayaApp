package com.krisoft.tridjayaelektronik.ui.leads

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
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
import com.krisoft.tridjayaelektronik.domain.leads.wajibBuktiProspek
import com.krisoft.tridjayaelektronik.util.ImagePixelPipeline
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
import javax.inject.Inject

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

// WebP, BUKAN lagi JPEG (2026-08-29) — memperbaiki bug hidup, bukan cuma ikut konvensi app-wide:
// `CrmRepository.uploadBuktiProspek` mengirim filename `.webp` + `Content-Type: image/webp`
// HARDCODE sejak awal, sedangkan `siapkanJpeg` (nama fungsi TETAP, isinya kini WebP — lihat
// KDoc-nya) mengembalikan bytes JPEG asli. Server (`kinerja-service::prospek::upload_bukti_prospek`
// → `aktivitas_harian::domain::is_valid_raport_evidence_content`) memvalidasi MAGIC BYTE untuk
// ekstensi "webp" (`RIFF....WEBP`) — JPEG (`FF D8 FF`) tak pernah cocok, jadi SETIAP upload bukti
// prospek dari layar ini ditolak 400 hari ini (diverifikasi baca kode server langsung, bukan
// dugaan). WebP asli membuat ketiganya (nama·content-type·isi) sepakat dengan sendirinya.
@Suppress("DEPRECATION")
private val IMAGE_PARAMS = ImagePixelPipeline.Params(
    maxDimension = MAX_BUKTI_DIMENSI,
    format = Bitmap.CompressFormat.WEBP,
    startQuality = 90,
    minQuality = 40,
    step = 15,
    maxBytes = MAX_BUKTI_PROSPEK_BYTES,
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
            val bytes = siapkanJpeg(uri)
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
     * Dekode → betulkan rotasi EXIF → WebP di bawah [MAX_BUKTI_PROSPEK_BYTES]. Nama fungsi TETAP
     * "Jpeg" walau isinya kini WebP (2026-08-29) — lihat komentar [IMAGE_PARAMS] soal kenapa
     * gantinya WAJIB, bukan opsional. `null` = PENOLAKAN, bukan fallback ke byte asli: masih
     * kebesaran di kualitas terendah = server pasti menolak, lebih baik gagal di sini dengan
     * pesan yang benar daripada sesudah 8 MB terkirim (beda sengaja dari `PhotoWatermark`/
     * `IndentCreateViewModel.prepareProofUpload`/`EventViewModel.siapkanKtpJpeg`, yang fail-soft ke
     * byte asli — `ImagePixelPipeline.compress` sendiri SELALU fail-soft, jadi penolakannya
     * dicek DI SINI, sesudah `compress()` kembali, bukan di dalam pipeline bersama).
     *
     * `suspend` + `withContext(Dispatchers.Default)` membungkus SELURUH badan (termasuk baca
     * `ContentResolver`) supaya `ImagePixelPipeline.compress` dipanggil di fungsi yang sama dengan
     * pemindahan dispatcher-nya — dijaga `ImagePixelPipelineGuardTest`. `onBuktiPicked` dulu
     * membungkus PANGGILAN ke fungsi ini dengan `withContext(Dispatchers.Default) { siapkanJpeg(uri) }`;
     * utasnya sama persis, cuma baris `withContext`-nya kini di dalam.
     */
    private suspend fun siapkanJpeg(uri: Uri): ByteArray? = withContext(Dispatchers.Default) {
        val raw = runCatching {
            appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull() ?: return@withContext null
        if (raw.isEmpty()) return@withContext null
        ImagePixelPipeline.compress(raw, IMAGE_PARAMS)?.first?.takeIf { it.size <= MAX_BUKTI_PROSPEK_BYTES }
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
