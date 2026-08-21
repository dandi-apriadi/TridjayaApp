package com.krisoft.tridjayaelektronik.data

/**
 * **Penomor GENERASI sesi — supaya LOGOUT LENGKET.**
 *
 * Masalah yang ditutup, urutannya nyata dan tidak jarang:
 *
 *  1. sebuah request kena 401 (atau lewat ambang refresh proaktif), jadi
 *     `TokenRefresher.refresh()` (`data/remote/NetworkModule.kt`) membaca
 *     `refreshToken` lalu menembak `POST /auth/refresh` — panggilan BLOCKING
 *     di thread OkHttp, sampai 8 detik;
 *  2. selagi itu orangnya menekan **Logout**: `AuthRepository.logout()`
 *     memanggil [TokenStore.clear], mirror dikosongkan dan `PersistedSession()`
 *     kosong dijadwalkan ke DataStore;
 *  3. refresh-nya SUKSES dan menulis balik token + profil lewat
 *     `TokenStore.updateSession(...)`.
 *
 * Hasilnya sesi HIDUP LAGI: mirror terisi, `sessionState` kembali `true`, dan —
 * yang paling merugikan — DataStore ikut tertulis, jadi cold start berikutnya
 * mendapati orangnya MASIH LOGIN padahal ia sudah menekan Logout. Di HP bersama
 * (kasus lazim di cabang) itu berarti akun orang lain terbuka.
 *
 * Server tidak menangkapnya: `POST /auth/logout` mencabut refresh token yang
 * DIKIRIM app, sementara refresh di langkah (1) sudah merotasinya — pencabutan
 * atas token yang sudah dirotasi dijawab `Ok(())` diam-diam
 * (`services/auth-service/src/sessions/service.rs`, Tridjaya-Web). Jadi token
 * BARU hasil langkah (3) tetap sah sampai kedaluwarsa. Klien harus yang
 * membuangnya.
 *
 * **Cara kerjanya.** Tiap sesi punya nomor generasi. [TokenStore.clear]
 * MENAIKKAN nomor itu; penulis yang lahir sebelum logout memegang nomor LAMA,
 * dan penulisannya ditolak. Bukan "cek lalu tulis" biasa:
 * pemeriksaan dan penulisannya berada di dalam KUNCI YANG SAMA dengan kenaikan
 * nomornya ([jalankanBila] vs [akhiri]), jadi tak ada celah di antara keduanya.
 * Tanpa itu penulis bisa lolos pemeriksaan, lalu logout berjalan lengkap, lalu
 * penulis menimpa mirror yang baru dikosongkan — persis bug yang sama dengan
 * jendela beberapa mikrodetik saja.
 *
 * **Kenapa `synchronized`, bukan penguncian yang menahan.** Pembaca sinkron
 * [TokenStore] (`accessToken`, `refreshToken`, `isLoggedIn`) dipanggil dari
 * thread OkHttp yang TIDAK BISA suspend, jadi tak boleh ada `Mutex` coroutine
 * di jalur itu. Monitor di sini hanya melingkupi penetapan field di memori:
 * nol I/O, nol suspensi, nol panggilan jaringan. Yang paling "berat" di
 * dalamnya adalah `scope.launch { … }` — sekadar menaruh tugas di antrean.
 *
 * **Satu aturan untuk pemakai.** Jangan panggil balik ke [TokenStore] dari
 * kolektor `sessionState` dengan cara yang MENUNGGU thread lain: kunci ini
 * ikut dipegang saat `sessionState` diterbitkan. Reentransi dari thread yang
 * sama aman (monitor Java re-entrant); yang berbahaya cuma menunggu pihak lain.
 */
internal class GenerasiSesi {

    private val kunci = Any()

    /** Dijaga [kunci]. Hanya NAIK, tak pernah dipakai ulang. */
    private var nomor: Long = 0L

    /** Generasi yang sedang berjalan — ambil SEBELUM memulai kerja panjang. */
    fun sekarang(): Long = synchronized(kunci) { nomor }

    /** [aksi] berjalan atomik terhadap [akhiri], menerima generasi berjalan. */
    fun <T> denganGenerasi(aksi: (Long) -> T): T = synchronized(kunci) { aksi(nomor) }

