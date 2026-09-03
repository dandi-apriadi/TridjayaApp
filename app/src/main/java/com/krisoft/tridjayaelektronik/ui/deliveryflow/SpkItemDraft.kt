package com.krisoft.tridjayaelektronik.ui.deliveryflow

import com.krisoft.tridjayaelektronik.data.model.CreateDeliveryItemBody
import com.krisoft.tridjayaelektronik.data.model.StokCabangRow

// Mirror web companyFacts.financingPartners. Sentinel Lainnya → free-text.
internal val FINCOY_PARTNERS = listOf("Adira Finance", "Spektra", "Kredivo", "Akulaku", "Indodana", "Home Credit")
internal const val FINCOY_LAINNYA = "__lainnya__"

/** Satu barang dalam SPK multi-unit — tiap barang bawa pembayaran/komisi/order sendiri
 *  (mirror web `SaleItemDraft`, migrasi 068/080/088). Semua uang = string digit mentah. */
data class SpkItemDraft(
    val kodeBarang: String,
    val namaBarang: String,
    val kategori: String,
    val merk: String,
    val tipe: String,
    val stokTersedia: Int? = null,
    val qty: String = "1",
    val warna: String = "",
    val serialNumber: String = "",
    /** Pre Order ID (2026-07-24, per-barang — melekat ke produk). */
    val preOrderId: String = "",
    /** URL foto PO hasil upload (per-barang). */
    val poPhotoUrl: String = "",
    /** PDI wajib/tidak (2026-07-24, per-barang, independen dari delivery
     *  method) — default true. false = skip PDI beneran, sales tanggung
     *  jawab sendiri (biasanya barang kecil). */
    val pdiRequired: Boolean = true,
    val hargaOtr: String = "",
    val diskon: String = "",
    val alasanDiskon: String = "",
    /** Siapa yang meng-acc diskon ini di luar sistem (2026-08-01). */
    val accDiskon: String = "",
    /** URL foto bukti acc hasil upload. */
    val buktiDiskonUrl: String = "",
    val paymentType: String = "cash",
    val fincoy: String = "",
    val fincoyLain: String = "",
    val dpNet: String = "",
    val pembayaran1: String = "",
    val angsuran: String = "",
    val tenor: String = "",
    val orderSource: String = "sales",
    val kbkBrokerKode: String = "",
    val kbkBrokerNama: String = "",
    val komisiKbk: String = "",
    val noHpKbk: String = "",
    /** COD (2026-07-25, cash-only): driver terima uang dari konsumen saat serah terima. */
    val driverTerimaUang: Boolean = false,
    /** Sub-pilihan COD: "full" | "dp" | "" (belum dipilih). Kosong = belum dipilih. */
    val codPaymentMode: String = "",
    /** Jumlah DP yang sudah diterima toko — digit mentah, wajib bila codPaymentMode="dp". */
    val codDpAmount: String = "",
    /**
     * UI: blok Diskon sengaja dibuka sales (progressive disclosure 2026-08-12).
     *
     * Murni tampilan — tak pernah dikirim ke server, sama seperti [expanded].
     * Kredit/COD/KBK tak butuh bendera semacam ini karena pemicunya sudah data
     * nyata (`paymentType`, `driverTerimaUang`, `orderSource`); diskon tak
     * punya, dan menyimpulkannya dari "nominal diskon terisi" saja membuat blok
     * yang baru dibuka langsung menutup diri lagi sebelum sempat diketik.
     */
    val diskonDibuka: Boolean = false,
    /** UI: kartu terbuka/tutup (baru ditambah = terbuka). */
    val expanded: Boolean = true,
) {
    private fun money(v: String): Double? = v.filter { it.isDigit() }.toDoubleOrNull()

    val qtyInt: Int? get() = qty.trim().toIntOrNull()
    val fincoyResolved: String get() = if (fincoy == FINCOY_LAINNYA) fincoyLain.trim() else fincoy.trim()
    val isCredit: Boolean get() = paymentType == "credit"
    val isKbk: Boolean get() = orderSource == "kbk"

    /**
     * Blok Diskon WAJIB terlihat.
     *
     * Sengaja BUKAN sekadar [diskonDibuka]: begitu salah satu isian diskon
     * terisi, bloknya harus terlihat apa pun keadaan tombolnya. Kalau tidak,
     * "Alasan diskon wajib diisi" / "Foto bukti acc wajib…" dari [issues]
     * mematikan tombol Simpan sambil menyembunyikan justru field yang harus
     * diperbaiki — kelas kegagalan yang tak memunculkan error apa pun, cuma
     * tombol yang tak mempan.
     *
     * Pasangannya [tanpaDiskon]: menutup blok = mengosongkan isinya, jadi
     * "tertutup" dan "tak ada isian diskon" selalu berarti hal yang sama.
     */
    val blokDiskonTerlihat: Boolean
        get() = diskonDibuka || diskon.isNotBlank() || alasanDiskon.isNotBlank() ||
            accDiskon.isNotBlank() || buktiDiskonUrl.isNotBlank()

    /** Menutup blok Diskon: bendera turun DAN seluruh isiannya dikosongkan. */
    fun tanpaDiskon(): SpkItemDraft = copy(
        diskonDibuka = false, diskon = "", alasanDiskon = "", accDiskon = "", buktiDiskonUrl = "",
    )

    /** Validasi mirror server `create_delivery` (subset yang relevan input mobile). */
    fun issues(): List<String> {
        val out = mutableListOf<String>()
        val harga = money(hargaOtr) ?: 0.0
        if (harga <= 0) out += "Harga wajib > 0"
        val d = money(diskon) ?: 0.0
        if (d > 0 && alasanDiskon.trim().isBlank()) out += "Alasan diskon wajib diisi (diskon > 0)"
        // Cerminan guard server (`create_delivery`): menyebut pemberi acc =
        // mengaku diskonnya sudah disetujui DI LUAR sistem, dan approver tak
        // punya cara memeriksa klaim itu selain fotonya. Menempel ke "acc
        // diisi", BUKAN ke "ada diskon" — diskon biasa memang menunggu
        // approval di dalam sistem dan tak perlu foto apa pun.
        if (d > 0 && accDiskon.trim().isNotBlank() && buktiDiskonUrl.trim().isBlank()) {
            out += "Foto bukti acc wajib kalau diskon sudah di-acc di luar sistem"
        }
        if (isCredit && fincoyResolved.isBlank()) out += "Fincoy/leasing wajib utk kredit"
        // Cerminan guard server pasca-2026-08-07: per-baris tinggal `qty < 1`.
        // Batas ATAS sengaja TIDAK diulang di sini — ia batas se-SPK
        // ([MAX_SPK_UNIT]) dan hidup di [spkSubmitBlocker] dengan teks yang
        // persis sama dengan server. Dua pesan untuk satu aturan membuat sales
        // memperbaiki hal yang salah ("Qty harus 1..10" pada baris tunggal yang
        // sebenarnya melanggar batas SPK, bukan batas baris).
        val q = qtyInt
        if (q == null || q < 1) out += "Qty minimal 1"
        else stokTersedia?.let { if (q > it) out += "Qty melebihi stok ($it)" }
        // Cerminan guard server (`create_delivery`, 2026-08-07): satu serial
        // menandai SATU unit fisik. Dulu nilainya disalin ke seluruh unit baris
        // (`for unit_seq in 1..=line.qty`), jadi qty=3 melahirkan tiga job
        // ber-SN identik dan SN berhenti menjadi identitas unit.
        //
        // Menempel ke "serial diisi DAN qty>1", BUKAN ke qty>1 saja — SPK qty
        // banyak tanpa serial adalah alur normal dan tak boleh ikut mati.
        if (serialNumber.trim().isNotBlank() && (q ?: 1) > 1) {
            out += "Serial hanya untuk qty 1 — pisahkan jadi baris sendiri, atau isi saat PDI"
        }
        if (isKbk && (kbkBrokerKode.isBlank() || kbkBrokerNama.isBlank())) out += "Broker KBK wajib dipilih"
        if (driverTerimaUang) {
            when (codPaymentMode) {
                "full" -> {}
                "dp" -> {
                    val dp = money(codDpAmount) ?: 0.0
                    if (dp <= 0) out += "Jumlah DP wajib diisi"
                    else if (dp >= harga) out += "DP harus lebih kecil dari Total"
                }
                else -> out += "Metode COD wajib dipilih (Full Payment/DP)"
            }
        }
        return out
    }

    /**
     * Header kartu saat collapse.
     *
     * Ikut menyebut pemicu yang aktif (Diskon/COD/KBK) sejak kartunya
     * menyembunyikan blok-blok itu secara default: tanpa itu, satu-satunya
     * tanda bahwa barang ini punya diskon atau COD adalah membuka kartunya
     * satu per satu.
     *
     * "PDI mandiri", BUKAN "Tanpa PDI" — sejak backend 2026-07-27 tak ada lagi
     * rute melewati PDI; yang berubah cuma SIAPA yang mengerjakannya.
     */
    fun summaryLine(): String {
        val bayar = if (isCredit) "Kredit" else "Cash"
        val tambahan = buildList {
            if ((money(diskon) ?: 0.0) > 0) add("Diskon")
            if (driverTerimaUang) add("COD")
            if (isKbk) add("KBK")
            if (!pdiRequired) add("PDI mandiri")
        }
        return "${namaBarang} · ${qty}x · $bayar${money(hargaOtr)?.let { " · Rp${it.toLong()}" } ?: ""}" +
            tambahan.joinToString("") { " · $it" }
    }

    fun toItemBody(kodeDealer: String, kodeCabang: String): CreateDeliveryItemBody {
        val d = money(diskon)?.takeIf { it > 0 }
        return CreateDeliveryItemBody(
            kodeBarang = kodeBarang.trim(), namaBarang = namaBarang.trim(), kategori = kategori,
            merk = merk, tipe = tipe, qty = qtyInt ?: 1,
            warna = warna.trim().ifBlank { null },
            serialNumber = serialNumber.trim().ifBlank { null },
            preOrderId = preOrderId.trim().ifBlank { null },
            poPhotoUrl = poPhotoUrl.trim().ifBlank { null },
            pdiRequired = if (pdiRequired) null else false,
            paymentType = paymentType,
            fincoy = if (isCredit) fincoyResolved.ifBlank { null } else null,
            hargaOtr = money(hargaOtr) ?: 0.0,
            diskon = d,
            alasanDiskon = if (d != null) alasanDiskon.trim().ifBlank { null } else null,
            // Tanpa diskon tak ada pengajuan yang dibuat, jadi buktinya tak
            // ikut terkirim — pola sama `alasanDiskon` di atas.
            accDiskon = if (d != null) accDiskon.trim().ifBlank { null } else null,
            buktiDiskonUrl = if (d != null) buktiDiskonUrl.trim().ifBlank { null } else null,
            dpNet = if (isCredit) money(dpNet) else null,
            pembayaran1 = if (isCredit) money(pembayaran1) else null,
            angsuran = if (isCredit) money(angsuran) else null,
            tenor = if (isCredit) tenor.filter { it.isDigit() }.toIntOrNull() else null,
            komisiKbk = if (isKbk) money(komisiKbk) else null,
            noHpKbk = if (isKbk) noHpKbk.trim().ifBlank { null } else null,
            orderSource = if (isKbk) "kbk" else null,
            kbkBrokerKode = if (isKbk) kbkBrokerKode.trim().ifBlank { null } else null,
            kbkBrokerNama = if (isKbk) kbkBrokerNama.trim().ifBlank { null } else null,
            driverTerimaUang = if (driverTerimaUang) true else null,
            codPaymentMode = if (driverTerimaUang) codPaymentMode.ifBlank { null } else null,
            codDpAmount = if (driverTerimaUang && codPaymentMode == "dp") money(codDpAmount) else null,
            kodeDealer = kodeDealer, kodeCabang = kodeCabang
        )
    }
}

