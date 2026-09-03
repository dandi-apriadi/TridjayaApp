package com.krisoft.tridjayaelektronik.data.remote

import com.krisoft.tridjayaelektronik.data.model.ApiResponse
import com.krisoft.tridjayaelektronik.data.model.HsAlasanBody
import com.krisoft.tridjayaelektronik.data.model.HsAmbilUnitBody
import com.krisoft.tridjayaelektronik.data.model.HsAssignBody
import com.krisoft.tridjayaelektronik.data.model.HsAssignTarikBody
import com.krisoft.tridjayaelektronik.data.model.HsCariData
import com.krisoft.tridjayaelektronik.data.model.HsCompleteBody
import com.krisoft.tridjayaelektronik.data.model.HsCreateTicketBody
import com.krisoft.tridjayaelektronik.data.model.HsListData
import com.krisoft.tridjayaelektronik.data.model.HsLookupData
import com.krisoft.tridjayaelektronik.data.model.HsStartBody
import com.krisoft.tridjayaelektronik.data.model.HsTicketDetailDto
import com.krisoft.tridjayaelektronik.data.model.HsTicketDto
import com.krisoft.tridjayaelektronik.data.model.HsUploadData
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Home Service (komplain purna-jual) — kinerja-service via gateway
 * `/api/home-service*`. Gateway hanya menuntut login; RBAC-nya di handler,
 * jadi kartu/tombol di app WAJIB mencerminkan gate-nya sendiri (lihat
 * `ActivityRegistry`), bukan mengandalkan gateway menolak.
 */
interface HomeServiceApi {

    /**
     * `status` hanya menerima SATU nilai (tak ada multi-status koma; "baru,ditugaskan"
     * dijawab 400) — papan CS karena itu memuat tanpa filter lalu memilah di klien,
     * persis yang web lakukan.
     *
     * `mine=true` artinya BERGANTUNG pada `jenis`: untuk `tarik_unit` ia menyaring
     * kolom `tarik_driver_id`, selain itu `assigned_teknisi_id`. Layar driver yang
     * lupa mengirim `jenis=tarik_unit` akan selalu kosong TANPA error.
     */
    @GET("api/home-service")
    suspend fun list(
        @Query("status") status: String? = null,
        @Query("jenis") jenis: String? = null,
        @Query("mine") mine: Boolean? = null,
        /**
         * `sayaLapor=true` — tiket yang AKU laporkan, apa pun cabang penanggung
         * jawabnya dan siapa pun yang ditugaskan.
         *
         * SENGAJA terpisah dari [mine], bukan melebarkannya (server menegaskan
         * hal yang sama): `mine` berarti "Tugas Saya", dan untuk pemegang
         * `homeservice.task` ia DIPAKSA nyala di server — mencampur laporan
         * pribadi ke sana akan mengotori antrian kerja teknisi. Dua pertanyaan
         * berbeda, dua daftar berbeda.
         *
         * `pelapor_user_id` di server SELALU id aktor, tak pernah dari query,
         * jadi parameter ini tak bisa dipakai mengintip laporan orang lain.
         */
        @Query("sayaLapor") sayaLapor: Boolean? = null,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 200,
    ): Response<ApiResponse<HsListData>>

    @GET("api/home-service/{id}")
    suspend fun detail(@Path("id") id: String): Response<ApiResponse<HsTicketDetailDto>>

    @POST("api/home-service")
    suspend fun create(@Body body: HsCreateTicketBody): Response<ApiResponse<HsTicketDto>>

    /** Cari transaksi konsumen dari nama dan/atau HP (server coba HP dulu). */
    @GET("api/home-service/cari")
    suspend fun cari(
        @Query("nama") nama: String? = null,
        @Query("hp") hp: String? = null,
    ): Response<ApiResponse<HsCariData>>

    /**
     * Rincian barang satu transaksi. Nomor transaksi GS memuat `/` — Retrofit
     * meng-encode nilai `@Query` otomatis, jadi JANGAN meng-encode manual di
     * pemanggil (hasilnya akan ter-encode dua kali dan tak ketemu).
     */
    @GET("api/home-service/lookup")
    suspend fun lookup(@Query("noTransaksi") noTransaksi: String): Response<ApiResponse<HsLookupData>>

    @POST("api/home-service/{id}/assign")
    suspend fun assign(
        @Path("id") id: String,
        @Body body: HsAssignBody,
    ): Response<ApiResponse<HsTicketDto>>

    @POST("api/home-service/{id}/start")
    suspend fun start(
        @Path("id") id: String,
        @Body body: HsStartBody,
    ): Response<ApiResponse<HsTicketDto>>

    @POST("api/home-service/{id}/complete")
    suspend fun complete(
        @Path("id") id: String,
        @Body body: HsCompleteBody,
    ): Response<ApiResponse<HsTicketDto>>

    @POST("api/home-service/{id}/cancel")
    suspend fun cancel(
        @Path("id") id: String,
        @Body body: HsAlasanBody,
    ): Response<ApiResponse<HsTicketDto>>

    /** CS mengalihkan tiket jadi penarikan unit (bukan kunjungan teknisi). */
    @POST("api/home-service/{id}/tarik")
    suspend fun mintaTarik(
        @Path("id") id: String,
        @Body body: HsAlasanBody,
    ): Response<ApiResponse<HsTicketDto>>

    @POST("api/home-service/{id}/tarik/assign")
    suspend fun assignTarik(
        @Path("id") id: String,
        @Body body: HsAssignTarikBody,
    ): Response<ApiResponse<HsTicketDto>>

    @POST("api/home-service/{id}/tarik/ambil")
    suspend fun ambilUnit(
        @Path("id") id: String,
        @Body body: HsAmbilUnitBody,
    ): Response<ApiResponse<HsTicketDto>>

    /** Membatalkan PENARIKANNYA saja — tiketnya kembali ke antrian triase CS. */
    @POST("api/home-service/{id}/tarik/batal")
    suspend fun batalTarik(
        @Path("id") id: String,
        @Body body: HsAlasanBody,
    ): Response<ApiResponse<HsTicketDto>>

    /** Maks 5 MB, hanya JPEG/PNG/WebP (magic-byte diperiksa server). */
    @Multipart
    @POST("api/home-service/upload-photo")
    suspend fun uploadPhoto(@Part file: MultipartBody.Part): Response<ApiResponse<HsUploadData>>
}
