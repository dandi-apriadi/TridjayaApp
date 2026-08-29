package com.krisoft.tridjayaelektronik.ui.deliveryflow

import com.krisoft.tridjayaelektronik.data.model.DeliveryJobDto
import com.krisoft.tridjayaelektronik.data.model.DeliveryStatusKey
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Sunting isi SPK oleh administrator (2026-08-01) — cerminan `EditJobBody` di
 * `services/inventory-service/src/delivery.rs` dan `utils/spkEdit.ts` di web.
 *
 * SEMUA aturan patch ada di file ini sebagai fungsi MURNI supaya bisa diuji
 * tanpa Compose maupun jaringan: layar cuma memegang `Map<String, String>` dan
 * memanggil [buildSpkEditPatch].
 *
 * `diskon` TIDAK ADA di daftar dan tak boleh ditambahkan: kolom itu hanya
 * berubah lewat pengajuan diskon yang menyimpan siapa memutuskan berapa.
 * `hargaTotal` juga tidak — server menghitungnya dari `hargaOtr` − diskon.
 *
 * **`lokasiPembayaran` (2026-08-12, migrasi 213) SENGAJA TIDAK di daftar ini —
 * jangan "melengkapi"-nya.** `PATCH /inventory/delivery/{id}` memang
 * menerimanya, TAPI **SUPER-ADMIN SAJA** (setara `kodeDealer`) dan berlaku
 * SE-SPK, sedangkan [SPK_EDIT_FIELDS] adalah daftar field yang boleh disunting
 * oleh SIAPA PUN yang lolos [bolehSuntingSpk] — termasuk sales PEMILIK SPK
 * selagi belum sampai PDI. Tak satu pun field super-admin ada di sini, dan itu bukan
 * kelalaian: menambahkannya membuat sales melihat kolom yang servernya jawab
 * 403, atau lebih buruk, memindahkan uang satu SPK ke kasir cabang lain lewat
 * layar sunting biasa. Koreksi lokasi pembayaran adalah pekerjaan super-admin
 * lewat web.
 */
enum class SpkEditGrup(val label: String) {
    BARANG("Barang (unit ini saja)"),
    KONSUMEN("Konsumen (berlaku ke seluruh unit SPK)"),
    PEMBIAYAAN("Pembiayaan (unit ini saja)"),
}

enum class SpkEditTipe { TEKS, TEKS_PANJANG, ANGKA, METODE_BAYAR }

data class SpkEditField(
    val key: String,
    val label: String,
    val grup: SpkEditGrup,
    val tipe: SpkEditTipe = SpkEditTipe.TEKS,
)

val SPK_EDIT_FIELDS: List<SpkEditField> = listOf(
    SpkEditField("kodeBarang", "Kode barang", SpkEditGrup.BARANG),
    SpkEditField("namaBarang", "Nama barang", SpkEditGrup.BARANG),
    SpkEditField("kategori", "Kategori", SpkEditGrup.BARANG),
    SpkEditField("merk", "Merk", SpkEditGrup.BARANG),
    SpkEditField("tipe", "Tipe", SpkEditGrup.BARANG),
    SpkEditField("warna", "Warna", SpkEditGrup.BARANG),
    SpkEditField("serialNumber", "No. rangka / serial", SpkEditGrup.BARANG),
    SpkEditField("engineNumber", "No. mesin", SpkEditGrup.BARANG),

    SpkEditField("customerName", "Nama konsumen", SpkEditGrup.KONSUMEN),
    SpkEditField("customerPhone", "No. HP", SpkEditGrup.KONSUMEN),
    SpkEditField("customerNik", "NIK (16 digit)", SpkEditGrup.KONSUMEN),
    SpkEditField("customerAddress", "Alamat", SpkEditGrup.KONSUMEN, SpkEditTipe.TEKS_PANJANG),
    SpkEditField("customerMapUrl", "Link lokasi Maps", SpkEditGrup.KONSUMEN),
    SpkEditField("sosmedTiktok", "TikTok", SpkEditGrup.KONSUMEN),
    SpkEditField("sosmedFacebook", "Facebook", SpkEditGrup.KONSUMEN),
    SpkEditField("sosmedInstagram", "Instagram", SpkEditGrup.KONSUMEN),

    SpkEditField("paymentType", "Metode bayar", SpkEditGrup.PEMBIAYAAN, SpkEditTipe.METODE_BAYAR),
    SpkEditField("fincoy", "Leasing / fincoy", SpkEditGrup.PEMBIAYAAN),
    SpkEditField("hargaOtr", "Harga OTR", SpkEditGrup.PEMBIAYAAN, SpkEditTipe.ANGKA),
    SpkEditField("biayaAdm", "Biaya admin", SpkEditGrup.PEMBIAYAAN, SpkEditTipe.ANGKA),
    SpkEditField("angsuranPertama", "Angsuran pertama", SpkEditGrup.PEMBIAYAAN, SpkEditTipe.ANGKA),
    SpkEditField("dpNet", "DP nett", SpkEditGrup.PEMBIAYAAN, SpkEditTipe.ANGKA),
    SpkEditField("pembayaran1", "Pembayaran pertama", SpkEditGrup.PEMBIAYAAN, SpkEditTipe.ANGKA),
    SpkEditField("angsuran", "Angsuran / bulan", SpkEditGrup.PEMBIAYAAN, SpkEditTipe.ANGKA),
    SpkEditField("tenor", "Tenor (bulan)", SpkEditGrup.PEMBIAYAAN, SpkEditTipe.ANGKA),
    SpkEditField("komisiKbk", "Komisi KBK", SpkEditGrup.PEMBIAYAAN, SpkEditTipe.ANGKA),
    SpkEditField("noHpKbk", "No. HP KBK", SpkEditGrup.PEMBIAYAAN),
    SpkEditField("keterangan", "Keterangan", SpkEditGrup.PEMBIAYAAN, SpkEditTipe.TEKS_PANJANG),
)

