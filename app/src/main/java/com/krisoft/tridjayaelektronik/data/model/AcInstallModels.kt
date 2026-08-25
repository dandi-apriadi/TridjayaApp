package com.krisoft.tridjayaelektronik.data.model

import kotlinx.serialization.Serializable

/**
 * Pemasangan AC — cerminan 1:1 `inventory-service/src/pemasangan_ac.rs`
 * (migrasi 253/255/256), lewat gateway yang MENUMPANG wildcard
 * `/api/inventory/delivery/{*rest}` yang sudah ada. Tak ada rute gateway baru,
 * jadi tak ada yang perlu di-deploy di sisi gateway untuk layar ini.
 *
 * **Dua sisi, sejak 2026-08-25.** Semula app hanya menyentuh sisi PETUGAS
 * (`tugas-saya`, `terima`, `tolak`, bukti `foto`) dan penjadwalan tetap
 * web-saja. Sisi VERIFIKATOR (`acinstall.schedule`) kini ikut: daftar pengajuan,
 * penjadwalan + penugasan tim, penutupan, dan pembatalan.
 *
 * Yang SENGAJA tetap web-saja: **pengelolaan master tim** (buat/ubah tim,
 * `POST|PATCH .../tim`). App hanya MEMBACA daftar tim untuk dipilih saat
 * menjadwalkan. Menyusun keanggotaan tim adalah pekerjaan meja — daftar
 * kandidat se-perusahaan dengan pencarian nama, di layar selebar telapak
 * tangan — dan memindahkannya ke HP menukar satu perjalanan yang jarang
 * dengan layar yang buruk untuk dipakai.
 *
 * Sisi PENGAJUAN (sales, `acinstall.submit`) juga tetap web-saja: ia menempel
 * pada SPK yang baru selesai, dan alurnya sudah ada di halaman SPK web.
 */

/**
 * Ringkasan SPK yang dipasang. Server membacanya lewat **JOIN, bukan salinan**,
 * jadi SPK yang disunting (`spk.edit`) langsung tercermin di sini — jangan
 * meng-cache-nya ke Room dengan anggapan ia beku.
 */
@Serializable
data class AcInstallSpkDto(
    val deliveryJobId: String = "",
    val kodePengiriman: String = "",
    val noTransaksi: String = "",
    val kategori: String? = null,
    val kodeBarang: String = "",
    val namaBarang: String? = null,
    val merk: String? = null,
    val tipe: String? = null,
    val customerName: String? = null,
    val customerPhone: String? = null,
    val customerAddress: String? = null,
    val kodeDealer: String = "",
    val cabangNama: String? = null,
    val salesName: String? = null,
    val tanggalJual: String? = null,
    /** Status SPK-nya SENDIRI (`pending_pdi` … `delivered`), bukan status pemasangan. */
    val statusSpk: String = "",
)

@Serializable
data class AcInstallAnggotaDto(
    val userId: String = "",
    val nama: String = "",
)

@Serializable
data class AcInstallTimDto(
    val teamId: String = "",
    val nama: String = "",
    val anggota: List<AcInstallAnggotaDto> = emptyList(),
)

/**
 * Jawaban SATU petugas atas tugas yang diberikan kepadanya (migrasi 256).
 *
 * [status] `null` berarti **belum menjawab**, dan itu keadaan yang paling
 * sering: barisnya baru lahir saat orangnya menjawab. Jangan memperlakukan
 * `null` sebagai "ditolak" atau sebagai galat — server sengaja tidak membuat
 * baris "menunggu" di muka, karena keanggotaan tim dibaca live dan baris
 * pra-buat jadi hantu bagi yang sudah keluar tim.
 */
@Serializable
data class AcInstallPetugasDto(
    val userId: String = "",
    val nama: String = "",
    /** `diterima` / `ditolak` / `null` = belum menjawab. */
    val status: String? = null,
    val alasan: String? = null,
    val respondedAt: String? = null,
)

/**
 * Bukti foto pemasangan (migrasi 255). [url] berbentuk
 * `/uploads/delivery/{uuid}.{ext}` — berkasnya MENUMPANG direktori unggah
 * delivery, jadi ia di-serve TERAUTENTIKASI lewat `GET /inventory/delivery/photo/{f}`
 * seperti foto delivery lain. `<img src="/uploads/…">` polos selalu gagal.
 */
@Serializable
data class AcInstallFotoDto(
    val id: String = "",
    val url: String = "",
    val keterangan: String? = null,
    val diunggahOleh: String = "",
    val diunggahOlehNama: String? = null,
    val diunggahAt: String? = null,
)

