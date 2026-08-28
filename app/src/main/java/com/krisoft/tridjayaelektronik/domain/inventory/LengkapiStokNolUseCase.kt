package com.krisoft.tridjayaelektronik.domain.inventory

import com.krisoft.tridjayaelektronik.data.AuthResult
import com.krisoft.tridjayaelektronik.data.InventoryRepository
import javax.inject.Inject

/**
 * Tambal cache Inventory dengan barang berstok nol yang cocok kata kunci.
 * Aturannya (kapan dipanggil, kenapa bukan sinkron penuh) ada di
 * [KatalogStokNol.kt][perluCariStokNol].
 */
class LengkapiStokNolUseCase @Inject constructor(
    private val inventoryRepository: InventoryRepository
) {
    suspend operator fun invoke(search: String, dealer: String): AuthResult<Int> =
        inventoryRepository.lengkapiStokNol(search, dealer)
}
