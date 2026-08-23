package com.krisoft.tridjayaelektronik.ui.aktivitas

import com.krisoft.tridjayaelektronik.data.model.AktivitasItemDto
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

    @Test
    fun `baris dinilai tanpa skor tak ikut rata-rata`() {
        // Baris lama bisa `approved` tanpa kolom skor. Menganggapnya 0 akan
        // menurunkan rata-rata hari itu tanpa sebab.
        val r = ringkasRiwayat(listOf(baris(0, "approved", 100), baris(1, "approved", null)))
        assertEquals(100, r.rataSkor)
        assertEquals(2, r.dinilai)
    }

    @Test
    fun `ringkasan daftar kosong tidak meledak`() {
        val r = ringkasRiwayat(emptyList())
        assertEquals(0, r.total)
        assertNull(r.rataSkor)
    }

    // ── Urutan ───────────────────────────────────────────────────────────────

    @Test
    fun `urut naik menurut nomor aktivitas`() {
        val urut = urutRiwayat(listOf(baris(3, "pending"), baris(0, "pending"), baris(1, "pending")))
        assertEquals(listOf(0, 1, 3), urut.map { it.jobdeskIndex })
    }
}
