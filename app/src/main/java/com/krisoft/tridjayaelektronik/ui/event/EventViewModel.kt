package com.krisoft.tridjayaelektronik.ui.event

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krisoft.tridjayaelektronik.data.AuthResult
import com.krisoft.tridjayaelektronik.data.EventRepository
import com.krisoft.tridjayaelektronik.data.model.EventDto
import com.krisoft.tridjayaelektronik.ui.aktivitas.pesanGagalDekode
import com.krisoft.tridjayaelektronik.util.ImagePixelPipeline
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

/** KTP harus tetap TERBACA — server pun menyimpannya pada 2400px/q88, jadi jangan turunkan lagi. */
private const val KTP_MAX_DIMENSION = 2000
private const val KTP_MAX_BYTES = 1_500_000L

// WebP, bukan lagi JPEG (2026-08-29) — `event::foto_ktp_sah` server SUDAH menerima `.webp`
// (`upload_ktp` memvalidasi lewat magic byte + SELALU re-encode ke webp sendiri, mengabaikan
// bytes/filename/content-type klien), jadi migrasi ini murni mengecilkan payload upload, nol
// risiko kompat — dikonfirmasi baca `services/kinerja-service/src/event/upload.rs` langsung.
@Suppress("DEPRECATION")
private val KTP_PARAMS = ImagePixelPipeline.Params(
    maxDimension = KTP_MAX_DIMENSION,
    format = Bitmap.CompressFormat.WEBP,
    startQuality = 88,
    minQuality = 50,
    step = 12,
    maxBytes = KTP_MAX_BYTES,
)

data class EventUiState(
    val loading: Boolean = true,
    val events: List<EventDto> = emptyList(),
    /** Vonis server (roster HR), bukan tebakan role di klien. */
    val bolehIsi: Boolean = false,
    val form: EventLeadForm = EventLeadForm(),
    /** Pratinjau KTP dari bytes yang BARU diunggah — tak perlu unduh ulang apa yang baru dikirim. */
    val ktpPratinjau: Bitmap? = null,
    val mengunggahKtp: Boolean = false,
    val mengirim: Boolean = false,
    val pesanError: String? = null,
    val pesanSukses: String? = null,
) {
    /**
     * Event yang layak menggantikan kartu sapaan di layar Operasional. Kosong = tampilkan
     * sapaan biasa; itu jawaban untuk SEMUA keadaan gagal sekaligus (jaringan mati, respons
     * tak terbaca, bukan sales, tak ada event) — layar utama tak boleh mati karena fitur ini.
     */
    val kartuEvent: List<EventDto> get() = if (bolehIsi) events else emptyList()

    val bolehSimpan: Boolean get() = adaIsian(form) && !mengirim && !mengunggahKtp
}

/**
 * Event lapangan: daftar event aktif (kartu di layar Operasional) + formulir prospek.
 *
 * SATU ViewModel untuk dua layar — sumbernya satu repository dan satu domain, dan tiap
 * layar mendapat instance sendiri lewat NavBackStackEntry, jadi form di layar isi tak
 * pernah bocor ke kartu di Home. VM kedua hanya menggandakan wiring Hilt.
 */
