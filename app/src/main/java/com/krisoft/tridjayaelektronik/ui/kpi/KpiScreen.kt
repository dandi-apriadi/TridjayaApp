package com.krisoft.tridjayaelektronik.ui.kpi

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.krisoft.tridjayaelektronik.data.model.KpiDetailData
import com.krisoft.tridjayaelektronik.data.model.KpiItemDto
import com.krisoft.tridjayaelektronik.data.model.KpiBracketDto
import com.krisoft.tridjayaelektronik.data.model.dampakAlasanKpi
import com.krisoft.tridjayaelektronik.data.model.judulAlasanKpi
import com.krisoft.tridjayaelektronik.data.model.KpiKaryawanRowDto
import com.krisoft.tridjayaelektronik.data.model.formatKpiNumber
import com.krisoft.tridjayaelektronik.data.model.formatPeriodeId
import com.krisoft.tridjayaelektronik.data.model.kpiKekurangan
import com.krisoft.tridjayaelektronik.ui.home.formatRupiah
import com.krisoft.tridjayaelektronik.ui.theme.ClayCard
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveEmptyState
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveErrorState
import com.krisoft.tridjayaelektronik.ui.theme.SkeletonCard
import com.krisoft.tridjayaelektronik.ui.theme.TridjayaCollapsibleHeader
import com.krisoft.tridjayaelektronik.ui.theme.TridjayaPullRefresh

private val RewardColor = Color(0xFF12B76A)
private val PunishmentColor = Color(0xFFF04438)
private val PendingColor = Color(0xFFF79009)

/** Warna capaian: hijau ≥100%, kuning ≥80%, merah di bawahnya — sama ambang
 *  dengan bracket rupiah backend (`score_bracket`) supaya isyarat warnanya
 *  tidak bercerita lain dari angka yang dibayarkan. */
private fun achievementColor(achievement: Double): Color = when {
    achievement >= 1.0 -> RewardColor
    achievement >= 0.8 -> PendingColor
    else -> PunishmentColor
}

/**
 * KPI — capaian per indikator, dengan selisih menuju target di tiap baris
 * ("masih perlu dikejar"). Milik sendiri untuk semua orang; pemegang
 * `kpi.manage` mendapat daftar karyawan di bawahnya dan bisa membuka KPI
 * masing-masing.
 */
@Composable
fun KpiScreen(
    onBack: () -> Unit,
    viewModel: KpiViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.start() }

    val viewing = state.viewing
    if (viewing != null) BackHandler { viewModel.closeKaryawan() }

    TridjayaCollapsibleHeader(
        title = viewing?.nama ?: "KPI Saya",
        onBack = { if (viewing != null) viewModel.closeKaryawan() else onBack() }
    ) { contentModifier ->
        val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        TridjayaPullRefresh(
            isRefreshing = state.loading && state.detail != null,
            // Meniru shiftMonth(): daftar karyawan ikut dimuat ulang HANYA di layar
            // "KPI Saya" — saat sedang membuka KPI orang lain daftar itu tak dirender.
            onRefresh = {
                viewModel.loadDetail()
                if (viewing == null && state.canManage) viewModel.loadList()
            },
            modifier = contentModifier
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp + navBottom),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item { BetaNotice() }

                item {
                    PeriodeSwitcher(
                        periode = state.periode,
                        enabled = !state.loading && state.periode.isNotEmpty(),
                        onShift = viewModel::shiftMonth
                    )
                }

                val detail = state.detail
                when {
                    state.loading && detail == null -> item {
                        Column { repeat(4) { SkeletonCard(modifier = Modifier.padding(vertical = 4.dp)) } }
                    }
                    state.error != null && detail == null -> item {
                        Box(Modifier.fillMaxWidth().padding(24.dp), Alignment.Center) {
                            ExpressiveErrorState(
                                message = state.error ?: "Gagal memuat",
                                onRetry = viewModel::loadDetail
                            )
                        }
                    }
                    detail != null -> {
                        item { SummaryCard(detail) }
                        if (detail.items.isEmpty()) {
                            item {
                                Box(Modifier.fillMaxWidth().padding(24.dp), Alignment.Center) {
                                    ExpressiveEmptyState(
                                        icon = { Icon(Icons.Rounded.Insights, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                        title = "Belum ada posisi KPI",
                                        subtitle = "HR belum menetapkan posisi penilaian untuk periode ini."
                                    )
                                }
                            }
                        } else {
                            items(detail.items, key = { it.indicatorId }) { IndicatorCard(it) }
                        }
                    }
                }

                // Daftar karyawan hanya untuk pemegang `kpi.manage`, dan hanya di
                // layar "KPI Saya" — saat sedang membuka KPI orang lain, daftar di
                // bawahnya cuma bikin bingung siapa yang sedang dilihat.
                if (state.canManage && viewing == null) {
                    item {
                        Text(
                            // "KPI Seluruh Karyawan" — sama dengan nama papan di web.
                            // Daftar ini duduk DI DALAM layar "KPI Saya", jadi judul
                            // "KPI Karyawan" saja membuat dua hal berbeda berjejer
                            // dengan nama yang nyaris sama di satu layar.
                            text = "KPI Seluruh Karyawan",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 12.dp, bottom = 2.dp)
                        )
                    }
                    state.listError?.let { message ->
                        item {
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    items(state.list, key = { it.karyawanId }) { row ->
                        KaryawanRow(row, onClick = { viewModel.openKaryawan(row) })
                    }
                }
            }
        }
    }
}

