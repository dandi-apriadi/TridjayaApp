package com.krisoft.tridjayaelektronik.ui.deliveryflow

import com.krisoft.tridjayaelektronik.data.model.DiscountRequestDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Angka yang dibaca approver sebelum memutuskan. Bug yang dikunci di sini:
 * kartu HP dulu menjumlahkan `value` mentah, sehingga SPK dengan baris qty 2
 * menampilkan SEPARUH potongan dan berselisih dengan papan web untuk SPK yang
 * SAMA.
 */
class DiskonPotonganTest {

    private fun req(
        id: String = "d1",
        baris: Int? = 1,
        unitIds: List<String> = listOf("j1"),
        value: Double = 500_000.0,
        discountType: String = "amount",
        sebelum: Double? = 13_000_000.0,
        sesudah: Double? = 12_500_000.0,
        createdAt: String = "2026-08-07T01:00:00Z",
        pengaju: String? = "Administrator",
        accOleh: String? = null,
        status: String = "pending",
    ) = DiscountRequestDto(
        id = id, spkBatchKode = "DLV-M11112222", baris = baris, deliveryJobIds = unitIds,
        discountType = discountType, value = value, hargaSebelum = sebelum, hargaSesudah = sesudah,
        createdAt = createdAt, requestedByName = pengaju, accOleh = accOleh, status = status,
    )

    @Test
    fun `baris qty 2 - selisih harga dipakai APA ADANYA, tak dikali unit lagi`() {
        // Server (`create_delivery` + `create_discount_request`) sudah menulis
        // hargaSebelum/hargaSesudah sebagai TOTAL SE-BARIS: 2 unit x 6,5jt
        // dipotong 2 x 500rb. Mengalikannya lagi dengan unitTerdampak
        // menghitung qty dua kali — bug 2026-08-07 s/d 2026-08-16 yang membuat
        // kartu HP menampilkan 2x lipat papan web untuk SPK yang sama.
        val d = req(unitIds = listOf("j1", "j2"), sebelum = 13_000_000.0, sesudah = 12_000_000.0)
        assertEquals(2, unitTerdampak(d))
        assertEquals(1_000_000.0, potonganPengajuan(d), 0.01)
    }

    @Test
    fun `diskon persen dihitung dari selisih harga, bukan angka persennya`() {
        val d = req(discountType = "percent", value = 5.0, sebelum = 10_000_000.0, sesudah = 9_500_000.0)
        assertEquals(500_000.0, potonganPengajuan(d), 0.01)
    }

    @Test
    fun `pengajuan warisan tanpa baris tetap dihitung satu unit di label`() {
        // deliveryJobIds = SELURUH unit SPK saat baris null (batch_job_ids),
        // jadi ia tak boleh dibaca sebagai "jumlah unit baris ini" di label
        // ringkas. Potongannya sendiri kini murni dari selisih harga.
        val d = req(baris = null, unitIds = listOf("j1", "j2", "j3"))
        assertEquals(1, unitTerdampak(d))
        assertEquals(500_000.0, potonganPengajuan(d), 0.01)
    }

    @Test
    fun `harga null tidak ditebak dari value`() {
        assertEquals(0.0, potonganPengajuan(req(sebelum = null)), 0.01)
        assertEquals(0.0, potonganPengajuan(req(sesudah = null)), 0.01)
    }

    @Test
    fun `total SPK menjumlah seluruh baris`() {
        val a = req(id = "a", baris = 1, unitIds = listOf("j1", "j2"), sebelum = 13_000_000.0, sesudah = 12_000_000.0)
        val b = req(id = "b", baris = 2, unitIds = listOf("j3"), sebelum = 4_000_000.0, sesudah = 3_800_000.0)
        assertEquals(1_200_000.0, totalPotonganSpk(listOf(a, b)), 0.01)
        assertEquals(0.0, totalPotonganSpk(emptyList()), 0.01)
    }

    // ── potonganMenunggu — angka "bila disetujui" di dialog detail SPK ──────
    //
    // Laporan approver 2026-08-16: dialog cuma menampilkan "Diskon berjalan
    // -Rp 0" untuk SPK yang pengajuannya justru sedang dia putuskan.

