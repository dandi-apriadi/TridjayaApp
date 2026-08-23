package com.krisoft.tridjayaelektronik.data.model

import kotlinx.serialization.Serializable

/**
 * Cerminan 1:1 respons `/api/eksekutif/…` (kinerja-service modul `eksekutif`,
 * `domain.rs`).
 *
 * **Repo ini NOL `@SerialName`** — nama properti Kotlin ADALAH nama di kabel.
 * Mengganti nama properti di sini tidak memunculkan galat apa pun: kotlinx
 * mengisi nilai default dan field-nya lenyap diam-diam. Kalau nama backend
 * berubah, ubah nama properti ini, jangan menambahkan alias.
 *
 * Semua angka ber-default supaya APK yang lebih baru dari server tidak meledak
 * pada field yang belum ada; `Double?`/`String?` yang `null` berarti "tak bisa
 * diukur" dan WAJIB dirender "—", bukan 0.
 */
@Serializable
data class EksekutifPapanDto(
    val periode: EksekutifPeriodeDto = EksekutifPeriodeDto(),
    val penjualan: EksekutifPenjualanDto = EksekutifPenjualanDto(),
    val absensi: EksekutifAbsensiDto = EksekutifAbsensiDto(),
    val spkVsGs: EksekutifKecocokanDto = EksekutifKecocokanDto(),
    val pemakaianPersen: Double? = null,
    val karyawanTotal: Long = 0,
    val kepatuhan: EksekutifKepatuhanDto = EksekutifKepatuhanDto(),
    val target: EksekutifTargetDto = EksekutifTargetDto(),
    val cabang: List<EksekutifCabangDto> = emptyList(),
    val kesegaran: EksekutifKesegaranDto = EksekutifKesegaranDto(),
)

@Serializable
data class EksekutifDetailCabangDto(
    val periode: EksekutifPeriodeDto = EksekutifPeriodeDto(),
    val cabang: EksekutifCabangDto = EksekutifCabangDto(),
    val karyawan: List<EksekutifKaryawanDto> = emptyList(),
    val kesegaran: EksekutifKesegaranDto = EksekutifKesegaranDto(),
)

@Serializable
data class EksekutifPeriodeDto(
    val start: String = "",
    val end: String = "",
    val hariKerja: Long = 0,
)

@Serializable
data class EksekutifPenjualanDto(
    val omset: Long = 0,
    val unitBesar: Long = 0,
    val transaksi: Long = 0,
)

@Serializable
data class EksekutifAbsensiDto(
    val hadir: Long = 0,
    val offHari: Long = 0,
    val alpa: Long = 0,
    val karyawan: Long = 0,
    val persenHadir: Double? = null,
)

/**
 * Tiga definisi "cocok" berdampingan — lihat `domain::Kecocokan` di backend.
 * Jangan menyederhanakannya jadi satu persentase di klien: yang dipilih
 * berikutnya akan jadi angka yang dipercaya, dan ketiganya berbeda jauh.
 */
@Serializable
data class EksekutifKecocokanDto(
    val dinilai: Long = 0,
    val cocokNomor: Long = 0,
    val cocokUnit: Long = 0,
    val cocokNominal: Long = 0,
    /** SPK terlalu muda untuk dinilai — DI LUAR `dinilai`, bukan "tidak cocok". */
    val belumMatang: Long = 0,
    /** SPK tanpa `no_transaksi` — juga di luar `dinilai`, masalah yang berbeda. */
    val tanpaNomor: Long = 0,
)

@Serializable
data class EksekutifCabangDto(
    val kodeDealer: String = "",
    val nama: String = "",
    val penjualan: EksekutifPenjualanDto = EksekutifPenjualanDto(),
    val absensi: EksekutifAbsensiDto = EksekutifAbsensiDto(),
    val spkVsGs: EksekutifKecocokanDto = EksekutifKecocokanDto(),
    val pemakaianPersen: Double? = null,
    val karyawanAktif: Long = 0,
    val kepatuhan: EksekutifKepatuhanDto = EksekutifKepatuhanDto(),
    val target: EksekutifTargetDto = EksekutifTargetDto(),
)

