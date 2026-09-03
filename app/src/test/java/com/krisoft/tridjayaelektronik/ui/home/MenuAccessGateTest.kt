package com.krisoft.tridjayaelektronik.ui.home

import com.krisoft.tridjayaelektronik.data.model.UserDto
import com.krisoft.tridjayaelektronik.ui.activity.akunUji
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Gate menu beranda HARUS cocok dengan guard backend — menu yang tampil tapi
 * dijawab 403 adalah keluhan berulang (CRM, 2026-07-27: manager/kepala-cabang/
 * owner menekan tombol CRM lalu mendarat di layar gagal).
 *
 * Dua arah yang dijaga di sini:
 *  1. jangan menampilkan menu yang pasti ditolak server;
 *  2. jangan MENYEMBUNYIKAN menu dari orang yang sebenarnya berhak — gate lama
 *     membaca role utama saja, sehingga hak dari `roles` (mis. page-grant
 *     `indent-approver`) dan `divisi` (mis. `admin-stok`) tak terbaca.
 */
class MenuAccessGateTest {

    private fun user(role: String, roles: List<String> = emptyList(), divisi: String = "") =
        UserDto(id = "u1", nik = "1", email = "a@b.c", name = "Uji", role = role, roles = roles, divisi = divisi)

    private fun rolesOf(role: String, roles: List<String> = emptyList(), divisi: String = "") =
        effectiveRoles(user(role, roles, divisi))

    @Test
    fun `role efektif menggabungkan role utama roles dan divisi`() {
        val r = rolesOf("karyawan", listOf("kasir"), "pdi, admin-penjualan")
        assertTrue(r.containsAll(setOf("karyawan", "kasir", "pdi", "admin-penjualan")))
    }

    // ── CRM: crm-service melayani karyawan (scoped) + crm-manager/admin ──────

    @Test
    fun `crm tampil untuk karyawan dan crm-manager, tersembunyi untuk manajemen`() {
        assertTrue(canAccessCrm(rolesOf("karyawan")))
        assertTrue(canAccessCrm(rolesOf("karyawan", divisi = "sales")))
        assertTrue(canAccessCrm(rolesOf("crm-manager")))
        assertTrue(canAccessCrm(rolesOf("superadmin")))
        // 2026-07-29: kepala cabang dilayani ter-scope ke lead sendiri
        // (`CRM_INPUT_ROLES` rust-shared) — ia punya target prospek harian.
        assertTrue(canAccessCrm(rolesOf("kepala-cabang")))
        // Semua ini dijawab 403 oleh crm-service → menunya tak boleh muncul.
        assertFalse(canAccessCrm(rolesOf("manager")))
        assertFalse(canAccessCrm(rolesOf("owner")))
        assertFalse(canAccessCrm(rolesOf("ai-engineer")))
    }

    @Test
    fun `karyawan yang juga crm-manager tetap dapat menu crm`() {
        assertTrue(canAccessCrm(rolesOf("karyawan", listOf("crm-manager"))))
    }

    // ── Absen & Slip Gaji: STAFF_ROLES kinerja-service ───────────────────────

    @Test
    fun `absen dan slip gaji tersembunyi untuk role di luar STAFF_ROLES`() {
        assertTrue(canAccessStaffSelfService(rolesOf("karyawan")))
        assertTrue(canAccessStaffSelfService(rolesOf("manager")))
        assertTrue(canAccessStaffSelfService(rolesOf("owner")))
        assertFalse(canAccessStaffSelfService(rolesOf("crm-manager")))
        assertFalse(canAccessStaffSelfService(rolesOf("ai-engineer")))
    }

    // ── Klasemen: MOBILE_LEADERBOARD_ROLES gateway ──────────────────────────

    @Test
    fun `klasemen tersembunyi untuk crm-manager dan ai-engineer`() {
        assertTrue(canAccessKlasemen(rolesOf("karyawan")))
        assertTrue(canAccessKlasemen(rolesOf("kepala-cabang")))
        assertFalse(canAccessKlasemen(rolesOf("crm-manager")))
        assertFalse(canAccessKlasemen(rolesOf("ai-engineer")))
    }

    // ── SPK: is_pipeline_actor (semua kecuali ai-engineer murni) ─────────────

