package com.krisoft.tridjayaelektronik.data.export

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.krisoft.tridjayaelektronik.data.pricing.hitungHargaPricetag

/**
 * Gambar SATU label harga di atas [baseBitmap] — aset statis
 * `assets/pricetag/base_template.png` (kartu "Promo Spesial / HARGA AMBYAR"
 * lengkap: logo, badge TE, tagline, kotak harga kosong dengan "Rp." sudah
 * tercetak, bar merek, footer ber-ikon). Fungsi ini HANYA menimpa dua hal:
 * angka harga besar (menyambung tepat setelah "Rp." yang sudah ada di
 * gambar) dan harga coret (kalau ada), di posisi piksel yang diukur
 * langsung dari aset referensi — lihat konstanta `*_F` di bawah.
 *
 * SENGAJA TIDAK generik per produk (keputusan user 2026-09-04): merek/nama/
 * kode barang di gambar (mis. "AQUA", "MESIN CUCI 2 TABUNG 7 KG") ikut
 * TERCETAK PERMANEN di `base_template.png` dan TIDAK diganti sesuai barang
 * yang sedang dibuka — hanya harga yang dinamis. Kalau kelak butuh templat
 * per-merek/kategori, gantinya bukan menambah teks di sini, melainkan
 * menyediakan beberapa `base_template_<slug>.png` dan memilihnya di
 * pemanggil (lihat [PricetagImageExporter]).
 */
internal object PricetagRenderer {

    private val RED = Color.parseColor("#E30613")

    // Fraksi posisi, diukur dari base_template.png ASLI (1491x1055 px) lewat
    // analisis piksel (batas kotak harga, bounding box glyph "Rp."). Dipakai
    // sebagai FRAKSI (bukan angka mutlak) supaya tetap benar kalau asetnya
    // kelak diganti dengan resolusi lain, selama proporsinya sama.
    private const val BOX_TOP_F = 572f / 1055f
    private const val BOX_HEIGHT_F = (824f - 572f) / 1055f
    private const val RP_RIGHT_F = 289f / 1491f          // tepi kanan glyph "Rp." tercetak
    private const val BASELINE_F = 814f / 1055f            // dasar glyph "Rp." (dari atas gambar)
    private const val PRICE_RIGHT_INSET_F = 1413f / 1491f  // tepi kanan area harga (dalam garis kotak)
    private const val CORET_BASELINE_F = 638f / 1055f      // baseline harga coret, area kosong di atas "Rp."

    fun rupiah(nilai: Double): String = "Rp. ${rupiahAngka(nilai)}"

    /** Angka berpemisah ribuan SAJA, tanpa prefix "Rp." — "Rp." sudah
     *  tercetak di [baseBitmap], jadi dicetak ulang di sini akan dobel. */
    fun rupiahAngka(nilai: Double): String {
        val bulat = nilai.toLong()
        return bulat.toString().reversed().chunked(3).joinToString(".").reversed()
    }

    fun draw(canvas: Canvas, baseBitmap: Bitmap, hargaAsli: Double, markup: Boolean) {
        canvas.drawBitmap(baseBitmap, 0f, 0f, null)

        val harga = hitungHargaPricetag(hargaAsli, markup)
        val w = baseBitmap.width.toFloat()
        val h = baseBitmap.height.toFloat()

        val boxTop = BOX_TOP_F * h
        val boxHeight = BOX_HEIGHT_F * h
        val baselineY = BASELINE_F * h
        val rpRight = RP_RIGHT_F * w
        val priceRightInset = PRICE_RIGHT_INSET_F * w

        if (harga.hargaCoret != null) {
            val coretText = rupiah(harga.hargaCoret)
            val coretPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = RED; isFakeBoldText = true; textSize = boxHeight * 0.16f; textAlign = Paint.Align.RIGHT
            }
            val coretY = CORET_BASELINE_F * h
            canvas.drawText(coretText, priceRightInset, coretY, coretPaint)
            val tw = coretPaint.measureText(coretText)
            canvas.drawLine(
                priceRightInset - tw, coretY - coretPaint.textSize * 0.34f,
                priceRightInset, coretY - coretPaint.textSize * 0.34f,
                Paint().apply { color = RED; strokeWidth = w * 0.0025f }
            )
        }

        val angkaText = rupiahAngka(harga.hargaBesar)
        val angkaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = RED; isFakeBoldText = true; textAlign = Paint.Align.LEFT
        }
        val angkaX = rpRight + w * 0.02f
        var angkaSize = boxHeight * 0.58f
        angkaPaint.textSize = angkaSize
        val angkaMaxWidth = priceRightInset - angkaX
        while (angkaPaint.measureText(angkaText) > angkaMaxWidth && angkaSize > boxHeight * 0.15f) {
            angkaSize -= boxHeight * 0.01f
            angkaPaint.textSize = angkaSize
        }
        // "Rp." di gambar dasar sedikit lebih kecil dari angka yang menyusul —
        // offset kecil ini menyamakan kesan dasar baris supaya tak "mengambang".
        canvas.drawText(angkaText, angkaX, baselineY + boxHeight * 0.02f, angkaPaint)
    }
}
