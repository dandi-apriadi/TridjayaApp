package com.krisoft.tridjayaelektronik.ui.eksekutif

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

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
    /** Hanya hari ini. Rentang TERSEMPIT yang dilayani. */
    HARI_INI("Hari ini"),

    /** Hanya kemarin — satu hari penuh, tak terpotong jam berjalan. */
    KEMARIN("Kemarin"),

    /** Hari ini + 6 hari ke belakang. */
    TUJUH_HARI("7 hari"),

    /** Hari ini + 29 hari ke belakang. */
    TIGA_PULUH_HARI("30 hari"),

    /** Tanggal 1 s/d hari ini. Sepadan dengan bawaan server dan periode KPI/gaji. */
    BULAN_INI("Bulan ini"),

    /** Bulan kalender penuh sebelumnya — satu-satunya pembanding yang setara. */
    BULAN_LALU("Bulan lalu"),

    /**
     * 1 Januari s/d hari ini.
     *
     * Muat di plafon server (`MAKS_HARI_RENTANG = 366`) untuk SETIAP hari dalam
     * tahun berjalan, termasuk 31 Desember tahun kabisat — 366 hari pas. Tahun
     * kalender PENUH yang sudah lewat juga muat; yang tidak muat cuma rentang
     * yang menyeberangi dua tahun lebih dari 366 hari, dan itu ditolak
     * [`validasiRentang`] sebelum permintaannya dikirim.
     */
    TAHUN_INI("Tahun ini"),
}

/**
 * Periode yang sedang dipilih — preset ATAU pilihan manual.
 *
 * Dipisah dari [EksekutifRentang] alih-alih menambah varian enum: tanggal,
 * bulan, dan rentang bebas MEMBAWA NILAI, dan enum yang membawa nilai lewat
 * variabel di sebelahnya adalah cara paling murah membuat label chip dan rentang
 * yang dikirim ke server berpisah tanpa satu pun galat.
 */
sealed interface PilihanPeriode {
    /** Salah satu preset. */
    data class Preset(val rentang: EksekutifRentang) : PilihanPeriode

    /** Satu tanggal tertentu, `yyyy-MM-dd`. */
    data class Tanggal(val tanggal: String) : PilihanPeriode

    /** Satu bulan kalender, `yyyy-MM`. Ujungnya dipotong hari ini bila belum lewat. */
    data class Bulan(val kunci: String) : PilihanPeriode

    /** Satu tahun kalender, `yyyy`. Ujungnya dipotong hari ini bila belum lewat. */
    data class Tahun(val kunci: String) : PilihanPeriode

    /** Rentang bebas, kedua ujung `yyyy-MM-dd`, INKLUSIF. */
    data class Kustom(val start: String, val end: String) : PilihanPeriode

    companion object {
        /** Bawaan: sama dengan bawaan server (bulan berjalan s/d hari ini). */
        val BAWAAN: PilihanPeriode = Preset(EksekutifRentang.BULAN_INI)
    }
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
        EksekutifRentang.HARI_INI -> RentangTanggal(hariIni, hariIni)
        EksekutifRentang.KEMARIN -> {
            val kemarin = geser(hariIniMillis, -1)
            RentangTanggal(kemarin, kemarin)
        }
        EksekutifRentang.TAHUN_INI -> {
            val awal = Calendar.getInstance().apply {
                timeInMillis = hariIniMillis
                set(Calendar.MONTH, Calendar.JANUARY)
                set(Calendar.DAY_OF_MONTH, 1)
            }
            RentangTanggal(ISO.format(awal.time), hariIni)
        }
    }
}

/**
 * Rentang untuk [pilihan] — preset maupun pilihan manual.
 *
 * **Ujung akhir SELALU dipotong hari ini.** Bulan atau tahun berjalan yang
 * dikirim utuh membuat server menghitung hari kerja yang BELUM TERJADI ke dalam
 * penyebut: kehadiran satu perusahaan langsung terlihat anjlok tiap tanggal
 * muda, dan penyebabnya tak terlihat di mana pun karena kedua angkanya benar
 * menurut rumusnya masing-masing. Pemotongan tidak berlaku bila SELURUH
 * rentangnya di masa depan (start > hari ini) — di situ tak ada yang bisa
 * dipotong, dan jawaban nol memang jawaban yang benar.
 */
