package com.krisoft.tridjayaelektronik.domain.lapangan

import com.krisoft.tridjayaelektronik.data.model.MetrikLapanganDto
import java.util.Locale

/**
 * Penyajian angka papan lapangan — fungsi murni, sengaja di luar Compose supaya
 * bisa diuji tanpa emulator.
 *
 * Aturannya sengaja SAMA PERSIS dengan sisi web
 * (`frontend/src/utils/klasemenLapangan.ts`): satu papan yang dibaca dua klien
 * tidak boleh menampilkan angka yang berbeda bentuk, karena orang yang sama
 * membandingkan posisinya di HP dan di layar kantor.
 */
object FormatMetrik {

    private val ID = Locale("id", "ID")

    private fun angka(nilai: Double, pecahan: Int): String =
        String.format(ID, "%,.${pecahan}f", nilai)

    /**
     * `null` menjadi "—", BUKAN "0". Lihat catatan di [MetrikLapanganDto.nilai].
     * NaN diperlakukan sama: angka yang tak bisa dihitung bukan angka nol.
     */
    fun nilai(metrik: MetrikLapanganDto): String {
        val v = metrik.nilai
        if (v == null || v.isNaN()) return "—"
        return when (metrik.satuan) {
            "persen" -> "${angka(v, 1)}%"
            "hari" -> "${angka(v, 1)} hari"
            "unit" -> "${angka(v, 0)} unit"
            else -> angka(v, 1)
        }
    }

    /** "78 dari 80" — pecahan di balik persentase, supaya sampel kecil kelihatan. */
    fun pecahan(metrik: MetrikLapanganDto): String? {
        val a = metrik.pembilang ?: return null
        val b = metrik.penyebut ?: return null
        return "${angka(a.toDouble(), 0)} dari ${angka(b.toDouble(), 0)}"
    }

    /** Medali tiga besar; sisanya nomor apa adanya. */
    fun lencana(rank: Int): String = when (rank) {
        1 -> "🥇"
        2 -> "🥈"
        3 -> "🥉"
        else -> rank.toString()
    }

    /** `2026-08` → `Agustus 2026`. Periode ngawur dikembalikan apa adanya. */
    fun periode(ym: String): String {
        val bagian = ym.split("-")
        val bulan = bagian.getOrNull(1)?.toIntOrNull() ?: return ym
        val tahun = bagian.getOrNull(0)?.toIntOrNull() ?: return ym
        val nama = NAMA_BULAN.getOrNull(bulan - 1) ?: return ym
        return "$nama $tahun"
    }

    private val NAMA_BULAN = listOf(
        "Januari", "Februari", "Maret", "April", "Mei", "Juni",
        "Juli", "Agustus", "September", "Oktober", "November", "Desember",
    )
}
