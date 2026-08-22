package com.krisoft.tridjayaelektronik.ui.homeservice

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.krisoft.tridjayaelektronik.data.model.HsRingkasTransaksiDto
import com.krisoft.tridjayaelektronik.data.model.HsTransaksiItemDto
import com.krisoft.tridjayaelektronik.ui.theme.ClayCard
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveFilledButton
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveOutlinedButton
import com.krisoft.tridjayaelektronik.ui.theme.TridjayaCollapsibleHeader
import java.io.File

/**
 * Lapor komplain — membuat tiket Home Service dari lapangan.
 *
 * Urutannya sengaja sama dengan web: cari konsumen (nama/HP) → pilih transaksi
 * → pilih barang → foto kwitansi → keluhan. Nomor transaksi GS praktis tak
 * pernah dihafal orang lapangan, jadi pencarian konsumen adalah pintu utama,
 * bukan pelengkap.
 */
@Composable
fun HomeServiceLaporScreen(
    onBack: () -> Unit,
    onLihatTiket: (String) -> Unit,
    viewModel: HomeServiceLaporViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    val fileKwitansi = remember {
        File(context.cacheDir, "home-service").apply { mkdirs() }.let { File(it, "kwitansi.jpg") }
    }
    val uriKwitansi = remember(fileKwitansi) {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", fileKwitansi)
    }
    val kamera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) viewModel.unggahKwitansi(fileKwitansi)
    }

    TridjayaCollapsibleHeader(title = "Lapor Komplain", onBack = onBack) { contentModifier ->
        val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        Column(
            modifier = contentModifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp + navBottom),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val tiket = state.tiketJadi
            if (tiket != null) {
                ClayCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Tiket ${tiket.nomorTiket} dibuat",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            buildString {
                                append(
                                    tiket.namaBarang?.takeIf { it.isNotBlank() }
                                        ?: tiket.noTransaksi.takeIf { it.isNotBlank() }
                                        ?: "Barang belum diisi",
                                )
                                // Tiket boleh memuat beberapa barang; kartu ini
                                // memajang yang utama saja, jadi sisanya harus
                                // dihitung — kalau tidak, tiket 3 barang terbaca
                                // persis seperti tiket 1 barang.
                                if (tiket.items.size > 1) append(" +${tiket.items.size - 1} barang lain")
                                // Status garansi DIHITUNG SERVER dari tanggal beli —
                                // ditampilkan, bukan diisi pelapor. Pada tiket BELUM
                                // TERVERIFIKASI server menulis `false` karena tanggal
                                // belinya memang belum diketahui: itu berarti "belum
                                // terbukti bergaransi", BUKAN "tidak bergaransi".
                                // Menuliskan vonis negatifnya di sini akan memberi
                                // tahu konsumen sesuatu yang belum tentu benar.
                                if (!tiket.terverifikasi) {
                                    append(" · belum terverifikasi, CS melengkapi dari foto kwitansi")
                                } else {
                                    when (tiket.dalamGaransi) {
                                        true -> append(" · masih garansi")
                                        false -> append(" · di luar garansi")
                                        null -> Unit
                                    }
                                }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            ExpressiveFilledButton(
                                onClick = { onLihatTiket(tiket.id) },
                                modifier = Modifier.weight(1f),
                            ) { Text("Lihat tiket") }
                            ExpressiveOutlinedButton(
                                onClick = { viewModel.laporLagi() },
                                modifier = Modifier.weight(1f),
                            ) { Text("Lapor lagi") }
                        }
                    }
                }
                return@Column
            }

            state.error?.let { pesan ->
                Text(pesan, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
            }

            // Sisa form hanya terbuka sesudah pelapor memilih SATU dari dua
            // jalur: transaksi yang cocok, atau maju tanpa data pembelian.
            if (state.noTransaksi == null && !state.tanpaVerifikasi) {
                SeksiCari(
                    nama = state.cariNama,
                    hp = state.cariHp,
                    mencari = state.mencari,
                    sudahMencari = state.sudahMencari,
                    hasil = state.hasilCari,
                    onNama = viewModel::ketikNama,
                    onHp = viewModel::ketikHp,
                    onCari = viewModel::cari,
                    onPilih = viewModel::pilihTransaksi,
                    onLanjutTanpaVerifikasi = viewModel::lanjutTanpaVerifikasi,
                )
                return@Column
            }

            if (state.tanpaVerifikasi) {
                SeksiTanpaVerifikasi(onCariLagi = viewModel::gantiTransaksi)
            } else {
                SeksiTransaksi(
                    noTransaksi = state.noTransaksi.orEmpty(),
                    memuat = state.memuatRincian,
                    barang = state.barang,
                    terpilih = state.barisTerpilih,
                    serial = state.kontak.serialNumber,
                    onToggle = viewModel::toggleBarang,
                    onGanti = viewModel::gantiTransaksi,
                )
            }

            KartuKwitansi(
                url = state.fotoKwitansiUrl,
                penanda = state.fotoPenanda,
                mengunggah = state.mengunggah,
                onJepret = { kamera.launch(uriKwitansi) },
            )

            OutlinedTextField(
                value = state.deskripsi,
                onValueChange = viewModel::ketikDeskripsi,
                label = { Text("Keluhan konsumen") },
                placeholder = { Text("Contoh: mesin cuci tidak berputar sejak kemarin") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("normal" to "Normal", "mendesak" to "Mendesak").forEach { (kunci, label) ->
                    FilterChip(
                        selected = state.prioritas == kunci,
                        onClick = { viewModel.pilihPrioritas(kunci) },
                        label = { Text(label) },
                    )
                }
            }

            Text(
                "Kontak konsumen",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                // Isian ini menang atas data hasil pengayaan SPK di server, jadi
                // perlu dijelaskan — bukan sekadar "opsional".
                if (state.tanpaVerifikasi) {
                    "Tanpa data pembelian, nama dan nomor HP WAJIB — itu satu-satunya cara CS " +
                        "menghubungi konsumen ini."
                } else {
                    "Terisi otomatis dari data transaksi bila ada. Yang kamu ketik di sini yang dipakai."
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = state.customerNama,
                onValueChange = viewModel::ketikCustomerNama,
                label = { Text(if (state.tanpaVerifikasi) "Nama *" else "Nama") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.customerHp,
                onValueChange = viewModel::ketikCustomerHp,
                label = { Text(if (state.tanpaVerifikasi) "Nomor HP *" else "Nomor HP") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.customerAlamat,
                onValueChange = viewModel::ketikCustomerAlamat,
                // Wajib di KEDUA jalur — server menolak tiket yang alamatnya
                // tetap kosong sesudah pengayaan SPK, dan teknisi memang
                // didatangkan ke alamat ini.
                label = { Text("Alamat kunjungan *") },
                placeholder = { Text("Teknisi akan mendatangi alamat ini") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )

            val gate = bolehBuatTiket(
                tanpaVerifikasi = state.tanpaVerifikasi,
                noTransaksi = state.noTransaksi,
                barisTerpilih = state.barisTerpilih,
                fotoKwitansiUrl = state.fotoKwitansiUrl,
                deskripsi = state.deskripsi,
                customerNama = state.customerNama,
                customerHp = state.customerHp,
                customerAlamat = state.customerAlamat,
            )
            if (!gate.ok) {
                Text(
                    gate.alasan.orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // Galat dirender SEKALI LAGI di sini. Yang di pucuk form berada
            // jauh di luar layar begitu form terisi penuh, jadi penolakan server
            // (mis. alamat wajib) terbaca sebagai "tombolnya tidak melakukan
            // apa-apa" dan pelapor menekannya berulang kali.
            state.error?.let { pesan ->
                Text(pesan, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
            }
            ExpressiveFilledButton(
                onClick = { viewModel.kirim() },
                // `mengunggah` ikut: menekan Kirim saat kwitansi sedang diganti
                // akan mengirim tiket tanpa foto (URL lama sudah dibuang).
                enabled = gate.ok && !state.mengirim && !state.mengunggah,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.mengirim) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (state.mengirim) "Mengirim…" else "Kirim komplain")
            }
        }
    }
}

@Composable
private fun SeksiCari(
    nama: String,
    hp: String,
    mencari: Boolean,
    sudahMencari: Boolean,
    hasil: List<HsRingkasTransaksiDto>,
    onNama: (String) -> Unit,
    onHp: (String) -> Unit,
    onCari: () -> Unit,
    onPilih: (String) -> Unit,
    onLanjutTanpaVerifikasi: () -> Unit,
) {
    Text("Cari transaksi konsumen", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    OutlinedTextField(
        value = nama,
        onValueChange = onNama,
        label = { Text("Nama konsumen") },
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = hp,
        onValueChange = onHp,
        label = { Text("Nomor HP") },
        modifier = Modifier.fillMaxWidth(),
    )
    ExpressiveFilledButton(
        onClick = onCari,
        enabled = !mencari,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(if (mencari) "Mencari…" else "Cari")
    }

    // Nihil hasil BUKAN jalan buntu (dulu iya, dan komplain konsumen dengan
    // pembelian lama/di luar alur SPK jadi tak bisa dicatat dari HP sama
    // sekali). Tawaran ini hanya muncul sesudah pencarian benar-benar SUKSES
    // dan kosong — `sudahMencari` tidak diset saat pencarian gagal, supaya
    // jaringan putus tak berubah jadi tiket tak terverifikasi.
    if (sudahMencari && hasil.isEmpty() && !mencari) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Data pembelian atas nama/nomor itu tidak ditemukan. Coba nomor HP lain atau " +
                        "ejaan nama yang berbeda — atau lanjutkan tanpa data pembelian: tiketnya " +
                        "ditandai belum terverifikasi supaya CS memeriksanya dari foto kwitansi.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                ExpressiveOutlinedButton(
                    onClick = onLanjutTanpaVerifikasi,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Lanjut tanpa data pembelian") }
            }
        }
    }
    hasil.forEach { transaksi ->
        ClayCard(modifier = Modifier.fillMaxWidth().clickable { onPilih(transaksi.noTransaksi) }) {
            Column(Modifier.fillMaxWidth().padding(14.dp)) {
                Text(
                    transaksi.customerNama?.takeIf { it.isNotBlank() } ?: "(tanpa nama)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    buildString {
                        append(transaksi.noTransaksi)
                        transaksi.tanggal?.takeIf { it.isNotBlank() }?.let { append(" · $it") }
                        append(" · ${transaksi.jumlahItem} barang")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                transaksi.contohBarang?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun SeksiTransaksi(
    noTransaksi: String,
    memuat: Boolean,
    barang: List<HsTransaksiItemDto>,
    terpilih: Set<Int>,
    serial: String?,
    onToggle: (Int) -> Unit,
    onGanti: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(noTransaksi, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            // Serial ini milik BARIS PERTAMA transaksi saja (server mengambilnya
            // dengan `kontak_dari_spk(no_transaksi, items.first())`), jadi ia
            // hanya sah dipajang sebagai fakta transaksi kalau transaksinya
            // memang satu barang. Pada transaksi multi-barang ia akan terbaca
            // seolah berlaku untuk semua yang dicentang.
            serial?.takeIf { it.isNotBlank() && barang.size <= 1 }?.let {
                Text(
                    "Serial: $it",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        ExpressiveOutlinedButton(onClick = onGanti) { Text("Ganti") }
    }

    if (memuat) {
        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
        return
    }
    if (barang.size > 1) {
        Text(
            "Centang SEMUA barang yang dikeluhkan — satu tiket boleh memuat lebih dari satu.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    barang.forEach { item ->
        val dicentang = item.baris in terpilih
        Surface(
            onClick = { onToggle(item.baris) },
            shape = RoundedCornerShape(14.dp),
            color = if (dicentang) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = dicentang, onCheckedChange = { onToggle(item.baris) })
                Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                    Text(
                        item.namaBarang?.takeIf { it.isNotBlank() } ?: item.kodeBarang.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (dicentang) FontWeight.Bold else FontWeight.Normal,
                    )
                    Text(
                        item.kodeBarang.orEmpty(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * Pengganti seksi transaksi pada jalur tanpa verifikasi. Isinya konsekuensi
 * yang harus diketahui pelapor SEBELUM mengirim — bukan sekadar penanda:
 * barang, tanggal beli, cabang, dan status garansi tidak akan terisi, dan CS
 * yang melengkapinya dari foto kwitansi.
 */
@Composable
private fun SeksiTanpaVerifikasi(onCariLagi: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Tanpa data pembelian",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Tiket ini ditandai belum terverifikasi. Nama barang, tanggal beli, cabang, dan " +
                    "status garansi tidak terisi otomatis — CS melengkapinya dari foto kwitansi " +
                    "sebelum menugaskan teknisi.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            ExpressiveOutlinedButton(onClick = onCariLagi, modifier = Modifier.fillMaxWidth()) {
                Text("Cari data pembelian lagi")
            }
        }
    }
}

@Composable
private fun KartuKwitansi(url: String?, penanda: String, mengunggah: Boolean, onJepret: () -> Unit) {
    ExpressiveOutlinedButton(
        onClick = onJepret,
        enabled = !mengunggah,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (mengunggah) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        } else {
            Icon(Icons.Rounded.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(8.dp))
        Text(
            when {
                mengunggah -> "Mengunggah…"
                url != null -> "Kwitansi terunggah — foto ulang"
                else -> "Foto kwitansi (opsional)"
            }
        )
    }
    // Penanda yang tercetak di watermark foto yang SEDANG terpasang. Tanpa ini
    // kwitansi milik transaksi/konsumen sebelumnya tak terlihat sama sekali —
    // label tombol berbunyi sama saja, dan tiket terbit membawa bukti orang lain.
    if (url != null && penanda.isNotBlank()) {
        Text(
            "Terpasang: $penanda",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
