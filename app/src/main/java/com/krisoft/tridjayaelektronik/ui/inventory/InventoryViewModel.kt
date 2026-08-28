package com.krisoft.tridjayaelektronik.ui.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.krisoft.tridjayaelektronik.data.InTransitLookup
import com.krisoft.tridjayaelektronik.data.AuthRepository
import com.krisoft.tridjayaelektronik.data.AuthResult
import com.krisoft.tridjayaelektronik.data.InTransitHint
import com.krisoft.tridjayaelektronik.data.local.BranchStockEntity
import com.krisoft.tridjayaelektronik.data.local.DealerAlias
import com.krisoft.tridjayaelektronik.data.local.ProductAggregate
import com.krisoft.tridjayaelektronik.data.local.ProductSortOrder
import com.krisoft.tridjayaelektronik.data.local.RegionAlias
import com.krisoft.tridjayaelektronik.domain.inventory.ExportProductsUseCase
import com.krisoft.tridjayaelektronik.domain.inventory.GetBranchBreakdownUseCase
import com.krisoft.tridjayaelektronik.domain.inventory.GetInTransitHintUseCase
import com.krisoft.tridjayaelektronik.domain.inventory.GetProductFiltersUseCase
import com.krisoft.tridjayaelektronik.domain.inventory.LengkapiStokNolUseCase
import com.krisoft.tridjayaelektronik.domain.inventory.kunciCariStokNol
import com.krisoft.tridjayaelektronik.domain.inventory.perluCariStokNol
import com.krisoft.tridjayaelektronik.domain.inventory.SyncInventoryUseCase
import com.krisoft.tridjayaelektronik.domain.inventory.WatchProductsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class InventoryFilters(
    val search: String = "",
    val region: String = "",
    /** Kode dealer/toko spesifik (mis. "D-01"); kosong = semua toko. */
    val dealer: String = "",
    val readyOnly: Boolean = false,
    val category: String = "",
    val merk: String = "",
    val sortOrder: Int = ProductSortOrder.NAME_ASC,
    /** Hanya produk deadstock (umur stok tertua >= [com.krisoft.tridjayaelektronik.data.local.DEADSTOCK_MIN_DAYS] hari). */
    val deadstockOnly: Boolean = false
)

data class InventoryUiState(
    val isSyncing: Boolean = true,
    val syncError: String? = null,
    val filters: InventoryFilters = InventoryFilters(),
    val myRegion: String? = null,
    /** Kode dealer toko user login (dari nama cabang profil) — basis chip "Toko Saya". */
    val myDealer: String? = null,
    val expanded: Set<String> = emptySet(),
    val branchDetails: Map<String, List<BranchStockEntity>> = emptyMap(),
    val loadingBranchFor: String? = null,
    val categories: List<String> = emptyList(),
    val merks: List<String> = emptyList(),
    val isExporting: Boolean = false,
    /** Diisi saat search kosong tapi barangnya ketemu lagi mutasi (stok 0 di dua cabang, jeda GS OUT→IN). */
    val inTransitHint: InTransitHint? = null,
    val inTransitHintLoading: Boolean = false,
    /**
     * Chip "Termasuk stok 0" — permintaan EKSPLISIT agar barang berstok nol ikut
     * ditarik dari server untuk kata kunci yang sedang diketik.
     *
     * Berbeda dari [InventoryFilters.readyOnly], yang cuma menyaring apa yang
     * SUDAH ada di Room. Chip ini menambah datanya; tanpanya, mematikan
     * `readyOnly` tak memunculkan apa pun karena barisnya memang tak pernah
     * disinkron (lihat `KatalogStokNol.kt`). Sengaja di luar [InventoryFilters]:
     * isi kelas itu semuanya argumen kueri DAO, dan menaruh saklar jaringan di
     * sana membuat tiap perubahannya memicu kueri ulang tanpa alasan.
     */
    val sertakanStokNol: Boolean = false,
    val stokNolLoading: Boolean = false,
    /**
     * Jumlah baris stok-nol yang ditambahkan pencarian TERAKHIR. `null` = belum
     * pernah mencari untuk kata kunci ini. Dipakai layar untuk membedakan
     * "server bilang memang tak ada" dari "belum ditanyakan" — dua keadaan yang
     * tanpa penanda ini sama-sama tampil sebagai daftar kosong.
     */
    val stokNolDitemukan: Int? = null
)

