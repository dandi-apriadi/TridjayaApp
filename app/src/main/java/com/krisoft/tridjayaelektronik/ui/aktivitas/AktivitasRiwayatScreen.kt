package com.krisoft.tridjayaelektronik.ui.aktivitas

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.krisoft.tridjayaelektronik.data.model.AktivitasItemDto
import com.krisoft.tridjayaelektronik.ui.theme.ClayCard
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveEmptyState
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveErrorState
import com.krisoft.tridjayaelektronik.ui.theme.ScrollableCenter
import com.krisoft.tridjayaelektronik.ui.theme.TridjayaCollapsibleHeader
import com.krisoft.tridjayaelektronik.ui.theme.TridjayaPullRefresh

/**
 * Riwayat aktivitas MILIK SENDIRI, per tanggal.
 *
 * Padanan `KaryawanAktivitasHistoryPage.tsx` di web — dan seperti di web, ia
 * BUKAN menu tersendiri melainkan sub-layar yang dibuka dari layar Input
 * Aktivitas. Karena itu ia tak punya entri di `ActivityRegistry`: hak
 * melihatnya sudah dijawab oleh hak membuka layar induknya, dan menambah kunci
 * kemampuan baru hanya menambah satu daftar lagi yang bisa melenceng.
 *
 * TAK ADA jalur tulis di layar ini — lihat [AktivitasRiwayatPlan.kt] untuk
 * kenapa navigasi tanggal tidak boleh hidup di layar yang punya tombol kirim.
 */
@Composable
fun AktivitasRiwayatScreen(
    onBack: () -> Unit,
    viewModel: AktivitasRiwayatViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val token = viewModel.bearerToken()

    TridjayaCollapsibleHeader(title = "Riwayat Aktivitas", onBack = onBack) { contentModifier ->
        val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        TridjayaPullRefresh(
            isRefreshing = state.loading && state.items.isNotEmpty(),
            onRefresh = viewModel::muat,
            modifier = contentModifier,
        ) {
            Column(Modifier.fillMaxSize()) {
                // Baris tanggal DI LUAR percabangan di bawah — kalau ia ikut
                // hilang saat daftar kosong/gagal, orang yang menggeser ke
                // tanggal tanpa data tak punya jalan kembali. Pola sama
                // `PeriodeFilterRow` di layar antrian pengiriman.
                BarisTanggal(
                    tanggal = state.tanggal,
                    bolehMaju = bolehMajuTanggal(state.tanggal, state.hariIni),
                    onGeser = viewModel::geserHari,
                )
                when {
                    state.loading && state.items.isEmpty() -> ScrollableCenter {
                        Text(
                            "Memuat riwayat…",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    state.error != null && state.items.isEmpty() -> ScrollableCenter {
                        ExpressiveErrorState(
                            message = state.error ?: "Gagal memuat riwayat",
                            onRetry = viewModel::muat,
                        )
                    }

                    state.items.isEmpty() -> ScrollableCenter {
                        ExpressiveEmptyState(
                            icon = {
                                Icon(
                                    Icons.Rounded.History,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(30.dp),
                                )
                            },
                            title = "Belum ada aktivitas",
                            // Sebut TANGGALNYA: kosong karena hari itu memang
                            // tak mengisi ≠ kosong karena datanya hilang.
                            subtitle = "Tidak ada laporan aktivitas yang tercatat pada " +
                                "${state.tanggal}. Geser tanggal di atas untuk melihat hari lain.",
                        )
                    }

                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp + navBottom,
                        ),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        item { RingkasanHari(ringkasRiwayat(state.items, state.positions)) }
                        items(state.items, key = { it.id }) { item ->
                            BarisRiwayat(item = item, token = token)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BarisTanggal(tanggal: String, bolehMaju: Boolean, onGeser: (Int) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        IconButton(onClick = { onGeser(-1) }) {
            Icon(Icons.Rounded.ChevronLeft, contentDescription = "Hari sebelumnya")
        }
        Text(
            tanggal,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        // Dimatikan di hari ini, bukan disembunyikan: tombol yang lenyap
        // terbaca sebagai layar rusak, tombol mati menjelaskan batasnya.
        IconButton(onClick = { onGeser(1) }, enabled = bolehMaju) {
            Icon(Icons.Rounded.ChevronRight, contentDescription = "Hari berikutnya")
        }
    }
}

@Composable
private fun RingkasanHari(r: RingkasanRiwayat) {
    ClayCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Text(
                "${r.total} aktivitas terkirim",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                buildString {
                    append("${r.disetujui} disetujui · ${r.ditolak} ditolak · ${r.menunggu} menunggu")
                    // Rata-rata hanya disebut kalau ADA yang sudah dinilai.
                    // Menulis "0" untuk hari yang PIC-nya belum sempat menilai
                    // adalah vonis yang belum pernah dijatuhkan siapa pun.
                    r.rataSkor?.let { append(" · rata-rata $it") }
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (r.menunggu > 0) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Baris yang menunggu belum punya nilai — bukan nilai nol.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun BarisRiwayat(item: AktivitasItemDto, token: String?) {
    ClayCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    // +1: `jobdeskIndex` berbasis nol, sedangkan layar input
                    // menomori baris mulai 1. Dua penomoran untuk daftar yang
                    // sama membuat orang menyebut aktivitas yang salah saat
                    // bertanya ke PIC.
                    "${item.jobdeskIndex + 1}. ${item.jobdeskText}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                ChipStatus(item.reviewStatus, item.score)
            }
            item.employeeNote?.takeIf { it.isNotBlank() }?.let { catatan ->
                Spacer(Modifier.height(4.dp))
                Text(
                    catatan,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // Komentar PIC adalah SATU-SATUNYA keterangan kenapa sebuah baris
            // ditolak — di web ia ditampilkan, dan tanpa cerminannya di sini
            // karyawan cuma melihat nilai nol tanpa tahu apa yang salah.
            item.reviewerComment?.takeIf { it.isNotBlank() }?.let { komentar ->
                Spacer(Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "Catatan penilai: $komentar",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    )
                }
            }
            if (buktiVideo(item)) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Bukti berupa video — buka lewat web untuk memutarnya.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                parseEvidenceUrls(item.evidenceUrl).forEach { mentah ->
                    evidenceImageUrl(mentah)?.let { url ->
                        Spacer(Modifier.height(8.dp))
                        AuthedEvidence(url = url, token = token)
                    }
                }
            }
        }
    }
}

@Composable
private fun ChipStatus(status: String, skor: Int?) {
    val warna = when (status) {
        "approved" -> Color(0xFF12B76A)
        "rejected" -> MaterialTheme.colorScheme.error
        else -> Color(0xFFB5670C)
    }
    Surface(color = warna.copy(alpha = 0.14f), shape = RoundedCornerShape(50)) {
        Text(
            // Skor hanya ikut kalau baris memang sudah dinilai — "Menunggu 0"
            // adalah dua pernyataan yang bertabrakan.
            labelStatusReview(status) + if (status != "pending" && skor != null) " · $skor" else "",
            color = warna,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}
