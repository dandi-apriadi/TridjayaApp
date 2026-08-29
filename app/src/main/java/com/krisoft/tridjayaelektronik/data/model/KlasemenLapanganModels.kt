package com.krisoft.tridjayaelektronik.data.model

import kotlinx.serialization.Serializable

/**
 * Papan klasemen kerja lapangan (driver & PDI) — `GET /api/klasemen/{peran}`.
 *
 * **Peringkatnya datang dari SERVER.** Berbeda dari klasemen penjualan, yang
 * menerima baris omset mentah lalu diagregasi + diperingkat di HP lewat
 * `KlasemenStandings`. Karena itu paket ini TIDAK memakai mesin tersebut dan
 * tidak memperluas enum-nya: menyalin aturan peringkat ke sisi klien akan
 * melahirkan salinan kedua yang menyimpang diam-diam dari server.
 *
 * Seluruh field bernilai default supaya penambahan kolom di server tidak
 * mematikan layar ini di APK lama (`ignoreUnknownKeys` + default = fail-soft).
 */
@Serializable
data class PapanLapanganDto(
    val periode: String = "",
    val peran: String = "",
    val dihitungPada: String = "",
    val peserta: List<PesertaLapanganDto> = emptyList(),
    val belumCukupData: List<BelumCukupDataDto> = emptyList(),
    val catatan: List<String> = emptyList(),
)

@Serializable
data class PesertaLapanganDto(
    val karyawanId: String = "",
    val nama: String = "",
    val cabang: String? = null,
    val rank: Int = 0,
    val skor: Double = 0.0,
    val metrik: List<MetrikLapanganDto> = emptyList(),
)

/**
 * `nilai == null` berarti komponen ini TIDAK MELEKAT pada orangnya — dirender
 * "—", bukan 0. Nol berarti "diukur dan hasilnya nol"; menyamakan keduanya
 * membuat driver yang tak pernah membawa uang COD terbaca sebagai driver yang
 * gagal menyetorkannya.
 */
@Serializable
data class MetrikLapanganDto(
    val kunci: String = "",
    val label: String = "",
    /** `persen` | `unit` | `hari` | `rasio` — dikirim server, jangan ditebak
     *  ulang dari `kunci`. Peta kedua di klien adalah cara termudah membuat
     *  kolom baru tampil sebagai angka telanjang tanpa ada yang sadar. */
    val satuan: String = "rasio",
    val nilai: Double? = null,
    val pembilang: Long? = null,
    val penyebut: Long? = null,
    val menentukanPeringkat: Boolean = false,
)

@Serializable
data class BelumCukupDataDto(
    val karyawanId: String = "",
    val nama: String = "",
    val cabang: String? = null,
    val alasan: String = "",
    /**
     * `true` = AKUN orangnya nonaktif, jadi ia ada di daftar ini BUKAN karena
     * datanya kurang (server 2026-08-29 ke atas).
     *
     * Default `false` menjaga APK lama/baru sama-sama waras: respons tanpa
     * field ini tak pernah merender lencana, dan itu perilaku yang benar.
     *
     * **Klaim tentang AKUN, bukan status kepegawaian** — `auth_users.is_active`
     * juga dipakai untuk suspend sementara. Jangan mengubah teksnya jadi
     * "sudah keluar"; lihat doc `BelumCukupData::akun_nonaktif` di Rust.
     */
    val akunNonaktif: Boolean = false,
)
