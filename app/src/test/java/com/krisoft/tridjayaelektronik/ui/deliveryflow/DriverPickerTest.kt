package com.krisoft.tridjayaelektronik.ui.deliveryflow

import com.krisoft.tridjayaelektronik.data.model.DriverDto
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pengunci regresi bug 2026-08-27: SPK cabang Manado (D-06 Samrat / D-07 Bahu)
 * memberi daftar driver KOSONG karena filter lama menebak region dari
 * `cabang_name.contains("manado")` — nama kanoniknya sudah dipendekkan migrasi
 * 126 jadi "Samrat"/"Bahu". Tes 1 & 2 akan gagal kalau penyaringan berbasis nama
 * cabang dihidupkan lagi dalam bentuk apa pun.
 */
class DriverPickerTest {

    private fun driver(
        id: String = "u1",
        name: String = "Driver",
        cabangName: String = "",
        isActive: Boolean = true,
    ) = DriverDto(id = id, name = name, cabangName = cabangName, isActive = isActive)

    @Test
    fun `driver Samrat dan Bahu tetap tampil di SPK Manado`() {
        val drivers = listOf(
            driver(id = "a", name = "Jeverken", cabangName = "Samrat"),
            driver(id = "b", name = "Jonathan", cabangName = "Bahu"),
        )
        // Keduanya HARUS tetap ada; urutannya diuji terpisah (se-cabang naik).
        assertEquals(setOf("a", "b"), driverBisaDitugaskan(drivers, "D-06").map { it.effectiveId }.toSet())
        assertEquals(setOf("a", "b"), driverBisaDitugaskan(drivers, "D-07").map { it.effectiveId }.toSet())
    }

    @Test
    fun `bentuk nama panjang lama juga tetap tampil`() {
        val drivers = listOf(driver(id = "a", cabangName = "Tridjaya Elektronik Manado Bahu"))
        assertEquals(listOf("a"), driverBisaDitugaskan(drivers, "D-07").map { it.effectiveId })
    }

    @Test
    fun `driver lintas cabang tak pernah dibuang, apa pun cabang SPK-nya`() {
        val drivers = listOf(
            driver(id = "jawa", name = "Adi", cabangName = "Pagaden"),
            driver(id = "manado", name = "Budi", cabangName = "Samrat"),
        )
        assertEquals(2, driverBisaDitugaskan(drivers, "D-06").size)
        assertEquals(2, driverBisaDitugaskan(drivers, "D-01").size)
        assertEquals(2, driverBisaDitugaskan(drivers, null).size)
        assertEquals(2, driverBisaDitugaskan(drivers, "D-99").size)
    }

    @Test
    fun `cabang kosong atau tak dikenal tetap ikut, tak pernah dibuang diam-diam`() {
        val drivers = listOf(
            driver(id = "a", cabangName = ""),
            driver(id = "b", cabangName = "Head Office"),
        )
        assertEquals(listOf("a", "b"), driverBisaDitugaskan(drivers, "D-06").map { it.effectiveId }.sorted())
    }

    @Test
    fun `driver se-cabang naik ke atas, sisanya urut nama`() {
        val drivers = listOf(
            driver(id = "z", name = "Zaki", cabangName = "Pagaden"),
            driver(id = "a", name = "Andi", cabangName = "Pagaden"),
            driver(id = "s", name = "Samuel", cabangName = "Samrat"),
        )
        assertEquals(listOf("s", "a", "z"), driverBisaDitugaskan(drivers, "D-06").map { it.effectiveId })
        assertEquals(listOf("a", "z", "s"), driverBisaDitugaskan(drivers, "D-01").map { it.effectiveId })
    }

    @Test
    fun `akun nonaktif dan baris tanpa id dibuang`() {
        val drivers = listOf(
            driver(id = "aktif", name = "Aktif"),
            driver(id = "mati", name = "Mati", isActive = false),
            DriverDto(id = null, userId = null, name = "Tanpa Id"),
        )
        assertEquals(listOf("aktif"), driverBisaDitugaskan(drivers, "D-01").map { it.effectiveId })
    }

    @Test
    fun `userId dipakai saat id null`() {
        val drivers = listOf(DriverDto(id = null, userId = "u9", name = "Pakai userId"))
        assertEquals(listOf("u9"), driverBisaDitugaskan(drivers, "D-01").map { it.effectiveId })
    }

    @Test
    fun `driverSeCabang hanya untuk urutan, kode dealer kosong bukan se-cabang`() {
        assertTrue(driverSeCabang(driver(cabangName = "Samrat"), "d-06"))
        assertTrue(driverSeCabang(driver(cabangName = "Tridjaya Elektronik Manado Samrat"), "D-06"))
        assertFalse(driverSeCabang(driver(cabangName = "Samrat"), null))
        assertFalse(driverSeCabang(driver(cabangName = ""), "D-06"))
        assertFalse(driverSeCabang(driver(cabangName = "Pagaden"), "D-06"))
    }

    @Test
    fun `payload api users terurai apa adanya termasuk key asing`() {
        val json = Json { ignoreUnknownKeys = true }
        val payload = """
            {"id":"1f2e","nik":"2026060106","name":"Jeverken Kennet Guraici","role":"driver",
             "cabang_id":"samrat-manado","cabang_name":"Samrat","is_active":true,
             "roles":["driver"],"must_change_password":false}
        """.trimIndent()
        val d = json.decodeFromString(DriverDto.serializer(), payload)
        assertEquals("Samrat", d.cabangName)
        assertEquals("1f2e", d.effectiveId)
        assertTrue(d.isActive)
    }

    @Test
    fun `is_active hilang dianggap aktif supaya server lama tak menyembunyikan driver`() {
        val json = Json { ignoreUnknownKeys = true }
        val d = json.decodeFromString(DriverDto.serializer(), """{"id":"1","name":"Lama","cabang_name":"Bahu"}""")
        assertTrue(d.isActive)
        assertEquals(listOf("1"), driverBisaDitugaskan(listOf(d), "D-07").map { it.effectiveId })
    }
}
