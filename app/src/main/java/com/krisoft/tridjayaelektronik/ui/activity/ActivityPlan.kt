package com.krisoft.tridjayaelektronik.ui.activity

import com.krisoft.tridjayaelektronik.data.model.ProspekTargetDto
import com.krisoft.tridjayaelektronik.data.model.parseTimestampMillis

/**
 * Penyusun tampilan layar Activity — SENGAJA fungsi murni tanpa Android/Compose
 * supaya bisa diuji JUnit biasa (repo ini tak punya mockk / coroutines-test).
 * `ActivityViewModel` hanya melakukan IO lalu menyerahkan hasilnya ke sini.
 */
data class ActivityCard(
    val item: ActivityItem,
    /** `null` = belum/ gagal dimuat — BUKAN nol. */
    val count: Int?,
    val failed: Boolean,
    /**
     * Baris pengganti subtitle, dirender MENONJOL (warna error) — dipakai kartu
     * yang angkanya perlu dipecah jadi "biasa vs mendesak" (SPK Gantung:
     * "2 lewat tenggat 24 jam"). `null` = pakai subtitle registri seperti biasa.
     */
    val alert: String? = null,
)

data class DailyTask(
    val item: ActivityItem,
    val done: Boolean,
    /** Teks kanan kartu: jam absen, "2 lead hari ini", "SEGERA", "belum". */
    val detail: String,
    /**
     * true = sumbernya GAGAL dimuat (jaringan/API), BUKAN "belum dikerjakan"
     * (I3 audit 2026-07-28). Dikecualikan dari penyebut [dailyProgressLabel] —
     * kegagalan jaringan bukan salah user, jangan menghukum progresnya.
     */
    val loadFailed: Boolean = false,
    /**
     * true = server menyatakan tugas ini TIDAK WAJIB hari ini (hari libur,
     * belum check-in, peran yang dibebaskan, saklar mati). Kartunya tetap
     * dirender supaya bisa dibuka — dulu ia disembunyikan sama sekali dan
     * karyawan melapor "menunya hilang" (2026-08-02, akun uji 11111111 pada
     * hari Minggu). Dikecualikan dari penyebut [dailyProgressLabel] dengan
     * alasan yang sama seperti [loadFailed]: yang tak dituntut hari ini tak
     * boleh membuat progres harian terlihat gagal.
     */
    val opsional: Boolean = false,
)

/**
 * Kartu antrian: yang berangka besar di atas, yang nol tetap tampil (redup)
 * supaya "semua beres" terbaca dan menu tak terasa hilang.
 *
 * PENGECUALIAN: dua kartu "pekerjaan yang ditugaskan ke saya" — Tugas Antar
 * ([ActivitySource.DLV_AS_DRIVER]) dan Tugas Tarik Unit
 * ([ActivitySource.HS_TUGAS_DRIVER]) — disembunyikan saat kosong bagi
 * non-driver (spec §6). Keduanya bergerbang `spk.pipeline` yang meloloskan
 * hampir semua orang, jadi tanpa penyempit ini kartunya jadi menu permanen
 * bagi orang yang tak pernah ditugaskan apa pun.
 */
