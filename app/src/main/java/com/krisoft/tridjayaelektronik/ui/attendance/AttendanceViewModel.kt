package com.krisoft.tridjayaelektronik.ui.attendance

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krisoft.tridjayaelektronik.data.AbsensiRepository
import com.krisoft.tridjayaelektronik.data.AuthRepository
import com.krisoft.tridjayaelektronik.data.AuthResult
import com.krisoft.tridjayaelektronik.data.OffRepository
import com.krisoft.tridjayaelektronik.data.model.AbsensiGeofenceDto
import com.krisoft.tridjayaelektronik.data.model.AbsensiRecordDto
import com.krisoft.tridjayaelektronik.data.model.AbsensiTodayDto
import com.krisoft.tridjayaelektronik.data.model.OffRequestDto
import com.krisoft.tridjayaelektronik.util.PhotoWatermark
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

data class AttendanceUiState(
    val loading: Boolean = true,
    val today: AbsensiRecordDto? = null,
    /**
     * Payload `/absensi/today` apa adanya — dipakai menurunkan [gatePulang].
     * `null` = belum/gagal dimuat, dan gerbangnya fail-open.
     */
    val todayDto: AbsensiTodayDto? = null,
    val history: List<AbsensiRecordDto> = emptyList(),
    val loadError: String? = null,
    /**
     * Kegagalan `history()` SECARA KHUSUS, walau `today()` sukses. Sebelum ini
     * kegagalan sebagian begini tak pernah terlihat: `loadError` cuma terisi
     * kalau KEDUANYA gagal, jadi layar diam-diam terus menampilkan riwayat lama
     * tanpa satu pun tanda — muat ulang berkali-kali percuma kalau sebab
     * gagalnya (mis. jaringan lemot) masih sama. `null` setelah ditampilkan
     * sekali (lihat [AttendanceViewModel.bersihkanHistoryLoadError]).
     */
    val historyLoadError: String? = null,

    /** Preview selfie yang baru diambil (byte upload disimpan terpisah di VM). */
    val selfie: Bitmap? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val locating: Boolean = false,
    val locationDenied: Boolean = false,
    val locationError: String? = null,

    /** Geofence SEMUA cabang (dari `today`) — app pilih terdekat/yang memuat untuk verdict live. */
    val geofences: List<AbsensiGeofenceDto> = emptyList(),
    /**
     * `true` hanya bila [geofences] datang dari field `geofences[]` server, yaitu
     * daftar SELURUH cabang — himpunan yang SAMA dengan yang dinilai
     * `evaluate_punch` di server.
     *
     * `false` = kita cuma punya `geofence` tunggal (cabang sendiri) dari server
     * lama, atau tak punya apa-apa. Bedanya menentukan: vonis "di luar area" dari
     * satu titik tidak sepadan dengan vonis server, jadi [gateAbsenMasuk] tak
     * boleh mengunci tombol atas dasar itu.
     */
    val geofenceLengkap: Boolean = false,
    /** Cabang hasil resolve (yang memuat kita, atau terdekat) — untuk nama & verdict live. */
    val geofence: AbsensiGeofenceDto? = null,
    val distanceM: Int? = null,
    /** true = dalam radius salah satu cabang, false = di luar semua, null = belum bisa dihitung. */
    val inArea: Boolean? = null,

    val submitting: Boolean = false,
    val actionError: String? = null,

    // Izin / OFF milik user login
    val offRequests: List<OffRequestDto> = emptyList(),
    val offSubmitting: Boolean = false,
    val offError: String? = null,

) {
    /** Gerbang tombol Absen Pulang — cermin server, fail-open. */
    val gatePulang: GatePulang get() = gateAbsenPulang(todayDto)
    val hasCheckedIn: Boolean get() = today?.checkInAt != null
    val hasCheckedOut: Boolean get() = today?.checkOutAt != null
    val hasLocation: Boolean get() = lat != null && lng != null
    val rekap: AttendanceRekap get() = buildRekap(history, offRequests, todayDto?.tanggal)
    val gateMasuk: GateMasuk
        get() = gateAbsenMasuk(inArea, geofenceLengkap, geofence?.cabangNama, distanceM)
}

/**
 * Absensi karyawan — langsung ke backend `kinerja-service` via [AbsensiRepository].
 * Alur punch: ambil GPS + selfie → upload selfie → check-in/out {lat,lng,photoUrl}. Verdict
 * geofence/telat ditentukan server dan tampil di record hasil.
 */
