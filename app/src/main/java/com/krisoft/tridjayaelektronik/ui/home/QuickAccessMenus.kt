package com.krisoft.tridjayaelektronik.ui.home

/**
 * REGISTRI menu Akses Cepat — satu-satunya tempat menyatakan SIAPA yang boleh
 * melihat sebuah menu.
 *
 * **Kenapa ada.** Berulang kali menu tampil ke role yang backend-nya menjawab
 * 403 (keluhan nyata 2026-07-27: CRM; audit yang sama menemukan Absen, Slip
 * Gaji, Klasemen, SPK), dan sebaliknya menu hilang dari orang yang sebenarnya
 * berhak karena gate cuma membaca role UTAMA. Penyebabnya struktural: tile
 * ditulis langsung sebagai `item { ... }` di dalam grid, jadi menambah menu
 * TANPA memikirkan hak akses adalah jalur termudah — dan default-nya "tampil
 * untuk semua".
 *
 * **Aturannya sekarang:** setiap menu WAJIB punya entri di [QUICK_ACCESS_MENUS]
 * dengan [QuickAccessMenu.allowedRoles] terisi. Tidak ada nilai default —
 * menambah menu tanpa menyatakan haknya tidak akan dikompilasi. Kalau memang
 * untuk semua yang login, tulis [ALL_LOGGED_IN] secara eksplisit supaya terlihat
 * bahwa itu keputusan, bukan kelalaian.
 *
 * [QuickAccessMenu.backendGuard] menyebut guard mana yang dicerminkan entri ini
 * — dipakai saat menelusuri ulang kalau backend berubah, dan diuji di
 * `MenuAccessGateTest`.
 */
internal data class QuickAccessMenu(
    /** Id stabil (dipakai HomeScreen memilih ikon/warna/aksi + dipakai test). */
    val id: String,
    val label: String,
    /** Kunci kemampuan dari `GET /api/me/capabilities` — SUMBER UTAMA sejak
     *  2026-07-27. `null` hanya untuk menu yang backend-nya memang tak ber-gate
     *  (mis. Inventory). */
    val capability: String?,
    /** Cadangan saat kemampuan server belum termuat (offline / server lama):
     *  role EFEKTIF yang boleh melihat menu ini, atau [ALL_LOGGED_IN]. Harus
     *  tetap mencerminkan guard backend. */
    val allowedRoles: Set<String>,
    /** Guard backend yang dicerminkan — sebut file/konstanta aslinya. */
    val backendGuard: String,
) {
    /**
     * `capabilities` dari server dipakai LEBIH DULU; daftar role lokal cuma
     * cadangan saat peta itu belum ada. Kunci yang absen di peta server
     * dianggap `false` — server yang tahu daftar role sebenarnya, bukan app.
     */
    fun visibleFor(effectiveRoles: Set<String>, capabilities: Map<String, Boolean>?): Boolean =
        gateAllows(capability, allowedRoles, effectiveRoles, capabilities)
}

/**
 * Satu-satunya tempat semantik gate menu ditulis — dipakai registri Akses Cepat
 * DAN registri Activity supaya keduanya tak bisa menyimpang.
 *
 * - Peta kemampuan ADA  → server yang memutuskan; kunci yang **absen dianggap
 *   tidak boleh** (fail-closed). Ini disengaja: kalau suatu kunci nanti dicabut
 *   untuk menyempitkan akses, klien tak boleh diam-diam kembali ke daftar role
 *   lokal yang basi.
 * - Peta kemampuan `null` (offline / panggilan gagal) → cadangan [allowedRoles].
 * - Role kosong (profil belum termuat) → sembunyikan, jangan menebak.
 */
