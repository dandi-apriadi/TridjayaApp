package com.krisoft.tridjayaelektronik.ui.inventory

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.View
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Sell
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.krisoft.tridjayaelektronik.data.export.FlyerImageExporter
import com.krisoft.tridjayaelektronik.data.local.BranchStockEntity
import com.krisoft.tridjayaelektronik.data.local.DEADSTOCK_MIN_DAYS
import com.krisoft.tridjayaelektronik.data.local.DealerAlias
import com.krisoft.tridjayaelektronik.data.local.ProductAggregate
import com.krisoft.tridjayaelektronik.data.local.RegionAlias
import com.krisoft.tridjayaelektronik.data.pricing.InstallmentResult
import com.krisoft.tridjayaelektronik.ui.theme.AgingBadge
import com.krisoft.tridjayaelektronik.ui.theme.ClayCard
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveErrorState
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveFilledButton
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveOutlinedButton
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveTextButton
import com.krisoft.tridjayaelektronik.ui.theme.ScrollableCenter
import com.krisoft.tridjayaelektronik.ui.theme.TridjayaCollapsibleHeader
import com.krisoft.tridjayaelektronik.ui.theme.TridjayaPullRefresh
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductDetailScreen(
    onBack: () -> Unit,
    viewModel: ProductDetailViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    var flyerBounds by remember { mutableStateOf<Rect?>(null) }
    var isGenerating by remember { mutableStateOf(false) }
    var flyerStyle by remember { mutableStateOf(FlyerCustomStyle()) }
    var showFlyerSheet by remember { mutableStateOf(false) }

    TridjayaCollapsibleHeader(title = "Detail Produk", onBack = onBack) { contentModifier ->
        TridjayaPullRefresh(
            isRefreshing = state.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = contentModifier
        ) {
            when {
                // Spinner layar penuh HANYA untuk load pertama: saat tarik-turun, detail yang
                // sudah tampil tak boleh diganti spinner, dan dari keadaan kosong pun jangan —
                // indikator tarik sudah jadi penanda "sedang memuat", dua sekaligus menumpuk.
                state.isLoading && state.product == null && !state.isRefreshing -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                state.product == null && state.errorMessage != null -> {
                    ScrollableCenter {
                        ExpressiveErrorState(
                            message = state.errorMessage ?: "Tidak bisa memuat detail produk.",
                            onRetry = viewModel::load
                        )
                    }
                }
                state.product == null -> {
                    ScrollableCenter {
                        Text("Produk tidak ditemukan")
                    }
                }
                else -> {
                    val product = state.product!!
                    var isGeneratingOnly by remember { mutableStateOf(false) }
                    var isPrintingLabel by remember { mutableStateOf(false) }
                    // Mode FRESH SALE utk barang deadstock: harga flyer otomatis -10% (dibulatkan
                    // ke ribuan), cicilan/DP ikut dihitung ulang dari harga promo.
                    val isDeadstock = (product.maxUmurHari ?: 0L) >= DEADSTOCK_MIN_DAYS
                    var freshSale by remember { mutableStateOf(false) }
                    val flyerProduct = remember(product, freshSale) {
                        if (freshSale) product.copy(harga = freshSalePrice(product.harga)) else product
                    }
                    // Tanpa fallback ke cicilan harga asli: bila cicilan promo tak tersedia,
                    // lebih baik flyer tampil tanpa blok cicilan daripada harga -10% disandingkan
                    // dengan DP/tenor yang dihitung dari harga penuh.
                    val flyerInstallment = if (freshSale) state.promoInstallment else state.installment
                    val flyerPromo = if (freshSale) FlyerPromo(product.harga, FRESH_SALE_PERCENT) else null

                    Column(modifier = Modifier.fillMaxSize()) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp)
                        ) {
                            // Flyer first so it's fully on-screen when captured (PixelCopy grabs its
                            // current visible bounds); the info/credit/stock cards sit below it.
                            Spacer(modifier = Modifier.height(8.dp))
                            if (isDeadstock) {
                                FreshSaleToggleCard(
                                    enabled = freshSale,
                                    originalPrice = product.harga,
                                    onToggle = { freshSale = it }
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                            }
                            // Mode FRESH SALE mengganti SELURUH daftar desain dengan satu desain
                            // cuci gudang khusus — bukan menempel sticker di desain biasa.
                            val designs = if (freshSale) listOf(FRESH_SALE_DESIGN) else FLYER_DESIGNS
                            val pagerState = rememberPagerState { designs.size }
                            LaunchedEffect(freshSale) { pagerState.scrollToPage(0) }
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Desain: ${(designs.getOrNull(pagerState.currentPage) ?: designs.last()).name}",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "${(pagerState.currentPage + 1).coerceAtMost(designs.size)}/${designs.size}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                IconButton(onClick = { showFlyerSheet = true }) {
                                    Icon(
                                        Icons.Rounded.Tune,
                                        contentDescription = "Kustomisasi flyer",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            HorizontalPager(state = pagerState, pageSpacing = 12.dp) { page ->
                                // Only the visible page reports capture bounds — "Buat Gambar"/
                                // "Kirim WA" always exports the design currently on screen.
                                val boundsModifier = if (page == pagerState.currentPage) {
                                    Modifier.onGloballyPositioned { flyerBounds = it.boundsInRoot() }
                                } else Modifier
                                ProductFlyer(
                                    product = flyerProduct,
                                    installment = if (flyerStyle.showInstallment) flyerInstallment else null,
                                    salesName = state.salesName,
                                    salesWhatsapp = state.salesWhatsapp,
                                    design = designs[page],
                                    style = flyerStyle,
                                    modifier = boundsModifier,
                                    promo = flyerPromo
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                repeat(designs.size) { index ->
                                    val selected = index == pagerState.currentPage
                                    Box(
                                        modifier = Modifier
                                            .padding(horizontal = 3.dp)
                                            .size(if (selected) 8.dp else 6.dp)
                                            .background(
                                                if (selected) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.outlineVariant,
                                                CircleShape
                                            )
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(18.dp))
                            DetailSectionLabel("Informasi Produk")
                            Spacer(modifier = Modifier.height(8.dp))
                            ProductInfoCard(product)

                            state.installment?.let { installment ->
                                Spacer(modifier = Modifier.height(18.dp))
                                DetailSectionLabel("Simulasi Kredit")
                                Spacer(modifier = Modifier.height(8.dp))
                                InstallmentCard(installment)
                            }

                            if (state.branches.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(18.dp))
                                DetailSectionLabel("Stok per Cabang")
                                Spacer(modifier = Modifier.height(8.dp))
                                BranchStockCard(state.branches)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        // Sticky action bar — sits above the system nav bar (this screen has no floating nav).
                        Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .navigationBarsPadding()
                                    .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    ExpressiveOutlinedButton(
                                        onClick = {
                                            val bounds = flyerBounds
                                            if (bounds == null || isGeneratingOnly) return@ExpressiveOutlinedButton
                                            isGeneratingOnly = true
                                            scope.launch {
                                                val bitmap = captureBitmap(view, bounds)
                                                if (bitmap != null) {
                                                    val uri = FlyerImageExporter.save(context, bitmap)
                                                    FlyerImageExporter.shareGeneric(context, uri)
                                                } else {
                                                    Toast.makeText(context, "Gagal membuat gambar flyer", Toast.LENGTH_SHORT).show()
                                                }
                                                isGeneratingOnly = false
                                            }
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        if (isGeneratingOnly) {
                                            CircularProgressIndicator(modifier = Modifier.height(18.dp))
                                        } else {
                                            Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Buat Gambar")
                                        }
                                    }
                                    ExpressiveFilledButton(
                                        onClick = {
                                            val bounds = flyerBounds
                                            if (bounds == null || isGenerating) return@ExpressiveFilledButton
                                            isGenerating = true
                                            scope.launch {
                                                val bitmap = captureBitmap(view, bounds)
                                                if (bitmap != null) {
                                                    val uri = FlyerImageExporter.save(context, bitmap)
                                                    FlyerImageExporter.shareToWhatsApp(context, uri)
                                                } else {
                                                    Toast.makeText(context, "Gagal membuat gambar flyer", Toast.LENGTH_SHORT).show()
                                                }
                                                isGenerating = false
                                            }
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        if (isGenerating) {
                                            CircularProgressIndicator(modifier = Modifier.height(18.dp), color = MaterialTheme.colorScheme.onPrimary)
                                        } else {
                                            Icon(Icons.AutoMirrored.Rounded.Chat, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Kirim WA")
                                        }
                                    }
                                }
                                // Salin mengikuti cicilan yang SEDANG tampil di flyer — saat mode
                                // FRESH SALE aktif, teks yang disalin harus harga/DP/tenor promo,
                                // bukan struktur kredit harga asli (biar tidak beda dgn flyer WA).
                                flyerInstallment?.let { installment ->
                                    ExpressiveTextButton(
                                        onClick = { copyStrukturKredit(context, installment.strukturKredit) },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Salin Struktur Kredit")
                                    }
                                }
                                ExpressiveTextButton(
                                    onClick = {
                                        if (isPrintingLabel) return@ExpressiveTextButton
                                        isPrintingLabel = true
                                        scope.launch {
                                            try {
                                                exportAndSharePricetags(
                                                    context,
                                                    listOf(product),
                                                    true,
                                                    product.nama.ifBlank { product.kode }
                                                )
                                            } catch (t: Throwable) {
                                                Toast.makeText(
                                                    context,
                                                    "Gagal membuat label harga: ${t.message ?: "kesalahan tak terduga"}",
                                                    Toast.LENGTH_LONG
                                                ).show()
                                            } finally {
                                                isPrintingLabel = false
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    if (isPrintingLabel) {
                                        CircularProgressIndicator(modifier = Modifier.size(18.dp))
                                    } else {
                                        Icon(Icons.Rounded.Sell, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Cetak Label Harga")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showFlyerSheet) {
        FlyerCustomizeSheet(
            style = flyerStyle,
            onStyleChange = { flyerStyle = it },
            onDismiss = { showFlyerSheet = false }
        )
    }
}

/** Bottom sheet with the flyer text-styling controls (font, sizes, alignment, effects). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FlyerCustomizeSheet(
    style: FlyerCustomStyle,
    onStyleChange: (FlyerCustomStyle) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Kustomisasi Flyer",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                ExpressiveTextButton(onClick = { onStyleChange(FlyerCustomStyle()) }) {
                    Text("Reset")
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text("Jenis Huruf", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FlyerFont.entries.forEach { font ->
                    FilterChip(
                        selected = style.fontChoice == font,
                        onClick = { onStyleChange(style.copy(fontChoice = font)) },
                        label = { Text(font.label, fontFamily = font.family) },
                        shape = RoundedCornerShape(50)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text("Posisi Teks Judul", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FlyerAlign.entries.forEach { align ->
                    FilterChip(
                        selected = style.titleAlign == align,
                        onClick = { onStyleChange(style.copy(titleAlign = align)) },
                        label = { Text(align.label) },
                        shape = RoundedCornerShape(50)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Ukuran Judul  (${(style.titleScale * 100).toInt()}%)",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            Slider(
                value = style.titleScale,
                onValueChange = { onStyleChange(style.copy(titleScale = it)) },
                valueRange = 0.8f..1.3f
            )

            Text(
                text = "Ukuran Harga  (${(style.priceScale * 100).toInt()}%)",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            Slider(
                value = style.priceScale,
                onValueChange = { onStyleChange(style.copy(priceScale = it)) },
                valueRange = 0.8f..1.3f
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Bayangan Teks",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = style.textShadow,
                    onCheckedChange = { onStyleChange(style.copy(textShadow = it)) }
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Huruf Kapital",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = style.uppercase,
                    onCheckedChange = { onStyleChange(style.copy(uppercase = it)) }
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Tampilkan Cicilan",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "DP, tenor per bulan & harga pembanding",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = style.showInstallment,
                    onCheckedChange = { onStyleChange(style.copy(showInstallment = it)) }
                )
            }
        }
    }
}

private fun copyStrukturKredit(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Struktur Kredit", text))
    Toast.makeText(context, "Struktur kredit berhasil disalin", Toast.LENGTH_SHORT).show()
}

/** Kartu toggle mode FRESH SALE — hanya tampil untuk barang deadstock (umur stok >= 180 hari).
 *  Saat aktif, flyer memakai harga -10% + sticker DISKON GEDE dan cicilan promo. */
@Composable
private fun FreshSaleToggleCard(
    enabled: Boolean,
    originalPrice: Double,
    onToggle: (Boolean) -> Unit
) {
    val accent = Color(0xFFE11D48)
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (enabled) accent.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "🔥 Mode FRESH SALE",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Black,
                    color = if (enabled) accent else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (enabled) {
                        "Flyer promo aktif — harga spesial ${formatRupiah(freshSalePrice(originalPrice))}"
                    } else {
                        "Barang lama (deadstock) — nyalakan untuk flyer harga spesial otomatis"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = enabled, onCheckedChange = onToggle)
        }
    }
}

@Composable
private fun DetailSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp)
    )
}

/** Product summary card at the top of the detail screen — name, brand/category, region, price, stock. */
@Composable
private fun ProductInfoCard(product: ProductAggregate) {
    ClayCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.size(56.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Inventory2, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(28.dp))
                    }
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = product.nama, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (product.merk.isNotBlank()) InfoChip(product.merk)
                        if (product.kategori.isNotBlank()) InfoChip(product.kategori)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Place, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = RegionAlias.label(product.kodeCabang), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                Column {
                    Text("Harga", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatRupiah(product.harga), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Total Stok", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${product.totalStok.toInt()} unit", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun InfoChip(text: String) {
    Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

/** In-app credit simulation card (mirrors the flyer's numbers in a clean, scrollable UI). */
@Composable
private fun InstallmentCard(installment: InstallmentResult) {
    ClayCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Payments, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Simulasi Kredit", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MiniInfo("DP ${installment.dpLabel}".trim(), "Rp ${formatPriceOnly(installment.dpAmount)}")
                MiniInfo("Per Hari", "Rp ${formatPriceOnly(installment.perHari)}")
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text("Angsuran per Tenor", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            installment.tenors.forEachIndexed { index, tenor ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 9.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${tenor.months} Bulan", style = MaterialTheme.typography.bodyMedium)
                    Text("Rp ${formatPriceOnly(tenor.monthlyAmount)} / bln", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
                if (index < installment.tenors.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Harga Normal", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Rp ${formatPriceOnly(installment.normalPrice)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textDecoration = TextDecoration.LineThrough)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Harga Promo", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text("Rp ${formatPriceOnly(installment.promoPrice)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun RowScope.MiniInfo(label: String, value: String) {
    Surface(modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(text = value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun BranchStockCard(branches: List<BranchStockEntity>) {
    ClayCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(vertical = 6.dp)) {
            branches.forEachIndexed { index, branch ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = DealerAlias.label(branch.kodeDealer),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    // Aging stok per dealer (kolom Kondisi web Stok All Cabang).
                    branch.umurHari?.let { umur ->
                        AgingBadge(umur)
                        Spacer(modifier = Modifier.width(10.dp))
                    }
                    Text(
                        text = "${branch.stok.toInt()} unit",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (branch.stok > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (index < branches.lastIndex) {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                }
            }
        }
    }
}

/**
 * Captures just the flyer region into a bitmap. Prefers [PixelCopy] which copies the
 * already-rendered window pixels on the render thread and delivers the result via callback — so the
 * heavy pixel work never blocks the UI thread (the old `View.draw(Canvas)` path allocated a
 * full-screen bitmap and rasterised the whole view tree synchronously on main, briefly freezing the
 * UI on tap). Falls back to the software-draw path when no host Activity/Window is reachable.
 *
 * **Batas API 26, bukan 24.** Yang dipakai di sini adalah overload
 * `PixelCopy.request(Window, Rect, Bitmap, …)` — overload ber-`srcRect` itu baru ada di API 26
 * (Oreo), sementara `minSdk` app ini 24. Tanpa penjagaan versi, HP Android 7.0/7.1 di lapangan
 * mendapat `NoSuchMethodError` saat menekan "Buat Gambar"/"Kirim ke WA" — bukan gagal build,
 * melainkan crash di tangan pengguna. (Komentar lama di berkas ini dan di CLAUDE.md sempat
 * menulis "API 24+"; itu keliru untuk overload ini.) Di API 24–25 jalurnya turun ke
 * [legacyCapture] — lebih lambat dan sinkron di main thread, tapi jalan.
 */
private suspend fun captureBitmap(view: View, bounds: Rect): Bitmap? {
    if (view.width <= 0 || view.height <= 0) return null
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return legacyCapture(view, bounds)
    val window = view.findActivity()?.window ?: return legacyCapture(view, bounds)

    // boundsInRoot() is relative to the Compose root view; PixelCopy's source rect is in window
    // coordinates, so offset by the root view's position within the window.
    val locationInWindow = IntArray(2)
    view.getLocationInWindow(locationInWindow)
    val left = (bounds.left.toInt() + locationInWindow[0]).coerceIn(0, view.width - 1)
    val top = (bounds.top.toInt() + locationInWindow[1]).coerceIn(0, view.height - 1)
    val width = bounds.width.toInt().coerceIn(1, view.width - left)
    val height = bounds.height.toInt().coerceIn(1, view.height - top)

    val dest = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val srcRect = android.graphics.Rect(left, top, left + width, top + height)
    return suspendCancellableCoroutine { cont ->
        try {
            PixelCopy.request(
                window,
                srcRect,
                dest,
                { result -> cont.resume(if (result == PixelCopy.SUCCESS) dest else null) },
                Handler(Looper.getMainLooper())
            )
        } catch (_: IllegalArgumentException) {
            cont.resume(null)
        }
    }
}

/** Last-resort software capture (only when no Window is reachable). Runs on the caller's thread. */
private fun legacyCapture(view: View, bounds: Rect): Bitmap? {
    if (view.width <= 0 || view.height <= 0) return null
    val full = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
    view.draw(Canvas(full))

    val left = bounds.left.toInt().coerceIn(0, full.width - 1)
    val top = bounds.top.toInt().coerceIn(0, full.height - 1)
    val width = bounds.width.toInt().coerceIn(1, full.width - left)
    val height = bounds.height.toInt().coerceIn(1, full.height - top)
    return Bitmap.createBitmap(full, left, top, width, height)
}

/** Unwraps the host Activity from a (possibly wrapped) Compose view context. */
private fun View.findActivity(): Activity? {
    var ctx = context
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

private fun formatRupiah(value: Double): String {
    val rounded = value.toLong()
    val text = rounded.toString().reversed().chunked(3).joinToString(".").reversed()
    return "Rp $text"
}

private fun formatPriceOnly(value: Int): String {
    return value.toString().reversed().chunked(3).joinToString(".").reversed()
}
