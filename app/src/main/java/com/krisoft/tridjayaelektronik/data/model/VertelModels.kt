package com.krisoft.tridjayaelektronik.data.model

import kotlinx.serialization.Serializable

/**
 * Verifikasi telepon ("vertel") — cerminan 1:1 `inventory-service/src/vertel.rs`
 * (migrasi 257), lewat gateway yang MENUMPANG wildcard
 * `/api/inventory/delivery/{*rest}` yang sudah ada. Tak ada rute gateway baru,
 * jadi APK ini bisa bicara dengan gateway produksi yang sekarang tanpa deploy
 * gateway — beda dari Kupon Gebyar, yang mengikat urutan deploy.
 *
 * Pekerjaannya: menelepon konsumen yang KEMARIN membeli barang berharga di atas
 * ambang, lalu mencatat hasilnya. Verifikator duduk di PUSAT untuk 13 cabang.
 */

/**
 * Catatan panggilan yang sudah masuk untuk satu transaksi.
 *
 * `null` di [BarisVertelDto.panggilan] = **belum ditelepon**, dan itu keadaan
 * AWAL setiap baris — bukan kegagalan, bukan data yang hilang.
 */
@Serializable
data class VertelPanggilanDto(
    /** `telepon` | `wa` — lihat [VertelKanal]. */
    val kanal: String = "",
    /** `terhubung` | `tidak_diangkat` | `nomor_salah` | `jadwal_ulang` — lihat [VertelHasil]. */
    val hasil: String = "",
    val adaKomplain: Boolean = false,
    val catatan: String? = null,
    val olehNama: String? = null,
    val calledAt: String? = null,
)

/** Satu transaksi yang perlu diverifikasi — `vertel::BarisVertel`. */
@Serializable
data class BarisVertelDto(
    val noTransaksi: String = "",
    val tanggal: String = "",
    val kodeDealer: String? = null,
    val cabangNama: String? = null,
    val customerNama: String? = null,
    /** Nomor APA ADANYA dari GS — ditampilkan supaya verifikator bisa membaca
     *  atau mengoreksinya, sekalipun tak layak ditautkan. */
    val customerHp: String? = null,
    /**
     * Nomor ternormalisasi `628…` untuk `https://wa.me/{waNumber}`.
     *
     * `null` = nomornya TAK LAYAK ditautkan (kosong, nomor kantor, ngawur).
     * Sembunyikan tombol WA-nya; **jangan** menautkan [customerHp] mentah
     * sebagai gantinya — server sudah menilai nomor itu dan menjawab tidak.
     */
    val waNumber: String? = null,
    /** Ringkasan barang, sudah DIGABUNG server supaya klien tak perlu tahu satu
     *  transaksi berisi banyak baris. */
    val barang: String = "",
    val jumlahBaris: Long = 0,
    val totalNominal: Long = 0,
    val salesNama: String? = null,
    val panggilan: VertelPanggilanDto? = null,
)

/**
 * Angka yang dipakai verifikator menilai pekerjaannya sendiri hari itu.
 *
 * [tanpaNomor] SENGAJA dipisah dari "belum ditelepon": ia BUKAN kelalaian
 * verifikator, dan menyatukannya membuat target mustahil dicapai oleh sebab
 * yang bukan salahnya.
 */
@Serializable
data class RingkasanVertelDto(
    val total: Long = 0,
    val sudahDitelepon: Long = 0,
    val terhubung: Long = 0,
    val adaKomplain: Long = 0,
    val tanpaNomor: Long = 0,
)

/** Isi `GET /inventory/delivery/vertel` — `vertel::DaftarVertel`. */
@Serializable
data class DaftarVertelDto(
    val tanggal: String = "",
    /**
     * Ambang HARGA SATUAN BARANG yang dipakai menyaring daftar ini (rupiah,
     * INKLUSIF). Ikut dikirim karena angkanya bisa diubah lewat `app_settings`
     * **tanpa deploy** — klien MENAMPILKANNYA, tak pernah menebak atau
     * menghitungnya sendiri.
     */
    val ambangHarga: Long = 0,
    val ringkasan: RingkasanVertelDto = RingkasanVertelDto(),
    val baris: List<BarisVertelDto> = emptyList(),
)

/**
 * Badan `POST /inventory/delivery/vertel/catat` — `vertel::CatatPayload`.
 *
 * [tanggal] adalah tanggal TRANSAKSI (bukan tanggal menelepon) dan wajib
 * `YYYY-MM-DD`; ia bagian dari kunci baris yang di-upsert, jadi mengirim
 * tanggal hari ini untuk transaksi kemarin membuat catatannya mendarat di
 * baris yang salah.
 */
@Serializable
data class VertelCatatBody(
    val noTransaksi: String,
    val tanggal: String,
    val kanal: String,
    val hasil: String,
    val adaKomplain: Boolean = false,
    val catatan: String? = null,
)

/** Cerminan `KANAL_SAH` (`vertel.rs`). Server menolak nilai di luar ini. */
object VertelKanal {
    const val TELEPON = "telepon"
    const val WA = "wa"

    val SEMUA = listOf(TELEPON, WA)

    fun label(kanal: String): String = when (kanal) {
        TELEPON -> "Telepon"
        WA -> "WhatsApp"
        else -> kanal
    }
}

/** Cerminan `HASIL_SAH` (`vertel.rs`). Server menolak nilai di luar ini. */
object VertelHasil {
    const val TERHUBUNG = "terhubung"
    const val TIDAK_DIANGKAT = "tidak_diangkat"
    const val NOMOR_SALAH = "nomor_salah"
    const val JADWAL_ULANG = "jadwal_ulang"

    val SEMUA = listOf(TERHUBUNG, TIDAK_DIANGKAT, NOMOR_SALAH, JADWAL_ULANG)

    fun label(hasil: String): String = when (hasil) {
        TERHUBUNG -> "Terhubung"
        TIDAK_DIANGKAT -> "Tidak diangkat"
        NOMOR_SALAH -> "Nomor salah"
        JADWAL_ULANG -> "Jadwal ulang"
        else -> hasil
    }
}
