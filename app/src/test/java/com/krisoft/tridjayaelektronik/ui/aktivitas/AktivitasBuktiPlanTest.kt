package com.krisoft.tridjayaelektronik.ui.aktivitas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Kontrak bukti Input Aktivitas — stringly-typed lintas TIGA tempat tanpa satu
 * pun pemeriksa kompiler: app ini, `KaryawanAktivitasPage.tsx` (web), dan
 * modul `raport` di `kinerja-service` (server).
 *
 * Nilainya ditulis sebagai LITERAL di sini, bukan dirujuk dari konstantanya —
 * test yang membandingkan konstanta dengan dirinya sendiri selalu hijau (pola
 * `OpnameKondisiTest`).
 */
class AktivitasBuktiPlanTest {

    // ── Bentuk evidenceUrl ───────────────────────────────────────────────────

    @Test
    fun `satu gambar dikirim sebagai string polos, bukan array`() {
        // Ini yang menjaga bukti tetap bisa dibuka pemiliknya sendiri: guard
        // server mencari `WHERE bukti_url = '/uploads/raport/a.jpg'` PERSIS.
        val hasil = buildEvidenceUrl("image", listOf("/uploads/raport/a.jpg"))
        assertEquals("/uploads/raport/a.jpg", hasil)
        assertFalse("satu gambar tak boleh dibungkus array", hasil!!.startsWith("["))
    }

    @Test
    fun `dua gambar dikirim sebagai JSON array seperti yang ditulis web`() {
        assertEquals(
            "[\"/uploads/raport/a.jpg\",\"/uploads/raport/b.jpg\"]",
            buildEvidenceUrl("image", listOf("/uploads/raport/a.jpg", "/uploads/raport/b.jpg")),
        )
    }

    @Test
    fun `video tidak pernah dibungkus array`() {
        assertEquals(
            "/uploads/raport/x.mp4",
            buildEvidenceUrl("video", listOf("/uploads/raport/x.mp4")),
        )
    }

    @Test
    fun `mode none tidak pernah membawa evidenceUrl`() {
        // Server menolak `none` + evidenceUrl (raport/service.rs:248).
        assertNull(buildEvidenceUrl("none", listOf("/uploads/raport/a.jpg")))
    }

    @Test
    fun `daftar kosong menghasilkan null, bukan array kosong`() {
        assertNull(buildEvidenceUrl("image", emptyList()))
        assertNull(buildEvidenceUrl("image", listOf("   ")))
        assertNull(buildEvidenceUrl("video", emptyList()))
    }

    @Test
    fun `yang ditulis app bisa dibaca ulang oleh parser app sendiri`() {
        // Round-trip lewat fungsi produksi, bukan parser JSON pinjaman — kalau
        // formatnya menyimpang, di sinilah ketahuan.
        val tiga = listOf("/uploads/raport/a.jpg", "/uploads/raport/b.jpg", "/uploads/raport/c.jpg")
        assertEquals(tiga, parseEvidenceUrls(buildEvidenceUrl("image", tiga)))

        val satu = listOf("/uploads/raport/a.jpg")
        assertEquals(satu, parseEvidenceUrls(buildEvidenceUrl("image", satu)))
    }

    // ── Bukti lama tidak boleh hilang ────────────────────────────────────────

    @Test
    fun `bukti lama satu URL tetap terbaca setelah ditambah satu gambar`() {
        // Server upsert dan MENIMPA bukti_url seluruhnya — mengirim hanya berkas
        // baru berarti menghapus bukti lama tanpa satu pun error.
        val hasil = gabungBukti(
            lama = parseEvidenceUrls("/uploads/raport/lama.jpg"),
            baru = listOf("/uploads/raport/baru.jpg"),
        )
        assertEquals(listOf("/uploads/raport/lama.jpg", "/uploads/raport/baru.jpg"), hasil)
    }

    @Test
    fun `gabung bukti membuang duplikat dan memotong di MAX_GAMBAR`() {
        // Sengaja diturunkan dari konstanta, bukan angka tetap: batasnya sudah
        // sekali berubah (6 -> 10, 2026-08-16) dan test yang menghafal angkanya
        // menuntut penyuntingan di dua tempat untuk satu keputusan.
        val lama = (1 until MAX_GAMBAR).map { "/uploads/raport/l$it.jpg" }
        val baru = listOf("/uploads/raport/l1.jpg", "/uploads/raport/b1.jpg", "/uploads/raport/b2.jpg")
        val hasil = gabungBukti(lama, baru)

        assertEquals(MAX_GAMBAR, hasil.size)
        // Yang BARU yang dibuang saat penuh, bukan yang sudah tersimpan.
        assertEquals(lama, hasil.take(MAX_GAMBAR - 1))
        assertEquals("/uploads/raport/b1.jpg", hasil[MAX_GAMBAR - 1])
    }

