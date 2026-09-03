package com.krisoft.tridjayaelektronik.data.export

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.text.TextPaint
import com.krisoft.tridjayaelektronik.data.local.ProductAggregate
import com.krisoft.tridjayaelektronik.data.pricing.hitungHargaPricetag
import kotlin.math.max
import kotlin.math.min

/**
 * Gambar SATU label harga (gaya poster "Promo Spesial / HARGA AMBYAR") ke
 * [rect] pada [canvas] mana pun — dipakai [PricetagImageExporter] untuk
 * merender satu label per foto PNG. Semua ukuran teks memakai PECAHAN dari
 * tinggi/lebar [rect] (bukan angka mutlak) supaya hasilnya konsisten dicetak
 * pada resolusi berapa pun.
 *
 * Struktur mengikuti template referensi baris demi baris (bukan hanya warna):
 * kartu putih -> "Promo Spesial" berpenjepit garis -> "HARGA AMBYAR" berpenjepit
 * aksen panah -> garis merah -> baris PUTIH (badge TE + nama toko + harga coret)
 * -> kotak harga besar -> bar NAVY (merek + nama/kode produk) -> footer PUTIH
 * ber-ikon (ongkir/pemasangan/hadiah/kontak). Logo merek TIDAK digambar sebagai
 * gambar (keputusan user): nama merek (`ProductAggregate.merk`) dicetak sebagai
 * teks, supaya tak perlu aset logo per merek. Ikon footer digambar manual lewat
 * [Path] (bukan aset/vector drawable) supaya renderer ini tetap murni Canvas
 * tanpa bergantung pada `Context`/resource.
 */
internal object PricetagRenderer {

    private const val KONTAK_KONSULTASI = "0878-0887-1588"

    private val NAVY = Color.parseColor("#123E8B")
    private val RED = Color.parseColor("#E30613")
    private val TEAL = Color.parseColor("#0E6E6E")

    fun rupiah(nilai: Double): String = "Rp. ${rupiahAngka(nilai)}"

    /** Angka berpemisah ribuan SAJA, tanpa prefix "Rp." — dipakai saat "Rp."
     *  dicetak terpisah dengan gaya/ukuran sendiri (lihat kotak harga besar). */
    fun rupiahAngka(nilai: Double): String {
        val bulat = nilai.toLong()
        return bulat.toString().reversed().chunked(3).joinToString(".").reversed()
    }

    private fun truncate(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var t = text
        while (t.isNotEmpty() && paint.measureText("$t…") > maxWidth) t = t.dropLast(1)
        return "$t…"
    }

    fun draw(canvas: Canvas, rect: RectF, product: ProductAggregate, markup: Boolean) {
        val harga = hitungHargaPricetag(product.harga, markup)
        val padX = rect.left + rect.width() * 0.035f
        val padRight = rect.right - rect.width() * 0.035f
        val strokeW = rect.width() * 0.0035f

        canvas.drawRoundRect(rect, rect.width() * 0.025f, rect.width() * 0.025f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
        canvas.drawRoundRect(
            rect, rect.width() * 0.025f, rect.width() * 0.025f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = NAVY; style = Paint.Style.STROKE; strokeWidth = strokeW * 1.4f }
        )

        drawCornerDots(canvas, rect.left + rect.width() * 0.02f, rect.top + rect.height() * 0.035f, rect.width())
        drawCornerDots(canvas, rect.right - rect.width() * 0.02f - rect.width() * 0.11f, rect.top + rect.height() * 0.035f, rect.width())

        val cx = rect.centerX()
        var y = rect.top + rect.height() * 0.075f

        // "Promo Spesial" — dipenjepit garis navy di kedua sisi (bukan teks polos).
        val promoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = NAVY; textSize = rect.height() * 0.032f; isFakeBoldText = true; textAlign = Paint.Align.CENTER
        }
        canvas.drawText("Promo Spesial", cx, y, promoPaint)
        val promoHalfWidth = promoPaint.measureText("Promo Spesial") / 2f
        val lineY = y - promoPaint.textSize * 0.32f
        val linePaint = Paint().apply { color = NAVY; strokeWidth = strokeW * 0.6f }
        canvas.drawLine(rect.left + rect.width() * 0.16f, lineY, cx - promoHalfWidth - rect.width() * 0.02f, lineY, linePaint)
        canvas.drawLine(cx + promoHalfWidth + rect.width() * 0.02f, lineY, rect.right - rect.width() * 0.16f, lineY, linePaint)

