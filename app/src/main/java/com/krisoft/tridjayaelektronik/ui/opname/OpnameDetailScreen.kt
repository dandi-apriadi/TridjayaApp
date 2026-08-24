package com.krisoft.tridjayaelektronik.ui.opname

import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddAPhoto
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.krisoft.tridjayaelektronik.data.KONDISI_LAYAK
import com.krisoft.tridjayaelektronik.data.KONDISI_PILIHAN
import com.krisoft.tridjayaelektronik.data.kondisiLabel
import com.krisoft.tridjayaelektronik.data.export.OpnamePdfExporter
import com.krisoft.tridjayaelektronik.data.local.OpnameUnitEntity
import com.krisoft.tridjayaelektronik.ui.deliveryflow.BarcodeScanButton
import com.krisoft.tridjayaelektronik.data.model.OpnameItemDto
import com.krisoft.tridjayaelektronik.data.model.OpnameStockItemDto
import com.krisoft.tridjayaelektronik.data.model.OpnameUnitDto
import com.krisoft.tridjayaelektronik.data.model.SerialRequestDto
import com.krisoft.tridjayaelektronik.ui.theme.ClayCard
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveErrorState
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveFilledButton
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveFilledIconButton
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveInlineError
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveTextField
import com.krisoft.tridjayaelektronik.ui.theme.ScrollableCenter
import com.krisoft.tridjayaelektronik.ui.theme.SkeletonCard
import com.krisoft.tridjayaelektronik.ui.theme.TridjayaCollapsibleHeader
import com.krisoft.tridjayaelektronik.ui.theme.TridjayaPullRefresh
import com.krisoft.tridjayaelektronik.util.kunciUnik
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun formatRupiah(value: Double): String {
    val negative = value < 0
    val digits = kotlin.math.abs(value).toLong().toString().reversed().chunked(3).joinToString(".").reversed()
    return (if (negative) "-Rp " else "Rp ") + digits
}

internal enum class StockFilter { SEMUA, BELUM, SUDAH }

/**
 * Kenapa seksi "Hitung Barang" tertutup — kalimatnya, bukan vonisnya.
 *
 * Cerminan `frontend/src/utils/opnameIzinCatat.ts` (`kalimatSebabCatat`); satu
 * kontrak, dua klien. Sampai 2026-08-21 layar ini hanya punya SATU kalimat, dan
 * kalimat itu mengarahkan SEMUA orang ke admin stok. Untuk pemantau lintas
 * cabang arahan itu BUNTU: manager/owner tak pernah punya `opname.hitung` di
 * cabang mana pun, dan admin/superadmin sejak 2026-08-21 memang sengaja dibuka
 * BACA-saja (`has_admin_platform` di `authorize_view`) — admin stok tak punya
 * apa pun untuk diberikan kepada mereka. Menyuruh mereka menagih izin membuat
 * admin stok menerima permintaan yang tak bisa ia penuhi.
 *
 * Fungsi MURNI supaya kalimatnya teruji tanpa Compose. Kedua argumen boleh
 * `null` — konteks diambil terpisah dan permintaannya boleh gagal; `null`
 * selalu berarti "belum tahu", TIDAK PERNAH "bukan pemantau", jadi kegagalan
 * memuat konteks jatuh ke kalimat LAMA, bukan menuduh peran yang salah.
 */
internal fun kalimatHitungTertutup(isManager: Boolean?, lingkup: String?): String = when {
    isManager == true ->
        "Akunmu memantau opname lintas cabang (read-only), jadi pencatatan memang tidak " +
            "tersedia di sini — bukan izin yang kurang. Semua angka, selisih, dan temuan " +
            "tetap bisa kamu baca."
    lingkup == "semua" ->
        "Akunmu administrator platform: boleh membaca sesi opname seluruh cabang, tapi " +
            "pencatatannya milik petugas dan admin stok cabang itu sendiri. Semua angka, " +
            "selisih, dan temuan tetap terbaca di sini."
    else ->
        "Kamu bisa melihat sesi ini, tapi belum bisa mencatat unit — cabang ini sudah " +
            "menunjuk petugas opname, atau sesi ini milik cabang lain. Hubungi admin stok " +
            "kalau seharusnya kamu ikut menghitung."
}

/**
 * Saring snapshot barang sesi ini menurut status scan lalu (opsional) teks
 * cari — dua langkah TERPISAH supaya "cari di dalam Belum" tetap bisa
 * dijawab dari satu kolom cari yang sama, bukan dua UI berbeda.
 */
internal fun filterOpnameStock(
    stock: List<OpnameStockItemDto>,
    unitsByCode: Map<String, List<OpnameUnitEntity>>,
    filter: StockFilter,
    search: String,
): List<OpnameStockItemDto> {
    val byStatus = when (filter) {
        StockFilter.SEMUA -> stock
        StockFilter.BELUM -> stock.filter { !unitsByCode.containsKey(it.kodeBarang.uppercase()) }
        StockFilter.SUDAH -> stock.filter { unitsByCode.containsKey(it.kodeBarang.uppercase()) }
    }
    val term = search.trim()
    if (term.isBlank()) return byStatus
    return byStatus.filter {
        it.kodeBarang.contains(term, ignoreCase = true) ||
            (it.namaBarang ?: "").contains(term, ignoreCase = true)
    }
}

/**
 * Kenapa daftar barang sesi ini kosong di layar. Tiga keadaan yang tindak
 * lanjutnya berbeda: tunggu / coba lagi / minta sesinya dibatalkan.
 *
 * [SEDANG_DIMUAT] sebelumnya TIDAK ADA, dan itu cacatnya: permintaan yang masih
 * terbang tak bisa dibedakan dari daftar yang benar-benar kosong, jadi sesi
 * sehat divonis "tidak punya daftar barang sama sekali" — lengkap dengan saran
 * membatalkannya — selama satu round-trip penuh.
 */
internal enum class SebabDaftarBarangKosong { SEDANG_DIMUAT, GAGAL_DIMUAT, MEMANG_KOSONG }

