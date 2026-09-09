package com.krisoft.tridjayaelektronik.data.export

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
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
 * [priceTypeface] WAJIB font Anton (`assets/fonts/anton_regular.ttf`,
 * OFL — cocok dengan "Rp." tercetak di gambar, dibandingkan piksel-demi-
 * piksel saat implementasi). Font sistem (bold sintetis) terlihat "aneh"
 * bersebelahan dengan "Rp." bergaya Anton yang jauh lebih tebal & padat.
 *
 * Zona merek (tiga baris di atas foto, contoh "AQUA / MESIN CUCI")
 * DIGAMBAR ULANG per barang dari [merk]/[kategori]/[nama] — contoh di
 * `base_template.png` ditutup cat biru panel dulu. Zona tipe/badge promo di
 * tengah (panel biru) generik dan dibiarkan apa adanya.
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
    private const val BASELINE_F = 800f / 1055f            // dasar glyph "Rp." (dari atas gambar) — naik ~14px dari 814 agar angka tak mepet garis bawah
    private const val PRICE_RIGHT_INSET_F = 1413f / 1491f  // tepi kanan area harga (dalam garis kotak)
    private const val CORET_BASELINE_F = 638f / 1055f      // baseline harga coret, area kosong di atas "Rp."

    // Zona merek: tiga baris contoh di y140-300 (diukur: baris1 151-203,
    // baris2 BESAR 204-259, baris3 260-281; tepi x100-1400). Latar foto terang
    // ditutup cat biru panel selebar zona, lalu tiga baris ditulis ulang dari
    // data barang. Baseline = bottom tiap baris contoh.
    private const val HEADER_TOP_F = 140f / 1055f
    private const val HEADER_BOTTOM_F = 300f / 1055f
    private const val HEADER_LEFT_F = 60f / 1491f
    private const val HEADER_RIGHT_F = 1430f / 1491f
    private const val HEADER_B1_F = 203f / 1055f
    private const val HEADER_B2_F = 259f / 1055f
    private const val HEADER_B3_F = 281f / 1055f
    // Biru panel promo (1,49,132) — zona merek diseragamkan dengan panel
    // tengah supaya label satu rupa; teksnya putih di atasnya.
    private const val HEADER_BG_R = 1
    private const val HEADER_BG_G = 49
    private const val HEADER_BG_B = 132

    fun rupiah(nilai: Double): String = "Rp. ${rupiahAngka(nilai)}"

    /** Angka berpemisah ribuan SAJA, tanpa prefix "Rp." — "Rp." sudah
     *  tercetak di [baseBitmap], jadi dicetak ulang di sini akan dobel. */
    fun rupiahAngka(nilai: Double): String {
        val bulat = nilai.toLong()
        return bulat.toString().reversed().chunked(3).joinToString(".").reversed()
    }

    fun draw(canvas: Canvas, baseBitmap: Bitmap, hargaAsli: Double, markup: Boolean, priceTypeface: Typeface, merk: String = "", kategori: String = "", nama: String = "") {
        canvas.drawBitmap(baseBitmap, 0f, 0f, null)

        drawHeader(canvas, baseBitmap, merk, kategori, nama, priceTypeface)

        val harga = hitungHargaPricetag(hargaAsli, markup)
        val w = baseBitmap.width.toFloat()
        val h = baseBitmap.height.toFloat()

        val boxHeight = BOX_HEIGHT_F * h
        val baselineY = BASELINE_F * h
        val rpRight = RP_RIGHT_F * w
        val priceRightInset = PRICE_RIGHT_INSET_F * w

        if (harga.hargaCoret != null) {
            val coretText = rupiah(harga.hargaCoret)
            val coretPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = RED; typeface = priceTypeface; textSize = boxHeight * 0.16f; textAlign = Paint.Align.RIGHT
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
            color = RED; typeface = priceTypeface; textAlign = Paint.Align.LEFT
        }
        val angkaX = rpRight + w * 0.02f
        var angkaSize = boxHeight * 0.58f
        angkaPaint.textSize = angkaSize
        val angkaMaxWidth = priceRightInset - angkaX
        while (angkaPaint.measureText(angkaText) > angkaMaxWidth && angkaSize > boxHeight * 0.15f) {
            angkaSize -= boxHeight * 0.01f
            angkaPaint.textSize = angkaSize
        }
        canvas.drawText(angkaText, angkaX, baselineY, angkaPaint)
    }

    /**
     * Tulis ulang tiga baris merek sesuai barang. Contoh di templat
     * ("AQUA / MESIN CUCI ...") permanen di gambar, jadi DITUTUP cat biru panel
     * dulu selebar zona — kalau tidak, huruf contoh mengintip di balik teks
     * baru yang lebih pendek.
     *
     * Ukuran mengikuti contoh: baris2 (merk) paling besar, baris1 (kategori)
     * sedang, baris3 (nama) kecil. Masing-masing shrink-fit ke lebar zona;
     * yang tak muat di lebar minimum dipotong dengan "…" (lebih jujur
     * daripada mengecil sampai tak terbaca).
     */
    private fun drawHeader(canvas: Canvas, baseBitmap: Bitmap, merk: String, kategori: String, nama: String, typeface: Typeface) {
        val w = baseBitmap.width.toFloat()
        val h = baseBitmap.height.toFloat()
        val left = HEADER_LEFT_F * w
        val right = HEADER_RIGHT_F * w
        val cx = (left + right) / 2f
        val maxWidth = right - left
        // Tutup contoh permanen.
        canvas.drawRect(left, HEADER_TOP_F * h, right, HEADER_BOTTOM_F * h, android.graphics.Paint().apply {
            color = Color.rgb(HEADER_BG_R, HEADER_BG_G, HEADER_BG_B); style = android.graphics.Paint.Style.FILL
        })
        // Baris kosong = baris itu dibiarkan biru (tak ada yang ditulis).
        drawHeaderLine(canvas, kategori.uppercase().trim(), HEADER_B1_F * h, 56f / 1055f * h, maxWidth, cx, typeface)
        drawHeaderLine(canvas, merk.uppercase().trim(), HEADER_B2_F * h, 64f / 1055f * h, maxWidth, cx, typeface)
        drawHeaderLine(canvas, nama.trim(), HEADER_B3_F * h, 30f / 1055f * h, maxWidth, cx, typeface)
    }

    private fun drawHeaderLine(canvas: Canvas, text: String, baselineY: Float, startSize: Float, maxWidth: Float, cx: Float, typeface: Typeface) {
        if (text.isBlank()) return
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; this.typeface = typeface; textAlign = Paint.Align.CENTER
        }
        var size = startSize
        var label = text
        paint.textSize = size
        while (paint.measureText(label) > maxWidth && size > startSize * 0.4f) {
            size *= 0.95f
            paint.textSize = size
        }
        while (paint.measureText(label) > maxWidth && label.length > 4) {
            label = label.dropLast(2) + "…"
            // ukur ulang setelah potong (paint sama, tak perlu set ulang)
        }
        canvas.drawText(label, cx, baselineY, paint)
    }
}