    @Test
    fun `spk tersembunyi hanya untuk ai-engineer murni`() {
        assertTrue(canAccessSpk(rolesOf("karyawan")))
        assertTrue(canAccessSpk(rolesOf("manager")))
        assertTrue(canAccessSpk(rolesOf("owner")))
        assertFalse(canAccessSpk(rolesOf("ai-engineer")))
        // ai-engineer yang juga admin tetap boleh (admin menang di backend).
        assertTrue(canAccessSpk(rolesOf("ai-engineer", listOf("superadmin"))))
    }

    // ── Hak dari roles/divisi tak boleh hilang (regresi gate role-utama) ─────

    @Test
    fun `input serial number tampil untuk karyawan berdivisi admin-stok`() {
        // Gate lama membaca role utama ("karyawan") → tile hilang padahal
        // `is_admin_stok_role` di serials.rs meloloskannya.
        assertTrue(canAccessSerialInput(rolesOf("karyawan", divisi = "admin-stok")))
        assertTrue(canAccessSerialInput(rolesOf("karyawan", listOf("admin-stok"))))
        assertFalse(canAccessSerialInput(rolesOf("karyawan")))
    }

    @Test
    fun `indent submit untuk admin-ish, bukan approver`() {
        // indent.submit (require_indent_submitter_role) != indent.approve.
        // indent-approver TIDAK otomatis dapat tombol ajukan — mirror
        // INDENT_SUBMIT_ROLES di ActivityRegistry.kt, dan test yang sama
        // ("approver inden tak mendapat tombol ajukan inden") di
        // ActivityRegistryTest.kt.
        assertTrue(canAccessIndent(rolesOf("manager")))
        assertTrue(canAccessIndent(rolesOf("superadmin")))
        assertFalse(
            canAccessIndent(rolesOf("karyawan", listOf("indent-approver"))),
        )
        assertFalse(canAccessIndent(rolesOf("karyawan")))
    }

    @Test
    fun `deadstock dan opname ikut membaca divisi`() {
        assertTrue(canAccessDeadstock(rolesOf("karyawan", divisi = "admin-stok")))
        assertTrue(canAccessOpname(rolesOf("karyawan", divisi = "admin-stok")))
        assertFalse(canAccessOpname(rolesOf("karyawan")))
    }
}

/**
 * Penjaga REGISTRI menu (`QuickAccessMenus.kt`) — ini bagian yang mencegah
 * masalah "menu tampil padahal 403" terulang oleh kontributor mana pun, bukan
 * sekadar memperbaiki kasus hari ini.
 */
class QuickAccessRegistryTest {

    @Test
    fun `setiap menu menyebut guard backend yang dicerminkan`() {
        QUICK_ACCESS_MENUS.forEach { menu ->
            assertTrue(
                "Menu '${menu.id}' tak menyebut guard backend — tulis file/konstanta aslinya " +
                    "supaya penerus bisa memeriksa ulang saat backend berubah",
                menu.backendGuard.isNotBlank(),
            )
        }
    }

    @Test
    fun `tidak ada role salah ketik di registri`() {
        QUICK_ACCESS_MENUS.forEach { menu ->
            if (menu.allowedRoles == ALL_LOGGED_IN) return@forEach
            val asing = menu.allowedRoles - KNOWN_ROLES
            assertTrue(
                "Menu '${menu.id}' memakai role yang tak dikenal: $asing. Role salah ketik tak " +
                    "akan pernah cocok → menunya hilang diam-diam untuk semua orang",
                asing.isEmpty(),
            )
        }
    }

    @Test
    fun `id menu unik`() {
        val duplikat = QUICK_ACCESS_MENUS.groupBy { it.id }.filterValues { it.size > 1 }.keys
        assertTrue("Id menu ganda: $duplikat", duplikat.isEmpty())
    }

    @Test
    fun `menu terbuka untuk semua harus dinyatakan eksplisit`() {
        // `allowedRoles` kosong = kelalaian (lupa mengisi), BUKAN "untuk semua".
        // Yang memang terbuka wajib memakai ALL_LOGGED_IN supaya terbaca sebagai keputusan.
        QUICK_ACCESS_MENUS.forEach { menu ->
            assertTrue("Menu '${menu.id}' tak menyatakan hak akses apa pun", menu.allowedRoles.isNotEmpty())
        }
    }

