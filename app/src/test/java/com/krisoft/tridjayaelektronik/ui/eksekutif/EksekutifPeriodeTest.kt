package com.krisoft.tridjayaelektronik.ui.eksekutif

import org.junit.Assert.assertEquals
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale

class EksekutifPeriodeTest {

    private val iso = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private fun millis(tanggal: String): Long = iso.parse(tanggal)!!.time

    @Test
    fun `bulan ini dari tanggal 1 sampai hari ini`() {
        val r = rentangUntuk(EksekutifRentang.BULAN_INI, millis("2026-08-23"))
        assertEquals("2026-08-01", r.start)
        assertEquals("2026-08-23", r.end)
    }

    /**
     * Jebakan yang membuat test ini ada: mengurangi satu bulan DULU lalu
     * memindahkan ke tanggal 1 akan menabrak bulan pendek. `Calendar` memaksa 31
     * Maret dikurangi satu bulan menjadi 3 Maret (Februari tak punya 31 hari),
     * jadi "bulan lalu" akan menunjuk MARET, bukan Februari — dan hanya pada
     * tanggal 29–31. Bug yang muncul tiga hari sebulan adalah bug yang lolos.
     */
    @Test
    fun `bulan lalu benar walau hari ini tanggal 31`() {
        val r = rentangUntuk(EksekutifRentang.BULAN_LALU, millis("2026-03-31"))
        assertEquals("2026-02-01", r.start)
        assertEquals("2026-02-28", r.end)
    }

    @Test
    fun `bulan lalu menyeberang tahun`() {
        val r = rentangUntuk(EksekutifRentang.BULAN_LALU, millis("2026-01-15"))
        assertEquals("2025-12-01", r.start)
        assertEquals("2025-12-31", r.end)
    }

    @Test
    fun `bulan lalu memakai hari terakhir bulan kabisat`() {
        val r = rentangUntuk(EksekutifRentang.BULAN_LALU, millis("2024-03-10"))
        assertEquals("2024-02-01", r.start)
        assertEquals("2024-02-29", r.end)
    }

    /** Rentang INKLUSIF: "7 hari" harus memuat tepat 7 tanggal, bukan 8. */
    @Test
    fun `tujuh hari inklusif di kedua ujung`() {
        val r = rentangUntuk(EksekutifRentang.TUJUH_HARI, millis("2026-08-23"))
        assertEquals("2026-08-17", r.start)
        assertEquals("2026-08-23", r.end)
    }

    @Test
    fun `tiga puluh hari menyeberang bulan`() {
        val r = rentangUntuk(EksekutifRentang.TIGA_PULUH_HARI, millis("2026-08-05"))
        assertEquals("2026-07-07", r.start)
        assertEquals("2026-08-05", r.end)
    }

    @Test
    fun `label rentang meringkas bulan yang sama`() {
        assertEquals(
            "1 – 23 Agustus 2026",
            labelRentang(RentangTanggal("2026-08-01", "2026-08-23")),
        )
    }

    @Test
    fun `label rentang lintas bulan menyebut keduanya`() {
        assertEquals(
            "7 Juli 2026 – 5 Agustus 2026",
            labelRentang(RentangTanggal("2026-07-07", "2026-08-05")),
        )
    }

    /** Tanggal rusak tak boleh menghasilkan label kosong — layar akan terbaca gagal muat. */
    @Test
    fun `label rentang jatuh ke teks mentah saat tanggal tak terbaca`() {
        assertEquals("xx – yy", labelRentang(RentangTanggal("xx", "yy")))
    }

    /**
     * `null` = penanda kesegaran tak terbaca, dan itu BUKAN "baru saja".
     * Menyamarkan ketidaktahuan sebagai kesegaran menghapus satu-satunya tanda
     * bahwa angka di layar mungkin basi.
     */
    @Test
    fun `umur data tak diketahui tidak disamarkan jadi segar`() {
        assertEquals("umur data tidak diketahui", labelKesegaran(null))
        assertEquals("data baru saja disegarkan", labelKesegaran(30))
        assertEquals("data 5 menit lalu", labelKesegaran(300))
        assertEquals("data 2 jam lalu", labelKesegaran(7_200))
        assertEquals("data 1 hari lalu", labelKesegaran(90_000))
    }

    @Test
    fun `rupiah penuh memakai pemisah ribuan`() {
        assertEquals("Rp 0", formatRupiah(0))
        assertEquals("Rp 1.234.567", formatRupiah(1_234_567))
        assertEquals("-Rp 5.000", formatRupiah(-5_000))
    }

    @Test
    fun `rupiah ringkas memilih satuan yang benar`() {
        assertEquals("Rp 999", formatRupiahRingkas(999))
        assertEquals("Rp 1,5 rb", formatRupiahRingkas(1_500))
        assertEquals("Rp 2,25 jt", formatRupiahRingkas(2_250_000))
        assertEquals("Rp 1,23 M", formatRupiahRingkas(1_234_000_000))
        assertEquals("Rp 3 M", formatRupiahRingkas(3_000_000_000))
        // Sisa satu digit WAJIB tetap ber-padding: 1.050 itu "1,05 rb".
        // Memangkasnya jadi "1,5 rb" bukan sekadar jelek — itu angka yang
        // salah sepuluh kali lipat.
        assertEquals("Rp 1,05 rb", formatRupiahRingkas(1_050))
        assertEquals("Rp 1,05 jt", formatRupiahRingkas(1_050_000))
        assertEquals("-Rp 2,5 jt", formatRupiahRingkas(-2_500_000))
    }

    /**
     * `null` → "—", tapi `0.0` TETAP "0%". Keduanya klaim yang berbeda:
     * "belum bisa diukur" vs "diukur dan hasilnya nol", dan di papan yang
     * menilai orang selisih itu bukan hal sepele.
     */
    @Test
    fun `persen membedakan tak terukur dari nol`() {
        assertEquals("—", formatPersen(null))
        assertEquals("0%", formatPersen(0.0))
        assertEquals("83,3%".replace(',', '.'), formatPersen(83.3))
        assertEquals("100%", formatPersen(100.0))
    }
}
