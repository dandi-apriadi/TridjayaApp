package com.krisoft.tridjayaelektronik.ui.aktivitas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material.icons.rounded.RateReview
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import com.krisoft.tridjayaelektronik.data.model.AktivitasItemDto
import com.krisoft.tridjayaelektronik.ui.theme.ClayCard
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveEmptyState
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveErrorState
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveFilledButton
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveOutlinedButton
import com.krisoft.tridjayaelektronik.ui.theme.ScrollableCenter
import com.krisoft.tridjayaelektronik.ui.theme.TridjayaCollapsibleHeader
import com.krisoft.tridjayaelektronik.ui.theme.TridjayaPullRefresh

/**
 * Antrian penilaian PIC raport — aktivitas harian karyawan disetujui/ditolak
 * di sini.
 *
 * Barisnya DIKELOMPOKKAN per karyawan (bukan daftar rata) mengikuti alur web
 * `PicAktivitasDashboardPage`: PIC menilai satu orang sekaligus, dan daftar rata
 * membuat aktivitas milik orang yang sama tercecer di antara karyawan lain.
 *
 * Tanpa pemutar video di dalam app: baris
 * ber-`mode=video` menampilkan penanda "bukti video" dan menyerahkan
 * pemeriksaannya ke web — endpoint videonya butuh header `Authorization`,
 * jadi URL polos tak bisa diserahkan ke pemutar.
 */
@Composable
fun AktivitasReviewScreen(
    onBack: () -> Unit,
    viewModel: AktivitasReviewViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var tolakId by remember { mutableStateOf<String?>(null) }
    var alasan by remember { mutableStateOf("") }
    var setujuId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { viewModel.muat() }

    TridjayaCollapsibleHeader(title = "Nilai Aktivitas", onBack = onBack) { contentModifier ->
        val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        TridjayaPullRefresh(
            isRefreshing = state.loading && state.grup.isNotEmpty(),
            onRefresh = { viewModel.muat() },
            modifier = contentModifier,
        ) {
            Column(Modifier.fillMaxSize()) {
                FilterBar(
                    tanggal = state.tanggal,
                    status = state.status,
                    onGeserHari = { viewModel.geserHari(it) },
                    onStatus = { viewModel.gantiStatus(it) },
                )
                // `weight(1f)`: isi di bawah memakai fillMaxSize, dan tanpa
                // pembatas ini ia meminta tinggi penuh layar SETELAH filter —
                // bagian bawah daftarnya terdorong keluar layar.
                Box(Modifier.weight(1f)) {
                    IsiAntrian(
                        state = state,
                        navBottom = navBottom,
                        token = viewModel.bearerToken(),
                        onMuatUlang = { viewModel.muat() },
                        onSetuju = { setujuId = it },
                        onTolak = { tolakId = it; alasan = "" },
                    )
                }
            }
        }
    }

    setujuId?.let { id ->
        DialogSetuju(
            onBatal = { setujuId = null },
            onSetuju = {
                viewModel.putuskan(id, "approved")
                setujuId = null
            },
        )
    }

    tolakId?.let { id ->
        AlertDialog(
            onDismissRequest = { tolakId = null },
            title = { Text("Tolak laporan ini?", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        "Laporan yang ditolak otomatis bernilai 0. Tulis apa yang harus " +
                            "diperbaiki — itu satu-satunya penjelasan yang karyawan terima.",
                        style = MaterialTheme.typography.bodySmall,
                    )
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
                    // Aturan yang SAMA dipakai ViewModel sebelum memanggil server.
                    enabled = bolehSimpanReview("rejected", alasan).ok,
                    onClick = {
                        viewModel.putuskan(id, "rejected", komentar = alasan.trim())
                        tolakId = null
                    },
                ) { Text("Tolak") }
            },
            dismissButton = { TextButton(onClick = { tolakId = null }) { Text("Batal") } },
        )
    }
}

