package com.krisoft.tridjayaelektronik.ui.aktivitas

import com.krisoft.tridjayaelektronik.data.model.AktivitasPositionDto
import com.krisoft.tridjayaelektronik.data.model.AktivitasItemDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pencocokan divisi → posisi aktivitas menentukan DAFTAR PEKERJAAN yang dinilai
 * (dan didenda) PIC. Salah cocok = karyawan dinilai atas aktivitas divisi lain,
 * jadi aturannya diuji, bukan diasumsikan.
 */
class AktivitasPlanTest {

    private val master = listOf(
        AktivitasPositionDto(id = "sales-elektronik", posisi = "SALES ELEKTRONIK", jobdesks = listOf("a", "b")),
        AktivitasPositionDto(id = "driver", posisi = "DRIVER", jobdesks = listOf("c")),
    )

    @Test
    fun `cocok lewat id, nama posisi, lalu longgar`() {
        assertEquals("driver", matchAktivitasPosition("driver", master)?.id)
        assertEquals("driver", matchAktivitasPosition("DRIVER", master)?.id)
        assertEquals("sales-elektronik", matchAktivitasPosition("Sales Elektronik", master)?.id)
        // Divisi multi-nilai (CSV) hasil divisi-driven-access.
        assertEquals("driver", matchAktivitasPosition("admin,driver", master)?.id)
    }

    @Test
    fun `divisi tak dikenal tidak jatuh ke posisi pertama`() {
        assertNull(matchAktivitasPosition("marketing", master))
        assertNull(matchAktivitasPosition("", master))
        assertNull(matchAktivitasPosition("driver", emptyList()))
    }

    @Test
    fun `entri master tanpa id tak menyambar semua orang`() {
        val rusak = listOf(AktivitasPositionDto(id = "", posisi = "", jobdesks = listOf("x"))) + master
        assertNull(matchAktivitasPosition("marketing", rusak))
        assertEquals("driver", matchAktivitasPosition("driver", rusak)?.id)
    }

    @Test
    fun `status baris mengikuti review server`() {
        assertEquals(AktivitasRowStatus.BELUM, rowStatus(null))
        assertEquals(AktivitasRowStatus.MENUNGGU, rowStatus(AktivitasItemDto(reviewStatus = "pending")))
        assertEquals(AktivitasRowStatus.DISETUJUI, rowStatus(AktivitasItemDto(reviewStatus = "approved")))
        assertEquals(AktivitasRowStatus.DITOLAK, rowStatus(AktivitasItemDto(reviewStatus = "rejected")))
    }

    @Test
    fun `baris terkirim dipetakan berdasarkan jobdeskIndex`() {
        val peta = submittedByIndex(
            listOf(
                AktivitasItemDto(id = "1", jobdeskIndex = 0, jobdeskText = "a"),
                AktivitasItemDto(id = "2", jobdeskIndex = 2, jobdeskText = "c"),
            )
        )
        assertEquals("1", peta[0]?.id)
        assertNull(peta[1])
        assertEquals("2", peta[2]?.id)
    }

    // ── Baris terkirim vs jabatan yang berganti ─────────────────────────────

    private fun kirim(index: Int, teks: String) =
        AktivitasItemDto(jobdeskIndex = index, jobdeskText = teks)

    @Test
    fun `baris yang teksnya cocok tetap menempel`() {
        val aktivitas = listOf("Sapu gudang", "Cek stok")
        val hasil = terkirimUntukAktivitas(listOf(kirim(0, "Sapu gudang"), kirim(1, "Cek stok")), aktivitas)
        assertEquals(setOf(0, 1), hasil.keys)
    }

