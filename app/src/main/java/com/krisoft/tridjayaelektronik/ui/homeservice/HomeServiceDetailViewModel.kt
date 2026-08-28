package com.krisoft.tridjayaelektronik.ui.homeservice

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krisoft.tridjayaelektronik.data.AuthRepository
import com.krisoft.tridjayaelektronik.data.AuthResult
import com.krisoft.tridjayaelektronik.data.DeliveryFlowRepository
import com.krisoft.tridjayaelektronik.data.HomeServiceRepository
import com.krisoft.tridjayaelektronik.data.TokenStore
import com.krisoft.tridjayaelektronik.data.model.DriverDto
import com.krisoft.tridjayaelektronik.data.model.HsCompleteBody
import com.krisoft.tridjayaelektronik.data.model.HsSparepartDto
import com.krisoft.tridjayaelektronik.data.model.HsTicketDetailDto
import com.krisoft.tridjayaelektronik.util.PhotoWatermark
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class HsDetailUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val pesan: String? = null,
    val tiket: HsTicketDetailDto? = null,
    /** Sedang mengirim satu aksi — semua tombol dimatikan selama ini. */
    val sibuk: Boolean = false,
    val teknisi: List<DriverDto> = emptyList(),
    /** Daftar teknisi gagal dimuat (mis. akun `cs` ditolak `GET /users`). */
    val teknisiError: String? = null,
    val driver: List<DriverDto> = emptyList(),
    val driverError: String? = null,
    /** Foto yang sudah terunggah untuk aksi yang sedang disiapkan. */
    val fotoTerunggah: List<String> = emptyList(),
    val mengunggahFoto: Boolean = false,
    /** `true` sesudah aksi yang mengubah antrian — layar daftar perlu memuat ulang. */
    val berubah: Boolean = false,
    /** `homeservice.dispatch` — triase CS (tugaskan teknisi, minta tarik, batalkan). */
    val bolehDispatch: Boolean = false,
    /** `delivery.control` — menugaskan driver penarikan unit. */
    val bolehAturTarik: Boolean = false,
)

/**
 * Detail satu tiket komplain + SEMUA aksinya (triase CS, kunjungan teknisi,
 * penarikan unit). Satu layar untuk semua peran: tombol mana yang tampil
 * ditentukan status tiket + kemampuan akun, bukan layar yang berbeda-beda —
 * satu tiket bisa berpindah antar peran tanpa berpindah halaman.
 */
