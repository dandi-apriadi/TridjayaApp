package com.krisoft.tridjayaelektronik.data.model

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import com.krisoft.tridjayaelektronik.data.KONDISI_LAYAK
import kotlinx.serialization.Serializable

/**
 * Alur pengiriman NYATA (SPK → antar) — wiring ke backend `inventory-service` modul delivery,
 * gateway `/api/inventory/delivery/...`. Envelope `{message,data}` (pakai [ApiResponse]). Semua DTO
 * camelCase 1:1 dengan serde backend. Pipeline status:
 * `pending_discount?` → `pending_pdi` → `pending_spk` → `pending_delivery_note` →
 * `pending_scheduling` → `assigned` → `in_transit` → `delivered` (+ `cancelled`).
 */
object DeliveryStatusKey {
    const val PENDING_DISCOUNT = "pending_discount"
    const val PENDING_PDI = "pending_pdi"

    /** Unit ditahan — ada jawaban checklist PDI "Tidak" (2026-08-04). Jalan
     *  keluar di app: PDI ULANG dengan semua jawaban OK (jalur a). Lepas paksa
     *  (jalur b) urusan kepala cabang lewat web. */
    const val PENDING_PERBAIKAN = "pending_perbaikan"
    const val PENDING_SPK = "pending_spk"
    const val PENDING_DELIVERY_NOTE = "pending_delivery_note"
    const val PENDING_SCHEDULING = "pending_scheduling"
    const val ASSIGNED = "assigned"
    const val IN_TRANSIT = "in_transit"
    const val DELIVERED = "delivered"
    const val CANCELLED = "cancelled"
}

/**
 * Balasan `PATCH /api/inventory/delivery/{id}` (sunting isi SPK, 2026-08-01).
 * [konsumenDiubah] = berapa unit sekode SPK yang ikut menerima perubahan data
 * konsumen — konsumen milik SPK, bukan milik barang.
 */
@Serializable
data class SpkEditResultDto(
    val job: DeliveryJobDto = DeliveryJobDto(),
    val konsumenDiubah: Int = 0,
)

/** Satu job pengiriman (1 unit fisik). Subset field yang dipakai app; semua opsional agar tahan null. */
/**
 * Isi field `warnaSelisih`. Lihat `DeliveryJobDto.warnaSelisih`.
 *
 * `jenis` sengaja String, bukan enum: nilai baru dari server tidak boleh
 * membuat parsing seluruh SPK gagal di APK lama. Yang tak dikenal diabaikan
 * oleh `pesanWarnaSelisih`.
 */
@Serializable
data class WarnaSelisihDto(
    val jenis: String? = null,
    val sku: String? = null,
    val diketik: String? = null,
)