    /**
     * INTI perbaikan ini. Orang dipindah jabatan; raport hari itu masih memuat
     * baris divisi LAMA. Dipetakan lewat index saja, `jobdeskIndex = 1` menempel
     * ke aktivitas ke-1 divisi BARU — pekerjaan yang sama sekali berbeda — dan
     * barisnya terbaca "sudah dikirim". Orangnya lalu TIDAK mengisinya, dan
     * dinilai kurang atas pekerjaan yang tak pernah ia laporkan.
     */
    @Test
    fun `baris jabatan lama tidak menempel ke aktivitas jabatan baru`() {
        val aktivitasBaru = listOf("Antar unit", "Isi bensin")
        val raportLama = listOf(kirim(0, "Sapu gudang"), kirim(1, "Cek stok"))
        assertTrue(terkirimUntukAktivitas(raportLama, aktivitasBaru).isEmpty())
    }

    /** Sebagian cocok: yang cocok menempel, yang tidak dibuang. */
    @Test
    fun `hanya baris yang benar-benar cocok yang lolos`() {
        val aktivitas = listOf("Antar unit", "Isi bensin")
        val raport = listOf(kirim(0, "Antar unit"), kirim(1, "Cek stok"))
        val hasil = terkirimUntukAktivitas(raport, aktivitas)
        assertEquals(setOf(0), hasil.keys)
    }

    /** Index di luar daftar sekarang tak punya baris untuk ditempeli. */
    @Test
    fun `index di luar daftar dibuang`() {
        val hasil = terkirimUntukAktivitas(listOf(kirim(5, "Apa saja")), listOf("Antar unit"))
        assertTrue(hasil.isEmpty())
    }

    /**
     * Longgar HANYA pada hal kosmetik: master aktivitas diketik admin, jadi
     * spasi ganda dan kapitalisasi berubah adalah kejadian sehari-hari yang
     * TIDAK berarti aktivitasnya berganti. Menolaknya membuat baris sah tampil
     * BELUM tiap kali ada yang merapikan ketikan.
     */
    @Test
    fun `beda spasi dan kapitalisasi tetap dianggap sama`() {
        val aktivitas = listOf("Cek  Stok   Gudang")
        val hasil = terkirimUntukAktivitas(listOf(kirim(0, "  cek stok gudang ")), aktivitas)
        assertEquals(setOf(0), hasil.keys)
    }

    /** Beda KATA tetap aktivitas yang berbeda — kelonggarannya berhenti di spasi. */
    @Test
    fun `beda kata tetap dianggap aktivitas berbeda`() {
        val hasil = terkirimUntukAktivitas(listOf(kirim(0, "Cek stok gudang")), listOf("Cek stok toko"))
        assertTrue(hasil.isEmpty())
    }

    /**
     * Daftar aktivitas kosong (penempatan tanpa divisi aktivitas) tak boleh
     * menempelkan apa pun — layar memang sedang tak punya baris.
     */
    @Test
    fun `daftar aktivitas kosong tak menempelkan apa pun`() {
        assertTrue(terkirimUntukAktivitas(listOf(kirim(0, "Apa saja")), emptyList()).isEmpty())
    }

    /**
     * Arah amannya MEMBUANG: teks kosong dari server lama tak boleh dianggap
     * cocok dengan aktivitas mana pun. Orangnya mengisi ulang (server upsert,
     * aman), alih-alih melihat pekerjaan yang tak pernah dilaporkan sebagai
     * selesai.
     */
    @Test
    fun `teks kosong tidak dianggap cocok`() {
        assertTrue(terkirimUntukAktivitas(listOf(kirim(0, "")), listOf("Antar unit")).isEmpty())
    }

}

/**
 * Daftar aktivitas kini dipilih dari PENEMPATAN KPI, bukan tag `auth_users.divisi`.
 *
 * Ini menutup keluhan yang sedang berjalan: pemegang tag `admin-penjualan,kasir`
 * melihat 8 butir KASIR di HP padahal dinilai atas 6 butir ADMIN PENJUALAN — ia
 * mengisi yang salah, lalu dinilai kurang. Gerbang absen pulang & KPI sudah
 * memakai penempatan sejak 2026-08-18; kelas ini yang membuat HP menyusul.
 *
 * Kontraknya cerminan `pilihDivisiUntukInput` di web. Yang paling mudah rusak
 * adalah TRI-STATE-nya, jadi ketiga keadaan diuji terpisah — bukan cuma
 * "ada/tak ada".
 */
