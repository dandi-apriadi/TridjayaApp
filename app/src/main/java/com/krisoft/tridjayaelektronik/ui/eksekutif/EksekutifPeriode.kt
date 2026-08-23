package com.krisoft.tridjayaelektronik.ui.eksekutif

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Pilihan rentang papan eksekutif — fungsi MURNI, diuji `EksekutifPeriodeTest`.
 *
 * **`java.time` DILARANG di `app/src/main`** (minSdk 24, `coreLibraryDesugaring`
 * tidak aktif). Kompilasinya tetap hijau; yang pecah HP Android 7.0/7.1 di
 * lapangan dengan `NoClassDefFoundError` — turunan `Error`, bukan `Exception`,
 * jadi `runCatching` menelannya jadi nol senyap sementara `catch (e: Exception)`
 * tak menangkapnya sama sekali dan app-nya tutup. Karena itu di sini
 * `Calendar` + `SimpleDateFormat`, sama seperti `KlasemenStandings`.
 */
enum class EksekutifRentang(val label: String) {
    /** Tanggal 1 s/d hari ini. Sepadan dengan bawaan server dan periode KPI/gaji. */
    BULAN_INI("Bulan ini"),

    /** Bulan kalender penuh sebelumnya — satu-satunya pembanding yang setara. */
    BULAN_LALU("Bulan lalu"),

    /** Hari ini + 6 hari ke belakang. */
    TUJUH_HARI("7 hari"),

    /** Hari ini + 29 hari ke belakang. */
    TIGA_PULUH_HARI("30 hari"),
}

/** Rentang tanggal `yyyy-MM-dd` inklusif di kedua ujung, sama seperti server. */
data class RentangTanggal(val start: String, val end: String)

private val ISO = SimpleDateFormat("yyyy-MM-dd", Locale.US)
private val LABEL_BULAN = SimpleDateFormat("MMMM yyyy", Locale("id", "ID"))

/**
 * Hitung rentang untuk [pilihan], relatif terhadap [hariIniMillis].
 *
 * [hariIniMillis] disuntikkan (bukan `System.currentTimeMillis()` di dalam)
 * supaya test bisa mematok tanggal. Pemanggil produksi mengoper waktu nyata.
 */
fun rentangUntuk(
    pilihan: EksekutifRentang,
    hariIniMillis: Long = System.currentTimeMillis(),
): RentangTanggal {
    val cal = Calendar.getInstance().apply { timeInMillis = hariIniMillis }
    val hariIni = ISO.format(cal.time)
    return when (pilihan) {
        EksekutifRentang.BULAN_INI -> {
            val awal = Calendar.getInstance().apply {
                timeInMillis = hariIniMillis
                set(Calendar.DAY_OF_MONTH, 1)
            }
            RentangTanggal(ISO.format(awal.time), hariIni)
        }
        EksekutifRentang.BULAN_LALU -> {
            // Mundur ke tanggal 1 DULU, baru kurangi satu bulan. Urutan
            // terbalik menabrak bulan pendek: 31 Maret dikurangi satu bulan
            // menjadi 3 Maret di `Calendar` (Februari tak punya 31 hari), jadi
            // "bulan lalu" akan menunjuk bulan yang salah setiap tanggal 29-31.
            val awal = Calendar.getInstance().apply {
                timeInMillis = hariIniMillis
                set(Calendar.DAY_OF_MONTH, 1)
                add(Calendar.MONTH, -1)
            }
            val akhir = (awal.clone() as Calendar).apply {
                set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
            }
            RentangTanggal(ISO.format(awal.time), ISO.format(akhir.time))
        }
        EksekutifRentang.TUJUH_HARI -> RentangTanggal(geser(hariIniMillis, -6), hariIni)
        EksekutifRentang.TIGA_PULUH_HARI -> RentangTanggal(geser(hariIniMillis, -29), hariIni)
    }
}

private fun geser(millis: Long, hari: Int): String {
    val cal = Calendar.getInstance().apply {
        timeInMillis = millis
        add(Calendar.DAY_OF_MONTH, hari)
    }
    return ISO.format(cal.time)
}

/**
 * Label rentang untuk ditampilkan, mis. "1 – 23 Agustus 2026".
 *
 * Gagal parse → kembalikan `"$start – $end"` apa adanya. Rentangnya tetap
 * terbaca, dan itu lebih baik daripada teks kosong yang membuat layar terlihat
 * seperti gagal memuat.
 */
