package com.krisoft.tridjayaelektronik.ui.aktivitas

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pembeda "boleh dicoba lagi" vs "sudah diputus server".
 *
 * ## Kejadian yang melahirkan test ini
 *
 * 2026-08-21, produksi. Gerbang bukti-lintas-hari menolak foto yang diunggah
 * ulang dari hari sebelumnya dengan pesan yang sudah ditulis lengkap:
 *
 *   "Foto ini sudah diunggah pada tanggal 19-08-2026. GANTI dengan foto baru
 *    yang diambil hari ini sebelum mengirim..."
 *
 * Tapi `AktivitasViewModel` menempelkan ekornya sendiri ke SETIAP kegagalan
 * unggah, tanpa memeriksa apa pun:
 *
 *   "...Tekan \"Kirim bukti\" lagi untuk melanjutkan dari gambar ini."
 *
 * Yang dibaca karyawan berakhir dengan perintah kedua, dan perintah kedua itu
 * mustahil berhasil. Angkanya: Tresandila 13 percobaan, Dita 12, Mamun 10 —
 * semuanya atas berkas yang sama, semuanya dijawab 400 identik. Agus Gunanto
 * berhenti setelah SATU percobaan; dia satu-satunya yang menuruti kalimat
 * servernya.
 *
 * Test ini mengunci pembedanya. Ia sengaja memakai ANGKA HTTP mentah, bukan
 * konstanta dari kode produksi — test yang membandingkan konstanta dengan
 * dirinya sendiri selalu hijau (pola yang sama dijelaskan di
 * [AktivitasBuktiPlanTest]).
 */
class AktivitasGagalPermanenTest {

    @Test
    fun `400 dan 422 adalah penolakan permanen`() {
        // 400 = gerbang bukti-lintas-hari, gerbang batas-atas jobdeskIndex,
        // ukuran/format berkas. Semuanya keputusan atas ISI, bukan kegagalan
        // penyampaian — mengirim ulang byte yang sama menghasilkan jawaban yang
        // sama.
        assertTrue("400 wajib permanen", gagalPermanen(400))
        assertTrue("422 wajib permanen", gagalPermanen(422))
    }

    @Test
    fun `kegagalan yang memang bisa sembuh TIDAK boleh dianggap permanen`() {
        // Kalau salah satu dari ini divonis permanen, karyawan disuruh menyerah
        // atas kegagalan yang sebenarnya hilang sendiri dalam beberapa detik.
        assertFalse("408 timeout layak diulang", gagalPermanen(408))
        assertFalse("429 rate limit layak diulang", gagalPermanen(429))
        assertFalse("500 layak diulang", gagalPermanen(500))
        assertFalse("502 layak diulang", gagalPermanen(502))
        assertFalse("503 layak diulang", gagalPermanen(503))
        assertFalse("504 layak diulang", gagalPermanen(504))
    }

    @Test
    fun `tanpa status HTTP dianggap boleh dicoba lagi`() {
        // Kegagalan jaringan (DNS, koneksi putus, TLS) tak pernah punya status.
        // Defaultnya WAJIB "boleh coba lagi": kerugiannya asimetris — salah
        // menebak permanen sebagai sementara cuma menyisakan satu tombol yang
        // tak menolong, sedangkan sebaliknya menyuruh orang menyerah.
        assertFalse("null wajib dianggap sementara", gagalPermanen(null))
    }

    @Test
    fun `401 dan 403 bukan urusan dialog bukti`() {
        // Ditangani jalur sesi/izin (logout paksa, peta kemampuan), bukan
        // dialog "ganti fotomu" yang tak nyambung dengan masalahnya.
        assertFalse("401 milik jalur sesi", gagalPermanen(401))
        assertFalse("403 milik jalur izin", gagalPermanen(403))
    }

    @Test
    fun `sukses tidak pernah permanen`() {
        // Penjaga arah: fungsi ini hanya boleh dipanggil di cabang kegagalan,
        // tapi kalau suatu saat ia dipanggil di jalur sukses, jangan sampai
        // 2xx memunculkan dialog penolakan.
        assertFalse(gagalPermanen(200))
        assertFalse(gagalPermanen(201))
        assertFalse(gagalPermanen(204))
    }
}
