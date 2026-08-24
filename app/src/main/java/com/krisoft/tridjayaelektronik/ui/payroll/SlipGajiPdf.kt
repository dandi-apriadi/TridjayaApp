package com.krisoft.tridjayaelektronik.ui.payroll

import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.krisoft.tridjayaelektronik.data.model.PayslipDetailData
import com.krisoft.tridjayaelektronik.data.model.formatPeriodeId
import java.io.File
import java.io.FileOutputStream

/**
 * Ekspor slip gaji ke PDF — PENGGANTI screenshot, bukan pelengkap.
 *
 * Layar Slip Gaji memasang `FLAG_SECURE` (audit keamanan 2026-08 temuan 3.4)
 * sehingga screenshot diblokir sistem. Sampai berkas ini ada, screenshot ADALAH
 * satu-satunya cara karyawan menyimpan slipnya — kdoc `PayrollScreen` menulis
 * itu apa adanya. Memasang FLAG_SECURE tanpa pengganti = mencabut kemampuan
 * orang menyimpan bukti gajinya sendiri demi menutup risiko bahu-membahu; yang
 * dibangun di sini jalan keluarnya.
 *
 * DIBUAT DI PERANGKAT, BUKAN DI SERVER. Datanya sudah ada di layar (respons
 * `GET /payroll/payslips/{id}`), jadi merendernya lokal tak menambah satu pun
 * endpoint yang mengalirkan angka gaji, tak menambah dependensi PDF di backend,
 * dan tetap bekerja saat sinyal buruk. `FLAG_SECURE` tidak menghalangi ini —
 * yang diblokirnya penangkapan LAYAR, bukan menggambar ke kanvas PDF.
 */

/** Satu baris cetak. Sengaja setipis mungkin supaya tata letaknya bisa diuji tanpa Android. */
data class BarisSlip(
    val kiri: String,
    val kanan: String = "",
    val tebal: Boolean = false,
    /** Jarak ekstra DI ATAS baris ini (poin). Pemisah antar-bagian. */
    val jedaAtas: Float = 0f,
    /** Garis tipis di bawah baris ini — dipakai sebelum baris total. */
    val garisBawah: Boolean = false,
)

private fun rupiah(nilai: Double): String {
    val bulat = nilai.toLong()
    val tanda = if (bulat < 0) "-" else ""
    val angka = kotlin.math.abs(bulat).toString().reversed().chunked(3).joinToString(".").reversed()
    return "$tanda" + "Rp " + angka
}

/**
 * Isi slip sebagai daftar baris — FUNGSI MURNI, tanpa `Context`/`Canvas`.
 *
 * Dipisah supaya yang paling mudah salah bisa diuji: baris yang HILANG dari PDF
 * tidak menimbulkan error apa pun, dan orang baru sadar saat membandingkan
 * kertas dengan layar. Test mengunci bahwa tiap komponen earning & deduction
 * ikut tercetak, dan bahwa totalnya diambil dari server (bukan dijumlah ulang
 * di klien — dua sumber angka yang bisa berselisih diam-diam).
 */
