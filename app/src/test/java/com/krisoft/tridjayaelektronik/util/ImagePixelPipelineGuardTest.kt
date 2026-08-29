package com.krisoft.tridjayaelektronik.util

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Dua aturan tentang `ImagePixelPipeline` yang TIDAK BISA diuji lewat perilaku di JVM —
 * `Bitmap`/`BitmapFactory` adalah stub di unit test (repo ini tak pakai Robolectric), dan
 * kegagalannya (app tutup, layar membeku) hanya muncul di HP. Pola PERSIS
 * [PhotoWatermarkGuardTest], karena `ImagePixelPipeline` adalah mekanisme yang diekstrak dari
 * `PhotoWatermark.olahPiksel` untuk dipakai bersama empat pemanggil.
 *
 * ## Aturan 1 — `compress()` berimplementasi sebagai `runCatching { … }`
 *
 * `decodeByteArray`, `createScaledBitmap`, `createBitmap`, dan `.compress(` mengalokasi gambar
 * utuh di heap; manifest app ini tak memakai `largeHeap`. Yang dilempar saat memori habis adalah
 * `OutOfMemoryError` — turunan `Error`, BUKAN `Exception`. Tak ada `CoroutineExceptionHandler`
 * maupun `setDefaultUncaughtExceptionHandler` di `app/src/main`, jadi `Error` yang lolos dari
 * `viewModelScope.launch` MENUTUP app.
 *
 * ## Aturan 2 — pemanggil wajib `withContext(Dispatchers.…)`
 *
 * `viewModelScope` = `Dispatchers.Main.immediate`. Dekode + skala + rotasi EXIF + loop kompresi
 * untuk foto kamera penuh mengunci UI persis pada ketukan tombolnya kalau dijalankan di sana.
 */
class ImagePixelPipelineGuardTest {

    private val sumberRoot: File =
        sequenceOf(File("src/main/java"), File("app/src/main/java"))
            .firstOrNull { it.isDirectory }
            ?: error("pohon sumber tak ketemu — cwd=${File(".").absolutePath}")

    private val pipelineFile: File =
        File(sumberRoot, "com/krisoft/tridjayaelektronik/util/ImagePixelPipeline.kt")

    @Test
    fun `pipa compress berada di dalam runCatching`() {
        val isi = pipelineFile.readText()
        assertTrue(
            "fun compress(...) tak lagi berimplementasi sebagai `= runCatching { … }` — " +
                "alokasi bitmap WAJIB berada di dalam runCatching, kalau tidak OutOfMemoryError " +
                "lolos dari coroutine dan menutup app.",
            Regex("""\)\s*:\s*Pair<ByteArray,\s*Bitmap>\?\s*=\s*runCatching\s*\{""").containsMatchIn(isi),
        )

        // Dipotong dari "fun compress(" (BUKAN dari awal berkas) supaya KDoc kelas — yang
        // SENGAJA menyebut nama-nama panggilan berbahaya ini sebagai dokumentasi — tak ikut
        // dihitung sebagai "dipanggil sebelum runCatching".
        val badanFungsi = isi.substringAfter("fun compress(")
        val sebelumRunCatching = badanFungsi.substringBefore("runCatching {")
        val sesudahRunCatching = badanFungsi.substringAfter("runCatching {")
        val berbahaya = listOf("decodeByteArray", "createScaledBitmap", "createBitmap", ".compress(")
        berbahaya.forEach { panggilan ->
            assertTrue(
                "`$panggilan` ditemukan SEBELUM runCatching { di ImagePixelPipeline.kt — " +
                    "alokasi bitmap harus berada DI DALAM runCatching.",
                panggilan !in sebelumRunCatching,
            )
            assertTrue(
                "`$panggilan` seharusnya dipanggil di dalam badan runCatching compress(), " +
                    "tapi tak ditemukan di sana.",
                panggilan in sesudahRunCatching,
            )
        }
    }

    @Test
    fun `semua pemanggil membungkus dengan withContext`() {
        val pelanggar = mutableListOf<String>()
        sumberRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { berkas ->
                val baris = berkas.readLines()
                baris.forEachIndexed { i, isi ->
                    if (!isi.contains("ImagePixelPipeline.compress(")) return@forEachIndexed
                    // KDoc/komentar MENYEBUT nama fungsi ini tanpa memanggilnya.
                    if (isi.trimStart().let { it.startsWith("*") || it.startsWith("//") || it.startsWith("/*") }) {
                        return@forEachIndexed
                    }
                    if (!adaWithContextDiFungsiYangSama(baris, i)) {
                        pelanggar += "${berkas.name}:${i + 1}"
                    }
                }
            }
        assertTrue(
            "ImagePixelPipeline.compress dipanggil tanpa withContext(Dispatchers.…) di: " +
                "${pelanggar.joinToString()} — itu Dispatchers.Main.immediate, jadi dekode + " +
                "kompresi foto kamera membekukan layar tepat saat tombolnya ditekan.",
            pelanggar.isEmpty(),
        )
    }

    /**
     * Telusuri ke ATAS dari baris pemanggilan sampai deklarasi fungsi terdekat (indentasi 4
     * spasi, gaya seluruh repo ini), cari `withContext(Dispatchers.` di antaranya. Pola PERSIS
     * `PhotoWatermarkGuardTest.adaWithContextDiFungsiYangSama` — sengaja tak diparameterisasi
     * lintas berkas, mengikuti gaya repo (tiap guard test berdiri sendiri).
     */
    private fun adaWithContextDiFungsiYangSama(baris: List<String>, indeksPanggilan: Int): Boolean {
        val awalFungsi = Regex("""^\s{0,8}(private |internal |public )?(suspend )?fun\s""")
        for (i in indeksPanggilan downTo 0) {
            val isi = baris[i]
            if (isi.contains("withContext(Dispatchers.")) return true
            if (i < indeksPanggilan && awalFungsi.containsMatchIn(isi)) return false
        }
        return false
    }
}
