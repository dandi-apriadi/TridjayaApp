package com.krisoft.tridjayaelektronik.ui.homeservice

import com.krisoft.tridjayaelektronik.data.HS_JENIS_TARIK_UNIT
import com.krisoft.tridjayaelektronik.data.model.HsSparepartDto
import com.krisoft.tridjayaelektronik.data.model.HsTicketDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Bagian murni layar Komplain (Home Service). */
class HomeServicePlanTest {

    private val base = "https://tridjaya.com/"

    private fun tiket(
        id: String = "1",
        status: String = HS_BARU,
        prioritas: String = "normal",
        sla: Boolean = false,
        umur: Int? = null,
    ) = HsTicketDto(id = id, status = status, prioritas = prioritas, melewatiSla = sla, umurJam = umur)

    // ── Penyaringan antrian ──────────────────────────────────────────────────

    @Test
    fun `antrian triase hanya tiket yang menunggu keputusan CS`() {
        // `ditugaskan`/`dikerjakan` sengaja TIDAK ikut: pekerjaannya sudah pindah
        // ke teknisi, dan menghitungnya membuat badge CS tak pernah nol.
        val semua = listOf(
            tiket("a", HS_BARU),
            tiket("b", HS_MENUNGGU_TINDAK_LANJUT),
            tiket("c", HS_DITUGASKAN),
            tiket("d", HS_DIKERJAKAN),
            tiket("e", HS_SELESAI),
        )
        assertEquals(listOf("a", "b"), saringStatus(semua, HS_STATUS_TRIASE).map { it.id })
    }

    @Test
    fun `tiap mode punya himpunan status sendiri`() {
        assertEquals(HS_STATUS_TRIASE, HsMode.TRIASE.statusAktif)
        assertEquals(HS_STATUS_TUGAS_TEKNISI, HsMode.TEKNISI.statusAktif)
        assertEquals(HS_STATUS_TARIK_AKTIF, HsMode.TARIK.statusAktif)
        assertEquals(setOf(HS_TARIK_DITUGASKAN), HsMode.DRIVER.statusAktif)
    }

    @Test
    fun `daftar tugas driver wajib menyertakan jenis tarik unit`() {
        // `mine=true` memilih KOLOM yang disaring berdasarkan `jenis`; tanpa
        // jenis=tarik_unit server menyaring assigned_teknisi_id dan daftar driver
        // selalu kosong TANPA error.
        assertEquals(HS_JENIS_TARIK_UNIT, HsMode.DRIVER.jenis)
        assertEquals(true, HsMode.DRIVER.mine)
        assertEquals(HS_JENIS_TARIK_UNIT, jenisUntukTugasDriver())
    }

    @Test
    fun `urutan antrian mendahulukan yang perlu perhatian lalu yang tertua`() {
        val hasil = urutkanAntrian(
            listOf(
                tiket("biasa-muda", umur = 1),
                tiket("biasa-tua", umur = 20),
                tiket("mendesak", prioritas = "mendesak", umur = 2),
            )
        )
        assertEquals(listOf("mendesak", "biasa-tua", "biasa-muda"), hasil.map { it.id })
    }

    @Test
    fun `umur tak diketahui jatuh ke bawah, bukan dianggap nol`() {
        val hasil = urutkanAntrian(listOf(tiket("null-umur", umur = null), tiket("umur-0", umur = 0)))
        assertEquals(listOf("umur-0", "null-umur"), hasil.map { it.id })
    }

    @Test
    fun `melewati SLA ikut ditandai perlu perhatian`() {
        assertTrue(perluPerhatian(tiket(sla = true)))
        assertTrue(perluPerhatian(tiket(prioritas = "MENDESAK")))
        assertFalse(perluPerhatian(tiket()))
    }

    // ── Gate aksi ────────────────────────────────────────────────────────────

    @Test
    fun `mulai kunjungan hanya dari status ditugaskan`() {
        assertTrue(bolehMulai(HS_DITUGASKAN).ok)
        assertFalse(bolehMulai(HS_DIKERJAKAN).ok)
        assertFalse(bolehMulai(HS_BARU).ok)
    }

