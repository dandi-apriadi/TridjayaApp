package com.krisoft.tridjayaelektronik.ui.activity

import com.krisoft.tridjayaelektronik.ui.home.ALL_LOGGED_IN
import com.krisoft.tridjayaelektronik.ui.home.KNOWN_ROLES
import com.krisoft.tridjayaelektronik.ui.navigation.AppDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Penjaga REGISTRI Activity. Layar pertama app menampilkan tugas & antrian
 * milik SIAPA — kalau gate-nya menyimpang dari guard backend, user menekan
 * kartu lalu mendarat di 403 (keluhan berulang: CRM 2026-07-27), atau kartu
 * hilang dari orang yang sebenarnya berhak.
 */
class ActivityRegistryTest {

    @Test
    fun `setiap item menyebut guard backend`() {
        ACTIVITY_ITEMS.forEach {
            assertTrue("Item '${it.id}' tak menyebut guard backend", it.backendGuard.isNotBlank())
        }
    }

    @Test
    fun `tidak ada role salah ketik`() {
        ACTIVITY_ITEMS.forEach { item ->
            if (item.allowedRoles == ALL_LOGGED_IN) return@forEach
            val asing = item.allowedRoles - KNOWN_ROLES
            assertTrue("Item '${item.id}' memakai role tak dikenal: $asing", asing.isEmpty())
        }
    }

    @Test
    fun `id item unik dan hak akses selalu dinyatakan`() {
        val duplikat = ACTIVITY_ITEMS.groupBy { it.id }.filterValues { it.size > 1 }.keys
        assertTrue("Id ganda: $duplikat", duplikat.isEmpty())
        ACTIVITY_ITEMS.forEach {
            assertTrue("Item '${it.id}' tak menyatakan hak akses", it.allowedRoles.isNotEmpty())
        }
    }

    @Test
    fun `hanya tiga item yang boleh tanpa kunci kemampuan`() {
        // raport: hak `upsert_raport` belum punya kunci di /api/me/capabilities.
        // inventory/cari_semua PINDAH ke QUICK_ACCESS_MENUS 2026-07-30 (lihat
        // MenuAccessGateTest.kt) — bukan lagi milik registri ini.
        // `lapor_komplain` menyusul 2026-08-15: jalur pelaporan kinerja-service
        // jadi login-only, jadi tak ada kunci yang bisa dicerminkan tanpa
        // menyempitkan.
        // `pemasangan_ac` menyusul 2026-08-22 dan alasannya BEDA dari dua yang
        // lain: bukan "kuncinya belum ada", melainkan server sengaja tak
        // membuatnya — `tugas-saya` self-scoped, dan anggota tim dipilih
        // per-ORANG sehingga tak ada daftar role yang benar untuk "petugas
        // pemasangan". Yang menyempitkan tampilannya `jabatan`, bukan role.
        // Ketiganya WAJIB menyebutkan alasannya di `backendGuard`.
        val tanpaKunci = ACTIVITY_ITEMS.filter { it.capability == null }.map { it.id }
        assertEquals(listOf("aktivitas", "lapor_komplain", "pemasangan_ac"), tanpaKunci)
    }

    // ── Gate JABATAN (bukan role) ────────────────────────────────────────────

    /**
     * Inti fitur ini. `teknisi` ber-`akses_slugs = '[]'` (migrasi 195) jadi ia tak
     * melipat jadi role apa pun, dan kedua teknisi produksi ber-`role = "karyawan"`.
     * Kalau kartunya digerbangi role, ia mendarat di HP hampir seluruh pegawai.
     */
    @Test
    fun `kartu pemasangan AC hanya untuk pemegang jabatan teknisi`() {
        val karyawanBiasa = visibleActivityItems(setOf("karyawan"), null, divisi = "sales").map { it.id }
        assertFalse("pemasangan_ac" in karyawanBiasa)

        val teknisi = visibleActivityItems(setOf("karyawan"), null, divisi = "teknisi").map { it.id }
        assertTrue("pemasangan_ac" in teknisi)
    }

    /** Jabatan majemuk: satu orang bisa sales merangkap teknisi. */
    @Test
    fun `jabatan majemuk tetap melihat kartu pemasangan AC`() {
        val ids = visibleActivityItems(setOf("karyawan"), null, divisi = "sales, teknisi").map { it.id }
        assertTrue("pemasangan_ac" in ids)
    }

    /**
     * `divisi` default `null` (pemanggil lupa mengoper) harus MENYEMBUNYIKAN,
     * bukan membocorkan — pola sama dengan default `akunUji = false`.
     */
    @Test
    fun `tanpa divisi kartu pemasangan AC disembunyikan`() {
        val ids = visibleActivityItems(setOf("karyawan"), null).map { it.id }
        assertFalse("pemasangan_ac" in ids)
    }

    /**
     * Bahkan superadmin tidak melihatnya: ini kartu KEPEMILIKAN TUGAS, bukan
     * kartu kewenangan. Superadmin yang bukan anggota tim akan membuka layar
     * yang selalu kosong, dan kartu yang tak pernah berisi adalah kartu yang
     * berhenti dibaca.
     */
    @Test
    fun `superadmin tanpa jabatan teknisi juga tidak melihatnya`() {
        val ids = visibleActivityItems(setOf("superadmin"), null, divisi = "manajemen").map { it.id }
        assertFalse("pemasangan_ac" in ids)
    }

