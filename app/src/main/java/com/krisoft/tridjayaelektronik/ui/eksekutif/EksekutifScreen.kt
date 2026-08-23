package com.krisoft.tridjayaelektronik.ui.eksekutif

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.krisoft.tridjayaelektronik.data.model.EksekutifCabangDto
import com.krisoft.tridjayaelektronik.data.model.EksekutifPapanDto
import com.krisoft.tridjayaelektronik.ui.theme.ClayCard
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveErrorState
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveInlineError
import com.krisoft.tridjayaelektronik.ui.theme.TridjayaCollapsibleHeader
import com.krisoft.tridjayaelektronik.ui.theme.TridjayaPullRefresh

/**
 * Papan pantau eksekutif — layar pertama untuk superadmin.
 *
 * Empat pertanyaan dalam satu layar (penjualan, absensi, kecocokan SPK vs GS,
 * pemakaian sistem), lalu daftar cabang yang bisa diketuk untuk turun ke
 * karyawan.
 */
@Composable
fun EksekutifScreen(
    viewModel: EksekutifViewModel = hiltViewModel(),
    onBukaCabang: (String) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    val papan = state.papan

    TridjayaCollapsibleHeader(title = "Eksekutif") { modifier ->
        TridjayaPullRefresh(
            isRefreshing = state.isLoading && papan != null,
            onRefresh = viewModel::muat,
            modifier = modifier,
        ) {
            when {
                // Error state penuh HANYA saat tak ada apa pun untuk ditampilkan.
                // Kalau papan lama masih ada, ia tetap dirender dan galatnya
                // muncul sebagai banner — membuang data yang masih berguna cuma
                // karena satu penyegaran gagal adalah kemunduran, bukan
                // kehati-hatian.
                papan == null && state.error != null -> ExpressiveErrorState(
                    message = state.error!!,
                    onRetry = viewModel::muat,
                )

                // Spinner, bukan Box kosong — layar kosong tanpa tanda selama
                // satu round-trip terbaca sebagai gagal muat.
                papan == null -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { androidx.compose.material3.CircularProgressIndicator() }
                else -> IsiPapan(
                    papan = papan,
                    rentang = state.rentang,
                    error = state.error,
                    onPilihRentang = viewModel::pilihRentang,
                    onBukaCabang = onBukaCabang,
                )
            }
        }
    }
}

@Composable
private fun IsiPapan(
    papan: EksekutifPapanDto,
    rentang: EksekutifRentang,
    error: String?,
    onPilihRentang: (EksekutifRentang) -> Unit,
    onBukaCabang: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 8.dp,
            // ~100dp ruang bawah supaya pil navigasi mengambang tak menutupi
            // baris terakhir secara permanen — konvensi tiap layar scrollable
            // di app ini.
            bottom = 100.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            PemilihRentang(terpilih = rentang, onPilih = onPilihRentang)
        }
        item {
            Column {
                Text(
                    labelRentang(
                        RentangTanggal(papan.periode.start, papan.periode.end)
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    "${papan.periode.hariKerja} hari kerja · " +
                        labelKesegaran(papan.kesegaran.mirrorUmurDetik),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (error != null) {
            item { ExpressiveInlineError(message = error) }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                KartuAngka(
                    judul = "Omset",
                    nilai = formatRupiahRingkas(papan.penjualan.omset),
                    keterangan = "${papan.penjualan.transaksi} transaksi",
                    modifier = Modifier.weight(1f),
                )
                KartuAngka(
                    judul = "Unit besar",
                    nilai = "${papan.penjualan.unitBesar}",
                    keterangan = "≥ ambang barang besar",
                    modifier = Modifier.weight(1f),
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                KartuAngka(
                    judul = "Kehadiran",
                    nilai = formatPersen(papan.absensi.persenHadir),
                    keterangan = "${papan.absensi.karyawan} orang wajib absen",
                    modifier = Modifier.weight(1f),
                )
                KartuAngka(
                    judul = "Pakai sistem",
                    nilai = formatPersen(papan.pemakaianPersen),
                    keterangan = "${papan.karyawanTotal} karyawan aktif",
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item {
            ClayCard {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        "SPK dashboard vs GS",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(8.dp))
                    PanelKecocokan(papan.spkVsGs, papan.kesegaran.spkMatangJam)
                }
            }
        }

        item {
            ClayCard {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        "Kehadiran & alpa",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(6.dp))
                    BarisRincian("Hari hadir", "${papan.absensi.hadir}")
                    BarisRincian("Hari izin/off disetujui", "${papan.absensi.offHari}")
                    BarisRincian(
                        "Hari tanpa keterangan",
                        "${papan.absensi.alpa}",
                        warnaNilai = if (papan.absensi.alpa > 0) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
            }
        }

        item {
            Text(
                "Per cabang",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        items(papan.cabang, key = { it.kodeDealer }) { cabang ->
            BarisCabang(cabang = cabang, onKetuk = { onBukaCabang(cabang.kodeDealer) })
        }
    }
}

@Composable
private fun BarisCabang(cabang: EksekutifCabangDto, onKetuk: () -> Unit) {
    ClayCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onKetuk)) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    cabang.nama,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${cabang.kodeDealer} · ${cabang.karyawanAktif} karyawan",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    formatRupiahRingkas(cabang.penjualan.omset) +
                        " · ${cabang.penjualan.unitBesar} unit",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    "Hadir ${formatPersen(cabang.absensi.persenHadir)}" +
                        " · Pakai sistem ${formatPersen(cabang.pemakaianPersen)}" +
                        " · GS " + ringkasKecocokan(cabang),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Ringkasan satu baris memakai definisi PALING LONGGAR (nomor ketemu), dan
 * label "nomor" ditulis eksplisit supaya tak dikira angka kecocokan penuh.
 * Ketiga definisinya tetap utuh satu ketukan lebih dalam.
 */
private fun ringkasKecocokan(cabang: EksekutifCabangDto): String {
    val k = cabang.spkVsGs
    if (k.dinilai <= 0) return "belum ada SPK dinilai"
    val persen = Math.round(k.cocokNomor * 1000.0 / k.dinilai) / 10.0
    return "${formatPersen(persen)} nomor"
}
