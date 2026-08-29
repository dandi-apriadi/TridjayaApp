package com.krisoft.tridjayaelektronik.ui.leads

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import com.krisoft.tridjayaelektronik.data.model.LeadDto
import com.krisoft.tridjayaelektronik.data.model.PipelineDto
import com.krisoft.tridjayaelektronik.ui.theme.ClayCard
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveErrorState
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveFilledButton
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveFormError
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveOutlinedButton
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveShapes
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveTextField
import com.krisoft.tridjayaelektronik.ui.theme.ScrollableCenter
import com.krisoft.tridjayaelektronik.ui.theme.TridjayaCollapsibleHeader
import com.krisoft.tridjayaelektronik.ui.theme.TridjayaPullRefresh
import com.krisoft.tridjayaelektronik.ui.theme.rememberHapticClick

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeadDetailScreen(
    onBack: () -> Unit,
    viewModel: LeadDetailViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showLostDialog by remember { mutableStateOf(false) }
    val navBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // Pemilih WhatsApp Business vs biasa — sama dengan tombol WA di layar list.
    val kirimWa = rememberKirimWa()

    TridjayaCollapsibleHeader(title = "Detail Prospek", onBack = onBack) { contentModifier ->
        TridjayaPullRefresh(
            isRefreshing = state.isLoading && state.lead != null,
            // Tarik saat mutasi tahap/status masih jalan akan menimpa hasilnya dengan data lama.
            onRefresh = { if (!state.isMovingStage && !state.isUpdatingStatus) viewModel.load() },
            modifier = contentModifier
        ) {
            when {
                state.isLoading && state.lead == null -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                state.lead == null && state.errorMessage != null -> {
                    ScrollableCenter {
                        ExpressiveErrorState(
                            message = state.errorMessage ?: "Tidak bisa memuat prospek.",
                            onRetry = viewModel::load
                        )
                    }
                }
                state.lead == null -> {
                    ScrollableCenter {
                        Text(text = "Prospek tidak ditemukan")
                    }
                }
                else -> {
                    val lead = state.lead!!
                    val isOpen = lead.status.equals("open", ignoreCase = true)
                    // Prospek yang ditangani sales lain hanya bisa DILIHAT — server menolak
                    // mutasi (ubah tahap / DEAL / GAGAL) dari non-pemilik, jadi aksinya
                    // dikunci di UI sekalian agar tidak antre gagal terus di sync offline.
                    val canManage = lead.assignedTo.isNullOrBlank() || lead.assignedTo == state.myId
                    val handlerName = resolveHandlerName(lead, state.myId, state.employeeNames)
                    val stages = remember(state.pipeline) {
                        state.pipeline?.stages?.sortedBy { it.urutan }.orEmpty()
                    }
                    val currentStageIndex = stages.indexOfFirst { it.id == lead.stageId }.coerceAtLeast(0)
                    val probability = leadProbability(
                        lead.status,
                        if (stages.isEmpty()) null else StageProgress(currentStageIndex, stages.size)
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = navBarInset + 24.dp)
                    ) {
                        ProspectHeroCard(
                            lead = lead,
                            probability = probability,
                            onWhatsApp = {
                                kirimWa(lead.phone, buildPromoMessage(lead, state.myName)) {
                                    // Dicatat SEBELUM intent dibuka, sesudah app tujuan
                                    // dipilih — lihat catatan yang sama di LeadsListScreen.
                                    viewModel.catatKontakWhatsapp()
                                }
                            },
                            onCall = {
                                runCatching {
                                    context.startActivity(
                                        android.content.Intent(
                                            android.content.Intent.ACTION_DIAL,
                                            "tel:${lead.phone}".toUri()
                                        )
                                    )
                                }
                            }
                        )

                        if (!canManage) {
                            Spacer(modifier = Modifier.height(12.dp))
                            ReadOnlyBanner(handlerName = handlerName)
                        }

                        if (stages.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(16.dp))
                            HorizontalStepperCard(
                                pipeline = state.pipeline!!,
                                currentIndex = currentStageIndex,
                                isMoving = state.isMovingStage,
                                isOpen = isOpen && canManage,
                                onSelectStage = viewModel::moveStage
                            )
                        }

                        if (canManage) {
                            Spacer(modifier = Modifier.height(16.dp))
                            OutcomeSection(
                                status = lead.status,
                                isUpdating = state.isUpdatingStatus,
                                onMarkWon = viewModel::markWon,
                                onMarkLost = { showLostDialog = true },
                                onReopen = viewModel::reopen
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        InfoListCard(
                            lead = lead,
                            creatorName = resolveCreatorName(lead, state.myId, state.employeeNames),
                            handlerName = handlerName,
                            thrownToOther = isThrownToOther(lead, state.myId),
                            thrownToMe = isThrownToMe(lead, state.myId)
                        )

                        if (state.errorMessage != null) {
                            Spacer(modifier = Modifier.height(12.dp))
                            ExpressiveFormError(message = state.errorMessage ?: "")
                        }
                    }
                }
            }
        }
    }

    if (showLostDialog) {
        LostReasonDialog(
            onDismiss = { showLostDialog = false },
            onConfirm = { reason ->
                viewModel.markLost(reason)
                showLostDialog = false
            }
        )
    }
}