    /**
     * Saringan jabatan berjalan DI ATAS gate kemampuan, bukan menggantikannya —
     * peta kemampuan yang ada tak boleh membuka kartu ini untuk non-teknisi.
     */
    @Test
    fun `peta kemampuan tak bisa membuka kartu pemasangan AC untuk non-teknisi`() {
        val caps = mapOf("homeservice.task" to true)
        val ids = visibleActivityItems(setOf("karyawan"), caps, divisi = "sales").map { it.id }
        assertFalse("pemasangan_ac" in ids)
    }

    // ── Item khusus akun uji ─────────────────────────────────────────────────

    @Test
    fun `Input Aktivitas terbuka untuk semua karyawan`() {
        // DIBUKA 2026-08-15 (permintaan user) — pembalikan keempat. Alasannya
        // terukur: selama pintunya ditutup, KPI `LAPORAN AKTIVITAS` tetap
        // menilai orang atas laporan yang tak bisa mereka isi (60 dari 61 orang
        // di bawah 40%). KPI sengaja TIDAK diubah; yang dibuka pintunya.
        // Cerminan web: `aktivitasInputVisible = true` di `DashboardLayout.tsx`.
        val karyawan = visibleActivityItems(setOf("karyawan"), null, akunUji = false).map { it.id }
        assertTrue("karyawan nyata harus melihat kartu Input Aktivitas", "aktivitas" in karyawan)

        val uji = visibleActivityItems(setOf("karyawan"), null, akunUji = true).map { it.id }
        assertTrue("akun uji tetap melihatnya", "aktivitas" in uji)

        // Yang MASIH dipangkas dari orang nyata hanya kedua kartu opname —
        // pembukaan raport tak boleh diam-diam ikut membuka yang lain.
        assertEquals(
            uji.filterNot { it in setOf("opname_cabang", "opname_validasi") },
            karyawan,
        )
    }

    @Test
    fun `Input Aktivitas terlihat dengan role apa pun, uji maupun nyata`() {
        // `AKTIVITAS_INPUT_ROLES = ALL_LOGGED_IN` dan itu memang cerminan backend:
        // `upsert_raport` login-only sejak 2026-08-14. Sejak pintunya dibuka
        // 2026-08-15, akun uji dan orang nyata sama-sama melihatnya — yang
        // dijaga di sini adalah tak ada role yang diam-diam tertinggal.
        listOf("karyawan", "manager", "owner", "kasir", "driver", "hrd", "sales").forEach { role ->
            assertTrue(
                "akun uji ber-role '$role' kehilangan kartu Input Aktivitas",
                "aktivitas" in visibleActivityItems(setOf(role), emptyMap(), akunUji = true).map { it.id },
            )
            assertTrue(
                "orang nyata ber-role '$role' kehilangan kartu Input Aktivitas",
                "aktivitas" in visibleActivityItems(setOf(role), emptyMap(), akunUji = false).map { it.id },
            )
        }
        // Batasnya tetap: profil belum termuat (role kosong) → jangan menebak.
        assertFalse("aktivitas" in visibleActivityItems(emptySet(), null, akunUji = true).map { it.id })
    }

    // ── Antrian PIC raport ───────────────────────────────────────────────────

    @Test
    fun `kartu Nilai Aktivitas memakai kunci raport review`() {
        val kartu = ACTIVITY_ITEMS.first { it.id == "aktivitas_review" }
        assertEquals("aktivitas.review", kartu.capability)
        assertEquals("aktivitas_review", kartu.navKey)
        assertEquals(ActivitySource.AKTIVITAS_REVIEW_PENDING, kartu.source)
        // Nilainya ditulis literal, bukan merujuk konstantanya sendiri: test yang
        // membandingkan konstanta dengan dirinya sendiri selalu hijau.
        //
        // Dipangkas 2026-08-18: `manager`, `kepala-cabang`, `hrd` dicabut atas
        // arahan user (penilaian aktivitas hanya PIC + administrator). Kartu ini
        // adalah tempat kebocoran itu terlihat — Vina Amelia melihatnya karena
        // `extra_roles = kepala-cabang`, bukan karena punya menu web.
        assertEquals(
            setOf("admin", "superadmin", "pic_raport", "pic-raport"),
            kartu.allowedRoles,
        )
    }

    @Test
    fun `peran yang dicabut tak lagi melihat kartu Nilai Aktivitas`() {
        // Dua jalur harus sama-sama tertutup, dan yang KEDUA paling mudah
        // tertinggal karena cuma muncul saat HP offline:
        //   (a) server menjawab kemampuannya false  -> peta kemampuan
        //   (b) peta belum termuat (offline)        -> daftar role lokal
        for (peran in listOf("manager", "kepala-cabang", "hrd")) {
            assertFalse(
                "$peran masih melihat kartu Nilai Aktivitas lewat peta kemampuan",
                "aktivitas_review" in
                    visibleActivityItems(setOf(peran), mapOf("aktivitas.review" to false)).map { it.id },
            )
            assertFalse(
                "$peran masih melihat kartu Nilai Aktivitas saat OFFLINE (cadangan role lokal)",
                "aktivitas_review" in visibleActivityItems(setOf(peran), null).map { it.id },
            )
        }
        // Kontrol positif: yang masih berhak tetap melihatnya di KEDUA jalur.
        for (peran in listOf("pic_raport", "pic-raport", "admin", "superadmin")) {
            assertTrue(
                "$peran kehilangan kartu Nilai Aktivitas saat offline",
                "aktivitas_review" in visibleActivityItems(setOf(peran), null).map { it.id },
            )
        }
    }

