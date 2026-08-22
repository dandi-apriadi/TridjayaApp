package com.krisoft.tridjayaelektronik.ui.homeservice

import com.krisoft.tridjayaelektronik.BuildConfig
import com.krisoft.tridjayaelektronik.data.HS_JENIS_TARIK_UNIT
import com.krisoft.tridjayaelektronik.data.model.HsSparepartDto
import com.krisoft.tridjayaelektronik.data.model.HsTicketDto

/**
 * Bagian murni layar Komplain (Home Service) — tanpa Android/Compose supaya
 * bisa diuji JUnit biasa. Pola sama `ui/raport/AktivitasReviewPlan.kt`.
 */

// ── Status ──────────────────────────────────────────────────────────────────

const val HS_BARU = "baru"
const val HS_DITUGASKAN = "ditugaskan"
const val HS_DIKERJAKAN = "dikerjakan"
const val HS_MENUNGGU_TINDAK_LANJUT = "menunggu_tindak_lanjut"
const val HS_MENUNGGU_TARIK = "menunggu_tarik"
const val HS_TARIK_DITUGASKAN = "tarik_ditugaskan"
const val HS_SELESAI = "selesai"
const val HS_ESKALASI = "eskalasi"
const val HS_DIBATALKAN = "dibatalkan"

/** Status yang sudah tak bergerak lagi — dipakai memisahkan antrian dari arsip. */
internal val HS_STATUS_FINAL = setOf(HS_SELESAI, HS_ESKALASI, HS_DIBATALKAN)

/**
 * Antrian TRIASE CS: tiket yang menunggu keputusan manusia. `dikerjakan`,
 * `ditugaskan`, dan seluruh jalur tarik SENGAJA tak masuk — pekerjaannya sudah
 * berpindah ke orang lain, dan menghitungnya di badge CS membuat angka tak
 * pernah turun ke nol walau CS sudah menuntaskan bagiannya.
 */
internal val HS_STATUS_TRIASE = setOf(HS_BARU, HS_MENUNGGU_TINDAK_LANJUT)

/** Tugas teknisi yang belum tuntas (layar "Tugas Home Service"). */
internal val HS_STATUS_TUGAS_TEKNISI = setOf(HS_DITUGASKAN, HS_DIKERJAKAN)

/** Antrian penarikan unit: menunggu driver + sudah ditugaskan tapi belum diambil. */
internal val HS_STATUS_TARIK_AKTIF = setOf(HS_MENUNGGU_TARIK, HS_TARIK_DITUGASKAN)

internal fun labelStatusHs(status: String): String = when (status) {
    HS_BARU -> "Baru"
    HS_DITUGASKAN -> "Ditugaskan"
    HS_DIKERJAKAN -> "Dikerjakan"
    HS_MENUNGGU_TINDAK_LANJUT -> "Perlu tindak lanjut"
    HS_MENUNGGU_TARIK -> "Menunggu tarik"
    HS_TARIK_DITUGASKAN -> "Driver ditugaskan"
    HS_SELESAI -> "Selesai"
    HS_ESKALASI -> "Eskalasi"
    HS_DIBATALKAN -> "Dibatalkan"
    else -> status
}

internal fun labelHasil(hasil: String): String = when (hasil) {
    "selesai" -> "Selesai"
    "kunjungan_ulang" -> "Perlu kunjungan ulang"
    "eskalasi" -> "Eskalasi"
    else -> hasil
}

/** Tiket yang perlu perhatian ditandai — mendesak ATAU sudah lewat SLA server. */
internal fun perluPerhatian(tiket: HsTicketDto): Boolean =
    tiket.melewatiSla || tiket.prioritas.equals("mendesak", ignoreCase = true)

// ── Penyaringan antrian (dilakukan di KLIEN, sesuai kontrak server) ─────────

/**
 * Server hanya menerima SATU nilai `status` per permintaan, sementara tiap
 * layar butuh beberapa sekaligus — persis alasan web memuat tanpa filter lalu
 * memilah sendiri. Fungsi ini satu-satunya tempat aturan itu ditulis.
 */
internal fun saringStatus(items: List<HsTicketDto>, status: Set<String>): List<HsTicketDto> =
    items.filter { it.status in status }

