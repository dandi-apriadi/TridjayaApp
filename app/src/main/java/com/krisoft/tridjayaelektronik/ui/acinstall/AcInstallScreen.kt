package com.krisoft.tridjayaelektronik.ui.acinstall

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.krisoft.tridjayaelektronik.data.model.AcInstallTaskDto
import com.krisoft.tridjayaelektronik.ui.theme.ClayCard
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveEmptyState
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveErrorState
import com.krisoft.tridjayaelektronik.ui.theme.ScrollableCenter
import com.krisoft.tridjayaelektronik.ui.theme.TridjayaCollapsibleHeader
import com.krisoft.tridjayaelektronik.ui.theme.TridjayaPullRefresh
import com.krisoft.tridjayaelektronik.util.PESAN_KAMERA_TAK_TERSIMPAN
import java.io.File

/**
 * "Tugas Pemasangan AC" — sisi PETUGAS dari modul pemasangan AC yang sebelumnya
 * web-saja.
 *
 * Satu layar, kartu yang bisa dibentangkan; tak ada layar detail terpisah
 * karena `tugas-saya` sudah mengirim seluruh isi tiap pengajuan (lihat
 * [AcInstallViewModel]).
 *
 * Tiga aksi, semuanya milik petugas: **Terima**, **Tolak** (wajib beralasan),
 * dan **bukti foto**. MENUTUP pekerjaan (`selesai`) sengaja TIDAK ada di sini —
 * lihat catatan di ujung berkas.
 */