/**
 * Vonis tiga-keadaan atas daftar barang yang kosong. Dipisah dari Compose
 * supaya bisa diuji (pola sama [filterOpnameStock]) — modul ini tak punya
 * source set `androidTest` sama sekali, jadi keputusan yang hidup di dalam
 * `@Composable` tak terjaga apa pun.
 *
 * [stockLoading] MENANG atas [stockError], dan itu disengaja: error lama sengaja
 * TIDAK dibersihkan saat "Coba lagi" ditekan (`stockError` baru ditimpa setelah
 * jawabannya tiba), jadi tanpa urutan ini percobaan ulang akan tetap terbaca
 * gagal selama ia berlangsung.
 *
 * Yang TIDAK dijanjikan di sini: layar berubah seketika saat tombol ditekan.
 * `load(paksaStock = true)` mengulang dari permintaan DETAIL, dan [stockLoading]
 * baru menyala sesudah detail itu tiba — jadi ada jeda pendek yang masih
 * menampilkan kegagalan lama. Yang dijamin cuma ini: begitu permintaan stok
 * benar-benar terbang, vonisnya SEDANG_DIMUAT, bukan GAGAL_DIMUAT.
 */
internal fun sebabDaftarBarangKosong(
    stockLoading: Boolean,
    stockError: String?,
): SebabDaftarBarangKosong = when {
    stockLoading -> SebabDaftarBarangKosong.SEDANG_DIMUAT
    stockError != null -> SebabDaftarBarangKosong.GAGAL_DIMUAT
    else -> SebabDaftarBarangKosong.MEMANG_KOSONG
}

