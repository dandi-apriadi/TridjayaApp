package com.krisoft.tridjayaelektronik.ui.deliveryflow

import com.krisoft.tridjayaelektronik.data.model.DiscountRequestDto

/**
 * Berapa unit FISIK yang benar-benar dipotong oleh satu pengajuan diskon.
 *
 * `deliveryJobIds` berarti DUA hal berbeda tergantung `baris` (lihat
 * `discounts/mysql.rs` `hydrate`): `Some(n)` → `line_job_ids`, yaitu unit
 * baris itu saja; `null` (pengajuan WARISAN se-batch) → `batch_job_ids`,
 * yaitu SELURUH unit SPK. Memakainya sebagai "jumlah unit baris ini" tanpa
 * memeriksa `baris` akan mengalikan potongan pengajuan warisan dengan jumlah
 * unit SPK utuh — jauh lebih salah dari bug yang sedang diperbaiki. Karena
 * pengajuan warisan tak bisa diatribusikan ke baris mana pun, ia dihitung 1.
 */
fun unitTerdampak(d: DiscountRequestDto): Int =
    if (d.baris != null) d.deliveryJobIds.size.coerceAtLeast(1) else 1

/**
 * Potongan RUPIAH sesungguhnya dari satu pengajuan.
 *
 * `value` TIDAK dipakai: untuk `discountType = "percent"` ia sebuah persen,
 * bukan rupiah — dan bahkan untuk `"amount"` ia nilai PER UNIT, sementara
 * `apply_to_line` menuliskannya ke SETIAP unit sebaris. Kartu lama
 * menjumlahkan `value` mentah, jadi SPK berisi baris qty 2 memperlihatkan
 * SEPARUH potongan sebenarnya — dan papan web (yang memakai selisih harga)
 * menampilkan angka lain untuk SPK yang sama.
 *
 * `hargaSebelum`/`hargaSesudah` **sudah TOTAL SE-BARIS** — server yang
 * mengalikannya dengan qty, di KEDUA penulisnya (`delivery::create_delivery`
 * sejak 62e4ade6 / 2026-08-02, dan `discounts::create_discount_request` sejak
 * 2026-08-16). Selisihnya karena itu dipakai APA ADANYA; mengalikannya lagi
 * dengan [unitTerdampak] menghitung qty DUA KALI — itu yang terjadi antara
 * 2026-08-07 dan perbaikan ini: baris qty 2 menampilkan potongan 2x lipat dan
 * berselisih dengan papan web untuk SPK yang sama.
 *
 * `value` TETAP tak dipakai (lihat catatan di atas): untuk `percent` ia bukan
 * rupiah, dan untuk `amount` ia nilai per unit. Salah satu harga null
 * (pengajuan lama) = 0, sama seperti web — angka yang tak bisa dihitung tak
 * boleh ditebak.
 */
fun potonganPengajuan(d: DiscountRequestDto): Double {
    val sebelum = d.hargaSebelum ?: return 0.0
    val sesudah = d.hargaSesudah ?: return 0.0
    return sebelum - sesudah
}

/** Total potongan satu SPK = jumlah potongan seluruh pengajuannya. */
fun totalPotonganSpk(pengajuan: List<DiscountRequestDto>): Double =
    pengajuan.sumOf { potonganPengajuan(it) }

/**
 * Potongan yang MASIH menunggu keputusan approver (`pending` saja).
 *
 * Dipakai dialog detail SPK untuk menjawab pertanyaan yang sebenarnya dipegang
 * approver: "SPK ini jadi berapa kalau saya setujui". Sebelumnya dialog cuma
 * menampilkan `totalDiskonBerjalan` (potongan yang SUDAH menempel), dan untuk
 * keadaan yang paling sering dibuka — satu pengajuan `pending`, belum ada yang
 * disetujui — angka itu SELALU nol. Barisnya terbaca **"Diskon berjalan
 * −Rp 0"** dengan total harga PENUH, yang dilaporkan approver sebagai
 * "diskonnya tidak masuk" (2026-08-16) padahal itu keadaan sebelum
 * keputusannya sendiri.
 *
 * `rejected` TIDAK ikut: sudah diputus, menunggu sales merevisi atau
 * merelakan. `approved` juga tidak — ia SUDAH terhitung di
 * `totalDiskonBerjalan`, jadi menjumlahkan keduanya = diskon dihitung dua kali.
 */