/**
 * Batas SPK — cerminan `MAX_MANUAL_LINES`/`MAX_MANUAL_UNITS` di
 * `inventory-service` `delivery.rs`. KEDUANYA ditegakkan: 10 baris barang DAN
 * 10 unit total (10 baris qty 1 lolos; 5 baris qty 3 tidak). Pesan galatnya
 * WAJIB sama persis dengan server — kalau berbeda, sales yang ditolak server
 * membaca kalimat yang tak pernah ia lihat di app dan mengira SPK-nya rusak.
 */
const val MAX_SPK_BARIS = 10
const val MAX_SPK_UNIT = 10

/**
 * Alasan form SPK belum boleh dikirim, atau `null` kalau sudah boleh. Satu
 * sumber untuk tombol Simpan DAN pesan merah di bawahnya — dulu dua daftar
 * syarat terpisah yang gampang berselisih.
 *
 * `mapUrl` WAJIB khusus metode `sales_delivery`: `try_auto_assign_sales_delivery`
 * (inventory-service delivery.rs) hanya menugaskan sales sebagai driver job-nya
 * sendiri kalau `customer_map_url` terisi; kosong = fail-soft diam ke antrian
 * Delivery Control.
 */
/**
 * Lokasi maps BISA DIPAKAI driver? Cerminan `maps_terpakai` (Rust
 * `delivery.rs`) dan `mapsTerpakai` (web `deliveryAccess.ts`).
 *
 * **"Terisi" TIDAK sama dengan "bisa dipakai".** Gerbang penugasan driver di
 * server hanya menuntut kolomnya tak kosong, dan hasilnya terukur di produksi
 * 2026-08-30: dari 2.224 unit, 985 kosong tapi **1.008 berisi teks bebas** dan
 * hanya 231 membawa tautan. Isi teratas singkatan lokal — "hgl" 236x, "sbg"
 * 44x, "pmk" 21x — beserta kalimat yang bukan lokasi ("sesuai sharelok").
 * Dari 1.045 unit yang pernah sampai ke tangan driver hanya 129 (12,3%)
 * membawa tautan yang bisa dibuka.
 *
 * **Sengaja permisif soal DOMAIN**: menerima http/https apa pun dan pasangan
 * koordinat, bukan hanya `maps.app.goo.gl`. Orang memakai tautan pendek, Apple
 * Maps, hasil bagikan WhatsApp; salah-tolak di sini jauh lebih mahal daripada
 * meloloskan satu tautan aneh.
 *
 * Ketiga salinan (Rust, web, di sini) HARUS sepakat — kalau tidak, app menahan
 * isian yang server terima, atau meloloskan yang server tolak dengan 400.
 */