/**
 * Penanda BETA layar KPI.
 *
 * Modul KPI masih tahap pengembangan: fitur input aktivitas belum berjalan
 * sehingga sebagian indikator diisi dari rekap manual, sebagian karyawan belum
 * punya posisi KPI, dan angkanya BUKAN penilaian resmi. Tanpa penanda ini
 * karyawan membaca skor di layarnya sebagai vonis atas kerjanya.
 *
 * Cerminan `KpiBetaBanner` di web (`components/dashboard/KpiHasilBadge.tsx`) —
 * kalau teksnya berubah, ubah keduanya supaya tak ada dua versi kebenaran.
 */
@Composable
private fun BetaNotice() {
    ClayCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "BETA",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Black,
                color = PendingColor
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Halaman ini masih tahap pengembangan — angkanya belum data aktual dan " +
                    "belum bisa dipakai sebagai penilaian resmi.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PeriodeSwitcher(periode: String, enabled: Boolean, onShift: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        IconButton(onClick = { onShift(-1) }, enabled = enabled) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "Bulan sebelumnya")
        }
        Text(
            text = if (periode.isEmpty()) "…" else formatPeriodeId(periode),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(140.dp)
        )
        IconButton(onClick = { onShift(1) }, enabled = enabled) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "Bulan berikutnya")
        }
    }
}