@Composable
private fun IsiAntrian(
    state: AktivitasReviewUiState,
    navBottom: androidx.compose.ui.unit.Dp,
    token: String?,
    onMuatUlang: () -> Unit,
    onSetuju: (String) -> Unit,
    onTolak: (String) -> Unit,
) {
    when {
        state.loading && state.grup.isEmpty() ->
            ScrollableCenter { CircularProgressIndicator() }

        state.error != null && state.grup.isEmpty() ->
            ScrollableCenter {
                ExpressiveErrorState(
                    message = state.error ?: "Gagal memuat antrian.",
                    onRetry = onMuatUlang,
                )
            }

        state.grup.isEmpty() ->
            ScrollableCenter {
                ExpressiveEmptyState(
                    icon = {
                        Icon(
                            Icons.Rounded.RateReview,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(30.dp),
                        )
                    },
                    title = "Tidak ada laporan yang menunggu.",
                    subtitle = "Semua laporan pada tanggal ini sudah dinilai.",
                )
            }

        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp + navBottom,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "hitungan") {
                val termuat = state.grup.sumOf { it.baris.size }
                Text(
                    // `total` bisa lebih besar dari yang termuat (server memotong
                    // ke `limit`) — menyembunyikan bedanya membuat PIC mengira
                    // pekerjaannya habis padahal belum.
                    if (state.total > termuat) {
                        "$termuat dari ${state.total} laporan · ${state.grup.size} karyawan"
                    } else {
                        "$termuat laporan · ${state.grup.size} karyawan"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            (state.pesan ?: state.error)?.let { pesan ->
                item(key = "pesan") {
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
            }
            items(state.grup, key = { it.employeeId }) { grup ->
                KartuKaryawan(
                    grup = grup,
                    token = token,
                    memutuskanId = state.memutuskanId,
                    onSetuju = onSetuju,
                    onTolak = onTolak,
                )
            }
        }
    }
}

/** Konfirmasi setuju. TANPA pilihan nilai: sejak 2026-08-15 nilainya biner dan
 *  ditentukan SERVER (setuju 100 / tolak 0). Chip 70/85/100 yang dulu ada di
 *  sini sudah tak berpengaruh apa pun — server mengabaikan `score` kiriman
 *  klien — jadi menampilkannya cuma membuat PIC mengira nilainya bisa diatur. */
@Composable
private fun DialogSetuju(onBatal: () -> Unit, onSetuju: () -> Unit) {
    AlertDialog(
        onDismissRequest = onBatal,
        title = { Text("Setujui laporan", fontWeight = FontWeight.Bold) },
        text = { Text("Laporan ini akan ditandai disetujui.", style = MaterialTheme.typography.bodySmall) },
        confirmButton = { TextButton(onClick = onSetuju) { Text("Setujui") } },
        dismissButton = { TextButton(onClick = onBatal) { Text("Batal") } },
    )
}

@Composable
private fun FilterBar(
    tanggal: String,
    status: String,
    onGeserHari: (Int) -> Unit,
    onStatus: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onGeserHari(-1) }) {
                Icon(Icons.Rounded.ChevronLeft, contentDescription = "Hari sebelumnya")
            }
            Text(
                tanggal,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { onGeserHari(1) }) {
                Icon(Icons.Rounded.ChevronRight, contentDescription = "Hari berikutnya")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("pending" to "Menunggu", "approved" to "Disetujui", "rejected" to "Ditolak", "all" to "Semua")
                .forEach { (kunci, label) ->
                    FilterChip(
                        selected = status == kunci,
                        onClick = { onStatus(kunci) },
                        label = { Text(label) },
                    )
                }
        }
    }
}

@Composable
private fun KartuKaryawan(
    grup: GrupAktivitas,
    token: String?,
    memutuskanId: String?,
    onSetuju: (String) -> Unit,
    onTolak: (String) -> Unit,
) {
    ClayCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Text(
                grup.nama,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOf(grup.divisi, grup.cabang).filter { it.isNotBlank() }
                    .joinToString(" · ")
                    .ifBlank { "${grup.baris.size} aktivitas" },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            grup.baris.forEach { baris ->
                Spacer(Modifier.height(12.dp))
                BarisAktivitas(
                    item = baris,
                    token = token,
                    sibuk = memutuskanId == baris.id,
                    onSetuju = { onSetuju(baris.id) },
                    onTolak = { onTolak(baris.id) },
                )
            }
        }
    }
}