fun mapsTerpakai(nilai: String?): Boolean {
    val t = (nilai ?: "").trim()
    if (t.length < 8) return false
    val rendah = t.lowercase()
    if (rendah.startsWith("http://") || rendah.startsWith("https://")) return true
    // "-6.123456, 106.789012" — koordinat mentah tetap bisa dibuka driver.
    val bagian = t.split(",")
    return bagian.size == 2 && bagian.all { it.trim().toDoubleOrNull() != null }
}

fun spkSubmitBlocker(
    pelanggan: String,
    telepon: String,
    nik: String,
    mapUrl: String,
    deliveryMethod: String,
    spkCabang: String,
    itemsCount: Int,
    itemsValid: Boolean,
    totalUnits: Int,
): String? = when {
    pelanggan.trim().length < 3 || telepon.trim().length < 6 -> "Lengkapi nama & No. HP pelanggan."
    nik.isNotEmpty() && nik.length != 16 -> "NIK harus 16 digit angka."
    deliveryMethod == "sales_delivery" && mapUrl.isBlank() ->
        "Isi Link Lokasi Maps — wajib untuk metode Sales Antar Sendiri."
    // Cerminan validasi `create_delivery` (server, 2026-08-30): yang SUDAH
    // diketik harus bisa dibuka driver. Kosong tetap boleh — mewajibkannya di
    // sini akan menghentikan pembuatan SPK untuk sales yang memang belum
    // memegang lokasinya, dan alur susulan (notifikasi + layar isi maps) sudah
    // menangkapnya.
    //
    // Tanpa cerminan ini sales baru tahu setelah menekan Simpan dan menerima
    // 400 atas form yang panjang.
    mapUrl.isNotBlank() && !mapsTerpakai(mapUrl) ->
        "Link Lokasi Maps belum berupa link atau koordinat — tempel link dari " +
            "Google Maps, atau kosongkan dulu."
    spkCabang.isBlank() -> "Pilih cabang dulu."
    itemsCount == 0 -> "Tambah minimal 1 barang dari pencarian stok."
    itemsCount > MAX_SPK_BARIS -> "Maksimal $MAX_SPK_BARIS barang per SPK"
    totalUnits > MAX_SPK_UNIT -> "Maksimal $MAX_SPK_UNIT unit per SPK"
    totalUnits < 1 -> "Isi jumlah unit minimal 1."
    !itemsValid -> "Ada barang belum lengkap — cek tanda merah di kartu."
    else -> null
}

