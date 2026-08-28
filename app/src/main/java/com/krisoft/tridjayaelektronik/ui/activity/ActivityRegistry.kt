package com.krisoft.tridjayaelektronik.ui.activity

import com.krisoft.tridjayaelektronik.ui.acinstall.JABATAN_PETUGAS_PEMASANGAN
import com.krisoft.tridjayaelektronik.ui.acinstall.punyaJabatanPetugasPemasangan
import com.krisoft.tridjayaelektronik.ui.home.ALL_LOGGED_IN
import com.krisoft.tridjayaelektronik.ui.home.CRM_MENU_ROLES
import com.krisoft.tridjayaelektronik.ui.home.SERIAL_INPUT_MENU_ROLES
import com.krisoft.tridjayaelektronik.ui.home.STAFF_MENU_ROLES
import com.krisoft.tridjayaelektronik.ui.home.SPK_MENU_ROLES
import com.krisoft.tridjayaelektronik.ui.home.HS_LAPOR_ROLES
import com.krisoft.tridjayaelektronik.ui.home.HS_TASK_ROLES
import com.krisoft.tridjayaelektronik.ui.home.gateAllows

/**
 * REGISTRI item layar Activity — satu-satunya tempat menyatakan SIAPA yang
 * melihat sebuah tugas/antrian, persis pola `ui/home/QuickAccessMenus.kt`.
 *
 * Tidak ada nilai default: menambah item tanpa menyatakan [ActivityItem.capability]
 * dan [ActivityItem.allowedRoles] tidak akan dikompilasi. [ActivityItem.backendGuard]
 * menyebut guard mana yang dicerminkan supaya penerus bisa menelusuri ulang saat
 * backend berubah — diuji di `ActivityRegistryTest`.
 */
enum class ActivityKind {
    /** Rutinitas harian dengan penanda selesai; reset tiap hari. */
    TUGAS_HARIAN,
    /** Pintasan cepat (buat baru / buka daftar); tanpa penanda selesai. */
    AKSI,
    /** Pekerjaan menumpuk milik orang lain; selesai = 0. */
    ANTRIAN,
}

/**
 * Asal angka/status sebuah item. Item yang berbagi endpoint HTTP yang sama
 * (mis. dua item aki) tetap punya nilai sendiri — dedup panggilan dilakukan
 * `ActivityViewModel`, yang menembak endpointnya sekali lalu menurunkan dua
 * angka dari respons yang sama.
 */
enum class ActivitySource {
    NONE,
    ABSENSI_TODAY,
    LEADS_CACHE,
    RAPORT_TODAY,
    /** `GET /raport-harian?tanggal=hari-ini&status=pending` — antrian PIC raport.
     *  Angkanya `total` (bukan `items.size`): server memotong `items` ke `limit`. */
    AKTIVITAS_REVIEW_PENDING,
    /** `GET /home-service` — tiket komplain menunggu triase CS. */
    HS_TRIASE,
    /** `GET /home-service?mine=true` — kunjungan yang ditugaskan ke teknisi ini. */
    HS_TUGAS_TEKNISI,
    /** `GET /home-service?jenis=tarik_unit` — antrian penarikan unit. */
    HS_TARIK,
    /** `GET /home-service?jenis=tarik_unit&mine=true` — unit yang harus driver ambil. */
    HS_TUGAS_DRIVER,
    SPK_LOCAL_COUNTER,
    DLV_PENDING_PDI,
    DLV_PENDING_SPK,
    DLV_PENDING_PAYMENT,
    DLV_PENDING_NOTE,
    DLV_PENDING_SCHEDULING,
    DLV_AS_DRIVER,
    AKI_FORMS_MINE,
    AKI_FORMS_APPROVAL,
    DISCOUNT_PENDING,
    INDENT_PENDING,
    /** `GET /inventory/opname/manual-units?status=pending` — unit ketik-manual
     *  menunggu putusan admin-stok. Sesi opname cabang TERKUNCI selama antriannya
     *  belum kosong, jadi angkanya bukan sekadar informasi. */
    OPNAME_MANUAL_PENDING,
    /**
     * `GET /inventory/delivery/pemasangan-ac/tugas-saya` — tugas pemasangan AC
     * yang menugaskan akun ini. Angkanya bukan `size` melainkan yang BELUM
     * dijawab (`butuhJawabanSaya`): tugas yang sudah diterima tetap harus
     * dikerjakan, tapi lencana yang tak pernah turun ke nol berhenti dibaca.
     */
    AC_INSTALL_TUGAS,

    /** `GET /inventory/opname?status=draft` — sesi opname yang sedang berjalan
     *  di CABANG petugas. Server-lah yang men-scope-nya ke cabang akun
     *  (`list_opname`), bukan parameter dari app. */
    OPNAME_SESI_DRAFT,

    /**
     * `GET /kupon-gebyar/meta` — konsumen cabang yang berhak kupon doorprize
     * dan BELUM dikirimi undangan. Angkanya `jumlah - sudahDikirim`.
     *
     * Sumber ini istimewa: responsnya juga membawa **vonis cabang**
     * (`bolehLihat`), yang tak bisa dinyatakan sebagai kunci kemampuan sama
     * sekali — lihat [kuponGebyarCardVisible].
     */
    KUPON_GEBYAR_SISA,
}

data class ActivityItem(
    /** Id stabil — dipakai UI memilih ikon/aksi dan dipakai test. */
    val id: String,
    val label: String,
    val subtitle: String,
    val kind: ActivityKind,
    /** Kunci dari `GET /api/me/capabilities` — sumber utama. `null` hanya untuk
     *  item yang memang tak ber-gate backend. */
    val capability: String?,
    /** Cadangan saat peta kemampuan gagal dimuat (offline). Harus tetap
     *  mencerminkan guard backend. */
    val allowedRoles: Set<String>,
    /** Guard backend yang dicerminkan — sebut file/konstanta aslinya. */
    val backendGuard: String,
    val source: ActivitySource,
    /** Kunci navigasi, diterjemahkan jadi route oleh `ActivityNavHost`. */
    val navKey: String,
    /** Item yang sengaja tampil tapi belum bisa dibuka. */
    val comingSoon: Boolean = false,
    /** Sudah bisa dipakai, tapi masih terbatas — kartunya diberi label "BETA". */
    val beta: Boolean = false,
    /** Menu tetap terdaftar untuk kontrak route/deep-link, tetapi tidak ditampilkan di Activity. */
    val hiddenFromActivity: Boolean = false,
    /**
     * Saringan JABATAN (`auth_users.divisi`), dipakai SEBAGAI TAMBAHAN atas
     * [capability]/[allowedRoles] — bukan penggantinya. `null` = tak disaring
     * per jabatan (mayoritas item).
     *
     * Ada karena sebagian pekerjaan tidak bisa dinyatakan lewat role sama
     * sekali. Contoh yang melahirkannya: `teknisi` adalah jabatan berkategori
     * `label` ber-`akses_slugs = '[]'` (migrasi 195) — ia TIDAK melipat jadi
     * role apa pun, dan kedua teknisi produksi ber-`role = "karyawan"`. Gate
     * role di sana menyaring nol orang, dan endpoint-nya sendiri login-only
     * self-scoped sehingga tak ada kunci kemampuan yang bisa dicerminkan.
     *
     * Pembagian kerjanya sama dengan [ITEM_KHUSUS_AKUN_UJI]: role/kemampuan
     * menjawab "boleh memanggil endpoint-nya?", jabatan menjawab "punya
     * pekerjaannya?".
     */
    val jabatan: Set<String>? = null,
)