    @Test
    fun `PIC melihat antrian penilaian, karyawan biasa tidak`() {
        val pic = visibleActivityItems(setOf("pic_raport"), null).map { it.id }
        assertTrue("aktivitas_review" in pic)
        // Sejak 2026-08-15 PIC melihat KEDUANYA: kartu PENGISIAN (dibuka untuk
        // semua) dan kartu PENILAIAN. Yang dijaga di sini tetap sama seperti
        // dulu — `raport_review` tak boleh ikut tergeser oleh perubahan apa pun
        // pada `raport`; reviewer yang kehilangan antriannya membuat raport
        // orang menumpuk tanpa satu pun error.
        assertTrue("aktivitas" in pic)
        assertTrue(
            "akun uji PIC harus melihat KEDUANYA",
            visibleActivityItems(setOf("pic_raport"), null, akunUji = true)
                .map { it.id }
                .containsAll(listOf("aktivitas", "aktivitas_review")),
        )

        val karyawan = visibleActivityItems(setOf("karyawan"), null).map { it.id }
        assertFalse("aktivitas_review" in karyawan)
    }

    @Test
    fun `owner boleh membaca raport tapi tak boleh menilainya`() {
        // `RAPORT_VIEW_ALL_ROLES` memuat owner, `AKTIVITAS_REVIEW_ROLES` TIDAK —
        // kartunya harus ikut aturan yang kedua, kalau tidak owner menekan
        // Setuju lalu dijawab 403.
        assertFalse("aktivitas_review" in visibleActivityItems(setOf("owner"), null).map { it.id })
    }

    // ── Komplain / Home Service ──────────────────────────────────────────────

    @Test
    fun `lapor komplain disembunyikan dari Activity, triase tetap terpisah`() {
        val caps = mapOf(
            "spk.pipeline" to true, "homeservice.dispatch" to false,
            "homeservice.task" to false, "delivery.control" to false,
        )
        val sales = visibleActivityItems(setOf("sales"), caps).map { it.id }
        assertFalse("lapor_komplain" in sales)
        assertFalse("komplain_masuk" in sales)
        assertFalse("tugas_home_service" in sales)

        assertTrue(ACTIVITY_ITEMS.first { it.id == "lapor_komplain" }.hiddenFromActivity)
    }

    @Test
    fun `petugas triase melihat antrian komplain, pdi melihat tugas teknisi`() {
        val capsTriase = mapOf("homeservice.dispatch" to true, "homeservice.task" to false, "spk.pipeline" to true)
        assertTrue(
            "komplain_masuk" in visibleActivityItems(setOf("delivery-control"), capsTriase).map { it.id }
        )

        val capsPdi = mapOf("homeservice.dispatch" to false, "homeservice.task" to true, "spk.pipeline" to true)
        val pdi = visibleActivityItems(setOf("pdi"), capsPdi).map { it.id }
        assertTrue("tugas_home_service" in pdi)
        assertFalse("komplain_masuk" in pdi)
    }

    @Test
    fun `pelapor komplain kini semua yang login, termasuk hrd`() {
        // Sampai 2026-08-15 cadangan offline menyalin `LAPOR_ROLES` server, dan
        // test ini mengunci bahwa `hrd` TIDAK ada di sana. Servernya kini
        // login-only (permintaan user: "semua karyawan bisa mengajukan komplain
        // konsumen"), jadi arah yang benar berbalik: menahan `hrd` di klien
        // berarti menu hilang dari orang yang servernya justru menerima.
        val kartu = ACTIVITY_ITEMS.first { it.id == "lapor_komplain" }
        assertEquals(ALL_LOGGED_IN, kartu.allowedRoles)
        // `capability` WAJIB null — kunci apa pun lebih sempit dari login-only,
        // dan peta kemampuan fail-closed menyembunyikan kunci yang tak dikenal.
        assertNull(kartu.capability)
        assertTrue(kartu.backendGuard.startsWith("tanpa guard:"))

        // Dua kartu komplain LAIN tetap ber-gate — pelebaran ini tak merembet.
        val triase = ACTIVITY_ITEMS.first { it.id == "komplain_masuk" }
        val tugas = ACTIVITY_ITEMS.first { it.id == "tugas_home_service" }
        assertEquals("homeservice.dispatch", triase.capability)
        assertEquals("homeservice.task", tugas.capability)
    }

    @Test
    fun `role cs tak ditulis di cadangan offline komplain`() {
        // rust-shared: "belum ada role literal `cs` di sistem; sampai ada,
        // orangnya diberi salah satu role di daftar ini". Menulisnya di sini =
        // baris yang tak akan pernah cocok — persis yang dijaga test
        // `tidak ada role salah ketik`. CS sungguhan lolos lewat peta kemampuan.
        ACTIVITY_ITEMS.filter { it.id.startsWith("lapor_komplain") || it.id == "komplain_masuk" }
            .forEach { assertFalse("Item '${it.id}' menulis role hantu 'cs'", "cs" in it.allowedRoles) }
    }

    @Test
    fun `tarik unit memakai kunci delivery control`() {
        // `boleh_atur_tarik` MENGIMPOR DELIVERY_CONTROL_ROLES — tak ada kunci
        // `homeservice.tarik` tersendiri, dan kunci karangan akan menyembunyikan
        // kartunya dari semua orang (peta fail-closed).
        assertEquals("delivery.control", ACTIVITY_ITEMS.first { it.id == "tarik_unit" }.capability)
    }

