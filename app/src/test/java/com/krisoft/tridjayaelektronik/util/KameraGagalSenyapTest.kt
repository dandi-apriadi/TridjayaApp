package com.krisoft.tridjayaelektronik.util

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Penjaga: **tak boleh ada callback `TakePicture` yang menelan `ok == false`.**
 *
 * Pola `TakePicture() { ok -> if (ok) { … } }` tanpa cabang lain terpasang di
 * ENAM BELAS tempat sampai 2026-08-28, dan tiap satunya membuang kegagalan
 * simpan tanpa jejak: nol pesan, nol unggahan, nol log. Petugas melihat app
 * kamera terbuka lalu tertutup, kembali ke layar yang tampak normal, dan
 * menyangka buktinya terkirim.
 *
 * Terbukti menggigit di Kupon Gebyar (Haurgeulis, 28 Agu 2026): "sempat bisa
 * upload tapi hanya 1 yang terupload, sisanya tidak" — tanpa satu pun galat yang
 * bisa ditunjukkan. Kelas kegagalan ini juga merusak PENYELIDIKAN: selama kamera
 * bisa gagal tanpa bersuara, laporan "kamera normal" tak bisa dipakai
 * membuktikan penyimpanan HP sehat, dan itu sempat menyesatkan diagnosa.
 *
 * Test ini memindai sumber, bukan perilaku, karena kelas kegagalannya justru
 * ketiadaan kode — tak ada yang bisa dipanggil untuk mengujinya. Sengaja
 * memindai SELURUH `app/src/main`: yang berbahaya bukan satu layar, melainkan
 * salin-tempel pola itu ke layar berikutnya.
 */
class KameraGagalSenyapTest {

    private fun sumberMain(): List<File> {
        // Test berjalan dengan working dir di `app/`, tapi jangan diandalkan —
        // naik sampai menemukan pohon sumbernya.
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            val kandidat = File(dir, "app/src/main/java")
            if (kandidat.isDirectory) return kandidat.walkTopDown().filter { it.extension == "kt" }.toList()
            val kandidat2 = File(dir, "src/main/java")
            if (kandidat2.isDirectory) return kandidat2.walkTopDown().filter { it.extension == "kt" }.toList()
            dir = dir.parentFile
        }
        error("pohon sumber app/src/main/java tak ditemukan dari ${File("").absolutePath}")
    }

    @Test
    fun `setiap callback TakePicture menangani kegagalan simpan`() {
        val berkas = sumberMain()
        assertTrue("pemindai tak menemukan berkas Kotlin — jalurnya salah", berkas.size > 50)

        // `TakePicture()` diikuti pembuka lambda dan nama parameternya.
        val pola = Regex("""TakePicture\(\)\s*\)?\s*\{\s*(\w+)\s*->""")
        val pelanggar = mutableListOf<String>()

        for (f in berkas) {
            // Berkas yang MENDEFINISIKAN kalimatnya menyebut pola itu di dalam
            // KDoc untuk menjelaskan dirinya sendiri — bukan callback nyata.
            if (f.name == "PesanKamera.kt") continue

            val isi = f.readText()
            for (m in pola.findAll(isi)) {
                // Batas lambda dihitung dari kurung kurawal, BUKAN jendela
                // sepanjang N karakter: callback-callback ini panjangnya jauh
                // berbeda (yang di KuponGebyarScreen membawa belasan baris
                // komentar), jadi jendela tetap akan salah menuduh yang panjang
                // atau melewatkan yang pendek. Salah satunya sudah terjadi saat
                // penjaga ini pertama dijalankan.
                // KOMENTAR DIBUANG DULU. Versi pertama penjaga ini tidak
                // melakukannya, dan itu membuatnya lolos-palsu: callback yang
                // penanganannya DIHAPUS tetap dinyatakan aman selama komentar di
                // atasnya masih menyebut nama konstanta — persis bentuk yang
                // tertinggal kalau seseorang menghapus satu baris `else`.
                // Dibuktikan dengan sengaja merusak `HomeServiceLaporScreen`:
                // penjaga versi lama tetap hijau.
                val blok = tanpaKomentar(badanLambda(isi, m.range.last))
                if (!blok.contains("PESAN_KAMERA_TAK_TERSIMPAN")) {
                    val baris = isi.substring(0, m.range.first).count { it == '\n' } + 1
                    pelanggar += "${f.name}:$baris"
                }
            }
        }

        assertEquals(
            "Callback TakePicture berikut menelan `ok == false` tanpa memberi tahu pengguna. " +
                "Tambahkan cabang yang memakai PESAN_KAMERA_TAK_TERSIMPAN " +
                "(lihat KuponGebyarScreen/AttendanceScreen sebagai contoh): $pelanggar",
            emptyList<String>(),
            pelanggar,
        )
    }

    /** Buang komentar baris dan blok — hanya KODE yang boleh menghitung. */
    private fun tanpaKomentar(s: String): String =
        s.replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), " ")
            .replace(Regex("""//[^\n]*"""), " ")

    /**
     * Isi lambda mulai dari `{` pembuka di/atau sebelum [dariIndeks], sampai `}`
     * pasangannya. Cukup menghitung kurung: badan callback ini tak memuat string
     * atau komentar ber-kurawal tak seimbang, dan kalau kelak memuat, yang
     * terjadi hanyalah blok terbaca terlalu panjang — arah yang aman (penjaga
     * jadi lebih longgar, bukan menuduh palsu).
     */
    private fun badanLambda(isi: String, dariIndeks: Int): String {
        val buka = isi.lastIndexOf('{', dariIndeks).takeIf { it >= 0 } ?: return ""
        var dalam = 0
        var i = buka
        while (i < isi.length) {
            when (isi[i]) {
                '{' -> dalam++
                '}' -> {
                    dalam--
                    if (dalam == 0) return isi.substring(buka, i + 1)
                }
            }
            i++
        }
        return isi.substring(buka)
    }

    @Test
    fun `pesannya menyebut dua kemungkinan, tanpa memvonis salah satu`() {
        // `TakePicture` cuma menyerahkan Boolean — "dibatalkan" dan "gagal
        // menulis" TIDAK bisa dipisahkan dari sana. Kalimat yang memvonis salah
        // satunya akan salah pada separuh kasus: menuduh orang membatalkan
        // padahal penyimpanannya penuh, atau sebaliknya menakut-nakuti soal
        // penyimpanan pada orang yang memang sengaja membatalkan.
        val p = PESAN_KAMERA_TAK_TERSIMPAN
        assertTrue("harus menyatakan fotonya tidak tersimpan: $p", p.contains("tidak jadi tersimpan"))
        assertTrue("harus menyebut kemungkinan membatalkan: $p", p.contains("membatalkan"))
        assertTrue("harus menyebut kemungkinan penyimpanan: $p", p.contains("penyimpanan"))
    }
}
