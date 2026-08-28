package com.krisoft.tridjayaelektronik.ui.indent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pesan kegagalan foto bukti indent. Sampai vc116 SELURUH kelas kegagalan di
 * sini — berkas cloud yang belum terunduh, grant Uri yang dicabut, gambar
 * korup, memori habis — dilaporkan dengan satu kalimat yang sama: *"Gagal
 * membaca salah satu foto bukti"*. Kalimat itu tak menyebut foto yang mana
 * (padahal daftarnya bisa berisi lima) dan tak menyebut sebabnya (padahal jalan
 * keluarnya berbeda-beda), lalu MEMBATALKAN seluruh pengajuan.
 */
class IndentFotoPesanTest {

    @Test
    fun `tanpa kegagalan tak ada pesan sama sekali`() {
        assertNull(pesanFotoGagalDisalin(emptyList()))
        assertNull(pesanFotoDilewati(emptyList()))
    }

    @Test
    fun `pesan salin menyebut jumlah, nama berkas, dan sebabnya`() {
        val pesan = pesanFotoGagalDisalin(
            listOf("IMG_20260828.heic (FileNotFoundException)", "nota.jpg (SecurityException)"),
        )!!
        assertTrue(pesan, pesan.startsWith("2 foto"))
        assertTrue(pesan, pesan.contains("IMG_20260828.heic (FileNotFoundException)"))
        assertTrue(pesan, pesan.contains("nota.jpg (SecurityException)"))
    }

    /**
     * Ekor "tekan lagi" WAJIB ada di jalur kirim: pengajuannya memang berhenti,
     * dan tanpa kalimat itu layarnya terbaca sebagai gagal permanen padahal
     * penekanan kedua akan berhasil.
     */
    @Test
    fun `pesan kirim menyebut jalan keluarnya`() {
        val pesan = pesanFotoDilewati(listOf("bukti.png (OutOfMemoryError)"))!!
        assertTrue(pesan, pesan.contains("bukti.png (OutOfMemoryError)"))
        assertTrue(pesan, pesan.contains("Ajukan Indent"))
        assertTrue(pesan, pesan.contains("dilepas dari daftar"))
    }

    /** Jumlahnya dari ukuran daftar, bukan ditulis tangan di pemanggil. */
    @Test
    fun `jumlah mengikuti panjang daftar`() {
        val tiga = pesanFotoDilewati(listOf("a (E)", "b (E)", "c (E)"))!!
        assertEquals("3", Regex("""^(\d+) foto""").find(tiga)!!.groupValues[1])
    }
}
