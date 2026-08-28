package com.krisoft.tridjayaelektronik.domain.inventory

import com.krisoft.tridjayaelektronik.data.local.BranchStockEntity
import com.krisoft.tridjayaelektronik.data.model.StokCabangItemDto

/**
 * Melengkapi cache Inventory dengan barang BERSTOK NOL, sesuai kata kunci.
 *
 * **Kenapa ini perlu ada sama sekali.** Sinkronisasi massal memanggil
 * `stok-cabang` dengan `inStock=true` (lihat `InventoryRepository.sync`), jadi
 * baris berstok nol TAK PERNAH masuk Room. Akibatnya layar Inventory tak
 * "menyaring" barang stok nol — ia memang tak punya datanya, dan orang yang
 * mencarinya menyimpulkan barangnya tak ada di katalog perusahaan. Filter
 * `readyOnly` di layar sudah default mati dan DAO-nya sudah siap
 * (`HAVING (:readyOnly = 0 OR SUM(stok) > 0)`); yang kurang cuma barisnya.
 *
 * **Kenapa BUKAN sekadar `inStock=false` di sinkronisasi massal.** Katalog penuh
 * 66.482 baris (67 halaman) dan hanya 5,5%-nya berstok. Itu bukan tebakan: 28
 * Juli 2026 SP GS `GetStokCabang` sempat mengembalikan katalog penuh dan
 * sinkronisasi HP lapangan tak pernah selesai — 834 percobaan berhenti di
 * halaman 4-5, hanya 19 yang tuntas. `SYNC_MAX_PAGES` (20) juga akan
 * memotongnya, dan snapshot terpotong sengaja TIDAK memanggil `replaceAll`
 * sehingga baris basi tak pernah dibersihkan. Jadi jalannya bukan menarik
 * semuanya, melainkan menarik yang SEDANG DICARI saja.
 *
 * **Bentuknya: menambal cache, bukan daftar kedua.** Baris hasil pencarian
 * di-`insertAll` ke `branch_stock`, lalu Paging yang mengamati Room memunculkannya
 * sendiri. Itu sebabnya tak ada "seksi hasil server" terpisah di layar: detail
 * produk, rincian per cabang, ekspor CSV, dan chip filter semuanya membaca Room,
 * jadi daftar kedua akan menghidupkan barang yang tak bisa dibuka. Baris tambalan
 * ini juga membersihkan dirinya sendiri — `sync()` berikutnya (TTL 5 jam)
 * memanggil `replaceAll`, yang mengosongkan tabel sebelum mengisi ulang dari
 * `inStock=true`.
 */

/**
 * Panjang minimum kata kunci sebelum pencarian stok nol dikirim ke server.
 *
 * Bukan kerapian: tanpa batas ini, satu huruf "a" meminta server memindai
 * katalog penuh dan mengembalikan potongan sembarang dari 66 ribu baris — mahal
 * di sisi server, dan di sisi app ia menambal cache dengan barang yang tak
 * seorang pun cari. Tiga huruf adalah ambang yang sama yang membuat hasilnya
 * cukup spesifik untuk berguna.
 */
const val MIN_KATA_KUNCI_STOK_NOL = 3

/**
 * Batas baris yang diminta per pencarian. Jauh di bawah satu halaman
 * sinkronisasi (1.000) karena ini melayani SATU kata kunci di SATU layar, dan
 * daftar yang lebih panjang dari ini tak terbaca manusia — yang dibutuhkan
 * orang yang mengetik nama barang adalah barangnya, bukan seluruh katalog yang
 * mirip.
 */
const val LIMIT_CARI_STOK_NOL = 200

/**
 * Apakah pencarian stok nol perlu dikirim sekarang.
 *
 * Fungsi murni supaya aturan pemicunya bisa diuji tanpa Retrofit/Room — pola
 * yang sama dengan `nextSyncStep` di `InventoryRepository`.
 *
 * @param search kata kunci yang sedang berlaku (belum di-`trim`).
 * @param chipMenyala chip "Termasuk stok 0" sedang aktif.
 * @param hasilTerlihat jumlah baris yang SUDAH tampil dari cache.
 * @param sudahDiperiksa kunci (kata kunci + cabang) ini sudah pernah dijawab server.
 * @param sedangMemuat panggilan sebelumnya belum selesai.
 *
 * Dua pemicunya sengaja berbeda sifat:
 *  - **chip menyala** = permintaan eksplisit, berlaku walau daftarnya sudah berisi.
 *    Mencari "kulkas" bisa memberi 3 barang berstok padahal katalognya memuat
 *    puluhan; tanpa jalur ini, chip-nya cuma janji kosong.
 *  - **daftar kosong** = jalur otomatis, karena "tidak ada hasil" adalah SATU-SATUNYA
 *    keadaan yang benar-benar menyesatkan: orang membacanya sebagai "barang ini
 *    tak ada di perusahaan", bukan sebagai "stoknya nol". Pola pemicunya meniru
 *    `InventoryViewModel.checkInTransitHint`, yang sudah menambal kelas
 *    kekeliruan bersaudara (barang hilang karena sedang mutasi antar cabang).
 */
