package com.krisoft.tridjayaelektronik.ui.opname

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FactCheck
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.krisoft.tridjayaelektronik.data.VALIDASI_APPROVED
import com.krisoft.tridjayaelektronik.data.VALIDASI_PENDING
import com.krisoft.tridjayaelektronik.data.VALIDASI_REJECTED
import com.krisoft.tridjayaelektronik.data.model.ManualUnitDto
import com.krisoft.tridjayaelektronik.ui.deliveryflow.BuktiFotoThumbnail
import com.krisoft.tridjayaelektronik.ui.theme.ClayCard
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveEmptyState
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveErrorState
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveFilledButton
import com.krisoft.tridjayaelektronik.ui.theme.ScrollableCenter
import com.krisoft.tridjayaelektronik.ui.theme.SkeletonCard
import com.krisoft.tridjayaelektronik.ui.theme.TridjayaCollapsibleHeader
import com.krisoft.tridjayaelektronik.ui.theme.TridjayaPullRefresh

/**
 * Antrian validasi unit opname ketik-manual — admin-stok memutus SETUJUI/TOLAK
 * dari HP. Sebelum layar ini keputusan hanya bisa diambil dari web, padahal
 * sesi opname di cabang TIDAK BISA ditutup selama masih ada unit `pending`.
 *
 * Tujuan deep-link notif `opname_manual_submitted` (route key `opname_validasi`).
 */
@Composable
fun OpnameValidasiScreen(
    onBack: () -> Unit,
    viewModel: OpnameValidasiViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var tolakUnit by remember { mutableStateOf<ManualUnitDto?>(null) }
    var alasan by remember { mutableStateOf("") }

    TridjayaCollapsibleHeader(title = "Validasi Opname", onBack = onBack) { contentModifier ->
        Box(modifier = contentModifier.fillMaxSize()) {
            TridjayaPullRefresh(
                isRefreshing = state.isLoading && state.items.isNotEmpty(),
                onRefresh = viewModel::load
            ) {
              Column(modifier = Modifier.fillMaxSize()) {
                // Baris chip DI LUAR `when` — aturan yang sama dengan antrian
                // delivery: kalau kontrolnya ikut hilang saat hasil nol, orang
                // yang membuka tab "Ditolak" yang kebetulan kosong tak punya
                // jalan kembali ke "Menunggu".
                ValidasiStatusChips(dipilih = state.status, onPilih = viewModel::gantiStatus)
                Box(modifier = Modifier.weight(1f)) {
                when {
                    state.isLoading && state.items.isEmpty() -> {
                        Column(modifier = Modifier.padding(top = 8.dp)) {
                            repeat(4) {
                                SkeletonCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                            }
                        }
                    }
                    // Gagal muat DIDAHULUKAN: menyamarkannya jadi "tak ada unit
                    // menunggu" membuat admin-stok mengira pekerjaannya kosong
                    // sementara sesi di cabang tetap terkunci.
                    state.errorMessage != null && state.items.isEmpty() -> {
                        ScrollableCenter {
                            ExpressiveErrorState(
                                message = state.errorMessage ?: "Tidak bisa memuat antrian validasi.",
                                onRetry = viewModel::load
                            )
                        }
                    }
                    state.items.isEmpty() -> {
                        ScrollableCenter {
                            ExpressiveEmptyState(
                                icon = { Icon(Icons.Rounded.FactCheck, contentDescription = null) },
                                title = when (state.status) {
                                    VALIDASI_APPROVED -> "Belum ada unit disetujui"
                                    VALIDASI_REJECTED -> "Belum ada unit ditolak"
                                    else -> "Tak ada unit menunggu validasi"
                                },
                                subtitle = when (state.status) {
                                    VALIDASI_PENDING -> "Unit opname yang diketik manual akan muncul di sini"
                                    // Kosong di tab riwayat ≠ tak ada pekerjaan.
                                    else -> "Riwayat keputusan pada tab ini masih kosong. Kembali ke \"Menunggu\" untuk antrian yang aktif."
                                }
                            )
                        }
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)
                        ) {
                            // Muat ulang yang GAGAL di atas daftar yang sudah
                            // terisi: tanpa baris ini spinner berhenti, daftar
                            // lama tetap terpampang, dan pemutus menyimpulkan
                            // antriannya sudah paling baru. Bukan kosong palsu
                            // (arah itu sudah ditutup di atas), tapi BASI palsu.
                            state.errorMessage?.let { pesan ->
                                item(key = "galat_muat_ulang") {
                                    Text(
                                        text = "$pesan — daftar di bawah mungkin sudah basi. Tarik untuk mencoba lagi.",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                    )
                                }
                            }
                            items(state.items, key = { it.id }) { unit ->
                                ManualUnitCard(
                                    unit = unit,
                                    photos = state.photos,
                                    submitting = state.submittingId != null,
                                    onBukaFoto = { viewModel.muatFoto(unit) },
                                    onSetujui = { viewModel.setujui(unit) },
                                    onTolak = {
                                        alasan = ""
                                        tolakUnit = unit
                                    }
                                )
                            }
                        }
                    }
                }
                }
              }
            }
        }
    }

    state.actionError?.let { pesan ->
        AlertDialog(
            onDismissRequest = viewModel::clearActionError,
            title = { Text("Keputusan gagal", fontWeight = FontWeight.Bold) },
            text = { Text(pesan) },
            confirmButton = { TextButton(onClick = viewModel::clearActionError) { Text("Tutup") } }
        )
    }

    tolakUnit?.let { unit ->
        AlertDialog(
            onDismissRequest = { tolakUnit = null },
            title = { Text("Tolak unit ${unit.serialNumber}?", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        "Alasan wajib diisi — petugas cabang perlu tahu apa yang harus diperbaiki.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = alasan,
                        onValueChange = { alasan = it },
                        placeholder = { Text("Alasan…") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = alasan.isNotBlank(),
                    onClick = {
                        viewModel.tolak(unit, alasan.trim())
                        tolakUnit = null
                    }
                ) { Text("Tolak") }
            },
            dismissButton = { TextButton(onClick = { tolakUnit = null }) { Text("Batal") } }
        )
    }
}