    /// Server LAMA (belum menyajikan `aktivitas.review`) tetap menyalakan kartu.
    ///
    /// Arah kompatibilitas yang mudah terlupa: aturan `gateAllows` yang lama
    /// menjaga "server menyempitkan akses" — kunci hilang = false. Tapi APK BARU
    /// di atas server LAMA juga menghasilkan kunci hilang, dan di sana artinya
    /// bukan penyempitan melainkan server yang belum tahu ejaan barunya. Tanpa
    /// cadangan `EJAAN_KUNCI_LAMA`, kartu "Nilai Aktivitas" hilang tanpa satu
    /// pun pesan — termasuk milik satu-satunya PIC yang berhak memakainya.
    @Test
    fun `kunci ejaan lama dipakai saat server belum menyajikan ejaan baru`() {
        val serverLama = mapOf("raport.review" to true)
        assertTrue(
            "server lama harus tetap menyalakan kartu lewat cadangan ejaan kunci",
            "aktivitas_review" in visibleActivityItems(setOf("pic_raport"), serverLama).map { it.id },
        )
        // Dan penyempitan dari server LAMA tetap menang — cadangan ini bukan
        // pintu belakang ke daftar role lokal.
        assertFalse(
            "server lama yang menjawab false harus tetap menyembunyikannya",
            "aktivitas_review" in
                visibleActivityItems(setOf("pic_raport"), mapOf("raport.review" to false)).map { it.id },
        )
    }

    @Test
    fun `peta kemampuan server menang atas daftar role lokal`() {
        val caps = mapOf("aktivitas.review" to false)
        assertFalse("aktivitas_review" in visibleActivityItems(setOf("manager"), caps).map { it.id })
    }

    @Test
    fun `akunUji cocok lewat prefiks, bukan substring`() {
        assertTrue(akunUji("UJI Sales", "11111111"))
        assertTrue(akunUji("E2E Approver Test", "990012345"))
        assertTrue(akunUji("test driver", null))
        assertTrue(akunUji("Nama Apa Saja", "990012345"))
        // Orang nyata yang namanya kebetulan memuat kata itu TIDAK boleh kena.
        assertFalse(akunUji("Puji Astuti", "2020010109"))
        assertFalse(akunUji("Kontes Testimoni", "2020010110"))
        assertFalse(akunUji(null, null))
    }

    // ── Gate dua arah per persona ────────────────────────────────────────────

    // Persona di bawah ini sengaja dinilai sebagai AKUN UJI supaya assertion
    // lamanya tetap menguji gate role/kemampuan — bukan ikut tertelan gate
    // akun-uji yang baru.
    private fun ids(vararg roles: String, caps: Map<String, Boolean>? = null) =
        visibleActivityItems(roles.toSet(), caps, akunUji = true).map { it.id }

    @Test
    fun `pdi melihat antrian pdi dan form akinya sendiri`() {
        val caps = mapOf(
            "absensi.self" to true, "pdi.queue" to true, "spk.pipeline" to true,
            "crm.input" to false, "kasir.queue" to false, "delivery.control" to false,
            "aki.approve" to false, "discount.approve" to false,
            "indent.submit" to false, "indent.approve" to false,
        )
        val terlihat = ids("pdi", caps = caps)
        assertTrue("antrian_pdi" in terlihat)
        assertTrue("aki_saya" in terlihat)
        assertFalse("aki_approval" in terlihat)
        assertFalse("antrian_kasir" in terlihat)
        assertFalse("surat_jalan" in terlihat)
    }

    @Test
    fun `kasir hanya melihat antrian kasir dari tahap SPK`() {
        val caps = mapOf(
            "absensi.self" to true, "kasir.queue" to true, "spk.pipeline" to true,
            "pdi.queue" to false, "delivery.control" to false, "aki.approve" to false,
            "discount.approve" to false, "indent.submit" to false, "indent.approve" to false,
            "crm.input" to false,
        )
        val terlihat = ids("kasir", caps = caps)
        assertTrue("antrian_kasir" in terlihat)
        assertFalse("antrian_pdi" in terlihat)
        assertFalse("penjadwalan" in terlihat)
    }

    @Test
    fun `delivery control melihat surat jalan dan penjadwalan`() {
        val caps = mapOf(
            "absensi.self" to true, "delivery.control" to true, "spk.pipeline" to true,
            "pdi.queue" to false, "kasir.queue" to false, "aki.approve" to false,
            "discount.approve" to false, "indent.submit" to false, "indent.approve" to false,
            "crm.input" to false,
        )
        val terlihat = ids("delivery-control", caps = caps)
        assertTrue("surat_jalan" in terlihat)
        assertTrue("penjadwalan" in terlihat)
        assertFalse("antrian_pdi" in terlihat)
    }

    @Test
    fun `approver inden tak mendapat tombol ajukan inden`() {
        // indent.approve = true, indent.submit = false — batas ini yang bikin
        // approver dulu menekan "Ajukan" lalu dijawab 403.
        val caps = mapOf(
            "indent.approve" to true, "indent.submit" to false,
            "absensi.self" to true, "spk.pipeline" to true, "crm.input" to false,
            "pdi.queue" to false, "kasir.queue" to false, "delivery.control" to false,
            "aki.approve" to false, "discount.approve" to false,
        )
        val terlihat = ids("karyawan", "indent-approver", caps = caps)
        assertTrue("approval_inden" in terlihat)
        assertFalse("ajukan_inden" in terlihat)
    }

