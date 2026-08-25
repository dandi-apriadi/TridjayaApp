package com.krisoft.tridjayaelektronik.data.model

import kotlinx.serialization.Serializable

/**
 * KUPON GEBYAR — cerminan 1:1 `kinerja-service/src/kupon_gebyar/domain.rs`
 * lewat gateway `/api/kupon-gebyar/{*rest}`.
 *
 * Program promo: konsumen dengan total belanja >= Rp1.500.000 dalam periode
 * 1 Jan – 30 Agu 2026 berhak **SATU** kupon (boolean, bukan kelipatan). Semua
 * cabang ikut KECUALI Manado — yang di sistem ini berarti DUA kode dealer
 * (`D-06` Samrat, `D-07` Bahu).
 *
 * **Data paling sensitif yang pernah disentuh app ini**: nama, nomor HP, dan
 * nilai belanja konsumen. Lingkupnya dikunci ke satu cabang DI SERVER, dibaca
 * dari `auth_users.cabang_id` — app tak pernah mengirim parameter cabang, dan
 * menambahkannya kelak berarti membuka jalan IDOR ke daftar konsumen cabang
 * lain hanya dengan menukar satu nilai.
 *
 * Semua field ber-default karena `ignoreUnknownKeys` hanya menutup field yang
 * BERLEBIH; field yang HILANG (server lama, canary) tetap melempar tanpa ini.
 */

/**
 * `GET /api/kupon-gebyar/meta` — dipakai kartu Activity untuk memutuskan tampil
 * atau tidak, tanpa menarik daftarnya lebih dulu.
 *
 * [bolehLihat] adalah **gerbang kedua yang wajib**, bukan hiasan: gerbang
 * CABANG tak bisa dinyatakan sebagai kunci kemampuan (`capabilities_for` hanya
 * tahu role, bukan `auth_users.cabang_id`), jadi kunci `kupon_gebyar.lihat`
 * meloloskan karyawan Manado juga. Default `false` = fail-closed.
 */
@Serializable
data class KuponGebyarMetaDto(
    val bolehLihat: Boolean = false,
    /** `null` = akun tidak terikat cabang mana pun. */
    val kodeDealer: String? = null,
    val namaCabang: String? = null,
    /** Kalimat penolakan DARI SERVER — jangan dikarang ulang di app. Server
     *  sengaja membedakan "akun belum terikat cabang" dari "cabang di luar
     *  program", dan menyamakannya membuat pesannya menyesatkan. */
    val alasan: String? = null,
    val periodeMulai: String = "",
    val periodeSelesai: String = "",
    val ambang: Long = 0,
    val jumlah: Int = 0,
    val sudahDikirim: Int = 0,
    /** Konsumen berhak yang DISEMBUNYIKAN karena nomornya tak ada. */
    val tanpaNomor: Int = 0,
    val dikecualikanKaryawan: Int = 0,
    val cocokNomorKaryawan: Int = 0,
    val terpotong: Boolean = false,
    val disinkronPada: String? = null,
) {
    /** Sisa pekerjaan cabang ini — angka kartu Activity. */
    val sisa: Int get() = (jumlah - sudahDikirim).coerceAtLeast(0)
}

@Serializable
data class KuponGebyarBarisDto(
    val kodeRekanan: String = "",
    val nama: String = "",
    /** `null` = nomornya kosong atau tak masuk akal. Server sudah MENYARING
     *  baris tanpa nomor dari daftar ini (kecuali yang sudah ada buktinya),
     *  jadi `null` di sini praktis cuma muncul pada baris yang sudah dikirim. */
    val hp: String? = null,
    val totalBelanja: Long = 0,
    val transaksi: Int = 0,
    val tanggalTerakhir: String = "",
    /** Selalu 1 untuk yang berhak — server yang memutuskan, bukan app. */
    val kupon: Int = 0,
    val konsumenKey: String = "",
    val sudahDikirim: Boolean = false,
    val dikirimOleh: String = "",
    val dikirimPada: String = "",
    /** Nomor yang tercatat adalah nomor KARYAWAN, bukan nomor konsumen ini —
     *  undangan yang dikirim ke sana tak pernah sampai ke orangnya. */
    val perluNomorPengganti: Boolean = false,
    // CATATAN: baris daftar TIDAK membawa `buktiUrl` — `BarisKuponPublic`
    // (kinerja-service `kupon_gebyar/domain.rs`) memang tak punya field itu,
    // sama seperti sisi web. Yang dibawa hanya SIAPA dan KAPAN. Menampilkan
    // fotonya butuh field baru di server lebih dulu; menambahkannya di sini
    // saja akan menjadi field yang selamanya kosong.
)

@Serializable
data class KuponGebyarDaftarDto(
    val items: List<KuponGebyarBarisDto> = emptyList(),
    val total: Int = 0,
    val page: Int = 1,
    val pageSize: Int = 50,
    val sudahDikirim: Int = 0,
    /** Konsumen berhak yang DISEMBUNYIKAN karena nomornya tak ada — mereka tak
     *  ikut di [items] maupun [total]. Angkanya dikirim supaya tidak lenyap. */
    val tanpaNomor: Int = 0,
    val dikecualikanKaryawan: Int = 0,
    val cocokNomorKaryawan: Int = 0,
    val terpotong: Boolean = false,
    val kodeDealer: String = "",
    val namaCabang: String = "",
    val disinkronPada: String? = null,
)

/**
 * Badan `POST /api/kupon-gebyar/bukti`.
 *
 * TANPA parameter cabang, sengaja — sama seperti `/daftar`. [buktiUrl] wajib
 * hasil `POST /api/kupon-gebyar/upload-bukti`; server memvalidasi bentuknya
 * (`domain::bukti_url_sah`), jadi URL karangan ditolak 400, bukan disimpan.
 */
@Serializable
data class KuponGebyarBuktiBody(
    val kodeRekanan: String,
    val buktiUrl: String,
    val catatan: String = "",
)

@Serializable
data class KuponGebyarBuktiDto(
    val id: String = "",
    val kodeRekanan: String = "",
    val konsumenKey: String = "",
    val nama: String = "",
    val buktiUrl: String = "",
    val catatan: String = "",
    val diunggahOlehNama: String = "",
    val createdAt: String = "",
)

@Serializable
data class KuponGebyarUploadDto(val url: String = "")
