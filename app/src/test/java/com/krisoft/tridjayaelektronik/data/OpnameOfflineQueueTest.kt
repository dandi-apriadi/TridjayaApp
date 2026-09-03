package com.krisoft.tridjayaelektronik.data

import com.krisoft.tridjayaelektronik.data.local.OpnameUnitDao
import com.krisoft.tridjayaelektronik.data.local.OpnameUnitEntity
import com.krisoft.tridjayaelektronik.data.model.InTransitHintDto
import com.krisoft.tridjayaelektronik.data.model.TandaiNihilRequest
import com.krisoft.tridjayaelektronik.data.model.ApiResponse
import com.krisoft.tridjayaelektronik.data.model.CreateIndentRequest
import com.krisoft.tridjayaelektronik.data.model.CreateOpnameRequest
import com.krisoft.tridjayaelektronik.data.model.CreateOpnameUnitsData
import com.krisoft.tridjayaelektronik.data.model.CreateOpnameUnitsRequest
import com.krisoft.tridjayaelektronik.data.model.IndentDto
import com.krisoft.tridjayaelektronik.data.model.IndentListData
import com.krisoft.tridjayaelektronik.data.model.ManualUnitListData
import com.krisoft.tridjayaelektronik.data.model.RejectUnitBody
import com.krisoft.tridjayaelektronik.data.model.MutasiHistoriDetailListDto
import com.krisoft.tridjayaelektronik.data.model.MutasiHistoriListDto
import com.krisoft.tridjayaelektronik.data.model.OpnameContextDto
import com.krisoft.tridjayaelektronik.data.model.OpnameDeleteData
import com.krisoft.tridjayaelektronik.data.model.OpnameDetailDto
import com.krisoft.tridjayaelektronik.data.model.OpnameListData
import com.krisoft.tridjayaelektronik.data.model.OpnameStockData
import com.krisoft.tridjayaelektronik.data.model.OpnameStockItemDto
import com.krisoft.tridjayaelektronik.data.model.OpnameUnitAccepted
import com.krisoft.tridjayaelektronik.data.model.OpnameUnitDto
import com.krisoft.tridjayaelektronik.data.model.OpnameUnitListData
import com.krisoft.tridjayaelektronik.data.model.OpnameUnitRejected
import com.krisoft.tridjayaelektronik.data.model.StokCabangPageDto
import com.krisoft.tridjayaelektronik.data.model.UpdateIndentRequest
import com.krisoft.tridjayaelektronik.data.model.UploadProofResponseDto
import com.krisoft.tridjayaelektronik.data.remote.InventoryApi
import com.krisoft.tridjayaelektronik.ui.opname.pesanUnitManual
import com.krisoft.tridjayaelektronik.ui.opname.temuanLabel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response
import java.io.IOException

/**
 * Antrean offline opname: satu unit yang discan tanpa sinyal harus TETAP terhitung di HP dan
 * ikut terkirim begitu jaringan pulih. Ini bagian yang tak boleh diam-diam rusak — kalau
 * antrean hilang, hitungan fisik yang sudah dilakukan petugas di gudang ikut hilang, dan
 * tak ada cara mengetahuinya selain menghitung ulang seisi gudang.
 *
 * Sengaja memakai DAO/API palsu di JVM: yang diuji keputusan repositorinya, bukan Room/Retrofit.
 */
class OpnameOfflineQueueTest {

    private val sessionId = "sesi-1"

    // ---- Test doubles -----------------------------------------------------------------

    private class FakeUnitDao : OpnameUnitDao {
        val rows = mutableListOf<OpnameUnitEntity>()

        /**
         * Serial yang DIKIRIM ke `updateValidation`, sebelum WHERE-nya menyaring.
         *
         * Penjaga vonis-basi sengaja ada di DUA tempat — di repositori (bisa diuji di JVM)
         * dan di WHERE query Room (menutup tulisan yang menyelinap di antara baca & tulis).
         * Kalau tes cuma memeriksa isi baris, mencabut salah satunya tetap hijau karena
         * yang lain menutupi. Daftar ini membuat penjaga repositori terlihat sendiri.
         */
        val validationCalls = mutableListOf<String>()
        private val stream = MutableStateFlow<List<OpnameUnitEntity>>(emptyList())

        private fun publish() {
            stream.value = rows.toList()
        }

        override fun observe(sessionId: String): Flow<List<OpnameUnitEntity>> = stream

        override suspend fun pending(sessionId: String): List<OpnameUnitEntity> =
            rows.filter { it.sessionId == sessionId && it.syncedAtMillis == null }

        // Cermin query DAO asli: baris REJECTED tak menghalangi serial dikirim ulang.
        override suspend fun countSerial(sessionId: String, serialNumber: String): Int =
            rows.count {
                it.sessionId == sessionId && it.serialNumber.equals(serialNumber, true) &&
                    it.validationStatus != "rejected"
            }

        // Cermin query DAO asli: cuma baris MANUAL, dan cuma yang lebih tua dari saat
        // GET dimulai — dua penjaga terhadap vonis basi yang mengecap ulang baris baru.
        override suspend fun updateValidation(
            sessionId: String,
            serialNumber: String,
            status: String?,
            reason: String?,
            sebelumMillis: Long
        ) {
            validationCalls += serialNumber
            rows.forEachIndexed { index, row ->
                if (row.sessionId == sessionId && row.serialNumber.equals(serialNumber, true) &&
                    row.inputMethod == "manual" && row.updatedAtMillis <= sebelumMillis
                ) {
                    rows[index] = row.copy(validationStatus = status, rejectReason = reason)
                }
            }
            publish()
        }