/**
 * Penyebab [spkSubmitBlocker] ada di kartu **"1. Pelanggan"** (bukan
 * "2. Barang").
 *
 * Dipakai untuk MEMBUKA kartu yang tepat saat submit ditolak. Kedua kartu bisa
 * ditutup sales, dan pesan merah yang menunjuk field di dalam kartu tertutup =
 * tombol yang tak mempan tanpa satu pun penjelasan yang bisa dilihat — kelas
 * kegagalan yang sama dengan field tersembunyi ber-issue.
 *
 * Ia MEMANGGIL ULANG [spkSubmitBlocker] dengan bagian barang diisi nilai yang
 * pasti lolos, BUKAN menyalin urutan syaratnya. Salinan urutan itulah yang akan
 * menyimpang diam-diam begitu syaratnya bertambah, dan menyimpangnya tak
 * menimbulkan error — cuma kartu yang salah yang terbuka.
 */
fun spkBlockerDiPelanggan(
    pelanggan: String,
    telepon: String,
    nik: String,
    mapUrl: String,
    deliveryMethod: String,
): Boolean = spkSubmitBlocker(
    pelanggan = pelanggan,
    telepon = telepon,
    nik = nik,
    mapUrl = mapUrl,
    deliveryMethod = deliveryMethod,
    spkCabang = "PENGISI",
    itemsCount = 1,
    itemsValid = true,
    totalUnits = 1,
) != null