        y += rect.height() * 0.085f
        val ambyarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = RED; textSize = rect.height() * 0.068f; isFakeBoldText = true; textAlign = Paint.Align.CENTER
        }
        canvas.drawText("HARGA AMBYAR", cx, y, ambyarPaint)
        val ambyarHalfWidth = ambyarPaint.measureText("HARGA AMBYAR") / 2f
        val accentY = y - ambyarPaint.textSize * 0.32f
        drawArrowAccent(canvas, cx - ambyarHalfWidth - rect.width() * 0.02f, accentY, rect.height() * 0.024f, pointingRight = false)
        drawArrowAccent(canvas, cx + ambyarHalfWidth + rect.width() * 0.02f, accentY, rect.height() * 0.024f, pointingRight = true)

        y += rect.height() * 0.03f
        canvas.drawLine(padX, y, padRight, y, Paint().apply { color = RED; strokeWidth = strokeW })

        // Baris PUTIH: badge TE + nama toko (kiri), harga coret (kanan) — SENGAJA
        // tanpa banner navy (beda dari revisi lama), mengikuti referensi.
        y += rect.height() * 0.03f
        val infoH = rect.height() * 0.19f
        val infoRect = RectF(padX, y, padRight, y + infoH)

        val badgeR = infoH * 0.42f
        val badgeCx = infoRect.left + badgeR
        val badgeCy = infoRect.centerY()
        canvas.drawCircle(badgeCx, badgeCy, badgeR, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
        canvas.drawCircle(badgeCx, badgeCy, badgeR, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = NAVY; style = Paint.Style.STROKE; strokeWidth = strokeW * 1.2f })
        canvas.drawText(
            "T", badgeCx - badgeR * 0.32f, badgeCy + badgeR * 0.35f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = RED; textSize = badgeR * 1.05f; isFakeBoldText = true; textAlign = Paint.Align.CENTER }
        )
        canvas.drawText(
            "E", badgeCx + badgeR * 0.38f, badgeCy + badgeR * 0.35f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = NAVY; textSize = badgeR * 1.05f; isFakeBoldText = true; textAlign = Paint.Align.CENTER }
        )

        val storeNameX = badgeCx + badgeR + rect.width() * 0.02f
        canvas.drawText(
            "TRIDJAYA", storeNameX, badgeCy - infoH * 0.06f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = RED; textSize = infoH * 0.28f; isFakeBoldText = true }
        )
        canvas.drawText(
            "ELEKTRONIK", storeNameX, badgeCy + infoH * 0.24f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = TEAL; textSize = infoH * 0.24f; isFakeBoldText = true }
        )
        val tagY = badgeCy + infoH * 0.40f
        val tagText = "RAJANYA KREDIT ELEKTRONIK"
        val tagPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = infoH * 0.13f; isFakeBoldText = true }
        val tagW = tagPaint.measureText(tagText) + infoH * 0.16f
        val tagRect = RectF(storeNameX, tagY, storeNameX + tagW, tagY + infoH * 0.2f)
        canvas.drawRoundRect(tagRect, tagRect.height() * 0.3f, tagRect.height() * 0.3f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = RED })
        canvas.drawText(tagText, tagRect.left + infoH * 0.08f, tagRect.top + tagRect.height() * 0.68f, tagPaint)

        // Harga coret TIDAK lagi di baris ini (referensi terbaru tak menaruhnya
        // di sebelah nama toko) — sekarang masuk ke area kosong di ATAS "Rp."
        // di dalam kotak harga besar, lihat di bawah.
        y = infoRect.bottom + rect.height() * 0.025f

        // Kotak harga besar (putih, garis navy). "Rp." dicetak FLUSH KIRI dengan
        // gaya tetap (ukuran & posisi baseline diambil dari pengukuran piksel
        // referensi: tinggi glyph ~52% tinggi kotak, baseline ~88% dari atas),
        // angka mengikuti persis di sebelah kanannya pada baseline yang SAMA —
        // supaya terbaca sebagai satu angka utuh "Rp. 1.399.000", bukan dua
        // elemen terpisah yang kebetulan bersebelahan. Harga coret (kalau ada)
        // mengisi ruang kosong di atasnya, rata kanan.
        val priceH = rect.height() * 0.25f
        val priceRect = RectF(padX, y, padRight, y + priceH)
        canvas.drawRoundRect(priceRect, priceH * 0.08f, priceH * 0.08f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(242, 242, 241) })
        canvas.drawRoundRect(
            priceRect, priceH * 0.08f, priceH * 0.08f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = RED; style = Paint.Style.STROKE; strokeWidth = strokeW * 0.9f }
        )

        val priceInsetLeft = priceRect.left + rect.width() * 0.012f
        val priceInsetRight = priceRect.right - rect.width() * 0.02f
        val baselineY = priceRect.top + priceH * 0.88f

        if (harga.hargaCoret != null) {
            val coretText = rupiah(harga.hargaCoret)
            val coretPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = RED; textSize = priceH * 0.16f; isFakeBoldText = true; textAlign = Paint.Align.RIGHT
            }
            val coretY = priceRect.top + priceH * 0.26f
            canvas.drawText(coretText, priceInsetRight, coretY, coretPaint)
            val tw = coretPaint.measureText(coretText)
            canvas.drawLine(
                priceInsetRight - tw, coretY - coretPaint.textSize * 0.34f,
                priceInsetRight, coretY - coretPaint.textSize * 0.34f,
                Paint().apply { color = RED; strokeWidth = strokeW * 0.8f }
            )
        }

        val rpPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = RED; isFakeBoldText = true; textSize = priceH * 0.52f; textAlign = Paint.Align.LEFT
        }
        canvas.drawText("Rp.", priceInsetLeft, baselineY, rpPaint)
        val rpWidth = rpPaint.measureText("Rp.")

        val angkaText = rupiahAngka(harga.hargaBesar)
        val angkaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = RED; isFakeBoldText = true; textAlign = Paint.Align.LEFT
        }
        val angkaX = priceInsetLeft + rpWidth + rect.width() * 0.02f
        var angkaSize = priceH * 0.58f
        angkaPaint.textSize = angkaSize
        val angkaMaxWidth = priceInsetRight - angkaX
        while (angkaPaint.measureText(angkaText) > angkaMaxWidth && angkaSize > priceH * 0.15f) {
            angkaSize -= priceH * 0.01f
            angkaPaint.textSize = angkaSize
        }
        // Baseline angka diturunkan sedikit dibanding "Rp." supaya dasar KEDUANYA
        // rata (font besar duduk lebih rendah dari font kecil pada baseline yang
        // sama secara matematis, tapi optically angka besar terasa "mengambang"
        // kalau dipaksa baseline identik — offset kecil ini mengoreksinya).
        canvas.drawText(angkaText, angkaX, baselineY + priceH * 0.02f, angkaPaint)
        y = priceRect.bottom + rect.height() * 0.022f

        // Bar NAVY: merek (pengganti logo) di kiri, nama/kode produk di kanan —
        // SEBALIKNYA dari revisi lama yang menaruh baris ini di latar putih.
        val descH = rect.height() * 0.12f
        val descRect = RectF(padX, y, padRight, y + descH)
        canvas.drawRoundRect(descRect, descH * 0.18f, descH * 0.18f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = NAVY })
        canvas.drawText(
            product.merk.uppercase(), descRect.left + rect.width() * 0.02f, y + descH * 0.65f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; isFakeBoldText = true; textSize = descH * 0.42f }
        )
        val maxDescWidth = descRect.width() * 0.55f
        val descRight = descRect.right - rect.width() * 0.02f
        val namaPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; isFakeBoldText = true; textSize = descH * 0.30f; textAlign = Paint.Align.RIGHT
        }
        val kodePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; alpha = 210; textSize = descH * 0.24f; textAlign = Paint.Align.RIGHT
        }
        canvas.drawText(truncate(product.nama.uppercase(), namaPaint, maxDescWidth), descRight, y + descH * 0.40f, namaPaint)
        canvas.drawText(truncate(product.kode, kodePaint, maxDescWidth), descRight, y + descH * 0.40f + namaPaint.textSize + descH * 0.06f, kodePaint)
        y = descRect.bottom + rect.height() * 0.02f

        // Footer PUTIH ber-ikon (ongkir/pemasangan/hadiah + kontak) — sebelumnya
        // bar navy tanpa ikon, ganti total sesuai referensi.
        val footerRect = RectF(padX, y, padRight, rect.bottom - rect.height() * 0.02f)
        canvas.drawLine(footerRect.left, footerRect.top, footerRect.right, footerRect.top, Paint().apply { color = NAVY; strokeWidth = strokeW * 0.6f })
        drawFooter(canvas, footerRect)
    }

    private fun drawCornerDots(canvas: Canvas, startX: Float, startY: Float, refWidth: Float) {
        val step = refWidth * 0.0135f
        val radius = refWidth * 0.0028f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = NAVY; alpha = 130 }
        for (row in 0 until 4) {
            for (col in 0 until 5) {
                canvas.drawCircle(startX + col * step, startY + row * step, radius, paint)
            }
        }
    }

    /** Aksen panah/percikan di kedua sisi "HARGA AMBYAR", meniru bentuk referensi. */
    private fun drawArrowAccent(canvas: Canvas, x: Float, y: Float, size: Float, pointingRight: Boolean) {
        val dir = if (pointingRight) 1f else -1f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = RED }
        for (i in 0 until 3) {
            val ox = x + dir * i * size * 0.9f
            val path = Path().apply {
                moveTo(ox, y - size)
                lineTo(ox + dir * size, y)
                lineTo(ox, y + size)
                lineTo(ox + dir * size * 0.45f, y)
                close()
            }
            paint.alpha = 255 - i * 60
            canvas.drawPath(path, paint)
        }
    }

    private fun drawFooter(canvas: Canvas, r: RectF) {
        val w = r.width()
        val h = r.height()
        val iconColNavy = listOf(
            Triple("GRATIS", "ONGKIR", NAVY),
            Triple("GRATIS", "PEMASANGAN", NAVY),
            Triple("HADIAH", "MENARIK", RED)
        )

        // Kontak (kanan) diukur DULU supaya sisa lebar untuk 3 kolom ikon
        // (kiri) pasti akurat — dulu kolom ikon pakai lebar TETAP (15,5%
        // layar) tanpa peduli kata "PEMASANGAN" yang lebih panjang dari itu,
        // jadi "HADIAH MENARIK" numpuk di atasnya. Sekarang tiap kolom ikon
        // selebar teks dua barisnya SENDIRI (diukur, bukan ditebak), dan
        // seluruh footer diciutkan bareng kalau totalnya masih kelebihan.
        var labelSize = h * 0.22f
        val iconSizeFrac = 0.42f
        var phoneSize = h * 0.30f
        var contactLabelSize = h * 0.19f

        fun measure(): Triple<List<Float>, Float, Float> {
            val labelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { isFakeBoldText = true; textSize = labelSize }
            val iconSize = h * iconSizeFrac
            val colWidths = iconColNavy.map { (l1, l2, _) ->
                iconSize * 0.75f + w * 0.018f + max(labelPaint.measureText(l1), labelPaint.measureText(l2))
            }
            val phonePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFakeBoldText = true; textSize = phoneSize }
            val contactPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFakeBoldText = true; textSize = contactLabelSize }
            val phoneW = phonePaint.measureText(KONTAK_KONSULTASI)
            val contactLabelW = max(contactPaint.measureText("Konsultasi"), contactPaint.measureText("Pembelian"))
            val contactBlockW = phoneW + w * 0.02f + contactLabelW
            return Triple(colWidths, phoneW, contactBlockW)
        }

        // Ciutkan ukuran font (label kolom, nomor telepon, label kontak) bareng
        // sampai total lebar muat — jaring pengaman kalau nama merek/nomor
        // WA berubah jadi lebih panjang di kemudian hari.
        var (colWidths, phoneW, contactW) = measure()
        val gap = w * 0.02f
        var totalNeeded = colWidths.sum() + gap * iconColNavy.size + contactW
        var guard = 0
        while (totalNeeded > w && guard < 40) {
            labelSize *= 0.96f
            phoneSize *= 0.96f
            contactLabelSize *= 0.96f
            val m = measure()
            colWidths = m.first; phoneW = m.second; contactW = m.third
            totalNeeded = colWidths.sum() + gap * iconColNavy.size + contactW
            guard++
        }

        val iconSize = h * iconSizeFrac
        val labelPaintTop = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { isFakeBoldText = true; textSize = labelSize; textAlign = Paint.Align.LEFT }
        val labelPaintBottom = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { isFakeBoldText = true; textSize = labelSize; textAlign = Paint.Align.LEFT }

        var colX = r.left
        iconColNavy.forEachIndexed { index, (line1, line2, color) ->
            val colW = colWidths[index]
            val iconCx = colX + iconSize * 0.42f
            val iconCy = r.top + h * 0.38f
            when (index) {
                0 -> drawTruckIcon(canvas, iconCx, iconCy, iconSize, color)
                1 -> drawToolsIcon(canvas, iconCx, iconCy, iconSize, color)
                else -> drawGiftIcon(canvas, iconCx, iconCy, iconSize, color)
            }
            val textX = colX + iconSize * 0.75f + w * 0.018f
            val textTop = r.top + h * 0.72f
            labelPaintTop.color = color
            labelPaintBottom.color = color
            canvas.drawText(line1, textX, textTop, labelPaintTop)
            canvas.drawText(line2, textX, textTop + h * 0.26f, labelPaintBottom)

            colX += colW + gap
            if (index < iconColNavy.lastIndex) {
                canvas.drawLine(colX - gap * 0.5f, r.top + h * 0.15f, colX - gap * 0.5f, r.bottom - h * 0.15f, Paint().apply { this.color = Color.LTGRAY; strokeWidth = h * 0.025f })
            }
        }

        // Kontak — dua baris label merah + nomor tebal hitam, rata kanan.
        val phonePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK; isFakeBoldText = true; textSize = phoneSize; textAlign = Paint.Align.RIGHT
        }
        val phoneY = r.top + h * 0.58f
        canvas.drawText(KONTAK_KONSULTASI, r.right, phoneY, phonePaint)
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = RED; isFakeBoldText = true; textSize = contactLabelSize; textAlign = Paint.Align.RIGHT
        }
        val labelX = r.right - phoneW - w * 0.02f
        canvas.drawText("Konsultasi", labelX, r.top + h * 0.40f, labelPaint)
        canvas.drawText("Pembelian", labelX, r.top + h * 0.40f + h * 0.22f, labelPaint)
    }

    private fun drawTruckIcon(canvas: Canvas, cx: Float, cy: Float, size: Float, color: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.STROKE; strokeWidth = size * 0.09f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
        val bodyLeft = cx - size * 0.55f
        val bodyRight = cx + size * 0.05f
        val bodyTop = cy - size * 0.28f
        val bodyBottom = cy + size * 0.18f
        canvas.drawRoundRect(RectF(bodyLeft, bodyTop, bodyRight, bodyBottom), size * 0.06f, size * 0.06f, paint)
        val cab = Path().apply {
            moveTo(bodyRight, cy - size * 0.05f)
            lineTo(bodyRight + size * 0.32f, cy - size * 0.05f)
            lineTo(bodyRight + size * 0.32f, bodyBottom)
            lineTo(bodyRight, bodyBottom)
            close()
        }
        canvas.drawPath(cab, paint)
        val wheelR = size * 0.13f
        val wheelY = bodyBottom + wheelR * 0.15f
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.FILL }
        canvas.drawCircle(bodyLeft + size * 0.15f, wheelY, wheelR, fillPaint)
        canvas.drawCircle(bodyRight + size * 0.20f, wheelY, wheelR, fillPaint)
    }

    private fun drawToolsIcon(canvas: Canvas, cx: Float, cy: Float, size: Float, color: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.STROKE; strokeWidth = size * 0.14f; strokeCap = Paint.Cap.ROUND }
        // Kunci pas: batang diagonal + dua lingkaran terbuka di ujung.
        canvas.drawLine(cx - size * 0.32f, cy + size * 0.32f, cx + size * 0.28f, cy - size * 0.28f, paint)
        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.STROKE; strokeWidth = size * 0.11f }
        canvas.drawArc(RectF(cx - size * 0.55f, cy + size * 0.06f, cx - size * 0.15f, cy + size * 0.46f), 30f, 300f, false, ringPaint)
        canvas.drawArc(RectF(cx + size * 0.10f, cy - size * 0.46f, cx + size * 0.50f, cy - size * 0.06f), 210f, 300f, false, ringPaint)
        // Obeng bersilang.
        val screwdriver = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.STROKE; strokeWidth = size * 0.09f; strokeCap = Paint.Cap.ROUND }
        canvas.drawLine(cx - size * 0.30f, cy - size * 0.30f, cx + size * 0.30f, cy + size * 0.30f, screwdriver)
    }

    private fun drawGiftIcon(canvas: Canvas, cx: Float, cy: Float, size: Float, color: Int) {
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.STROKE; strokeWidth = size * 0.09f; strokeJoin = Paint.Join.ROUND }
        val boxRect = RectF(cx - size * 0.42f, cy - size * 0.05f, cx + size * 0.42f, cy + size * 0.45f)
        canvas.drawRoundRect(boxRect, size * 0.05f, size * 0.05f, fill)
        canvas.drawLine(cx, boxRect.top, cx, boxRect.bottom, fill)
        canvas.drawLine(boxRect.left, cy + size * 0.18f, boxRect.right, cy + size * 0.18f, fill)
        // Pita atas (dua kelopak).
        canvas.drawCircle(cx - size * 0.18f, cy - size * 0.14f, size * 0.16f, fill)
        canvas.drawCircle(cx + size * 0.18f, cy - size * 0.14f, size * 0.16f, fill)
    }
}
