package com.krisoft.tridjayaelektronik.domain.inventory

import com.krisoft.tridjayaelektronik.data.model.StokCabangItemDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pelengkapan cache Inventory dengan barang berstok NOL.
 *
 * Yang dijaga di sini dua hal, dan keduanya soal ONGKOS + KEBENARAN ANGKA:
 * (a) pemicunya tak boleh pernah berubah jadi "tarik seluruh katalog" — 66.482
 * baris yang sudah pernah membuat sinkronisasi HP lapangan tak selesai; dan
 * (b) vonis "stok nol" harus dihitung dengan pengelompokan yang PERSIS sama
 * dengan DAO (`kode` + `kodeCabang`, `SUM(stok)`), kalau tidak angkanya tak
 * pernah cocok dengan yang dilihat layar.
 */
class KatalogStokNolTest {

    private fun row(
        kode: String,
        dealer: String,
        stok: Double,
        cabang: String = "BDG",
        nama: String = "Barang $kode",
    ) = StokCabangItemDto(
        Kode = kode,
        Nama = nama,
        Kategori = "Kulkas",
        Merk = "Sharp",
        Harga = 2_500_000.0,
        Stok = stok,
        kodeCabang = cabang,
        kodeDealer = dealer,
    )

    // ── pemicu: kapan boleh menembak server ──────────────────────────────────

    /**
     * Penjagaan terpenting di berkas ini. Tanpa batas panjang kata kunci, satu
     * huruf meminta server memindai katalog penuh dan menambal cache dengan
     * barang yang tak seorang pun cari.
     */
    @Test
    fun `kata kunci terlalu pendek tak pernah memicu`() {
        for (q in listOf("", " ", "a", "ku", "  k  ")) {
            assertFalse(
                "kata kunci \"$q\" seharusnya tak memicu",
                perluCariStokNol(q, chipMenyala = true, hasilTerlihat = 0, sudahDiperiksa = false, sedangMemuat = false),
            )
        }
    }

    @Test
    fun `kata kunci cukup panjang dan daftar kosong memicu walau chip mati`() {
        assertTrue(
            perluCariStokNol("kulkas", chipMenyala = false, hasilTerlihat = 0, sudahDiperiksa = false, sedangMemuat = false),
        )
    }

    /**
     * Chip menyala = permintaan eksplisit, jadi berlaku walau daftarnya sudah
     * berisi: "kulkas" bisa memberi 3 barang berstok padahal katalognya memuat
     * puluhan. Tanpa jalur ini chip-nya cuma janji kosong.
     */
    @Test
    fun `chip menyala memicu walau daftar sudah berisi`() {
        assertTrue(
            perluCariStokNol("kulkas", chipMenyala = true, hasilTerlihat = 12, sudahDiperiksa = false, sedangMemuat = false),
        )
    }

    /** Daftar berisi + chip mati = tak ada alasan menembak server sama sekali. */
    @Test
    fun `daftar berisi dan chip mati tidak memicu`() {
        assertFalse(
            perluCariStokNol("kulkas", chipMenyala = false, hasilTerlihat = 12, sudahDiperiksa = false, sedangMemuat = false),
        )
    }

    /**
     * Memo + penjaga tumpang-tindih. Pemicunya duduk di `LaunchedEffect` yang
     * relaunch tiap `itemCount` berubah — dan penambalan SENDIRI mengubah
     * `itemCount`. Tanpa dua penjagaan ini, satu pencarian yang berhasil memicu
     * pencarian berikutnya, terus-menerus.
     */
    @Test
    fun `sudah diperiksa atau sedang memuat tidak memicu`() {
        assertFalse(
            perluCariStokNol("kulkas", chipMenyala = true, hasilTerlihat = 0, sudahDiperiksa = true, sedangMemuat = false),
        )
        assertFalse(
            perluCariStokNol("kulkas", chipMenyala = true, hasilTerlihat = 0, sudahDiperiksa = false, sedangMemuat = true),
        )
    }

    /** Cabang ikut jadi kunci: kata kunci sama di cabang lain adalah pertanyaan lain. */
    @Test
    fun `kunci memo membedakan cabang`() {
        assertEquals(kunciCariStokNol("kulkas", "D-01"), kunciCariStokNol(" kulkas ", " D-01 "))
        assertTrue(kunciCariStokNol("kulkas", "D-01") != kunciCariStokNol("kulkas", "D-02"))
    }