fun potonganMenunggu(pengajuan: List<DiscountRequestDto>): Double =
    pengajuan.filter { it.status == "pending" }.sumOf { potonganPengajuan(it) }

/**
 * 7000000 → "7.000.000". Diekstrak dari `rupiah()` supaya baris ringkas kartu
 * diskon ("2 unit · 7.000.000 → 6.700.000") memakai format yang SAMA persis;
 * dua penulis format berarti dua penulisan berbeda untuk angka yang sama.
 *
 * ponytail: negatif di bawah 1000 salah pisah ("-100" → "-.100"). Nominal
 * harga/potongan di sini selalu positif; kalau nanti dipakai untuk selisih
 * bertanda, pisahkan tandanya dulu sebelum `chunked`.
 */
fun ribuan(v: Double?): String {
    val n = (v ?: 0.0).toLong()
    return n.toString().reversed().chunked(3).joinToString(".").reversed()
}

/**
 * Baris kedua satu barang: "2 unit · 7.000.000 → 6.700.000 · acc Pak Kiryanto".
 *
 * Menggantikan DUA baris `InfoLine` ("Harga sebelum" + "Harga sesudah") yang
 * memakan dua baris penuh untuk satu informasi yang dibaca sebagai satu
 * kalimat. Harga tak lengkap (pengajuan lama) → cuma jumlah unit; angka yang
 * tak ada TIDAK ditebak, sama seperti [potonganPengajuan].
 *
 * [accOleh] ikut DI SINI, bukan sebagai baris `InfoLine` sendiri: label
 * "Acc oleh (di luar sistem)" memakan satu baris penuh per barang, dan karena
 * `InfoLine` merentang selebar kartu ia keluar dari indentasi barangnya —
 * terbaca seperti keterangan milik SPK, bukan milik barang di atasnya.
 * Dipakai HANYA saat nilainya tak seragam se-SPK; yang seragam naik ke header
 * lewat [nilaiSeragam].
 */
fun ringkasHarga(d: DiscountRequestDto, sertakanAcc: Boolean = false): String {
    val n = unitTerdampak(d)
    // "1 unit ·" disembunyikan saat memang satu unit: itu keadaan MAYORITAS,
    // dan menuliskannya menghabiskan ±9 karakter di baris yang paling sering
    // membungkus. Jumlah unit hanya berarti ketika lebih dari satu — di situlah
    // diskon dikali dan angkanya bisa mengejutkan approver.
    val unit = if (n > 1) "$n unit" else ""
    val acc = d.accOleh?.takeIf { sertakanAcc && it.isNotBlank() }?.let { "acc $it" } ?: ""
    val harga = when {
        d.hargaSebelum == null || d.hargaSesudah == null -> ""
        else -> "${ribuan(d.hargaSebelum)} → ${ribuan(d.hargaSesudah)}"
    }
    // Gabung dengan pemisah HANYA di antara bagian yang benar-benar ada —
    // supaya tak pernah ada "· " menggantung di ujung baris saat salah satu
    // bagian kosong.
    val bagian = listOf(unit, harga, acc).filter { it.isNotBlank() }
    return if (bagian.isEmpty()) "1 unit" else bagian.joinToString(" · ")
}