@Composable
fun AcInstallScreen(
    onBack: () -> Unit,
    viewModel: AcInstallViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    var dialogTolak by rememberSaveable { mutableStateOf<String?>(null) }
    // Id tugas yang kameranya baru dibuka. WAJIB dicatat: callback kamera tak
    // membawa konteks apa pun, jadi tanpa ini foto bisa mendarat di tugas yang
    // salah saat petugas membuka kartu lain sementara kamera terbuka.
    var fotoUntuk by rememberSaveable { mutableStateOf<String?>(null) }

    // `cacheDir/ac-install/` WAJIB punya entri di res/xml/file_paths.xml —
    // tanpa itu `getUriForFile` melempar dan (karena panggilannya di dalam
    // `remember`) app tutup saat KOMPOSISI, bukan saat tombol ditekan.
    // Dijaga `FileProviderPathsTest`.
    val fileFoto = remember {
        File(context.cacheDir, "ac-install").apply { mkdirs() }.let { File(it, "bukti.jpg") }
    }
    val uriFoto = remember(fileFoto) {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", fileFoto)
    }
    val kamera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val id = fotoUntuk
        fotoUntuk = null
        // `ok == false` tak lagi ditelan: kegagalan simpan foto kamera dulu
        // menghasilkan nol pesan, jadi petugas menyangka buktinya terkirim.
        // Lihat [PESAN_KAMERA_TAK_TERSIMPAN].
        when {
            ok && id != null -> viewModel.unggahBukti(id, fileFoto, keterangan = null)
            !ok && id != null -> Toast.makeText(context, PESAN_KAMERA_TAK_TERSIMPAN, Toast.LENGTH_LONG).show()
        }
    }

    dialogTolak?.let { id ->
        DialogTolak(
            submitting = state.submitting,
            onDismiss = { dialogTolak = null },
            onTolak = { alasan ->
                viewModel.tolak(id, alasan)
                dialogTolak = null
            },
        )
    }

    TridjayaCollapsibleHeader(title = "Tugas Pemasangan AC", onBack = onBack) { contentModifier ->
        val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        TridjayaPullRefresh(
            isRefreshing = state.loading && state.items.isNotEmpty(),
            onRefresh = { viewModel.muat() },
            modifier = contentModifier,
        ) {
            when {
                state.loading && state.items.isEmpty() -> ScrollableCenter { CircularProgressIndicator() }

                state.error != null && state.items.isEmpty() -> ScrollableCenter {
                    ExpressiveErrorState(
                        message = state.error ?: "Gagal memuat tugas pemasangan.",
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
                        title = "Belum ada tugas pemasangan.",
                        subtitle = "Tugas muncul di sini setelah verifikator menjadwalkan " +
                            "pemasangan dan memasukkan kamu ke tim yang berangkat.",
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
                        val belum = butuhJawabanSaya(state.items, viewModel.currentUserId)
                        Text(
                            if (belum > 0) "$belum dari ${state.items.size} tugas belum kamu jawab"
                            else "${state.items.size} tugas terjadwal",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    // Kegagalan AKSI ditampilkan DI ATAS daftar, bukan menggantikannya:
                    // pesannya sering menyebut apa yang harus diulang, dan daftar yang
                    // lenyap membuat petugas kehilangan alamat yang sedang dibacanya.
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
                    items(state.items, key = { it.id }) { tugas ->
                        KartuTugas(
                            tugas = tugas,
                            userId = viewModel.currentUserId,
                            terbuka = state.terbuka == tugas.id,
                            submitting = state.submitting,
                            mengunggahFoto = state.mengunggahFoto,
                            adaBuktiTertunda = state.buktiTertunda.containsKey(tugas.id),
                            onToggle = { viewModel.buka(tugas.id) },
                            onTerima = { viewModel.terima(tugas.id) },
                            onTolak = { dialogTolak = tugas.id },
                            onFoto = {
                                fotoUntuk = tugas.id
                                kamera.launch(uriFoto)
                            },
                            onLampirkanUlang = { viewModel.lampirkanUlang(tugas.id, keterangan = null) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun KartuTugas(
    tugas: AcInstallTaskDto,
    userId: String,
    terbuka: Boolean,
    submitting: Boolean,
    mengunggahFoto: Boolean,
    adaBuktiTertunda: Boolean,
    onToggle: () -> Unit,
    onTerima: () -> Unit,
    onTolak: () -> Unit,
    onFoto: () -> Unit,
    onLampirkanUlang: () -> Unit,
) {
    val saya = jawabanSaya(tugas, userId)
    val label = labelRespon(saya?.status)
    ClayCard(modifier = Modifier.fillMaxWidth().clickable { onToggle() }) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    tugas.spk.namaBarang ?: tugas.spk.kodeBarang,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                // Belum menjawab TIDAK diberi label apa pun — "Menunggu" akan
                // terbaca seperti keadaan yang dibuat server, padahal barisnya
                // memang belum ada (lihat AcInstallPetugasDto).
                label?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            labelJadwal(tugas)?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            }
            alamatEfektif(tugas)?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (terbuka) {
                Spacer(Modifier.height(10.dp))
                Baris("SPK", tugas.spk.kodePengiriman)
                Baris("Cabang", tugas.spk.cabangNama ?: tugas.spk.kodeDealer)
                kontakNamaEfektif(tugas)?.let { Baris("Kontak", it) }
                kontakHpEfektif(tugas)?.let { Baris("No. HP", it) }
                tugas.catatan?.takeIf { it.isNotBlank() }?.let { Baris("Catatan", it) }
                tugas.catatanJadwal?.takeIf { it.isNotBlank() }?.let { Baris("Catatan jadwal", it) }
                tugas.tim.forEach { tim ->
                    Baris("Tim ${tim.nama}", tim.anggota.joinToString(", ") { it.nama }.ifBlank { "-" })
                }
                Baris("Bukti foto", "${tugas.foto.size} foto")
                saya?.alasan?.takeIf { it.isNotBlank() }?.let { Baris("Alasan kamu", it) }

                if (!sayaDitugaskan(tugas, userId)) {
                    // Bisa terjadi: verifikator menjadwalkan ulang dan mengganti
                    // daftar tim sementara layar ini terbuka. Tanpa kalimat ini
                    // tombolnya cuma hilang tanpa sebab.
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Kamu tidak lagi terdaftar di tim tugas ini.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                } else if (bolehDijawab(tugas)) {
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(onClick = onTerima, enabled = !submitting, modifier = Modifier.weight(1f)) {
                            // Label mengikuti keadaan: menerima itu upsert, jadi
                            // yang sudah menolak memang boleh berubah pikiran.
                            Text(if (saya?.status == "diterima") "Sudah diterima" else "Terima")
                        }
                        OutlinedButton(onClick = onTolak, enabled = !submitting, modifier = Modifier.weight(1f)) {
                            Text("Tolak")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    if (adaBuktiTertunda) {
                        OutlinedButton(
                            onClick = onLampirkanUlang,
                            enabled = !mengunggahFoto,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Lampirkan ulang bukti") }
                    } else {
                        OutlinedButton(
                            onClick = onFoto,
                            enabled = !mengunggahFoto,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (mengunggahFoto) {
                                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text("Mengunggah…")
                            } else {
                                Icon(Icons.Rounded.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Ambil bukti foto")
                            }
                        }
                    }
                }
            }
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
            modifier = Modifier.width(110.dp),
        )
        Text(nilai, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
    }
}

/** Menolak WAJIB beralasan — gerbangnya [bolehTolak], bukan 400 dari server. */
@Composable
private fun DialogTolak(
    submitting: Boolean,
    onDismiss: () -> Unit,
    onTolak: (String) -> Unit,
) {
    var alasan by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tolak tugas ini?") },
        text = {
            Column {
                Text(
                    "Alasannya dikirim ke penjadwal. Menolak tidak mencabut penugasan — " +
                        "verifikator yang memutuskan penggantinya.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = alasan,
                    onValueChange = { alasan = it },
                    label = { Text("Alasan (wajib)") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onTolak(alasan) },
                enabled = !submitting && bolehTolak(alasan),
            ) { Text("Tolak") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !submitting) { Text("Batal") } },
    )
}

/*
 * KENAPA TAK ADA TOMBOL "SELESAI" DI SINI.
 *
 * Server MENGIZINKANNYA — `transisi::boleh_selesai` + aturan kepemilikan tugas
 * (`petugas_ditugaskan`) memang dibuat supaya petugas bisa menutup pekerjaannya
 * sendiri, dengan verifikator sebagai cadangan. Jadi absennya tombol ini adalah
 * PILIHAN LINGKUP (permintaan user 2026-08-22: terima, tolak, bukti foto), bukan
 * batasan server dan bukan kelalaian.
 *
 * Kalau nanti ditambahkan, dua hal yang mengikat:
 * - `selesai` menuntut MINIMAL SATU foto, dihitung DI DALAM transaksi penutupan.
 *   Tombolnya karena itu harus mati selama `tugas.foto` kosong — bukan
 *   mengandalkan 400 sebagai validasi.
 * - Bukti foto BEKU setelah ditutup (`transisi::boleh_ubah_foto`), jadi urutan
 *   di layar harus memotret DULU baru menutup, dan setelah tertutup tombol foto
 *   ikut mati ([bolehDijawab] sudah menutupinya).
 */