@Serializable
data class EksekutifKaryawanDto(
    val userId: String = "",
    val nik: String = "",
    val nama: String = "",
    val role: String = "",
    val divisi: String = "",
    val penjualan: EksekutifPenjualanDto = EksekutifPenjualanDto(),
    val hadir: Long = 0,
    val offHari: Long = 0,
    /** `null` = orang ini tidak diwajibkan absen (manager/akun uji/dikecualikan). */
    val persenHadir: Double? = null,
    val spkVsGs: EksekutifKecocokanDto = EksekutifKecocokanDto(),
    val pemakaian: EksekutifPemakaianDto = EksekutifPemakaianDto(),
    val kepatuhan: EksekutifKepatuhanDto = EksekutifKepatuhanDto(),
    val target: EksekutifTargetDto = EksekutifTargetDto(),
    val terakhirLoginAt: String? = null,
)

/**
 * Skor kepatuhan PROSES — bukan kinerja penjualan.
 *
 * Tiap komponen `null` = tidak berlaku untuk subjek ini (driver tak punya SPK,
 * orang tanpa penempatan tak punya penyebut aktivitas). `skor` adalah rata-rata
 * berbobot dari yang terukur SAJA, dan [bobotTerpakai] menyebut berapa banyak
 * bobot yang benar-benar ikut. **Rendering `0` untuk `null` di sini akan
 * menuduh orang atas komponen yang memang tak melekat padanya.**
 */
@Serializable
data class EksekutifKepatuhanDto(
    val skor: Double? = null,
    val kehadiran: Double? = null,
    val aktivitas: Double? = null,
    val bukti: Double? = null,
    val spk: Double? = null,
    val bobotTerpakai: Double = 0.0,
    val rincian: EksekutifRincianKepatuhanDto = EksekutifRincianKepatuhanDto(),
)

/** Pembilang & penyebut mentah tiap komponen kepatuhan — supaya bisa dibantah. */
@Serializable
data class EksekutifRincianKepatuhanDto(
    val hadir: Long = 0,
    val hariWajib: Long = 0,
    val aktivitasTerisi: Long = 0,
    val aktivitasWajib: Long = 0,
    val buktiSah: Long = 0,
    val buktiWajib: Long = 0,
    val spkCocok: Long = 0,
    val spkDinilai: Long = 0,
)

/**
 * Target periode + capaiannya.
 *
 * Semua `null`-able, dan `null` BERBEDA dari `0`: target yang belum pernah
 * diisi bukan target nol. Produksi 2026-08-23 hanya punya target cabang untuk
 * Juli & Agustus 2026 dan target unit per orang HANYA untuk Juli, jadi "—"
 * adalah tampilan yang WAJAR di sini, bukan tanda gagal muat.
 *
 * [prorata] `true` = rentangnya bukan satu bulan kalender penuh, jadi angkanya
 * hasil pembagian menurut hari kerja — bukan angka yang pernah diketik orang.
 */
@Serializable
data class EksekutifTargetDto(
    val omset: Long? = null,
    val unit: Long? = null,
    val omsetBulanan: Long? = null,
    val unitBulanan: Long? = null,
    val capaianOmsetPersen: Double? = null,
    val capaianUnitPersen: Double? = null,
    val prorata: Boolean = false,
)

@Serializable
data class EksekutifPemakaianDto(
    val hariAktif: Long = 0,
    val hariKerja: Long = 0,
    val persen: Double? = null,
    val rincian: EksekutifRincianPemakaianDto = EksekutifRincianPemakaianDto(),
)

@Serializable
data class EksekutifRincianPemakaianDto(
    val absen: Long = 0,
    val aktivitas: Long = 0,
    val spk: Long = 0,
    val prospek: Long = 0,
)

@Serializable
data class EksekutifKesegaranDto(
    val mirrorSyncedAt: String? = null,
    val mirrorUmurDetik: Long? = null,
    val spkMatangJam: Long = 0,
)
