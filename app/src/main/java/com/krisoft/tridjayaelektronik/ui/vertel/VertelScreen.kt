package com.krisoft.tridjayaelektronik.ui.vertel

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.PhoneInTalk
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import com.krisoft.tridjayaelektronik.data.model.BarisVertelDto
import com.krisoft.tridjayaelektronik.data.model.VertelHasil
import com.krisoft.tridjayaelektronik.data.model.VertelKanal
import com.krisoft.tridjayaelektronik.data.model.VertelPanggilanDto
import com.krisoft.tridjayaelektronik.ui.theme.ClayCard
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveEmptyState
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveErrorState
import com.krisoft.tridjayaelektronik.ui.theme.ScrollableCenter
import com.krisoft.tridjayaelektronik.ui.theme.TridjayaCollapsibleHeader
import com.krisoft.tridjayaelektronik.ui.theme.TridjayaPullRefresh

/**
 * "Verifikasi Telepon" — antrian konsumen yang KEMARIN membeli barang di atas
 * ambang harga, untuk ditelepon lalu dicatat hasilnya (`vertel.manage`).
 *
 * Satu layar, kartu yang bisa dibentangkan jadi form pencatatan. Tak ada layar
 * detail terpisah: `GET /vertel` sudah mengirim seluruh isi tiap baris.
 */