/**
 * Kartu identitas bergaya profil terpusat: avatar besar, nama & nomor di tengah, badge status,
 * meter suhu Cold/Warm/Hot (pemetaan probabilitas — hanya utk lead aktif), chip minat/kategori/
 * estimasi, lalu tombol WhatsApp promo & telepon.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProspectHeroCard(
    lead: LeadDto,
    probability: Int,
    onWhatsApp: () -> Unit,
    onCall: () -> Unit
) {
    val accent = leadAccentColor(lead.status)
    val isOpen = lead.status.equals("open", ignoreCase = true)
    val temp = leadTemperature(probability)
    ClayCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(accent.copy(alpha = 0.12f), RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = lead.nama.trim().firstOrNull()?.uppercase() ?: "?",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    color = accent
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = lead.nama,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = lead.phone,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(50), color = accent.copy(alpha = 0.13f)) {
                    Text(
                        text = leadStatusLabel(lead.status),
                        color = accent,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
                // "ANTRE" hanya untuk yang benar-benar mengantre. Baris yang
                // server tolak permanen diberi label sendiri + sebabnya di bawah.
                if (lead.pendingSync) {
                    val ditolak = lead.syncRejectReason != null
                    val warna = if (ditolak) MaterialTheme.colorScheme.error else Color(0xFFB5670C)
                    Surface(color = warna.copy(alpha = 0.13f), shape = RoundedCornerShape(50)) {
                        Text(
                            text = if (ditolak) "BELUM MASUK SERVER" else "ANTRE",
                            color = warna,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            lead.assignmentWarning?.let { pesan ->
                Spacer(modifier = Modifier.height(10.dp))
                // Oranye, bukan merah: lead-nya TERSIMPAN di server, yang gagal
                // cuma pemberitahuan ke penerimanya. Memakai `errorContainer`
                // seperti blok di bawah akan membacakan "gagal simpan" dan
                // memancing sales mengetik ulang prospek yang sudah ada.
                Surface(
                    color = Color(0xFFB5670C).copy(alpha = 0.14f),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = "$pesan Hubungi penerimanya langsung supaya lead ini tak terlewat.",
                        color = Color(0xFFB5670C),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    )
                }
            }

            lead.syncRejectReason?.let { alasan ->
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = alasan,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    )
                }
            }

            if (isOpen) {
                Spacer(modifier = Modifier.height(14.dp))
                TemperatureMeter(current = temp)
            }

            val chips = buildList {
                lead.minatBarang?.takeIf { it.isNotBlank() }?.let { add(Icons.Rounded.ShoppingBag to it) }
                lead.kategoriProduk?.takeIf { it.isNotBlank() }?.let { add(Icons.Rounded.Category to it) }
                if (lead.estimatedValue > 0) add(Icons.Rounded.Payments to formatRupiahShort(lead.estimatedValue))
            }
            if (chips.isNotEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    chips.forEach { (icon, label) ->
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ExpressiveFilledButton(
                    onClick = onWhatsApp,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1FA855), contentColor = Color.White),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Chat, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("WA Promo")
                }
                ExpressiveOutlinedButton(
                    onClick = onCall,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.Call, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Telepon")
                }
            }
        }
    }
}

/** Banner info bahwa prospek ini milik sales lain — hanya bisa dilihat, tidak bisa diubah. */
@Composable
private fun ReadOnlyBanner(handlerName: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Rounded.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = "Followup oleh $handlerName — Anda hanya bisa melihat prospek ini.",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}

/** Meter suhu 3 segmen: segmen aktif terisi penuh warna suhunya, sisanya tint lembut. */
@Composable
private fun TemperatureMeter(current: LeadTemperature) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        LeadTemperature.entries.forEach { temp ->
            val active = temp == current
            Column(
                modifier = Modifier
                    .weight(1f)
                    .background(
                        if (active) temp.color else temp.color.copy(alpha = 0.08f),
                        RoundedCornerShape(14.dp)
                    )
                    .padding(vertical = 9.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = temp.icon,
                    contentDescription = null,
                    tint = if (active) Color.White else temp.color.copy(alpha = 0.7f),
                    modifier = Modifier.size(17.dp)
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = temp.label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (active) FontWeight.Black else FontWeight.Medium,
                    color = if (active) Color.White else temp.color.copy(alpha = 0.8f)
                )
            }
        }
    }
}

