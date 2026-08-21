package com.krisoft.tridjayaelektronik.ui.aktivitas

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Assignment
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.krisoft.tridjayaelektronik.data.model.AktivitasItemDto
import com.krisoft.tridjayaelektronik.ui.theme.ClayCard
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveEmptyState
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveErrorState
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveFilledButton
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveInlineError
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveOutlinedButton
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveTextButton
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveTextField
import com.krisoft.tridjayaelektronik.ui.theme.ScrollableCenter
import com.krisoft.tridjayaelektronik.ui.theme.SkeletonCard
import com.krisoft.tridjayaelektronik.ui.theme.TridjayaCollapsibleHeader
import com.krisoft.tridjayaelektronik.ui.theme.TridjayaPullRefresh
import com.krisoft.tridjayaelektronik.util.bacaInfoBerkas
import java.io.File

/**
 * Input Aktivitas (raport harian) — BETA.
 *
 * Satu baris per aktivitas posisi karyawan. Buktinya: foto kamera, sampai
 * [MAX_GAMBAR] gambar dari galeri, satu video, atau "tanpa bukti + alasan".
 * Kirim per baris (server upsert per baris), jadi sinyal putus di tengah tak
 * membatalkan baris yang sudah masuk.
 *
 * Berkas dipilih dulu ke staging lalu dikirim lewat satu tombol — BUKAN
 * auto-kirim seperti versi kamera-saja: server menimpa `bukti_url` seluruhnya,
 * jadi menambah satu foto berarti mengirim ulang daftar lengkapnya.
 *
 * Picker-nya `PickVisualMedia`/`PickMultipleVisualMedia` (Photo Picker) dan
 * **tidak butuh izin apa pun**. Di bawah Android 11/13 ia turun sendiri ke SAF —
 * itu perilaku yang benar, JANGAN ditambal dengan `READ_MEDIA_*` /
 * `READ_EXTERNAL_STORAGE`.
 */
