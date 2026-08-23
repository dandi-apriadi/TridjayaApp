package com.krisoft.tridjayaelektronik.ui.eksekutif

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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

/**
 * Test pilihan periode MANUAL (tanggal / bulan / tahun / rentang bebas) dan
 * pemotongan ujung ke hari ini.
 */
class EksekutifPilihanPeriodeTest {

    private val iso = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private fun millis(tanggal: String): Long = iso.parse(tanggal)!!.time

    @Test
    fun `hari ini dan kemarin adalah satu tanggal`() {
        val ini = rentangUntuk(EksekutifRentang.HARI_INI, millis("2026-08-23"))
        assertEquals("2026-08-23", ini.start)
        assertEquals("2026-08-23", ini.end)

        val kemarin = rentangUntuk(EksekutifRentang.KEMARIN, millis("2026-08-23"))
        assertEquals("2026-08-22", kemarin.start)
        assertEquals("2026-08-22", kemarin.end)
    }

    /** Kemarin di tanggal 1 harus mundur ke bulan sebelumnya, bukan ke tanggal 0. */
    @Test
    fun `kemarin menyeberang bulan dan tahun`() {
        val awalBulan = rentangUntuk(EksekutifRentang.KEMARIN, millis("2026-03-01"))
        assertEquals("2026-02-28", awalBulan.start)

        val awalTahun = rentangUntuk(EksekutifRentang.KEMARIN, millis("2026-01-01"))
        assertEquals("2025-12-31", awalTahun.start)
    }

    @Test
    fun `tahun ini mulai 1 januari sampai hari ini`() {
        val r = rentangUntuk(EksekutifRentang.TAHUN_INI, millis("2026-08-23"))
        assertEquals("2026-01-01", r.start)
        assertEquals("2026-08-23", r.end)
    }

    /**
     * Plafon server 366 hari harus muat untuk SETIAP hari tahun berjalan,
     * termasuk 31 Desember tahun kabisat. Kalau tidak, chip "Tahun ini" akan
     * dijawab 400 tepat di hari terakhir tahun — hari yang tak seorang pun uji.
     */
    @Test
    fun `tahun ini muat plafon server di 31 desember kabisat`() {
        val r = rentangUntuk(PilihanPeriode.Preset(EksekutifRentang.TAHUN_INI), millis("2024-12-31"))
        assertEquals("2024-01-01", r.start)
        assertEquals("2024-12-31", r.end)
        assertEquals(null, validasiRentang(r.start, r.end))
    }

    /**
     * Bulan BERJALAN dipotong hari ini.
     *
     * Tanpa pemotongan, server memasukkan hari kerja yang belum terjadi ke
     * penyebut kehadiran & kepatuhan — angka satu perusahaan anjlok tiap tanggal
     * muda, tanpa satu pun sebab yang terlihat.
     */
    @Test
    fun `bulan berjalan dipotong hari ini`() {
        val r = rentangUntuk(PilihanPeriode.Bulan("2026-08"), millis("2026-08-23"))
        assertEquals("2026-08-01", r.start)
        assertEquals("2026-08-23", r.end)
    }

    @Test
    fun `bulan yang sudah lewat utuh sebulan penuh`() {
        val r = rentangUntuk(PilihanPeriode.Bulan("2026-02"), millis("2026-08-23"))
        assertEquals("2026-02-01", r.start)
        assertEquals("2026-02-28", r.end)
    }

    @Test
    fun `tahun berjalan dipotong hari ini tapi tahun lalu utuh`() {
        val berjalan = rentangUntuk(PilihanPeriode.Tahun("2026"), millis("2026-08-23"))
        assertEquals("2026-01-01", berjalan.start)
        assertEquals("2026-08-23", berjalan.end)

        val lalu = rentangUntuk(PilihanPeriode.Tahun("2025"), millis("2026-08-23"))
        assertEquals("2025-01-01", lalu.start)
        assertEquals("2025-12-31", lalu.end)
    }

    @Test
    fun `tanggal tunggal dan rentang kustom diteruskan apa adanya`() {
        val satu = rentangUntuk(PilihanPeriode.Tanggal("2026-08-12"), millis("2026-08-23"))
        assertEquals("2026-08-12", satu.start)
        assertEquals("2026-08-12", satu.end)

        val kustom = rentangUntuk(
            PilihanPeriode.Kustom("2026-07-05", "2026-08-10"),
            millis("2026-08-23"),
        )
        assertEquals("2026-07-05", kustom.start)
        assertEquals("2026-08-10", kustom.end)
    }

    /** Rentang yang ujungnya di masa depan dipotong; yang SELURUHNYA di masa depan tidak. */
    @Test
    fun `ujung masa depan dipotong tapi rentang masa depan penuh dibiarkan`() {
        val separuh = rentangUntuk(
            PilihanPeriode.Kustom("2026-08-01", "2026-12-31"),
            millis("2026-08-23"),
        )
        assertEquals("2026-08-23", separuh.end)

        // Tak ada yang bisa dipotong di sini, dan jawaban nol memang benar.
        val penuh = rentangUntuk(
            PilihanPeriode.Kustom("2026-09-01", "2026-09-30"),
            millis("2026-08-23"),
        )
        assertEquals("2026-09-01", penuh.start)
        assertEquals("2026-09-30", penuh.end)
    }