fun labelRentang(rentang: RentangTanggal): String {
    val awal = runCatching { ISO.parse(rentang.start) }.getOrNull()
    val akhir = runCatching { ISO.parse(rentang.end) }.getOrNull()
    if (awal == null || akhir == null) return "${rentang.start} – ${rentang.end}"

    val calAwal = Calendar.getInstance().apply { time = awal }
    val calAkhir = Calendar.getInstance().apply { time = akhir }
    val bulanSama = calAwal.get(Calendar.YEAR) == calAkhir.get(Calendar.YEAR) &&
        calAwal.get(Calendar.MONTH) == calAkhir.get(Calendar.MONTH)

    return if (bulanSama) {
        "${calAwal.get(Calendar.DAY_OF_MONTH)} – ${calAkhir.get(Calendar.DAY_OF_MONTH)} " +
            LABEL_BULAN.format(akhir)
    } else {
        "${calAwal.get(Calendar.DAY_OF_MONTH)} ${LABEL_BULAN.format(awal)} – " +
            "${calAkhir.get(Calendar.DAY_OF_MONTH)} ${LABEL_BULAN.format(akhir)}"
    }
}

/**
 * Umur salinan GS jadi teks pendek.
 *
 * `null` → "tidak diketahui", BUKAN "baru saja". Penanda kesegaran ditulis
 * inventory-service dan bisa saja belum pernah ada di environment ini;
 * menyamarkan ketidaktahuan sebagai kesegaran justru menghapus satu-satunya
 * tanda bahwa angkanya mungkin basi.
 */
fun labelKesegaran(umurDetik: Long?): String = when {
    umurDetik == null -> "umur data tidak diketahui"
    umurDetik < 120 -> "data baru saja disegarkan"
    umurDetik < 3600 -> "data ${umurDetik / 60} menit lalu"
    umurDetik < 86_400 -> "data ${umurDetik / 3600} jam lalu"
    else -> "data ${umurDetik / 86_400} hari lalu"
}

/** `1234567` → `"Rp 1.234.567"`. Nol tetap ditulis "Rp 0", bukan dikosongkan. */
fun formatRupiah(value: Long): String {
    val negatif = value < 0
    val angka = kotlin.math.abs(value).toString().reversed().chunked(3).joinToString(".").reversed()
    return if (negatif) "-Rp $angka" else "Rp $angka"
}

/**
 * Rupiah ringkas untuk kartu sempit: `1_234_567_890` → `"Rp 1,23 M"`.
 *
 * Dipisah dari [formatRupiah] karena keduanya dipakai bersamaan di layar yang
 * sama — ringkas di kartu ringkasan, penuh di baris rincian yang bisa dibaca
 * pelan-pelan.
 */
fun formatRupiahRingkas(value: Long): String {
    val abs = kotlin.math.abs(value)
    val tanda = if (value < 0) "-" else ""
    return when {
        abs >= 1_000_000_000L -> "${tanda}Rp ${desimal(abs, 1_000_000_000L)} M"
        abs >= 1_000_000L -> "${tanda}Rp ${desimal(abs, 1_000_000L)} jt"
        abs >= 1_000L -> "${tanda}Rp ${desimal(abs, 1_000L)} rb"
        else -> "${tanda}Rp $abs"
    }
}

private fun desimal(nilai: Long, pembagi: Long): String {
    val ratus = (nilai * 100 / pembagi)
    val bulat = ratus / 100
    val sisa = ratus % 100
    return when {
        sisa == 0L -> "$bulat"
        // Nol di BELAKANG dipangkas: 1.500 itu "1,5 rb", bukan "1,50 rb".
        // `padStart` tetap perlu untuk sisa satu digit — 1.050 itu "1,05 rb",
        // dan tanpa padding ia jadi "1,5 rb", yaitu angka yang SALAH sepuluh
        // kali lipat, bukan sekadar jelek.
        sisa % 10 == 0L -> "$bulat,${sisa / 10}"
        else -> "$bulat,${sisa.toString().padStart(2, '0')}"
    }
}

/** `null` → "—". Angka `0.0` TETAP dirender "0%" — lihat `domain::persen` di backend. */
fun formatPersen(value: Double?): String =
    if (value == null) "—" else "${value.toString().removeSuffix(".0")}%"