        override suspend fun all(sessionId: String): List<OpnameUnitEntity> =
            rows.filter { it.sessionId == sessionId }

        override suspend fun countAll(sessionId: String): Int = rows.count { it.sessionId == sessionId }

        override suspend fun upsert(entity: OpnameUnitEntity) {
            rows.removeAll { it.sessionId == entity.sessionId && it.serialNumber == entity.serialNumber }
            rows += entity
            publish()
        }

        override suspend fun markSynced(sessionId: String, serialNumber: String, now: Long, temuan: String?) {
            rows.forEachIndexed { index, row ->
                if (row.sessionId == sessionId && row.serialNumber == serialNumber) {
                    rows[index] = row.copy(syncedAtMillis = now, temuan = temuan)
                }
            }
            publish()
        }

        override suspend fun delete(sessionId: String, serialNumber: String) {
            rows.removeAll { it.sessionId == sessionId && it.serialNumber == serialNumber }
            publish()
        }

        override suspend fun clearSession(sessionId: String) {
            rows.removeAll { it.sessionId == sessionId }
            publish()
        }
    }

    /** `null` = offline (lempar IOException, persis mode pesawat). */
    private class FakeApi(var response: CreateOpnameUnitsData?) : StubInventoryApi() {
        var pushCount = 0
        var lastSent: List<String> = emptyList()

        /**
         * Status HTTP yang dijawab `POST .../units`. Diperiksa SEBELUM
         * [response], jadi ia bisa menjawab 403/500 walau responsnya terisi —
         * itulah bedanya "server menolak" dan "server tak terjangkau".
         */
        var errorStatus: Int? = null

        /** Dijalankan SELAGI `POST .../units` "terbang" — meniru scan yang menyelinap. */
        var saatKirim: (suspend () -> Unit)? = null

        /** Isi `GET /opname/{id}/units` — snapshot server, bisa sengaja dibikin basi. */
        var units: List<OpnameUnitDto> = emptyList()

        /** Isi `GET /opname/{id}/stock` — sumber nama barang saat rekonsiliasi. */
        var stock: List<OpnameStockItemDto> = emptyList()
        var stockCount = 0

        /** Dijalankan SELAGI GET unit "terbang" — untuk mensimulasikan scan yang menyelinap. */
        var saatListUnits: (suspend () -> Unit)? = null

        /** Kode barang yang diminta dinyatakan nihil, per panggilan. */
        var nihilDikirim: List<List<String>> = emptyList()

        override suspend fun tandaiOpnameNihil(
            id: String,
            body: TandaiNihilRequest
        ): Response<ApiResponse<OpnameDetailDto>> {
            nihilDikirim = nihilDikirim + listOf(body.kodeBarang)
            return Response.success(ApiResponse("ok", OpnameDetailDto(id = id)))
        }

        override suspend fun createOpnameUnits(
            id: String,
            body: CreateOpnameUnitsRequest
        ): Response<ApiResponse<CreateOpnameUnitsData>> {
            pushCount += 1
            lastSent = body.items.map { it.serialNumber }
            saatKirim?.invoke()
            errorStatus?.let { status ->
                return Response.error(
                    status,
                    """{"code":"forbidden","message":"Akses ditolak","errors":[]}"""
                        .toResponseBody("application/json".toMediaType())
                )
            }
            val data = response ?: throw IOException("tidak ada jaringan")
            return Response.success(ApiResponse("ok", data))
        }

        override suspend fun listOpnameUnits(id: String): Response<ApiResponse<OpnameUnitListData>> {
            saatListUnits?.invoke()
            return Response.success(ApiResponse("ok", OpnameUnitListData(units)))
        }

        override suspend fun opnameStock(id: String): Response<ApiResponse<OpnameStockData>> {
            stockCount += 1
            return Response.success(ApiResponse("ok", OpnameStockData(stock)))
        }
    }

    private fun repo(dao: OpnameUnitDao, api: InventoryApi) = OpnameRepository(api, dao)

    // ---- Tests ------------------------------------------------------------------------

    @Test
    fun `scan tanpa sinyal tetap tersimpan dan masuk antrean`() = runBlocking {
        val dao = FakeUnitDao()
        val api = FakeApi(response = null)

        val hasil = repo(dao, api).scanUnit(sessionId, "BRG-1", "Kulkas", "sn-001")

        assertTrue("harus dilaporkan sebagai antre, bukan gagal", hasil is OpnameRepository.ScanResult.Queued)
        assertEquals(1, dao.rows.size)
        assertEquals("SN-001", dao.rows.first().serialNumber)
        assertNull("belum tersinkron", dao.rows.first().syncedAtMillis)
        assertEquals(1, dao.pending(sessionId).size)
    }

    @Test
    fun `antrean terkirim saat jaringan pulih lalu hilang dari antrean`() = runBlocking {
        val dao = FakeUnitDao()
        val api = FakeApi(response = null)
        val repository = repo(dao, api)

        repository.scanUnit(sessionId, "BRG-1", "Kulkas", "sn-001")
        repository.scanUnit(sessionId, "BRG-1", "Kulkas", "sn-002")
        assertEquals(2, dao.pending(sessionId).size)

        api.response = CreateOpnameUnitsData(
            accepted = listOf(OpnameUnitAccepted("SN-001"), OpnameUnitAccepted("SN-002"))
        )
        repository.pushPending(sessionId)

        assertEquals("dua unit tetap terhitung", 2, dao.rows.size)
        assertEquals("antrean kosong", 0, dao.pending(sessionId).size)
        assertEquals(listOf("SN-001", "SN-002"), api.lastSent)
    }