    @Test
    fun `hasil selesai wajib berfoto`() {
        val tanpaFoto = bolehTutupKunjungan("selesai", emptyList(), false, emptyList(), null, null, null)
        assertFalse(tanpaFoto.ok)
        assertTrue(
            bolehTutupKunjungan("selesai", listOf("/uploads/home-service/a.jpg"), false, emptyList(), null, null, null).ok
        )
        // Kunjungan ulang & eskalasi TIDAK menuntut foto (server pun tidak).
        assertTrue(bolehTutupKunjungan("kunjungan_ulang", emptyList(), false, emptyList(), null, null, null).ok)
    }

    @Test
    fun `rating di luar 1 sampai 5 ditolak`() {
        val foto = listOf("/uploads/home-service/a.jpg")
        assertFalse(bolehTutupKunjungan("selesai", foto, false, emptyList(), null, null, 0).ok)
        assertFalse(bolehTutupKunjungan("selesai", foto, false, emptyList(), null, null, 6).ok)
        assertTrue(bolehTutupKunjungan("selesai", foto, false, emptyList(), null, null, 5).ok)
    }

    @Test
    fun `sparepart berbiaya menuntut bukti bayar dan nominal`() {
        val foto = listOf("/uploads/home-service/a.jpg")
        val part = listOf(HsSparepartDto(nama = "Dinamo", qty = 1, harga = 250_000.0))
        assertFalse(bolehTutupKunjungan("selesai", foto, true, part, null, null, null).ok)
        assertFalse(bolehTutupKunjungan("selesai", foto, true, part, 250_000.0, null, null).ok)
        assertTrue(bolehTutupKunjungan("selesai", foto, true, part, 250_000.0, "/uploads/home-service/b.jpg", null).ok)
        // Sparepart gratis tak menuntut bukti bayar apa pun.
        val gratis = listOf(HsSparepartDto(nama = "Baut", qty = 2, harga = 0.0))
        assertTrue(bolehTutupKunjungan("selesai", foto, true, gratis, null, null, null).ok)
    }

    @Test
    fun `sparepart kosong atau tak wajar ditolak`() {
        val foto = listOf("/uploads/home-service/a.jpg")
        assertFalse(bolehTutupKunjungan("selesai", foto, true, emptyList(), null, null, null).ok)
        assertFalse(
            bolehTutupKunjungan("selesai", foto, true, listOf(HsSparepartDto(nama = " ", qty = 1)), null, null, null).ok
        )
        assertFalse(
            bolehTutupKunjungan("selesai", foto, true, listOf(HsSparepartDto(nama = "X", qty = 0)), null, null, null).ok
        )
    }

    @Test
    fun `biaya dihitung qty kali harga`() {
        assertEquals(
            300_000.0,
            hitungBiaya(
                listOf(
                    HsSparepartDto(nama = "A", qty = 2, harga = 100_000.0),
                    HsSparepartDto(nama = "B", qty = 1, harga = 100_000.0),
                )
            ),
            0.001,
        )
    }

    @Test
    fun `aksi beralasan wajib diisi`() {
        assertFalse(bolehAlasan(null).ok)
        assertFalse(bolehAlasan("   ").ok)
        assertTrue(bolehAlasan("Unit rusak berat").ok)
    }

    /** Pembungkus supaya tiap test cuma menyebut yang sedang diujinya. Default =
     *  jalur terverifikasi yang LENGKAP. */
    private fun kurang(
        tanpaVerifikasi: Boolean = false,
        noTransaksi: String? = "TR-1",
        barisTerpilih: Set<Int> = setOf(1),
        foto: String? = "/uploads/x.jpg",
        deskripsi: String? = "rusak",
        nama: String? = "BUDI",
        hp: String? = "0812",
        alamat: String? = "Jl. Mawar 1",
    ) = kurangBuatTiket(
        tanpaVerifikasi = tanpaVerifikasi,
        noTransaksi = noTransaksi,
        barisTerpilih = barisTerpilih,
        fotoKwitansiUrl = foto,
        deskripsi = deskripsi,
        customerNama = nama,
        customerHp = hp,
        customerAlamat = alamat,
    )

    @Test
    fun `jalur terverifikasi lengkap boleh dikirim`() {
        assertEquals(emptyList<String>(), kurang())
        assertTrue(
            bolehBuatTiket(
                tanpaVerifikasi = false,
                noTransaksi = "TR-1",
                barisTerpilih = setOf(1),
                fotoKwitansiUrl = "/uploads/x.jpg",
                deskripsi = "rusak",
                customerNama = null,
                customerHp = null,
                customerAlamat = "Jl. Mawar 1",
            ).ok,
        )
    }