@Composable
private fun SummaryCard(detail: KpiDetailData) {
    ClayCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            detail.karyawan?.let { karyawan ->
                Text(text = karyawan.nama, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                val sub = listOf(karyawan.jabatan, karyawan.divisi, karyawan.cabangName)
                    .filter { it.isNotBlank() }
                    .joinToString(" · ")
                if (sub.isNotBlank()) {
                    Text(
                        text = sub,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
            detail.position?.let { position ->
                Text(
                    text = position.judul + if (position.auto) " (otomatis dari masa kerja)" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            Text(
                text = "Total Capaian",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp)
            )
            Text(
                text = "${formatKpiNumber(detail.totalPct)}%",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = achievementColor(detail.totalScore)
            )

            Spacer(modifier = Modifier.height(8.dp))
            VerdictText(detail)
        }
    }
}

/** Vonis periode ini. Server menahan vonis sampai Σbobot terisi ≥ 0,5
 *  (`filled`) — jangan menghitung sendiri di sini, uangnya nyata. */
@Composable
private fun VerdictText(detail: KpiDetailData) {
    if (!detail.filled) {
        Text(
            text = "Penilaian belum lengkap — belum ada reward/punishment untuk periode ini.",
            style = MaterialTheme.typography.bodySmall,
            color = PendingColor
        )
        return
    }
    detail.insentif?.let { insentif ->
        val tint = if (insentif.pct >= 0) RewardColor else PunishmentColor
        Text(
            text = "Insentif ${formatKpiNumber(insentif.pct)}%",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = tint
        )
        insentif.komponen.forEach { komponen ->
            Text(
                text = "${komponen.label} → ${formatKpiNumber(komponen.pct)}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }
    detail.bracket?.let { bracket ->
        // Cabang `netral` WAJIB eksplisit dan didahulukan. Tanpa itu `kind`
        // apa pun selain "reward" jatuh ke else dan dirender "Punishment Rp 0"
        // MERAH — padahal model bonus per indikator tak punya denda sama sekali
        // dan mengirim "netral" justru untuk menyatakan itu. Orang yang cuma
        // tak dapat bonus terbaca seperti sedang dihukum, dan itu memicu
        // keberatan yang tak seharusnya ada.
        when {
            bracket.kind == "netral" -> {
                Text(
                    text = "Netral · ${formatRupiah(bracket.amount.toDouble())}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (bracket.modelBonus) {
                        // Rp 0 di model bonus BUKAN "pas target": `amount` =
                        // Σ(nominal kategori × bobot) dan nominalnya nol hanya
                        // untuk KURANG. Menyebutnya "tak ada reward maupun
                        // punishment" membacakan kebalikannya.
                        "Belum ada indikator yang mencapai kategori SEDANG (≥80%), " +
                            "atau belum ada yang dinilai."
                    } else {
                        // Pita netral 91-100% (migrasi 155) — hanya berlaku untuk
                        // vonis aturan lama di snapshot periode terkunci.
                        "Capaian 91–100%: tidak ada reward maupun punishment."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            else -> {
                val reward = bracket.kind == "reward"
                // Aturan LAMA menamainya REWARD, bukan Bonus. Memakai satu label
                // untuk keduanya membuat arsip Juli terbaca seolah lahir dari
                // model bonus hari ini — padahal angkanya tak bisa direproduksi
                // model itu.
                val label = when {
                    !reward -> "Punishment "   // hanya snapshot periode terkunci
                    bracket.modelBonus -> "Bonus "
                    else -> "Reward "
                }
                Text(
                    text = label + formatRupiah(bracket.amount.toDouble()),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (reward) RewardColor else PunishmentColor
                )
            }
        }
        // Pembanding "dari berapa" — DI LUAR `when`, jadi vonis Rp 0 ikut
        // mendapatkannya. Menaruhnya hanya di cabang non-netral membuat orang
        // yang justru kehilangan paling banyak (Rp 0 dari maksimal Rp 1,5 jt)
        // satu-satunya yang tak pernah melihat skalanya.
        bracket.bonusMaksRp?.takeIf { it > 0 }?.let { maks ->
            Text(
                text = "dari maksimal ${formatRupiah(maks.toDouble())}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        BracketAlasanList(bracket)
    }
}

/**
 * Rincian "kenapa bonusnya tidak penuh" — indikator mana yang kehilangan
 * rupiah, urut dari yang terbesar (server yang mengurutkan).
 *
 * **Dirender untuk KEDUA model**, dengan judul & satuan yang mengikuti model
 * yang melahirkan vonisnya ([KpiBracketDto.modelBonus]) — paritas dengan
 * `KpiBracketAlasan` di web.
 *
 * Versi pertama panel ini (2026-08-28 pagi) `return` lebih awal untuk snapshot
 * periode terkunci, dengan alasan yang BENAR sebagiannya: baris lama tak punya
 * `hilangRp`, dan membacanya sebagai `0` mencetak "−Rp 0" untuk SETIAP baris —
 * daftar yang terbaca "tak ada yang hilang" padahal sebabnya cuma tak terbaca.
 * Yang keliru adalah OBATNYA: web tidak menahan diri, ia mengganti SATUANNYA
 * (`dampakPct` + "poin"). Menahan diri berarti karyawan yang membuka periode
 * Juli/Agustus dari HP melihat "Punishment Rp 250.000" TANPA satu baris sebab,
 * sementara rekannya di web melihat daftar penyebabnya — denda rupiah yang tak
 * bisa dibantah dari kanal lapangan, persis kerugian yang panel ini ada untuk
 * menutup.
 *
 * Angka rupiah tanpa sebab tak bisa dibantah — itu alasan panel ini ada.
 * `alasan` kosong dirender sebagai pernyataan POSITIF **hanya di model bonus**:
 * di sana ia berarti tiap indikator BAGUS SEKALI, sedangkan di snapshot lama ia
 * cuma berarti rinciannya tak tersimpan.
 */
@Composable
private fun BracketAlasanList(bracket: KpiBracketDto) {
    // `alasan` kosong ditangani BERBEDA per model, dan itu bukan kerapian:
    // di model bonus ia berarti "tiap indikator BAGUS SEKALI" (kabar baik yang
    // layak dinyatakan), sedangkan di snapshot aturan lama ia cuma berarti
    // rinciannya memang tak tersimpan — mengumumkan "bonus penuh" di sana akan
    // mengarang kabar baik atas vonis yang bisa saja denda.
    if (bracket.alasan.isEmpty()) {
        if (!bracket.modelBonus) return
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "Semua indikator BAGUS SEKALI — bonus penuh.",
            style = MaterialTheme.typography.bodySmall,
            color = RewardColor
        )
        return
    }
    Spacer(modifier = Modifier.height(10.dp))
    Text(
        // Judul & satuan ikut MODEL yang melahirkan vonis ini — aturannya hidup
        // sebagai fungsi murni di `KpiModels.kt` supaya bisa diuji tanpa Compose.
        text = judulAlasanKpi(bracket.modelBonus),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(4.dp))
    bracket.alasan.forEach { a ->
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = a.indikator + if (a.dinilai) "" else " (belum dinilai)",
                style = MaterialTheme.typography.bodySmall,
                // "belum dinilai" BUKAN kinerja buruk — itu penilaian yang belum
                // dikerjakan HR, dan mewarnainya merah menyalahkan orang yang salah.
                color = if (a.dinilai) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    PendingColor
                },
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            val dampak = dampakAlasanKpi(
                baris = a,
                modelBonus = bracket.modelBonus,
                formatRupiah = { formatRupiah(it) },
                formatAngka = { formatKpiNumber(it) },
            )
            dampak?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    // Merah untuk KEDUA model, termasuk `dampakPct` positif —
                    // cerminan `text-error` web. Panel ini mendaftar apa yang
                    // MENEKAN vonis; mewarnai sebagiannya hijau membuat daftar
                    // terbaca sebagai campuran untung-rugi, bukan sebab.
                    color = PunishmentColor
                )
            }
            // Kategori (BAGUS SEKALI/CUKUP/…) ikut dirender seperti web: tanpa
            // itu baris "−Rp 450.000" tak memberi tahu SEBERAPA jauh capaiannya
            // dari yang membayar penuh.
            a.kategori?.takeIf { it.isNotBlank() }?.let {
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun IndicatorCard(item: KpiItemDto) {
    val kurang = kpiKekurangan(item)
    ClayCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.indikator,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                )
                SourceBadge(item.source)
            }
            item.keterangan?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${item.actual?.let(::formatKpiNumber) ?: "—"} / ${formatKpiNumber(item.target)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "${formatKpiNumber(item.achievement * 100)}%",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = achievementColor(item.achievement)
                )
            }

            LinearProgressIndicator(
                progress = { item.achievement.coerceIn(0.0, 1.0).toFloat() },
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                color = achievementColor(item.achievement),
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Text(
                text = kurang?.let { "Kurang ${formatKpiNumber(it)} lagi menuju target" } ?: "Target tercapai",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = if (kurang != null) PunishmentColor else RewardColor,
                modifier = Modifier.padding(top = 6.dp)
            )
            Text(
                text = "Bobot ${formatKpiNumber(item.bobot * 100)}% · kontribusi skor ${formatKpiNumber(item.hasilBobot * 100)}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
            // Baris RUPIAH per indikator — dan inilah yang benar-benar dibayar.
            // `hasilBobot` di atas cuma bahan skor total yang dipajang; sejak
            // model bonus per indikator, uangnya = Σ `bonusRp` tiap baris.
            // Keduanya `null` untuk snapshot periode terkunci (aturan lama tak
            // punya konsep ini), jadi barisnya menghilang, bukan mencetak Rp 0.
            item.bonusRp?.let { rp ->
                Text(
                    text = buildString {
                        item.kategori?.takeIf { it.isNotBlank() }?.let { append(it).append(" · ") }
                        append("bonus ").append(formatRupiah(rp.toDouble()))
                    },
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    // Rp 0 bukan hukuman — di model ini indikator yang belum
                    // mencapai SEDANG hanya GAGAL MENAMBAH, tak pernah mengurangi
                    // bonus indikator lain. Mewarnainya merah membacakan denda
                    // yang tak ada.
                    color = if (rp > 0) RewardColor else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun SourceBadge(source: String?) {
    val (label, tint) = when (source) {
        "auto" -> "Otomatis" to MaterialTheme.colorScheme.primary
        "manual" -> "Dinilai HR" to MaterialTheme.colorScheme.tertiary
        else -> "Belum dinilai" to PendingColor
    }
    Surface(color = tint.copy(alpha = 0.14f), shape = MaterialTheme.shapes.small) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun KaryawanRow(row: KpiKaryawanRowDto, onClick: () -> Unit) {
    ClayCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = row.nama, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                val sub = listOf(row.positionJudul.orEmpty(), row.cabangName)
                    .filter { it.isNotBlank() }
                    .joinToString(" · ")
                if (sub.isNotBlank()) {
                    Text(
                        text = sub,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
            Text(
                text = if (row.filled) "${formatKpiNumber(row.totalPct)}%" else "—",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = if (row.filled) achievementColor(row.totalPct / 100.0) else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}
