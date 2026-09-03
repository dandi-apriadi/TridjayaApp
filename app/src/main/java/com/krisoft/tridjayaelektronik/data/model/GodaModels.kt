package com.krisoft.tridjayaelektronik.data.model

import kotlinx.serialization.Serializable

/**
 * Menu **SN Goda** — stok + serial number unit sepeda listrik merk GODA
 * (kinerja-service `goda.rs`, via gateway `/api/goda/…`). Semua field
 * `camelCase`: servernya memakai `#[serde(rename_all = "camelCase")]`.
 *
 * **Angkanya adalah isi SALINAN stok (`erp_mirror_stok`), bukan GS langsung** —
 * itu keputusan sadar di server (lihat KDoc modul `goda.rs`), jadi layar wajib
 * menyebut umur salinannya ([GodaStokDto.syncedAt]) alih-alih membiarkan orang
 * mengira angkanya real-time.
 */
@Serializable
data class GodaSerialDto(
    val id: String = "",
    val serialNumber: String = "",
    val updatedAt: String = ""
)

@Serializable
data class GodaBarisDto(
    val kodeDealer: String = "",
    /** Nama cabang siap-baca ("Pagaden"), turunan [kodeDealer] di server. */
    val cabangNama: String = "",
    val kodeBarang: String = "",
    val namaBarang: String = "",
    val merk: String = "",
    val tipe: String = "",
    val stok: Long = 0,
    val serials: List<GodaSerialDto> = emptyList(),
    /**
     * Jumlah SN TERDAFTAR. Sengaja dilaporkan terpisah dari [stok]: selisihnya
     * adalah keadaan NYATA (SN belum diinput), bukan galat — dan justru itulah
     * daftar kerja layar ini.
     */
    val jumlahSn: Long = 0
)

@Serializable
data class GodaStokDto(
    val baris: List<GodaBarisDto> = emptyList(),
    val totalStok: Long = 0,
    val totalSn: Long = 0,
    /** `null` = salinan belum pernah terisi — render "tidak diketahui", BUKAN "baru saja". */
    val syncedAt: String? = null
)

/** Badan `POST /api/goda/serial`. */
@Serializable
data class GodaTambahSnBody(
    val kodeDealer: String,
    val kodeBarang: String,
    val serialNumber: String
)

@Serializable
data class GodaTambahHasilDto(
    val id: String = "",
    val serialNumber: String = ""
)