@Composable
fun VertelScreen(
    onBack: () -> Unit,
    viewModel: VertelViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val daftar = state.daftar

    TridjayaCollapsibleHeader(title = "Verifikasi Telepon", onBack = onBack) { contentModifier ->
        val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        TridjayaPullRefresh(
            isRefreshing = state.loading && daftar != null,
            onRefresh = { viewModel.muat() },
            modifier = contentModifier,
        ) {
            when {
                state.loading && daftar == null -> ScrollableCenter { CircularProgressIndicator() }

                state.error != null && daftar == null -> ScrollableCenter {
                    ExpressiveErrorState(
                        message = state.error ?: "Gagal memuat antrian verifikasi.",
                        onRetry = { viewModel.muat() },
                    )
                }

                daftar == null || daftar.baris.isEmpty() -> ScrollableCenter {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (daftar != null) {
                            BarisTanggal(daftar.tanggal) { viewModel.geserHari(it) }
                            Spacer(Modifier.height(12.dp))
                        }
                        ExpressiveEmptyState(
                            icon = {
                                Icon(
                                    Icons.Rounded.PhoneInTalk,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(30.dp),
                                )
                            },
                            title = "Tidak ada transaksi untuk diverifikasi.",
                            subtitle = "Hanya penjualan dengan harga barang di atas ambang yang " +
                                "masuk daftar ini. Coba geser ke tanggal lain.",
                        )
                    }
                }

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp + navBottom,
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item(key = "kepala") {
                        Column {
                            BarisTanggal(daftar.tanggal) { viewModel.geserHari(it) }
                            Spacer(Modifier.height(10.dp))
                            KartuRingkasan(
                                persen = VertelPlan.persenSelesai(daftar),
                                total = daftar.ringkasan.total,
                                sudah = daftar.ringkasan.sudahDitelepon,
                                terhubung = daftar.ringkasan.terhubung,
                                komplain = daftar.ringkasan.adaKomplain,
                                tanpaNomor = daftar.ringkasan.tanpaNomor,
                                ambang = daftar.ambangHarga,
                            )
                        }
                    }
                    // Kegagalan AKSI ditampilkan DI ATAS daftar, bukan menggantikannya
                    // (pola `AcInstallScreen`): daftar yang lenyap membuat verifikator
                    // kehilangan nomor yang sedang dibacanya.
                    state.actionError?.let { pesan ->
                        item(key = "galat-aksi") {
                            Text(
                                pesan,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.clickable { viewModel.bersihkanActionError() },
                            )
                        }
                    }
                    items(VertelPlan.urutKerja(daftar.baris), key = { it.noTransaksi }) { baris ->
                        KartuBaris(
                            baris = baris,
                            terbuka = state.terbuka == baris.noTransaksi,
                            submitting = state.submitting,
                            onToggle = {
                                viewModel.buka(
                                    if (state.terbuka == baris.noTransaksi) null else baris.noTransaksi,
                                )
                            },
                            onBukaUrl = { url -> bukaUrl(context, url) },
                            onSimpan = { kanal, hasil, komplain, catatan ->
                                viewModel.catat(
                                    noTransaksi = baris.noTransaksi,
                                    // Tanggal TRANSAKSI dari barisnya sendiri, BUKAN
                                    // tanggal yang sedang ditampilkan. Keduanya
                                    // biasanya sama, tapi kunci upsert server memakai
                                    // yang ini.
                                    tanggalTransaksi = baris.tanggal,
                                    kanal = kanal,
                                    hasil = hasil,
                                    adaKomplain = komplain,
                                    catatan = catatan,
                                    onSukses = {},
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BarisTanggal(tanggal: String, onGeser: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(onClick = { onGeser(-1) }) {
            Icon(Icons.Rounded.ChevronLeft, contentDescription = "Hari sebelumnya")
        }
        Text(
            "Transaksi $tanggal",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        IconButton(onClick = { onGeser(1) }) {
            Icon(Icons.Rounded.ChevronRight, contentDescription = "Hari berikutnya")
        }
    }
}

@Composable
private fun KartuRingkasan(
    persen: Int,
    total: Long,
    sudah: Long,
    terhubung: Long,
    komplain: Long,
    tanpaNomor: Long,
    ambang: Long,
) {
    ClayCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text("$sudah dari $total sudah ditelepon", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { persen / 100f },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "$persen% - terhubung $terhubung - komplain $komplain" +
                    if (tanpaNomor > 0) " - tanpa nomor $tanpaNomor" else "",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            // Ambang hidup di `app_settings` dan bisa berubah TANPA deploy —
            // ditampilkan supaya verifikator tahu daftar ini disaring atas dasar
            // apa, dan supaya perubahan setelan terlihat, bukan cuma terasa.
            Text(
                "Disaring harga barang minimal ${rupiah(ambang)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun KartuBaris(
    baris: BarisVertelDto,
    terbuka: Boolean,
    submitting: Boolean,
    onToggle: () -> Unit,
    onBukaUrl: (String) -> Unit,
    onSimpan: (String, String, Boolean, String) -> Unit,
) {
    val panggilan = baris.panggilan
    ClayCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxWidth()
                .clickable { onToggle() }
                .padding(14.dp),
        ) {
            Text(
                baris.customerNama?.takeIf { it.isNotBlank() } ?: "(tanpa nama)",
                fontWeight = FontWeight.Bold,
            )
            Text(
                baris.barang,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                listOfNotNull(
                    baris.cabangNama?.takeIf { it.isNotBlank() },
                    rupiah(baris.totalNominal),
                    baris.salesNama?.takeIf { it.isNotBlank() },
                ).joinToString(" - "),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(8.dp))
            if (panggilan != null) {
                Text(
                    "Sudah: ${VertelKanal.label(panggilan.kanal)} - " +
                        VertelHasil.label(panggilan.hasil) +
                        if (panggilan.adaKomplain) " - ADA KOMPLAIN" else "",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (panggilan.adaKomplain) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                panggilan.olehNama?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        "oleh $it",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Text(
                    "Belum ditelepon",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (terbuka) {
                Spacer(Modifier.height(12.dp))
                BarisKontak(baris = baris, onBukaUrl = onBukaUrl)
                Spacer(Modifier.height(12.dp))
                FormCatat(awal = panggilan, submitting = submitting, onSimpan = onSimpan)
            }
        }
    }
}

@Composable
private fun BarisKontak(baris: BarisVertelDto, onBukaUrl: (String) -> Unit) {
    val wa = VertelPlan.waUrl(baris)
    val tel = VertelPlan.telUrl(baris)
    Column {
        Text(
            // Nomor MENTAH ditampilkan apa adanya supaya verifikator bisa membaca
            // dan mengoreksinya — termasuk saat nomor itu tak layak ditautkan dan
            // kedua tombol di bawah tidak muncul.
            baris.customerHp?.takeIf { it.isNotBlank() } ?: "Nomor tidak tercatat",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (tel != null) {
                OutlinedButton(onClick = { onBukaUrl(tel) }) {
                    Icon(
                        Icons.Rounded.Call,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Telepon")
                }
            }
            if (wa != null) {
                OutlinedButton(onClick = { onBukaUrl(wa) }) { Text("WhatsApp") }
            }
        }
        if (wa == null && tel == null) {
            Text(
                "Nomor tidak bisa dihubungi. Baris ini tidak dihitung sebagai kelalaian.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FormCatat(
    awal: VertelPanggilanDto?,
    submitting: Boolean,
    onSimpan: (String, String, Boolean, String) -> Unit,
) {
    // Disemai dari catatan yang SUDAH ada supaya mengoreksi hasil tidak berarti
    // mengisi ulang dari nol — server meng-upsert, jadi koreksi memang alurnya.
    var kanal by rememberSaveable(awal?.kanal) { mutableStateOf(awal?.kanal ?: VertelKanal.TELEPON) }
    var hasil by rememberSaveable(awal?.hasil) { mutableStateOf(awal?.hasil ?: VertelHasil.TERHUBUNG) }
    var komplain by rememberSaveable(awal?.adaKomplain) { mutableStateOf(awal?.adaKomplain ?: false) }
    var catatan by rememberSaveable(awal?.catatan) { mutableStateOf(awal?.catatan.orEmpty()) }
    val gate = VertelPlan.catatGate(kanal, hasil, komplain, catatan)

    Column {
        Text("Kanal", style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VertelKanal.SEMUA.forEach { k ->
                FilterChip(
                    selected = kanal == k,
                    onClick = { kanal = k },
                    label = { Text(VertelKanal.label(k)) },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("Hasil", style = MaterialTheme.typography.labelMedium)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            VertelHasil.SEMUA.chunked(2).forEach { pasangan ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pasangan.forEach { h ->
                        FilterChip(
                            selected = hasil == h,
                            onClick = { hasil = h },
                            label = { Text(VertelHasil.label(h)) },
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = komplain, onCheckedChange = { komplain = it })
            Text("Konsumen menyampaikan komplain")
        }
        OutlinedTextField(
            value = catatan,
            onValueChange = { catatan = it },
            label = { Text("Catatan (opsional)") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
        )
        Spacer(Modifier.height(8.dp))
        TextButton(
            onClick = { onSimpan(kanal, hasil, komplain, catatan) },
            enabled = !submitting && gate.bolehSimpan,
        ) {
            Text(if (awal == null) "Simpan hasil" else "Perbarui hasil")
        }
        if (gate.alasan != null) {
            Text(
                gate.alasan,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        } else if (komplain) {
            Text(
                "Komplain yang perlu ditindaklanjuti tetap dibuat lewat menu Komplain — " +
                    "centang ini hanya menandai hasil panggilan.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Membuka `tel:` atau `https://wa.me/...`.
 *
 * Kegagalan ditelan DENGAN SENGAJA: satu-satunya sebabnya adalah tak ada app
 * yang menangani intent itu (HP tanpa dialer, tanpa WhatsApp maupun browser),
 * dan tak ada yang bisa dilakukan verifikator soal itu dari layar ini.
 */
private fun bukaUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    } catch (_: ActivityNotFoundException) {
        // sengaja diam — lihat KDoc
    }
}

/**
 * Format rupiah dengan pemisah ribuan.
 *
 * Ditulis sendiri, BUKAN `NumberFormat.getCurrencyInstance(Locale("id","ID"))`:
 * hasil formatter itu bergantung pada data locale ROM (sebagian perangkat
 * menulis "Rp1.234,00", sebagian "IDR 1.234"), sementara angka ini dibaca
 * berdampingan dengan angka dari layar lain di app yang sama.
 */
private fun rupiah(nilai: Long): String {
    val negatif = nilai < 0
    val angka = kotlin.math.abs(nilai).toString()
        .reversed().chunked(3).joinToString(".").reversed()
    return if (negatif) "-Rp$angka" else "Rp$angka"
}