/**
 * Urutan antrian: yang perlu perhatian di atas, lalu yang paling tua.
 *
 * `umurJam` dipakai apa adanya dari server (BUKAN dihitung ulang dari
 * `createdAt`) — lihat catatan bias zona waktu di `HsTicketDto.umurJam`.
 * `null` = tak diketahui, ditaruh paling bawah, bukan dianggap 0.
 */
internal fun urutkanAntrian(items: List<HsTicketDto>): List<HsTicketDto> =
    items.sortedWith(
        compareByDescending<HsTicketDto> { perluPerhatian(it) }
            .thenByDescending { it.umurJam ?: Int.MIN_VALUE }
    )

// ── Gate aksi ───────────────────────────────────────────────────────────────

data class HsGate(val ok: Boolean, val alasan: String? = null)

/** Teknisi hanya boleh memulai kunjungan atas tiket yang sudah ditugaskan padanya. */
internal fun bolehMulai(status: String): HsGate = when (status) {
    HS_DITUGASKAN -> HsGate(true)
    HS_DIKERJAKAN -> HsGate(false, "Kunjungan sudah dimulai.")
    else -> HsGate(false, "Tiket belum ditugaskan ke teknisi.")
}

/**
 * Syarat tutup kunjungan — cerminan validasi server (`domain.rs`), ditulis di
 * klien supaya teknisi tak kehilangan isian formnya gara-gara 400 di ujung.
 */
internal fun bolehTutupKunjungan(
    hasil: String,
    fotoUrls: List<String>,
    adaSparepart: Boolean,
    sparepart: List<HsSparepartDto>,
    biayaDibayar: Double?,
    buktiBayarUrl: String?,
    rating: Int?,
): HsGate {
    if (hasil !in setOf("selesai", "kunjungan_ulang", "eskalasi")) {
        return HsGate(false, "Pilih hasil kunjungan dulu.")
    }
    if (hasil == "selesai" && fotoUrls.isEmpty()) {
        return HsGate(false, "Hasil selesai wajib melampirkan foto bukti kerja.")
    }
    if (rating != null && rating !in 1..5) return HsGate(false, "Rating harus 1–5.")
    if (adaSparepart) {
        if (sparepart.isEmpty()) return HsGate(false, "Isi minimal satu sparepart.")
        if (sparepart.any { it.nama.isBlank() }) return HsGate(false, "Nama sparepart tak boleh kosong.")
        if (sparepart.any { it.qty <= 0 }) return HsGate(false, "Qty sparepart minimal 1.")
        // Biaya TOTAL dihitung server dari daftar ini; app hanya memastikan
        // buktinya ada supaya tak ditolak setelah form panjang diisi.
        if (hitungBiaya(sparepart) > 0.0) {
            if (buktiBayarUrl.isNullOrBlank()) return HsGate(false, "Ada biaya sparepart — foto bukti bayar wajib.")
            if (biayaDibayar == null || biayaDibayar <= 0.0) {
                return HsGate(false, "Isi nominal yang dibayar konsumen.")
            }
        }
    }
    return HsGate(true)
}

/** Cerminan `hitung_biaya` server: qty × harga, dijumlahkan. */
internal fun hitungBiaya(items: List<HsSparepartDto>): Double =
    items.sumOf { it.qty.coerceAtLeast(0) * it.harga }

/** Semua aksi beralasan (batal / minta tarik / batal tarik) wajib diisi. */
internal fun bolehAlasan(alasan: String?): HsGate =
    if (alasan.isNullOrBlank()) HsGate(false, "Alasan wajib diisi.") else HsGate(true)

