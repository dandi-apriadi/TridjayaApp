package com.krisoft.tridjayaelektronik.ui.lapangan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krisoft.tridjayaelektronik.data.AuthResult
import com.krisoft.tridjayaelektronik.data.SalesRepository
import com.krisoft.tridjayaelektronik.data.model.PapanLapanganDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

enum class PeranPapan(val slug: String, val judul: String) {
    DRIVER("driver", "Driver"),
    PDI("pdi", "PDI"),
}

data class KlasemenLapanganUiState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val peran: PeranPapan = PeranPapan.DRIVER,
    val periode: String = periodeBerjalan(),
    val papan: PapanLapanganDto? = null,
)

/** `YYYY-MM` bulan berjalan menurut jam perangkat. */
internal fun periodeBerjalan(kalender: Calendar = Calendar.getInstance()): String {
    val bulan = kalender.get(Calendar.MONTH) + 1
    return "${kalender.get(Calendar.YEAR)}-${bulan.toString().padStart(2, '0')}"
}

/**
 * Papan klasemen kerja lapangan.
 *
 * Nyaris tak punya logika: peringkat, lantai sampel, dan bobot dihitung SERVER.
 * Itu memang tujuannya — satu-satunya cara memastikan papan di HP dan papan di
 * web tak pernah berselisih adalah dengan tidak punya aturan penilaian kedua
 * di sini.
 */
@HiltViewModel
class KlasemenLapanganViewModel @Inject constructor(
    private val salesRepository: SalesRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(KlasemenLapanganUiState())
    val uiState: StateFlow<KlasemenLapanganUiState> = _uiState.asStateFlow()

    init {
        muat()
    }

    fun pilihPeran(peran: PeranPapan) {
        if (_uiState.value.peran == peran) return
        // Papan lama dibuang, bukan dibiarkan tampil di bawah judul peran baru:
        // tabel PDI yang masih memajang nama driver adalah kesalahan yang
        // terbaca sebagai fakta.
        _uiState.update { it.copy(peran = peran, papan = null) }
        muat()
    }

    fun muat(forceRefresh: Boolean = false) {
        val (peran, periode) = _uiState.value.let { it.peran to it.periode }
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            when (val hasil = salesRepository.papanLapangan(peran.slug, periode, forceRefresh)) {
                is AuthResult.Success -> _uiState.update {
                    // Balasan yang datang setelah pemakai berpindah tab dibuang:
                    // tanpa penjaga ini, jawaban lambat untuk peran lama menimpa
                    // papan peran baru.
                    if (it.peran != peran) it
                    else it.copy(isLoading = false, papan = hasil.data, errorMessage = null)
                }
                is AuthResult.Failure -> _uiState.update {
                    if (it.peran != peran) it
                    else it.copy(
                        isLoading = false,
                        errorMessage = pesanGalat(hasil.httpStatus, hasil.message),
                    )
                }
            }
        }
    }

    /**
     * 403 harus terbaca "kamu memang tak berhak", bukan "coba lagi nanti".
     *
     * Dipilih dari `httpStatus`, BUKAN dari `code`: gateway memakai satu kode
     * `gateway_error` untuk beberapa status sekaligus (lihat doc
     * `AuthResult.Failure`), jadi menebak dari kode akan salah menggolongkan
     * penolakan permanen sebagai gangguan sementara.
     */
    internal fun pesanGalat(httpStatus: Int?, message: String): String = when (httpStatus) {
        403 -> "Papan ini hanya untuk petugas lapangan dan atasannya."
        else -> message.ifBlank { "Gagal memuat papan." }
    }
}
