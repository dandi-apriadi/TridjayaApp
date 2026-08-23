package com.krisoft.tridjayaelektronik.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Angka & string harapan ditulis sebagai literal, bukan dihitung ulang lewat
 * fungsi yang sedang diuji — test yang memanggil rumus yang sama dengan kode
 * yang diujinya cuma membuktikan rumus itu konsisten dengan dirinya sendiri.
 */
class SaringanAntrianTest {

    // ── indikatorTerpotong ──────────────────────────────────────────────────
    //
    // Kelas kegagalan yang dijaga di sini: server LAMA tak mengirim `total`.
    // Kalau indikatornya jatuh ke `total ?: items.size`, ia akan mengarang
    // "Menampilkan 200 dari 200" — pernyataan bahwa daftarnya utuh, justru pada
    // server yang tak bisa memastikannya. Diam adalah jawaban yang jujur.

    @Test
    fun `total null tidak merender indikator apa pun`() {
        assertNull(indikatorTerpotong(ditampilkan = 200, total = null))
    }

    @Test
    fun `total sama dengan yang ditampilkan berarti daftar utuh`() {
        assertNull(indikatorTerpotong(ditampilkan = 8, total = 8))
    }

    @Test
    fun `total lebih kecil pun dianggap utuh bukan negatif`() {
        // Bisa terjadi kalau server dan daftar dihitung pada detik berbeda.
        assertNull(indikatorTerpotong(ditampilkan = 10, total = 7))
    }

    @Test
    fun `total lebih besar merender berapa dari berapa`() {
        assertEquals(
            "Menampilkan 200 dari 431 unit",
            indikatorTerpotong(ditampilkan = 200, total = 431),
        )
    }

    @Test
    fun `satuan bisa diganti untuk layar non-unit`() {
        assertEquals(
            "Menampilkan 200 dari 431 SPK",
            indikatorTerpotong(ditampilkan = 200, total = 431, satuan = "SPK"),
        )
    }

    // ── SaringanAntrian.adaYangAktif ────────────────────────────────────────
    //
    // Dipakai empty-state untuk menyebut sebab daftar kosong. Kalau ia salah
    // menjawab `false` saat saringan aktif, pemakai yang menyaring ke satu
    // cabang membaca hasil nol sebagai DATA HILANG, bukan sebagai saringan.

    @Test
    fun `saringan kosong tidak dianggap aktif`() {
        assertFalse(SaringanAntrian.KOSONG.adaYangAktif)
    }

    @Test
    fun `string kosong dan spasi tidak dianggap saringan aktif`() {
        assertFalse(SaringanAntrian(q = "", kodeDealer = "   ").adaYangAktif)
    }

    @Test
    fun `urut terbaru adalah default server jadi bukan saringan aktif`() {
        // Mengirim `urut=terbaru` sama saja dengan tidak mengirim apa-apa —
        // menandainya aktif membuat empty-state menyalahkan saringan yang tak
        // menyaring apa pun.
        assertFalse(SaringanAntrian(urut = SaringanAntrian.URUT_TERBARU).adaYangAktif)
    }

    @Test
    fun `urut terlama adalah pilihan sadar jadi saringan aktif`() {
        assertTrue(SaringanAntrian(urut = SaringanAntrian.URUT_TERLAMA).adaYangAktif)
    }

    @Test
    fun `pencarian dan cabang menandai saringan aktif`() {
        assertTrue(SaringanAntrian(q = "SPK-001").adaYangAktif)
        assertTrue(SaringanAntrian(kodeDealer = "D-01").adaYangAktif)
        assertTrue(SaringanAntrian(deliveryMethod = "self_pickup").adaYangAktif)
    }

    // ── Nilai yang dikirim ke server ────────────────────────────────────────
    //
    // `urut`/`sumbu` adalah SATU-SATUNYA param saringan delivery yang tidak
    // fail-open: nilai asing dijawab 400, bukan diabaikan. Test ini mengunci
    // ejaannya supaya tak ada yang menambah nilai baru di klien sebelum
    // servernya mengenalinya.

    @Test
    fun `nilai urut persis seperti yang diterima server`() {
        assertEquals("terbaru", SaringanAntrian.URUT_TERBARU)
        assertEquals("terlama", SaringanAntrian.URUT_TERLAMA)
    }

    @Test
    fun `pilihan metode kirim persis kosakata server`() {
        assertEquals(
            listOf("driver", "self_pickup", "sales_delivery"),
            SaringanAntrian.DELIVERY_METHOD_PILIHAN.map { it.first },
        )
    }

    // ── KontrolSaringan ─────────────────────────────────────────────────────

    @Test
    fun `kontrol nihil tidak merender bilah saringan`() {
        assertFalse(KontrolSaringan.NIHIL.adaKontrol)
    }

    @Test
    fun `satu kontrol saja sudah cukup merender bilahnya`() {
        assertTrue(KontrolSaringan(cari = true).adaKontrol)
    }
}
