package com.krisoft.tridjayaelektronik.ui.deliveryflow

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Discount
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.krisoft.tridjayaelektronik.data.model.formatWaktuId
import com.krisoft.tridjayaelektronik.ui.theme.ClayCard
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveEmptyState
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveErrorState
import com.krisoft.tridjayaelektronik.ui.theme.ScrollableCenter
import com.krisoft.tridjayaelektronik.ui.theme.TridjayaCollapsibleHeader
import com.krisoft.tridjayaelektronik.ui.theme.TridjayaPullRefresh

private val STATUS_TABS: List<Pair<String?, String>> = listOf(
    null to "Semua",
    "pending" to "Menunggu",
    "approved" to "Disetujui",
    "rejected" to "Ditolak",
    "dilepas" to "Dilepas",
)

@Composable
fun DiskonHistoryScreen(
    onBack: () -> Unit,
    onDetailSpk: (String) -> Unit,
    viewModel: DeliveryFlowViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var periode by remember { mutableStateOf(PeriodeSpk.SEMUA) }
    var status by remember { mutableStateOf<String?>(null) }
    val rentang = rentangPeriode(periode)
    LaunchedEffect(status, rentang) { viewModel.loadDiscounts(status, rentang.dari, rentang.sampai) }
    val muatUlang = { viewModel.loadDiscounts(status, rentang.dari, rentang.sampai) }

    TridjayaCollapsibleHeader(title = "Riwayat Diskon", onBack = onBack) { contentModifier ->
        val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        TridjayaPullRefresh(
            isRefreshing = state.loading && state.discounts.isNotEmpty(),
            onRefresh = muatUlang,
            modifier = contentModifier,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Status chips — read-only, tanpa tombol keputusan.
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    STATUS_TABS.forEach { (v, label) ->
                        val aktif = v == status
                        FilterChip(
                            selected = aktif,
                            onClick = { status = v },
                            label = { Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = if (aktif) FontWeight.Bold else FontWeight.Medium) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                        )
                    }
                }
                PeriodeFilterRow(dipilih = periode, onPilih = { periode = it })
                if (!(state.loading && state.discounts.isEmpty())) {
                    val ditampilkan = state.discounts.size
                    Text(
                        if (state.diskonTotal > ditampilkan)
                            "Menampilkan $ditampilkan dari ${state.diskonTotal} pengajuan \u00b7 ${periode.keterangan}"
                        else "Menampilkan $ditampilkan pengajuan \u00b7 ${periode.keterangan}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    when {
                        state.loading && state.discounts.isEmpty() ->
                            Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                        state.error != null && state.discounts.isEmpty() ->
                            ScrollableCenter { ExpressiveErrorState(message = state.error ?: "Gagal memuat", onRetry = muatUlang) }
                        state.discounts.isEmpty() ->
                            ScrollableCenter {
                                ExpressiveEmptyState(
                                    icon = { Icon(Icons.Rounded.Discount, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp)) },
                                    title = "Belum ada riwayat",
                                    subtitle = "Tidak ada pengajuan pada ${periode.keterangan}" + (status?.let { " dengan status $it" } ?: "") + ".",
                                )
                            }
                        else -> LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp + navBottom),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            val grup = state.discounts.groupBy { it.spkBatchKode }.entries.toList()
                            items(grup, key = { it.key }) { (kode, pengajuan) ->
                                val urut = urutPengajuanSpk(pengajuan)
                                HistorySpkCard(
                                    kode = kode,
                                    pengajuan = urut,
                                    onDetail = { onDetailSpk(kode) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistorySpkCard(
    kode: String,
    pengajuan: List<com.krisoft.tridjayaelektronik.data.model.DiscountRequestDto>,
    onDetail: () -> Unit,
) {
    val total = totalPotonganSpk(pengajuan)
    val konsumen = pengajuan.firstOrNull()?.jobSummary?.customerName ?: "-"
    val pengaju = nilaiSeragam(pengajuan) { it.requestedByName?.trim()?.ifBlank { null } }
    val tanggal = nilaiSeragam(pengajuan) { formatWaktuId(it.createdAt).substringBefore(' ').takeIf { t -> t != "-" } }
    val kemajuan = kemajuanSpk(pengajuan)
    ClayCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(kode, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Surface(
                    color = if (kemajuan.semuaTuntas) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(50),
                ) {
                    Text(kemajuan.teks, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                }
            }
            Text(konsumen, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            listOfNotNull(pengaju?.let { "Diajukan $it" }, tanggal).takeIf { it.isNotEmpty() }?.let {
                Text(it.joinToString(" \u00b7 "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            pengajuan.forEach { d ->
                Spacer(Modifier.height(8.dp))
                HistoryBaris(d = d)
            }
            Spacer(Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Total potongan SPK", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Text("Rp" + ribuan(total), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = Color(0xFFB5670C))
            }
            TextButton(onClick = onDetail, modifier = Modifier.align(Alignment.Start)) {
                Text("Lihat detail SPK", style = MaterialTheme.typography.labelMedium)
                Icon(Icons.Rounded.ChevronRight, contentDescription = null, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun HistoryBaris(d: com.krisoft.tridjayaelektronik.data.model.DiscountRequestDto) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Top) {
            Text(d.baris?.toString() ?: "\u00b7", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Text(d.jobSummary?.namaBarang ?: d.jobSummary?.kodeBarang ?: "-", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(ringkasHarga(d), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (d.reason.isNotBlank()) {
                    Text("\u201c${d.reason}\u201d", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                // Jejak keputusan — approver, waktu, catatan (yang membedakan history dari antrian).
                val jejak = buildList {
                    d.decidedByName?.takeIf { it.isNotBlank() }?.let { add(it) }
                    d.decidedAt?.takeIf { it.isNotBlank() }?.let { add(formatWaktuId(it)) }
                }.joinToString(" \u00b7 ")
                if (jejak.isNotEmpty()) {
                    Text(jejak, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                d.decisionNote?.takeIf { it.isNotBlank() }?.let { note ->
                    Text("Catatan: $note", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text("\u2212Rp" + ribuan(potonganPengajuan(d)), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color(0xFFB5670C))
                Spacer(Modifier.height(2.dp))
                HistoryStatusChip(d.status)
            }
        }
        if (d.status == "rejected") {
            Text("Menunggu sales: revisi diskon atau lanjut tanpa diskon.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(start = 18.dp, top = 2.dp))
        }
    }
}

@Composable
private fun HistoryStatusChip(status: String) {
    val warna = when {
        barisTuntas(status) -> MaterialTheme.colorScheme.primary
        status == "rejected" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(color = warna.copy(alpha = 0.12f), shape = RoundedCornerShape(50)) {
        Text(labelStatusBaris(status), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = warna, maxLines = 1, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
    }
}
