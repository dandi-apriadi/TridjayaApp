package com.krisoft.tridjayaelektronik.ui.deliveryflow

import com.krisoft.tridjayaelektronik.data.model.DeliveryJobDto
import com.krisoft.tridjayaelektronik.data.model.DeliveryStatusKey
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Cerminan `frontend/src/utils/__tests__/spkEdit.spec.ts` — aturan patch harus
 *  sama di kedua klien, kalau tidak koreksi dari HP dan dari web berbeda hasil. */
class SpkEditFieldsTest {

    private val job = DeliveryJobDto(
        id = "j1",
        kodePengiriman = "DLV-M5081A724-1u1",
        status = DeliveryStatusKey.PENDING_PDI,
        kodeBarang = "TE-9093",
        namaBarang = "SEPEDA LISTRIK SAIGE LATTE PRO RED",
        kategori = "SEPEDA LISTRIK",
        merk = "SAIGE",
        tipe = "LATTE PRO RED",
        warna = "grey",
        customerName = "EUIS SETIANAH",
        customerAddress = "kp. gardu",
        paymentType = "cash",
        hargaOtr = 4_199_000.0,
        diskon = 200_000.0,
    )

    @Test
    fun `angka bulat dicetak tanpa desimal supaya tak terbaca sebagai perubahan`() {
        val form = spkEditFormFromJob(job)
        assertEquals("4199000", form["hargaOtr"])
        assertNull(buildSpkEditPatch(form, job, "tak menyentuh apa pun"))
    }

    @Test
    fun `hanya field yang berubah yang dikirim`() {
        val form = spkEditFormFromJob(job) + mapOf(
            "kodeBarang" to "TE-9091",
            "tipe" to "LATTE PRO GREY",
        )
        val patch = buildSpkEditPatch(form, job, "salah varian warna")!!
        assertEquals(setOf("kodeBarang", "tipe", "alasan"), patch.keys)
        assertEquals("TE-9091", patch["kodeBarang"]!!.jsonPrimitive.content)
        assertEquals("salah varian warna", patch["alasan"]!!.jsonPrimitive.content)
    }

    @Test
    fun `spasi di ujung bukan perubahan`() {
        val form = spkEditFormFromJob(job) + ("merk" to "  SAIGE  ")
        assertNull(buildSpkEditPatch(form, job, "apa saja"))
    }

    @Test
    fun `angka dikirim sebagai angka bukan teks`() {
        val form = spkEditFormFromJob(job) + ("hargaOtr" to "4500000")
        val patch = buildSpkEditPatch(form, job, "harga salah ketik")!!
        assertNull(patch["hargaOtr"]!!.jsonPrimitive.isString.takeIf { it })
        assertEquals(4_500_000.0, patch["hargaOtr"]!!.jsonPrimitive.content.toDouble(), 0.0)
    }

    @Test
    fun `angka tak terbaca diabaikan, tak dikirim sebagai teks`() {
        val form = spkEditFormFromJob(job) + ("hargaOtr" to "empat juta")
        assertNull(buildSpkEditPatch(form, job, "apa saja"))
    }

    @Test
    fun `teks dikosongkan berarti perintah kosongkan kolom`() {
        val form = spkEditFormFromJob(job) + ("warna" to "")
        val patch = buildSpkEditPatch(form, job, "warna salah isi")!!
        assertEquals(setOf("warna", "alasan"), patch.keys)
        assertEquals("", patch["warna"]!!.jsonPrimitive.content)
    }

    @Test
    fun `diskon dan hargaTotal bukan field yang bisa disunting`() {
        val keys = SPK_EDIT_FIELDS.map { it.key }
        assertFalse("diskon" in keys)
        assertFalse("hargaTotal" in keys)
    }

    @Test
    fun `boleh disunting hanya sebelum PDI dan sebelum tercatat di GS`() {
        assertTrue(spkBolehDisunting(job))
        assertTrue(spkBolehDisunting(job.copy(status = DeliveryStatusKey.PENDING_DISCOUNT)))
        assertFalse(spkBolehDisunting(job.copy(noTransaksi = "INV-1")))
        for (s in listOf(
            DeliveryStatusKey.PENDING_SPK,
            DeliveryStatusKey.PENDING_DELIVERY_NOTE,
            DeliveryStatusKey.PENDING_SCHEDULING,
            DeliveryStatusKey.ASSIGNED,
            DeliveryStatusKey.IN_TRANSIT,
            DeliveryStatusKey.DELIVERED,
            DeliveryStatusKey.CANCELLED,
        )) {
            assertFalse(s, spkBolehDisunting(job.copy(status = s)))
        }
    }