@Serializable
data class DeliveryJobDto(
    val id: String = "",
    val kodePengiriman: String = "",
    val noTransaksi: String? = null,
    val baris: Int? = null,
    val unitSeq: Int? = null,
    val kodeDealer: String? = null,
    val dealerName: String? = null,
    val kodeCabang: String? = null,
    val tanggalJual: String? = null,
    val kodeBarang: String? = null,
    val namaBarang: String? = null,
    val kategori: String? = null,
    val merk: String? = null,
    val tipe: String? = null,
    val warna: String? = null,
    /**
     * Selisih warna SKU vs kolom `warna` (2026-08-10, `android-api.md`
     * §12.11b). DITURUNKAN server tiap job dibaca — bukan kolom DB.
     *
     * `null` berarti "tak ada yang perlu ditampilkan", BUKAN error: server
     * menghilangkan field ini saat warnanya cocok atau tak bisa dinilai, dan
     * server lama tak mengenalnya sama sekali. Aturannya milik
     * `delivery/warna.rs`; JANGAN dihitung ulang di app.
     */
    val warnaSelisih: WarnaSelisihDto? = null,
    val customerName: String? = null,
    val customerAddress: String? = null,
    val customerPhone: String? = null,
    /** Link Google Maps konsumen (086) — prasyarat backend sebelum assign driver. */
    val customerMapUrl: String? = null,
    val customerNik: String? = null,
    /** Pre Order ID (2026-07-24, opsional, per-barang). */
    val preOrderId: String? = null,
    /** URL foto PO (2026-07-24, opsional, per-barang). */
    val poPhotoUrl: String? = null,
    /** Metode pengiriman (2026-07-24): null/'driver' (default) | 'self_pickup' | 'sales_delivery'. */
    val deliveryMethod: String? = null,
    /** PDI wajib/tidak (2026-07-24, per-barang, independen dari deliveryMethod).
     *  `null` = backend lama tanpa kolom ini (perlakukan sbg true). */
    val pdiRequired: Boolean? = null,
    val fincoy: String? = null,
    val paymentType: String? = null,
    val hargaOtr: Double? = null,
    val diskon: Double? = null,
    val hargaTotal: Double? = null,
    // Pembiayaan per-unit (068)
    val dpNet: Double? = null,
    val pembayaran1: Double? = null,
    val angsuran: Double? = null,
    val tenor: Int? = null,
    val biayaAdm: Double? = null,
    val angsuranPertama: Double? = null,
    // Komisi + sumber order (068/080)
    val komisiKbk: Double? = null,
    val noHpKbk: String? = null,
    val orderSource: String? = null,
    val kbkBrokerKode: String? = null,
    val kbkBrokerNama: String? = null,
    val keterangan: String? = null,
    val salesName: String? = null,
    /** User id sales pembuat SPK (2026-07-24) — opsi "Sales antar sendiri" di assign driver. */
    val salesUserId: String? = null,
    // Sosmed konsumen (068, denormalisasi per baris)
    val sosmedTiktok: String? = null,
    val sosmedFacebook: String? = null,
    val sosmedInstagram: String? = null,
    val status: String = DeliveryStatusKey.PENDING_PDI,
    val inputChannel: String? = null,
    val serialNumber: String? = null,
    val engineNumber: String? = null,
    val pdiReadyPhotoUrl: String? = null,
    val pdiByName: String? = null,
    val pdiAt: String? = null,
    /** Klaim PDI (111, 2026-07-29) — petugas yang menekan "Ambil PDI"; server
     *  mengosongkannya lagi saat PDI selesai / TTL habis. `null` bisa berarti
     *  DUA hal: belum ada yang mengklaim, ATAU server lama yang belum kenal
     *  fitur ini. Pembedanya `DeliveryContextDto.pdiClaimTtlHours` — lihat
     *  `pdiClaimView` (DeliveryFlowScreens.kt). */
    val pdiClaimedBy: String? = null,
    val pdiClaimedByName: String? = null,
    val pdiClaimedAt: String? = null,
    val spkConfirmedBy: String? = null,
    val spkConfirmedAt: String? = null,
    val sourceBranch: String? = null,
    val deliveryNoteNo: String? = null,
    val deliveryNoteBy: String? = null,
    val deliveryNoteAt: String? = null,
    val assignedDriverId: String? = null,
    val assignedDriverName: String? = null,
    val scheduledDate: String? = null,
    val assignedByName: String? = null,
    val assignedAt: String? = null,
    val dispatchedAt: String? = null,
    val deliveryPhotoUrl: String? = null,
    val deliveredAt: String? = null,
    val deliveryLat: Double? = null,
    val deliveryLng: Double? = null,
    val deliveredBy: String? = null,
    val reviewRating: Int? = null,
    val reviewComment: String? = null,
    val reviewAt: String? = null,
    val cancelReason: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    // 088 — driver checklist/chat/terima uang. `driverTerimaUang != null` = penanda
    // backend 088 aktif (kolom NOT NULL → selalu terisi pasca-088).
    val consumerChatAt: String? = null,
    val driverTerimaUang: Boolean? = null,
    val driverTerimaNominal: Double? = null,
    val cashPhotoUrl: String? = null,
    /** COD Full Payment/DP (2026-07-25, migrasi 103) — "full" | "dp" | null. */
    val codPaymentMode: String? = null,
    /** Rencana DP sales saat SPK dibuat — beda dari `kasirDpDiterima` (aktual). */
    val codDpAmount: Double? = null,
    /** Cabang LOGIN sales pembuat SPK (2026-07-25, migrasi 104) — independen
     *  dari `kodeDealer` (cabang stok fisik unit). */
    val salesDealerCode: String? = null,
    val salesDealerName: String? = null,
    /**
     * Di cabang mana konsumen membayar (2026-08-12, migrasi 213) — "asal"
     * (cabang login sales) | "tujuan" (cabang stok) | `null`.
     *
     * `null` punya DUA arti yang sama-sama berujung "tujuan": SPK dibuat
     * sebelum fitur ini ada, ATAU server belum mengenal kolomnya. Jangan
     * membacanya langsung — pakai [lokasiBayarEfektif]
     * (`ui/deliveryflow/LokasiPembayaran.kt`).
     */
    val lokasiPembayaran: String? = null,
    /**
     * Kode dealer cabang tempat bayar EFEKTIF — DIHITUNG SERVER dari
     * [lokasiPembayaran] + `kodeDealer`/`salesDealerCode`. `null` = server lama.
     */
    val bayarDealerCode: String? = null,
    /**
     * Nama tampil cabang tempat bayar — **satu-satunya** sumber yang boleh
     * dipajang untuk "Bayar di: X". JANGAN dihitung ulang di app dari
     * `kodeDealer`/`salesDealerCode`: aturan turunannya milik server, dan dua
     * penurunan yang berselisih berarti kasir di HP dan kasir di web memutuskan
     * uang yang sama secara berbeda tanpa satu pun error.
     */
    val bayarDealerName: String? = null,
    /** Nominal DP AKTUAL diterima kasir (2026-07-25, migrasi 105) — beda dari
     *  `codDpAmount` (rencana sales). */
    val kasirDpDiterima: Double? = null,
    /** Kasir sudah konfirmasi cek pembayaran (2026-07-25, migrasi 105). */
    val kasirKonfirmasiPembayaran: Boolean = false,
    // Setoran driver→kasir (2026-07-25, migrasi 105) — non-blocking, kasir
    // konfirmasi terima balik uang COD dari driver setelah delivered.
    val setoranKasirNominal: Double? = null,
    val setoranKasirPhotoUrl: String? = null,
    val setoranKasirByNama: String? = null,
    val setoranKasirAt: String? = null,
    /** Timeline siap-render dari SERVER (2026-07-27) — hanya diisi endpoint
     *  DETAIL, tidak di list. Satu sumber untuk app & web (termasuk approval
     *  diskon + form aki dari tabel samping); kosong = server lama, app menyusun
     *  timeline-nya sendiri. */
    val timeline: List<TimelineStepDto> = emptyList(),
    /** Lama menunggu DI TAHAP SAAT INI, jam — DITURUNKAN server (`antrian::vonis`),
     *  bukan kolom DB. Absen (`null`) = status terminal atau server lama; jangan
     *  hitung ulang dari `createdAt` (itu umur sejak SPK dibuat, pertanyaan
     *  berbeda — lihat `delivery.rs` `umur_tahap_jam`). */
    val umurTahapJam: Long? = null,
    /** Sudah melewati `DELIVERY_STALL_HOURS` (default 24) DAN memang sedang
     *  menunggu. Vonisnya MILIK SERVER — tampilkan apa adanya, jangan
     *  dihitung ulang di app (dua definisi yang bisa berselisih untuk kata
     *  yang sama, kelas kegagalan yang sudah dibayar mahal di repo web). */
    val mandek: Boolean = false,
    /** Sudah menembus ambang eskalasi kepala cabang (120 jam). */
    val eskalasi: Boolean = false,
)

/** Satu tahap timeline SPK (`delivery/timeline.rs`). `tone`:
 *  done|active|pending|rejected|cancelled. */
@Serializable
data class TimelineStepDto(
    val key: String = "",
    val label: String = "",
    val timestamp: String? = null,
    val detail: String? = null,
    val tone: String = "pending"
)

/** Response `GET /api/inventory/delivery` (di dalam `data`). */
@Serializable
data class DeliveryListData(
    val items: List<DeliveryJobDto> = emptyList(),
    val page: Int? = null,
    val limit: Int? = null,
    /**
     * Baris yang lolos SELURUH saringan SEBELUM `LIMIT` — dikirim server sejak
     * `delivery.rs` menambahkannya ("halaman yang menampilkan 200 dari 431 diam
     * saja"). Field ini sebelumnya TIDAK ada di sini, jadi kotlinx membuangnya
     * lewat `ignoreUnknownKeys` dan app tak punya cara tahu daftarnya terpotong.
     *
     * `null` = server lama yang belum mengirimnya. Perlakukan sebagai "tidak
     * tahu", JANGAN sebagai nol — indikator "N dari M" harus diam, bukan
     * mengarang bahwa daftarnya utuh.
     */
    val total: Int? = null
)

/** Response `POST /api/inventory/delivery` (di dalam `data`). */
@Serializable
data class DeliveryCreateResult(
    val created: Int = 0,
    val kodePengiriman: List<String> = emptyList(),
    /** Sejajar `kodePengiriman` (2026-07-26) — dipakai auto-navigate PDI Mandiri,
     *  langsung tanpa reverse-lookup via search antrian (yang rapuh). */
    val ids: List<String> = emptyList(),
    val discountPending: Boolean = false
)