    @Test
    fun `profil belum termuat tidak menampilkan menu apa pun`() {
        // Fail-closed: lebih baik grid kosong sesaat daripada menampilkan menu
        // yang ternyata 403 begitu ditekan.
        assertTrue(visibleQuickAccessMenus(emptySet()).isEmpty())
    }

    @Test
    fun `role produksi hanya melihat menu yang backend-nya melayani mereka`() {
        val karyawanSales = setOf("karyawan", "sales")
        val terlihat = visibleQuickAccessMenus(karyawanSales).map { it.id }
        assertTrue("gaji" in terlihat)          // STAFF_ROLES
        assertTrue("klasemen" in terlihat)      // MOBILE_LEADERBOARD_ROLES
        assertTrue("harga_gs" in terlihat)      // require_price_changes_reader memuat karyawan
        assertFalse("serial_input" in terlihat) // hanya admin-stok
        assertFalse("opname" in terlihat)       // has_admin/has_manager tak memuat karyawan

        val crmManager = setOf("crm-manager")
        val terlihatCrm = visibleQuickAccessMenus(crmManager).map { it.id }
        assertFalse("gaji" in terlihatCrm)      // bukan STAFF_ROLES
        assertFalse("klasemen" in terlihatCrm)  // bukan MOBILE_LEADERBOARD_ROLES

        val adminStok = setOf("karyawan", "admin-stok")
        val terlihatStok = visibleQuickAccessMenus(adminStok).map { it.id }
        assertTrue("serial_input" in terlihatStok)
        assertTrue("opname" in terlihatStok)
        assertTrue("deadstock" in terlihatStok)
    }

    /**
     * Ubin **SN Goda** memakai kunci PENAMBAH (`goda.serial.add`, dipisah dari
     * `goda.serial.edit` 2026-09-03), bukan kunci pembaca (`goda.view`) yang
     * jauh lebih luas.
     *
     * Bedanya bukan soal kerapian: gateway `require_goda_access` MELOLOSKAN
     * manager/owner/kepala-cabang ke seluruh rute `/api/goda/…`, dan yang
     * menolak penulisan adalah service-nya. Ubin yang meniru gerbang gateway
     * karena itu akan terbuka untuk mereka, lalu tombol simpannya dijawab 400
     * saat orangnya sudah berdiri di depan unit — layar ini tak punya isi lain
     * selain menulis. **`kepala-cabang` PINDAH KUBU 2026-09-03**: ia dulu
     * pembaca murni (di sini bersama manager/owner), sekarang JUGA penambah
     * (bersama admin-penjualan/kasir yang baru) — TAPI tetap bukan PENGGANTI
     * (`goda.serial.edit` tetap tak menyentuhnya).
     */
    @Test
    fun `SN Goda hanya untuk penulis registry, bukan pembaca List Goda`() {
        assertFalse("goda_serial" in visibleQuickAccessMenus(setOf("manager")).map { it.id })
        assertFalse("goda_serial" in visibleQuickAccessMenus(setOf("owner")).map { it.id })
        assertTrue("goda_serial" in visibleQuickAccessMenus(setOf("karyawan", "admin-stok")).map { it.id })
        assertTrue("goda_serial" in visibleQuickAccessMenus(setOf("superadmin")).map { it.id })
        // 2026-09-03: ketiga role BARU ini kini juga penambah, bukan lagi
        // pembaca murni — lihat dokumentasi di atas.
        for (penambahBaru in listOf("kepala-cabang", "admin-penjualan", "kasir")) {
            assertTrue(
                "`$penambahBaru` harus melihat ubin SN Goda sejak 2026-09-03",
                "goda_serial" in visibleQuickAccessMenus(setOf(penambahBaru)).map { it.id },
            )
        }

        // Peta kemampuan server tetap yang memutuskan saat ia ada.
        val ditolak = visibleQuickAccessMenus(setOf("admin-stok"), mapOf("goda.serial.add" to false))
        assertFalse(ditolak.any { it.id == "goda_serial" })
        val diizinkan = visibleQuickAccessMenus(setOf("karyawan"), mapOf("goda.serial.add" to true))
        assertTrue(diizinkan.any { it.id == "goda_serial" })
    }