@HiltViewModel
class EventViewModel @Inject constructor(
    private val repository: EventRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(EventUiState())
    val uiState: StateFlow<EventUiState> = _uiState.asStateFlow()

    /**
     * SENGAJA tanpa `init { muat() }`. VM ini ter-scope pada `NavBackStackEntry` yang tak
     * pernah di-pop (start destination tab Operasional), jadi `init` hanya berjalan SEKALI
     * seumur proses: wifi bazar putus saat app dibuka = kartu sapaan biasa SELAMANYA, dan
     * spec §7.3 sengaja meniadakan tile Akses Cepat sehingga tak ada pintu masuk lain —
     * sales harus force-close app tanpa satu pun petunjuk. Event yang baru dibuka manajemen
     * setelah app hidup juga tak akan pernah muncul.
     *
     * Pemanggilnya `LaunchedEffect(Unit)` di layar (pola `notifViewModel.refreshUnreadCount()`
     * di `HomeScreen`): NavHost hanya menyusun destinasi yang sedang tampil, jadi HomeScreen
     * KELUAR komposisi saat pindah ke route anak dan masuk lagi saat di-pop; `DestinationContent`
     * (MainActivity) juga `when`-per-tab, jadi pindah tab pun mengeluarkannya dari komposisi
     * sementara back stack-nya tetap hidup di `summaryNav` yang di-remember di luar. Dua-duanya
     * berarti effect-nya berjalan lagi — dan VM-nya yang bertahan itu justru yang membuat
     * `init` tak cukup.
     */
    fun muat() {
        _uiState.update { it.copy(loading = true) }
        viewModelScope.launch {
            when (val res = repository.daftar()) {
                is AuthResult.Success -> _uiState.update {
                    it.copy(
                        loading = false,
                        events = res.data.events.filter { e -> e.aktif },
                        bolehIsi = res.data.bolehIsi,
                        pesanError = null,
                    )
                }
                // Gagal = TIDAK ada event, bukan layar error: pemanggil terbesar fungsi ini
                // adalah kartu di layar Operasional, yang harus jatuh ke sapaan biasa.
                // `pesanError` tetap diisi untuk layar isi prospek, yang memang menampilkannya.
                is AuthResult.Failure -> _uiState.update {
                    it.copy(loading = false, events = emptyList(), bolehIsi = false, pesanError = res.message)
                }
            }
        }
    }

    fun ubahForm(ubah: (EventLeadForm) -> EventLeadForm) {
        _uiState.update { it.copy(form = ubah(it.form), pesanError = null, pesanSukses = null) }
    }

    fun bersihkanPesan() = _uiState.update { it.copy(pesanError = null, pesanSukses = null) }

    /**
     * Kegagalan yang terjadi DI LAYAR, sebelum bytes sampai ke sini (mis. berkas galeri
     * tak terbaca). Lewat channel pesan yang sama dengan gagal unggah — dua tempat pesan
     * untuk satu kejadian hanya membuat salah satunya lupa diisi.
     */
    fun laporError(pesan: String) =
        _uiState.update { it.copy(pesanError = pesan, pesanSukses = null) }

    /**
     * [file] = berkas cache hasil kamera ATAU salinan dari galeri (satu jalur, lihat layar).
     * Dikompres dulu di HP: server menolak >5MB, dan foto kamera full-res HP baru rutin
     * melewatinya — sales di lapangan cuma akan melihat "gagal unggah" tanpa sebab.
     */
    fun unggahKtp(file: File, dariGaleri: Boolean = false) {
        if (_uiState.value.mengunggahKtp) return
        _uiState.update { it.copy(mengunggahKtp = true, pesanError = null, pesanSukses = null) }
        viewModelScope.launch {
            // Berkas cache DIHAPUS begitu bytes-nya terbaca — apa pun hasil unggahnya.
            // Isinya KTP: nama, NIK, alamat, wajah. Satu bazar = puluhan kali masuk layar
            // ini, dan cache Android tak punya masa kedaluwarsa yang bisa diandalkan.
            // Kamera/galeri membuat ulang berkasnya pada pemilihan berikutnya (FileProvider
            // membuka mode "w" = CREATE), jadi menghapus di sini tidak mematikan tombolnya.
            val siap = siapkanKtpJpeg(file).also { file.delete() }
            if (siap == null) {
                // "Coba ambil ulang" hanya benar untuk KAMERA. Foto galeri yang
                // gagal didekode di HP Android 7/8 (HEIC dari iPhone, masuk
                // lewat Bluetooth/kartu SD — `BitmapFactory` baru bisa HEIF di
                // API 28 sedangkan minSdk 24) akan gagal lagi setiap kali,
                // jadi kalimat itu menyuruh mengulang hal yang mustahil.
                // Kalimat per-sumber dipinjam dari `pesanGagalDekode` (jalur
                // raport), bukan ditulis ulang.
                _uiState.update {
                    it.copy(mengunggahKtp = false, pesanError = pesanGagalDekode(dariGaleri))
                }
                return@launch
            }
            val (bytes, bitmap) = siap
            when (val res = repository.unggahKtp(bytes, "ktp_${System.currentTimeMillis()}.webp")) {
                is AuthResult.Failure ->
                    _uiState.update { it.copy(mengunggahKtp = false, pesanError = res.message) }
                is AuthResult.Success -> _uiState.update {
                    it.copy(
                        mengunggahKtp = false,
                        form = it.form.copy(fotoKtpUrl = res.data),
                        ktpPratinjau = bitmap,
                    )
                }
            }
        }
    }

    fun hapusKtp() {
        _uiState.update { it.copy(form = it.form.copy(fotoKtpUrl = ""), ktpPratinjau = null) }
    }

    /** Sukses → form dikosongkan dan sales TETAP di layar; di stan orang mengantre. */
    fun kirim(eventId: String) {
        val state = _uiState.value
        if (state.mengirim || !state.bolehSimpan) return
        _uiState.update { it.copy(mengirim = true, pesanError = null, pesanSukses = null) }
        viewModelScope.launch {
            when (val res = repository.kirimLead(eventId, state.form.toRequest())) {
                is AuthResult.Failure ->
                    _uiState.update { it.copy(mengirim = false, pesanError = res.message) }
                is AuthResult.Success -> _uiState.update {
                    it.copy(
                        mengirim = false,
                        form = EventLeadForm(),
                        ktpPratinjau = null,
                        pesanSukses = "Prospek tersimpan. Lanjut ke konsumen berikutnya.",
                    )
                }
            }
        }
    }
}

