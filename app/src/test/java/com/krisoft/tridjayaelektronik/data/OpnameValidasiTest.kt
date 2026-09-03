package com.krisoft.tridjayaelektronik.data

import com.krisoft.tridjayaelektronik.data.local.OpnameUnitDao
import com.krisoft.tridjayaelektronik.data.local.OpnameUnitEntity
import com.krisoft.tridjayaelektronik.data.model.ApiResponse
import com.krisoft.tridjayaelektronik.data.model.ManualUnitDto
import com.krisoft.tridjayaelektronik.data.model.ManualUnitListData
import com.krisoft.tridjayaelektronik.data.model.OpnameDetailDto
import com.krisoft.tridjayaelektronik.data.model.RejectUnitBody
import com.krisoft.tridjayaelektronik.ui.opname.buangUnit
import com.krisoft.tridjayaelektronik.ui.opname.rencanaApproveMassal
import com.krisoft.tridjayaelektronik.ui.opname.ringkasHasilMassal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

/**
 * Antrian validasi unit ketik-manual (admin-stok). Yang dijaga di sini adalah
 * keputusan REPOSITORI, bukan Retrofit/Room — sisi yang kalau rusak gagal
 * SENYAP: tolak tanpa alasan yang baru ketahuan di server, dua id path yang
 * tertukar, dan gagal-muat yang menyamar jadi "tak ada yang menunggu"
 * (pemutus mengira pekerjaannya kosong sementara sesi cabang tetap terkunci).
 */
class OpnameValidasiTest {

    private val sessionId = "sesi-1"

    private class FakeApi : StubInventoryApi() {
        var items: List<ManualUnitDto> = emptyList()
        var gagalList = false
        var listStatus: String? = null
        var approveCalls = mutableListOf<Pair<String, String>>()
        var rejectCalls = mutableListOf<Triple<String, String, String>>()
        var photoCalls = mutableListOf<String>()
        var photoBytes: ByteArray? = null

        override suspend fun manualUnits(status: String?): Response<ApiResponse<ManualUnitListData>> {
            listStatus = status
            if (gagalList) {
                return Response.error(500, "".toResponseBody("application/json".toMediaType()))
            }
            return Response.success(ApiResponse("ok", ManualUnitListData(items.size, status ?: "", items)))
        }

        override suspend fun approveManualUnit(id: String, unitId: String):
            Response<ApiResponse<OpnameDetailDto>> {
            approveCalls += id to unitId
            return Response.success(ApiResponse("ok", OpnameDetailDto()))
        }

        override suspend fun rejectManualUnit(id: String, unitId: String, body: RejectUnitBody):
            Response<ApiResponse<OpnameDetailDto>> {
            rejectCalls += Triple(id, unitId, body.alasan)
            return Response.success(ApiResponse("ok", OpnameDetailDto()))
        }

        override suspend fun serialPhoto(filename: String): Response<okhttp3.ResponseBody> {
            photoCalls += filename
            val bytes = photoBytes ?: return Response.error(
                404, "".toResponseBody("application/json".toMediaType())
            )
            return Response.success(bytes.toResponseBody("image/jpeg".toMediaType()))
        }
    }

    /** Antrian validasi tak menyentuh buffer lokal sama sekali. */
    private class UnusedDao : OpnameUnitDao {
        private fun nope(): Nothing = error("DAO tak dipakai di antrian validasi")
        override fun observe(sessionId: String): Flow<List<OpnameUnitEntity>> = flowOf(emptyList())
        override suspend fun pending(sessionId: String) = emptyList<OpnameUnitEntity>()
        override suspend fun countSerial(sessionId: String, serialNumber: String) = 0
        override suspend fun updateValidation(
            sessionId: String,
            serialNumber: String,
            status: String?,
            reason: String?,
            sebelumMillis: Long
        ) = nope()
        override suspend fun all(sessionId: String) = emptyList<OpnameUnitEntity>()
        override suspend fun countAll(sessionId: String) = 0
        override suspend fun upsert(entity: OpnameUnitEntity) = nope()
        override suspend fun markSynced(sessionId: String, serialNumber: String, now: Long, temuan: String?) = nope()
        override suspend fun delete(sessionId: String, serialNumber: String) = nope()
        override suspend fun clearSession(sessionId: String) = nope()
    }

    private fun repo(api: FakeApi) = OpnameRepository(api, UnusedDao())

    private fun unit(id: String) = ManualUnitDto(id = id, serialNumber = "SN-$id", sessionId = sessionId)

    @Test
    fun `tolak tanpa alasan ditolak sebelum menyentuh jaringan`() = runBlocking {
        val api = FakeApi()

        val hasil = repo(api).rejectManualUnit(sessionId, "u1", "   ")

        assertTrue(hasil is AuthResult.Failure)
        assertEquals("Alasan penolakan wajib diisi", (hasil as AuthResult.Failure).message)
        assertTrue("server tak boleh dipanggil sama sekali", api.rejectCalls.isEmpty())
    }

    @Test
    fun `tolak mengirim sessionId dan unitId ke path yang benar`() = runBlocking {
        val api = FakeApi()

        val hasil = repo(api).rejectManualUnit(sessionId, "u1", "  foto buram  ")

        assertTrue(hasil is AuthResult.Success)
        // Urutan tertukar = 404, bukan pesan yang menjelaskan apa pun.
        assertEquals(listOf(Triple(sessionId, "u1", "foto buram")), api.rejectCalls)
    }