    /**
     * Laporan user 2026-08-15: sales & PDI tak menemukan menu Home Service di app.
     *
     * Sebabnya modul komplain NOL pintu masuk di beranda — registri ini tak punya
     * satu pun entrinya, sementara di layar Activity kartu "Lapor Komplain" memang
     * SENGAJA disembunyikan (`hiddenFromActivity`, commit 2344c71, keputusan user
     * yang TIDAK dibatalkan). Layar laporannya hidup, route `home_hs_lapor` hidup,
     * dan tak ada satu pun yang menavigasi ke sana.
     *
     * Test ini mengunci PINTUNYA, bukan sekadar gate-nya: yang berhak melapor harus
     * menemukan ubinnya, dan yang tidak berhak tetap tak melihatnya.
     */
    @Test
    fun `home service punya pintu masuk di Akses Cepat untuk pelapor dan teknisi`() {
        // Sales: melapor boleh, mengerjakan kunjungan tidak.
        val sales = visibleQuickAccessMenus(setOf("sales")).map { it.id }
        assertTrue("komplain_lapor" in sales)
        assertFalse("komplain_tugas" in sales)

        // PDI: keduanya — ia melapor DAN mengerjakan tugas kunjungan.
        val pdi = visibleQuickAccessMenus(setOf("pdi")).map { it.id }
        assertTrue("komplain_lapor" in pdi)
        assertTrue("komplain_tugas" in pdi)

        // Peta kemampuan server yang memutus, bukan daftar role lokal: `pdi` yang
        // kemampuan teknisinya dicabut server kehilangan ubin tugasnya.
        val dicabut = mapOf("spk.pipeline" to true, "homeservice.task" to false)
        val pdiDicabut = visibleQuickAccessMenus(setOf("pdi"), dicabut).map { it.id }
        assertTrue("komplain_lapor" in pdiDicabut)
        assertFalse("komplain_tugas" in pdiDicabut)

        // `ai-engineer` — satu-satunya role yang `spk.pipeline` tutup — kini IKUT
        // boleh melapor (server login-only sejak 2026-08-15), tapi tetap bukan
        // teknisi. Inilah yang membuat `capability` ubin lapor harus `null`:
        // dengan `spk.pipeline` dia akan tertutup lagi.
        val ai = visibleQuickAccessMenus(setOf("ai-engineer")).map { it.id }
        assertTrue("komplain_lapor" in ai)
        assertFalse("komplain_tugas" in ai)
    }

    @Test
    fun `ajukan inden dan cari semua kini terjangkau dari Operasional`() {
        // 2026-07-30: dipindah dari Activity ke sini — pintu masuknya harus
        // tetap ada SATU tempat, bukan hilang total.
        val terlihat = visibleQuickAccessMenus(setOf("manager")).map { it.id }
        assertTrue("indent" in terlihat)
        assertTrue("cari_semua" in terlihat)

        // Cari Semua terbuka untuk siapa pun yang login — layarnya baca cache
        // lokal, tak ada guard backend yang bisa dilanggar.
        assertTrue("cari_semua" in visibleQuickAccessMenus(setOf("driver")).map { it.id })
        assertTrue(
            "cari_semua" in visibleQuickAccessMenus(setOf("driver"), null).map { it.id },
        )

        // Ajukan Inden tetap ber-gate: karyawan biasa tak boleh.
        assertFalse("indent" in visibleQuickAccessMenus(setOf("karyawan")).map { it.id })
    }
}

/**
 * Migrasi ke `GET /api/me/capabilities` (2026-07-27): server yang memutuskan,
 * daftar role lokal tinggal cadangan saat peta itu belum ada.
 */
class CapabilityDrivenMenuTest {

    @Test
    fun `kemampuan server menang atas daftar role lokal`() {
        // Server bilang boleh walau role lokal tak memuatnya (mis. backend
        // melebarkan aksesnya tanpa rilis app baru) → menu muncul.
        val caps = mapOf("opname.view" to true)
        assertTrue(visibleQuickAccessMenus(setOf("karyawan"), caps).any { it.id == "opname" })

        // Sebaliknya: role lokal mengira boleh, server bilang tidak → sembunyi.
        // Inilah yang mencegah menu-tampil-lalu-403 muncul lagi.
        val capsTolak = mapOf("payroll.self" to false)
        assertFalse(visibleQuickAccessMenus(setOf("karyawan"), capsTolak).any { it.id == "gaji" })
    }