    @Test
    fun `menunggu hanya menghitung pengajuan yang belum diputus`() {
        val pending = req(id = "p", baris = 1, status = "pending", sebelum = 4_099_000.0, sesudah = 3_799_000.0)
        val disetujui = req(id = "a", baris = 2, status = "approved", sebelum = 1_000_000.0, sesudah = 900_000.0)
        val ditolak = req(id = "r", baris = 3, status = "rejected", sebelum = 2_000_000.0, sesudah = 1_800_000.0)
        val dilepas = req(id = "d", baris = 4, status = "dilepas", sebelum = 3_000_000.0, sesudah = 2_500_000.0)
        // `approved` SUDAH terhitung di totalDiskonBerjalan — menjumlahkan
        // keduanya berarti diskon dihitung dua kali di baris "bila disetujui".
        assertEquals(300_000.0, potonganMenunggu(listOf(pending, disetujui, ditolak, dilepas)), 0.01)
    }

    @Test
    fun `tak ada pengajuan menunggu = nol, bukan tebakan dari value`() {
        assertEquals(0.0, potonganMenunggu(emptyList()), 0.01)
        assertEquals(0.0, potonganMenunggu(listOf(req(status = "approved"))), 0.01)
    }

    @Test
    fun `kartu diurut menurut baris, bukan urutan pengajuan dari server`() {
        val b3 = req(id = "c", baris = 3, createdAt = "2026-08-07T03:00:00Z")
        val b1 = req(id = "a", baris = 1, createdAt = "2026-08-07T01:00:00Z")
        val warisan = req(id = "w", baris = null)
        // Server mengirim created_at DESC.
        val hasil = urutPengajuanSpk(listOf(b3, warisan, b1))
        assertEquals(listOf("a", "c", "w"), hasil.map { it.id })
    }

    // ── Kartu ringkas (2026-08-07) ───────────────────────────────────────────

    @Test
    fun `ringkasHarga menggantikan dua baris harga dengan satu`() {
        val d = req(unitIds = listOf("j1", "j2"), sebelum = 7_000_000.0, sesudah = 6_700_000.0)
        assertEquals("2 unit · 7.000.000 → 6.700.000", ringkasHarga(d))
    }

    @Test
    fun `harga tak lengkap tidak ditebak - cuma jumlah unitnya`() {
        assertEquals("1 unit", ringkasHarga(req(sebelum = null)))
        assertEquals("1 unit", ringkasHarga(req(sesudah = null)))
    }

    // "Acc oleh" ikut baris harga, bukan baris `InfoLine` sendiri: sebagai
    // InfoLine ia merentang selebar kartu sehingga keluar dari indentasi
    // barangnya dan terbaca seperti keterangan milik SPK.
    @Test
    fun `acc menempel di baris harga hanya saat diminta`() {
        val d = req(sebelum = 3_500_000.0, sesudah = 3_350_000.0, accOleh = "Pak Kiryanto")
        assertEquals("3.500.000 → 3.350.000", ringkasHarga(d))
        assertEquals(
            "3.500.000 → 3.350.000 · acc Pak Kiryanto",
            ringkasHarga(d, sertakanAcc = true),
        )
    }

    @Test
    fun `acc kosong tidak meninggalkan pemisah menggantung`() {
        assertEquals(
            "3.500.000 → 3.350.000",
            ringkasHarga(req(sebelum = 3_500_000.0, sesudah = 3_350_000.0, accOleh = "  "), sertakanAcc = true),
        )
        assertEquals(
            "3.500.000 → 3.350.000",
            ringkasHarga(req(sebelum = 3_500_000.0, sesudah = 3_350_000.0, accOleh = null), sertakanAcc = true),
        )
    }

    // Harga tak lengkap TAPI acc ada: acc tetap ikut, tak boleh hilang hanya
    // karena angkanya tak lengkap.
    @Test
    fun `acc tetap ikut walau harga tak lengkap`() {
        assertEquals(
            "acc Bu Dinda",
            ringkasHarga(req(sebelum = null, accOleh = "Bu Dinda"), sertakanAcc = true),
        )
    }