    @Test
    fun `pengiriman gagal tidak menghapus apa pun dari antrean`() = runBlocking {
        val dao = FakeUnitDao()
        val api = FakeApi(response = null)
        val repository = repo(dao, api)

        repository.scanUnit(sessionId, "BRG-1", "Kulkas", "sn-001")
        val ulang = repository.pushPending(sessionId)

        assertTrue(ulang is AuthResult.Failure)
        assertEquals(1, dao.pending(sessionId).size)
    }

    @Test
    fun `duplikat lokal ditolak tanpa menyentuh jaringan`() = runBlocking {
        val dao = FakeUnitDao()
        val api = FakeApi(response = CreateOpnameUnitsData(accepted = listOf(OpnameUnitAccepted("SN-001"))))
        val repository = repo(dao, api)

        repository.scanUnit(sessionId, "BRG-1", "Kulkas", "sn-001")
        val pushSetelahScanPertama = api.pushCount

        // Huruf kecil + spasi: normalisasi harus menyamakannya dengan yang sudah discan.
        val kedua = repository.scanUnit(sessionId, "BRG-2", "Mesin Cuci", " sn-001 ")

        assertTrue(kedua is OpnameRepository.ScanResult.Rejected)
        assertEquals("serial ini sudah discan di sesi ini", (kedua as OpnameRepository.ScanResult.Rejected).reason)
        assertEquals("tak boleh ada request kedua", pushSetelahScanPertama, api.pushCount)
        assertEquals(1, dao.rows.size)
    }

    @Test
    fun `serial kosong ditolak sebelum menyentuh jaringan`() = runBlocking {
        val dao = FakeUnitDao()
        val api = FakeApi(response = CreateOpnameUnitsData())

        val hasil = repo(dao, api).scanUnit(sessionId, "BRG-1", "Kulkas", "   ")

        assertTrue(hasil is OpnameRepository.ScanResult.Rejected)
        assertEquals(0, api.pushCount)
        assertEquals(0, dao.rows.size)
    }

    @Test
    fun `serial asing diterima dan penandanya ikut tersimpan`() = runBlocking {
        val dao = FakeUnitDao()
        val api = FakeApi(
            response = CreateOpnameUnitsData(
                accepted = listOf(OpnameUnitAccepted("SN-ASING", temuan = "tidak_terdaftar"))
            )
        )

        val hasil = repo(dao, api).scanUnit(sessionId, "BRG-1", "Kulkas", "sn-asing")

        // Temuan BUKAN error: unit tetap terhitung, cuma diberi penanda buat manager.
        assertTrue(hasil is OpnameRepository.ScanResult.Accepted)
        assertEquals("tidak_terdaftar", (hasil as OpnameRepository.ScanResult.Accepted).temuan)
        assertEquals(1, dao.rows.size)
        assertEquals("tidak_terdaftar", dao.rows.first().temuan)
        assertEquals("baris tersinkron, bukan tertinggal di antrean", 0, dao.pending(sessionId).size)
    }

    @Test
    fun `tiga jenis temuan punya label yang bisa dibaca petugas`() {
        assertEquals("belum terdaftar di registry", temuanLabel("tidak_terdaftar"))
        assertEquals("terdaftar di cabang lain", temuanLabel("cabang_lain"))
        assertEquals("tercatat sudah terjual", temuanLabel("sudah_terjual"))
        // Penanda baru dari server tampil apa adanya, bukan hilang.
        assertEquals("penanda_baru", temuanLabel("penanda_baru"))
    }

    @Test
    fun `ditolak server karena petugas lain sudah scan maka baris hantu dibuang`() = runBlocking {
        val dao = FakeUnitDao()
        val api = FakeApi(response = null)
        val repository = repo(dao, api)

        repository.scanUnit(sessionId, "BRG-1", "Kulkas", "sn-001")
        api.response = CreateOpnameUnitsData(
            rejected = listOf(OpnameUnitRejected("SN-001", "duplikat_dalam_sesi"))
        )
        repository.pushPending(sessionId)

        // Server pemilik kebenaran: unit itu sudah tercatat atas nama petugas lain, jadi
        // menyimpannya di HP ini cuma bikin hitungan lokal lebih besar dari hitungan server.
        assertEquals(0, dao.rows.size)
    }