    @Test
    fun `setujui mengirim dua id, bukan satu`() = runBlocking {
        val api = FakeApi()

        assertTrue(repo(api).approveManualUnit(sessionId, "u1") is AuthResult.Success)
        assertEquals(listOf(sessionId to "u1"), api.approveCalls)
    }

    @Test
    fun `gagal memuat dilaporkan gagal, bukan daftar kosong`() = runBlocking {
        val api = FakeApi().apply { gagalList = true }

        val hasil = repo(api).manualUnits()

        assertTrue(
            "gagal-muat yang jadi Success(emptyList) bikin pemutus mengira antriannya kosong",
            hasil is AuthResult.Failure
        )
    }

    @Test
    fun `antrian default hanya yang pending`() = runBlocking {
        val api = FakeApi().apply { items = listOf(unit("u1"), unit("u2")) }

        val hasil = repo(api).manualUnits()

        assertTrue(hasil is AuthResult.Success)
        assertEquals(2, (hasil as AuthResult.Success).data.size)
        assertEquals("pending", api.listStatus)
    }

    @Test
    fun `baris yang sudah diputus hilang dari daftar`() {
        val items = listOf(unit("u1"), unit("u2"), unit("u3"))
        assertEquals(listOf("u1", "u3"), buangUnit(items, "u2").map { it.id })
        // Id yang tak ada tak boleh mengosongkan daftar.
        assertEquals(3, buangUnit(items, "u9").size)
    }

    // ── Approve massal ───────────────────────────────────────────────────────

    /**
     * `approve-batch` ber-path `/opname/{id}/units/approve-batch` — SATU
     * panggilan hanya menyentuh SATU sesi, sedangkan antrian layar ini datang
     * dari `GET /opname/manual-units` yang LINTAS-sesi. Tanpa pengelompokan,
     * unit sesi lain dikirim ke sessionId yang salah dan dilaporkan gagal.
     */
    @Test
    fun `rencana massal mengelompokkan unit per sesi`() {
        val items = listOf(
            ManualUnitDto(id = "a1", sessionId = "S1"),
            ManualUnitDto(id = "b1", sessionId = "S2"),
            ManualUnitDto(id = "a2", sessionId = "S1"),
        )
        val rencana = rencanaApproveMassal(items)
        assertEquals(2, rencana.size)
        assertEquals("S1" to listOf("a1", "a2"), rencana[0])
        assertEquals("S2" to listOf("b1"), rencana[1])
    }

    /** Baris tanpa `sessionId` tak punya tujuan panggilan — dibuang, bukan
     *  dikirim ke path kosong yang jadi 404. */
    @Test
    fun `unit tanpa sessionId tidak ikut rencana`() {
        val rencana = rencanaApproveMassal(
            listOf(ManualUnitDto(id = "x", sessionId = ""), ManualUnitDto(id = "y", sessionId = "S1"))
        )
        assertEquals(listOf("S1" to listOf("y")), rencana)
    }

    /**
     * Daftar KOSONG ditolak sebelum menyentuh jaringan: server memperlakukan
     * `unitIds: []` sebagai "SELURUH pending sesi ini", jadi mengirimnya berarti
     * menyetujui unit yang tak pernah tampil di layar pemutusnya.
     */
    @Test
    fun `approve massal menolak daftar kosong sebelum menyentuh jaringan`() = runBlocking {
        val api = FakeApi()

        val hasil = repo(api).approveManualUnitsBatch(sessionId, emptyList())

        assertTrue(hasil is AuthResult.Failure)
        assertEquals("validation", (hasil as AuthResult.Failure).code)
    }

    /**
     * `gagal > 0` adalah hasil yang SAH (unit sudah diputus orang lain di sela),
     * bukan error — kalimatnya harus menyebut angkanya, bukan menyuruh
     * mencoba lagi.
     */
    @Test
    fun `ringkasan hasil massal membedakan sebagian gagal dari gagal total`() {
        assertEquals("5 unit disetujui.", ringkasHasilMassal(5, 0))
        assertTrue(ringkasHasilMassal(3, 2).contains("3 unit disetujui, 2 gagal"))
        assertTrue(ringkasHasilMassal(0, 4).contains("4 unit gagal"))
        assertEquals("Tak ada unit yang diproses.", ringkasHasilMassal(0, 0))
    }

    @Test
    fun `foto dikirim sebagai nama berkas, bukan path`() = runBlocking {
        val api = FakeApi().apply { photoBytes = byteArrayOf(1, 2, 3) }

        val bytes = repo(api).fetchSerialPhoto("/uploads/serial/abc-123.jpg")

        // Retrofit meng-encode `/` di @Path jadi %2F dan backend menolaknya.
        assertEquals(listOf("abc-123.jpg"), api.photoCalls)
        assertEquals(3, bytes?.size)
    }

    @Test
    fun `foto hilang di server tidak melempar, cuma null`() = runBlocking {
        val api = FakeApi()

        assertNull(repo(api).fetchSerialPhoto("/uploads/serial/hilang.jpg"))
    }
}