@Composable
private fun ManualUnitCard(
    unit: ManualUnitDto,
    photos: Map<String, FotoBukti>,
    submitting: Boolean,
    onBukaFoto: () -> Unit,
    onSetujui: () -> Unit,
    onTolak: () -> Unit
) {
    var buka by remember { mutableStateOf(false) }
    // Foto ditarik hanya untuk kartu yang dibuka — antrian panjang × 2 bitmap
    // penuh bisa menghabiskan memori HP lapangan.
    LaunchedEffect(buka) { if (buka) onBukaFoto() }

    ClayCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .clickable { buka = !buka }
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    unit.serialNumber,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                OpnameStatusBadge(unit.sessionStatus)
            }
            Spacer(Modifier.height(2.dp))
            Text(
                unit.kodeBarang + " · " + if (unit.kondisi == "tidak_layak") "tidak layak" else "layak",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                unit.dealerName.ifBlank { unit.dealerCode } + " · " + unit.kodeOpname,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                (unit.countedByName ?: "-") + " · " + formatOpnameDate(unit.countedAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            unit.temuan?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Temuan: " + temuanLabel(it),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            if (!buka) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Ketuk untuk melihat foto bukti & memutuskan",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                return@Column
            }

            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FotoSlot("Foto SN", photos[fotoKey(unit.id, "sn")], unit.fotoSnUrl, Modifier.weight(1f))
                FotoSlot("Foto barang", photos[fotoKey(unit.id, "barang")], unit.fotoBarangUrl, Modifier.weight(1f))
            }

            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExpressiveFilledButton(
                    onClick = onSetujui,
                    enabled = !submitting,
                    modifier = Modifier.weight(1f)
                ) { Text("Setujui") }
                OutlinedButton(
                    onClick = onTolak,
                    enabled = !submitting,
                    modifier = Modifier.weight(1f)
                ) { Text("Tolak") }
            }
        }
    }
}

/** Empat keadaan berbeda, sengaja tak disamakan — lihat [FotoBukti]. */
@Composable
private fun FotoSlot(
    judul: String,
    foto: FotoBukti?,
    url: String?,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        Text(
            judul,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        when (foto) {
            is FotoBukti.Ada -> BuktiFotoThumbnail(
                bitmap = foto.bitmap,
                deskripsi = judul,
                modifier = Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(10.dp))
            )
            is FotoBukti.Memuat -> Text(
                "Memuat…",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            is FotoBukti.Gagal -> Text(
                "Gagal dimuat — berkas tak ada di server atau jaringan putus.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error
            )
            null -> Text(
                if (url.isNullOrBlank()) "Tanpa foto bukti." else "Belum dimuat.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

/**
 * Chip status antrian validasi manual.
 *
 * Nilainya cerminan `list_manual_units_handler` (`inventory-service opname.rs`)
 * yang menerima PERSIS `pending|approved|rejected` dan menolak sisanya dengan
 * 400 — jadi jangan menuliskan literal baru di sini, pakai konstanta.
 *
 * `onClick` memanggil `onPilih(kunci)` LANGSUNG, bukan pola toggle
 * `dipilih?.let(onPilih)` yang dipakai `OpnameListScreen`. Pola itu benar di
 * sana karena status boleh dilepas ke "Semua"; di sini tak ada keadaan
 * tanpa-status, jadi menyalinnya menghasilkan chip yang mati rasa saat ditekan.
 */
@Composable
private fun ValidasiStatusChips(
    dipilih: String,
    onPilih: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf(
            VALIDASI_PENDING to "Menunggu",
            VALIDASI_APPROVED to "Disetujui",
            VALIDASI_REJECTED to "Ditolak",
        ).forEach { (kunci, label) ->
            FilterChip(
                selected = dipilih == kunci,
                onClick = { onPilih(kunci) },
                label = { Text(label) },
            )
        }
    }
}