/**
 * One opname session. Counting is BLIND (system stock is never shown while counting — matches
 * the physical-count discipline and the backend's own coverage endpoint) and per-UNIT: satu
 * baris per serial number, bukan angka jumlah. Tiap scan disimpan ke Room lalu LANGSUNG
 * dikirim (duplikat cuma bisa divonis server); tanpa sinyal ia diantre dan bisa dikirim ulang.
 * Selisih unit + nilainya baru terungkap dari server setelah sesi ditutup.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpnameDetailScreen(
    sessionId: String,
    onBack: () -> Unit,
    viewModel: OpnameDetailViewModel = hiltViewModel()
) {
    BackHandler(onBack = onBack)
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var search by remember { mutableStateOf("") }
    var stockFilter by remember { mutableStateOf(StockFilter.BELUM) }
    var scanSheetOpen by remember { mutableStateOf(false) }
    // Dikonfirmasi dulu: nihil tercatat SELISIH PENUH (barang dilaporkan
    // hilang), bukan "lewati saja".
    var konfirmasiNihil by remember { mutableStateOf<OpnameStockItemDto?>(null) }
    var confirmAction by remember { mutableStateOf<String?>(null) } // "complete" | "cancel"
    var isExportingPdf by remember { mutableStateOf(false) }

    LaunchedEffect(sessionId) { viewModel.load(sessionId) }

    val detail = state.detail
    // Jumlah unit per SKU: dipakai penanda "sudah dihitung" di hasil pencarian.
    val unitsByCode = remember(state.units) {
        state.units.groupBy { it.kodeBarang.uppercase() }
    }
    val pendingCount = remember(state.units) { state.units.count { it.syncedAtMillis == null } }
    val searchResults = remember(state.stock, search, unitsByCode) {
        val term = search.trim()
        if (term.isBlank()) emptyList()
        else state.stock.asSequence()
            .filter {
                it.kodeBarang.contains(term, ignoreCase = true) ||
                    (it.namaBarang ?: "").contains(term, ignoreCase = true)
            }
            // Already-counted items sink to the bottom so the next uncounted item is always
            // the first thing under the search box.
            .sortedBy { unitsByCode.containsKey(it.kodeBarang.uppercase()) }
            .take(20)
            .toList()
    }
    val coverageList = remember(state.stock, stockFilter, unitsByCode) {
        filterOpnameStock(state.stock, unitsByCode, stockFilter, search = "").take(200)
    }

    TridjayaCollapsibleHeader(
        title = "Sesi Opname",
        onBack = onBack,
        actions = {
            ExpressiveFilledIconButton(
                onClick = {
                    val current = state.detail ?: return@ExpressiveFilledIconButton
                    if (isExportingPdf) return@ExpressiveFilledIconButton
                    isExportingPdf = true
                    scope.launch {
                        runCatching {
                            val uri = withContext(Dispatchers.IO) {
                                OpnamePdfExporter.export(context, current, state.units)
                            }
                            sharePdf(context, uri)
                        }
                        isExportingPdf = false
                    }
                }
            ) {
                if (isExportingPdf) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(Icons.Rounded.PictureAsPdf, contentDescription = "Print ke PDF")
                }
            }
        }
    ) { contentModifier ->
        val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        TridjayaPullRefresh(
            isRefreshing = state.isLoading && detail != null,
            // paksaStock: daftar barang hanya diambil sekali saat masuk layar, jadi tanpa ini
            // tarik-turun cuma menyegarkan angka header.
            onRefresh = { viewModel.load(sessionId, paksaStock = true) },
            modifier = contentModifier
        ) {
        when {
            state.isLoading && detail == null -> {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    repeat(5) {
                        SkeletonCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                    }
                }
            }
            detail == null -> {
                ScrollableCenter {
                    ExpressiveErrorState(
                        message = state.errorMessage ?: "Tidak bisa memuat sesi opname.",
                        onRetry = { viewModel.load(sessionId) }
                    )
                }
            }
            else -> {
                val completed = detail.status == "completed"
                // remember — jangan jumlahkan ulang ratusan baris setiap ketikan di kolom cari
                // (recomposition scope layar ini ikut ter-trigger oleh perubahan `search`).
                val totalUnit = state.units.size
                val selisihUnit = remember(detail.items) { detail.items.sumOf { it.selisih } }
                val selisihNilai = remember(detail.items) {
                    detail.items.sumOf { (it.harga ?: 0.0) * it.selisih }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp + navBottom)
                ) {
                    item(key = "header") {
                        ClayCard(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = detail.kodeOpname,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            // Jendela waktu ikut di baris ini, bukan
                                            // di kartu terpisah: ia menentukan apakah
                                            // petugas bisa scan SEKARANG, jadi harus
                                            // terbaca bersama identitas sesinya.
                                            text = listOfNotNull(
                                                detail.dealerName.ifBlank { detail.dealerCode },
                                                formatOpnameDate(detail.periodeDate),
                                                if (detail.jenis == "mingguan") "Mingguan" else "Bulanan",
                                                labelJendela(detail.mulaiAt, detail.selesaiAt),
                                            ).joinToString(" \u00b7 "),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    OpnameStatusBadge(detail.status)
                                }
                                if (!detail.catatan.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = detail.catatan,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                if (completed) {
                                    // Selisih (unit + nilai Rp) only exists once the server has
                                    // reconciled the finished session.
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        OpnameStat("Jenis Barang", "${detail.totalItems}")
                                        OpnameStat("Unit Fisik", "${detail.totalStokFisik}")
                                        OpnameStat(
                                            "Selisih Unit",
                                            if (selisihUnit > 0) "+$selisihUnit" else "$selisihUnit",
                                            highlight = selisihUnit != 0L
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Nilai Selisih",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = formatRupiah(selisihNilai),
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (selisihNilai < 0) Color(0xFFF04438) else MaterialTheme.colorScheme.primary
                                        )
                                    }
                                } else {
                                    // Live counting progress: total coverage, REMAINING types
                                    // (drops with every input), and inputted units.
                                    val totalJenis = state.stock.size
                                    val jenisTerhitung = unitsByCode.size
                                    val sisaJenis = (totalJenis - jenisTerhitung).coerceAtLeast(0)
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        OpnameStat(
                                            "Sisa Jenis",
                                            if (totalJenis > 0) "$sisaJenis" else "-",
                                            highlight = totalJenis > 0 && sisaJenis > 0
                                        )
                                        OpnameStat(
                                            "Dihitung",
                                            if (totalJenis > 0) "$jenisTerhitung/$totalJenis" else "$jenisTerhitung"
                                        )
                                        OpnameStat("Unit Diinput", "$totalUnit")
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Dibuat ${detail.createdByName ?: "-"}" +
                                        (detail.completedByName?.let { " · diselesaikan $it" } ?: ""),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    if (state.statusError != null) {
                        item(key = "status_error") {
                            ExpressiveInlineError(
                                message = state.statusError ?: "",
                                modifier = Modifier.padding(vertical = 6.dp)
                            )
                        }
                    }

                    if (state.canHitung) {
                        item(key = "count_input") {
                            Column(modifier = Modifier.padding(top = 12.dp)) {
                                Text(
                                    text = "Hitung Barang",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Scan serial tiap unit. Tersimpan di HP lalu langsung dikirim; tanpa sinyal akan diantre.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (pendingCount > 0) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "$pendingCount unit menunggu terkirim",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        TextButton(onClick = { viewModel.retryPending() }) {
                                            Text("Kirim ulang")
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                ExpressiveTextField(
                                    value = search,
                                    onValueChange = { search = it },
                                    placeholder = "Cari kode atau nama barang...",
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                        val kunciStok = kunciUnik(searchResults) { "stock_${it.kodeBarang}" }
                        itemsIndexed(searchResults, key = { i, _ -> kunciStok.getOrElse(i) { "idx_stock_$i" } }) { _, stockItem ->
                            StockSearchRow(
                                item = stockItem,
                                unitCount = unitsByCode[stockItem.kodeBarang.uppercase()]?.size ?: 0,
                                onClick = {
                                    viewModel.selectItem(stockItem)
                                    scanSheetOpen = true
                                }
                            )
                        }
                        if (search.isNotBlank() && searchResults.isEmpty()) {
                            item(key = "stock_empty") {
                                Text(
                                    text = "Tidak ada barang cocok di daftar opname sesi ini",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 10.dp)
                                )
                            }
                        }
                    }

                    // Seluruh seksi "Hitung Barang" lenyap saat `canHitung` false —
                    // dan sejak penunjukan petugas (migrasi 212) itu kondisi yang
                    // WAJAR bagi karyawan yang tetap boleh MEMBACA sesinya (notif
                    // `opname_sesi_dibuka` menyapu se-cabang). Tanpa kalimat ini
                    // ia membuka undangan lalu menemukan layar yang tak bisa
                    // diapa-apakan, tanpa satu pun penjelasan.
                    if (!completed && !state.canHitung && detail.status == "draft") {
                        item(key = "hitung_tertutup") {
                            Column(modifier = Modifier.padding(top = 12.dp)) {
                                IzinKurangNote(
                                    kalimatHitungTertutup(state.isManager, state.lingkup)
                                )
                            }
                        }
                    }

                    // Seluruh blok "Daftar Barang" di bawah hanya dirender saat
                    // `state.stock` terisi. Tanpa cabang ini, sesi yang daftar
                    // barangnya BELUM SELESAI atau GAGAL dimuat terlihat persis
                    // seperti sesi tanpa barang: layarnya sunyi, tak ada tombol
                    // muat ulang, dan petugas yang sedang berdiri di gudang
                    // menyimpulkan sesinya rusak. Ketiga sebabnya dibedakan
                    // karena tindak lanjutnya berbeda.
                    if (!completed && detail.status == "draft" && state.stock.isEmpty()) {
                        item(key = "coverage_kosong") {
                            Column(modifier = Modifier.padding(top = 16.dp)) {
                                when (sebabDaftarBarangKosong(state.stockLoading, state.stockError)) {
                                    // Permintaannya masih terbang — sesi ini belum
                                    // boleh dituduh apa pun.
                                    SebabDaftarBarangKosong.SEDANG_DIMUAT -> DaftarBarangMemuatNote()

                                    SebabDaftarBarangKosong.GAGAL_DIMUAT -> ExpressiveInlineError(
                                        // Sebab dari server ikut disebut kalau ada:
                                        // tanpa itu layanan yang mati (gateway
                                        // menjawab 502/503 dengan kalimatnya
                                        // sendiri) dan sinyal yang hilang melebur
                                        // jadi satu keluhan "sinyal jelek".
                                        message = pesanDaftarBarangGagal(state.stockError),
                                        onRetry = { viewModel.load(sessionId, paksaStock = true) }
                                    )

                                    // Sesi tanpa satu pun baris snapshot. Server
                                    // sekarang menolak melahirkannya (`snapshot
                                    // .is_empty()` di `create_opname_session`),
                                    // jadi ini hanya sesi lama — tapi ia TIDAK
                                    // bisa dikerjakan dan tak boleh terbaca
                                    // sebagai "belum dihitung". `cancel_opname`
                                    // memakai `authorize_kelola`, BUKAN
                                    // `authorize_owner`: selain pembuatnya,
                                    // admin stok mana pun boleh membatalkan bila
                                    // pembuatnya sudah nonaktif (jalan darurat
                                    // sesi yatim). Jalan kedua itu wajib ikut
                                    // disebut justru di sini — sesi bersnapshot
                                    // kosong hanya lahir dari masa lalu, dan
                                    // pembuatnya paling mungkin sudah resign.
                                    SebabDaftarBarangKosong.MEMANG_KOSONG -> IzinKurangNote(
                                        "Sesi ini tidak punya daftar barang sama sekali, jadi " +
                                            "tak ada yang bisa dihitung di sini. Minta " +
                                            (detail.createdByName?.takeIf { it.isNotBlank() }
                                                ?: "pembuat sesi ini") +
                                            " membatalkan sesi ini lalu membuat sesi baru. " +
                                            "Kalau akunnya sudah tidak aktif, admin stok yang " +
                                            "bisa membatalkannya."
                                    )
                                }
                            }
                        }
                    }

                    if (!completed && state.stock.isNotEmpty()) {
                        item(key = "coverage_header") {
                            Column(modifier = Modifier.padding(top = 16.dp, bottom = 6.dp)) {
                                Text(
                                    text = "Daftar Barang",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    StockFilterChip("Belum (${state.stock.size - unitsByCode.size})", stockFilter == StockFilter.BELUM) {
                                        stockFilter = StockFilter.BELUM
                                    }
                                    StockFilterChip("Sudah (${unitsByCode.size})", stockFilter == StockFilter.SUDAH) {
                                        stockFilter = StockFilter.SUDAH
                                    }
                                    StockFilterChip("Semua (${state.stock.size})", stockFilter == StockFilter.SEMUA) {
                                        stockFilter = StockFilter.SEMUA
                                    }
                                }
                            }
                        }
                        if (coverageList.isEmpty()) {
                            item(key = "coverage_empty") {
                                Text(
                                    text = "Tidak ada barang di kelompok ini",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 10.dp)
                                )
                            }
                        } else {
                            val kunciCoverage = kunciUnik(coverageList) { "coverage_${it.kodeBarang}" }
                            itemsIndexed(coverageList, key = { i, _ -> kunciCoverage.getOrElse(i) { "idx_coverage_$i" } }) { _, stockItem ->
                                StockSearchRow(
                                    item = stockItem,
                                    unitCount = unitsByCode[stockItem.kodeBarang.uppercase()]?.size ?: 0,
                                    onClick = if (state.canHitung) {
                                        {
                                            viewModel.selectItem(stockItem)
                                            scanSheetOpen = true
                                        }
                                    } else {
                                        {}
                                    },
                                    onNihil = if (
                                        state.canHitung &&
                                        (unitsByCode[stockItem.kodeBarang.uppercase()]?.size ?: 0) == 0
                                    ) {
                                        { konfirmasiNihil = stockItem }
                                    } else null,
                                    nihilBusy = state.nihilBusy,
                                )
                            }
                        }
                    }

                    if (state.selisihKondisi.isNotEmpty()) {
                        item(key = "selisih_kondisi") {
                            SelisihKondisiCard(state.selisihKondisi)
                        }
                    }

                    item(key = "items_header") {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 6.dp)
                        ) {
                            Text(
                                text = if (completed) "Hasil Opname (${detail.items.size})" else "Unit Terscan (${state.units.size})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            if (state.canPropose) {
                                TextButton(onClick = viewModel::openRequests) {
                                    Text("Usulan SN", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                    if (!completed) {
                        // Draft: blind count — hanya serial yang benar-benar discan petugas.
                        if (state.units.isEmpty()) {
                            item(key = "items_empty") {
                                Text(
                                    text = "Belum ada unit yang discan",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            val kunciUnit = kunciUnik(state.units) { "unit_${it.serialNumber}" }
                            itemsIndexed(state.units, key = { i, _ -> kunciUnit.getOrElse(i) { "idx_unit_$i" } }) { _, unit ->
                                ScannedUnitRow(
                                    unit = unit,
                                    // Pemilik sesi boleh menghapus unit siapa pun;
                                    // petugas hanya miliknya sendiri. Cermin
                                    // `delete_unit(.., only_counted_by)` di server —
                                    // tanpa ini tombolnya muncul lalu dijawab 403.
                                    onDelete = if (
                                        state.canManage ||
                                        (state.canHitung &&
                                            unit.serialNumber.uppercase() in state.serialMilikSaya)
                                    ) {
                                        { viewModel.deleteUnit(unit) }
                                    } else null,
                                    onUsulkan = if (bolehUsulkanSn(unit, state.canPropose)) {
                                        { viewModel.startProposal(unit) }
                                    } else null
                                )
                            }
                        }
                    } else {
                        items(detail.items, key = { it.id }) { item ->
                            CompletedItemRow(item)
                        }
                    }

                    if (state.canManage) {
                        item(key = "actions") {
                            Column(modifier = Modifier.padding(top = 20.dp)) {
                                ExpressiveFilledButton(
                                    onClick = { confirmAction = "complete" },
                                    enabled = !state.isMutatingStatus && state.units.isNotEmpty(),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    if (state.isMutatingStatus) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.onPrimary
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Mengirim hitungan...")
                                    } else {
                                        Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Selesaikan Sesi")
                                    }
                                }
                                TextButton(
                                    onClick = { confirmAction = "cancel" },
                                    enabled = !state.isMutatingStatus,
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                                ) {
                                    Text("Batalkan Sesi", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }

                    if (state.canDelete) {
                        item(key = "delete_action") {
                            TextButton(
                                onClick = { confirmAction = "delete" },
                                enabled = !state.isMutatingStatus,
                                modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Hapus Sesi", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
        }
    }

    val selected = state.selectedItem
    if (scanSheetOpen && selected != null) {
        ScanUnitSheet(
            item = selected,
            units = unitsByCode[selected.kodeBarang.uppercase()].orEmpty(),
            isSaving = state.isSaving,
            saveError = state.saveError,
            scanMessage = state.scanMessage,
            onDismiss = {
                scanSheetOpen = false
                viewModel.selectItem(null)
                search = ""
            },
            onScan = { serial, kondisi, keterangan -> viewModel.scan(serial, kondisi, keterangan) },
            onManual = { serial, kondisi, keterangan ->
                viewModel.startManualUnit(serial, kondisi, keterangan)
            },
            onDelete = { unit -> viewModel.deleteUnit(unit) },
            canPropose = state.canPropose,
            onUsulkan = { unit -> viewModel.startProposal(unit) },
            canVerifikasiSn = state.canVerifikasiSn,
            canTetapkanSn = state.canTetapkanSn
        )
    }

    state.manualDraft?.let { draft ->
        ManualUnitDialog(
            draft = draft,
            onCapture = { file, kind -> viewModel.uploadManualPhoto(file, kind) },
            onSubmit = viewModel::submitManualUnit,
            onDismiss = viewModel::cancelManualUnit
        )
    }

    if (state.requestsOpen) {
        SerialRequestsSheet(
            loading = state.requestsLoading,
            error = state.requestsError,
            requests = state.requests,
            onDismiss = viewModel::closeRequests
        )
    }

    state.proposal?.let { draft ->
        SerialProposalDialog(
            draft = draft,
            onCatatanChange = viewModel::onProposalCatatan,
            onCapture = { file, kind -> viewModel.uploadProposalPhoto(file, kind) },
            onSubmit = viewModel::submitProposal,
            onDismiss = viewModel::cancelProposal
        )
    }

    state.proposalMessage?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::clearProposalMessage,
            title = { Text("Usulan terkirim") },
            text = { Text("$message. Serial baru bisa dipakai setelah disetujui.") },
            confirmButton = {
                TextButton(onClick = viewModel::clearProposalMessage) { Text("Tutup") }
            }
        )
    }

    konfirmasiNihil?.let { barang ->
        AlertDialog(
            onDismissRequest = { konfirmasiNihil = null },
            title = { Text("Nyatakan barang ini nihil?") },
            text = {
                Text(
                    "${barang.namaBarang ?: barang.kodeBarang} akan tercatat SELISIH PENUH — " +
                        "barang yang menurut sistem ada tapi tak ditemukan di gudang. " +
                        "Kalau nanti ketemu, cukup scan serialnya dan tanda ini batal sendiri."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.tandaiNihil(listOf(barang.kodeBarang))
                    konfirmasiNihil = null
                }) {
                    Text("Ya, nihil", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { konfirmasiNihil = null }) { Text("Batal") }
            }
        )
    }

    confirmAction?.let { action ->
        val isComplete = action == "complete"
        val isDelete = action == "delete"
        AlertDialog(
            onDismissRequest = { confirmAction = null },
            title = {
                Text(
                    when {
                        isComplete -> "Selesaikan sesi opname?"
                        isDelete -> "Hapus sesi opname?"
                        else -> "Batalkan sesi opname?"
                    }
                )
            },
            text = {
                Text(
                    when {
                        isComplete -> "Sisa antrean (${state.units.count { it.syncedAtMillis == null }} unit) akan dikirim dulu, lalu sesi ditutup. Setelah selesai, hitungan tidak bisa diubah lagi dan selisih vs stok sistem dihitung."
                        isDelete -> "Sesi dan seluruh data hitungannya akan dihapus PERMANEN. Tindakan ini tidak bisa dibatalkan."
                        else -> "Sesi yang dibatalkan tidak bisa dilanjutkan lagi. Unit yang tersimpan di HP juga akan dihapus."
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmAction = null
                    when {
                        isComplete -> viewModel.complete()
                        isDelete -> viewModel.deleteSession(onDeleted = onBack)
                        else -> viewModel.cancel()
                    }
                }) {
                    Text(
                        when {
                            isComplete -> "Kirim & Selesaikan"
                            isDelete -> "Ya, hapus"
                            else -> "Ya, batalkan"
                        },
                        color = if (isComplete) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmAction = null }) { Text("Kembali") }
            }
        )
    }
}

private fun sharePdf(context: Context, uri: android.net.Uri) {
    // ClipData is what actually propagates the read grant through the chooser on several OEMs
    // (Vivo included) — with only EXTRA_STREAM some receivers can't read the Uri and report the
    // file as corrupt even though the PDF itself is valid.
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = android.content.ClipData.newRawUri("laporan_opname", uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(send, "Buka / print laporan opname").apply {
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(chooser)
}

/**
 * Kalimat sebab untuk jalur yang tertutup izin penunjukan petugas (migrasi 212).
 *
 * Nada sengaja INFORMATIF, bukan merah-error: yang bersangkutan tidak melakukan
 * kesalahan apa pun, ia cuma belum ditunjuk — dan warna error di layar hitung
 * fisik membuat orang mengira hasil scan sebelumnya ikut gagal.
 */