class PilihAktivitasUntukInputTest {

    private val master = listOf(
        AktivitasPositionDto(id = "admin-penjualan", posisi = "ADMIN PENJUALAN", jobdesks = List(6) { "a$it" }),
        AktivitasPositionDto(id = "kasir", posisi = "KASIR", jobdesks = List(8) { "k$it" }),
        AktivitasPositionDto(id = "sales", posisi = "SALES", jobdesks = List(13) { "s$it" }),
    )

    /** Keluhan aslinya, dalam bentuk test. */
    @Test
    fun `penempatan menang atas tag yang menyesatkan`() {
        val hasil = pilihAktivitasUntukInput(
            divisi = "admin-penjualan,kasir",
            positions = master,
            penempatan = PenempatanSaya.Ada("admin-penjualan"),
        )
        assertEquals("admin-penjualan", hasil?.id)
        assertEquals(6, hasil?.jobdesks?.size)
    }

    /**
     * BELUM TERMUAT != TIDAK ADA. Yang pertama harus MENUNGGU; jatuh ke tag
     * sekejap lalu berganti membuat kotak isian berkedip dan berpindah isi —
     * dan orang yang sudah mulai mengetik kehilangan konteksnya.
     */
    @Test
    fun `belum termuat menunggu, tidak jatuh ke tag`() {
        assertNull(
            pilihAktivitasUntukInput("admin-penjualan,kasir", master, PenempatanSaya.BelumTermuat)
        )
    }

    /** Belum punya `kpi_assignments` ATAU permintaan gagal → jalur tag lama. */
    @Test
    fun `tidak ada penempatan jatuh ke tag`() {
        val hasil = pilihAktivitasUntukInput("sales", master, PenempatanSaya.TidakAda)
        assertEquals("sales", hasil?.id)
    }

    /**
     * Penempatan ADA tapi tak punya divisi aktivitas → JANGAN jatuh ke tag.
     * Tag akan memberi daftar milik divisi lain, dan mengisi daftar orang lain
     * lebih buruk daripada tak punya daftar sama sekali.
     */
    @Test
    fun `posisi tanpa aktivitas tidak jatuh ke tag`() {
        assertNull(
            pilihAktivitasUntukInput("kasir", master, PenempatanSaya.Ada("posisi-yang-tak-ada-di-master"))
        )
    }

    /**
     * Peta posisi→divisi harus sama dengan web DAN `kpi/domain.rs`. Kalau
     * ketiganya berpisah, HP menampilkan daftar berbeda dari yang menilai
     * orangnya, tanpa satu pun error.
     */
    @Test
    fun `penempatan bervarian masa kerja dipetakan ke divisi induknya`() {
        assertEquals(
            "sales",
            pilihAktivitasUntukInput("", master, PenempatanSaya.Ada("sales-1-3-bulan"))?.id
        )
        assertEquals(
            "sales",
            pilihAktivitasUntukInput("", master, PenempatanSaya.Ada("sales-4-6-bulan"))?.id
        )
    }

    /** Normalisasi kunci: underscore & spasi jadi `-`, huruf besar diabaikan. */
    @Test
    fun `id penempatan dinormalkan sebelum dicocokkan`() {
        assertEquals("admin-penjualan", pilihAktivitasUntukInput("", master, PenempatanSaya.Ada("ADMIN_PENJUALAN"))?.id)
        assertEquals("admin-penjualan", pilihAktivitasUntukInput("", master, PenempatanSaya.Ada(" admin penjualan "))?.id)
    }

    /** `Ada("")` bentuknya sah tapi isinya kosong → diperlakukan tanpa penempatan. */
    @Test
    fun `penempatan kosong jatuh ke tag, bukan menghilangkan daftar`() {
        assertEquals("sales", pilihAktivitasUntukInput("sales", master, PenempatanSaya.Ada(""))?.id)
    }
}
