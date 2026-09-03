package com.krisoft.tridjayaelektronik.data

import com.krisoft.tridjayaelektronik.data.model.AktivitasDivisionsData
import com.krisoft.tridjayaelektronik.data.model.AktivitasListData
import com.krisoft.tridjayaelektronik.data.model.AktivitasUploadData
import com.krisoft.tridjayaelektronik.data.model.ApiResponse
import com.krisoft.tridjayaelektronik.data.model.PenempatanSayaData
import com.krisoft.tridjayaelektronik.data.model.ReviewAktivitasBody
import com.krisoft.tridjayaelektronik.data.model.ReviewAktivitasResult
import com.krisoft.tridjayaelektronik.data.model.SubmitAktivitasBody
import com.krisoft.tridjayaelektronik.data.model.SubmitAktivitasResult
import com.krisoft.tridjayaelektronik.data.remote.AktivitasApi
import com.krisoft.tridjayaelektronik.data.remote.AktivitasUploadApi
import com.krisoft.tridjayaelektronik.ui.aktivitas.gagalPermanen
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response
import java.io.IOException

/**
 * `AktivitasRepository.parseError` WAJIB membawa status HTTP mentah ke
 * [AuthResult.Failure.httpStatus].
 *
 * ## Kenapa test ini ada, dan kenapa bentuknya begini
 *
 * [com.krisoft.tridjayaelektronik.ui.aktivitas.AktivitasGagalPermanenTest] sudah
 * mengunci PREDIKATNYA (`gagalPermanen(400) == true`) sejak vc97, dan ia hijau
 * terus. Yang tidak pernah diuji siapa pun adalah KABELNYA: `parseError`
 * memanggil `AuthResult.Failure(code, message)` dengan DUA argumen, sedangkan
 * parameter ketiganya punya default `null`. Kompilasi hijau, lint hijau, dan
 * `gagalPermanen(null)` = false untuk SETIAP kegagalan — jadi ketiga cabang
 * `blokir(...)` di `AktivitasViewModel` (gambar, video, submit) tak pernah
 * sekali pun tereksekusi di HP siapa pun. Perbaikan vc97 mendarat, ada
 * penjaganya, dan tetap MATI selama itu.
 *
 * Karena itu test di sini SENGAJA tidak memanggil `gagalPermanen(400)` dengan
 * angka literal. Ia menyuapkan `Response.error(400, …)` ke repositori sungguhan
 * lalu memberi `httpStatus` hasilnya kepada `gagalPermanen` — persis rantai yang
 * dijalankan ViewModel. Test yang memotong kompas di tengah rantai inilah yang
 * membuat cacat ini lolos berbulan-bulan.
 *
 * Sisi lain rantai ikut dikunci: kegagalan jaringan (delapan `catch` di
 * repositori) HARUS tetap `httpStatus = null` supaya orang boleh mencoba lagi.
 * Menambal cacat di atas dengan "isi saja semuanya" akan menyuruh karyawan
 * menyerah tiap sinyal lapangan putus.
 */
class AktivitasRepositoryHttpStatusTest {

    // ---- Test doubles ---------------------------------------------------------------

    /**
     * Retrofit palsu. Hanya `submit` yang dipakai — sisanya `error(...)` supaya
     * test yang tak sengaja menyentuh jalur lain gagal keras, bukan diam-diam
     * mengembalikan data karangan.
     */
    private class FakeAktivitasApi : AktivitasApi {
        /** `null` = lempar [IOException] (jaringan putus, tak pernah sampai server). */
        var balasanSubmit: (() -> Response<ApiResponse<SubmitAktivitasResult>>)? = null

        override suspend fun submit(
            body: SubmitAktivitasBody,
        ): Response<ApiResponse<SubmitAktivitasResult>> =
            balasanSubmit?.invoke() ?: throw IOException("koneksi terputus")