/**
 * Konteks cabang/dealer aktor untuk form input SPK (`GET /delivery/context`).
 * Backend (`delivery_context` di `delivery.rs`) balas key `kodeDealer`/`dealerName`/`cabangName`/`name`
 * — TIDAK ADA `kodeCabang` (nama field lama sebelumnya salah tebak, selalu null tak terpakai).
 */
@Serializable
data class DeliveryContextDto(
    val kodeDealer: String? = null,
    val dealerName: String? = null,
    val cabangName: String? = null,
    /** Kill-switch gate serah-terima driver 088 di server (H-1/checklist/foto uang).
     *  null = backend lama tanpa field → perlakukan seperti OFF (gate klien jadi
     *  warning saja): server yang gate-nya ON PASTI sudah membawa field ini. */
    val driverGateEnabled: Boolean? = null,
    /** Jeda minimum chat konsumen → serah terima (MENIT; 0 = chat wajib ditandai
     *  tapi tanpa tunggu). null = backend lama → fallback 60. */
    val chatMinMinutes: Int? = null,
    /** Umur maksimum klaim PDI (JAM, 111). Kehadiran field ini = satu-satunya
     *  penanda server sudah kenal klaim PDI; `null` (server lama ATAU konteks
     *  gagal dimuat) → app TIDAK menawarkan "Ambil PDI" sama sekali dan alur
     *  PDI persis seperti sebelumnya. */
    val pdiClaimTtlHours: Int? = null,
    /**
     * Ambang "barang besar" dalam RUPIAH (2026-08-05) — `app_settings`
     * `spk_barang_besar_threshold`, default Rp 1.500.000. Harga OTR DI ATAS
     * ambang = PDI per unit (checklist + no. rangka); di bawah/sama = boleh
     * lewat jalur massal `POST /delivery/{id}/pdi-kecil`.
     *
     * **Jangan hardcode 1.500.000** — angkanya bisa diubah di `app_settings`
     * tanpa deploy, dan app yang memakai angka sendiri akan menawarkan jalur
     * massal untuk unit yang server tolak 400 (atau sebaliknya menyembunyikannya
     * untuk unit yang sebenarnya boleh).
     *
     * `null` = server lama ATAU konteks gagal dimuat → [isBarangBesar]
     * memperlakukan SEMUA unit sebagai besar, jadi app kembali persis ke
     * perilaku PDI per unit sebelum fitur ini ada.
     */
    val barangBesarThreshold: Double? = null
)

/** Response upload foto (`POST /delivery/upload-photo`). */
@Serializable
data class DeliveryUploadResponse(val url: String = "")

/** Item checklist PDI per-kategori (`GET /delivery/config/checklist?kategori=`). */
@Serializable
data class ChecklistItemDto(
    val id: String = "",
    val kategori: String = "",
    val itemLabel: String = "",
    val urutan: Int = 0,
    val wajib: Boolean = false,
    val aktif: Boolean = true
)

@Serializable
data class ChecklistConfigData(val items: List<ChecklistItemDto> = emptyList())

/** Driver untuk dropdown assign (`GET /api/users?role=driver`). Field 1-kata → aman snake/camel;
 *  `cabang_name`/`is_active` snake_case (UserPublic auth-service TANPA rename_all camelCase). */
@Serializable
data class DriverDto(
    val id: String? = null,
    val userId: String? = null,
    val name: String = "",
    val nik: String? = null,
    val role: String? = null,
    /**
     * Nama cabang untuk DIPAJANG saja (mis. "Samrat", "Bahu", "Pagaden").
     *
     * JANGAN dipakai sebagai kunci region/cabang. Ia cerminan `cabang.nama` yang
     * ditulis ulang auth-service tiap user disimpan, jadi bentuknya berubah tanpa
     * migrasi apa pun menyentuh `auth_users` — migrasi 126 memendekkan
     * "Tridjaya Elektronik Manado Bahu" jadi "Bahu" dan mematikan filter yang
     * menebak region darinya (lihat [com.krisoft.tridjayaelektronik.ui.deliveryflow.driverBisaDitugaskan]).
     */
    @SerialName("cabang_name") val cabangName: String = "",
    /** `false` = akun dinonaktifkan → tak bisa login, jadi tak boleh ditugaskan.
     *  Default `true`: server lama tanpa field ini berperilaku persis seperti dulu. */
    @SerialName("is_active") val isActive: Boolean = true
) {
    val effectiveId: String get() = (id ?: userId).orEmpty()
}

@Serializable
data class UsersListData(val items: List<DriverDto> = emptyList())

/**
 * Baris stok GS (`GET /inventory/stok-cabang`) — dipakai autocomplete barang Input SPK.
 * JSON key PascalCase (asal GS), BEDA dari konvensi camelCase DTO lain di file ini —
 * kotlinx-serialization TIDAK case-insensitive, `@SerialName` eksplisit wajib per field.
 */
@Serializable
data class StokCabangRow(
    @SerialName("Kode") val kode: String = "",
    @SerialName("Nama") val nama: String = "",
    @SerialName("Kategori") val kategori: String = "",
    @SerialName("Merk") val merk: String = "",
    @SerialName("Tipe") val tipe: String = "",
    @SerialName("Harga") val harga: Double? = null,
    @SerialName("Stok") val stok: Int? = null,
    /**
     * Jumlah unit ber-SPK yang belum tuntas di cabang ini. Hanya dikirim server
     * saat picker meminta `includeDipesan=true`; nol/absen di jalur lain.
     *
     * Baris berstok NOL ber-nilai > 0 muncul SENGAJA: sebelum ini barang yang
     * notanya sudah diinput kasir ke GS lenyap dari picker tanpa keterangan dan
     * terbaca cabang sebagai "mutasi tak masuk stok" (2026-08-03, Samsung A57
     * Pagaden→Pamanukan). Baris begitu WAJIB tak bisa dipilih — lihat
     * [terkunciKarenaDipesan].
     */
    @SerialName("SudahDipesan") val sudahDipesan: Int? = null
) {
    /** Jumlah SPK berjalan atas barang ini; 0 kalau server tak mengirimnya. */
    val dipesan: Int get() = sudahDipesan?.takeIf { it > 0 } ?: 0

    /**
     * Tak boleh dipilih: stok habis DAN sudah ada SPK berjalan. Syarat stok-habis
     * WAJIB — barang yang masih berstok boleh dipesan lagi walau satu unitnya
     * sedang ber-SPK, itu penjualan normal. Stok negatif (GS menyimpannya saat
     * jual-dari-nol) dihitung habis.
     */
    val terkunciKarenaDipesan: Boolean get() = dipesan > 0 && (stok ?: 0) <= 0
}

/** Response `GET /api/inventory/stok-cabang` (di dalam `data`). */
@Serializable
data class StokCabangData(val items: List<StokCabangRow> = emptyList())

