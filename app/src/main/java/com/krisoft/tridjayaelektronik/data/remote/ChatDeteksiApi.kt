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

    /**
     * `data` TIDAK PERNAH `null` — server sengaja mengembalikan objek
     * sentinel `status="belum_pernah"` kalau belum ada submisi hari ini,
     * BUKAN `data: null`. Retrofit + kotlinx.serialization membangun
     * deserializer generik bersarang dari `java.lang.reflect.Type`, yang
     * MEMBUANG info nullability Kotlin — `ApiResponse<StatusChatDeteksiDto?>`
     * gagal parse `data: null` SENYAP (terukur di emulator 2026-09-01:
     * "Expected start of the object '{', but had 'n' instead at path:
     * $.data"). Jangan ubah jadi nullable lagi tanpa memperbaiki masalah
     * itu duluan.
     */
    @GET("api/chat-deteksi/status")
    suspend fun status(): Response<ApiResponse<StatusChatDeteksiDto>>
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