    // ── Gerbang sebelum unggah ───────────────────────────────────────────────

    @Test
    fun `batas sepuluh gambar sama dengan web`() {
        // Angka LITERAL di sini disengaja: ini satu-satunya test yang menjaga
        // kesepakatan lintas-repo dengan `MAX_IMAGE_FILES` web. Kalau seseorang
        // mengubah konstanta app saja, test ini yang merah — itu tujuannya.
        assertEquals(10, MAX_GAMBAR)
        assertTrue("batas di bawah 10 memotong aktivitas bertarget 10", MAX_GAMBAR >= 10)
        val gate = gateKirimBukti(jumlahGambar = MAX_GAMBAR + 1, adaVideo = false, ukuranVideoBytes = 0L)
        assertFalse(gate.ok)
        assertTrue("pesannya harus menyebut angka batasnya", gate.alasan!!.contains("$MAX_GAMBAR"))
    }

    @Test
    fun `tepat di batas masih lolos, bukan ditolak`() {
        // Penjaga off-by-one: `>` vs `>=` di `gateKirimBukti` adalah selisih
        // antara "boleh 10 foto" dan "boleh 9", dan keduanya sama-sama hijau
        // di test yang cuma menguji kasus melebihi batas.
        val gate = gateKirimBukti(jumlahGambar = MAX_GAMBAR, adaVideo = false, ukuranVideoBytes = 0L)
        assertTrue(gate.alasan ?: "", gate.ok)
    }

    @Test
    fun `foto dan video tidak boleh bercampur dalam satu aktivitas`() {
        // Server hanya punya SATU `mode` per baris.
        val gate = gateKirimBukti(jumlahGambar = 2, adaVideo = true, ukuranVideoBytes = 1L)
        assertFalse(gate.ok)
        assertTrue(gate.alasan!!.contains("ATAU"))
    }

    @Test
    fun `tanpa berkas apa pun tidak boleh kirim`() {
        assertFalse(gateKirimBukti(jumlahGambar = 0, adaVideo = false, ukuranVideoBytes = 0L).ok)
    }

    @Test
    fun `video di atas 30MB ditolak sebelum menyentuh jaringan`() {
        // Angkanya ditulis literal sebagai penjaga terhadap MAX_EVIDENCE_BYTES
        // di kinerja-service/src/raport.rs:14.
        assertEquals(30L * 1024 * 1024, MAX_VIDEO_BUKTI_BYTES)

        val gate = gateKirimBukti(0, adaVideo = true, ukuranVideoBytes = MAX_VIDEO_BUKTI_BYTES + 1)
        assertFalse(gate.ok)
        assertTrue(gate.alasan!!.contains("30 MB"))

        assertTrue(gateKirimBukti(0, adaVideo = true, ukuranVideoBytes = MAX_VIDEO_BUKTI_BYTES).ok)
    }

    @Test
    fun `ukuran video nol berarti tak terbaca dan tetap boleh dikirim`() {
        // Kolom SIZE null itu normal untuk sebagian penyedia; menolaknya berarti
        // memblokir video yang sebenarnya sah.
        assertTrue(gateKirimBukti(0, adaVideo = true, ukuranVideoBytes = 0L).ok)
    }

    @Test
    fun `batas ukuran gambar sama dengan web`() {
        assertEquals(25L * 1024 * 1024, MAX_GAMBAR_INPUT_BYTES)
    }

    // ── Ekstensi & MIME video ────────────────────────────────────────────────

    @Test
    fun `ekstensi video hanya mp4 webm mov`() {
        assertNull(ekstensiVideo("rekaman.mkv", null))
        assertNull(ekstensiVideo("rekaman.3gp", null))
        assertNull(ekstensiVideo("rekaman.avi", "video/x-msvideo"))
        assertNull(ekstensiVideo(null, null))
        assertNull(ekstensiVideo("", null))
    }

    @Test
    fun `ekstensi diambil dari nama berkas dulu, mime jadi cadangan`() {
        // Penyedia galeri kadang menjawab video/mp4 untuk berkas .mov, dan
        // server memvalidasi ekstensi x mime x magic bytes SERENTAK.
        assertEquals("mov", ekstensiVideo("rekaman.MOV", "video/mp4"))
        assertEquals("mov", ekstensiVideo(null, "video/quicktime"))
        assertEquals("webm", ekstensiVideo("bukti", "video/webm"))
        assertEquals("mp4", ekstensiVideo("bukti", "video/mp4;codecs=avc1"))
    }

    @Test
    fun `mime dan ekstensi selalu pasangan yang divalidasi server`() {
        assertEquals("video/mp4", mimeVideo("mp4"))
        assertEquals("video/webm", mimeVideo("webm"))
        assertEquals("video/quicktime", mimeVideo("mov"))
    }

