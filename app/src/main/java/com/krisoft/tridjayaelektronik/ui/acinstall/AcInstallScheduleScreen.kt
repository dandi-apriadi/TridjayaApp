package com.krisoft.tridjayaelektronik.ui.acinstall

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.krisoft.tridjayaelektronik.data.model.AcInstallStatus
import com.krisoft.tridjayaelektronik.data.model.AcInstallTaskDto
import com.krisoft.tridjayaelektronik.data.model.AcInstallTimMasterDto
import com.krisoft.tridjayaelektronik.ui.theme.ClayCard
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveEmptyState
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveErrorState
import com.krisoft.tridjayaelektronik.ui.theme.ScrollableCenter
import com.krisoft.tridjayaelektronik.ui.theme.TridjayaCollapsibleHeader
import com.krisoft.tridjayaelektronik.ui.theme.TridjayaPullRefresh

/**
 * "Penugasan AC" — sisi VERIFIKATOR pemasangan AC (`acinstall.schedule`).
 *
 * Pasangan dari [AcInstallScreen]: yang itu MENERIMA pekerjaan, yang ini
 * MEMBERIKANNYA. Di web pekerjaan yang sama terbelah dua menu ("Jadwal
 * Pemasangan AC" + "Tim Pemasangan AC"); di sini disatukan karena memilih tim
 * tanpa melihat jadwalnya berarti dua kali perjalanan untuk satu keputusan.
 *
 * **Master tim hanya DIBACA.** Membuat/mengubah tim tetap di web — lihat
 * alasannya di `AcInstallModels.kt`.
 */
