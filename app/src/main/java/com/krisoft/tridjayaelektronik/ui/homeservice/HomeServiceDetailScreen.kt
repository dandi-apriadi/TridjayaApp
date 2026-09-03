package com.krisoft.tridjayaelektronik.ui.homeservice

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.core.net.toUri
import android.widget.Toast
import android.content.Intent
import com.krisoft.tridjayaelektronik.ui.leads.rememberKirimWa
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import com.krisoft.tridjayaelektronik.data.model.DriverDto
import com.krisoft.tridjayaelektronik.data.model.HsTicketDetailDto
import com.krisoft.tridjayaelektronik.data.model.HsVisitDto
import com.krisoft.tridjayaelektronik.data.model.formatWaktuId
import com.krisoft.tridjayaelektronik.ui.theme.ClayCard
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveErrorState
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveFilledButton
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveOutlinedButton
import com.krisoft.tridjayaelektronik.ui.theme.ScrollableCenter
import com.krisoft.tridjayaelektronik.ui.theme.TridjayaCollapsibleHeader
import com.krisoft.tridjayaelektronik.util.PESAN_KAMERA_TAK_TERSIMPAN
import java.io.File

/**
 * Detail satu tiket komplain — SATU layar untuk semua peran.
 *
 * Tombol yang tampil ditentukan status tiket + kemampuan akun (peta
 * `/api/me/capabilities`, sumber yang sama dengan guard backend), bukan oleh
 * layar terpisah per peran: satu tiket berpindah tangan CS → teknisi → driver
 * dan kembali lagi, jadi memecahnya per peran berarti orang harus menebak
 * halaman mana yang memuat tiket yang sedang dibicarakan.
 */
