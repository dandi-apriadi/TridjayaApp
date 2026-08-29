package com.krisoft.tridjayaelektronik.data

import com.krisoft.tridjayaelektronik.data.model.ApiErrorResponse
import com.krisoft.tridjayaelektronik.data.model.EventLeadDto
import com.krisoft.tridjayaelektronik.data.model.EventListDto
import com.krisoft.tridjayaelektronik.data.model.SubmitEventLeadRequest
import com.krisoft.tridjayaelektronik.data.remote.EventApi
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Event lapangan + prospek yang dicatat sales di sana. Tanpa cache lokal: event dibuka/ditutup
 * manajemen kapan saja, jadi daftar basi lebih berbahaya daripada daftar kosong — sales bisa
 * mengetik data ke event yang sudah ditutup lalu ditolak 400 di ujung. Pola sama
 * repository lain di paket ini.
 */
@Singleton
class EventRepository @Inject constructor(
    private val api: EventApi,
) {
    private val errorJson = Json { ignoreUnknownKeys = true }

    suspend fun daftar(): AuthResult<EventListDto> = try {
        val response = api.daftar()
        val data = response.body()?.data
        if (response.isSuccessful && data != null) AuthResult.Success(data)
        else parseError(response, "Gagal memuat daftar event")
    } catch (e: Exception) {
        AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
    }

    suspend fun kirimLead(eventId: String, body: SubmitEventLeadRequest): AuthResult<EventLeadDto> = try {
        val response = api.kirimLead(eventId, body)
        val data = response.body()?.data
        if (response.isSuccessful && data != null) AuthResult.Success(data)
        else parseError(response, "Gagal menyimpan prospek event")
    } catch (e: Exception) {
        AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
    }

    /**
     * Unggah foto KTP → URL logis untuk dikirim di [kirimLead]. [bytes] sudah JPEG hasil kompres
     * di ViewModel (bukan berkas kamera mentah), jadi memuatnya penuh ke memori aman —
     * beda dari jalur video bukti chat yang wajib streaming.
     */
    suspend fun unggahKtp(bytes: ByteArray, namaFile: String): AuthResult<String> = try {
        // Nama part WAJIB "file" — itu yang dibaca server.
        val part = MultipartBody.Part.createFormData(
            "file", namaFile, bytes.toRequestBody("image/webp".toMediaType()),
        )
        val response = api.unggahKtp(part)
        val data = response.body()?.data
        if (response.isSuccessful && data != null && data.url.isNotBlank()) AuthResult.Success(data.url)
        else parseError(response, "Gagal mengunggah foto KTP")
    } catch (e: Exception) {
        AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
    }

    /**
     * Ambil bytes foto KTP ter-autentikasi. [url] = URL logis `/uploads/event/{berkas}`;
     * hanya nama berkasnya yang dipakai (pola [DeliveryFlowRepository.fetchPhoto]).
     * Fail-soft `null` — foto tak tampil, layar tetap jalan.
     */
    suspend fun fetchFoto(url: String): ByteArray? = try {
        val namaBerkas = url.trim().substringAfterLast('/')
        if (namaBerkas.isBlank()) null
        else api.foto(namaBerkas).let { if (it.isSuccessful) it.body()?.bytes() else null }
    } catch (e: Exception) {
        null
    }

    private fun <T> parseError(response: Response<*>, fallback: String): AuthResult<T> {
        val raw = response.errorBody()?.string()
        val parsed = raw?.let {
            runCatching { errorJson.decodeFromString(ApiErrorResponse.serializer(), it) }.getOrNull()
        }
        return AuthResult.Failure(
            parsed?.code ?: "http_${response.code()}",
            pesanGalat(parsed, fallback, response.code()),
        )
    }
}

/**
 * Pesan yang benar-benar dibaca sales. `ApiError::Validation` di Rust SELALU ber-`message`
 * generik ("Input tidak valid") dan menaruh kalimat sesungguhnya di `errors[0]` — jadi
 * membaca `message` lebih dulu berarti membuang justru dua pesan yang harus terbaca di
 * lapangan: "Event tidak ditemukan atau sudah tidak aktif" dan "Nomor WhatsApp tidak valid".
 * Sisi web sudah begini.
 */
internal fun pesanGalat(parsed: ApiErrorResponse?, fallback: String, kode: Int): String =
    parsed?.errors?.firstOrNull { it.isNotBlank() }
        ?: parsed?.message?.takeIf { it.isNotBlank() }
        ?: "$fallback ($kode)"
