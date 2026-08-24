package com.krisoft.tridjayaelektronik.ui.opname

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.FactCheck
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.krisoft.tridjayaelektronik.data.model.OpnameSessionDto
import com.krisoft.tridjayaelektronik.ui.theme.ClayCard
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveEmptyState
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveErrorState
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveFilledButton
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveFormError
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveTextField
import com.krisoft.tridjayaelektronik.ui.theme.ScrollableCenter
import com.krisoft.tridjayaelektronik.ui.theme.SkeletonCard
import com.krisoft.tridjayaelektronik.ui.theme.TridjayaCollapsibleHeader
import com.krisoft.tridjayaelektronik.ui.theme.TridjayaPullRefresh

internal fun opnameStatusLabel(status: String): String = when (status.lowercase()) {
    "draft" -> "Draft"
    "completed" -> "Selesai"
    "cancelled" -> "Batal"
    else -> status
}

internal fun opnameStatusColor(status: String): Color = when (status.lowercase()) {
    "draft" -> Color(0xFFB5670C)
    "completed" -> Color(0xFF12B76A)
    "cancelled" -> Color(0xFFF04438)
    else -> Color(0xFF667085)
}

// Lookup bulan murni (tanpa SimpleDateFormat) — fungsi ini dipanggil per baris saat scroll,
// dan konstruksi SimpleDateFormat per panggilan mahal (parsing pola + simbol locale).
private val OPNAME_MONTHS =
    arrayOf("Jan", "Feb", "Mar", "Apr", "Mei", "Jun", "Jul", "Agu", "Sep", "Okt", "Nov", "Des")

internal fun formatOpnameDate(iso: String): String {
    if (iso.length < 10) return iso
    val datePart = iso.take(10)
    val parts = datePart.split("-")
    if (parts.size != 3) return datePart
    val month = parts[1].toIntOrNull()?.takeIf { it in 1..12 } ?: return datePart
    val day = parts[2].toIntOrNull() ?: return datePart
    return "$day ${OPNAME_MONTHS[month - 1]} ${parts[0]}"
}

/**
 * Stock opname sessions — list, status filter (scrolls with the list), and a create sheet for
 * roles the backend allows (context.canCreate). Selecting a session opens [OpnameDetailScreen]
 * via the same state-swap pattern the Indent screens use.
 */
@Composable
fun OpnameListScreen(
    onBack: () -> Unit,
    viewModel: OpnameListViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var selectedId by remember { mutableStateOf<String?>(null) }
    var showCreate by remember { mutableStateOf(false) }
    var statusFilter by remember { mutableStateOf<String?>(null) }

    selectedId?.let { id ->
        OpnameDetailScreen(
            sessionId = id,
            onBack = {
                selectedId = null
                viewModel.load()
            }
        )
        return
    }

    val filteredItems = remember(state.items, statusFilter) {
        state.items.filter { statusFilter == null || it.status.equals(statusFilter, ignoreCase = true) }
    }

    TridjayaCollapsibleHeader(title = "Stok Opname", onBack = onBack) { contentModifier ->
        Box(modifier = contentModifier.fillMaxSize()) {
            TridjayaPullRefresh(
                isRefreshing = state.isLoading && state.items.isNotEmpty(),
                onRefresh = viewModel::load
            ) {
                when {
                    state.isLoading && state.items.isEmpty() -> {
                        Column(modifier = Modifier.padding(top = 8.dp)) {
                            repeat(6) {
                                SkeletonCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                            }
                        }
                    }
                    state.errorMessage != null && state.items.isEmpty() -> {
                        ScrollableCenter {
                            ExpressiveErrorState(
                                message = state.errorMessage ?: "Tidak bisa memuat daftar opname.",
                                onRetry = viewModel::load
                            )
                        }
                    }
                    state.items.isEmpty() -> {
                        ScrollableCenter {
                            ExpressiveEmptyState(
                                icon = { Icon(Icons.Rounded.FactCheck, contentDescription = null) },
                                title = "Belum ada sesi opname",
                                // Kalimatnya menyebut CABANG + siapa yang bisa
                                // membuka sesi — lihat `subtitleDaftarKosong`.
                                subtitle = subtitleDaftarKosong(state.context)
                            )
                        }
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(top = 4.dp, bottom = 100.dp)
                        ) {
                            item(key = "status_filter") {
                                OpnameStatusChips(
                                    items = state.items,
                                    selected = statusFilter,
                                    onSelect = { statusFilter = if (statusFilter == it) null else it }
                                )
                            }
                            if (filteredItems.isEmpty()) {
                                item(key = "no_match") {
                                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                                        ExpressiveEmptyState(
                                            icon = { Icon(Icons.Rounded.FactCheck, contentDescription = null) },
                                            title = "Tidak ditemukan",
                                            subtitle = "Tidak ada sesi dengan status itu"
                                        )
                                    }
                                }
                            } else {
                                items(filteredItems, key = { it.id }) { session ->
                                    OpnameSessionRow(session, onClick = { selectedId = session.id })
                                }
                            }
                        }
                    }
                }
            }

            if (state.context?.canCreate == true) {
                ExtendedFloatingActionButton(
                    onClick = { showCreate = true },
                    icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                    text = { Text("Sesi Baru") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .navigationBarsPadding()
                        .padding(end = 16.dp, bottom = 16.dp)
                )
            }
        }
    }

    if (showCreate) {
        OpnameCreateSheet(
            state = state,
            todayIso = viewModel.todayIso(),
            onDismiss = {
                showCreate = false
                viewModel.clearCreateError()
            },
            onSubmit = { dealer, periode, jenis, catatan, mulai, selesai ->
                viewModel.create(dealer, periode, jenis, catatan, mulai, selesai) { detail ->
                    showCreate = false
                    selectedId = detail.id
                }
            }
        )
    }
}