    @Test
    fun `ditolak karena sesi belum dibuka maka barisnya BERTAHAN dan dikirim ulang`() = runBlocking {
        // Petugas men-scan sejam sebelum jendela opname terbuka. Pekerjaannya
        // benar, cuma kepagian — membuang barisnya (perilaku lama untuk SETIAP
        // penolakan) menghilangkan hasil scan tanpa error, tanpa tanda di layar,
        // dan baru ketahuan saat hitungan akhir kurang.
        val dao = FakeUnitDao()
        val api = FakeApi(response = null)
        val repository = repo(dao, api)

        repository.scanUnit(sessionId, "BRG-1", "Kulkas", "sn-002")
        api.response = CreateOpnameUnitsData(
            rejected = listOf(
                OpnameUnitRejected(
                    serialNumber = "SN-002",
                    reason = TOLAK_JENDELA_BELUM_MULAI,
                    reasonText = "Sesi opname baru dibuka 10/08 08:00",
                )
            )
        )
        val hasil = repository.pushPending(sessionId)
        assertTrue(hasil is AuthResult.Success)

        assertEquals("baris tak boleh dibuang", 1, dao.rows.size)
        assertNull("harus tetap pending supaya dicoba lagi", dao.rows.first().syncedAtMillis)

        // Jendela terbuka: pengiriman berikutnya diterima, tanpa petugas
        // men-scan ulang apa pun.
        api.response = CreateOpnameUnitsData(
            accepted = listOf(OpnameUnitAccepted(serialNumber = "SN-002"))
        )
        repository.pushPending(sessionId)
        assertEquals(1, dao.rows.size)
        assertNotNull("sudah terkirim", dao.rows.first().syncedAtMillis)
    }

    @Test
    fun `scan yang kepagian dilaporkan Queued, bukan Rejected`() = runBlocking {
        // "Ditolak" menyuruh petugas men-scan ulang unit yang sebenarnya sudah
        // tercatat di antrean.
        val dao = FakeUnitDao()
        val api = FakeApi(
            response = CreateOpnameUnitsData(
                rejected = listOf(
                    OpnameUnitRejected(
                        serialNumber = "SN-003",
                        reason = TOLAK_JENDELA_BELUM_MULAI,
                        reasonText = "Sesi opname baru dibuka 10/08 08:00",
                    )
                )
            )
        )
        val hasil = repo(dao, api).scanUnit(sessionId, "BRG-1", "Kulkas", "sn-003")
        assertTrue("dapat $hasil", hasil is OpnameRepository.ScanResult.Queued)
        // Kalimat rinci server dipakai apa adanya — ia memuat JAM jendelanya,
        // yang tak bisa diturunkan dari kode penolakannya saja.
        assertTrue((hasil as OpnameRepository.ScanResult.Queued).reason.contains("10/08 08:00"))
    }

    // ---- 403 BUKAN gangguan jaringan ---------------------------------------------------

    @Test
    fun `ditolak 403 dilaporkan Rejected dan barisnya dibuang`() = runBlocking {
        // BLOCKER RILIS yang diperbaiki 2026-08-12: sebelum ini SETIAP kegagalan
        // — 403 sekalipun — dilaporkan `Queued` alias "tersimpan offline,
        // menunggu jaringan". Petugas yang izinnya dicabut men-scan seharian
        // sementara barisnya mengendap di Room dan TETAP terhitung di daftar unit
        // + PDF hitung fisik. Angka opname salah, nol error terlihat.
        val dao = FakeUnitDao()
        val api = FakeApi(response = null).apply { errorStatus = 403 }

        val hasil = repo(dao, api).scanUnit(sessionId, "BRG-1", "Kulkas", "sn-001")

        assertTrue("dapat $hasil", hasil is OpnameRepository.ScanResult.Rejected)
        val alasan = (hasil as OpnameRepository.ScanResult.Rejected).reason
        assertTrue("harus menyebut jalan keluarnya: $alasan", alasan.contains("admin stok"))
        assertEquals("baris yang tak akan pernah diterima tak boleh ikut terhitung", 0, dao.rows.size)
    }

    @Test
    fun `gagal 500 tetap Queued dan barisnya bertahan`() = runBlocking {
        // Server sekarat itu SEMENTARA. Membuang antrean di sini persis kesalahan
        // kebalikannya: hasil hitung fisik hilang justru saat backend bermasalah.
        val dao = FakeUnitDao()
        val api = FakeApi(response = null).apply { errorStatus = 500 }

        val hasil = repo(dao, api).scanUnit(sessionId, "BRG-1", "Kulkas", "sn-001")

        assertTrue("dapat $hasil", hasil is OpnameRepository.ScanResult.Queued)
        assertEquals(1, dao.pending(sessionId).size)
    }

    @Test
    fun `404 rute belum ter-deploy tak membuang antrean`() = runBlocking {
        // Rute gateway yang belum terpasang menjawab 404 juga (insiden APK 2.67).
        // Penolakannya PERMANEN — petugas harus tahu unitnya belum sampai — tapi
        // barisnya bertahan supaya tak hilang hanya karena backend tertinggal.
        val dao = FakeUnitDao()
        val api = FakeApi(response = null).apply { errorStatus = 404 }

        val hasil = repo(dao, api).scanUnit(sessionId, "BRG-1", "Kulkas", "sn-001")

        assertTrue("dapat $hasil", hasil is OpnameRepository.ScanResult.Rejected)
        assertEquals("antrean harus utuh", 1, dao.pending(sessionId).size)
    }

    @Test
    fun `sesi berakhir 401 disebut apa adanya dan datanya tak dibuang`() = runBlocking {
        val dao = FakeUnitDao()
        val api = FakeApi(response = null).apply { errorStatus = 401 }

        val hasil = repo(dao, api).scanUnit(sessionId, "BRG-1", "Kulkas", "sn-001")

        // Permintaannya sah, SESINYA yang mati — sah lagi begitu user masuk lagi.
        assertTrue("dapat $hasil", hasil is OpnameRepository.ScanResult.Rejected)
        assertTrue((hasil as OpnameRepository.ScanResult.Rejected).reason.contains("masuk lagi"))
        assertEquals(1, dao.pending(sessionId).size)
    }

