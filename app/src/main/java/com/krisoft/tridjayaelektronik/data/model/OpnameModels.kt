package com.krisoft.tridjayaelektronik.data.model

import kotlinx.serialization.Serializable

/**
 * Stock opname (hitung fisik stok) — mirrors inventory-service's opname module. The detail
 * endpoint serde-flattens the session into the same JSON object as `items`, so
 * [OpnameDetailDto] repeats the session fields instead of nesting an [OpnameSessionDto].
 */
@Serializable
data class OpnameSessionDto(
    val id: String = "",
    val kodeOpname: String = "",
    val dealerCode: String = "",
    val dealerName: String = "",
    val cabangId: String? = null,
    val cabangName: String? = null,
    val periodeDate: String = "",
    /** Jendela boleh-menghitung (WIB, migrasi 196). `null` = TANPA jendela —
     *  boleh kapan saja, BUKAN "sudah lewat tenggat". Seluruh sesi pra-migrasi
     *  begitu, dan server lama juga tak mengirimnya. */
    val mulaiAt: String? = null,
    val selesaiAt: String? = null,
    val jenis: String = "bulanan",
    val status: String = "draft",
    val createdByUserId: String = "",
    val createdByName: String? = null,
    val completedByUserId: String? = null,
    val completedByName: String? = null,
    val completedAt: String? = null,
    val catatan: String? = null,
    val totalItems: Long = 0,
    val totalSelisihItems: Long = 0,
    val totalStokFisik: Long = 0,
    val createdAt: String = "",
    val updatedAt: String = ""
)

@Serializable
data class OpnameItemDto(
    val id: String = "",
    val kodeBarang: String = "",
    val namaBarang: String? = null,
    val merk: String? = null,
    val kategori: String? = null,
    val stokSistem: Long = 0,
    val stokFisikLayak: Long = 0,
    val stokFisikTidakLayak: Long = 0,
    val selisih: Long = 0,
    /** `true` = dinyatakan NIHIL (sudah dicari, tak ada satu pun) — BUKAN
     *  "belum dihitung". Yang kedua menahan penutupan sesi, yang pertama
     *  adalah temuan (migrasi 197). */
    val nihil: Boolean = false,
    val nihilByName: String? = null,
    val nihilAt: String? = null,
    val stokSistemAkhir: Long? = null,
    val terjual: Long? = null,
    val masuk: Long? = null,
    val harga: Double? = null,
    val keterangan: String? = null,
    val countedByUserId: String = "",
    val countedByName: String? = null,
    val countedAt: String = ""
)

@Serializable
data class OpnameDetailDto(
    val id: String = "",
    val kodeOpname: String = "",
    val dealerCode: String = "",
    val dealerName: String = "",
    val cabangId: String? = null,
    val cabangName: String? = null,
    val periodeDate: String = "",
    /** Jendela boleh-menghitung (WIB, migrasi 196). `null` = TANPA jendela —
     *  boleh kapan saja, BUKAN "sudah lewat tenggat". Seluruh sesi pra-migrasi
     *  begitu, dan server lama juga tak mengirimnya. */
    val mulaiAt: String? = null,
    val selesaiAt: String? = null,
    val jenis: String = "bulanan",
    val status: String = "draft",
    val createdByUserId: String = "",
    val createdByName: String? = null,
    val completedByUserId: String? = null,
    val completedByName: String? = null,
    val completedAt: String? = null,
    val catatan: String? = null,
    val totalItems: Long = 0,
    val totalSelisihItems: Long = 0,
    val totalStokFisik: Long = 0,
    val createdAt: String = "",
    val updatedAt: String = "",
    val items: List<OpnameItemDto> = emptyList(),
    /**
     * Boleh IKUT MENGHITUNG di sesi ini — dihitung SERVER (`authorize_hitung`,
     * sudah termasuk syarat sesi masih draft).
     *
     * `null` = server BELUM mengenal field ini (bukan "tidak boleh"). Bedanya
     * penting: memakai `false` sebagai default akan mencabut tombol scan bahkan
     * dari PEMILIK sesi begitu APK ini beredar di atas server lama — mengurangi
     * fungsi yang sudah jalan. Pemanggil wajib jatuh balik ke aturan lama
     * (`isOwner && draft`) saat `null`. Pola sama `validationStatus` di
     * `manualUnit`: ikuti jawaban server, jangan mengarang.
     */
    val canHitung: Boolean? = null,
    /** Boleh MENGELOLA sesi: tutup/batal/hapus (`authorize_owner`). Terpisah
     *  dari [canHitung] — petugas cabang menghitung, tidak menutup. `null`
     *  diperlakukan sama: jatuh balik ke `isOwner`. */
    val canManage: Boolean? = null,
    /**
     * Boleh mencatat unit KETIK MANUAL (`input_method='manual'`) — izin
     * `tetapkan_sn` penunjukan petugas opname (migrasi 212). Server sudah
     * meng-AND-kannya dengan [canHitung], jadi `true` tak pernah muncul di sesi
     * yang aktornya tak boleh sentuh sama sekali.
     *
     * `null` = server BELUM mengenal field ini, BUKAN "tidak boleh" — pemanggil
     * WAJIB jatuh balik ke [canHitung] (aturan pra-212: yang boleh menghitung
     * boleh keduanya). Pakai `Boolean` non-nullable ber-default `false` dan APK
     * ini akan mencabut ketik-manual dari SEMUA orang begitu beredar di atas
     * server lama. Pola sama [canHitung]/[canManage].
     */
    val canTetapkanSn: Boolean? = null,
    /**
     * Boleh mencatat unit hasil SCAN (`input_method='scan'`) — izin
     * `verifikasi_sn` penunjukan petugas opname (migrasi 212). `null` = server
     * lama, jatuh balik ke [canHitung]. Lihat catatan [canTetapkanSn].
     */
    val canVerifikasiSn: Boolean? = null
)