    @Test
    fun `kunci absen di peta server dianggap tidak boleh`() {
        // Peta ada tapi kuncinya tak disebut = server tak memberi kemampuan itu.
        val caps = mapOf("payroll.self" to true)
        val ids = visibleQuickAccessMenus(setOf("karyawan"), caps).map { it.id }
        assertTrue("gaji" in ids)
        assertFalse("klasemen" in ids)
    }

    @Test
    fun `tanpa peta server jatuh ke daftar role lokal`() {
        // Offline / server lama: app tetap berguna, memakai cadangan.
        val ids = visibleQuickAccessMenus(setOf("karyawan"), null).map { it.id }
        assertTrue("gaji" in ids)
        assertTrue("klasemen" in ids)
        assertFalse("serial_input" in ids)
    }

    @Test
    fun `menu tanpa kunci kemampuan tetap pakai daftar role`() {
        // Inventory memang tak ber-gate di backend → `capability = null`.
        val inventory = QUICK_ACCESS_MENUS.first { it.id == "inventory" }
        assertTrue(inventory.capability == null)
        assertTrue(inventory.visibleFor(setOf("karyawan"), mapOf("payroll.self" to false)))
    }

    @Test
    fun `setiap menu ber-gate menyebut kunci kemampuan`() {
        // Menu baru wajib punya kunci supaya ikut sumber tunggal; hanya menu
        // yang backend-nya benar-benar terbuka boleh `null`.
        // `kpi` ikut daftar ini: `GET /kpi/me` sama sekali tak memanggil
        // `ensure_role` (scope-nya diri sendiri lewat user_id token), jadi tak
        // ada kunci kemampuan yang bisa dicerminkan. Daftar karyawan di dalam
        // layar itulah yang ber-gate (`kpi.manage`), dan itu dinilai di
        // KpiViewModel, bukan oleh gate menu ini.
        // `komplain_lapor` menyusul 2026-08-15 dengan alasan yang SAMA seperti
        // `kpi`: endpointnya tak memanggil `ensure_role` sama sekali (login-only,
        // self-scoped), jadi kunci apa pun akan lebih sempit dari servernya.
        // `komplain_saya` menyusul 2026-08-28 — pasangan baca dari
        // `komplain_lapor`, dan self-scoped dengan cara yang sama: jalur
        // `sayaLapor` memaksa `pelapor_user_id` = id AKTOR di server dan tak
        // pernah membacanya dari query, jadi ia tak bisa dipakai mengintip
        // laporan orang lain DAN tak punya kunci yang bisa dicerminkan.
        val tanpaKunci = QUICK_ACCESS_MENUS.filter { it.capability == null }.map { it.id }
        assertEquals(
            listOf("kpi", "inventory", "cari_semua", "komplain_lapor", "komplain_saya"),
            tanpaKunci,
        )
    }

    @Test
    fun `kpi hanya manager dan superadmin`() {
        // 2026-08-02: KPI masih diuji, karyawan belum boleh melihat skor
        // dirinya. Gate MENU saja — `/kpi/me` tetap terbuka, jadi ini bukan
        // cerminan guard backend melainkan penyempitan yang disengaja.
        //
        // Tes ini menilai gerbang ROLE-nya saja (`akunUji` dibiarkan default
        // `false`) dan itu SENGAJA: sejak 2026-08-15 ada jalan masuk kedua
        // untuk akun uji — lihat `akun uji menembus gate kpi…` di bawah.
        // Judul "hanya manager dan superadmin" karena itu berarti "hanya role
        // ini", bukan "hanya orang ini".
        assertTrue(visibleQuickAccessMenus(setOf("manager")).any { it.id == "kpi" })
        assertTrue(visibleQuickAccessMenus(setOf("superadmin")).any { it.id == "kpi" })
        assertTrue(visibleQuickAccessMenus(setOf("admin")).any { it.id == "kpi" })
        // Multi-role: cukup salah satu role efektif cocok.
        assertTrue(visibleQuickAccessMenus(setOf("karyawan", "manager")).any { it.id == "kpi" })

        assertFalse(visibleQuickAccessMenus(setOf("karyawan")).any { it.id == "kpi" })
        assertFalse(visibleQuickAccessMenus(setOf("kepala-cabang")).any { it.id == "kpi" })
        assertFalse(visibleQuickAccessMenus(setOf("crm-manager")).any { it.id == "kpi" })
        assertFalse(visibleQuickAccessMenus(setOf("ai-engineer")).any { it.id == "kpi" })
        // Profil belum termuat tetap fail-closed.
        assertFalse(visibleQuickAccessMenus(emptySet()).any { it.id == "kpi" })
    }

