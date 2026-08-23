package com.krisoft.tridjayaelektronik.ui.update

import com.krisoft.tridjayaelektronik.data.update.UpdateStatus

/**
 * Aturan KAPAN memeriksa pembaruan dan KAPAN promptnya boleh tampil.
 *
 * Fungsi-fungsi di sini MURNI supaya bisa diuji tanpa Android/Hilt/coroutines-test (repo ini
 * hanya punya JUnit4) — pola yang sama dengan `unduhanUtuh` di `UpdateManager.kt` dan
 * `updateCheckMessage` di `SettingsViewModel.kt`.
 *
 * **Kenapa ada.** Sebelum ini `UpdateViewModel.init` memanggil `check()` SEKALI dan tak pernah
 * lagi; ViewModel-nya hidup selama Activity hidup (`hiltViewModel()` di `TridjayaNavHost`, yang
 * dikomposisi langsung di `setContent`), jadi praktis satu pemeriksaan per pembukaan aplikasi.
 * Aplikasi kerja yang dibuka pagi lalu dibiarkan hidup seharian tak pernah bertanya lagi.
 * Pengukuran produksi 2026-08-15 yang jadi dasar perubahan ini: dari ~106 orang di armada, hanya
 * 10 yang sampai di versionCode 85 (rilis 84 naik 08:10, 85 naik 09:00) — sementara orang yang
 * me-restart aplikasinya di hari yang sama terekam melompat 81 → 85.
 */

/**
 * Jeda minimum antar pemeriksaan otomatis setelah pemeriksaan yang BERHASIL.
 *
 * Kenapa 30 menit:
 * - **Batas atas trafik yang bisa dihitung.** Pemicunya ON_RESUME, dan di aplikasi kerja itu
 *   sering (buka kamera untuk selfie absen, buka WhatsApp, kunci-buka layar) — tanpa ambang,
 *   satu orang bisa memicu puluhan permintaan per jam. Dengan lantai 30 menit, laju satu
 *   perangkat paling banyak 2 permintaan/jam, jadi armada ~106 perangkat × 2 × 12 jam kerja =
 *   2.544 permintaan/hari sebagai PLAFON (≈ 212 per jam, ≈ 3,5 per menit se-armada) untuk satu
 *   endpoint metadata JSON kecil.
 * - **Cukup cepat untuk masalah yang sedang diperbaiki.** Keterlambatan terburuk 30 menit itu
 *   kecil dibanding keadaan sekarang, yaitu "tidak pernah sampai pengguna me-restart aplikasi"
 *   (terukur: satu hari kerja penuh tertinggal dua rilis).
 * - Angka yang jauh lebih kecil (mis. 1–5 menit) tidak menambah kegunaan apa pun — rilis tidak
 *   terbit tiap menit — tapi mengalikan trafik dengan jumlah perangkat.
 */
internal const val JEDA_CEK_SUKSES_MS: Long = 30 * 60 * 1000L

/**
 * Jeda minimum sesudah pemeriksaan yang GAGAL ([UpdateStatus.Unknown]).
 *
 * Kenapa lebih pendek (5 menit): `UpdateManager.check()` mengembalikan `Unknown` saat responsnya
 * tidak sukses (401/403/5xx) ATAU saat melempar (offline/timeout/TLS/gagal parse) — lihat
 * `check()`; jadi `Unknown` berarti "belum tahu", bukan "sudah terbaru". Menunggu 30 menit penuh
 * untuk sesuatu yang belum pernah terjawab akan menghukum justru perangkat yang paling sering
 * kehilangan sinyal — persis petugas lapangan yang paling tertinggal versinya. Ongkos terburuknya
 * tetap terbatas: 12 permintaan/jam/perangkat, dan kegagalan karena offline tak pernah sampai ke
 * server sama sekali.
 */
internal const val JEDA_CEK_GAGAL_MS: Long = 5 * 60 * 1000L

/**
 * Apakah pemeriksaan otomatis (dipicu app kembali ke foreground) boleh jalan sekarang.
 *
 * [percobaanTerakhirAt] adalah waktu pemeriksaan terakhir DIMULAI, bukan selesai — kalau dicatat
 * saat selesai, ON_RESUME yang datang selagi permintaan masih terbang lolos dari ambang dan
 * mengirim permintaan kedua.
 *
 * Waktunya diharapkan monotonik (pemanggilnya memakai `SystemClock.elapsedRealtime()`), jadi
 * tidak ada penanganan waktu mundur di sini: jam dinding yang dikoreksi NTP/manual bisa melompat
 * ke belakang dan mengunci pemeriksaan berikutnya selama selisihnya.
 */
internal fun bolehCekOtomatis(
    percobaanTerakhirAt: Long?,
    percobaanTerakhirGagal: Boolean,
    now: Long,
): Boolean {
    if (percobaanTerakhirAt == null) return true
    val jeda = if (percobaanTerakhirGagal) JEDA_CEK_GAGAL_MS else JEDA_CEK_SUKSES_MS
    return now - percobaanTerakhirAt >= jeda
}