        override suspend fun divisions(): Response<ApiResponse<AktivitasDivisionsData>> =
            error("divisions() tak dipakai di test ini")

        override suspend fun list(
            tanggal: String?,
            tanggalFrom: String?,
            tanggalTo: String?,
            karyawanId: String?,
            limit: Int,
            status: String?,
            q: String?,
        ): Response<ApiResponse<AktivitasListData>> = error("list() tak dipakai di test ini")

        override suspend fun penempatanSaya(): Response<ApiResponse<PenempatanSayaData>> =
            error("penempatanSaya() tak dipakai di test ini")

        override suspend fun review(
            id: String,
            body: ReviewAktivitasBody,
        ): Response<ApiResponse<ReviewAktivitasResult>> = error("review() tak dipakai di test ini")

        // `AktivitasApi` memuat jalur unggah bukti SENDIRI (`uploadEvidence`,
        // `POST api/raport-harian/upload`) selain `AktivitasUploadApi` yang
        // dipalsukan terpisah di bawah. Ia tak dipakai test ini, tapi wajib
        // ditulis: interface Kotlin menuntut SELURUH anggota, dan lupa satu
        // anggota membuat berkas ini gagal KOMPILASI — bukan gagal assert.
        override suspend fun uploadEvidence(
            file: MultipartBody.Part,
        ): Response<ApiResponse<AktivitasUploadData>> =
            error("uploadEvidence() tak dipakai di test ini")
    }

    private class FakeUploadApi : AktivitasUploadApi {
        /** `null` = lempar [IOException] (jaringan putus, tak pernah sampai server). */
        var balasan: (() -> Response<ApiResponse<AktivitasUploadData>>)? = null

        override suspend fun uploadEvidence(
            file: MultipartBody.Part,
        ): Response<ApiResponse<AktivitasUploadData>> =
            balasan?.invoke() ?: throw IOException("koneksi terputus")
    }

    private val api = FakeAktivitasApi()
    private val uploadApi = FakeUploadApi()
    private val repo = AktivitasRepository(api, uploadApi)

    /**
     * Badan error apa adanya dari `ApiError::Validation` (`rust-shared
     * error.rs`): `code` selalu `validation_error`, `message` selalu generik,
     * dan kalimat yang berguna cuma ada di `errors[0]`.
     */
    private fun badanValidasi(detail: String) =
        """{"code":"validation_error","message":"Input tidak valid","errors":["$detail"]}"""
            .toResponseBody("application/json".toMediaType())

    private fun <T> gagal(kode: Int, detail: String): Response<T> =
        Response.error(kode, badanValidasi(detail))

    /**
     * Badan 5xx gateway: `errors` KOSONG dan kodenya sama untuk 502/503/404
     * (`GatewayError`), jadi ia sekaligus contoh kenapa `code` saja tak cukup
     * memutuskan permanen-atau-tidak — lihat KDoc [AuthResult.Failure].
     */
    private fun <T> gagalGateway(kode: Int, pesan: String): Response<T> = Response.error(
        kode,
        """{"code":"gateway_error","message":"$pesan","errors":[]}"""
            .toResponseBody("application/json".toMediaType()),
    )

    // ---- Jalur HTTP nyata: status WAJIB terbawa --------------------------------------

    @Test
    fun `unggah gambar ditolak 400 sampai ke gagalPermanen sebagai permanen`() = runBlocking {
        // Kalimat aslinya dari gerbang bukti-lintas-hari, 2026-08-21.
        val detail = "Foto ini sudah diunggah pada tanggal 19-08-2026. GANTI dengan foto baru " +
            "yang diambil hari ini sebelum mengirim."
        uploadApi.balasan = { gagal(400, detail) }

        val hasil = repo.uploadEvidence(byteArrayOf(1, 2, 3), "bukti.webp")

        val failure = hasil as AuthResult.Failure
        assertEquals("status HTTP wajib terbawa dari respons", 400, failure.httpStatus)
        // Inilah yang benar-benar diuji: rantai repositori → ViewModel. Kalau
        // `parseError` kembali jadi dua argumen, baris ini merah.
        assertTrue(
            "400 dari server wajib divonis permanen oleh gagalPermanen",
            gagalPermanen(failure.httpStatus),
        )
        assertEquals("errors[0] tetap didahulukan atas message generik", detail, failure.message)
        assertEquals("validation_error", failure.code)
    }

