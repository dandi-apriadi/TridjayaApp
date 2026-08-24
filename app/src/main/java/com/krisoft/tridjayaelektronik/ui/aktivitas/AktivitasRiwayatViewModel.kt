package com.krisoft.tridjayaelektronik.ui.aktivitas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krisoft.tridjayaelektronik.data.AktivitasRepository
import com.krisoft.tridjayaelektronik.data.AuthRepository
import com.krisoft.tridjayaelektronik.data.AuthResult
import com.krisoft.tridjayaelektronik.data.TokenStore
import com.krisoft.tridjayaelektronik.data.model.AktivitasItemDto
import com.krisoft.tridjayaelektronik.domain.sales.KlasemenStandings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AktivitasRiwayatUiState(
    val loading: Boolean = false,
    val error: String? = null,
    /** `yyyy-MM-dd` yang sedang dilihat. Bawaannya hari ini. */
    val tanggal: String = KlasemenStandings.todayIso(),
    /** Hari ini, dipegang di state supaya batas maju tak dihitung ulang tiap recomposition. */
    val hariIni: String = KlasemenStandings.todayIso(),
    val items: List<AktivitasItemDto> = emptyList(),
)

/**
 * Riwayat aktivitas MILIK SENDIRI — padanan `KaryawanAktivitasHistoryPage.tsx`
 * di web, yang juga bukan menu tersendiri melainkan sub-layar dari layar input.
 *
 * READ-ONLY, dan itu syarat kebenaran bukan penyederhanaan — alasannya ditulis
 * di [AktivitasRiwayatPlan.kt]. Tak ada satu pun jalur tulis di sini.
 *
 * Tanpa cache: status review & nilai diputuskan PIC dari sisi lain, dan riwayat
 * basi membuat orang mengira penilaiannya belum masuk (alasan sama
 * [AktivitasReviewViewModel]).
 */
@HiltViewModel
class AktivitasRiwayatViewModel @Inject constructor(
    private val repository: AktivitasRepository,
    private val authRepository: AuthRepository,
    private val tokenStore: TokenStore,
) : ViewModel() {

    private val _state = MutableStateFlow(AktivitasRiwayatUiState())
    val state: StateFlow<AktivitasRiwayatUiState> = _state.asStateFlow()

    init { muat() }

    /** Bearer token untuk Coil memuat bukti privat (pola `AuthedImage`). */
    fun bearerToken(): String? = tokenStore.accessToken

    fun muat() {
        val tanggal = _state.value.tanggal
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            // `cachedUser?.id` WAJIB dioper: `raportOfDay` memakainya untuk
            // menyaring ULANG di sisi klien. Server hanya memaksa scope diri
            // sendiri saat role PRIMARY = `karyawan`, jadi user multi-role
            // (mis. primary `sales` + extra `karyawan`) menerima baris orang
            // lain — dan layar "riwayat SAYA" akan memajang milik orang lain.
            val karyawanId = authRepository.cachedUser?.id
            when (val hasil = repository.raportOfDay(tanggal, karyawanId)) {
                is AuthResult.Success -> _state.update {
                    // Balasan tanggal LAIN dibuang: pengguna bisa menggeser
                    // tanggal lagi selagi permintaan sebelumnya masih terbang,
                    // dan yang datang belakangan belum tentu yang terakhir
                    // diminta. Tanpa penjaga ini layar bisa memajang baris
                    // tanggal A di bawah judul tanggal B.
                    if (it.tanggal != tanggal) it
                    else it.copy(loading = false, error = null, items = urutRiwayat(hasil.data))
                }
                is AuthResult.Failure -> _state.update {
                    if (it.tanggal != tanggal) it
                    // Daftar lama DIKOSONGKAN: menahannya membuat baris tanggal
                    // sebelumnya terbaca sebagai isi tanggal yang gagal dimuat.
                    else it.copy(loading = false, error = hasil.message, items = emptyList())
                }
            }
        }
    }

    fun gantiTanggal(tanggal: String) {
        if (tanggal == _state.value.tanggal) return
        _state.update { it.copy(tanggal = tanggal) }
        muat()
    }

    /** Geser [hari] hari (−1 = kemarin); tak pernah melewati hari ini. */
    fun geserHari(hari: Int) {
        val s = _state.value
        gantiTanggal(geserTanggalRiwayat(s.tanggal, hari, s.hariIni))
    }
}
