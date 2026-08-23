package com.krisoft.tridjayaelektronik.ui.aktivitas

import com.krisoft.tridjayaelektronik.BuildConfig
import com.krisoft.tridjayaelektronik.data.model.AktivitasItemDto

/**
 * Bagian murni layar Nilai Aktivitas (PIC raport) — tanpa Android/Compose,
 * supaya bisa diuji JUnit biasa. Pola sama [AktivitasPlan.kt].
 */

data class ReviewGate(val ok: Boolean, val alasan: String? = null)

/**
 * Putusan boleh dikirim atau tidak. Tolak WAJIB berkomentar: server menyimpan
 * skor 0 untuk baris yang ditolak, dan tanpa komentar karyawan cuma melihat
 * nilai nol tanpa tahu apa yang harus diperbaiki.
 */
internal fun bolehSimpanReview(status: String, komentar: String?): ReviewGate = when {
    status !in setOf("approved", "rejected") -> ReviewGate(false, "Status penilaian tidak dikenal.")
    status == "rejected" && komentar.isNullOrBlank() -> ReviewGate(false, "Alasan penolakan wajib diisi.")
    else -> ReviewGate(true)
}

/**
 * Skor yang AKAN tersimpan di server, dicerminkan di sini supaya angka yang
 * app tampilkan setelah menekan tombol sama dengan isi basis data.
 *
 * Aturan server (`raport/service.rs`): `rejected` → 0 (apa pun yang dikirim),
 * `pending` → tanpa skor, selainnya `score ?: 100` di-clamp 0..100.
 */
/** Nilai review DITENTUKAN SERVER (keputusan user 2026-08-15): setuju 100,
 *  tolak 0, tanpa nilai antara. Angka ini cuma cerminan optimistis untuk
 *  layar — server mengabaikan `score` kiriman klien dan menghitung sendiri
 *  dari `status`, jadi kalau keduanya pernah berbeda yang benar server. */
internal const val SKOR_SETUJU = 100
internal const val SKOR_TOLAK = 0

internal fun skorReview(status: String): Int? = when (status) {
    "pending" -> null
    "rejected" -> SKOR_TOLAK
    else -> SKOR_SETUJU
}

/**
 * `evidenceUrl` bisa berisi SATU url atau string JSON array (baris lama web
 * multi-bukti) — web mem-parsingnya (`parseEvidenceUrls` di `picAktivitasStore.ts`)
 * dan app harus sama, kalau tidak bukti multi-foto tampil sebagai satu teks
 * `["/uploads/…"]` yang tak bisa dimuat.
 *
 * Sengaja TANPA parser JSON sungguhan: isinya selalu array string datar, dan
 * memanggil kotlinx.serialization di sini berarti melempar `SerializationException`
 * untuk nilai lama yang bukan JSON sama sekali (mayoritas baris).
 */
internal fun parseEvidenceUrls(raw: String?): List<String> {
    val trimmed = raw?.trim().orEmpty()
    if (trimmed.isEmpty()) return emptyList()
    if (!trimmed.startsWith("[")) return listOf(trimmed)
    return trimmed.removePrefix("[").removeSuffix("]")
        .split(',')
        .map { it.trim().trim('"').trim() }
        .filter { it.isNotEmpty() }
}

/**
 * URL bukti yang bisa dimuat Coil. Bukti raport di-serve TERAUTENTIKASI (upload
 * privat, lihat `utils/protectedMedia.ts` di web) — nilai mentahnya
 * `/uploads/raport/<berkas>` bukan alamat yang bisa diambil langsung.
 *
 * Dipetakan ke `api/raport-harian/evidence/{berkas}` dan BUKAN ke alias gateway
 * `api/raport-files/{berkas}` yang dipakai web: alias itu menolak role `hrd`
 * (`RAPORT_FILE_ROLES`), padahal `hrd` termasuk penilai — lewat jalur ini ia
 * ikut lolos (`LIST_ROLES`). Pemanggil tetap wajib menyertakan header
 * `Authorization` (lihat `AuthedImage` di layar).
 */
internal fun evidenceImageUrl(
    raw: String?,
    baseUrl: String = BuildConfig.API_BASE_URL,
): String? {
    val trimmed = raw?.trim().orEmpty()
    if (trimmed.isEmpty()) return null
    if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return trimmed
    val berkas = trimmed.substringAfterLast('/')
    if (berkas.isBlank()) return null
    return baseUrl.trimEnd('/') + "/api/raport-harian/evidence/" + berkas
}

/** `true` = buktinya video, jadi tak boleh dirender sebagai gambar. */
internal fun buktiVideo(item: AktivitasItemDto): Boolean =
    item.mode.equals("video", ignoreCase = true)

/**
 * Baris dikelompokkan per karyawan supaya PIC menilai satu orang sekaligus —
 * urutan karyawan mengikuti kemunculan pertamanya dari server (sudah terurut
 * waktu kirim), dan aktivitas di dalam tiap grup naik menurut `jobdeskIndex`
 * (nama field di kabel, ejaan lama sengaja dipertahankan).
 */
internal fun grupPerKaryawan(items: List<AktivitasItemDto>): List<GrupAktivitas> =
    items.groupBy { it.employeeId }
        .map { (id, baris) ->
            GrupAktivitas(
                employeeId = id,
                nama = baris.firstOrNull { it.employeeName.isNotBlank() }?.employeeName
                    ?: "(tanpa nama)",
                divisi = baris.firstOrNull { it.divisiName.isNotBlank() }?.divisiName.orEmpty(),
                cabang = baris.firstOrNull { it.cabang.isNotBlank() }?.cabang.orEmpty(),
                baris = baris.sortedBy { it.jobdeskIndex },
            )
        }