@Serializable
data class OpnameListData(
    val items: List<OpnameSessionDto> = emptyList()
)

@Serializable
data class OpnameDealerDto(
    val code: String = "",
    val name: String = ""
)

/** `GET /api/inventory/opname/context` — actor capabilities + dealer dropdown options. */
@Serializable
data class OpnameContextDto(
    val canCreate: Boolean = false,
    val isManager: Boolean = false,
    val role: String = "",
    val dealers: List<OpnameDealerDto> = emptyList(),
    /**
     * Lingkup daftar sesi: `"semua"` (pemantau lintas cabang), `"cabang"`
     * (disaring ke cabang akun), `"pembuat"` (akun tanpa cabang — hanya sesi
     * buatannya sendiri). Cerminan `list_scope` di inventory-service.
     *
     * String kosong = server belum mengenal field ini.
     */
    val lingkup: String = "",
    /** Cabang yang dipakai server menyaring daftar; `null` untuk `semua`/`pembuat`. */
    val dealerCode: String? = null,
    val dealerName: String? = null,
    /**
     * Kalimat siap tampil saat daftar sesi KOSONG — menyebut CABANG orang itu
     * dan SIAPA yang bisa membuka sesi.
     *
     * Datang dari server, bukan dikarang app: dua fakta di dalamnya (cabang mana
     * yang dipakai menyaring, dan peran mana yang lolos guard
     * `create_opname_session`) hanya benar di sisi server, dan menyalinnya ke
     * sini berarti kalimatnya basi diam-diam begitu guard-nya bergeser.
     *
     * `null`/kosong = server lama → pemanggil WAJIB jatuh balik ke teks
     * cadangannya sendiri, jangan menampilkan layar tanpa keterangan.
     */
    val pesanKosong: String? = null,
)

/** One row of the session's frozen coverage list (identity only — no stock values). */
@Serializable
data class OpnameStockItemDto(
    val kodeBarang: String = "",
    val namaBarang: String? = null,
    val merk: String? = null,
    val kategori: String? = null
)

@Serializable
data class OpnameStockData(
    val items: List<OpnameStockItemDto> = emptyList()
)

@Serializable
data class CreateOpnameRequest(
    val dealerCode: String,
    val periodeDate: String,
    val jenis: String? = null,
    val catatan: String? = null,
    /** `YYYY-MM-DDTHH:MM` WIB. `null` = sesi tanpa batas waktu (perilaku lama,
     *  tetap sah). Server MENEGAKKAN-nya: scan di luar rentang ditolak. */
    val mulaiAt: String? = null,
    val selesaiAt: String? = null
)

/**
 * Body `POST /inventory/opname/{id}/nihil` — barang yang dinyatakan NIHIL.
 *
 * Daftar, bukan satuan: menutup sesi biasanya berarti menandai sisa barang
 * sekaligus, dan satu permintaan per barang jadi ratusan permintaan di cabang
 * dengan ~900 SKU.
 */
@Serializable
data class TandaiNihilRequest(val kodeBarang: List<String>)

/** Satu unit fisik terhitung — satu baris per serial number. */
@Serializable
data class OpnameUnitDto(
    val id: String = "",
    val kodeBarang: String = "",
    val serialNumber: String = "",
    val kondisi: String = "layak",
    /** Vonis admin-stok di registry untuk serial ini (migrasi 194). `null` =
     *  belum terdaftar ATAU terdaftar tapi kondisinya belum pernah ditetapkan —
     *  dua-duanya berarti "tak ada pembanding", bukan "layak". */
    val kondisiRegistry: String? = null,
    /** Dihitung SERVER: `true` hanya bila registry punya vonis DAN berbeda dari
     *  temuan lapangan. Jangan dihitung ulang di klien — perlakuan `null` yang
     *  berbeda membuat web dan app melaporkan angka berbeda untuk sesi sama. */
    val kondisiSelisih: Boolean = false,
    val temuan: String? = null,
    val keterangan: String? = null,
    /** `scan` | `manual` — ketik manual wajib 2 foto + validasi admin-stok. */
    val inputMethod: String = "scan",
    /** Hanya unit manual: `pending` | `approved` | `rejected`; scan = null. */
    val validationStatus: String? = null,
    val rejectReason: String? = null,
    val countedByUserId: String = "",
    val countedByName: String? = null,
    val countedAt: String = ""
)