fun rentangUntuk(
    pilihan: PilihanPeriode,
    hariIniMillis: Long = System.currentTimeMillis(),
): RentangTanggal {
    val hariIni = ISO.format(Calendar.getInstance().apply { timeInMillis = hariIniMillis }.time)
    val mentah = when (pilihan) {
        is PilihanPeriode.Preset -> rentangUntuk(pilihan.rentang, hariIniMillis)
        is PilihanPeriode.Tanggal -> RentangTanggal(pilihan.tanggal, pilihan.tanggal)
        is PilihanPeriode.Bulan -> batasBulan(pilihan.kunci)
            ?: rentangUntuk(EksekutifRentang.BULAN_INI, hariIniMillis)
        is PilihanPeriode.Tahun -> batasTahun(pilihan.kunci)
            ?: rentangUntuk(EksekutifRentang.TAHUN_INI, hariIniMillis)
        is PilihanPeriode.Kustom -> RentangTanggal(pilihan.start, pilihan.end)
    }
    // Perbandingan STRING atas `yyyy-MM-dd` itu sah dan sengaja: formatnya
    // panjang-tetap dan berzona-nol, jadi urutan leksikografisnya IDENTIK
    // dengan urutan kronologisnya. Mengurainya jadi `Date` di sini cuma
    // menambah satu tempat yang bisa gagal parse.
    if (mentah.start > hariIni) return mentah
    return if (mentah.end > hariIni) mentah.copy(end = hariIni) else mentah
}

/** Batas kalender satu bulan `yyyy-MM`. `null` bila kuncinya tak sah. */
fun batasBulan(kunci: String): RentangTanggal? {
    val bagian = kunci.split("-")
    if (bagian.size != 2) return null
    val tahun = bagian[0].toIntOrNull() ?: return null
    val bulan = bagian[1].toIntOrNull() ?: return null
    if (bulan !in 1..12) return null
    val cal = Calendar.getInstance().apply {
        clear()
        set(tahun, bulan - 1, 1)
    }
    val start = ISO.format(cal.time)
    cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
    return RentangTanggal(start, ISO.format(cal.time))
}

/** Batas kalender satu tahun `yyyy`. `null` bila kuncinya tak sah. */
fun batasTahun(kunci: String): RentangTanggal? {
    val tahun = kunci.toIntOrNull() ?: return null
    if (tahun !in 2000..2100) return null
    val cal = Calendar.getInstance().apply {
        clear()
        set(tahun, Calendar.JANUARY, 1)
    }
    val start = ISO.format(cal.time)
    cal.set(tahun, Calendar.DECEMBER, 31)
    return RentangTanggal(start, ISO.format(cal.time))
}

/** Batas atas rentang yang dilayani server (`eksekutif::domain::MAKS_HARI_RENTANG`). */
const val MAKS_HARI_RENTANG = 366

/**
 * Alasan sebuah rentang ditolak, atau `null` bila sah.
 *
 * **Disaring di klien MESKI server juga menyaringnya.** Bukan duplikasi yang
 * sia-sia: yang dicegah di sini adalah permintaan yang sudah PASTI dijawab 400,
 * lalu memunculkan banner merah di atas papan yang sebelumnya baik-baik saja.
 * Orang yang menggeser tanggal akhir satu hari terlalu jauh tak sedang membuat
 * kesalahan yang layak dijawab galat; ia layak dijawab "maksimal 366 hari"
 * SEBELUM apa pun dikirim.
 */
fun validasiRentang(start: String, end: String): String? {
    val a = runCatching { ISO.parse(start) }.getOrNull() ?: return "Tanggal mulai tidak sah"
    val b = runCatching { ISO.parse(end) }.getOrNull() ?: return "Tanggal akhir tidak sah"
    if (b.before(a)) return "Tanggal akhir tidak boleh sebelum tanggal mulai"
    // +1 karena rentangnya INKLUSIF di kedua ujung, sama seperti `BETWEEN` di
    // server. Pembagian pakai 86_400_000L dan bukan selisih hari kalender:
    // yang dibandingkan sudah tengah malam waktu lokal di kedua ujung.
    val hari = (b.time - a.time) / 86_400_000L + 1
    if (hari > MAKS_HARI_RENTANG) {
        return "Rentang maksimal $MAKS_HARI_RENTANG hari (dipilih $hari hari)"
    }
    return null
}

/** Label pendek untuk chip periode yang sedang aktif. */
fun labelPilihan(pilihan: PilihanPeriode): String = when (pilihan) {
    is PilihanPeriode.Preset -> pilihan.rentang.label
    is PilihanPeriode.Tanggal -> labelTanggalPendek(pilihan.tanggal)
    is PilihanPeriode.Bulan -> batasBulan(pilihan.kunci)
        ?.let { LABEL_BULAN.format(ISO.parse(it.start)!!) }
        ?: pilihan.kunci
    is PilihanPeriode.Tahun -> "Tahun ${pilihan.kunci}"
    is PilihanPeriode.Kustom -> "Rentang khusus"
}