    @Test
    fun `403 hanya membuang baris yang sudah dikirim`() = runBlocking {
        // Scan yang menyelinap masuk selagi permintaan terbang belum pernah
        // dicoba, jadi penolakan ini bukan vonis atasnya.
        val dao = FakeUnitDao()
        val api = FakeApi(response = null)
        val repository = repo(dao, api)
        repository.scanUnit(sessionId, "BRG-1", "Kulkas", "sn-001")

        api.errorStatus = 403
        api.saatKirim = {
            dao.upsert(
                OpnameUnitEntity(
                    sessionId = sessionId,
                    serialNumber = "SN-BELAKANGAN",
                    kodeBarang = "BRG-1",
                    namaBarang = "Kulkas",
                    kondisi = KONDISI_LAYAK,
                    keterangan = null,
                    temuan = null,
                    updatedAtMillis = System.currentTimeMillis(),
                    syncedAtMillis = null
                )
            )
        }
        repository.pushPending(sessionId)

        assertEquals(listOf("SN-BELAKANGAN"), dao.rows.map { it.serialNumber })
    }

    @Test
    fun `penolakan izin per baris membuang barisnya dan punya kalimat jelas`() = runBlocking {
        // Migrasi 212 menolak PER BARIS di dalam respons 200 — satu batch boleh
        // memuat campuran scan & ketik-manual sementara petugasnya cuma dipercaya
        // salah satunya.
        val dao = FakeUnitDao()
        val api = FakeApi(
            response = CreateOpnameUnitsData(
                rejected = listOf(OpnameUnitRejected("SN-001", TOLAK_IZIN_VERIFIKASI_SN))
            )
        )

        val hasil = repo(dao, api).scanUnit(sessionId, "BRG-1", "Kulkas", "sn-001")

        assertTrue("dapat $hasil", hasil is OpnameRepository.ScanResult.Rejected)
        assertTrue(
            (hasil as OpnameRepository.ScanResult.Rejected).reason.contains("hubungi admin stok")
        )
        assertEquals(0, dao.rows.size)
    }

    @Test
    fun `antrean sesi lain tidak ikut terkirim`() = runBlocking {
        val dao = FakeUnitDao()
        val api = FakeApi(response = null)
        val repository = repo(dao, api)

        repository.scanUnit(sessionId, "BRG-1", "Kulkas", "sn-001")
        repository.scanUnit("sesi-2", "BRG-9", "TV", "sn-999")

        api.response = CreateOpnameUnitsData(accepted = listOf(OpnameUnitAccepted("SN-001")))
        repository.pushPending(sessionId)

        assertEquals(listOf("SN-001"), api.lastSent)
        assertEquals(1, dao.pending("sesi-2").size)
    }

    // ---- Unit ketik-manual (wajib foto + validasi admin-stok) -------------------------

    @Test
    fun `unit manual diterima masuk pending dan tak pernah mengantre offline`() = runBlocking {
        val dao = FakeUnitDao()
        val api = FakeApi(
            response = CreateOpnameUnitsData(
                accepted = listOf(OpnameUnitAccepted("MAN-1", validationStatus = "pending"))
            )
        )

        val hasil = repo(dao, api).manualUnit(
            sessionId, "BRG-1", "Kulkas", "man-1", KONDISI_LAYAK, "sn.jpg", "barang.jpg"
        )

        assertTrue(hasil is OpnameRepository.ScanResult.Accepted)
        assertEquals("pending", (hasil as OpnameRepository.ScanResult.Accepted).validationStatus)
        val row = dao.rows.single()
        assertEquals("manual", row.inputMethod)
        assertEquals("pending", row.validationStatus)
        assertEquals("langsung synced — pushPending tak boleh mengirim ulang tanpa foto", 0, dao.pending(sessionId).size)
        assertEquals(listOf("MAN-1"), api.lastSent)
    }

    @Test
    fun `unit manual saat offline DITOLAK jelas tanpa baris hantu`() = runBlocking {
        val dao = FakeUnitDao()
        val api = FakeApi(response = null)

        val hasil = repo(dao, api).manualUnit(
            sessionId, "BRG-1", "Kulkas", "man-1", KONDISI_LAYAK, "sn.jpg", "barang.jpg"
        )

        assertTrue("offline harus penolakan jelas, bukan antre", hasil is OpnameRepository.ScanResult.Rejected)
        assertTrue((hasil as OpnameRepository.ScanResult.Rejected).reason.contains("Butuh koneksi"))
        assertEquals(0, dao.rows.size)
    }

    @Test
    fun `serial yang ditolak admin-stok boleh discan ulang`() = runBlocking {
        val dao = FakeUnitDao()
        val api = FakeApi(
            response = CreateOpnameUnitsData(
                accepted = listOf(OpnameUnitAccepted("MAN-1", validationStatus = "pending"))
            )
        )
        val repository = repo(dao, api)

        repository.manualUnit(sessionId, "BRG-1", "Kulkas", "man-1", KONDISI_LAYAK, "a.jpg", "b.jpg")
        // Admin-stok menolak (vonis ditarik refreshValidationStatuses di alur nyata).
        dao.updateValidation(sessionId, "MAN-1", "rejected", "foto buram", Long.MAX_VALUE)

        api.response = CreateOpnameUnitsData(accepted = listOf(OpnameUnitAccepted("MAN-1")))
        val ulang = repository.scanUnit(sessionId, "BRG-1", "Kulkas", "man-1")

        assertTrue("baris rejected tak boleh memblokir scan ulang", ulang is OpnameRepository.ScanResult.Accepted)
        val row = dao.rows.single()
        assertEquals("scan", row.inputMethod)
        assertNull("jejak reject bersih setelah tertimpa", row.validationStatus)
    }

