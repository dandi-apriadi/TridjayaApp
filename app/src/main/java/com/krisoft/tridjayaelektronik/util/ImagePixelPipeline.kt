package com.krisoft.tridjayaelektronik.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import com.krisoft.tridjayaelektronik.domain.media.exifDegreesFor
import com.krisoft.tridjayaelektronik.domain.media.sampleSizeUntuk
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.math.max

private const val LOG_TAG = "ImagePixelPipeline"

/**
 * Mekanisme kompresi gambar bersama: decode(inSampleSize) → scale ke sisi terpanjang maksimum →
 * perbaiki rotasi EXIF → [Params.transform opsional lewat parameter `transform`] → turun kualitas
 * sampai anggaran byte. Diekstrak dari `PhotoWatermark.olahPiksel` — pipa yang sama dipakai
 * `IndentCreateViewModel.compressImage`, `AddLeadViewModel.siapkanJpeg`, dan
 * `EventViewModel.siapkanKtpJpeg`, yang sebelumnya masing-masing menulis ulang decode+scale+EXIF+
 * loop kualitas sendiri dengan variasi kecil.
 *
 * **Yang SENGAJA tetap jadi keputusan pemanggil, bukan di-hardcode di sini** — format
 * (JPEG/WebP), anggaran byte, kualitas awal/minimum, langkah penurunan, dan transformasi piksel
 * tambahan (watermark) semuanya lewat [Params]/parameter `transform`, karena masing-masing dari
 * empat pemanggil sengaja berbeda di titik-titik itu (lihat komentar di `Params` dan di setiap
 * pemanggil). Yang TIDAK sengaja beda — dan karena itu digabung di sini — adalah MEKANISMENYA:
 * cara membaca bounds, menghitung `inSampleSize`, menskalakan, membaca & menerapkan rotasi EXIF,
 * dan loop kompres-turun-kualitas.
 *
 * ## Kenapa SELURUH badan di dalam SATU runCatching(Throwable)
 *
 * Sama persis alasan `PhotoWatermark` (lihat KDoc-nya dan `mobile/CLAUDE.md`): `decodeByteArray`,
 * `createScaledBitmap`, `createBitmap`, `.compress` semuanya mengalokasi gambar utuh di heap,
 * manifest app ini tak memakai `largeHeap`, dan yang dilempar saat memori habis adalah
 * `OutOfMemoryError` — turunan `Error`, BUKAN `Exception`. Tak ada `CoroutineExceptionHandler`
 * maupun `setDefaultUncaughtExceptionHandler` di `app/src/main`, jadi `Error` yang lolos dari
 * `viewModelScope.launch` MENUTUP SELURUH APP. `runCatching` menangkap `Throwable`, jadi
 * kegagalan apa pun berakhir sebagai `null` yang sudah ditangani semua pemanggil.
 *
 * ## Kenapa pemanggil WAJIB `withContext(Dispatchers.Default)`
 *
 * `viewModelScope` adalah `Dispatchers.Main.immediate`; dekode + skala + rotasi + loop kompresi
 * untuk foto kamera penuh mengunci UI persis pada ketukan tombolnya. Dijaga
 * `ImagePixelPipelineGuardTest` (pemindai sumber, pola sama `PhotoWatermarkGuardTest`).
 *
 * ## "Masih di atas `maxBytes` di `minQuality`" BUKAN kegagalan
 *
 * Hasil dikembalikan apa adanya (fail-soft) — bukan `null`. Pemanggil yang mau menolak SEBELUM
 * upload kalau ukurannya tetap kebesaran (mis. `AddLeadViewModel`, kebijakan sengaja beda dari
 * `PhotoWatermark`/`IndentCreateViewModel`/`EventViewModel` yang selalu fallback ke hasil apa
 * adanya) mengecek ulang `.first.size` SENDIRI sesudah `compress()` kembali.
 */
object ImagePixelPipeline {