/**
 * Gerbang ROLE kartu "Input aktivitas" — [ALL_LOGGED_IN], dan itu kini benar
 * mencerminkan backend: `upsert_raport` (kinerja-service `raport/handlers.rs`)
 * LOGIN-ONLY sejak 2026-08-14, karena endpoint-nya self-scoped (payload tak
 * membawa id karyawan; service memasangnya dari identity). Peringatan lama
 * "tombol kirim akan dijawab 403" sudah TIDAK berlaku.
 *
 * Yang membatasi siapa yang MELIHAT kartunya bukan baris ini melainkan
 * [ITEM_KHUSUS_AKUN_UJI] — sengaja dipisah: role menjawab "boleh mengirim?",
 * set akun-uji menjawab "fiturnya sudah dilepas ke orang nyata?". Menyatukan
 * keduanya (mis. mengunci ulang ke role `karyawan`) akan MENGHILANGKAN kartu
 * dari akun uji sendiri — UJI Sales/PDI/Kasir/Driver ber-role macam-macam,
 * bukan `karyawan`.
 *
 * Belum ada kunci di `GET /api/me/capabilities` untuk hak kirim, jadi item
 * `aktivitas` ber-`capability = null`.
 *
 * **Ia BUKAN satu-satunya — kalimat itu sudah basi sejak 2026-08-15.**
 * `lapor_komplain` ikut dinolkan hari itu karena jalur pelaporan kinerja-service
 * jadi login-only, sehingga tak ada kunci yang bisa dicerminkan tanpa
 * menyempitkan. Hitungan yang berlaku sekarang: dari **26** entri
 * [ACTIVITY_ITEMS], **3** ber-`capability = null` (`aktivitas`,
 * `lapor_komplain`, `pemasangan_ac`) dan 23 sisanya ditentukan sepenuhnya oleh peta
 * kemampuan
 * server. Daftar tiga itu dikunci `ActivityRegistryTest`, jadi menambah item
 * tanpa kunci akan memerahkan test — bukan diam-diam memperbesar angka ini.
 */
internal val AKTIVITAS_INPUT_ROLES = ALL_LOGGED_IN

/**
 * Cerminan `capabilities::AKTIVITAS_REVIEW_ROLES` (rust-shared) — cadangan OFFLINE
 * saja; sumber utamanya kunci `raport.review` dari `GET /api/me/capabilities`.
 *
 * `owner` SENGAJA tak ada: ia boleh MEMBACA raport (`RAPORT_VIEW_ALL_ROLES`)
 * tapi ditolak `review_raport`. Dua ejaan `pic_raport`/`pic-raport` sama-sama
 * ditulis karena backend memang mengenali keduanya (`auth.rs`).
 *
 * **`manager`, `kepala-cabang`, dan `hrd` DICABUT 2026-08-18** (arahan user:
 * penilaian aktivitas hanya PIC + administrator). Daftar ini yang menyembunyikan
 * kartu "Nilai Aktivitas" saat OFFLINE; sisi ONLINE sudah tertutup begitu
 * server berhenti menjawab `raport.review = true`.
 *
 * Kenapa keduanya WAJIB dipangkas bersama: `gateAllows` memakai peta kemampuan
 * lebih dulu dan berhenti di sana, tapi ia jatuh ke daftar ini kalau peta belum
 * termuat (`capabilities == null`, yaitu saat offline atau panggilan gagal).
 * Meninggalkan ketiga peran di sini = kartunya tetap muncul di HP yang belum
 * berhasil memuat kemampuannya, lalu setiap ketukan dijawab 403.
 */
internal val AKTIVITAS_REVIEW_ROLES = setOf(
    "admin", "superadmin", "pic_raport", "pic-raport",
)

/**
 * Siapa boleh MELAPOR komplain — cerminan `home_service.rs LAPOR_ROLES`, yang
 * memang sangat luas: keluhan datang ke siapa pun yang kebetulan dihubungi
 * konsumen, jadi menyempitkannya berarti keluhan tak tercatat.
 *
 * `hrd` TIDAK ada di daftar server; jangan ditambahkan "biar rapi" — kartunya
 * akan tampil lalu semua panggilannya dijawab 403.
 *
 * `"cs"` yang ada di daftar server SENGAJA tak ditulis di sini — tapi **alasan
 * lamanya sudah BASI dan jangan dipakai lagi**. Dulu tertulis: "rust-shared
 * menyatakan sendiri belum ada role literal `cs` di sistem, jadi ejaan itu tak
 * akan pernah cocok dengan role siapa pun". Itu berhenti benar pada migrasi 223
 * (2026-08-15): divisi `verificator-dan-reporting` naik jadi divisi ber-akses,
 * dan `divisi_access_slugs` (rust-shared `auth.rs`) melipatnya jadi slug `cs`
 * pada role efektif. Diperiksa di produksi 2026-08-25: 3 akun AKTIF memegangnya.
 *
 * Yang masih berlaku: petugas CS lolos lewat PETA KEMAMPUAN server, yang memang
 * sumber utamanya, jadi ketiadaan "cs" di sini tak berpengaruh saat online.
 *
 * **Celah yang tersisa, dan diketahui:** saat peta kemampuan gagal dimuat,
 * `gateAllows` jatuh ke daftar role lokal — dan di situ `cs` tak ada, jadi
 * kartu komplain hilang dari petugas CS tanpa satu pun pesan. Sengaja TIDAK
 * ditambal bersamaan dengan penambahan dua ubin verifikator (2026-08-25):
 * melebarkan gate modul komplain adalah perubahan perilaku tersendiri yang
 * layak diputuskan terpisah, bukan ditumpangkan.
 */
// [HS_LAPOR_ROLES] PINDAH ke `ui/home/QuickAccessMenus.kt` (lihat impor di atas).

/** `homeservice.dispatch` — triase CS (tugaskan teknisi / minta tarik / batalkan).
 *  `"cs"` dilepas dengan alasan yang sama seperti di [HS_LAPOR_ROLES]. */
internal val HS_DISPATCH_ROLES = setOf("admin", "superadmin", "manager", "delivery-control")

// [HS_TASK_ROLES] PINDAH ke `ui/home/QuickAccessMenus.kt` (lihat impor di atas).

