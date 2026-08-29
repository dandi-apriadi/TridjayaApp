package com.krisoft.tridjayaelektronik.data.remote

import com.krisoft.tridjayaelektronik.data.model.ApiResponse
import com.krisoft.tridjayaelektronik.data.model.ExecutiveKpiDto
import com.krisoft.tridjayaelektronik.data.model.LeaderboardReportDto
import com.krisoft.tridjayaelektronik.data.model.MonthlyTargetDto
import com.krisoft.tridjayaelektronik.data.model.OmsetListDto
import com.krisoft.tridjayaelektronik.data.model.PapanLapanganDto
import com.krisoft.tridjayaelektronik.data.model.SparklineListDto
import com.krisoft.tridjayaelektronik.data.model.TransactionPageDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface SalesApi {

    @GET("api/inventory/executive/kpi")
    suspend fun executiveKpi(): Response<ApiResponse<ExecutiveKpiDto>>

    @GET("api/inventory/executive/monthly-target")
    suspend fun monthlyTarget(): Response<ApiResponse<MonthlyTargetDto>>

    @GET("api/inventory/executive/sparkline")
    suspend fun sparkline(): Response<ApiResponse<SparklineListDto>>

    // Both /leaderboards/sales and /leaderboards/branches proxy to the identical cached
    // owner/sales-report payload (gateway/src/lib.rs) — calling just one avoids a redundant
    // duplicate request for the same data.
    @GET("api/mobile/v1/leaderboards/sales")
    suspend fun salesLeaderboard(): Response<ApiResponse<LeaderboardReportDto>>

    // Token-only klasemen alias (the web /dashboard/klasemen source): raw month-to-date omset
    // rows, aggregated client-side. Fallback when salesLeaderboard() is role-guarded (403).
    @GET("api/finance/leaderboard")
    suspend fun klasemenOmset(@Query("periode") periode: String): Response<ApiResponse<OmsetListDto>>

    // Papan kerja LAPANGAN (driver & PDI) — beda dari klasemen penjualan di
    // atas: peringkatnya sudah dihitung SERVER, jadi respons ini dipakai apa
    // adanya tanpa agregasi di HP. `peran` = "driver" | "pdi"; path-nya tetap
    // eksplisit di gateway (bukan wildcard), jadi peran lain akan 404.
    @GET("api/klasemen/{peran}")
    suspend fun papanLapangan(
        @Path("peran") peran: String,
        @Query("periode") periode: String,
    ): Response<ApiResponse<PapanLapanganDto>>

    @GET("api/owner/sales-transactions")
    suspend fun salesTransactions(
        @Query("role") role: String = "sales",
        @Query("employeeCode") employeeCode: String,
        @Query("startDate") startDate: String,
        @Query("endDate") endDate: String,
        @Query("page") page: Int,
        @Query("limit") limit: Int
    ): Response<ApiResponse<TransactionPageDto>>

    @GET("api/owner/branch-sales-transactions")
    suspend fun branchSalesTransactions(
        @Query("dealerCode") dealerCode: String,
        @Query("startDate") startDate: String,
        @Query("endDate") endDate: String,
        @Query("page") page: Int,
        @Query("limit") limit: Int
    ): Response<ApiResponse<TransactionPageDto>>
}
