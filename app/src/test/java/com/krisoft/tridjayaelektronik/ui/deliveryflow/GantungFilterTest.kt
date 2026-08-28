package com.krisoft.tridjayaelektronik.ui.deliveryflow

import com.krisoft.tridjayaelektronik.data.model.DeliveryJobDto
import com.krisoft.tridjayaelektronik.ui.activity.GANTUNG_TENGGAT_MS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Saringan umur antrian "Konfirmasi Pembayaran" (SPK Gantung) — divisi kasir.
 *
 * Yang dijaga di sini BUKAN tampilan chip melainkan (a) dua penjagaan yang
 * mencegah saringan ini berubah jadi penyembunyi pekerjaan — kelas kekeliruan
 * yang sudah membuat saringan PERIODE dilarang di antrian yang sama — dan (b)
 * kesepakatan angkanya dengan kartu Activity, yang memakai ambang yang persis
 * sama lewat `spkGantungRingkas`.
 */
class GantungFilterTest {

    /** `nowMillis` tetap supaya test tak bergantung jam mesin. */
    private val now = 1_800_000_000_000L // 2027-01-15, arbitrer

    /**
     * Bentuk yang BENAR-BENAR dikirim backend sejak 2026-07-30: jam dinding WIB
     * tanpa penanda zona. Diformat dengan zona device — sama seperti
     * `parseWallClockMillis` membacanya — jadi bolak-baliknya persis di zona mana
     * pun test ini jalan. Helper ber-UTC akan meleset sebesar offset device.
     */
    private fun wibString(millis: Long) = java.text.SimpleDateFormat(
        "yyyy-MM-dd HH:mm:ss", java.util.Locale.US
    ).format(java.util.Date(millis))

    private fun jamLalu(n: Long) = wibString(now - n * 60 * 60 * 1000L)

    /** Satu SPK; tiap argumen = `deliveredAt` satu unit di dalamnya. */
    private fun grup(kode: String, vararg deliveredAt: String?) = SpkBatchGroup(
        kode = kode,
        jobs = deliveredAt.mapIndexed { i, t ->
            DeliveryJobDto(id = "$kode-$i", kodePengiriman = "$kode-1u${i + 1}", deliveredAt = t)
        },
    )

    // ── vonis per grup ───────────────────────────────────────────────────────

    @Test
    fun `grup lewat tenggat bila salah satu unitnya sudah lewat 24 jam`() {
        // SPK yang unitnya diantar bertahap: menuntut SELURUH unit menunggak akan
        // membuangnya dari ember yang justru dicari kasir.
        assertTrue(grupLewatTenggat(grup("A", jamLalu(1), jamLalu(30)), now))
        assertFalse(grupLewatTenggat(grup("B", jamLalu(1), jamLalu(5)), now))
    }

    /** Ambangnya sama persis dengan kartu Activity — termasuk sisi ambangnya. */
    @Test
    fun `tepat di ambang belum dianggap lewat tenggat`() {
        assertFalse(grupLewatTenggat(grup("A", jamLalu(24)), now))
        assertTrue(grupLewatTenggat(grup("B", wibString(now - GANTUNG_TENGGAT_MS - 1000)), now))
    }

    /**
     * Cerminan `spkGantungRingkas`: baris ber-`deliveredAt` tak terbaca tetap
     * dihitung sebagai pekerjaan, tapi tak pernah dituduh lewat tenggat.
     * Menebak "lewat" akan menaruh SPK yang baru diantar di ember mendesak.
     */
    @Test
    fun `deliveredAt kosong atau rusak tidak dituduh lewat tenggat`() {
        assertFalse(grupLewatTenggat(grup("A", null), now))
        assertFalse(grupLewatTenggat(grup("B", ""), now))
        assertFalse(grupLewatTenggat(grup("C", "bukan tanggal"), now))
        // …tapi satu unit yang memang tua tetap menang atas saudaranya yang rusak.
        assertTrue(grupLewatTenggat(grup("D", "bukan tanggal", jamLalu(48)), now))
    }

    // ── penjagaan 1: chip hanya saat daftarnya bercampur ─────────────────────

    @Test
    fun `chip tak ditawarkan saat semuanya lewat tenggat`() {
        val groups = listOf(grup("A", jamLalu(30)), grup("B", jamLalu(48)))
        val hasil = saringPerGantung(groups, now, GantungSaring.SEMUA)
        assertFalse(hasil.tampilkanChip)
        assertEquals(groups, hasil.terlihat)
    }

    @Test
    fun `chip tak ditawarkan saat tak satu pun lewat tenggat`() {
        val groups = listOf(grup("A", jamLalu(1)), grup("B", jamLalu(5)))
        val hasil = saringPerGantung(groups, now, GantungSaring.SEMUA)
        assertFalse(hasil.tampilkanChip)
        assertEquals(groups, hasil.terlihat)
    }

    @Test
    fun `daftar kosong tak menawarkan chip`() {
        val hasil = saringPerGantung(emptyList(), now, GantungSaring.SEMUA)
        assertFalse(hasil.tampilkanChip)
        assertTrue(hasil.terlihat.isEmpty())
    }

    @Test
    fun `chip ditawarkan begitu daftarnya bercampur`() {
        val hasil = saringPerGantung(
            listOf(grup("A", jamLalu(1)), grup("B", jamLalu(30))), now, GantungSaring.SEMUA,
        )
        assertTrue(hasil.tampilkanChip)
    }