    /**
     * Kebijakan kompresi — SENGAJA parameter, bukan konstanta modul ini, karena tiap pemanggil
     * punya angka berbeda yang masing-masing punya alasannya sendiri (lihat komentar di titik
     * pemanggilan, bukan di sini):
     *
     * @param maxDimension sisi terpanjang maksimum sesudah scale (mis. 1600 bukti absen/delivery,
     *   2560 tangkapan layar prospek — nilai lebih besar = detail teks lebih terjaga).
     * @param format `Bitmap.CompressFormat` keluaran. `WEBP` (bukan `WEBP_LOSSY`/`WEBP_LOSSLESS`
     *   yang butuh API 30+) untuk pemanggil yang sudah pindah ke WebP; `JPEG` untuk yang belum
     *   diverifikasi diterima server sebagai WebP.
     * @param startQuality kualitas awal loop kompres-turun.
     * @param minQuality kualitas terendah sebelum loop menyerah dan mengembalikan hasil apa
     *   adanya walau masih di atas [maxBytes] (fail-soft — lihat KDoc kelas).
     * @param step besar penurunan kualitas tiap iterasi loop.
     * @param maxBytes anggaran ukuran keluaran yang ingin dicapai loop (bukan jaminan keras).
     */
    data class Params(
        val maxDimension: Int,
        val format: Bitmap.CompressFormat,
        val startQuality: Int,
        val minQuality: Int,
        val step: Int,
        val maxBytes: Long,
    )

    /**
     * Baca gambar mentah → siap upload. `null` bila berkasnya rusak, tak terbaca, atau memori tak
     * cukup untuk memprosesnya (lihat KDoc kelas — `OutOfMemoryError` tertangkap di sini juga).
     *
     * @param transform transformasi piksel tambahan sesudah scale+rotasi EXIF, SEBELUM loop
     *   kompresi — mis. `PhotoWatermark` menggambar bar watermark di sini. Identitas
     *   (`{ it }`, default) untuk pemanggil yang tak butuh transformasi tambahan.
     * @return pasangan (byte terkompresi, bitmap piksel akhir — dipakai pemanggil yang perlu
     *   dimensi/pratinjau hasil), atau `null` kalau gagal.
     */
    fun compress(
        raw: ByteArray,
        params: Params,
        transform: (Bitmap) -> Bitmap = { it },
    ): Pair<ByteArray, Bitmap>? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(raw, 0, raw.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null

        val sampleSize = sampleSizeUntuk(bounds.outWidth, bounds.outHeight, params.maxDimension)
        var bitmap = BitmapFactory.decodeByteArray(
            raw, 0, raw.size,
            BitmapFactory.Options().apply { inSampleSize = sampleSize },
        ) ?: return@runCatching null

        val maxSide = max(bitmap.width, bitmap.height)
        if (maxSide > params.maxDimension) {
            val scale = params.maxDimension.toFloat() / maxSide
            bitmap = Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt().coerceAtLeast(1),
                (bitmap.height * scale).toInt().coerceAtLeast(1),
                true,
            )
        }

        val orientation = runCatching {
            ExifInterface(ByteArrayInputStream(raw))
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val degrees = exifDegreesFor(orientation)
        if (degrees != 0f) {
            val matrix = Matrix().apply { postRotate(degrees) }
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }

        bitmap = transform(bitmap)

        var quality = params.startQuality
        var out = ByteArrayOutputStream().apply { bitmap.compress(params.format, quality, this) }.toByteArray()
        while (out.size > params.maxBytes && quality > params.minQuality) {
            quality -= params.step
            out = ByteArrayOutputStream().apply { bitmap.compress(params.format, quality, this) }.toByteArray()
        }
        out to bitmap
    }.onFailure { Log.w(LOG_TAG, "Foto gagal diproses: ${it.javaClass.simpleName}", it) }
        .getOrNull()
}
