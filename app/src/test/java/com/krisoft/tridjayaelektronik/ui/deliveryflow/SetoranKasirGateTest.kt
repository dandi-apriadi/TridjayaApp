package com.krisoft.tridjayaelektronik.ui.deliveryflow

import com.krisoft.tridjayaelektronik.data.model.DeliveryJobDto
import com.krisoft.tridjayaelektronik.data.model.DeliveryStatusKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Gerbang kirim "Konfirmasi Pembayaran Diterima".
 *
 * Yang dijaga di sini adalah KESEPAKATAN DENGAN SERVER, bukan tampilan tombol:
 * `record_kasir_setoran` (inventory-service `delivery.rs`) menolak
 * `nominal_diterima <= 0.0`, dan klien pernah memakai `>= 0` sehingga tombolnya
 * aktif di Rp 0. Penyimpangan itu tak menghasilkan error klien apa pun — foto
 * ter-upload lebih dulu, lalu server menjawab 400 dan pekerjaan kasir tak pernah
 * selesai. Tak ada pemeriksa kompiler lintas repo untuk kontrak ini; test inilah
 * penggantinya.
 */
class SetoranKasirGateTest {

    // ── nominal: cerminan `nominal_diterima <= 0.0` di server ────────────────

    @Test
    fun `nol ditolak walau foto sudah ada - server menolaknya 400`() {
        val gate = setoranKasirGate("0", adaFoto = true)
        assertFalse(gate.bolehKirim)
        assertEquals("Isi nominal yang diterima", gate.label)
    }

    @Test
    fun `nol berangka banyak juga ditolak`() {
        assertFalse(setoranKasirGate("000", adaFoto = true).bolehKirim)
    }

    @Test
    fun `kosong ditolak - kolomnya wajib`() {
        assertFalse(setoranKasirGate("", adaFoto = true).bolehKirim)
    }

    @Test
    fun `nominal wajar diterima`() {
        val gate = setoranKasirGate("5750000", adaFoto = true)
        assertTrue(gate.bolehKirim)
        assertEquals("Konfirmasi Pembayaran", gate.label)
    }

    @Test
    fun `satu rupiah diterima - ambangnya lebih besar dari nol, bukan angka lain`() {
        assertTrue(setoranKasirGate("1", adaFoto = true).bolehKirim)
    }

    // ── foto ─────────────────────────────────────────────────────────────────

    @Test
    fun `tanpa foto ditolak walau nominal sudah benar`() {
        val gate = setoranKasirGate("5750000", adaFoto = false)
        assertFalse(gate.bolehKirim)
        assertEquals("Ambil foto bukti dulu", gate.label)
    }

    /**
     * Urutan pesan, bukan selera: memotret jauh lebih lama daripada mengetik
     * angka, jadi menagihnya belakangan membuat kasir mengetik nominal lalu baru
     * diberi tahu harus keluar memotret.
     */
    @Test
    fun `keduanya kurang - foto ditagih lebih dulu`() {
        assertEquals("Ambil foto bukti dulu", setoranKasirGate("", adaFoto = false).label)
    }

    // ── masukan yang tak wajar tak boleh lolos jadi kiriman ───────────────────

    @Test
    fun `bukan angka ditolak - bukan dianggap nol lalu dikirim`() {
        assertFalse(setoranKasirGate("abc", adaFoto = true).bolehKirim)
    }

    /**
     * `MoneyTextField` menyaring ke digit saja, jadi ini pertahanan berlapis:
     * `toDoubleOrNull` menerima "Infinity" dan "-1", dan keduanya tak boleh
     * pernah sampai ke `nominalDiterima`.
     */
    @Test
    fun `negatif dan tak hingga ditolak`() {
        assertFalse(setoranKasirGate("-1", adaFoto = true).bolehKirim)
        assertFalse(setoranKasirGate("Infinity", adaFoto = true).bolehKirim)
        assertFalse(setoranKasirGate("NaN", adaFoto = true).bolehKirim)
    }

    // ── se-SPK: satu foto, satu tombol, nominal tetap per unit ───────────────

    private fun baris(vararg nominal: String) =
        nominal.mapIndexed { i, n -> SetoranBaris(id = "job-$i", nominalMentah = n) }

    @Test
    fun `daftar kosong ditolak - tak ada yang bisa dikirim`() {
        val r = setoranSpkRencana(emptyList(), adaFoto = true)
        assertFalse(r.bolehKirim)
        assertEquals("Tak ada barang menunggu setoran", r.label)
    }

    @Test
    fun `tanpa foto ditolak walau seluruh nominal sudah benar`() {
        val r = setoranSpkRencana(baris("5000000", "3000000"), adaFoto = false)
        assertFalse(r.bolehKirim)
        assertEquals("Ambil foto bukti dulu", r.label)
    }

    /**
     * Inti fitur ini: SATU foto menutup seluruh SPK, jadi tiga barang menghasilkan
     * tiga kiriman dari satu kali potret — bukan tiga kali potret.
     */
    @Test
    fun `tiga barang lengkap jadi tiga kiriman dari satu foto`() {
        val r = setoranSpkRencana(baris("5000000", "3000000", "1250000"), adaFoto = true)
        assertTrue(r.bolehKirim)
        assertEquals("Konfirmasi Pembayaran 3 Barang", r.label)
        assertEquals(listOf("job-0", "job-1", "job-2"), r.kiriman.map { it.id })
        assertEquals(listOf(5_000_000.0, 3_000_000.0, 1_250_000.0), r.kiriman.map { it.nominal })
    }

