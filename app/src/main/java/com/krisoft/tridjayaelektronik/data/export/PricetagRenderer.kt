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
 * Zona merek contoh di atas foto ("AQUA / ...") DITUTUP putih polos, dan
 * merek+tipe barang DIGAMBAR ULANG di bawah kotak harga (di atas putih
 * footer) dari [merk]/[nama] — pembaca melihat nama barang tepat di bawah
 * harganya, bukan jauh di atas. Zona tipe/badge promo di tengah (panel
 * biru) generik dan dibiarkan apa adanya.
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

    // Zona merek contoh di y140-300 (tepi x60-1430) DITUTUP putih polos —
    // merek+tipe pindah ke bawah kotak harga (lihat SUB_*). Putih (246)
    // menyatu dengan latar foto terang di sekitarnya.
    private const val HEADER_TOP_F = 140f / 1055f
    private const val HEADER_BOTTOM_F = 300f / 1055f
    private const val HEADER_LEFT_F = 60f / 1491f
    private const val HEADER_RIGHT_F = 1430f / 1491f
    // Merek+tipe di bawah harga: ruang putih y835-930 antara garis bawah
    // kotak (823) dan footer ikon (934). Dua baris: merk merah besar di
    // atas, nama gelap kecil di bawah. Baseline = bottom tiap baris.
    private const val SUB_LEFT_F = 120f / 1491f
    private const val SUB_RIGHT_F = 1370f / 1491f
    private const val SUB_MERK_F = 878f / 1055f
    private const val SUB_NAMA_F = 918f / 1055f

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
     * Contoh merek di templat ("AQUA / ...") permanen di gambar, jadi DITUTUP
     * putih polos — kalau tidak, huruf contoh mengintip. Merek+tipe barang
     * ditulis di BAWAH kotak harga ([SUB_MERK_F]/[SUB_NAMA_F]): merk merah
     * besar, nama gelap kecil. Masing-masing shrink-fit ke lebar zona; yang
     * tak muat dipotong "…" (lebih jujur daripada mengecil tak terbaca).
     */
    private fun drawHeader(canvas: Canvas, baseBitmap: Bitmap, merk: String, kategori: String, nama: String, typeface: Typeface) {
        val w = baseBitmap.width.toFloat()
        val h = baseBitmap.height.toFloat()
        // Tutup contoh permanen dengan putih polos.
        canvas.drawRect(HEADER_LEFT_F * w, HEADER_TOP_F * h, HEADER_RIGHT_F * w, HEADER_BOTTOM_F * h, android.graphics.Paint().apply {
            color = Color.rgb(246, 246, 246); style = android.graphics.Paint.Style.FILL
        })
        val left = SUB_LEFT_F * w
        val right = SUB_RIGHT_F * w
        val cx = (left + right) / 2f
        val maxWidth = right - left
        // Kategori digabung ke nama bila ada ("KATEGORI — Nama").
        val subNama = listOf(kategori.trim(), nama.trim()).filter { it.isNotBlank() }.joinToString(" — ")
        drawSubLine(canvas, merk.uppercase().trim(), SUB_MERK_F * h, 46f / 1055f * h, maxWidth, cx, typeface, RED)
        drawSubLine(canvas, subNama, SUB_NAMA_F * h, 26f / 1055f * h, maxWidth, cx, typeface, Color.rgb(34, 34, 34))
    }

    private fun drawSubLine(canvas: Canvas, text: String, baselineY: Float, startSize: Float, maxWidth: Float, cx: Float, typeface: Typeface, color: Int) {
        if (text.isBlank()) return
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color; this.typeface = typeface; textAlign = Paint.Align.CENTER
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
