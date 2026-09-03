package com.krisoft.tridjayaelektronik.ui.attendance

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Gate tombol Absen Masuk (geofence) — cerminan `AbsensiService::check_in` di
 * kinerja-service.
 *
 * **Aturannya sudah dua kali berbalik; keduanya benar pada zamannya, dan itu
 * sebabnya berkas ini menguji ARAH, bukan cuma teks.** Sampai 2026-08-15 layar
 * menulis "absen perlu review" untuk orang di luar area dan membiarkan tombolnya
 * hidup, padahal server MENOLAK dan tak ada baris yang lahir — nginx produksi
 * 4–15 Agustus 2026 mencatat 314 check-in dijawab 400, seluruhnya penolakan
 * geofence. Gate ini lahir untuk menutup janji palsu itu.
 *
 * Lalu keputusan user **2026-08-26** membalik sisi SERVER: di luar radius (asal
 * geofence-nya terkonfigurasi) TIDAK lagi ditolak, melainkan tercatat
 * `pending_review`. Sejak itu gate klien yang tetap mengunci adalah
 * satu-satunya yang menghentikan orang — kerugian yang sama, arah terbalik.
 *
 * Yang dijaga sekarang, dan masing-masing merugikan orang berbeda kalau lepas:
 *  - **tak boleh mengunci** (mengunci = orang ber-GPS kasar kehilangan absennya,
 *    padahal server menerimanya);
 *  - **wajib menyebut `pending_review` DAN akibat KPI-nya** (tanpa itu orangnya
 *    mengira beres, lalu kehadirannya kosong — janji setengah);
 *  - **tak boleh menjanjikan `pending_review` saat daftar cabang belum lengkap**
 *    (hasilnya bisa `valid` kalau ia memang di radius cabang lain).
 */
class GateAbsenMasukTest {

    @Test
    fun `di dalam area lolos tanpa kalimat apa pun`() {
        val gate = gateAbsenMasuk(
            inArea = true,
            daftarCabangLengkap = true,
            namaCabangTerdekat = "Pagaden",
            jarakM = 40
        )
        assertTrue(gate.boleh)
        assertNull("yang sudah di area tak perlu diberi tahu apa-apa", gate.alasan)
    }

    @Test
    fun `lokasi belum terbaca fail-open`() {
        val gate = gateAbsenMasuk(
            inArea = null,
            daftarCabangLengkap = true,
            namaCabangTerdekat = null,
            jarakM = null
        )
        assertTrue("tak tahu posisi bukan berarti di luar area", gate.boleh)
        assertNull(gate.alasan)
    }

    /**
     * Server menilai terhadap SELURUH cabang. Kalau app cuma memegang satu titik
     * (server lama yang hanya mengirim `geofence` tunggal), "di luar" bisa berarti
     * "sedang di cabang sebelah" — dan server menilainya `valid`, bukan
     * `pending_review`. Menjanjikan "Perlu Review" di sini akan salah untuk
     * separuh kasusnya.
     */
    @Test
    fun `daftar cabang belum lengkap tak menjanjikan status apa pun secara pasti`() {
        val gate = gateAbsenMasuk(
            inArea = false,
            daftarCabangLengkap = false,
            namaCabangTerdekat = "Pagaden",
            jarakM = 3_200
        )
        assertTrue("daftar sepotong tak boleh mengunci tombol", gate.boleh)
        assertNotNull("tapi orangnya tetap harus diberi tahu", gate.alasan)
        val alasan = gate.alasan!!
        assertTrue(
            "harus menyebut kemungkinan bertugas di cabang lain: $alasan",
            alasan.contains("cabang lain")
        )
        assertTrue(
            "dan kemungkinan satunya (Perlu Review) juga disebut: $alasan",
            alasan.contains("Perlu Review")
        )
    }