    /** Satu barang tak boleh berbunyi "1 Barang" — itu SPK biasa, bukan batch. */
    @Test
    fun `satu barang memakai label lama`() {
        val r = setoranSpkRencana(baris("5750000"), adaFoto = true)
        assertTrue(r.bolehKirim)
        assertEquals("Konfirmasi Pembayaran", r.label)
    }

    @Test
    fun `satu barang yang kosong memakai pesan lama, bukan hitungan`() {
        assertEquals("Isi nominal yang diterima", setoranSpkRencana(baris(""), adaFoto = true).label)
    }

    /**
     * Dengan beberapa kolom di layar, "Isi nominal yang diterima" tak memberi tahu
     * kolom MANA yang kurang.
     */
    @Test
    fun `beberapa barang kurang - jumlahnya disebut`() {
        val r = setoranSpkRencana(baris("5000000", "", "0"), adaFoto = true)
        assertFalse(r.bolehKirim)
        assertEquals("Isi nominal 2 barang lagi", r.label)
    }

    /**
     * KESEPAKATAN DENGAN SERVER, bukan kerapian: `record_kasir_setoran` menolak
     * `<= 0` per baris. Kalau satu baris nol lolos dari gerbang, unit-unit
     * SEBELUMNYA sudah tersimpan saat unit itu dijawab 400 — kasir melihat pesan
     * gagal untuk pekerjaan yang separuhnya sudah masuk.
     */
    @Test
    fun `satu baris nol menahan SELURUH kiriman`() {
        val r = setoranSpkRencana(baris("5000000", "0"), adaFoto = true)
        assertFalse(r.bolehKirim)
        assertTrue(r.kiriman.isEmpty())
    }

    @Test
    fun `kiriman selalu kosong selama ditolak - caller tak perlu memeriksa dua kali`() {
        assertTrue(setoranSpkRencana(baris("5000000"), adaFoto = false).kiriman.isEmpty())
        assertTrue(setoranSpkRencana(emptyList(), adaFoto = true).kiriman.isEmpty())
    }

    // ── unit mana yang ditawarkan ────────────────────────────────────────────

    private fun unit(id: String, status: String = DeliveryStatusKey.DELIVERED, setoranAt: String? = null) =
        DeliveryJobDto(id = id, status = status, setoranKasirAt = setoranAt)

    @Test
    fun `unit yang sudah disetor tak ditawarkan lagi`() {
        val dibuka = unit("a")
        val hasil = unitMenungguSetoran(listOf(dibuka, unit("b", setoranAt = "2026-08-22T10:00:00")), dibuka)
        assertEquals(listOf("a"), hasil.map { it.id })
    }

    /** Server cuma menerima `delivered`; unit sebatch yang masih di tahap lain
     *  akan dijawab 400 dan menggagalkan kiriman yang lain ikut terlihat gagal. */
    @Test
    fun `unit yang belum terkirim tak ditawarkan`() {
        val dibuka = unit("a")
        val hasil = unitMenungguSetoran(listOf(dibuka, unit("b", status = DeliveryStatusKey.IN_TRANSIT)), dibuka)
        assertEquals(listOf("a"), hasil.map { it.id })
    }

    /**
     * FAIL-SOFT: `batchUnits` kosong berarti daftar saudara gagal dimuat, BUKAN
     * "tak ada yang perlu disetor". Layarnya harus jatuh balik ke satu unit —
     * yaitu perilaku sebelum fitur ini ada.
     */
    @Test
    fun `batch kosong jatuh balik ke unit yang dibuka`() {
        val dibuka = unit("a")
        assertEquals(listOf("a"), unitMenungguSetoran(emptyList(), dibuka).map { it.id })
    }

    /** Riwayat terpotong / baru berpindah status — unit yang sedang dibaca orangnya
     *  tak boleh hilang dari layarnya sendiri. */
    @Test
    fun `unit yang dibuka tetap masuk walau tak ada di batch`() {
        val dibuka = unit("a")
        val hasil = unitMenungguSetoran(listOf(unit("b")), dibuka)
        assertEquals(listOf("a", "b"), hasil.map { it.id })
    }

    /** Dedup: id kembar berarti DUA POST ke unit yang sama, yang kedua menimpa
     *  yang pertama dengan angka yang sama — diam-diam, tanpa error. */
    @Test
    fun `id kembar dibuang`() {
        val dibuka = unit("a")
        assertEquals(listOf("a"), unitMenungguSetoran(listOf(dibuka, unit("a")), dibuka).map { it.id })
    }

    /** Bahkan di jalur fallback: job yang dibuka dan sudah tersetor tak boleh
     *  ditawarkan ulang, kalau tidak kasir menimpa catatan yang sudah benar. */
    @Test
    fun `unit dibuka yang sudah tersetor tidak muncul`() {
        val dibuka = unit("a", setoranAt = "2026-08-22T10:00:00")
        assertTrue(unitMenungguSetoran(emptyList(), dibuka).isEmpty())
    }
}