@Composable
fun AktivitasScreen(
    onBack: () -> Unit,
    viewModel: AktivitasViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val resolver = context.contentResolver

    // Satu launcher dipakai semua baris — index yang sedang diisi dipegang di
    // sini karena callback-nya baru jalan setelah kembali dari app lain.
    var pending by remember { mutableStateOf<Pair<Int, File>?>(null) }
    var indexAktif by remember { mutableStateOf<Int?>(null) }
    var slot by remember { mutableIntStateOf(0) }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        pending?.let { (index, file) -> if (ok) viewModel.tambahFotoKamera(index, file) }
        pending = null
    }

    // Gambar disalin ke cache SEKARANG JUGA, bukan saat Kirim: grant Uri dari
    // picker tidak persistable dan hilang begitu proses mati.
    val galeri = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MAX_GAMBAR)
    ) { uris ->
        val index = indexAktif ?: return@rememberLauncherForActivityResult
        indexAktif = null
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        var gagal = 0
        val files = uris.mapNotNull { uri ->
            val (_, ukuran) = bacaInfoBerkas(resolver, uri)
            // Hanya yang TERBUKTI kebesaran dibuang; ukuran 0 = tak terbaca.
            if (ukuran > MAX_GAMBAR_INPUT_BYTES) {
                gagal++
                return@mapNotNull null
            }
            // Nama berkasnya PATH FileProvider, bukan teks layar — ejaan
            // "jobdesk" sengaja tak ikut diganti (lihat `res/xml/file_paths.xml`).
            val target = File(context.cacheDir, "raport/jobdesk_${index}_g${slot++}.jpg")
                .apply { parentFile?.mkdirs() }
            runCatching {
                resolver.openInputStream(uri)?.use { inp ->
                    target.outputStream().use { out -> inp.copyTo(out) }
                } ?: error("stream null")
            }.fold(onSuccess = { target }, onFailure = { gagal++; null })
        }
        viewModel.tambahFotoGaleri(index, files, diabaikan = gagal)
    }

    val videoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        val index = indexAktif ?: return@rememberLauncherForActivityResult
        indexAktif = null
        if (uri == null) return@rememberLauncherForActivityResult
        val (nama, ukuran) = bacaInfoBerkas(resolver, uri)
        viewModel.pilihVideo(index, uri, nama, ukuran)
    }

    var alasanUntukIndex by remember { mutableStateOf<Int?>(null) }

    TridjayaCollapsibleHeader(title = "Input Aktivitas", onBack = onBack) { contentModifier ->
        val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        TridjayaPullRefresh(
            isRefreshing = state.isLoading && state.aktivitas.isNotEmpty(),
            // Selagi satu baris sedang mengunggah, refresh() akan menimpa `submitted`
            // dan membuat baris itu tampak mundur — abaikan tariknya sampai selesai.
            onRefresh = { if (state.busyIndex == null) viewModel.refresh() },
            modifier = contentModifier
        ) {
            when {
                state.isLoading && state.aktivitas.isEmpty() -> Column(modifier = Modifier.padding(top = 4.dp)) {
                    repeat(5) { SkeletonCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }
                }

                state.error != null -> ScrollableCenter {
                    ExpressiveErrorState(
                        message = state.error ?: "Gagal memuat",
                        onRetry = viewModel::refresh
                    )
                }

                state.aktivitas.isEmpty() -> ScrollableCenter {
                    ExpressiveEmptyState(
                        icon = {
                            Icon(
                                Icons.AutoMirrored.Rounded.Assignment,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        title = "Aktivitas belum diatur",
                        // Yang disebut harus SEBABNYA. Daftar aktivitas kini
                        // dipilih dari PENEMPATAN KPI; kalau penempatan itu tak
                        // punya divisi di master, menyebut tag divisi menyuruh
                        // PIC menambahkan divisi yang sama sekali lain — dan
                        // orangnya tetap tak bisa mengisi laporan.
                        subtitle = if (state.penempatanId.isNotBlank()) {
                            "Penempatan \"${state.penempatanId}\" belum punya daftar aktivitas " +
                                "di Master Aktivitas. Minta PIC Aktivitas menambahkannya."
                        } else {
                            "Divisi \"${state.divisi.ifBlank { "-" }}\" belum punya daftar aktivitas " +
                                "di Master Aktivitas. Minta PIC Aktivitas menambahkannya."
                        }
                    )
                }

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp + navBottom),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item { Ringkasan(state) }
                    // Hari Minggu diberitahukan DI DEPAN, bukan setelah orang
                    // memotret, memilih 10 foto, dan menunggu seluruh unggahan
                    // selesai. Web sudah lama menutupnya di klien; app-lah yang
                    // tak punya cerminannya, dan itu terukur: Minggu 16 Agustus
                    // 2026 ada 36 berkas terunggah dengan NOL baris tercatat.
                    if (hariMinggu(System.currentTimeMillis())) {
                        item {
                            Text(
                                PESAN_HARI_MINGGU,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                            )
                        }
                    }
                    state.message?.let { pesan ->
                        item {
                            ExpressiveInlineError(
                                message = pesan,
                                onRetry = viewModel::consumeMessage,
                                retryLabel = "Tutup"
                            )
                        }
                    }
                    itemsIndexed(state.aktivitas) { index, aktivitas ->
                        AktivitasRow(
                            nomor = index + 1,
                            aktivitas = aktivitas,
                            terkirim = state.submitted[index],
                            pilihan = state.pilihan[index],
                            progres = state.kirim?.takeIf { it.index == index },
                            enabled = state.busyIndex == null,
                            onKamera = {
                                // Nama ber-slot: satu baris kini boleh punya beberapa
                                // jepretan, dan nama tetap membuat jepretan kedua
                                // menimpa yang pertama tanpa tanda apa pun di layar.
                                // Ejaan "jobdesk" di sini PATH FileProvider, bukan
                                // teks layar — sengaja tak ikut diganti.
                                val file = File(context.cacheDir, "raport/jobdesk_${index}_k${slot++}.jpg")
                                    .apply { parentFile?.mkdirs() }
                                pending = index to file
                                camera.launch(
                                    FileProvider.getUriForFile(
                                        context, "${context.packageName}.fileprovider", file
                                    )
                                )
                            },
                            onGaleri = {
                                indexAktif = index
                                galeri.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            onVideo = {
                                indexAktif = index
                                videoPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                )
                            },
                            onHapusGambar = { posisi -> viewModel.hapusGambar(index, posisi) },
                            onHapusVideo = { viewModel.hapusVideo(index) },
                            onKirim = { viewModel.kirimBukti(index, resolver) },
                            onTanpaBukti = { alasanUntukIndex = index },
                        )
                    }
                }
            }
        }
    }

    // Penolakan PERMANEN dipajang sebagai dialog, bukan sebagai baris di tengah
    // daftar seperti `state.message`. Dua sebab, dan keduanya terukur:
    //   1. `ExpressiveInlineError` dirender sebagai item LazyColumn pada posisi
    //      tetap — karyawan yang sudah menggulir ke aktivitas ke-7 tidak
    //      melihatnya sama sekali;
    //   2. sampai vc97 pesannya diakhiri "Tekan Kirim bukti lagi", yang untuk
    //      400 permanen berarti mengulang kegagalan yang sama tanpa ujung.
    //      Tiga karyawan melakukannya 10-13 kali pada 2026-08-21.
    // Dialog menghentikan alurnya dan memaksa penolakannya dibaca.
    state.blokir?.let { blokir ->
        AlertDialog(
            onDismissRequest = viewModel::consumeBlokir,
            title = { Text(blokir.judul) },
            text = {
                Text(
                    // APA ADANYA dari server. Kalimatnya sudah memuat langkah
                    // konkret dan berlaku untuk web maupun app; memarafrasenya
                    // di sini melahirkan versi kedua yang akan berselisih.
                    blokir.isi,
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            // Satu tombol saja, dan BUKAN "Coba lagi": tak ada yang bisa
            // dicoba lagi — itu justru kekeliruan yang sedang ditutup.
            confirmButton = {
                ExpressiveFilledButton(onClick = viewModel::consumeBlokir) { Text("Mengerti") }
            },
        )
    }

    alasanUntukIndex?.let { index ->
        AlasanDialog(
            onDismiss = { alasanUntukIndex = null },
            onSubmit = { alasan ->
                alasanUntukIndex = null
                viewModel.submitWithoutEvidence(index, alasan)
            }
        )
    }
}

@Composable
private fun Ringkasan(state: AktivitasUiState) {
    Column(modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = state.posisi.ifBlank { "Laporan aktivitas harian" },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(Modifier.size(8.dp))
            Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.primaryContainer) {
                Text(
                    "BETA",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
        Text(
            text = "${state.terkirim}/${state.aktivitas.size} aktivitas terkirim hari ini · " +
                "bukti: foto kamera/galeri (maks $MAX_GAMBAR) atau satu video",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AktivitasRow(
    nomor: Int,
    aktivitas: String,
    terkirim: AktivitasItemDto?,
    pilihan: PilihanBukti?,
    progres: KirimProgres?,
    enabled: Boolean,
    onKamera: () -> Unit,
    onGaleri: () -> Unit,
    onVideo: () -> Unit,
    onHapusGambar: (Int) -> Unit,
    onHapusVideo: () -> Unit,
    onKirim: () -> Unit,
    onTanpaBukti: () -> Unit,
) {
    val status = rowStatus(terkirim)
    val gambar = pilihan?.gambar.orEmpty()
    val video = pilihan?.video
    val adaStaging = gambar.isNotEmpty() || video != null
    val gate = gateKirimBukti(gambar.size, video != null, video?.ukuranBytes ?: 0L)
    // Sudah dinilai PIC = server menolak penimpaan. Seluruh tombol sumber bukti
    // ikut mati lewat `bolehIsi`, dan alasannya ditulis — tombol mati tanpa
    // keterangan terbaca sebagai aplikasi rusak, dan yang dibutuhkan orangnya
    // bukan "tidak bisa" melainkan "harus minta siapa".
    val terkunci = terkunciPic(terkirim?.reviewStatus)
    val bolehIsi = enabled && !terkunci

    ClayCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "$nomor. $aktivitas",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.size(6.dp))
            StatusChip(status, terkirim)
            Spacer(Modifier.size(10.dp))

            if (progres != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.size(10.dp))
                    Column {
                        Text(progres.label, style = MaterialTheme.typography.labelMedium)
                        Text(
                            "Jangan tutup layar ini",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                return@Column
            }

            if (terkunci) {
                Text(
                    PESAN_TERKUNCI_PIC,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(10.dp))
            }

            // Baris 1 — sumber bukti. Tombol lawan DINONAKTIFKAN, bukan
            // disembunyikan: tombol yang hilang-muncul lebih membingungkan
            // daripada tombol mati yang jelas alasannya.
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ExpressiveFilledButton(
                    onClick = onKamera,
                    enabled = bolehIsi && video == null && gambar.size < MAX_GAMBAR,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Rounded.PhotoCamera, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("Kamera")
                }
                ExpressiveOutlinedButton(
                    onClick = onGaleri,
                    enabled = bolehIsi && video == null && gambar.size < MAX_GAMBAR,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("Galeri")
                }
                ExpressiveOutlinedButton(
                    onClick = onVideo,
                    enabled = bolehIsi && gambar.isEmpty(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Rounded.Videocam, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("Video")
                }
            }

            if (adaStaging) {
                Spacer(Modifier.size(10.dp))
                if (video != null) {
                    VideoTerpilih(video, enabled, onHapusVideo)
                } else {
                    PratinjauGambar(gambar, enabled, onHapusGambar)
                }
            }

            Spacer(Modifier.size(10.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ExpressiveTextButton(onClick = onTanpaBukti, enabled = bolehIsi) { Text("Tanpa bukti") }
                Spacer(Modifier.weight(1f))
                if (adaStaging) {
                    ExpressiveFilledButton(
                        onClick = onKirim,
                        enabled = bolehIsi && gate.ok,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    ) {
                        Text(if (video != null) "Kirim video" else "Kirim bukti (${gambar.size})")
                    }
                }
            }
            if (adaStaging && !gate.ok) {
                Text(
                    gate.alasan.orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

/**
 * Pratinjau gambar staging. Bukti LAMA (`file == null`) SENGAJA tidak dirender
 * lewat jaringan: guard kepemilikan server mencocokkan `bukti_url` persis, jadi
 * baris multi-gambar menjawab 404 untuk karyawan pemiliknya sendiri (lihat KDoc
 * `AktivitasBuktiPlan.kt`). Kotak berlabel "Terkirim" jujur; gambar rusak tidak.
 */
@Composable
private fun PratinjauGambar(
    gambar: List<GambarBukti>,
    enabled: Boolean,
    onHapus: (Int) -> Unit,
) {
    Column {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            itemsIndexed(gambar) { i, item ->
                Box {
                    if (item.file != null) {
                        AsyncImage(
                            model = item.file,
                            contentDescription = "Bukti ${i + 1}",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(64.dp)
                                .clip(MaterialTheme.shapes.small),
                        )
                    } else {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            modifier = Modifier.size(64.dp),
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                Icon(
                                    Icons.Rounded.CloudDone,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    "Terkirim",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(2.dp),
                    ) {
                        IconButton(
                            onClick = { onHapus(i) },
                            enabled = enabled,
                            modifier = Modifier.size(22.dp),
                        ) {
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = "Hapus bukti ${i + 1}",
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                }
            }
        }
        Text(
            "${gambar.size} gambar · maks $MAX_GAMBAR",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/**
 * Video terpilih. Tanpa thumbnail — app ini sengaja tak memuat pustaka pemutar
 * video, dan layar penilaian PIC pun tak memutarnya. Karyawan diberi tahu itu
 * supaya tak mengira penilaiannya macet.
 */
@Composable
private fun VideoTerpilih(video: VideoBukti, enabled: Boolean, onHapus: () -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Rounded.Movie,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.size(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    video.nama.ifBlank { "Video bukti" },
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    formatUkuranBerkas(video.ukuranBytes),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onHapus, enabled = enabled, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Rounded.Close, contentDescription = "Hapus video", modifier = Modifier.size(16.dp))
            }
        }
        Text(
            "Bukti video hanya bisa dinilai lewat web, jadi penilaiannya bisa lebih lambat.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun StatusChip(status: AktivitasRowStatus, terkirim: AktivitasItemDto?) {
    val (label, warna) = when (status) {
        AktivitasRowStatus.BELUM -> "Belum dikirim" to MaterialTheme.colorScheme.surfaceContainerHighest
        AktivitasRowStatus.MENUNGGU -> "Menunggu review PIC" to MaterialTheme.colorScheme.tertiaryContainer
        AktivitasRowStatus.DISETUJUI -> "Disetujui PIC" to MaterialTheme.colorScheme.primaryContainer
        AktivitasRowStatus.DITOLAK -> "Ditolak PIC" to MaterialTheme.colorScheme.errorContainer
    }
    Column {
        Surface(shape = MaterialTheme.shapes.small, color = warna) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
        // Alasan "tanpa bukti" & komentar reviewer ditampilkan apa adanya —
        // keduanya yang menentukan nilai raport, jadi jangan disembunyikan.
        terkirim?.employeeNote?.takeIf { it.isNotBlank() }?.let {
            Text(
                "Alasan: $it",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        terkirim?.reviewerComment?.takeIf { it.isNotBlank() }?.let {
            Text(
                "Catatan PIC: $it",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
private fun AlasanDialog(onDismiss: () -> Unit, onSubmit: (String) -> Unit) {
    var alasan by remember { mutableStateOf("") }
    val cukup = alasan.trim().length >= AktivitasViewModel.MIN_REASON_LENGTH
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tanpa bukti") },
        text = {
            Column {
                Text(
                    "Aktivitas tanpa bukti biasanya dinilai 0 oleh PIC. Jelaskan alasannya.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(8.dp))
                ExpressiveTextField(
                    value = alasan,
                    onValueChange = { alasan = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = "Alasan (minimal ${AktivitasViewModel.MIN_REASON_LENGTH} karakter)",
                    singleLine = false,
                    isError = alasan.isNotBlank() && !cukup,
                    supportingText = "${alasan.trim().length}/${AktivitasViewModel.MIN_REASON_LENGTH}",
                )
            }
        },
        confirmButton = {
            ExpressiveFilledButton(onClick = { onSubmit(alasan) }, enabled = cukup) { Text("Kirim") }
        },
        dismissButton = { ExpressiveOutlinedButton(onClick = onDismiss) { Text("Batal") } }
    )
}