    @Test
    fun `akun uji menembus gate kpi tanpa mencabut role yang sudah ada`() {
        // 2026-08-15 (permintaan user): "KPI jangan dulu ditampilkan ke semua
        // karyawan KECUALI akun uji." Gate 2026-08-02 tetap berdiri; ini
        // MENAMBAH satu jalan masuk, bukan mengganti aturan role.
        val karyawan = setOf("karyawan")

        // Sebelum: karyawan biasa tetap tak melihat KPI…
        assertFalse(visibleQuickAccessMenus(karyawan, akunUji = false).any { it.id == "kpi" })
        // …dan akun uji melihatnya walau role-nya `karyawan` murni.
        assertTrue(visibleQuickAccessMenus(karyawan, akunUji = true).any { it.id == "kpi" })

        // Role efektif keluarga akun uji bermacam-macam, jadi jalan tembusnya
        // TIDAK boleh bergantung pada role tertentu. Daftar di bawah adalah
        // role efektif nyata akun uji produksi (diperiksa 2026-08-15 di
        // `auth_users`, baca-saja): dari 11 akun uji, 10 ber-`role` `karyawan`
        // dan satu ber-`role` `owner`; variasinya datang dari kolom `divisi`
        // yang di-fold `effectiveRoles`. Jebakan yang sama sudah dicatat di
        // `AKTIVITAS_INPUT_ROLES` (ActivityRegistry.kt): mengunci ke role justru
        // menghilangkan menu dari akun uji sendiri.
        val roleEfektifAkunUjiProduksi = listOf(
            setOf("karyawan", "sales"),           // UJI Sales
            setOf("karyawan", "pdi", "pic-raport"), // UJI PDI
            setOf("karyawan", "kasir"),           // UJI Kasir
            setOf("karyawan", "driver"),          // UJI Driver / test driver
            setOf("karyawan", "admin-stok"),      // uji admin stok
            setOf("karyawan", "kepala-cabang"),   // UJI Kepala Cabang
            setOf("karyawan", "ai-engineer"),     // Test AI Engineer
            setOf("owner"),                       // test pic
        )
        for (roles in roleEfektifAkunUjiProduksi) {
            assertTrue(
                "akun uji ber-role efektif $roles harus melihat KPI",
                visibleQuickAccessMenus(roles, null, akunUji = true).any { it.id == "kpi" },
            )
        }

        // Peta kemampuan server TIDAK boleh membatalkan jalan tembus: entri
        // `kpi` ber-`capability = null`, jadi `gateAllows` melewati cabang peta
        // sepenuhnya — peta yang ada pun tak menyentuhnya.
        assertTrue(
            visibleQuickAccessMenus(karyawan, mapOf("payroll.self" to false), akunUji = true)
                .any { it.id == "kpi" },
        )
    }

