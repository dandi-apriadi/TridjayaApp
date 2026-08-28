package com.krisoft.tridjayaelektronik.ui.attendance

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.core.content.ContextCompat
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddAPhoto
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LocationOff
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Login
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.krisoft.tridjayaelektronik.data.model.AbsensiRecordDto
import com.krisoft.tridjayaelektronik.data.model.OffRequestDto
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveTextField
import com.krisoft.tridjayaelektronik.util.kunciUnik
import java.util.Date
import java.util.TimeZone
import com.krisoft.tridjayaelektronik.ui.theme.ClayCard
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveErrorState
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveFilledButton
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveOutlinedButton
import com.krisoft.tridjayaelektronik.ui.theme.ScrollableCenter
import com.krisoft.tridjayaelektronik.ui.theme.TridjayaCollapsibleHeader
import com.krisoft.tridjayaelektronik.ui.theme.TridjayaPullRefresh
import com.krisoft.tridjayaelektronik.util.PESAN_KAMERA_TAK_TERSIMPAN
import kotlinx.coroutines.delay
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Absensi karyawan — check-in/out dengan selfie + GPS, langsung ke backend
 * (`kinerja-service` via [AttendanceViewModel]/[com.krisoft.tridjayaelektronik.data.AbsensiRepository]).
 * Selfie full-res pakai [ActivityResultContracts.TakePicture] + FileProvider (dikompres lalu
 * di-upload); verdict geofence/telat diputuskan server dan tampil di hasil.
 */
