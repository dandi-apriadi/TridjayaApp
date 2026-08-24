package com.krisoft.tridjayaelektronik.ui.eksekutif

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krisoft.tridjayaelektronik.data.AuthRepository
import com.krisoft.tridjayaelektronik.data.AuthResult
import com.krisoft.tridjayaelektronik.data.EksekutifRepository
import com.krisoft.tridjayaelektronik.data.model.EksekutifDetailCabangDto
import com.krisoft.tridjayaelektronik.data.model.EksekutifPapanDto
import com.krisoft.tridjayaelektronik.ui.home.PenyegarKemampuan
import com.krisoft.tridjayaelektronik.ui.home.sidikAkses
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EksekutifUiState(
    val periode: PilihanPeriode = PilihanPeriode.BAWAAN,
    val papan: EksekutifPapanDto? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    /** Cabang yang sedang dibuka. `null` = sedang di papan utama. */
    val detail: EksekutifDetailCabangDto? = null,
    val detailLoading: Boolean = false,
    val detailError: String? = null,
    /** Kode dealer yang sedang dimuat/dibuka — dipakai judul layar detail. */
    val kodeDealerDibuka: String? = null,
)

/**
 * ViewModel papan eksekutif.
 *
 * **Di-scope ke route, bukan ke tab.** `EksekutifNavHost` memasangnya lewat
 * `hiltViewModel()` di dalam `composable(...)`, jadi ia menempel pada
 * `NavBackStackEntry`. Itu penting karena tab ini kept-alive seumur sesi
 * (`visitedDestinations` di `MainActivity`): ViewModel yang di-scope ke tab tak
 * pernah mati, dan datanya membeku sampai app ditutup.
 *
 * **Ia MENYEGARKAN peta kemampuan walau tak memakainya sendiri.** Ini bagian
 * yang paling mudah dikira mubazir lalu dihapus, jadi baca alasannya:
 *
 * Cermin `AuthRepository.petaKemampuanTerakhir` — yang menentukan apakah tab
 * Eksekutif tampil — hanya diisi oleh `PenyegarKemampuan` milik
 * `ActivityViewModel` dan `HomeViewModel`. Superadmin mendarat LANGSUNG di tab
 * ini, dan kalau ia tak pernah membuka Activity/Operasional lagi, kedua VM itu
 * tak pernah `load()` ulang. Akibatnya dua-duanya buruk dan dua-duanya senyap:
 *
 *  - **akses DICABUT** → cerminnya beku `true` → tab tetap tampil seumur proses
 *    dan tiap permintaannya dijawab 403;
 *  - **akses BARU diberi** saat orangnya sedang membuka tab lain → tak pernah
 *    terbaca sampai app dimatikan.
 *
 * Karena itu VM ini ikut menyegarkan. Ia TIDAK membaca hasilnya untuk gerbang
 * apa pun di layarnya sendiri (gerbangnya di lapisan tab), tapi tulisannya ke
 * cermin itulah yang menjaga gerbang tetap hidup. `PenyegarKemampuan` yang
 * menahan badai request: pengambilan hanya terjadi saat sidik akses atau
 * identitas token berubah.
 */
