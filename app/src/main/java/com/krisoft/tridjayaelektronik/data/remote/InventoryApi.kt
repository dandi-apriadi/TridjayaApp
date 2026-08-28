package com.krisoft.tridjayaelektronik.data.remote

import com.krisoft.tridjayaelektronik.data.model.TandaiNihilRequest
import com.krisoft.tridjayaelektronik.data.model.ApiResponse
import com.krisoft.tridjayaelektronik.data.model.CreateOpnameUnitsData
import com.krisoft.tridjayaelektronik.data.model.InTransitHintDto
import com.krisoft.tridjayaelektronik.data.model.CreateOpnameUnitsRequest
import com.krisoft.tridjayaelektronik.data.model.CreateIndentRequest
import com.krisoft.tridjayaelektronik.data.model.CreateOpnameRequest
import com.krisoft.tridjayaelektronik.data.model.IndentDto
import com.krisoft.tridjayaelektronik.data.model.IndentListData
import com.krisoft.tridjayaelektronik.data.model.ManualUnitListData
import com.krisoft.tridjayaelektronik.data.model.MutasiHistoriDetailListDto
import com.krisoft.tridjayaelektronik.data.model.MutasiHistoriListDto
import com.krisoft.tridjayaelektronik.data.model.RejectUnitBody
import com.krisoft.tridjayaelektronik.data.model.OpnameContextDto
import com.krisoft.tridjayaelektronik.data.model.OpnameDeleteData
import com.krisoft.tridjayaelektronik.data.model.OpnameDetailDto
import com.krisoft.tridjayaelektronik.data.model.OpnameListData
import com.krisoft.tridjayaelektronik.data.model.OpnameStockData
import com.krisoft.tridjayaelektronik.data.model.StokCabangPageDto
import com.krisoft.tridjayaelektronik.data.model.UpdateIndentRequest
import com.krisoft.tridjayaelektronik.data.model.UploadProofResponseDto
import com.krisoft.tridjayaelektronik.data.model.OpnameUnitListData
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

interface InventoryApi {

    /**
     * [inStock] `true` = hanya baris berstok > 0. WAJIB dikirim eksplisit oleh sinkronisasi:
     * default server pernah berarti "seluruh katalog dealer" (66.482 baris, hanya 5,5% berstok
     * — perubahan perilaku SP GS `GetStokCabang` 28 Jul 2026), dan server lama/rollback bisa
     * mengembalikan default itu lagi. Jangan bergantung pada default sisi server.
     */
    @GET("api/inventory/stok-cabang")
    suspend fun stokCabang(
        @Query("page") page: Int? = null,
        @Query("limit") limit: Int? = null,
        @Query("refresh") refresh: Boolean? = null,
        @Query("inStock") inStock: Boolean? = null,
        /**
         * Pencarian sisi-server (nama/kode). Dipakai HANYA jalur "lengkapi stok
         * nol" (`InventoryRepository.lengkapiStokNol`), bukan sinkronisasi massal.
         *
         * `null` = tak dikirim sama sekali (Retrofit membuang query null), jadi
         * pemanggil lama tak berubah perilakunya. Parameternya sendiri sudah lama
         * ada di endpoint ini — autocomplete Input SPK memakainya lewat
         * `DeliveryFlowApi.stokCabang`; yang baru cuma pemakaiannya dari sisi
         * Inventory.
         */
        @Query("search") search: String? = null,
        /** Batasi ke satu toko. `null` = seluruh cabang, seperti sinkronisasi massal. */
        @Query("kodeDealer") kodeDealer: String? = null
    ): Response<ApiResponse<StokCabangPageDto>>

    @GET("api/inventory/indent")
    suspend fun listIndent(
        @Query("status") status: String? = null
    ): Response<ApiResponse<IndentListData>>

    @POST("api/inventory/indent")
    suspend fun createIndent(@Body body: CreateIndentRequest): Response<ApiResponse<IndentDto>>

    @PATCH("api/inventory/indent/{id}")
    suspend fun updateIndentStatus(
        @Path("id") id: String,
        @Body body: UpdateIndentRequest
    ): Response<ApiResponse<IndentDto>>

    @Multipart
    @POST("api/inventory/indent/upload-proof")
    suspend fun uploadIndentProof(@Part file: MultipartBody.Part): Response<ApiResponse<UploadProofResponseDto>>

    // ---- Stock opname (hitung fisik) — inventory-service opname module ----

    @GET("api/inventory/opname/context")
    suspend fun opnameContext(): Response<ApiResponse<OpnameContextDto>>

    @GET("api/inventory/opname")
    suspend fun listOpname(@Query("status") status: String? = null): Response<ApiResponse<OpnameListData>>

    @POST("api/inventory/opname")
    suspend fun createOpname(@Body body: CreateOpnameRequest): Response<ApiResponse<OpnameDetailDto>>

    @GET("api/inventory/opname/{id}")
    suspend fun opnameDetail(@Path("id") id: String): Response<ApiResponse<OpnameDetailDto>>

    @GET("api/inventory/opname/{id}/stock")
    suspend fun opnameStock(@Path("id") id: String): Response<ApiResponse<OpnameStockData>>

    @POST("api/inventory/opname/{id}/units")
    suspend fun createOpnameUnits(
        @Path("id") id: String,
        @Body body: CreateOpnameUnitsRequest
    ): Response<ApiResponse<CreateOpnameUnitsData>>

