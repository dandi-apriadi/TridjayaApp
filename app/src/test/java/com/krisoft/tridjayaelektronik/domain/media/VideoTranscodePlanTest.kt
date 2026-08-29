package com.krisoft.tridjayaelektronik.domain.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [targetDimensions] cermin `scale='min(1280,iw)':-2` server (`kinerja-service`
 * `video_compress.rs`) — mengecilkan LEBAR ke `min(maxWidth, iw)`, BUKAN mencap sisi terpanjang
 * secara umum. Lihat KDoc fungsi untuk kenapa itu penting: video PORTRAIT dengan lebar ≤ maxWidth
 * TIDAK disusutkan sama sekali, dan itu paritas, bukan bug.
 */
class TargetDimensionsTest {

    /** Landscape lebih lebar dari maxWidth (default 1280) → lebar dicap, tinggi ikut skala. */
    @Test
    fun `landscape lebih lebar dari maxWidth dicap`() {
        val (w, h) = targetDimensions(sourceWidth = 1920, sourceHeight = 1080)
        assertEquals(1280, w)
        assertEquals(720, h)
    }

    /** Landscape yang sudah ≤ maxWidth TAK BERUBAH (server pun tak menyusutkannya). */
    @Test
    fun `landscape di bawah maxWidth tidak berubah`() {
        val (w, h) = targetDimensions(sourceWidth = 1280, sourceHeight = 720)
        assertEquals(1280, w)
        assertEquals(720, h)

        val (w2, h2) = targetDimensions(sourceWidth = 640, sourceHeight = 360)
        assertEquals(640, w2)
        assertEquals(360, h2)
    }

    /**
     * Portrait dengan LEBAR ≤ maxWidth (kasus paling lazim rekaman HP dipegang tegak, mis.
     * 1080×1920) TAK BERUBAH SAMA SEKALI — server memfilter berdasar `iw` (lebar container),
     * dan untuk portrait itu selalu ≤ 1280. Menganggap ini "sisi terpanjang dicap ke 1280" akan
     * salah menyusutkan tingginya (1920→1280) padahal server tak pernah melakukan itu.
     */
    @Test
    fun `portrait dengan lebar di bawah maxWidth tidak berubah sama sekali`() {
        val (w, h) = targetDimensions(sourceWidth = 1080, sourceHeight = 1920)
        assertEquals(1080, w)
        assertEquals(1920, h)
    }

    /**
     * Hasil selalu genap (encoder H.264 YUV 4:2:0 butuh dimensi genap) — termasuk di cabang
     * "tak disusutkan": video 1080×1921 (tinggi GANJIL, bisa terjadi dari sebagian encoder
     * kamera) harus jadi 1080×1920, bukan dibiarkan ganjil.
     */
    @Test
    fun `hasil selalu genap, bahkan tanpa penyusutan`() {
        assertEquals(1080 to 1920, targetDimensions(sourceWidth = 1080, sourceHeight = 1921))

        val (w, h) = targetDimensions(sourceWidth = 1921, sourceHeight = 1081, maxWidth = 1280)
        assertTrue("lebar harus genap: $w", w % 2 == 0)
        assertTrue("tinggi harus genap: $h", h % 2 == 0)
    }

    /** Tak pernah upscale — sumber lebih kecil dari maxWidth tak pernah diperbesar. */
    @Test
    fun `tak pernah upscale`() {
        val (w, h) = targetDimensions(sourceWidth = 320, sourceHeight = 180, maxWidth = 1280)
        assertEquals(320, w)
        assertEquals(180, h)
    }

    /** maxWidth kustom (bukan default 1280) dihormati. */
    @Test
    fun `maxWidth kustom dihormati`() {
        val (w, h) = targetDimensions(sourceWidth = 1920, sourceHeight = 1080, maxWidth = 640)
        assertEquals(640, w)
        assertEquals(360, h)
    }

    /** Dimensi/maxWidth tak masuk akal (0/negatif) dikembalikan apa adanya, tanpa pembagian nol. */
    @Test
    fun `dimensi tak masuk akal dikembalikan apa adanya`() {
        assertEquals(0 to 0, targetDimensions(0, 0))
        assertEquals(-10 to 100, targetDimensions(-10, 100))
        assertEquals(100 to 100, targetDimensions(100, 100, maxWidth = 0))
    }
}
