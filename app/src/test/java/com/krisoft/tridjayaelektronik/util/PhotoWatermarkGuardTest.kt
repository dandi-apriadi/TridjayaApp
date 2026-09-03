package com.krisoft.tridjayaelektronik.util

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Dua aturan tentang `PhotoWatermark` yang TIDAK BISA diuji lewat perilaku di
 * JVM — `Bitmap`/`BitmapFactory` adalah stub di unit test, dan kegagalannya
 * (app tutup, layar membeku) hanya muncul di HP. Jadi yang diperiksa di sini
 * adalah BENTUK kodenya, pola yang sama dengan [FileProviderPathsTest].
 *
 * ## Aturan 1 — seluruh pipa piksel di dalam `runCatching`
 *
 * `decodeByteArray`, `createScaledBitmap`, `createBitmap`, `Bitmap.copy`, dan
 * loop `compress` mengalokasi gambar utuh di heap; manifest app ini tak memakai
 * `largeHeap`. Yang dilempar saat memori habis adalah `OutOfMemoryError` —
 * turunan `Error`, BUKAN `Exception`. Di seluruh `app/src/main` tak ada
 * `CoroutineExceptionHandler` maupun `setDefaultUncaughtExceptionHandler`, jadi
 * `Error` yang lolos dari `viewModelScope.launch` MENUTUP app; petugas
 * kehilangan seluruh isian layar, bukan cuma fotonya.
 *
 * ## Aturan 2 — pemanggil wajib `withContext(Dispatchers.…)`
 *
 * `viewModelScope` = `Dispatchers.Main.immediate`. Dekode + skala + rotasi EXIF
 * + loop kompresi WebP untuk foto kamera penuh memakan ratusan milidetik sampai
 * beberapa detik; dijalankan di sana, layarnya membeku persis pada ketukan
 * tombol. Kelalaian ini sudah berulang di tiga layar berbeda (Kupon Gebyar,
 * pemasangan AC, detail komplain) — masing-masing ditulis dengan menyalin
 * tetangga yang kebetulan juga belum benar.
 */
class PhotoWatermarkGuardTest {

    private val sumberRoot: File =
        sequenceOf(File("src/main/java"), File("app/src/main/java"))
            .firstOrNull { it.isDirectory }
            ?: error("pohon sumber tak ketemu — cwd=${File(".").absolutePath}")

    private val utilFile: File =
        File(sumberRoot, "com/krisoft/tridjayaelektronik/util/PhotoWatermark.kt")

    /**
     * KOSONG, dan sebaiknya tetap begitu. Daftar ini sempat memuat
     * `KuponGebyarViewModel.kt` — diperbaiki di `fix/galeri-photo-picker`
     * (`ae2e7943`, rilis APK 3.05/vc116) yang belum mendarat saat test ini
     * ditulis — lalu dikosongkan begitu branch itu landing, sesuai catatan
     * yang ditinggalkan di sini. Menambah entri baru berarti mengecualikan
     * layar yang MEMBEKU di tangan petugas; perbaiki layarnya, jangan
     * daftarnya.
     */
    private val dikecualikan = emptySet<String>()

    @Test
    fun `pipa piksel berada di dalam runCatching`() {
        val isi = utilFile.readText()
        val publik = isi.substringAfter("fun prepareWatermarkedJpeg(").substringBefore("\n    /**")
        val berbahaya = listOf("decodeByteArray", "createScaledBitmap", "createBitmap", ".compress(")
        berbahaya.forEach { panggilan ->
            assertTrue(
                "`$panggilan` ada langsung di badan prepareWatermarkedJpeg — alokasi bitmap " +
                    "WAJIB berada di dalam runCatching (lewat olahPiksel), kalau tidak " +
                    "OutOfMemoryError lolos dari coroutine dan menutup app.",
                panggilan !in publik,
            )
        }
        assertTrue(
            "prepareWatermarkedJpeg tak lagi memanggil olahPiksel di dalam runCatching",
            Regex("""runCatching\s*\{\s*olahPiksel\(""").containsMatchIn(isi),
        )
    }

    @Test
    fun `semua pemanggil membungkus dengan withContext`() {
        val pelanggar = mutableListOf<String>()
        sumberRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.name !in dikecualikan }
            .forEach { berkas ->
                val baris = berkas.readLines()
                baris.forEachIndexed { i, isi ->
                    if (!isi.contains("PhotoWatermark.prepareWatermarkedJpeg")) return@forEachIndexed
                    // Nama fungsi ini disebut di BANYAK KDoc (repository, Api,
                    // NetworkModule, plan) justru karena aturannya penting —
                    // menyebutnya bukan memanggilnya.
                    if (isi.trimStart().let { it.startsWith("*") || it.startsWith("//") || it.startsWith("/*") }) {
                        return@forEachIndexed
                    }
                    if (!adaWithContextDiFungsiYangSama(baris, i)) {
                        pelanggar += "${berkas.name}:${i + 1}"
                    }
                }
            }
        assertTrue(
            "prepareWatermarkedJpeg dipanggil tanpa withContext(Dispatchers.…) di: " +
                "${pelanggar.joinToString()} — itu Dispatchers.Main.immediate, jadi dekode + " +
                "kompresi foto kamera membekukan layar tepat saat tombolnya ditekan.",
            pelanggar.isEmpty(),
        )
    }

    /**
     * Telusuri ke ATAS dari baris pemanggilan sampai deklarasi fungsi terdekat
     * (indentasi 4 spasi, gaya seluruh repo ini), cari `withContext(Dispatchers.`
     * di antaranya. Sengaja tak mengurai blok/kurung: yang perlu dibuktikan
     * cuma "ada pemindahan dispatcher di fungsi yang sama", dan penguraian penuh
     * akan menukar kesalahan yang terang dengan kesalahan yang halus.
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
