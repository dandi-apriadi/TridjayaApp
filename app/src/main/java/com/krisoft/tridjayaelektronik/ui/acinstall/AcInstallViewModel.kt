package com.krisoft.tridjayaelektronik.ui.acinstall

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krisoft.tridjayaelektronik.data.AcInstallRepository
import com.krisoft.tridjayaelektronik.data.AuthRepository
import com.krisoft.tridjayaelektronik.data.AuthResult
import com.krisoft.tridjayaelektronik.data.DeliveryFlowRepository
import com.krisoft.tridjayaelektronik.data.model.AcInstallTaskDto
import com.krisoft.tridjayaelektronik.util.PhotoWatermark
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

data class AcInstallUiState(
    val loading: Boolean = false,
    /** Kegagalan MUAT daftar — memicu `ExpressiveErrorState` saat tak ada data. */
    val error: String? = null,
    /** Kegagalan AKSI (terima/tolak/foto) — ditampilkan tanpa membuang daftar. */
    val actionError: String? = null,
    val submitting: Boolean = false,
    val mengunggahFoto: Boolean = false,
    val items: List<AcInstallTaskDto> = emptyList(),
    /** Id tugas yang kartunya sedang dibentangkan. */
    val terbuka: String? = null,
    /**
     * `idTugas -> url` bukti yang SUDAH terunggah tapi gagal dicatat sebagai
     * bukti. Layar memakainya untuk menawarkan "Lampirkan ulang" alih-alih
     * menyuruh petugas memotret lagi — tiap potret ulang meninggalkan satu lagi
     * berkas tanpa induk di `uploads/delivery`.
     */
    val buktiTertunda: Map<String, String> = emptyMap(),
)

/**
 * Tugas pemasangan AC milik petugas (teknisi).
 *
 * **Satu layar, tanpa layar detail terpisah.** Daftarnya pendek menurut
 * bentuknya sendiri — server hanya mengirim pengajuan `dijadwalkan` yang
 * menugaskan orang ini — dan `tugas-saya` sudah mengirim SELURUH isi tiap
 * pengajuan (SPK, tim, petugas, foto). Layar detail terpisah berarti request
 * kedua untuk data yang sudah ada di tangan.
 */