    @Test
    fun `vonis rejected yang basi tak mengecap ulang baris yang sudah discan ulang`() = runBlocking {
        val dao = FakeUnitDao()
        val api = FakeApi(
            response = CreateOpnameUnitsData(
                accepted = listOf(OpnameUnitAccepted("MAN-1", validationStatus = "pending"))
            )
        )
        val repository = repo(dao, api)

        repository.manualUnit(sessionId, "BRG-1", "Kulkas", "man-1", KONDISI_LAYAK, "a.jpg", "b.jpg")
        dao.updateValidation(sessionId, "MAN-1", "rejected", "foto buram", Long.MAX_VALUE)

        // Petugas scan ulang serial yang ditolak — barisnya jadi `scan` tanpa vonis,
        // dan server menerimanya.
        api.response = CreateOpnameUnitsData(accepted = listOf(OpnameUnitAccepted("MAN-1")))
        repository.scanUnit(sessionId, "BRG-1", "Kulkas", "man-1")

        // GET yang sudah terbang duluan membawa snapshot PRA-scan: masih manual+rejected.
        api.units = listOf(
            OpnameUnitDto(
                id = "u1",
                kodeBarang = "BRG-1",
                serialNumber = "MAN-1",
                inputMethod = "manual",
                validationStatus = "rejected",
                rejectReason = "foto buram"
            )
        )
        dao.validationCalls.clear()
        repository.refreshValidationStatuses(sessionId, STATUS_DRAFT)

        assertEquals("vonis basi tak boleh dikirim ke baris itu sama sekali", emptyList<String>(), dao.validationCalls)
        val row = dao.rows.single()
        assertEquals("scan", row.inputMethod)
        assertNull("unit sah tak boleh terlihat ditolak selamanya", row.validationStatus)
        // countSerial mengabaikan baris rejected — kalau tercap ulang, petugas discan
        // lagi dan server menjawab duplikat_dalam_sesi tanpa jalan keluar.
        assertEquals(1, dao.countSerial(sessionId, "man-1"))
    }

    @Test
    fun `unit yang hanya ada di server disisipkan ke buffer lokal`() = runBlocking {
        val dao = FakeUnitDao()
        val api = FakeApi(response = null)
        // Server sudah menerima unitnya (proses app mati sebelum Room ditulis, atau ini
        // HP kedua yang menghitung sesi yang sama).
        api.units = listOf(
            OpnameUnitDto(
                id = "u1",
                kodeBarang = "BRG-1",
                serialNumber = "MAN-1",
                kondisi = KONDISI_LAYAK,
                inputMethod = "manual",
                validationStatus = "pending"
            )
        )

        repo(dao, api).refreshValidationStatuses(sessionId, STATUS_DRAFT)

        val row = dao.rows.single()
        assertEquals("MAN-1", row.serialNumber)
        assertEquals("manual", row.inputMethod)
        assertEquals("pending", row.validationStatus)
        assertEquals("sudah ada di server — jangan masuk antrean kirim ulang", 0, dao.pending(sessionId).size)
        assertEquals("tak lagi terlihat 'belum ada' saat petugas scan ulang", 1, dao.countSerial(sessionId, "man-1"))
    }

    @Test
    fun `server lama tanpa validationStatus tak dicap menunggu validasi selamanya`() = runBlocking {
        val dao = FakeUnitDao()
        // Backend sebelum migrasi 193: inputMethod diabaikan, unit diterima sebagai scan
        // biasa, balasannya tanpa validationStatus.
        val api = FakeApi(response = CreateOpnameUnitsData(accepted = listOf(OpnameUnitAccepted("MAN-1"))))

        val hasil = repo(dao, api).manualUnit(
            sessionId, "BRG-1", "Kulkas", "man-1", KONDISI_LAYAK, "sn.jpg", "barang.jpg"
        )

        assertTrue(hasil is OpnameRepository.ScanResult.Accepted)
        assertNull((hasil as OpnameRepository.ScanResult.Accepted).validationStatus)
        val row = dao.rows.single()
        assertEquals("scan", row.inputMethod)
        assertNull("badge merah yang tak pernah bisa lepas", row.validationStatus)
    }

