package com.krisoft.tridjayaelektronik.ui.eksekutif

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import com.krisoft.tridjayaelektronik.data.model.EksekutifKepatuhanDto
import com.krisoft.tridjayaelektronik.data.model.EksekutifTargetDto
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
    // `1000.0` (Double), bukan `1000` — `cocok * 1000 / dari` dengan dua Long
    // adalah pembagian INTEGER yang MEMOTONG, sedangkan server MEMBULATKAN
    // (`domain::persen`). Dua angka untuk satu hal yang sama, berselisih
    // sampai 0,1 poin, tanpa ada yang bisa menjelaskan sebabnya.
    val persen = if (dari > 0) Math.round(cocok * 1000.0 / dari) / 10.0 else null
    Column(Modifier.padding(vertical = 4.dp)) {
        BarisRincian(label, "$cocok / $dari · ${formatPersen(persen)}")
        BarPersen(persen)
    }
}


/**
 * Kartu skor kepatuhan — **skor besar + keempat komponennya, selalu bersama**.
 *
 * Skor tunggal tanpa komponennya adalah angka yang tak bisa ditindaklanjuti:
 * "cabang ini 62" tak memberi tahu apakah yang bocor kehadiran, pengisian
 * aktivitas, bukti, atau penyalinan SPK ke GS. Dan tanpa pembilang/penyebut
 * mentah, angkanya cuma bisa dipercaya atau ditolak — tak bisa diperiksa.
 *
 * Komponen `null` ditulis "tidak berlaku", BUKAN 0%: driver memang tak punya
 * SPK, dan orang tanpa penempatan KPI memang tak punya penyebut aktivitas.
 */