/**
 * Cerminan `capabilities::OPNAME_HITUNG_ROLES` (rust-shared) — cadangan OFFLINE
 * saja; sumber utamanya kunci `opname.hitung` dari `GET /api/me/capabilities`.
 *
 * Memuat `karyawan` dan itu memang disengaja: sejak migrasi 144 role primary
 * dikecilkan jadi {superadmin, owner, manager, karyawan}, jadi `karyawan`
 * adalah role hampir seluruh pegawai. Yang menyempitkan aksesnya BUKAN daftar
 * ini melainkan penguncian cabang di server (`authorize_hitung`:
 * `session.dealer_code` harus sama dengan cabang AKUN). Kartu yang tampil untuk
 * orang yang cabangnya beda paling jauh membawanya ke daftar sesi kosong —
 * jauh lebih murah daripada petugas yang berhak tapi kartunya hilang.
 */
internal val OPNAME_HITUNG_MENU_ROLES =
    setOf("admin", "superadmin", "admin-stok", "kepala-cabang", "karyawan")

/** `indent.submit` — `require_indent_submitter_role` (inventory `indent.rs`). */
internal val INDENT_SUBMIT_ROLES = setOf("admin", "superadmin", "kepala-cabang", "manager")

/** `indent.approve` — `require_indent_approver_role`. admin SENGAJA tak ada. */
internal val INDENT_APPROVE_ROLES = setOf("owner", "indent-approver")

/** `aki.approve` — `delivery/aki.rs ensure_may_decide`. */
internal val AKI_APPROVE_ROLES = setOf("aki-approver", "admin", "superadmin", "manager", "owner")

/** `delivery.control` — `submit_delivery_note`/`assign_driver`. manager TIDAK. */
internal val DELIVERY_CONTROL_ROLES = setOf("delivery-control", "admin", "superadmin")

/** `discount.approve` — cerminan; guard aslinya lookup `page_grants`. */
internal val DISCOUNT_APPROVE_ROLES = setOf("discount-approver", "admin", "superadmin")

/** `pdi.queue` — `submit_pdi` (ROLE_PDI/admin). */
internal val PDI_QUEUE_ROLES = setOf("pdi", "admin", "superadmin")

/** `kasir.queue` — `confirm_spk` (ROLE_KASIR/admin). */
internal val KASIR_QUEUE_ROLES = setOf("kasir", "admin", "superadmin")


/**
 * `spk.create` — `can_create_spk` (inventory-service `delivery.rs`, kini
 * memanggil `tridjaya_shared::capabilities::can_create_spk`). Beda dari
 * [SPK_MENU_ROLES] (dipakai `spk.pipeline`, gate BACA antrian/riwayat): itu
 * meloloskan manager/owner, ini menolak keduanya — `create_delivery` (gate
 * TULIS) menolak mereka juga. C1 audit 2026-07-28: chip "Buat SPK" sempat
 * memakai `spk.pipeline` padahal menavigasi langsung ke form input, jadi
 * manager/owner menekannya lalu dijawab 403.
 */
internal val SPK_CREATE_ROLES: Set<String> = SPK_MENU_ROLES - "manager" - "owner"

/**
 * Cerminan `capabilities::KUPON_GEBYAR_LIHAT_ROLES` (rust-shared) — cadangan
 * OFFLINE saja; sumber utamanya kunci `kupon_gebyar.lihat` dari
 * `GET /api/me/capabilities`.
 *
 * **DISAMAKAN PERSIS dengan daftar server 2026-08-28.** Sebelumnya isinya
 * `kepala-cabang, admin-sales, karyawan, manager, admin, superadmin, owner` —
 * menyimpang di TIGA arah sekaligus dari `KUPON_GEBYAR_LIHAT_ROLES`, dan tiap
 * arah merugikan orang yang berbeda:
 *  - **salah ejaan**: `admin-sales` ada di sini, server menulis
 *    `admin-penjualan`. Slug yang tak pernah cocok = baris mati yang terlihat
 *    seperti jaring pengaman;
 *  - **kurang**: `kasir` tak pernah ada di sini padahal server memuatnya, jadi
 *    kasir offline kehilangan kartu yang jadi haknya;
 *  - **lebih**: `manager`/`admin`/`superadmin`/`owner` ada di sini padahal doc
 *    server EKSPLISIT mengecualikan mereka ("pengawasan lintas-cabang mereka
 *    lewat Papan Gebyar, bukan daftar konsumen ini") — offline mereka melihat
 *    kartunya lalu dijawab 403 begitu online, persis pola "menu tampil, endpoint
 *    403" yang CLAUDE.md repo ini justru ingin dicegah.
 *
 * SENGAJA tetap lebar sampai `karyawan`: programnya memang untuk "setiap
 * karyawan di cabang terkait" (arahan owner). Menyempitkannya akan mengunci
 * orang yang justru diminta mengerjakannya.
 *
 * **`"cs"` yang ada di daftar server sengaja TIDAK ditulis di sini**, alasan
 * sama dengan [HS_LAPOR_ROLES] dulu: belum ada role literal `cs` di sistem,
 * jadi ejaan itu tak akan pernah cocok dengan role siapa pun dan cuma jadi
 * baris yang tampak seperti jaring pengaman padahal mati. Petugas CS sungguhan
 * tetap lolos lewat peta kemampuan server. (Catatan: `cs` KINI ada di
 * `KNOWN_ROLES` sejak migrasi 223, jadi menuliskannya tak lagi memerahkan test —
 * yang menahannya sekarang murni alasan di atas, bukan penjaga salah-ketik.)
 *
 * **Daftar ini BUKAN gerbang yang sesungguhnya.** Yang menentukan siapa melihat
 * kartunya adalah vonis cabang dari server — lihat [kuponGebyarCardVisible].
 */
internal val KUPON_GEBYAR_MENU_ROLES = setOf(
    "kepala-cabang", "admin-penjualan", "kasir", "karyawan",
)

/**
 * Urutan di sini = urutan dasar tampil dalam tiap seksi (kartu ANTRIAN
 * diurutkan ulang menurun berdasarkan angka di `ActivityPlan`).
 */