    @Test
    fun `barang wajib dicentang — server yang tak menerima pilihan diam-diam memakai barang pertama`() {
        assertEquals(listOf("barang yang dikomplainkan"), kurang(barisTerpilih = emptySet()))
    }

    @Test
    fun `alamat wajib di kedua jalur`() {
        assertEquals(listOf("alamat konsumen"), kurang(alamat = "   "))
        assertEquals(listOf("alamat konsumen"), kurang(tanpaVerifikasi = true, noTransaksi = null, alamat = null))
    }

    @Test
    fun `foto kwitansi TIDAK wajib di kedua jalur, keluhan tetap wajib`() {
        // Dibalik 2026-08-22 (permintaan user): dulu `foto = null` memulangkan
        // listOf("foto kwitansi"). Dibalik, bukan dihapus — tanpa baris ini
        // syarat itu bisa dipasang lagi di app tanpa ada yang menahannya, dan
        // gejalanya bukan galat melainkan tombol Kirim yang mati.
        assertEquals(emptyList<String>(), kurang(foto = null))
        assertEquals(emptyList<String>(), kurang(foto = "   "))
        assertEquals(listOf("isi keluhan"), kurang(deskripsi = "  "))
        assertEquals(
            emptyList<String>(),
            kurang(tanpaVerifikasi = true, noTransaksi = null, foto = null),
        )
        assertEquals(listOf("isi keluhan"), kurang(tanpaVerifikasi = true, noTransaksi = null, deskripsi = null))
    }

    @Test
    fun `tanpa verifikasi — nama dan HP wajib, barang TIDAK diminta`() {
        // Barang memang tak ada untuk dipilih di jalur ini; memintanya =
        // jalan buntu. Yang wajib justru nama+HP (server menolak tanpanya).
        assertEquals(
            emptyList<String>(),
            kurang(tanpaVerifikasi = true, noTransaksi = null, barisTerpilih = emptySet()),
        )
        assertEquals(
            listOf("nama konsumen", "nomor HP konsumen"),
            kurang(tanpaVerifikasi = true, noTransaksi = null, barisTerpilih = emptySet(), nama = " ", hp = null),
        )
    }

    @Test
    fun `tanpa transaksi dan tanpa jalur tanpa-verifikasi = data pembelian yang kurang`() {
        assertEquals(listOf("data pembelian konsumen"), kurang(noTransaksi = null, barisTerpilih = emptySet()))
    }

    @Test
    fun `urutan daftar kekurangan sama dengan web`() {
        // Urutan `kurang` di HomeServiceLaporPage.tsx: (nama, HP | pembelian |
        // barang) → alamat → keluhan. Pelapor yang berpindah antara HP dan web
        // harus membaca daftar yang sama persis. Suku "foto kwitansi" dibuang
        // dari KEDUA sisi pada 2026-08-22 — kalau salah satu sisi memasangnya
        // lagi, test inilah yang jatuh.
        assertEquals(
            listOf("barang yang dikomplainkan", "alamat konsumen", "isi keluhan"),
            kurang(barisTerpilih = emptySet(), foto = null, deskripsi = null, alamat = null),
        )
        assertEquals(
            listOf("nama konsumen", "nomor HP konsumen", "alamat konsumen", "isi keluhan"),
            kurang(
                tanpaVerifikasi = true, noTransaksi = null, barisTerpilih = emptySet(),
                foto = null, deskripsi = null, nama = null, hp = null, alamat = null,
            ),
        )
    }

    @Test
    fun `pesan gerbang menyebut SEMUA yang kurang, bukan cuma yang pertama`() {
        val gate = bolehBuatTiket(
            tanpaVerifikasi = false,
            noTransaksi = "TR-1",
            barisTerpilih = emptySet(),
            fotoKwitansiUrl = null,
            deskripsi = "rusak",
            customerNama = null,
            customerHp = null,
            customerAlamat = null,
        )
        assertFalse(gate.ok)
        assertEquals(
            "Masih perlu: barang yang dikomplainkan, alamat konsumen.",
            gate.alasan,
        )
    }