@Composable
fun KartuKepatuhan(
    k: EksekutifKepatuhanDto,
    modifier: Modifier = Modifier,
    judul: String = "Skor kepatuhan",
) {
    val pita = pitaKepatuhan(k.skor)
    ClayCard(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        judul,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        // Kata, bukan cuma warna. Papan ini menilai orang, dan
                        // layar yang membedakan baik/buruk HANYA lewat
                        // hijau/merah tak terbaca oleh sebagian pembacanya.
                        pita.label + " · bobot terukur ${formatBobot(k.bobotTerpakai)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    formatSkor(k.skor),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = warnaPita(pita),
                )
            }
            Spacer(Modifier.height(10.dp))
            KomponenKepatuhan("Kehadiran", k.kehadiran, k.rincian.hadir, k.rincian.hariWajib, "hari")
            KomponenKepatuhan(
                "Aktivitas terisi",
                k.aktivitas,
                k.rincian.aktivitasTerisi,
                k.rincian.aktivitasWajib,
                "butir",
            )
            KomponenKepatuhan(
                "Bukti sah",
                k.bukti,
                k.rincian.buktiSah,
                k.rincian.buktiWajib,
                "butir",
            )
            // Label menyebut CAKUPAN dan PENYEBUTNYA, dan itu wajib: panel
            // "SPK dashboard vs GS" di layar yang sama menampilkan angka yang
            // BERBEDA untuk kalimat yang terdengar sama. Dua sebabnya nyata —
            // panel itu dikelompokkan per DEALER (termasuk SPK yang sales-nya
            // kosong atau sudah non-aktif) sementara kolom ini dijumlah dari
            // karyawan cabang; dan penyebut di sini IKUT menghitung SPK yang
            // nomornya tak pernah diisi, yang di panel itu sengaja di luar
            // `dinilai`. Tanpa label, dua angka yang sama-sama benar terbaca
            // sebagai satu angka yang salah.
            KomponenKepatuhan(
                "SPK bernomor & cocok (milik karyawan cabang)",
                k.spk,
                k.rincian.spkCocok,
                k.rincian.spkDinilai,
                "SPK",
            )
            if (k.skor == null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Belum ada satu pun komponen yang bisa diukur pada periode ini — " +
                        "itu berarti datanya belum ada, bukan berarti nilainya nol.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun KomponenKepatuhan(
    label: String,
    persen: Double?,
    pembilang: Long,
    penyebut: Long,
    satuan: String,
) {
    Column(Modifier.padding(vertical = 4.dp)) {
        BarisRincian(
            label,
            if (persen == null) {
                "tidak berlaku"
            } else {
                "$pembilang / $penyebut $satuan · ${formatPersen(persen)}"
            },
            warnaNilai = if (persen == null) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
        BarPersen(persen)
    }
}

@Composable
private fun warnaPita(pita: PitaKepatuhan): Color = when (pita) {
    PitaKepatuhan.PRIMA -> MaterialTheme.colorScheme.primary
    PitaKepatuhan.PANTAU -> MaterialTheme.colorScheme.tertiary
    PitaKepatuhan.PRIORITAS -> MaterialTheme.colorScheme.error
    PitaKepatuhan.TAK_TERUKUR -> MaterialTheme.colorScheme.onSurfaceVariant
}

/**
 * Panel target vs capaian.
 *
 * **Target kosong ditulis "belum diisi", bukan "Rp 0".** Halaman Target vs
 * Actual di web memilih jalan lain — mengarang target `aktual × 1,05` — sehingga
 * tiap barisnya selalu ≈95% dan tak mengukur apa pun. Yang dikerjakan di sini
 * kebalikannya: kalau targetnya belum pernah diketik, layar mengatakan begitu.
 *
 * Tanda **prorata** ikut ditulis saat rentangnya bukan bulan kalender penuh:
 * angka yang muncul adalah bagian target bulanan menurut HARI KERJA yang sudah
 * lewat, bukan angka yang pernah seseorang setujui.
 */
@Composable
fun PanelTarget(
    t: EksekutifTargetDto,
    aktualOmset: Long,
    aktualUnit: Long,
    modifier: Modifier = Modifier,
    tampilkanOmset: Boolean = true,
    tampilkanUnit: Boolean = true,
) {
    Column(modifier) {
        if (tampilkanOmset) {
            BarisTarget(
                label = "Omset",
                aktual = formatRupiahRingkas(aktualOmset),
                target = t.omset?.let { formatRupiahRingkas(it) },
                persen = t.capaianOmsetPersen,
            )
        }
        if (tampilkanUnit) {
            BarisTarget(
                label = "Unit besar",
                aktual = "$aktualUnit unit",
                target = t.unit?.let { "$it unit" },
                persen = t.capaianUnitPersen,
            )
        }
        val adaTarget = (tampilkanOmset && t.omset != null) || (tampilkanUnit && t.unit != null)
        if (adaTarget && t.prorata) {
            // Konteks "bulan penuh" dirakit dari baris yang BENAR-BENAR
            // ditampilkan. Menyebut `omsetBulanan` tanpa syarat membuat kartu
            // karyawan — yang hanya menampilkan baris UNIT — tak pernah
            // memunculkan konteks apa pun, karena target omset per orang
            // memang selalu null.
            val konteks = listOfNotNull(
                t.omsetBulanan?.takeIf { tampilkanOmset }
                    ?.let { "Target bulan penuh ${formatRupiahRingkas(it)}." },
                t.unitBulanan?.takeIf { tampilkanUnit }
                    ?.let { "Target bulan penuh $it unit." },
            ).joinToString(" ")
            Spacer(Modifier.height(6.dp))
            Text(
                "Target diprorata menurut hari kerja yang sudah lewat — " +
                    "periodenya tidak menutupi bulannya secara penuh." +
                    (if (konteks.isEmpty()) "" else " $konteks"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!adaTarget) {
            Spacer(Modifier.height(4.dp))
            Text(
                "Target periode ini belum lengkap. Satu bulan saja tanpa target " +
                    "membatalkan seluruh rentang — kalau tidak, omset seluruh " +
                    "periode akan dibandingkan dengan target sebagian periode. " +
                    "Angka capaian sengaja dikosongkan, bukan dihitung dari " +
                    "target karangan.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BarisTarget(label: String, aktual: String, target: String?, persen: Double?) {
    Column(Modifier.padding(vertical = 4.dp)) {
        BarisRincian(
            label,
            if (target == null) {
                "$aktual · target belum diisi"
            } else {
                "$aktual / $target · ${formatPersen(persen)}"
            },
            warnaNilai = when {
                persen == null -> MaterialTheme.colorScheme.onSurfaceVariant
                persen >= 100.0 -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurface
            },
        )
        // Bar sengaja DIPLAFON 100 supaya capaian 130% tak menggambar batang
        // yang melewati kotaknya; angka persisnya tetap tertulis di sebelahnya.
        BarPersen(persen?.coerceAtMost(100.0))
    }
}