internal val ACTIVITY_ITEMS: List<ActivityItem> = listOf(
    ActivityItem(
        id = "absen_masuk",
        label = "Absen masuk",
        subtitle = "Check-in + selfie & lokasi",
        kind = ActivityKind.TUGAS_HARIAN,
        capability = "absensi.self",
        allowedRoles = STAFF_MENU_ROLES,
        backendGuard = "kinerja-service absensi.rs STAFF_ROLES",
        source = ActivitySource.ABSENSI_TODAY,
        navKey = "absen",
    ),
    ActivityItem(
        id = "absen_pulang",
        label = "Absen pulang",
        subtitle = "Check-out setelah jam kerja",
        kind = ActivityKind.TUGAS_HARIAN,
        capability = "absensi.self",
        allowedRoles = STAFF_MENU_ROLES,
        backendGuard = "kinerja-service absensi.rs STAFF_ROLES",
        source = ActivitySource.ABSENSI_TODAY,
        navKey = "absen",
    ),
    ActivityItem(
        id = "prospek",
        label = "Input prospek",
        subtitle = "Catat calon konsumen hari ini",
        kind = ActivityKind.TUGAS_HARIAN,
        capability = "crm.input",
        allowedRoles = CRM_MENU_ROLES,
        backendGuard = "crm-service http.rs CRM_INPUT_ROLES + karyawan_scope",
        source = ActivitySource.LEADS_CACHE,
        navKey = "crm",
    ),
    ActivityItem(
        id = "aktivitas",
        label = "Input aktivitas",
        subtitle = "Laporan aktivitas harian",
        kind = ActivityKind.TUGAS_HARIAN,
        capability = null,
        allowedRoles = AKTIVITAS_INPUT_ROLES,
        backendGuard = "kinerja-service raport.rs KARYAWAN_ROLES (upsert_raport)",
        source = ActivitySource.RAPORT_TODAY,
        navKey = "aktivitas",
        beta = true,
    ),
    ActivityItem(
        id = "buat_spk",
        label = "Buat SPK",
        subtitle = "Input penjualan baru",
        kind = ActivityKind.AKSI,
        capability = "spk.create",
        allowedRoles = SPK_CREATE_ROLES,
        backendGuard = "inventory-service delivery.rs can_create_spk",
        source = ActivitySource.SPK_LOCAL_COUNTER,
        navKey = "spk_input",
    ),
    ActivityItem(
        // Sengaja PERSIS setelah `buat_spk`: seksi PINTASAN dirender dua kolom
        // menurut urutan daftar ini, jadi pasangan SPK duduk bersebelahan dalam
        // satu baris. Menyelipkan item lain di antaranya akan memisahkan mereka.
        id = "daftar_spk",
        label = "Daftar SPK",
        subtitle = "Riwayat & status terkini",
        kind = ActivityKind.AKSI,
        // Gate BACA (bukan `spk.create`): `list_delivery` meloloskan seluruh aktor
        // pipeline, termasuk manager/owner yang ditolak `create_delivery`. Sama
        // dengan entri "Riwayat SPK" di `SpkHubScreen`.
        capability = "spk.pipeline",
        allowedRoles = SPK_MENU_ROLES,
        backendGuard = "inventory-service delivery.rs list_delivery (view=history)",
        source = ActivitySource.NONE,
        navKey = "spk_history",
    ),
    ActivityItem(
        id = "lapor_komplain",
        label = "Lapor Komplain",
        subtitle = "Keluhan konsumen purna-jual",
        kind = ActivityKind.AKSI,
        // `null` sejak 2026-08-15 — servernya login-only, jadi kunci apa pun
        // menyempitkan. Alasan lengkap di KDoc [HS_LAPOR_ROLES] (`ui/home`).
        capability = null,
        allowedRoles = HS_LAPOR_ROLES,
        backendGuard = "tanpa guard: kinerja-service home_service/handlers.rs create_ticket login-only (self-scoped)",
        source = ActivitySource.NONE,
        navKey = "hs_lapor",
        hiddenFromActivity = true,
    ),
    ActivityItem(
        id = "komplain_masuk",
        label = "Komplain Masuk",
        subtitle = "Tiket menunggu ditriase",
        kind = ActivityKind.ANTRIAN,
        capability = "homeservice.dispatch",
        allowedRoles = HS_DISPATCH_ROLES,
        backendGuard = "rust-shared capabilities.rs HOMESERVICE_DISPATCH_ROLES",
        source = ActivitySource.HS_TRIASE,
        navKey = "hs_triase",
    ),
    ActivityItem(
        id = "tugas_home_service",
        label = "Tugas Home Service",
        subtitle = "Kunjungan teknisi yang ditugaskan",
        kind = ActivityKind.ANTRIAN,
        capability = "homeservice.task",
        allowedRoles = HS_TASK_ROLES,
        backendGuard = "rust-shared capabilities.rs HOMESERVICE_TASK_ROLES",
        source = ActivitySource.HS_TUGAS_TEKNISI,
        navKey = "hs_teknisi",
    ),
    /**
     * Tugas pemasangan AC (migrasi 253/255/256). Sisi PETUGAS dari modul yang
     * sebelumnya web-saja.
     *
     * **`capability = null` dan `allowedRoles = ALL_LOGGED_IN` di sini BENAR,
     * bukan kelonggaran yang terlewat.** `GET .../pemasangan-ac/tugas-saya`
     * memang login-only dan SELF-SCOPED: ia mengembalikan hanya pengajuan yang
     * menugaskan pemanggil, jadi tak ada yang bisa bocor lewat role. Server
     * sengaja tak memberinya kunci kemampuan — anggota tim dipilih per-ORANG
     * dari akun aktif, jadi tak ada daftar role yang benar untuk "petugas
     * pemasangan" (keputusan yang sama dengan `DRIVER_TASKS_ROLES`).
     *
     * Yang menyempitkan tampilannya adalah [ActivityItem.jabatan] — lihat
     * `AcInstallPlan.punyaJabatanPetugasPemasangan` untuk kenapa `teknisi`
     * TIDAK bisa dinyatakan sebagai role.
     */
    ActivityItem(
        id = "pemasangan_ac",
        label = "Tugas Pemasangan AC",
        subtitle = "Jadwal pemasangan yang ditugaskan ke kamu",
        kind = ActivityKind.ANTRIAN,
        capability = null,
        allowedRoles = ALL_LOGGED_IN,
        backendGuard = "inventory-service pemasangan_ac.rs tugas_saya_handler (login-only, self-scoped)",
        source = ActivitySource.AC_INSTALL_TUGAS,
        navKey = "pemasangan_ac",
        jabatan = JABATAN_PETUGAS_PEMASANGAN,
    ),
    ActivityItem(
        id = "tarik_unit",
        label = "Tarik Unit",
        subtitle = "Penarikan unit menunggu driver",
        kind = ActivityKind.ANTRIAN,
        // `delivery.control` — guard `boleh_atur_tarik` MENGIMPOR
        // DELIVERY_CONTROL_ROLES, jadi kunci ini sudah disajikan server. Tak ada
        // kunci `homeservice.tarik` tersendiri.
        capability = "delivery.control",
        allowedRoles = DELIVERY_CONTROL_ROLES,
        backendGuard = "kinerja-service home_service/service.rs boleh_atur_tarik (DELIVERY_CONTROL_ROLES)",
        source = ActivitySource.HS_TARIK,
        navKey = "hs_tarik",
    ),
    ActivityItem(
        id = "tugas_tarik_unit",
        label = "Tugas Tarik Unit",
        subtitle = "Unit yang harus kamu jemput",
        kind = ActivityKind.ANTRIAN,
        // Sama alasannya dengan kartu "Tugas Antar": kepemilikan ditentukan
        // SERVER (`mine` menyaring `tarik_driver_id`), bukan daftar role — jadi
        // gate-nya longgar dan angkanya sendiri yang menyembunyikan kartu ini
        // dari orang yang tak pernah ditugaskan.
        //
        // Kalimat terakhir itu sempat TIDAK BENAR: `buildQueueCards` hanya
        // menyembunyikan `DLV_AS_DRIVER`, sehingga kartu ini tampil permanen
        // untuk semua pemegang `spk.pipeline` (= semua kecuali `ai-engineer`).
        // Sekarang `HS_TUGAS_DRIVER` ikut disaring `driverCardVisible`, jadi
        // janji di atas benar-benar ditepati — dan `ActivityPlanTest`
        // menguncinya supaya tak lepas lagi.
        capability = "spk.pipeline",
        allowedRoles = SPK_MENU_ROLES,
        backendGuard = "kinerja-service home_service/service.rs list (mine → tarik_driver_id)",
        source = ActivitySource.HS_TUGAS_DRIVER,
        navKey = "hs_driver",
    ),
    ActivityItem(
        // Sisi PIC dari kartu "aktivitas" di atas: karyawan mengisi, PIC menilai.
        id = "aktivitas_review",
        label = "Nilai Aktivitas",
        subtitle = "Laporan karyawan menunggu dinilai",
        kind = ActivityKind.ANTRIAN,
        capability = "aktivitas.review",
        allowedRoles = AKTIVITAS_REVIEW_ROLES,
        backendGuard = "kinerja-service raport.rs REVIEW_ROLES (capabilities::AKTIVITAS_REVIEW_ROLES)",
        source = ActivitySource.AKTIVITAS_REVIEW_PENDING,
        navKey = "aktivitas_review",
    ),
    ActivityItem(
        id = "antrian_pdi",
        label = "Antrian PDI",
        subtitle = "Unit menunggu inspeksi",
        kind = ActivityKind.ANTRIAN,
        capability = "pdi.queue",
        allowedRoles = PDI_QUEUE_ROLES,
        backendGuard = "inventory-service delivery.rs submit_pdi",
        source = ActivitySource.DLV_PENDING_PDI,
        navKey = "pdi",
    ),
    ActivityItem(
        id = "aki_saya",
        label = "Form Aki saya",
        subtitle = "Aki bekas belum dikembalikan",
        kind = ActivityKind.ANTRIAN,
        capability = "pdi.queue",
        allowedRoles = PDI_QUEUE_ROLES,
        backendGuard = "inventory-service delivery/aki.rs may_read_forms (PDI cabang sendiri)",
        source = ActivitySource.AKI_FORMS_MINE,
        navKey = "aki",
    ),
    ActivityItem(
        id = "antrian_kasir",
        label = "Antrian Kasir",
        subtitle = "SPK menunggu konfirmasi bayar",
        kind = ActivityKind.ANTRIAN,
        capability = "kasir.queue",
        allowedRoles = KASIR_QUEUE_ROLES,
        backendGuard = "inventory-service delivery.rs confirm_spk",
        source = ActivitySource.DLV_PENDING_SPK,
        navKey = "kasir",
    ),
    ActivityItem(
        id = "spk_gantung",
        label = "SPK Gantung",
        // Sejak 2026-07-29 kartunya berangka SEMUA unit terkirim yang bayarnya
        // belum dikonfirmasi (berapa pun umurnya) — subtitle ikut dilepas dari
        // ">24 jam", karena umur kini tampil sbg baris `alert` terpisah.
        subtitle = "Sudah diantar, bayar belum dikonfirmasi",
        kind = ActivityKind.ANTRIAN,
        capability = "kasir.queue",
        allowedRoles = KASIR_QUEUE_ROLES,
        backendGuard = "inventory-service delivery.rs list_delivery view=pending_payment",
        source = ActivitySource.DLV_PENDING_PAYMENT,
        navKey = "spk_gantung",
    ),
    ActivityItem(
        id = "surat_jalan",
        label = "Surat Jalan",
        subtitle = "Menunggu diterbitkan",
        kind = ActivityKind.ANTRIAN,
        capability = "delivery.control",
        allowedRoles = DELIVERY_CONTROL_ROLES,
        backendGuard = "inventory-service delivery.rs submit_delivery_note",
        source = ActivitySource.DLV_PENDING_NOTE,
        navKey = "note",
    ),
    ActivityItem(
        id = "penjadwalan",
        label = "Penjadwalan",
        subtitle = "Menunggu driver & jadwal",
        kind = ActivityKind.ANTRIAN,
        capability = "delivery.control",
        allowedRoles = DELIVERY_CONTROL_ROLES,
        backendGuard = "inventory-service delivery.rs assign_driver",
        source = ActivitySource.DLV_PENDING_SCHEDULING,
        navKey = "jadwal",
    ),
    ActivityItem(
        id = "tugas_antar",
        label = "Tugas Antar",
        subtitle = "Pengiriman yang ditugaskan ke kamu",
        kind = ActivityKind.ANTRIAN,
        // Sengaja memakai kunci pipeline, BUKAN kunci role driver: guard
        // `authorize_driver` meloloskan `can_create_spk` yang luas, jadi daftar
        // role apa pun akan salah. Visibilitas akhir ditentukan
        // [driverCardVisible] — server sudah men-scope ke job yang di-assign.
        capability = "spk.pipeline",
        allowedRoles = SPK_MENU_ROLES,
        backendGuard = "inventory-service delivery.rs authorize_driver (kepemilikan di server)",
        source = ActivitySource.DLV_AS_DRIVER,
        navKey = "driver",
    ),
    ActivityItem(
        id = "approval_diskon",
        label = "Approval Diskon",
        subtitle = "Pengajuan menunggu putusan",
        kind = ActivityKind.ANTRIAN,
        capability = "discount.approve",
        allowedRoles = DISCOUNT_APPROVE_ROLES,
        backendGuard = "inventory-service discounts.rs ensure_may_decide",
        source = ActivitySource.DISCOUNT_PENDING,
        navKey = "diskon",
    ),
    ActivityItem(
        id = "aki_approval",
        label = "Approval Aki",
        subtitle = "Form aki menunggu putusan",
        kind = ActivityKind.ANTRIAN,
        capability = "aki.approve",
        allowedRoles = AKI_APPROVE_ROLES,
        backendGuard = "inventory-service delivery/aki.rs ensure_may_decide",
        source = ActivitySource.AKI_FORMS_APPROVAL,
        navKey = "aki",
    ),
    ActivityItem(
        id = "approval_inden",
        label = "Approval Inden",
        subtitle = "Pengajuan inden menunggu putusan",
        kind = ActivityKind.ANTRIAN,
        capability = "indent.approve",
        allowedRoles = INDENT_APPROVE_ROLES,
        backendGuard = "inventory-service indent.rs require_indent_approver_role",
        source = ActivitySource.INDENT_PENDING,
        navKey = "indent",
    ),
    ActivityItem(
        id = "opname_cabang",
        label = "Opname Cabang",
        subtitle = "Hitung fisik unit di gudang cabang",
        kind = ActivityKind.ANTRIAN,
        // `opname.hitung` — kunci BARU di katalog rust-shared (2026-08-09),
        // sengaja BUKAN `opname.view`: kunci itu menyetir menu Stock Opname di
        // web, dan memakainya di sini berarti kartu ini cuma tampil untuk
        // pengelola — persis kebalikan dari maksudnya. Kunci karangan juga tak
        // boleh: peta kemampuan fail-closed, kunci tak dikenal menyembunyikan
        // kartunya dari SEMUA orang tanpa error.
        //
        // TAPI kunci ini TIDAK menentukan siapa yang melihat kartunya sekarang:
        // sejak 2026-08-14 id-nya ada di [ITEM_KHUSUS_AKUN_UJI], jadi orang
        // nyata tak melihatnya walau server menjawab `opname.hitung = true`.
        // Baca alasannya di sana sebelum menyimpulkan gate ini rusak.
        capability = "opname.hitung",
        allowedRoles = OPNAME_HITUNG_MENU_ROLES,
        backendGuard = "inventory-service opname.rs authorize_hitung (capabilities::OPNAME_HITUNG_ROLES)",
        source = ActivitySource.OPNAME_SESI_DRAFT,
        navKey = "opname",
    ),
    ActivityItem(
        id = "opname_validasi",
        label = "Validasi Opname",
        subtitle = "Unit ketik manual menunggu putusan",
        kind = ActivityKind.ANTRIAN,
        // `serial.input` — BUKAN kunci baru: `has_admin_stok` meng-IMPORT
        // SERIAL_INPUT_ROLES dari katalog rust-shared, jadi kunci ini sudah
        // disajikan server. Kunci karangan = peta fail-closed menyembunyikan
        // kartunya dari SEMUA orang, tanpa error.
        //
        // Ikut [ITEM_KHUSUS_AKUN_UJI] sejak 2026-08-14 — sisi lain dari alur
        // yang sama dengan `opname_cabang`. Tile "Input SN" (Akses Cepat,
        // kunci `serial.input` yang SAMA) sengaja TIDAK ikut: ia pekerjaan
        // admin-stok yang berdiri sendiri, bukan bagian alur opname per-SN.
        capability = "serial.input",
        allowedRoles = SERIAL_INPUT_MENU_ROLES,
        backendGuard = "inventory-service opname.rs has_admin_stok (capabilities::SERIAL_INPUT_ROLES)",
        source = ActivitySource.OPNAME_MANUAL_PENDING,
        navKey = "opname_validasi",
    ),
    /**
     * Konsumen Gebyar — undangan kupon doorprize yang belum dikirim di cabang
     * ini (kinerja-service `kupon_gebyar/`).
     *
     * **Kunci `kupon_gebyar.lihat` SUDAH ada di katalog server** (`capabilities.rs`
     * + migrasi 278) — periksa itu lagi sebelum menyalin pola ini untuk fitur
     * lain: peta kemampuan fail-closed, jadi kunci yang belum dikenal server
     * menyembunyikan kartunya dari SEMUA orang, termasuk manager, tanpa satu pun
     * galat.
     *
     * **Kunci itu TIDAK cukup, dan tak akan pernah cukup.** Ia hanya tahu ROLE;
     * gerbang yang sesungguhnya adalah CABANG (semua kecuali Manado = D-06 dan
     * D-07), yang hidup di `auth_users.cabang_id` dan tak bisa dinyatakan di
     * katalog kemampuan sama sekali. Lapis keduanya [kuponGebyarCardVisible],
     * yang membaca vonis `bolehLihat` dari server.
     */
    ActivityItem(
        id = "kupon_gebyar",
        label = "Konsumen Gebyar",
        subtitle = "Undangan kupon doorprize belum dikirim",
        kind = ActivityKind.ANTRIAN,
        capability = "kupon_gebyar.lihat",
        allowedRoles = KUPON_GEBYAR_MENU_ROLES,
        backendGuard = "kinerja-service kupon_gebyar/handlers.rs LIHAT_ROLES (capabilities::KUPON_GEBYAR_LIHAT_ROLES) + vonis cabang service.rs lingkup()",
        source = ActivitySource.KUPON_GEBYAR_SISA,
        navKey = "kupon_gebyar",
    ),
)

