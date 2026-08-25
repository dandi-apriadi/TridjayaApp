package com.krisoft.tridjayaelektronik.ui.activity

// Minor 2 audit final-fix-2: `spkCounterAfterIncrement` pindah ke lapisan
// `data` (satu-satunya pemakainya, `SpkTodayCounter`) — arah dependensi lama
// (data mengimpor ui) kebalik. Test murninya tetap di sini, tinggal impor.
import com.krisoft.tridjayaelektronik.data.model.ProspekTargetDto
import com.krisoft.tridjayaelektronik.data.spkCounterAfterIncrement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivityPlanTest {

    private fun item(id: String) = ACTIVITY_ITEMS.first { it.id == id }

    // ── Kartu antrian ───────────────────────────────────────────────────────

    @Test
    fun `kartu diurut jumlah menurun dan yang nol tetap tampil`() {
        val items = listOf(item("antrian_pdi"), item("aki_saya"))
        val cards = buildQueueCards(
            items = items,
            counts = mapOf(ActivitySource.DLV_PENDING_PDI to 0, ActivitySource.AKI_FORMS_MINE to 3),
            failed = emptySet(),
            effectiveRoles = setOf("pdi"),
        )
        assertEquals(listOf("aki_saya", "antrian_pdi"), cards.map { it.item.id })
        assertEquals(0, cards.last().count)
    }

    @Test
    fun `sumber gagal ditandai dan tak dianggap nol`() {
        val cards = buildQueueCards(
            items = listOf(item("antrian_pdi")),
            counts = emptyMap(),
            failed = setOf(ActivitySource.DLV_PENDING_PDI),
            effectiveRoles = setOf("pdi"),
        )
        assertTrue(cards.single().failed)
        assertEquals(null, cards.single().count)
    }

    @Test
    fun `kartu gagal tidak menjatuhkan kartu lain`() {
        val cards = buildQueueCards(
            items = listOf(item("antrian_pdi"), item("aki_saya")),
            counts = mapOf(ActivitySource.AKI_FORMS_MINE to 2),
            failed = setOf(ActivitySource.DLV_PENDING_PDI),
            effectiveRoles = setOf("pdi"),
        )
        assertEquals(2, cards.size)
        assertEquals(2, cards.first { it.item.id == "aki_saya" }.count)
    }

    // ── Kartu "Konsumen Gebyar" (gerbang CABANG, bukan gerbang angka) ──────
    //
    // Gerbangnya tak bisa dinyatakan sebagai kunci kemampuan: `kupon_gebyar.lihat`
    // hanya tahu ROLE, sementara yang menentukan adalah cabang
    // (`auth_users.cabang_id`) — Manado (D-06 + D-07) di luar program. Karena
    // itu vonisnya datang dari respons `GET /kupon-gebyar/meta`, dan ketiga
    // keadaannya (`true`/`false`/`null`) harus berbeda.

    @Test
    fun `kartu gebyar hilang hanya kalau server memvonis cabangnya di luar program`() {
        val cards = buildQueueCards(
            items = listOf(item("kupon_gebyar")),
            counts = emptyMap(),
            failed = emptySet(),
            effectiveRoles = setOf("karyawan"),
            kuponGebyarBoleh = false,
        )
        assertTrue("Cabang di luar program tak boleh melihat kartunya", cards.isEmpty())
    }

    @Test
    fun `kartu gebyar tetap tampil saat sisanya nol`() {
        // "Semua undangan sudah dikirim" adalah kabar yang berguna. Menyembunyikan
        // kartu yang nol di sini akan membuat menu terasa hilang justru pada
        // cabang yang paling rajin.
        val cards = buildQueueCards(
            items = listOf(item("kupon_gebyar")),
            counts = mapOf(ActivitySource.KUPON_GEBYAR_SISA to 0),
            failed = emptySet(),
            effectiveRoles = setOf("karyawan"),
            kuponGebyarBoleh = true,
        )
        assertEquals(0, cards.single().count)
    }

    @Test
    fun `vonis yang belum diketahui menampilkan kartu bertanda gagal, bukan menyembunyikannya`() {
        // Offline / panggilan gagal. Menyembunyikannya di sini = menu yang
        // lenyap tiap kali sinyal jelek, keluhan yang sudah pernah muncul untuk
        // kartu lain. Arahnya SENGAJA kebalikan `gateAllows`, yang fail-closed:
        // di sana server MENJAWAB dan kuncinya absen; di sini server tak menjawab.
        val cards = buildQueueCards(
            items = listOf(item("kupon_gebyar")),
            counts = emptyMap(),
            failed = setOf(ActivitySource.KUPON_GEBYAR_SISA),
            effectiveRoles = setOf("karyawan"),
            kuponGebyarBoleh = null,
        )
        assertTrue(cards.single().failed)
        assertEquals(null, cards.single().count)
    }

    @Test
    fun `pemanggil yang lupa mengoper vonis tetap menampilkan kartunya`() {
        assertTrue(kuponGebyarCardVisible(null))
        assertTrue(kuponGebyarCardVisible(true))
        assertFalse(kuponGebyarCardVisible(false))
    }

    // ── Kartu SPK Gantung (antrian konfirmasi pembayaran kasir) ─────────────
    //
    // Bug 2026-07-29: angka kartu disaring `isGantung` (>24 jam) DULU, jadi
    // setelah seluruh data SPK produksi dihapus semua baris berumur < 24 jam
    // dan kartunya nol seharian — kasir tak pernah tahu ada yang menunggu.

    /** `nowMillis` tetap supaya test tak bergantung jam mesin. */
    private val now = 1_800_000_000_000L // 2027-01-15 08:00:00 UTC, arbitrer
    private fun jamLalu(n: Long) = utcString(now - n * 60 * 60 * 1000L)

    /**
     * Bentuk yang BENAR-BENAR dikirim backend sejak 2026-07-30: jam dinding
     * WIB tanpa penanda zona. Sengaja TIDAK di-pin ke UTC seperti dulu —
     * helper ber-UTC membuat seluruh uji umur di berkas ini menghitung selisih
     * yang tak pernah terjadi di lapangan (meleset sebesar offset device).
     */
    private fun utcString(millis: Long) = java.text.SimpleDateFormat(
        "yyyy-MM-dd HH:mm:ss", java.util.Locale.US
    ).format(java.util.Date(millis))

    @Test
    fun `SPK baru langsung terhitung walau belum 24 jam`() {
        val r = spkGantungRingkas(listOf(jamLalu(1), jamLalu(5)), nowMillis = now)
        assertEquals(2, r.total)
        assertEquals(0, r.lewatTenggat)
        // Belum ada yang lewat tenggat → kartu memakai subtitle biasa.
        assertEquals(null, spkGantungAlert(r))
    }

    @Test
    fun `yang lewat 24 jam dipisahkan sebagai penanda mendesak`() {
        val r = spkGantungRingkas(listOf(jamLalu(1), jamLalu(30), jamLalu(72)), nowMillis = now)
        assertEquals(3, r.total)
        assertEquals(2, r.lewatTenggat)
        assertEquals("2 lewat tenggat 24 jam", spkGantungAlert(r))
    }

    @Test
    fun `tepat di ambang belum dianggap lewat tenggat`() {
        val tepat = spkGantungRingkas(listOf(jamLalu(24)), nowMillis = now)
        assertEquals(0, tepat.lewatTenggat)
        val lewat = spkGantungRingkas(listOf(utcString(now - GANTUNG_TENGGAT_MS - 1000)), nowMillis = now)
        assertEquals(1, lewat.lewatTenggat)
    }

    @Test
    fun `deliveredAt kosong atau rusak tetap dihitung tapi tak dituduh telat`() {
        val r = spkGantungRingkas(listOf(null, "", "bukan tanggal", jamLalu(48)), nowMillis = now)
        // Pekerjaannya nyata — jangan hilang dari angka cuma karena timestamp tak terbaca.
        assertEquals(4, r.total)
        assertEquals(1, r.lewatTenggat)
    }

    @Test
    fun `daftar kosong berperilaku seperti kartu antrian lain`() {
        val r = spkGantungRingkas(emptyList(), nowMillis = now)
        assertEquals(0, r.total)
        assertEquals(null, spkGantungAlert(r))
        // Nol = kartu redup yang tetap tampil, bukan kartu yang dipaksa muncul.
        val cards = buildQueueCards(
            items = listOf(item("spk_gantung")),
            counts = mapOf(ActivitySource.DLV_PENDING_PAYMENT to r.total),
            failed = emptySet(),
            effectiveRoles = setOf("kasir"),
        )
        assertEquals(0, cards.single().count)
        assertEquals(null, cards.single().alert)
    }

    /**
     * Parser TIDAK boleh memakai `java.time`: minSdk 24 tanpa
     * `coreLibraryDesugaring` → `NoClassDefFoundError` (turunan `Error`, tetap
     * tertangkap `runCatching`) di Android 7.0/7.1, hitungannya nol senyap.
     * Test ini menjaga formatnya tetap terbaca; penjaga API-nya ada di
     * komentar `deliveredAtUtcMillis`.
     */
    @Test
    fun `format kontrak delivery terbaca dengan dan tanpa sufiks Z`() {
        // Sejak 2026-07-30 backend mengirim WIB POLOS tanpa penanda. Bentuk polos
        // dan bentuk ber-`Z` karena itu SENGAJA menghasilkan instan yang BERBEDA:
        // polos = jam dinding device (WIB), ber-`Z` = UTC. Menyamakan keduanya —
        // seperti kontrak lama — membuat tiap nilai baru mundur 7 jam.
        val spasi = deliveredAtUtcMillis("2027-01-14 08:00:00")
        val isoPolos = deliveredAtUtcMillis("2027-01-14T08:00:00")
        val isoZ = deliveredAtUtcMillis("2027-01-14T08:00:00Z")
        val pecahan = deliveredAtUtcMillis("2027-01-14T08:00:00.123Z")
        assertEquals("spasi dan 'T' sama-sama polos", spasi, isoPolos)
        assertEquals("dua bentuk ber-Z sama", isoZ, pecahan)
        val offsetDevice = java.util.TimeZone.getDefault().getOffset(isoZ!!).toLong()
        assertEquals("polos = ber-Z digeser offset device", isoZ - offsetDevice, spasi)
        assertEquals(null, deliveredAtUtcMillis(null))
        assertEquals(null, deliveredAtUtcMillis("   "))
    }

    @Test
    fun `alert kartu dirender dari sumbernya dan tak menimpa kartu gagal`() {
        val alerts = mapOf(ActivitySource.DLV_PENDING_PAYMENT to "2 lewat tenggat 24 jam")
        val ok = buildQueueCards(
            items = listOf(item("spk_gantung")),
            counts = mapOf(ActivitySource.DLV_PENDING_PAYMENT to 3),
            failed = emptySet(),
            effectiveRoles = setOf("kasir"),
            alerts = alerts,
        )
        assertEquals(3, ok.single().count)
        assertEquals("2 lewat tenggat 24 jam", ok.single().alert)

        val gagal = buildQueueCards(
            items = listOf(item("spk_gantung")),
            counts = emptyMap(),
            failed = setOf(ActivitySource.DLV_PENDING_PAYMENT),
            effectiveRoles = setOf("kasir"),
            alerts = alerts,
        )
        // Baris subtitle sudah dipakai pesan "ketuk untuk coba lagi".
        assertEquals(null, gagal.single().alert)
    }

    @Test
    fun `tugas antar disembunyikan saat kosong untuk non-driver`() {
        val items = listOf(item("tugas_antar"))
        val sales = buildQueueCards(items, mapOf(ActivitySource.DLV_AS_DRIVER to 0), emptySet(), setOf("sales"))
        assertTrue(sales.isEmpty())
        val salesPunyaJob = buildQueueCards(items, mapOf(ActivitySource.DLV_AS_DRIVER to 1), emptySet(), setOf("sales"))
        assertEquals(1, salesPunyaJob.size)
        val driver = buildQueueCards(items, mapOf(ActivitySource.DLV_AS_DRIVER to 0), emptySet(), setOf("driver"))
        assertEquals(1, driver.size)
    }

    @Test
    fun `tugas tarik unit disembunyikan saat kosong untuk non-driver`() {
        // BOCOR SAMPAI 2026-08-18: `buildQueueCards` hanya menyaring
        // `DLV_AS_DRIVER`, sehingga kartu ini tampil untuk SETIAP akun yang
        // lolos `spk.pipeline` — yaitu semua kecuali `ai-engineer` — walau ia
        // tak pernah ditugaskan menarik unit sekali pun. Di produksi saat itu
        // NOL tiket berjenis tarik_unit dan NOL tiket punya `tarik_driver_id`,
        // jadi kartunya berangka 0 untuk semua orang, permanen.
        val items = listOf(item("tugas_tarik_unit"))
        val itProgrammer = buildQueueCards(
            items, mapOf(ActivitySource.HS_TUGAS_DRIVER to 0), emptySet(), setOf("karyawan", "it-programmer"),
        )
        assertTrue("akun tanpa tugas tarik tak boleh melihat kartunya", itProgrammer.isEmpty())

        val punyaTugas = buildQueueCards(
            items, mapOf(ActivitySource.HS_TUGAS_DRIVER to 1), emptySet(), setOf("karyawan", "it-programmer"),
        )
        assertEquals("begitu benar-benar ditugaskan, kartunya harus muncul", 1, punyaTugas.size)

        val driver = buildQueueCards(items, mapOf(ActivitySource.HS_TUGAS_DRIVER to 0), emptySet(), setOf("driver"))
        assertEquals("driver tetap melihatnya walau kosong — 'hari ini bersih'", 1, driver.size)
    }

    @Test
    fun `manager owner admin tak pernah melihat tugas tarik unit walau count besar`() {
        val items = listOf(item("tugas_tarik_unit"))
        for (role in listOf("manager", "owner", "admin", "superadmin")) {
            val cards = buildQueueCards(items, mapOf(ActivitySource.HS_TUGAS_DRIVER to 200), emptySet(), setOf(role))
            assertTrue("role '$role' semestinya tak melihat kartu tugas tarik unit", cards.isEmpty())
        }
    }

    @Test
    fun `tugas tarik unit yang gagal dimuat tetap tampil walau bukan driver`() {
        // Gagal != nol, sama seperti Tugas Antar: angka yang tak diketahui tak
        // boleh membuat kartunya hilang diam-diam.
        val items = listOf(item("tugas_tarik_unit"))
        val cards = buildQueueCards(
            items, emptyMap(), setOf(ActivitySource.HS_TUGAS_DRIVER), setOf("karyawan"),
        )
        assertEquals(1, cards.size)
        assertTrue(cards.single().failed)
    }

    @Test
    fun `manager owner admin tak pernah melihat tugas antar walau count besar`() {
        // C2 audit 2026-07-28: `list_delivery` cabang `is_manager || is_admin`
        // mengembalikan SELURUH job perusahaan (mengabaikan asDriver), bukan job
        // milik mereka — angka besar di sini bukan berarti tugas mereka menumpuk.
        val items = listOf(item("tugas_antar"))
        for (role in listOf("manager", "owner", "admin", "superadmin")) {
            val cards = buildQueueCards(items, mapOf(ActivitySource.DLV_AS_DRIVER to 200), emptySet(), setOf(role))
            assertTrue("role '$role' semestinya tak melihat kartu tugas antar", cards.isEmpty())
        }
    }

    @Test
    fun `tugas antar yang gagal dimuat tetap tampil walau bukan driver`() {
        // Gagal != nol: kalau angkanya tak diketahui, kartu tak boleh hilang
        // diam-diam — user harus bisa melihat "—" dan mencoba lagi.
        val cards = buildQueueCards(
            items = listOf(item("tugas_antar")),
            counts = emptyMap(),
            failed = setOf(ActivitySource.DLV_AS_DRIVER),
            effectiveRoles = setOf("sales"),
        )
        assertEquals(1, cards.size)
        assertTrue(cards.single().failed)
        assertEquals(null, cards.single().count)
    }

    // ── Tugas harian ────────────────────────────────────────────────────────

    @Test
    fun `absen pulang baru muncul setelah check-in`() {
        val items = listOf(item("absen_masuk"), item("absen_pulang"))
        val belum = buildDailyTasks(items, checkInAt = null, checkOutAt = null, leadsToday = 0)
        assertEquals(listOf("absen_masuk"), belum.map { it.item.id })

        val sudahMasuk = buildDailyTasks(
            items, checkInAt = "2026-07-28 07:58:00", checkOutAt = null, leadsToday = 0
        )
        assertEquals(listOf("absen_masuk", "absen_pulang"), sudahMasuk.map { it.item.id })
        assertTrue(sudahMasuk.first().done)
        assertFalse(sudahMasuk.last().done)
        assertEquals("07:58", sudahMasuk.first().detail)
    }

    @Test
    fun `prospek selesai bila ada lead hari ini`() {
        val items = listOf(item("prospek"))
        assertFalse(buildDailyTasks(items, null, null, leadsToday = 0).single().done)
        val ada = buildDailyTasks(items, null, null, leadsToday = 2).single()
        assertTrue(ada.done)
        assertEquals("2 lead hari ini", ada.detail)
    }

    @Test
    fun `prospek di bawah target belum tercentang dan tampil aktual per target`() {
        val items = listOf(item("prospek"))
        // `leadsToday` sengaja diisi angka LAIN (5) — kalau ia sampai bocor ke
        // tampilan/centang, berarti klien masih menghitung sendiri.
        val kurang = buildDailyTasks(
            items, null, null, leadsToday = 5,
            prospekTarget = ProspekTargetDto(target = 20, aktual = 3, tercapai = false),
        ).single()
        assertEquals("3/20 prospek", kurang.detail)
        assertFalse(kurang.done)
    }

    @Test
    fun `prospek tercentang begitu server bilang target tercapai`() {
        val items = listOf(item("prospek"))
        val pas = buildDailyTasks(
            items, null, null, leadsToday = 0,
            prospekTarget = ProspekTargetDto(target = 5, aktual = 5, tercapai = true),
        ).single()
        assertEquals("5/5 prospek", pas.detail)
        assertTrue(pas.done)

        // Lebih dari target tetap tercentang, dan angkanya tidak dipotong.
        val lebih = buildDailyTasks(
            items, null, null, leadsToday = 0,
            prospekTarget = ProspekTargetDto(target = 5, aktual = 8, tercapai = true),
        ).single()
        assertEquals("8/5 prospek", lebih.detail)
        assertTrue(lebih.done)
    }

    @Test
    fun `target prospek tak diketahui jatuh ke perilaku lama bukan divonis belum`() {
        val items = listOf(item("prospek"))
        // `null` = panggilan gagal/offline; `target = 0` = setelan tak terbaca.
        // Keduanya TIDAK boleh jadi "0/0" atau "3/0", dan tak boleh membatalkan
        // centang orang yang sudah menginput hari ini.
        for (server in listOf(null, ProspekTargetDto(target = 0, aktual = 0, tercapai = false))) {
            val ada = buildDailyTasks(items, null, null, leadsToday = 2, prospekTarget = server).single()
            assertEquals("2 lead hari ini", ada.detail)
            assertTrue(ada.done)

            val kosong = buildDailyTasks(items, null, null, leadsToday = 0, prospekTarget = server).single()
            assertEquals("belum ada", kosong.detail)
            assertFalse(kosong.done)
            // Tetap masuk penyebut progres — ini bukan "gagal muat".
            assertFalse(kosong.loadFailed)
            assertEquals("0/1", dailyProgressLabel(listOf(kosong)))
        }
    }

    @Test
    fun `raport BETA ikut dihitung dan selesai begitu ada aktivitas terkirim`() {
        val items = listOf(item("absen_masuk"), item("aktivitas"))
        val belum = buildDailyTasks(items, checkInAt = "2026-07-28 08:00:00", checkOutAt = null, leadsToday = 0)
        // Sudah bisa dikerjakan (bukan `comingSoon` lagi) → masuk penyebut.
        assertEquals("1/2", dailyProgressLabel(belum))
        assertEquals("belum", belum.first { it.item.id == "aktivitas" }.detail)

        val terkirim = buildDailyTasks(
            items, checkInAt = "2026-07-28 08:00:00", checkOutAt = null, leadsToday = 0, aktivitasToday = 3
        )
        assertEquals("2/2", dailyProgressLabel(terkirim))
        val raport = terkirim.first { it.item.id == "aktivitas" }
        assertTrue(raport.done)
        assertEquals("3 aktivitas terkirim", raport.detail)
    }

    @Test
    fun `penyebut aktivitas tak diketahui tidak pernah dirender sebagai pecahan`() {
        // Q5: mayoritas karyawan aktif divisinya tak ada di master aktivitas →
        // `matchAktivitasPosition` balikin null. "0/0" akan memvonis mereka belum
        // mengerjakan sesuatu yang memang tak bisa dihitung.
        val items = listOf(item("aktivitas"))
        for (expected in listOf(null, 0)) {
            val belum = buildDailyTasks(items, null, null, leadsToday = 0, aktivitasExpected = expected)
            assertEquals("belum", belum.single().detail)
            assertFalse(belum.single().done)

            val ada = buildDailyTasks(
                items, null, null, leadsToday = 0, aktivitasToday = 3, aktivitasExpected = expected
            )
            assertEquals("3 aktivitas terkirim", ada.single().detail)
            assertTrue(ada.single().done)
        }
    }

    @Test
    fun `penyebut aktivitas diketahui tampil sebagai x per y`() {
        val items = listOf(item("aktivitas"))
        val sebagian = buildDailyTasks(
            items, null, null, leadsToday = 0, aktivitasToday = 3, aktivitasExpected = 7
        )
        assertEquals("3/7 aktivitas", sebagian.single().detail)
        // Centang TETAP "ada minimal satu aktivitas terkirim" — bukan "3 == 7".
        assertTrue(sebagian.single().done)

        val kosong = buildDailyTasks(items, null, null, leadsToday = 0, aktivitasExpected = 7)
        assertEquals("0/7 aktivitas", kosong.single().detail)
        assertFalse(kosong.single().done)
    }

    @Test
    fun `penyebut aktivitas tak menutupi kegagalan memuat raport`() {
        // Penyebut datang dari panggilan LAIN (master aktivitas) — kalau raport hari
        // ini sendiri gagal dimuat, angka pembilangnya tak bisa dipercaya.
        val tasks = buildDailyTasks(
            listOf(item("aktivitas")), null, null, leadsToday = 0,
            aktivitasFailed = true, aktivitasExpected = 7,
        )
        assertEquals("gagal muat", tasks.single().detail)
        assertTrue(tasks.single().loadFailed)
    }

    @Test
    fun `raport gagal dimuat tampil gagal muat dan tak menghukum progres`() {
        val items = listOf(item("aktivitas"))
        val tasks = buildDailyTasks(items, null, null, leadsToday = 0, aktivitasFailed = true)
        assertEquals("gagal muat", tasks.single().detail)
        assertTrue(tasks.single().loadFailed)
        assertEquals("0/0", dailyProgressLabel(tasks))
    }

    @Test
    fun `item coming soon tak dihitung sebagai penyebut progres`() {
        // Tak ada item `comingSoon` tersisa di registri — aturannya tetap diuji
        // lewat salinan item supaya penambah item baru tak kehilangan jaringnya.
        val palsu = item("absen_masuk").copy(id = "nanti", comingSoon = true)
        val tasks = buildDailyTasks(
            listOf(item("absen_masuk"), palsu),
            checkInAt = "2026-07-28 08:00:00", checkOutAt = null, leadsToday = 0
        )
        assertEquals("1/1", dailyProgressLabel(tasks))
        assertEquals("SEGERA", tasks.first { it.item.id == "nanti" }.detail)
    }

    @Test
    fun `absensi gagal dimuat tampil gagal muat bukan belum`() {
        // I3 audit 2026-07-28: gagal jaringan dulu jatuh ke "belum" — tak bisa
        // dibedakan dari benar-benar belum absen, kartunya bisa mendorong user
        // yang sudah check-in untuk absen lagi.
        val items = listOf(item("absen_masuk"), item("absen_pulang"))
        val tasks = buildDailyTasks(
            items, checkInAt = null, checkOutAt = null, leadsToday = 0, absensiFailed = true
        )
        // Absen pulang TETAP tampil walau checkInAt null — gagal-muat bukan
        // "belum check-in", jadi aturan sembunyi-sebelum-check-in tak berlaku.
        assertEquals(listOf("absen_masuk", "absen_pulang"), tasks.map { it.item.id })
        assertTrue(tasks.all { it.detail == "gagal muat" && !it.done && it.loadFailed })
    }

    @Test
    fun `tugas gagal muat tak dihitung di penyebut progres`() {
        val items = listOf(item("absen_masuk"), item("prospek"))
        val tasks = buildDailyTasks(
            items, checkInAt = null, checkOutAt = null, leadsToday = 1, absensiFailed = true
        )
        // absen_masuk gagal (dibuang dari penyebut) → cuma prospek yang dihitung,
        // dan itu sudah selesai (leadsToday > 0) → "1/1", bukan "1/2".
        assertEquals("1/1", dailyProgressLabel(tasks))
    }

    // ── Lead hari ini ───────────────────────────────────────────────────────

    @Test
    fun `hitung lead hari ini milik user`() {
        val leads = listOf(
            "2026-07-28T09:00:00" to "u1",
            "2026-07-28T10:00:00" to "u2",
            "2026-07-27T09:00:00" to "u1",
            "2026-07-28T11:00:00" to null, // cache lama tanpa createdBy
        )
        assertEquals(2, leadsCreatedTodayBy(leads, userId = "u1", todayIso = "2026-07-28"))
        // Tanpa identitas (profil belum termuat) → hitung semua yang hari ini.
        assertEquals(3, leadsCreatedTodayBy(leads, userId = null, todayIso = "2026-07-28"))
    }

    // ── Counter SPK harian ──────────────────────────────────────────────────

    @Test
    fun `counter spk reset saat ganti hari`() {
        assertEquals("2026-07-28" to 1, spkCounterAfterIncrement("2026-07-27", 5, "2026-07-28"))
        assertEquals("2026-07-28" to 6, spkCounterAfterIncrement("2026-07-28", 5, "2026-07-28"))
        assertEquals("2026-07-28" to 1, spkCounterAfterIncrement(null, 0, "2026-07-28"))
    }
}
