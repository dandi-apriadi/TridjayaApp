package com.krisoft.tridjayaelektronik.ui.payroll

import android.content.Intent
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.krisoft.tridjayaelektronik.data.model.PayslipDto
import com.krisoft.tridjayaelektronik.data.model.PayslipItemDto
import com.krisoft.tridjayaelektronik.data.model.formatPeriodeId
import com.krisoft.tridjayaelektronik.ui.theme.ClayCard
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveEmptyState
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveErrorState
import com.krisoft.tridjayaelektronik.ui.theme.SkeletonCard
import com.krisoft.tridjayaelektronik.ui.theme.TridjayaCollapsibleHeader

/**
 * Pasang `FLAG_SECURE` selama layar ini hidup, lepas saat ditinggalkan (audit
 * keamanan 2026-08 temuan 3.4).
 *
 * Slip gaji memuat nominal gaji seseorang. Tanpa flag ini, aplikasi lain yang
 * memegang izin perekaman layar — dan siapa pun yang memegang HP-nya sebentar —
 * bisa menyalinnya utuh. Dipasang PER LAYAR, bukan app-wide: mematikan tangkap
 * layar di seluruh app akan mencabut cara petugas lapangan mengirim bukti
 * kendala ke grup, yang justru dipakai tiap hari.
 *
 * `onDispose` WAJIB melepasnya. Flag ini milik Window, bukan Composable — kalau
 * tak dilepas, seluruh app kehilangan kemampuan tangkap layar sampai proses
 * dimatikan, dan gejalanya "screenshot tiba-tiba mati" tanpa satu pun error.
 */
@Composable
private fun LayarRahasia() {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val window = (context as? android.app.Activity)?.window
        window?.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
}

private val EarningColor = Color(0xFF12B76A)
private val DeductionColor = Color(0xFFF04438)

private fun formatRupiah(value: Double): String {
    val text = value.toLong().toString().reversed().chunked(3).joinToString(".").reversed()
    return "Rp $text"
}