/** Broker KBK (`GET /inventory/delivery/brokers?q=`). camelCase 1:1. */
@Serializable
data class BrokerOption(val kode: String = "", val nama: String = "")

@Serializable
data class BrokerListData(val items: List<BrokerOption> = emptyList())

/**
 * Baris registry serial (`GET /inventory/serial-numbers`).
 *
 * `kondisi` = vonis admin-stok atas unit fisik ini (migrasi 194):
 * `layak` | `tidak_layak` | `repair` | `retur`. `null` = belum pernah
 * ditetapkan — itu BUKAN sinonim `layak`, tapi untuk picker SPK keduanya
 * sama-sama "tak ada alasan memperingatkan".
 */
@Serializable
data class SerialRegistryRow(
    val serialNumber: String = "",
    /**
     * `false` = baris ini TAG LEASING (AS BIKE / FIF KOPO / FIF SOETA), bukan
     * nomor seri unit fisik. Yang sebanding dengan jumlah stok hanya baris
     * ber-`true`; menghitung semua baris membuat "SN tercatat" tampak lebih
     * besar dari kenyataan dan sisa kebutuhan tampak lebih kecil.
     */
    val isSerial: Boolean = true,
    val kondisi: String? = null,
    val kondisiKeterangan: String? = null,
    /** Siapa & kapan kondisi terakhir ditetapkan; `null` = belum pernah. */
    val kondisiByName: String? = null,
    val kondisiAt: String? = null,
    /**
     * Asal-usul baris: `manual-input` (admin-stok mengetik), `manager-generated`
     * (kode `GEN-`), `usulan-cabang` (cabang mengusulkan, admin-stok menyetujui),
     * atau nama berkas impor Excel. Dipakai layar Input SN untuk menjelaskan
     * dari mana sebuah unit masuk registry — `createdByName` saja tak bisa
     * membedakan yang diketik sendiri dari yang disetujui dari usulan cabang.
     */
    val sourceFile: String = "",
    val createdByName: String? = null,
    val importedAt: String? = null
) {
    /** Perlu diperingatkan sebelum dijual. */
    val bermasalah: Boolean get() = kondisi != null && kondisi != KONDISI_LAYAK
}

@Serializable
data class SerialListData(val items: List<SerialRegistryRow> = emptyList())

/**
 * Cakupan SN satu kode barang di satu cabang (`GET /inventory/serial-numbers/summary`)
 * — bahan badge "SN 3/5" dan filter "belum lengkap" di layar Input Serial Number.
 *
 * Dihitung SERVER (GROUP BY), bukan diturunkan klien dari daftar registry: satu
 * cabang bisa punya baris registry jauh melebihi batas daftar, jadi menghitung
 * sendiri dari halaman yang sudah terpotong melaporkan cakupan terlalu kecil dan
 * menyuruh admin mendaftarkan ulang SN yang sebenarnya sudah ada.
 */
@Serializable
data class SerialCoverageRowDto(
    val kodeBarang: String = "",
    /** Seluruh baris registry, TERMASUK tag leasing. */
    val total: Int = 0,
    /** Baris ber-`isSerial` saja — INI yang sebanding dengan jumlah stok fisik. */
    val serial: Int = 0,
    val nonSerial: Int = 0
)

@Serializable
data class SerialCoverageData(
    val kodeDealer: String = "",
    val count: Int = 0,
    /**
     * `true` = daftar dipotong di batas server (8.000 kode). Produk yang TIDAK
     * ada di dalamnya tak boleh disimpulkan nol SN — lihat [com.krisoft.tridjayaelektronik.ui.serials.kelengkapanSerial].
     */
    val truncated: Boolean = false,
    val items: List<SerialCoverageRowDto> = emptyList()
)

/**
 * Konteks mutasi (`GET /inventory/mutasi/context`) — dipakai layar Input Serial Number
 * admin-stok utk resolve dealer sendiri sebelum POST manual. Respons penuh juga bawa
 * `canRequest`/`isManager`/`dealers` (form mutasi create/receive) — diabaikan di sini,
 * hanya field dealer sendiri yang relevan utk layar SN.
 */
@Serializable
data class MutasiContextDto(
    val sourceDealerCode: String? = null,
    val sourceDealerName: String? = null
)

/**
 * Body `POST /inventory/serial-numbers/requests` — cabang MENGUSULKAN SN, tidak
 * mendaftarkannya. Registry tetap ditulis admin-stok saat menyetujui; kalau app
 * menulis registry langsung, temuan `tidak_terdaftar` pada opname berikutnya
 * hilang dan sinyalnya ikut hilang.
 */
@Serializable
data class CreateSerialRequestBody(
    val kodeDealer: String,
    val kodeBarang: String,
    val namaBarang: String? = null,
    val serialNumber: String,
    val fotoSnUrl: String,
    val fotoBarangUrl: String,
    val opnameSessionId: String? = null,
    val catatan: String? = null
)

/** Satu usulan pendaftaran SN. Field keputusan (`decided*`) kosong selama `pending`. */
@Serializable
data class SerialRequestDto(
    val id: String = "",
    val kodeDealer: String = "",
    val kodeBarang: String = "",
    val namaBarang: String? = null,
    val serialNumber: String = "",
    val status: String = "pending",
    val catatan: String? = null,
    val alasanTolak: String? = null,
    val requestedByName: String? = null,
    val requestedAt: String? = null,
    val decidedByName: String? = null,
    val decidedAt: String? = null,
    /**
     * Lama usulan MENUNGGU keputusan admin-stok, jam — DITURUNKAN server
     * (`serials/requests.rs` `vonis_usulan`), bukan kolom DB. `null` = usulan
     * sudah diputuskan (tak menunggu apa pun lagi) ATAU server lama yang belum
     * mengirim field ini; jangan hitung ulang dari [requestedAt] — dua definisi
     * untuk satu kata adalah kelas kegagalan yang sudah dibayar mahal di
     * pipeline SPK.
     */
    val umurAntrianJam: Long? = null,
    /**
     * Sudah melewati `DELIVERY_STALL_HOURS` (default 24) — ambang yang SAMA
     * dengan pipeline SPK dan form aki. Server tak mengirim field ini saat
     * `false` (`skip_serializing_if`), jadi defaultnya WAJIB `false`: usulan
     * dari server lama terbaca "belum mandek", bukan lencana palsu.
     */
    val mandek: Boolean = false
)

@Serializable
data class SerialRequestListData(
    val count: Int = 0,
    /** Cabang yang di-scope server, atau "all" untuk pemutus/pengawas. */
    val kodeDealer: String = "",
    val items: List<SerialRequestDto> = emptyList()
)

/**
 * Body `POST /inventory/serial-numbers/generate` — kode pengganti SN untuk
 * barang tanpa serial pabrik (sofa, kursi). `jumlah` dibatasi 1–500 server.
 */
