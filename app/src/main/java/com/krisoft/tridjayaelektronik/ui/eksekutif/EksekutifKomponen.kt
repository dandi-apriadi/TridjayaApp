package com.krisoft.tridjayaelektronik.ui.eksekutif

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.krisoft.tridjayaelektronik.data.model.EksekutifKecocokanDto
import com.krisoft.tridjayaelektronik.ui.theme.ClayCard

/** Kartu angka tunggal untuk baris ringkasan. */
@Composable
fun KartuAngka(
    judul: String,
    nilai: String,
    keterangan: String? = null,
    modifier: Modifier = Modifier,
) {
    ClayCard(modifier = modifier) {
        Column(Modifier.padding(14.dp)) {
            Text(
                judul,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                nilai,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (keterangan != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    keterangan,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Baris label–nilai untuk daftar rincian. */
@Composable
fun BarisRincian(
    label: String,
    nilai: String,
    modifier: Modifier = Modifier,
    warnaNilai: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            nilai,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = warnaNilai,
        )
    }
}

/** Bar persentase tipis. `persen` null → bar kosong + teks "—". */
@Composable
fun BarPersen(persen: Double?, modifier: Modifier = Modifier) {
    val rasio = ((persen ?: 0.0) / 100.0).coerceIn(0.0, 1.0).toFloat()
    LinearProgressIndicator(
        progress = { rasio },
        modifier = modifier.fillMaxWidth().height(6.dp),
        trackColor = MaterialTheme.colorScheme.surfaceVariant,
    )
}

/**
 * Panel kecocokan SPK vs GS — **tiga angka berdampingan, bukan satu**.
 *
 * Ini bukan pilihan tata letak melainkan cerminan keputusan produk: ketiga
 * definisi sama-sama benar dan berbeda jauh (28,2% / 56,6% / 69,2% pada survei
 * 16 Agt untuk populasi yang sama). Menampilkan satu berarti memilih angka mana
 * yang enak dilihat, dan menghilangkan satu-satunya bagian yang bisa
 * ditindaklanjuti: DI MANA kedua sistem berpisah.
 *
 * "Belum matang" dan "tanpa nomor" ditampilkan TERPISAH dan tidak pernah masuk
 * penyebut — keduanya bukan ketidakcocokan, dan penanganannya berbeda.
 */
@Composable
fun PanelKecocokan(k: EksekutifKecocokanDto, matangJam: Long, modifier: Modifier = Modifier) {
    Column(modifier) {
        if (k.dinilai <= 0) {
            Text(
                "Belum ada SPK yang bisa dinilai pada periode ini.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            BarisKecocokan("Nomor ketemu di GS", k.cocokNomor, k.dinilai)
            BarisKecocokan("Jumlah unit sama", k.cocokUnit, k.dinilai)
            BarisKecocokan("Nominal sama", k.cocokNominal, k.dinilai)
        }
        if (k.belumMatang > 0) {
            Spacer(Modifier.height(6.dp))
            Text(
                "${k.belumMatang} SPK belum dinilai — usianya di bawah $matangJam jam, " +
                    "kasir bisa jadi belum sempat menyalinnya ke GS.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (k.tanpaNomor > 0) {
            Spacer(Modifier.height(6.dp))
            Text(
                "${k.tanpaNomor} SPK tanpa nomor transaksi — tak ada yang bisa dicocokkan.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun BarisKecocokan(label: String, cocok: Long, dari: Long) {
    val persen = if (dari > 0) cocok * 1000 / dari / 10.0 else null
    Column(Modifier.padding(vertical = 4.dp)) {
        BarisRincian(label, "$cocok / $dari · ${formatPersen(persen)}")
        BarPersen(persen)
    }
}

/** Deretan chip pemilih rentang. */
@Composable
fun PemilihRentang(
    terpilih: EksekutifRentang,
    onPilih: (EksekutifRentang) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        EksekutifRentang.entries.forEach { pilihan ->
            FilterChip(
                selected = pilihan == terpilih,
                onClick = { onPilih(pilihan) },
                label = {
                    Text(
                        pilihan.label,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                    )
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}
