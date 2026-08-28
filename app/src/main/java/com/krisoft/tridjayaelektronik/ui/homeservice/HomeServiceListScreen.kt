package com.krisoft.tridjayaelektronik.ui.homeservice

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.PriorityHigh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.krisoft.tridjayaelektronik.data.model.HsTicketDto
import com.krisoft.tridjayaelektronik.ui.theme.ClayCard
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveEmptyState
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveErrorState
import com.krisoft.tridjayaelektronik.ui.theme.ScrollableCenter
import com.krisoft.tridjayaelektronik.ui.theme.TridjayaCollapsibleHeader
import com.krisoft.tridjayaelektronik.ui.theme.TridjayaPullRefresh

/**
 * Daftar tiket komplain. SATU layar dipakai empat peran ([HsMode]) — papan CS,
 * tugas teknisi, antrian tarik unit, dan tugas driver — karena isinya sama
 * (kartu tiket → detail); yang berbeda cuma permintaan ke server dan status
 * mana yang dianggap "aktif". Aksinya semua ada di layar detail.
 */
@Composable
fun HomeServiceListScreen(
    mode: HsMode,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    viewModel: HomeServiceListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    // `mode` sebagai kunci: satu ViewModel bisa dipakai ulang oleh route lain
    // dalam tab yang sama, jadi jangan andalkan `Unit`.
    LaunchedEffect(mode) { viewModel.muat(mode) }

    TridjayaCollapsibleHeader(title = mode.judul, onBack = onBack) { contentModifier ->
        val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        TridjayaPullRefresh(
            isRefreshing = state.loading && state.items.isNotEmpty(),
            onRefresh = { viewModel.muat(mode) },
            modifier = contentModifier,
        ) {
            when {
                state.loading && state.items.isEmpty() && state.lainnya.isEmpty() ->
                    ScrollableCenter { CircularProgressIndicator() }

                state.error != null && state.items.isEmpty() ->
                    ScrollableCenter {
                        ExpressiveErrorState(
                            message = state.error ?: "Gagal memuat daftar komplain.",
                            onRetry = { viewModel.muat(mode) },
                        )
                    }

                state.items.isEmpty() && state.lainnya.isEmpty() ->
                    ScrollableCenter {
                        ExpressiveEmptyState(
                            icon = {
                                Icon(
                                    Icons.Rounded.Build,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(30.dp),
                                )
                            },
                            title = "Belum ada tiket di sini.",
                            subtitle = when (mode) {
                                HsMode.TRIASE -> "Komplain baru akan muncul otomatis."
                                HsMode.TEKNISI -> "Belum ada kunjungan yang ditugaskan ke kamu."
                                HsMode.TARIK -> "Tak ada permintaan penarikan unit."
                                HsMode.DRIVER -> "Belum ada unit yang harus kamu ambil."
                                // Bukan antrian kerja: kosong di sini berarti dia
                                // memang belum pernah melapor, bukan "tak ada
                                // pekerjaan". Kalimatnya menyebut jalan masuknya.
                                HsMode.SAYA_LAPOR ->
                                    "Kamu belum pernah melaporkan komplain. Pakai \"Lapor Komplain\" untuk membuatnya."
                            },
                        )
                    }

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp + navBottom,
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item(key = "hitungan") {
                        Text(
                            "${state.items.size} tiket menunggu",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    items(state.items, key = { it.id }) { tiket ->
                        KartuTiket(tiket = tiket, mode = state.mode, onClick = { onOpen(tiket.id) })
                    }
                    if (state.lainnya.isNotEmpty()) {
                        item(key = "judul-lainnya") {
                            // Tiket yang sudah berpindah tangan/selesai tetap
                            // ditampilkan — CS sering perlu membuka yang barusan
                            // ditugaskan untuk mengoreksi, dan menyembunyikannya
                            // memaksa mereka kembali ke web.
                            Text(
                                "Tiket lain",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        items(state.lainnya, key = { "lain-${it.id}" }) { tiket ->
                            KartuTiket(tiket = tiket, mode = state.mode, onClick = { onOpen(tiket.id) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KartuTiket(tiket: HsTicketDto, mode: HsMode, onClick: () -> Unit) {
    ClayCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    tiket.nomorTiket.ifBlank { "(tanpa nomor)" },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (perluPerhatian(tiket)) {
                    Icon(
                        Icons.Rounded.PriorityHigh,
                        contentDescription = "Perlu perhatian",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.size(6.dp))
                }
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(50),
                ) {
                    Text(
                        labelStatusHs(tiket.status),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                    )
                }
            }

            Spacer(Modifier.height(6.dp))
            Text(
                buildString {
                    // Jumlah barang ditulis DI DEPAN, bukan di ekor: barisnya
                    // `maxLines = 1` + Ellipsis dan nama barang ERP justru
                    // panjang, jadi ekornya akan terpotong persis pada tiket yang
                    // paling perlu ditandai. Kolom tunggal `namaBarang` cuma
                    // SNAPSHOT BARANG PERTAMA (migrasi 214) — tanpa angka ini
                    // teknisi berangkat membawa persiapan untuk satu unit.
                    if (tiket.items.size > 1) append("${tiket.items.size} barang · ")
                    // Tiket tanpa data pembelian menyimpan namaBarang DAN
                    // noTransaksi sebagai string kosong (server memakai
                    // `unwrap_or_default`), jadi tanpa cadangan ini judul kartu
                    // terbit KOSONG — CS men-triase antrian tanpa satu pun
                    // petunjuk tiket mana yang perlu dilengkapi.
                    append(
                        tiket.namaBarang?.takeIf { it.isNotBlank() }
                            ?: tiket.noTransaksi.takeIf { it.isNotBlank() }
                            ?: "Barang belum diisi",
                    )
                },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // TIGA keadaan, bukan dua. `terverifikasi` menjawab "cocok dengan
            // mirror penjualan?" dan server SENGAJA tak pernah menaikkannya saat
            // CS melengkapi tiket — memakainya sebagai "sudah lengkap?" membuat
            // peringatan merah menyala selamanya walau CS sudah mengerjakannya.
            // Kalimat PERINTAH hanya untuk yang bisa menindaklanjuti (triase CS,
            // tiket belum final); selebihnya cukup FAKTA netral.
            if (!tiket.terverifikasi) {
                val sudahDilengkapi = !tiket.dilengkapiAt.isNullOrBlank()
                val bolehDitindak = mode == HsMode.TRIASE && tiket.status !in HS_STATUS_FINAL
                Text(
                    when {
                        sudahDilengkapi ->
                            "Dilengkapi CS" + (tiket.dilengkapiNama?.takeIf { it.isNotBlank() }?.let { " oleh $it" } ?: "")
                        bolehDitindak -> "Belum terverifikasi — cocokkan barang & garansi dari foto kwitansi"
                        else -> "Belum terverifikasi"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (sudahDilengkapi) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
            }
            Text(
                tiket.deskripsi,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(Modifier.height(6.dp))
            Text(
                buildString {
                    append(tiket.customerNama?.takeIf { it.isNotBlank() } ?: "Konsumen —")
                    tiket.customerHp?.takeIf { it.isNotBlank() }?.let { append(" · $it") }
                    // `umurJam` dari server apa adanya (lihat catatan bias zona
                    // di HsTicketDto) — nilai negatif/null diperlakukan "baru".
                    tiket.umurJam?.takeIf { it > 0 }?.let { append(" · ${it} jam") }
                    if (tiket.dalamGaransi == true) append(" · garansi")
                    // Penginput ikut di kartu daftar, bukan cuma di detail: CS
                    // yang men-triase antrian menilai kelayakan tiket sebelum
                    // membukanya, dan "siapa yang mengisi" bagian dari itu.
                    tiket.pelaporNama?.takeIf { it.isNotBlank() }?.let { append(" · oleh $it") }
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