/**
 * Item yang HANYA tampil untuk akun uji, bukan karyawan nyata — dipakai saat
 * sebuah fitur masih diuji di produksi.
 *
 * `raport` (Input Aktivitas, BETA) masuk sini 2026-07-31, sempat DIBUKA untuk
 * semua karyawan 2026-08-12, lalu DISEMBUNYIKAN LAGI 2026-08-14 atas permintaan
 * user — jadi ia kembali ke sini.
 *
 * Gate ini gate TAMPILAN saja: `POST /raport-harian` sengaja TIDAK ikut ditutup,
 * supaya baris raport yang sudah berjalan + auto-feed KPI `LAPORAN AKTIVITAS`
 * tak putus. Menutup endpoint-nya bersamaan akan mematikan indikator KPI orang
 * yang datanya sudah masuk — kerugian yang tak terlihat sampai gajian.
 *
 * Cerminan web: `aktivitasInputVisible` di `DashboardLayout.tsx` (`isAkunUji`).
 * Keduanya harus sepakat, kalau tidak menu hilang di satu sisi saja dan orang
 * mengira app-nya rusak.
 *
 * `opname_cabang` + `opname_validasi` masuk 2026-08-14 atas permintaan user:
 * alur opname per-SN belum boleh terlihat karyawan. Keduanya, bukan salah satu
 * — mereka dua sisi alur yang sama (petugas menghitung, admin-stok memutus unit
 * ketik-manual), jadi menutup satu saja meninggalkan alurnya setengah terbuka.
 *
 * BEDANYA DENGAN `raport`, dan ini yang perlu diingat: kedua kartu opname punya
 * `capability` SUNGGUHAN yang server memang jawab `true` (`opname.hitung`
 * mencakup role `karyawan` di `capabilities.rs` — sengaja, "semua pegawai di
 * cabang itu"). Yang menyembunyikannya adalah urutan di [visibleActivityItems]:
 * saringan set ini berjalan SEBELUM `gateAllows`, jadi ia menang atas peta
 * kemampuan. Jangan dibalik urutannya, dan jangan "merapikan" dengan mencabut
 * `karyawan` dari `OPNAME_HITUNG_ROLES` di rust-shared — itu mematikan izin
 * MENGHITUNG di server untuk akun uji juga, sehingga fiturnya tak bisa diuji.
 *
 * TIDAK ikut ditutup (keputusan user 2026-08-14): tile "Opname" (`opname.view`)
 * dan "Input SN" (`serial.input`) di grid Akses Cepat Operasional. Keduanya
 * sudah jalan untuk admin/manager sejak Juli dan bukan bagian alur per-SN baru.
 * Akibatnya layar sesi opname MASIH terjangkau lewat tile itu bagi pemegang
 * `opname.view` — memang begitu yang diminta, bukan celah yang terlewat.
 */
