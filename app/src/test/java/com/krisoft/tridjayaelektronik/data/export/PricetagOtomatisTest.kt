package com.krisoft.tridjayaelektronik.data.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PricetagOtomatisTest {

    @Test
    fun `rupiah format Indonesia`() {
        assertEquals("Rp. 1.700.000", PricetagOtomatis.rupiah(1_700_000.0))
        assertEquals("Rp. 1.400.000", PricetagOtomatis.rupiah(1_400_000.0))
        assertEquals("Rp. 500", PricetagOtomatis.rupiah(500.0))
    }

    @Test
    fun `interior menyisakan padding aman dari border`() {
        // Kotak template promo baru (diukur piksel: 56,571,1435,863).
        val area = PricetagOtomatis.KotakHarga(56f, 571f, 1435f, 863f).interior(8f)
        assertTrue(area.kiri > 56f + 4f)
        assertTrue(area.kanan < 1435f - 4f)
        assertTrue(area.atas > 571f + 4f)
        assertTrue(area.bawah < 863f - 4f)
        assertTrue(area.lebar() > 1000f)
        assertTrue(area.tinggi() > 150f)
    }

    @Test
    fun `interior template lama tetap valid`() {
        val area = PricetagOtomatis.KotakHarga(59f, 572f, 1413f, 824f).interior(8f)
        assertTrue(area.lebar() > 1000f)
        assertTrue(area.tinggi() > 100f)
    }
}
