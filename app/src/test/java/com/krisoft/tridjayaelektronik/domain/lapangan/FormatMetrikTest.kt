package com.krisoft.tridjayaelektronik.domain.lapangan

import com.krisoft.tridjayaelektronik.data.model.MetrikLapanganDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Aturan penyajian papan lapangan WAJIB sama dengan sisi web
 * (`frontend/src/utils/klasemenLapangan.ts` + spec-nya): satu papan yang dibaca
 * dua klien tak boleh menampilkan angka berbeda bentuk, karena orang yang sama
 * membandingkan posisinya di HP dan di layar kantor.
 */
class FormatMetrikTest {

    private fun metrik(
        satuan: String = "persen",
        nilai: Double? = 97.5,
        pembilang: Long? = 78,
        penyebut: Long? = 80,
    ) = MetrikLapanganDto(
        kunci = "penuntasan",
        label = "Beban tuntas",
        satuan = satuan,
        nilai = nilai,
        pembilang = pembilang,
        penyebut = penyebut,
        menentukanPeringkat = true,
    )

    @Test
    fun `nilai kosong dirender em dash, bukan nol`() {
        assertEquals("—", FormatMetrik.nilai(metrik(nilai = null)))
        // Nol berarti "diukur dan hasilnya nol" — beda arti, harus beda tampilan.
        assertEquals("0,0%", FormatMetrik.nilai(metrik(nilai = 0.0)))
    }

    @Test
    fun `NaN diperlakukan kosong, bukan dicetak NaN`() {
        assertEquals("—", FormatMetrik.nilai(metrik(nilai = Double.NaN)))
    }

    @Test
    fun `satuan datang dari server, bukan ditebak dari kunci`() {
        assertEquals("97,5%", FormatMetrik.nilai(metrik(satuan = "persen", nilai = 97.5)))
        assertEquals("117 unit", FormatMetrik.nilai(metrik(satuan = "unit", nilai = 117.0)))
        assertEquals("14,6 hari", FormatMetrik.nilai(metrik(satuan = "hari", nilai = 14.6)))
        assertEquals("8,7", FormatMetrik.nilai(metrik(satuan = "rasio", nilai = 8.7)))
        // Satuan tak dikenal (server lebih baru) jatuh ke angka biasa, bukan crash.
        assertEquals("8,7", FormatMetrik.nilai(metrik(satuan = "satuan-baru", nilai = 8.7)))
    }

    @Test
    fun `pecahan menampilkan sampelnya supaya penyebut kecil kelihatan`() {
        assertEquals("1 dari 2", FormatMetrik.pecahan(metrik(pembilang = 1, penyebut = 2)))
        assertNull(FormatMetrik.pecahan(metrik(pembilang = null)))
        assertNull(FormatMetrik.pecahan(metrik(penyebut = null)))
    }

    @Test
    fun `tiga besar bermedali, sisanya bernomor`() {
        assertEquals("🥇", FormatMetrik.lencana(1))
        assertEquals("🥉", FormatMetrik.lencana(3))
        assertEquals("4", FormatMetrik.lencana(4))
        assertEquals("11", FormatMetrik.lencana(11))
    }

    @Test
    fun `periode dirender bahasa Indonesia dan yang ngawur apa adanya`() {
        assertEquals("Agustus 2026", FormatMetrik.periode("2026-08"))
        assertEquals("Desember 2026", FormatMetrik.periode("2026-12"))
        assertEquals("2026-13", FormatMetrik.periode("2026-13"))
        assertEquals("bukan-periode", FormatMetrik.periode("bukan-periode"))
        assertEquals("2026", FormatMetrik.periode("2026"))
    }
}
