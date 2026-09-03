package com.krisoft.tridjayaelektronik.data.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.net.Uri
import androidx.core.content.FileProvider
import com.krisoft.tridjayaelektronik.data.local.ProductAggregate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Cetak label harga (pricetag) sebagai FOTO (PNG) — satu file per barang,
 * gaya poster "Promo Spesial / HARGA AMBYAR" (lihat [PricetagRenderer] untuk
 * gambar labelnya). Satu barang → satu PNG dibagikan langsung; banyak barang
 * → dikemas satu ZIP supaya share-sheet Android tak diminta membuka ratusan
 * intent sekaligus.
 *
 * Latar labelnya SATU aset statis (`assets/pricetag/base_template.png`) yang
 * dipakai ULANG untuk setiap barang — hanya harga & harga coret yang
 * digambar dinamis di atasnya (lihat catatan di [PricetagRenderer] soal
 * kenapa merek/nama/kode barang TIDAK ikut dinamis).
 */
object PricetagImageExporter {

    private const val ASSET_PATH = "pricetag/base_template.png"

    suspend fun export(
        context: Context,
        products: List<ProductAggregate>,
        markup: Boolean,
        filePrefix: String
    ): Uri = withContext(Dispatchers.IO) {
        val safePrefix = filePrefix.trim().ifBlank { "Produk" }
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())
        val suffix = if (markup) "" else "_hargaAsli"
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }

        val baseBitmap = context.assets.open(ASSET_PATH).use { BitmapFactory.decodeStream(it) }
            ?: error("Gagal memuat template label harga")

        val tmpDir = File(dir, "pricetags_tmp_$timestamp").apply { mkdirs() }
        val resultFile = try {
            products.forEachIndexed { index, product ->
                val bitmap = renderBitmap(baseBitmap, product.harga, markup)
                val file = File(tmpDir, "${index}_${sanitize(product.kode)}_${sanitize(product.kodeCabang)}.png")
                FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
                bitmap.recycle()
            }

            val pngFiles = listPngFiles(tmpDir)
            if (pngFiles.size == 1) {
                val single = File(dir, "Pricetag_${safePrefix}_$timestamp$suffix.png")
                pngFiles.first().copyTo(single, overwrite = true)
                single
            } else {
                val zipFile = File(dir, "Pricetag_${safePrefix}_$timestamp$suffix.zip")
                ZipOutputStream(FileOutputStream(zipFile)).use { zip ->
                    pngFiles.forEach { f ->
                        zip.putNextEntry(ZipEntry(f.name))
                        f.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
                }
                zipFile
            }
        } finally {
            tmpDir.deleteRecursively()
            baseBitmap.recycle()
        }

        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", resultFile)
    }

    private fun listPngFiles(dir: File): List<File> =
        dir.listFiles { f -> f.extension == "png" }?.sortedBy { it.name } ?: emptyList()

    private fun sanitize(value: String): String =
        value.replace(Regex("[^A-Za-z0-9-]+"), "-").trim('-').ifBlank { "x" }

    private fun renderBitmap(baseBitmap: Bitmap, hargaAsli: Double, markup: Boolean): Bitmap {
        val bitmap = Bitmap.createBitmap(baseBitmap.width, baseBitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        PricetagRenderer.draw(canvas, baseBitmap, hargaAsli, markup)
        return bitmap
    }
}
