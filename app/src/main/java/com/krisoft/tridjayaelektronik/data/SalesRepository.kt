package com.krisoft.tridjayaelektronik.data

import com.krisoft.tridjayaelektronik.data.local.DashboardCacheDao
import com.krisoft.tridjayaelektronik.data.local.DashboardCacheEntity
import com.krisoft.tridjayaelektronik.data.model.ApiErrorResponse
import com.krisoft.tridjayaelektronik.data.model.HomeDashboardCache
import com.krisoft.tridjayaelektronik.data.model.LeaderboardBranchItemDto
import com.krisoft.tridjayaelektronik.data.model.LeaderboardReportDto
import com.krisoft.tridjayaelektronik.data.model.LeaderboardSalesItemDto
import com.krisoft.tridjayaelektronik.data.model.OmsetRowDto
import com.krisoft.tridjayaelektronik.data.model.PapanLapanganDto
import com.krisoft.tridjayaelektronik.data.model.TransactionPageDto
import com.krisoft.tridjayaelektronik.data.remote.SalesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Umur cache dashboard/klasemen di Room. MENIT — sebelumnya `HOURS.toMillis(5)` sementara
 * komentar di [SalesRepository.homeDashboard] dan [SalesRepository.klasemenRows] sama-sama
 * menulis "5 minutes"; salah-satuan itu bertahan lama justru karena tak menimbulkan galat,
 * cuma angka klasemen yang tak berubah setengah hari (keluhan lapangan 2026-08-07).
 *
 * Lantai kesegaran di server sendiri ±30 menit (mirror ERP 900 dtk → snapshot finance 15 mnt),
 * jadi TTL di bawah itu tak menambah kesegaran nyata — 5 menit dipilih supaya penjualan yang
 * baru tiba di snapshot tampil pada pembukaan layar berikutnya, bukan supaya tiap buka layar
 * memukul jaringan.
 */
private val DASHBOARD_CACHE_TTL_MILLIS = java.util.concurrent.TimeUnit.MINUTES.toMillis(5)

/** Month-to-date range vs the same range one month back, for the *-performance endpoints. */
private data class DateRange(val start: String, val end: String, val compareStart: String, val compareEnd: String)