/**
 * Apa yang MASIH KURANG sebelum tiket bisa dibuat, dalam urutan yang sama
 * dengan daftar `kurang` milik web (`HomeServiceLaporPage.tsx:211-229`).
 * Daftar, bukan satu pesan: form ini punya sembilan isian, dan "tombol mati
 * tanpa sebab" memaksa pelapor menebak mana yang salah.
 *
 * Dua jalur, persis seperti web:
 *  - TERVERIFIKASI (`noTransaksi` terisi): wajib memilih barang. Server yang
 *    tidak menerima pilihan TIDAK menolak — ia diam-diam memakai barang
 *    PERTAMA transaksi (`service.rs:281-284`), jadi tiket bisa menunjuk unit
 *    yang salah tanpa satu pun galat. Gerbang inilah satu-satunya yang
 *    mencegahnya.
 *  - TANPA VERIFIKASI (`noTransaksi` kosong): tak ada barang untuk dipilih,
 *    tapi nama DAN nomor HP jadi wajib — tanpa jangkar transaksi, tiket tanpa
 *    keduanya tak bisa ditindaklanjuti siapa pun (server menolaknya di
 *    `service.rs:367-378`).
 *
 * `alamat` wajib di KEDUA jalur. Itu bukan pengetatan buatan app: server
 * menolak tiket yang alamatnya tetap kosong sesudah pengayaan SPK
 * (`service.rs:382-387`) — memeriksanya di sini hanya memindahkan penolakan
 * dari sesudah tombol ditekan ke sebelum pelapor mengetik panjang-panjang.
 */
internal fun kurangBuatTiket(
    tanpaVerifikasi: Boolean,
    noTransaksi: String?,
    barisTerpilih: Set<Int>,
    fotoKwitansiUrl: String?,
    deskripsi: String?,
    customerNama: String?,
    customerHp: String?,
    customerAlamat: String?,
): List<String> = buildList {
    // `fotoKwitansiUrl` SENGAJA tidak diperiksa: foto kwitansi opsional sejak
    // 2026-08-22 (permintaan user), cerminan server `service.rs` yang berhenti
    // memanggil `wajib_isi` untuk kolom ini. Parameternya dipertahankan supaya
    // pemanggil tak perlu diubah dan supaya syarat ini tak bisa dipasang lagi
    // di app tanpa terlihat di diff fungsi ini.
    if (tanpaVerifikasi) {
        if (customerNama.isNullOrBlank()) add("nama konsumen")
        if (customerHp.isNullOrBlank()) add("nomor HP konsumen")
    } else if (noTransaksi.isNullOrBlank()) {
        add("data pembelian konsumen")
    } else if (barisTerpilih.isEmpty()) {
        add("barang yang dikomplainkan")
    }
    if (customerAlamat.isNullOrBlank()) add("alamat konsumen")
    if (deskripsi.isNullOrBlank()) add("isi keluhan")
}

/** Isian kontak konsumen sesudah rincian transaksi dimuat. */
internal data class KontakIsian(val nama: String, val hp: String, val alamat: String)

/**
 * Kontak mana yang dipakai sesudah `lookup` mendarat.
 *
 * Dua kesalahan yang saling berlawanan harus dihindari sekaligus, dan itulah
 * kenapa aturannya ditulis sebagai fungsi murni yang bisa diuji:
 *  - MEMPERTAHANKAN isian lama (dulu `ifBlank`) membuat kontak konsumen yang
 *    barusan ditinggalkan menyeberang ke tiket berikutnya — dan server
 *    memenangkan isian klien atas data SPK, jadi teknisi berangkat ke alamat
 *    orang lain;
 *  - MENIMPA tanpa syarat menghapus ketikan pelapor: seluruh form sudah
 *    terender saat rincian masih dimuat, jadi alamat yang sedang diketik bisa
 *    dihapus oleh respons yang mendarat beberapa detik kemudian.
 *
 * Jalan tengahnya: yang menang adalah SENTUHAN MANUSIA (`disunting`, direset
 * tiap kali transaksi dipilih), selebihnya data server dengan cadangan kotak
 * pencarian — cadangan itu penting untuk transaksi lama yang SPK-nya nihil,
 * karena di jalur itu satu-satunya identitas yang kita punya adalah yang
 * barusan diketik pelapor.
 *
 * `disunting` dilacak PER KOLOM, bukan satu bendera untuk bertiga. Versi
 * pertama memakai satu `Boolean`, dan itu membuang data yang benar: seluruh
 * form sudah terender saat rincian masih dimuat, jadi pelapor yang mengetik
 * NAMA lebih dulu menyalakan bendera itu untuk ketiganya — alamat dari SPK
 * yang mendarat sedetik kemudian ditolak, dan kolom alamat tinggal kosong
 * sampai ada yang mengisinya tangan. Sentuhan pada satu kolom bukan
 * pernyataan apa pun tentang dua kolom lainnya.
 */
/**
 * Kolom kontak mana saja yang SUDAH disentuh pelapor. Lihat [kontakSetelahLookup].
 *
 * `public`, tak seperti tetangganya di berkas ini, karena ia jadi field
 * `HomeServiceLaporState` yang dibaca layar.
 */