@Serializable
data class GenerateSerialBody(
    val kodeDealer: String,
    val kodeBarang: String,
    val namaBarang: String? = null,
    val jumlah: Int
)

@Serializable
data class GenerateSerialData(val generated: List<String> = emptyList())

/** Body `POST /inventory/serial-numbers` — input manual admin-stok (dipaksa dealer sendiri di backend). */
@Serializable
data class CreateSerialNumbersBody(
    val kodeDealer: String,
    val kodeBarang: String,
    val namaBarang: String? = null,
    val serialNumbers: List<String>
)

/**
 * Satu baris arsip mutasi (`GET /inventory/mutasi-histori`) — inventory-service
 * (`repository.rs::get_mutasi_histori`, MSSQL raw `tHeaderMutasiPart{IN,OUT}` digabung,
 * map generik bukan struct tetap — field di bawah adalah kolom yang benar-benar
 * di-`SELECT`/di-`insert` server, lihat source). Endpoint HISTORI-ONLY (arsip baca-saja,
 * bukan alur create/receive yang masih di balik flag `HISTORI_ONLY` di web) — tanpa gate
 * role server-side, RBAC halaman direplikasi di client (lihat `canAccessMutasiHistori`).
 */
@Serializable
data class MutasiHistoriRowDto(
    /** "IN" (barang masuk) | "OUT" (barang keluar). */
    val arah: String = "",
    val noTransaksi: String = "",
    /** Format ERP mentah `"YYYY-MM-DD HH:MM:SS"` — BUKAN ISO dgn `T`, parse manual. */
    val tanggal: String = "",
    val cabang: String = "",
    val cabangNama: String = "",
    val lawan: String = "",
    val lawanNama: String = "",
    val usernya: String = "",
    val totalQty: Int? = null,
    val jumlahItem: Int? = null
)

@Serializable
data class MutasiHistoriListDto(
    val count: Int = 0,
    val items: List<MutasiHistoriRowDto> = emptyList()
)

/**
 * `GET /inventory/mutasi/in-transit-self` — pembungkus satu petunjuk.
 *
 * `hint` NULL adalah jawaban SAH, bukan kegagalan: akun tanpa cabang dan
 * pemindaian yang habis waktu sama-sama dijawab 200 + null oleh server, supaya
 * layar pencarian stok tak memerahkan sesuatu yang cuma pelengkap.
 */
@Serializable
data class InTransitHintDto(
    val hint: InTransitHintRowDto? = null
)

/** Isi `data.hint`. Nama field 1:1 dengan `mutasi_in_transit.rs`. */
@Serializable
data class InTransitHintRowDto(
    val namaBarang: String = "",
    val tujuanCabang: String = "",
    val tanggal: String = ""
)

/** Satu baris detail barang 1 transaksi mutasi (`GET /inventory/mutasi-histori/detail`). */
@Serializable
data class MutasiHistoriDetailRowDto(
    val kodeBarang: String = "",
    val nama: String = "",
    val jumlah: Int? = null,
    /** Serial number — bisa string kosong (tak semua barang mutasi ber-SN tercatat ERP). */
    val sn: String = ""
)

@Serializable
data class MutasiHistoriDetailListDto(
    val count: Int = 0,
    val items: List<MutasiHistoriDetailRowDto> = emptyList()
)

/**
 * Body `POST /inventory/serial-numbers/kondisi` — vonis admin-stok atas unit
 * yang SUDAH terdaftar di registry. Satu panggilan = satu nilai `kondisi` untuk
 * sekumpulan serial dalam satu produk di satu cabang, jadi batch dengan kondisi
 * berbeda WAJIB dikirim sebagai panggilan terpisah.
 *
 * `kondisi` harus salah satu `KONDISI_PILIHAN` (cerminan `opname::KONDISI_VALID`);
 * server menolak nilai asing, bukan memaksanya jadi `layak`.
 */
@Serializable
data class SetKondisiBody(
    val kodeDealer: String,
    val kodeBarang: String,
    val serialNumbers: List<String>,
    val kondisi: String,
    val keterangan: String? = null
)

/**
 * Satu perubahan kondisi unit (`GET /inventory/serial-numbers/kondisi-log`),
 * cerminan baris `stock_serial_kondisi_log`.
 *
 * `kondisiLama` `null` = belum pernah ditetapkan siapa pun sebelum perubahan
 * itu — SENGAJA bukan `layak`. Membedakan keduanya adalah gunanya kolom ini:
 * baris impor Excel tak pernah dilihat admin-stok.
 */
@Serializable
data class SerialKondisiLogRowDto(
    val id: String = "",
    val kodeBarang: String = "",
    val serialNumber: String = "",
    val kondisiLama: String? = null,
    val kondisiBaru: String = "",
    val keterangan: String? = null,
    val changedByName: String? = null,
    val changedAt: String? = null
)

@Serializable
data class SerialKondisiLogData(
    val kodeDealer: String = "",
    val count: Int = 0,
    /** `true` = masih ada baris lebih tua di luar batas; JANGAN simpulkan ini
     *  seluruh riwayat unitnya. */
    val truncated: Boolean = false,
    val items: List<SerialKondisiLogRowDto> = emptyList()
)

@Serializable
data class SetKondisiResultDto(
    val updated: Int = 0,
    val skipped: List<SkippedSerialDto> = emptyList()
)

@Serializable
data class SkippedSerialDto(val serialNumber: String = "", val reason: String = "")

@Serializable
data class SerialCreateResultDto(val inserted: Int = 0, val skipped: List<SkippedSerialDto> = emptyList())

// ── Approval diskon per-baris (SPK) ──────────────────────────────────────────

@Serializable
data class DiscountJobSummaryDto(
    val kodeBarang: String? = null,
    val namaBarang: String? = null,
    val kategori: String? = null,
    val merk: String? = null,
    val tipe: String? = null,
    val customerName: String? = null,
    val customerPhone: String? = null
)

/** Pengajuan diskon (`GET /api/inventory/discount-requests`). camelCase 1:1. */
@Serializable
data class DiscountRequestDto(
    val id: String = "",
    val context: String = "",
    val spkBatchKode: String = "",
    val baris: Int? = null,
    val deliveryJobIds: List<String> = emptyList(),
    val jobSummary: DiscountJobSummaryDto? = null,
    val discountType: String = "",
    val value: Double = 0.0,
    val reason: String = "",
    /** Siapa yang meng-acc di luar sistem (2026-08-01) — klaim sales, teks
     *  bebas, TIDAK terverifikasi. Beda dari `decidedByName`. */
    val accOleh: String? = null,
    /** Foto bukti acc — path privat, diambil lewat `fetchPhoto`. */
    val buktiUrl: String? = null,
    val hargaSebelum: Double? = null,
    val hargaSesudah: Double? = null,
    val status: String = "pending",
    val requestedById: String = "",
    val requestedByName: String? = null,
    val decidedById: String? = null,
    val decidedByName: String? = null,
    val decidedAt: String? = null,
    val decisionNote: String? = null,
    val createdAt: String = ""
)

