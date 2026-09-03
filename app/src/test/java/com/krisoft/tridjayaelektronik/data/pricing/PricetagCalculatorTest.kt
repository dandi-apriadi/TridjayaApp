package com.krisoft.tridjayaelektronik.data.pricing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PricetagCalculatorTest {

    @Test
    fun `markup aktif menaikkan harga besar 300 ribu dan mencoret harga asli`() {
        val hasil = hitungHargaPricetag(hargaAsli = 1_550_000.0, markup = true)
        assertEquals(1_850_000.0, hasil.hargaBesar, 0.0)
        assertEquals(1_550_000.0, hasil.hargaCoret)
    }

    @Test
    fun `markup nonaktif memakai harga asli apa adanya tanpa coret`() {
        val hasil = hitungHargaPricetag(hargaAsli = 1_550_000.0, markup = false)
        assertEquals(1_550_000.0, hasil.hargaBesar, 0.0)
        assertNull(hasil.hargaCoret)
    }
}
