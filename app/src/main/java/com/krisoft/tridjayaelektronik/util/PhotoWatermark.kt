package com.krisoft.tridjayaelektronik.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val MAX_DIMENSION = 1600
private const val MAX_BYTES = 2L * 1024 * 1024
private const val LOG_TAG = "PhotoWatermark"

// WEBP (bukan WEBP_LOSSY/WEBP_LOSSLESS — enum baru itu butuh API 30+, minSdk modul ini 24).
// Deprecated tapi tetap lossy & fungsional di semua versi yang didukung; server (kinerja-service)
// juga sudah pindah ke WebP lossy, jadi format ini konsisten ujung ke ujung.
@Suppress("DEPRECATION")
private val PARAMS = ImagePixelPipeline.Params(
    maxDimension = MAX_DIMENSION,
    format = Bitmap.CompressFormat.WEBP,
    startQuality = 85,
    minQuality = 40,
    step = 15,
    maxBytes = MAX_BYTES,
)

/**
 * Diangkat dari [com.krisoft.tridjayaelektronik.ui.attendance.AttendanceViewModel] (dulu
 * private di sana, sekarang dipakai juga oleh alur foto delivery/PDI) — util bersama bukti foto
 * anti-manipulasi: downscale, perbaiki rotasi EXIF, cap watermark geotag+jam KE PIKSEL gambar
 * (bukan metadata, supaya ikut terkirim & tak mudah dihapus), lalu WebP-kompres < [MAX_BYTES]
 * (2026-08-28, sebelumnya JPEG — WebP lossy ~40-50% lebih kecil pada kualitas setara untuk
 * foto kamera HP, tanpa penurunan kualitas kasat mata; diukur langsung dengan sampel produksi).
 */
object PhotoWatermark {

    /**
     * Baca foto full-res dari kamera → siap upload. `null` bila berkasnya rusak,
     * tak terbaca, **atau memori tak cukup untuk memprosesnya**.
     *
     * ## Kenapa SELURUH pipa piksel dibungkus `runCatching`
     *
     * `decodeByteArray`, `createScaledBitmap`, `createBitmap`, `Bitmap.copy` di
     * [drawWatermark], dan loop `compress` semuanya mengalokasi gambar utuh di
     * heap. Foto 108 MP dari HP kelas atas = satu bitmap ARGB_8888 ratusan MB,
     * dan `AndroidManifest.xml` app ini TIDAK memakai `largeHeap` — jadi
     * kehabisan memori di sini bukan kasus teoretis.
     *
     * Yang dilempar adalah `OutOfMemoryError`: turunan `Error`, BUKAN
     * `Exception`. Di seluruh `app/src/main` tak ada satu pun
     * `CoroutineExceptionHandler` maupun `setDefaultUncaughtExceptionHandler`,
     * jadi `Error` yang lolos dari `viewModelScope.launch` **menutup app** —
     * petugas kehilangan seluruh isian layar, bukan cuma fotonya. Ini kelas
     * kegagalan yang sama dengan `NoClassDefFoundError` di catatan `java.time`
     * (`mobile/CLAUDE.md`): `catch (e: Exception)` tak menangkapnya sama sekali.
     *
     * `runCatching` menangkap `Throwable`, jadi ia berakhir sebagai `null` →
     * pesan "foto tidak terbaca" yang SUDAH ditangani semua pemanggil. Fungsi
     * ini tak pernah `suspend` sehingga tak ada `CancellationException` yang
     * ikut tertelan; sebabnya tetap dicatat ke logcat, tidak dibuang diam-diam.
     *
     * Pemanggil WAJIB memanggilnya dari `withContext(Dispatchers.Default)` —
     * `viewModelScope` adalah `Dispatchers.Main.immediate`, dan pipa yang sama
     * ini mengunci UI kalau dijalankan di sana. Dijaga `PhotoWatermarkGuardTest`.
     */
    fun prepareWatermarkedJpeg(
        file: File,
        lat: Double?,
        lng: Double?,
        title: String,
        subtitle: String,
        accuracyM: Float? = null,
        address: String? = null,
    ): Pair<ByteArray, Bitmap>? {
        val raw = runCatching { file.readBytes() }.getOrNull() ?: return null
        if (raw.isEmpty()) return null
        return runCatching { olahPiksel(raw, lat, lng, title, subtitle, accuracyM, address) }
            .onFailure { Log.w(LOG_TAG, "Foto gagal diproses: ${it.javaClass.simpleName}", it) }
            .getOrNull()
    }