@Serializable
data class OpnameUnitInput(
    val kodeBarang: String,
    val serialNumber: String,
    val kondisi: String = "layak",
    val keterangan: String? = null,
    /** Absen = server memakai default `scan` — kompatibel dgn backend lama. */
    val inputMethod: String? = null,
    val fotoSnUrl: String? = null,
    val fotoBarangUrl: String? = null
)

@Serializable
data class CreateOpnameUnitsRequest(
    val items: List<OpnameUnitInput>
)

@Serializable
data class OpnameUnitAccepted(
    val serialNumber: String = "",
    val temuan: String? = null,
    /** `pending` bila unitnya manual — bahan pesan "menunggu validasi". */
    val validationStatus: String? = null
)

@Serializable
data class OpnameUnitRejected(
    val serialNumber: String = "",
    /** KODE mesin (`duplikat_dalam_sesi`, `jendela_belum_mulai`, ...) — bukan
     *  kalimat. Dipetakan ke bahasa petugas lewat `alasanTolakLabel`. */
    val reason: String = "",
    /** Kalimat rinci dari server, sudah memuat jam jendelanya. Kosong untuk
     *  kode lama yang tak membawanya. */
    val reasonText: String? = null
)

/**
 * Hasil per-baris: satu serial bermasalah tidak menggagalkan sisanya, jadi
 * petugas tak perlu mengulang seluruh batch scan di lapangan.
 */
@Serializable
data class CreateOpnameUnitsData(
    val accepted: List<OpnameUnitAccepted> = emptyList(),
    val rejected: List<OpnameUnitRejected> = emptyList(),
    val session: OpnameDetailDto = OpnameDetailDto()
)

@Serializable
data class OpnameUnitListData(
    val items: List<OpnameUnitDto> = emptyList()
)

/**
 * Satu baris antrian validasi admin-stok — unit ketik-manual PLUS konteks
 * sesinya (antriannya lintas sesi, jadi pemutus harus tahu cabang & kode
 * opname-nya). Backend serde-flatten `OpnameUnit` ke objek yang sama, makanya
 * field unit diulang di sini alih-alih dibungkus [OpnameUnitDto].
 *
 * `fotoSnUrl`/`fotoBarangUrl` nullable walau kontraknya menjanjikan terisi:
 * baris lama (sebelum foto diwajibkan) bisa kosong, dan "memang tak ada foto"
 * WAJIB terlihat beda dari "foto gagal dimuat".
 */
@Serializable
data class ManualUnitDto(
    val id: String = "",
    val kodeBarang: String = "",
    val serialNumber: String = "",
    val kondisi: String = "layak",
    val temuan: String? = null,
    val keterangan: String? = null,
    val inputMethod: String = "manual",
    val validationStatus: String? = null,
    val fotoSnUrl: String? = null,
    val fotoBarangUrl: String? = null,
    val validatedByName: String? = null,
    val rejectReason: String? = null,
    val countedByUserId: String = "",
    val countedByName: String? = null,
    val countedAt: String = "",
    val sessionId: String = "",
    val kodeOpname: String = "",
    val dealerCode: String = "",
    val dealerName: String = "",
    val sessionStatus: String = ""
)

/** Body tolak — server 400 kalau `alasan` kosong; klien menolak lebih dulu. */
@Serializable
data class RejectUnitBody(
    val alasan: String
)

/**
 * Body approve massal. `unitIds` SELALU diisi eksplisit dari app — server
 * menerima daftar kosong sebagai "seluruh pending sesi ini", dan itu berarti
 * menyetujui unit yang tak pernah tampil di layar pemutusnya.
 */
@Serializable
data class ApproveBatchRequest(
    val unitIds: List<String>
)

/**
 * Hasil approve massal (`approve_manual_units_batch`).
 *
 * `gagal` BUKAN error jaringan: server memproses tiap unit sendiri-sendiri dan
 * menghitung unit yang sudah diputus orang lain di sela sebagai gagal. Jadi
 * `disetujui = 3, gagal = 1` adalah hasil yang SAH dan wajib dilaporkan apa
 * adanya, bukan diubah jadi pesan gagal.
 */
@Serializable
data class ApproveBatchData(
    val disetujui: Int = 0,
    val gagal: Int = 0,
    val sisaPending: Int = 0
)

@Serializable
data class ManualUnitListData(
    val count: Int = 0,
    val status: String = "",
    val items: List<ManualUnitDto> = emptyList()
)

/** `DELETE /api/inventory/opname/{id}` — sesi sudah lenyap, cuma id yg dikonfirmasi. */
@Serializable
data class OpnameDeleteData(
    val id: String = ""
)