    // ── pemetaan: siapa yang dianggap stok nol ───────────────────────────────

    /** Produk yang nol di SEMUA dealer grupnya ikut ditambal. */
    @Test
    fun `produk nol di seluruh dealer ikut ditambal`() {
        val hasil = barisStokNol(listOf(row("K1", "D-01", 0.0), row("K1", "D-02", 0.0)))
        assertEquals(2, hasil.size)
        assertTrue(hasil.all { it.kode == "K1" && it.stok == 0.0 })
    }

    /**
     * Produk yang masih punya stok di salah satu dealer DIBUANG walau server
     * mengirimnya: ia sudah ada di cache lewat sinkronisasi biasa. Memasukkannya
     * berarti menambah baris cabang berstok nol ke produk yang sudah tampil —
     * diam-diam mengubah rincian per cabang dan ekspor CSV.
     */
    @Test
    fun `produk yang masih berstok di salah satu dealer dibuang seluruhnya`() {
        val hasil = barisStokNol(listOf(row("K1", "D-01", 0.0), row("K1", "D-02", 3.0)))
        assertTrue("baris produk berstok tak boleh ikut", hasil.isEmpty())
    }

    /**
     * Pengelompokan `kode + kodeCabang`, bukan `kode` saja — identitas produk di
     * app ini komposit (kode ERP bertabrakan antar region). Kalau dikelompokkan
     * dengan `kode` saja, stok di Bandung akan menutupi nol di Subang dan
     * barangnya tak pernah muncul di sana.
     */
    @Test
    fun `kode sama di region berbeda dinilai terpisah`() {
        val hasil = barisStokNol(
            listOf(
                row("K1", "D-01", 5.0, cabang = "BDG"),
                row("K1", "D-09", 0.0, cabang = "SBG"),
            )
        )
        assertEquals(1, hasil.size)
        assertEquals("SBG", hasil.first().kodeCabang)
        assertEquals("D-09", hasil.first().kodeDealer)
    }

    /**
     * `<= 0`, bukan `== 0.0`: `Stok` Double dari SP GS pernah membawa nilai
     * negatif (koreksi stok), yang jelas bukan "ada barangnya".
     */
    @Test
    fun `stok negatif dihitung sebagai nol`() {
        val hasil = barisStokNol(listOf(row("K1", "D-01", -2.0)))
        assertEquals(1, hasil.size)
    }

    /**
     * `kodeDealer` separuh kunci primer (`kode` + `kodeDealer`). Baris tanpanya
     * akan saling menimpa di Room dan menyisakan satu baris sembarang alih-alih
     * gagal dengan jelas.
     */
    @Test
    fun `baris tanpa kode atau dealer dibuang`() {
        val hasil = barisStokNol(
            listOf(
                row("K1", "", 0.0),
                row("", "D-01", 0.0),
                row("K2", "D-02", 0.0),
            )
        )
        assertEquals(listOf("K2"), hasil.map { it.kode })
    }

    @Test
    fun `daftar kosong menghasilkan kosong`() {
        assertTrue(barisStokNol(emptyList()).isEmpty())
    }

    /** Gambar kosong dinormalkan jadi null, sama seperti jalur sinkronisasi massal. */
    @Test
    fun `gambar kosong jadi null`() {
        val hasil = barisStokNol(listOf(row("K1", "D-01", 0.0).copy(Gambar = "   ")))
        assertEquals(null, hasil.first().gambar)
    }

    /** Kolom lain diteruskan apa adanya — baris ini masuk tabel yang sama dengan hasil sinkron. */
    @Test
    fun `kolom produk diteruskan ke entity`() {
        val hasil = barisStokNol(listOf(row("K1", "D-01", 0.0, nama = "Kulkas 2 Pintu"))).first()
        assertEquals("Kulkas 2 Pintu", hasil.nama)
        assertEquals("Kulkas", hasil.kategori)
        assertEquals("Sharp", hasil.merk)
        assertEquals(2_500_000.0, hasil.harga, 0.001)
    }
}
