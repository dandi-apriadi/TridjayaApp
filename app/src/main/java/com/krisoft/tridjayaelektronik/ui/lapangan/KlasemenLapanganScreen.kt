package com.krisoft.tridjayaelektronik.ui.lapangan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Leaderboard
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.krisoft.tridjayaelektronik.data.model.MetrikLapanganDto
import com.krisoft.tridjayaelektronik.data.model.PesertaLapanganDto
import com.krisoft.tridjayaelektronik.domain.lapangan.FormatMetrik
import com.krisoft.tridjayaelektronik.ui.theme.ClayCard
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveEmptyState
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveErrorState
import com.krisoft.tridjayaelektronik.ui.theme.TridjayaCollapsibleHeader

/**
 * Papan klasemen kerja lapangan di HP.
 *
 * Orang yang dinilai papan ini — driver dan petugas PDI — tidak pegang laptop,
 * jadi HP adalah satu-satunya tempat mereka melihat posisinya sendiri. Isinya
 * sengaja sama persis dengan web, termasuk daftar "belum cukup data" dan catatan
 * batasnya: papan yang menyembunyikan syaratnya sendiri di satu kanal akan
 * dibaca sebagai papan yang berbeda.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KlasemenLapanganScreen(
    onBack: () -> Unit,
    viewModel: KlasemenLapanganViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    TridjayaCollapsibleHeader(title = "Klasemen Lapangan", onBack = onBack) { contentModifier ->
        val pullState = rememberPullToRefreshState()
        val papan = state.papan
        // Pola RankingListScreen: `isLoading` menutup load pertama MAUPUN refresh,
        // jadi dipakai mentah ia menumpuk spinner di atas daftar yang sudah ada.
        val isRefreshing = state.isLoading && papan != null
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.muat(forceRefresh = true) },
            state = pullState,
            modifier = contentModifier.fillMaxSize(),
            indicator = {
                PullToRefreshDefaults.Indicator(
                    modifier = Modifier.align(Alignment.TopCenter),
                    isRefreshing = isRefreshing,
                    state = pullState,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    color = MaterialTheme.colorScheme.primary,
                )
            },
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PeranPapan.entries.forEach { peran ->
                        FilterChip(
                            selected = state.peran == peran,
                            onClick = { viewModel.pilihPeran(peran) },
                            label = { Text(peran.judul) },
                        )
                    }
                }
                // Periode + KAPAN angkanya dihitung. Umur wajib terlihat:
                // repository menyajikan salinan cache (TTL 5 jam) dan, saat
                // jaringan mati, salinan BASI tanpa batas umur — papan kemarin
                // yang tampil tanpa tanda terbaca sebagai papan hari ini.
                Text(
                    buildString {
                        append(FormatMetrik.periode(papan?.periode ?: state.periode))
                        papan?.dihitungPada?.takeIf { it.isNotBlank() }?.let {
                            append(" · dihitung ")
                            append(it.replace('T', ' '))
                        }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )

                when {
                    state.isLoading && papan == null -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator() }

                    state.errorMessage != null && papan == null -> Box(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        ExpressiveErrorState(
                            message = state.errorMessage ?: "Tidak bisa memuat papan.",
                            // Coba-lagi WAJIB memaksa jaringan: retry yang membaca
                            // cache Room hanya mengulang kegagalan yang sama.
                            onRetry = { viewModel.muat(forceRefresh = true) },
                        )
                    }

                    papan == null -> Box(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        ExpressiveEmptyState(
                            icon = { Icon(Icons.Rounded.Leaderboard, contentDescription = null) },
                            title = "Belum ada peringkat",
                            subtitle = "Belum ada yang memenuhi ambang penilaian di periode ini.",
                        )
                    }

                    // Peserta kosong TIDAK boleh membuang `belumCukupData` dan
                    // `catatan`: justru pada papan kosong keduanya satu-satunya
                    // yang menjelaskan KENAPA kosong. Papan PDI dijamin kosong
                    // di hari-hari awal bulan (lantai 8 hari aktif), dan tanpa
                    // ini layarnya cuma berkata "belum ada peringkat".
                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 120.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (papan.peserta.isEmpty()) {
                            item {
                                ClayCard(modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        "Belum ada yang memenuhi ambang penilaian di periode ini.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(14.dp),
                                    )
                                }
                            }
                        }
                        items(papan.peserta, key = { it.karyawanId }) { peserta ->
                            BarisPeserta(peserta)
                        }

                        if (papan.belumCukupData.isNotEmpty()) {
                            item {
                                ClayCard(modifier = Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(14.dp)) {
                                        Text(
                                            "Belum cukup data untuk diperingkat",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        papan.belumCukupData.forEach {
                                            Text(
                                                "${it.nama} — ${it.alasan}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(top = 4.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Catatan batas WAJIB ikut, bukan hiasan: tanpanya angka
                        // di papan ini terbaca sebagai ukuran yang lebih luas
                        // dari sebenarnya.
                        items(papan.catatan) { catatan ->
                            Text(
                                catatan,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BarisPeserta(peserta: PesertaLapanganDto) {
    ClayCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    FormatMetrik.lencana(peserta.rank),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Column(Modifier.padding(start = 10.dp).fillMaxWidth()) {
                    Text(
                        peserta.nama,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    peserta.cabang?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            peserta.metrik.forEach { metrik -> BarisMetrik(metrik) }
        }
    }
}

@Composable
private fun BarisMetrik(metrik: MetrikLapanganDto) {
    val pecahan = FormatMetrik.pecahan(metrik)
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            // Titik menandai kolom yang benar-benar menentukan peringkat; sisanya
            // informasi. Tanpa penanda, pembaca wajar menyimpulkan seluruh angka
            // ikut memeringkat — dan di papan ini justru sebagian besar TIDAK.
            if (metrik.menentukanPeringkat) "• ${metrik.label}" else metrik.label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            buildString {
                append(FormatMetrik.nilai(metrik))
                if (pecahan != null && metrik.satuan == "persen") append(" ($pecahan)")
            },
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (metrik.menentukanPeringkat) FontWeight.Bold else FontWeight.Normal,
        )
    }
}
