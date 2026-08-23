package com.krisoft.tridjayaelektronik.data.model

import kotlinx.serialization.Serializable

/**
 * Pemasangan AC — cerminan 1:1 `inventory-service/src/pemasangan_ac.rs`
 * (migrasi 253/255/256), lewat gateway yang MENUMPANG wildcard
 * `/api/inventory/delivery/{*rest}` yang sudah ada. Tak ada rute gateway baru,
 * jadi tak ada yang perlu di-deploy di sisi gateway untuk layar ini.
 *
 * **App hanya menyentuh sisi PETUGAS**: `tugas-saya`, `terima`, `tolak`, dan
 * bukti `foto`. Pengajuan (sales) dan penjadwalan (verifikator) tetap web-saja —
 * itu keputusan yang sudah diambil saat fitur ini lahir, dan menariknya ke app
 * berarti memindahkan juga pemilihan tim + kalender yang tak ada padanannya di
 * layar HP.
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
