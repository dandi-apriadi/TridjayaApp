package com.krisoft.tridjayaelektronik.util

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Dua aturan tentang `VideoTranscoder` yang TIDAK BISA diuji lewat perilaku di JVM —
 * `MediaCodec`/`Transformer` butuh perangkat/emulator nyata (lihat `VideoTranscoderApi24Test`
 * untuk itu). Pola pemindai-sumber PERSIS [PhotoWatermarkGuardTest]/[ImagePixelPipelineGuardTest],
 * TAPI aturan kedua di sini SEBALIKNYA dari keduanya — lihat penjelasan di bawah.
 *
 * ## Aturan 1 — `transcode()` berimplementasi sebagai `runCatching { … }`
 *
 * Sama alasannya dengan gambar: kegagalan Transformer (mis. `ExportException`, atau perangkat
 * tanpa encoder hardware yang sesuai) tak boleh lolos dari `viewModelScope.launch` dan menutup
 * app — `runCatching` menangkap `Throwable`, berakhir sebagai `null` (fail-soft, pemanggil
 * fallback ke berkas asli).
 *
 * ## Aturan 2 — pemanggil TIDAK BOLEH membungkus dengan `withContext(Dispatchers.…)`
 *
 * **Kebalikan dari `ImagePixelPipeline`.** `Transformer` (media3 1.11.0, dibaca langsung dari
 * source `Transformer.java`) mensyaratkan diakses dari SATU application thread — untuk hampir
 * semua kasus, thread utama. `addListener`/`start`/`cancel` semuanya memanggil
 * `verifyApplicationThread()` yang melempar `IllegalStateException("Transformer is accessed on
 * the wrong thread.")` kalau dilanggar. `VideoTranscoder.transcode()` karena itu HARUS dipanggil
 * langsung di dalam `viewModelScope.launch { }` (`Dispatchers.Main.immediate`), TANPA
 * `withContext(Dispatchers.Default)` — encode berat sesungguhnya tetap jalan di thread MediaCodec
 * internal Transformer sendiri, bukan di thread pemanggil, jadi Main tak terblokir.
 *
 * Tes ini sengaja ADA supaya kalau suatu hari sesi lain "menyeragamkan" pola pemanggilan
 * `VideoTranscoder` dengan `ImagePixelPipeline` (menambahkan `withContext(Dispatchers.Default)`
 * di sekitar panggilan `transcode`), test ini GAGAL DULU sebagai sinyal — bukan ditemukan lewat
 * SETIAP transcode gagal `IllegalStateException` di HP petugas.
 */
class VideoTranscoderGuardTest {

    private val sumberRoot: File =
        sequenceOf(File("src/main/java"), File("app/src/main/java"))
            .firstOrNull { it.isDirectory }
            ?: error("pohon sumber tak ketemu — cwd=${File(".").absolutePath}")

    private val transcoderFile: File =
        File(sumberRoot, "com/krisoft/tridjayaelektronik/util/VideoTranscoder.kt")

    @Test
    fun `pipa transcode berada di dalam runCatching`() {
        val isi = transcoderFile.readText()
        assertTrue(
            "fun transcode(...) tak lagi berimplementasi sebagai `= runCatching { … }` — " +
                "kegagalan Transformer (ExportException dkk.) WAJIB tertangkap di sini, kalau " +
                "tidak lolos dari coroutine dan menutup app.",
            Regex("""\)\s*:\s*File\?\s*=\s*runCatching\s*\{""").containsMatchIn(isi),
        )

        // Dipotong dari "fun transcode(" (BUKAN dari awal berkas) supaya KDoc kelas — yang
        // menyebut `Transformer`/`start`/`cancel` sebagai dokumentasi — tak ikut dihitung
        // sebagai "dipanggil sebelum runCatching". Pola sama `ImagePixelPipelineGuardTest`.
        val badanFungsi = isi.substringAfter("fun transcode(")
        val sebelumRunCatching = badanFungsi.substringBefore("runCatching {")
        val sesudahRunCatching = badanFungsi.substringAfter("runCatching {")
        val berbahaya = listOf("Transformer.Builder(", ".start(")
        berbahaya.forEach { panggilan ->
            assertTrue(
                "`$panggilan` ditemukan SEBELUM runCatching { di VideoTranscoder.kt — " +
                    "pemakaian Transformer harus berada DI DALAM runCatching.",
                panggilan !in sebelumRunCatching,
            )
            assertTrue(
                "`$panggilan` seharusnya dipanggil di dalam badan runCatching transcode(), " +
                    "tapi tak ditemukan di sana.",
                panggilan in sesudahRunCatching,
            )
        }
    }

    /**
     * SEBALIKNYA dari `ImagePixelPipelineGuardTest`/`PhotoWatermarkGuardTest`: di sini yang
     * dijaga adalah KETIADAAN `withContext(Dispatchers.…)` di sekitar panggilan
     * `VideoTranscoder.transcode(` — lihat KDoc kelas untuk alasan `verifyApplicationThread()`.
     *
     * Belum ada pemanggil nyata `VideoTranscoder.transcode(` di `app/src/main` pada langkah ini
     * (modul kompresi dibangun sebelum pemanggil lama dipindah) — daftar pelanggar karena itu
     * KOSONG hari ini, dan test ini baru benar-benar mulai bekerja begitu `AktivitasViewModel`
     * memanggilnya di langkah berikutnya.
     */
    @Test
    fun `tak ada pemanggil yang membungkus dengan withContext`() {
        val pelanggar = mutableListOf<String>()
        sumberRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { berkas ->
                val baris = berkas.readLines()
                baris.forEachIndexed { i, isi ->
                    if (!isi.contains("VideoTranscoder.transcode(")) return@forEachIndexed
                    // KDoc/komentar MENYEBUT nama fungsi ini tanpa memanggilnya.
                    if (isi.trimStart().let { it.startsWith("*") || it.startsWith("//") || it.startsWith("/*") }) {
                        return@forEachIndexed
                    }
                    if (adaWithContextDiFungsiYangSama(baris, i)) {
                        pelanggar += "${berkas.name}:${i + 1}"
                    }
                }
            }
        assertTrue(
            "VideoTranscoder.transcode dipanggil DI DALAM withContext(Dispatchers.…) di: " +
                "${pelanggar.joinToString()} — Transformer WAJIB diakses dari application " +
                "thread (viewModelScope biasa, TANPA withContext), kalau tidak SETIAP transcode " +
                "gagal IllegalStateException(\"Transformer is accessed on the wrong thread.\").",
            pelanggar.isEmpty(),
        )
    }

    /**
     * Telusuri ke ATAS dari baris pemanggilan sampai deklarasi fungsi terdekat, cari
     * `withContext(Dispatchers.` di antaranya. Pola sama `PhotoWatermarkGuardTest`/
     * `ImagePixelPipelineGuardTest`, hasilnya dipakai TERBALIK oleh test di atas.
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