/** Nilai sekarang per field, sebagai STRING (itu yang dipegang TextField).
 *
 *  Angka bulat dicetak TANPA `.0`: `hargaOtr` datang sebagai `Double`, dan
 *  "4199000.0" di kotak isian bukan cuma jelek — ia langsung terbaca sebagai
 *  perubahan dibanding "4199000" sehingga patch terkirim padahal tak ada yang
 *  disentuh. */
fun spkEditFormFromJob(job: DeliveryJobDto): Map<String, String> = mapOf(
    "kodeBarang" to job.kodeBarang.orEmpty(),
    "namaBarang" to job.namaBarang.orEmpty(),
    "kategori" to job.kategori.orEmpty(),
    "merk" to job.merk.orEmpty(),
    "tipe" to job.tipe.orEmpty(),
    "warna" to job.warna.orEmpty(),
    "serialNumber" to job.serialNumber.orEmpty(),
    "engineNumber" to job.engineNumber.orEmpty(),
    "customerName" to job.customerName.orEmpty(),
    "customerPhone" to job.customerPhone.orEmpty(),
    "customerNik" to job.customerNik.orEmpty(),
    "customerAddress" to job.customerAddress.orEmpty(),
    "customerMapUrl" to job.customerMapUrl.orEmpty(),
    "sosmedTiktok" to job.sosmedTiktok.orEmpty(),
    "sosmedFacebook" to job.sosmedFacebook.orEmpty(),
    "sosmedInstagram" to job.sosmedInstagram.orEmpty(),
    "paymentType" to job.paymentType.orEmpty(),
    "fincoy" to job.fincoy.orEmpty(),
    "hargaOtr" to angkaTeks(job.hargaOtr),
    "biayaAdm" to angkaTeks(job.biayaAdm),
    "angsuranPertama" to angkaTeks(job.angsuranPertama),
    "dpNet" to angkaTeks(job.dpNet),
    "pembayaran1" to angkaTeks(job.pembayaran1),
    "angsuran" to angkaTeks(job.angsuran),
    "tenor" to (job.tenor?.toString() ?: ""),
    "komisiKbk" to angkaTeks(job.komisiKbk),
    "noHpKbk" to job.noHpKbk.orEmpty(),
    "keterangan" to job.keterangan.orEmpty(),
)

private fun angkaTeks(v: Double?): String = when {
    v == null -> ""
    v == v.toLong().toDouble() -> v.toLong().toString()
    else -> v.toString()
}

/**
 * Susun patch dari SELISIH form vs baris. Field yang tak berubah TIDAK dikirim
 * — endpoint-nya patch parsial, dan mengirim seluruh isi form berarti dua
 * administrator yang membuka SPK yang sama saling menimpa field yang tak
 * mereka sentuh.
 *
 * `null` = tak ada yang berubah (jangan panggil server). Angka yang tak bisa
 * dibaca diabaikan seperti tak diubah, BUKAN dikirim sebagai teks.
 */
