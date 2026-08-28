package com.krisoft.tridjayaelektronik.data

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.krisoft.tridjayaelektronik.data.local.BranchStockDao
import com.krisoft.tridjayaelektronik.data.local.BranchStockEntity
import com.krisoft.tridjayaelektronik.data.local.ProductAggregate
import com.krisoft.tridjayaelektronik.data.local.SyncMetaDao
import com.krisoft.tridjayaelektronik.data.local.SyncMetaEntity
import com.krisoft.tridjayaelektronik.data.model.ApiErrorResponse
import com.krisoft.tridjayaelektronik.data.model.CreateIndentRequest
import com.krisoft.tridjayaelektronik.data.model.IndentDto
import com.krisoft.tridjayaelektronik.data.model.IndentListData
import com.krisoft.tridjayaelektronik.data.model.UpdateIndentRequest
import com.krisoft.tridjayaelektronik.data.remote.InventoryApi
import com.krisoft.tridjayaelektronik.domain.inventory.LIMIT_CARI_STOK_NOL
import com.krisoft.tridjayaelektronik.domain.inventory.MIN_KATA_KUNCI_STOK_NOL
import com.krisoft.tridjayaelektronik.domain.inventory.barisStokNol
import com.krisoft.tridjayaelektronik.domain.sales.KlasemenStandings
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

private const val SYNC_PAGE_LIMIT = 1000
private val SYNC_INTERVAL_MILLIS = java.util.concurrent.TimeUnit.HOURS.toMillis(5)

/**
 * Batas waras sinkronisasi stok: 20 halaman × [SYNC_PAGE_LIMIT] = 20.000 baris.
 *
 * Dengan `inStock=true` katalog nyata ±3.6k baris (4 halaman), jadi batas ini murni sabuk
 * pengaman: 28 Jul 2026 SP GS `GetStokCabang` mulai mengembalikan katalog penuh per dealer
 * (66.482 baris = 67 halaman) dan sinkronisasi HP lapangan tak pernah selesai — 834 percobaan
 * berhenti di halaman 4-5, hanya 19 yang tuntas. Kalau server mengabaikan/rollback filter
 * `inStock`, lebih baik berhenti dengan 20.000 baris terpakai + catatan log daripada menarik
 * tanpa akhir.
 */
private const val SYNC_MAX_PAGES = 20

private const val SYNC_LOG_TAG = "InventorySync"

/** Keputusan setelah satu halaman stok berhasil ditarik & ditulis. */
internal enum class SyncStep {
    /** Masih ada halaman berikutnya dan batas belum tersentuh. */
    CONTINUE,

    /** Server bilang habis — data yang terkumpul = snapshot utuh, boleh menggantikan cache. */
    DONE,

    /** Berhenti karena batas waras / respons tak jelas — data parsial, JANGAN buang baris lama. */
    TRUNCATED
}

/**
 * Fungsi murni penentu langkah paging (diuji di `InventorySyncPagingTest`). Dipisah dari [sync]
 * supaya batas & syarat berhenti bisa diuji tanpa Retrofit/Room.
 */
internal fun nextSyncStep(page: Int, hasMore: Boolean, maxPages: Int = SYNC_MAX_PAGES): SyncStep =
    when {
        !hasMore -> SyncStep.DONE
        page >= maxPages -> SyncStep.TRUNCATED
        else -> SyncStep.CONTINUE
    }

/**
 * Hasil pencarian petunjuk in-transit — TIGA keadaan, bukan dua.
 *
 * Memetakan kegagalan dan "memang tak ada" ke `null` yang sama adalah kelas bug
 * yang menyembunyikan matinya `mutasi-histori` selama tiga minggu (10.230
 * permintaan, nol 200, 78 akun). Sebabnya berganti-ganti — 403 kemarin, 429
 * hari ini karena rate limit 20/menit di gateway — tapi gejalanya selalu sama:
 * petunjuknya hilang tanpa satu pun tanda. Selama tipe ini punya cabang
 * [Gagal] yang terpisah, pemanggil tak bisa lagi salah menyimpulkan.
 */
sealed interface InTransitLookup {
    /** Ketemu. */
    data class Ada(val hint: InTransitHint) : InTransitLookup
    /** Server menjawab, dan jawabannya "tak ada". Ini SAH — layak di-memo. */
    data object TakAda : InTransitLookup
    /** Permintaannya sendiri gagal (jaringan, 4xx, 5xx). JANGAN di-memo:
     *  memo-nya akan mengunci kegagalan sementara jadi permanen. */
    data class Gagal(val kode: Int?) : InTransitLookup
}