/** Product identity is `kode` + `kodeCabang` — the same `kode` can be a different product per region. */
private fun productKey(kode: String, kodeCabang: String) = "$kode|$kodeCabang"

@HiltViewModel
class InventoryViewModel @Inject constructor(
    private val watchProductsUseCase: WatchProductsUseCase,
    private val getProductFiltersUseCase: GetProductFiltersUseCase,
    private val syncInventoryUseCase: SyncInventoryUseCase,
    private val exportProductsUseCase: ExportProductsUseCase,
    private val getBranchBreakdownUseCase: GetBranchBreakdownUseCase,
    private val getInTransitHintUseCase: GetInTransitHintUseCase,
    private val lengkapiStokNolUseCase: LengkapiStokNolUseCase,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        InventoryUiState(
            myRegion = RegionAlias.resolveFromBranchName(authRepository.currentCabangName),
            myDealer = DealerAlias.resolveFromBranchName(authRepository.currentCabangName)
        )
    )
    val uiState: StateFlow<InventoryUiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")

    val pagingFlow: Flow<PagingData<ProductAggregate>> =
        watchProductsUseCase(_searchQuery, _uiState.map { it.filters })
            .cachedIn(viewModelScope)

    init {
        syncIfStale()
        loadFilterOptions()
    }

    private fun loadFilterOptions() {
        viewModelScope.launch {
            val options = getProductFiltersUseCase()
            _uiState.update { it.copy(categories = options.categories, merks = options.merks) }
        }
    }

    fun onSearchChange(value: String) {
        _searchQuery.value = value
        // `stokNolDitemukan` ikut direset: angkanya milik kata kunci SEBELUMNYA,
        // dan membiarkannya membuat layar berkata "0 barang stok 0" tentang
        // pencarian yang belum pernah dikirim.
        _uiState.update {
            it.copy(
                filters = it.filters.copy(search = value),
                inTransitHint = null,
                stokNolDitemukan = null,
            )
        }
    }

    /** Dipanggil dari layar saat hasil search benar-benar kosong — cek apakah barangnya
     *  lagi mutasi (stok 0 di dua cabang selama jeda GS OUT→IN, lihat delivery-flow-audit.md #5).
     *  Toko yang dicek: filter toko aktif, atau toko sendiri kalau lagi lihat "semua toko". */
    /**
     * Kunci (cabang + kata kunci) yang SUDAH pernah dijawab server. Selama
     * kuncinya sama, tak ada permintaan kedua.
     *
     * KENAPA PERLU: pemicunya `LaunchedEffect(state.filters.search, …)` yang
     * duduk DI DALAM cabang empty-state `InventoryScreen`, jadi tiap perubahan
     * kata kunci = key baru = relaunch. `inTransitHintLoading` cuma mencegah
     * dua permintaan TUMPANG TINDIH, bukan pengulangan — jadi lajunya satu
     * permintaan per round-trip selama orang terus mengetik di keadaan
     * hasil-kosong: ±20 permintaan dalam ±20 detik mengetik.
     *
     * Gateway membatasi rute ini **20/menit per user** (`IN_TRANSIT_LIMIT`),
     * jadi tanpa memo ini pemakai normal menabrak plafonnya sendiri lalu
     * petunjuknya hilang — kelas kegagalan yang PERSIS sama dengan 403 yang
     * baru saja ditutup, cuma berganti sebab.
     *
     * Debounce SAJA tidak cukup: cabang empty-state keluar-masuk komposisi,
     * jadi efeknya relaunch walau kata kuncinya tak berubah.
     */
    private var kunciInTransitTerperiksa: String? = null

    fun checkInTransitHint() {
        val query = _uiState.value.filters.search.trim()
        val dealer = _uiState.value.filters.dealer.ifEmpty { _uiState.value.myDealer.orEmpty() }
        if (query.isEmpty() || _uiState.value.inTransitHintLoading) return
        val kunci = "$dealer|$query"
        if (kunci == kunciInTransitTerperiksa) return
        _uiState.update { it.copy(inTransitHintLoading = true) }
        viewModelScope.launch {
            when (val hasil = getInTransitHintUseCase(dealer, query)) {
                // Dua-duanya jawaban server, jadi dua-duanya di-memo.
                is InTransitLookup.Ada -> {
                    kunciInTransitTerperiksa = kunci
                    _uiState.update { it.copy(inTransitHintLoading = false, inTransitHint = hasil.hint) }
                }
                InTransitLookup.TakAda -> {
                    kunciInTransitTerperiksa = kunci
                    _uiState.update { it.copy(inTransitHintLoading = false, inTransitHint = null) }
                }
                // GAGAL TIDAK di-memo: satu 429 atau jaringan putus sesaat tak
                // boleh mengunci kata kunci itu jadi "sudah diperiksa" selamanya.
                is InTransitLookup.Gagal -> {
                    _uiState.update { it.copy(inTransitHintLoading = false, inTransitHint = null) }
                }
            }
        }
    }

    fun toggleReadyOnly() {
        _uiState.update { it.copy(filters = it.filters.copy(readyOnly = !it.filters.readyOnly)) }
    }

    /**
     * Kunci (cabang + kata kunci) yang sudah dijawab server untuk stok nol —
     * alasannya sama persis dengan [kunciInTransitTerperiksa]: pemicunya duduk di
     * dalam `LaunchedEffect` yang relaunch tiap kata kunci berubah, jadi tanpa memo
     * ini satu sesi mengetik mengirim satu permintaan per ketukan.
     */
    private var kunciStokNolTerperiksa: String? = null

    /**
     * Chip "Termasuk stok 0". Menyalakannya MELUPAKAN memo, supaya menekannya
     * langsung mencari untuk kata kunci yang sedang tampil — kalau tidak, orang
     * yang baru saja melihat "tidak ada barang" lalu menyalakan chip tak akan
     * mendapat apa pun sampai ia mengubah ketikannya, dan chip-nya terbaca rusak.
     */
    fun toggleSertakanStokNol() {
        val menyala = !_uiState.value.sertakanStokNol
        if (menyala) kunciStokNolTerperiksa = null
        _uiState.update { it.copy(sertakanStokNol = menyala, stokNolDitemukan = null) }
    }

    /**
     * Tarik barang berstok nol untuk kata kunci yang sedang berlaku, lalu tambal
     * cache — Paging yang mengamati Room memunculkannya sendiri, jadi di sini tak
     * ada daftar hasil yang perlu disimpan.
     *
     * Keputusan "perlu atau tidak" hidup di [perluCariStokNol] (fungsi murni,
     * diuji). [hasilTerlihat] datang dari layar karena hanya layar yang tahu
     * berapa baris yang benar-benar ter-render oleh Paging.
     */
    fun cariStokNol(hasilTerlihat: Int) {
        val s = _uiState.value
        val query = s.filters.search
        val dealer = s.filters.dealer.ifEmpty { s.myDealer.orEmpty() }
        val kunci = kunciCariStokNol(query, dealer)
        val perlu = perluCariStokNol(
            search = query,
            chipMenyala = s.sertakanStokNol,
            hasilTerlihat = hasilTerlihat,
            sudahDiperiksa = kunci == kunciStokNolTerperiksa,
            sedangMemuat = s.stokNolLoading,
        )
        if (!perlu) return
        _uiState.update { it.copy(stokNolLoading = true) }
        viewModelScope.launch {
            when (val hasil = lengkapiStokNolUseCase(query, dealer)) {
                is AuthResult.Success -> {
                    kunciStokNolTerperiksa = kunci
                    _uiState.update { it.copy(stokNolLoading = false, stokNolDitemukan = hasil.data) }
                }
                // GAGAL TIDAK di-memo — satu jaringan putus sesaat tak boleh
                // mengunci kata kunci itu jadi "sudah diperiksa" selamanya. Pola
                // yang sama dengan `checkInTransitHint`.
                is AuthResult.Failure -> {
                    _uiState.update { it.copy(stokNolLoading = false, stokNolDitemukan = null) }
                }
            }
        }
    }

    fun setRegion(region: String) {
        _uiState.update {
            val newRegion = if (it.filters.region == region) "" else region
            it.copy(filters = it.filters.copy(region = newRegion), inTransitHint = null)
        }
    }

    fun setMyBranchOnly() {
        val region = _uiState.value.myRegion ?: return
        setRegion(region)
    }

    /** Toggle filter ke satu toko/dealer spesifik; kode sama = matikan. */
    fun setDealer(dealer: String) {
        _uiState.update {
            val newDealer = if (it.filters.dealer == dealer) "" else dealer
            it.copy(filters = it.filters.copy(dealer = newDealer), inTransitHint = null)
        }
    }

    /** Chip "Toko Saya" — filter ke toko user login (dari profil). */
    fun toggleMyStore() {
        val dealer = _uiState.value.myDealer ?: return
        setDealer(dealer)
    }

    fun setSortOrder(sortOrder: Int) {
        _uiState.update { it.copy(filters = it.filters.copy(sortOrder = sortOrder)) }
    }

    fun toggleDeadstockOnly() {
        _uiState.update { it.copy(filters = it.filters.copy(deadstockOnly = !it.filters.deadstockOnly)) }
    }

    /** Applies both fields from the filter panel at once; blank clears that filter. */
    fun applyCategoryMerk(category: String, merk: String) {
        _uiState.update {
            it.copy(filters = it.filters.copy(category = category.trim(), merk = merk.trim()))
        }
    }

    /** Commit dari bottom sheet Filter & Urutkan — kategori, merk, toko, dan sort sekali jalan.
     *  [dealerKode] kode dealer hasil pilihan dropdown (mis. "D-01"); kosong = semua toko.
     *  Kode asing tetap disaring jadi kosong: dropdown tak bisa menghasilkannya, tapi
     *  membiarkannya lewat berarti daftar barang kosong tanpa satu pun penjelasan. */
    fun applyFilterSheet(category: String, merk: String, sortOrder: Int, dealerKode: String) {
        val dealerCode = if (dealerKode in DealerAlias.allCodes) dealerKode else ""
        _uiState.update {
            it.copy(
                filters = it.filters.copy(
                    category = category.trim(),
                    merk = merk.trim(),
                    sortOrder = sortOrder,
                    dealer = dealerCode
                ),
                inTransitHint = null
            )
        }
    }

    private fun syncIfStale() {
        _uiState.update { it.copy(isSyncing = true, syncError = null) }
        viewModelScope.launch {
            when (val result = syncInventoryUseCase()) {
                is AuthResult.Success -> _uiState.update { it.copy(isSyncing = false) }
                is AuthResult.Failure -> _uiState.update {
                    it.copy(isSyncing = false, syncError = result.message)
                }
            }
        }
    }

    fun refresh() {
        _uiState.update { it.copy(isSyncing = true, syncError = null) }
        viewModelScope.launch {
            when (val result = syncInventoryUseCase(forceRefresh = true)) {
                is AuthResult.Success -> {
                    _uiState.update { it.copy(isSyncing = false, branchDetails = emptyMap()) }
                    loadFilterOptions()
                }
                is AuthResult.Failure -> _uiState.update {
                    it.copy(isSyncing = false, syncError = result.message)
                }
            }
        }
    }

    /** Returns every product matching the current search/filters (not just the loaded paging window), for export. */
    suspend fun exportProducts(): List<ProductAggregate> = exportProductsUseCase(_uiState.value.filters)

    fun toggleExpand(kode: String, kodeCabang: String) {
        val key = productKey(kode, kodeCabang)
        val isExpanded = key in _uiState.value.expanded
        _uiState.update {
            it.copy(expanded = if (isExpanded) it.expanded - key else it.expanded + key)
        }
        if (!isExpanded && key !in _uiState.value.branchDetails) {
            loadBranchDetails(kode, kodeCabang)
        }
    }

    private fun loadBranchDetails(kode: String, kodeCabang: String) {
        val key = productKey(kode, kodeCabang)
        _uiState.update { it.copy(loadingBranchFor = key) }
        viewModelScope.launch {
            val branches = getBranchBreakdownUseCase(kode, kodeCabang)
            _uiState.update {
                it.copy(
                    loadingBranchFor = null,
                    branchDetails = it.branchDetails + (key to branches)
                )
            }
        }
    }
}
