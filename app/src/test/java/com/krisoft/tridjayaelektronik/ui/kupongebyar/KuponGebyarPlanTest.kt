package com.krisoft.tridjayaelektronik.ui.kupongebyar

import com.krisoft.tridjayaelektronik.data.model.KuponGebyarBarisDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Penjaga aturan layar "Konsumen Gebyar".
 *
 * Yang dipertaruhkan bukan kerapian: kupon doorprize dibagikan atas dasar
 * daftar ini, dan tiap kesalahan di sini berakhir sebagai undangan yang salah
 * alamat atau konsumen yang tak pernah diundang siapa pun.
 */
class KuponGebyarPlanTest {

    private fun baris(
        kode: String = "K-1",
        nama: String = "BUDI",
        hp: String? = "081234567890",
        sudahDikirim: Boolean = false,
        dikirimOleh: String = "",
    ) = KuponGebyarBarisDto(
        kodeRekanan = kode,
        nama = nama,
        hp = hp,
        sudahDikirim = sudahDikirim,
        dikirimOleh = dikirimOleh,
    )

    @Test
    fun `boleh kirim hanya bergantung pada sudahDikirim`() {
        assertTrue(bolehKirim(baris()))
        assertFalse(bolehKirim(baris(sudahDikirim = true)))
        // Nomor kosong TIDAK mematikan tombol: server sudah menyaring baris
        // tanpa nomor dari daftar, jadi memeriksanya lagi di klien hanya bisa
        // salah ke arah yang mematikan pekerjaan yang sah.
        assertTrue(bolehKirim(baris(hp = null)))
    }

    @Test
    fun `nomor lokal dinormalkan ke 62`() {
        assertEquals("6281234567890", nomorWa("081234567890"))
        assertEquals("6281234567890", nomorWa("+6281234567890"))
        assertEquals("6281234567890", nomorWa("62 812-3456-7890"))
        assertEquals("6281234567890", nomorWa("81234567890"))
    }

    /**
     * Regresi dari `hp_normal` di `laporan/gebyar/fase0_kandidat_duplikat.py`:
     * `+886902135055` pernah lolos jadi `0886902135055` dan terbaca seperti
     * nomor lokal. Membuka WhatsApp ke sana berarti undangan mendarat di orang
     * asing sementara karyawan mengira tugasnya beres.
     */
    @Test
    fun `nomor luar negeri ditolak`() {
        assertNull(nomorWa("+886902135055"))
        assertNull(nomorWa("+1 415 555 0100"))
    }

    @Test
    fun `nomor yang tak meyakinkan ditolak, bukan ditebak`() {
        assertNull(nomorWa(null))
        assertNull(nomorWa(""))
        assertNull(nomorWa("-"))
        assertNull(nomorWa("12345"))
        // Terlalu pendek walau berawalan 8.
        assertNull(nomorWa("81234"))
        // Terlalu panjang untuk nomor Indonesia mana pun.
        assertNull(nomorWa("6281234567890123456"))
    }

    @Test
    fun `status baris tak pernah menggantung tanpa nama`() {
        assertEquals("Belum dikirim", statusBaris(baris()))
        assertEquals("Sudah dikirim", statusBaris(baris(sudahDikirim = true)))
        assertEquals(
            "Sudah dikirim · SITI",
            statusBaris(baris(sudahDikirim = true, dikirimOleh = "SITI")),
        )
    }

    @Test
    fun `rupiah tak bergantung locale perangkat`() {
        assertEquals("Rp1.500.000", formatRupiahGebyar(1_500_000))
        assertEquals("Rp0", formatRupiahGebyar(0))
        assertEquals("Rp999", formatRupiahGebyar(999))
        assertEquals("Rp1.000", formatRupiahGebyar(1_000))
        // Nilai negatif mustahil dari server, tapi jangan mencetak "Rp-1".
        assertEquals("Rp0", formatRupiahGebyar(-5))
    }

    /**
     * Dihitung dari `total` server, bukan `items.size == pageSize`: halaman
     * terakhir yang kebetulan penuh akan membuat tombol "Muat lagi" tetap
     * tampil lalu menjawab halaman kosong.
     */
    @Test
    fun `halaman berikutnya dihitung dari total server`() {
        assertTrue(adaHalamanLagi(sudahDimuat = 50, total = 120))
        assertFalse(adaHalamanLagi(sudahDimuat = 50, total = 50))
        assertFalse(adaHalamanLagi(sudahDimuat = 0, total = 0))
    }

    // ── Indikator capaian ────────────────────────────────────────────────────

    @Test
    fun `cabang tanpa konsumen berhak tidak dilaporkan nol persen`() {
        // 0% menaruh cabang di puncak daftar "paling perlu dikejar" untuk
        // pekerjaan yang tidak ada. Aturan yang sama dipakai Papan Gebyar.
        assertNull(teksCapaian(sudahDikirim = 0, jumlahKupon = 0, persen = null))
        assertNull(rasioCapaian(null))
    }

    @Test
    fun `persen dibulatkan ke BAWAH`() {
        // 99,6% yang tampil "100%" membuat orang berhenti mencari padahal masih
        // ada konsumen yang belum dikirimi undangan.
        val teks = teksCapaian(sudahDikirim = 249, jumlahKupon = 250, persen = 99.6)
        assertEquals("99% terkirim · 249 dari 250 kupon", teks)
    }

    @Test
    fun `persen dipakai apa adanya dari server, bukan dihitung ulang`() {
        // Kalau app membaginya sendiri ia akan memakai jumlah BARIS sebagai
        // penyebut sementara papan memakai KUPON — dan dua layar berselisih
        // angka tanpa satu pun galat muncul. Di sini server bilang 50% untuk
        // 1 dari 3, dan itulah yang ditampilkan.
        val teks = teksCapaian(sudahDikirim = 1, jumlahKupon = 3, persen = 50.0)
        assertEquals("50% terkirim · 1 dari 3 kupon", teks)
    }

    @Test
    fun `rasio bilah dijepit ke 0f--1f`() {
        assertEquals(0f, rasioCapaian(-5.0))
        assertEquals(1f, rasioCapaian(140.0))
        assertEquals(0.5f, rasioCapaian(50.0))
    }
}