@Composable
private fun IzinKurangNote(message: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
        )
    }
}

/**
 * Keadaan "daftar barangnya sedang diambil" — keadaan ketiga yang dulu tak ada
 * sama sekali di layar ini.
 *
 * Sengaja BUKAN skeleton atau spinner telanjang: yang perlu dibantah di sini
 * bukan kekosongan visual melainkan sebuah kesimpulan ("sesi ini kosong"), dan
 * kesimpulan cuma bisa dibantah kalimat. Bentuknya dibuat sama dengan
 * [IzinKurangNote] supaya ketiga keadaan menempati slot yang sama dan tak ada
 * yang terlihat seperti kerusakan.
 */
@Composable
private fun DaftarBarangMemuatNote() {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Daftar barang sesi ini masih diambil dari server. " +
                    "Tunggu sebentar — belum tentu sesinya kosong.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun OpnameStat(label: String, value: String, highlight: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (highlight) Color(0xFFB5670C) else MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun StockFilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun StockSearchRow(
    item: OpnameStockItemDto,
    unitCount: Int,
    onClick: () -> Unit,
    /** `null` = tombol nihil tak ditampilkan (bukan penghitung, atau barangnya
     *  sudah punya unit — menandai nihil barang yang sudah dihitung akan
     *  membuang hasil kerja orang lain, dan server pun menolaknya). */
    onNihil: (() -> Unit)? = null,
    nihilBusy: Boolean = false,
) {
    ClayCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.namaBarang ?: item.kodeBarang,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = listOfNotNull(item.kodeBarang, item.merk?.takeIf { it.isNotBlank() }).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (unitCount > 0) {
                Surface(color = Color(0xFF12B76A).copy(alpha = 0.14f), shape = RoundedCornerShape(8.dp)) {
                    Text(
                        text = "✓ $unitCount",
                        color = Color(0xFF12B76A),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            } else if (onNihil != null) {
                // "Nihil" = sudah dicari, tak ada satu pun. Tercatat selisih
                // PENUH, jadi ia temuan — bukan "lewati saja".
                TextButton(onClick = onNihil, enabled = !nihilBusy) {
                    Text("Nihil", style = MaterialTheme.typography.labelMedium)
                }
            } else {
                Icon(
                    Icons.Rounded.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * Baris unit terscan: serial + kondisi + penanda temuan/antrean. Sengaja tanpa stok
 * sistem — penghitung tak boleh melihat angka pembanding (blind count).
 */
@Composable
private fun ScannedUnitRow(
    unit: OpnameUnitEntity,
    onDelete: (() -> Unit)?,
    onUsulkan: (() -> Unit)? = null
) {
    ClayCard(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = unit.serialNumber,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = buildString {
                        append(unit.kodeBarang)
                        append(" \u00b7 ")
                        append(if (unit.kondisi == "layak") "layak" else "tidak layak")
                        unit.temuan?.let {
                            append(" \u00b7 ")
                            append(temuanLabel(it))
                        }
                        if (unit.syncedAtMillis == null) append(" \u00b7 menunggu kirim")
                        when (unit.validationStatus) {
                            "pending" -> append(" \u00b7 MANUAL, menunggu validasi admin stok")
                            "rejected" -> {
                                append(" \u00b7 MANUAL DITOLAK")
                                unit.rejectReason?.let { append(": $it") }
                                append(" \u2014 scan/kirim ulang")
                            }
                            "approved" -> append(" \u00b7 manual disetujui")
                        }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (
                        unit.temuan != null || unit.syncedAtMillis == null ||
                        unit.validationStatus == "pending" || unit.validationStatus == "rejected"
                    ) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                if (onUsulkan != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    TextButton(
                        onClick = onUsulkan,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("Usulkan SN", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            if (onDelete != null) {
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Rounded.Delete,
                        contentDescription = "Hapus unit ${unit.serialNumber}",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

/**
 * Status usulan SN cabang ini. Yang DITOLAK adalah alasan utama panel ini ada:
 * usulan yang diterima akhirnya terlihat sendiri (temuan "belum terdaftar"
 * berhenti muncul), sedangkan penolakan tak pernah memberi tanda apa pun.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SerialRequestsSheet(
    loading: Boolean,
    error: String?,
    requests: List<SerialRequestDto>,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Text("Usulan Pendaftaran SN", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            when {
                loading -> repeat(3) { SkeletonCard(modifier = Modifier.padding(vertical = 4.dp)) }
                error != null -> ExpressiveInlineError(message = error)
                requests.isEmpty() -> Text(
                    text = "Belum ada usulan dari cabang ini.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                else -> requests.take(50).forEach { req ->
                    ClayCard(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = req.serialNumber,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                // Umur antrian ikut di baris status, bukan di
                                // kaki kartu: yang dicari pengusul saat membuka
                                // panel ini adalah "usulanku sudah berapa lama
                                // diam", dan itu harus terbaca bersama
                                // statusnya. Vonisnya milik server — lihat
                                // `labelUmurUsulan`.
                                labelUmurUsulan(req.status, req.umurAntrianJam, req.mandek)?.let { umur ->
                                    Text(
                                        text = umur,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (req.mandek) {
                                            Color(0xFFB5670C)
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                                Text(
                                    text = statusUsulanLabel(req.status),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = when (req.status) {
                                        "approved" -> Color(0xFF12B76A)
                                        "rejected" -> MaterialTheme.colorScheme.error
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                            Text(
                                text = buildString {
                                    append(req.kodeBarang)
                                    req.namaBarang?.takeIf { it.isNotBlank() }?.let { append(" · $it") }
                                    // Alasan tolak dibawa ke muka: itu satu-satunya
                                    // petunjuk apa yang harus diperbaiki sebelum
                                    // mengusulkan ulang.
                                    req.alasanTolak?.takeIf { it.isNotBlank() }?.let { append("\nDitolak: $it") }
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            if (requests.size > 50) {
                Text(
                    text = "...dan ${requests.size - 50} usulan lainnya",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun statusUsulanLabel(status: String): String = when (status) {
    "pending" -> "MENUNGGU"
    "approved" -> "DISETUJUI"
    "rejected" -> "DITOLAK"
    else -> status.uppercase()
}

/**
 * Unit ketik-manual — dua foto wajib (label rusak dari dekat + barang utuh),
 * lalu menunggu validasi admin-stok. Reuse `SerialPhotoField` milik usulan SN:
 * mekanik fotonya identik, yang beda cuma tujuan kirimnya.
 */
@Composable
private fun ManualUnitDialog(
    draft: ManualUnitDraft,
    onCapture: (java.io.File, SerialPhotoKind) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!draft.busy) onDismiss() },
        title = { Text("Input manual + foto bukti") },
        text = {
            Column {
                Text(
                    text = "${draft.serialNumber}\n${draft.kodeBarang}${draft.namaBarang?.let { " · $it" } ?: ""}" +
                        "\nKondisi: ${kondisiLabel(draft.kondisi).uppercase()}" +
                        (draft.keterangan?.let { "\nKeterangan: $it" } ?: ""),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Serial diketik tangan, jadi unit ini baru dihitung setelah admin stok memeriksa fotonya. Sesi tidak bisa ditutup selama masih menunggu.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                SerialPhotoField(
                    label = "Foto label/serial rusak",
                    hint = "Dekat — tunjukkan label yang tak bisa discan",
                    url = draft.fotoSnUrl,
                    enabled = !draft.busy,
                    kind = SerialPhotoKind.SERIAL,
                    onCapture = onCapture
                )
                Spacer(modifier = Modifier.height(8.dp))
                SerialPhotoField(
                    label = "Foto barang",
                    hint = "Seluruh unit terlihat",
                    url = draft.fotoBarangUrl,
                    enabled = !draft.busy,
                    kind = SerialPhotoKind.BARANG,
                    onCapture = onCapture
                )
                draft.error?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    ExpressiveInlineError(message = it)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onSubmit, enabled = draft.isValid && !draft.busy) {
                Text(if (draft.submitting) "Mengirim…" else "Simpan unit")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !draft.busy) { Text("Batal") }
        }
    )
}

/**
 * Usulan pendaftaran SN — dua foto wajib, keputusan tetap di admin-stok.
 *
 * Teks jarak ditulis sebagai instruksi, BUKAN divalidasi: "minimal 1 meter" tak
 * bisa diukur program dari sebuah JPEG, dan pura-pura bisa lebih berbahaya
 * daripada tidak ada — penegaknya mata admin-stok saat menyetujui.
 */
@Composable
private fun SerialProposalDialog(
    draft: SerialProposalDraft,
    onCatatanChange: (String) -> Unit,
    onCapture: (java.io.File, SerialPhotoKind) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!draft.busy) onDismiss() },
        title = { Text("Usulkan pendaftaran SN") },
        text = {
            Column {
                Text(
                    text = "${draft.serialNumber}\n${draft.kodeBarang}${draft.namaBarang?.let { " · $it" } ?: ""}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(10.dp))
                SerialPhotoField(
                    label = "Foto nomor seri",
                    hint = "Dekat, nomornya terbaca jelas",
                    url = draft.fotoSnUrl,
                    enabled = !draft.busy,
                    kind = SerialPhotoKind.SERIAL,
                    onCapture = onCapture
                )
                Spacer(modifier = Modifier.height(8.dp))
                SerialPhotoField(
                    label = "Foto barang",
                    hint = "Ambil dari jarak ±1 meter, seluruh unit terlihat",
                    url = draft.fotoBarangUrl,
                    enabled = !draft.busy,
                    kind = SerialPhotoKind.BARANG,
                    onCapture = onCapture
                )
                Spacer(modifier = Modifier.height(10.dp))
                ExpressiveTextField(
                    value = draft.catatan,
                    onValueChange = onCatatanChange,
                    label = "Catatan (opsional)",
                    modifier = Modifier.fillMaxWidth()
                )
                draft.error?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    ExpressiveInlineError(message = it)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onSubmit, enabled = draft.isValid && !draft.busy) {
                Text(if (draft.submitting) "Mengirim…" else "Kirim usulan")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !draft.busy) { Text("Batal") }
        }
    )
}

/** Satu slot foto: kamera → watermark+unggah (ViewModel) → URL tersimpan di draft. */
@Composable
private fun SerialPhotoField(
    label: String,
    hint: String,
    url: String?,
    enabled: Boolean,
    kind: SerialPhotoKind,
    onCapture: (java.io.File, SerialPhotoKind) -> Unit
) {
    val context = LocalContext.current
    // Satu file cache per slot: pengambilan ulang menimpa isinya, jadi tak ada
    // sampah menumpuk di cache HP lapangan.
    val file = remember(kind) {
        java.io.File(context.cacheDir, "serial/usulan_${kind.name.lowercase()}.jpg")
            .apply { parentFile?.mkdirs() }
    }
    val uri = remember(file) {
        androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }
    val cam = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.TakePicture()
    ) { ok -> if (ok) onCapture(file, kind) }

    Surface(
        onClick = { if (enabled) cam.launch(uri) },
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (url.isNullOrBlank()) label else "$label ✓",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = if (url.isNullOrBlank()) hint else "Tersimpan — ketuk untuk ambil ulang",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.Rounded.AddAPhoto,
                contentDescription = null,
                tint = if (url.isNullOrBlank()) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.primary
                },
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/** Completed row: full reconciliation — fisik vs sistem, selisih units, and selisih value. */
@Composable
private fun CompletedItemRow(item: OpnameItemDto) {
    val selisihColor = when {
        item.selisih == 0L -> Color(0xFF12B76A)
        item.selisih > 0 -> Color(0xFF0086C9)
        else -> Color(0xFFF04438)
    }
    val nilaiSelisih = (item.harga ?: 0.0) * item.selisih
    ClayCard(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.namaBarang ?: item.kodeBarang,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${item.kodeBarang} · fisik ${item.stokFisikLayak + item.stokFisikTidakLayak} · sistem ${item.stokSistem}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Surface(color = selisihColor.copy(alpha = 0.14f), shape = RoundedCornerShape(8.dp)) {
                        Text(
                            text = if (item.selisih == 0L) "Sesuai" else if (item.selisih > 0) "+${item.selisih}" else "${item.selisih}",
                            color = selisihColor,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    if (item.selisih != 0L && item.harga != null) {
                        Text(
                            text = formatRupiah(nilaiSelisih),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = selisihColor,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }
            if (!item.keterangan.isNullOrBlank()) {
                Text(
                    text = item.keterangan,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

/**
 * Panel scan: satu barang, banyak unit. Tombol scan memakai Google code scanner yang
 * sudah dipakai PDI/SPK (tanpa izin kamera). Ketik manual tetap tersedia untuk barcode
 * yang rusak — tapi TIDAK langsung tersimpan: [onManual] membuka dialog dua foto bukti
 * dan unitnya menunggu validasi admin-stok (ketikan tangan tak membuktikan barangnya ada).
 */
/**
 * Unit yang temuan lapangannya BEDA dari kondisi yang ditetapkan admin-stok.
 *
 * Inti dari keputusan menyimpan kondisi di dua tempat (registry + sesi): tanpa
 * daftar ini kedua angka cuma duduk berdampingan tanpa ada yang membacanya.
 * Benderanya datang dari server (`kondisiSelisih`) dan TIDAK dihitung ulang di
 * sini — unit yang kondisinya belum pernah ditetapkan bukan selisih, dan salah
 * memperlakukannya akan menandai ribuan baris impor Excel sebagai temuan palsu.
 */
@Composable
private fun SelisihKondisiCard(items: List<OpnameUnitDto>) {
    ClayCard(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "${items.size} unit beda dari kondisi yang ditetapkan admin stok",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error
            )
            Text(
                text = "Bukan penolakan — cocokkan lagi barangnya, lalu minta admin stok memperbarui kondisinya kalau memang sudah berubah.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
            items.forEach { unit ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    Text(
                        text = unit.serialNumber,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${kondisiLabel(unit.kondisiRegistry.orEmpty())} \u2192 ${kondisiLabel(unit.kondisi)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ScanUnitSheet(
    item: OpnameStockItemDto,
    units: List<OpnameUnitEntity>,
    isSaving: Boolean,
    saveError: String?,
    scanMessage: String?,
    onDismiss: () -> Unit,
    onScan: (serial: String, kondisi: String, keterangan: String?) -> Unit,
    /** `true` = dialog dua-foto terbuka; `false` = ditolak, ketikan dipertahankan. */
    onManual: (serial: String, kondisi: String, keterangan: String?) -> Boolean,
    onDelete: (OpnameUnitEntity) -> Unit,
    canPropose: Boolean,
    onUsulkan: ((OpnameUnitEntity) -> Unit)?,
    /** Izin `verifikasi_sn` (migrasi 212) — gerbang panel scan. */
    canVerifikasiSn: Boolean,
    /** Izin `tetapkan_sn` (migrasi 212) — gerbang alur ketik-manual. */
    canTetapkanSn: Boolean
) {
    var manual by remember { mutableStateOf("") }
    // Kondisi & keterangan BERTAHAN antar scan dalam satu sheet: petugas yang
    // menemukan satu rak rusak menandai berturut-turut, dan mereset ke "layak"
    // tiap unit membuat dia diam-diam mencatat sisanya sebagai layak.
    var kondisi by remember { mutableStateOf(KONDISI_LAYAK) }
    var keterangan by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // Ketik manual dipakai justru saat barcode rusak — tanpa ini keyboard
                // menutupi kolom serial dan tombolnya.
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = item.namaBarang ?: item.kodeBarang,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = listOfNotNull(item.kodeBarang, item.merk?.takeIf { it.isNotBlank() })
                    .joinToString(" \u00b7 "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                ExpressiveTextField(
                    value = manual,
                    onValueChange = { manual = it },
                    placeholder = "Ketik serial number...",
                    enabled = canTetapkanSn,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                // Tombol scan hanya untuk yang ditunjuk memverifikasi SN.
                // Kalimat sebabnya menggantikan tombolnya — bukan ruang kosong.
                if (canVerifikasiSn) {
                    BarcodeScanButton(contentDescription = "Scan serial unit") { hasil ->
                        onScan(hasil, kondisi, keterangan.takeIf { it.isNotBlank() })
                    }
                }
            }
            if (!canVerifikasiSn) {
                Spacer(modifier = Modifier.height(8.dp))
                IzinKurangNote(ALASAN_TAK_BOLEH_SCAN)
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Kondisi unit berikutnya",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KONDISI_PILIHAN.forEach { nilai ->
                    FilterChip(
                        selected = kondisi == nilai,
                        onClick = { kondisi = nilai },
                        label = { Text(kondisiLabel(nilai)) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            ExpressiveTextField(
                value = keterangan,
                onValueChange = { keterangan = it },
                placeholder = "Keterangan kondisi (opsional)",
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))
            ExpressiveFilledButton(
                onClick = {
                    // Dikosongkan hanya bila dialognya benar-benar terbuka — kalau serialnya
                    // ditolak, ketikan panjang petugas tak boleh ikut lenyap.
                    if (onManual(manual, kondisi, keterangan.takeIf { it.isNotBlank() })) manual = ""
                },
                enabled = !isSaving && manual.isNotBlank() && canTetapkanSn,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Menyimpan...")
                } else {
                    Text("Simpan Manual + Foto")
                }
            }
            // Tombolnya SENGAJA tetap terlihat (cuma mati) dan sebabnya ditulis
            // di bawahnya: tombol yang lenyap tanpa penjelasan terbaca sebagai
            // aplikasi rusak, dan orangnya melapor ke tempat yang salah.
            if (!canTetapkanSn) {
                Spacer(modifier = Modifier.height(6.dp))
                IzinKurangNote(ALASAN_TAK_BOLEH_MANUAL)
            } else {
                Text(
                    text = "Serial yang diketik wajib 2 foto bukti dan menunggu validasi admin stok — pakai tombol scan bila barcode masih terbaca.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            if (saveError != null) {
                Spacer(modifier = Modifier.height(8.dp))
                ExpressiveInlineError(message = saveError)
            } else if (scanMessage != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = scanMessage,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "${units.size} unit terscan untuk barang ini",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            units.take(30).forEach { unit ->
                ScannedUnitRow(
                    unit = unit,
                    onDelete = { onDelete(unit) },
                    // Usulan dipicu justru di sini: petugas melihat "belum terdaftar"
                    // sesaat setelah scan, sambil barangnya masih di depan mata.
                    onUsulkan = onUsulkan?.takeIf { bolehUsulkanSn(unit, canPropose) }
                        ?.let { aksi -> { aksi(unit) } }
                )
            }
            if (units.size > 30) {
                Text(
                    text = "...dan ${units.size - 30} unit lainnya",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