    @Test
    fun `manager dan owner tak melihat chip buat SPK, karyawan melihatnya`() {
        // C1 audit 2026-07-28: `buat_spk` dulu memakai `spk.pipeline` (manager/
        // owner = true di situ) padahal endpoint `create_delivery` menolak
        // keduanya — chip tampil lalu 403. Dicek dua arah: lewat peta kemampuan
        // server DAN lewat cadangan role offline.
        val capsManagerOwnerDitolak = mapOf(
            "spk.pipeline" to true, "spk.create" to false,
            "absensi.self" to true, "crm.input" to false, "pdi.queue" to false,
            "kasir.queue" to false, "delivery.control" to false, "aki.approve" to true,
            "discount.approve" to false, "indent.submit" to false, "indent.approve" to false,
        )
        assertFalse("buat_spk" in ids("manager", caps = capsManagerOwnerDitolak))
        assertFalse("buat_spk" in ids("owner", caps = capsManagerOwnerDitolak))
        // Cadangan offline (peta kemampuan null) harus sepakat.
        assertFalse("buat_spk" in ids("manager", caps = null))
        assertFalse("buat_spk" in ids("owner", caps = null))

        val capsKaryawanBoleh = mapOf(
            "spk.pipeline" to true, "spk.create" to true,
            "absensi.self" to true, "crm.input" to true, "pdi.queue" to false,
            "kasir.queue" to false, "delivery.control" to false, "aki.approve" to false,
            "discount.approve" to false, "indent.submit" to false, "indent.approve" to false,
        )
        assertTrue("buat_spk" in ids("karyawan", caps = capsKaryawanBoleh))
        assertTrue("buat_spk" in ids("karyawan", caps = null))
    }

    @Test
    fun `pasangan ubin SPK berdampingan dan daftar SPK terbuka untuk manager`() {
        // Seksi PINTASAN dirender dua kolom menurut urutan ACTIVITY_ITEMS —
        // menyelipkan item AKSI lain di antara keduanya memisahkan pasangan ini
        // ke dua baris, persis pemborosan tempat yang dibuang di sini.
        val aksi = ACTIVITY_ITEMS.filter { it.kind == ActivityKind.AKSI }.map { it.id }
        assertEquals(aksi.indexOf("buat_spk") + 1, aksi.indexOf("daftar_spk"))

        // "Daftar SPK" memakai gate BACA, jadi manager/owner (ditolak `spk.create`)
        // tetap punya jalan ke riwayat SPK dari layar pertama.
        val caps = mapOf(
            "spk.pipeline" to true, "spk.create" to false,
            "absensi.self" to true, "crm.input" to false, "pdi.queue" to false,
            "kasir.queue" to false, "delivery.control" to false, "aki.approve" to true,
            "discount.approve" to false, "indent.submit" to false, "indent.approve" to false,
        )
        assertTrue("daftar_spk" in ids("manager", caps = caps))
        assertTrue("daftar_spk" in ids("owner", caps = caps))
        assertTrue("daftar_spk" in ids("karyawan", caps = null))
    }

    // ── Inventory/Cari Semua: pintu masuknya kini di Operasional, bukan di sini
    // ── (2026-07-30) — lihat `ajukan inden dan cari semua kini terjangkau dari
    // ── Operasional` di MenuAccessGateTest.kt. INVENTORY tetap bukan bottom-nav
    // ── item (itu bagian yang TIDAK berubah oleh pemindahan ini).

    @Test
    fun `inventory tetap bukan item bottom nav`() {
        assertFalse(
            "Tombol Cari sudah dihapus — INVENTORY tak boleh kembali ke bottom nav " +
                "tanpa keputusan sadar",
            AppDestination.INVENTORY in AppDestination.bottomNavItems,
        )
        // Destination-nya sendiri WAJIB tetap ada: ia yang meng-host InventoryNavHost.
        assertTrue(AppDestination.INVENTORY.route.isNotBlank())
    }

    @Test
    fun `profil belum termuat tidak menampilkan item apa pun`() {
        // Fail-closed, sama dengan registri Akses Cepat: role kosong berarti
        // profil belum termuat — lebih baik layar kosong sesaat daripada
        // menampilkan kartu yang ternyata 403 begitu ditekan. Berlaku juga
        // untuk item ALL_LOGGED_IN.
        assertTrue(visibleActivityItems(emptySet(), null).isEmpty())
    }

    @Test
    fun `kunci absen dari peta server tetap tersembunyi`() {
        // Fail-closed, sama dengan registri Akses Cepat (spec §8). JANGAN dibalik.
        val terlihat = visibleActivityItems(setOf("pdi"), mapOf("absensi.self" to true)).map { it.id }
        assertTrue("absen_masuk" in terlihat)
        assertFalse("antrian_pdi" in terlihat)
    }

    @Test
    fun `tanpa peta server jatuh ke daftar role lokal`() {
        val terlihat = visibleActivityItems(setOf("kasir"), null).map { it.id }
        assertTrue("antrian_kasir" in terlihat)
        assertTrue("absen_masuk" in terlihat)
        assertFalse("surat_jalan" in terlihat)
    }

    @Test
    fun `validasi opname hanya untuk admin-stok`() {
        // `has_admin_stok` (opname.rs) = SERIAL_INPUT_ROLES = admin-stok SAJA.
        // Kepala cabang yang MENGUSULKAN unitnya sengaja ditolak memvalidasi
        // inputnya sendiri, dan `opname.view` (manager/owner read-only) TIDAK
        // boleh dipakai di sini.
        //
        // `ids()` memakai `akunUji = true`, jadi tes ini mengunci gerbang ROLE-nya
        // saja — lapisan akun-uji di atasnya diuji terpisah (`ActivityOpnameCabangTest`).
        assertTrue("opname_validasi" in ids("admin-stok", caps = mapOf("serial.input" to true)))
        // Admin-stok nyata di produksi umumnya role `karyawan` + divisi admin-stok.
        assertTrue("opname_validasi" in ids("karyawan", "admin-stok", caps = null))
        assertFalse("opname_validasi" in ids("kepala-cabang", caps = mapOf("serial.input" to false)))
        assertFalse("opname_validasi" in ids("manager", caps = null))
        assertFalse("opname_validasi" in ids("karyawan", caps = null))
    }

