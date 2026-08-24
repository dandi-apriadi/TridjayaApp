package com.krisoft.tridjayaelektronik.ui.payroll

import com.krisoft.tridjayaelektronik.data.model.PayslipDetailData
import com.krisoft.tridjayaelektronik.data.model.PayslipDto
import com.krisoft.tridjayaelektronik.data.model.PayslipItemDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Isi slip PDF diuji sebagai DAFTAR BARIS, bukan lewat rendering.
 *
 * Baris yang HILANG dari PDF tidak menimbulkan error apa pun — orang baru sadar
 * saat membandingkan kertas dengan layar, dan pada dokumen gaji itu berarti
 * seseorang mengira potongannya tidak ada. Karena itu yang dikunci di sini:
 * tiap komponen ikut tercetak, dan total diambil dari angka SERVER.
 */
class SlipGajiPdfTest {

    private fun detail(
        items: List<PayslipItemDto> = emptyList(),
        totalEarning: Double = 0.0,
        totalDeduction: Double = 0.0,
        netPay: Double = 0.0,
    ) = PayslipDetailData(
        payslip = PayslipDto(
            id = 7,
            periode = "2026-07",
            karyawanNama = "Budi Santoso",
            jabatan = "Sales",
            divisi = "sales",
            cabangNama = "Pagaden",
            namaBank = "BCA",
            noRekening = "1234567890",
            totalEarning = totalEarning,
            totalDeduction = totalDeduction,
            netPay = netPay,
        ),
        items = items,
    )

    private fun teks(d: PayslipDetailData) = barisSlipGaji(d).joinToString("\n") { "${it.kiri}|${it.kanan}" }

    @Test
    fun setiap_komponen_pendapatan_dan_potongan_ikut_tercetak() {
        val hasil = teks(
            detail(
                items = listOf(
                    PayslipItemDto(label = "Gaji Pokok", tipe = "earning", amount = 4_000_000.0, urutan = 1),
                    PayslipItemDto(label = "Insentif Penjualan", tipe = "earning", amount = 1_250_000.0, urutan = 2),
                    PayslipItemDto(label = "BPJS", tipe = "deduction", amount = 150_000.0, urutan = 1),
                    PayslipItemDto(label = "Denda Absen", tipe = "deduction", amount = 50_000.0, urutan = 2),
                ),
            ),
        )
        listOf("Gaji Pokok", "Insentif Penjualan", "BPJS", "Denda Absen").forEach {
            assertTrue("komponen `$it` hilang dari PDF", hasil.contains(it))
        }
    }

    @Test
    fun urutan_komponen_mengikuti_field_urutan_server() {
        val baris = barisSlipGaji(
            detail(
                items = listOf(
                    PayslipItemDto(label = "Kedua", tipe = "earning", amount = 1.0, urutan = 2),
                    PayslipItemDto(label = "Pertama", tipe = "earning", amount = 1.0, urutan = 1),
                ),
            ),
        ).map { it.kiri }
        assertTrue(baris.indexOf("Pertama") < baris.indexOf("Kedua"))
    }

    /**
     * Total datang dari server, BUKAN dijumlah ulang di klien. Kalau keduanya
     * berselisih, yang benar adalah angka yang dipakai membayar — menjumlah
     * ulang menyembunyikan selisih itu di dokumen yang justru jadi bukti.
     */
    @Test
    fun total_memakai_angka_server_walau_beda_dari_jumlah_komponen() {
        val hasil = teks(
            detail(
                items = listOf(
                    PayslipItemDto(label = "Gaji Pokok", tipe = "earning", amount = 1_000_000.0, urutan = 1),
                ),
                totalEarning = 9_999_999.0,
                totalDeduction = 111_111.0,
                netPay = 9_888_888.0,
            ),
        )
        assertTrue(hasil.contains("Total Pendapatan|Rp 9.999.999"))
        assertTrue(hasil.contains("Total Potongan|Rp 111.111"))
        assertTrue(hasil.contains("GAJI BERSIH|Rp 9.888.888"))
    }

    @Test
    fun bagian_kosong_tetap_dicetak_sebagai_tidak_ada_komponen() {
        // Bagian yang lenyap sama sekali terbaca sebagai "belum dihitung";
        // "(tidak ada komponen)" menyatakan bahwa memang nol.
        val hasil = teks(detail())
        assertTrue(hasil.contains("PENDAPATAN|"))
        assertTrue(hasil.contains("POTONGAN|"))
        assertEquals(2, Regex("\\(tidak ada komponen\\)").findAll(hasil).count())
    }

    @Test
    fun nama_berkas_aman_dipakai_di_penyimpanan_mana_pun() {
        val nama = namaBerkasSlip(detail())
        assertEquals("slip-gaji-budi-santoso-2026-07.pdf", nama)
        assertTrue(nama.none { it in "/\\:*?\"<>| " })
    }

    @Test
    fun nama_berkas_tak_pecah_saat_nama_karyawan_aneh() {
        val d = PayslipDetailData(payslip = PayslipDto(karyawanNama = "  ", periode = ""))
        val nama = namaBerkasSlip(d)
        assertEquals("slip-gaji-karyawan-tanpa-periode.pdf", nama)
    }

    @Test
    fun nilai_negatif_tetap_terbaca_sebagai_negatif() {
        // Potongan yang melebihi pendapatan menghasilkan netPay negatif. Angka
        // yang kehilangan tandanya di dokumen gaji adalah kesalahan yang mahal.
        val hasil = teks(detail(netPay = -250_000.0))
        assertTrue(hasil.contains("GAJI BERSIH|-Rp 250.000"))
    }
}
