package com.krisoft.tridjayaelektronik.data.export

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface

/**
 * Penempatan harga OTOMATIS pada template promo apa pun (aturan user 2026-09-09).
 *
 * Prinsip: JANGAN percaya koordinat absolut satu template — deteksi kotak
 * harga dari border merahnya saat runtime, lalu tempatkan teks dengan fraksi
 * relatif terhadap bounding box yang ketemu:
 * - harga utama: kiri-bawah interior, merah, paling besar & dominan, satu
 *   baris, shrink-fit proporsional dengan padding aman 4%.
 * - harga lama: kanan-atas interior, jauh lebih kecil, merah + strikethrough
 *   tepat di tengah teks mengikuti lebar teks.
 * - satu harga: kanan-atas dibiarkan kosong.
 *
 * Yang TIDAK disentuh: logo, header, foto produk, nama produk, nomor WA,
 * footer, warna/background template, aspect ratio. Contoh harga bawaan
 * template (teks di dalam kotak) DITUTUP cat putih seukuran bbox-nya dulu —
 * kalau tidak, harga baru menimpa angka contoh.
 */
internal object PricetagOtomatis {

    // Literal ARGB, bukan Color.parseColor — parseColor butuh runtime Android
    // dan meledak di unit test JVM (ExceptionInInitializerError).
    private val RED = 0xFFE30613.toInt()

    /** Area tulis: data murni (bukan RectF) supaya bisa diuji di JVM. */
    data class AreaTulis(
        val kiri: Float,
        val atas: Float,
        val kanan: Float,
        val bawah: Float,
    ) {
        fun lebar(): Float = kanan - kiri
        fun tinggi(): Float = bawah - atas
        fun keRect(): RectF = RectF(kiri, atas, kanan, bawah)
    }

    /** Hasil deteksi: batas luar border + area tulis di dalamnya. */
    data class KotakHarga(
        val borderKiri: Float,
        val borderAtas: Float,
        val borderKanan: Float,
        val borderBawah: Float,
    ) {
        /** Interior dikurangi setengah tebal border + padding aman 4%. */
        fun interior(tebalBorder: Float): AreaTulis {
            val padX = (borderKanan - borderKiri) * 0.04f
            val padY = (borderBawah - borderAtas) * 0.04f
            return AreaTulis(
                borderKiri + tebalBorder / 2f + padX,
                borderAtas + tebalBorder / 2f + padY,
                borderKanan - tebalBorder / 2f - padX,
                borderBawah - tebalBorder / 2f - padY,
            )
        }
    }

    /**
     * Cari persegi border merah terbesar: baris/kolom dengan run piksel
     * merah dominan. Ambang 60% lebar/tinggi menyaring garis dekorasi
     * pendek. Gagal = null (pemanggil pakai fallback fraksi template lama).
     */
    fun deteksi(bitmap: Bitmap): KotakHarga? {
        val w = bitmap.width
        val h = bitmap.height
        if (w < 100 || h < 100) return null
        val px = IntArray(w * h)
        bitmap.getPixels(px, 0, w, 0, 0, w, h)
        fun merah(i: Int): Boolean {
            val r = px[i] shr 16 and 0xFF
            val g = px[i] shr 8 and 0xFF
            val b = px[i] and 0xFF
            return r > 180 && g < 100 && b < 100
        }
        // Baris horizontal kandidat: run merah >= 60% lebar.
        val baris = ArrayList<Int>()
        var y = 0
        while (y < h) {
            var run = 0
            var x = 0
            while (x < w) {
                if (merah(y * w + x)) run++ else run = 0
                if (run >= (w * 0.6f).toInt()) {
                    baris.add(y)
                    break
                }
                x++
            }
            y++
        }
        if (baris.size < 2) return null
        val atas = baris.first().toFloat()
        val bawah = baris.last().toFloat()
        if (bawah - atas < h * 0.1f) return null
        // Kolom vertikal kandidat di antara kedua garis.
        val tengah = ((atas + bawah) / 2f).toInt()
        var kiri = -1
        var x = 0
        while (x < w) {
            if (merah(tengah * w + x)) {
                kiri = x
                break
            }
            x++
        }
        var kanan = -1
        x = w - 1
        while (x >= 0) {
            if (merah(tengah * w + x)) {
                kanan = x
                break
            }
            x--
        }
        if (kiri < 0 || kanan < 0 || kanan - kiri < w * 0.3f) return null
        return KotakHarga(kiri.toFloat(), atas, kanan.toFloat(), bawah)
    }

    /**
     * Gambar harga ke [canvas] yang SUDAH berisi template. Menutup contoh
     * harga bawaan ([tutupContoh]) lalu menulis harga utama + lama.
     */
    fun gambar(
        canvas: Canvas,
        bitmap: Bitmap,
        kotak: KotakHarga,
        hargaUtama: String,
        hargaLama: String?,
        typeface: Typeface,
        tutupContoh: List<RectF> = emptyList(),
    ) {
        val w = bitmap.width.toFloat()
        val h = bitmap.height.toFloat()
        val tebal = ((kotak.borderKanan - kotak.borderKiri) / w * 8f).coerceIn(2f, 12f)
        val putih = Paint().apply { color = Color.WHITE; style = Paint.Style.FILL }
        tutupContoh.forEach { canvas.drawRect(it, putih) }
        val area = kotak.interior(tebal)

        // Harga lama dulu (kanan-atas), supaya tak tertimpa utama.
        if (!hargaLama.isNullOrBlank()) {
            val paintLama = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = RED; this.typeface = typeface; textAlign = Paint.Align.RIGHT
            }
            var sizeLama = (area.tinggi() * 0.20f).coerceAtLeast(8f)
            paintLama.textSize = sizeLama
            while (paintLama.measureText(hargaLama) > area.lebar() * 0.45f && sizeLama > 8f) {
                sizeLama *= 0.95f
                paintLama.textSize = sizeLama
            }
            val xKanan = area.kanan
            val yAtas = area.atas + sizeLama
            canvas.drawText(hargaLama, xKanan, yAtas, paintLama)
            val tw = paintLama.measureText(hargaLama)
            val garisY = yAtas - sizeLama * 0.30f
            canvas.drawLine(
                xKanan - tw, garisY, xKanan, garisY,
                Paint().apply { color = RED; strokeWidth = (sizeLama * 0.07f).coerceAtLeast(1.5f) },
            )
        }

        // Harga utama: kiri-bawah, dominan, satu baris, shrink proporsional.
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = RED; this.typeface = typeface; textAlign = Paint.Align.LEFT
        }
        var size = (area.tinggi() * 0.42f).coerceAtLeast(12f)
        paint.textSize = size
        while (paint.measureText(hargaUtama) > area.lebar() && size > 10f) {
            size *= 0.95f
            paint.textSize = size
        }
        canvas.drawText(hargaUtama, area.kiri, area.bawah, paint)
    }

    /** Format rupiah Indonesia: "Rp. 1.700.000". */
    fun rupiah(nilai: Double): String {
        val bulat = nilai.toLong()
        val angka = bulat.toString().reversed().chunked(3).joinToString(".").reversed()
        return "Rp. $angka"
    }
}
