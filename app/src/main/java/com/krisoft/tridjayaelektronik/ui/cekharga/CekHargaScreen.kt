package com.krisoft.tridjayaelektronik.ui.cekharga

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Sell
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.krisoft.tridjayaelektronik.data.model.ProductPriceSearchItemDto
import com.krisoft.tridjayaelektronik.ui.theme.ClayCard
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveEmptyState
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveErrorState
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveTextField
import com.krisoft.tridjayaelektronik.ui.theme.TridjayaCollapsibleHeader
import com.krisoft.tridjayaelektronik.util.kunciUnik

private fun formatRupiah(value: Long): String {
    val text = kotlin.math.abs(value).toString().reversed().chunked(3).joinToString(".").reversed()
    return "Rp $text"
}

/**
 * Layar "Cek Harga" — cari kode/nama barang, lihat harga & ketersediaan cabang TERKINI
 * langsung dari server (`GET /inventory/product-price-search`). Beda dari Inventory penuh
 * (browse ber-filter/paging dari cache Room): ini pencarian ringan sekali-pakai, tanpa cache,
 * dipakai saat perlu jawaban cepat "berapa harganya sekarang" tanpa menyaring seluruh katalog.
 */
@Composable
fun CekHargaScreen(
    onBack: () -> Unit,
    viewModel: CekHargaViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    TridjayaCollapsibleHeader(title = "Cek Harga", onBack = onBack) { contentModifier ->
        val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        Column(modifier = contentModifier.fillMaxSize()) {
            ExpressiveTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
                placeholder = "Cari kode atau nama barang"
            )

            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    state.query.trim().length < 2 -> {
                        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                            ExpressiveEmptyState(
                                icon = { Icon(Icons.Rounded.Sell, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                title = "Cari harga produk",
                                subtitle = "Ketik minimal 2 huruf kode atau nama barang."
                            )
                        }
                    }
                    state.isSearching && state.items.isEmpty() -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    state.error != null && state.items.isEmpty() -> {
                        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                            ExpressiveErrorState(
                                message = state.error ?: "Gagal memuat",
                                onRetry = { viewModel.onQueryChange(state.query) }
                            )
                        }
                    }
                    state.hasSearched && state.items.isEmpty() -> {
                        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                            ExpressiveEmptyState(
                                icon = { Icon(Icons.Rounded.Sell, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                title = "Tidak ditemukan",
                                subtitle = "Tidak ada barang yang cocok dengan kata kunci ini."
                            )
                        }
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp + navBottom)
                        ) {
                            val kunci = kunciUnik(state.items) { it.kode }
                            itemsIndexed(state.items, key = { i, _ -> kunci.getOrElse(i) { "idx_$i" } }) { _, item ->
                                CekHargaRow(item, modifier = Modifier.padding(bottom = 8.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CekHargaRow(item: ProductPriceSearchItemDto, modifier: Modifier = Modifier) {
    ClayCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = item.nama, style = MaterialTheme.typography.titleSmall, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    Text(
                        text = "${item.kode} · ${item.kategori}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = formatRupiah(item.harga),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Text(
                    text = "Tersedia di ${item.tersediaDiCabang} cabang",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
    }
}