internal fun gateAllows(
    capability: String?,
    allowedRoles: Set<String>,
    effectiveRoles: Set<String>,
    capabilities: Map<String, Boolean>?,
): Boolean {
    capability?.let { key ->
        capabilities?.let { caps ->
            // Kunci yang diminta dulu; kalau server BELUM mengenalnya, jatuh ke
            // ejaan LAMA-nya. Ini bukan pelonggaran gate: `EJAAN_KUNCI_LAMA`
            // memetakan satu-ke-satu ke kunci yang persis sama artinya, dan
            // nilainya tetap datang dari server.
            //
            // Wajib ada karena arah kompatibilitasnya DUA-ARAH. Aturan di doc
            // atas ("kunci hilang = false, jangan jatuh ke daftar role lokal")
            // menjaga arah server-menyempitkan-akses. Yang TIDAK dijaga: APK
            // BARU yang bicara dengan server LAMA — di sana kunci baru memang
            // belum ada, dan tanpa cadangan ini menunya hilang tanpa satu pun
            // pesan, termasuk kartu "Nilai Aktivitas" milik satu-satunya PIC.
            // Cadangan tetap jatuh ke SERVER, bukan ke daftar role lokal, jadi
            // penyempitan dari server tetap menang.
            caps[key]?.let { return it }
            EJAAN_KUNCI_LAMA[key]?.let { lama -> return caps[lama] == true }
            return false
        }
    }
    return when {
        effectiveRoles.isEmpty() -> false
        allowedRoles == ALL_LOGGED_IN -> true
        else -> effectiveRoles.any { it in allowedRoles }
    }
}

/**
 * Kunci kemampuan ejaan BARU -> ejaan LAMA yang artinya persis sama.
 *
 * Fase EKSPAND rename istilah 2026-08-19: server menyajikan KEDUANYA
 * (`capabilities.rs` `CAPABILITY_ROLES`), jadi peta ini hanya terpakai saat APK
 * ini dipasang di HP yang servernya belum naik. Dihapus di fase kontrak,
 * bersama pencabutan ejaan lama di server.
 */
internal val EJAAN_KUNCI_LAMA: Map<String, String> = mapOf(
    "aktivitas.review" to "raport.review",
)

/** Menu yang memang terbuka untuk semua role yang login — HARUS eksplisit. */
internal val ALL_LOGGED_IN: Set<String> = setOf("*")

/** Nama role yang dikenal sistem (rust-shared `Role` + implied role page-grant +
 *  slug divisi yang di-fold jadi akses). Dipakai test untuk menangkap salah
 *  ketik pada [QuickAccessMenu.allowedRoles] — role yang tak ada di sini tak
 *  akan pernah cocok dengan apa pun dan menunya diam-diam hilang selamanya. */
internal val KNOWN_ROLES: Set<String> = setOf(
    "superadmin", "admin", "owner", "manager", "sales-manager", "kepala-cabang",
    "karyawan", "sales", "admin-sales", "admin-stok", "admin-penjualan", "operator",
    "agent", "hrd", "pic_raport", "pic-raport", "crm-manager", "ads-manager",
    "ai-engineer", "pdi", "kasir", "driver", "delivery-control",
    "indent-approver", "discount-approver", "aki-approver",
    // `trainee` = role primary KELIMA sejak 2026-08-17 (paket 3.18d), BUKAN
    // divisi. Ia sudah dipegang `CRM_INPUT_ROLES` + `STAFF_SELF_SERVICE_ROLES`
    // di rust-shared sejak hari itu, tapi tak pernah didaftarkan di sini —
    // sehingga `allowedRoles` mana pun yang menyebutnya akan divonis salah ketik
    // oleh test dan cadangan offline-nya tak akan pernah bisa ditulis.
    "trainee",
    // `cs` BUKAN lagi role hantu sejak migrasi 223 (2026-08-15): divisi
    // `verificator-dan-reporting` naik jadi divisi ber-akses, dan
    // `divisi_access_slugs` (rust-shared `auth.rs`) melipatnya jadi slug `cs`
    // pada role efektif. Diperiksa langsung di produksi 2026-08-25: 3 akun
    // AKTIF memegang divisi itu — semuanya ber-`role` `karyawan`, jadi slug-nya
    // memang datang dari kolom `divisi`, bukan dari `role`.
    //
    // Selama "cs" tak ada di daftar ini, setiap `allowedRoles` yang menyebutnya
    // divonis salah ketik oleh test — jadi dua menu verifikator di bawah tak
    // akan bisa punya cadangan offline sama sekali.
    "cs",
)

