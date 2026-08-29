package com.krisoft.tridjayaelektronik.ui.aktivitas

import com.krisoft.tridjayaelektronik.data.model.AktivitasItemDto
import com.krisoft.tridjayaelektronik.data.model.AktivitasPositionDto
import com.krisoft.tridjayaelektronik.data.model.jumlahButirAktif
import com.krisoft.tridjayaelektronik.domain.sales.KlasemenStandings

/**
 * Bagian murni layar Riwayat Aktivitas (milik KARYAWAN sendiri) — tanpa
 * Android/Compose supaya bisa diuji JUnit biasa. Pola sama [AktivitasPlan.kt]
 * dan [AktivitasReviewPlan.kt].
 *
 * Layar ini READ-ONLY dan itu bukan kemalasan melainkan syarat kebenaran:
 * `POST /raport-harian` SENGAJA tidak pernah dikirimi field `tanggal` oleh app
 * (`AktivitasModels.kt` — jam HP yang salah membuat raport nyasar ke hari yang
 * bukan-bukan), jadi server selalu menulis ke HARI INI. Tombol kirim di layar
 * yang sedang memajang tanggal lampau karena itu akan menulis baris ke hari
 * yang BERBEDA dari yang dilihat orangnya, tanpa satu pun error. Menaruh
 * navigasi tanggal di layar Input Aktivitas berarti memasang jebakan itu; layar
 * terpisah tak punya tombol kirim sama sekali.
 */

/**
 * Cerminan `LIST_ROLES` (`kinerja-service/src/aktivitas_harian.rs:64`) — gerbang
 * `GET /raport-harian`, yaitu satu-satunya sumber layar riwayat.
 *
 * **Ia LEBIH SEMPIT dari gerbang kartu Input Aktivitas** (`AKTIVITAS_INPUT_ROLES
 * = ALL_LOGGED_IN`), dan selisih itu disengaja di backend: MENGIRIM laporan
 * login-only supaya auto-feed KPI tak putus, sedangkan MEMBACA daftar tetap
 * ber-role. Tanpa cerminan ini tombol "Lihat Riwayat" muncul untuk pemegang
 * role `sales`/`kasir`/`driver`/`admin-penjualan` yang kartunya memang terbuka,
 * lalu layarnya dijawab 403 — persis kelas "menu mati" yang dilarang aturan
 * repo (menu tampil, backend menolak).
 *
 * Dua ejaan `pic_raport`/`pic-raport` sama-sama ditulis karena backend memang
 * mengenali keduanya. Kalau `LIST_ROLES` berubah, daftar ini WAJIB ikut.
 *
 * `superadmin` ADA di sini walau TIDAK ada di `LIST_ROLES`, dan itu bukan
 * kelebihan: vocab wire gateway→service masih `"admin"` (shim
 * `legacy_wire_role`), sedangkan yang dibaca klien dari profil adalah ejaan
 * DB/JWT `"superadmin"`. Menulis `"admin"` saja akan menyembunyikan tombol dari
 * superadmin yang servernya justru menerima. Konvensi yang sama dipakai SEMUA
 * daftar role di `ActivityRegistry.kt` dan `capabilities.rs`.
 */
internal val AKTIVITAS_BACA_ROLES = setOf(
    "admin",
    "superadmin",
    "owner",
    "pic_raport",
    "pic-raport",
    "karyawan",
    "trainee",
    "manager",
)

/**
 * Boleh membuka layar riwayat? Dinilai dari role EFEKTIF (role utama + `roles` +
 * `divisi`), BUKAN role primary tunggal — akun multi-role yang primary-nya
 * `sales` tapi juga ber-role `karyawan` tetap berhak, dan gate yang cuma
 * membaca role utama akan menyembunyikan riwayatnya sendiri darinya.
 */
internal fun bolehLihatRiwayat(roleEfektif: Set<String>): Boolean =
    roleEfektif.any { it in AKTIVITAS_BACA_ROLES }