@Singleton
class SalesRepository @Inject constructor(
    private val api: SalesApi,
    private val dashboardCacheDao: DashboardCacheDao
) {

    private val json = Json { ignoreUnknownKeys = true }
    private val errorJson = Json { ignoreUnknownKeys = true }

    /** Bundle dashboard terakhir yang sudah ter-parse, di-key `cachedAtMillis` — tiga use case
     *  (Home, Sales, Lihat Semua) membaca cache yang sama dalam jendela [DASHBOARD_CACHE_TTL_MILLIS];
     *  tanpa memo ini blob JSON besar di Room di-decode ulang pada setiap perpindahan layar. */
    @Volatile
    private var dashboardMemo: Pair<Long, HomeDashboardCache>? = null

    /** Memo serupa untuk baris klasemen per periode. */
    private val klasemenMemo = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, List<OmsetRowDto>>>()

    /**
     * KPI + monthly target + full branch/sales ranking, bundled and cached in Room for
     * [DASHBOARD_CACHE_TTL_MILLIS] so switching tabs or opening "Lihat Semua" doesn't re-hit
     * the API every time.
     */
    suspend fun homeDashboard(forceRefresh: Boolean = false): AuthResult<HomeDashboardCache> {
        if (!forceRefresh) {
            val cached = dashboardCacheDao.get(DashboardCacheEntity.KEY_HOME_DASHBOARD)
            val isFresh = cached != null && System.currentTimeMillis() - cached.cachedAtMillis < DASHBOARD_CACHE_TTL_MILLIS
            if (isFresh) {
                dashboardMemo?.takeIf { it.first == cached!!.cachedAtMillis }
                    ?.let { return AuthResult.Success(it.second) }
                // Decode di Default — jangan blokir main thread dgn blob dashboard penuh.
                val parsed = withContext(Dispatchers.Default) {
                    runCatching {
                        json.decodeFromString(HomeDashboardCache.serializer(), cached!!.jsonPayload)
                    }.getOrNull()
                }
                if (parsed != null) {
                    dashboardMemo = cached!!.cachedAtMillis to parsed
                    return AuthResult.Success(parsed)
                }
            }
        }

        return try {
            coroutineScope {
                // Fire all independent endpoints concurrently — and treat EACH section as
                // best-effort. The executive KPI/target endpoints are role-guarded (403 for
                // regular sales/karyawan accounts), but the leaderboards aren't: a role that can
                // only see the klasemen must still get a working dashboard, not a full-screen 403.
                val kpiDeferred = async { runCatching { api.executiveKpi() }.getOrNull() }
                val targetDeferred = async { runCatching { api.monthlyTarget() }.getOrNull() }
                val leaderboardDeferred = async { runCatching { api.salesLeaderboard() }.getOrNull() }
                val sparklineDeferred = async { runCatching { api.sparkline() }.getOrNull() }

                val kpiResponse = kpiDeferred.await()
                val targetResponse = targetDeferred.await()
                val leaderboardResponse = leaderboardDeferred.await()
                val sparklineResponse = sparklineDeferred.await()

                val kpi = kpiResponse?.takeIf { it.isSuccessful }?.body()?.data
                val target = targetResponse?.takeIf { it.isSuccessful }?.body()?.data
                var leaderboard = leaderboardResponse?.takeIf { it.isSuccessful }?.body()?.data
                val sparkline = sparklineResponse?.takeIf { it.isSuccessful }?.body()?.data?.items.orEmpty()

                if (leaderboard == null) {
                    // The mobile leaderboard facade is role-guarded (403 for karyawan/sales
                    // accounts). Fall back to /api/finance/leaderboard — the token-only alias the
                    // web's public Klasemen page uses — and build the standings client-side,
                    // exactly like frontend StandingsSection publicMode does.
                    leaderboard = runCatching { api.klasemenOmset(currentPeriode()) }.getOrNull()
                        ?.takeIf { it.isSuccessful }?.body()?.data?.items
                        ?.let { rows -> withContext(Dispatchers.Default) { buildKlasemenReport(rows) } }
                }

                if (kpi == null && target == null && leaderboard == null) {
                    // Nothing at all came back — surface the most meaningful failure.
                    val failedCode = listOfNotNull(leaderboardResponse, kpiResponse, targetResponse)
                        .firstOrNull { !it.isSuccessful }
                        ?.code()
                    return@coroutineScope if (failedCode != null) {
                        AuthResult.Failure("http_$failedCode", "Gagal memuat dashboard ($failedCode)")
                    } else {
                        AuthResult.Failure("network_error", "Tidak bisa terhubung ke server")
                    }
                }

                // Sortir + serialisasi bundle di Default, jangan di main thread.
                val (bundle, payload) = withContext(Dispatchers.Default) {
                    val b = HomeDashboardCache(
                        kpi = kpi,
                        target = target,
                        branches = leaderboard?.omsetPerCabang.orEmpty().sortedByDescending { it.omset },
                        sales = leaderboard?.salesTable.orEmpty().sortedBy { it.rank },
                        sparkline = sparkline
                    )
                    b to json.encodeToString(HomeDashboardCache.serializer(), b)
                }
                val cachedAt = System.currentTimeMillis()
                dashboardCacheDao.upsert(
                    DashboardCacheEntity(
                        key = DashboardCacheEntity.KEY_HOME_DASHBOARD,
                        jsonPayload = payload,
                        cachedAtMillis = cachedAt
                    )
                )
                dashboardMemo = cachedAt to bundle
                AuthResult.Success(bundle)
            }
        } catch (e: Exception) {
            AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
        }
    }

    /**
     * Raw month-to-date omset rows for the interactive Klasemen (web-parity standings with
     * rank movement). Room-cached per periode with the shared 5h TTL; on a network failure a
     * stale cache is served so the klasemen stays readable offline.
     */
    suspend fun klasemenRows(periode: String, forceRefresh: Boolean = false): AuthResult<List<OmsetRowDto>> {
        val cacheKey = "klasemen_omset_$periode"
        val rowsSerializer = kotlinx.serialization.builtins.ListSerializer(OmsetRowDto.serializer())
        if (!forceRefresh) {
            val cached = dashboardCacheDao.get(cacheKey)
            val isFresh = cached != null && System.currentTimeMillis() - cached.cachedAtMillis < DASHBOARD_CACHE_TTL_MILLIS
            if (isFresh) {
                klasemenMemo[cacheKey]?.takeIf { it.first == cached!!.cachedAtMillis }
                    ?.let { return AuthResult.Success(it.second) }
                val parsed = withContext(Dispatchers.Default) {
                    runCatching { json.decodeFromString(rowsSerializer, cached!!.jsonPayload) }.getOrNull()
                }
                if (parsed != null) {
                    klasemenMemo[cacheKey] = cached!!.cachedAtMillis to parsed
                    return AuthResult.Success(parsed)
                }
            }
        }
        return try {
            val response = api.klasemenOmset(periode)
            val items = response.body()?.data?.items
            if (response.isSuccessful && items != null) {
                val payload = withContext(Dispatchers.Default) { json.encodeToString(rowsSerializer, items) }
                val cachedAt = System.currentTimeMillis()
                dashboardCacheDao.upsert(
                    DashboardCacheEntity(
                        key = cacheKey,
                        jsonPayload = payload,
                        cachedAtMillis = cachedAt
                    )
                )
                klasemenMemo[cacheKey] = cachedAt to items
                AuthResult.Success(items)
            } else {
                parseError(response)
            }
        } catch (e: Exception) {
            val stale = dashboardCacheDao.get(cacheKey)?.let {
                withContext(Dispatchers.Default) {
                    runCatching { json.decodeFromString(rowsSerializer, it.jsonPayload) }.getOrNull()
                }
            }
            if (stale != null) AuthResult.Success(stale)
            else AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
        }
    }

    /**
     * Papan kerja lapangan (driver/PDI). Cache Room + TTL yang SAMA dengan
     * klasemen penjualan, berikut fallback ke salinan basi saat jaringan mati —
     * pola itu sudah teruji untuk sinyal cabang yang jelek, dan orang lapangan
     * justru yang paling sering kehilangan sinyal.
     *
     * Peringkatnya TIDAK dihitung ulang di sini: server sudah mengirim `rank`.
     */
    suspend fun papanLapangan(
        peran: String,
        periode: String,
        forceRefresh: Boolean = false,
    ): AuthResult<PapanLapanganDto> {
        val cacheKey = "papan_lapangan_${peran}_$periode"
        val serializer = PapanLapanganDto.serializer()
        if (!forceRefresh) {
            val cached = dashboardCacheDao.get(cacheKey)
            val isFresh = cached != null &&
                System.currentTimeMillis() - cached.cachedAtMillis < DASHBOARD_CACHE_TTL_MILLIS
            if (isFresh) {
                val parsed = withContext(Dispatchers.Default) {
                    runCatching { json.decodeFromString(serializer, cached!!.jsonPayload) }.getOrNull()
                }
                if (parsed != null) return AuthResult.Success(parsed)
            }
        }
        return try {
            // `when` atas slug, bukan `@Path` — lihat alasannya di `SalesApi`.
            val response = when (peran) {
                "pdi" -> api.papanPdi(periode)
                else -> api.papanDriver(periode)
            }
            val papan = response.body()?.data
            if (response.isSuccessful && papan != null) {
                val payload = withContext(Dispatchers.Default) { json.encodeToString(serializer, papan) }
                dashboardCacheDao.upsert(
                    DashboardCacheEntity(
                        key = cacheKey,
                        jsonPayload = payload,
                        cachedAtMillis = System.currentTimeMillis()
                    )
                )
                AuthResult.Success(papan)
            } else {
                parseError(response)
            }
        } catch (e: Exception) {
            val stale = dashboardCacheDao.get(cacheKey)?.let {
                withContext(Dispatchers.Default) {
                    runCatching { json.decodeFromString(serializer, it.jsonPayload) }.getOrNull()
                }
            }
            if (stale != null) AuthResult.Success(stale)
            else AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
        }
    }

    /** Individual sales transactions behind a sales person's ranking number, month-to-date. */
    suspend fun salesTransactions(kodePegawai: String, page: Int, limit: Int = 20): AuthResult<TransactionPageDto> {
        val range = monthToDateRange()
        return try {
            val response = api.salesTransactions(
                employeeCode = kodePegawai,
                startDate = range.start,
                endDate = range.end,
                page = page,
                limit = limit
            )
            val data = response.body()?.data
            if (response.isSuccessful && data != null) {
                AuthResult.Success(data)
            } else {
                parseError(response)
            }
        } catch (e: Exception) {
            AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
        }
    }

    /** Individual sales transactions behind a branch's ranking number, month-to-date. */
    suspend fun branchTransactions(kodeDealer: String, page: Int, limit: Int = 20): AuthResult<TransactionPageDto> {
        val range = monthToDateRange()
        return try {
            val response = api.branchSalesTransactions(
                dealerCode = kodeDealer,
                startDate = range.start,
                endDate = range.end,
                page = page,
                limit = limit
            )
            val data = response.body()?.data
            if (response.isSuccessful && data != null) {
                AuthResult.Success(data)
            } else {
                parseError(response)
            }
        } catch (e: Exception) {
            AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
        }
    }

    private fun <T> parseError(response: Response<*>): AuthResult<T> {
        val raw = response.errorBody()?.string()
        val parsed = raw?.let {
            runCatching { errorJson.decodeFromString(ApiErrorResponse.serializer(), it) }.getOrNull()
        }
        return AuthResult.Failure(
            parsed?.code ?: "http_${response.code()}",
            parsed?.message ?: "Terjadi kesalahan (${response.code()})",
            // `httpStatus` diisi supaya pemanggil bisa membedakan penolakan
            // PERMANEN (403 — orangnya memang tak berhak) dari gangguan
            // sementara. Menebaknya dari `code` tak bisa: gateway memakai satu
            // kode untuk beberapa status sekaligus (lihat doc `AuthResult`).
            response.code()
        )
    }

    /**
     * Non-sales rows leaking into the omset snapshot: empty name, numeric NIK-as-name, or the
     * "owner" system account. Excluded from the SALES standings only — their omset still counts
     * toward branch totals (port of the web's isNonSalesRowName).
     */
    private fun isNonSalesName(nama: String?): Boolean {
        val trimmed = nama?.trim().orEmpty()
        return trimmed.isEmpty() || trimmed.all { it.isDigit() } || trimmed.equals("owner", ignoreCase = true)
    }

    /**
     * Aggregates raw month-to-date omset rows into the same report shape the mobile leaderboard
     * facade returns: branch standings from ALL rows, sales standings from roster-gated sales
     * rows only (isSales != false + name heuristic), both ranked by omset, zero-value entries
     * dropped — mirroring the web Klasemen page's client-side buildStandings.
     */
    private fun buildKlasemenReport(rows: List<OmsetRowDto>): LeaderboardReportDto {
        val branches = rows
            .groupBy { it.cabangNama.trim() }
            .filterKeys { it.isNotEmpty() }
            .map { (nama, list) ->
                LeaderboardBranchItemDto(
                    kodeDealer = list.firstOrNull()?.cabangId.orEmpty(),
                    cabang = nama,
                    totalTransaksi = list.sumOf { it.jumlahTransaksi }.toInt(),
                    totalQty = list.sumOf { it.unit }.toLong(),
                    currentMonthOmset = list.sumOf { it.omset }.toLong(),
                    omset = list.sumOf { it.omset }.toLong()
                )
            }
            .filter { it.omset > 0 }
            .sortedByDescending { it.omset }

        val sales = rows
            .filter { it.isSales != false && !isNonSalesName(it.salesNama) }
            .groupBy { it.salesNama!!.trim() }
            .map { (nama, list) ->
                LeaderboardSalesItemDto(
                    sourceCode = list.firstOrNull()?.salesId.orEmpty(),
                    name = nama,
                    dealerCode = list.firstOrNull()?.cabangId.orEmpty(),
                    cabang = list.firstOrNull()?.cabangNama.orEmpty(),
                    totalTransaksi = list.sumOf { it.jumlahTransaksi }.toInt(),
                    totalQty = list.sumOf { it.unit }.toLong(),
                    revenue = list.sumOf { it.omset }.toLong()
                )
            }
            .filter { it.revenue > 0 }
            .sortedByDescending { it.revenue }
            .mapIndexed { index, item -> item.copy(rank = index + 1) }

        return LeaderboardReportDto(
            activeMonth = currentPeriode(),
            salesTable = sales,
            omsetPerCabang = branches
        )
    }

    private fun currentPeriode(): String =
        SimpleDateFormat("yyyy-MM", Locale.US).format(Calendar.getInstance().time)

    private fun monthToDateRange(): DateRange {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)

        val endCal = Calendar.getInstance()
        val end = fmt.format(endCal.time)

        val startCal = Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1) }
        val start = fmt.format(startCal.time)

        val compareEndCal = Calendar.getInstance().apply { add(Calendar.MONTH, -1) }
        val compareEnd = fmt.format(compareEndCal.time)

        val compareStartCal = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            add(Calendar.MONTH, -1)
        }
        val compareStart = fmt.format(compareStartCal.time)

        return DateRange(start, end, compareStart, compareEnd)
    }
}
