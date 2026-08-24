package com.krisoft.tridjayaelektronik.ui.eksekutif

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.krisoft.tridjayaelektronik.ui.attendance.hariIniSebagaiUtcMidnight

/**
 * Pemilih periode papan eksekutif.
 *
 * Dua lapis, dan pemisahannya disengaja: **preset** untuk yang ditanyakan tiap
 * hari (hari ini, kemarin, bulan ini) sebagai satu ketukan, dan **sheet** untuk
 * pilihan yang lebih jarang tapi harus ada (tanggal tertentu, bulan tertentu,
 * tahun, rentang bebas). Menaruh semuanya sebagai chip akan membuat baris
 * pertama layar penuh chip yang jarang disentuh; menaruh semuanya di sheet
 * membuat "hari ini" — pertanyaan paling sering — butuh tiga ketukan.
 *
 * Chip terakhir menampilkan pilihan MANUAL yang sedang aktif kalau ada, bukan
 * kata "Pilih" yang tetap: orang yang memilih 12 Agustus lalu men-scroll harus
 * bisa melihat periode apa yang sedang dibacanya tanpa membuka sheet lagi.
 */
@Composable
fun PemilihPeriode(
    terpilih: PilihanPeriode,
    onPilih: (PilihanPeriode) -> Unit,
    modifier: Modifier = Modifier,
) {
    var sheetTerbuka by remember { mutableStateOf(false) }
    val manual = terpilih !is PilihanPeriode.Preset

    Row(
        modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        EksekutifRentang.entries.forEach { preset ->
            FilterChip(
                selected = terpilih == PilihanPeriode.Preset(preset),
                onClick = { onPilih(PilihanPeriode.Preset(preset)) },
                label = {
                    Text(preset.label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                },
            )
        }
        FilterChip(
            selected = manual,
            onClick = { sheetTerbuka = true },
            leadingIcon = {
                Icon(Icons.Rounded.CalendarMonth, contentDescription = null)
            },
            label = {
                Text(
                    if (manual) labelPilihan(terpilih) else "Pilih…",
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                )
            },
        )
    }

    if (sheetTerbuka) {
        SheetPeriode(
            terpilih = terpilih,
            onTutup = { sheetTerbuka = false },
            onPilih = {
                sheetTerbuka = false
                onPilih(it)
            },
        )
    }
}

private enum class ModePilih(val label: String) {
    TANGGAL("Tanggal"),
    BULAN("Bulan"),
    TAHUN("Tahun"),
    RENTANG("Rentang"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SheetPeriode(
    terpilih: PilihanPeriode,
    onTutup: () -> Unit,
    onPilih: (PilihanPeriode) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // Mode awal mengikuti apa yang SEDANG dipilih, bukan selalu "Tanggal":
    // membuka kembali sheet lalu mendapati tab lain adalah cara tercepat
    // membuat orang mengira pilihannya hilang.
    var mode by remember {
        mutableStateOf(
            when (terpilih) {
                is PilihanPeriode.Bulan -> ModePilih.BULAN
                is PilihanPeriode.Tahun -> ModePilih.TAHUN
                is PilihanPeriode.Kustom -> ModePilih.RENTANG
                else -> ModePilih.TANGGAL
            }
        )
    }

    ModalBottomSheet(onDismissRequest = onTutup, sheetState = sheetState) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
            Text(
                "Pilih periode",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(12.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                ModePilih.entries.forEachIndexed { index, m ->
                    SegmentedButton(
                        selected = mode == m,
                        onClick = { mode = m },
                        shape = SegmentedButtonDefaults.itemShape(index, ModePilih.entries.size),
                        label = { Text(m.label, style = MaterialTheme.typography.labelMedium) },
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            when (mode) {
                ModePilih.TANGGAL -> IsiTanggal(terpilih, onPilih)
                ModePilih.BULAN -> IsiBulan(terpilih, onPilih)
                ModePilih.TAHUN -> IsiTahun(terpilih, onPilih)
                ModePilih.RENTANG -> IsiRentang(terpilih, onPilih)
            }
        }
    }
}

/**
 * Batas atas picker = HARI INI.
 *
 * Bukan kerapian: rentang yang ujungnya di masa depan membuat server memasukkan
 * hari kerja yang BELUM TERJADI ke penyebut kehadiran dan kepatuhan, sehingga
 * angka satu perusahaan terlihat anjlok tanpa satu pun sebab yang terlihat.
 * `rentangUntuk` sudah memotongnya juga — ini lapis pertama, supaya orangnya tak
 * pernah sempat memilih tanggal yang lalu diam-diam diubah di belakangnya.
 */
@OptIn(ExperimentalMaterial3Api::class)
private object SampaiHariIni : androidx.compose.material3.SelectableDates {
    override fun isSelectableDate(utcTimeMillis: Long): Boolean =
        utcTimeMillis <= hariIniSebagaiUtcMidnight()

    override fun isSelectableYear(year: Int): Boolean {
        val tahunIni = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        return year <= tahunIni
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IsiTanggal(terpilih: PilihanPeriode, onPilih: (PilihanPeriode) -> Unit) {
    val awal = (terpilih as? PilihanPeriode.Tanggal)?.tanggal?.let(::isoKeUtcMidnight)
        ?: hariIniSebagaiUtcMidnight()
    val state = rememberDatePickerState(
        initialSelectedDateMillis = awal,
        selectableDates = SampaiHariIni,
    )
    Column {
        DatePicker(state = state, title = null, headline = null, showModeToggle = false)
        Spacer(Modifier.height(8.dp))
        TombolTerapkan(aktif = state.selectedDateMillis != null) {
            state.selectedDateMillis?.let { onPilih(PilihanPeriode.Tanggal(utcMidnightKeIso(it))) }
        }
    }
}

@Composable
private fun IsiBulan(terpilih: PilihanPeriode, onPilih: (PilihanPeriode) -> Unit) {
    val cal = remember { java.util.Calendar.getInstance() }
    val tahunIni = cal.get(java.util.Calendar.YEAR)
    val bulanIni = cal.get(java.util.Calendar.MONTH) + 1
    var tahun by remember {
        mutableStateOf((terpilih as? PilihanPeriode.Bulan)?.kunci?.take(4)?.toIntOrNull() ?: tahunIni)
    }
    Column {
        PemilihTahunBaris(tahun = tahun, batasAtas = tahunIni, onGeser = { tahun = it })
        Spacer(Modifier.height(12.dp))
        // Bulan yang BELUM datang dimatikan, bukan disembunyikan: kisi 12 bulan
        // yang jumlahnya berubah tiap bulan lebih sulit dibaca daripada kisi
        // tetap yang sebagian pudar.
        (1..12).chunked(3).forEach { baris ->
            Row(
                Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                baris.forEach { b ->
                    val kunci = "%04d-%02d".format(tahun, b)
                    val bisa = tahun < tahunIni || b <= bulanIni
                    FilterChip(
                        selected = terpilih == PilihanPeriode.Bulan(kunci),
                        enabled = bisa,
                        onClick = { onPilih(PilihanPeriode.Bulan(kunci)) },
                        label = {
                            Text(
                                NAMA_BULAN[b - 1],
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                            )
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun IsiTahun(terpilih: PilihanPeriode, onPilih: (PilihanPeriode) -> Unit) {
    val tahunIni = remember { java.util.Calendar.getInstance().get(java.util.Calendar.YEAR) }
    Column {
        Text(
            // Batas server 366 hari berarti tahun kalender penuh MUAT, tapi
            // hanya satu tahun — dan itu perlu ditulis, karena "Tahun" terbaca
            // seperti janji riwayat berbilang tahun.
            "Satu tahun kalender penuh. Server melayani maksimal $MAKS_HARI_RENTANG hari, " +
                "jadi dua tahun sekaligus tidak bisa.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (0..2).forEach { mundur ->
                val t = tahunIni - mundur
                FilterChip(
                    selected = terpilih == PilihanPeriode.Tahun(t.toString()),
                    onClick = { onPilih(PilihanPeriode.Tahun(t.toString())) },
                    label = { Text("$t", style = MaterialTheme.typography.labelMedium) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IsiRentang(terpilih: PilihanPeriode, onPilih: (PilihanPeriode) -> Unit) {
    val kustom = terpilih as? PilihanPeriode.Kustom
    var mulai by remember {
        mutableStateOf(kustom?.start ?: utcMidnightKeIso(hariIniSebagaiUtcMidnight()))
    }
    var akhir by remember {
        mutableStateOf(kustom?.end ?: utcMidnightKeIso(hariIniSebagaiUtcMidnight()))
    }
    var pickerUntuk by remember { mutableStateOf<String?>(null) }
    val alasan = validasiRentang(mulai, akhir)

    Column {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AssistChip(
                onClick = { pickerUntuk = "mulai" },
                label = { Text("Mulai: ${labelTanggalPendek(mulai)}", maxLines = 1) },
                modifier = Modifier.weight(1f),
            )
            AssistChip(
                onClick = { pickerUntuk = "akhir" },
                label = { Text("Akhir: ${labelTanggalPendek(akhir)}", maxLines = 1) },
                modifier = Modifier.weight(1f),
            )
        }
        if (alasan != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                alasan,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Spacer(Modifier.height(12.dp))
        TombolTerapkan(aktif = alasan == null) {
            onPilih(PilihanPeriode.Kustom(mulai, akhir))
        }
    }

    if (pickerUntuk != null) {
        val awal = (if (pickerUntuk == "mulai") mulai else akhir).let(::isoKeUtcMidnight)
            ?: hariIniSebagaiUtcMidnight()
        val state = rememberDatePickerState(
            initialSelectedDateMillis = awal,
            selectableDates = SampaiHariIni,
        )
        DatePickerDialog(
            onDismissRequest = { pickerUntuk = null },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        val iso = utcMidnightKeIso(it)
                        if (pickerUntuk == "mulai") mulai = iso else akhir = iso
                    }
                    pickerUntuk = null
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { pickerUntuk = null }) { Text("Batal") }
            },
        ) { DatePicker(state = state) }
    }
}

@Composable
private fun PemilihTahunBaris(tahun: Int, batasAtas: Int, onGeser: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TextButton(onClick = { onGeser(tahun - 1) }) { Text("‹ ${tahun - 1}") }
        Text(
            "$tahun",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f).padding(top = 12.dp),
        )
        TextButton(
            onClick = { onGeser(tahun + 1) },
            enabled = tahun < batasAtas,
        ) { Text("${tahun + 1} ›") }
    }
}

@Composable
private fun TombolTerapkan(aktif: Boolean, onKlik: () -> Unit) {
    androidx.compose.material3.Button(
        onClick = onKlik,
        enabled = aktif,
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Terapkan") }
}

private val NAMA_BULAN = listOf(
    "Jan", "Feb", "Mar", "Apr", "Mei", "Jun",
    "Jul", "Agu", "Sep", "Okt", "Nov", "Des",
)