@HiltViewModel
class EksekutifViewModel @Inject constructor(
    private val repository: EksekutifRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(EksekutifUiState())
    val uiState: StateFlow<EksekutifUiState> = _uiState.asStateFlow()

    /** Lihat [bukaCabang] — hanya disentuh dari `viewModelScope` (Main.immediate). */
    private var generasiDetail: Long = 0L

    /** Lihat KDoc kelas: menjaga cermin peta kemampuan tetap hidup. */
    private val penyegarKemampuan = PenyegarKemampuan(
        identitasToken = { authRepository.sidikTokenAkses },
        ambil = { authRepository.capabilities() },
    )

    init {
        muat()
    }

    /**
     * Ganti periode. Rentang yang PASTI ditolak server disaring di sini dan
     * dijawab pesan, bukan dikirim lalu dibalas 400 merah di atas papan yang
     * sebelumnya baik-baik saja.
     */
    fun pilihPeriode(periode: PilihanPeriode) {
        // Chip yang SAMA tetap memuat ulang saat ada galat yang menggantung.
        // Tanpa pengecualian ini, gagal muat mengunci layar pada periode yang
        // sedang dipilih: satu-satunya chip yang ingin diketuk orangnya adalah
        // chip yang sedang aktif, dan itulah yang diabaikan. (Jalan lain —
        // tarik-untuk-muat-ulang dan tombol "Coba lagi" — tetap ada; ini
        // menutup jalan yang paling naluriah.)
        val menggantung = _uiState.value.error != null || _uiState.value.detailError != null
        if (_uiState.value.periode == periode && !menggantung) return
        val r = rentangUntuk(periode)
        val alasan = validasiRentang(r.start, r.end)
        if (alasan != null) {
            // Periode LAMA sengaja dipertahankan: mengganti state ke pilihan
            // yang tak bisa dimuat berarti layar berjudul periode baru sambil
            // menampilkan angka periode lama — salah tanpa satu pun tanda.
            _uiState.value = _uiState.value.copy(error = alasan)
            return
        }
        _uiState.value = _uiState.value.copy(periode = periode, error = null)
        muat()
        // Detail cabang yang sedang terbuka ikut dimuat ulang — kalau tidak,
        // layar detail memperlihatkan periode LAMA di bawah judul periode BARU,
        // dan tak ada satu pun tanda bahwa keduanya berbeda.
        _uiState.value.kodeDealerDibuka?.let { bukaCabang(it) }
    }

    fun muat() {
        val rentang = rentangUntuk(_uiState.value.periode)
        segarkanKemampuan()
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            when (val hasil = repository.papan(rentang.start, rentang.end)) {
                is AuthResult.Success -> _uiState.value =
                    _uiState.value.copy(papan = hasil.data, isLoading = false, error = null)
                is AuthResult.Failure -> _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    // Papan LAMA sengaja dibiarkan apa adanya saat gagal
                    // menyegarkan: mengosongkannya membuang data yang masih
                    // berguna dan mengubah kegagalan sementara jadi layar
                    // kosong. Layar menampilkan galatnya sebagai banner di atas
                    // data lama, bukan menggantikannya.
                    error = hasil.message,
                )
            }
        }
    }

    fun bukaCabang(kodeDealer: String) {
        val rentang = rentangUntuk(_uiState.value.periode)
        // Penghitung generasi, bukan `Job.cancel()`: repository selalu jalan
        // sampai akhir (mengembalikan `AuthResult`, tak pernah melempar), jadi
        // yang perlu dijaga bukan pembatalan melainkan URUTAN SELESAI. Tanpa
        // ini, membuka cabang A lalu cepat pindah ke B bisa menaruh angka A di
        // layar berjudul B — salah tanpa satu pun tanda. Pola yang sama sudah
        // dipakai `ActivityViewModel.loadGeneration`.
        generasiDetail += 1
        val generasi = generasiDetail
        _uiState.value = _uiState.value.copy(
            kodeDealerDibuka = kodeDealer,
            detailLoading = true,
            detailError = null,
            // Detail cabang LAIN dibuang di sini — beda dengan papan di atas.
            // Membiarkannya berarti layar cabang B sempat menampilkan angka
            // cabang A dengan judul cabang B, yaitu angka yang salah tanpa satu
            // pun tanda. Kosong-lalu-terisi jujur; salah-lalu-berubah tidak.
            detail = _uiState.value.detail?.takeIf { it.cabang.kodeDealer == kodeDealer },
        )
        viewModelScope.launch {
            val hasil = repository.cabang(kodeDealer, rentang.start, rentang.end)
            if (generasi != generasiDetail) return@launch
            when (hasil) {
                is AuthResult.Success -> _uiState.value = _uiState.value.copy(
                    detail = hasil.data,
                    detailLoading = false,
                    detailError = null,
                )
                is AuthResult.Failure -> _uiState.value = _uiState.value.copy(
                    detailLoading = false,
                    detailError = hasil.message,
                )
            }
        }
    }

    /**
     * Menulis ke cermin bersama, TIDAK ke state layar ini.
     *
     * Sengaja tak memicu muat-ulang apa pun: peta kemampuan tak mengubah satu
     * angka pun di papan. Yang berubah adalah apakah TAB-nya masih boleh ada,
     * dan itu dinilai `MainActivity` dari cermin yang sama.
     */
    private fun segarkanKemampuan() {
        val sidik = sidikAkses(authRepository.cachedUser)
        viewModelScope.launch { penyegarKemampuan.segarkan(sidik) }
    }

    fun tutupCabang() {
        _uiState.value = _uiState.value.copy(
            kodeDealerDibuka = null,
            detail = null,
            detailError = null,
            detailLoading = false,
        )
    }
}