    /** Kunci bulan/tahun ngawur JATUH KE BAWAAN, bukan melempar. */
    @Test
    fun `kunci tak sah jatuh ke bawaan bukan crash`() {
        assertEquals(null, batasBulan("2026-13"))
        assertEquals(null, batasBulan("bukan"))
        assertEquals(null, batasTahun("19xx"))
        assertEquals(null, batasTahun("1800"))

        val r = rentangUntuk(PilihanPeriode.Bulan("2026-99"), millis("2026-08-23"))
        assertEquals("2026-08-01", r.start)
        assertEquals("2026-08-23", r.end)
    }

    @Test
    fun `validasi menolak rentang terbalik dan yang melewati plafon`() {
        assertEquals(null, validasiRentang("2026-08-01", "2026-08-23"))
        assertEquals(null, validasiRentang("2026-08-23", "2026-08-23"))

        assertNotNull(validasiRentang("2026-08-23", "2026-08-01"))
        // 367 hari inklusif — satu hari di atas plafon.
        assertNotNull(validasiRentang("2025-08-23", "2026-08-24"))
        // 366 hari inklusif — pas di plafon, harus LOLOS.
        assertEquals(null, validasiRentang("2025-08-24", "2026-08-24"))
        assertNotNull(validasiRentang("bukan-tanggal", "2026-08-01"))
    }

    /**
     * Konversi picker WAJIB lewat UTC.
     *
     * `DatePickerState` menormalkan tiap pilihan ke tengah malam UTC. Membaca
     * miliknya dengan formatter LOKAL menggeser tanggalnya satu hari selama
     * 00:00–06:59 WIB — kelas kesalahan yang sudah pernah terjadi di form izin.
     */
    @Test
    fun `konversi picker bolak-balik menjaga tanggal`() {
        listOf("2026-01-01", "2026-08-23", "2024-02-29", "2026-12-31").forEach { t ->
            val utc = isoKeUtcMidnight(t)
            assertNotNull(utc)
            assertEquals(t, utcMidnightKeIso(utc!!))
        }
        assertEquals(null, isoKeUtcMidnight("bukan-tanggal"))
        // Tanggal yang TAK ADA ditolak, bukan digeser diam-diam ke 2 Maret.
        assertEquals(null, isoKeUtcMidnight("2026-02-31"))
        assertEquals(null, isoKeUtcMidnight("2026-13-01"))
        assertEquals(null, isoKeUtcMidnight("2026-08"))
    }

    /**
     * Konversinya tak boleh bergeser walau zona device berubah di tengah sesi.
     *
     * `SimpleDateFormat` membekukan `TimeZone.getDefault()` saat dibuat,
     * sedangkan `Calendar.getInstance()` membaca zona yang HIDUP — dua zona bisa
     * bercampur di dalam satu fungsi yang tugasnya justru menjamin satu zona.
     * Karena itu `isoKeUtcMidnight` mengurai string secara leksikal.
     */
    @Test
    fun `konversi picker tak bergantung zona device`() {
        val asli = java.util.TimeZone.getDefault()
        try {
            listOf("Pacific/Kiritimati", "Pacific/Midway", "UTC", "Asia/Jakarta").forEach { zona ->
                java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone(zona))
                assertEquals(
                    "gagal di zona $zona",
                    "2026-08-23",
                    utcMidnightKeIso(isoKeUtcMidnight("2026-08-23")!!),
                )
            }
        } finally {
            java.util.TimeZone.setDefault(asli)
        }
    }

    @Test
    fun `label pilihan menyebut periode yang sedang aktif`() {
        assertEquals("Hari ini", labelPilihan(PilihanPeriode.Preset(EksekutifRentang.HARI_INI)))
        assertEquals("Tahun 2025", labelPilihan(PilihanPeriode.Tahun("2025")))
        assertEquals("Rentang khusus", labelPilihan(PilihanPeriode.Kustom("2026-08-01", "2026-08-10")))
        // Tanggal & bulan memakai nama bulan Indonesia.
        assertEquals("23 Agustus 2026", labelPilihan(PilihanPeriode.Tanggal("2026-08-23")))
        assertEquals("Agustus 2026", labelPilihan(PilihanPeriode.Bulan("2026-08")))
    }

    /** Pita kepatuhan sama dengan ambang halaman aktivitas web (80 / 50). */
    @Test
    fun `pita kepatuhan memakai ambang yang sama dengan web`() {
        assertEquals(PitaKepatuhan.PRIMA, pitaKepatuhan(80.0))
        assertEquals(PitaKepatuhan.PANTAU, pitaKepatuhan(79.9))
        assertEquals(PitaKepatuhan.PANTAU, pitaKepatuhan(50.0))
        assertEquals(PitaKepatuhan.PRIORITAS, pitaKepatuhan(49.9))
        assertEquals(PitaKepatuhan.PRIORITAS, pitaKepatuhan(0.0))
        // `null` BUKAN buruk — ia belum terukur.
        assertEquals(PitaKepatuhan.TAK_TERUKUR, pitaKepatuhan(null))
    }

    /** `null` → "—", nol tetap "0". Sama seperti `formatPersen`. */
    @Test
    fun `format skor membedakan belum terukur dari nol`() {
        assertEquals("—", formatSkor(null))
        assertEquals("0", formatSkor(0.0))
        assertEquals("62.5", formatSkor(62.5))
        assertEquals("100", formatSkor(100.0))
        assertEquals("35%", formatBobot(0.35))
        assertEquals("100%", formatBobot(1.0))
        assertEquals("0%", formatBobot(0.0))
    }
}
