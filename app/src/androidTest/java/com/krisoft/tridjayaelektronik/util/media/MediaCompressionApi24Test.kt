package com.krisoft.tridjayaelektronik.util.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.krisoft.tridjayaelektronik.util.ImagePixelPipeline
import com.krisoft.tridjayaelektronik.util.VideoTranscoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Random

/**
 * Penjaga yang HANYA bisa hidup di perangkat — `Bitmap`/`BitmapFactory`/`MediaCodec`/
 * `Transformer` adalah stub atau tak ada sama sekali di unit test JVM biasa (repo ini tak pakai
 * Robolectric). Pola PERSIS `EksporXlsxApi24Test`: menjalankan pipa SUNGGUHAN, bukan cuma
 * memeriksa bentuk kodenya (itu tugas `ImagePixelPipelineGuardTest`/`VideoTranscoderGuardTest`).
 *
 * Jalan di API 24 (emulator/HP paling tua yang didukung `minSdk`) supaya sekaligus membuktikan
 * `ImagePixelPipeline`/`VideoTranscoder` tak diam-diam bergantung pada API yang lebih baru:
 *   ./gradlew :app:connectedDebugAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class MediaCompressionApi24Test {

    private fun ctx(): Context = InstrumentationRegistry.getInstrumentation().targetContext

    /**
     * Aset `sample_input_video.mp4` (`app/src/androidTest/assets/`) dikemas ke **APK test**,
     * bukan APK app-under-test — jadi ia HANYA terlihat lewat context instrumentasi
     * (`getInstrumentation().context`), BUKAN `targetContext` (context app, dipakai untuk
     * `cacheDir` supaya mencerminkan Context nyata yang dioper `VideoTranscoder.transcode` di
     * produksi). Memakai `targetContext.assets.open(...)` di sini melempar
     * `FileNotFoundException` walau asetnya ADA di dalam APK — terverifikasi persis begini di
     * emulator API 24 sebelum diperbaiki.
     */
    private fun asetCtx(): Context = InstrumentationRegistry.getInstrumentation().context

    /** Gambar "kamera" sintetis dengan banyak detail (bukan warna polos) supaya JPEG kualitas
     * 100 tak trivial kecil — ratusan kotak berwarna acak (seed tetap = hasil dapat diulang). */
    private fun bitmapKamera(width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val rnd = Random(42)
        val paint = Paint()
        canvas.drawColor(Color.WHITE)
        repeat(400) {
            paint.color = Color.rgb(rnd.nextInt(256), rnd.nextInt(256), rnd.nextInt(256))
            val x0 = rnd.nextInt(width)
            val y0 = rnd.nextInt(height)
            val x1 = rnd.nextInt(width)
            val y1 = rnd.nextInt(height)
            canvas.drawRect(
                minOf(x0, x1).toFloat(), minOf(y0, y1).toFloat(),
                maxOf(x0, x1).toFloat(), maxOf(y0, y1).toFloat(), paint,
            )
        }
        return bitmap
    }

    /**
     * Mensimulasikan "foto kamera 4000×2666 penuh" → dikompres ke anggaran kecil dan sisi
     * terpanjang jauh lebih pendek, lalu dibuktikan hasilnya BENAR-BENAR bisa didekode ulang
     * (bukan cuma "tak melempar").
     */
    @Test
    fun imagePixelPipeline_mengecilkan_dan_hasilnya_valid() {
        val width = 2400
        val height = 1600
        val sumber = bitmapKamera(width, height)
        val raw = ByteArrayOutputStream()
            .apply { sumber.compress(Bitmap.CompressFormat.JPEG, 100, this) }
            .toByteArray()
        assertTrue("berkas uji terlalu kecil untuk membuktikan penyusutan", raw.size > 50_000)

        val params = ImagePixelPipeline.Params(
            maxDimension = 800,
            format = Bitmap.CompressFormat.JPEG,
            startQuality = 80,
            minQuality = 40,
            step = 15,
            maxBytes = 300_000L,
        )
        val hasil = ImagePixelPipeline.compress(raw, params)
        assertNotNull("compress() tak boleh gagal untuk JPEG sah", hasil)
        val (out, bitmapAkhir) = hasil!!

        assertTrue(
            "hasil (${out.size} byte) harus lebih kecil dari sumber (${raw.size} byte)",
            out.size < raw.size,
        )
        assertTrue("sisi terpanjang bitmap akhir harus ≤ maxDimension", maxOf(bitmapAkhir.width, bitmapAkhir.height) <= 800)

        // Bukan cuma "tak melempar": harus BENAR-BENAR bisa didekode ulang jadi bitmap valid.
        val decodeUlang = BitmapFactory.decodeByteArray(out, 0, out.size)
        assertNotNull("byte hasil compress() harus bisa didekode ulang", decodeUlang)
        assertTrue(decodeUlang!!.width > 0 && decodeUlang.height > 0)
        assertTrue(maxOf(decodeUlang.width, decodeUlang.height) <= 800)
    }

    /**
     * Video sintetis 1920×1080/10 Mbps (`sample_input_video.mp4`, dibuat via ffmpeg
     * `testsrc2`+`sine`, digenerate sekali dan disimpan sebagai aset uji — bukan dibuat on-device)
     * ditranscode via [VideoTranscoder.transcode] ke target ~2,5 Mbps/lebar 1280, lalu dibuktikan
     * hasilnya BENAR-BENAR video MP4 valid yang bisa dibuka ulang, bukan cuma "berkas ada".
     *
     * `runBlocking(Dispatchers.Main)`: `Transformer` mensyaratkan diakses dari SATU application
     * thread (lihat KDoc [VideoTranscoder]) — thread instrumentasi JUnit BUKAN main thread,
     * jadi tanpa ini `verifyApplicationThread()` melempar `IllegalStateException` persis seperti
     * yang diperingatkan di sana.
     */
    @Test
    fun videoTranscoder_mengecilkan_dan_hasilnya_valid() {
        val context = ctx()
        val inputFile = salinAsetKeCache(context, asetCtx(), "sample_input_video.mp4", "input.mp4")

        val retriever = MediaMetadataRetriever()
        val (sourceWidth, sourceHeight) = try {
            retriever.setDataSource(inputFile.absolutePath)
            val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            w to h
        } finally {
            retriever.release()
        }
        assertTrue("metadata video sumber harus terbaca (didapat ${sourceWidth}x$sourceHeight)", sourceWidth > 0 && sourceHeight > 0)

        val outputFile = File(context.cacheDir, "media-compress-test/output_${System.currentTimeMillis()}.mp4")
        outputFile.parentFile?.mkdirs()

        val hasil = runBlocking(Dispatchers.Main) {
            VideoTranscoder.transcode(
                context = context,
                sourceUri = Uri.fromFile(inputFile),
                sourceWidth = sourceWidth,
                sourceHeight = sourceHeight,
                outputFile = outputFile,
            )
        }

        assertNotNull("transcode video sintetis harus berhasil di perangkat nyata", hasil)
        assertTrue("berkas keluaran harus ada", outputFile.exists())
        assertTrue(
            "keluaran (${outputFile.length()} byte) harus lebih kecil dari masukan " +
                "(${inputFile.length()} byte) — target ~2,5 Mbps dari sumber ~10 Mbps",
            outputFile.length() < inputFile.length(),
        )

        // Bukan cuma "berkas ada": header MP4 (box `ftyp`, byte offset 4..7) harus benar.
        val header = ByteArray(8)
        outputFile.inputStream().use { assertEquals(8, it.read(header)) }
        val ftyp = String(header, 4, 4, Charsets.US_ASCII)
        assertEquals("berkas keluaran harus berawalan box MP4 'ftyp'", "ftyp", ftyp)

        // Bukan cuma "header cocok": harus BENAR-BENAR bisa dibuka ulang sebagai video valid.
        val outRetriever = MediaMetadataRetriever()
        try {
            outRetriever.setDataSource(outputFile.absolutePath)
            val outWidth = outRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
            val outDurationMs = outRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toIntOrNull()
            assertNotNull("lebar video keluaran harus terbaca ulang", outWidth)
            assertTrue("lebar keluaran (${outWidth}) harus ≤ 1280 (target scale)", outWidth!! <= 1280)
            assertNotNull("durasi video keluaran harus terbaca ulang", outDurationMs)
            assertTrue("durasi keluaran harus > 0", outDurationMs!! > 0)
        } finally {
            outRetriever.release()
        }
    }

    private fun salinAsetKeCache(
        outputContext: Context,
        assetContext: Context,
        namaAset: String,
        namaTujuan: String,
    ): File {
        val tujuan = File(outputContext.cacheDir, "media-compress-test/$namaTujuan")
        tujuan.parentFile?.mkdirs()
        assetContext.assets.open(namaAset).use { masuk ->
            tujuan.outputStream().use { keluar -> masuk.copyTo(keluar) }
        }
        return tujuan
    }
}