@Composable
fun HomeServiceDetailScreen(
    onBack: () -> Unit,
    viewModel: HomeServiceDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    var dialogTugas by remember { mutableStateOf(false) }
    var dialogDriver by remember { mutableStateOf(false) }
    var dialogAlasan by remember { mutableStateOf<String?>(null) }
    var dialogSelesai by remember { mutableStateOf(false) }
    var dialogAmbil by remember { mutableStateOf(false) }

    val fileFoto = remember {
        File(context.cacheDir, "home-service").apply { mkdirs() }.let { File(it, "bukti.jpg") }
    }
    val uriFoto = remember(fileFoto) {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", fileFoto)
    }
    val kamera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        // `ok == false` tak lagi ditelan: kegagalan simpan foto kamera dulu
        // menghasilkan nol pesan, jadi petugas menyangka buktinya terkirim.
        // Lihat [PESAN_KAMERA_TAK_TERSIMPAN].
        if (ok) viewModel.unggahFoto(fileFoto, judul = "Bukti home service")
        else Toast.makeText(context, PESAN_KAMERA_TAK_TERSIMPAN, Toast.LENGTH_LONG).show()
    }

    TridjayaCollapsibleHeader(title = "Detail Komplain", onBack = onBack) { contentModifier ->
        val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val tiket = state.tiket
        when {
            state.loading && tiket == null -> ScrollableCenter { CircularProgressIndicator() }

            tiket == null -> ScrollableCenter {
                ExpressiveErrorState(
                    message = state.error ?: "Tiket tidak ditemukan.",
                    onRetry = { viewModel.muat() },
                )
            }

            else -> Column(
                modifier = contentModifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp + navBottom),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                RingkasanTiket(tiket)

                (state.pesan ?: state.error)?.let { pesan ->
                    Text(
                        pesan,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (state.error != null) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                    )
                }

                tiket.fotoKwitansiUrl?.let { url ->
                    fotoHsUrl(url)?.let { AuthedFoto(it, viewModel.bearerToken(), "Foto kwitansi") }
                }

                Aksi(
                    tiket = tiket,
                    state = state,
                    userId = viewModel.userId,
                    onTugaskan = { dialogTugas = true; viewModel.muatTeknisi() },
                    onMintaTarik = { dialogAlasan = "tarik" },
                    onBatalkan = { dialogAlasan = "batal" },
                    onMulai = { viewModel.mulaiKunjungan(null, null) },
                    onSelesai = { dialogSelesai = true },
                    onTugaskanDriver = { dialogDriver = true; viewModel.muatDriver() },
                    onBatalTarik = { dialogAlasan = "batal_tarik" },
                    onAmbilUnit = { dialogAmbil = true },
                )

                if (tiket.visits.isNotEmpty()) {
                    Text("Riwayat kunjungan", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    tiket.visits.forEach { KartuKunjungan(it, viewModel.bearerToken()) }
                }
            }
        }
    }

    if (dialogTugas) {
        DialogPilihOrang(
            judul = "Tugaskan teknisi",
            orang = state.teknisi,
            keterangan = state.teknisiError,
            sibuk = state.sibuk,
            onTutup = { dialogTugas = false },
            onPilih = { id, tanggal ->
                viewModel.tugaskanTeknisi(id, tanggal)
                dialogTugas = false
            },
        )
    }

    if (dialogDriver) {
        DialogPilihOrang(
            judul = "Tugaskan driver penarikan",
            orang = state.driver,
            keterangan = state.driverError,
            sibuk = state.sibuk,
            onTutup = { dialogDriver = false },
            onPilih = { id, tanggal ->
                viewModel.tugaskanDriver(id, tanggal)
                dialogDriver = false
            },
        )
    }

    dialogAlasan?.let { jenis ->
        DialogAlasan(
            judul = when (jenis) {
                "tarik" -> "Minta tarik unit"
                "batal_tarik" -> "Batalkan penarikan"
                else -> "Batalkan tiket"
            },
            penjelasan = when (jenis) {
                "tarik" -> "Unit akan dijemput driver, bukan diperbaiki di rumah konsumen."
                "batal_tarik" -> "Tiket kembali ke antrian triase, bukan dibatalkan."
                else -> "Tiket yang dibatalkan tak bisa dibuka lagi — buat tiket baru bila perlu."
            },
            onTutup = { dialogAlasan = null },
            onKirim = { alasan ->
                when (jenis) {
                    "tarik" -> viewModel.mintaTarik(alasan)
                    "batal_tarik" -> viewModel.batalTarik(alasan)
                    else -> viewModel.batalkan(alasan)
                }
                dialogAlasan = null
            },
        )
    }

    if (dialogSelesai) {
        DialogTutupKunjungan(
            fotoTerunggah = state.fotoTerunggah,
            mengunggah = state.mengunggahFoto,
            onFoto = { kamera.launch(uriFoto) },
            onTutup = { dialogSelesai = false },
            onKirim = { hasil, tindakan, catatan ->
                viewModel.tutupKunjungan(
                    hasil = hasil,
                    tindakan = tindakan,
                    catatan = catatan,
                    adaSparepart = false,
                    sparepart = emptyList(),
                    biayaDibayar = null,
                    buktiBayarUrl = null,
                    rating = null,
                    komentar = null,
                )
                dialogSelesai = false
            },
        )
    }

    if (dialogAmbil) {
        var catatan by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { dialogAmbil = false },
            title = { Text("Unit sudah diambil?", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        "Foto unit saat diambil jadi bukti serah terima dari konsumen.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(8.dp))
                    ExpressiveOutlinedButton(
                        onClick = { kamera.launch(uriFoto) },
                        enabled = !state.mengunggahFoto,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Rounded.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            when {
                                state.mengunggahFoto -> "Mengunggah…"
                                state.fotoTerunggah.isNotEmpty() -> "Foto siap — jepret ulang"
                                else -> "Foto unit"
                            }
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = catatan,
                        onValueChange = { catatan = it },
                        label = { Text("Catatan (opsional)") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !state.sibuk,
                    onClick = {
                        viewModel.tandaiUnitDiambil(catatan)
                        dialogAmbil = false
                    },
                ) { Text("Sudah diambil") }
            },
            dismissButton = { TextButton(onClick = { dialogAmbil = false }) { Text("Batal") } },
        )
    }
}

