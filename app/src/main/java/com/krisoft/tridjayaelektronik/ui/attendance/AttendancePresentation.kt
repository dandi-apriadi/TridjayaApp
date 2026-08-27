package com.krisoft.tridjayaelektronik.ui.attendance

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.krisoft.tridjayaelektronik.data.model.AbsensiRecordDto
import com.krisoft.tridjayaelektronik.data.model.AbsensiTodayDto
import com.krisoft.tridjayaelektronik.data.model.OffRequestDto
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Status review absensi dari backend (`valid` | `pending_review` | `approved` | `rejected`). */
enum class AbsensiStatus(
    val key: String,
    val label: String,
    val color: Color,
    val icon: ImageVector
) {
    VALID("valid", "Tercatat", Color(0xFF12B76A), Icons.Rounded.CheckCircle),
    PENDING_REVIEW("pending_review", "Perlu Review", Color(0xFFB5670C), Icons.Rounded.HourglassTop),
    APPROVED("approved", "Disetujui", Color(0xFF12B76A), Icons.Rounded.Verified),
    REJECTED("rejected", "Ditolak", Color(0xFFF04438), Icons.Rounded.Cancel);

    companion object {
        fun from(key: String?): AbsensiStatus =
            entries.firstOrNull { it.key.equals(key?.trim(), ignoreCase = true) } ?: VALID
    }
}

/** Format jarak singkat: "18 m" atau "1,2 km". */
fun formatDistance(meters: Long): String =
    if (meters < 1000) "$meters m"
    else String.format(Locale("in", "ID"), "%.1f km", meters / 1000.0)

private val dayFormatter = SimpleDateFormat("EEEE, d MMMM yyyy", Locale("in", "ID"))
private val shortDayFormatter = SimpleDateFormat("EEE, d MMM", Locale("in", "ID"))
private val isoDateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
private val dbDateTimeFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

/** "yyyy-MM-dd" → "Sabtu, 18 Juli 2026" (fallback: string asli). */
fun formatAttendanceDate(iso: String): String =
    runCatching { dayFormatter.format(isoDateFormatter.parse(iso)!!) }.getOrDefault(iso)

/** "yyyy-MM-dd" → "Sab, 18 Jul". */
fun formatAttendanceDateShort(iso: String): String =
    runCatching { shortDayFormatter.format(isoDateFormatter.parse(iso)!!) }.getOrDefault(iso)

/** "yyyy-MM-dd HH:mm:ss" → "HH:mm" (fallback: potong 11..16, atau string asli). */
fun formatPunchTime(datetime: String?): String {
    if (datetime.isNullOrBlank()) return "-"
    return runCatching {
        SimpleDateFormat("HH:mm", Locale.US).format(dbDateTimeFormatter.parse(datetime)!!)
    }.getOrElse {
        if (datetime.length >= 16) datetime.substring(11, 16) else datetime
    }
}

/** Kategori izin/OFF — samakan dgn web `OFF_KATEGORI_LABEL` (izin|sakit|cuti|off). */
enum class OffKategori(val key: String, val label: String, val color: Color) {
    IZIN("izin", "Izin", Color(0xFF1565C0)),
    SAKIT("sakit", "Sakit", Color(0xFFB5670C)),
    CUTI("cuti", "Cuti", Color(0xFF6941C6)),
    OFF("off", "Off", Color(0xFF667085));

    companion object {
        /** Fallback ke IZIN untuk nilai tak dikenal — samakan dgn web (`... : 'izin'`). */
        fun from(key: String?): OffKategori =
            entries.firstOrNull { it.key.equals(key?.trim(), ignoreCase = true) } ?: IZIN
    }
}

/** Status harian rekap — sepadan dgn `RekapStatus` di web `AbsensiPage`. */
enum class RekapStatus(val label: String, val color: Color) {
    HADIR("Hadir", Color(0xFF12B76A)),
    IZIN("Izin", Color(0xFF1565C0)),
    SAKIT("Sakit", Color(0xFFB5670C)),
    CUTI("Cuti", Color(0xFF6941C6)),
    OFF("Off", Color(0xFF667085)),
    BELUM_ABSEN("Belum Absen", Color(0xFFF04438))
}