fun perluCariStokNol(
    search: String,
    chipMenyala: Boolean,
    hasilTerlihat: Int,
    sudahDiperiksa: Boolean,
    sedangMemuat: Boolean,
): Boolean {
    if (sedangMemuat || sudahDiperiksa) return false
    // Kata kunci kosong TAK PERNAH memicu apa pun — itu yang membedakan fitur ini
    // dari "tarik seluruh katalog", dan satu-satunya hal yang menjaganya tetap murah.
    if (search.trim().length < MIN_KATA_KUNCI_STOK_NOL) return false
    return chipMenyala || hasilTerlihat == 0
}

/**
 * Kunci memo satu pencarian. Cabang IKUT jadi kunci karena hasil server
 * di-scope cabang: kata kunci sama di cabang berbeda adalah pertanyaan berbeda,
 * dan memo tanpa cabang akan menjawab pertanyaan kedua dengan hasil yang pertama.
 */
fun kunciCariStokNol(search: String, dealer: String): String = "${dealer.trim()}|${search.trim()}"

/**
 * Baris server → baris cache, HANYA untuk produk yang stoknya nol di SELURUH
 * cabang grupnya.
 *
 * **Pengelompokan `kode + kodeCabang`, bukan `kode` saja** — identitas produk di
 * app ini memang komposit (kode ERP bertabrakan antar region; lihat CLAUDE.md),
 * dan angka yang dibandingkan di sini harus persis angka yang dijumlahkan DAO
 * (`GROUP BY kode, kodeCabang` + `SUM(stok)`). Mengelompokkan dengan cara lain
 * menghasilkan vonis "nol" yang tak pernah cocok dengan yang dilihat layar.
 *
 * **Produk yang masih punya stok di salah satu cabang DIBUANG**, dan itu
 * disengaja walau server mengirimnya: produk begitu SUDAH ada di cache lewat
 * sinkronisasi biasa. Memasukkannya berarti menambahkan baris cabang berstok nol
 * ke produk yang sudah tampil — yang diam-diam mengubah rincian per cabang dan
 * ekspor CSV, dua hal yang tak seorang pun minta ubah di sini.
 *
 * Urutan masukan dipertahankan supaya hasilnya deterministik dan bisa diuji;
 * pengurutan tampilan tetap milik DAO (`sortOrder`).
 */
fun barisStokNol(rows: List<StokCabangItemDto>): List<BranchStockEntity> {
    val perProduk = rows.groupBy { it.Kode to it.kodeCabang }
    return perProduk
        // `<= 0`, bukan `== 0.0`: `Stok` adalah Double dari SP GS dan pernah
        // membawa nilai negatif (koreksi stok), yang jelas bukan "ada barangnya".
        .filterValues { grup -> grup.sumOf { it.Stok } <= 0.0 }
        .values
        .flatten()
        .map { dto ->
            BranchStockEntity(
                kode = dto.Kode,
                kodeDealer = dto.kodeDealer,
                nama = dto.Nama,
                kategori = dto.Kategori,
                merk = dto.Merk,
                harga = dto.Harga,
                stok = dto.Stok,
                kodeCabang = dto.kodeCabang,
                gambar = dto.Gambar?.trim()?.takeIf { url -> url.isNotEmpty() },
                umurHari = dto.umurHari,
                kondisi = dto.kondisi,
            )
        }
        // Baris tanpa `kodeDealer` tak bisa disimpan: ia separuh kunci primer
        // (`kode` + `kodeDealer`), jadi beberapa baris begitu akan saling menimpa
        // dan menyisakan satu baris sembarang alih-alih gagal dengan jelas.
        .filter { it.kode.isNotBlank() && it.kodeDealer.isNotBlank() }
}