internal fun buildQueueCards(
    items: List<ActivityItem>,
    counts: Map<ActivitySource, Int?>,
    failed: Set<ActivitySource>,
    effectiveRoles: Set<String>,
    /** Baris [ActivityCard.alert] per sumber — lihat [spkGantungAlert]. */
    alerts: Map<ActivitySource, String> = emptyMap(),
    /**
     * Vonis `bolehLihat` dari `GET /kupon-gebyar/meta`. `null` = belum/gagal
     * diketahui — lihat [kuponGebyarCardVisible] untuk kenapa ketiga keadaannya
     * berbeda dan kenapa yang `null` justru TAMPIL.
     *
     * Default `null` DISENGAJA dan arahnya kebalikan dari [akunUji]: pemanggil
     * yang lupa mengopernya menampilkan kartu yang bertanda "gagal muat", bukan
     * menghilangkannya diam-diam dari cabang yang berhak.
     */
    kuponGebyarBoleh: Boolean? = null,
): List<ActivityCard> = items
    .filter { it.kind == ActivityKind.ANTRIAN }
    .map { item ->
        val gagal = item.source in failed
        ActivityCard(
            item = item,
            count = if (gagal) null else counts[item.source],
            failed = gagal,
            // Gagal muat: barisnya sudah dipakai pesan "ketuk untuk coba lagi".
            alert = if (gagal) null else alerts[item.source],
        )
    }
    .filter { card ->
        when (card.item.source) {
            // DUA kartu "pekerjaan yang ditugaskan ke saya", bukan satu.
            // Keduanya bergerbang longgar DENGAN SENGAJA (`spk.pipeline`):
            // kepemilikannya ditentukan SERVER lewat kolom penugasan
            // (`assigned_driver_id` / `tarik_driver_id`), bukan daftar role —
            // jadi yang menyempitkannya memang harus angka, bukan gerbang.
            //
            // `HS_TUGAS_DRIVER` sempat TIDAK ada di sini, dan itu membuat
            // janji di registri ("angkanya sendiri yang menyembunyikan kartu
            // ini") tak pernah ditepati: kartu "Tugas Tarik Unit" tampil untuk
            // SETIAP akun yang lolos `spk.pipeline` — yaitu semua kecuali
            // `ai-engineer` — selamanya, walau ia tak pernah ditugaskan sekali
            // pun. Gerbang longgar tanpa penyempit adalah gerbang terbuka.
            ActivitySource.DLV_AS_DRIVER, ActivitySource.HS_TUGAS_DRIVER ->
                card.failed || driverCardVisible(card.count, effectiveRoles)
            // Gerbang CABANG, bukan gerbang angka: kartu ini TETAP tampil saat
            // sisanya nol ("semua undangan sudah dikirim" adalah kabar yang
            // berguna), dan hanya hilang kalau server memvonis cabangnya di luar
            // program. `card.failed` tak perlu disebut — vonis `null` sudah
            // dijawab `true` oleh fungsinya.
            ActivitySource.KUPON_GEBYAR_SISA -> kuponGebyarCardVisible(kuponGebyarBoleh)
            else -> true
        }
    }
    .sortedByDescending { it.count ?: -1 }

// ── Kartu "SPK Gantung" (antrian konfirmasi pembayaran kasir) ───────────────

/** Ambang "lewat tenggat" — bukan lagi syarat MUNCUL, cuma penanda mendesak. */
internal const val GANTUNG_TENGGAT_MS = 24 * 60 * 60 * 1000L

/**
 * [total] = SEMUA unit terkirim yang pembayarannya belum dikonfirmasi;
 * [lewatTenggat] = bagian yang umurnya sudah melewati [GANTUNG_TENGGAT_MS].
 */
internal data class SpkGantungRingkas(val total: Int, val lewatTenggat: Int)

/**
 * Timestamp `deliveredAt` kontrak delivery (`YYYY-MM-DD HH:MM:SS` UTC, kadang
 * bersufiks `Z`) → epoch millis. Spasi dinormalkan jadi `T` lalu diserahkan ke
 * [parseIsoUtcMillis] (SimpleDateFormat).
 *
 * SENGAJA bukan `java.time.LocalDateTime.parse`, yang dipakai versi sebelum
 * 2026-07-29: modul ini minSdk 24 TANPA `coreLibraryDesugaring` (dicek di
 * `app/build.gradle.kts`), jadi di Android 7.0/7.1 baris itu melempar
 * `NoClassDefFoundError` — turunan `Error`, yang tetap tertangkap
 * `runCatching` (menangkap `Throwable`), sehingga kegagalannya SENYAP dan
 * hitungannya nol selamanya di HP tersebut.
 */
internal fun deliveredAtUtcMillis(raw: String?): Long? =
    parseTimestampMillis(raw?.trim()?.takeIf { it.isNotEmpty() }?.replace(' ', 'T'))

/**
 * Aturan hitung kartu SPK Gantung.
 *
 * Angka kartu = JUMLAH SELURUH baris, bukan hanya yang lewat 24 jam. Menyaring
 * lebih dulu (perilaku sebelum 2026-07-29) membuat kartunya nol sepanjang hari
 * pertama — persis yang terjadi setelah data SPK produksi dihapus: semua SPK
 * baru berumur < 24 jam, kasir tak pernah tahu ada yang menunggu.
 *
 * Umurnya tidak dibuang, cuma turun pangkat jadi [SpkGantungRingkas.lewatTenggat]
 * (dirender [spkGantungAlert]) — itu yang membedakan mendesak dari biasa.
 * Baris yang `deliveredAt`-nya kosong/tak terbaca tetap DIHITUNG di [total]
 * (pekerjaannya nyata) tapi tak pernah dituduh lewat tenggat.
 */