    // ── Penamaan & watermark ─────────────────────────────────────────────────

    @Test
    fun `gambar hasil watermark selalu webp apa pun sumbernya`() {
        // prepareWatermarkedJpeg selalu meng-encode WebP (2026-08-28), termasuk
        // untuk PNG/JPEG dari galeri — ekstensi yang meleset ditolak server.
        assertTrue(namaBerkasGambar(2, 1L).endsWith(".webp"))
        assertEquals("raport_1700000000000_0.webp", namaBerkasGambar(0, 1_700_000_000_000L))
    }

    @Test
    fun `nama berkas video memakai ekstensi aslinya, bukan webp`() {
        assertEquals("raport_1700000000000.mov", namaBerkasVideo("mov", 1_700_000_000_000L))
        assertEquals("raport_1.webm", namaBerkasVideo("webm", 1L))
    }

    @Test
    fun `nama berkas gambar berbeda per urutan agar tidak saling menimpa`() {
        val nama = (0 until MAX_GAMBAR).map { namaBerkasGambar(it, 1L) }
        assertEquals(MAX_GAMBAR, nama.distinct().size)
    }

    @Test
    fun `judul watermark galeri berbeda dari kamera`() {
        assertEquals("TRIDJAYA · AKTIVITAS", watermarkTitleBukti(dariGaleri = false))
        assertEquals("TRIDJAYA · AKTIVITAS (GALERI)", watermarkTitleBukti(dariGaleri = true))
        assertTrue(watermarkTitleBukti(dariGaleri = true).contains("GALERI"))
    }

    // ── Pesan ke user ────────────────────────────────────────────────────────

    @Test
    fun `pesan gagal dekode galeri menyebut HEIC, bukan menyuruh jepret ulang`() {
        val galeri = pesanGagalDekode(dariGaleri = true)
        assertTrue(galeri.contains("HEIC"))
        assertFalse("saran 'jepret ulang' tak nyambung untuk berkas galeri", galeri.contains("jepret"))

        assertTrue(pesanGagalDekode(dariGaleri = false).contains("jepret"))
    }

    @Test
    fun `sebab dekode ditempelkan kalau ada, tanpa merusak kalimat aslinya`() {
        val tanpa = pesanGagalDekode(dariGaleri = true)
        assertEquals(tanpa, pesanGagalDekode(dariGaleri = true, sebab = null))
        assertEquals(tanpa, pesanGagalDekode(dariGaleri = true, sebab = "  "))

        val dengan = pesanGagalDekode(dariGaleri = true, sebab = "FileNotFoundException")
        assertTrue(dengan, dengan.startsWith(tanpa))
        assertTrue(dengan, dengan.endsWith("(FileNotFoundException)"))
    }

    // ── Sebab gambar diabaikan (dipisah per penghitung, vc117) ───────────────

    @Test
    fun `tanpa yang diabaikan tak ada pesan sama sekali`() {
        assertNull(pesanGambarDiabaikan(takMuat = 0, terlaluBesar = 0, takTerbaca = 0))
    }

    /**
     * INTI perbaikannya: gambar yang tak terbaca TIDAK BOLEH dijelaskan sebagai
     * kuota penuh. Seseorang yang memilih 2 dari 10 lalu dibilang "maksimal 10"
     * bisa membantahnya di depan matanya sendiri — dan sesudah itu ia tak
     * mempercayai pesan berikutnya juga.
     */
    @Test
    fun `gagal baca tidak dijelaskan sebagai kuota penuh`() {
        val pesan = pesanGambarDiabaikan(
            takMuat = 0,
            terlaluBesar = 0,
            takTerbaca = 2,
            sebabTakTerbaca = "FileNotFoundException",
        )!!
        assertFalse("kuota disebut padahal sebabnya bukan kuota", pesan.contains("maksimal"))
        assertFalse(pesan.contains("$MAX_GAMBAR gambar per aktivitas"))
        assertTrue(pesan, pesan.contains("2 gambar tidak bisa dibaca"))
        assertTrue("sebab aslinya harus ikut", pesan.contains("FileNotFoundException"))
        // Kalimat HEIC dipakai ULANG, bukan ditulis baru di ViewModel.
        assertTrue(pesan, pesan.contains("HEIC"))
    }

    @Test
    fun `kuota penuh tetap menyebut kuota`() {
        val pesan = pesanGambarDiabaikan(takMuat = 3, terlaluBesar = 0, takTerbaca = 0)!!
        assertTrue(pesan, pesan.contains("3 gambar"))
        assertTrue(pesan, pesan.contains("maksimal $MAX_GAMBAR"))
        assertFalse("tak ada sebab lain yang disebut", pesan.contains("HEIC"))
    }