/**
 * Nilai yang SAMA di seluruh pengajuan satu SPK → boleh naik ke header kartu
 * dan ditulis SEKALI. Beda, atau ada satu saja yang kosong → null, artinya
 * nilainya tetap tinggal di barisnya masing-masing.
 *
 * "Diajukan: Administrator" yang berulang di tiap barang adalah pemakan tinggi
 * terbesar kartu ini tanpa menambah satu pun informasi. Tapi menghapusnya
 * begitu saja salah: satu SPK bisa memuat pengajuan dari orang berbeda, dan
 * approver yang tak melihat perbedaan itu justru kehilangan informasi penting.
 *
 * Pemanggil yang menormalkan (mis. `?.trim()?.ifBlank { null }`) — fungsi ini
 * cuma membandingkan apa yang diberikan.
 */
fun <T : Any> nilaiSeragam(
    pengajuan: List<DiscountRequestDto>,
    ambil: (DiscountRequestDto) -> T?,
): T? {
    val pertama = pengajuan.firstOrNull()?.let(ambil) ?: return null
    return pertama.takeIf { pengajuan.all { d -> ambil(d) == pertama } }
}

/**
 * Urutkan isi kartu menurut nomor baris SPK.
 *
 * Server mengirim antrian `created_at DESC`, jadi barang ke-3 bisa tampil di
 * atas barang ke-1 — approver membaca daftar yang urutannya tak cocok dengan
 * SPK di tangan sales. Pengajuan warisan tanpa `baris` ditaruh paling akhir.
 */
fun urutPengajuanSpk(pengajuan: List<DiscountRequestDto>): List<DiscountRequestDto> =
    pengajuan.sortedWith(compareBy({ it.baris ?: Int.MAX_VALUE }, { it.createdAt }))

// ── Ketuntasan per barang (2026-08-07) ───────────────────────────────────────

/**
 * Barang yang langsung tampil di kartu SPK sebelum daftarnya dipotong. 4
 * dipilih supaya kartu SPK biasa (1-4 barang) tak berubah sama sekali, dan SPK
 * 10 barang tetap muat di satu layar. Batas ini TIDAK berlaku untuk barang yang
 * belum tuntas — lihat [ringkasDaftar].
 */
const val BATAS_RINGKAS = 4

/**
 * Barang ini SELESAI diurus — tak perlu tombol keputusan lagi.
 *
 * Membalik perilaku 2026-08-06 (satu keputusan menyapu seluruh SPK): sejak
 * 2026-08-07 keputusan diambil PER BARANG, dan SPK baru lanjut ke PDI setelah
 * SELURUH barangnya tuntas — saat itu seluruh unitnya dilepas bersamaan.
 *
 * `rejected` SENGAJA bukan tuntas: bolanya pindah ke SALES (revisi atau lanjut
 * tanpa diskon), dan SPK tetap tertahan sampai ia memilih. Menganggapnya tuntas
 * membuat kartu mengklaim SPK sudah jalan padahal masih mandek.
 *
 * `dilepas` = status BARU dari server (sales menyerah pada diskon yang
 * ditolak). Tanpa arm ini kartu menawarkan tombol keputusan atas barang yang
 * sudah selesai, dan kemajuannya kurang hitung.
 */
fun barisTuntas(status: String): Boolean = status == "approved" || status == "dilepas"

/**
 * Label pengganti tombol untuk barang yang sudah diputus.
 *
 * Sengaja PENDEK: label ini hidup di ujung baris barang, bersebelahan dengan
 * nama barang dan nominal potongan — label panjang menggencet nama barang
 * sampai ter-elipsis pada layar 360dp. Nuansa "ditolak = bolanya di sales"
 * ditulis sebagai baris keterangan sendiri di bawahnya, bukan dijejalkan ke
 * dalam chip.
 *
 * Status tak dikenal dikembalikan APA ADANYA, bukan dikosongkan: status baru
 * dari server harus terbaca sebagai teks aneh (ketahuan) ketimbang menghilang.
 */
fun labelStatusBaris(status: String): String = when (status) {
    "approved" -> "Disetujui"
    "dilepas" -> "Tanpa diskon"
    "rejected" -> "Ditolak"
    "pending" -> "Menunggu"
    else -> status
}