/** Hasil [InventoryRepository.findInTransitHint] — barang ketemu di mutasi OUT belum ada padanan IN. */
data class InTransitHint(
    val namaBarang: String,
    val tujuanCabang: String,
    val tanggal: String
)

/** Sejauh mana ke belakang riwayat mutasi OUT ditelusuri [InventoryRepository.findInTransitHint]. */
internal const val IN_TRANSIT_LOOKBACK_DAYS = 30

/**
 * Batas bawah jendela riwayat mutasi (`yyyy-MM-dd`) = [todayIso] mundur
 * [IN_TRANSIT_LOOKBACK_DAYS] hari. Fungsi murni supaya ambangnya bisa diuji tanpa
 * Retrofit/Room (pola sama [nextSyncStep]).
 *
 * SENGAJA lewat [KlasemenStandings.shiftDays] (`Calendar` + `SimpleDateFormat`), BUKAN
 * `java.time.LocalDate.now().minusDays(30)` yang dipakai versi sebelum 2026-07-29: modul ini
 * minSdk 24 TANPA `coreLibraryDesugaring`, sedangkan `java.time` baru ada di API 26. Di
 * Android 7.0/7.1 baris itu melempar `NoClassDefFoundError` — turunan `Error`, jadi
 * `catch (e: Exception)` di [InventoryRepository.findInTransitHint] TIDAK menangkapnya dan
 * app-nya benar-benar tutup (beda dari kasus `isGantung` 371d0f5 yang tertelan `runCatching`).
 * Alih-alih menulis parser/format tanggal keempat, ini memakai ulang helper yang sudah ada.
 */
internal fun inTransitFromDate(todayIso: String = KlasemenStandings.todayIso()): String =
    KlasemenStandings.shiftDays(todayIso, -IN_TRANSIT_LOOKBACK_DAYS)