@HiltViewModel
class AcInstallViewModel @Inject constructor(
    private val repository: AcInstallRepository,
    /**
     * Untuk unggah foto SAJA. Server memvalidasi bahwa URL bukti berasal dari
     * endpoint unggah delivery (`foto_url_sah`), jadi jalur unggahnya memang
     * harus yang itu — bukan jalur baru milik layar ini.
     */
    private val deliveryRepository: DeliveryFlowRepository,
    authRepository: AuthRepository,
) : ViewModel() {

    /**
     * Dipakai memilih baris jawaban SAYA dari `petugas[]`. Kosong (profil belum
     * termuat) membuat [jawabanSaya] mengembalikan `null` — layar lalu
     * memperlakukan saya sebagai belum menjawab, bukan menebak.
     */
    val currentUserId: String = authRepository.currentUserId?.trim().orEmpty()

    private val _state = MutableStateFlow(AcInstallUiState())
    val state: StateFlow<AcInstallUiState> = _state.asStateFlow()

    init {
        muat()
    }

    /**
     * TANPA parameter status: default server = hanya `dijadwalkan`, dan itu
     * persis yang layar ini butuhkan. Mengirim status lain akan memasukkan
     * pengajuan yang sudah ditutup ke daftar pekerjaan.
     */
    fun muat() {
        _state.update { it.copy(loading = true, error = null, actionError = null) }
        viewModelScope.launch {
            when (val r = repository.tugasSaya()) {
                is AuthResult.Success -> _state.update {
                    it.copy(loading = false, error = null, items = r.data)
                }
                // Daftar lama SENGAJA dipertahankan saat refresh gagal: teknisi
                // yang sedang di lokasi dengan sinyal buruk tak boleh kehilangan
                // alamat dan nomor kontak yang sudah ada di layarnya.
                is AuthResult.Failure -> _state.update {
                    it.copy(loading = false, error = r.message)
                }
            }
        }
    }

    fun buka(id: String) = _state.update { it.copy(terbuka = if (it.terbuka == id) null else id) }

    fun bersihkanActionError() = _state.update { it.copy(actionError = null) }

    fun terima(id: String) = aksi { repository.terima(id) }

    /** [alasan] sudah lolos [bolehTolak] di layar; repository tetap men-`trim`. */
    fun tolak(id: String, alasan: String) = aksi { repository.tolak(id, alasan) }

    /**
     * Kamera → watermark → unggah → lampirkan.
     *
     * Watermark memakai util yang sama dengan absensi/PDI/komplain (jam + titik
     * GPS dicap KE PIKSEL): bukti pemasangan yang tak bisa dipastikan kapan dan
     * di mana diambil tak lebih berguna daripada tak ada foto.
     *
     * **Dua panggilan, dan urutannya penting.** Unggah lebih dulu, baru
     * `POST .../foto`. Kalau langkah kedua gagal, berkasnya sudah telanjur ada
     * di `uploads/delivery` tanpa induk — itu ongkos yang diterima, karena
     * kebalikannya (mendaftarkan URL sebelum berkasnya ada) menghasilkan baris
     * bukti yang menunjuk ke gambar yang tak pernah bisa dimuat siapa pun.
     *
     * **`withContext(Dispatchers.Default)` WAJIB.** `viewModelScope` berjalan di
     * `Dispatchers.Main.immediate`, sedangkan dekode + skala + rotasi EXIF +
     * loop kompresi WebP di dalam util itu memakan ratusan milidetik sampai
     * beberapa detik untuk foto kamera penuh — tanpa ini layarnya membeku persis
     * saat petugas menekan tombolnya. Pola yang sama dipakai delivery, absensi,
     * opname, dan komplain; dijaga `PhotoWatermarkGuardTest`.
     */
    fun unggahBukti(id: String, file: File, keterangan: String?) {
        _state.update { it.copy(mengunggahFoto = true, actionError = null) }
        viewModelScope.launch {
            val siap = withContext(Dispatchers.Default) {
                PhotoWatermark.prepareWatermarkedJpeg(
                    file = file,
                    lat = null,
                    lng = null,
                    title = "TRIDJAYA · PEMASANGAN AC",
                    subtitle = _state.value.items.firstOrNull { it.id == id }?.spk?.kodePengiriman.orEmpty(),
                )
            }
            if (siap == null) {
                _state.update { it.copy(mengunggahFoto = false, actionError = "Foto tidak terbaca, ulangi.") }
                return@launch
            }
            val url = when (
                val up = deliveryRepository.uploadPhoto(siap.first, "ac_install_${System.currentTimeMillis()}.webp")
            ) {
                is AuthResult.Success -> up.data
                is AuthResult.Failure -> {
                    _state.update { it.copy(mengunggahFoto = false, actionError = up.message) }
                    return@launch
                }
            }
            lampirkan(id, url, keterangan)
        }
    }

    /**
     * Coba lagi mencatat bukti yang unggahannya SUDAH berhasil — dipakai tombol
     * "Lampirkan ulang" yang muncul saat [AcInstallUiState.buktiTertunda] berisi
     * tugas ini. Tanpa jalan ini, satu-satunya cara maju adalah memotret ulang,
     * yang menambah berkas tanpa induk untuk pekerjaan yang cuma kurang satu
     * panggilan.
     */
    fun lampirkanUlang(id: String, keterangan: String?) {
        val url = _state.value.buktiTertunda[id] ?: return
        _state.update { it.copy(mengunggahFoto = true, actionError = null) }
        viewModelScope.launch { lampirkan(id, url, keterangan) }
    }

    private suspend fun lampirkan(id: String, url: String, keterangan: String?) {
        when (val r = repository.tambahFoto(id, url, keterangan)) {
            is AuthResult.Success -> _state.update { s ->
                s.copy(
                    mengunggahFoto = false,
                    actionError = null,
                    items = gantiSatu(s.items, r.data),
                    buktiTertunda = s.buktiTertunda - id,
                )
            }
            is AuthResult.Failure -> _state.update { s ->
                s.copy(
                    mengunggahFoto = false,
                    actionError = "${r.message} Fotonya sudah terunggah — pakai \"Lampirkan ulang\", tak perlu memotret lagi.",
                    buktiTertunda = s.buktiTertunda + (id to url),
                )
            }
        }
    }

    private fun aksi(blok: suspend () -> AuthResult<AcInstallTaskDto>) {
        if (_state.value.submitting) return
        _state.update { it.copy(submitting = true, actionError = null) }
        viewModelScope.launch {
            when (val r = blok()) {
                // Server mengembalikan pengajuan LENGKAP setelah aksi, jadi baris
                // itu ditukar di tempat — tanpa memuat ulang seluruh daftar, yang
                // akan menggeser posisi gulir petugas di tengah pekerjaan.
                is AuthResult.Success -> _state.update {
                    it.copy(submitting = false, actionError = null, items = gantiSatu(it.items, r.data))
                }
                is AuthResult.Failure -> _state.update { it.copy(submitting = false, actionError = r.message) }
            }
        }
    }

    private fun gantiSatu(lama: List<AcInstallTaskDto>, baru: AcInstallTaskDto): List<AcInstallTaskDto> =
        lama.map { if (it.id == baru.id) baru else it }
}
