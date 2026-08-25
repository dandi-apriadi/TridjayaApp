package com.krisoft.tridjayaelektronik.ui.vertel

import com.krisoft.tridjayaelektronik.data.model.BarisVertelDto
import com.krisoft.tridjayaelektronik.data.model.DaftarVertelDto
import com.krisoft.tridjayaelektronik.data.model.RingkasanVertelDto
import com.krisoft.tridjayaelektronik.data.model.VertelHasil
import com.krisoft.tridjayaelektronik.data.model.VertelKanal
import com.krisoft.tridjayaelektronik.data.model.VertelPanggilanDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Cerminan `vertel::validasi_catat` + `vertel::ringkas`. */
class VertelPlanTest {

    private fun baris(
        no: String = "T1",
        hp: String? = "081234567890",
        wa: String? = "6281234567890",
        panggilan: VertelPanggilanDto? = null,
    ) = BarisVertelDto(noTransaksi = no, customerHp = hp, waNumber = wa, panggilan = panggilan)

    private fun daftar(total: Long, sudah: Long, tanpaNomor: Long) = DaftarVertelDto(
        ringkasan = RingkasanVertelDto(total = total, sudahDitelepon = sudah, tanpaNomor = tanpaNomor),
    )

    @Test
    fun `belum ditelepon adalah keadaan awal, bukan kegagalan`() {
        assertFalse(VertelPlan.sudahDitelepon(baris()))
        assertTrue(
            VertelPlan.sudahDitelepon(
                baris(panggilan = VertelPanggilanDto(kanal = VertelKanal.WA, hasil = VertelHasil.TERHUBUNG)),
            ),
        )
    }

    @Test
    fun `tautan WA hanya dari nomor yang sudah dinormalkan server`() {
        assertEquals("https://wa.me/6281234567890", VertelPlan.waUrl(baris()))

        // `waNumber` null = server SUDAH menilai nomor mentahnya dan menjawab
        // "tak layak ditautkan". Jatuh ke `customerHp` di sini akan membangun
        // tautan yang server sengaja tolak — chat ke nomor yang salah.
        val takLayak = baris(hp = "0274-555123 (kantor)", wa = null)
        assertNull(VertelPlan.waUrl(takLayak))
        // …tapi nomor mentahnya tetap bisa ditelepon lewat dialer, dan tetap
        // ditampilkan supaya verifikator bisa mengoreksinya.
        assertEquals("tel:0274-555123 (kantor)", VertelPlan.telUrl(takLayak))

        assertNull(VertelPlan.waUrl(baris(wa = "")))
        assertNull(VertelPlan.telUrl(baris(hp = "   ")))
    }

    @Test
    fun `kanal dan hasil divalidasi terhadap daftar tertutup server`() {
        assertTrue(VertelPlan.bolehSimpan(VertelKanal.TELEPON, VertelHasil.TIDAK_DIANGKAT))
        assertTrue(VertelPlan.bolehSimpan(VertelKanal.WA, VertelHasil.JADWAL_ULANG))
        assertFalse(VertelPlan.bolehSimpan("sms", VertelHasil.TERHUBUNG))
        assertFalse(VertelPlan.bolehSimpan(VertelKanal.TELEPON, "diangkat"))
    }

    @Test
    fun `catatan tidak diwajibkan bahkan saat ada komplain`() {
        // Sengaja tidak diperketat di klien: komplain yang tercatat tanpa
        // keterangan tetap jauh lebih berguna daripada komplain yang TAK JADI
        // dicatat karena verifikator sedang di telepon dan formnya menolak
        // disimpan. Server pun tak mewajibkannya.
        assertTrue(VertelPlan.bolehSimpan(VertelKanal.TELEPON, VertelHasil.TERHUBUNG))
    }

    @Test
    fun `persentase memakai penyebut yang bisa dihubungi, bukan total`() {
        // 10 transaksi, 4 di antaranya tanpa nomor, 6 sudah ditelepon →
        // 6/6 = 100%, BUKAN 6/10 = 60%. Baris tanpa nomor bukan kelalaian
        // verifikator; memasukkannya ke penyebut membuat target mustahil
        // dicapai oleh sebab yang bukan salahnya.
        assertEquals(100, VertelPlan.persenSelesai(daftar(total = 10, sudah = 6, tanpaNomor = 4)))
        assertEquals(50, VertelPlan.persenSelesai(daftar(total = 10, sudah = 5, tanpaNomor = 0)))
    }

    @Test
    fun `penyebut nol menghasilkan nol, bukan seratus`() {
        // Tak ada yang bisa dikerjakan BELUM berarti selesai — dan tak boleh
        // jadi pembagian nol.
        assertEquals(0, VertelPlan.persenSelesai(daftar(total = 0, sudah = 0, tanpaNomor = 0)))
        assertEquals(0, VertelPlan.persenSelesai(daftar(total = 3, sudah = 0, tanpaNomor = 3)))
    }

    @Test
    fun `urutan kerja mendahulukan yang belum ditelepon dan membuang yang tak bisa dihubungi ke bawah`() {
        val selesai = baris(
            no = "SUDAH",
            panggilan = VertelPanggilanDto(kanal = VertelKanal.TELEPON, hasil = VertelHasil.TERHUBUNG),
        )
        val takAdaNomor = baris(no = "TANPA", hp = null, wa = null)
        val belum = baris(no = "BELUM")

        assertEquals(
            listOf("BELUM", "SUDAH", "TANPA"),
            VertelPlan.urutKerja(listOf(selesai, takAdaNomor, belum)).map { it.noTransaksi },
        )
    }

    @Test
    fun `urutan stabil untuk baris yang setara`() {
        // Urutan server dipertahankan di dalam kelompok yang sama — verifikator
        // yang menggulir tak boleh melihat daftarnya berlompatan tiap refresh.
        val a = baris(no = "A")
        val b = baris(no = "B")
        val c = baris(no = "C")
        assertEquals(
            listOf("A", "B", "C"),
            VertelPlan.urutKerja(listOf(a, b, c)).map { it.noTransaksi },
        )
    }
}
