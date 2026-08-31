package com.krisoft.tridjayaelektronik.ui.chatdeteksi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatDeteksiPresentationTest {

    @Test
    fun `tanpa video tidak boleh kirim`() {
        val gate = bolehKirim(adaVideo = false, ukuranBytes = 0)
        assertFalse(gate.ok)
    }

    @Test
    fun `ukuran tak terbaca (0) tetap boleh kirim`() {
        // Sebagian ContentProvider mengembalikan kolom SIZE null -> 0. Menolaknya
        // mengunci karyawan dari fitur ini tanpa jalan keluar.
        val gate = bolehKirim(adaVideo = true, ukuranBytes = 0)
        assertTrue(gate.ok)
    }

    @Test
    fun `video pas di batas boleh kirim`() {
        assertTrue(bolehKirim(adaVideo = true, ukuranBytes = MAX_VIDEO_BYTES).ok)
    }

    @Test
    fun `video lewat batas ditolak`() {
        val gate = bolehKirim(adaVideo = true, ukuranBytes = MAX_VIDEO_BYTES + 1)
        assertFalse(gate.ok)
        assertTrue(gate.alasan!!.contains("20 MB"))
    }

    @Test
    fun `label status mencerminkan tiga keadaan final plus default`() {
        assertEquals("Sedang diproses", statusLabel("pending_review"))
        assertEquals("Lolos", statusLabel("lolos_otomatis"))
        assertEquals("Belum memenuhi target", statusLabel("tidak_lolos_otomatis"))
        assertEquals("Belum pernah mengirim", statusLabel("nilai_tak_dikenal"))
    }

    @Test
    fun `pending_review tanpa error bukan kegagalan`() {
        assertFalse(gagalPerluKirimUlang("pending_review", null))
        assertFalse(gagalPerluKirimUlang("pending_review", ""))
    }

    @Test
    fun `pending_review dengan error adalah kegagalan yang perlu kirim ulang`() {
        assertTrue(gagalPerluKirimUlang("pending_review", "9router mengembalikan status 403"))
    }

    @Test
    fun `status final dengan error historis tidak dianggap gagal (server sudah putuskan)`() {
        // Kolom llmError hanya direset saat simpan_hasil sukses (lihat mysql.rs);
        // status final berarti keputusannya sudah ada, apa pun isi error lama.
        assertFalse(gagalPerluKirimUlang("lolos_otomatis", "error lama yang tak lagi relevan"))
    }

    @Test
    fun `format ukuran berkas`() {
        assertEquals("ukuran tak terbaca", formatUkuranBerkas(0))
        assertEquals("500 KB", formatUkuranBerkas(500 * 1024))
        assertEquals("2.0 MB", formatUkuranBerkas(2L * 1024 * 1024))
    }
}