    @Test
    fun `predikat akun uji dan gate menu sepakat untuk profil akun uji produksi`() {
        // Menyambung DUA bagian yang di HomeScreen dipasang berurutan: vonis
        // identitas (`akunUji(name, nik)`) dan gate menu. Test lain memberi
        // flag-nya langsung, jadi tanpa test ini nama yang tak dikenali
        // predikat akan lolos diam-diam — menunya tetap tak muncul di HP.
        //
        // Profil di bawah disalin dari produksi (2026-08-15, `auth_users`,
        // baca-saja). Perhatikan `uji admin stok` HURUF KECIL: predikatnya
        // meng-uppercase dulu, jadi ejaan itu tetap terbaca akun uji.
        val profilUji = listOf(
            Triple("UJI Sales", "11111111", "sales"),
            // `divisi` majemuk apa adanya dari produksi — sekaligus melewati
            // jalur pemisah koma di `effectiveRoles`.
            Triple("UJI PDI", "33333333", "pdi,pic-raport"),
            Triple("uji admin stok", "99999999", "admin-stok"),
            Triple("test driver", "12345678910", "driver"),
            Triple("Test AI Engineer", "90009001", "ai-engineer"),
        )
        for ((nama, nik, divisi) in profilUji) {
            val user = UserDto(
                id = "u", nik = nik, email = "a@b.c", name = nama, role = "karyawan", divisi = divisi,
            )
            assertTrue("$nama harus dikenali akun uji", akunUji(user.name, user.nik))
            assertTrue(
                "$nama harus melihat KPI",
                visibleQuickAccessMenus(effectiveRoles(user), null, akunUji(user.name, user.nik))
                    .any { it.id == "kpi" },
            )
        }

        // Kebalikannya, dan ini yang paling mudah dirusak: predikatnya PREFIKS,
        // bukan `contains`. Kedua baris di bawah ADA di produksi (diperiksa
        // 2026-08-15, baca-saja) dan memuat "Test" BUKAN di awal nama —
        // `contains("TEST")` akan memvonis keduanya akun uji dan membuka skor
        // KPI mereka. Alasan yang sama sudah ditulis di doc-comment `akunUji`
        // (ActivityRegistry.kt) dengan contoh "Puji Astuti".
        val hampirCocok = listOf(
            Triple("Drawer Test 184651", "98184651", "admin-sales"),
            Triple("RoleTest", "91213214", "hrd"),
        )
        for ((nama, nik, divisi) in hampirCocok) {
            val user = UserDto(
                id = "u", nik = nik, email = "a@b.c", name = nama, role = "karyawan", divisi = divisi,
            )
            assertFalse("$nama BUKAN akun uji", akunUji(user.name, user.nik))
            assertFalse(
                "$nama tidak boleh melihat KPI",
                visibleQuickAccessMenus(effectiveRoles(user), null, akunUji(user.name, user.nik))
                    .any { it.id == "kpi" },
            )
        }

        // "Sales Mgr Test" (NIK 123456789, produksi) ber-role `manager`: ia
        // BUKAN akun uji, tapi tetap melihat KPI — lewat gate role 2026-08-02,
        // bukan lewat jalan tembus. Dipisah supaya kalau nanti gate role dicabut
        // tak seorang pun mengira jalan tembus akun-uji yang menutupinya.
        val salesMgr = UserDto(
            id = "u", nik = "123456789", email = "a@b.c", name = "Sales Mgr Test", role = "manager",
        )
        assertFalse(akunUji(salesMgr.name, salesMgr.nik))
        assertTrue(
            visibleQuickAccessMenus(effectiveRoles(salesMgr), null, akunUji = false)
                .any { it.id == "kpi" },
        )
    }