    /**
     * Pipa piksel — dipisah dari [prepareWatermarkedJpeg] supaya seluruh alokasi bitmap berada
     * di dalam SATU `runCatching` di pemanggilnya. `null` = gambar tak bisa didekode (bukan
     * kegagalan memori). Mekanisme decode+scale+rotasi-EXIF+loop-kompresi dipakai bersama lewat
     * [ImagePixelPipeline] — watermark disisipkan lewat parameter `transform`, dijalankan SETELAH
     * scale+rotasi dan SEBELUM loop kompresi final (`ImagePixelPipeline.compress` KDoc).
     */
    private fun olahPiksel(
        raw: ByteArray,
        lat: Double?,
        lng: Double?,
        title: String,
        subtitle: String,
        accuracyM: Float?,
        address: String?,
    ): Pair<ByteArray, Bitmap>? = ImagePixelPipeline.compress(raw, PARAMS) { bitmap ->
        drawWatermark(bitmap, title, subtitle, lat, lng, accuracyM, address)
    }

    /**
     * Gambar bar watermark di bagian bawah foto: [title], tanggal+jam, koordinat GPS (geotag), dan
     * [subtitle] (konteks: nama·cabang utk absensi, nama·kode SPK utk delivery). Ukuran teks
     * proporsional terhadap lebar gambar.
     */
    private fun drawWatermark(
        src: Bitmap, title: String, subtitle: String,
        lat: Double?, lng: Double?, accuracyM: Float?, address: String?,
    ): Bitmap {
        val bmp = if (src.isMutable) src else src.copy(Bitmap.Config.ARGB_8888, true)
        val w = bmp.width.toFloat()
        val h = bmp.height.toFloat()
        val canvas = Canvas(bmp)

        val pad = w * 0.03f
        val titleSize = w / 38f
        val bodySize = w / 30f
        val smallSize = w / 34f
        val gap = bodySize * 0.42f
        val barH = pad * 2 + titleSize + bodySize + smallSize * 2 + gap * 3

        // Latar semi-transparan + strip aksen biru brand di kiri.
        canvas.drawRect(0f, h - barH, w, h, Paint().apply { color = Color.argb(150, 0, 0, 0) })
        canvas.drawRect(0f, h - barH, pad * 0.35f, h, Paint().apply { color = Color.rgb(30, 99, 233) })

        val timeStr = SimpleDateFormat("EEE, dd MMM yyyy · HH:mm:ss", Locale("in", "ID")).format(Date())
        // Alamat terbaca diutamakan (kota/kabupaten/tempat) — `canvas.drawText` tak wrap/clip
        // otomatis, jadi teks yang kepanjangan dipotong manual ber-ellipsis biar tak lari ke luar
        // tepi gambar. Fallback koordinat mentah bila geocoder gagal (offline dsb.).
        val geoStr = if (!address.isNullOrBlank()) {
            val avgCharPx = smallSize * 0.55f
            val maxWidthPx = w - pad * 2.35f
            val maxChars = (maxWidthPx / avgCharPx).toInt().coerceAtLeast(20)
            if (address.length > maxChars) address.take(maxChars - 1) + "…" else address
        } else if (lat != null && lng != null) {
            val acc = accuracyM?.let { " (±%.0fm)".format(it) }.orEmpty()
            "Lat %.6f, Lng %.6f%s".format(lat, lng, acc)
        } else "Lokasi GPS belum terkunci"

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        val x = pad + pad * 0.35f
        var y = h - barH + pad + titleSize

        paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        paint.textSize = titleSize
        paint.color = Color.rgb(130, 185, 255)
        canvas.drawText(title, x, y, paint)

        y += bodySize + gap
        paint.color = Color.WHITE
        paint.textSize = bodySize
        canvas.drawText(timeStr, x, y, paint)

        y += smallSize + gap
        paint.typeface = Typeface.SANS_SERIF
        paint.textSize = smallSize
        canvas.drawText(geoStr, x, y, paint)

        y += smallSize + gap
        canvas.drawText(subtitle, x, y, paint)

        return bmp
    }
}