    /**
     * Inti perubahan 2026-08-26: di luar radius BUKAN lagi penolakan. Mengunci di
     * sini berarti app satu-satunya yang menghentikan orang yang server-nya
     * sendiri sudah menerima — persis 314 penolakan yang memicu perubahan itu.
     */
    @Test
    fun `di luar area dengan daftar lengkap TIDAK mengunci dan menyebut konsekuensinya`() {
        val gate = gateAbsenMasuk(
            inArea = false,
            daftarCabangLengkap = true,
            namaCabangTerdekat = "Pagaden",
            jarakM = 3_200
        )
        assertTrue("server menerimanya sebagai pending_review — app tak boleh mengunci", gate.boleh)
        val alasan = gate.alasan!!
        assertTrue("absennya TETAP tercatat: $alasan", alasan.contains("tetap tercatat"))
        assertTrue("statusnya apa: $alasan", alasan.contains("Perlu Review"))
        // Tanpa kalimat ini "tercatat" jadi janji setengah: orangnya mengira
        // beres, lalu kehadirannya kosong di KPI sampai atasan menyetujui.
        assertTrue("AKIBATNYA — belum dihitung hadir: $alasan", alasan.contains("belum dihitung hadir"))
        // Koma, bukan titik: `formatDistance` memakai Locale("in","ID").
        assertTrue("KENAPA — jaraknya: $alasan", alasan.contains("3,2 km"))
        assertTrue("KENAPA — dari mana: $alasan", alasan.contains("Pagaden"))
    }

    /**
     * Penjaga arah, terpisah dari uji teks di atas: SELURUH kombinasi masukan
     * harus lolos. Kalau kelak ada yang menambahkan cabang `GateMasuk(false)`
     * baru, test ini merah walau kalimatnya kebetulan masih cocok.
     */
    @Test
    fun `gate ini tak pernah mengunci untuk kombinasi masukan apa pun`() {
        val jarakUji = listOf(null, 0, 40, AMBANG_DUGAAN_TITIK_SALAH_M, AMBANG_DUGAAN_TITIK_SALAH_M + 1, 50_000)
        for (inArea in listOf(true, false, null)) {
            for (lengkap in listOf(true, false)) {
                for (cabang in listOf("Pagaden", null, "   ")) {
                    for (jarak in jarakUji) {
                        val gate = gateAbsenMasuk(inArea, lengkap, cabang, jarak)
                        assertTrue(
                            "mengunci pada inArea=$inArea lengkap=$lengkap cabang=$cabang jarak=$jarak",
                            gate.boleh
                        )
                    }
                }
            }
        }
    }

    /**
     * "Minta admin membetulkan titik cabang" hanya masuk akal dari dekat. Kalau
     * disodorkan pada jarak kilometer, seluruh cabang belajar menyalahkan setelan
     * untuk lokasi yang memang salah — dan admin dikirimi laporan palsu.
     */
    @Test
    fun `saran memperbaiki titik cabang hanya muncul dari dekat`() {
        val dekat = gateAbsenMasuk(
            inArea = false,
            daftarCabangLengkap = true,
            namaCabangTerdekat = "Pagaden",
            jarakM = AMBANG_DUGAAN_TITIK_SALAH_M
        )
        assertTrue(
            "tepat di ambang masih menyarankan: ${dekat.alasan}",
            dekat.alasan!!.contains("admin")
        )

        val jauh = gateAbsenMasuk(
            inArea = false,
            daftarCabangLengkap = true,
            namaCabangTerdekat = "Pagaden",
            jarakM = AMBANG_DUGAAN_TITIK_SALAH_M + 1
        )
        assertFalse(
            "lewat ambang tak boleh menyarankan menyalahkan admin: ${jauh.alasan}",
            jauh.alasan!!.contains("admin")
        )
        assertTrue(
            "yang jauh diberi langkah lain: ${jauh.alasan}",
            jauh.alasan!!.contains("perbarui lokasi")
        )
    }

    /**
     * Nama cabang / jarak bisa saja tak ada (cabang tanpa nama di config, jarak
     * gagal dihitung). Kalimatnya tetap harus utuh dan tetap memberi tahu
     * konsekuensinya — bukan "Kamu null dari null".
     */
    @Test
    fun `tanpa nama cabang dan tanpa jarak kalimatnya tetap utuh`() {
        val gate = gateAbsenMasuk(
            inArea = false,
            daftarCabangLengkap = true,
            namaCabangTerdekat = "   ",
            jarakM = null
        )
        assertTrue(gate.boleh)
        val alasan = gate.alasan!!
        assertFalse("tak boleh bocor null: $alasan", alasan.contains("null"))
        assertTrue(alasan.contains("di luar area toko"))
        assertTrue("konsekuensinya tetap disebut: $alasan", alasan.contains("Perlu Review"))
        // Jarak tak diketahui = tak bisa menuduh titiknya meleset.
        assertFalse("tanpa jarak jangan menyalahkan admin: $alasan", alasan.contains("admin"))
    }
}
