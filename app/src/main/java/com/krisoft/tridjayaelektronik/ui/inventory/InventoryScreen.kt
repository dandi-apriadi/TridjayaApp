package com.krisoft.tridjayaelektronik.ui.inventory

import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FilterAlt
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Sell
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SelectableChipColors
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import coil.compose.AsyncImage
import com.krisoft.tridjayaelektronik.data.ProductImageUrl
import com.krisoft.tridjayaelektronik.data.export.InventoryXlsxExporter
import com.krisoft.tridjayaelektronik.data.export.PricetagImageExporter
import com.krisoft.tridjayaelektronik.data.local.BranchStockEntity
import com.krisoft.tridjayaelektronik.data.local.DealerAlias
import com.krisoft.tridjayaelektronik.data.local.ProductAggregate
import com.krisoft.tridjayaelektronik.data.local.ProductSortOrder
import com.krisoft.tridjayaelektronik.data.local.RegionAlias
import com.krisoft.tridjayaelektronik.ui.theme.AgingBadge
import com.krisoft.tridjayaelektronik.ui.theme.agingColor
import com.krisoft.tridjayaelektronik.ui.theme.ClayCard
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveEmptyState
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveErrorState
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveFilledButton
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveFilledIconButton
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveInlineError
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveShapes
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveTextField
import com.krisoft.tridjayaelektronik.ui.theme.SkeletonCard
import com.krisoft.tridjayaelektronik.ui.theme.TridjayaCollapsibleHeader
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(
    onProductClick: (kode: String, kodeCabang: String) -> Unit,
    onBack: (() -> Unit)? = null,
    viewModel: InventoryViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val pagingItems = viewModel.pagingFlow.collectAsLazyPagingItems()
    var showSortSheet by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var showExportSheet by remember { mutableStateOf(false) }
    var showPricetagSheet by remember { mutableStateOf(false) }

    TridjayaCollapsibleHeader(
        title = "Semua Barang",
        onBack = onBack,
        actions = {
            ExpressiveFilledIconButton(
                onClick = {
                    showSearch = !showSearch
                    if (!showSearch) viewModel.onSearchChange("")
                }
            ) {
                Icon(
                    if (showSearch) Icons.Rounded.Close else Icons.Rounded.Search,
                    contentDescription = if (showSearch) "Tutup pencarian" else "Cari barang"
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            ExpressiveFilledIconButton(onClick = { showExportSheet = true }) {
                Icon(Icons.Rounded.Share, contentDescription = "Export inventaris")
            }
            Spacer(modifier = Modifier.width(8.dp))
            ExpressiveFilledIconButton(onClick = { showPricetagSheet = true }) {
                Icon(Icons.Rounded.Sell, contentDescription = "Cetak label harga")
            }
        }
    ) { contentModifier ->
        Column(modifier = contentModifier) {
            AnimatedVisibility(
                visible = showSearch,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                ExpressiveTextField(
                    value = state.filters.search,
                    onValueChange = viewModel::onSearchChange,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = "Cari nama atau kode barang",
                    trailingIcon = if (state.filters.search.isNotEmpty()) {
                        {
                            IconButton(onClick = { viewModel.onSearchChange("") }) {
                                Icon(Icons.Rounded.Clear, contentDescription = "Hapus pencarian")
                            }
                        }
                    } else null
                )
            }
            FilterChipsRow(
                filters = state.filters,
                hasMyRegion = state.myRegion != null,
                myDealer = state.myDealer,
                sertakanStokNol = state.sertakanStokNol,
                stokNolLoading = state.stokNolLoading,
                onToggleStokNol = viewModel::toggleSertakanStokNol,
                onToggleReady = viewModel::toggleReadyOnly,
                onToggleRegion = viewModel::setRegion,
                onMyBranch = viewModel::setMyBranchOnly,
                onToggleMyStore = viewModel::toggleMyStore,
                onToggleDeadstock = viewModel::toggleDeadstockOnly,
                onFilterClick = { showSortSheet = true }
            )
            if (state.syncError != null) {
                ExpressiveInlineError(
                    message = state.syncError ?: "",
                    onRetry = viewModel::refresh,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            val pullState = rememberPullToRefreshState()
            PullToRefreshBox(
                isRefreshing = state.isSyncing,
                onRefresh = viewModel::refresh,
                state = pullState,
                modifier = Modifier.weight(1f),
                indicator = {
                    PullToRefreshDefaults.Indicator(
                        modifier = Modifier.align(Alignment.TopCenter),
                        isRefreshing = state.isSyncing,
                        state = pullState,
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            ) {
                // Barang berstok NOL tak pernah ikut sinkronisasi massal
                // (`inStock=true`), jadi ia tak bisa "disaring balik" dari cache —
                // datanya memang tak ada. Di sini cache ditambal dari server untuk
                // kata kunci yang sedang berlaku; hasilnya masuk Room dan Paging
                // memunculkannya sendiri, jadi tak ada daftar kedua di layar ini.
                // Aturan kapan ia dipanggil hidup di `perluCariStokNol`.
                //
                // `siapDinilai` WAJIB: selama muatan pertama berjalan `itemCount`
                // masih 0, dan tanpa penjagaan ini tiap pembukaan layar menembak
                // pencarian stok-nol atas daftar yang sebenarnya belum termuat.
                val siapDinilai = !state.isSyncing &&
                    pagingItems.loadState.refresh !is LoadState.Loading
                LaunchedEffect(
                    state.filters.search,
                    state.filters.dealer,
                    state.sertakanStokNol,
                    pagingItems.itemCount,
                    siapDinilai,
                ) {
                    // Penambalan mengubah `itemCount`, yang me-relaunch efek ini —
                    // yang mencegah permintaan kedua adalah memo `kunciStokNolTerperiksa`
                    // di ViewModel, bukan daftar key di sini.
                    if (siapDinilai) viewModel.cariStokNol(pagingItems.itemCount)
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    // Bottom clearance so the last row clears the floating nav it scrolls behind.
                    contentPadding = PaddingValues(top = 4.dp, bottom = 100.dp)
                ) {
                    items(
                        count = pagingItems.itemCount,
                        key = pagingItems.itemKey { "${it.kode}|${it.kodeCabang}" }
                    ) { index ->
                        val product = pagingItems[index]
                        if (product != null) {
                            val key = "${product.kode}|${product.kodeCabang}"
                            // derivedStateOf so toggling one card's expand state only recomposes that
                            // card — without it, every visible ProductCard re-reads the whole `state`
                            // object on every expand/collapse, causing scroll jank as the list grows.
                            val isExpanded by remember(key) { derivedStateOf { key in state.expanded } }
                            val isLoadingBranches by remember(key) { derivedStateOf { state.loadingBranchFor == key } }
                            val branches by remember(key) { derivedStateOf { state.branchDetails[key] } }
                            ProductCard(
                                product = product,
                                isExpanded = isExpanded,
                                isLoadingBranches = isLoadingBranches,
                                branches = branches,
                                onClick = { onProductClick(product.kode, product.kodeCabang) },
                                onToggleExpand = { viewModel.toggleExpand(product.kode, product.kodeCabang) }
                            )
                        }
                    }

                    when (val append = pagingItems.loadState.append) {
                        is LoadState.Loading -> {
                            item {
                                Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator()
                                }
                            }
                        }
                        is LoadState.Error -> {
                            item {
                                ExpressiveInlineError(
                                    message = "Gagal memuat data.",
                                    onRetry = { pagingItems.retry() },
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                                )
                            }
                        }
                        else -> Unit
                    }

                    // Empty-list states, mutually exclusive so exactly one renders. Room's own
                    // `loadState.refresh` alone isn't enough to detect "still loading" here — querying
                    // an empty table resolves to NotLoading almost instantly, so on a fresh install
                    // (no rows synced yet) it would flip straight to the "no results" empty state while
                    // the first-ever network sync (`state.isSyncing`) is still running in the background.
                    // Checking `state.isSyncing` too is what actually shows a loading state during that
                    // first download instead of a misleading "Tidak ada barang".
                    if (pagingItems.itemCount == 0 && (state.isSyncing || pagingItems.loadState.refresh is LoadState.Loading)) {
                        items(7) {
                            SkeletonCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                        }
                    } else if (pagingItems.itemCount == 0 && (state.syncError != null || pagingItems.loadState.refresh is LoadState.Error)) {
                        // Sync finished with an error and nothing cached (e.g. first install while
                        // offline) → full error state with retry, instead of a blank list.
                        item {
                            Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                                ExpressiveErrorState(
                                    message = state.syncError ?: "Tidak bisa memuat barang. Periksa koneksi lalu coba lagi.",
                                    onRetry = viewModel::refresh
                                )
                            }
                        }
                    } else if (pagingItems.itemCount == 0) {
                        item {
                            // Barang bisa gak kebaca gara-gara lagi mutasi antar cabang (stok 0 di dua
                            // sisi selama jeda GS OUT->IN) -- cek sekali tiap masuk state kosong ini,
                            // bukan tiap keystroke (lihat InventoryViewModel.checkInTransitHint).
                            LaunchedEffect(state.filters.search, state.filters.dealer, state.filters.region) {
                                viewModel.checkInTransitHint()
                            }
                            Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                                val hint = state.inTransitHint
                                if (hint != null) {
                                    ExpressiveEmptyState(
                                        icon = {
                                            Icon(
                                                Icons.Rounded.Share,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        },
                                        title = "Sedang Dalam Perjalanan",
                                        subtitle = "${hint.namaBarang} sedang dikirim ke ${hint.tujuanCabang} (${hint.tanggal}). " +
                                            "Belum tercatat di stok cabang manapun sampai diterima."
                                    )
                                } else {
                                    ExpressiveEmptyState(
                                        icon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                        title = "Tidak ada barang",
                                        // Tiga kalimat untuk tiga keadaan yang BERBEDA, yang
                                        // sebelumnya sama-sama tampil sebagai "tidak cocok
                                        // dengan filter". Yang paling menyesatkan keadaan
                                        // ketiga: orang membacanya sebagai "barang ini tak ada
                                        // di perusahaan", padahal yang benar "stoknya nol" —
                                        // dan itu jawaban yang sama sekali lain untuk sales
                                        // yang sedang ditanyai konsumen.
                                        subtitle = when {
                                            state.stokNolLoading -> "Mencari barang berstok 0 di server…"
                                            state.stokNolDitemukan == 0 ->
                                                "Tidak ada barang yang cocok, termasuk yang berstok 0 di katalog."
                                            else -> "Tidak ada barang yang cocok dengan filter"
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSortSheet) {
        InventoryFilterSheet(
            filters = state.filters,
            categories = state.categories,
            merks = state.merks,
            onApply = { category, merk, sortOrder, dealerText ->
                viewModel.applyFilterSheet(category, merk, sortOrder, dealerText)
                showSortSheet = false
            },
            onDismiss = { showSortSheet = false }
        )
    }

    if (showExportSheet) {
        InventoryExportSheet(
            filters = state.filters,
            loadItems = { viewModel.exportProducts() },
            onDismiss = { showExportSheet = false }
        )
    }

    if (showPricetagSheet) {
        InventoryPricetagSheet(
            filters = state.filters,
            loadItems = { viewModel.exportProducts() },
            onDismiss = { showPricetagSheet = false }
        )
    }
}

private suspend fun exportAndSharePricetags(
    context: Context,
    products: List<ProductAggregate>,
    markup: Boolean,
    filePrefix: String
) {
    val uri = PricetagImageExporter.export(context, products, markup, filePrefix)
    // Satu barang -> PNG tunggal; banyak barang -> ZIP berisi satu PNG per barang.
    // MIME diambil dari ekstensi berkas lewat FileProvider, bukan ditebak di sini.
    val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Bagikan Label Harga"))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InventoryPricetagSheet(
    filters: InventoryFilters,
    loadItems: suspend () -> List<ProductAggregate>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<ProductAggregate>?>(null) }
    var isExporting by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf(false) }
    var markup by remember { mutableStateOf(true) }
    val filterSummary = remember(filters) { buildFilterSummary(filters) }

    LaunchedEffect(Unit) {
        runCatching { withContext(Dispatchers.IO) { loadItems() } }
            .onSuccess { items = it; loadError = false }
            .onFailure {
                items = emptyList()
                loadError = true
                Toast.makeText(context, "Gagal memuat produk untuk label harga", Toast.LENGTH_LONG).show()
            }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = ExpressiveShapes.Squircle,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(modifier = Modifier.padding(14.dp), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Sell,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = "Cetak Label Harga",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Foto (.png) per barang, dikemas .zip kalau lebih dari satu",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            ClayCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Total Barang",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val count = items?.size
                            if (count == null) {
                                Spacer(modifier = Modifier.height(4.dp))
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Text(
                                    text = "$count item",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Surface(
                            shape = ExpressiveShapes.Squircle,
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Box(modifier = Modifier.padding(10.dp)) {
                                Icon(
                                    imageVector = Icons.Rounded.Inventory2,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                    if (filterSummary != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.FilterAlt,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = filterSummary,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            ClayCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Naikkan harga otomatis",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (markup) {
                                "Harga besar = harga sistem + Rp 300.000, harga coret = harga sistem"
                            } else {
                                "Harga besar = harga sistem apa adanya, tanpa coret"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = markup, onCheckedChange = { markup = it })
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            ExpressiveFilledButton(
                onClick = {
                    val list = items ?: return@ExpressiveFilledButton
                    if (list.isEmpty()) {
                        Toast.makeText(context, "Tidak ada barang untuk dicetak", Toast.LENGTH_SHORT).show()
                        return@ExpressiveFilledButton
                    }
                    isExporting = true
                    scope.launch {
                        try {
                            exportAndSharePricetags(context, list, markup, buildExportPrefix(filters))
                            onDismiss()
                        } catch (t: Throwable) {
                            // `Throwable`, bukan `Exception` — pola sama `InventoryExportSheet`
                            // (lihat catatan di sana soal `NoClassDefFoundError`/`NoSuchMethodError`).
                            Toast.makeText(context, "Gagal membuat file: ${t.message ?: "kesalahan tak terduga"}", Toast.LENGTH_LONG).show()
                        } finally {
                            isExporting = false
                        }
                    }
                },
                enabled = !isExporting && items != null && !loadError,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isExporting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Membuat foto...")
                } else {
                    Icon(Icons.Rounded.Sell, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (markup) "Buat Foto (+Rp 300.000)" else "Buat Foto (Harga Asli)")
                }
            }
        }
    }
}

private suspend fun exportAndShareXlsx(context: Context, products: List<ProductAggregate>, filePrefix: String) {
    val uri = InventoryXlsxExporter.export(context, products, filePrefix)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Export ke Excel"))
}

/** Human-readable summary of active filters ("Ready · Jakarta · Kategori: TV"), null if none active. */
private fun buildFilterSummary(filters: InventoryFilters): String? {
    val parts = buildList {
        if (filters.readyOnly) add("Ready")
        if (filters.deadstockOnly) add("Deadstock")
        if (filters.region.isNotEmpty()) add(RegionAlias.label(filters.region))
        if (filters.dealer.isNotEmpty()) add(DealerAlias.label(filters.dealer))
        if (filters.category.isNotEmpty()) add(filters.category)
        if (filters.merk.isNotEmpty()) add(filters.merk)
        if (filters.search.isNotBlank()) add("\"${filters.search}\"")
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

/** Prefix nama file export dari filter aktif — urutan Merk_Kategori_Deadstock (bukan "inventaris").
 *  Contoh: "Samsung_TV", "Deadstock", "LG_Kulkas". Kosong bila tak ada filter → "Produk". */
private fun buildExportPrefix(filters: InventoryFilters): String {
    val parts = buildList {
        if (filters.merk.isNotBlank()) add(filters.merk)
        if (filters.category.isNotBlank()) add(filters.category)
        if (filters.deadstockOnly) add("Deadstock")
    }
    val slug = parts.joinToString("_") { it.trim().replace(Regex("[^A-Za-z0-9]+"), "-").trim('-') }
        .replace(Regex("_+"), "_").trim('_')
    return slug.ifBlank { "Produk" }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InventoryExportSheet(
    filters: InventoryFilters,
    loadItems: suspend () -> List<ProductAggregate>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<ProductAggregate>?>(null) }
    var isExporting by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf(false) }
    val filterSummary = remember(filters) { buildFilterSummary(filters) }

    // Muat daftar produk di IO + tahan error: kegagalan tak boleh crash / nyangkut spinner.
    LaunchedEffect(Unit) {
        runCatching { withContext(Dispatchers.IO) { loadItems() } }
            .onSuccess { items = it; loadError = false }
            .onFailure {
                items = emptyList()
                loadError = true
                Toast.makeText(context, "Gagal memuat produk untuk export", Toast.LENGTH_LONG).show()
            }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = ExpressiveShapes.Squircle,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(modifier = Modifier.padding(14.dp), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.TableChart,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = "Export Inventaris",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Format Microsoft Excel (.xlsx)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            ClayCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Total Produk",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val count = items?.size
                            if (count == null) {
                                Spacer(modifier = Modifier.height(4.dp))
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Text(
                                    text = "$count item",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Surface(
                            shape = ExpressiveShapes.Squircle,
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Box(modifier = Modifier.padding(10.dp)) {
                                Icon(
                                    imageVector = Icons.Rounded.Inventory2,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                    if (filterSummary != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.FilterAlt,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = filterSummary,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            ExpressiveFilledButton(
                onClick = {
                    val list = items ?: return@ExpressiveFilledButton
                    if (list.isEmpty()) {
                        Toast.makeText(context, "Tidak ada produk untuk diexport", Toast.LENGTH_SHORT).show()
                        return@ExpressiveFilledButton
                    }
                    isExporting = true
                    scope.launch {
                        try {
                            exportAndShareXlsx(context, list, buildExportPrefix(filters))
                            onDismiss()
                        } catch (t: Throwable) {
                            // `Throwable`, BUKAN `Exception`. Kegagalan kelas
                            // yang hilang di API lama (`NoClassDefFoundError`,
                            // `NoSuchMethodError`) adalah `Error` — `catch (e:
                            // Exception)` melewatkannya dan app tertutup. Sejak
                            // core library desugaring dinyalakan penyebab yang
                            // diketahui sudah hilang, tapi penjaga ini tetap
                            // dipertahankan: pustaka pihak ketiga bisa membawa
                            // pemakaian API baru lain kapan saja, dan ekspor
                            // gagal seharusnya tak pernah setara app tertutup.
                            Toast.makeText(context, "Gagal membuat file: ${t.message ?: "kesalahan tak terduga"}", Toast.LENGTH_LONG).show()
                        } finally {
                            isExporting = false
                        }
                    }
                },
                enabled = !isExporting && items != null && !loadError,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isExporting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Membuat file...")
                } else {
                    Icon(Icons.Rounded.TableChart, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Export ke Excel (.xlsx)")
                }
            }
        }
    }
}

@Composable
private fun FilterChipsRow(
    filters: InventoryFilters,
    hasMyRegion: Boolean,
    myDealer: String?,
    /** Chip "Termasuk stok 0" — saklar jaringan, bukan bagian [InventoryFilters]. */
    sertakanStokNol: Boolean,
    stokNolLoading: Boolean,
    onToggleStokNol: () -> Unit,
    onToggleReady: () -> Unit,
    onToggleRegion: (String) -> Unit,
    onMyBranch: () -> Unit,
    onToggleMyStore: () -> Unit,
    onToggleDeadstock: () -> Unit,
    onFilterClick: () -> Unit
) {
    val chipColors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
        selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer
    )
    // Deadstock pakai aksen merah supaya langsung terbaca sebagai "barang bermasalah".
    val deadstockColors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = Color(0xFFC62828),
        selectedLabelColor = Color.White,
        selectedLeadingIconColor = Color.White
    )
    val filterActive = filters.category.isNotEmpty() || filters.merk.isNotEmpty() ||
        filters.dealer.isNotEmpty() || filters.sortOrder != ProductSortOrder.NAME_ASC
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
    ) {
        item {
            // Semua filter kategori/merk/urutan pindah ke bottom sheet — satu pintu.
            FilterChip(
                selected = filterActive,
                onClick = onFilterClick,
                label = { Text(if (filterActive) "Filter Aktif" else "Filter & Urut") },
                leadingIcon = {
                    Icon(Icons.Rounded.FilterAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                },
                shape = RoundedCornerShape(50),
                colors = chipColors
            )
        }
        item {
            FilterChip(
                selected = filters.readyOnly,
                onClick = onToggleReady,
                label = { Text("Ready") },
                leadingIcon = {
                    Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                },
                shape = RoundedCornerShape(50),
                colors = chipColors
            )
        }
        item {
            // BUKAN kembaran "Ready" yang dibalik. "Ready" menyaring apa yang SUDAH
            // ada di Room; chip ini menambah datanya — barang berstok nol tak pernah
            // ikut disinkron (`inStock=true`), jadi mematikan "Ready" saja tak
            // memunculkan apa pun. Lihat `domain/inventory/KatalogStokNol.kt`.
            FilterChip(
                selected = sertakanStokNol,
                onClick = onToggleStokNol,
                label = { Text("Termasuk stok 0") },
                leadingIcon = {
                    if (stokNolLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                },
                shape = RoundedCornerShape(50),
                colors = chipColors
            )
        }
        item {
            FilterChip(
                selected = filters.deadstockOnly,
                onClick = onToggleDeadstock,
                label = { Text("Deadstock") },
                leadingIcon = {
                    Icon(Icons.Rounded.HourglassBottom, contentDescription = null, modifier = Modifier.size(16.dp))
                },
                shape = RoundedCornerShape(50),
                colors = deadstockColors
            )
        }
        if (myDealer != null) {
            item {
                // Otomatis dari cabang profil user login — sekali tap: stok toko sendiri saja.
                FilterChip(
                    selected = filters.dealer == myDealer,
                    onClick = onToggleMyStore,
                    label = { Text("Toko Saya") },
                    leadingIcon = {
                        Icon(Icons.Rounded.Storefront, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    shape = RoundedCornerShape(50),
                    colors = chipColors
                )
            }
        }
        if (hasMyRegion) {
            item {
                FilterChip(
                    selected = false,
                    onClick = onMyBranch,
                    label = { Text("Cabang Saya") },
                    leadingIcon = {
                        Icon(Icons.Rounded.Place, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    shape = RoundedCornerShape(50)
                )
            }
        }
        items(RegionAlias.allCodes) { code ->
            FilterChip(
                selected = filters.region == code,
                onClick = { onToggleRegion(code) },
                label = { Text(RegionAlias.label(code)) },
                leadingIcon = {
                    Icon(Icons.Rounded.LocationOn, contentDescription = null, modifier = Modifier.size(16.dp))
                },
                shape = RoundedCornerShape(50),
                colors = chipColors
            )
        }
    }
}

// Kategori+Merk kini diatur lewat InventoryFilterSheet di bawah (bottom sheet gabungan);
// Global Search masih memakai FilterPanelChip di ui/theme/FilterPanel.kt.

/** Sheet gabungan Filter (kategori/merk dgn saran ketik) + Urutkan (nama/harga/stok). Draft lokal —
 *  baru diterapkan saat tombol Terapkan ditekan, pola yang sama dengan panel filter lama. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun InventoryFilterSheet(
    filters: InventoryFilters,
    categories: List<String>,
    merks: List<String>,
    onApply: (category: String, merk: String, sortOrder: Int, dealerText: String) -> Unit,
    onDismiss: () -> Unit
) {
    var categoryDraft by remember { mutableStateOf(filters.category) }
    var merkDraft by remember { mutableStateOf(filters.merk) }
    var sortDraft by remember { mutableStateOf(filters.sortOrder) }
    // Draft menyimpan KODE dealer ("D-01"), bukan labelnya: dulu kolom ini teks
    // bebas yang di-resolve balik ke kode saat Terapkan, jadi salah ketik satu
    // huruf diam-diam berarti "semua toko" — filternya seolah tak berfungsi.
    var dealerDraft by remember { mutableStateOf(filters.dealer) }
    val sortOptions = listOf(
        ProductSortOrder.NAME_ASC to "Nama (A-Z)",
        ProductSortOrder.PRICE_ASC to "Harga Termurah",
        ProductSortOrder.PRICE_DESC to "Harga Termahal",
        ProductSortOrder.STOCK_DESC to "Stok Terbanyak",
        ProductSortOrder.STOCK_ASC to "Stok Paling Sedikit"
    )

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.FilterAlt, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    text = "Filter & Urutkan",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp).weight(1f)
                )
                if (categoryDraft.isNotEmpty() || merkDraft.isNotEmpty() || dealerDraft.isNotEmpty() ||
                    sortDraft != ProductSortOrder.NAME_ASC
                ) {
                    Text(
                        text = "Reset",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                categoryDraft = ""
                                merkDraft = ""
                                dealerDraft = ""
                                sortDraft = ProductSortOrder.NAME_ASC
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            SheetSuggestField(
                label = "Kategori",
                value = categoryDraft,
                onValueChange = { categoryDraft = it },
                options = categories,
                placeholder = "Semua kategori"
            )
            Spacer(modifier = Modifier.height(12.dp))
            SheetSuggestField(
                label = "Merk",
                value = merkDraft,
                onValueChange = { merkDraft = it },
                options = merks,
                placeholder = "Semua merk"
            )
            Spacer(modifier = Modifier.height(12.dp))
            SheetDealerDropdown(
                selectedCode = dealerDraft,
                onSelect = { dealerDraft = it }
            )

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Urutkan Berdasarkan",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            sortOptions.forEach { (value, label) ->
                val selected = sortDraft == value
                ListItem(
                    headlineContent = {
                        Text(
                            text = label,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    },
                    leadingContent = {
                        RadioButton(selected = selected, onClick = { sortDraft = value })
                    },
                    colors = ListItemDefaults.colors(
                        containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else Color.Transparent
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { sortDraft = value }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            ExpressiveFilledButton(
                onClick = { onApply(categoryDraft.trim(), merkDraft.trim(), sortDraft, dealerDraft) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Terapkan")
            }
        }
    }
}

/**
 * Pemilih toko: dropdown pilihan tetap, bukan kolom teks bebas.
 *
 * Sebelumnya toko memakai [SheetSuggestField] yang sama dengan Kategori & Merk,
 * padahal tokonya cuma 13 dan daftarnya TETAP ([DealerAlias]) — teks bebas cuma
 * membuka dua cara gagal yang senyap: (1) salah ketik ter-resolve jadi kosong
 * sehingga filternya terlihat tak berfungsi, dan (2) saran chip dipotong enam,
 * jadi toko ke-7 dan seterusnya hanya bisa ditemukan dengan mengetik namanya
 * lebih dulu. Kategori & Merk TETAP teks bebas: nilainya datang dari data ERP,
 * jumlahnya tak tentu, dan mengetik memang lebih cepat di sana.
 */
@Composable
private fun SheetDealerDropdown(
    selectedCode: String,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Text(text = "Toko / Cabang", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    Spacer(modifier = Modifier.height(6.dp))
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(14.dp))
                .clickable { expanded = true }
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Storefront,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = if (selectedCode.isEmpty()) "Semua toko" else DealerAlias.label(selectedCode),
                style = MaterialTheme.typography.bodyMedium,
                color = if (selectedCode.isEmpty()) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.padding(start = 10.dp).weight(1f)
            )
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = "Pilih toko",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DealerOption(
                label = "Semua toko",
                selected = selectedCode.isEmpty(),
                onClick = { onSelect(""); expanded = false }
            )
            DealerAlias.allCodes.forEach { code ->
                DealerOption(
                    label = DealerAlias.label(code),
                    selected = code == selectedCode,
                    onClick = { onSelect(code); expanded = false }
                )
            }
        }
    }
}

@Composable
private fun DealerOption(label: String, selected: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = {
            Text(
                text = label,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
        },
        trailingIcon = if (selected) {
            {
                Icon(
                    Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        } else null,
        onClick = onClick
    )
}

/** Kolom teks + saran chip (maks 6, terfilter mengikuti ketikan) — tap saran mengisi kolom. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SheetSuggestField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    options: List<String>,
    placeholder: String
) {
    Text(text = label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    Spacer(modifier = Modifier.height(6.dp))
    ExpressiveTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = placeholder,
        trailingIcon = if (value.isNotEmpty()) {
            {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(Icons.Rounded.Clear, contentDescription = "Hapus $label")
                }
            }
        } else null
    )
    val suggestions = remember(value, options) {
        options.filter { it.contains(value.trim(), ignoreCase = true) && !it.equals(value.trim(), ignoreCase = true) }
            .take(6)
    }
    if (suggestions.isNotEmpty()) {
        Spacer(modifier = Modifier.height(6.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            suggestions.forEach { option ->
                Surface(
                    onClick = { onValueChange(option) },
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Text(
                        text = option,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ProductCard(
    product: ProductAggregate,
    isExpanded: Boolean,
    isLoadingBranches: Boolean,
    branches: List<BranchStockEntity>?,
    onClick: () -> Unit,
    onToggleExpand: () -> Unit
) {
    ClayCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                // Rhythm-style leading artwork slot — real photo when the ERP has one (`Gambar`),
                // icon placeholder otherwise (most products don't have one populated yet).
                val imageUrl = remember(product.gambar) { ProductImageUrl.resolve(product.gambar) }
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(52.dp)
                ) {
                    // AsyncImage (bukan SubcomposeAsyncImage) — subkomposisi per baris terlalu
                    // mahal saat fling di list panjang; ikon placeholder cukup digambar di
                    // belakang dan tertutup foto begitu berhasil dimuat.
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            imageVector = Icons.Rounded.Inventory2,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(26.dp)
                        )
                        if (imageUrl != null) {
                            AsyncImage(
                                model = imageUrl,
                                contentDescription = product.nama,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.size(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = product.nama, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = "Kode: ${product.kode}  |  ${product.kategori}  |  ${product.merk}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = RegionAlias.label(product.kodeCabang),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        // Aging stok tertua di antara semua dealer (kolom Kondisi web Stok All Cabang).
                        product.maxUmurHari?.let { umur ->
                            Spacer(modifier = Modifier.width(8.dp))
                            AgingBadge(umur)
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = remember(product.harga) { formatRupiah(product.harga) },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "${product.totalStok.toInt()}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                IconButton(onClick = onToggleExpand, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "Sembunyikan stok cabang" else "Lihat stok cabang"
                    )
                }
            }

            AnimatedVisibility(visible = isExpanded, enter = expandVertically(), exit = shrinkVertically()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                        .padding(10.dp)
                ) {
                    when {
                        isLoadingBranches -> {
                            Box(modifier = Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                            }
                        }
                        branches.isNullOrEmpty() -> {
                            Text(text = "Tidak ada rincian stok cabang", style = MaterialTheme.typography.bodySmall)
                        }
                        else -> {
                            branches.forEach { branch ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = DealerAlias.label(branch.kodeDealer),
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.weight(1f)
                                    )
                                    // Umur stok per dealer — deadstock (>=90 hr) tampil merah.
                                    branch.umurHari?.let { umur ->
                                        Text(
                                            text = "$umur hr",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = agingColor(umur)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                    }
                                    Text(text = "${branch.stok.toInt()}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatRupiah(value: Double): String {
    val rounded = value.toLong()
    val text = rounded.toString().reversed().chunked(3).joinToString(".").reversed()
    return "Rp $text"
}