@HiltViewModel
class AttendanceViewModel @Inject constructor(
    private val repository: AbsensiRepository,
    private val offRepository: OffRepository,
    private val deviceRepository: com.krisoft.tridjayaelektronik.data.DeviceRepository,
    authRepository: AuthRepository,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    val userName: String = authRepository.currentUserName?.trim().orEmpty().ifBlank { "Pegawai" }
    val cabang: String = authRepository.currentCabangName?.trim().orEmpty().ifBlank { "Cabang" }

    private val _uiState = MutableStateFlow(AttendanceUiState())
    val uiState: StateFlow<AttendanceUiState> = _uiState.asStateFlow()

    /** Byte selfie terkompres siap upload (terpisah dari state agar copy state ringan). */
    private var selfieBytes: ByteArray? = null

    init {
        load()
        loadOff()
        // Daftarkan FCM token supaya user menerima push saat izin/absen disetujui.
        viewModelScope.launch { deviceRepository.registerCurrentToken() }
    }

    /** Muat pengajuan izin milik user login (server otomatis scope ke diri sendiri). */
    fun loadOff() {
        viewModelScope.launch {
            (offRepository.mine() as? AuthResult.Success)?.let { res ->
                _uiState.update { it.copy(offRequests = res.data) }
            }
        }
    }

    /** Ajukan izin. [tanggal] = "yyyy-MM-dd". [onSuccess] dipanggil setelah tersimpan. */
    fun createOff(tanggal: String, alasan: String, onSuccess: () -> Unit) {
        if (_uiState.value.offSubmitting) return
        _uiState.update { it.copy(offSubmitting = true, offError = null) }
        viewModelScope.launch {
            when (val res = offRepository.create(tanggal, alasan.trim())) {
                is AuthResult.Success -> {
                    _uiState.update { it.copy(offSubmitting = false, offError = null, offRequests = listOf(res.data) + it.offRequests) }
                    onSuccess()
                }
                is AuthResult.Failure ->
                    _uiState.update { it.copy(offSubmitting = false, offError = res.message) }
            }
        }
    }

    fun clearOffError() = _uiState.update { it.copy(offError = null) }

    fun bersihkanHistoryLoadError() = _uiState.update { it.copy(historyLoadError = null) }

    fun load() {
        _uiState.update { it.copy(loading = true, loadError = null, historyLoadError = null) }
        viewModelScope.launch {
            val (todayRes, historyRes) = coroutineScope {
                val t = async { repository.today() }
                val h = async { repository.history() }
                t.await() to h.await()
            }
            val todayData = (todayRes as? AuthResult.Success)?.data
            val today = todayData?.record
            // Kompat: backend baru kirim `geofences[]` (SELURUH cabang); versi lama
            // kirim `geofence` tunggal (cabang sendiri). Bedanya bukan kosmetik —
            // hanya daftar lengkap yang boleh dipakai MENGUNCI tombol Check In,
            // lihat [AttendanceUiState.geofenceLengkap] dan [gateAbsenMasuk].
            val geofences = todayData?.let { it.geofences.ifEmpty { listOfNotNull(it.geofence) } }
            val geofenceLengkap = todayData?.geofences?.isNotEmpty() == true
            val history = (historyRes as? AuthResult.Success)?.data
            val error = when {
                todayRes is AuthResult.Failure && historyRes is AuthResult.Failure -> todayRes.message
                else -> null
            }
            // Kegagalan SEBAGIAN (history gagal, today sukses) sengaja dipisah
            // dari `error` di atas — `error` menggerbangi layar KOSONG penuh
            // (lihat `!adaData` di AttendanceScreen), jadi tak boleh terpicu
            // hanya karena riwayat gagal padahal kartu "hari ini" sudah tampil.
            // Tanpa field terpisah ini, kegagalan sebagian itu tak pernah
            // terlihat sama sekali — riwayat lama terus tertampil diam-diam.
            val historyError = (historyRes as? AuthResult.Failure)?.message
            _uiState.update {
                withArea(
                    it.copy(
                        loading = false,
                        today = today ?: it.today,
                        // Gagal = `null` (bukan nilai lama): status tadi bisa
                        // sudah tak berlaku, dan menahan gerbang atas data basi
                        // persis yang dilarang doc [gateAbsenPulang].
                        todayDto = todayData,
                        history = history ?: it.history,
                        loadError = error,
                        historyLoadError = historyError,
                        geofences = geofences ?: it.geofences,
                        // Ikut nasib `geofences`: kalau daftarnya tak diperbarui
                        // (panggilan gagal), status "lengkap"-nya juga tak boleh
                        // berubah — kalau tidak, satu muat ulang yang gagal bisa
                        // mengunci tombol atas daftar cabang yang lama.
                        geofenceLengkap = if (geofences != null) geofenceLengkap else it.geofenceLengkap
                    )
                )
            }
        }
    }

    fun refreshLocation() {
        if (_uiState.value.locating) return
        _uiState.update { it.copy(locating = true, locationError = null, locationDenied = false) }
        viewModelScope.launch {
            if (!LocationProvider.hasPermission(appContext)) {
                _uiState.update { it.copy(locating = false, locationDenied = true) }
                return@launch
            }
            val loc = LocationProvider.current(appContext)
            if (loc == null) {
                _uiState.update {
                    it.copy(locating = false, locationError = "Tidak bisa mendapatkan lokasi. Pastikan GPS aktif.")
                }
            } else {
                _uiState.update { withArea(it.copy(locating = false, lat = loc.latitude, lng = loc.longitude, locationError = null)) }
            }
        }
    }

    fun onLocationPermissionDenied() {
        _uiState.update { it.copy(locating = false, locationDenied = true) }
    }

    /** Baca foto full-res dari kamera, kompres + cap watermark geotag/jam, simpan byte upload + preview. */
    fun onSelfieCaptured(file: File) {
        val lat = _uiState.value.lat
        val lng = _uiState.value.lng
        viewModelScope.launch {
            val prepared = withContext(Dispatchers.Default) {
                PhotoWatermark.prepareWatermarkedJpeg(file, lat, lng, "TRIDJAYA · ABSEN", "$userName · $cabang")
            }
            if (prepared == null) {
                _uiState.update { it.copy(actionError = "Gagal memproses foto selfie") }
            } else {
                selfieBytes = prepared.first
                _uiState.update { it.copy(selfie = prepared.second, actionError = null) }
            }
        }
    }

    fun clearSelfie() {
        selfieBytes = null
        _uiState.update { it.copy(selfie = null) }
    }

    /**
     * Verdict live: karyawan boleh absen di cabang manapun, jadi hitung jarak ke SEMUA cabang.
     * Jika berada dalam radius salah satu cabang → dalam area (pilih yang terdekat di antara yang
     * memuat); jika tidak → tampilkan cabang terdekat + "perlu review". Sinkron dengan `evaluate_punch`
     * server yang juga mengevaluasi terhadap seluruh cabang.
     */
    private fun withArea(state: AttendanceUiState): AttendanceUiState {
        val lat = state.lat
        val lng = state.lng
        if (state.geofences.isEmpty() || lat == null || lng == null) {
            return state.copy(geofence = null, distanceM = null, inArea = null)
        }
        val out = FloatArray(1)
        var nearest: AbsensiGeofenceDto? = null
        var nearestDist = Int.MAX_VALUE
        var inside: AbsensiGeofenceDto? = null
        var insideDist = Int.MAX_VALUE
        for (g in state.geofences) {
            android.location.Location.distanceBetween(lat, lng, g.latitude, g.longitude, out)
            val d = out[0].toInt()
            if (d < nearestDist) { nearestDist = d; nearest = g }
            if (d <= g.radiusM && d < insideDist) { insideDist = d; inside = g }
        }
        return if (inside != null) {
            state.copy(geofence = inside, distanceM = insideDist, inArea = true)
        } else {
            state.copy(geofence = nearest, distanceM = nearestDist, inArea = false)
        }
    }

    fun checkIn() = punch(isCheckIn = true)
    fun checkOut() = punch(isCheckIn = false)

    private fun punch(isCheckIn: Boolean) {
        val state = _uiState.value
        val lat = state.lat ?: return
        val lng = state.lng ?: return
        val bytes = selfieBytes ?: return
        if (state.submitting) return
        _uiState.update { it.copy(submitting = true, actionError = null) }
        viewModelScope.launch {
            val filename = "selfie_${System.currentTimeMillis()}.webp"
            when (val upload = repository.uploadPhoto(bytes, filename)) {
                is AuthResult.Failure ->
                    _uiState.update { it.copy(submitting = false, actionError = upload.message) }
                is AuthResult.Success -> {
                    val result = if (isCheckIn) repository.checkIn(lat, lng, upload.data)
                    else repository.checkOut(lat, lng, upload.data)
                    when (result) {
                        is AuthResult.Failure ->
                            _uiState.update { it.copy(submitting = false, actionError = result.message) }
                        is AuthResult.Success -> {
                            selfieBytes = null
                            _uiState.update {
                                it.copy(submitting = false, today = result.data, selfie = null, actionError = null)
                            }
                            reloadHistory()
                        }
                    }
                }
            }
        }
    }

    private fun reloadHistory() {
        viewModelScope.launch {
            (repository.history() as? AuthResult.Success)?.let { res ->
                _uiState.update { it.copy(history = res.data) }
            }
        }
    }

}