    /**
     * Penjaga `updatedAtMillis <= mulai` sendirian, tanpa dibantu klausa `inputMethod`:
     * barisnya MASIH manual, cuma ditulis sesudah GET dimulai. Kalau kondisi timestamp
     * dicabut, vonis basi ikut terkirim dan `validationCalls` tak lagi kosong.
     */
    @Test
    fun `vonis basi tak menyentuh baris manual yang ditulis sesudah GET dimulai`() = runBlocking {
        val dao = FakeUnitDao()
        val api = FakeApi(response = null)
        // Snapshot server: masih pending (vonis lama).
        api.units = listOf(
            OpnameUnitDto(
                id = "u1",
                kodeBarang = "BRG-1",
                serialNumber = "MAN-1",
                inputMethod = "manual",
                validationStatus = "rejected",
                rejectReason = "foto buram"
            )
        )
        // Petugas mengetik ulang unit manual itu SELAGI GET terbang — barisnya lebih baru
        // dari snapshot, jadi vonis di snapshot tak boleh menimpanya.
        api.saatListUnits = {
            dao.upsert(
                OpnameUnitEntity(
                    sessionId = sessionId,
                    serialNumber = "MAN-1",
                    kodeBarang = "BRG-1",
                    namaBarang = "Kulkas",
                    kondisi = KONDISI_LAYAK,
                    keterangan = null,
                    temuan = null,
                    inputMethod = "manual",
                    validationStatus = "pending",
                    rejectReason = null,
                    updatedAtMillis = System.currentTimeMillis() + 60_000,
                    syncedAtMillis = System.currentTimeMillis()
                )
            )
        }

        repo(dao, api).refreshValidationStatuses(sessionId, STATUS_DRAFT)

        assertEquals("vonis basi tak boleh dikirim sama sekali", emptyList<String>(), dao.validationCalls)
        val row = dao.rows.single()
        assertEquals("manual", row.inputMethod)
        assertEquals("unit yang baru ditulis tak boleh terlihat ditolak", "pending", row.validationStatus)
        assertNull(row.rejectReason)
    }

    @Test
    fun `nama barang unit hasil rekonsiliasi diambil dari daftar barang sesi`() = runBlocking {
        val dao = FakeUnitDao()
        val api = FakeApi(response = null)
        api.units = listOf(OpnameUnitDto(id = "u1", kodeBarang = "brg-1", serialNumber = "MAN-1"))
        api.stock = listOf(OpnameStockItemDto(kodeBarang = "BRG-1", namaBarang = "Kulkas 2 Pintu"))

        repo(dao, api).refreshValidationStatuses(sessionId, STATUS_DRAFT)

        // Tanpa ini unit hasil rekonsiliasi tercetak "-" di PDF hitung fisik sementara
        // unit hasil scan di HP ini bernama lengkap — dua kualitas data satu dokumen.
        assertEquals("Kulkas 2 Pintu", dao.rows.single().namaBarang)
    }

    @Test
    fun `daftar barang hanya diambil sekali walau banyak unit disisipkan`() = runBlocking {
        val dao = FakeUnitDao()
        val api = FakeApi(response = null)
        api.units = listOf(
            OpnameUnitDto(id = "u1", kodeBarang = "BRG-1", serialNumber = "SN-1"),
            OpnameUnitDto(id = "u2", kodeBarang = "BRG-1", serialNumber = "SN-2"),
            OpnameUnitDto(id = "u3", kodeBarang = "BRG-2", serialNumber = "SN-3")
        )
        api.stock = listOf(OpnameStockItemDto(kodeBarang = "BRG-1", namaBarang = "Kulkas"))

        repo(dao, api).refreshValidationStatuses(sessionId, STATUS_DRAFT)

        assertEquals(1, api.stockCount)
        assertEquals(3, dao.rows.size)
        // Kode yang tak ada di daftar barang tetap null, bukan bikin rekonsiliasi batal.
        assertNull(dao.rows.single { it.serialNumber == "SN-3" }.namaBarang)
    }

    @Test
    fun `sesi batal tak dihidupkan lagi oleh rekonsiliasi`() = runBlocking {
        val dao = FakeUnitDao()
        val api = FakeApi(response = null)
        // Server TIDAK menghapus unit saat sesi dibatalkan, jadi GET-nya tetap berisi.
        api.units = listOf(OpnameUnitDto(id = "u1", kodeBarang = "BRG-1", serialNumber = "SN-1"))

        val repository = repo(dao, api)
        repository.refreshValidationStatuses(sessionId, "cancelled")

        // cancel() sengaja memanggil clearSession; menyisipkan lagi di sini membuat unitnya
        // tinggal permanen (clearSession tak akan pernah jalan lagi untuk sesi itu).
        assertEquals("buffer sesi batal harus tetap kosong", 0, dao.rows.size)
        repository.refreshValidationStatuses(sessionId, "completed")
        assertEquals(0, dao.rows.size)
        repository.refreshValidationStatuses(sessionId, STATUS_DRAFT)
        assertEquals("sesi draft tetap direkonsiliasi", 1, dao.rows.size)
    }

    @Test
    fun `pesan unit manual mengikuti vonis server bukan mengarang`() {
        // Backend lama menerima unitnya sebagai scan biasa — tak ada vonis yang akan datang.
        assertEquals("MAN-1 tersimpan", pesanUnitManual("MAN-1", null))
        assertEquals(
            "MAN-1 tersimpan — menunggu validasi admin stok",
            pesanUnitManual("MAN-1", VALIDASI_PENDING)
        )
        assertEquals("MAN-1 tersimpan — approved", pesanUnitManual("MAN-1", "approved"))
    }

    @Test
    fun `alasan foto wajib punya label terbaca`() {
        assertEquals(
            "input manual wajib menyertakan dua foto bukti",
            alasanTolakLabel("foto_wajib_untuk_manual")
        )
    }
    @Test
    fun `nihil membersihkan kode kosong dan duplikat sebelum dikirim`() = runBlocking {
        // Daftar datang dari UI (bisa "tandai semua sisa"), jadi duplikat &
        // spasi bukan hal aneh. Mengirimnya apa adanya membuat server
        // mengerjakan barang yang sama berkali-kali dalam satu permintaan.
        val api = FakeApi(response = null)
        val hasil = repo(FakeUnitDao(), api).tandaiNihil(sessionId, listOf(" P1 ", "P1", "", "  ", "P2"))

        assertTrue(hasil is AuthResult.Success)
        assertEquals(listOf(listOf("P1", "P2")), api.nihilDikirim)
    }