    // ── Konsumen Gebyar ─────────────────────────────────────────────────────

    /**
     * Kunci `kupon_gebyar.lihat` HARUS yang dipakai — bukan kunci karangan.
     * Peta kemampuan fail-closed: kunci yang tak dikenal server menyembunyikan
     * kartunya dari SEMUA orang, termasuk manager, tanpa satu pun galat. Kunci
     * ini sudah ada di katalog rust-shared (`CAPABILITY_ROLES`) + migrasi 278.
     */
    @Test
    fun `kartu gebyar memakai kunci yang memang disajikan server`() {
        val kartu = ACTIVITY_ITEMS.first { it.id == "kupon_gebyar" }
        assertEquals("kupon_gebyar.lihat", kartu.capability)
        assertEquals("kupon_gebyar", kartu.navKey)
        assertEquals(ActivitySource.KUPON_GEBYAR_SISA, kartu.source)
        assertEquals("home_kupon_gebyar", routeForNavKey(kartu.navKey))
    }

    /**
     * Daftar cadangan offline SENGAJA tak memuat `"cs"` walau server memuatnya
     * (`KUPON_GEBYAR_LIHAT_ROLES`): belum ada role literal `cs` di sistem, jadi
     * ejaan itu tak akan pernah cocok dengan siapa pun dan cuma jadi baris yang
     * tampak seperti jaring pengaman padahal mati. Petugas CS sungguhan tetap
     * lolos lewat peta kemampuan server, yang memang sumber utamanya.
     */
    @Test
    fun `daftar cadangan gebyar tidak memuat role yang tak pernah ada`() {
        assertFalse("cs" in KUPON_GEBYAR_MENU_ROLES)
        assertTrue("karyawan" in KUPON_GEBYAR_MENU_ROLES)
        assertTrue("kepala-cabang" in KUPON_GEBYAR_MENU_ROLES)
    }

    /**
     * Gerbang kemampuan lolos untuk SEMUA karyawan, termasuk yang cabangnya di
     * luar program — dan itu memang tak bisa diperbaiki di sini. Yang menutup
     * Manado adalah vonis `bolehLihat` dari server (`kuponGebyarCardVisible`),
     * bukan daftar role mana pun. Test ini mengunci pembagian kerja itu supaya
     * penerus tak "merapikan" gate-nya dengan mencabut role dan mengira
     * masalahnya beres.
     */
    @Test
    fun `gerbang role gebyar tidak dan tidak bisa menutup cabang`() {
        val caps = mapOf("kupon_gebyar.lihat" to true)
        assertTrue("kupon_gebyar" in ids("karyawan", caps = caps))
        assertTrue("kupon_gebyar" in ids("kepala-cabang", caps = caps))
        // Server yang mencabut kuncinya tetap menang — arah penyempitan dijaga.
        assertFalse("kupon_gebyar" in ids("karyawan", caps = mapOf("kupon_gebyar.lihat" to false)))
    }

    // ── Dedup fan-out ────────────────────────────────────────────────────────

    @Test
    fun `dua item aki hanya menghasilkan satu sumber http`() {
        val items = ACTIVITY_ITEMS.filter { it.id == "aki_saya" || it.id == "aki_approval" }
        assertEquals(2, items.size)
        // Sumber berbeda secara semantik, tapi ViewModel menembak endpoint yang
        // sama sekali — dijaga di ActivityViewModel (Task B3) lewat konstanta ini.
        assertTrue(items.all { it.source.name.startsWith("AKI_FORMS") })
    }

    @Test
    fun `sumber NONE tidak pernah ditembak`() {
        val semua = sourcesToFetch(ACTIVITY_ITEMS)
        assertFalse(ActivitySource.NONE in semua)
    }

    // ── Aturan khusus kartu Tugas Antar (spec §6) ────────────────────────────

    @Test
    fun `tugas antar tampil bila punya job walau bukan role driver`() {
        assertTrue(driverCardVisible(2, setOf("karyawan", "sales")))
        assertFalse(driverCardVisible(0, setOf("karyawan", "sales")))
        assertFalse(driverCardVisible(null, setOf("karyawan", "sales")))
    }

    @Test
    fun `driver selalu melihat kartunya walau kosong`() {
        assertTrue(driverCardVisible(0, setOf("driver")))
        assertTrue(driverCardVisible(null, setOf("driver")))
    }

    @Test
    fun `manager owner admin superadmin tak pernah melihat tugas antar`() {
        // C2 audit 2026-07-28: `list_delivery` cabang is_manager||is_admin
        // mengabaikan asDriver dan mengembalikan seluruh job perusahaan —
        // angka besar untuk role ini bukan tugas miliknya.
        for (role in listOf("manager", "owner", "admin", "superadmin")) {
            assertFalse(driverCardVisible(200, setOf(role)))
            assertFalse(driverCardVisible(0, setOf(role)))
            assertFalse(driverCardVisible(null, setOf(role)))
        }
    }

    // ── Tab awal saat app dibuka (manager/owner → Operasional) ──────────────

    @Test
    fun `manager dan owner mendarat di Operasional`() {
        assertTrue(landsOnSummary(setOf("manager")))
        assertTrue(landsOnSummary(setOf("owner")))
        // Multi-role: cukup salah satu ada di daftar.
        assertTrue(landsOnSummary(setOf("karyawan", "manager")))
    }

    @Test
    fun `role lain tetap mendarat di Activity`() {
        for (role in listOf(
            "karyawan", "sales", "pdi", "kasir", "driver", "delivery-control",
            "admin", "superadmin",
        )) {
            assertFalse("role '$role' seharusnya tetap di Activity", landsOnSummary(setOf(role)))
        }
    }