data class KontakDisunting(
    val nama: Boolean = false,
    val hp: Boolean = false,
    val alamat: Boolean = false,
) {
    companion object {
        val NIHIL = KontakDisunting()
    }
}

internal fun kontakSetelahLookup(
    disunting: KontakDisunting,
    sekarang: KontakIsian,
    kontakNama: String?,
    kontakHp: String?,
    kontakAlamat: String?,
    cariNama: String,
    cariHp: String,
): KontakIsian = KontakIsian(
    nama = if (disunting.nama) sekarang.nama else kontakNama?.takeIf { it.isNotBlank() } ?: cariNama.trim(),
    hp = if (disunting.hp) sekarang.hp else kontakHp?.takeIf { it.isNotBlank() } ?: cariHp.trim(),
    alamat = if (disunting.alamat) sekarang.alamat else kontakAlamat.orEmpty(),
)

/** Gerbang tombol "Kirim komplain" — pembungkus [kurangBuatTiket] supaya layar
 *  dan ViewModel memakai SATU aturan (ViewModel memeriksanya ulang sebelum
 *  memanggil server; tombol yang tak sengaja hidup tak boleh cukup). */
internal fun bolehBuatTiket(
    tanpaVerifikasi: Boolean,
    noTransaksi: String?,
    barisTerpilih: Set<Int>,
    fotoKwitansiUrl: String?,
    deskripsi: String?,
    customerNama: String?,
    customerHp: String?,
    customerAlamat: String?,
): HsGate {
    val kurang = kurangBuatTiket(
        tanpaVerifikasi = tanpaVerifikasi,
        noTransaksi = noTransaksi,
        barisTerpilih = barisTerpilih,
        fotoKwitansiUrl = fotoKwitansiUrl,
        deskripsi = deskripsi,
        customerNama = customerNama,
        customerHp = customerHp,
        customerAlamat = customerAlamat,
    )
    return if (kurang.isEmpty()) HsGate(true) else HsGate(false, "Masih perlu: ${kurang.joinToString(", ")}.")
}

// ── Format nilai kirim ──────────────────────────────────────────────────────

/**
 * Server hanya menerima `YYYY-MM-DD` atau `YYYY-MM-DD HH:MM:SS` — ISO8601
 * ber-`Z`/offset/milidetik dijawab 400. Jamnya WIB apa adanya; JANGAN
 * dikonversi ke UTC, server menyimpan yang dikirim tanpa konversi zona.
 *
 * `null` untuk masukan kosong: `jadwalAt` memang opsional, dan mengirim string
 * kosong berbeda dengan tidak mengirim sama sekali.
 */
internal fun jadwalUntukServer(tanggalIso: String?): String? {
    val bersih = tanggalIso?.trim().orEmpty()
    if (bersih.isEmpty()) return null
    if (!Regex("""^\d{4}-\d{2}-\d{2}$""").matches(bersih)) return null
    return bersih
}

/**
 * URL foto yang bisa dimuat Coil. Foto komplain di-serve TERAUTENTIKASI
 * (`/api/home-service/photo/{berkas}`), bukan berkas statis publik — memuat
 * `/uploads/home-service/…` apa adanya selalu gagal. Pemanggil tetap wajib
 * menyertakan header `Authorization` (lihat `AuthedFoto` di layar).
 */
internal fun fotoHsUrl(raw: String?, baseUrl: String = BuildConfig.API_BASE_URL): String? {
    val trimmed = raw?.trim().orEmpty()
    if (trimmed.isEmpty()) return null
    if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return trimmed
    val berkas = trimmed.substringAfterLast('/')
    if (berkas.isBlank()) return null
    return baseUrl.trimEnd('/') + "/api/home-service/photo/" + berkas
}

/**
 * Daftar tugas driver WAJIB menyertakan `jenis=tarik_unit`: `mine=true` memilih
 * KOLOM yang disaring (`tarik_driver_id` vs `assigned_teknisi_id`) berdasarkan
 * `jenis`, jadi tanpa itu daftar driver selalu kosong tanpa satu pun error.
 */
internal fun jenisUntukTugasDriver(): String = HS_JENIS_TARIK_UNIT
