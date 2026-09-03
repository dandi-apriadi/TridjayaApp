package com.krisoft.tridjayaelektronik.ui.homeservice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krisoft.tridjayaelektronik.data.AuthResult
import com.krisoft.tridjayaelektronik.data.HS_JENIS_TARIK_UNIT
import com.krisoft.tridjayaelektronik.data.HomeServiceRepository
import com.krisoft.tridjayaelektronik.data.model.HsTicketDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Empat layar daftar, satu ViewModel — yang membedakan hanya parameter
 * permintaan + himpunan status yang ditampilkan, semuanya dinyatakan di sini
 * supaya tak ada layar yang diam-diam memakai kombinasi berbeda.
 */
enum class HsMode(
    val judul: String,
    /** `jenis` yang dikirim ke server (`null` = kedua jalur). */
    val jenis: String?,
    /** `mine=true` = hanya tugas milik akun ini. */
    val mine: Boolean?,
    val statusAktif: Set<String>,
    /** `sayaLapor=true` = tiket yang AKU laporkan, bukan yang ditugaskan padaku. */
    val sayaLapor: Boolean? = null,
) {
    /** Papan CS: tiket menunggu keputusan triase. */
    TRIASE("Komplain Masuk", null, null, HS_STATUS_TRIASE),

    /** Teknisi PDI: tugas kunjungan miliknya (server memaksa `mine` untuk pdi murni). */
    TEKNISI("Tugas Home Service", null, true, HS_STATUS_TUGAS_TEKNISI),

    /** Delivery control: antrian penarikan unit yang perlu driver / sedang berjalan. */
    TARIK("Tarik Unit", HS_JENIS_TARIK_UNIT, null, HS_STATUS_TARIK_AKTIF),

    /** Driver: unit yang ditugaskan padanya dan belum diambil. */
    DRIVER("Tugas Tarik Unit", HS_JENIS_TARIK_UNIT, true, setOf(HS_TARIK_DITUGASKAN)),

    /**
     * Riwayat komplain yang DIA laporkan sendiri — satu-satunya mode yang bukan
     * antrian kerja.
     *
     * Tiga hal membedakannya dari empat mode di atas, dan ketiganya disengaja:
     * - `mine = null` sementara `sayaLapor = true`. Server memaksa `mine` nyala
     *   untuk pemegang `homeservice.task`; kalau keduanya dikirim bersama,
     *   teknisi yang melapor akan melihat irisan "tugasku DAN laporanku" alih-alih
     *   laporannya. Server sendiri mematikan penyaring tugas saat `sayaLapor`.
     * - [HS_STATUS_SEMUA], bukan himpunan aktif: ini RIWAYAT, dan pelapor justru
     *   paling ingin tahu tiketnya sudah `selesai`/`eskalasi`.
     * - `jenis = null`: komplain yang berujung penarikan unit tetap laporannya.
     */
    SAYA_LAPOR("Komplain Saya", null, null, HS_STATUS_SEMUA, sayaLapor = true),
}

data class HsListUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val mode: HsMode = HsMode.TRIASE,
    /** Tiket aktif menurut [HsMode.statusAktif], sudah terurut. */
    val items: List<HsTicketDto> = emptyList(),
    /** Tiket lain yang ikut terambil (mis. sudah selesai) — ditampilkan terpisah. */
    val lainnya: List<HsTicketDto> = emptyList(),
)

@HiltViewModel
class HomeServiceListViewModel @Inject constructor(
    private val repository: HomeServiceRepository
) : ViewModel() {

    private val _state = MutableStateFlow(HsListUiState())
    val state: StateFlow<HsListUiState> = _state.asStateFlow()

    fun muat(mode: HsMode) {
        _state.update { it.copy(mode = mode, loading = true, error = null) }
        viewModelScope.launch {
            when (
                val r = repository.list(
                    // `status` TIDAK dikirim: server cuma menerima satu nilai,
                    // sementara layar ini butuh beberapa (lihat `saringStatus`).
                    status = null,
                    jenis = mode.jenis,
                    mine = mode.mine,
                    sayaLapor = mode.sayaLapor,
                )
            ) {
                is AuthResult.Success -> _state.update {
                    val semua = r.data.items
                    it.copy(
                        loading = false,
                        error = null,
                        items = urutkanAntrian(saringStatus(semua, mode.statusAktif)),
                        lainnya = urutkanAntrian(semua.filterNot { t -> t.status in mode.statusAktif }),
                    )
                }
                is AuthResult.Failure -> _state.update {
                    it.copy(loading = false, error = r.message, items = emptyList(), lainnya = emptyList())
                }
            }
        }
    }

    fun muatUlang() = muat(_state.value.mode)
}