@Singleton
class InventoryRepository @Inject constructor(
    private val api: InventoryApi,
    private val branchStockDao: BranchStockDao,
    private val syncMetaDao: SyncMetaDao
) {

    fun pagedProducts(
        search: String,
        region: String,
        dealer: String,
        readyOnly: Boolean,
        category: String,
        merk: String,
        sortOrder: Int,
        deadstockOnly: Boolean
    ): Flow<PagingData<ProductAggregate>> {
        return Pager(
            config = PagingConfig(
                pageSize = 30,
                enablePlaceholders = false,
                initialLoadSize = 30,
                prefetchDistance = 10
            )
        ) {
            branchStockDao.pagingSource(
                search.trim(), region, dealer, readyOnly, category, merk, sortOrder, deadstockOnly
            )
        }.flow
    }

    suspend fun exportProducts(
        search: String,
        region: String,
        dealer: String,
        readyOnly: Boolean,
        category: String,
        merk: String,
        sortOrder: Int,
        deadstockOnly: Boolean
    ): List<ProductAggregate> =
        branchStockDao.filteredProducts(
            search.trim(), region, dealer, readyOnly, category, merk, sortOrder, deadstockOnly
        )

    /** Simple product search for the global search screen — name/code match, default sort, no filters. */
    suspend fun searchProducts(query: String): List<ProductAggregate> =
        branchStockDao.filteredProducts(
            query.trim(),
            "",
            "",
            false,
            "",
            "",
            com.krisoft.tridjayaelektronik.data.local.ProductSortOrder.NAME_ASC,
            false
        )

    suspend fun categories(): List<String> = branchStockDao.distinctCategories()

    suspend fun merks(): List<String> = branchStockDao.distinctMerks()

    suspend fun branchBreakdown(kode: String, kodeCabang: String): List<BranchStockEntity> =
        branchStockDao.branchesForProduct(kode, kodeCabang)

    suspend fun productDetail(kode: String, kodeCabang: String): ProductAggregate? =
        branchStockDao.productAggregate(kode, kodeCabang)

    /**
     * Barang yang sedang mutasi antar cabang bikin stok 0 di DUA sisi selama jeda GS
     * OUT→IN, jadi ia tak muncul di pencarian biasa. Dipanggil HANYA saat hasil
     * pencarian kosong, bukan tiap ketukan.
     *
     * **Sejak 2026-08-16 memakai `GET /inventory/mutasi/in-transit-self`** — satu
     * panggilan, server yang memindai. Sebelumnya fungsi ini memanggil
     * `mutasi-histori` + `mutasi-histori/detail` per transaksi, dan sejak gate role
     * 27 Juli 2026 keduanya dijawab **403 untuk hampir semua pemakai**: 10.230
     * permintaan, NOL yang pernah 200, 78 akun, tiga minggu berjalan. Tak seorang
     * pun melihatnya — 403 bukan exception di Retrofit, `body()` null, lalu
     * `.orEmpty()` mengubah kegagalan jadi "tidak ada hasil". Gate itu dulu
     * dibenarkan dengan premis "tak ada klien yang kehilangan menu": benar untuk
     * MENU, salah untuk FUNGSI — pemanggilnya panggilan LATAR, bukan tile.
     *
     * [dealerCode] dan [limit] SENGAJA dipertahankan di tanda tangan walau tak lagi
     * dikirim: cabang kini DIPAKSA server dari profil pemanggil (anti-IDOR, itulah
     * yang membuat endpoint barunya boleh login-only), dan jendela pindainya milik
     * server. Membuangnya dari tanda tangan cuma memaksa menyunting pemanggil tanpa
     * mengubah perilaku.
     *
     * Fail-soft dipertahankan: `hint` null adalah jawaban SAH (akun tanpa cabang,
     * pemindaian habis waktu), bukan kegagalan — ini pelengkap di empty-state,
     * bukan jalur kritis.
     */
    suspend fun findInTransitHint(
        dealerCode: String,
        query: String,
        limit: Int = 15
    ): InTransitLookup {
        val needle = query.trim()
        if (needle.isEmpty()) return InTransitLookup.TakAda
        return try {
            val response = api.inTransitSelf(q = needle)
            // `isSuccessful` DULU, sebelum menyentuh `body()`. Tanpa ini, 429
            // dari rate limit gateway (20/menit/user) dan 403 dari gate role
            // sama-sama menghasilkan `body() == null` lalu terbaca "tak ada
            // barang dalam perjalanan" — jawaban yang salah, tanpa gejala.
            if (!response.isSuccessful) return InTransitLookup.Gagal(response.code())
            val hint = response.body()?.data?.hint ?: return InTransitLookup.TakAda
            InTransitLookup.Ada(
                InTransitHint(
                    namaBarang = hint.namaBarang,
                    tujuanCabang = hint.tujuanCabang,
                    tanggal = hint.tanggal
                )
            )
        } catch (e: Exception) {
            InTransitLookup.Gagal(null)
        }
    }

    /**
     * Refreshes the local cache from the network only if the last sync is older than 6 hours.
     *
     * **Tabel kosong ikut dianggap basi, walau metanya masih segar.** [sync] memperbarui
     * `syncMeta` juga di dua jalan keluar yang TIDAK menulis satu baris pun (server menjawab
     * 0 baris → cache lama dipertahankan; snapshot dipotong batas halaman), jadi tanpa
     * pemeriksaan ini tabel yang benar-benar kosong bisa terkunci kosong selama 5 jam tanpa
     * satu pun percobaan ulang — dan layar yang cuma membaca cache (`GlobalSearchScreen`)
     * menjawab "tidak ditemukan" sepanjang jendela itu.
     */
    suspend fun syncIfStale(): AuthResult<Unit> {
        val lastSync = syncMetaDao.get(SyncMetaEntity.KEY_BRANCH_STOCK)?.lastSyncMillis ?: 0L
        val isStale = System.currentTimeMillis() - lastSync >= SYNC_INTERVAL_MILLIS
        if (!isStale && branchStockDao.count() > 0) return AuthResult.Success(Unit)
        return sync()
    }

    /**
     * Forces a network refresh regardless of the last sync time (pull-to-refresh).
     *
     * **Tulis per halaman, bukan semua-atau-tidak.** Tiap halaman langsung di-upsert ke Room, jadi
     * sinkronisasi yang putus di halaman ke-4 tetap meninggalkan data yang bisa dipakai (dulu:
     * satu `replaceAll` di akhir → putus di tengah = nol baris tersimpan, layar Inventory kosong
     * terus). Pembersihan baris yang hilang dari server (stok jadi 0 → tak dikirim lagi karena
     * `inStock=true`) baru dilakukan saat snapshot benar-benar utuh, lewat [BranchStockDao.replaceAll]
     * yang `@Transaction` — jadi TIDAK PERNAH ada jendela "DB kosong": pembaca melihat data lama
     * sampai transaksi commit, tak pernah tabel setengah terhapus.
     */
    suspend fun sync(): AuthResult<Unit> {
        return try {
            val rows = mutableListOf<BranchStockEntity>()
            var page = 1
            var step: SyncStep
            while (true) {
                val response = api.stokCabang(page = page, limit = SYNC_PAGE_LIMIT, inStock = true)
                if (!response.isSuccessful) {
                    // Halaman yang sudah masuk Room tetap tinggal; syncMeta sengaja TIDAK diperbarui
                    // supaya percobaan berikutnya menyegarkan lagi (upsert = idempoten).
                    return AuthResult.Failure(
                        "http_${response.code()}",
                        "Gagal mengambil data stok (${response.code()})"
                    )
                }
                val data = response.body()?.data
                if (data == null) {
                    // Page pertama sukses tapi body kosong/null → JANGAN timpa cache dengan list kosong
                    // (bisa mengosongkan inventori selama 5 jam). Page berikutnya null → data parsial.
                    if (page == 1) return AuthResult.Failure("empty_response", "Respons stok kosong dari server")
                    step = SyncStep.TRUNCATED
                    android.util.Log.w(SYNC_LOG_TAG, "Body kosong di halaman $page — snapshot parsial (${rows.size} baris)")
                    break
                }
                val pageRows = data.items.map {
                    BranchStockEntity(
                        kode = it.Kode,
                        kodeDealer = it.kodeDealer,
                        nama = it.Nama,
                        kategori = it.Kategori,
                        merk = it.Merk,
                        harga = it.Harga,
                        stok = it.Stok,
                        kodeCabang = it.kodeCabang,
                        gambar = it.Gambar?.trim()?.takeIf { url -> url.isNotEmpty() },
                        umurHari = it.umurHari,
                        kondisi = it.kondisi
                    )
                }
                branchStockDao.insertAll(pageRows)
                rows += pageRows
                step = nextSyncStep(page, data.hasMore)
                if (step != SyncStep.CONTINUE) break
                page += 1
            }
            if (step == SyncStep.DONE && rows.isNotEmpty()) {
                branchStockDao.replaceAll(rows)
            } else if (rows.isEmpty()) {
                // Snapshot utuh TAPI nol baris (mis. mirror stok server lagi kosong sesaat) —
                // dengan `inStock=true` ini mungkin terjadi tanpa error HTTP. Menukar cache
                // dengan list kosong = inventori HP kosong 5 jam; lebih baik pertahankan yang lama.
                android.util.Log.w(SYNC_LOG_TAG, "Server mengembalikan 0 baris stok — cache lama dipertahankan")
            } else {
                android.util.Log.w(
                    SYNC_LOG_TAG,
                    "Sinkronisasi stok DIPOTONG di halaman $page (batas $SYNC_MAX_PAGES halaman × $SYNC_PAGE_LIMIT baris): " +
                        "${rows.size} baris tersimpan, baris lama TIDAK dibersihkan karena snapshot tak utuh"
                )
            }
            syncMetaDao.upsert(SyncMetaEntity(SyncMetaEntity.KEY_BRANCH_STOCK, System.currentTimeMillis()))
            AuthResult.Success(Unit)
        } catch (e: Exception) {
            AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
        }
    }

    /**
     * Tambal cache dengan barang BERSTOK NOL yang cocok [search] — bukan daftar
     * kedua, melainkan baris yang di-`insertAll` ke tabel yang sama supaya Paging
     * yang mengamati Room memunculkannya sendiri. Alasan lengkapnya (termasuk
     * kenapa BUKAN `inStock=false` di [sync]) ada di `domain/inventory/KatalogStokNol.kt`.
     *
     * Mengembalikan jumlah baris yang ditambahkan; nol = server memang tak punya
     * barang stok-nol yang cocok, dan itu jawaban yang sah — pemanggil memo-nya
     * supaya kata kunci yang sama tak ditanyakan dua kali.
     *
     * **`insertAll`, bukan `replaceAll`.** Ini tambalan parsial atas satu kata
     * kunci, bukan snapshot; `replaceAll` akan mengosongkan seluruh inventori
     * lalu menyisakan segelintir barang stok nol — persis kegagalan yang dijaga
     * [sync] dengan syarat `step == DONE`.
     *
     * **`syncMeta` sengaja TIDAK disentuh.** Menyegarkannya di sini akan menunda
     * sinkronisasi penuh berikutnya hingga 5 jam, sehingga baris tambalan ini —
     * yang seharusnya dibersihkan `replaceAll` — justru berumur lebih panjang
     * daripada yang dimaksudkan.
     */
    suspend fun lengkapiStokNol(search: String, dealer: String): AuthResult<Int> {
        val kunci = search.trim()
        if (kunci.length < MIN_KATA_KUNCI_STOK_NOL) return AuthResult.Success(0)
        return try {
            val response = api.stokCabang(
                limit = LIMIT_CARI_STOK_NOL,
                inStock = false,
                search = kunci,
                kodeDealer = dealer.trim().takeIf { it.isNotEmpty() },
            )
            if (!response.isSuccessful) {
                return AuthResult.Failure(
                    "http_${response.code()}",
                    "Gagal mencari barang stok 0 (${response.code()})"
                )
            }
            val items = response.body()?.data?.items
                ?: return AuthResult.Failure("empty_response", "Respons stok kosong dari server")
            val baris = barisStokNol(items)
            if (baris.isNotEmpty()) branchStockDao.insertAll(baris)
            AuthResult.Success(baris.size)
        } catch (e: Exception) {
            AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
        }
    }

    suspend fun listIndent(status: String? = null): AuthResult<IndentListData> {
        return try {
            val response = api.listIndent(status)
            val data = response.body()?.data
            if (response.isSuccessful && data != null) {
                AuthResult.Success(data)
            } else {
                parseError(response, "Gagal memuat daftar indent")
            }
        } catch (e: Exception) {
            AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
        }
    }

    /** Putusan approver: setujui (`dipesan`) atau tolak (`batal` + alasan). */
    suspend fun updateIndentStatus(
        id: String,
        body: UpdateIndentRequest
    ): AuthResult<IndentDto> {
        return try {
            val response = api.updateIndentStatus(id, body)
            val data = response.body()?.data
            if (response.isSuccessful && data != null) {
                AuthResult.Success(data)
            } else {
                parseError(response, "Gagal memperbarui status indent")
            }
        } catch (e: Exception) {
            AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
        }
    }

    suspend fun createIndent(request: CreateIndentRequest): AuthResult<IndentDto> {
        return try {
            val response = api.createIndent(request)
            val data = response.body()?.data
            if (response.isSuccessful && data != null) {
                AuthResult.Success(data)
            } else {
                parseError(response, "Gagal mengajukan indent")
            }
        } catch (e: Exception) {
            AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
        }
    }

    suspend fun uploadIndentProof(bytes: ByteArray, filename: String, mimeType: String): AuthResult<String> {
        return try {
            val body = bytes.toRequestBody(mimeType.toMediaType())
            val part = MultipartBody.Part.createFormData("file", filename, body)
            val response = api.uploadIndentProof(part)
            val data = response.body()?.data
            if (response.isSuccessful && data != null) {
                AuthResult.Success(data.url)
            } else {
                parseError(response, "Gagal mengunggah bukti")
            }
        } catch (e: Exception) {
            AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
        }
    }

    /**
     * Surfaces the backend's own error text instead of a bare HTTP code — validation errors
     * carry the actionable detail in `errors[0]` ("Ukuran file maksimum 5 MB", etc.) with a
     * generic "Input tidak valid" message, so the detail wins when present.
     */
    private fun <T> parseError(response: Response<*>, fallback: String): AuthResult<T> {
        val raw = response.errorBody()?.string()
        val parsed = raw?.let {
            runCatching { errorJson.decodeFromString(ApiErrorResponse.serializer(), it) }.getOrNull()
        }
        val detail = parsed?.errors?.firstOrNull() ?: parsed?.message
        return AuthResult.Failure(
            parsed?.code ?: "http_${response.code()}",
            detail ?: "$fallback (${response.code()})"
        )
    }

    private companion object {
        val errorJson = Json { ignoreUnknownKeys = true }
    }
}