internal fun spkGantungRingkas(
    deliveredAt: List<String?>,
    nowMillis: Long = System.currentTimeMillis(),
    tenggatMs: Long = GANTUNG_TENGGAT_MS,
): SpkGantungRingkas = SpkGantungRingkas(
    total = deliveredAt.size,
    lewatTenggat = deliveredAt.count { raw ->
        deliveredAtUtcMillis(raw)?.let { nowMillis - it > tenggatMs } ?: false
    },
)

/** `null` = tak ada yang lewat tenggat → kartu pakai subtitle biasa. */
internal fun spkGantungAlert(ringkas: SpkGantungRingkas): String? =
    if (ringkas.lewatTenggat > 0) "${ringkas.lewatTenggat} lewat tenggat 24 jam" else null

/**
 * Jam dari timestamp kontrak absensi (`YYYY-MM-DD HH:MM:SS`, bukan ISO "T").
 * Asumsi: backend absensi SELALU mengirim format penuh itu (posisi 11-16 tetap
 * "HH:MM") — JANGAN pakai ulang parser ini untuk timestamp dari sumber lain.
 */
private fun jam(timestamp: String?): String? =
    timestamp?.takeIf { it.length >= 16 }?.substring(11, 16)

internal fun buildDailyTasks(
    items: List<ActivityItem>,
    checkInAt: String?,
    checkOutAt: String?,
    leadsToday: Int,
    /** true = panggilan absensi hari ini gagal (jaringan/API) — [checkInAt]/
     *  [checkOutAt] di atas TIDAK bisa dipercaya (bukan benar-benar "belum"). */
    absensiFailed: Boolean = false,
    /** Jumlah aktivitas raport yang sudah dikirim hari ini. */
    aktivitasToday: Int = 0,
    /** true = panggilan raport hari ini gagal — sama alasannya dgn [absensiFailed]. */
    aktivitasFailed: Boolean = false,
    /**
     * Jumlah aktivitas yang SEHARUSNYA diisi user hari ini (dari master aktivitas
     * yang cocok dengan `divisi`-nya). `null` = TIDAK DIKETAHUI — lihat
     * [aktivitasDetail] kenapa itu bukan kasus pinggiran dan kenapa 0 tak
     * boleh dipakai sebagai sentinel.
     */
    aktivitasExpected: Int? = null,
    /**
     * Target prospek harian dari server (`GET /prospek-harian/my-target`).
     * `null` = TIDAK DIKETAHUI (panggilan gagal / offline) → kartu jatuh ke
     * perilaku lama, JANGAN divonis belum selesai. Lihat [prospekTaskDetail].
     */
    prospekTarget: ProspekTargetDto? = null,
): List<DailyTask> = items
    .filter { it.kind == ActivityKind.TUGAS_HARIAN }
    // Absen pulang tak relevan sebelum check-in — menampilkannya sejak pagi
    // membuat progres harian selalu terlihat gagal. Gagal-muat dikecualikan
    // dari aturan ini (di bawah): kita tak TAHU status check-in-nya, jadi
    // jangan diam-diam menyembunyikan tugas gara-gara ketidaktahuan itu.
    .filterNot { it.id == "absen_pulang" && checkInAt.isNullOrBlank() && !absensiFailed }
    .map { item ->
        when {
            absensiFailed && (item.id == "absen_masuk" || item.id == "absen_pulang") ->
                // I3 audit 2026-07-28: sebelumnya jatuh ke detail "belum" — tak
                // bisa dibedakan dari benar-benar belum absen, jadi kartu yang
                // bisa ditap MENDORONG USER YANG SUDAH CHECK-IN untuk absen
                // lagi. "gagal muat" jujur soal ketidaktahuan kita.
                DailyTask(item, done = false, detail = "gagal muat", loadFailed = true)
            item.id == "absen_masuk" -> DailyTask(item, !checkInAt.isNullOrBlank(), jam(checkInAt) ?: "belum")
            item.id == "absen_pulang" -> DailyTask(item, !checkOutAt.isNullOrBlank(), jam(checkOutAt) ?: "belum")
            item.id == "prospek" -> {
                // Angka SERVER menang mutlak atas hitungan cache lokal: server
                // menghitung per `karyawan_id` (penerima penugasan), klien cuma
                // tahu `createdBy` — kalau klien tetap menghitung sendiri,
                // angkanya berselisih dgn raport/summary/KPI dan user melapor
                // "sudah input tapi tak tercentang".
                val srv = prospekTarget?.takeIf { it.target > 0 }
                DailyTask(
                    item,
                    // Server yang memutuskan tercapai/tidak; tanpa data →
                    // aturan lama "ada minimal satu lead hari ini".
                    done = srv?.tercapai ?: (leadsToday > 0),
                    detail = prospekTaskDetail(srv, leadsToday),
                )
            }
            aktivitasFailed && item.id == "aktivitas" ->
                DailyTask(item, done = false, detail = "gagal muat", loadFailed = true)
            item.id == "aktivitas" -> DailyTask(
                item,
                // "Selesai" TETAP = sudah mengirim minimal satu aktivitas, SENGAJA
                // tidak dinaikkan jadi "semua aktivitas terisi" walau penyebutnya
                // kini kadang diketahui: penyebut itu hanya ada untuk sebagian
                // karyawan, jadi menaikkannya membuat centang (dan penyebut
                // [dailyProgressLabel]) berarti dua hal berbeda tergantung apakah
                // divisi orangnya kebetulan ada di master aktivitas.
                aktivitasToday > 0,
                aktivitasDetail(aktivitasToday, aktivitasExpected),
            )
            else -> DailyTask(item, done = false, detail = if (item.comingSoon) "SEGERA" else "belum")
        }
    }