@Serializable
data class DiscountListData(
    val items: List<DiscountRequestDto> = emptyList(),
    val total: Int = 0,
    val page: Int = 1,
    val limit: Int = 20
)

/** Body approve/reject diskon. */
@Serializable
data class DecisionBody(val decisionNote: String? = null)

/**
 * Body pengajuan diskon baru (`POST /inventory/discount-requests`) — dipakai
 * jalur REVISI setelah ditolak. `context` sengaja tak dikirim: server
 * memasangnya `"spk"` bila absen, dan itu satu-satunya nilai yang didukung.
 *
 * `baris` WAJIB sejak 2026-08-02 (diskon per BARANG, bukan se-SPK); server
 * menolak 400 tanpa itu. `discountType` di app selalu `"amount"` — form SPK
 * mobile pun mengetik rupiah, jadi persen tak punya tempat memasukkan angkanya.
 */
@Serializable
data class CreateDiscountBody(
    val spkBatchKode: String,
    val baris: Int,
    val discountType: String = "amount",
    val value: Double,
    val reason: String,
)

/** Satu unit fisik SPK di kartu approval diskon (`GET .../discount-requests/spk/{kode}`). */
@Serializable
data class SpkDiscountUnitDto(
    val baris: Int = 0,
    val unitSeq: Int = 0,
    val kodePengiriman: String = "",
    val kodeBarang: String = "",
    val namaBarang: String? = null,
    val kategori: String? = null,
    val merk: String? = null,
    val tipe: String? = null,
    val warna: String? = null,
    val hargaOtr: Double? = null,
    val diskon: Double? = null,
    val hargaTotal: Double? = null,
    val status: String = "",
    val codPaymentMode: String? = null,
    val codDpAmount: Double? = null,
    val driverTerimaUang: Boolean = false,
)

/**
 * SPK utuh untuk kartu approval diskon — SELURUH unit se-batch, termasuk yang
 * TIDAK berdiskon. Keputusan diskon mem-fan-out ke satu SPK penuh (2026-08-06),
 * jadi approver harus melihat SPK-nya, bukan potongan baris yang kebetulan
 * mengajukan.
 *
 * PII sengaja DIPANGKAS di server (NIK, alamat, titik lokasi, sosmed,
 * komisi/no HP KBK, nomor rangka/mesin, seluruh `*PhotoUrl`) karena approver
 * diskon bisa siapa saja pemegang page-grant, LINTAS CABANG. JANGAN menambah
 * field di sini dengan harapan server mengirimnya.
 */
@Serializable
data class SpkDiscountContextDto(
    val spkBatchKode: String = "",
    val kodeDealer: String = "",
    val dealerName: String? = null,
    val kodeCabang: String? = null,
    val salesDealerCode: String? = null,
    val tanggalJual: String? = null,
    val salesName: String? = null,
    val orderSource: String? = null,
    val kbkBrokerNama: String? = null,
    val customerName: String? = null,
    val customerPhone: String? = null,
    val paymentType: String? = null,
    val fincoy: String? = null,
    val biayaAdm: Double? = null,
    val angsuranPertama: Double? = null,
    val dpNet: Double? = null,
    val pembayaran1: Double? = null,
    val angsuran: Double? = null,
    val tenor: Int? = null,
    val deliveryMethod: String? = null,
    val keterangan: String? = null,
    val totalHargaOtr: Double = 0.0,
    val totalDiskonBerjalan: Double = 0.0,
    val totalSetelahDiskon: Double = 0.0,
    val units: List<SpkDiscountUnitDto> = emptyList(),
)

// ── Request bodies ───────────────────────────────────────────────────────────

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class CreateDeliveryItemBody(
    val kodeBarang: String,
    val namaBarang: String,
    val kategori: String,
    val merk: String,
    val tipe: String,
    val qty: Int = 1,
    val warna: String? = null,
    val serialNumber: String? = null,
    /** Pre Order ID (2026-07-24, per-barang — melekat ke produk). */
    val preOrderId: String? = null,
    /** URL foto PO hasil upload (per-barang). */
    val poPhotoUrl: String? = null,
    /** PDI wajib/tidak (2026-07-24, per-barang, independen dari deliveryMethod
     *  SPK). `null`/absen = default true. `false` = skip PDI beneran, sales
     *  bertanggung jawab cek barang sendiri (biasanya barang kecil). */
    val pdiRequired: Boolean? = null,
    // ponytail: paksa selalu ter-serialize — Retrofit Json (encodeDefaults=false) buang field
    // yang = default, tapi backend butuh paymentType eksplisit walau nilainya "cash".
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val paymentType: String = "cash",
    val fincoy: String? = null,
    val hargaOtr: Double,
    val diskon: Double? = null,
    val alasanDiskon: String? = null,
    /** Siapa yang meng-acc diskon di luar sistem (2026-08-01) — opsional,
     *  hanya dikirim kalau barang ini berdiskon. */
    val accDiskon: String? = null,
    /** URL foto bukti acc hasil upload-photo — opsional. */
    val buktiDiskonUrl: String? = null,
    val dpNet: Double? = null,
    val pembayaran1: Double? = null,
    val angsuran: Double? = null,
    val tenor: Int? = null,
    val komisiKbk: Double? = null,
    val noHpKbk: String? = null,
    val orderSource: String? = null,
    val kbkBrokerKode: String? = null,
    val kbkBrokerNama: String? = null,
    /** 088: driver terima uang dari konsumen (gate foto uang saat deliver). */
    val driverTerimaUang: Boolean? = null,
    /** 2026-07-25: driverTerimaNominal DIHITUNG backend dari codPaymentMode/
     *  codDpAmount + hargaOtr, bukan lagi input manual — field ini tak dikirim lagi. */
    val codPaymentMode: String? = null,
    val codDpAmount: Double? = null,
    val kodeDealer: String? = null,
    val kodeCabang: String? = null
)