    @Test
    fun `jalan tembus akun uji hanya untuk kpi dan tidak membocorkan menu lain`() {
        // Jalan tembus ini SEMPIT — kalau nanti ada yang menambah id ke
        // [MENU_TAMBAHAN_AKUN_UJI] tanpa niat, tes ini yang berteriak.
        assertEquals(setOf("kpi"), MENU_TAMBAHAN_AKUN_UJI)

        val karyawan = setOf("karyawan")
        val nyata = visibleQuickAccessMenus(karyawan, null, akunUji = false).map { it.id }
        val uji = visibleQuickAccessMenus(karyawan, null, akunUji = true).map { it.id }
        assertEquals(listOf("kpi"), uji - nyata.toSet())

        // Menu ber-gate role lain (mis. Input SN milik admin-stok) tetap
        // tertutup untuk akun uji ber-role karyawan — status akun uji bukan
        // kunci serba-bisa.
        assertFalse("serial_input" in uji)
        assertFalse("indent" in uji)

        // Default fail-closed: pemanggil yang LUPA mengoper `akunUji` harus
        // MENYEMBUNYIKAN, bukan membocorkan. Meniru doc-comment
        // `visibleActivityItems` di ActivityRegistry.kt.
        assertFalse(visibleQuickAccessMenus(karyawan).any { it.id == "kpi" })
        assertFalse(visibleQuickAccessMenus(karyawan, null).any { it.id == "kpi" })

        // Profil belum termuat (role kosong) tetap fail-closed walau flag
        // akun-uji terlanjur menyala — jangan menebak sebelum profil ada.
        assertFalse(visibleQuickAccessMenus(emptySet(), null, akunUji = true).any { it.id == "kpi" })

        // Pemegang gate role 2026-08-02 tetap melihat KPI tanpa perlu jadi akun
        // uji. Assert ini IKUT gagal kalau bentuk PENGURANG
        // `ITEM_KHUSUS_AKUN_UJI` disalin ke sini — tapi bukan dia yang MELAPOR
        // duluan: saat mutasi itu dicoba sungguhan, test berhenti lebih awal di
        // assert `assertEquals(listOf("kpi"), uji - nyata.toSet())` di atas
        // dengan pesan `expected:<[kpi]> but was:<[]>`, sehingga loop di bawah
        // tak pernah dieksekusi. Bentuk aslinya bukan sekadar
        // `id in set && !akunUji` → `false`: di `visibleActivityItems`
        // (ActivityRegistry.kt) syarat itu membuka blok yang memotong KECUALI
        // role ada di `TEMBUS_AKUN_UJI[id]` — jalan tembus yang dipakai
        // `opname_cabang`/`opname_validasi` untuk `OPNAME_PELAKSANA_NYATA`.
        // Yang tersalin ke sini tinggal potongannya, karena `kpi` tak punya
        // entri tembus. DIVERIFIKASI dengan menerapkan bentuk itu sementara
        // (4 test gagal, bersama test lama `kpi hanya manager dan superadmin`).
        for (role in listOf("manager", "superadmin", "admin")) {
            assertTrue(visibleQuickAccessMenus(setOf(role), null, akunUji = false).any { it.id == "kpi" })
        }
    }

    @Test
    fun `menyalakan flag akun uji tidak pernah menghilangkan menu dari siapa pun`() {
        // INVARIAN ARAH — inti perbedaan dengan `ITEM_KHUSUS_AKUN_UJI`, yang
        // MENGURANGI apa yang sudah diluluskan gate role (sebagian pengurangan
        // itu dikembalikan `TEMBUS_AKUN_UJI` untuk `opname_cabang`/
        // `opname_validasi`, tapi arahnya tetap mengurangi). Di sini tak ada
        // pengurangan sama sekali: hasil `akunUji = true` harus selalu SUPERSET
        // dari `akunUji = false`, untuk role apa pun.
        //
        // Berdiri sebagai test SENDIRI, bukan assert terakhir di test lain:
        // saat diletakkan setelah assert fail-closed, kekeliruan yang mengubah
        // jalan tembus jadi EKSKLUSIF (`if (akunUji) return id in set`) sudah
        // menggagalkan assert sebelumnya sehingga invarian ini tak pernah
        // dijalankan. DIVERIFIKASI setelah dipisah: dengan bentuk eksklusif itu
        // test ini gagal dengan "role superadmin kehilangan menu saat akunUji
        // dinyalakan: [gaji, inventory, indent, cari_semua, klasemen]"
        // — superadmin karena ia role pertama di [KNOWN_ROLES], dan kelima menu
        // itu memang SELURUH menu yang dilihatnya lewat cadangan role (di luar
        // `kpi`): superadmin tidak tercantum di OPNAME/HARGA_GS/DEADSTOCK/
        // MUTASI_HISTORI/SERIAL_INPUT_MENU_ROLES.
        val semuaRole = KNOWN_ROLES + "" // "" mewakili role yang tak dikenal registri
        for (role in semuaRole) {
            val tanpa = visibleQuickAccessMenus(setOf(role), null, akunUji = false).map { it.id }
            val dengan = visibleQuickAccessMenus(setOf(role), null, akunUji = true).map { it.id }
            assertTrue(
                "role $role kehilangan menu saat akunUji dinyalakan: ${tanpa - dengan.toSet()}",
                dengan.containsAll(tanpa),
            )
        }
    }
}