/**
 * Downscale ke [KTP_MAX_DIMENSION], bakar rotasi EXIF ke piksel, lalu WebP-kompres di bawah
 * [KTP_MAX_BYTES]. `null` = berkas rusak/tak terbaca. Nama fungsi TETAP "Jpeg" walau isinya kini
 * WebP — lihat komentar [KTP_PARAMS]. Mekanismenya kini lewat `util.ImagePixelPipeline`, dipakai
 * bersama `PhotoWatermark`/`IndentCreateViewModel`/`AddLeadViewModel` (rotasi EXIF WAJIB dibakar
 * di sana: re-encode membuang EXIF, dan KTP yang tersimpan miring 90° praktis tak terbaca
 * petugas yang memeriksanya nanti). Selalu return hasil apa adanya kalau masih di atas
 * [KTP_MAX_BYTES] di kualitas terendah (fail-soft, BEDA dari `AddLeadViewModel.siapkanJpeg` yang
 * menolak — KTP best-effort, tak pernah `null` kecuali gagal didekode).
 *
 * `suspend` + `withContext(Dispatchers.Default)` membungkus SELURUH badan (termasuk baca berkas)
 * supaya `ImagePixelPipeline.compress` dipanggil di fungsi yang sama dengan pemindahan
 * dispatcher-nya — dijaga `ImagePixelPipelineGuardTest`. `unggahKtp` dulu membungkus PANGGILAN ke
 * fungsi ini dengan `withContext(Dispatchers.Default) { siapkanKtpJpeg(file)... }`; utasnya sama
 * persis, cuma baris `withContext`-nya kini di dalam.
 */
private suspend fun siapkanKtpJpeg(file: File): Pair<ByteArray, Bitmap>? = withContext(Dispatchers.Default) {
    val raw = runCatching { file.readBytes() }.getOrNull() ?: return@withContext null
    if (raw.isEmpty()) return@withContext null
    ImagePixelPipeline.compress(raw, KTP_PARAMS)
}
