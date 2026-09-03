package com.krisoft.tridjayaelektronik.data.export

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.text.TextPaint
import com.krisoft.tridjayaelektronik.data.local.ProductAggregate
import com.krisoft.tridjayaelektronik.data.pricing.hitungHargaPricetag

/**
 * Gambar SATU label harga (gaya poster "Promo Spesial / HARGA AMBYAR") ke
 * [rect] pada [canvas] mana pun — dipakai [PricetagImageExporter] untuk
 * merender satu label per foto PNG. Semua ukuran teks memakai PECAHAN dari
 * tinggi/lebar [rect] (bukan angka mutlak) supaya hasilnya konsisten dicetak
 * pada resolusi berapa pun.
 *
 * Logo merek TIDAK digambar sebagai gambar (keputusan user): nama merek
 * (`ProductAggregate.merk`) dicetak sebagai teks di posisi yang sama, supaya
 * tak perlu aset logo per merek.
 */
internal object PricetagRenderer {

    private const val KONTAK_KONSULTASI = "0878-0887-1588"

    private val NAVY = Color.parseColor("#123E8B")
    private val RED = Color.parseColor("#E30613")
    private val GOLD = Color.parseColor("#FFD54F")

    fun rupiah(nilai: Double): String {
        val bulat = nilai.toLong()
        val angka = bulat.toString().reversed().chunked(3).joinToString(".").reversed()
        return "Rp. $angka"
    }

    private fun truncate(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var t = text
        while (t.isNotEmpty() && paint.measureText("$t…") > maxWidth) t = t.dropLast(1)
        return "$t…"
    }