@Composable
fun AttendanceScreen(
    onBack: () -> Unit,
    viewModel: AttendanceViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showOffForm by remember { mutableStateOf(false) }

    val selfieFile = remember {
        File(context.cacheDir, "absensi/selfie.jpg").apply { parentFile?.mkdirs() }
    }
    val selfieUri = remember {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", selfieFile)
    }
    // Cabang `else` WAJIB ada: absensi yang gagal senyap adalah kelas kegagalan
    // paling mahal di app ini — orangnya sudah berdiri di lokasi, mengira sudah
    // absen, dan baru tahu tidak tercatat saat penggajian. Lihat alasan lengkap
    // di [PESAN_KAMERA_TAK_TERSIMPAN].
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) viewModel.onSelfieCaptured(selfieFile)
        else Toast.makeText(context, PESAN_KAMERA_TAK_TERSIMPAN, Toast.LENGTH_LONG).show()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.any { it }) viewModel.refreshLocation() else viewModel.onLocationPermissionDenied()
    }

    val requestLocation: () -> Unit = {
        if (LocationProvider.hasPermission(context)) viewModel.refreshLocation()
        else permissionLauncher.launch(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        )
    }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* diabaikan: kalau ditolak, push cuma tak tampil */ }

    LaunchedEffect(Unit) {
        requestLocation()
        // Android 13+: minta izin notifikasi agar push approval bisa tampil.
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Kegagalan SEBAGIAN (riwayat gagal dimuat walau kartu "hari ini" sukses)
    // sebelum ini tak pernah terlihat — layar diam-diam terus menampilkan
    // riwayat lama tanpa tanda apa pun, dan muat ulang berkali-kali percuma
    // kalau sebabnya (mis. jaringan lemot) masih sama. Toast, bukan banner
    // penuh: `!adaData` di atas sudah menutup jalur kosong-total.
    LaunchedEffect(state.historyLoadError) {
        state.historyLoadError?.let {
            Toast.makeText(context, "Gagal memuat riwayat kehadiran: $it", Toast.LENGTH_LONG).show()
            viewModel.bersihkanHistoryLoadError()
        }
    }

    TridjayaCollapsibleHeader(title = "Absen", onBack = onBack) { contentModifier ->
        val adaData = state.today != null || state.history.isNotEmpty()
        TridjayaPullRefresh(
            isRefreshing = state.loading && adaData,
            // Riwayat di layar ini gabungan DUA sumber (buildTimeline: absensi + izin);
            // load() saja membuat izin yang baru disetujui tak pernah ikut tersegarkan.
            onRefresh = { viewModel.load(); viewModel.loadOff() },
            modifier = contentModifier
        ) {
            when {
                state.loading && !adaData -> {
                    ScrollableCenter { CircularProgressIndicator() }
                }
                state.loadError != null && !adaData -> {
                    ScrollableCenter {
                        ExpressiveErrorState(
                            message = state.loadError ?: "Gagal memuat absensi.",
                            onRetry = { viewModel.load() }
                        )
                    }
                }
                else -> {
                    // Sisakan ruang untuk navigation bar sistem agar item riwayat terakhir tidak
                    // terpotong di tepi bawah (edge-to-edge).
                    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                    // Riwayat gabungan absensi + hari izin/OFF disetujui (dihitung di luar
                    // LazyListScope karena remember bukan @Composable yg boleh dipanggil di sana).
                    val timeline = remember(state.history, state.offRequests) {
                        buildTimeline(state.history, state.offRequests)
                    }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp + navBottom),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item(key = "clock") { ClockCard(viewModel.userName, viewModel.cabang, state) }
                        item(key = "absen") {
                            AbsenCard(
                                state = state,
                                onRefreshLocation = requestLocation,
                                onTakeSelfie = { cameraLauncher.launch(selfieUri) },
                                onCheckIn = viewModel::checkIn,
                                onCheckOut = viewModel::checkOut,
                            )
                        }
                        item(key = "aturan") { WorkRuleInfo() }
                        item(key = "rekap") { RekapStrip(state.rekap) }
                        item(key = "izin") { OffSection(state.offRequests, onAjukan = { showOffForm = true }) }
                        if (timeline.isNotEmpty()) {
                            item(key = "riwayat_header") {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                                    Icon(Icons.Rounded.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Riwayat Kehadiran", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                }
                            }
                            val kunciTimeline = kunciUnik(timeline) {
                                when (it) {
                                    is TimelineEntry.Attendance -> "att_" + it.record.id.ifBlank { it.record.tanggal }
                                    is TimelineEntry.Off -> "off_" + it.off.id.ifBlank { it.off.tanggal }
                                }
                            }
                            itemsIndexed(
                                timeline,
                                key = { i, _ -> kunciTimeline.getOrElse(i) { "idx_$i" } }
                            ) { _, entry ->
                                when (entry) {
                                    is TimelineEntry.Attendance -> HistoryRow(entry.record)
                                    is TimelineEntry.Off -> OffHistoryRow(entry.off)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showOffForm) {
        OffFormSheet(
            submitting = state.offSubmitting,
            error = state.offError,
            onDismiss = { showOffForm = false; viewModel.clearOffError() },
            onSubmit = { tanggal, alasan -> viewModel.createOff(tanggal, alasan) { showOffForm = false } }
        )
    }
}

@Composable
private fun ClockCard(userName: String, cabang: String, state: AttendanceUiState) {
    var clock by remember { mutableStateOf(currentClock()) }
    LaunchedEffect(Unit) { while (true) { clock = currentClock(); delay(1000) } }

    val primary = MaterialTheme.colorScheme.primary
    val gradient = Brush.linearGradient(listOf(primary, lerp(primary, Color.Black, 0.30f)))
    val white = Color.White
    // Izin/OFF disetujui utk hari ini → tampilkan kategorinya, bukan "Belum Absen"
    // (selaras dgn status harian web). Absen tetap menang bila sudah check-in.
    val todayOff = state.offRequests.firstOrNull {
        it.status.equals("approved", ignoreCase = true) && it.tanggal == todayIso()
    }
    val (statusLabel, statusIcon) = when {
        state.hasCheckedOut -> "Selesai" to Icons.Rounded.CheckCircle
        state.hasCheckedIn -> "Sudah Masuk" to Icons.Rounded.Login
        todayOff != null -> OffKategori.from(todayOff.kategori).label to Icons.Rounded.EventBusy
        else -> "Belum Absen" to Icons.Rounded.Schedule
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(gradient, RoundedCornerShape(24.dp))
            .padding(20.dp)
    ) {
        // Ornamen lingkaran transparan di pojok kanan atas.
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(120.dp)
                .background(white.copy(alpha = 0.06f), CircleShape)
        )
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(44.dp).background(white.copy(alpha = 0.18f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        userName.trim().take(1).uppercase(),
                        color = white,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Halo, $userName",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = white,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.LocationOn, contentDescription = null, tint = white.copy(alpha = 0.85f), modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            cabang,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = white.copy(alpha = 0.9f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Surface(color = white.copy(alpha = 0.20f), shape = RoundedCornerShape(50)) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(statusIcon, contentDescription = null, tint = white, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(statusLabel, color = white, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                clock,
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = white,
                maxLines = 1
            )
            Text(
                formatAttendanceDate(todayIso()),
                style = MaterialTheme.typography.bodyMedium,
                color = white.copy(alpha = 0.85f)
            )
        }
    }
}

@Composable
private fun AbsenCard(
    state: AttendanceUiState,
    onRefreshLocation: () -> Unit,
    onTakeSelfie: () -> Unit,
    onCheckIn: () -> Unit,
    onCheckOut: () -> Unit,
) {
    ClayCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            when {
                state.hasCheckedOut -> DoneSection(state)
                // Gerbang aktivitas HANYA berlaku untuk absen pulang. Absen
                // MASUK dapat `GatePulang(true)` mati-matian: aktivitas justru
                // baru bisa diisi setelah orangnya masuk kerja, jadi menguncinya
                // di sini akan menahan orang sebelum ia sempat bekerja.
                state.hasCheckedIn -> PunchSection(
                    state, isCheckIn = false, onRefreshLocation, onTakeSelfie, onCheckOut,
                    gate = state.gatePulang,
                )
                // Yang mengunci absen masuk adalah GEOFENCE, dan itu dinilai
                // `state.gateMasuk` di dalam `PunchSection`.
                else -> PunchSection(
                    state, isCheckIn = true, onRefreshLocation, onTakeSelfie, onCheckIn,
                    gate = GatePulang(true),
                )
            }
        }
    }
}

/** Bagian punch (dipakai untuk check-in maupun check-out) — lokasi + selfie + tombol. */
@Composable
private fun ColumnScope.PunchSection(
    state: AttendanceUiState,
    isCheckIn: Boolean,
    onRefreshLocation: () -> Unit,
    onTakeSelfie: () -> Unit,
    onSubmit: () -> Unit,
    gate: GatePulang,
) {
    val title = if (isCheckIn) "Absen Masuk" else "Absen Pulang"
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Text(
        "Ambil selfie & pastikan lokasi aktif, lalu tekan ${if (isCheckIn) "Check In" else "Check Out"}.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    // Ringkasan check-in (hanya saat mau check-out).
    if (!isCheckIn) {
        state.today?.let { rec ->
            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = Color(0xFF12B76A), modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Masuk ${formatPunchTime(rec.checkInAt)}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.weight(1f))
                FlagChips(rec, checkIn = true)
            }
            GeofenceLine(rec.checkInInGeofence, rec.checkInDistanceM)
        }
    }

    Spacer(modifier = Modifier.height(14.dp))
    LocationStatus(state, isCheckIn, onRefreshLocation)
    Spacer(modifier = Modifier.height(14.dp))
    SelfieBox(state, onTakeSelfie)
    Spacer(modifier = Modifier.height(16.dp))

    // `siapKirim` = syarat yang bisa dipenuhi DI LAYAR INI (selfie + lokasi);
    // dipisah dari `gate` supaya petunjuk di bawah tombol tak salah menuduh
    // "menunggu lokasi…" padahal yang mengunci adalah kelengkapan aktivitas.
    val siapKirim = state.hasLocation && state.selfie != null && !state.submitting
    // Gate geofence HANYA untuk absen masuk: `check_out` di server sengaja tak
    // dipagari area, jadi mengunci tombol pulang di sini akan menahan orang yang
    // server-nya sendiri akan menerima.
    val gateMasuk = if (isCheckIn) state.gateMasuk else GateMasuk(true)
    val canSubmit = siapKirim && gate.boleh && gateMasuk.boleh
    ExpressiveFilledButton(onClick = onSubmit, enabled = canSubmit, modifier = Modifier.fillMaxWidth()) {
        if (state.submitting) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
        } else {
            Icon(if (isCheckIn) Icons.Rounded.Login else Icons.Rounded.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(if (isCheckIn) "Check In Sekarang" else "Check Out (Pulang)")
    }
    val hint = when {
        state.actionError != null -> state.actionError
        state.submitting -> null
        !siapKirim && state.selfie == null && !state.hasLocation -> "Perlu selfie & lokasi dulu"
        !siapKirim && state.selfie == null -> "Ambil selfie dulu"
        !siapKirim -> "Menunggu lokasi…"
        else -> null
    }
    if (hint != null) {
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            hint,
            style = MaterialTheme.typography.labelSmall,
            color = if (state.actionError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
    // Alasan geofence dirender saat ada ALASAN, bukan saat tertutup — tanpa
    // daftar cabang lengkap kita tak mengunci tapi tetap wajib memberi tahu
    // (lihat [gateAbsenMasuk]).
    gateMasuk.alasan?.let { alasan ->
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            alasan,
            style = MaterialTheme.typography.bodySmall,
            // Merah hanya saat benar-benar menahan; peringatan yang tak memblokir
            // tapi berwarna error mengajari orang mengabaikan merah.
            color = if (gateMasuk.boleh) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.error
            },
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
    // Tagihan kelengkapan aktivitas. Dirender saat ada ALASAN, BUKAN saat
    // tertutup: selama peluncuran bertahap saklarnya mati, jadi kekurangannya
    // ditagih tanpa mengunci — dan menggantungkan kartu ini pada `!gate.boleh`
    // akan membuat tagihannya ikut hilang.
    gate.alasan?.let { alasan ->
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            // Kalimatnya milik SERVER — jangan menyusun teks sendiri di sini,
            // supaya layar dan pesan error 400 saat menekan tombol tak pernah
            // berselisih isi.
            alasan,
            style = MaterialTheme.typography.bodySmall,
            color = if (gate.boleh) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.error
            },
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}

@Composable
private fun DoneSection(state: AttendanceUiState) {
    val rec = state.today ?: return
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(44.dp).background(Color(0xFF12B76A).copy(alpha = 0.14f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = Color(0xFF12B76A), modifier = Modifier.size(26.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("Absen Hari Ini Selesai", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Terima kasih, hati-hati di jalan.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        AbsensiStatusBadge(rec.status)
    }
    Spacer(modifier = Modifier.height(14.dp))
    Row(modifier = Modifier.fillMaxWidth()) {
        TimePill("Masuk", formatPunchTime(rec.checkInAt), if (rec.checkInLate) "Telat" else null, Color(0xFF1565C0), modifier = Modifier.weight(1f))
        Spacer(modifier = Modifier.width(10.dp))
        TimePill("Pulang", formatPunchTime(rec.checkOutAt), if (rec.checkOutEarly) "Cepat" else null, Color(0xFF6941C6), modifier = Modifier.weight(1f))
    }
    GeofenceLine(rec.checkInInGeofence, rec.checkInDistanceM)
}

@Composable
private fun TimePill(label: String, time: String, flag: String?, color: Color, modifier: Modifier = Modifier) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = RoundedCornerShape(14.dp), modifier = modifier) {
        Column(modifier = Modifier.padding(vertical = 12.dp, horizontal = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (flag != null) {
                    Spacer(modifier = Modifier.width(6.dp))
                    MiniChip(flag, Color(0xFFB5670C))
                }
            }
            Text(time, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

/**
 * Status lokasi GPS (deteksi / ditolak / error / terdeteksi). Verdict akhir tetap
 * server, tapi kalimat di sini WAJIB sesuai dengan apa yang akan dilakukan server.
 *
 * **Aturannya sudah berbalik dua kali — jangan menyalin kalimat lama.** Sampai
 * 2026-08-15 kedua keadaan (masuk & pulang) memakai kalimat "absen perlu review",
 * yang untuk absen MASUK adalah janji tak pernah ditepati: server menolak 400.
 * Lalu keputusan user 2026-08-26 membuat server MENERIMA absen masuk di luar
 * radius sebagai `pending_review`, jadi kalimat "belum bisa dari sini" berbalik
 * jadi salah. Yang berlaku sekarang: **keduanya tercatat**, bedanya absen masuk
 * dapat status Perlu Review (belum dihitung hadir sampai disetujui), absen pulang
 * memang tak pernah dipagari area. [isCheckIn] tetap ada karena akibatnya
 * berbeda, bukan karena yang satu ditolak.
 */
@Composable
private fun LocationStatus(state: AttendanceUiState, isCheckIn: Boolean, onRefresh: () -> Unit) {
    val (bg, fg, icon, title, subtitle) = when {
        state.locationDenied -> Quintet(Color(0xFFF04438).copy(alpha = 0.12f), Color(0xFFF04438), Icons.Rounded.LocationOff, "Izin lokasi ditolak", "Aktifkan izin lokasi untuk absen.")
        state.locating -> Quintet(MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.onSurfaceVariant, Icons.Rounded.MyLocation, "Mendeteksi lokasi…", "Mohon tunggu sebentar.")
        state.locationError != null -> Quintet(Color(0xFFB5670C).copy(alpha = 0.12f), Color(0xFFB5670C), Icons.Rounded.LocationOff, "Gagal ambil lokasi", state.locationError)
        state.hasLocation && state.inArea == true -> Quintet(
            Color(0xFF12B76A).copy(alpha = 0.12f), Color(0xFF12B76A), Icons.Rounded.LocationOn,
            state.geofence?.cabangNama?.takeIf { it.isNotBlank() }?.let { "Dalam area $it" } ?: "Dalam area toko",
            "Jarak ${formatDistance((state.distanceM ?: 0).toLong())} dari titik toko · " +
                if (isCheckIn) "siap check-in." else "siap check-out."
        )
        // Oranye (peringatan), BUKAN merah: sejak 2026-08-26 keadaan ini tidak
        // lagi menghalangi absen sama sekali — merah untuk sesuatu yang tetap
        // berhasil mengajari orang mengabaikan warna merah.
        state.hasLocation && state.inArea == false -> Quintet(
            Color(0xFFB5670C).copy(alpha = 0.12f), Color(0xFFB5670C), Icons.Rounded.LocationOn,
            "Di luar area toko",
            run {
                val terdekat = state.geofence?.cabangNama?.takeIf { it.isNotBlank() }
                    ?.let { "Terdekat $it · " } ?: ""
                val jarak = formatDistance((state.distanceM ?: 0).toLong())
                // Absen MASUK: tercatat `pending_review` (keputusan user
                // 2026-08-26 — sebelumnya ditolak 400). Absen PULANG: tetap
                // tercatat, dan sejak 2026-07-31 tak ada antrian review untuknya
                // sehingga "perlu review" memang tak berlaku di sisi itu.
                val akibat = if (isCheckIn) {
                    "absen masuk tetap tercatat, tapi Perlu Review dulu."
                } else {
                    "absen pulang tetap tercatat, jaraknya ikut tercatat juga."
                }
                "$terdekat$jarak · $akibat"
            }
        )
        state.hasLocation -> Quintet(
            Color(0xFF12B76A).copy(alpha = 0.12f), Color(0xFF12B76A), Icons.Rounded.LocationOn,
            "Lokasi terdeteksi",
            // Dulu berbunyi "Cabang belum diatur geofence — titik GPS tetap
            // dikirim.", dua klaim yang sama-sama bisa salah: daftar cabang bisa
            // saja gagal dimuat (bukan berarti belum diatur), dan kalau memang
            // belum diatur, check-in justru DITOLAK (fail-closed 2026-07-31),
            // bukan "tetap dikirim".
            "Area toko belum bisa dicek dari sini. Coba perbarui lokasi."
        )
        else -> Quintet(MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.onSurfaceVariant, Icons.Rounded.MyLocation, "Lokasi belum terdeteksi", "Tekan perbarui untuk ambil lokasi.")
    }
    Surface(color = bg, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (state.locating) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = fg)
            else Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = fg)
                Text(subtitle ?: "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Surface(onClick = onRefresh, shape = CircleShape, color = fg.copy(alpha = 0.15f), modifier = Modifier.size(36.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Perbarui lokasi", tint = fg, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun SelfieBox(state: AttendanceUiState, onTakeSelfie: () -> Unit) {
    Text("Selfie Bukti", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(modifier = Modifier.height(6.dp))
    Surface(
        onClick = onTakeSelfie,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.fillMaxWidth().height(190.dp)
    ) {
        val selfie = state.selfie
        if (selfie != null) {
            Box(contentAlignment = Alignment.BottomEnd) {
                // alignment=BottomCenter: watermark ada di bar bawah gambar (PhotoWatermark.drawWatermark);
                // default Crop (Center) motong tepi atas+bawah foto portrait, bar watermark ikut hilang.
                Image(
                    bitmap = selfie.asImageBitmap(), contentDescription = "Selfie absen",
                    contentScale = ContentScale.Crop, alignment = Alignment.BottomCenter,
                    modifier = Modifier.fillMaxSize()
                )
                Surface(color = Color.Black.copy(alpha = 0.45f), shape = RoundedCornerShape(topStart = 12.dp)) {
                    Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.CameraAlt, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Ambil ulang", color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(Icons.Rounded.AddAPhoto, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(34.dp))
                Spacer(modifier = Modifier.height(8.dp))
                Text("Ketuk untuk ambil selfie", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun GeofenceLine(inGeofence: Boolean?, distanceM: Long?) {
    val (color, text) = when (inGeofence) {
        true -> Color(0xFF12B76A) to ("Dalam area toko" + (distanceM?.let { " · ${formatDistance(it)}" } ?: ""))
        // Baris ini SENGAJA tak menyebut "perlu review", walau sejak 2026-08-26
        // check-in di luar radius memang lahir `pending_review`: status itu sudah
        // dibawa `AbsensiStatusBadge(rec.status)` yang dirender berdampingan, dan
        // dari SATU baris data kita tak bisa tahu apakah `pending_review`-nya
        // datang dari geofence atau dari sengketa check-out warisan. Menebaknya di
        // sini melahirkan label kedua yang bisa berselisih dengan badge-nya.
        false -> Color(0xFFB5670C) to ("Di luar area · " + (distanceM?.let { formatDistance(it) } ?: "?") + " · jarak tercatat")
        null -> MaterialTheme.colorScheme.onSurfaceVariant to "Jarak ke titik toko tidak tercatat"
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
        Icon(Icons.Rounded.LocationOn, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun RekapStrip(rekap: AttendanceRekap) {
    ClayCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text("Rekap Kehadiran Bulan Ini", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(10.dp))
            // Enam kategori seperti web (Hadir/Izin/Sakit/Cuti/Off/Belum Absen), dua baris.
            val order = RekapStatus.entries
            Row(modifier = Modifier.fillMaxWidth()) {
                order.take(3).forEach { s ->
                    RekapCell(rekap.count(s).toString(), s.label, s.color, modifier = Modifier.weight(1f))
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                order.drop(3).forEach { s ->
                    RekapCell(rekap.count(s).toString(), s.label, s.color, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun RekapCell(value: String, label: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

@Composable
private fun HistoryRow(record: AbsensiRecordDto) {
    val status = AbsensiStatus.from(record.status)
    ClayCard(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(38.dp).background(status.color.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(status.icon, contentDescription = null, tint = status.color, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(formatAttendanceDateShort(record.tanggal), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                val detail = buildString {
                    append("Masuk ${formatPunchTime(record.checkInAt)}")
                    if (record.checkOutAt != null) append(" · Pulang ${formatPunchTime(record.checkOutAt)}")
                }
                Text(detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(horizontalAlignment = Alignment.End) {
                AbsensiStatusBadge(record.status)
                if (record.checkInLate) {
                    Spacer(modifier = Modifier.height(4.dp))
                    MiniChip("Telat", Color(0xFFB5670C))
                }
            }
        }
    }
}

/** Baris riwayat untuk hari izin/OFF disetujui (tanpa absensi) — setara kartu "Izin" web. */
@Composable
private fun OffHistoryRow(off: OffRequestDto) {
    val kategori = OffKategori.from(off.kategori)
    ClayCard(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(38.dp).background(kategori.color.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.EventBusy, contentDescription = null, tint = kategori.color, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(formatAttendanceDateShort(off.tanggal), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(off.alasan.ifBlank { kategori.label }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Surface(color = kategori.color.copy(alpha = 0.14f), shape = RoundedCornerShape(50)) {
                Text(kategori.label, color = kategori.color, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp))
            }
        }
    }
}

/** Info aturan jam kerja global — cerminan keterangan di web (batas masuk & shift 12 jam). */
@Composable
private fun WorkRuleInfo() {
    ClayCard(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Icon(Icons.Rounded.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text("Aturan Jam Kerja", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    "Batas masuk 10:00 (lewat = telat). Shift 12 jam sejak check-in (pulang < 12 jam = pulang cepat). Dihitung dari jam server, bukan jam HP.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun FlagChips(record: AbsensiRecordDto, checkIn: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        AbsensiStatusBadge(record.status)
        if (checkIn && record.checkInLate) MiniChip("Telat", Color(0xFFB5670C))
    }
}

@Composable
private fun AbsensiStatusBadge(statusKey: String) {
    val status = AbsensiStatus.from(statusKey)
    Surface(color = status.color.copy(alpha = 0.14f), shape = RoundedCornerShape(50)) {
        Text(status.label, color = status.color, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp))
    }
}

@Composable
private fun MiniChip(text: String, color: Color) {
    Surface(color = color.copy(alpha = 0.14f), shape = RoundedCornerShape(50)) {
        Text(text, color = color, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
    }
}

// ── Izin / OFF ───────────────────────────────────────────────────────────────

@Composable
private fun OffSection(requests: List<OffRequestDto>, onAjukan: () -> Unit) {
    ClayCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.EventBusy, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Izin / OFF", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Ajukan izin tidak masuk kerja; disetujui/ditolak atasan.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            ExpressiveFilledButton(onClick = onAjukan, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Ajukan Izin")
            }
            if (requests.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                requests.take(5).forEachIndexed { i, req ->
                    if (i > 0) Spacer(modifier = Modifier.height(8.dp))
                    OffRow(req)
                }
            }
        }
    }
}

@Composable
private fun OffRow(req: OffRequestDto) {
    val (label, color) = offStatusMeta(req.status)
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Izin ${formatAttendanceDateShort(req.tanggal)}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(req.alasan, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (!req.reviewerComment.isNullOrBlank() && req.status.lowercase() != "pending") {
                    Text("Catatan: ${req.reviewerComment}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Surface(color = color.copy(alpha = 0.14f), shape = RoundedCornerShape(50)) {
                Text(label, color = color, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp))
            }
        }
    }
}

private fun offStatusMeta(status: String): Pair<String, Color> = when (status.lowercase()) {
    "approved" -> "Disetujui" to Color(0xFF12B76A)
    "rejected" -> "Ditolak" to Color(0xFFF04438)
    "expired" -> "Kadaluarsa" to Color(0xFF667085)
    else -> "Menunggu" to Color(0xFFB5670C)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OffFormSheet(
    submitting: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSubmit: (tanggal: String, alasan: String) -> Unit
) {
    // Hari ini menurut jam DEVICE, dinyatakan sebagai tengah malam UTC — satuan
    // yang dipakai `DatePickerState` (Material3 menormalkan tiap pilihan ke
    // sana, dan dua formatter di bawah ikut ber-zona UTC karena itu).
    //
    // Mengoper `System.currentTimeMillis()` mentah SALAH: nilai itu dinormalkan
    // ke tanggal UTC-nya, sehingga antara 00:00–06:59 WIB form ini terbuka —
    // dan mengirim — tanggal KEMARIN. Dulu diam-diam membuat izin di hari yang
    // salah; sejak server menolak tanggal mundur (2026-07-31) ia jadi 400 tepat
    // pada kasus yang aturan itu justru ingin biarkan lewat: sakit mendadak
    // pagi hari.
    val hariIniUtcMillis = remember { hariIniSebagaiUtcMidnight() }
    val dateState = rememberDatePickerState(initialSelectedDateMillis = hariIniUtcMillis)
    var showPicker by remember { mutableStateOf(false) }
    var alasan by remember { mutableStateOf("") }
    val isoUtc = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") } }
    val displayFmt = remember { SimpleDateFormat("EEEE, d MMMM yyyy", Locale("in", "ID")).apply { timeZone = TimeZone.getTimeZone("UTC") } }
    val millis = dateState.selectedDateMillis ?: hariIniUtcMillis
    val tanggalIso = isoUtc.format(Date(millis))
    val canSubmit = alasan.trim().length >= 5 && !submitting

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
        ) {
            Text("Ajukan Izin / OFF", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Pilih tanggal & tulis alasan. Menunggu persetujuan atasan.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(16.dp))

            Text("Tanggal izin", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(6.dp))
            Surface(onClick = { showPicker = true }, shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.EventBusy, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(displayFmt.format(Date(millis)), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            ExpressiveTextField(value = alasan, onValueChange = { alasan = it }, label = "Alasan (min 5 huruf)", singleLine = false, modifier = Modifier.fillMaxWidth())
            if (alasan.isNotEmpty() && alasan.trim().length < 5) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Kurang ${5 - alasan.trim().length} huruf lagi (tombol Kirim aktif setelah alasan minimal 5 huruf)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            if (error != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(error, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }
            Spacer(modifier = Modifier.height(20.dp))
            ExpressiveFilledButton(onClick = { onSubmit(tanggalIso, alasan.trim()) }, enabled = canSubmit, modifier = Modifier.fillMaxWidth()) {
                if (submitting) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text("Kirim Pengajuan")
            }
        }
    }

    if (showPicker) {
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = { TextButton(onClick = { showPicker = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Batal") } }
        ) { DatePicker(state = dateState) }
    }
}

private data class Quintet(
    val bg: Color,
    val fg: Color,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val title: String,
    val subtitle: String?
)

private fun currentClock(): String =
    SimpleDateFormat("HH:mm:ss", Locale.US).format(Calendar.getInstance().time)

private fun todayIso(): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Calendar.getInstance().time)