@Serializable
data class CreateDeliveryBody(
    val customerName: String,
    val customerPhone: String,
    val customerAddress: String? = null,
    val customerMapUrl: String? = null,
    val customerNik: String? = null,
    val salesNik: String? = null,
    /** Metode pengiriman (2026-07-24, opsional): kosong = 'driver' (default) |
     *  'self_pickup' | 'sales_delivery'. Body-level, denormalisasi backend ke semua barang. */
    val deliveryMethod: String? = null,
    /**
     * Lokasi pembayaran SPK (2026-08-12, migrasi 213) — "asal" | "tujuan".
     * **Body-level, BUKAN per barang**: satu SPK dibayar di satu tempat.
     *
     * SENGAJA `String?` ber-default `null` DAN SENGAJA selalu diisi eksplisit
     * di call site ([CreateSpkScreen]). Retrofit `Json` di `NetworkModule`
     * memakai `encodeDefaults = false`, jadi field yang nilainya sama dengan
     * default-nya TIDAK IKUT TERKIRIM tanpa error apa pun — memberi default
     * non-null di sini ("asal") akan membuat pilihan sales lenyap diam-diam
     * persis pada kasus yang paling sering. Nilai non-null selalu berbeda dari
     * default `null` sehingga selalu ter-serialize; `null` memang dimaksudkan
     * absen (server membacanya sbg perilaku lama = "tujuan").
     */
    val lokasiPembayaran: String? = null,
    val sosmedTiktok: String? = null,
    val sosmedFacebook: String? = null,
    val sosmedInstagram: String? = null,
    val keterangan: String? = null,
    val tanggalJual: String? = null,
    val items: List<CreateDeliveryItemBody>
)

/** Body `POST /delivery/{id}/self-pickup-complete` (2026-07-24) — konsumen ambil unit
 *  sendiri di cabang, DC/admin tandai selesai. Foto+rating wajib, sama standar [DeliverBody]. */
@Serializable
data class SelfPickupCompleteBody(
    val photoUrl: String,
    val reviewRating: Int,
    val reviewComment: String? = null
)

@Serializable
data class PdiChecklistItemBody(
    val item: String,
    val hasil: String,          // "ok" | "tidak" | "na"
    val catatan: String? = null
)

@Serializable
data class PdiBody(
    val serialNumber: String,
    val engineNumber: String? = null,
    val readyPhotoUrl: String? = null,
    val checklist: List<PdiChecklistItemBody> = emptyList()
)

/** Body wajib `POST .../spk` sejak 2026-07-25 (migrasi 105) — sebelumnya
 *  endpoint ini tanpa body sama sekali, sekarang backend WAJIB `Content-Type:
 *  application/json` + `noTransaksi` non-kosong (axum `Json` extractor 415
 *  kalau content-type absen/salah — root cause "gagal konfirmasi SPK 415"). */
@Serializable
data class ConfirmSpkBody(
    val noTransaksi: String,
    val kasirDpDiterima: Double? = null,
    val kasirKonfirmasiPembayaran: Boolean? = null,
    /**
     * Nominal DP AKTUAL per unit (2026-08-05) untuk SPK yang unit COD `dp`-nya
     * lebih dari satu. Konfirmasi kasir kini FAN-OUT se-SPK: satu panggilan
     * mengonfirmasi seluruh unit `pending_spk` sebatch dengan `noTransaksi`
     * yang sama, jadi tanpa daftar ini tak ada tempat menyebutkan DP unit
     * SELAIN unit yang dipanggil.
     *
     * Aturan server: **kalau dikirim, tiap unit COD `dp` sebatch wajib
     * bernominal** (kalau tidak → 400 `"Nominal DP unit <kode> wajib diisi"`,
     * divalidasi SEBELUM ada status yang bergerak). Kalau TIDAK dikirim
     * (`null`, jalur APK lama), unit lain memakai fallback `codDpAmount`
     * rencana sales — bukan salah, tapi angkanya rencana, bukan yang benar-benar
     * diterima kasir.
     */
    val units: List<ConfirmSpkUnitBody>? = null,
)

/** Satu unit di dalam [ConfirmSpkBody.units]. */
@Serializable
data class ConfirmSpkUnitBody(
    val id: String,
    val kasirDpDiterima: Double? = null,
)

/**
 * Kasir konfirmasi pembayaran unit yang sudah sampai konsumen. Berlaku untuk
 * SEMUA jenis pembayaran (bukan cuma COD) sejak 2026-07-28 — nominal + foto
 * bukti sama-sama wajib, yang beda cuma sumber uangnya.
 */
@Serializable
data class SetoranKasirBody(
    val nominalDiterima: Double,
    val photoUrl: String
)

@Serializable
data class DeliveryNoteBody(
    val sourceBranch: String,
    val customerName: String? = null,
    val customerAddress: String? = null,
    val customerPhone: String? = null,
    val deliveryNoteNo: String? = null
)

@Serializable
data class AssignBody(
    val driverId: String,
    val driverName: String? = null,
    val scheduledDate: String,
    val customerMapUrl: String? = null
)

@Serializable
data class DeliverBody(
    val photoUrl: String,
    val lat: Double? = null,
    val lng: Double? = null,
    val reviewRating: Int,
    val reviewComment: String? = null,
    /** 088: checklist serah-terima driver (kategori ber-item stage=driver). */
    val checklist: List<PdiChecklistItemBody>? = null,
    /** 088: foto bukti terima uang (wajib bila job.driverTerimaUang). */
    val cashPhotoUrl: String? = null
)

/** Body reorder muatan driver (`POST /delivery/driver/reorder`) — array posisi = urutan muat. */
@Serializable
data class ReorderBody(val orderedIds: List<String>)

@Serializable
data class ReorderResult(val count: Int = 0)

// ── Form pengambilan aki (PDI gate, migrasi 082) ─────────────────────────────

/** Kategori PDI (`GET /delivery/config/categories`) — `requiresAkiForm` = gate hard-block submit PDI. */
@Serializable
data class DeliveryCategoryDto(
    val id: String = "",
    val kategori: String = "",
    val requiresAkiForm: Boolean = false,
    val aktif: Boolean = true
)

@Serializable
data class DeliveryCategoriesData(val items: List<DeliveryCategoryDto> = emptyList())