data class GrupAktivitas(
    val employeeId: String,
    val nama: String,
    val divisi: String,
    val cabang: String,
    val baris: List<AktivitasItemDto>,
)

/**
 * Tujuh hari terakhir berakhir di [hariIni] (`yyyy-MM-dd`), TERTUA dulu — sama
 * bentuk dengan `getLast7Days()` web.
 *
 * Penggeser harinya WAJIB [geser] (`KlasemenStandings.shiftDays`, di atas
 * `Calendar`): `java.time` haram di `app/src/main` (minSdk 24 tanpa
 * desugaring) dan aritmetika tanggal buatan sendiri pecah di batas bulan.
 */
internal fun tujuhHariTerakhir(hariIni: String, geser: (String, Int) -> String): List<String> =
    (6 downTo 0).map { geser(hariIni, -it) }

/**
 * Lencana angka per hari: berapa baris yang MASIH menunggu penilaian.
 *
 * Sengaja menyaring `reviewStatus == "pending"` lagi walau permintaannya sudah
 * `status=pending`: fungsi ini juga dipakai atas daftar yang datang dari filter
 * lain, dan lencana "menunggu" yang diam-diam menghitung baris tersetujui
 * menyuruh PIC mengerjakan pekerjaan yang sudah selesai.
 *
 * Hari tanpa baris TIDAK diberi entri (bukan `0`) — pemanggil membedakan
 * "kosong" dari "tidak ada angkanya", dan lencana nol yang dirender sebagai
 * titik merah adalah kebalikan dari gunanya.
 */
internal fun hitungPendingPerHari(items: List<AktivitasItemDto>): Map<String, Int> =
    items.asSequence()
        .filter { it.reviewStatus == "pending" && it.tanggal.isNotBlank() }
        .groupingBy { it.tanggal }
        .eachCount()

/**
 * Teks chip satu hari: "Hari ini" / "Kemarin" / `dd/MM`.
 *
 * Murni potong-string, TANPA satu pun pustaka tanggal. Bukan kemalasan:
 * `java.time` haram di `app/src/main` (minSdk 24 tanpa desugaring), dan nama
 * hari lewat `SimpleDateFormat` bergantung locale ROM — di HP berlocale
 * en-US chip-nya akan berbunyi "Mon"/"Tue" tanpa satu pun error, sementara
 * seluruh layar berbahasa Indonesia.
 */
internal fun labelChipHari(iso: String, hariIni: String, kemarin: String): String = when {
    iso == hariIni -> "Hari ini"
    iso == kemarin -> "Kemarin"
    else -> {
        val bagian = iso.split('-')
        if (bagian.size == 3) "${bagian[2]}/${bagian[1]}" else iso
    }
}

/**
 * `true` = server memotong daftarnya di `limit`, jadi angka lencana adalah
 * BATAS BAWAH, bukan hitungan penuh.
 *
 * Dipisahkan supaya layar bisa mengatakannya. Angka yang lebih kecil dari
 * kenyataan membuat PIC mengira pekerjaannya habis — kelas kegagalan yang sama
 * dengan baris "termuat dari total" di daftar utama, dan di sana pun tak
 * disembunyikan.
 */
internal fun lencanaTerpotong(total: Int, termuat: Int): Boolean = total > termuat

/** Label status untuk chip di kartu — sama istilah dengan layar karyawan. */
internal fun labelStatusReview(status: String): String = when (status) {
    "approved" -> "Disetujui"
    "rejected" -> "Ditolak"
    else -> "Menunggu"
}

/**
 * Kalimat lencana "bukti daur-ulang" untuk SATU duplikat.
 *
 * Ditarik keluar dari `@Composable` dengan sengaja: selama ia hidup di dalam
 * fungsi UI, 718 test modul ini hijau tanpa menyentuh satu baris pun aturannya —
 * dan yang diputuskan di sini adalah apakah seorang karyawan disebut menyalin
 * milik ORANG LAIN atau milik dirinya sendiri.
 *
 * Aturannya:
 * - `asliKaryawanId` kosong  -> "sebelumnya" (server MENYENSOR identitas untuk
 *   penilai berbatas cabang; menuduh dengan nama yang sengaja dicabut adalah
 *   kebocoran yang sama lewat pintu lain).
 * - id sama dengan pemilik baris -> "sebelumnya" (unggahan dirinya sendiri).
 * - id berbeda -> nama + "(karyawan lain)".
 *
 * Tanggal selalu ikut bila ada: tuduhan tanpa penunjuk tak bisa diperiksa
 * sendiri oleh yang menerimanya.
 */
internal fun kalimatDuplikatBukti(
    asliKaryawanId: String,
    asliKaryawanNama: String,
    asliDiunggahAt: String,
    asliDisetujui: Boolean,
    pemilikBarisId: String,
): String = buildString {
    append("Bukti sama dengan unggahan ")
    if (asliKaryawanId.isNotBlank() && asliKaryawanId != pemilikBarisId) {
        append(asliKaryawanNama.ifBlank { "karyawan lain" })
        append(" (karyawan lain)")
    } else {
        append("sebelumnya")
    }
    asliDiunggahAt.takeIf { it.isNotBlank() }?.let { append(" · ${it.replace('T', ' ')}") }
    if (asliDisetujui) append(" · sudah disetujui")
}
