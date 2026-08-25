package com.krisoft.tridjayaelektronik.ui.acinstall

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krisoft.tridjayaelektronik.data.AcInstallRepository
import com.krisoft.tridjayaelektronik.data.AuthResult
import com.krisoft.tridjayaelektronik.data.model.AcInstallStatus
import com.krisoft.tridjayaelektronik.data.model.AcInstallTaskDto
import com.krisoft.tridjayaelektronik.data.model.AcInstallTimMasterDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AcInstallScheduleUiState(
    val loading: Boolean = false,
    /** Kegagalan MUAT daftar — memicu `ExpressiveErrorState` saat tak ada data. */
    val error: String? = null,
    /** Kegagalan AKSI (jadwal/selesai/batal) — ditampilkan tanpa membuang daftar. */
    val actionError: String? = null,
    val submitting: Boolean = false,
    val status: String = AcInstallStatus.DIAJUKAN,
    val items: List<AcInstallTaskDto> = emptyList(),
    /**
     * Master tim. Gagal memuatnya BUKAN kegagalan layar: daftar pengajuan tetap
     * berguna dibaca, dan yang hilang hanya kemampuan memilih tim saat
     * menjadwalkan — form-nya sendiri yang menjelaskan itu.
     */
    val tim: List<AcInstallTimMasterDto> = emptyList(),
    val timError: String? = null,
    /** Id pengajuan yang kartunya sedang dibentangkan. */
    val terbuka: String? = null,
)

/**
 * Penugasan pemasangan AC — sisi VERIFIKATOR (`acinstall.schedule`).
 *
 * **Memuat per-status, bukan sekali ambil lalu disaring di klien.** Rute
 * `GET .../pemasangan-ac` tanpa `status` mengirim SEMUA status yang dipotong
 * **300 terbaru** oleh server, dan potongan itu senyap: begitu antriannya
 * melewati angka tersebut, yang hilang adalah baris TERTUA — justru yang paling
 * perlu dikerjakan. Menyaring di klien akan menyembunyikan kehilangan itu di
 * balik tab yang terlihat wajar.
 */
@HiltViewModel
class AcInstallScheduleViewModel @Inject constructor(
    private val repository: AcInstallRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AcInstallScheduleUiState())
    val state: StateFlow<AcInstallScheduleUiState> = _state.asStateFlow()

    init {
        muat()
        muatTim()
    }

    fun pilihStatus(status: String) {
        if (status == _state.value.status) return
        _state.update { it.copy(status = status, terbuka = null) }
        muat()
    }

    fun muat() {
        val status = _state.value.status
        _state.update { it.copy(loading = true, error = null, actionError = null) }
        viewModelScope.launch {
            when (val r = repository.daftar(status)) {
                is AuthResult.Success -> _state.update {
                    // Hanya tulis kalau tab-nya masih yang sama: pengguna bisa
                    // berpindah tab selagi permintaan ini di jalan, dan hasil
                    // yang datang belakangan akan menaruh baris tab lama di
                    // bawah judul tab baru tanpa satu pun galat.
                    if (it.status != status) it
                    else it.copy(loading = false, error = null, items = r.data)
                }
                is AuthResult.Failure -> _state.update {
                    if (it.status != status) it
                    // Daftar lama dipertahankan (pola `AcInstallViewModel`):
                    // refresh yang gagal tak boleh mengosongkan layar yang
                    // sedang dipakai.
                    else it.copy(loading = false, error = r.message)
                }
            }
        }
    }

    private fun muatTim() {
        viewModelScope.launch {
            when (val r = repository.tim()) {
                is AuthResult.Success -> _state.update { it.copy(tim = r.data, timError = null) }
                is AuthResult.Failure -> _state.update { it.copy(timError = r.message) }
            }
        }
    }

    fun buka(id: String?) = _state.update { it.copy(terbuka = id, actionError = null) }

    fun bersihkanActionError() = _state.update { it.copy(actionError = null) }

    /**
     * [teamIds] dikirim UTUH — server MENGGANTI seluruh daftar tim, bukan
     * menambahinya. Layar menyemainya dari [AcInstallSchedulePlan.timTerpilihAwal].
     */
    fun jadwalkan(
        id: String,
        tanggal: String,
        jam: String,
        teamIds: Set<String>,
        catatan: String,
        onSukses: () -> Unit,
    ) {
        if (!AcInstallSchedulePlan.bolehSimpanJadwal(tanggal, jam)) {
            _state.update { it.copy(actionError = "Tanggal harus YYYY-MM-DD dan jam HH:MM") }
            return
        }
        aksi(onSukses) {
            repository.jadwalkan(id, tanggal, jam.ifBlank { null }, teamIds.toList(), catatan)
        }
    }

    fun selesaikan(id: String, catatan: String, onSukses: () -> Unit) =
        aksi(onSukses) { repository.selesai(id, catatan) }

    fun batalkan(id: String, alasan: String, onSukses: () -> Unit) {
        if (!AcInstallSchedulePlan.bolehSimpanBatal(alasan)) {
            _state.update { it.copy(actionError = "Alasan pembatalan wajib diisi") }
            return
        }
        aksi(onSukses) { repository.batal(id, alasan) }
    }

    /**
     * Hasil aksi MENIMPA baris yang bersangkutan di tempat, tanpa memuat ulang
     * seluruh daftar — kecuali kalau statusnya berpindah keluar dari tab yang
     * sedang dibuka, yang membuat barisnya memang tak lagi milik daftar ini.
     */
    private fun aksi(
        onSukses: () -> Unit,
        panggil: suspend () -> AuthResult<AcInstallTaskDto>,
    ) {
        _state.update { it.copy(submitting = true, actionError = null) }
        viewModelScope.launch {
            when (val r = panggil()) {
                is AuthResult.Success -> {
                    val baru = r.data
                    _state.update { s ->
                        val masihDiTab = baru.status == s.status
                        s.copy(
                            submitting = false,
                            actionError = null,
                            items = if (masihDiTab) {
                                s.items.map { if (it.id == baru.id) baru else it }
                            } else {
                                s.items.filterNot { it.id == baru.id }
                            },
                            terbuka = if (masihDiTab) s.terbuka else null,
                        )
                    }
                    onSukses()
                }
                is AuthResult.Failure -> _state.update {
                    it.copy(submitting = false, actionError = r.message)
                }
            }
        }
    }
}