@Composable
private fun BarisAktivitas(
    item: AktivitasItemDto,
    token: String?,
    sibuk: Boolean,
    onSetuju: () -> Unit,
    onTolak: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                item.jobdeskText.ifBlank { "Aktivitas #${item.jobdeskIndex + 1}" },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                shape = RoundedCornerShape(50),
            ) {
                Text(
                    labelStatusReview(item.reviewStatus) +
                        (item.score?.let { " · $it" } ?: ""),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                )
            }
        }

        // Penanda bukti daur-ulang, DITULIS PER DUPLIKAT.
        //
        // Diringkas jadi satu kalimat, ia menggabungkan asal yang berbeda: dua
        // bukti — satu milik orang lain, satu milik sendiri yang sudah
        // disetujui — akan terbaca "karyawan lain yang sudah disetujui", tuduhan
        // yang tak dimiliki data mana pun. Bentuk & kalimatnya sengaja sama
        // dengan `BuktiDuplikatBadge` web: penilai yang membaca dua kalimat
        // berbeda untuk baris yang sama berhenti mempercayai keduanya.
        if (item.buktiDuplikat.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            item.buktiDuplikat.forEach { d ->
                // Gambar ASLI ikut dipajang kecil. Tuduhan tanpa penunjuk
                // memaksa penilai memilih antara mempercayainya buta atau
                // mengabaikannya — dua-duanya membuat penandanya tak berguna.
                // Bukti disajikan terautentikasi, jadi harus lewat AuthedEvidence.
                // `asliUrl` KOSONG untuk penilai berbatas cabang — server
                // mencabutnya beserta identitas pemiliknya. `evidenceImageUrl`
                // menjawab null, jadi tak ada gambar yang dicoba dimuat: tanpa
                // ini yang tampil kotak abu "Bukti gagal dimuat" atas berkas
                // yang memang tak boleh mereka lihat.
                evidenceImageUrl(d.asliUrl)?.let { urlAsli ->
                    Box(Modifier.fillMaxWidth(0.35f)) {
                        AuthedEvidence(
                            url = urlAsli,
                            token = token,
                            contentDescription = "Bukti unggahan terdahulu yang isinya sama",
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                }
                Text(
                    kalimatDuplikatBukti(
                        asliKaryawanId = d.asliKaryawanId,
                        asliKaryawanNama = d.asliKaryawanNama,
                        asliDiunggahAt = d.asliDiunggahAt,
                        asliDisetujui = d.asliDisetujui,
                        pemilikBarisId = item.employeeId,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        // Penanda "karyawan ini punya off_requests approved pada tanggal
        // baris ini" — PENANDA, bukan pemblokir. Bentuk & kalimat sengaja sama
        // dengan `KaryawanOffBadge` web: baris lama (sebelum gerbang submit
        // `ensure_bukan_off` ada) dan baris auto-isi "Kirim Prospek" (lewat
        // worker, bukan submit manual) tetap bisa muncul di sini tanpa
        // penanda ini, dan penilai yang tak melihatnya menilai seperti hari
        // kerja biasa — insiden nyata 2026-08-19.
        if (item.karyawanSedangOff) {
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.EventBusy,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    "Karyawan sedang ${item.offKategori ?: "off"} pada tanggal ini",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }

        item.employeeNote?.takeIf { it.isNotBlank() }?.let { catatan ->
            Spacer(Modifier.height(4.dp))
            Text(
                catatan,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        val gambar = if (buktiVideo(item)) emptyList() else parseEvidenceUrls(item.evidenceUrl)
        if (buktiVideo(item)) {
            Spacer(Modifier.height(6.dp))
            Text(
                "Bukti berupa video — periksa lewat web.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        gambar.forEach { mentah ->
            evidenceImageUrl(mentah)?.let { url ->
                Spacer(Modifier.height(8.dp))
                AuthedEvidence(url = url, token = token)
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            ExpressiveFilledButton(onClick = onSetuju, enabled = !sibuk, modifier = Modifier.weight(1f)) {
                Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Setuju")
            }
            ExpressiveOutlinedButton(onClick = onTolak, enabled = !sibuk, modifier = Modifier.weight(1f)) {
                Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Tolak")
            }
        }
    }
}

/**
 * Bukti raport di-serve terautentikasi — pola `AuthedImage` (Deadstock/Indent).
 *
 * `internal`, bukan `private`: layar Riwayat Aktivitas
 * ([AktivitasRiwayatScreen]) memuat bukti yang SAMA lewat endpoint yang sama.
 * Menyalinnya ke sana berarti dua tempat yang harus sepakat soal header
 * `Authorization` — dan yang lupa headernya tidak error, cuma menampilkan
 * "Bukti gagal dimuat" selamanya.
 */
@Composable
internal fun AuthedEvidence(
    url: String,
    token: String?,
    contentDescription: String = "Bukti aktivitas",
) {
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
            contentDescription = contentDescription,
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
                    Text("Bukti gagal dimuat", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
