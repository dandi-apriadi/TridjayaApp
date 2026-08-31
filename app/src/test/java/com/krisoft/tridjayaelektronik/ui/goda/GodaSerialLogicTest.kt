package com.krisoft.tridjayaelektronik.ui.goda

import com.krisoft.tridjayaelektronik.data.model.GodaBarisDto
import com.krisoft.tridjayaelektronik.data.model.GodaSerialDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Aturan layar SN Goda yang harus SEPAKAT dengan `goda.rs` di server. Yang
 * diuji di sini bukan tampilannya melainkan hal-hal yang kalau meleset TIDAK
 * memunculkan galat apa pun: SN yang lolos beda-huruf, duplikat yang baru
 * ketahuan setelah menekan simpan, dan unit yang hilang dari saringan.
 */
class GodaSerialLogicTest {

    private fun baris(
        kodeBarang: String,
        nama: String = "SEPEDA LISTRIK GODA 123",
        tipe: String = "NEO",
        stok: Long = 3,
        serial: List<String> = emptyList()
    ) = GodaBarisDto(
        kodeDealer = "D-01",
        cabangNama = "Pagaden",
        kodeBarang = kodeBarang,
        namaBarang = nama,
        tipe = tipe,
        stok = stok,
        serials = serial.mapIndexed { i, sn -> GodaSerialDto(id = "id$i", serialNumber = sn) },
        jumlahSn = serial.size.toLong()
    )

    @Test
    fun `rapikanSn memangkas spasi dan menaikkan huruf`() {
        assertEquals("GD1234AB", rapikanSn("  gd1234ab "))
    }

    @Test
    fun `periksaSn menolak kosong dan kepanjangan, membiarkan format bebas`() {
        assertNotNull(periksaSn("   "))
        assertNotNull(periksaSn("X".repeat(65)))
        assertNull(periksaSn("X".repeat(64)))
        // Format TIDAK dipaksakan — registry memuat SN beberapa merk dengan pola
        // berbeda, sama seperti `bersihkan_sn` di server.
        assertNull(periksaSn("gd-2024/09 001"))
    }

    @Test
    fun `duplikat di barang yang sama terdeteksi tanpa peduli besar-kecil huruf`() {
        val b = baris("B1", serial = listOf("GD001"))
        assertTrue(sudahTerdaftarDiBarangIni(b, "GD001"))
        assertTrue(sudahTerdaftarDiBarangIni(b, rapikanSn("gd001")))
        assertFalse(sudahTerdaftarDiBarangIni(b, "GD002"))
    }

    @Test
    fun `SN milik barang lain dilaporkan sebagai peringatan, bukan disembunyikan`() {
        val semua = listOf(baris("B1", serial = listOf("GD001")), baris("B2"))
        // Server TIDAK menolak keadaan ini (unik-nya per dealer+barang+serial),
        // jadi yang wajib ada adalah pemberitahuannya — bukan penolakan.
        val lain = barangLainDenganSn(semua, "GD001", kodeBarangIni = "B2")
        assertEquals("B1", lain?.kodeBarang)
        // Barang yang SN-nya memang miliknya sendiri bukan "barang lain".
        assertNull(barangLainDenganSn(semua, "GD001", kodeBarangIni = "B1"))
    }

    @Test
    fun `belumLengkap membandingkan SN terdaftar dengan stok fisik`() {
        assertTrue(belumLengkap(baris("B1", stok = 3, serial = listOf("A"))))
        assertFalse(belumLengkap(baris("B2", stok = 1, serial = listOf("A"))))
        // SN lebih banyak dari stok = keadaan nyata (unit sudah terjual, SN-nya
        // tetap tercatat). Bukan "belum lengkap", dan bukan galat.
        assertFalse(belumLengkap(baris("B3", stok = 0, serial = listOf("A"))))
    }

    @Test
    fun `pencarian menjangkau nama, kode, dan tipe`() {
        val semua = listOf(
            baris("B1", nama = "SEPEDA LISTRIK GODA 123 NEO", tipe = "NEO"),
            baris("B2", nama = "SEPEDA LISTRIK GODA 140A", tipe = "140A")
        )
        assertEquals(listOf("B1"), saringBaris(semua, "neo", false).map { it.kodeBarang })
        assertEquals(listOf("B2"), saringBaris(semua, "B2", false).map { it.kodeBarang })
        assertEquals(listOf("B2"), saringBaris(semua, "140a", false).map { it.kodeBarang })
        assertEquals(2, saringBaris(semua, "  ", false).size)
    }

    @Test
    fun `saringan belum lengkap menyisakan pekerjaan yang memang tersisa`() {
        val semua = listOf(
            baris("B1", stok = 2, serial = listOf("A")),
            baris("B2", stok = 1, serial = listOf("A"))
        )
        assertEquals(listOf("B1"), saringBaris(semua, "", true).map { it.kodeBarang })
    }
}