    @Test
    fun `terlalu besar menyebut ambangnya, bukan kuota`() {
        val pesan = pesanGambarDiabaikan(takMuat = 0, terlaluBesar = 1, takTerbaca = 0)!!
        assertTrue(pesan, pesan.contains(formatUkuranBerkas(MAX_GAMBAR_INPUT_BYTES)))
        assertFalse(pesan.contains("maksimal $MAX_GAMBAR"))
        assertFalse(pesan.contains("HEIC"))
    }

    /**
     * Satu pemilihan bisa memuat ketiga sebab sekaligus. Menjumlahkannya jadi
     * satu angka (perilaku sampai vc116) membuang justru keterangan yang
     * menentukan langkah berikutnya.
     */
    @Test
    fun `tiga sebab sekaligus tetap terbaca sebagai tiga hal`() {
        val pesan = pesanGambarDiabaikan(
            takMuat = 1,
            terlaluBesar = 2,
            takTerbaca = 3,
            sebabTakTerbaca = "IOException",
        )!!
        assertTrue(pesan, pesan.contains("1 gambar tidak ditambahkan"))
        assertTrue(pesan, pesan.contains("2 gambar dilewati"))
        assertTrue(pesan, pesan.contains("3 gambar tidak bisa dibaca"))
        assertTrue(pesan, pesan.contains("IOException"))
    }

    @Test
    fun `ukuran berkas nol dilaporkan tak diketahui, bukan nol byte`() {
        assertEquals("ukuran tak diketahui", formatUkuranBerkas(0L))
        assertEquals("ukuran tak diketahui", formatUkuranBerkas(-1L))
        assertTrue(formatUkuranBerkas(30L * 1024 * 1024).contains("MB"))
        assertTrue(formatUkuranBerkas(500L * 1024).contains("KB"))
    }

    // ── Gerbang hari Minggu ──────────────────────────────────────────────────
    //
    // Server menolak POST /raport-harian pada hari Minggu tapi MEMBIARKAN
    // /raport-harian/upload, dan app mengunggah semua berkas dulu baru
    // mengirim. Minggu 16 Agustus 2026 itu berarti 36 berkas naik ke server
    // dengan NOL baris raport tercatat — seluruhnya yatim.

    /** Tanggal tetap, dibangun tanpa `java.time` (haram di app/src/main). */
    private fun millis(tahun: Int, bulanNol: Int, tanggal: Int): Long =
        java.util.Calendar.getInstance().apply {
            clear()
            set(tahun, bulanNol, tanggal, 12, 0, 0)
        }.timeInMillis

    @Test
    fun `minggu dikenali, hari lain tidak`() {
        assertTrue("16 Agustus 2026 itu Minggu", hariMinggu(millis(2026, 7, 16)))
        assertFalse("15 Agustus 2026 itu Sabtu", hariMinggu(millis(2026, 7, 15)))
        assertFalse("17 Agustus 2026 itu Senin", hariMinggu(millis(2026, 7, 17)))
    }

    @Test
    fun `gerbang menolak Minggu dengan kalimat yang SAMA PERSIS dengan server`() {
        val minggu = gerbangHariIni(millis(2026, 7, 16))
        assertFalse(minggu.ok)
        // Salinan literal dari kinerja-service/src/raport/service.rs. Dua
        // kalimat berbeda untuk satu aturan membuat orang mengira ada dua
        // aturan.
        assertEquals("Hari Minggu tidak wajib mengisi laporan raport.", minggu.alasan)
        assertEquals(PESAN_HARI_MINGGU, minggu.alasan)
    }

    @Test
    fun `hari kerja lolos tanpa alasan`() {
        val senin = gerbangHariIni(millis(2026, 7, 17))
        assertTrue(senin.ok)
        assertEquals(null, senin.alasan)
    }

    // ── Gerbang "sudah disetujui PIC" ────────────────────────────────────────

    @Test
    fun `hanya approved yang mengunci`() {
        assertTrue(terkunciPic("approved"))
        assertTrue("server membandingkan tanpa peduli huruf besar", terkunciPic("APPROVED"))
        assertTrue(terkunciPic(" approved "))
        // `rejected` justru ALUR REVISI — mengunci di sini akan mematikan
        // satu-satunya jalan keluar karyawan yang buktinya ditolak.
        assertFalse(terkunciPic("rejected"))
        assertFalse(terkunciPic("pending"))
        assertFalse("belum pernah dikirim", terkunciPic(null))
        assertFalse(terkunciPic(""))
    }

    @Test
    fun `pesan terkunci menyebut jalan keluarnya, bukan cuma larangan`() {
        // Yang dibutuhkan orangnya bukan "tidak bisa" melainkan "harus minta
        // siapa" — tanpa itu ia mengira aplikasinya rusak.
        assertTrue(PESAN_TERKUNCI_PIC.contains("PIC Aktivitas"))
        assertTrue(PESAN_TERKUNCI_PIC.contains("Menunggu"))
    }
}
