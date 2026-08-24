package com.krisoft.tridjayaelektronik.ui.opname

import com.krisoft.tridjayaelektronik.data.model.OpnameSessionDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Cerminan `parse_jendela` + validasi urutan di `inventory-service/src/opname.rs`.
 *
 * Diperiksa di klien BUKAN untuk menggantikan server, tapi supaya petugas tahu
 * kolom mana yang salah sebelum menekan Simpan — 400 sesudah submit cuma
 * berkata "harus berformat", tanpa menunjuk kolomnya.
 */
class OpnameJendelaTest {

    @Test
    fun `menerima spasi maupun T, keluarannya satu bentuk`() {
        assertEquals("2026-08-09T08:00", normalisasiJendela("2026-08-09 08:00"))
        assertEquals("2026-08-09T08:00", normalisasiJendela("2026-08-09T08:00"))
        assertEquals("2026-08-09T08:00", normalisasiJendela("  2026-08-09 08:00  "))
    }

    @Test
    fun `menolak rentang angka yang mustahil`() {
        // Regex saja meloloskan ini, dan server akan menolaknya SESUDAH submit
        // dengan pesan yang tak menunjuk kolomnya.
        assertNull("bulan 13", normalisasiJendela("2026-13-09 08:00"))
        assertNull("hari 32", normalisasiJendela("2026-08-32 08:00"))
        assertNull("jam 25", normalisasiJendela("2026-08-09 25:00"))
        assertNull("menit 60", normalisasiJendela("2026-08-09 08:60"))
    }

    @Test
    fun `menolak bentuk yang tak lengkap`() {
        assertNull(normalisasiJendela("2026-08-09"))
        assertNull(normalisasiJendela("08:00"))
        assertNull(normalisasiJendela("besok pagi"))
        assertNull(normalisasiJendela(""))
    }

    @Test
    fun `dua kolom kosong berarti sesi tanpa batas waktu, bukan kesalahan`() {
        // Perilaku LAMA, dan tetap sah — seluruh sesi pra-migrasi 196 begitu.
        assertNull(masalahJendela("", ""))
    }

    @Test
    fun `satu sisi saja tetap sah`() {
        assertNull(masalahJendela("2026-08-09 08:00", ""))
        assertNull(masalahJendela("", "2026-08-09 17:00"))
    }

    @Test
    fun `selesai sebelum mulai ditolak`() {
        // Jendela yang tak pernah terbuka: server akan menolak SETIAP scan, dan
        // petugas cuma melihat "di luar jendela" berulang tanpa tahu sesinya
        // yang salah dibuat.
        assertNotNull(masalahJendela("2026-08-09 17:00", "2026-08-09 08:00"))
        assertNotNull("sama persis juga bukan jendela", masalahJendela("2026-08-09 08:00", "2026-08-09 08:00"))
    }

    @Test
    fun `lintas hari sah selama urutannya benar`() {
        assertNull(masalahJendela("2026-08-09 20:00", "2026-08-10 06:00"))
    }

    @Test
    fun `pesan menyebut kolom mana yang salah`() {
        assertEquals(
            "Waktu mulai harus berformat YYYY-MM-DD HH:MM",
            masalahJendela("ngawur", "2026-08-09 17:00"),
        )
        assertEquals(
            "Waktu selesai harus berformat YYYY-MM-DD HH:MM",
            masalahJendela("2026-08-09 08:00", "ngawur"),
        )
    }

    @Test
    fun `label ringkas dan tak mengulang tanggal untuk sesi sehari`() {
        assertEquals("09/08 08:00-17:00", labelJendela("2026-08-09T08:00:00", "2026-08-09T17:00:00"))
        assertEquals(
            "09/08 20:00 - 10/08 06:00",
            labelJendela("2026-08-09T20:00:00", "2026-08-10T06:00:00"),
        )
        assertEquals("mulai 09/08 08:00", labelJendela("2026-08-09T08:00:00", null))
        assertEquals("sampai 09/08 17:00", labelJendela(null, "2026-08-09T17:00:00"))
    }

    @Test
    fun `tanpa jendela menghasilkan null, bukan kalimat`() {
        // Pemanggil harus bisa membedakan "tanpa batas" dari "sudah lewat".
        assertNull(labelJendela(null, null))
        assertNull(labelJendela("", ""))
    }

    /**
     * Kartu daftar sesi (`OpnameListScreen`) memakai label yang SAMA dengan
     * layar detail, dan sumbernya `OpnameSessionDto` — bukan `OpnameDetailDto`.
     *
     * Dua DTO itu terpisah (endpoint detail mem-flatten sesi ke objek yang sama
     * dengan `items`), jadi field jendela harus ada di KEDUANYA. Kalau salah
     * satunya tertinggal, gejalanya bukan galat kompilasi melainkan kartu yang
     * diam-diam tak pernah menampilkan jendela.
     */
    @Test
    fun `dto daftar membawa jendela dan bisa dilabeli`() {
        val sesi = OpnameSessionDto(
            id = "s-1",
            kodeOpname = "OPN-20260809-0001",
            mulaiAt = "2026-08-09T08:00:00",
            selesaiAt = "2026-08-09T17:00:00",
        )
        assertEquals("09/08 08:00-17:00", labelJendela(sesi.mulaiAt, sesi.selesaiAt))
    }

    @Test
    fun `sesi daftar tanpa selesaiAt bukan sesi terlambat`() {
        // Peringatan ini ditulis eksplisit di `OpnameJendela.kt`: `selesaiAt`
        // kosong = boleh kapan saja (seluruh sesi pra-migrasi 196), jadi
        // kartunya menyebut "mulai …" dan TIDAK boleh menyiratkan tenggat.
        val sesi = OpnameSessionDto(id = "s-1", mulaiAt = "2026-08-09T08:00:00")
        assertEquals("mulai 09/08 08:00", labelJendela(sesi.mulaiAt, sesi.selesaiAt))
        assertNull(labelJendela(OpnameSessionDto(id = "s-2").mulaiAt, null))
    }
}