    @Test
    fun `ribuan sama persis dengan rupiah tanpa prefiks`() {
        assertEquals("0", ribuan(null))
        assertEquals("999", ribuan(999.0))
        assertEquals("7.000.000", ribuan(7_000_000.0))
    }

    @Test
    fun `pengaju sama di semua barang naik ke header`() {
        val p = listOf(req(id = "a", baris = 1), req(id = "b", baris = 2))
        assertEquals("Administrator", nilaiSeragam(p) { it.requestedByName })
    }

    @Test
    fun `pengaju berbeda tetap di barisnya masing-masing`() {
        val p = listOf(req(id = "a", baris = 1), req(id = "b", baris = 2, pengaju = "UJI Sales"))
        assertEquals(null, nilaiSeragam(p) { it.requestedByName })
    }

    @Test
    fun `satu nilai kosong membatalkan keseragaman - jangan mengklaim se-SPK`() {
        // Kalau 1 dari 3 barang tak punya accOleh, menaikkannya ke header
        // membuat approver mengira SELURUH SPK sudah di-acc orang itu.
        val p = listOf(
            req(id = "a", baris = 1, accOleh = "Pak Budi"),
            req(id = "b", baris = 2, accOleh = "Pak Budi"),
            req(id = "c", baris = 3, accOleh = null),
        )
        assertEquals(null, nilaiSeragam(p) { it.accOleh?.trim()?.ifBlank { null } })
        // Blank dianggap kosong oleh pemanggil (fungsi ini cuma membandingkan).
        val q = listOf(req(id = "a", accOleh = "  "), req(id = "b", accOleh = "  "))
        assertEquals(null, nilaiSeragam(q) { it.accOleh?.trim()?.ifBlank { null } })
    }

    @Test
    fun `daftar kosong atau satu barang`() {
        assertEquals(null, nilaiSeragam(emptyList()) { it.requestedByName })
        assertEquals("Administrator", nilaiSeragam(listOf(req())) { it.requestedByName })
    }

    // ── Keputusan PER BARANG (2026-08-07) ────────────────────────────────────
    // MEMBALIK perilaku 2026-08-06: dulu satu keputusan mem-FAN-OUT ke seluruh
    // pengajuan `pending` sebatch dan tiap baris yang diputus langsung dilepas
    // sendiri-sendiri ke PDI. Sekarang keputusan hanya mengenai barang yang
    // ditunjuk, dan SPK baru lanjut ke PDI setelah SELURUH barangnya tuntas.

    @Test
    fun `tuntas hanya approved dan dilepas`() {
        assertEquals(true, barisTuntas("approved"))
        assertEquals(true, barisTuntas("dilepas"))
        // `rejected` bolanya di SALES (revisi / lanjut tanpa diskon) — SPK tetap
        // tertahan. Menganggapnya tuntas = kartu mengklaim SPK sudah jalan.
        assertEquals(false, barisTuntas("rejected"))
        assertEquals(false, barisTuntas("pending"))
    }

    @Test
    fun `kemajuan dihitung per barang, bukan per SPK`() {
        val p = listOf(
            req(id = "a", baris = 1, status = "approved"),
            req(id = "b", baris = 2, status = "dilepas"),
            req(id = "c", baris = 3, status = "pending"),
        )
        assertEquals("2 dari 3 barang tuntas", kemajuanSpk(p).teks)
        assertEquals(false, kemajuanSpk(p).semuaTuntas)
    }

    @Test
    fun `satu barang rejected menahan SPK walau sisanya disetujui`() {
        val p = listOf(
            req(id = "a", baris = 1, status = "approved"),
            req(id = "b", baris = 2, status = "approved"),
            req(id = "c", baris = 3, status = "rejected"),
        )
        assertEquals(false, kemajuanSpk(p).semuaTuntas)
        assertEquals("2 dari 3 barang tuntas", kemajuanSpk(p).teks)
    }