@Composable
private fun RingkasanTiket(tiket: HsTicketDetailDto) {
    ClayCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    tiket.nomorTiket,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
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
            Spacer(Modifier.height(8.dp))
            // KELENGKAPAN data ≠ `terverifikasi`. Server SENGAJA tidak pernah
            // menaikkan `terverifikasi` saat CS mengisi barang/tanggal beli dari
            // foto kwitansi — kolom itu menjawab "cocok dengan mirror penjualan?"
            // — dan yang menandai "sudah diperiksa orang" adalah `dilengkapiAt`
            // (migrasi 222). Memakai `terverifikasi` sebagai proksi kelengkapan
            // akan MENYEMBUNYIKAN barang & vonis garansi yang baru saja
            // ditegakkan CS, persis kebalikan dari yang mau dicegah.
            val sudahDilengkapi = !tiket.dilengkapiAt.isNullOrBlank()
            if (!tiket.terverifikasi) {
                Baris(
                    "Status data",
                    if (sudahDilengkapi) {
                        "Dilengkapi CS${tiket.dilengkapiNama?.takeIf { it.isNotBlank() }?.let { " oleh $it" } ?: ""}" +
                            " · ${formatWaktuId(tiket.dilengkapiAt)}"
                    } else {
                        "Belum terverifikasi, CS melengkapi dari foto kwitansi"
                    },
                )
            }
            if (!tiket.terverifikasi && !sudahDilengkapi) {
                // Belum ada yang memeriksa: barang/tanggal beli memang kosong dan
                // `dalamGaransi=false` berarti "belum terbukti", BUKAN "tidak
                // bergaransi" — menampilkan vonis negatifnya membuat teknisi
                // menagih biaya ke konsumen yang mungkin masih bergaransi.
                Baris("Barang", "— belum diisi —")
            } else {
                // Tiket boleh memuat beberapa barang. Kolom tunggal di bawah
                // adalah SNAPSHOT BARANG PERTAMA saja (migrasi 214), jadi tiket
                // multi-barang harus dirinci — teknisi yang membaca satu baris
                // berangkat membawa persiapan untuk satu unit.
                if (tiket.items.size > 1) {
                    Baris("Barang", "${tiket.items.size} barang")
                    tiket.items.forEach { item ->
                        Baris(
                            "· ${item.urutan}",
                            buildString {
                                append(item.namaBarang?.takeIf { it.isNotBlank() } ?: item.kodeBarang.orEmpty())
                                item.serialNumber?.takeIf { it.isNotBlank() }?.let { append(" · SN $it") }
                                // Tanggal beli per barang ikut: pada tiket
                                // multi-barang inilah satu-satunya angka yang
                                // menerangkan kenapa dua unit di kwitansi yang
                                // sama bisa berbeda vonis garansinya.
                                item.tanggalBeli?.takeIf { it.isNotBlank() }?.let { append(" · beli $it") }
                                append(if (item.dalamGaransi) " · masih garansi" else " · di luar garansi")
                            },
                        )
                    }
                } else {
                    Baris(
                        "Barang",
                        tiket.namaBarang?.takeIf { it.isNotBlank() }
                            ?: tiket.kodeBarang?.takeIf { it.isNotBlank() }
                            ?: "— belum diisi —",
                    )
                }
                // Tiket tanpa transaksi menyimpan string KOSONG, bukan null —
                // barisnya akan terbaca sebagai label tanpa nilai.
                tiket.noTransaksi.takeIf { it.isNotBlank() }?.let { Baris("Transaksi", it) }
                if (tiket.items.size <= 1) {
                    tiket.serialNumber?.takeIf { it.isNotBlank() }?.let { Baris("Serial", it) }
                    tiket.tanggalBeli?.takeIf { it.isNotBlank() }?.let { Baris("Tanggal beli", it) }
                    // Garansi DIHITUNG SERVER — ditampilkan apa adanya, TAPI
                    // hanya kalau ada dasarnya. `dalamGaransi` bertipe non-Option
                    // di server: tanpa tanggal beli ia bernilai `false` yang
                    // artinya "belum terbukti", bukan "tidak bergaransi", dan
                    // mencetak vonis negatifnya membuat teknisi menagih biaya ke
                    // konsumen yang mungkin masih bergaransi.
                    if (tiket.tanggalBeli.isNullOrBlank()) {
                        Baris("Garansi", "Belum terbukti — tanggal beli belum diisi")
                    } else {
                        when (tiket.dalamGaransi) {
                            true -> Baris("Garansi", "Masih garansi")
                            false -> Baris("Garansi", "Di luar garansi")
                            null -> Unit
                        }
                    }
                }
            }
            Baris("Konsumen", tiket.customerNama?.takeIf { it.isNotBlank() } ?: "—")
            tiket.customerHp?.takeIf { it.isNotBlank() }?.let { KontakKonsumen(it) }
            tiket.customerAlamat?.takeIf { it.isNotBlank() }?.let { Baris("Alamat", it) }
            // PENGINPUT tiket. Datanya sudah tersimpan sejak migrasi 118
            // (`pelapor_nama`) dan selalu ikut di respons, tapi app tak pernah
            // menampilkannya — CS/teknisi yang menemukan isian meragukan tak
            // punya cara tahu harus bertanya ke siapa, padahal namanya ada di
            // baris yang sama. Web sudah menampilkannya di kartu papan.
            tiket.pelaporNama?.takeIf { it.isNotBlank() }?.let { Baris("Penginput", it) }
            tiket.assignedTeknisiNama?.takeIf { it.isNotBlank() }?.let { Baris("Teknisi", it) }
            tiket.tarikDriverNama?.takeIf { it.isNotBlank() }?.let { Baris("Driver tarik", it) }
            tiket.tarikAlasan?.takeIf { it.isNotBlank() }?.let { Baris("Alasan tarik", it) }
            tiket.alasanBatal?.takeIf { it.isNotBlank() }?.let { Baris("Alasan batal", it) }

            Spacer(Modifier.height(8.dp))
            Text("Keluhan", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(tiket.deskripsi, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/**
 * Baris "HP" yang bisa DIPAKAI, bukan sekadar dibaca (2026-08-15, laporan user:
 * teknisi tak punya tombol chat/telepon di layar penugasan).
 *
 * Sebelumnya nomor konsumen dirender `Baris("HP", …)` — teks mati. Teknisi yang
 * sudah berdiri di depan rumah dan tak menemukan orangnya harus mengetik ulang
 * nomornya ke aplikasi lain; nomor ERP sering ditulis `62…`/`+62…`/`08…` dan
 * salah satu bentuk itu tak bisa langsung ditelepon.
 *
 * Chat WA memakai ULANG [rememberKirimWa] — itu yang menghormati pilihan
 * WhatsApp Business vs biasa (sheet muncul hanya bila keduanya terpasang) dan
 * yang menormalkan nomor lewat `normalizeWaPhone`. Menulis intent `wa.me`
 * sendiri di sini akan memaksa satu paket dan mengulang normalisasi nomor —
 * dua aturan yang sudah punya satu rumah.
 *
 * Pesannya sengaja KOSONG: teknisi menelepon/mengetik sendiri sesuai keadaan di
 * lapangan, dan template yang salah konteks lebih merepotkan daripada tak ada.
 *
 * `ACTION_DIAL` (bukan `ACTION_CALL`) supaya tak butuh izin `CALL_PHONE` —
 * nomornya cuma dimuat di dialer, penggunanya yang menekan panggil.
 */
@Composable
private fun KontakKonsumen(hp: String) {
    val context = LocalContext.current
    val kirimWa = rememberKirimWa()
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "HP",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(96.dp),
        )
        Text(hp, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        IconButton(onClick = { kirimWa(hp, "") {} }) {
            Icon(
                Icons.AutoMirrored.Rounded.Chat,
                contentDescription = "Chat WhatsApp konsumen",
                tint = Color(0xFF12B76A),
            )
        }
        IconButton(
            onClick = {
                // Gagal (tak ada dialer, mis. tablet tanpa telepon) tak boleh
                // menutup app — tombolnya diam saja, nomornya tetap terbaca.
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_DIAL, "tel:$hp".toUri()))
                }
            }
        ) {
            Icon(
                Icons.Rounded.Call,
                contentDescription = "Telepon konsumen",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun Baris(label: String, nilai: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(96.dp),
        )
        Text(nilai, style = MaterialTheme.typography.bodySmall)
    }
}

/**
 * Tombol yang tersedia = status tiket × peran akun. Ditulis sebagai satu blok
 * supaya tak ada dua tempat yang bisa berbeda pendapat soal "kapan tombol ini
 * boleh muncul".
 */
@Composable
private fun Aksi(
    tiket: HsTicketDetailDto,
    state: HsDetailUiState,
    userId: String?,
    onTugaskan: () -> Unit,
    onMintaTarik: () -> Unit,
    onBatalkan: () -> Unit,
    onMulai: () -> Unit,
    onSelesai: () -> Unit,
    onTugaskanDriver: () -> Unit,
    onBatalTarik: () -> Unit,
    onAmbilUnit: () -> Unit,
) {
    if (tiket.status in HS_STATUS_FINAL) {
        Text(
            "Tiket sudah ${labelStatusHs(tiket.status).lowercase()} — tak ada aksi lagi.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    val tugasSaya = !userId.isNullOrBlank() && tiket.assignedTeknisiId == userId
    val tarikSaya = !userId.isNullOrBlank() && tiket.tarikDriverId == userId

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.bolehDispatch && tiket.status in HS_STATUS_TRIASE) {
            ExpressiveFilledButton(
                onClick = onTugaskan,
                enabled = !state.sibuk,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Tugaskan teknisi") }
            ExpressiveOutlinedButton(
                onClick = onMintaTarik,
                enabled = !state.sibuk,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Minta tarik unit") }
        }

        if (tugasSaya && tiket.status == HS_DITUGASKAN) {
            ExpressiveFilledButton(
                onClick = onMulai,
                enabled = !state.sibuk,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Mulai kunjungan") }
        }
        if (tugasSaya && tiket.status == HS_DIKERJAKAN) {
            ExpressiveFilledButton(
                onClick = onSelesai,
                enabled = !state.sibuk,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Tutup kunjungan") }
        }

        if (state.bolehAturTarik && tiket.status == HS_MENUNGGU_TARIK) {
            ExpressiveFilledButton(
                onClick = onTugaskanDriver,
                enabled = !state.sibuk,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Tugaskan driver") }
        }
        if (tarikSaya && tiket.status == HS_TARIK_DITUGASKAN) {
            ExpressiveFilledButton(
                onClick = onAmbilUnit,
                enabled = !state.sibuk,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Unit sudah diambil") }
        }
        if ((state.bolehAturTarik || state.bolehDispatch) && tiket.status in HS_STATUS_TARIK_AKTIF) {
            ExpressiveOutlinedButton(
                onClick = onBatalTarik,
                enabled = !state.sibuk,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Batalkan penarikan") }
        }

        if (state.bolehDispatch) {
            ExpressiveOutlinedButton(
                onClick = onBatalkan,
                enabled = !state.sibuk,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Batalkan tiket") }
        }
    }
}

@Composable
private fun KartuKunjungan(visit: HsVisitDto, token: String?) {
    ClayCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Text(
                "Kunjungan ${visit.urutan}" + (visit.teknisiNama?.let { " · $it" } ?: ""),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            visit.hasil?.takeIf { it.isNotBlank() }?.let {
                Text(labelHasil(it), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            visit.tindakan?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
            visit.catatan?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (visit.sparepartItems.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                visit.sparepartItems.forEach {
                    Text(
                        "${it.nama} ×${it.qty}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            visit.fotoUrls.forEach { raw ->
                fotoHsUrl(raw)?.let {
                    Spacer(Modifier.height(8.dp))
                    AuthedFoto(it, token, "Bukti kunjungan")
                }
            }
        }
    }
}

@Composable
private fun DialogPilihOrang(
    judul: String,
    orang: List<DriverDto>,
    keterangan: String?,
    sibuk: Boolean,
    onTutup: () -> Unit,
    onPilih: (String, String?) -> Unit,
) {
    var dipilih by remember { mutableStateOf<String?>(null) }
    var tanggal by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onTutup,
        title = { Text(judul, fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                keterangan?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(8.dp))
                }
                if (orang.isEmpty() && keterangan == null) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                }
                orang.forEach { o ->
                    val id = o.effectiveId
                    FilterChip(
                        selected = dipilih == id,
                        onClick = { dipilih = id },
                        label = { Text(o.name + (o.nik?.let { " — $it" } ?: "")) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = tanggal,
                    onValueChange = { tanggal = it },
                    label = { Text("Jadwal (YYYY-MM-DD, opsional)") },
                    modifier = Modifier.fillMaxWidth(),
                )
                // Server menolak format lain dengan 400 — beri tahu SEBELUM kirim,
                // bukan sesudah dialognya tertutup.
                if (tanggal.isNotBlank() && jadwalUntukServer(tanggal) == null) {
                    Text(
                        "Format tanggal harus YYYY-MM-DD.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !sibuk && dipilih != null &&
                    (tanggal.isBlank() || jadwalUntukServer(tanggal) != null),
                onClick = { dipilih?.let { onPilih(it, tanggal.ifBlank { null }) } },
            ) { Text("Tugaskan") }
        },
        dismissButton = { TextButton(onClick = onTutup) { Text("Batal") } },
    )
}

@Composable
private fun DialogAlasan(
    judul: String,
    penjelasan: String,
    onTutup: () -> Unit,
    onKirim: (String) -> Unit,
) {
    var alasan by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onTutup,
        title = { Text(judul, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(penjelasan, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = alasan,
                    onValueChange = { alasan = it },
                    placeholder = { Text("Alasan…") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = bolehAlasan(alasan).ok,
                onClick = { onKirim(alasan.trim()) },
            ) { Text("Kirim") }
        },
        dismissButton = { TextButton(onClick = onTutup) { Text("Batal") } },
    )
}

@Composable
private fun DialogTutupKunjungan(
    fotoTerunggah: List<String>,
    mengunggah: Boolean,
    onFoto: () -> Unit,
    onTutup: () -> Unit,
    onKirim: (String, String?, String?) -> Unit,
) {
    var hasil by remember { mutableStateOf("selesai") }
    var tindakan by remember { mutableStateOf("") }
    var catatan by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onTutup,
        title = { Text("Tutup kunjungan", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                listOf(
                    "selesai" to "Selesai",
                    "kunjungan_ulang" to "Perlu kunjungan ulang",
                    "eskalasi" to "Eskalasi",
                ).forEach { (kunci, label) ->
                    FilterChip(
                        selected = hasil == kunci,
                        onClick = { hasil = kunci },
                        label = { Text(label) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
                ExpressiveOutlinedButton(
                    onClick = onFoto,
                    enabled = !mengunggah,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Rounded.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        when {
                            mengunggah -> "Mengunggah…"
                            fotoTerunggah.isEmpty() -> "Foto bukti kerja"
                            else -> "${fotoTerunggah.size} foto siap — tambah lagi"
                        }
                    )
                }
                // Aturan server: hasil "selesai" WAJIB berfoto. Dinyatakan di sini
                // supaya teknisi tak kehilangan isian gara-gara 400 di ujung.
                val gate = bolehTutupKunjungan(hasil, fotoTerunggah, false, emptyList(), null, null, null)
                if (!gate.ok) {
                    Text(
                        gate.alasan.orEmpty(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = tindakan,
                    onValueChange = { tindakan = it },
                    label = { Text("Tindakan") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = catatan,
                    onValueChange = { catatan = it },
                    label = { Text("Catatan (opsional)") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    // Sparepart & biaya sengaja belum ada di app: nominalnya
                    // dihitung server dari daftar sparepart dan menuntut bukti
                    // bayar — alur uang yang belum diuji di lapangan lewat HP.
                    "Penggantian sparepart berbiaya masih lewat web.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = bolehTutupKunjungan(hasil, fotoTerunggah, false, emptyList(), null, null, null).ok,
                onClick = { onKirim(hasil, tindakan, catatan) },
            ) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onTutup) { Text("Batal") } },
    )
}

/** Foto komplain di-serve terautentikasi — pola `AuthedImage` (Deadstock/Indent). */
@Composable
private fun AuthedFoto(url: String, token: String?, deskripsi: String) {
    val context = LocalContext.current
    val request = remember(url, token) {
        ImageRequest.Builder(context)
            .data(url)
            .apply { if (!token.isNullOrBlank()) addHeader("Authorization", "Bearer $token") }
            .build()
    }
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f),
    ) {
        SubcomposeAsyncImage(
            model = request,
            contentDescription = deskripsi,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        ) {
            when (painter.state) {
                is AsyncImagePainter.State.Success -> SubcomposeAsyncImageContent()
                is AsyncImagePainter.State.Loading -> Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize(),
                ) { CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp) }
                else -> Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Text("Foto gagal dimuat", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
