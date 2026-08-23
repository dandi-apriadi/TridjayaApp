package com.krisoft.tridjayaelektronik.ui.update

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krisoft.tridjayaelektronik.data.update.UpdateDownloadState
import com.krisoft.tridjayaelektronik.data.update.UpdateManager
import com.krisoft.tridjayaelektronik.data.update.UpdateStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Checks for an app update at startup **and every time the app returns to the foreground**; the
 *  root UI gates a force update on the result. Mandatory updates auto-download the moment they're
 *  detected — optional ones wait for [startDownload] (the dialog's "Perbarui Sekarang" tap).
 *
 *  Pemeriksaan berulang itu inti perubahan ini: ViewModel ini hidup selama Activity hidup, jadi
 *  satu pemeriksaan di `init` berarti satu pemeriksaan per pembukaan aplikasi, dan aplikasi kerja
 *  yang dibiarkan hidup seharian tak pernah bertanya lagi. Pemicunya ON_RESUME dari lifecycle
 *  Activity (dipasang di `TridjayaNavHost`) — bukan timer, bukan polling. Ambang jeda,
 *  penanganan kegagalan, dan penutupan prompt per versi ada di `UpdatePrompt.kt` sebagai fungsi
 *  murni; kelas ini cuma menyimpan keadaan dan memanggilnya. */
@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val updateManager: UpdateManager
) : ViewModel() {

    private val _status = MutableStateFlow<UpdateStatus>(UpdateStatus.Unknown)
    val status: StateFlow<UpdateStatus> = _status.asStateFlow()

    /** `latestVersionCode` yang promptnya sudah ditutup pengguna; `null` = belum ada yang ditutup.
     *  Versi yang lebih baru dari ini WAJIB memunculkan prompt lagi — lihat
     *  [bolehTampilkanPrompt], yang dipakai pemanggil (root UI) untuk memutuskan. */
    private val _versiPromptDitutup = MutableStateFlow<Long?>(null)
    val versiPromptDitutup: StateFlow<Long?> = _versiPromptDitutup.asStateFlow()

    /**
     * Versi yang promptnya ditunda SEMENTARA lewat Back / ketuk di luar dialog.
     * Dibersihkan tiap pemeriksaan yang selesai, jadi umurnya paling lama satu
     * [JEDA_CEK_SUKSES_MS] — berbeda dari [_versiPromptDitutup] yang bertahan
     * seumur Activity.
     */
    private val _versiDitundaSementara = MutableStateFlow<Long?>(null)
    val versiDitundaSementara: StateFlow<Long?> = _versiDitundaSementara.asStateFlow()

    private val _download = MutableStateFlow<UpdateDownloadState>(UpdateDownloadState.Idle)
    val download: StateFlow<UpdateDownloadState> = _download.asStateFlow()

    /** Versi yang keadaan [_download] saat ini mewakilinya — dipakai membuang unduhan basi
     *  ([unduhanBasi]) saat pemeriksaan berikutnya menemukan versi yang lebih baru. */
    private var unduhanUntukVersi: Long? = null

    /** Waktu monotonik ([SystemClock.elapsedRealtime]) saat pemeriksaan terakhir DIMULAI. Jam
     *  dinding sengaja tak dipakai: ia bisa melompat mundur (koreksi NTP/manual) dan mengunci
     *  pemeriksaan berikutnya selama selisihnya. */
    private var cekTerakhirAt: Long? = null
    private var cekTerakhirGagal = false
    private var cekBerjalan = false

    init {
        // Pemeriksaan pertama selalu jalan, tanpa ambang.
        jalankanCek(paksa = true)
    }

    /** Dipanggil saat aplikasi kembali ke foreground (ON_RESUME Activity). Sengaja TIDAK
     *  memaksa: ambangnya yang memutuskan, karena ON_RESUME di aplikasi ini sering (kembali dari
     *  kamera selfie absen, dari WhatsApp, dari kunci layar). */
    fun onForeground() {
        jalankanCek(paksa = false)
    }

    private fun jalankanCek(paksa: Boolean) {
        if (cekBerjalan) return
        val now = SystemClock.elapsedRealtime()
        if (!paksa && !bolehCekOtomatis(cekTerakhirAt, cekTerakhirGagal, now)) return
        cekBerjalan = true
        // Dicatat SEBELUM permintaan terbang: kalau dicatat sesudah, ON_RESUME yang datang
        // selagi permintaan masih berjalan lolos dari ambang dan mengirim permintaan kedua.
        cekTerakhirAt = now
        viewModelScope.launch {
            val hasil = try {
                updateManager.check()
            } finally {
                cekBerjalan = false
            }
            cekTerakhirGagal = hasil is UpdateStatus.Unknown
            val baru = statusSetelahCek(_status.value, hasil)
            _status.value = baru
            // Penundaan SEMENTARA (Back / ketuk di luar) berumur satu siklus
            // pemeriksaan saja. Tanpa baris ini ia jadi permanen dan kita cuma
            // memindahkan bug-nya, bukan menutupnya. Penolakan SENGAJA lewat
            // tombol "Nanti" (`_versiPromptDitutup`) TIDAK ikut dibersihkan.
            _versiDitundaSementara.value = null

            val versiBaru = (baru as? UpdateStatus.Available)?.latestVersionCode
            if (unduhanBasi(unduhanUntukVersi, _download.value is UpdateDownloadState.Downloading, versiBaru)) {
                unduhanUntukVersi = null
                _download.value = UpdateDownloadState.Idle
            }
            // Pembaruan wajib: jangan menunggu ketukan, langsung tarik berkasnya.
            // Syarat `Idle` mencegah pemeriksaan foreground berikutnya menembakkan ULANG intent
            // installer sistem di atas pekerjaan pengguna; saat wajib, dialognya toh memblokir
            // seluruh app dan menyediakan tombol "Instal Sekarang" untuk mengulanginya manual.
            if (baru is UpdateStatus.Available && baru.force && _download.value is UpdateDownloadState.Idle) {
                startDownload()
            }
        }
    }

    /** Menutup prompt untuk versi yang SEDANG tampil saja. Penanda lama berupa Boolean per-sesi
     *  membuat penutupan versi 84 ikut menelan prompt versi 85 yang terbit sesudahnya. */
    fun dismissOptional() {
        val tampil = _status.value as? UpdateStatus.Available ?: return
        _versiPromptDitutup.value = tampil.latestVersionCode
    }

    /**
     * Menutup prompt SEMENTARA — dipanggil dari Back / ketuk di luar dialog.
     *
     * Dipisah dari [dismissOptional] karena niatnya berbeda: menekan "Nanti"
     * adalah keputusan, ketukan meleset di luar kotak dialog bukan. Sebelumnya
     * keduanya memanggil fungsi yang sama, sehingga satu ketukan tak sengaja
     * menyembunyikan pembaruan selama Activity hidup — di HP kerja yang jarang
     * ditutup, itu berhari-hari.
     */
    fun tundaSementara() {
        val tampil = _status.value as? UpdateStatus.Available ?: return
        _versiDitundaSementara.value = tampil.latestVersionCode
    }

    /** Starts (or re-fires the install prompt for) the update download. Safe to call while a
     *  download is already in flight — it's a no-op then. */
    fun startDownload() {
        val current = _download.value
        if (current is UpdateDownloadState.Downloading) return
        if (current is UpdateDownloadState.ReadyToInstall) {
            // `promptInstall` mengembalikan false kalau installer paket tak ada
            // / ditolak. Dulu ia melempar dan menutup app; sekarang diamnya
            // harus tetap terlihat, kalau tidak orang menekan tombol berkali-
            // kali tanpa satu pun tanda bahwa ada yang salah.
            if (!updateManager.promptInstall(current.fileUri)) {
                _download.value = UpdateDownloadState.Failed("HP ini tidak punya pemasang paket. Buka berkas APK-nya lewat aplikasi Berkas, atau minta bantuan admin.")
            }
            return
        }
        unduhanUntukVersi = (_status.value as? UpdateStatus.Available)?.latestVersionCode
        viewModelScope.launch {
            _download.value = UpdateDownloadState.Downloading(null)
            updateManager.downloadApk { progress -> _download.value = UpdateDownloadState.Downloading(progress) }
                .onSuccess { uri ->
                    _download.value = if (updateManager.promptInstall(uri)) {
                        UpdateDownloadState.ReadyToInstall(uri)
                    } else {
                        UpdateDownloadState.Failed("HP ini tidak punya pemasang paket. Buka berkas APK-nya lewat aplikasi Berkas, atau minta bantuan admin.")
                    }
                }
                .onFailure { error ->
                    _download.value = UpdateDownloadState.Failed(error.message ?: "Gagal mengunduh pembaruan")
                }
        }
    }
}