    @Test
    fun `seluruh barang tuntas menandai SPK jalan`() {
        val p = listOf(
            req(id = "a", baris = 1, status = "approved"),
            req(id = "b", baris = 2, status = "dilepas"),
        )
        assertEquals(true, kemajuanSpk(p).semuaTuntas)
        // Kartu kosong BUKAN "semua tuntas" — tak ada yang bisa disimpulkan.
        assertEquals(false, kemajuanSpk(emptyList()).semuaTuntas)
    }

    @Test
    fun `status dilepas punya labelnya sendiri, tidak jatuh ke teks mentah`() {
        assertEquals("Disetujui", labelStatusBaris("approved"))
        assertEquals("Tanpa diskon", labelStatusBaris("dilepas"))
        assertEquals("Ditolak", labelStatusBaris("rejected"))
        // Status asing dikembalikan apa adanya — ketahuan, bukan menghilang.
        assertEquals("entah_apa", labelStatusBaris("entah_apa"))
    }

    // ── Barang belum tuntas tak boleh tersembunyi ────────────────────────────

    @Test
    fun `daftar pendek tidak dipotong sama sekali`() {
        val p = (1..4).map { req(id = "d$it", baris = it) }
        assertEquals(p.map { it.id }, ringkasDaftar(p).map { it.id })
    }

    @Test
    fun `yang dipotong hanya barang yang sudah tuntas`() {
        // 6 barang: 4 tuntas + 2 pending. Batas 4 → 2 pending WAJIB tampil,
        // sisa kuota 2 diisi barang tuntas, urutan baris dipertahankan.
        val p = listOf(
            req(id = "a", baris = 1, status = "approved"),
            req(id = "b", baris = 2, status = "pending"),
            req(id = "c", baris = 3, status = "approved"),
            req(id = "d", baris = 4, status = "dilepas"),
            req(id = "e", baris = 5, status = "pending"),
            req(id = "f", baris = 6, status = "approved"),
        )
        assertEquals(listOf("a", "b", "c", "e"), ringkasDaftar(p, batas = 4).map { it.id })
    }

    @Test
    fun `batas ringkas kalah dari tombol yang harus terjangkau`() {
        // 6 barang pending: memotong di 4 berarti menyembunyikan 2 TOMBOL
        // keputusan di balik "Lihat N lainnya". Batasnya yang mengalah.
        val p = (1..6).map { req(id = "d$it", baris = it) }
        assertEquals(6, ringkasDaftar(p, batas = 4).size)
        // `rejected` juga belum tuntas — approver perlu melihat mana yang
        // masih dipegang sales, itu yang menjelaskan kenapa SPK tak jalan.
        val q = (1..6).map { req(id = "r$it", baris = it, status = "rejected") }
        assertEquals(6, ringkasDaftar(q, batas = 4).size)
    }

    /**
     * Satu-satunya titik unggah alur delivery yang boleh dari galeri, dan
     * sampai 2026-08-29 hasilnya dicap kalimat yang sama persis dengan hasil
     * kamera. Karena stempel jamnya jam PROSES, foto lama tercetak berjam hari
     * ini — approver tak punya cara membedakannya, dan server tak menyimpan
     * apa pun soal asal foto.
     */
    @Test
    fun `judul watermark bukti acc membedakan galeri dari kamera`() {
        assertEquals("TRIDJAYA · ACC DISKON", watermarkTitleBuktiAcc(dariGaleri = false))
        assertEquals("TRIDJAYA · ACC DISKON (GALERI)", watermarkTitleBuktiAcc(dariGaleri = true))
        // Ditegaskan terpisah dari string di atas: yang penting bagi approver
        // adalah ADANYA penanda, bukan ejaannya. Kalau kalimatnya kelak
        // diperhalus, baris ini yang harus tetap hijau.
        assertTrue(watermarkTitleBuktiAcc(dariGaleri = true).contains("GALERI"))
        assertFalse(watermarkTitleBuktiAcc(dariGaleri = false).contains("GALERI"))
    }
}