/**
 * Cerminan `SPK_BLOCKED_ROLES` (rust-shared `capabilities.rs`) — daftar
 * TERLARANG, karena `spk.pipeline` di backend memang dihitung dari DENYLIST.
 *
 * **Ditulis eksplisit sejak 2026-08-28, dan itu memperbaiki regresi nyata.**
 * Sebelumnya baris ini berbunyi `KNOWN_ROLES - "ai-engineer"` dengan alasan
 * "supaya role baru otomatis ikut, sama seperti backend" — premis yang benar
 * HANYA selama backend meloloskan tiap role baru. Sejak `trainee` lahir
 * (2026-08-17) itu tidak lagi benar: Rust memblokirnya. Begitu `trainee`
 * ditambahkan ke [KNOWN_ROLES] (commit sinkronisasi role hari ini, demi Absen
 * & Input Prospek yang memang haknya), selisih ini diam-diam memberinya kartu
 * SPK juga — kartu yang `create_delivery` jawab 403, dan yang masterplan 3.18d
 * justru ingin tutup. Denylist yang disalin apa adanya tak punya mode gagal itu.
 */
internal val SPK_BLOCKED_ROLES: Set<String> = setOf("ai-engineer", "trainee")

/** `is_pipeline_actor` (inventory-service delivery.rs) meloloskan semua role
 *  KECUALI yang ada di [SPK_BLOCKED_ROLES]. */
internal val SPK_MENU_ROLES: Set<String> = KNOWN_ROLES - SPK_BLOCKED_ROLES

/**
 * Home Service — daftar role cadangan offline. Tinggal DI SINI, bukan di
 * `ActivityRegistry`, karena kedua registri memakainya dan arah ketergantungan
 * antar-berkas hanya boleh SATU: `ui.activity` mengimpor dari `ui.home`
 * (`ALL_LOGGED_IN`, `SPK_MENU_ROLES`, `gateAllows`, …), tak pernah sebaliknya.
 *
 * Sempat dibalik saat dua ubin Home Service ditambahkan (2026-08-15) dan
 * akibatnya BUKAN error kompilasi melainkan `ExceptionInInitializerError` saat
 * runtime: `<clinit>` kedua berkas saling menunggu, `ALL_LOGGED_IN` terbaca
 * `null`, dan SELURUH registri Activity mati sekaligus. 24 test tumbang
 * serentak dengan pesan yang tak menyebut sebabnya.
 *
 * **[HS_LAPOR_ROLES] kini [ALL_LOGGED_IN]** (2026-08-15, permintaan user
 * "semua karyawan bisa mengajukan komplain konsumen"). Jalur pelaporan
 * kinerja-service sudah LOGIN-ONLY — `ensure_role` dicabut dari `lookup`,
 * `cari`, `create`, `list`, `detail`, dan unggah fotonya — jadi daftar role apa
 * pun di sini hanya akan lebih sempit dari servernya.
 *
 * Daftar lamanya memuat 13 role dan tetap menutup 24 karyawan AKTIF di
 * produksi (admin-penjualan 9, pic-raport 6, crm-manager 3, it-programmer 2,
 * digital-team 2, hrd 1, ai-engineer 1). Daftar role selalu tertinggal dari
 * daftar pegawai; itu sebabnya bentuknya diganti, bukan ditambahi.
 *
 * Konsekuensinya ubin `komplain_lapor` WAJIB ber-`capability = null`: kunci apa
 * pun (termasuk `spk.pipeline` yang dipakai sebelumnya) akan menyempitkan lagi,
 * dan peta kemampuan fail-closed membuat kunci tak dikenal menyembunyikan menu
 * dari SEMUA orang.
 */
internal val HS_LAPOR_ROLES = ALL_LOGGED_IN

/** `homeservice.task` — teknisi kunjungan. Cerminan `HOMESERVICE_TASK_ROLES` (= PDI_ROLES). */
internal val HS_TASK_ROLES = setOf("pdi", "admin", "superadmin")