// `raport` DIKELUARKAN 2026-08-15 (permintaan user): kartu "Input aktivitas"
// kembali terlihat oleh SEMUA karyawan, keempat kalinya gate ini dibalik.
//
// Alasannya kali ini terukur, bukan preferensi: selama pintu masuknya ditutup,
// indikator KPI `LAPORAN AKTIVITAS` TETAP menilai orang atasnya. Di produksi
// 2026-08-15 itu berarti 431 dari 493 baris Agustus ternyata tulisan worker
// auto-isi prospek (`evidence_mode='none'`, tanpa bukti) — cuma 62 baris dari
// 14 orang yang benar-benar diunggah manusia — dan 60 dari 61 orang divonis di
// bawah 40% pada laporan yang tak punya jalan untuk mereka isi. Angka itu
// mengalir ke insentif.
//
// KPI SENGAJA TIDAK diubah (keputusan user): indikatornya tetap dihitung apa
// adanya; yang dibuka hanya pintunya. Cerminan web: `aktivitasInputVisible = true`
// di `DashboardLayout.tsx` — dua sisi ini WAJIB sepakat.
//
// Menutupnya lagi = kembalikan `"aktivitas"` ke set ini DAN balikkan baris web itu.
private val ITEM_KHUSUS_AKUN_UJI = setOf("opname_cabang", "opname_validasi")