fun barisSlipGaji(detail: PayslipDetailData): List<BarisSlip> {
    val p = detail.payslip
    val baris = mutableListOf<BarisSlip>()

    baris += BarisSlip("SLIP GAJI", tebal = true)
    baris += BarisSlip("Tridjaya Elektronik")
    baris += BarisSlip("Periode", formatPeriodeId(p.periode), jedaAtas = 14f)
    baris += BarisSlip("Nama", p.karyawanNama)
    if (p.jabatan.isNotBlank()) baris += BarisSlip("Jabatan", p.jabatan)
    if (p.divisi.isNotBlank()) baris += BarisSlip("Divisi", p.divisi)
    if (p.cabangNama.isNotBlank()) baris += BarisSlip("Cabang", p.cabangNama)
    p.namaBank?.takeIf { it.isNotBlank() }?.let { baris += BarisSlip("Bank", it) }
    p.noRekening?.takeIf { it.isNotBlank() }?.let { baris += BarisSlip("No. Rekening", it) }

    val pendapatan = detail.items.filter { it.tipe == "earning" }.sortedBy { it.urutan }
    val potongan = detail.items.filter { it.tipe == "deduction" }.sortedBy { it.urutan }

    baris += BarisSlip("PENDAPATAN", tebal = true, jedaAtas = 18f)
    if (pendapatan.isEmpty()) {
        baris += BarisSlip("(tidak ada komponen)", "")
    } else {
        pendapatan.forEach { baris += BarisSlip(it.label, rupiah(it.amount)) }
    }
    // Total dari SERVER (`totalEarning`), bukan hasil menjumlah ulang daftar di
    // atas: kalau keduanya berselisih, yang benar adalah angka yang dipakai
    // membayar — dan menjumlah ulang di klien menyembunyikan selisih itu.
    baris += BarisSlip("Total Pendapatan", rupiah(p.totalEarning), tebal = true, garisBawah = true)

    baris += BarisSlip("POTONGAN", tebal = true, jedaAtas = 18f)
    if (potongan.isEmpty()) {
        baris += BarisSlip("(tidak ada komponen)", "")
    } else {
        potongan.forEach { baris += BarisSlip(it.label, rupiah(it.amount)) }
    }
    baris += BarisSlip("Total Potongan", rupiah(p.totalDeduction), tebal = true, garisBawah = true)

    baris += BarisSlip("GAJI BERSIH", rupiah(p.netPay), tebal = true, jedaAtas = 20f)
    baris += BarisSlip(
        "Dokumen ini dibuat dari aplikasi Tridjaya dan tidak memerlukan tanda tangan.",
        jedaAtas = 24f,
    )
    return baris
}

/** Nama berkas yang aman dipakai di penyimpanan mana pun. */
fun namaBerkasSlip(detail: PayslipDetailData): String {
    val p = detail.payslip
    val nama = p.karyawanNama.ifBlank { "karyawan" }
        .replace(Regex("[^A-Za-z0-9]+"), "-")
        .trim('-')
        .lowercase()
    // Sanitasi DULU, baru cadangan: `"periode"` yang disaring `[^0-9-]` menjadi
    // string kosong, dan berkas bernama "slip-gaji-budi-.pdf" terlihat seperti
    // ekspor yang gagal separuh.
    val periode = p.periode.replace(Regex("[^0-9-]"), "").ifBlank { "tanpa-periode" }
    return "slip-gaji-$nama-$periode.pdf"
}

/**
 * Render [barisSlipGaji] jadi satu halaman A4 (595 × 842 pt @72dpi) di
 * `cacheDir/slip-gaji/`, lalu kembalikan berkasnya.
 *
 * Direktori `slip-gaji` WAJIB punya entri di `res/xml/file_paths.xml` — tanpa
 * itu `FileProvider.getUriForFile` melempar saat dibagikan (jebakan yang sudah
 * menggigit empat kali di repo ini; dijaga `FileProviderPathsTest`).
 */
fun tulisSlipGajiPdf(context: Context, detail: PayslipDetailData): File {
    val dokumen = PdfDocument()
    val halaman = dokumen.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
    val kanvas = halaman.canvas

    val teks = Paint().apply { textSize = 11f; isAntiAlias = true }
    val teksTebal = Paint().apply { textSize = 12f; isAntiAlias = true; isFakeBoldText = true }
    val garis = Paint().apply { strokeWidth = 0.7f }

    val kiriX = 48f
    val kananX = 595f - 48f
    var y = 64f

    barisSlipGaji(detail).forEach { b ->
        y += b.jedaAtas
        val cat = if (b.tebal) teksTebal else teks
        kanvas.drawText(b.kiri, kiriX, y, cat)
        if (b.kanan.isNotEmpty()) {
            val lebar = cat.measureText(b.kanan)
            kanvas.drawText(b.kanan, kananX - lebar, y, cat)
        }
        if (b.garisBawah) {
            kanvas.drawLine(kiriX, y + 5f, kananX, y + 5f, garis)
        }
        y += 18f
    }

    dokumen.finishPage(halaman)
    val dir = File(context.cacheDir, "slip-gaji").apply { mkdirs() }
    val berkas = File(dir, namaBerkasSlip(detail))
    FileOutputStream(berkas).use { dokumen.writeTo(it) }
    dokumen.close()
    return berkas
}