    /**
     * Gate "Ubah Isi SPK" dilebarkan 2026-08-06 (sales PEMILIK saat
     * `pending_discount`) lalu **DILEBARKAN LAGI 2026-08-29** ke `pending_pdi`:
     * jendelanya kini "belum sampai PDI". SPK tanpa diskon lahir langsung di
     * `pending_pdi`, jadi aturan lama membuat mayoritas sales tak pernah punya
     * jendela koreksi sama sekali.
     *
     * Yang TIDAK ikut melebar: sales lain (kepemilikan tetap satu-satunya
     * pagar) dan tahap SESUDAH PDI — termasuk `pending_perbaikan`, yang
     * [spkBolehDisunting] izinkan untuk ADMIN tapi server tolak untuk sales.
     */
    @Test
    fun `sales pemilik boleh menyunting selama belum sampai PDI`() {
        val diskon = job.copy(status = DeliveryStatusKey.PENDING_DISCOUNT, salesUserId = "U1")
        val pdi = job.copy(status = DeliveryStatusKey.PENDING_PDI, salesUserId = "U1")
        assertTrue(bolehSuntingSpk(diskon, isAdmin = false, currentUserId = "U1"))
        assertTrue(bolehSuntingSpk(pdi, isAdmin = false, currentUserId = "U1"))
        // Sales LAIN: 403 di server, jadi tombolnya tak boleh muncul.
        assertFalse(bolehSuntingSpk(diskon, isAdmin = false, currentUserId = "U2"))
        assertFalse(bolehSuntingSpk(pdi, isAdmin = false, currentUserId = "U2"))
        // Tahap SESUDAH PDI tetap bukan haknya — `pending_perbaikan` termasuk,
        // walau daftar tampil-tombol ADMIN memuatnya.
        assertFalse(
            bolehSuntingSpk(
                job.copy(status = DeliveryStatusKey.PENDING_PERBAIKAN, salesUserId = "U1"),
                isAdmin = false, currentUserId = "U1",
            )
        )
        assertFalse(
            bolehSuntingSpk(
                job.copy(status = DeliveryStatusKey.PENDING_SPK, salesUserId = "U1"),
                isAdmin = false, currentUserId = "U1",
            )
        )
        // Admin tak kehilangan apa pun.
        assertTrue(bolehSuntingSpk(job.copy(status = DeliveryStatusKey.PENDING_PDI), isAdmin = true, currentUserId = ""))
        assertTrue(
            bolehSuntingSpk(
                job.copy(status = DeliveryStatusKey.PENDING_PERBAIKAN),
                isAdmin = true, currentUserId = "",
            )
        )
        // Sudah tercatat di GS = tertutup untuk SEMUA orang, admin sekalipun.
        assertFalse(bolehSuntingSpk(diskon.copy(noTransaksi = "INV-1"), isAdmin = true, currentUserId = "U1"))
        assertFalse(bolehSuntingSpk(pdi.copy(noTransaksi = "INV-1"), isAdmin = false, currentUserId = "U1"))
    }

    /** `salesUserId` kosong + `currentUserId` kosong TIDAK boleh saling cocok —
     *  itu akan memberi hak sunting ke siapa pun yang membuka SPK lama. */
    @Test
    fun `pemilik kosong bukan berarti semua orang pemilik`() {
        val tanpaPemilik = job.copy(status = DeliveryStatusKey.PENDING_DISCOUNT, salesUserId = null)
        assertFalse(bolehSuntingSpk(tanpaPemilik, isAdmin = false, currentUserId = ""))
        assertFalse(bolehSuntingSpk(tanpaPemilik.copy(salesUserId = ""), isAdmin = false, currentUserId = ""))
    }

    @Test
    fun `ambang alasan sama dengan server`() {
        assertFalse(spkEditAlasanValid("oops"))
        assertFalse(spkEditAlasanValid("     "))
        assertTrue(spkEditAlasanValid("salah varian"))
    }
}