/** Perbandingan `yyyy-MM-dd` cukup leksikografis — polanya sama `OpnameJendela.kt`. */
internal fun bolehMajuTanggal(tanggal: String, hariIni: String): Boolean = tanggal < hariIni

/**
 * Geser [hari] hari dari [tanggal], TAPI tak pernah melewati [hariIni].
 *
 * Batas depannya wajib: baris raport hari esok mustahil ada, dan layar yang
 * membiarkan orang menyusurinya menampilkan "belum ada aktivitas" untuk tanggal
 * yang memang belum tiba — terbaca sebagai data hilang, bukan sebagai tanggal
 * yang salah. Tak ada batas belakang: raport lama memang boleh ditengok sejauh
 * apa pun, dan server yang memutuskan ada-tidaknya barisnya.
 */
internal fun geserTanggalRiwayat(tanggal: String, hari: Int, hariIni: String): String {
    val calon = KlasemenStandings.shiftDays(tanggal, hari)
    return if (calon > hariIni) hariIni else calon
}

/**
 * Ringkasan satu hari untuk kepala layar.
 *
 * [dinilai] sengaja DIPISAH dari [total]: baris yang masih `pending` belum punya
 * nilai, dan memasukkannya ke penyebut rata-rata membuat hari yang PIC-nya belum
 * sempat menilai terlihat seperti hari dengan kinerja buruk. Sama alasannya
 * dengan penyebut `filled_bobot` di mesin KPI.
 */
internal data class RingkasanRiwayat(
    val total: Int,
    val disetujui: Int,
    val ditolak: Int,
    val menunggu: Int,
    /** Baris yang SUDAH punya putusan (disetujui + ditolak). */
    val dinilai: Int,
    /** Rata-rata skor atas baris yang sudah dinilai; `null` kalau belum ada. */
    val rataSkor: Int?,
)

/**
 * PENYEBUT nilai harian — cerminan **`kpi::domain::raport_daily_nilai` (Rust)**,
 * yaitu `max(expected, maxIndex+1, cnt, 1) − pending`.
 *
 * **Yang dicerminkan SENGAJA backend, BUKAN web.** Web
 * (`summarizeAktivitasScore`) memakai rumus yang sama TANPA mengurangi baris
 * `pending`, dan itu penyimpangan yang SUDAH DIKETAHUI: sejak keputusan user
 * 2026-08-15 baris `pending` dikecualikan dari pembilang MAUPUN penyebut, dan
 * `docs/codebase-map/kpi-engine.md` mencatat cerminan web belum ikut berubah
 * dengan catatan tegas "arahnya belum diputus user, jangan diselaraskan
 * diam-diam". Menyamakan layar ini ke web berarti menyalin penyimpangan itu ke
 * kanal kedua — dan yang benar adalah angka yang MEMBAYAR.
 *
 * Tiap komponen menutup celah yang berbeda:
 * - [jumlahButirAktif] divisi penempatannya — tagihan sebenarnya, dan
 *   satu-satunya yang benar untuk orang yang mengisi 2 dari 8 butir. Inilah
 *   komponen yang sampai 2026-08-28 TIDAK ADA di app;
 * - indeks butir tertinggi + 1 — menyelamatkan baris yang divisinya sudah tak
 *   ada di master;
 * - jumlah baris — batas bawah;
 * - dikurangi jumlah `pending`: hari yang PIC-nya belum sempat menilai tak boleh
 *   terlihat seperti hari berkinerja buruk — vonis yang belum dijatuhkan siapa pun.
 *
 * `positions` kosong (master belum termuat / gagal) membuat komponen pertama 0,
 * jadi hasilnya jatuh ke perilaku LAMA. Itu disengaja: penyebut yang mengecil
 * karena data belum datang menampilkan nilai terlalu TINGGI — kabar baik palsu.
 */