/** Form pengambilan aki (`aki.rs` — subset field yang dipakai app). */
@Serializable
data class AkiFormDto(
    val id: String = "",
    val deliveryJobId: String = "",
    /**
     * Isi unit di balik form — approver butuh ini untuk tahu aki ini diambilkan
     * untuk barang & konsumen yang mana. Dibaca server LIVE dari `delivery_jobs`
     * (join saat query daftar, TIDAK didenormalisasi), jadi `null` pada respons
     * langsung create/approve/reject yang tak di-join; hanya terisi di daftar.
     */
    val jobKodePengiriman: String? = null,
    val jobNamaBarang: String? = null,
    val jobKategori: String? = null,
    val jobCustomerNama: String? = null,
    val kodeDealer: String = "",
    val cabangNama: String? = null,
    val tanggal: String = "",
    /** HH:MM — form kertas kadang tak mengisi jam. */
    val jam: String? = null,
    val pengambilNama: String = "",
    val pengambilJabatan: String? = null,
    val tujuan: String = "",
    val tujuanLainnya: String? = null,
    val merkTipe: String = "",
    val jumlahPcs: Int = 0,
    /**
     * Kapasitas/charger/spion — DIKIRIM server sejak dulu (`aki.rs` struct
     * respons), tapi baru dibaca app 2026-08-06. Sebelumnya keempat field ini
     * hanya ada di [CreateAkiFormBody] (arah KIRIM), sehingga app bisa membuat
     * form ber-charger lalu tak pernah bisa menampilkannya lagi — charger &
     * kaca spion praktis tak terlihat di mana pun dari HP.
     *
     * Dipakai [kelengkapanDariAkiForms] untuk menurunkan baris baterai/charger
     * yang ikut diserahkan bersama unitnya.
     */
    val kapasitas: String? = null,
    val jumlahKeterangan: String? = null,
    val keterangan: String? = null,
    val ambilCharger: Boolean = false,
    val ambilKacaSpion: Boolean = false,
    /** Foto bukti aki (2026-07-24) — wajib diisi PDI saat submit. `null` = form lama sebelum fitur ini. */
    val photoUrl: String? = null,
    val akiBekasStatus: String = "belum",
    val akiBekasJumlah: Int? = null,
    val akiBekasKeterangan: String? = null,
    val akiBekasReturnedAt: String? = null,
    /** `rejected` bila ditolak, `approved` bila approver PUSAT (`aki_approver`)
     *  sudah menyetujui (redesain 2026-07-24, dulu 3 slot kepala-cabang+admin-
     *  penjualan+kasir/aki-approver); selain itu `pending`. PDI di-gate backend
     *  sampai `approved`. */
    val approvalStatus: String = "pending",
    val rejectedByNama: String? = null,
    val rejectedReason: String? = null,
    val rejectedAt: String? = null,
    /** Satu-satunya slot yang masih ditulis backend (approver pusat). */
    val akiApproverApprovedNama: String? = null,
    val akiApproverApprovedAt: String? = null,
    /** Dipakai timeline detail SPK (2026-07-27): kapan & siapa yang mengisi form,
     *  plus penentu form MANA yang berlaku kalau ada pengajuan ulang. */
    val createdAt: String = "",
    val createdById: String = "",
    val createdByNama: String = ""
)

@Serializable
data class AkiFormsData(val items: List<AkiFormDto> = emptyList())

/** Wrapper create (`POST /delivery/{id}/aki-form` → `data.form`, BUKAN objek langsung). */
@Serializable
data class AkiFormCreateData(val form: AkiFormDto = AkiFormDto())

/** Body create (`aki.rs:107-133`, camelCase; tujuan+merkTipe+jumlahPcs wajib; pengambil = actor). */
@Serializable
data class CreateAkiFormBody(
    val tujuan: String,
    val merkTipe: String,
    val jumlahPcs: Int,
    val tujuanLainnya: String? = null,
    val kapasitas: String? = null,
    val jumlahKeterangan: String? = null,
    val keterangan: String? = null,
    val ambilCharger: Boolean = false,
    val ambilKacaSpion: Boolean = false,
    /** Wajib (2026-07-24) — URL hasil upload lewat endpoint upload-photo generic. */
    val photoUrl: String
)

/** Body tandai aki bekas dikembalikan (`POST /aki-forms/{id}/return`); kosong = default backend. */
@Serializable
data class ReturnAkiBody(val jumlah: Int? = null, val keterangan: String? = null)

/** Body tolak form aki (`POST /aki-forms/{id}/reject`). `reason` wajib (backend 400 kalau kosong). */
@Serializable
data class RejectAkiBody(val reason: String)

/** Preferensi WA alur SPK (`GET/PUT /inventory/discount-requests/wa-pref`). `spkWaOptout=true`
 *  → user matikan WA (dapat notif app push saja, anti-double). Default false = WA tetap ON. */
@Serializable
data class WaPrefDto(val spkWaOptout: Boolean = false)

// ---------------------------------------------------------------------------
// Direktori petugas + panduan alur (`GET /inventory/delivery/petugas`, WP7).
// Muatan server SENGAJA minimal (nama + WA saja, tanpa NIK/email/jabatan) —
// jangan menambah field di sini tanpa backend yang benar-benar mengirimnya.
// ---------------------------------------------------------------------------

/** Satu orang di direktori. `whatsapp` null = tetap ditampilkan, tapi tak bisa dihubungi. */
@Serializable
data class PetugasDto(val nama: String = "", val whatsapp: String? = null)

/** Satu peran yang pernah dilakukan atas unit ini. `waktu` null = tercatat
 *  sebagai pelakunya tapi tanpa jam (kolom lama / worker GS). */
@Serializable
data class KontributorPeranDto(val label: String = "", val waktu: String? = null)

/**
 * Karyawan yang BENAR-BENAR menyentuh unit ini, beda dari [PetugasDto] yang
 * daftarnya jabatan se-cabang. Saat sebuah unit bermasalah, yang dicari orang
 * adalah "siapa yang meng-PDI unit INI", bukan "siapa saja petugas PDI".
 *
 * `karyawanId` dikirim server hanya bila akunnya masih ada; di app belum ada
 * layar profil karyawan sehingga field itu belum dipakai — tindakan per orang
 * di sini adalah WhatsApp, sama seperti direktori petugas. Web memakainya untuk
 * menautkan ke halaman statistik karyawan.
 */
@Serializable
data class KontributorDto(
    val karyawanId: String? = null,
    val nama: String = "",
    val whatsapp: String? = null,
    val peran: List<KontributorPeranDto> = emptyList(),
)

/**
 * Satu kelompok tugas. Server SELALU mengirim enam kelompok berurutan
 * (sales, pdi, kasir, delivery-control, driver, kepala-cabang) termasuk yang
 * kosong — klien tak perlu bercabang untuk kelompok yang tak ada.
 *
 * [lintasCabang] true = orangnya melayani semua cabang (saat ini
 * delivery-control). Tampilkan keterangannya dari flag ini, JANGAN hardcode
 * [kunci] — kelompok lintas-cabang bisa bertambah di server.
 */
@Serializable
data class PetugasGroupDto(
    val kunci: String = "",
    val label: String = "",
    val lintasCabang: Boolean = false,
    val petugas: List<PetugasDto> = emptyList(),
)

/** Satu tahap alur. Kalimatnya datang dari server supaya koreksi teks tak menuntut rilis APK. */
@Serializable
data class TahapAlurDto(
    val status: String = "",
    val aktor: String = "",
    val keterangan: String = "",
)

@Serializable
data class PetugasDirektoriDto(
    val kodeDealer: String? = null,
    val dealerName: String? = null,
    val divisi: List<PetugasGroupDto> = emptyList(),
    val tahapan: List<TahapAlurDto> = emptyList(),
)