/**
 * Dua menu VERIFIKATOR (divisi `verificator-dan-reporting`, slug `cs`).
 *
 * Cerminan `AC_INSTALL_SCHEDULE_ROLES` dan `VERTEL_ROLES` di
 * `packages/rust-shared/src/capabilities.rs`. Keduanya kebetulan berisi daftar
 * yang SAMA hari ini, tapi ditulis TERPISAH dengan sengaja: masing-masing
 * mencerminkan konstanta server yang berbeda dan bisa menyimpang kapan saja.
 * Satu nilai bersama akan membuat perubahan di salah satu sisi server diam-diam
 * ikut menggeser menu yang lain.
 *
 * Ini cadangan OFFLINE saja. Saat peta kemampuan terbaca, `capability` yang
 * memutuskan (`gateAllows`) — dan itulah jalur normalnya, karena verifikator
 * memegang `cs` lewat divisi.
 *
 * **CS bersifat PUSAT, lintas 13 cabang** (`vertel.rs batas_dealer` menjawab
 * `None` untuk role ini; pemasangan AC sama). Jangan menambahkan penyaring
 * cabang di sisi klien untuk dua menu ini — server memang mengirim seluruh
 * cabang, dan menyaringnya di HP menghilangkan 12 cabang dari daftar kerjanya
 * tanpa satu pun galat.
 */
internal val AC_INSTALL_SCHEDULE_ROLES = setOf("cs", "admin", "superadmin")

/** Cerminan `VERTEL_ROLES` — lihat catatan di [AC_INSTALL_SCHEDULE_ROLES]. */
internal val VERTEL_ROLES = setOf("cs", "admin", "superadmin")

/**
 * Daftar menu + haknya. Urutan di sini = urutan tampil di grid.
 *
 * Saat menambah menu: cari guard backend-nya DULU (gateway `require_*` /
 * konstanta role di service), tulis di `backendGuard`, baru isi `allowedRoles`
 * meniru guard itu. Kalau backend tak mengecek role sama sekali, tulis
 * [ALL_LOGGED_IN] dan sebutkan itu di `backendGuard`.
 */
