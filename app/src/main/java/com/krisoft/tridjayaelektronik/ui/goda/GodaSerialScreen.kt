package com.krisoft.tridjayaelektronik.ui.goda

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ElectricBike
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.krisoft.tridjayaelektronik.data.model.GodaBarisDto
import com.krisoft.tridjayaelektronik.ui.deliveryflow.BarcodeScanButton
import com.krisoft.tridjayaelektronik.ui.deliveryflow.CabangSelector
import com.krisoft.tridjayaelektronik.ui.theme.ClayCard
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveEmptyState
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveErrorState
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveFilledButton
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveTextField
import com.krisoft.tridjayaelektronik.ui.theme.SkeletonCard
import com.krisoft.tridjayaelektronik.ui.theme.TridjayaCollapsibleHeader
import com.krisoft.tridjayaelektronik.ui.theme.TridjayaPullRefresh

private val LengkapColor = Color(0xFF12B76A)
private val KurangColor = Color(0xFFF79009)

/**
 * **SN Goda** - daftarkan serial number unit sepeda listrik GODA sambil
 * memegang unitnya: pilih cabang, cari barangnya, scan barcode di rangka.
 *
 * Layar ini hanya MENAMBAH. Mengganti/menghapus SN yang sudah terdaftar tetap
 * lewat web (menu List Goda): penggantian menghapus nilai lama secara permanen
 * karena registry-nya tak punya tabel riwayat, dan tombol yang bisa melakukan
 * itu tak pantas berada di layar yang dipakai sambil berjalan di gudang.
 */
@Composable
fun GodaSerialScreen(
    onBack: () -> Unit,
    viewModel: GodaSerialViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.mulai() }

    val tersaring = remember(state.baris, state.cari, state.hanyaBelumLengkap) {
        saringBaris(state.baris, state.cari, state.hanyaBelumLengkap)
    }

    TridjayaCollapsibleHeader(title = "SN Goda", onBack = onBack) { contentModifier ->
        val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        Column(modifier = contentModifier.fillMaxSize()) {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                CabangSelector(
                    selected = state.kodeDealer,
                    onSelect = viewModel::pilihCabang,
                    label = "Cabang gudang *"
                )
                Spacer(modifier = Modifier.height(10.dp))
                ExpressiveTextField(
                    value = state.cari,
                    onValueChange = viewModel::onCariChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = "Cari nama, kode, atau tipe unit"
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = state.hanyaBelumLengkap,
                    onClick = viewModel::toggleBelumLengkap,
                    label = { Text("SN belum lengkap") }
                )
                if (state.baris.isNotEmpty()) {
                    Text(
                        text = "SN ${state.totalSn} / stok ${state.totalStok}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Umur SALINAN stok, bukan "data GS sekarang" - server modul ini
            // membaca `erp_mirror_stok` apa adanya, dan layar wajib mengatakannya.
            if (state.baris.isNotEmpty()) {
                Text(
                    text = state.syncedAt?.let { "Stok salinan GS per $it" }
                        ?: "Umur salinan stok tidak diketahui",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                )
            }

            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    state.kodeDealer.isBlank() -> PesanTengah(
                        title = "Pilih cabang dulu",
                        subtitle = "Serial number ditulis ke cabang yang dipilih di atas, jadi ia tak ditebak dari profil."
                    )
                    state.loading && state.baris.isEmpty() -> {
                        Column(modifier = Modifier.padding(top = 4.dp)) {
                            repeat(5) { SkeletonCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }
                        }
                    }
                    state.error != null && state.baris.isEmpty() -> {
                        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                            ExpressiveErrorState(message = state.error ?: "Gagal memuat", onRetry = viewModel::muat)
                        }
                    }
                    tersaring.isEmpty() -> PesanTengah(
                        title = "Tidak ada unit GODA",
                        subtitle = if (state.baris.isEmpty()) {
                            "Cabang ini belum punya baris stok sepeda listrik GODA di salinan GS."
                        } else {
                            "Tidak ada yang cocok dengan pencarian atau saringan ini."
                        }
                    )
                    else -> TridjayaPullRefresh(isRefreshing = state.loading, onRefresh = viewModel::muat) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp + navBottom
                            ),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(tersaring, key = { it.kodeBarang }) { baris ->
                                KartuUnit(
                                    baris = baris,
                                    terbuka = state.kodeBarangTerpilih == baris.kodeBarang,
                                    state = state,
                                    onToggle = { viewModel.pilihBarang(baris.kodeBarang) },
                                    onEntriChange = viewModel::onEntriChange,
                                    onTambah = viewModel::tambah
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
private fun PesanTengah(title: String, subtitle: String) {
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        ExpressiveEmptyState(
            icon = { Icon(Icons.Rounded.ElectricBike, contentDescription = null) },
            title = title,
            subtitle = subtitle
        )
    }
}

@Composable
private fun KartuUnit(
    baris: GodaBarisDto,
    terbuka: Boolean,
    state: GodaSnUiState,
    onToggle: () -> Unit,
    onEntriChange: (String) -> Unit,
    onTambah: (String) -> Unit
) {
    ClayCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            // `clickable` HANYA di kepala kartu, bukan di seluruh kartu: kalau ia
            // membungkus panel input juga, mengetuk ruang kosong di sekitar field
            // menutup panelnya — dan menutup panel membuang SN yang sedang
            // diketik/di-scan.
            Column(modifier = Modifier.fillMaxWidth().clickable { onToggle() }) {
                Text(
                    text = baris.namaBarang.ifBlank { baris.kodeBarang },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = listOf(baris.kodeBarang, baris.tipe).filter { it.isNotBlank() }.joinToString(" - "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "SN ${baris.jumlahSn} / stok ${baris.stok}",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = if (belumLengkap(baris)) KurangColor else LengkapColor
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (terbuka) "Tutup" else "Scan / tambah SN",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            AnimatedVisibility(visible = terbuka) {
                Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    Text(
                        text = "Scan barcode di rangka unit, atau ketik serialnya kalau barcode-nya rusak.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ExpressiveTextField(
                            value = state.entri,
                            onValueChange = onEntriChange,
                            modifier = Modifier.weight(1f),
                            placeholder = "Serial number unit",
                            isError = state.entriError != null,
                            enabled = !state.menyimpan,
                            // Hasil scan masuk lewat PINTU YANG SAMA dengan tombol
                            // Simpan supaya normalisasi & pemeriksaan duplikat tak
                            // punya dua jalur yang bisa menyimpang.
                            trailingIcon = {
                                BarcodeScanButton(contentDescription = "Scan serial unit GODA") { onTambah(it) }
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        ExpressiveFilledButton(
                            onClick = { onTambah(state.entri) },
                            enabled = state.entri.isNotBlank() && !state.menyimpan
                        ) {
                            Text(if (state.menyimpan) "..." else "Simpan")
                        }
                    }
                    state.entriError?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                    state.entriPeringatan?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = KurangColor,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                    state.pesanSukses?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = LengkapColor,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }

                    if (baris.serials.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Sudah terdaftar (${baris.serials.size})",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        // Daftar SN dirender di dalam kartu (bukan daftar bergulir
                        // sendiri): LazyColumn bersarang di dalam LazyColumn tak
                        // punya tinggi terhingga dan meledak saat runtime.
                        baris.serials.forEach { sn ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Rounded.QrCodeScanner,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = sn.serialNumber, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}
