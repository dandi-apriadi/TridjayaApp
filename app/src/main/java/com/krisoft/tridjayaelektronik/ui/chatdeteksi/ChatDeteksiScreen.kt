package com.krisoft.tridjayaelektronik.ui.chatdeteksi

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.krisoft.tridjayaelektronik.ui.theme.ClayCard
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveErrorState
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveFilledButton
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveOutlinedButton
import com.krisoft.tridjayaelektronik.ui.theme.ScrollableCenter
import com.krisoft.tridjayaelektronik.ui.theme.TridjayaCollapsibleHeader
import com.krisoft.tridjayaelektronik.ui.theme.TridjayaPullRefresh
import com.krisoft.tridjayaelektronik.util.bacaInfoBerkas
import kotlinx.coroutines.delay

private val HIJAU = Color(0xFF12B76A)
private val KUNING = Color(0xFFB5670C)
private val MERAH = Color(0xFFF04438)

/**
 * Deteksi otomatis jumlah chat via LLM — karyawan mengirim video rekaman
 * layar daftar chat WhatsApp, server yang menghitung jumlahnya dan
 * memutuskan lolos/tidak. TIDAK ADA kolom isi jumlah chat manual (beda dari
 * fitur "Bukti Chat Harian" lama) dan TIDAK ADA antrian review kepala cabang
 * — keputusannya otomatis.
 *
 * Video **dipilih dari galeri, bukan direkam di dalam app**: buktinya
 * rekaman layar WhatsApp, dan Android tak mengizinkan satu app merekam
 * layar app lain (alasan sama seperti layar "Bukti Chat Harian" lama).
 */
@Composable
fun ChatDeteksiScreen(
    onBack: () -> Unit,
    viewModel: ChatDeteksiViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val pilihVideo = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val (nama, ukuran) = bacaInfoBerkas(context.contentResolver, uri)
            viewModel.pilihVideo(uri, ukuran, nama)
        }
    }

    LaunchedEffect(state.pesanSukses) {
        if (state.pesanSukses != null) {
            delay(4000)
            viewModel.bersihkanPesan()
        }
    }

    val scrollState = rememberScrollState()
    LaunchedEffect(state.pesanError) {
        if (state.pesanError != null) scrollState.animateScrollTo(scrollState.maxValue)
    }

    TridjayaCollapsibleHeader(title = "Deteksi Chat", onBack = onBack) { contentModifier ->
        val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        TridjayaPullRefresh(
            isRefreshing = state.loading,
            onRefresh = { viewModel.muat() },
            modifier = contentModifier,
        ) {
            when {
                state.loading && state.status == null && state.pesanError == null ->
                    ScrollableCenter { CircularProgressIndicator() }

                state.pesanError != null && state.status == null && !state.loading ->
                    ScrollableCenter {
                        ExpressiveErrorState(
                            message = state.pesanError ?: "Gagal memuat status.",
                            onRetry = { viewModel.muat() },
                        )
                    }

                else -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp + navBottom),
                ) {
                    KartuPanduan()
                    Spacer(Modifier.height(12.dp))

                    val status = state.status
                    if (status != null) {
                        KartuStatus(status)
                        Spacer(Modifier.height(12.dp))
                    }

                    if (!state.sudahFinal) {
                        KartuKirim(
                            state = state,
                            adaSubmisiSebelumnya = status != null,
                            onPilihVideo = { pilihVideo.launch("video/*") },
                            onKirim = { viewModel.kirim(context.contentResolver) },
                        )
                    }

                    if (state.pesanSukses != null) {
                        Spacer(Modifier.height(12.dp))
                        Banner(HIJAU, Icons.Rounded.CheckCircle, "Berhasil", state.pesanSukses.orEmpty())
                    }
                    if (state.pesanError != null) {
                        Spacer(Modifier.height(12.dp))
                        Banner(MERAH, Icons.Rounded.Warning, "Gagal", state.pesanError.orEmpty())
                    }
                }
            }
        }
    }
}

@Composable
private fun KartuPanduan() {
    ClayCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Cara mengirim", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Rekam layar HP saat menggulir daftar chat WhatsApp dari atas sampai bawah, " +
                    "lalu unggah videonya di sini. Sistem akan menghitung jumlah chat dari video itu " +
                    "secara otomatis — tidak perlu mengetik jumlahnya sendiri.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun KartuStatus(status: com.krisoft.tridjayaelektronik.data.model.StatusChatDeteksiDto) {
    val gagal = gagalPerluKirimUlang(status.status, status.llmError)
    val warna = when {
        status.status == "lolos_otomatis" -> HIJAU
        status.status == "tidak_lolos_otomatis" -> MERAH
        gagal -> MERAH
        else -> KUNING
    }
    val ikon = when {
        status.status == "lolos_otomatis" -> Icons.Rounded.CheckCircle
        status.status == "tidak_lolos_otomatis" -> Icons.Rounded.Warning
        gagal -> Icons.Rounded.Warning
        else -> Icons.Rounded.HourglassTop
    }
    ClayCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(ikon, contentDescription = null, tint = warna)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (gagal) "Gagal diproses" else statusLabel(status.status),
                    fontWeight = FontWeight.Bold,
                    color = warna,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text("Target minimal hari ini: ${status.targetMinimal} chat", style = MaterialTheme.typography.bodyMedium)
            status.llmJumlahTerhitung?.let {
                Text("Terdeteksi: $it chat", style = MaterialTheme.typography.bodyMedium)
            }
            if (gagal) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Video sebelumnya gagal diproses server. Kirim ulang video di bawah.",
                    style = MaterialTheme.typography.bodySmall,
                    color = warna,
                )
            }
        }
    }
}

@Composable
private fun KartuKirim(
    state: ChatDeteksiUiState,
    adaSubmisiSebelumnya: Boolean,
    onPilihVideo: () -> Unit,
    onKirim: () -> Unit,
) {
    ClayCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                if (adaSubmisiSebelumnya) "Kirim ulang video" else "Kirim video",
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(10.dp))
            ExpressiveOutlinedButton(onClick = onPilihVideo, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Movie, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (state.videoUri == null) "Pilih video dari galeri" else "Ganti video")
            }
            if (state.videoUri != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "${state.videoNama} (${formatUkuranBerkas(state.videoUkuranBytes)})",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Spacer(Modifier.height(12.dp))
            ExpressiveFilledButton(
                onClick = onKirim,
                enabled = state.gate.ok && !state.mengirim,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.mengirim) {
                    CircularProgressIndicator(modifier = Modifier.height(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Icon(Icons.Rounded.Send, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Kirim")
                }
            }
        }
    }
}

@Composable
private fun Banner(warna: Color, ikon: androidx.compose.ui.graphics.vector.ImageVector, judul: String, isi: String) {
    ClayCard(modifier = Modifier.fillMaxWidth(), containerColor = warna.copy(alpha = 0.12f)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(ikon, contentDescription = null, tint = warna)
                Spacer(Modifier.width(8.dp))
                Text(judul, fontWeight = FontWeight.Bold, color = warna)
            }
            Spacer(Modifier.height(4.dp))
            Text(isi, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