/**
 * Role yang TETAP melihat kartu opname walau item-nya ber-gate akun-uji.
 *
 * Ditambahkan 2026-08-14 sore setelah kenyataan lapangan membantah asumsi gate
 * itu. Gate akun-uji dipasang pagi harinya karena `opname.hitung` mencakup role
 * `karyawan`, jadi kartunya mendarat di HP hampir seluruh pegawai — itu memang
 * yang tak diinginkan user. Tapi vonis "belum boleh dipakai orang nyata"
 * ternyata terlalu lebar: **admin-stok sudah memakainya di produksi hari itu
 * juga** (sesi `OPN-20260814-0001`, scan SN 17:46), dan merekalah satu-satunya
 * role yang boleh MENDAFTARKAN SN (`SERIAL_INPUT_ROLES = ["admin-stok"]`).
 * Menutup kartunya dari mereka mencabut alat kerja yang sedang dipakai.
 *
 * Jadi saringannya dipersempit, BUKAN dicabut: pelaksana nyata tembus, karyawan
 * biasa tetap tidak melihat apa pun — permintaan asli user utuh.
 *
 * **`raport` SENGAJA tidak ikut** dan tak boleh diberi daftar tembus: ia BETA
 * penuh, tak punya pelaksana-nyata yang setara, dan riwayatnya sudah tiga kali
 * dibuka-tutup atas permintaan user.
 *
 * **Jangan ganti mekanismenya jadi mencabut `karyawan` dari
 * [OPNAME_HITUNG_MENU_ROLES]** — keluarga akun uji ber-role macam-macam
 * (UJI Sales/PDI/Kasir/Driver), jadi menyempitkan role justru menghilangkan
 * kartu dari akun uji sendiri; itu jebakan yang sama persis dengan
 * `AKTIVITAS_INPUT_ROLES`. Dua saringan ini memang menjawab dua pertanyaan
 * berbeda: role = "boleh mengerjakan?", set ini = "sudah dilepas ke siapa?".
 */
internal val OPNAME_PELAKSANA_NYATA =
    setOf("admin-stok", "admin", "superadmin", "kepala-cabang")

/**
 * Item ber-gate akun-uji yang punya jalan tembus role. Subset dari
 * [ITEM_KHUSUS_AKUN_UJI] — item yang tak tercantum di sini tetap akun-uji murni.
 */
private val TEMBUS_AKUN_UJI: Map<String, Set<String>> = mapOf(
    "opname_cabang" to OPNAME_PELAKSANA_NYATA,
    "opname_validasi" to OPNAME_PELAKSANA_NYATA,
)

/**
 * Apakah akun ini akun UJI (bukan karyawan nyata).
 *
 * Cerminan `birthday::akun_uji` di kinerja-service. PREFIKS, bukan `contains` —
 * "Puji Astuti" itu nama orang nyata dan tak boleh kehilangan menunya.
 */
internal fun akunUji(name: String?, nik: String?): Boolean {
    if (nik?.trim()?.startsWith("9900") == true) return true
    val atas = name.orEmpty().trimStart().uppercase()
    return listOf("UJI ", "E2E ", "TEST ").any { atas.startsWith(it) }
}

/**
 * Item yang boleh tampil untuk pemilik [effectiveRoles]. Fail-closed.
 *
 * [akunUji] default `false` DISENGAJA: pemanggil yang lupa mengopernya
 * menyembunyikan item BETA, bukan membocorkannya ke seluruh karyawan.
 */
internal fun visibleActivityItems(
    effectiveRoles: Set<String>,
    capabilities: Map<String, Boolean>? = null,
    akunUji: Boolean = false,
    divisi: String? = null,
): List<ActivityItem> = ACTIVITY_ITEMS.filter {
    if (it.hiddenFromActivity) return@filter false
    if (it.id in ITEM_KHUSUS_AKUN_UJI && !akunUji) {
        // Pelaksana nyata (admin-stok dst) menembus; karyawan biasa tidak.
        // Lihat [OPNAME_PELAKSANA_NYATA] untuk alasannya.
        val tembus = TEMBUS_AKUN_UJI[it.id].orEmpty()
        if (effectiveRoles.none { role -> role in tembus }) return@filter false
    }
    // Saringan JABATAN, DI ATAS gate role/kemampuan — bukan penggantinya.
    // [divisi] default `null` disengaja, pola sama [akunUji]: pemanggil yang
    // lupa mengopernya MENYEMBUNYIKAN item ber-jabatan, bukan membocorkannya
    // ke seluruh karyawan.
    it.jabatan?.let { perlu ->
        if (!cocokJabatan(divisi, perlu)) return@filter false
    }
    gateAllows(it.capability, it.allowedRoles, effectiveRoles, capabilities)
}

/**
 * Cocokkan [divisi] (CSV `auth_users.divisi`) dengan himpunan jabatan yang
 * dituntut item.
 *
 * Aturan pencocokannya hidup di `AcInstallPlan.punyaJabatanPetugasPemasangan`
 * — cerminan `FIND_IN_SET(..., REPLACE(divisi,' ',''))` di server, per-elemen
 * dan bukan `contains`. Ia dipanggil ulang di sini alih-alih ditulis ulang,
 * supaya tak lahir dua aturan yang berselisih diam-diam.
 */
private fun cocokJabatan(divisi: String?, perlu: Set<String>): Boolean = when (perlu) {
    JABATAN_PETUGAS_PEMASANGAN -> punyaJabatanPetugasPemasangan(divisi)
    // Belum ada himpunan jabatan lain. Ditolak, bukan diloloskan: item baru
    // ber-jabatan yang lupa menambah cabangnya di sini akan HILANG (terlihat,
    // dan pemiliknya melapor) alih-alih tampil untuk semua orang (tak terlihat).
    else -> false
}