    /** Tandai barang NIHIL: sudah dicari di gudang, tak ada satu pun. INI yang
     *  membuat sesi bisa ditutup — barang yang fisiknya habis tak bisa di-scan,
     *  jadi tanpa penanda ini ia menahan sesinya selamanya. */
    @POST("api/inventory/opname/{id}/nihil")
    suspend fun tandaiOpnameNihil(
        @Path("id") id: String,
        @Body body: TandaiNihilRequest
    ): Response<ApiResponse<OpnameDetailDto>>

    @GET("api/inventory/opname/{id}/units")
    suspend fun listOpnameUnits(@Path("id") id: String): Response<ApiResponse<OpnameUnitListData>>

    @DELETE("api/inventory/opname/{id}/units/{unitId}")
    suspend fun deleteOpnameUnit(
        @Path("id") id: String,
        @Path("unitId") unitId: String
    ): Response<ApiResponse<OpnameDetailDto>>

    // ---- Antrian validasi unit ketik-manual (admin-stok SAJA) ----
    // Guard: inventory-service opname.rs `has_admin_stok` (SERIAL_INPUT_ROLES).

    @GET("api/inventory/opname/manual-units")
    suspend fun manualUnits(
        @Query("status") status: String? = null
    ): Response<ApiResponse<ManualUnitListData>>

    /** Butuh DUA id: sesi (pemilik unit) + unit. Salah urutan = 404, bukan error jelas. */
    @POST("api/inventory/opname/{id}/units/{unitId}/approve")
    suspend fun approveManualUnit(
        @Path("id") id: String,
        @Path("unitId") unitId: String
    ): Response<ApiResponse<OpnameDetailDto>>

    @POST("api/inventory/opname/{id}/units/{unitId}/reject")
    suspend fun rejectManualUnit(
        @Path("id") id: String,
        @Path("unitId") unitId: String,
        @Body body: RejectUnitBody
    ): Response<ApiResponse<OpnameDetailDto>>

    /**
     * Foto bukti unit manual — ter-AUTENTIKASI, jadi tak bisa dilempar mentah ke
     * Coil (ImageLoader app tak memakai OkHttp ber-interceptor auth). Foto opname
     * manual diunggah lewat `serial-numbers/photo` (SERIAL_UPLOAD_DIR), BUKAN
     * direktori delivery — memakai `delivery/photo` di sini = 404 senyap.
     * Kirim NAMA BERKAS saja: Retrofit meng-encode `/` jadi `%2F` dan backend
     * menolaknya eksplisit.
     */
    @GET("api/inventory/serial-numbers/photo/{filename}")
    suspend fun serialPhoto(@Path("filename") filename: String): Response<okhttp3.ResponseBody>

    @POST("api/inventory/opname/{id}/complete")
    suspend fun completeOpname(@Path("id") id: String): Response<ApiResponse<OpnameDetailDto>>

    @POST("api/inventory/opname/{id}/cancel")
    suspend fun cancelOpname(@Path("id") id: String): Response<ApiResponse<OpnameDetailDto>>

    @DELETE("api/inventory/opname/{id}")
    suspend fun deleteOpname(@Path("id") id: String): Response<ApiResponse<OpnameDeleteData>>

    // ---- Petunjuk "barang dalam perjalanan" saat pencarian stok kosong ----
    //
    // JALUR RESMI sejak 2026-08-16: satu panggilan, cabangnya DIPAKSA server dari
    // profil pemanggil (`resolve_user`), jadi ia login-only tanpa jadi celah IDOR.
    //
    // Menggantikan pasangan `mutasiHistori` + `mutasiHistoriDetail` di bawah, yang
    // sejak gate role 27 Juli 2026 dijawab **403 untuk hampir semua pemakai**:
    // 10.230 permintaan, NOL yang pernah 200, 78 akun, tiga minggu — dan tak
    // seorang pun melihatnya, karena 403 bukan exception di Retrofit dan
    // `body()` null lalu jatuh ke `.orEmpty()`. Fungsi mati tanpa gejala.
    //
    // Bentuk balasannya: `data.hint` = objek {namaBarang, tujuanCabang, tanggal}
    // ATAU null. `null` BUKAN kegagalan — akun tanpa cabang dan pemindaian yang
    // habis waktu sama-sama menjawab 200 + null, supaya layar pencarian stok tak
    // pernah memerahkan sesuatu yang cuma petunjuk opsional.
    @GET("api/inventory/mutasi/in-transit-self")
    suspend fun inTransitSelf(
        @Query("q") q: String
    ): Response<ApiResponse<InTransitHintDto>>

    // ---- Mutasi histori (arsip GS, read-only) ----
    //
    // DIPERTAHANKAN untuk pemanggil arsip yang memang berhak (gate role
    // `mutasi-histori` di gateway). JANGAN dipakai lagi untuk petunjuk in-transit
    // — itu yang membuatnya mati senyap selama tiga minggu.

    @GET("api/inventory/mutasi-histori")
    suspend fun mutasiHistori(
        @Query("dealer") dealer: String? = null,
        @Query("arah") arah: String? = null,
        @Query("from") from: String? = null,
        @Query("limit") limit: Int? = null
    ): Response<ApiResponse<MutasiHistoriListDto>>

    @GET("api/inventory/mutasi-histori/detail")
    suspend fun mutasiHistoriDetail(
        @Query("noTransaksi") noTransaksi: String,
        @Query("arah") arah: String
    ): Response<ApiResponse<MutasiHistoriDetailListDto>>
}