    @Test
    fun `profil belum termuat tetap mendarat di Activity`() {
        assertFalse(landsOnSummary(emptySet()))
    }

    // ── Pindahan dari QuickAccessRegistryTest ────────────────────────────────
    // Tile CRM & Absen dicabut dari grid Akses Cepat (2026-07-28) karena sudah
    // jadi kartu di sini. Penjaganya ikut pindah — insiden CRM-403 2026-07-27
    // (manager/kepala-cabang/owner melihat menu CRM lalu dijawab 403) tak boleh
    // kehilangan test-nya cuma karena menunya berpindah layar.

    @Test
    fun `prospek hanya untuk yang benar-benar dilayani crm-service`() {
        assertTrue("prospek" in ids("karyawan"))
        assertTrue("prospek" in ids("crm-manager"))
        // 2026-07-29: kepala cabang ikut `CRM_INPUT_ROLES` (rust-shared) —
        // dilayani ter-scope ke lead sendiri, dan `hr_roster` memberinya target
        // prospek harian. Menahannya di sini = target tanpa pintu input.
        assertTrue("prospek" in ids("kepala-cabang"))
        assertFalse("prospek" in ids("manager"))
        assertFalse("prospek" in ids("owner"))
    }

    @Test
    fun `absen untuk staf, bukan crm-manager`() {
        assertTrue("absen_masuk" in ids("karyawan"))
        assertTrue("absen_masuk" in ids("kepala-cabang"))
        // STAFF_ROLES kinerja-service tak memuat crm-manager → absen 403.
        assertFalse("absen_masuk" in ids("crm-manager"))
    }
}

/**
 * Kartu "Opname Cabang" (2026-08-09). Pintu masuk petugas cabang ke sesi opname
 * yang sedang berjalan — dulu opname hanya bisa dijangkau lewat tile Akses
 * Cepat tab Operasional yang gate-nya `opname.view` (pengelola & pemantau saja).
 */
class ActivityOpnameCabangTest {

    private val kartu = ACTIVITY_ITEMS.first { it.id == "opname_cabang" }

    @Test
    fun `memakai kunci opname hitung, bukan opname view`() {
        // `opname.view` menyetir menu Stock Opname di WEB; memakainya di sini
        // berarti kartu ini cuma tampil untuk pengelola — kebalikan maksudnya.
        assertEquals("opname.hitung", kartu.capability)
    }

    @Test
    fun `cadangan offline mencerminkan OPNAME_HITUNG_ROLES di rust-shared`() {
        // Nilainya ditulis literal, bukan merujuk konstantanya sendiri: test
        // yang membandingkan konstanta dengan dirinya sendiri selalu hijau.
        assertEquals(
            setOf("admin", "superadmin", "admin-stok", "kepala-cabang", "karyawan"),
            kartu.allowedRoles,
        )
    }

    @Test
    fun `akun uji melihat Opname Cabang tapi bukan antrian validasi`() {
        // Dua kartu opname, dua audiens: petugas menghitung, admin-stok memutus
        // unit ketik-manual. Dinilai atas AKUN UJI karena orang nyata tak lagi
        // melihat keduanya (lihat tes berikutnya) — tanpa itu tes ini cuma
        // mengukur gate akun-uji dua kali dan gerbang role-nya tak terjaga.
        val uji = visibleActivityItems(setOf("karyawan"), null, akunUji = true).map { it.id }
        assertTrue("opname_cabang" in uji)
        assertFalse("opname_validasi" in uji)
    }

    @Test
    fun `kedua kartu opname disembunyikan dari karyawan biasa, bukan salah satu`() {
        // Permintaan user 2026-08-14 pagi: alur opname per-SN belum boleh
        // terlihat karyawan. KEDUANYA — menutup `opname_cabang` saja
        // meninggalkan antrian validasi terbuka, yaitu sisi lain alur yang sama.
        //
        // Peta kemampuan sengaja diisi `true`: itulah yang server BENAR-BENAR
        // kirim (`opname.hitung` memuat `karyawan`). Tes ini karena itu menahan
        // urutan di `visibleActivityItems` — saringan akun-uji HARUS berjalan
        // sebelum `gateAllows`, kalau dibalik kartunya muncul lagi.
        val caps = mapOf("opname.hitung" to true, "serial.input" to true)
        listOf("karyawan", "manager", "owner", "sales", "driver", "kasir").forEach { role ->
            val nyata = visibleActivityItems(setOf(role), caps, akunUji = false).map { it.id }
            assertFalse(
                "orang biasa ber-role '$role' masih melihat kartu Opname Cabang",
                "opname_cabang" in nyata,
            )
            assertFalse(
                "orang biasa ber-role '$role' masih melihat kartu Validasi Opname",
                "opname_validasi" in nyata,
            )
        }
        // Akun uji tetap melihat keduanya — kalau tidak, fiturnya tak bisa diuji
        // sama sekali di produksi.
        val uji = visibleActivityItems(setOf("admin-stok"), caps, akunUji = true).map { it.id }
        assertTrue(uji.containsAll(listOf("opname_cabang", "opname_validasi")))
    }