    @Test
    fun `nihil tanpa satu pun kode ditolak tanpa menyentuh jaringan`() = runBlocking {
        val api = FakeApi(response = null)
        val hasil = repo(FakeUnitDao(), api).tandaiNihil(sessionId, listOf("", "   "))

        assertTrue(hasil is AuthResult.Failure)
        assertTrue("tak boleh ada permintaan terkirim", api.nihilDikirim.isEmpty())
    }

    @Test
    fun `nihil TIDAK diantre offline seperti scan`() = runBlocking {
        // Nihil adalah PERNYATAAN, bukan temuan fisik yang bisa hilang kalau tak
        // segera dikirim: petugas bisa mengulanginya kapan saja. Pernyataan yang
        // "tersimpan" menurut layar tapi belum sampai server jauh lebih
        // menyesatkan — ia menyangkut barang yang dilaporkan HILANG.
        val dao = FakeUnitDao()
        val api = object : StubInventoryApi() {
            override suspend fun tandaiOpnameNihil(id: String, body: TandaiNihilRequest):
                Response<ApiResponse<OpnameDetailDto>> = throw IOException("tidak ada jaringan")
        }
        val hasil = repo(dao, api).tandaiNihil(sessionId, listOf("P1"))

        assertTrue("gagal harus dilaporkan apa adanya", hasil is AuthResult.Failure)
        assertEquals("tak boleh menyisakan baris di buffer lokal", 0, dao.rows.size)
    }

}

/**
 * Hanya endpoint opname unit yang dipakai tes ini; sisanya sengaja meledak supaya tes yang
 * diam-diam menyentuh jaringan lain langsung ketahuan.
 */
/** Bukan `private`: dipakai ulang `OpnameValidasiTest` di paket yang sama. */
internal open class StubInventoryApi : InventoryApi {
    private fun nope(): Nothing = error("endpoint ini tidak dipakai di tes")

    override suspend fun stokCabang(
        page: Int?, limit: Int?, refresh: Boolean?, inStock: Boolean?, search: String?, kodeDealer: String?,
    ): Response<ApiResponse<StokCabangPageDto>> = nope()

    override suspend fun listIndent(status: String?): Response<ApiResponse<IndentListData>> = nope()

    override suspend fun createIndent(body: CreateIndentRequest): Response<ApiResponse<IndentDto>> = nope()

    override suspend fun updateIndentStatus(id: String, body: UpdateIndentRequest):
        Response<ApiResponse<IndentDto>> = nope()

    override suspend fun uploadIndentProof(file: MultipartBody.Part):
        Response<ApiResponse<UploadProofResponseDto>> = nope()

    override suspend fun opnameContext(): Response<ApiResponse<OpnameContextDto>> = nope()

    override suspend fun listOpname(status: String?): Response<ApiResponse<OpnameListData>> = nope()

    override suspend fun createOpname(body: CreateOpnameRequest): Response<ApiResponse<OpnameDetailDto>> = nope()

    override suspend fun opnameDetail(id: String): Response<ApiResponse<OpnameDetailDto>> = nope()

    override suspend fun opnameStock(id: String): Response<ApiResponse<OpnameStockData>> = nope()

    override suspend fun createOpnameUnits(id: String, body: CreateOpnameUnitsRequest):
        Response<ApiResponse<CreateOpnameUnitsData>> = nope()

    override suspend fun tandaiOpnameNihil(id: String, body: TandaiNihilRequest):
        Response<ApiResponse<OpnameDetailDto>> = nope()

    override suspend fun listOpnameUnits(id: String): Response<ApiResponse<OpnameUnitListData>> = nope()

    override suspend fun deleteOpnameUnit(id: String, unitId: String):
        Response<ApiResponse<OpnameDetailDto>> = nope()

    override suspend fun completeOpname(id: String): Response<ApiResponse<OpnameDetailDto>> = nope()

    override suspend fun cancelOpname(id: String): Response<ApiResponse<OpnameDetailDto>> = nope()

    override suspend fun deleteOpname(id: String): Response<ApiResponse<OpnameDeleteData>> = nope()

    override suspend fun manualUnits(status: String?): Response<ApiResponse<ManualUnitListData>> = nope()

    override suspend fun approveManualUnit(id: String, unitId: String):
        Response<ApiResponse<OpnameDetailDto>> = nope()

    override suspend fun rejectManualUnit(id: String, unitId: String, body: RejectUnitBody):
        Response<ApiResponse<OpnameDetailDto>> = nope()

    override suspend fun serialPhoto(filename: String): Response<okhttp3.ResponseBody> = nope()

    override suspend fun mutasiHistori(dealer: String?, arah: String?, from: String?, limit: Int?):
        Response<ApiResponse<MutasiHistoriListDto>> = nope()

    override suspend fun mutasiHistoriDetail(noTransaksi: String, arah: String):
        Response<ApiResponse<MutasiHistoriDetailListDto>> = nope()

    override suspend fun inTransitSelf(q: String):
        Response<ApiResponse<InTransitHintDto>> = nope()
}