    // ── Kontak sesudah lookup ────────────────────────────────────────────────

    private val kosong = KontakIsian("", "", "")

    @Test
    fun `kontak transaksi mengisi kolom yang belum disentuh pelapor`() {
        val hasil = kontakSetelahLookup(
            disunting = KontakDisunting.NIHIL,
            sekarang = kosong,
            kontakNama = "BUDI",
            kontakHp = "0812",
            kontakAlamat = "Jl. Mawar 1",
            cariNama = "bud",
            cariHp = "",
        )
        assertEquals(KontakIsian("BUDI", "0812", "Jl. Mawar 1"), hasil)
    }

    @Test
    fun `transaksi tanpa kontak jatuh ke kotak pencarian, bukan dibiarkan kosong`() {
        // Transaksi lama tanpa SPK: satu-satunya identitas yang kita punya
        // adalah yang barusan diketik pelapor. Server MEWAJIBKAN alamat, jadi
        // membiarkan ketiganya kosong berarti tombol mati tanpa bahan.
        val hasil = kontakSetelahLookup(
            disunting = KontakDisunting.NIHIL,
            sekarang = kosong,
            kontakNama = null,
            kontakHp = "   ",
            kontakAlamat = null,
            cariNama = "  SITI  ",
            cariHp = "0899",
        )
        assertEquals(KontakIsian("SITI", "0899", ""), hasil)
    }

    @Test
    fun `ketikan pelapor MENANG atas respons yang telat mendarat`() {
        // Seluruh form sudah terender saat rincian masih dimuat, jadi alamat
        // yang sedang diketik bisa dihapus respons yang datang beberapa detik
        // kemudian kalau aturannya menimpa tanpa syarat.
        val diketik = KontakIsian("BUDI SANTOSO", "0812", "Jl. Melati 7 RT03")
        val hasil = kontakSetelahLookup(
            disunting = KontakDisunting(nama = true, hp = true, alamat = true),
            sekarang = diketik,
            kontakNama = "BUDI",
            kontakHp = "0800",
            kontakAlamat = "Jl. Mawar 1",
            cariNama = "budi",
            cariHp = "0812",
        )
        assertEquals(diketik, hasil)
    }

    @Test
    fun `kontak konsumen SEBELUMNYA tidak menyeberang saat transaksi diganti`() {
        // `disunting` direset tiap `pilihTransaksi`, jadi isian yang tertinggal
        // dari konsumen sebelumnya WAJIB kalah dari kontak transaksi baru —
        // server memenangkan isian klien atas data SPK, dan alamat yang salah
        // mengirim teknisi ke rumah orang lain.
        val tertinggal = KontakIsian("BUDI", "0812", "Jl. Mawar 1")
        val hasil = kontakSetelahLookup(
            disunting = KontakDisunting.NIHIL,
            sekarang = tertinggal,
            kontakNama = "SITI",
            kontakHp = "0899",
            kontakAlamat = "Jl. Kenanga 9",
            cariNama = "",
            cariHp = "",
        )
        assertEquals(KontakIsian("SITI", "0899", "Jl. Kenanga 9"), hasil)
    }

    @Test
    fun `jalur tanpa verifikasi membuang alamat warisan transaksi, menahan yang diketik`() {
        // Dipakai `lanjutTanpaVerifikasi` dengan kontak server serba null.
        val warisan = KontakIsian("BUDI", "0812", "Jl. Mawar 1")
        assertEquals(
            KontakIsian("SITI", "0899", ""),
            kontakSetelahLookup(KontakDisunting.NIHIL, warisan, null, null, null, "SITI", "0899"),
        )
        assertEquals(
            warisan,
            kontakSetelahLookup(
                KontakDisunting(nama = true, hp = true, alamat = true),
                warisan, null, null, null, "SITI", "0899",
            ),
        )
    }