/** Satu pengajuan pemasangan — `pemasangan_ac::Pengajuan`. */
@Serializable
data class AcInstallTaskDto(
    val id: String = "",
    val spk: AcInstallSpkDto = AcInstallSpkDto(),
    val alamatPemasangan: String? = null,
    val kontakNama: String? = null,
    val kontakHp: String? = null,
    val catatan: String? = null,
    val preferensiTanggal: String? = null,
    /** `diajukan` | `dijadwalkan` | `selesai` | `dibatalkan`. */
    val status: String = "",
    val diajukanOleh: String = "",
    val diajukanOlehNama: String? = null,
    val diajukanAt: String? = null,
    val jadwalTanggal: String? = null,
    val jadwalJam: String? = null,
    val catatanJadwal: String? = null,
    val dijadwalkanOleh: String? = null,
    val dijadwalkanOlehNama: String? = null,
    val dijadwalkanAt: String? = null,
    val selesaiOlehNama: String? = null,
    val selesaiAt: String? = null,
    val catatanSelesai: String? = null,
    val batalAlasan: String? = null,
    val batalOlehNama: String? = null,
    val batalAt: String? = null,
    val tim: List<AcInstallTimDto> = emptyList(),
    val foto: List<AcInstallFotoDto> = emptyList(),
    /** Satu baris per ORANG yang ditugaskan — gabungan anggota tim live + jawaban
     *  yang sudah masuk. Yang berangkat adalah orang, bukan tim. */
    val petugas: List<AcInstallPetugasDto> = emptyList(),
)

/**
 * Badan untuk `terima` DAN `tolak` — server memakai satu bentuk untuk dua rute
 * (`ResponPayload`). Menolak WAJIB beralasan; menerima tidak.
 */
@Serializable
data class AcInstallResponBody(
    val alasan: String? = null,
)

/**
 * [url] WAJIB hasil `POST /inventory/delivery/upload-photo` — server menolak URL
 * yang bukan dari endpoint unggah kita (`foto_url_sah`), karena kolom ini
 * berakhir sebagai gambar di layar orang lain.
 */
@Serializable
data class AcInstallFotoBody(
    val url: String,
    val keterangan: String? = null,
)

/** Status pemasangan — cerminan slug yang dipakai `transisi::*` di server. */
object AcInstallStatus {
    const val DIAJUKAN = "diajukan"
    const val DIJADWALKAN = "dijadwalkan"
    const val SELESAI = "selesai"
    const val DIBATALKAN = "dibatalkan"
}

/** Jawaban petugas — cerminan `ac_install_task_responses.status`. */
object AcInstallRespon {
    const val DITERIMA = "diterima"
    const val DITOLAK = "ditolak"
}

// ---------------------------------------------------------------------------
// Sisi VERIFIKATOR (`acinstall.schedule`) — penjadwalan & penugasan
// ---------------------------------------------------------------------------

/**
 * Satu tim di MASTER — `pemasangan_ac::Tim`. Beda dari [AcInstallTimDto], yang
 * merupakan tim yang SUDAH ditugaskan pada sebuah pengajuan (`TimDitugaskan`,
 * berkunci `teamId`). Yang ini berkunci `id` dan membawa cabang + status aktif.
 *
 * [kodeDealer] `null` = **tim pusat/lintas cabang, dan itu SAH** — bukan data
 * yang belum lengkap. Tim pemasangan AC memang berpindah cabang, jadi jangan
 * menyembunyikan tim ber-`kodeDealer` null dari picker cabang mana pun.
 */
@Serializable
data class AcInstallTimMasterDto(
    val id: String = "",
    val nama: String = "",
    val kodeDealer: String? = null,
    val cabangNama: String? = null,
    val keterangan: String? = null,
    val aktif: Boolean = true,
    val anggota: List<AcInstallAnggotaDto> = emptyList(),
)

/**
 * Calon anggota tim — `pemasangan_ac::Kandidat`. Dipakai layar ini HANYA untuk
 * menampilkan siapa saja isi sebuah tim saat memilihnya; penyusunan tim sendiri
 * tetap di web.
 */
@Serializable
data class AcInstallKandidatDto(
    val userId: String = "",
    val nama: String = "",
    val kodeDealer: String? = null,
    val cabangNama: String? = null,
    /** Isi `auth_users.divisi` apa adanya (CSV) — kolom `jabatan` dibuang migrasi 208. */
    val divisi: String? = null,
)

/**
 * Menjadwalkan (atau MENJADWALKAN ULANG) sebuah pengajuan — `JadwalPayload`.
 *
 * **[teamIds] MENGGANTI seluruh daftar tim, bukan menambahinya.** Mengirim
 * daftar kosong = mencabut semua penugasan. Ini perilaku server, dan layar
 * apa pun yang mengirim "tim yang baru dicentang" saja akan diam-diam
 * membuang tim yang sudah ada di sana.
 *
 * [tanggal] `YYYY-MM-DD`, [jam] `HH:MM` (opsional). Menjadwalkan ulang yang
 * sudah dijadwalkan DIIZINKAN — yang ditolak hanya pengajuan yang sudah
 * ditutup (`selesai`/`dibatalkan`), lihat `transisi::boleh_jadwalkan`.
 */
@Serializable
data class AcInstallJadwalBody(
    val tanggal: String,
    val jam: String? = null,
    val teamIds: List<String> = emptyList(),
    val catatan: String? = null,
)

/** Menutup pekerjaan. Server MENUNTUT pengajuan sudah punya jadwal — tanpa itu
 *  sebuah pengajuan bisa melompat dari "diajukan" langsung ke "selesai" dan
 *  laporan kehilangan satu-satunya jejak kapan tim benar-benar berangkat. */
@Serializable
data class AcInstallSelesaiBody(
    val catatan: String? = null,
)

/** Membatalkan. [alasan] WAJIB — server menolak yang kosong. */
@Serializable
data class AcInstallBatalBody(
    val alasan: String,
)
