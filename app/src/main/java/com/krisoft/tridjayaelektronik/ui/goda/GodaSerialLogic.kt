package com.krisoft.tridjayaelektronik.ui.goda

import com.krisoft.tridjayaelektronik.data.model.GodaBarisDto

/**
 * Aturan layar SN Goda yang bisa diuji TANPA Android — sengaja dipisah dari
 * [GodaSerialViewModel] supaya cerminan aturan server bisa dijaga test biasa.
 */

/**
 * Cerminan `goda.rs::bersihkan_sn` sisi klien: `trim` + huruf besar.
 *
 * `uppercase()` (bukan `toUpperCase()` yang usang) DISENGAJA — yang pertama
 * memakai `Locale.ROOT`, yang kedua locale perangkat. Di HP ber-locale Turki
 * "i" jadi "İ", jadi SN yang diketik petugas akan berbeda dari SN yang sama
 * yang dinormalkan server, dan bedanya cuma muncul sebagai duplikat yang lolos.
 */
fun rapikanSn(masukan: String): String = masukan.trim().uppercase()

/**
 * `null` = boleh dikirim. Pesan = alasan penolakan, memakai kalimat yang sama
 * dengan server supaya orang tak melihat dua bunyi berbeda untuk aturan yang
 * sama.
 *
 * Aturannya sengaja cuma dua (kosong & panjang) — persis `bersihkan_sn`. Pola/
 * panjang minimum TIDAK dipaksakan: registry memuat SN beberapa merk dengan
 * format berbeda, dan menolak yang sah cuma memindahkan pekerjaan ke kertas.
 */
fun periksaSn(masukan: String): String? {
    val bersih = rapikanSn(masukan)
    return when {
        bersih.isEmpty() -> "Serial number tidak boleh kosong"
        // `codePointCount`, bukan `length`: server menghitung KARAKTER
        // (`chars().count()`), sedangkan `length` menghitung unit UTF-16.
        bersih.codePointCount(0, bersih.length) > 64 -> "Serial number maksimal 64 karakter"
        else -> null
    }
}

/** Baris yang SN-nya belum lengkap — `stok` fisik lebih banyak dari SN terdaftar. */
fun belumLengkap(baris: GodaBarisDto): Boolean = baris.jumlahSn < baris.stok

/**
 * Penyaring daftar barang. Pencarian mencakup nama, kode, dan TIPE — tiga hal
 * yang sama-sama dipakai orang gudang untuk menyebut unit yang sama.
 */
fun saringBaris(
    baris: List<GodaBarisDto>,
    cari: String,
    hanyaBelumLengkap: Boolean
): List<GodaBarisDto> {
    val kunci = cari.trim()
    return baris.filter { b ->
        val cocokCari = kunci.isBlank() ||
            b.namaBarang.contains(kunci, ignoreCase = true) ||
            b.kodeBarang.contains(kunci, ignoreCase = true) ||
            b.tipe.contains(kunci, ignoreCase = true)
        val cocokLengkap = !hanyaBelumLengkap || belumLengkap(b)
        cocokCari && cocokLengkap
    }
}

/** SN yang SUDAH terdaftar untuk barang ini — server menolaknya (UNIQUE 1062). */
fun sudahTerdaftarDiBarangIni(baris: GodaBarisDto, sn: String): Boolean =
    baris.serials.any { it.serialNumber.equals(sn, ignoreCase = true) }

/**
 * Barang LAIN di cabang yang sama yang sudah memakai SN ini, kalau ada.
 *
 * Server TIDAK menolak keadaan ini — kunci uniknya `(dealer, barang, serial)` —
 * jadi ini PERINGATAN, bukan penghalang: satu unit fisik memang cuma boleh ada
 * di satu SKU, tapi yang tahu mana yang benar adalah orang yang memegang
 * unitnya, bukan app. Memblokirnya berarti data yang salah tak bisa diperbaiki
 * dari lapangan sama sekali.
 */
fun barangLainDenganSn(
    semua: List<GodaBarisDto>,
    sn: String,
    kodeBarangIni: String
): GodaBarisDto? = semua.firstOrNull { b ->
    b.kodeBarang != kodeBarangIni && b.serials.any { it.serialNumber.equals(sn, ignoreCase = true) }
}