    /**
     * Menyentuh SATU kolom tidak boleh menolak data server untuk dua kolom lain.
     *
     * Kelas bug yang dijaga: satu bendera `disunting` untuk bertiga. Seluruh
     * form sudah terender saat rincian transaksi masih dimuat, jadi pelapor
     * yang mengetik NAMA lebih dulu menyalakan bendera itu untuk ketiganya —
     * alamat dari SPK yang mendarat sedetik kemudian ditolak, dan kolom alamat
     * tinggal kosong. Alamat WAJIB di kedua jalur, jadi akibatnya tombol kirim
     * mati tanpa alasan yang terlihat, atau teknisi berangkat tanpa alamat.
     */
    @Test
    fun `mengetik nama tidak membuang alamat dari SPK`() {
        val hasil = kontakSetelahLookup(
            disunting = KontakDisunting(nama = true),
            sekarang = KontakIsian("BUDI SANTOSO", "", ""),
            kontakNama = "BUDI",
            kontakHp = "0812",
            kontakAlamat = "Jl. Mawar 1",
            cariNama = "budi",
            cariHp = "",
        )
        assertEquals(KontakIsian("BUDI SANTOSO", "0812", "Jl. Mawar 1"), hasil)
    }

    @Test
    fun `alamat yang diketik tetap menang walau nama dan hp datang dari SPK`() {
        val hasil = kontakSetelahLookup(
            disunting = KontakDisunting(alamat = true),
            sekarang = KontakIsian("", "", "Jl. Melati 7 RT03 (rumah belakang)"),
            kontakNama = "SITI",
            kontakHp = "0899",
            kontakAlamat = "Jl. Kenanga 9",
            cariNama = "",
            cariHp = "",
        )
        assertEquals(KontakIsian("SITI", "0899", "Jl. Melati 7 RT03 (rumah belakang)"), hasil)
    }

    // ── Format kirim ─────────────────────────────────────────────────────────

    @Test
    fun `jadwal hanya menerima YYYY-MM-DD`() {
        // Server menolak ISO8601 ber-Z/offset dengan 400 — disaring di klien
        // supaya dialognya tak tertutup lalu gagal diam-diam.
        assertEquals("2026-08-12", jadwalUntukServer("2026-08-12"))
        assertEquals("2026-08-12", jadwalUntukServer(" 2026-08-12 "))
        assertNull(jadwalUntukServer("2026-08-12T10:00:00Z"))
        assertNull(jadwalUntukServer("12/08/2026"))
        assertNull(jadwalUntukServer(""))
        assertNull(jadwalUntukServer(null))
    }

    @Test
    fun `foto dipetakan ke endpoint terautentikasi`() {
        // Upload komplain TIDAK disajikan sebagai berkas statis publik; memuat
        // "/uploads/..." apa adanya = gambar yang selalu gagal.
        assertEquals(
            "https://tridjaya.com/api/home-service/photo/abc.jpg",
            fotoHsUrl("/uploads/home-service/abc.jpg", base),
        )
        assertEquals("https://cdn.example.com/x.jpg", fotoHsUrl("https://cdn.example.com/x.jpg", base))
        assertNull(fotoHsUrl(null, base))
        assertNull(fotoHsUrl("  ", base))
    }

    @Test
    fun `status final tak menyisakan aksi`() {
        assertTrue(HS_SELESAI in HS_STATUS_FINAL)
        assertTrue(HS_ESKALASI in HS_STATUS_FINAL)
        assertTrue(HS_DIBATALKAN in HS_STATUS_FINAL)
        assertFalse(HS_MENUNGGU_TINDAK_LANJUT in HS_STATUS_FINAL)
    }

    @Test
    fun `label status memakai istilah yang dipahami lapangan`() {
        assertEquals("Perlu tindak lanjut", labelStatusHs(HS_MENUNGGU_TINDAK_LANJUT))
        assertEquals("Driver ditugaskan", labelStatusHs(HS_TARIK_DITUGASKAN))
        // Status tak dikenal (server lebih baru) tampil apa adanya, bukan kosong.
        assertEquals("status_baru_dari_server", labelStatusHs("status_baru_dari_server"))
    }

    // ── Komplain Saya (riwayat pelapor) ──────────────────────────────────────