@Composable
private fun OpnameStatusChips(
    items: List<OpnameSessionDto>,
    selected: String?,
    onSelect: (String) -> Unit
) {
    val counts = remember(items) { items.groupingBy { it.status.lowercase() }.eachCount() }
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
    ) {
        item {
            FilterChip(
                selected = selected == null,
                onClick = { selected?.let(onSelect) },
                label = { Text("Semua (${items.size})") },
                shape = RoundedCornerShape(50)
            )
        }
        items(listOf("draft", "completed", "cancelled")) { status ->
            val count = counts[status] ?: 0
            FilterChip(
                selected = selected == status,
                onClick = { onSelect(status) },
                label = { Text(if (count > 0) "${opnameStatusLabel(status)} ($count)" else opnameStatusLabel(status)) },
                leadingIcon = {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(opnameStatusColor(status), RoundedCornerShape(50))
                    )
                },
                shape = RoundedCornerShape(50)
            )
        }
    }
}

/** Order-card style row, same family as the Indent list. */
@Composable
private fun OpnameSessionRow(session: OpnameSessionDto, onClick: () -> Unit) {
    ClayCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OpnameStatusBadge(session.status)
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = formatOpnameDate(session.periodeDate),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = session.dealerName.ifBlank { session.dealerCode },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${session.kodeOpname} · ${if (session.jenis == "mingguan") "Mingguan" else "Bulanan"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            // Jendela waktu ikut di kartu, bukan cuma di layar detail: petugas
            // memilih sesi mana yang dibuka DARI SINI, dan sesi yang jendelanya
            // belum buka menolak SETIAP scan (`jendela_belum_mulai`).
            //
            // Barisnya TERPISAH, bukan disambung ke baris kode di atas: baris
            // itu `maxLines = 1`, jadi jendela yang ditempel di ekornya justru
            // bagian pertama yang dipotong ellipsis di layar sempit.
            //
            // `labelJendela` menjawab null untuk sesi TANPA jendela, dan itu
            // berarti "boleh kapan saja" — bukan "sudah lewat tenggat". Seluruh
            // sesi pra-migrasi 196 begitu, jadi barisnya memang tak dirender
            // sama sekali; jangan diganti teks pengganti macam "tanpa jendela".
            labelJendela(session.mulaiAt, session.selesaiAt)?.let { jendela ->
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Jendela $jendela",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${session.totalItems} barang dihitung · ${session.totalSelisihItems} selisih",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (session.totalSelisihItems > 0) opnameStatusColor("draft") else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "Lihat Detail",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Icon(
                    Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
internal fun OpnameStatusBadge(status: String) {
    val color = opnameStatusColor(status)
    Surface(color = color.copy(alpha = 0.14f), shape = RoundedCornerShape(8.dp)) {
        Text(
            text = opnameStatusLabel(status),
            color = color,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

/** Create-session sheet: dealer dropdown (from context), jenis chips, periode date, catatan. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OpnameCreateSheet(
    state: OpnameListUiState,
    todayIso: String,
    onDismiss: () -> Unit,
    onSubmit: (
        dealer: String,
        periode: String,
        jenis: String,
        catatan: String,
        mulai: String,
        selesai: String,
    ) -> Unit
) {
    var dealerCode by remember { mutableStateOf("") }
    var jenis by remember { mutableStateOf("bulanan") }
    var periode by remember { mutableStateOf(todayIso) }
    var catatan by remember { mutableStateOf("") }
    // Jendela waktu MATI secara default: sesi tanpa batas adalah perilaku lama
    // dan tetap sah. Menyalakannya diam-diam berarti admin yang cuma ingin
    // membuat sesi biasa tiba-tiba mengunci petugasnya ke rentang jam.
    var batasiWaktu by remember { mutableStateOf(false) }
    var mulai by remember { mutableStateOf("") }
    var selesai by remember { mutableStateOf("") }
    val masalah = if (batasiWaktu) masalahJendela(mulai, selesai) else null
    var dealerMenuOpen by remember { mutableStateOf(false) }
    val dealers = state.context?.dealers.orEmpty()

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Text(
                text = "Sesi Opname Baru",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Stok sistem dibekukan dari ERP saat sesi dibuat",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Cabang / Dealer",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            Box {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { dealerMenuOpen = true }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp)
                    ) {
                        Text(
                            text = dealers.firstOrNull { it.code == dealerCode }?.name
                                ?: "Pilih cabang...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (dealerCode.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null)
                    }
                }
                DropdownMenu(expanded = dealerMenuOpen, onDismissRequest = { dealerMenuOpen = false }) {
                    dealers.forEach { dealer ->
                        DropdownMenuItem(
                            text = { Text("${dealer.name} (${dealer.code})") },
                            onClick = {
                                dealerCode = dealer.code
                                dealerMenuOpen = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Jenis",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                listOf("bulanan" to "Bulanan", "mingguan" to "Mingguan").forEach { (value, label) ->
                    FilterChip(
                        selected = jenis == value,
                        onClick = { jenis = value },
                        label = { Text(label) },
                        shape = RoundedCornerShape(50)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            ExpressiveTextField(
                value = periode,
                onValueChange = { periode = it },
                label = "Tanggal Periode (YYYY-MM-DD)",
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = batasiWaktu,
                    onCheckedChange = { nyala ->
                        batasiWaktu = nyala
                        // Diisi jam kerja umum saat dinyalakan supaya admin
                        // tinggal menyunting, bukan mengetik bentuknya dari nol.
                        if (nyala && mulai.isBlank() && selesai.isBlank()) {
                            mulai = "$periode 08:00"
                            selesai = "$periode 17:00"
                        }
                    }
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text("Batasi waktu opname", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = "Di luar rentang ini scan DITOLAK server",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (batasiWaktu) {
                Spacer(modifier = Modifier.height(10.dp))
                ExpressiveTextField(
                    value = mulai,
                    onValueChange = { mulai = it },
                    label = "Mulai (YYYY-MM-DD HH:MM)",
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                ExpressiveTextField(
                    value = selesai,
                    onValueChange = { selesai = it },
                    label = "Selesai (YYYY-MM-DD HH:MM)",
                    modifier = Modifier.fillMaxWidth()
                )
                if (masalah != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = masalah,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Seluruh pegawai cabang ini akan dapat notifikasi saat sesi dibuat.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            ExpressiveTextField(
                value = catatan,
                onValueChange = { catatan = it },
                label = "Catatan (opsional)",
                singleLine = false,
                modifier = Modifier.fillMaxWidth()
            )

            if (state.createError != null) {
                ExpressiveFormError(message = state.createError)
            }

            Spacer(modifier = Modifier.height(20.dp))
            ExpressiveFilledButton(
                onClick = {
                    onSubmit(
                        dealerCode,
                        periode.trim(),
                        jenis,
                        catatan,
                        if (batasiWaktu) mulai.trim() else "",
                        if (batasiWaktu) selesai.trim() else "",
                    )
                },
                enabled = !state.isCreating && masalah == null,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.isCreating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Membuat sesi...")
                } else {
                    Text("Mulai Opname")
                }
            }
        }
    }
}