/** Horizontal stepper: numbered nodes joined by connector lines, scrollable sideways; the whole
 *  journey is visible at a glance and each node is tappable. A progress bar + one-tap "Lanjut"
 *  button sit underneath. */
@Composable
private fun HorizontalStepperCard(
    pipeline: PipelineDto,
    currentIndex: Int,
    isMoving: Boolean,
    isOpen: Boolean,
    onSelectStage: (Long) -> Unit
) {
    val stages = remember(pipeline) { pipeline.stages.sortedBy { it.urutan } }
    val nextStage = stages.getOrNull(currentIndex + 1)
    val progress = if (stages.isEmpty()) 0f else (currentIndex + 1).toFloat() / stages.size

    ClayCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Tahap Pengerjaan", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (isMoving) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                }
            }
            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.Top
            ) {
                stages.forEachIndexed { index, stage ->
                    val isCurrent = index == currentIndex
                    val isDone = index < currentIndex
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .width(86.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(enabled = isOpen && !isMoving && !isCurrent) { onSelectStage(stage.id) }
                            .padding(vertical = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .background(
                                    color = when {
                                        isDone || isCurrent -> MaterialTheme.colorScheme.primary
                                        else -> MaterialTheme.colorScheme.surfaceVariant
                                    },
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            when {
                                isDone -> Icon(
                                    Icons.Rounded.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                                else -> Text(
                                    text = "${index + 1}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Black,
                                    color = if (isCurrent) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = stage.nama,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isCurrent) FontWeight.Black else FontWeight.Medium,
                            color = when {
                                isCurrent -> MaterialTheme.colorScheme.primary
                                isDone -> MaterialTheme.colorScheme.onSurface
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (index != stages.lastIndex) {
                        Box(
                            modifier = Modifier
                                .padding(top = 20.dp)
                                .width(18.dp)
                                .height(2.dp)
                                .background(
                                    if (index < currentIndex) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant
                                )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                strokeCap = StrokeCap.Round
            )

            if (isOpen && nextStage != null) {
                Spacer(modifier = Modifier.height(12.dp))
                ExpressiveFilledButton(
                    onClick = { onSelectStage(nextStage.id) },
                    enabled = !isMoving,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Lanjut: ${nextStage.nama}")
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

/** Hasil prospek: dua tile bergaya tint lembut (open), atau banner hasil + buka kembali. */
@Composable
private fun OutcomeSection(
    status: String,
    isUpdating: Boolean,
    onMarkWon: () -> Unit,
    onMarkLost: () -> Unit,
    onReopen: () -> Unit
) {
    when (status.lowercase()) {
        "open" -> {
            Text(
                text = "HASIL PROSPEK",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutcomeTile(
                    icon = Icons.Rounded.ThumbUp,
                    label = "DEAL",
                    caption = "Pelanggan setuju",
                    accent = Color(0xFF2E7D32),
                    enabled = !isUpdating,
                    onClick = onMarkWon,
                    modifier = Modifier.weight(1f)
                )
                OutcomeTile(
                    icon = Icons.Rounded.Cancel,
                    label = "GAGAL",
                    caption = "Prospek batal",
                    accent = Color(0xFFC62828),
                    enabled = !isUpdating,
                    onClick = onMarkLost,
                    modifier = Modifier.weight(1f)
                )
            }
            if (isUpdating) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
        "won", "lost" -> {
            val won = status.equals("won", ignoreCase = true)
            val accent = if (won) Color(0xFF2E7D32) else Color(0xFFC62828)
            ClayCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(accent.copy(alpha = 0.10f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (won) Icons.Rounded.ThumbUp else Icons.Rounded.Cancel,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = if (won) "Prospek ini sudah DEAL 🎉" else "Prospek ini GAGAL",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = accent
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        if (isUpdating) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    ExpressiveOutlinedButton(
                        onClick = onReopen,
                        enabled = !isUpdating,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Buka Kembali")
                    }
                }
            }
        }
    }
}

@Composable
private fun OutcomeTile(
    icon: ImageVector,
    label: String,
    caption: String,
    accent: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(accent.copy(alpha = if (enabled) 0.10f else 0.05f))
            .border(1.dp, accent.copy(alpha = 0.30f), RoundedCornerShape(18.dp))
            .clickable(enabled = enabled, onClick = rememberHapticClick(onClick))
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(40.dp).background(accent, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Black,
            color = accent
        )
        Text(
            text = caption,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Informasi lengkap prospek — termasuk penginput, penanggung jawab, minat & kategori. */
@Composable
private fun InfoListCard(
    lead: LeadDto,
    creatorName: String,
    handlerName: String,
    thrownToOther: Boolean,
    thrownToMe: Boolean
) {
    val handlerSuffix = when {
        thrownToOther -> "  ·  Dilempar"
        thrownToMe -> "  ·  Limpahan"
        else -> ""
    }
    val rows = buildList {
        add(Triple(Icons.Rounded.PersonAdd, "Dibuat oleh", creatorName))
        add(Triple(Icons.Rounded.Person, "Followup oleh", handlerName + handlerSuffix))
        if (!lead.cabang.isNullOrBlank()) add(Triple(Icons.Rounded.Storefront, "Cabang", lead.cabang!!))
        if (!lead.minatBarang.isNullOrBlank()) add(Triple(Icons.Rounded.ShoppingBag, "Minat Barang", lead.minatBarang!!))
        if (!lead.kategoriProduk.isNullOrBlank()) add(Triple(Icons.Rounded.Category, "Kategori Produk", lead.kategoriProduk!!))
        if (!lead.source.isNullOrBlank()) add(Triple(Icons.Rounded.Campaign, "Sumber", lead.source!!))
        if (!lead.lokasi.isNullOrBlank()) add(Triple(Icons.Rounded.Place, "Alamat", lead.lokasi!!))
        if (!lead.catatan.isNullOrBlank()) add(Triple(Icons.AutoMirrored.Rounded.Notes, "Keterangan", lead.catatan!!))
        if (lead.createdAt.isNotBlank()) add(Triple(Icons.Rounded.CalendarMonth, "Tanggal Masuk", formatDate(lead.createdAt)))
        if (lead.status.equals("lost", ignoreCase = true) && !lead.lostReason.isNullOrBlank()) {
            add(Triple(Icons.Rounded.Cancel, "Alasan Gagal", lead.lostReason!!))
        }
    }

    ClayCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                text = "Informasi Prospek",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
            )
            rows.forEachIndexed { index, (icon, label, value) ->
                val isLostReason = label == "Alasan Gagal"
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .background(
                                if (isLostReason) MaterialTheme.colorScheme.errorContainer
                                else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                                RoundedCornerShape(11.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (isLostReason) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = value,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isLostReason) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                if (index != rows.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                }
            }
        }
    }
}

@Composable
private fun LostReasonDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var reason by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = ExpressiveShapes.Large,
        title = { Text("Tandai GAGAL", fontWeight = FontWeight.Bold) },
        text = {
            ExpressiveTextField(
                value = reason,
                onValueChange = { reason = it },
                label = "Alasan gagal",
                placeholder = "Harga, stok kosong, pindah toko, dsb.",
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            ExpressiveFilledButton(
                onClick = { if (reason.isNotBlank()) onConfirm(reason) },
                enabled = reason.isNotBlank()
            ) {
                Text("Konfirmasi")
            }
        },
        dismissButton = {
            ExpressiveOutlinedButton(onClick = onDismiss) {
                Text("Batal")
            }
        }
    )
}

/** Compact currency for the stats strip, e.g. Rp 1,8M / Rp 3,7Jt. */
private fun formatRupiahShort(value: Double): String {
    val abs = kotlin.math.abs(value)
    val sign = if (value < 0) "-" else ""
    return when {
        abs >= 1_000_000_000 -> "%sRp %.1fM".format(sign, abs / 1_000_000_000)
        abs >= 1_000_000 -> "%sRp %.1fJt".format(sign, abs / 1_000_000)
        abs >= 1_000 -> "%sRp %.0fRb".format(sign, abs / 1_000)
        else -> "%sRp %.0f".format(sign, abs)
    }
}

/** Best-effort "yyyy-MM-dd…" → "d Mmm yyyy" (Indonesian months); falls back to the raw string. */
private fun formatDate(raw: String): String {
    val datePart = raw.substringBefore('T').substringBefore(' ').trim()
    val parts = datePart.split("-")
    if (parts.size != 3) return raw
    val month = parts[1].toIntOrNull() ?: return raw
    val day = parts[2].toIntOrNull() ?: return raw
    if (month !in 1..12) return raw
    val months = listOf("", "Jan", "Feb", "Mar", "Apr", "Mei", "Jun", "Jul", "Agu", "Sep", "Okt", "Nov", "Des")
    return "$day ${months[month]} ${parts[0]}"
}