/**
 * Baris stok yang boleh ditampilkan/ditap untuk `spkCabang` sekarang.
 *
 * `StokCabangRow` tak membawa kode dealer, dan cabang barang baru dilekatkan
 * saat submit dari `spkCabang` — jadi daftar yang berasal dari cabang lain
 * harus hilang, bukan sekadar "kemungkinan basi". Respons pencarian cabang
 * sebelumnya bisa mendarat setelah selektor pindah (insiden DLV-M84149DA0,
 * 2026-07-29: barang Pagaden ter-submit sebagai Soklat).
 */
fun stokRowsForCabang(
    rows: List<StokCabangRow>,
    stokDealer: String,
    spkCabang: String,
): List<StokCabangRow> =
    if (spkCabang.isNotBlank() && stokDealer.trim().equals(spkCabang.trim(), ignoreCase = true)) rows
    else emptyList()

/** Baris baru dari hasil picker stok (mirror web `pickBarang`+`emptySaleItem`). */
fun newSpkItemDraft(row: StokCabangRow): SpkItemDraft = SpkItemDraft(
    kodeBarang = row.kode.trim(),
    namaBarang = row.nama.trim(),
    kategori = row.kategori,
    merk = row.merk,
    tipe = row.tipe,
    stokTersedia = row.stok,
    hargaOtr = row.harga?.takeIf { it > 0 }?.toLong()?.toString() ?: "",
)