/**
 * Status bukti chat yang berarti tugas hari ini beres — sama dengan yang membuka
 * `checkoutTerbuka` di server (`auto_approved` = ambang tercapai tanpa perlu
 * diperiksa manusia).
 */

/**
 * Teks kanan kartu "Input Aktivitas": "3/7 aktivitas" kalau penyebutnya benar-
 * benar diketahui, teks lama kalau tidak.
 *
 * [expected] `null` (master aktivitas gagal dimuat, atau divisi user tak cocok
 * satu pun posisi) DAN 0 sama-sama berarti TIDAK DIKETAHUI. Ini bukan kasus
 * pinggiran: mayoritas karyawan aktif divisinya memang tak ada di master
 * aktivitas (`matchAktivitasPosition` balikin `null`), jadi merendernya sebagai
 * "0/0" akan memvonis mereka belum mengerjakan sesuatu yang tak pernah bisa
 * dihitung. Karena itu penyebutnya `Int?`, bukan `Int` ber-sentinel 0.
 */
internal fun aktivitasDetail(terkirim: Int, expected: Int?): String = when {
    expected != null && expected > 0 -> "$terkirim/$expected aktivitas"
    terkirim > 0 -> "$terkirim aktivitas terkirim"
    else -> "belum"
}

/**
 * Teks kanan kartu "Input Prospek": "3/20 prospek" kalau targetnya diketahui,
 * teks lama kalau tidak.
 *
 * [server] `null` (panggilan gagal / offline) DAN `target = 0` (setelan tak
 * terbaca / dimatikan) sama-sama berarti TIDAK DIKETAHUI: merendernya "3/0"
 * memberi penyebut palsu dan membuat kartunya tak pernah bisa tercentang.
 * Fallback-nya hitungan cache lokal — kurang tepat (lihat [ProspekTargetDto])
 * tapi tetap benar saat offline, dan tak pernah memvonis "belum".
 */
internal fun prospekTaskDetail(server: ProspekTargetDto?, leadsToday: Int): String = when {
    server != null && server.target > 0 -> "${server.aktual}/${server.target} prospek"
    leadsToday > 0 -> "$leadsToday lead hari ini"
    else -> "belum ada"
}

/**
 * `n/total` — item `comingSoon` (belum bisa dikerjakan) DAN [DailyTask.loadFailed]
 * (bukan salah user, kegagalan jaringan) tak ikut jadi penyebut.
 */
internal fun dailyProgressLabel(tasks: List<DailyTask>): String {
    val nyata = tasks.filterNot { it.item.comingSoon || it.loadFailed || it.opsional }
    return "${nyata.count { it.done }}/${nyata.size}"
}

/**
 * Lead yang dibuat hari ini oleh [userId]. Baris cache lama tanpa `createdBy`
 * ikut dihitung — untuk `karyawan` cache-nya memang sudah di-scope server
 * (`karyawan_scope`), jadi lebih baik menghitung daripada diam-diam nol.
 *
 * [leads] = pasangan (`createdAt`, `createdBy`).
 */
internal fun leadsCreatedTodayBy(
    leads: List<Pair<String, String?>>,
    userId: String?,
    todayIso: String,
): Int = leads.count { (createdAt, createdBy) ->
    createdAt.startsWith(todayIso) && (userId == null || createdBy == null || createdBy == userId)
}