/**
 * Kemajuan satu kartu SPK — "2 dari 3 barang tuntas".
 *
 * Penyebutnya adalah pengajuan yang DIPEGANG kartu: antrian approval hanya
 * memuat yang `pending`, jadi setelah muat ulang penuh barang yang sudah
 * diputus tak lagi terhitung. Keputusan sesi ini tetap terhitung karena
 * ViewModel menambal barisnya di tempat (bukan memuat ulang antrian).
 *
 * ponytail: penyebut se-SPK yang sejati butuh endpoint ringkasan ketuntasan
 * yang sengaja belum dibuat backend. Tambahkan kalau approver ternyata butuh
 * melihat kemajuan yang bertahan lintas muat ulang.
 */
data class KemajuanSpk(val tuntas: Int, val total: Int) {
    val semuaTuntas: Boolean get() = total > 0 && tuntas == total
    val teks: String get() = "$tuntas dari $total barang tuntas"
}

fun kemajuanSpk(pengajuan: List<DiscountRequestDto>): KemajuanSpk =
    KemajuanSpk(pengajuan.count { barisTuntas(it.status) }, pengajuan.size)

/**
 * Daftar yang tampil saat kartu diringkas: barang yang BELUM tuntas tak pernah
 * disembunyikan, sisa kuota baru diisi barang yang sudah tuntas.
 *
 * Sejak tombol keputusan pindah ke tiap barang (2026-08-07), memotong daftar di
 * [BATAS_RINGKAS] apa adanya berarti menyembunyikan TOMBOL — approver melihat
 * "0 dari 6 tuntas" tanpa satu pun cara memutuskan sampai ia menemukan tautan
 * "Lihat N lainnya". Kuota boleh terlampaui; batas ringkas kalah dari
 * keterjangkauan tombol.
 *
 * Urutan asli (nomor baris SPK) dipertahankan — approver membaca kartu sambil
 * memegang SPK cetak.
 */
fun ringkasDaftar(urut: List<DiscountRequestDto>, batas: Int = BATAS_RINGKAS): List<DiscountRequestDto> {
    if (urut.size <= batas) return urut
    val kuotaTuntas = (batas - urut.count { !barisTuntas(it.status) }).coerceAtLeast(0)
    val tampil = urut.filter { barisTuntas(it.status) }.take(kuotaTuntas).map { it.id }.toSet()
    return urut.filter { !barisTuntas(it.status) || it.id in tampil }
}

/**
 * Judul watermark bukti acc diskon. **Galeri DIBEDAKAN** — dan ini satu-satunya
 * titik unggah di seluruh alur delivery yang memang boleh memilih dari galeri
 * (`SpkItemCard`, tombol "Galeri"); enam titik lainnya kamera-saja.
 *
 * Alasannya sama persis dengan `AktivitasBuktiPlan.watermarkTitleBukti`:
 * stempel jam di bar watermark adalah jam **PROSES**, bukan jam foto diambil.
 * Tanpa pembeda, tangkapan layar persetujuan dari chat bulan lalu tercetak
 * dengan jam hari ini dan terlihat identik dengan foto yang baru dijepret di
 * depan approver. Approver bukti diskon tak punya sumber lain untuk
 * mengetahuinya — server tak menyimpan apa pun soal asal foto
 * (`delivery/upload.rs` nol EXIF, nol penanda), jadi piksel inilah satu-satunya
 * tempat keterangan itu bisa hidup.
 *
 * Web SENGAJA tak ikut: `<input type="file">` tak pernah memberi tahu asal
 * berkasnya, jadi label apa pun di sana adalah tebakan.
 */
fun watermarkTitleBuktiAcc(dariGaleri: Boolean): String =
    if (dariGaleri) "TRIDJAYA · ACC DISKON (GALERI)" else "TRIDJAYA · ACC DISKON"
