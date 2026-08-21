package com.krisoft.tridjayaelektronik.ui.kpi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krisoft.tridjayaelektronik.data.AuthRepository
import com.krisoft.tridjayaelektronik.data.AuthResult
import com.krisoft.tridjayaelektronik.data.KpiRepository
import com.krisoft.tridjayaelektronik.data.model.KpiDetailData
import com.krisoft.tridjayaelektronik.data.model.KpiKaryawanRowDto
import com.krisoft.tridjayaelektronik.data.model.shiftPeriode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class KpiUiState(
    /** "" = biarkan server memilih bulan berjalan (jam server, bukan jam HP).
     *  Diisi dari `periode` respons pertama supaya tombol geser bulan punya
     *  titik awal yang sama dengan yang dihitung server. */
    val periode: String = "",
    /** `kpi.manage` — pemiliknya boleh melihat KPI orang lain. */
    val canManage: Boolean = false,
    val detail: KpiDetailData? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val list: List<KpiKaryawanRowDto> = emptyList(),
    val listLoading: Boolean = false,
    val listError: String? = null,
    /** Karyawan yang sedang dibuka; `null` = KPI milik sendiri. */
    val viewing: KpiKaryawanRowDto? = null
)

/**
 * KPI: capaian per indikator milik sendiri (`/kpi/me`) — dan, bagi pemegang
 * `kpi.manage`, daftar seluruh karyawan (`/kpi/karyawan`) untuk dibuka satu per
 * satu. Gate dibaca dari `GET /api/me/capabilities`, bukan ditebak dari role
 * lokal, supaya server tetap yang menentukan.
 */
@HiltViewModel
class KpiViewModel @Inject constructor(
    private val repository: KpiRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _state = MutableStateFlow(KpiUiState())
    val state: StateFlow<KpiUiState> = _state.asStateFlow()

    private var started = false

    /**
     * Peta kemampuan diambil SEKALI di sini ([started]), dan itu cukup: VM ini
     * di-scope ke `NavBackStackEntry` milik `ROUTE_KPI` yang di-push lalu
     * di-pop, jadi ia mati bersama layarnya dan buka-berikutnya mengambil ulang.
     * Kalau VM ini kelak dipindah ke scope kept-alive (root tab), gerbang
     * `canManage` akan beku seumur proses dan `PenyegarKemampuan`
     * (`ui/home/SidikAkses.kt`) jadi WAJIB — lihat `PembacaPetaKemampuanTest`.
     */
    fun start() {
        if (started) return
        started = true
        viewModelScope.launch {
            val canManage = authRepository.capabilities()?.get("kpi.manage") == true
            _state.update { it.copy(canManage = canManage) }
            if (canManage) loadList()
        }
        loadDetail()
    }

    fun loadDetail() {
        _state.update { it.copy(loading = true, error = null) }
        val periode = _state.value.periode.ifEmpty { null }
        val target = _state.value.viewing
        viewModelScope.launch {
            val res = if (target == null) repository.me(periode)
            else repository.karyawanDetail(target.karyawanId, periode)
            when (res) {
                is AuthResult.Success -> _state.update {
                    it.copy(
                        loading = false,
                        detail = res.data,
                        // Kunci periode ke jawaban server saat pertama kali.
                        periode = it.periode.ifEmpty { res.data.periode }
                    )
                }
                is AuthResult.Failure -> _state.update { it.copy(loading = false, error = res.message) }
            }
        }
    }

    fun loadList() {
        _state.update { it.copy(listLoading = true, listError = null) }
        val periode = _state.value.periode.ifEmpty { null }
        viewModelScope.launch {
            when (val res = repository.karyawan(periode)) {
                is AuthResult.Success -> _state.update { it.copy(listLoading = false, list = res.data) }
                is AuthResult.Failure -> _state.update { it.copy(listLoading = false, listError = res.message) }
            }
        }
    }

    /** Geser bulan. Periode belum terkunci (respons pertama belum datang) =
     *  abaikan, daripada menebak bulan dari jam HP. */
    fun shiftMonth(delta: Int) {
        val current = _state.value.periode
        if (current.isEmpty()) return
        _state.update { it.copy(periode = shiftPeriode(current, delta)) }
        loadDetail()
        if (_state.value.canManage) loadList()
    }

    fun openKaryawan(row: KpiKaryawanRowDto) {
        _state.update { it.copy(viewing = row, detail = null, error = null) }
        loadDetail()
    }

    /** Kembali ke KPI sendiri. */
    fun closeKaryawan() {
        _state.update { it.copy(viewing = null, detail = null, error = null) }
        loadDetail()
    }
}
