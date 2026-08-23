package com.krisoft.tridjayaelektronik.ui.eksekutif

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krisoft.tridjayaelektronik.data.AuthResult
import com.krisoft.tridjayaelektronik.data.EksekutifRepository
import com.krisoft.tridjayaelektronik.data.model.EksekutifDetailCabangDto
import com.krisoft.tridjayaelektronik.data.model.EksekutifPapanDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EksekutifUiState(
    val rentang: EksekutifRentang = EksekutifRentang.BULAN_INI,
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
 * **Tidak memanggil `capabilities()`.** Gerbangnya sudah di lapisan tab —
 * `AppDestination.visibleBottomNavItems` hanya merender tab ini untuk pemegang
 * `monitoring.eksekutif`, jadi layar ini secara struktural tak terjangkau orang
 * yang akan dijawab 403. Menambah pengambilan peta di sini akan memerahkan
 * `PembacaPetaKemampuanTest` dengan alasan yang benar: VM ini berumur panjang
 * dan petanya akan beku.
 */
@HiltViewModel
class EksekutifViewModel @Inject constructor(
    private val repository: EksekutifRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(EksekutifUiState())
    val uiState: StateFlow<EksekutifUiState> = _uiState.asStateFlow()

    init {
        muat()
    }

    fun pilihRentang(rentang: EksekutifRentang) {
        if (_uiState.value.rentang == rentang) return
        _uiState.value = _uiState.value.copy(rentang = rentang)
        muat()
        // Detail cabang yang sedang terbuka ikut dimuat ulang — kalau tidak,
        // layar detail memperlihatkan periode LAMA di bawah judul periode BARU,
        // dan tak ada satu pun tanda bahwa keduanya berbeda.
        _uiState.value.kodeDealerDibuka?.let { bukaCabang(it) }
    }

    fun muat() {
        val rentang = rentangUntuk(_uiState.value.rentang)
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
        val rentang = rentangUntuk(_uiState.value.rentang)
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
            when (val hasil = repository.cabang(kodeDealer, rentang.start, rentang.end)) {
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

    fun tutupCabang() {
        _uiState.value = _uiState.value.copy(
            kodeDealerDibuka = null,
            detail = null,
            detailError = null,
            detailLoading = false,
        )
    }
}