    /**
     * `mine` dan `sayaLapor` TIDAK boleh dikirim bersama.
     *
     * Server mematikan penyaring tugas saat `sayaLapor` menyala
     * (`teknisi_id = None`), tapi kalau app ikut mengirim `mine=true` ia
     * menyatakan dua pertanyaan sekaligus. Untuk pemegang `homeservice.task`
     * server bahkan MEMAKSA `mine` nyala — itulah sebabnya teknisi yang melapor
     * dulu tak punya satu pun daftar yang memuat tiketnya sendiri.
     */
    @Test
    fun `mode Komplain Saya memakai sayaLapor, bukan mine`() {
        assertEquals(true, HsMode.SAYA_LAPOR.sayaLapor)
        assertNull("mine wajib null — dua pertanyaan berbeda", HsMode.SAYA_LAPOR.mine)
        // Komplain yang berujung penarikan unit TETAP laporannya.
        assertNull("jenis wajib null — kedua jalur ikut", HsMode.SAYA_LAPOR.jenis)
    }

    /**
     * Satu-satunya mode yang TIDAK menyaring status. Empat mode lain adalah
     * ANTRIAN KERJA (yang tuntas harus keluar supaya badge bisa turun ke nol);
     * ini RIWAYAT PRIBADI — pelapor justru paling ingin tahu tiketnya sudah
     * `selesai`/`eskalasi`. Menyaringnya membuat tiket lenyap dari layar tepat
     * pada saat kabarnya datang.
     */
    @Test
    fun `Komplain Saya menampilkan status final juga`() {
        val aktif = HsMode.SAYA_LAPOR.statusAktif
        assertTrue("selesai wajib tampil", HS_SELESAI in aktif)
        assertTrue("eskalasi wajib tampil", HS_ESKALASI in aktif)
        assertTrue("dibatalkan wajib tampil", HS_DIBATALKAN in aktif)
        // Dan tetap memuat yang sedang berjalan.
        assertTrue(HS_BARU in aktif)
        assertTrue(HS_DITUGASKAN in aktif)
        assertTrue(HS_MENUNGGU_TARIK in aktif)
    }

    /**
     * Empat mode lain TIDAK boleh ikut melebar. Kalau salah satunya kelak
     * memakai `HS_STATUS_SEMUA`, badge antriannya berhenti bisa turun ke nol.
     */
    @Test
    fun `mode antrian kerja tetap menyaring status`() {
        listOf(HsMode.TRIASE, HsMode.TEKNISI, HsMode.TARIK, HsMode.DRIVER).forEach { mode ->
            assertFalse(
                "${mode.name} tak boleh memuat status final",
                mode.statusAktif.any { it in HS_STATUS_FINAL },
            )
            assertNull("${mode.name} bukan daftar laporan pribadi", mode.sayaLapor)
        }
    }

    /**
     * Bukti serah terima unit yang DITARIK. Daftarnya cuma bertambah
     * (`unggahFoto` menulis `fotoTerunggah + r.data`) sementara aksinya cuma
     * mengirim satu URL — sampai 2026-08-29 yang terkirim elemen PERTAMA,
     * yaitu jepretan tertua yang masih tertahan di layar.
     */
    @Test
    fun `foto ambil unit memakai jepretan TERBARU`() {
        // Tombolnya sendiri berbunyi "Foto siap — jepret ulang". Menekannya
        // MENAMBAH, tidak mengganti — jadi `first` mengirim justru foto yang
        // barusan diminta diganti. Ini SATU-SATUNYA jalan masuk bug-nya:
        // foto bukti kunjungan tak bisa nyangkut ke sini (tombol ambil-unit
        // hanya tampil di status `tarik_ditugaskan`, dan status cuma berubah
        // lewat `muat()` yang berjalan setelah daftar dikosongkan).
        assertEquals("/b.webp", fotoUntukAmbilUnit(listOf("/a.webp", "/b.webp")))
        // Tiga jepretan: aturannya sama, elemen terakhir. Ada di sini karena
        // "jepret ulang" bisa ditekan berkali-kali.
        assertEquals("/c.webp", fotoUntukAmbilUnit(listOf("/a.webp", "/b.webp", "/c.webp")))
        // Satu foto: kedua aturan sepakat, jadi ini BUKAN kasus yang
        // membedakan — ada di sini supaya jalur normal ikut terkunci.
        assertEquals("/satu.webp", fotoUntukAmbilUnit(listOf("/satu.webp")))
        // Belum ada foto sama sekali: null, bukan string kosong. Server
        // membedakan "tanpa bukti" dari "bukti beralamat kosong".
        assertNull(fotoUntukAmbilUnit(emptyList()))
    }
}