    fun draw(canvas: Canvas, rect: RectF, product: ProductAggregate, markup: Boolean) {
        val harga = hitungHargaPricetag(product.harga, markup)
        val padX = rect.left + rect.width() * 0.012f
        val padRight = rect.right - rect.width() * 0.012f
        val strokeW = rect.width() * 0.0035f

        canvas.drawRoundRect(rect, rect.width() * 0.012f, rect.width() * 0.012f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
        canvas.drawRoundRect(
            rect, rect.width() * 0.012f, rect.width() * 0.012f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = NAVY; style = Paint.Style.STROKE; strokeWidth = strokeW }
        )

        val cx = rect.centerX()
        var y = rect.top + rect.height() * 0.045f
        canvas.drawText(
            "Promo Spesial", cx, y,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = NAVY; textSize = rect.height() * 0.028f; textAlign = Paint.Align.CENTER }
        )
        y += rect.height() * 0.06f
        canvas.drawText(
            "HARGA AMBYAR", cx, y,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = RED; textSize = rect.height() * 0.052f; isFakeBoldText = true; textAlign = Paint.Align.CENTER
            }
        )
        y += rect.height() * 0.02f
        canvas.drawLine(padX, y, padRight, y, Paint().apply { color = NAVY; strokeWidth = strokeW * 0.5f })

        // Banner toko (navy): badge TE + nama toko di kiri, harga coret (kalau ada) di kanan.
        y += rect.height() * 0.02f
        val bannerH = rect.height() * 0.20f
        val bannerRect = RectF(padX, y, padRight, y + bannerH)
        canvas.drawRoundRect(bannerRect, bannerH * 0.1f, bannerH * 0.1f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = NAVY })

        val badgeR = bannerH * 0.36f
        val badgeCx = bannerRect.left + badgeR + rect.width() * 0.012f
        val badgeCy = bannerRect.centerY()
        canvas.drawCircle(badgeCx, badgeCy, badgeR, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
        canvas.drawText(
            "TE", badgeCx, badgeCy + badgeR * 0.35f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = NAVY; textSize = badgeR * 0.9f; isFakeBoldText = true; textAlign = Paint.Align.CENTER
            }
        )
        val storeNameX = badgeCx + badgeR + rect.width() * 0.012f
        canvas.drawText(
            "TRIDJAYA ELEKTRONIK", storeNameX, badgeCy - bannerH * 0.02f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = bannerH * 0.24f; isFakeBoldText = true }
        )
        canvas.drawText(
            "RAJANYA KREDIT ELEKTRONIK", storeNameX, badgeCy + bannerH * 0.28f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = RED; textSize = bannerH * 0.15f; isFakeBoldText = true }
        )

        if (harga.hargaCoret != null) {
            val coretText = rupiah(harga.hargaCoret)
            val coretPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE; textSize = bannerH * 0.28f; isFakeBoldText = true; textAlign = Paint.Align.RIGHT
            }
            val ty = bannerRect.centerY() + coretPaint.textSize * 0.35f
            val coretRight = bannerRect.right - rect.width() * 0.012f
            canvas.drawText(coretText, coretRight, ty, coretPaint)
            val tw = coretPaint.measureText(coretText)
            canvas.drawLine(
                coretRight - tw, ty - coretPaint.textSize * 0.32f,
                coretRight, ty - coretPaint.textSize * 0.32f,
                Paint().apply { color = RED; strokeWidth = strokeW * 0.7f }
            )
        }
        y = bannerRect.bottom + rect.height() * 0.02f

        // Kotak harga besar (putih, garis navy).
        val priceH = rect.height() * 0.26f
        val priceRect = RectF(padX, y, padRight, y + priceH)
        canvas.drawRoundRect(priceRect, priceH * 0.08f, priceH * 0.08f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
        canvas.drawRoundRect(
            priceRect, priceH * 0.08f, priceH * 0.08f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = NAVY; style = Paint.Style.STROKE; strokeWidth = strokeW * 0.7f }
        )
        val priceText = rupiah(harga.hargaBesar)
        val pricePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = RED; isFakeBoldText = true; textAlign = Paint.Align.CENTER
        }
        var priceSize = priceH * 0.55f
        pricePaint.textSize = priceSize
        val priceMaxWidth = priceRect.width() - rect.width() * 0.03f
        while (pricePaint.measureText(priceText) > priceMaxWidth && priceSize > priceH * 0.1f) {
            priceSize -= priceH * 0.01f
            pricePaint.textSize = priceSize
        }
        canvas.drawText(priceText, priceRect.centerX(), priceRect.centerY() + priceSize * 0.35f, pricePaint)
        y = priceRect.bottom + rect.height() * 0.02f

        // Baris merek (pengganti logo) + nama/kode produk.
        val descH = rect.height() * 0.12f
        canvas.drawText(
            product.merk.uppercase(), padX, y + descH * 0.65f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = NAVY; isFakeBoldText = true; textSize = descH * 0.7f }
        )
        val maxDescWidth = priceRect.width() * 0.58f
        val namaPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = NAVY; isFakeBoldText = true; textSize = descH * 0.34f; textAlign = Paint.Align.RIGHT
        }
        val kodePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.DKGRAY; textSize = descH * 0.28f; textAlign = Paint.Align.RIGHT
        }
        canvas.drawText(truncate(product.nama.uppercase(), namaPaint, maxDescWidth), padRight, y + descH * 0.42f, namaPaint)
        canvas.drawText(truncate(product.kode, kodePaint, maxDescWidth), padRight, y + descH * 0.42f + namaPaint.textSize + descH * 0.02f, kodePaint)
        y += descH + rect.height() * 0.015f

        // Footer (navy): promo standar toko + kontak.
        val footerRect = RectF(padX, y, padRight, rect.bottom - rect.height() * 0.015f)
        canvas.drawRoundRect(footerRect, footerRect.height() * 0.15f, footerRect.height() * 0.15f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = NAVY })
        canvas.drawText(
            "GRATIS ONGKIR · GRATIS PEMASANGAN · HADIAH MENARIK",
            footerRect.centerX(), footerRect.top + footerRect.height() * 0.42f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE; textSize = footerRect.height() * 0.32f; textAlign = Paint.Align.CENTER
            }
        )
        canvas.drawText(
            "Konsultasi Pembelian: $KONTAK_KONSULTASI",
            footerRect.centerX(), footerRect.top + footerRect.height() * 0.82f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = GOLD; isFakeBoldText = true; textSize = footerRect.height() * 0.34f; textAlign = Paint.Align.CENTER
            }
        )
    }
}