fun buildSpkEditPatch(
    form: Map<String, String>,
    job: DeliveryJobDto,
    alasan: String,
): JsonObject? {
    val semula = spkEditFormFromJob(job)
    val patch = mutableMapOf<String, JsonPrimitive>()
    for (f in SPK_EDIT_FIELDS) {
        val baru = form[f.key].orEmpty()
        if (baru.trim() == semula[f.key].orEmpty().trim()) continue
        if (f.tipe == SpkEditTipe.ANGKA) {
            // Mengosongkan angka tak didukung endpoint (kirim 0 kalau memang nol).
            val n = baru.trim().toDoubleOrNull() ?: continue
            patch[f.key] = JsonPrimitive(n)
        } else {
            // Teks kosong = perintah kosongkan kolomnya (kontrak server).
            patch[f.key] = JsonPrimitive(baru.trim())
        }
    }
    if (patch.isEmpty()) return null
    return JsonObject(patch + ("alasan" to JsonPrimitive(alasan.trim())))
}

/** Status yang isinya masih boleh disunting — cerminan guard `edit_job`.
 *  DUA syarat: tahapnya belum lewat PDI DAN belum tercatat di GS. */
fun spkBolehDisunting(job: DeliveryJobDto): Boolean =
    job.noTransaksi.isNullOrBlank() &&
        (job.status == DeliveryStatusKey.PENDING_PDI || job.status == DeliveryStatusKey.PENDING_DISCOUNT ||
            job.status == DeliveryStatusKey.PENDING_PERBAIKAN)

/**
 * Status yang membuat SPK masih "di tangan sales" — cerminan
 * `sales_pemilik_revisi` di `edit_job`.
 *
 * SENGAJA lebih sempit dari [spkBolehDisunting]: daftar itu memuat
 * `pending_perbaikan`, yang untuk SALES dijawab **403** server (tahap itu sudah
 * SESUDAH PDI). Menyatukan keduanya memunculkan tombol yang pasti gagal —
 * kelas bug "menu tampil, backend 403" yang sudah mahal di repo ini.
 */
private val STATUS_SALES_BOLEH_REVISI = setOf(
    DeliveryStatusKey.PENDING_DISCOUNT,
    DeliveryStatusKey.PENDING_PDI,
)

/**
 * Siapa yang boleh menekan "Ubah Isi SPK" pada unit ini.
 *
 * Cerminan guard `edit_job` yang DILEBARKAN 2026-08-06: selain administrator,
 * **sales PEMILIK SPK** boleh menyunting selagi status `pending_discount`.
 * Alasannya bukan kemurahan hati — sejak penolakan diskon tidak lagi melepas
 * unit, SPK memang sedang berada di tangan sales, dan "revisi isi SPK" adalah
 * satu dari tiga jalan keluar yang ditawarkan kepadanya. Tanpa gate ini
 * tombolnya tak pernah muncul di HP dan sales harus membuka web hanya untuk
 * mengubah satu angka.
 *
 * **DILEBARKAN LAGI 2026-08-29** (permintaan user): jendelanya kini "belum
 * sampai PDI", jadi `pending_pdi` ikut. SPK TANPA diskon lahir LANGSUNG di
 * `pending_pdi` dan tak pernah menyentuh `pending_discount` sama sekali —
 * dengan aturan lama, mayoritas sales tak pernah punya jendela koreksi dan satu
 * warna tertukar berarti membatalkan SPK lalu mengetik ulang.
 *
 * Kepemilikan tetap satu-satunya batas: sales LAIN dijawab 403 server, dan
 * jendelanya tetap berhenti di PDI (tahap sesudahnya tertutup, termasuk
 * `pending_perbaikan`) serta sebelum tercatat di GS.
 */
fun bolehSuntingSpk(job: DeliveryJobDto, isAdmin: Boolean, currentUserId: String): Boolean {
    if (!spkBolehDisunting(job)) return false
    if (isAdmin) return true
    val pemilik = !job.salesUserId.isNullOrBlank() && job.salesUserId == currentUserId
    return pemilik && job.status in STATUS_SALES_BOLEH_REVISI
}

/** Alasan koreksi wajib — jejaknya hidup di `activity_log` gateway, dan jejak
 *  tanpa alasan cuma memberi tahu bahwa sesuatu berubah. Ambangnya SAMA dengan
 *  server (5 karakter) supaya tombol tak hidup untuk permintaan yang pasti 400. */
const val SPK_EDIT_ALASAN_MIN = 5

fun spkEditAlasanValid(alasan: String): Boolean = alasan.trim().length >= SPK_EDIT_ALASAN_MIN