    // ── penjagaan 2: saringan diabaikan saat chip tak muncul ─────────────────

    /**
     * Kebuntuan yang dicegah, dan di sini embernya digerakkan JAM bukan perbuatan
     * petugas: kasir memilih "Belum 24 jam", meninggalkan HP-nya, lalu
     * tarik-refresh esok pagi ketika semua sisanya sudah lewat tenggat. Kalau
     * saringannya tetap berlaku sementara chip-nya lenyap, layarnya kosong TANPA
     * jalan kembali — dan antrian yang paling mendesak terbaca sebagai beres.
     */
    @Test
    fun `saringan diabaikan saat chip tak ditampilkan`() {
        val semuaLewat = listOf(grup("A", jamLalu(30)), grup("B", jamLalu(48)))
        val hasil = saringPerGantung(semuaLewat, now, GantungSaring.BELUM_TENGGAT)
        assertFalse(hasil.tampilkanChip)
        // Ember "Belum 24 jam" kosong, tapi layarnya TIDAK.
        assertEquals(semuaLewat, hasil.terlihat)
    }

    @Test
    fun `saringan lewat-tenggat pun diabaikan saat tak ada yang lewat`() {
        val semuaBaru = listOf(grup("A", jamLalu(1)), grup("B", jamLalu(5)))
        val hasil = saringPerGantung(semuaBaru, now, GantungSaring.LEWAT_TENGGAT)
        assertEquals(semuaBaru, hasil.terlihat)
    }

    // ── penyaringan saat bercampur ───────────────────────────────────────────

    private val bercampur = listOf(
        grup("A", jamLalu(1)),
        grup("B", jamLalu(30)),
        grup("C", jamLalu(5)),
        grup("D", jamLalu(72)),
    )

    @Test
    fun `lewat tenggat menampilkan yang tertua saja`() {
        val hasil = saringPerGantung(bercampur, now, GantungSaring.LEWAT_TENGGAT)
        assertEquals(listOf("B", "D"), hasil.terlihat.map { it.kode })
    }

    @Test
    fun `belum 24 jam menampilkan sisanya`() {
        val hasil = saringPerGantung(bercampur, now, GantungSaring.BELUM_TENGGAT)
        assertEquals(listOf("A", "C"), hasil.terlihat.map { it.kode })
    }

    @Test
    fun `semua menampilkan daftar utuh`() {
        val hasil = saringPerGantung(bercampur, now, GantungSaring.SEMUA)
        assertEquals(bercampur, hasil.terlihat)
    }

    /**
     * Kedua ember MEMBELAH HABIS daftar: tak ada grup yang masuk keduanya, tak
     * ada yang tak masuk mana pun. Tanpa sifat ini, angka chip berhenti bisa
     * dipercaya sebagai "yang tak sedang tampil".
     */
    @Test
    fun `kedua ember membelah habis daftar`() {
        val lewat = saringPerGantung(bercampur, now, GantungSaring.LEWAT_TENGGAT).terlihat
        val belum = saringPerGantung(bercampur, now, GantungSaring.BELUM_TENGGAT).terlihat
        assertEquals(bercampur.size, lewat.size + belum.size)
        assertTrue((lewat.map { it.kode } intersect belum.map { it.kode }.toSet()).isEmpty())
    }

    /**
     * Urutan server (terbaru dulu) tak boleh teracak: antrian yang urutannya
     * berubah sendiri membuat kasir kehilangan tempatnya — alasan yang sama
     * dengan `groupJobsBySpk` yang sengaja tak menyortir ulang.
     */
    @Test
    fun `urutan daftar tidak diubah`() {
        val hasil = saringPerGantung(bercampur, now, GantungSaring.LEWAT_TENGGAT)
        assertEquals(listOf("B", "D"), hasil.terlihat.map { it.kode })
    }

    // ── label chip ───────────────────────────────────────────────────────────

    /**
     * Angka WAJIB ada: tanpanya pekerjaan yang tersaring HILANG dari pandangan
     * alih-alih sekadar tersembunyi, dan chip "Lewat tenggat (2)" inilah yang
     * menyambung dengan baris "2 lewat tenggat 24 jam" di kartu Activity.
     */
    @Test
    fun `label chip membawa angkanya`() {
        val hasil = saringPerGantung(bercampur, now, GantungSaring.SEMUA)
        assertEquals("Semua (4)", labelChipGantung(GantungSaring.SEMUA, hasil))
        assertEquals("Lewat tenggat (2)", labelChipGantung(GantungSaring.LEWAT_TENGGAT, hasil))
        assertEquals("Belum 24 jam (2)", labelChipGantung(GantungSaring.BELUM_TENGGAT, hasil))
    }

    /** Angka "Semua" = jumlah kedua ember, apa pun chip yang sedang aktif. */
    @Test
    fun `angka chip tidak berubah saat saringan berpindah`() {
        GantungSaring.entries.forEach { s ->
            val hasil = saringPerGantung(bercampur, now, s)
            assertEquals("Semua (4)", labelChipGantung(GantungSaring.SEMUA, hasil))
            assertEquals(2, hasil.jumlahLewatTenggat)
            assertEquals(2, hasil.jumlahBelumTenggat)
        }
    }

    /** Urutan enum = urutan tampil; "Semua" harus tetap yang pertama (default). */
    @Test
    fun `semua adalah pilihan pertama`() {
        assertEquals(GantungSaring.SEMUA, GantungSaring.entries.first())
    }
}