    @Test
    fun `pelaksana nyata tetap melihat kartu opname walau bukan akun uji`() {
        // Ditambahkan 2026-08-14 SORE setelah gate pagi harinya terbukti terlalu
        // lebar: admin-stok sudah memakai opname di produksi hari itu juga
        // (sesi OPN-20260814-0001, scan SN 17:46), dan hanya merekalah yang
        // boleh mendaftarkan SN (`SERIAL_INPUT_ROLES = ["admin-stok"]`).
        // Menyembunyikan kartunya = mencabut alat kerja yang sedang dipakai.
        val caps = mapOf("opname.hitung" to true, "serial.input" to true)
        OPNAME_PELAKSANA_NYATA.forEach { role ->
            val nyata = visibleActivityItems(setOf(role), caps, akunUji = false).map { it.id }
            assertTrue(
                "pelaksana nyata ber-role '$role' kehilangan kartu Opname Cabang",
                "opname_cabang" in nyata,
            )
        }
        // Kasus SIDIK yang sebenarnya: role utamanya `karyawan`, `admin-stok`
        // datang dari `extra_roles`. Menilai dari role utama saja akan menolaknya.
        val sidik = visibleActivityItems(setOf("karyawan", "admin-stok"), caps, akunUji = false).map { it.id }
        assertTrue("opname_cabang" in sidik)
        assertTrue("opname_validasi" in sidik)
    }

    @Test
    fun `jalan tembus itu KHUSUS opname, bukan pintu umum`() {
        // Daftar tembus tak boleh dipukul rata ke seluruh ITEM_KHUSUS_AKUN_UJI.
        // `raport` sudah TIDAK di set itu sejak 2026-08-15 (dibuka untuk semua),
        // jadi yang dijaga sekarang: kedua kartu opname tetap TERTUTUP bagi
        // karyawan biasa meski pelaksana nyata menembusnya.
        val caps = mapOf("opname.hitung" to true, "serial.input" to true)
        val karyawanBiasa = visibleActivityItems(setOf("karyawan"), caps, akunUji = false).map { it.id }
        assertFalse("opname_cabang" in karyawanBiasa)
        assertFalse("opname_validasi" in karyawanBiasa)
        OPNAME_PELAKSANA_NYATA.forEach { role ->
            assertTrue(
                "pelaksana nyata ber-role '$role' kehilangan kartu opname",
                "opname_cabang" in visibleActivityItems(setOf(role), caps, akunUji = false).map { it.id },
            )
        }
    }

    @Test
    fun `gate akun uji tidak menyeret kartu lain ikut hilang`() {
        // Kegagalan senyap yang paling mungkin: menambah id ke ITEM_KHUSUS_AKUN_UJI
        // salah ketik / kelebihan, lalu antrian orang lain ikut lenyap tanpa error.
        // Role sengaja `karyawan`, BUKAN `admin-stok`: sejak 2026-08-14 sore
        // pelaksana nyata menembus gate opname, jadi memakai admin-stok di sini
        // membuat selisihnya tinggal `raport` dan tes ini berhenti menjaga
        // kelebihan id di ITEM_KHUSUS_AKUN_UJI — yang justru inti tesnya.
        val caps = mapOf("opname.hitung" to true, "serial.input" to true, "indent.approve" to true)
        val nyata = visibleActivityItems(setOf("karyawan"), caps, akunUji = false).map { it.id }
        val uji = visibleActivityItems(setOf("karyawan"), caps, akunUji = true).map { it.id }
        // `raport` keluar dari set ini 2026-08-15 — selisihnya kini kedua opname saja.
        assertEquals(uji.filterNot { it in setOf("opname_cabang", "opname_validasi") }, nyata)
    }

    @Test
    fun `manager tidak melihat kartu ini bahkan sebagai akun uji`() {
        // Manager/owner pemantau lintas cabang — `authorize_hitung` menolaknya,
        // jadi kartunya tak boleh ada (menu mati = keluhan CRM 2026-07-27).
        // Dinilai dengan `akunUji = true` supaya yang diuji benar-benar gerbang
        // ROLE-nya: dengan `false` tes ini akan hijau walau daftar role-nya
        // dirusak, karena gate akun-uji sudah memangkasnya lebih dulu.
        val manager = visibleActivityItems(setOf("manager"), null, akunUji = true).map { it.id }
        assertFalse("opname_cabang" in manager)
    }

    @Test
    fun `navKey menunjuk layar sesi opname yang sudah ada`() {
        assertEquals("opname", kartu.navKey)
        assertEquals("home_opname", routeForNavKey(kartu.navKey))
    }

    @Test
    fun `angka kartu datang dari daftar sesi draft, bukan antrian validasi`() {
        assertEquals(ActivitySource.OPNAME_SESI_DRAFT, kartu.source)
    }
}

/**
 * Deep-link notifikasi "sesi opname dibuka".
 *
 * Rantainya stringly-typed dan melintasi dua repo: `route_for_kind` (Rust)
 * mengirim navKey, `deliveryNotifRouteKey` (app) menerjemahkan tipe notif jadi
 * navKey yang sama, lalu `routeForNavKey` mengubahnya jadi route. Satu mata
 * rantai meleset = notif yang di-tap tak membuka apa-apa, tanpa error.
 */
class NotifOpnameSesiDibukaTest {

    @Test
    fun `tipe notif menunjuk navKey yang sama dengan kartu Activity`() {
        assertEquals(
            "opname",
            com.krisoft.tridjayaelektronik.ui.notifications.deliveryNotifRouteKey("opname_sesi_dibuka"),
        )
    }

    @Test
    fun `navKey itu punya route`() {
        assertEquals("home_opname", routeForNavKey("opname"))
    }

    @Test
    fun `antrian validasi tetap ke layarnya sendiri`() {
        // Dua kind opname, dua tujuan berbeda — penerimanya juga berbeda.
        assertEquals(
            "opname_validasi",
            com.krisoft.tridjayaelektronik.ui.notifications.deliveryNotifRouteKey("opname_manual_submitted"),
        )
    }
}