internal fun penyebutRiwayat(
    items: List<AktivitasItemDto>,
    positions: List<AktivitasPositionDto>,
): Int {
    // Penempatan dibaca dari SAMPEL baris, bukan dari profil yang sedang login:
    // baris riwayat bisa lahir saat orangnya masih di penempatan lain, dan yang
    // benar adalah tagihan YANG BERLAKU SAAT ITU.
    val divisi = when (val d = cariDivisiDariPenempatan(items.firstOrNull()?.kpiPositionId, positions)) {
        is DivisiDariPenempatan.Ketemu -> d.divisi
        // Posisinya memang belum punya divisi aktivitas (produksi 2026-08-15:
        // teknisi, digital-team, crm, admin-gudang). TIDAK ditebak ke divisi
        // lain — penyebutnya jatuh ke batas bawah data nyata, sama seperti
        // backend.
        DivisiDariPenempatan.PosisiTanpaAktivitas -> null
        DivisiDariPenempatan.TanpaPenempatan -> null
    }
    val indeksTertinggi = items.maxOfOrNull { it.jobdeskIndex } ?: -1
    val slot = maxOf(jumlahButirAktif(divisi), indeksTertinggi + 1, items.size, 1)
    val pending = items.count { it.reviewStatus != "approved" && it.reviewStatus != "rejected" }
    return slot - pending
}

internal fun ringkasRiwayat(
    items: List<AktivitasItemDto>,
    positions: List<AktivitasPositionDto> = emptyList(),
): RingkasanRiwayat {
    val disetujui = items.count { it.reviewStatus == "approved" }
    val ditolak = items.count { it.reviewStatus == "rejected" }
    val menunggu = items.size - disetujui - ditolak
    val dinilai = disetujui + ditolak
    // Skor dibaca dari server apa adanya. `skorReview(status)` SENGAJA tidak
    // dipakai di sini: fungsi itu cerminan OPTIMISTIS untuk layar PIC tepat
    // setelah menekan tombol, sedangkan di sini yang benar adalah angka yang
    // sudah tersimpan — termasuk baris lama yang skornya bukan 0/100.
    //
    // Pembilang PERSIS `SUM(CASE WHEN review_status IN ('rejected','pending')
    // THEN 0 ELSE COALESCE(score, 0) END)` di `kpi/mysql.rs`:
    // - `rejected` -> 0 dan TETAP memakai slotnya (pekerjaan yang dinilai lalu
    //   gagal, beda dari yang belum dinilai);
    // - `approved` TANPA skor -> `COALESCE(...,0)` = 0, juga memakai slotnya.
    //   Versi sebelumnya membuangnya lewat `mapNotNull { score }` sehingga baris
    //   begitu menaikkan rata-rata; backend tak pernah memaafkannya, jadi layar
    //   ini dulu memajang angka yang LEBIH TINGGI dari yang dibayarkan.
    val dinilaiRows = items.filter { it.reviewStatus == "approved" || it.reviewStatus == "rejected" }
    val jumlahSkor = dinilaiRows.sumOf { if (it.reviewStatus == "rejected") 0 else (it.score ?: 0) }
    // Penyebut = TAGIHAN dikurangi yang belum dinilai, bukan jumlah baris yang
    // kebetulan sudah punya skor. Memakai `skor.size` (perilaku sampai
    // 2026-08-28) membuat hari yang cuma terisi 2 dari 8 butir tampil seolah
    // bernilai penuh.
    val penyebut = penyebutRiwayat(items, positions)
    return RingkasanRiwayat(
        total = items.size,
        disetujui = disetujui,
        ditolak = ditolak,
        menunggu = menunggu,
        dinilai = dinilai,
        rataSkor = if (dinilaiRows.isEmpty() || penyebut <= 0) null else jumlahSkor / penyebut,
    )
}

/** Urut naik menurut nomor aktivitas — `jobdeskIndex` NAMA KABEL, ejaan lama. */
internal fun urutRiwayat(items: List<AktivitasItemDto>): List<AktivitasItemDto> =
    items.sortedBy { it.jobdeskIndex }
