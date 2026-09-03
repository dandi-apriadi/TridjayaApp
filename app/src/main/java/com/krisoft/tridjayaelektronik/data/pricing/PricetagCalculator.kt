package com.krisoft.tridjayaelektronik.data.pricing

/** Markup tetap yang ditambahkan ke harga sistem saat mode "naik harga" aktif. */
const val PRICETAG_MARKUP = 300_000.0

/**
 * Dua angka yang tayang di label harga: [hargaBesar] adalah harga jual yang
 * dicetak besar, [hargaCoret] (kalau ada) dicetak lebih kecil dengan garis coret
 * di atasnya.
 */
data class PricetagPrice(
    val hargaBesar: Double,
    val hargaCoret: Double?
)

/**
 * Saat [markup] aktif, harga besar = harga sistem + [PRICETAG_MARKUP] dan harga
 * coret = harga sistem asli (menunjukkan harga normal di sistem, bukan diskon).
 * Saat tidak aktif, cuma satu angka: harga sistem asli, tanpa coret.
 */
fun hitungHargaPricetag(hargaAsli: Double, markup: Boolean): PricetagPrice =
    if (markup) {
        PricetagPrice(hargaBesar = hargaAsli + PRICETAG_MARKUP, hargaCoret = hargaAsli)
    } else {
        PricetagPrice(hargaBesar = hargaAsli, hargaCoret = null)
    }