@Composable
fun AcInstallScheduleScreen(
    onBack: () -> Unit,
    viewModel: AcInstallScheduleViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    var dialogJadwal by rememberSaveable { mutableStateOf<String?>(null) }
    var dialogBatal by rememberSaveable { mutableStateOf<String?>(null) }
    var dialogSelesai by rememberSaveable { mutableStateOf<String?>(null) }

    dialogJadwal?.let { id ->
        val task = state.items.firstOrNull { it.id == id }
        if (task == null) {
            dialogJadwal = null
        } else {
            DialogJadwal(
                task = task,
                tim = AcInstallSchedulePlan.timBisaDipilih(state.tim),
                timError = state.timError,
                submitting = state.submitting,
                onDismiss = { dialogJadwal = null },
                onSimpan = { tanggal, jam, timIds, catatan ->
                    viewModel.jadwalkan(id, tanggal, jam, timIds, catatan) { dialogJadwal = null }
                },
            )
        }
    }

    dialogBatal?.let { id ->
        DialogAlasan(
            judul = "Batalkan pengajuan",
            label = "Alasan pembatalan",
            wajib = true,
            submitting = state.submitting,
            onDismiss = { dialogBatal = null },
            onSimpan = { alasan -> viewModel.batalkan(id, alasan) { dialogBatal = null } },
        )
    }

    dialogSelesai?.let { id ->
        DialogAlasan(
            judul = "Tandai selesai",
            label = "Catatan penutup (opsional)",
            wajib = false,
            submitting = state.submitting,
            onDismiss = { dialogSelesai = null },
            onSimpan = { catatan -> viewModel.selesaikan(id, catatan) { dialogSelesai = null } },
        )
    }

    TridjayaCollapsibleHeader(title = "Penugasan AC", onBack = onBack) { contentModifier ->
        val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        Column(modifier = contentModifier) {
            TabStatus(
                terpilih = state.status,
                onPilih = { viewModel.pilihStatus(it) },
            )
            TridjayaPullRefresh(
                isRefreshing = state.loading && state.items.isNotEmpty(),
                onRefresh = { viewModel.muat() },
            ) {
                when {
                    state.loading && state.items.isEmpty() ->
                        ScrollableCenter { CircularProgressIndicator() }

                    state.error != null && state.items.isEmpty() -> ScrollableCenter {
                        ExpressiveErrorState(
                            message = state.error ?: "Gagal memuat daftar pengajuan.",
                            onRetry = { viewModel.muat() },
                        )
                    }

                    state.items.isEmpty() -> ScrollableCenter {
                        ExpressiveEmptyState(
                            icon = {
                                Icon(
                                    Icons.Rounded.AcUnit,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(30.dp),
                                )
                            },
                            title = "Tidak ada pengajuan ${
                                AcInstallSchedulePlan.labelStatus(state.status).lowercase()
                            }.",
                            subtitle = "Pengajuan dibuat sales dari halaman SPK setelah barang " +
                                "selesai dikirim.",
                        )
                    }

                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp + navBottom,
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
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
                        items(state.items, key = { it.id }) { task ->
                            KartuPengajuan(
                                task = task,
                                terbuka = state.terbuka == task.id,
                                submitting = state.submitting,
                                onToggle = {
                                    viewModel.buka(if (state.terbuka == task.id) null else task.id)
                                },
                                onJadwalkan = { dialogJadwal = task.id },
                                onSelesai = { dialogSelesai = task.id },
                                onBatal = { dialogBatal = task.id },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TabStatus(terpilih: String, onPilih: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AcInstallSchedulePlan.URUTAN_STATUS.forEach { status ->
            FilterChip(
                selected = terpilih == status,
                onClick = { onPilih(status) },
                label = { Text(AcInstallSchedulePlan.labelStatus(status)) },
            )
        }
    }
}

@Composable
private fun KartuPengajuan(
    task: AcInstallTaskDto,
    terbuka: Boolean,
    submitting: Boolean,
    onToggle: () -> Unit,
    onJadwalkan: () -> Unit,
    onSelesai: () -> Unit,
    onBatal: () -> Unit,
) {
    ClayCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxWidth()
                .clickable { onToggle() }
                .padding(14.dp),
        ) {
            Text(
                task.spk.customerName?.takeIf { it.isNotBlank() } ?: "(tanpa nama)",
                fontWeight = FontWeight.Bold,
            )
            Text(
                listOfNotNull(
                    task.spk.namaBarang?.takeIf { it.isNotBlank() },
                    task.spk.merk?.takeIf { it.isNotBlank() },
                ).joinToString(" "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                listOfNotNull(
                    task.spk.cabangNama?.takeIf { it.isNotBlank() },
                    task.spk.kodePengiriman.takeIf { it.isNotBlank() },
                ).joinToString(" - "),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(8.dp))
            when (task.status) {
                AcInstallStatus.DIJADWALKAN -> Text(
                    "Terjadwal ${task.jadwalTanggal.orEmpty()} ${task.jadwalJam.orEmpty()}".trim(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                AcInstallStatus.DIAJUKAN -> Text(
                    task.preferensiTanggal?.takeIf { it.isNotBlank() }
                        ?.let { "Usulan tanggal: $it" }
                        ?: "Belum dijadwalkan",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> Text(
                    AcInstallSchedulePlan.labelStatus(task.status),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (terbuka) {
                Spacer(Modifier.height(12.dp))
                task.alamatPemasangan?.takeIf { it.isNotBlank() }?.let {
                    Text("Alamat: $it", style = MaterialTheme.typography.bodySmall)
                }
                listOfNotNull(
                    task.kontakNama?.takeIf { it.isNotBlank() },
                    task.kontakHp?.takeIf { it.isNotBlank() },
                ).takeIf { it.isNotEmpty() }?.let {
                    Text("Kontak: ${it.joinToString(" - ")}", style = MaterialTheme.typography.bodySmall)
                }
                task.catatan?.takeIf { it.isNotBlank() }?.let {
                    Text("Catatan: $it", style = MaterialTheme.typography.bodySmall)
                }

                if (task.tim.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("Tim ditugaskan", style = MaterialTheme.typography.labelMedium)
                    task.tim.forEach { tim ->
                        Text(
                            "- ${tim.nama}: " + tim.anggota.joinToString(", ") { it.nama }
                                .ifBlank { "(belum ada anggota)" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // Jawaban petugas: `status == null` berarti BELUM menjawab, bukan
                // ditolak. Verifikator perlu membedakannya untuk tahu apakah tim
                // sudah tahu jadwalnya.
                if (task.petugas.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("Jawaban petugas", style = MaterialTheme.typography.labelMedium)
                    task.petugas.forEach { p ->
                        Text(
                            "- ${p.nama}: " + when (p.status) {
                                null -> "belum menjawab"
                                else -> p.status + (p.alasan?.let { " ($it)" } ?: "")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))
                Text(
                    "Bukti foto: ${task.foto.size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (AcInstallSchedulePlan.bolehJadwalkan(task.status)) {
                        TextButton(onClick = onJadwalkan, enabled = !submitting) {
                            Text(
                                if (task.status == AcInstallStatus.DIJADWALKAN) "Ubah jadwal"
                                else "Jadwalkan",
                            )
                        }
                    }
                    if (AcInstallSchedulePlan.bolehSelesai(task)) {
                        TextButton(onClick = onSelesai, enabled = !submitting) { Text("Tandai selesai") }
                    }
                    if (AcInstallSchedulePlan.bolehBatal(task.status)) {
                        TextButton(onClick = onBatal, enabled = !submitting) { Text("Batalkan") }
                    }
                }
                // Tombol yang TIDAK muncul dijelaskan sebabnya. Tombol yang hilang
                // tanpa keterangan terbaca sebagai app rusak, dan syarat "minimal
                // satu foto" khususnya mustahil ditebak dari layar ini.
                AcInstallSchedulePlan.alasanTakBisaSelesai(task)
                    ?.takeIf { !AcInstallSchedulePlan.tertutup(task.status) }
                    ?.let {
                        Text(
                            "Belum bisa ditutup: $it",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
            }
        }
    }
}

@Composable
private fun DialogJadwal(
    task: AcInstallTaskDto,
    tim: List<AcInstallTimMasterDto>,
    timError: String?,
    submitting: Boolean,
    onDismiss: () -> Unit,
    onSimpan: (String, String, Set<String>, String) -> Unit,
) {
    var tanggal by rememberSaveable(task.id) {
        mutableStateOf(task.jadwalTanggal ?: task.preferensiTanggal.orEmpty())
    }
    // Server mengirim `HH:MM:SS`; form hanya menerima `HH:MM`, jadi detiknya
    // dipangkas saat menyemai — kalau tidak, form terbuka dalam keadaan tak sah
    // dan tombol simpannya mati tanpa sebab yang terlihat.
    var jam by rememberSaveable(task.id) { mutableStateOf(task.jadwalJam?.take(5).orEmpty()) }
    var catatan by rememberSaveable(task.id) { mutableStateOf(task.catatanJadwal.orEmpty()) }
    // Disemai dari tim yang SUDAH ditugaskan. Wajib: `teamIds` mengganti seluruh
    // daftar, jadi form yang mulai kosong akan mencabut penugasan yang ada.
    val awal = remember(task.id) { AcInstallSchedulePlan.timTerpilihAwal(task) }
    var terpilih by rememberSaveable(task.id) { mutableStateOf(awal) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (task.jadwalTanggal == null) "Jadwalkan pemasangan" else "Ubah jadwal") },
        text = {
            Column {
                OutlinedTextField(
                    value = tanggal,
                    onValueChange = { tanggal = it },
                    label = { Text("Tanggal (YYYY-MM-DD)") },
                    isError = tanggal.isNotBlank() && !AcInstallSchedulePlan.tanggalSah(tanggal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = jam,
                    onValueChange = { jam = it },
                    label = { Text("Jam (HH:MM, opsional)") },
                    isError = !AcInstallSchedulePlan.jamSah(jam),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                Text("Tim yang berangkat", style = MaterialTheme.typography.labelMedium)
                when {
                    timError != null -> Text(
                        "Daftar tim gagal dimuat ($timError). Jadwal tetap bisa disimpan; " +
                            "tim bisa ditetapkan menyusul.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    tim.isEmpty() -> Text(
                        "Belum ada tim aktif. Buat tim lewat dashboard web.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    else -> tim.forEach { t ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = t.id in terpilih,
                                onCheckedChange = { pilih ->
                                    terpilih = if (pilih) terpilih + t.id else terpilih - t.id
                                },
                            )
                            Column {
                                Text(t.nama, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    // `kodeDealer` null = tim pusat/lintas cabang,
                                    // dan itu SAH — bukan data yang belum lengkap.
                                    t.cabangNama?.takeIf { it.isNotBlank() } ?: "Lintas cabang",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = catatan,
                    onValueChange = { catatan = it },
                    label = { Text("Catatan jadwal (opsional)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSimpan(tanggal, jam, terpilih, catatan) },
                enabled = !submitting && AcInstallSchedulePlan.bolehSimpanJadwal(tanggal, jam),
            ) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}

@Composable
private fun DialogAlasan(
    judul: String,
    label: String,
    wajib: Boolean,
    submitting: Boolean,
    onDismiss: () -> Unit,
    onSimpan: (String) -> Unit,
) {
    var teks by rememberSaveable(judul) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(judul) },
        text = {
            OutlinedTextField(
                value = teks,
                onValueChange = { teks = it },
                label = { Text(label) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onSimpan(teks) },
                enabled = !submitting && (!wajib || teks.isNotBlank()),
            ) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}
