package com.krisoft.tridjayaelektronik.data.model

import kotlinx.serialization.Serializable

/**
 * Aktivitas harian (laporan aktivitas harian) — kinerja-service `raport.rs` +
 * `jobdesk.rs` lewat gateway `/api/raport-harian` & `/api/jobdesk-divisions`.
 * Semua field camelCase, sama persis dengan yang dipakai web
 * (`store/picAktivitasStore.ts`).
 *
 * **Istilahnya diganti "jobdesk" → "aktivitas" (2026-08-17), TAPI hanya nama
 * KELAS.** Repo ini nol `@SerialName`, jadi nama PROPERTI di bawah ADALAH nama
 * di kabel: mengganti `jobdesks`/`jobdeskIndex`/`jobdeskText` = field hilang
 * senyap (kotlinx.serialization mengisi nilai default, bukan melempar), dan
 * yang terlihat di lapangan cuma daftar kosong / index 0.
 */

/** Satu posisi/jabatan + daftar aktivitas hariannya (master `app_settings`). */
@Serializable
data class AktivitasPositionDto(
    val id: String = "",
    val posisi: String = "",
    /** NAMA KABEL — jangan ikut di-rename jadi `aktivitas`, lihat KDoc berkas. */
    val jobdesks: List<String> = emptyList(),
    /**
     * Nomor POSISI butir yang sudah dicabut — `KUNCI_NONAKTIF`
     * (`aktivitas_master.rs`). Butir nonaktif **berhenti dihitung sebagai
     * tagihan** di server: ia keluar dari penyebut indikator KPI
     * `LAPORAN AKTIVITAS` (yang bermuara ke slip gaji) DAN dari gerbang absen
     * pulang.
     *
     * Diturunkan sejak 2026-08-28. Sebelumnya app tak mengenal kunci ini sama
     * sekali — konsekuensi yang server sebut DITERIMA (klien lama merender
     * butir nonaktif sebagai pekerjaan biasa, "tak lebih buruk dari hari ini"),
     * bukan terlewat. Yang diperbaiki di sini adalah PENYEBUTNYA: layar riwayat
     * memakai `jumlahButirAktif`, jadi angkanya berhenti berselisih dengan skor
     * yang benar-benar dibayarkan.
     *
     * Nomor posisi, BUKAN array boolean sepanjang [jobdesks] — menambah butir
     * baru di akhir tak menyentuh daftar ini sama sekali.
     */
    val nonaktif: List<Int> = emptyList(),
)

/**
 * Nomor butir nonaktif yang SAH untuk divisi ini — cerminan `nomorButirNonaktif`
 * (web `ownerAktivitasData.ts`) dan `indeks_nonaktif` (Rust).
 *
 * Nomor di luar rentang disaring, dan itu bukan kehati-hatian berlebih: daftar
 * ini dipakai sebagai PENGURANG penyebut, jadi satu nomor liar dari katalog lama
 * membuat penyebut di layar lebih kecil dari yang sebenarnya ditagih — tanpa
 * satu pun error.
 */
internal fun nomorButirNonaktif(posisi: AktivitasPositionDto?): Set<Int> {
    val jumlah = posisi?.jobdesks?.size ?: 0
    return posisi?.nonaktif.orEmpty().filter { it in 0 until jumlah }.toSet()
}

/**
 * Berapa butir divisi ini yang masih DITAGIH.
 *
 * **Cerminan `aktivitas_master::jumlah_butir_aktif` (Rust) dan
 * `jumlahButirAktif` (web) — ketiganya HARUS menjawab angka yang sama.** Layar
 * yang memakai `jobdesks.size` penuh menampilkan pencapaian atas penyebut yang
 * LEBIH BESAR: 10/13 = 77% di layar sementara yang dibayarkan 10/12 = 83%.
 *
 * **Dipakai TIGA tempat** (kartu Home `ActivityViewModel`, footer layar
 * Aktivitas `AktivitasViewModel`, dan penyebut riwayat `AktivitasRiwayatPlan`).
 * Kalau hanya sebagian yang memakainya, satu layar menagih jumlah butir yang
 * berbeda dari layar sebelahnya untuk orang yang sama.
 */
internal fun jumlahButirAktif(posisi: AktivitasPositionDto?): Int =
    (posisi?.jobdesks?.size ?: 0) - nomorButirNonaktif(posisi).size

/**
 * Balasan `GET /api/raport-harian/penempatan-saya`.
 *
 * `penempatanId` NULL punya arti sendiri — "orang ini belum punya baris
 * `kpi_assignments`" — dan itu BUKAN sama dengan "permintaannya belum selesai".
 * Pemanggil WAJIB memisahkan keduanya lewat `PenempatanSaya`; lihat KDoc-nya
 * di `ui/raport/RaportPlan.kt` untuk alasan kenapa mencampurnya merusak layar.
 */
@Serializable
data class PenempatanSayaData(
    val penempatanId: String? = null,
)

@Serializable
data class AktivitasDivisionsData(
    val divisions: List<AktivitasPositionDto> = emptyList(),
)