/**
 * Tombol "Panduan Alur" di baris PINTASAN. BUKAN item registri (tak punya
 * angka/tugas), tapi gate-nya tetap harus mencerminkan backend: endpoint
 * `GET /inventory/delivery/petugas` (inventory-service `delivery/petugas.rs`)
 * ber-guard `is_pipeline_actor` — sama persis dengan `spk.pipeline` /
 * [SPK_MENU_ROLES]. Tanpa gate ini `ai-engineer` melihat tombol yang dijawab
 * 403 (kelas bug yang sudah berulang, lihat CLAUDE.md).
 */
internal fun panduanAlurVisible(
    effectiveRoles: Set<String>,
    capabilities: Map<String, Boolean>? = null,
): Boolean = gateAllows("spk.pipeline", SPK_MENU_ROLES, effectiveRoles, capabilities)

/** Sumber unik yang perlu diambil untuk sekumpulan item — dasar dedup fan-out. */
internal fun sourcesToFetch(items: List<ActivityItem>): Set<ActivitySource> =
    items.map { it.source }.filterNot { it == ActivitySource.NONE }.toSet()

/**
 * C2 audit 2026-07-28: di `list_delivery` (inventory-service `delivery.rs`)
 * cabang PERTAMA adalah `is_manager(roles) || is_admin(roles)` — cabang itu
 * TIDAK PERNAH membaca `query.as_driver`, jadi role ini menerima SELURUH job
 * perusahaan (terpotong `limit = 200`), bukan job yang ditugaskan ke mereka.
 * Kartu "Tugas Antar" karena itu tak berarti apa-apa buat mereka: angkanya
 * bukan tugas miliknya, dan menembak endpointnya cuma menarik 200 baris
 * percuma di layar pertama. Dipakai [driverCardVisible] DAN `ActivityViewModel`
 * (skip fetch) — satu konstanta, jangan disalin dua kali.
 */
internal val DELIVERY_READ_ALL_ROLES = setOf("manager", "owner", "admin", "superadmin")

/**
 * Kartu "Tugas Antar" sengaja tak punya kunci kemampuan (spec §6): backend
 * meloloskan siapa pun yang di-assign, jadi angka job-lah yang menentukan.
 * Role `driver` tetap melihat kartunya walau kosong — "hari ini bersih" adalah
 * informasi yang berguna baginya. Role di [DELIVERY_READ_ALL_ROLES] TIDAK
 * PERNAH melihat kartu ini (lihat doc di sana) — apa pun isi `count`.
 */
internal fun driverCardVisible(count: Int?, effectiveRoles: Set<String>): Boolean =
    if (effectiveRoles.any { it in DELIVERY_READ_ALL_ROLES }) false
    else "driver" in effectiveRoles || (count ?: 0) > 0

/**
 * Kartu "Konsumen Gebyar" tampil?
 *
 * [bolehLihat] adalah vonis SERVER dari `GET /kupon-gebyar/meta`, dan ia
 * gerbang yang sesungguhnya: kunci `kupon_gebyar.lihat` hanya tahu role,
 * sementara yang menentukan adalah CABANG (`auth_users.cabang_id`) — Manado
 * (D-06 Samrat + D-07 Bahu) di luar program. Tak ada bentuk daftar role yang
 * bisa menyatakan itu, jadi gerbangnya memang harus datang dari respons.
 *
 * Tiga keadaan, dan ketiganya berbeda:
 * - `false` → **sembunyikan**. Cabang ini memang tak punya pekerjaannya;
 *   menampilkan kartu yang setiap ketukannya dijawab 403 adalah kelas bug yang
 *   sudah berulang di repo ini (lihat CLAUDE.md).
 * - `true` → tampilkan.
 * - `null` (panggilan gagal / offline) → **tampilkan**, karena kartunya sudah
 *   bertanda "gagal muat, ketuk untuk coba lagi". Menyembunyikannya di sini
 *   berarti menu yang lenyap tiap kali sinyal jelek, dan karyawan melapor
 *   "menunya hilang" — keluhan yang persis sama sudah muncul untuk kartu lain.
 *
 * Perhatikan bedanya dengan [gateAllows], yang fail-CLOSED untuk kunci tak
 * dikenal: di sana ketidaktahuan datang dari SERVER yang menjawab (kunci absen
 * = sengaja dicabut), di sini ketidaktahuan datang dari server yang TIDAK
 * menjawab. Dua hal berbeda, dan menyamakannya salah di kedua arah.
 */
internal fun kuponGebyarCardVisible(bolehLihat: Boolean?): Boolean = bolehLihat != false

/**
 * Role yang MENDARAT di tab Operasional saat app dibuka, bukan Activity (default
 * untuk semua role lain).
 *
 * HANYA manager & owner. Guard backend yang mengisi seksi "PINTASAN" di Activity
 * menolak mereka: `can_create_spk` ([SPK_CREATE_ROLES] di atas mengurangi
 * "manager" DAN "owner") dan `require_indent_submitter_role`
 * ([INDENT_SUBMIT_ROLES] memuat "manager" tapi TIDAK "owner") — yang tersisa
 * cuma "Daftar SPK" (baca). Jadi Activity manager/owner nyaris kosong: HARI INI
 * cuma absen, PINTASAN satu ubin, PERLU TINDAKAN paling banter 1-2 kartu
 * approval. Dashboard (tab Operasional) sudah lama jadi layar utama kedua role ini.
 *
 * JANGAN tambah admin/superadmin: mereka lolos `spk.create` PENUH
 * ([SPK_CREATE_ROLES] tak mengurangi mereka) dan hampir semua kartu antrian
 * ([PDI_QUEUE_ROLES], [KASIR_QUEUE_ROLES], [DELIVERY_CONTROL_ROLES], plus
 * ketiga approval [AKI_APPROVE_ROLES]/[DISCOUNT_APPROVE_ROLES]/
 * [INDENT_APPROVE_ROLES] lewat page-grant) — Activity mereka justru padat,
 * mendaratkan mereka di Operasional malah menyembunyikan layar paling berguna
 * buat mereka.
 */
internal val SUMMARY_LANDING_ROLES: Set<String> = setOf("manager", "owner")

/**
 * `true` bila salah satu role efektif ada di [SUMMARY_LANDING_ROLES] — dipakai
 * `MainScreen` SEKALI saat komposisi pertama (`remember`) untuk memilih tab
 * awal (`AppDestination.SUMMARY` vs default lama `AppDestination.ACTIVITY`).
 * Role kosong (profil belum termuat saat itu) selalu `false` — jatuh ke
 * default lama, BUKAN ditebak, sama prinsip [gateAllows] di
 * `QuickAccessMenus.kt`. Sengaja TIDAK dievaluasi ulang setelah tab awal
 * dipilih: melompat tab sendiri setelah user sudah melihat/menyentuh layar
 * itu mengganggu.
 */
internal fun landsOnSummary(effectiveRoles: Set<String>): Boolean =
    effectiveRoles.any { it in SUMMARY_LANDING_ROLES }
