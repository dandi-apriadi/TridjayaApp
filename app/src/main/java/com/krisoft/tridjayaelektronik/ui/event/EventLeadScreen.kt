package com.krisoft.tridjayaelektronik.ui.event

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.krisoft.tridjayaelektronik.ui.theme.ClayCard
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveFilledButton
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveFormError
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveOutlinedButton
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveTextButton
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveTextField
import com.krisoft.tridjayaelektronik.ui.theme.TridjayaCollapsibleHeader
import com.krisoft.tridjayaelektronik.util.PESAN_KAMERA_TAK_TERSIMPAN
import java.io.File

/**
 * Isi prospek event: antrean di depan stan tidak menunggu, jadi SEMUA field opsional dan
 * sukses TIDAK menutup layar — form dikosongkan dan sales langsung lanjut ke orang berikutnya.
 *
 * Tombol Simpan mati sampai minimal satu field terisi ([adaIsian]); itu cerminan aturan
 * server, bukan penggantinya.
 */
@Composable
fun EventLeadScreen(
    eventId: String,
    onBack: () -> Unit,
    viewModel: EventViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val form = state.form
    val namaEvent = state.events.firstOrNull { it.id == eventId }?.nama
    // VM ini instance sendiri (NavBackStackEntry layar ini), jadi daftar event-nya kosong
    // sampai diminta — judul layar bergantung padanya. Lihat `EventViewModel.muat()` soal
    // kenapa pemuatan tidak lagi di `init`.
    LaunchedEffect(Unit) { viewModel.muat() }

    // Kamera & galeri bermuara ke berkas cache yang SAMA supaya jalur unggahnya cuma satu,
    // bukan dua yang bisa menyimpang diam-diam (pola `BuktiAccField` di SpkItemCard).
    // Nama TETAP, dan TANPA sapuan direktori — dua-duanya disengaja.
    //
    // `remember{}` berjalan ulang saat Activity dibuat ulang, dan app kamera rutin
    // menendang proses host dari memori di HP low-RAM (persis HP yang dipakai di bazar).
    // Urutannya pasti, bukan balapan: badan komposisi jalan lebih dulu, `rememberLauncher…`
    // baru mendaftar lewat DisposableEffect, dan hasil kamera yang tertunda dikirim
    // SESUDAH itu. Jadi nama ber-timestamp + sapuan `listFiles().delete()` akan
    // mengosongkan direktori lalu menunjuk berkas baru tepat sebelum callback membaca
    // hasil jepretan — foto yang benar-benar terambil dihapus sendiri oleh app, dan sales
    // melihat "Foto tidak terbaca" berulang selamanya.
    //
    // Konsekuensi yang diterima: proses yang mati di antara jepret dan unggah meninggalkan
    // SATU berkas di cache. Ia ditimpa jepretan/pilihan berikutnya (mode tulis) dan dihapus
    // `unggahKtp` begitu bytes-nya terbaca, jadi paling banyak satu, bukan menumpuk.
    val fileKtp = remember {
        File(context.cacheDir, "event").apply { mkdirs() }.let { File(it, "ktp.jpg") }
    }
    val uriKtp = remember {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", fileKtp)
    }
    val kamera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        // `ok == false` tak lagi ditelan: kegagalan simpan foto kamera dulu
        // menghasilkan nol pesan, jadi petugas menyangka buktinya terkirim.
        // Lihat [PESAN_KAMERA_TAK_TERSIMPAN].
        if (ok) viewModel.unggahKtp(fileKtp)
        else viewModel.laporError(PESAN_KAMERA_TAK_TERSIMPAN)
    }
    // Picker-nya `PickVisualMedia` (Photo Picker), tak butuh izin penyimpanan. Bukti dari lapangan
    // sering sudah berupa tangkapan layar/foto lama, jadi galeri bukan pelengkap — ia jalur setara.
    //
    // Sampai 2026-08-28 baris ini memakai `GetContent()` sambil menyebutnya Photo Picker; itu
    // keliru — `GetContent()` = `ACTION_GET_CONTENT`, pemilih dokumen lama, yang justru
    // memperbesar peluang URI-nya tak bisa dibuka. Lihat `KuponGebyarScreen.kt`.
    val galeri = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { dipilih ->
        if (dipilih == null) return@rememberLauncherForActivityResult
        // Gagal menyalin BUKAN kasus langka: foto Google Photos yang belum terunduh membuat
        // `openInputStream` melempar, dan tanpa pesan sales cuma melihat layar diam total.
        // Jenis exception-nya ikut dilaporkan supaya sebabnya bisa dibedakan saat ada laporan.
        // `openInputStream` sendiri bisa melempar — WAJIB di dalam `runCatching`.
        runCatching {
            val masuk = context.contentResolver.openInputStream(dipilih)
                ?: error("galeri tak memberi isi berkas")
            masuk.use { input ->
                fileKtp.outputStream().use { output -> input.copyTo(output) }
            }
        }.fold(
            onSuccess = { viewModel.unggahKtp(fileKtp, dariGaleri = true) },
            onFailure = { e ->
                viewModel.laporError(
                    "Foto itu tidak bisa dibaca (${e.javaClass.simpleName}) — mungkin masih di " +
                        "Google Photos dan belum terunduh. Coba ambil ulang lewat Kamera."
                )
            },
        )
    }

    TridjayaCollapsibleHeader(title = namaEvent ?: "Prospek Event", onBack = onBack) { contentModifier ->
        val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        Column(
            modifier = contentModifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp + navBottom),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Cukup isi yang sempat ditanyakan — minimal satu. Sisanya bisa dilengkapi " +
                    "belakangan lewat tindak lanjut.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            ClayCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Foto KTP",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    state.ktpPratinjau?.let { bmp ->
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Foto KTP konsumen",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(10.dp)),
                        )
                    }
                    if (state.mengunggahKtp) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Text("Mengunggah foto…", style = MaterialTheme.typography.bodySmall)
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ExpressiveOutlinedButton(
                                onClick = { kamera.launch(uriKtp) },
                                modifier = Modifier.weight(1f),
                            ) { Text("Kamera") }
                            ExpressiveOutlinedButton(
                                onClick = { galeri.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                                modifier = Modifier.weight(1f),
                            ) { Text("Galeri") }
                        }
                    }
                    if (form.fotoKtpUrl.isNotBlank()) {
                        ExpressiveTextButton(onClick = { viewModel.hapusKtp() }) { Text("Hapus foto") }
                    }
                }
            }

            ExpressiveTextField(
                value = form.noWa,
                onValueChange = { v -> viewModel.ubahForm { it.copy(noWa = v) } },
                modifier = Modifier.fillMaxWidth(),
                label = "No. WhatsApp",
                keyboardType = KeyboardType.Phone,
            )
            ExpressiveTextField(
                value = form.namaKonsumen,
                onValueChange = { v -> viewModel.ubahForm { it.copy(namaKonsumen = v) } },
                modifier = Modifier.fillMaxWidth(),
                label = "Nama konsumen",
            )
            ExpressiveTextField(
                value = form.minat,
                onValueChange = { v -> viewModel.ubahForm { it.copy(minat = v) } },
                modifier = Modifier.fillMaxWidth(),
                label = "Minat",
                placeholder = "Kulkas 2 pintu, mesin cuci…",
            )
            ExpressiveTextField(
                value = form.alamat,
                onValueChange = { v -> viewModel.ubahForm { it.copy(alamat = v) } },
                modifier = Modifier.fillMaxWidth(),
                label = "Alamat",
                singleLine = false,
            )

            ExpressiveFilledButton(
                onClick = { viewModel.kirim(eventId) },
                modifier = Modifier.fillMaxWidth(),
                enabled = state.bolehSimpan,
            ) {
                Text(if (state.mengirim) "Menyimpan…" else "Simpan Prospek")
            }
            if (!adaIsian(form)) {
                Text(
                    text = "Isi minimal satu data konsumen untuk menyimpan.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            state.pesanSukses?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            state.pesanError?.let { ExpressiveFormError(it) }
        }
    }
}
