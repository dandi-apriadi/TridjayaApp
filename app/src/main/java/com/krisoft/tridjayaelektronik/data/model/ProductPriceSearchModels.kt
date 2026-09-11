package com.krisoft.tridjayaelektronik.data.model

import kotlinx.serialization.Serializable

/**
 * `GET /inventory/product-price-search` — autocomplete harga produk (inventory-service
 * `repository.rs::build_product_price_search_sql`, 1 baris per Kode di-rank). Login-only,
 * tanpa gate role tambahan (sama seperti `/inventory/barang`).
 */
@Serializable
data class ProductPriceSearchItemDto(
    val kode: String = "",
    val nama: String = "",
    val kategori: String = "",
    val harga: Long = 0,
    val tersediaDiCabang: Int = 0
)

@Serializable
data class ProductPriceSearchListDto(
    val items: List<ProductPriceSearchItemDto> = emptyList()
)
