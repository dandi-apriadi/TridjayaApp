package com.krisoft.tridjayaelektronik.ui.deliveryflow

import com.krisoft.tridjayaelektronik.data.model.DeliveryJobDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cerminan `maps_terpakai` (Rust) dan `mapsTerpakai` (web). Ketiganya HARUS
 * sepakat — kalau tidak, app menahan isian yang server terima, atau meloloskan
 * yang server tolak dengan 400 setelah sales mengetik form panjang.
 *
 * "Terisi" TIDAK sama dengan "bisa dipakai": dari 2.224 unit produksi
 * (2026-08-30) 985 kosong tapi 1.008 berisi TEKS BEBAS dan hanya 231 membawa
 * tautan. Isi teratas singkatan lokal — "hgl" 236x, "sbg" 44x, "pmk" 21x.
 */
class MapsTerpakaiTest {

    @Test
    fun `tautan dan koordinat diterima`() {
        for (baik in listOf(
            "https://maps.app.goo.gl/abcd1234",
            "http://maps.google.com/?q=-6.2,106.8",
            "HTTPS://GOO.GL/MAPS/XYZ",
            "-6.123456, 106.789012",
            "-6.123456,106.789012",
        )) {
            assertTrue(baik, mapsTerpakai(baik))
        }
    }

    /** Nilai NYATA dari kolom produksi — semuanya harus ditolak. */
    @Test
    fun `singkatan lokal dan kalimat non-lokasi ditolak`() {
        for (buruk in listOf(
            "hgl", "sbg", "pmk", "sesuai sharelok", "sdh terkirim", "", "   ", "-",
        )) {
            assertFalse(buruk, mapsTerpakai(buruk))
        }
        assertFalse(mapsTerpakai(null))
    }

    /** Sengaja permisif soal domain — tautan pendek, Apple Maps, hasil bagikan
     *  WhatsApp semuanya sah. Salah-tolak lebih mahal daripada satu tautan aneh. */
    @Test
    fun `domain tidak disaring`() {
        assertTrue(mapsTerpakai("https://wa.me/x?loc=1"))
        assertTrue(mapsTerpakai("https://apple.co/3xyzAbc"))
    }

    @Test
    fun `pasangan yang bukan angka ditolak`() {
        assertFalse(mapsTerpakai("depan, masjid"))
        assertFalse(mapsTerpakai("-6.123456, "))
    }

    /**
     * INTI pencegahan: form SPK menolak isi yang tak bisa dibuka, TAPI kosong
     * tetap boleh — mewajibkannya akan menghentikan pembuatan SPK untuk sales
     * yang memang belum memegang lokasinya.
     */
    @Test
    fun `blocker menolak maps tak terpakai tapi menerima kosong`() {
        fun blocker(mapUrl: String) = spkSubmitBlocker(
            pelanggan = "Budi Santoso",
            telepon = "081234567",
            nik = "",
            mapUrl = mapUrl,
            deliveryMethod = "driver",
            spkCabang = "D-01",
            itemsCount = 1,
            itemsValid = true,
            totalUnits = 1,
        )
        assertNull("kosong harus tetap lolos", blocker(""))
        assertNull(blocker("https://maps.app.goo.gl/abcd1234"))
        assertEquals(
            "Link Lokasi Maps belum berupa link atau koordinat — tempel link dari " +
                "Google Maps, atau kosongkan dulu.",
            blocker("hgl"),
        )
    }

    /**
     * Jendela isi-maps JAUH lebih lebar dari jendela sunting SPK, dan itu
     * memang bedanya: tanpa lokasi unitnya tak bisa dijadwalkan sama sekali,
     * jadi menutupnya bersama jendela sunting membuat unit diam sampai ada yang
     * membuka dashboard web.
     */
    @Test
    fun `boleh isi maps sesudah PDI dan sesudah tercatat di GS`() {
        val job = jobDummy(status = "pending_spk", noTransaksi = "D-01/PJB2026/abc")
        assertFalse(
            "jendela sunting SPK memang sudah tutup di sini",
            bolehSuntingSpk(job, isAdmin = false, currentUserId = "sales-1"),
        )
        assertTrue(bolehIsiMapsSpk(job, isAdmin = false, currentUserId = "sales-1"))
        assertTrue(
            bolehIsiMapsSpk(jobDummy(status = "in_transit"), false, "sales-1"),
        )
    }

    @Test
    fun `tidak boleh sesudah unit terkirim atau dibatalkan`() {
        assertFalse(bolehIsiMapsSpk(jobDummy(status = "delivered"), false, "sales-1"))
        assertFalse(bolehIsiMapsSpk(jobDummy(status = "cancelled"), false, "sales-1"))
        // Admin pun tidak — unit yang sudah sampai tak tertolong lokasi baru.
        assertFalse(bolehIsiMapsSpk(jobDummy(status = "delivered"), true, "x"))
    }

    /** Sales LAIN tetap ditolak — jendela lebar bukan berarti pintu terbuka. */
    @Test
    fun `sales bukan pemilik ditolak`() {
        val job = jobDummy(status = "pending_spk")
        assertFalse(bolehIsiMapsSpk(job, isAdmin = false, currentUserId = "sales-2"))
        assertFalse(
            bolehIsiMapsSpk(jobDummy(status = "pending_spk", salesUserId = null), false, "sales-1"),
        )
        // Admin boleh untuk SPK cabang mana pun.
        assertTrue(bolehIsiMapsSpk(job, isAdmin = true, currentUserId = "orang-lain"))
    }

    private fun jobDummy(
        status: String,
        noTransaksi: String? = null,
        salesUserId: String? = "sales-1",
    ) = DeliveryJobDto(
        id = "job-1",
        kodePengiriman = "DLV-X-1u1",
        status = status,
        noTransaksi = noTransaksi,
        salesUserId = salesUserId,
    )
}
