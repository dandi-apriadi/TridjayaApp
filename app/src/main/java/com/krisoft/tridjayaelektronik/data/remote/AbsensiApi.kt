package com.krisoft.tridjayaelektronik.data.remote

import com.krisoft.tridjayaelektronik.data.model.AbsensiListDto
import com.krisoft.tridjayaelektronik.data.model.AbsensiPunchRequest
import com.krisoft.tridjayaelektronik.data.model.AbsensiRecordDto
import com.krisoft.tridjayaelektronik.data.model.AbsensiTodayDto
import com.krisoft.tridjayaelektronik.data.model.AbsensiUploadPhotoDto
import com.krisoft.tridjayaelektronik.data.model.ApiResponse
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Query

/** Absensi karyawan (check-in/out + selfie + geofence) — kinerja-service via gateway `/api/absensi`. */
interface AbsensiApi {

    @GET("api/absensi/today")
    suspend fun today(): Response<ApiResponse<AbsensiTodayDto>>

    @GET("api/absensi")
    suspend fun list(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 60,
        @Query("tanggalFrom") tanggalFrom: String? = null,
        @Query("tanggalTo") tanggalTo: String? = null,
        /**
         * WAJIB diisi ID diri sendiri untuk "riwayat saya" (lihat
         * `AbsensiRepository.history`) — JANGAN dibiarkan `null` di jalur itu.
         *
         * Server (`kinerja-service::attendance::service::list`) membaca
         * absennya param ini beda arti tergantung ROLE pemanggil: staf biasa
         * dipaksa ke dirinya sendiri, tapi role peninjau lintas-cabang
         * (admin/owner/manager/hrd — termasuk yang datang dari DIVISI, bukan
         * cuma role utama, lihat `divisi_access_slugs`) membacanya sebagai
         * "tanpa filter = SEMUA karyawan". Itu benar & disengaja untuk papan
         * admin web (`KehadiranTab.tsx`, review lintas cabang) yang memang
         * ingin itu — tapi bug nyata 2026-08-27: karyawan ber-divisi HRD yang
         * membuka "Riwayat Kehadiran" di app melihat data SELURUH PERUSAHAAN
         * bercampur tanpa nama pemilik, rekap bulanannya pun salah hitung
         * (punya sendiri tenggelam di antara ratusan baris orang lain).
         */
        @Query("karyawanId") karyawanId: String? = null
    ): Response<ApiResponse<AbsensiListDto>>

    @POST("api/absensi/check-in")
    suspend fun checkIn(@Body body: AbsensiPunchRequest): Response<ApiResponse<AbsensiRecordDto>>

    @POST("api/absensi/check-out")
    suspend fun checkOut(@Body body: AbsensiPunchRequest): Response<ApiResponse<AbsensiRecordDto>>

    @Multipart
    @POST("api/absensi/upload-photo")
    suspend fun uploadPhoto(@Part file: MultipartBody.Part): Response<ApiResponse<AbsensiUploadPhotoDto>>
}