/**
 * Slip Gaji — daftar periode yang sudah dibayarkan (milik sendiri) → tap → detail rincian
 * komponen. Tidak ada cetak/PDF (backend tak punya endpoint PDF) — tampilan layar rapi cukup
 * untuk screenshot/share manual, sama seperti alternatif yang dibolehkan brief.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PayrollScreen(
    onBack: () -> Unit,
    viewModel: PayrollViewModel = hiltViewModel()
) {
    LayarRahasia()
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.load() }
    var selectedId by remember { mutableStateOf<Long?>(null) }

    selectedId?.let { id ->
        LaunchedEffect(id) { viewModel.openDetail(id) }
        PayslipDetailScreen(
            loading = state.detailLoading,
            detail = state.detail,
            error = state.detailError,
            onBack = {
                selectedId = null
                viewModel.closeDetail()
            }
        )
        return
    }

    TridjayaCollapsibleHeader(title = "Slip Gaji", onBack = onBack) { contentModifier ->
        val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        when {
            state.loading && state.items.isEmpty() ->
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    repeat(4) { SkeletonCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }
                }
            state.error != null && state.items.isEmpty() ->
                Box(contentModifier.fillMaxSize().padding(24.dp), Alignment.Center) {
                    ExpressiveErrorState(message = state.error ?: "Gagal memuat", onRetry = viewModel::load)
                }
            state.items.isEmpty() ->
                Box(contentModifier.fillMaxSize().padding(24.dp), Alignment.Center) {
                    ExpressiveEmptyState(
                        icon = { Icon(Icons.Rounded.Payments, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        title = "Belum ada slip gaji",
                        subtitle = "Slip gaji akan tampil di sini setelah periode dibayarkan."
                    )
                }
            else -> {
                val pullState = rememberPullToRefreshState()
                PullToRefreshBox(
                    isRefreshing = state.loading,
                    onRefresh = viewModel::load,
                    state = pullState,
                    modifier = contentModifier.fillMaxSize(),
                    indicator = {
                        PullToRefreshDefaults.Indicator(
                            modifier = Modifier.align(Alignment.TopCenter),
                            isRefreshing = state.loading,
                            state = pullState,
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp + navBottom),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(state.items, key = { it.id }) { payslip ->
                            PayslipRow(payslip, onClick = { selectedId = payslip.id })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PayslipRow(payslip: PayslipDto, onClick: () -> Unit) {
    ClayCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = formatPeriodeId(payslip.periode),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${payslip.cabangNama} · ${payslip.kategoriNama}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
                Text(
                    text = formatRupiah(payslip.netPay),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun PayslipDetailScreen(
    loading: Boolean,
    detail: com.krisoft.tridjayaelektronik.data.model.PayslipDetailData?,
    error: String?,
    onBack: () -> Unit
) {
    // Detail adalah state-swap di dalam route Slip Gaji (bukan nav destination sendiri) —
    // tanpa ini system back akan pop seluruh route, bukan kembali ke daftar (pola IndentDetailScreen).
    BackHandler(onBack = onBack)
    LayarRahasia()
    val context = LocalContext.current

    TridjayaCollapsibleHeader(title = "Detail Slip Gaji", onBack = onBack) { contentModifier ->
        when {
            loading && detail == null ->
                Box(contentModifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            error != null && detail == null ->
                Box(contentModifier.fillMaxSize().padding(24.dp), Alignment.Center) {
                    ExpressiveErrorState(message = error, onRetry = {})
                }
            detail != null -> {
                val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                val payslip = detail.payslip
                Column(
                    modifier = contentModifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 24.dp + navBottom)
                ) {
                    ClayCard(
                        modifier = Modifier.fillMaxWidth(),
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = formatPeriodeId(payslip.periode),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Take Home Pay",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                            Text(
                                text = formatRupiah(payslip.netPay),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // PENGGANTI SCREENSHOT, bukan tambahan. Layar ini memasang
                    // FLAG_SECURE (audit 3.4) sehingga tangkap layar diblokir
                    // sistem; tanpa tombol ini karyawan kehilangan satu-satunya
                    // cara menyimpan slip gajinya sendiri.
                    //
                    // PDF dirakit DI PERANGKAT dari data yang sudah ada di layar
                    // — tak ada endpoint baru yang mengalirkan angka gaji, dan
                    // ia tetap bekerja saat sinyal buruk.
                    Button(
                        onClick = {
                            runCatching {
                                val berkas = tulisSlipGajiPdf(context, detail)
                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    berkas,
                                )
                                val kirim = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/pdf"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    putExtra(Intent.EXTRA_SUBJECT, berkas.name)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(
                                    Intent.createChooser(kirim, "Simpan / bagikan slip gaji")
                                )
                            }.onFailure {
                                // Tak ada penangan PDF / izin URI ditolak. Keduanya
                                // dilempar SINKRON dari startActivity — tanpa
                                // penjaga ini satu ketukan menutup app.
                                android.widget.Toast.makeText(
                                    context,
                                    "Tidak ada aplikasi untuk menyimpan PDF di HP ini.",
                                    android.widget.Toast.LENGTH_LONG,
                                ).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Simpan / bagikan PDF")
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    ClayCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                            InfoRow("Karyawan", payslip.karyawanNama)
                            InfoRow("Jabatan", payslip.jabatan)
                            InfoRow("Divisi", payslip.divisi)
                            InfoRow("Cabang", payslip.cabangNama)
                            InfoRow("Kategori", payslip.kategoriNama)
                            payslip.namaBank?.takeIf { it.isNotBlank() }?.let { InfoRow("Bank", it) }
                            payslip.noRekening?.takeIf { it.isNotBlank() }?.let { InfoRow("No. Rekening", it) }
                            InfoRow("Total Pendapatan", formatRupiah(payslip.totalEarning))
                            InfoRow("Total Potongan", formatRupiah(payslip.totalDeduction), isLast = true)
                        }
                    }

                    val earnings = detail.items.filter { it.tipe.equals("earning", ignoreCase = true) }
                    val deductions = detail.items.filter { it.tipe.equals("deduction", ignoreCase = true) }

                    if (earnings.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        ComponentBreakdownCard(title = "Rincian Pendapatan", items = earnings, tint = EarningColor)
                    }
                    if (deductions.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        ComponentBreakdownCard(title = "Rincian Potongan", items = deductions, tint = DeductionColor)
                    }
                }
            }
        }
    }
}

@Composable
private fun ComponentBreakdownCard(title: String, items: List<PayslipItemDto>, tint: Color) {
    ClayCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            items.sortedBy { it.urutan }.forEach { item ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                    )
                    Text(
                        text = formatRupiah(item.amount),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = tint
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, isLast: Boolean = false) {
    Column(modifier = Modifier.padding(vertical = 10.dp)) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
    if (!isLast) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    }
}