// Absen, CRM, SPK, dan Antrian PDI SENGAJA tak ada di sini sejak 2026-07-28:
// keempatnya sudah jadi kartu di layar Activity (tab pertama), dan
// menampilkannya di dua tempat membuat user ragu mana yang "benar". Indent
// PINDAH KEMBALI ke sini 2026-07-30 (bersama Cari Barang/inventory yang
// sudah lebih dulu ada di grid ini, dan Cari Semua yang baru) — permintaan
// user memisahkan tugas harian (Activity) dari akses cepat (Operasional).
// Gate-nya tidak pernah hilang — cek `ui/activity/ActivityRegistry.kt` kalau
// menu lain juga pindah lagi nanti.
internal val QUICK_ACCESS_MENUS: List<QuickAccessMenu> = listOf(
    QuickAccessMenu(
        id = "klasemen_lapangan",
        // Kunci BARU, sengaja bukan `klasemen.view` (audiens klasemen PENJUALAN,
        // yang justru tak memuat driver maupun pdi). Daftar rolenya hidup di
        // `KLASEMEN_OPERASIONAL_ROLES` — cadangan di bawah cuma dipakai saat
        // peta kemampuan server belum termuat (offline / server lama).
        capability = "klasemen.operasional",
        label = "Klasemen Lapangan",
        allowedRoles = setOf(
            "driver", "pdi", "delivery-control", "kepala-cabang",
            "sales-manager", "manager", "admin", "superadmin", "owner",
        ),
        backendGuard = "kinerja-service klasemen.rs LIHAT_ROLES (= KLASEMEN_OPERASIONAL_ROLES)",
    ),
    QuickAccessMenu(
        id = "gaji",
        capability = "payroll.self",
        label = "Slip Gaji",
        allowedRoles = STAFF_MENU_ROLES,
        backendGuard = "kinerja-service payroll VIEW_OWN_ROLES (= STAFF_ROLES)",
    ),
    QuickAccessMenu(
        id = "kpi",
        // `GET /kpi/me` HANYA memanggil `identity_from_headers` — tak ada
        // `ensure_role` sama sekali; scope-nya diri sendiri lewat user_id token.
        // Karena itu `capability` tetap `null`: TIDAK ADA kunci kemampuan yang
        // bisa dicerminkan, dan mengisinya dengan kunci yang tak dikenal server
        // justru menyembunyikan menu dari SEMUA orang (peta kemampuan ada →
        // kunci absen dianggap false).
        //
        // MANAGER & SUPERADMIN SAJA sejak 2026-08-02 (permintaan user): KPI
        // masih diuji, karyawan belum boleh melihat skor dirinya. Ini gate
        // MENU — endpoint `/kpi/me` sengaja dibiarkan terbuka, sama seperti
        // "Input aktivitas" (`ActivityRegistry.kt`) 2026-07-31. Cerminan web:
        // `DashboardLayout.tsx` blok "KPI Saya" + `dashboardAccess.ts`.
        // "admin" ikut karena alias lama superadmin (migrasi 075).
        capability = null,
        label = "KPI Saya",
        allowedRoles = setOf("manager", "superadmin", "admin"),
        backendGuard = "tanpa gate role — kinerja-service kpi.rs get_me (self-scoped); pembatasan ini MENU-only",
    ),
    QuickAccessMenu(
        id = "inventory",
        capability = null,
        label = "Inventory",
        allowedRoles = ALL_LOGGED_IN,
        backendGuard = "gateway grup protected — stok cabang tanpa gate role tambahan",
    ),
    QuickAccessMenu(
        id = "indent",
        capability = "indent.submit",
        label = "Ajukan Inden",
        allowedRoles = INDENT_MENU_ROLES,
        backendGuard = "inventory-service indent.rs require_indent_submitter_role",
    ),
    QuickAccessMenu(
        id = "cari_semua",
        capability = null,
        label = "Cari Semua",
        allowedRoles = ALL_LOGGED_IN,
        backendGuard = "tidak ada — layar baca cache Room lokal (branch_stock + leads), tanpa panggilan backend",
    ),
    QuickAccessMenu(
        id = "klasemen",
        capability = "klasemen.view",
        label = "Sales",
        allowedRoles = KLASEMEN_MENU_ROLES,
        backendGuard = "gateway MOBILE_LEADERBOARD_ROLES",
    ),
    QuickAccessMenu(
        id = "opname",
        capability = "opname.view",
        label = "Opname",
        allowedRoles = OPNAME_MENU_ROLES,
        backendGuard = "inventory-service opname.rs authorize_view",
    ),
    QuickAccessMenu(
        id = "harga_gs",
        capability = "harga_gs.view",
        label = "Harga GS",
        allowedRoles = HARGA_GS_MENU_ROLES,
        backendGuard = "gateway require_price_changes_reader",
    ),
    QuickAccessMenu(
        id = "serial_input",
        capability = "serial.input",
        label = "Input SN",
        allowedRoles = SERIAL_INPUT_MENU_ROLES,
        backendGuard = "inventory-service serials.rs is_admin_stok_role",
    ),
    QuickAccessMenu(
        id = "deadstock",
        capability = "deadstock.cabang",
        label = "Deadstock",
        allowedRoles = DEADSTOCK_MENU_ROLES,
        backendGuard = "inventory-service deadstock/mod.rs is_cabang_role",
    ),
    QuickAccessMenu(
        id = "mutasi_histori",
        capability = "mutasi_histori.view",
        label = "Riwayat Mutasi",
        allowedRoles = MUTASI_HISTORI_MENU_ROLES,
        backendGuard = "tanpa gate role server-side — meniru RoleGuard web InventoryMutasiPage",
    ),
    // ── Home Service (komplain purna-jual) ──────────────────────────────────
    //
    // Ditambahkan 2026-08-15 (laporan user: sales & PDI tak menemukan menunya).
    // Sebelum ini modul komplain NOL pintu masuk di beranda: registri ini tak
    // punya satu pun entrinya, dan di layar Activity kartu "Lapor Komplain"
    // memang SENGAJA disembunyikan (`hiddenFromActivity`, commit 2344c71 —
    // keputusan user, TIDAK dibatalkan di sini). Yang tersisa cuma route
    // `home_hs_lapor` yang hidup tanpa ada yang menavigasinya.
    //
    // Kedua entri memakai ULANG konstanta role milik `ActivityRegistry` —
    // menyalinnya ke sini berarti dua daftar yang bisa berpisah diam-diam,
    // persis kelas bug yang registri ini dibuat untuk mencegahnya.
    QuickAccessMenu(
        id = "komplain_lapor",
        // `null` DISENGAJA, lihat KDoc [HS_LAPOR_ROLES]: servernya login-only,
        // jadi kunci apa pun di sini menyempitkan. `spk.pipeline` yang dipakai
        // sampai 2026-08-15 pun menutup `ai-engineer`.
        capability = null,
        label = "Lapor Komplain",
        allowedRoles = HS_LAPOR_ROLES,
        backendGuard = "tanpa guard: kinerja-service home_service/handlers.rs create_ticket login-only (self-scoped)",
    ),
    QuickAccessMenu(
        id = "komplain_saya",
        // `null` dengan alasan yang SAMA seperti tetangga di atas — jalur
        // `sayaLapor` self-scoped (server memaksa `pelapor_user_id` = id aktor).
        // Siapa pun yang boleh melapor harus bisa melihat laporannya sendiri.
        capability = null,
        label = "Komplain Saya",
        allowedRoles = HS_LAPOR_ROLES,
        backendGuard = "tanpa guard: kinerja-service home_service/service.rs list sayaLapor (self-scoped, pelapor_user_id = id aktor)",
    ),
    QuickAccessMenu(
        id = "komplain_tugas",
        capability = "homeservice.task",
        label = "Tugas Home Service",
        allowedRoles = HS_TASK_ROLES,
        backendGuard = "rust-shared capabilities.rs HOMESERVICE_TASK_ROLES (= PDI_ROLES)",
    ),
    /**
     * Sisi VERIFIKATOR pemasangan AC — menjadwalkan dan menugaskan tim.
     *
     * Sampai 2026-08-25 app hanya punya sisi PETUGAS (`ActivityRegistry`
     * `pemasangan_ac`, "Tugas Pemasangan AC") dan penugasannya web-saja. Ubin
     * ini melengkapi pasangannya: yang satu MENERIMA pekerjaan, yang ini
     * MEMBERIKANNYA.
     *
     * `acinstall.schedule` kunci yang SAMA dengan DUA menu web ("Jadwal
     * Pemasangan AC" + "Tim Pemasangan AC", `navCatalog.ts`). Di HP keduanya
     * jadi satu layar: memilih tim tanpa melihat jadwalnya berarti dua kali
     * perjalanan untuk satu keputusan.
     */
    QuickAccessMenu(
        id = "pemasangan_ac_kontrol",
        capability = "acinstall.schedule",
        label = "Penugasan AC",
        allowedRoles = AC_INSTALL_SCHEDULE_ROLES,
        backendGuard = "inventory-service pemasangan_ac.rs boleh_jadwalkan (rust-shared AC_INSTALL_SCHEDULE_ROLES)",
    ),
    /**
     * Verifikasi telepon penjualan kemarin (migrasi 257).
     *
     * Daftarnya sudah disaring SERVER per ambang harga barang, dan ambang itu
     * hidup di `app_settings` (bisa berubah tanpa deploy). Klien MENAMPILKAN
     * angkanya apa adanya dari respons, tak pernah menghitung atau menebaknya.
     */
    QuickAccessMenu(
        id = "vertel",
        capability = "vertel.manage",
        label = "Verifikasi Telepon",
        allowedRoles = VERTEL_ROLES,
        backendGuard = "inventory-service vertel.rs boleh_vertel (rust-shared VERTEL_ROLES)",
    ),
)