/**
 * Status yang dipakai setelah satu pemeriksaan selesai.
 *
 * **Kegagalan TIDAK menghapus temuan yang sudah ada.** Pemeriksaan pertama bisa menemukan versi
 * baru, lalu pemeriksaan foreground berikutnya gagal karena sinyal hilang; kalau `Unknown`
 * ditimpakan begitu saja, promptnya lenyap dari layar tanpa pengguna menutupnya dan tanpa satu
 * pun pesan — kegagalan jaringan biasa jadi membatalkan pemberitahuan yang sudah benar.
 * `Unknown` = "belum tahu", jadi yang benar adalah mempertahankan apa yang terakhir diketahui.
 *
 * Hasil `UpToDate` TETAP menimpa: itu jawaban tegas dari server (mis. rilisnya ditarik kembali),
 * bukan ketidaktahuan.
 */
internal fun statusSetelahCek(sebelum: UpdateStatus, hasil: UpdateStatus): UpdateStatus =
    if (hasil is UpdateStatus.Unknown) sebelum else hasil

/**
 * Apakah prompt pembaruan boleh tampil, mengingat versi mana yang promptnya sudah ditutup.
 *
 * [versiDitutup] = `latestVersionCode` yang ada di layar saat pengguna menekan "Nanti".
 * Sebelumnya penandanya cuma Boolean per-sesi, jadi menutup prompt versi 84 ikut menelan prompt
 * versi 85 yang terbit sesudahnya dalam sesi yang sama — dan sesi di sini berarti seumur Activity,
 * bukan sekejap.
 *
 * Pembaruan WAJIB tak pernah bisa ditutup: `UpdateDialog` memang tak memberi tombol "Nanti" saat
 * `force`, tapi aturannya ditegakkan di sini juga supaya penanda basi dari prompt opsional
 * sebelumnya tak bisa menyembunyikannya.
 */
internal fun bolehTampilkanPrompt(
    available: UpdateStatus.Available,
    versiDitutup: Long?,
    /**
     * Versi yang promptnya ditunda SEMENTARA (Back / ketuk di luar dialog).
     * Dibersihkan tiap pemeriksaan yang selesai, jadi paling lama satu jeda
     * [JEDA_CEK_SUKSES_MS] — bukan seumur Activity seperti [versiDitutup].
     *
     * Dipisah dari [versiDitutup] karena keduanya menyatakan niat yang berbeda:
     * menekan "Nanti" adalah keputusan, sedangkan ketukan meleset di luar kotak
     * dialog bukan. Sebelum pemisahan ini keduanya mengunci sama kerasnya, dan
     * satu ketukan tak sengaja bisa menyembunyikan pembaruan berhari-hari.
     */
    versiDitundaSementara: Long? = null,
): Boolean {
    if (available.force) return true
    if (versiDitundaSementara == available.latestVersionCode) return false
    return versiDitutup == null || available.latestVersionCode > versiDitutup
}

/**
 * Apakah berkas/keadaan unduhan yang tersimpan sudah menunjuk versi yang salah.
 *
 * Muncul karena pemeriksaan kini berulang: unduhan versi 84 bisa sudah selesai
 * (`UpdateDownloadState.ReadyToInstall`) ketika pemeriksaan berikutnya menemukan 85. Tanpa
 * pembuangan ini, `UpdateViewModel.startDownload()` melihat `ReadyToInstall` dan memanggil ulang
 * installer untuk **berkas 84** — pengguna memasang versi yang sudah kedaluwarsa dan mengira
 * sudah selesai.
 *
 * Unduhan yang sedang BERJALAN ([sedangMengunduh]) sengaja dibiarkan: pembatalan di tengah aliran
 * tidak dipasang di `downloadApk`, jadi menyentuh keadaannya di sini hanya akan berselisih dengan
 * coroutine yang masih menulis. Akibat yang diterima: unduhan 84 yang menyusul selesai tetap
 * menawarkan 84, dan 85 baru terkejar pada pemeriksaan sesudahnya.
 *
 * Parameternya `Boolean`, bukan `UpdateDownloadState`, supaya tetap bisa diuji: varian
 * `ReadyToInstall` membawa `android.net.Uri`, dan kelas itu melempar `RuntimeException("Stub!")`
 * di unit test JVM (repo ini tanpa Robolectric/mock — lihat `testImplementation` di
 * `app/build.gradle.kts`).
 */
internal fun unduhanBasi(
    unduhanUntukVersi: Long?,
    sedangMengunduh: Boolean,
    versiTerbaru: Long?,
): Boolean =
    unduhanUntukVersi != null &&
        versiTerbaru != null &&
        versiTerbaru != unduhanUntukVersi &&
        !sedangMengunduh
