package com.krisoft.tridjayaelektronik.data

import android.content.ContentResolver
import android.net.Uri
import com.krisoft.tridjayaelektronik.data.model.ApiErrorResponse
import com.krisoft.tridjayaelektronik.data.model.KirimVideoChatDto
import com.krisoft.tridjayaelektronik.data.model.StatusChatDeteksiDto
import com.krisoft.tridjayaelektronik.data.remote.ChatDeteksiApi
import com.krisoft.tridjayaelektronik.data.remote.ChatDeteksiUploadApi
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Deteksi otomatis jumlah chat via LLM. TANPA cache lokal (pola sama
 * [com.krisoft.tridjayaelektronik.data.AktivitasChatRepository] lama): status
 * SELALU dibaca ulang dari server, tak pernah disimpulkan dari state klien.
 *
 * Beda dari fitur lama: SATU panggilan (`kirimVideo`) langsung menyerahkan
 * video ke server, yang menyimpan + memanggil LLM + memutuskan lolos/tidak
 * di latar belakangnya sendiri. Tidak ada langkah "kirim jumlah chat" terpisah
 * (dulu ada karena karyawan yang mengetik angkanya, sekarang LLM yang menghitung)
 * dan tidak ada langkah "review" (tidak ada endpoint-nya di backend baru ini).
 */
@Singleton
class ChatDeteksiRepository @Inject constructor(
    private val api: ChatDeteksiApi,
    private val uploadApi: ChatDeteksiUploadApi,
) {
    private val errorJson = Json { ignoreUnknownKeys = true }

    suspend fun status(): AuthResult<StatusChatDeteksiDto> = try {
        val response = api.status()
        val data = response.body()?.data
        if (response.isSuccessful && data != null) AuthResult.Success(data)
        else parseError(response, "Gagal memuat status deteksi chat")
    } catch (e: Exception) {
        AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
    }

    /**
     * [ukuranBytes] hanya untuk header `Content-Length`, sama seperti
     * [AktivitasChatRepository.uploadVideo] lama — `0` = biarkan OkHttp
     * mengirim chunked (kolom `SIZE` ContentProvider tak selalu terisi).
     */
    suspend fun kirimVideo(
        resolver: ContentResolver,
        uri: Uri,
        namaFile: String,
        mimeType: String = "video/mp4",
        ukuranBytes: Long = 0,
    ): AuthResult<KirimVideoChatDto> = try {
        val body = UriRequestBody(resolver, uri, mimeType.toMediaTypeOrNull(), ukuranBytes)
        // Nama part WAJIB "file" — itu yang dibaca server.
        val part = MultipartBody.Part.createFormData("file", namaFile, body)
        val response = uploadApi.kirimVideo(part)
        val data = response.body()?.data
        if (response.isSuccessful && data != null) AuthResult.Success(data)
        else parseError(response, "Gagal mengunggah video")
    } catch (e: Exception) {
        AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
    }

    private fun <T> parseError(response: Response<*>, fallback: String): AuthResult<T> {
        val raw = response.errorBody()?.string()
        val parsed = raw?.let {
            runCatching { errorJson.decodeFromString(ApiErrorResponse.serializer(), it) }.getOrNull()
        }
        return AuthResult.Failure(
            parsed?.code ?: "http_${response.code()}",
            parsed?.message ?: "$fallback (${response.code()})"
        )
    }
}