    @Test
    fun `submit butir CHAT ditolak 400 sampai ke gagalPermanen sebagai permanen`() = runBlocking {
        // Kelas 400 BARU yang dibawa paket trainee (`pesan_chat_kurang`,
        // `aktivitas_harian/service.rs`) — populasi yang paling tak berdaya
        // menafsirkan kegagalan diam.
        val detail = "Jumlah chat yang dikirim 40, sedangkan butir \\\"CHAT 200 WA\\\" menuntut " +
            "minimal 200 chat hari ini."
        api.balasanSubmit = { gagal(400, detail) }

        val hasil = repo.submitItem(
            aktivitasIndex = 0,
            aktivitasText = "CHAT 200 WA",
            mode = "video",
            evidenceUrl = "/uploads/raport/bukti.mp4",
            jumlah = 40,
        )

        val failure = hasil as AuthResult.Failure
        assertEquals(400, failure.httpStatus)
        assertTrue(gagalPermanen(failure.httpStatus))
    }

    @Test
    fun `422 dari server ikut terbawa dan divonis permanen`() = runBlocking {
        api.balasanSubmit = { gagal(422, "status tidak dikenal") }

        val failure = repo.submitItem(
            aktivitasIndex = 1,
            aktivitasText = "KUNJUNGI 5 TOKO",
            mode = "none",
        ) as AuthResult.Failure

        assertEquals(422, failure.httpStatus)
        assertTrue(gagalPermanen(failure.httpStatus))
    }

    @Test
    fun `503 terbawa apa adanya dan TIDAK divonis permanen`() = runBlocking {
        // Penjaga arah: perbaikannya adalah "bawa angkanya", BUKAN "anggap
        // semua kegagalan HTTP permanen". Service yang sedang dimatikan wajib
        // tetap boleh dicoba lagi.
        uploadApi.balasan = { gagalGateway(503, "Layanan sedang tidak tersedia") }

        val failure = repo.uploadEvidence(byteArrayOf(9), "bukti.webp") as AuthResult.Failure

        assertEquals(503, failure.httpStatus)
        assertFalse("503 wajib tetap boleh dicoba lagi", gagalPermanen(failure.httpStatus))
        assertEquals(
            "errors kosong = jatuh ke message, bukan ke teks fallback",
            "Layanan sedang tidak tersedia",
            failure.message,
        )
    }

    // ---- Jalur `catch`: TIDAK boleh ikut diisi ---------------------------------------

    @Test
    fun `kegagalan jaringan saat unggah tetap tanpa status HTTP`() = runBlocking {
        uploadApi.balasan = null // → IOException

        val failure = repo.uploadEvidence(byteArrayOf(7), "bukti.webp") as AuthResult.Failure

        assertNull("permintaan yang tak pernah sampai server tak punya status", failure.httpStatus)
        assertFalse("tanpa status = boleh dicoba lagi", gagalPermanen(failure.httpStatus))
        assertEquals("network_error", failure.code)
    }

    @Test
    fun `kegagalan jaringan saat submit tetap tanpa status HTTP`() = runBlocking {
        api.balasanSubmit = null // → IOException

        val failure = repo.submitItem(
            aktivitasIndex = 2,
            aktivitasText = "CHAT 200 WA",
            mode = "none",
        ) as AuthResult.Failure

        assertNull(failure.httpStatus)
        assertFalse(gagalPermanen(failure.httpStatus))
    }
}