@HiltViewModel
class HomeServiceDetailViewModel @Inject constructor(
    private val repository: HomeServiceRepository,
    private val deliveryRepository: DeliveryFlowRepository,
    private val authRepository: AuthRepository,
    private val tokenStore: TokenStore,
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val ticketId: String = savedStateHandle.get<String>("id").orEmpty()

    private val _state = MutableStateFlow(HsDetailUiState())
    val state: StateFlow<HsDetailUiState> = _state.asStateFlow()

    /** Bearer token untuk Coil memuat foto bukti (di-serve terautentikasi). */
    fun bearerToken(): String? = tokenStore.accessToken

    /** Id akun ini — dipakai layar menilai "tugas ini milikku" (tombol mulai/selesai). */
    val userId: String? get() = authRepository.cachedUser?.id

    init {
        muat()
        // Kemampuan dari SERVER, bukan tebakan role: tombol triase/tarik di layar
        // ini memetakan langsung ke guard backend, jadi peta kemampuan adalah
        // sumber yang sama dengan yang menolak/meloloskan aksinya nanti.
        // Coroutine terpisah supaya detail tetap tampil walau panggilan ini lambat.
        //
        // SEKALI seumur ViewModel, dan itu cukup: VM ini di-scope ke
        // `NavBackStackEntry` milik `ROUTE_HS_DETAIL` yang di-push lalu di-pop,
        // jadi ia mati bersama layarnya. Menaikkannya ke scope kept-alive tanpa
        // `PenyegarKemampuan` = peta beku seumur proses — lihat
        // `PembacaPetaKemampuanTest`.
        viewModelScope.launch {
            authRepository.capabilities()?.let { caps ->
                _state.update {
                    it.copy(
                        bolehDispatch = caps["homeservice.dispatch"] == true,
                        bolehAturTarik = caps["delivery.control"] == true,
                    )
                }
            }
        }
    }

    fun muat() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val r = repository.detail(ticketId)) {
                is AuthResult.Success -> _state.update { it.copy(loading = false, tiket = r.data, error = null) }
                is AuthResult.Failure -> _state.update { it.copy(loading = false, error = r.message) }
            }
        }
    }

    /**
     * Daftar teknisi PDI untuk dropdown penugasan. Dimuat HANYA saat dialognya
     * dibuka: `GET /api/users` ditolak untuk role `cs` (gate `USERS_READ_ROLES`
     * tak memuatnya) walau `cs` justru yang berhak menugaskan — jadi kegagalan
     * di sini normal dan ditampilkan sebagai keterangan, bukan error layar.
     */
    fun muatTeknisi() {
        if (_state.value.teknisi.isNotEmpty()) return
        viewModelScope.launch {
            when (val r = deliveryRepository.teknisiPdi()) {
                is AuthResult.Success -> _state.update { it.copy(teknisi = r.data, teknisiError = null) }
                is AuthResult.Failure -> _state.update {
                    it.copy(
                        teknisiError = "Daftar teknisi tak bisa dimuat akun ini (${r.message}). " +
                            "Tugaskan lewat web, atau minta admin menugaskannya.",
                    )
                }
            }
        }
    }

    fun muatDriver() {
        if (_state.value.driver.isNotEmpty()) return
        viewModelScope.launch {
            when (val r = deliveryRepository.drivers()) {
                is AuthResult.Success -> _state.update { it.copy(driver = r.data, driverError = null) }
                is AuthResult.Failure -> _state.update { it.copy(driverError = r.message) }
            }
        }
    }

    /**
     * Foto bukti dari kamera → watermark (judul + jam/lokasi dicap KE PIKSEL,
     * util yang sama dipakai absensi/PDI) → unggah → URL relatif.
     */
    fun unggahFoto(file: File, judul: String, lat: Double? = null, lng: Double? = null) {
        _state.update { it.copy(mengunggahFoto = true, error = null) }
        viewModelScope.launch {
            val siap = PhotoWatermark.prepareWatermarkedJpeg(
                file = file,
                lat = lat,
                lng = lng,
                title = judul,
                subtitle = _state.value.tiket?.nomorTiket.orEmpty(),
            )
            if (siap == null) {
                _state.update { it.copy(mengunggahFoto = false, error = "Foto tidak terbaca, ulangi.") }
                return@launch
            }
            when (val r = repository.uploadPhoto(siap.first, "home-service.webp")) {
                is AuthResult.Success -> _state.update {
                    it.copy(mengunggahFoto = false, fotoTerunggah = it.fotoTerunggah + r.data)
                }
                is AuthResult.Failure -> _state.update { it.copy(mengunggahFoto = false, error = r.message) }
            }
        }
    }

    fun hapusFotoTerunggah(url: String) =
        _state.update { it.copy(fotoTerunggah = it.fotoTerunggah - url) }

    fun tugaskanTeknisi(teknisiId: String, tanggal: String?) =
        jalankan { repository.assign(ticketId, teknisiId, jadwalUntukServer(tanggal)) }

    fun mulaiKunjungan(lat: Double?, lng: Double?) =
        jalankan { repository.start(ticketId, lat, lng) }

    fun tutupKunjungan(
        hasil: String,
        tindakan: String?,
        catatan: String?,
        adaSparepart: Boolean,
        sparepart: List<HsSparepartDto>,
        biayaDibayar: Double?,
        buktiBayarUrl: String?,
        rating: Int?,
        komentar: String?,
    ) {
        val foto = _state.value.fotoTerunggah
        val gate = bolehTutupKunjungan(
            hasil = hasil,
            fotoUrls = foto,
            adaSparepart = adaSparepart,
            sparepart = sparepart,
            biayaDibayar = biayaDibayar,
            buktiBayarUrl = buktiBayarUrl,
            rating = rating,
        )
        if (!gate.ok) {
            _state.update { it.copy(error = gate.alasan) }
            return
        }
        jalankan {
            repository.complete(
                ticketId,
                HsCompleteBody(
                    hasil = hasil,
                    tindakan = tindakan?.trim()?.takeIf { it.isNotBlank() },
                    catatan = catatan?.trim()?.takeIf { it.isNotBlank() },
                    adaPenggantianSparepart = adaSparepart,
                    sparepartItems = if (adaSparepart) sparepart else emptyList(),
                    biayaDibayar = biayaDibayar,
                    buktiBayarUrl = buktiBayarUrl,
                    fotoUrls = foto,
                    rating = rating,
                    komentarKonsumen = komentar?.trim()?.takeIf { it.isNotBlank() },
                )
            )
        }
    }

    fun batalkan(alasan: String) = jalankanBeralasan(alasan) { repository.cancel(ticketId, alasan) }

    fun mintaTarik(alasan: String) = jalankanBeralasan(alasan) { repository.mintaTarik(ticketId, alasan) }

    fun batalTarik(alasan: String) = jalankanBeralasan(alasan) { repository.batalTarik(ticketId, alasan) }

    fun tugaskanDriver(driverId: String, tanggal: String?) =
        jalankan { repository.assignTarik(ticketId, driverId, jadwalUntukServer(tanggal)) }

    fun tandaiUnitDiambil(catatan: String?) = jalankan {
        repository.ambilUnit(ticketId, _state.value.fotoTerunggah.firstOrNull(), catatan)
    }

    fun hapusPesan() = _state.update { it.copy(pesan = null, error = null) }

    private fun jalankanBeralasan(alasan: String, blok: suspend () -> AuthResult<*>) {
        val gate = bolehAlasan(alasan)
        if (!gate.ok) {
            _state.update { it.copy(error = gate.alasan) }
            return
        }
        jalankan(blok)
    }

    private fun jalankan(blok: suspend () -> AuthResult<*>) {
        _state.update { it.copy(sibuk = true, error = null, pesan = null) }
        viewModelScope.launch {
            when (val r = blok()) {
                is AuthResult.Success -> {
                    // Muat ULANG dari server, bukan menambal state dari respons:
                    // aksi tahap ini mengubah `visits` juga, dan respons aksinya
                    // cuma mengembalikan tiketnya (tanpa kunjungan).
                    _state.update {
                        it.copy(sibuk = false, pesan = "Tersimpan.", fotoTerunggah = emptyList(), berubah = true)
                    }
                    muat()
                }
                is AuthResult.Failure -> _state.update { it.copy(sibuk = false, error = r.message) }
            }
        }
    }
}