private fun offToRekap(kategori: String): RekapStatus = when (OffKategori.from(kategori)) {
    OffKategori.IZIN -> RekapStatus.IZIN
    OffKategori.SAKIT -> RekapStatus.SAKIT
    OffKategori.CUTI -> RekapStatus.CUTI
    OffKategori.OFF -> RekapStatus.OFF
}

/** "yyyy-MM-dd" LOKAL hari ini (tz device = tz server Indonesia WIB/WITA). */
fun attendanceToday(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

/**
 * Tanggal hari ini menurut jam DEVICE, dinyatakan sebagai tengah malam UTC.
 *
 * Satuan itulah yang dipakai `DatePickerState` Material3: tiap pilihan user
 * dinormalkan ke tengah malam UTC, jadi nilai awalnya harus bicara satuan yang
 * sama. Mengoper `System.currentTimeMillis()` mentah membuat picker terbuka di
 * tanggal UTC-nya — antara 00:00–06:59 WIB itu tanggal KEMARIN.
 *
 * `nowMillis`/`zonaDevice` bisa dioper demi test; produksi memakai default.
 */
fun hariIniSebagaiUtcMidnight(
    nowMillis: Long = System.currentTimeMillis(),
    zonaDevice: TimeZone = TimeZone.getDefault(),
): Long {
    val lokal = Calendar.getInstance(zonaDevice).apply { timeInMillis = nowMillis }
    return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(lokal.get(Calendar.YEAR), lokal.get(Calendar.MONTH), lokal.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis
}

/**
 * Tanggal 1 bulan berjalan s/d hari ini ("yyyy-MM-dd", lokal). Sengaja komponen
 * tanggal lokal (bukan UTC) supaya cocok dgn `tanggal` record server — sama dgn
 * perbaikan geser-UTC di web `dateRangeKeys`.
 *
 * [serverToday] (format "yyyy-MM-dd", dari `AbsensiTodayDto.tanggal`) dipakai
 * kalau ada — SERVER yang menentukan "hari ini", bukan jam device. Ditemukan
 * 2026-08-27: jam/zona device yang salah (kasus nyata — HP di zona WITA
 * terbaca WIB, HP lain jamnya melenceng) membuat rentang tanggal yang
 * dihasilkan di sini tak pernah cocok dengan `tanggal` di record server sama
 * sekali, sehingga rekap bulanan menghitung HAMPIR SEMUA hari sebagai "Belum
 * Absen" walau baris absensinya lengkap di server — dan tak ada satu pun
 * error yang bisa menandainya, karena secara teknis fetch-nya SUKSES, cuma
 * kuncinya (tanggal) yang tak pernah ketemu. Fallback ke jam device HANYA
 * kalau `serverToday` belum ada (`todayDto` belum sempat termuat) atau
 * bentuknya rusak.
 */
fun currentMonthDays(serverToday: String? = null): List<String> {
    val cal = Calendar.getInstance()
    var year = cal.get(Calendar.YEAR)
    var month = cal.get(Calendar.MONTH)
    var today = cal.get(Calendar.DAY_OF_MONTH)
    val bagian = serverToday?.split("-")
    if (bagian?.size == 3) {
        val y = bagian[0].toIntOrNull()
        val m = bagian[1].toIntOrNull()
        val d = bagian[2].toIntOrNull()
        if (y != null && m in 1..12 && d != null && d in 1..31) {
            year = y
            month = m!! - 1
            today = d
        }
    }
    val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    return (1..today).map { d ->
        fmt.format(Calendar.getInstance().apply { clear(); set(year, month, d) }.time)
    }
}

/**
 * Rekap kehadiran bulan berjalan (tgl 1 s/d hari ini) digabung izin/OFF — mengikuti
 * logika `RekapKehadiranTab` web: tiap hari = HADIR bila ada check-in, else kategori
 * off disetujui, else BELUM_ABSEN. (Seperti web, hari libur tanpa absen/izin ikut
 * terhitung "belum absen" karena tak ada jadwal kerja per-hari.)
 */
data class AttendanceRekap(
    val counts: Map<RekapStatus, Int> = RekapStatus.entries.associateWith { 0 },
    val totalHari: Int = 0
) {
    fun count(status: RekapStatus): Int = counts[status] ?: 0
}

fun buildRekap(
    history: List<AbsensiRecordDto>,
    offRequests: List<OffRequestDto>,
    /** `AbsensiTodayDto.tanggal` — lihat catatan [currentMonthDays]. */
    serverToday: String? = null,
    days: List<String> = currentMonthDays(serverToday)
): AttendanceRekap {
    val attByDate = history.associateBy { it.tanggal }
    val offByDate = offRequests
        .filter { it.status.equals("approved", ignoreCase = true) }
        .associateBy { it.tanggal }
    val counts = RekapStatus.entries.associateWith { 0 }.toMutableMap()
    for (day in days) {
        val status = when {
            attByDate[day]?.checkInAt != null -> RekapStatus.HADIR
            offByDate[day] != null -> offToRekap(offByDate.getValue(day).kategori)
            else -> RekapStatus.BELUM_ABSEN
        }
        counts[status] = (counts[status] ?: 0) + 1
    }
    return AttendanceRekap(counts, days.size)
}

/**
 * Entri riwayat gabungan: absensi ATAU hari izin/OFF disetujui (yang belum punya
 * record absensi). Membuat riwayat mobile setara "Detail Kehadiran" web yang
 * menampilkan izin/sakit/cuti/off, bukan cuma hari check-in.
 */
sealed interface TimelineEntry {
    val tanggal: String

    data class Attendance(val record: AbsensiRecordDto) : TimelineEntry {
        override val tanggal: String get() = record.tanggal
    }

    data class Off(val off: OffRequestDto) : TimelineEntry {
        override val tanggal: String get() = off.tanggal
    }
}

/**
 * Hasil gerbang tombol Absen Pulang: kelengkapan laporan aktivitas
 * (permintaan user 2026-08-17).
 *
 * [alasan] TIDAK berarti "tertutup". Selama peluncuran bertahap, saklar server
 * `absensi_gate_aktivitas` default MATI: tagihannya tetap dikirim sementara
 * [boleh] tetap `true`. Karena itu layar merender kartunya dari `alasan != null`,
 * BUKAN dari `!boleh` — kalau tidak, menyalakan/mematikan kunci ikut
 * menghapus tagihannya dari layar.
 */
data class GatePulang(val boleh: Boolean, val alasan: String? = null)

/**
 * Cermin gerbang server (`AbsensiService::pastikan_aktivitas_lengkap`).
 *
 * **FAIL-OPEN saat data tak ada.** [today] `null` = panggilan `/absensi/today`
 * gagal, offline, atau backend lama yang belum punya field ini — bukan berarti
 * aktivitasnya kurang. Server tetap penegak sebenarnya (ia yang menolak
 * check-out-nya); klien yang mengunci saat ragu akan memblokir absen pulang
 * SELURUH armada begitu satu endpoint bermasalah. Itu persis bentuk insiden
 * 2026-07-31, dan ia tak boleh lahir ulang dari cermin yang terlalu percaya diri.
 *
 * Kalimatnya datang APA ADANYA dari server — satu-satunya sumbernya di sana,
 * supaya teks di layar dan pesan error 400 saat menekan tombol tak berselisih.
 */
fun gateAbsenPulang(today: AbsensiTodayDto?): GatePulang {
    if (today == null) return GatePulang(true)
    return GatePulang(today.checkoutTerbuka, today.peringatanAktivitas?.takeIf { it.isNotBlank() })
}

/**
 * Hasil gate tombol Absen Masuk (geofence).
 *
 * Absen MASUK ditolak server kalau di luar area, absen PULANG tidak
 * (`check_out` sengaja tak dipagari supaya driver/sales yang masih di lapangan
 * sore hari tetap bisa pulang).
 */
data class GateMasuk(val boleh: Boolean, val alasan: String? = null)

/**
 * Jarak di mana "aku sudah di depan toko tapi tetap ditulis di luar area" masih
 * masuk akal, sehingga tersangkanya titik geofence yang meleset — bukan orangnya.
 * Radius cabang di produksi 200 m, jadi 500 m memberi ruang untuk simpangan GPS
 * di dalam gedung tanpa menyarankan hal itu kepada orang yang jaraknya kilometer.
 */
const val AMBANG_DUGAAN_TITIK_SALAH_M = 500

/**
 * Cermin gate server `AbsensiService::check_in` → `pastikan_di_area_toko`
 * (kinerja-service): absen MASUK di luar radius semua toko ditolak 400, tidak
 * tercatat sama sekali.
 *
 * **Kenapa ada.** Sebelum ini layar absensi menulis "absen perlu review" untuk
 * keadaan itu dan membiarkan tombol Check In hidup — kalimat yang menjanjikan
 * absennya tetap masuk, hanya butuh persetujuan. Yang terjadi sebaliknya: tak ada
 * baris yang lahir. Terukur di nginx produksi 4–15 Agustus 2026, **314 check-in
 * dijawab 400 dan seluruhnya penolakan geofence** (ukuran badan 152/153/154 byte
 * = varian pesan berjarak; nol yang "foto wajib" atau "titik toko belum diatur").
 * Orang di lapangan tak punya cara membedakannya dari aplikasi rusak.
 *
 * **[daftarCabangLengkap] menentukan apakah kita BOLEH mengunci.** Server menilai
 * terhadap SELURUH `absensi_cabang_config` (karyawan boleh absen di cabang
 * Tridjaya manapun), jadi vonis "di luar area" baru sepadan dengan vonis server
 * kalau app memegang daftar lengkapnya — yaitu saat `today` mengirim `geofences[]`.
 * Server lama hanya mengirim `geofence` tunggal (cabang sendiri); dari satu titik
 * itu "di luar" bisa berarti "sedang bertugas di cabang sebelah", dan mengunci
 * atas dasar itu MENGHENTIKAN orang yang sebenarnya berhak. Karena itu tanpa
 * daftar lengkap fungsi ini **fail-open** — server tetap penegak sebenarnya.
 *
 * [inArea] `null` (lokasi belum terbaca / belum ada geofence sama sekali) juga
 * fail-open, alasan yang sama.
 */
fun gateAbsenMasuk(
    inArea: Boolean?,
    daftarCabangLengkap: Boolean,
    namaCabangTerdekat: String?,
    jarakM: Int?
): GateMasuk {
    if (inArea != false) return GateMasuk(true)
    val cabang = namaCabangTerdekat?.trim()?.takeIf { it.isNotBlank() }
    val jarak = jarakM?.takeIf { it >= 0 }
    val sebutJarak = when {
        jarak != null && cabang != null -> "Kamu ${formatDistance(jarak.toLong())} dari $cabang"
        jarak != null -> "Kamu ${formatDistance(jarak.toLong())} dari toko terdekat"
        cabang != null -> "Toko terdekat $cabang"
        else -> "Kamu berada di luar area toko"
    }
    if (!daftarCabangLengkap) {
        // Tak boleh mengunci, tapi diam juga salah: orangnya berhak tahu titik
        // ini akan ditolak KALAU ia memang tak sedang di cabang lain.
        return GateMasuk(
            true,
            "$sebutJarak. Kalau kamu sedang bertugas di cabang lain, absen masuk tetap bisa; " +
                "kalau tidak, mendekatlah ke toko dulu."
        )
    }
    // Dugaan "titik cabangnya yang meleset" hanya masuk akal dari dekat. Menyodorkan
    // "minta admin memperbaiki titik" kepada orang yang jaraknya 2 km mengajari
    // seluruh cabang menyalahkan setelan untuk lokasi yang memang salah.
    val jalanKeluar = if (jarak != null && jarak <= AMBANG_DUGAAN_TITIK_SALAH_M) {
        " Mendekatlah ke toko lalu perbarui lokasi. Kalau kamu memang sudah di toko, " +
            "minta admin membetulkan titik lokasi cabang di halaman Absensi."
    } else {
        " Mendekatlah ke toko lalu perbarui lokasi."
    }
    return GateMasuk(false, "Absen masuk hanya bisa dari dalam area toko. $sebutJarak.$jalanKeluar")
}

fun buildTimeline(
    history: List<AbsensiRecordDto>,
    offRequests: List<OffRequestDto>
): List<TimelineEntry> {
    val attDates = history.map { it.tanggal }.toSet()
    val offEntries = offRequests
        .filter { it.status.equals("approved", ignoreCase = true) && it.tanggal !in attDates }
        .map { TimelineEntry.Off(it) }
    return (history.map { TimelineEntry.Attendance(it) } + offEntries)
        .sortedByDescending { it.tanggal }
}
