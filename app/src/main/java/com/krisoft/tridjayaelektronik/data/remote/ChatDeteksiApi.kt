package com.krisoft.tridjayaelektronik.data.remote

import com.krisoft.tridjayaelektronik.data.model.ApiResponse
import com.krisoft.tridjayaelektronik.data.model.StatusChatDeteksiDto
import com.krisoft.tridjayaelektronik.data.model.KirimVideoChatDto
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

/** Deteksi otomatis jumlah chat via LLM — kinerja-service `/api/chat-deteksi`. */
interface ChatDeteksiApi {

    /** `data: null` kalau belum pernah submit hari ini — BUKAN error. */
    @GET("api/chat-deteksi/status")
    suspend fun status(): Response<ApiResponse<StatusChatDeteksiDto?>>
}

/**
 * Interface TERPISAH dari [ChatDeteksiApi] — alasan sama dengan
 * [AktivitasUploadApi]/[AktivitasChatUploadApi] lama: client bersama
 * timeout-nya pendek, dan unggah video (maks 20 MB, `chat_deteksi::handlers::
 * MAX_VIDEO_BYTES`) butuh timeout yang jauh lebih panjang di jaringan cabang.
 */
interface ChatDeteksiUploadApi {

    /** Nama part WAJIB `file` — yang dibaca `chat_deteksi::handlers::submit`. */
    @Multipart
    @POST("api/chat-deteksi")
    suspend fun kirimVideo(@Part file: MultipartBody.Part): Response<ApiResponse<KirimVideoChatDto>>
}