/**
 * Menu yang DITAMBAHKAN untuk akun uji, di luar daftar role-nya.
 *
 * Ditambahkan 2026-08-15 atas permintaan user: "KPI jangan dulu ditampilkan ke
 * semua karyawan KECUALI akun uji." Gate 2026-08-02 (manager/superadmin/admin)
 * TETAP BERDIRI apa adanya — yang berubah cuma ada satu pintu tambahan supaya
 * fitur KPI bisa diuji dari HP tanpa memberi role manager ke akun uji.
 *
 * **JANGAN tertukar dengan `ITEM_KHUSUS_AKUN_UJI` di `ActivityRegistry.kt`** —
 * namanya mirip, arahnya berlawanan:
 *
 * | Konstanta (per id) | Arah | Akibat untuk non-akun-uji |
 * |---|---|---|
 * | `ITEM_KHUSUS_AKUN_UJI` → `raport` | MEMBATASI ke akun uji, TANPA jalan tembus | item HILANG walau role/kemampuan mengizinkan (`allowedRoles` = `ALL_LOGGED_IN`) |
 * | `ITEM_KHUSUS_AKUN_UJI` → `opname_cabang` | MEMBATASI, ada jalan tembus role | hilang untuk karyawan biasa; **TETAP TAMPIL** untuk keempat role `OPNAME_PELAKSANA_NYATA` — semuanya memang ada di `OPNAME_HITUNG_MENU_ROLES` |
 * | `ITEM_KHUSUS_AKUN_UJI` → `opname_validasi` | MEMBATASI, jalan tembusnya SEBAGIAN PERCUMA | hilang untuk karyawan biasa; dari keempat role `TEMBUS_AKUN_UJI` hanya **`admin-stok`** yang benar-benar melihatnya — `allowedRoles` = `SERIAL_INPUT_MENU_ROLES` = `{admin-stok}` dan kemampuan `serial.input` juga `admin-stok` saja, jadi `gateAllows` menyaring tiga sisanya SESUDAH jalan tembus |
 * | [MENU_TAMBAHAN_AKUN_UJI] (di sini) | MENAMBAH akun uji | tak ada yang hilang; role lama utuh |
 *
 * **`TEMBUS_AKUN_UJI` itu ADA dan sedang menopang pekerjaan nyata.** Ia
 * ditambahkan 2026-08-14 — SEHARI sebelum berkas ini disentuh — karena
 * admin-stok sudah memakai opname di produksi hari itu juga. Jangan
 * mencabutnya saat "menyeragamkan" kedua berkas dengan alasan tak ada
 * padanannya di sini: yang tak punya padanan itu KEBUTUHANNYA (`kpi` memang
 * tak perlu jalan tembus), bukan konstantanya.
 *
 * Yang membedakan keduanya BUKAN urutan evaluasi melainkan BENTUK ungkapannya.
 * Di sana vonis akun-uji dirangkai sebagai PENGURANG: `id in set && !akunUji`
 * membuka satu blok yang `return@filter false` KECUALI `effectiveRoles`
 * memuat role di `TEMBUS_AKUN_UJI[id]` (kutipan utuhnya di
 * `visibleActivityItems`, `ActivityRegistry.kt` — baca di sana, jangan
 * mengandalkan ringkasan ini). Jalan tembus itu MEMPERSEMPIT siapa yang
 * dipotong, tapi tidak membalik arahnya: bentuk itu tetap bisa membatalkan
 * gate yang sudah meluluskan. Di sini vonisnya dirangkai sebagai ATAU
 * (`gateAllows(...) || jalanAkunUji`) sehingga ia hanya bisa menambah. Kalau
 * bentuk ActivityRegistry disalin apa adanya ke sini,
 * `manager`/`superadmin`/`admin` yang BUKAN akun uji akan kehilangan menu KPI
 * yang sudah mereka pegang sejak 2026-08-02 — dan tak ada entri tembus untuk
 * `kpi` yang akan menahannya. Itulah kekeliruan yang paling mudah terjadi saat
 * "menyeragamkan" dua berkas ini.
 *
 * (Urutannya memang ikut berbeda — di sana saringan akun-uji berjalan SEBELUM
 * `gateAllows`, di sini SESUDAH — tapi itu akibat dari bentuknya, bukan
 * sebabnya. Menukar urutan saja di sini tidak mencabut hak siapa pun.)
 *
 * **Alternatif yang SENGAJA tidak dipilih:** menambahkan role akun uji ke
 * `allowedRoles` entri `kpi`. Diperiksa di produksi 2026-08-15 (`auth_users`,
 * baca-saja): ada **11** akun yang cocok predikat akun uji — **10 ber-`role`
 * `karyawan`**, satu (`test pic`) ber-`role` `owner`. Variasi "UJI
 * Sales/PDI/Kasir/Driver" ada di kolom `divisi` (`sales`, `pdi`, `kasir`,
 * `driver`, `admin-stok`, …) yang di-fold jadi role efektif oleh
 * [effectiveRoles], BUKAN di kolom `role`. Artinya satu-satunya role yang
 * mencakup hampir seluruh keluarga akun uji adalah `karyawan` — yang dipegang
 * pula oleh seluruh karyawan nyata, persis yang diminta user ditutup.
 * Menyebut divisinya satu per satu juga tak menolong: divisi itu dipakai
 * karyawan sungguhan juga (hitungan hari itu: `sales` 45, `pdi` 17, `driver`
 * 13, `kasir` 8 akun non-uji).
 *
 * Ini gate MENU saja, tak ada backend yang perlu ikut diubah: `GET /kpi/me`
 * (kinerja-service `kpi/handlers.rs` `get_me`) hanya memanggil
 * `identity_from_headers` lalu men-scope ke `identity.user_id` — TANPA
 * `ensure_role`, berbeda dari `get_karyawan_list`/`get_karyawan_detail` di
 * berkas yang sama yang memakai `ensure_role(&identity, VIEW_ALL_ROLES)`.
 *
 * Cerminan web mendarat lebih dulu di commit `ce67e13b` (Tridjaya-Web,
 * 2026-08-15): param ketiga `akunUji` (ber-default `false`) pada
 * `canAccessDashboardPath` (`utils/dashboardAccess.ts`), injeksi menu sidebar
 * `DashboardLayout.tsx`, prop `allowAkunUji` pada RoleGuard route
 * `/dashboard/kpi` (`App.tsx`), dan kartu KPI beranda `KaryawanDashboard.tsx`.
 * Bentuknya di sana juga ATAU (`akunUji || normalizeAccessRole(role) ===
 * 'manager'`) — sengaja sama. Predikatnya sendiri, `isAkunUji`
 * (`utils/akunUji.ts`), BUKAN bagian commit itu: ia lahir jauh lebih awal di
 * `de7f7be8` (2026-07-31, gate akun-uji untuk Input Aktivitas), dan
 * `ce67e13b` hanya mengimpornya.
 */
