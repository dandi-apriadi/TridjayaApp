package com.krisoft.tridjayaelektronik.ui.aktivitas

import com.krisoft.tridjayaelektronik.data.model.AktivitasItemDto
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

internal fun ringkasRiwayat(items: List<AktivitasItemDto>): RingkasanRiwayat {
    val disetujui = items.count { it.reviewStatus == "approved" }
    val ditolak = items.count { it.reviewStatus == "rejected" }
    val menunggu = items.size - disetujui - ditolak
    val dinilai = disetujui + ditolak
    // Skor dibaca dari server apa adanya. `skorReview(status)` SENGAJA tidak
    // dipakai di sini: fungsi itu cerminan OPTIMISTIS untuk layar PIC tepat
    // setelah menekan tombol, sedangkan di sini yang benar adalah angka yang
    // sudah tersimpan — termasuk baris lama yang skornya bukan 0/100.
    val skor = items.filter { it.reviewStatus == "approved" || it.reviewStatus == "rejected" }
        .mapNotNull { it.score }
    return RingkasanRiwayat(
        total = items.size,
        disetujui = disetujui,
        ditolak = ditolak,
        menunggu = menunggu,
        dinilai = dinilai,
        rataSkor = if (skor.isEmpty()) null else skor.sum() / skor.size,
    )
}

/** Urut naik menurut nomor aktivitas — `jobdeskIndex` NAMA KABEL, ejaan lama. */
internal fun urutRiwayat(items: List<AktivitasItemDto>): List<AktivitasItemDto> =
    items.sortedBy { it.jobdeskIndex }
