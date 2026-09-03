package com.krisoft.tridjayaelektronik.ui.aktivitas

import com.krisoft.tridjayaelektronik.data.model.AktivitasItemDto
import com.krisoft.tridjayaelektronik.data.model.AktivitasPositionDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AktivitasRiwayatPlanTest {

    private fun baris(
        index: Int,
        status: String,
        skor: Int? = null,
    ) = AktivitasItemDto(
        id = "id-$index-$status",
        jobdeskIndex = index,
        jobdeskText = "Aktivitas $index",
        reviewStatus = status,
        score = skor,
    )

    // ── Gerbang baca (dua arah) ──────────────────────────────────────────────

    @Test
    fun `role yang boleh membaca daftar melihat tombol riwayat`() {
        listOf("karyawan", "trainee", "manager", "owner", "admin", "superadmin", "pic_raport", "pic-raport")
            .forEach { role ->
                assertTrue("$role harus boleh", bolehLihatRiwayat(setOf(role)))
            }
    }

    @Test
    fun `role di luar LIST_ROLES tidak melihat tombol riwayat`() {
        // Arah yang menutup "menu mati": kartu Input Aktivitas terbuka untuk
        // SEMUA yang login (AKTIVITAS_INPUT_ROLES = ALL_LOGGED_IN), tapi
        // GET /raport-harian menolak role-role ini dengan 403.
        listOf("sales", "kasir", "driver", "admin-penjualan", "pdi", "delivery-control", "agent")
            .forEach { role ->
                assertFalse("$role tak boleh", bolehLihatRiwayat(setOf(role)))
            }
    }

    @Test
    fun `akun multi-role berhak lewat role sekunder`() {
        // Kalau gerbangnya membaca role UTAMA saja, orang ini kehilangan
        // riwayatnya sendiri padahal server menerimanya.
        assertTrue(bolehLihatRiwayat(setOf("sales", "karyawan")))
    }

    @Test
    fun `tanpa role sama sekali fail-closed`() {
        assertFalse(bolehLihatRiwayat(emptySet()))
    }

    // ── Batas maju ───────────────────────────────────────────────────────────

    @Test
    fun `mundur sehari bebas`() {
        assertEquals("2026-08-22", geserTanggalRiwayat("2026-08-23", -1, "2026-08-23"))
    }

    @Test
    fun `maju berhenti di hari ini`() {
        // Inti gerbangnya: dari hari ini, tombol maju tak boleh melompat ke
        // besok — barisnya mustahil ada dan layarnya akan bilang "belum ada
        // aktivitas" untuk tanggal yang memang belum tiba.
        assertEquals("2026-08-23", geserTanggalRiwayat("2026-08-23", 1, "2026-08-23"))
    }

    @Test
    fun `maju dari masa lalu tetap boleh sampai hari ini`() {
        assertEquals("2026-08-21", geserTanggalRiwayat("2026-08-20", 1, "2026-08-23"))
    }

    @Test
    fun `lompatan besar ke depan dijepit ke hari ini`() {
        assertEquals("2026-08-23", geserTanggalRiwayat("2026-08-22", 30, "2026-08-23"))
    }

    @Test
    fun `bolehMaju mati tepat di hari ini`() {
        assertFalse(bolehMajuTanggal("2026-08-23", "2026-08-23"))
        assertTrue(bolehMajuTanggal("2026-08-22", "2026-08-23"))
    }

    @Test
    fun `pergantian bulan tak merusak geseran`() {
        // Perbandingannya leksikografis, tapi PENGGESERANNYA aritmetika
        // kalender — kalau suatu saat diganti jadi manipulasi string, kasus ini
        // yang pertama pecah.
        assertEquals("2026-07-31", geserTanggalRiwayat("2026-08-01", -1, "2026-08-23"))
        assertEquals("2026-09-01", geserTanggalRiwayat("2026-08-31", 1, "2026-09-05"))
    }

    // ── Ringkasan ────────────────────────────────────────────────────────────

    @Test
    fun `ringkasan menghitung tiap status`() {
        val r = ringkasRiwayat(
            listOf(
                baris(0, "approved", 100),
                baris(1, "approved", 100),
                baris(2, "rejected", 0),
                baris(3, "pending"),
            )
        )
        assertEquals(4, r.total)
        assertEquals(2, r.disetujui)
        assertEquals(1, r.ditolak)
        assertEquals(1, r.menunggu)
        assertEquals(3, r.dinilai)
    }

    @Test
    fun `rata-rata hanya atas baris yang sudah dinilai`() {
        // Yang dijaga: baris `pending` TIDAK boleh masuk penyebut. Kalau ia
        // ikut, hari yang PIC-nya belum sempat menilai terlihat seperti hari
        // berkinerja buruk — vonis yang belum pernah dijatuhkan siapa pun.
        val r = ringkasRiwayat(
            listOf(
                baris(0, "approved", 100),
                baris(1, "rejected", 0),
                baris(2, "pending"),
                baris(3, "pending"),
            )
        )
        assertEquals(50, r.rataSkor)
    }

    @Test
    fun `rata-rata null saat belum ada yang dinilai`() {
        val r = ringkasRiwayat(listOf(baris(0, "pending"), baris(1, "pending")))
        assertNull(r.rataSkor)
        assertEquals(2, r.menunggu)
        assertEquals(0, r.dinilai)
    }

    /**
     * **Ekspektasi DIBALIK 2026-08-28 (dulu `100`), dan pembalikannya yang
     * benar — bukan test yang dilonggarkan.**
     *
     * Versi lama beralasan "menganggapnya 0 akan menurunkan rata-rata hari itu
     * tanpa sebab". Sebabnya ADA, dan ada di backend: pembilangnya
     * `SUM(CASE WHEN review_status IN ('rejected','pending') THEN 0 ELSE
     * COALESCE(r.score, 0) END)` (`kpi/mysql.rs`), jadi baris `approved` tanpa
     * skor menyumbang 0 sambil TETAP memakai slotnya di penyebut. Selama layar
     * ini memaafkannya, ia memajang 100 untuk hari yang DIBAYAR 50 — dan angka
     * yang membayar itulah yang benar.
     */
    @Test
    fun `baris approved tanpa skor dihitung nol, sama seperti backend`() {
        val r = ringkasRiwayat(listOf(baris(0, "approved", 100), baris(1, "approved", null)))
        assertEquals(50, r.rataSkor)
        assertEquals(2, r.dinilai)
    }

    @Test
    fun `ringkasan daftar kosong tidak meledak`() {
        val r = ringkasRiwayat(emptyList())
        assertEquals(0, r.total)
        assertNull(r.rataSkor)
    }

    // ── Penyebut = TAGIHAN (cerminan raport_daily_nilai) ─────────────────────

    private fun barisBerPosisi(index: Int, status: String, skor: Int?, positionId: String?) =
        baris(index, status, skor).copy(kpiPositionId = positionId)

    private fun divisi(id: String, butir: Int, nonaktif: List<Int> = emptyList()) =
        AktivitasPositionDto(
            id = id,
            posisi = id,
            jobdesks = (1..butir).map { "Butir $it" },
            nonaktif = nonaktif,
        )

    /**
     * INTI perbaikan 2026-08-28. Orang yang mengisi 2 dari 8 butir dulu tampil
     * bernilai PENUH di HP (penyebutnya cuma baris yang sudah dinilai),
     * sementara skor yang dibayarkan memakai 8. Tanpa `expected` dari master,
     * tak ada komponen lain yang bisa tahu ada 6 butir yang tak diisi sama
     * sekali — `maxIndex+1` dan `items.size` sama-sama cuma melihat baris yang ADA.
     */
    @Test
    fun `butir yang tak diisi sama sekali tetap masuk penyebut`() {
        val items = listOf(
            barisBerPosisi(0, "approved", 100, "sales"),
            barisBerPosisi(1, "approved", 100, "sales"),
        )
        val positions = listOf(divisi("sales", butir = 8))
        // slot = max(8, 1+1, 2, 1) = 8; pending 0 -> penyebut 8; (100+100)/8 = 25
        assertEquals(8, penyebutRiwayat(items, positions))
        assertEquals(25, ringkasRiwayat(items, positions).rataSkor)
        // Tanpa master: jatuh ke perilaku lama (penyebut 2) — nilai terlalu tinggi,
        // tapi itu batas jujur dari data yang ada, bukan angka karangan.
        assertEquals(100, ringkasRiwayat(items, emptyList()).rataSkor)
    }

    /**
     * Butir NONAKTIF berhenti ditagih server (`jumlah_butir_aktif`), jadi tak
     * boleh ikut penyebut di sini. Memakai `jobdesks.size` penuh membuat layar
     * memajang 10/13 = 77% untuk orang yang dibayar atas 10/12 = 83%.
     */
    @Test
    fun `butir nonaktif tidak ikut penyebut`() {
        val items = listOf(barisBerPosisi(0, "approved", 100, "sales"))
        val positions = listOf(divisi("sales", butir = 8, nonaktif = listOf(6, 7)))
        assertEquals(6, penyebutRiwayat(items, positions))
    }

    /**
     * Nomor nonaktif di LUAR rentang disaring — ia PENGURANG penyebut, jadi satu
     * nomor liar dari katalog lama membuat penyebut lebih kecil dari tagihan
     * sebenarnya, tanpa satu pun error.
     */
    @Test
    fun `nomor nonaktif di luar rentang diabaikan`() {
        val items = listOf(barisBerPosisi(0, "approved", 100, "sales"))
        val positions = listOf(divisi("sales", butir = 4, nonaktif = listOf(-1, 4, 99)))
        assertEquals(4, penyebutRiwayat(items, positions))
    }

    /**
     * Cerminan `raport_daily_nilai`: `slot − pending`. SENGAJA berbeda dari web
     * (`summarizeAktivitasScore`) yang tak mengurangi pending — penyimpangan web
     * itu sudah tercatat dan tak boleh disalin ke kanal kedua.
     */
    @Test
    fun `pending dikurangi dari penyebut, bukan ditanggung`() {
        val items = listOf(
            barisBerPosisi(0, "approved", 100, "sales"),
            barisBerPosisi(1, "pending", null, "sales"),
            barisBerPosisi(2, "pending", null, "sales"),
        )
        val positions = listOf(divisi("sales", butir = 5))
        // slot = max(5, 2+1, 3, 1) = 5; pending 2 -> 3
        assertEquals(3, penyebutRiwayat(items, positions))
        assertEquals(33, ringkasRiwayat(items, positions).rataSkor)
    }

    /**
     * Penempatan yang posisinya memang belum punya divisi aktivitas (produksi
     * 2026-08-15: teknisi, digital-team, crm, admin-gudang) TIDAK ditebak ke
     * divisi lain — penyebutnya jatuh ke batas bawah data nyata, sama seperti
     * backend. Menebak berarti menilai orang atas tagihan divisi orang lain.
     */
    @Test
    fun `posisi tanpa divisi aktivitas jatuh ke batas bawah, bukan ditebak`() {
        val items = listOf(barisBerPosisi(0, "approved", 100, "posisi-asing"))
        val positions = listOf(divisi("sales", butir = 8))
        assertEquals(1, penyebutRiwayat(items, positions))
    }

    // ── Urutan ───────────────────────────────────────────────────────────────

    @Test
    fun `urut naik menurut nomor aktivitas`() {
        val urut = urutRiwayat(listOf(baris(3, "pending"), baris(0, "pending"), baris(1, "pending")))
        assertEquals(listOf(0, 1, 3), urut.map { it.jobdeskIndex })
    }
}