internal val MENU_TAMBAHAN_AKUN_UJI: Set<String> = setOf("kpi")

/**
 * Menu yang boleh tampil untuk pemilik [effectiveRoles]. Fail-closed: role
 * kosong (profil belum termuat) = tak ada menu ber-gate yang muncul.
 *
 * [akunUji] default `false` DISENGAJA — kontrak yang sama dengan
 * `visibleActivityItems` (`ActivityRegistry.kt`) dan `canAccessDashboardPath`
 * (web): pemanggil yang lupa mengopernya MENYEMBUNYIKAN, tak pernah
 * membocorkan. Di sini akibatnya menu [MENU_TAMBAHAN_AKUN_UJI] tak muncul untuk
 * akun uji — kerugiannya cuma "fitur tak bisa diuji", dan itu ketahuan pada
 * pemakaian pertama; kebalikannya (default `true`) membocorkan skor KPI ke
 * karyawan nyata dan tak ketahuan sama sekali. Nilai default ini ikut dijaga
 * test `jalan tembus akun uji hanya untuk kpi…`.
 */
internal fun visibleQuickAccessMenus(
    effectiveRoles: Set<String>,
    capabilities: Map<String, Boolean>? = null,
    akunUji: Boolean = false,
): List<QuickAccessMenu> = QUICK_ACCESS_MENUS.filter { menu ->
    // Gate role/kemampuan dinilai lebih dulu dan berdiri SENDIRI: klausa akun-uji
    // di bawah hanya bisa menambah `true`, tak pernah membatalkan `true` ini.
    if (menu.visibleFor(effectiveRoles, capabilities)) return@filter true
    // Jalan kedua khusus akun uji.
    //
    // `effectiveRoles.isNotEmpty()` ikut diperiksa supaya aturan fail-closed
    // `gateAllows` ("role kosong = profil belum termuat = jangan menebak") tidak
    // bisa dilangkahi lewat pintu ini. Sisi web melakukan hal setara di
    // `canAccessDashboardPath`, yang `return false` saat `!role` SEBELUM cabang
    // akun-uji dinilai.
    akunUji && effectiveRoles.isNotEmpty() && menu.id in MENU_TAMBAHAN_AKUN_UJI
}
