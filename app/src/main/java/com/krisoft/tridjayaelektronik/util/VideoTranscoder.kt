package com.krisoft.tridjayaelektronik.util

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.krisoft.tridjayaelektronik.domain.media.targetDimensions
import java.io.File
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

private const val LOG_TAG = "VideoTranscoder"

/**
 * Transcode video bukti Input Aktivitas (>30 MB) on-device sebelum diunggah, via
 * `androidx.media3:media3-transformer` (MediaCodec hardware). Satu-satunya video upload di app
 * (`AktivitasViewModel.kirimVideo`) — video dipilih dari galeri lewat Photo Picker, tak pernah
 * direkam dalam app, jadi ukurannya di luar kendali app sampai titik ini.
 *
 * Paritas dengan server (`kinerja-service` ffmpeg libx264 crf 28, scale `min(1280,iw)`): target
 * lebar lihat [com.krisoft.tridjayaelektronik.domain.media.targetDimensions], bitrate video wajar
 * untuk lebar itu lewat [Params.videoBitrateBps], audio AAC.
 *
 * ## CATATAN THREADING KRUSIAL — kebalikan dari [ImagePixelPipeline]
 *
 * `Transformer` (media3 1.11.0, `Transformer.java`): "Transformer instances must be accessed from
 * a single application thread. For the vast majority of cases this should be the application's
 * main thread" — `addListener`/`start`/`cancel` semuanya memanggil `verifyApplicationThread()`
 * yang melempar `IllegalStateException("Transformer is accessed on the wrong thread.")` kalau
 * dipanggil dari thread lain (dibaca langsung dari source, bukan asumsi).
 *
 * Karena itu [transcode] **HARUS dipanggil TANPA `withContext(Dispatchers.Default)`** — langsung
 * di dalam `viewModelScope.launch { }` (`Dispatchers.Main.immediate`, sudah punya Looper yang
 * benar). Encode berat sesungguhnya tetap jalan di thread MediaCodec internal milik Transformer
 * sendiri, BUKAN di thread pemanggil — jadi Main tidak terblokir walau `Builder`/`start`/listener
 * dipanggil dari sana; yang perlu Looper yang tepat hanya panggilan kontrolnya (mikrodetik), bukan
 * encode-nya. Kalau pemanggil "menyeragamkan" pola ini dengan [ImagePixelPipeline] (membungkusnya
 * di `Dispatchers.Default`), SETIAP transcode gagal `IllegalStateException` — 100% gagal, bukan
 * degradasi. Dijaga `VideoTranscoderGuardTest` — assert SEBALIKNYA dari
 * `ImagePixelPipelineGuardTest`: TIDAK boleh ada `withContext(Dispatchers.` di fungsi yang sama
 * dengan panggilan [transcode].
 *
 * ## Fail-soft
 *
 * Gagal/timeout/output tak lebih kecil dari asli → `null`. Pemanggil (`kirimVideo`) fallback ke
 * `Uri`/berkas ASLI, upload tetap jalan — sama seperti kompresi video/PDF sisi server.
 */
object VideoTranscoder {

    /**
     * @param maxWidth cermin `min(1280, iw)` server — lihat [targetDimensions].
     * @param videoBitrateBps bitrate video target H.264. ~2-3 Mbps wajar untuk lebar 1280;
     *   disesuaikan dengan hasil uji nyata di HP (lihat catatan produk desain).
     * @param audioBitrateBps bitrate audio AAC target.
     * @param timeoutMs batas keras proses transcode — paritas `FFMPEG_TIMEOUT` server (120 detik).
     *   Timeout MEMBATALKAN Transformer secara eksplisit ([Transformer.cancel]), bukan cuma
     *   melepas coroutine, supaya proses MediaCodec tak menggantung di belakang layar.
     */
    data class Params(
        val maxWidth: Int = 1280,
        val videoBitrateBps: Int = 2_500_000,
        val audioBitrateBps: Int = 96_000,
        val timeoutMs: Long = 120_000L,
    )

    /**
     * `sourceWidth`/`sourceHeight` HARUS dimensi pasca-rotasi (sama seperti yang dibaca
     * `MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION` digabung ke lebar/tinggi mentah) —
     * tanggung jawab pemanggil, bukan fungsi ini.
     *
     * WAJIB dipanggil TANPA `withContext(Dispatchers.…)` — lihat KDoc kelas.
     *
     * @return [outputFile] kalau transcode berhasil DAN filenya ada & tak kosong, `null` kalau
     *   gagal/timeout/dibatalkan (fail-soft — pemanggil fallback ke berkas asli).
     */
    suspend fun transcode(
        context: Context,
        sourceUri: Uri,
        sourceWidth: Int,
        sourceHeight: Int,
        outputFile: File,
        params: Params = Params(),
    ): File? = runCatching {
        val (targetWidth, targetHeight) = targetDimensions(sourceWidth, sourceHeight, params.maxWidth)
        val encoderFactory = DefaultEncoderFactory.Builder(context)
            .setRequestedVideoEncoderSettings(
                VideoEncoderSettings.Builder().setBitrate(params.videoBitrateBps).build(),
            )
            .build()
        val editedMediaItem = EditedMediaItem.Builder(MediaItem.fromUri(sourceUri))
            .setEffects(
                Effects(
                    emptyList(),
                    listOf(Presentation.createForWidthAndHeight(targetWidth, targetHeight, Presentation.LAYOUT_SCALE_TO_FIT)),
                ),
            )
            .build()

        val berhasil = withTimeoutOrNull(params.timeoutMs) {
            suspendCancellableCoroutine<Boolean> { cont ->
                lateinit var transformer: Transformer
                val listener = object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        transformer.removeListener(this)
                        if (cont.isActive) cont.resume(true, onCancellation = null)
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException,
                    ) {
                        transformer.removeListener(this)
                        Log.w(LOG_TAG, "Transcode gagal: ${exportException.javaClass.simpleName}", exportException)
                        if (cont.isActive) cont.resume(false, onCancellation = null)
                    }
                }
                transformer = Transformer.Builder(context)
                    .setEncoderFactory(encoderFactory)
                    .addListener(listener)
                    .build()
                // Menutup DUA kasus: timeout (withTimeoutOrNull membatalkan coroutine ini) dan
                // pembatalan biasa dari scope pemanggil — keduanya harus menghentikan Transformer
                // yang sedang jalan, bukan cuma melepas coroutine dan membiarkan MediaCodec
                // menggantung di belakang layar.
                cont.invokeOnCancellation { transformer.cancel() }
                transformer.start(editedMediaItem, outputFile.absolutePath)
            }
        }

        // Perbandingan "hasil lebih kecil dari asli" SENGAJA bukan di sini — fungsi ini tak
        // punya cara murah membaca ukuran `sourceUri` (butuh query ContentResolver terpisah,
        // ongkos yang tak relevan bagi fungsi mekanisme), dan pemanggil (`kirimVideo`) SUDAH
        // memegang `video.ukuranBytes` di tangan. Di sinilah cukup membuktikan hasilnya VALID:
        // proses selesai sukses dan berkasnya benar-benar ada & tak kosong.
        if (berhasil != true || !outputFile.exists() || outputFile.length() == 0L) {
            outputFile.delete()
            null
        } else {
            outputFile
        }
    }.onFailure { Log.w(LOG_TAG, "Transcode gagal: ${it.javaClass.simpleName}", it) }
        .getOrNull()
}