    /**
     * [aksi] berjalan atomik terhadap [akhiri], **tanpa** nomor generasinya.
     *
     * Dipakai kolektor `dataStore.data` di [TokenStore]. Emisi disk tak "lahir"
     * dari sebuah generasi — ia tak punya tanda untuk dibandingkan — jadi
     * [jalankanBila] tak berlaku di sana. Yang ia butuhkan cuma jaminan yang
     * sama: **pemeriksaan** [bolehPasangDariDisk] (yang membaca mirror) dan
     * **penulisan** mirror yang menyusulnya tak boleh disela [akhiri]. Tanpa
     * kunci ini emisi bisa lolos pemeriksaan, lalu logout berjalan lengkap,
     * lalu emisinya menimpa mirror yang baru dikosongkan — persis jendela yang
     * ditutup [jalankanBila] di jalur penulisan, cuma lewat pintu lain.
     *
     * [aksi] tunduk pada aturan yang sama dengan pemakai kunci lainnya: nol
     * I/O, nol suspensi, dan jangan menunggu thread lain.
     */
    fun <T> terkunci(aksi: () -> T): T = synchronized(kunci) { aksi() }

    /**
     * Jalankan [aksi] HANYA bila [tanda] masih generasi berjalan.
     *
     * @return `true` bila [aksi] benar-benar dijalankan; `false` berarti sesi
     *   sudah diakhiri sesudah [tanda] diambil, jadi penulisannya DIBUANG.
     */
    fun jalankanBila(tanda: Long, aksi: (Long) -> Unit): Boolean = synchronized(kunci) {
        if (tanda != nomor) return@synchronized false
        aksi(nomor)
        true
    }

    /**
     * Akhiri generasi berjalan. [aksi] menerima generasi BARU dan berjalan di
     * dalam kunci yang sama, jadi tak ada penulis lama yang bisa menyelinap di
     * antara kenaikan nomor dan pembersihan keadaannya.
     */
    fun <T> akhiri(aksi: (Long) -> T): T = synchronized(kunci) {
        nomor += 1
        aksi(nomor)
    }

    /** Apakah [tanda] masih generasi berjalan (dipakai penulis DataStore). */
    fun masihBerlaku(tanda: Long): Boolean = synchronized(kunci) { tanda == nomor }
}

/**
 * Bolehkah emisi DataStore [emisi] dipasang ke mirror memori?
 *
 * **Separuh kedua dari logout-lengket, dan ia menutup jendela yang berbeda dari
 * [GenerasiSesi].** Penjaga generasi menolak penulisan yang lahir sebelum
 * `clear()`. Yang tersisa: penulisan yang SAH — sudah commit ke disk sebelum
 * `clear()` — tapi EMISI-nya baru sampai ke kolektor `dataStore.data` sesudahnya
 * (kolektornya coroutine di `Dispatchers.IO`, jadi pengirimannya asinkron).
 * Emisi itu membawa sesi yang lengkap, dan `cache = s` di kolektor akan
 * menghidupkan lagi mirror yang baru dikosongkan berikut `sessionState = true`.
 * Layar akan berkedip balik ke Beranda sesudah orangnya menekan Logout.
 *
 * Aturannya satu kalimat: **emisi disk tak boleh MENGHIDUPKAN sesi ke dalam
 * mirror yang sudah kosong.** `cache` selalu niat TERAKHIR proses ini (tiap
 * penulis menyetelnya sinkron sebelum menjadwalkan penyimpanan), jadi "disk
 * bilang ada sesi, mirror bilang tidak" hanya bisa berarti emisi basi —
 * penulisan `clear()` sendiri menyusul beberapa milidetik kemudian.
 *
 * @param sudahTermuat `TokenStore.loaded`. WAJIB ikut: sebelum `load()` selesai,
 *   mirror memang masih kosong dan emisi berisi sesi justru yang ditunggu —
 *   itulah sesi tersimpan yang baru dibaca dari disk (dan jalur migrasi legacy
 *   yang menulis lewat DataStore langsung). Tanpa syarat ini, penjaga ini
 *   memblokir pemulihan sesi saat app dibuka — kegagalan yang jauh lebih parah
 *   daripada yang sedang ditutup: semua orang jadi harus login tiap pagi.
 */
internal fun bolehPasangDariDisk(
    emisi: PersistedSession,
    cermin: PersistedSession,
    sudahTermuat: Boolean,
): Boolean {
    if (!sudahTermuat) return true
    val emisiBersesi = emisi.accessToken.isNotBlank()
    val cerminKosong = cermin.accessToken.isBlank()
    return !(emisiBersesi && cerminKosong)
}