/** `2026-08-23` → `23 Agustus 2026`. Gagal parse → teks apa adanya. */
fun labelTanggalPendek(tanggal: String): String {
    val d = runCatching { ISO.parse(tanggal) }.getOrNull() ?: return tanggal
    val cal = Calendar.getInstance().apply { time = d }
    return "${cal.get(Calendar.DAY_OF_MONTH)} ${LABEL_BULAN.format(d)}"
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

/**
 * Tingkat skor kepatuhan — tiga pita, dipakai untuk warna DAN untuk kata.
 *
 * Warnanya tak pernah berdiri sendiri: tiap tempat yang memakai pita ini juga
 * menulis [label]-nya. Layar yang membedakan "baik" dari "buruk" HANYA lewat
 * hijau/merah tak terbaca oleh mata yang tak membedakan keduanya, dan papan ini
 * dipakai untuk menilai orang.
 */
enum class PitaKepatuhan(val label: String) {
    PRIMA("Prima"),
    PANTAU("Pantau"),
    PRIORITAS("Prioritas"),
    /** Belum bisa diukur — bukan buruk, bukan baik. */
    TAK_TERUKUR("Belum terukur"),
}

/**
 * Ambangnya SAMA dengan halaman aktivitas web (`OwnerAktivitasPage.getStatus`:
 * ≥80 prima, ≥50 pantau) supaya satu orang tak pernah "Prima" di satu layar dan
 * "Pantau" di layar lain. Kalau ambang di sana berubah, ubah di sini juga —
 * tak ada pemeriksa yang bisa melihat keduanya berpisah.
 */
fun pitaKepatuhan(skor: Double?): PitaKepatuhan = when {
    skor == null -> PitaKepatuhan.TAK_TERUKUR
    skor >= 80.0 -> PitaKepatuhan.PRIMA
    skor >= 50.0 -> PitaKepatuhan.PANTAU
    else -> PitaKepatuhan.PRIORITAS
}

/**
 * Skor kepatuhan jadi teks. `null` → "—", BUKAN "0".
 *
 * Alasan yang sama dengan `formatPersen`: nol berarti "diukur dan hasilnya nol",
 * dan itu klaim yang berbeda dari "belum bisa diukur".
 */
fun formatSkor(skor: Double?): String =
    if (skor == null) "—" else skor.toString().removeSuffix(".0")

/**
 * Bobot sebagai persen bulat, mis. `0.65` → `"65%"`.
 *
 * Ditampilkan bersama skor supaya "90 dari satu komponen" bisa dibedakan dari
 * "90 dari empat komponen" — dua angka 90 yang artinya jauh berbeda.
 */
fun formatBobot(bobot: Double): String = "${Math.round(bobot * 100)}%"

/**
 * `yyyy-MM-dd` → tengah malam **UTC**, satuan yang dipakai `DatePickerState`.
 *
 * **Material3 menormalkan tiap pilihan tanggal ke UTC**, jadi mengoper atau
 * membaca milidetik lokal di sekitar picker menggeser tanggalnya satu hari
 * selama 00:00–06:59 WIB. Kesalahan itu sudah pernah terjadi di repo ini dan
 * uraiannya ada di `AttendanceScreen.OffFormSheet`; di papan eksekutif akibatnya
 * lebih sunyi — bukan galat, melainkan angka hari yang salah tanpa satu pun
 * tanda. Karena itu konversinya cuma boleh lewat pasangan fungsi ini.
 */
fun isoKeUtcMidnight(tanggal: String): Long? {
    // Diurai LEKSIKAL, bukan lewat `ISO.parse` lalu `Calendar` lokal.
    // `SimpleDateFormat` membekukan `TimeZone.getDefault()` saat dibuat,
    // sedangkan `Calendar.getInstance()` membaca zona yang HIDUP — jadi dua
    // zona bisa bercampur di dalam satu fungsi yang tugasnya justru menjamin
    // satu zona. Memecah "2026-08-23" jadi tiga bilangan tak bergantung zona
    // sama sekali, dan itu satu-satunya tafsir yang benar untuk tanggal polos.
    val bagian = tanggal.trim().split("-")
    if (bagian.size != 3) return null
    val tahun = bagian[0].toIntOrNull() ?: return null
    val bulan = bagian[1].toIntOrNull() ?: return null
    val hari = bagian[2].toIntOrNull() ?: return null
    if (bulan !in 1..12 || hari !in 1..31) return null
    val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        isLenient = false
        set(tahun, bulan - 1, hari)
    }
    // `isLenient = false` melempar pada tanggal yang tak ada (31 Februari);
    // itu ditangkap di sini, bukan diteruskan sebagai tanggal yang bergeser.
    return runCatching { cal.timeInMillis }.getOrNull()
}

/** Kebalikan [isoKeUtcMidnight]. */
fun utcMidnightKeIso(millis: Long): String = ISO_UTC.format(java.util.Date(millis))

private val ISO_UTC = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
    timeZone = TimeZone.getTimeZone("UTC")
}
