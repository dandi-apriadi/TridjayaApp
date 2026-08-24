package com.krisoft.tridjayaelektronik.ui.deliveryflow

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.krisoft.tridjayaelektronik.data.model.KontrolSaringan
import com.krisoft.tridjayaelektronik.data.model.SaringanAntrian
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveTextField

/**
 * Bilah saringan di atas antrian delivery.
 *
 * **WAJIB dirender DI LUAR `when` keadaan layar** — sama seperti
 * `PeriodeFilterRow`, dan alasannya identik: kalau kontrolnya ikut hilang saat
 * hasil nol, orang yang menyaring ke satu cabang tak punya jalan kembali dan
 * membaca daftar kosong sebagai data yang hilang.
 *
 * Keputusan "kontrol mana yang muncul" TIDAK dibuat di sini — ia datang sebagai
 * [KontrolSaringan] per rute, karena `kodeDealer` diabaikan server di sebagian
 * layar dan menghasilkan daftar kosong di layar lain (lihat KDoc
 * [SaringanAntrian]).
 */
@Composable
fun SaringanAntrianBar(
    kontrol: KontrolSaringan,
    saringan: SaringanAntrian,
    onUbah: (SaringanAntrian) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!kontrol.adaKontrol) return

    Column(modifier = modifier.fillMaxWidth()) {
        if (kontrol.cari) {
            CariAntrianField(
                nilaiTerpakai = saringan.q,
                onTerapkan = { onUbah(saringan.copy(q = it)) },
            )
        }
        if (kontrol.cabang || kontrol.urut || kontrol.metode) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (kontrol.cabang) {
                    CabangFilterChip(
                        dipilih = saringan.kodeDealer,
                        onPilih = { onUbah(saringan.copy(kodeDealer = it)) },
                    )
                }
                if (kontrol.urut) {
                    // Pengganti chip periode di antrian kerja: ia menjawab
                    // kebutuhan yang sama ("yang mendesak di atas") TANPA
                    // menyembunyikan tunggakan seperti saringan tanggal.
                    FilterChip(
                        selected = saringan.urut == SaringanAntrian.URUT_TERLAMA,
                        onClick = {
                            val baru = if (saringan.urut == SaringanAntrian.URUT_TERLAMA) null
                            else SaringanAntrian.URUT_TERLAMA
                            onUbah(saringan.copy(urut = baru))
                        },
                        label = { Text("Terlama dulu") },
                    )
                }
                if (kontrol.metode) {
                    MetodeKirimChips(
                        dipilih = saringan.deliveryMethod,
                        onPilih = { onUbah(saringan.copy(deliveryMethod = it)) },
                    )
                }
            }
        }
    }
}

/**
 * Kotak cari yang MENEMBAK SERVER, jadi ia submit pada tombol — bukan tiap
 * ketikan. Itu preseden yang sudah dipilih repo ini untuk daftar server-side
 * (`AktivitasReviewViewModel.terapkanCari`, KDoc-nya eksplisit "bukan tiap
 * ketikan"), dan menghindari kebutuhan debounce sama sekali: repo ini tak punya
 * helper debounce bersama.
 */
@Composable
private fun CariAntrianField(
    nilaiTerpakai: String?,
    onTerapkan: (String?) -> Unit,
) {
    // Draf lokal. Di-`remember` atas nilai yang SEDANG TERPAKAI supaya kotaknya
    // ikut kosong saat saringan direset dari luar, tapi tidak menimpa ketikan
    // yang sedang berjalan.
    var ketikan by remember(nilaiTerpakai) { mutableStateOf(nilaiTerpakai.orEmpty()) }
    val berubah = ketikan.trim() != nilaiTerpakai.orEmpty().trim()

    ExpressiveTextField(
        value = ketikan,
        onValueChange = { ketikan = it },
        placeholder = "Cari kode SPK / konsumen / no. transaksi / serial…",
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        trailingIcon = {
            when {
                // Ada ketikan yang belum diterapkan → tombol cari.
                berubah -> IconButton(onClick = { onTerapkan(ketikan.trim().ifBlank { null }) }) {
                    Icon(Icons.Rounded.Search, contentDescription = "Cari")
                }
                // Saringan sedang terpasang → tombol hapus.
                !nilaiTerpakai.isNullOrBlank() -> IconButton(onClick = {
                    ketikan = ""
                    onTerapkan(null)
                }) {
                    Icon(Icons.Rounded.Close, contentDescription = "Hapus pencarian")
                }
                else -> Icon(
                    Icons.Rounded.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

/**
 * Chip-dropdown cabang — setinggi chip, BUKAN setinggi field.
 *
 * Sengaja bukan `CabangSelector` yang sudah ada: yang itu Column berlabel +
 * Row selebar layar tanpa opsi kosong, yaitu kolom FORM yang tepat untuk form
 * SPK dan Input SN. Menempelkannya di atas antrian kerja memakan satu blok
 * vertikal permanen di layar yang isinya pekerjaan. Yang dipakai ulang di sini
 * adalah SUMBER DATANYA, bukan bentuknya.
 */
@Composable
private fun CabangFilterChip(
    dipilih: String?,
    onPilih: (String?) -> Unit,
) {
    var buka by remember { mutableStateOf(false) }
    val label = dipilih?.let { kode ->
        BranchRegions.DEALER_LABEL[kode]?.let { "$kode · $it" } ?: kode
    } ?: "Semua cabang"

    Box {
        FilterChip(
            selected = dipilih != null,
            onClick = { buka = true },
            label = { Text(label) },
            trailingIcon = {
                Icon(Icons.Rounded.ExpandMore, contentDescription = null, modifier = Modifier.size(18.dp))
            },
        )
        DropdownMenu(expanded = buka, onDismissRequest = { buka = false }) {
            DropdownMenuItem(
                text = { Text("Semua cabang") },
                onClick = { onPilih(null); buka = false },
            )
            BranchRegions.DEALER_LABEL.forEach { (kode, nama) ->
                DropdownMenuItem(
                    text = { Text("$kode · $nama") },
                    onClick = { onPilih(kode); buka = false },
                )
            }
        }
    }
}

/**
 * Chip metode kirim — HANYA layar Penjadwalan.
 *
 * Labelnya harus mengatakan kebenarannya: server MEMBALIK default di tahap ini
 * (kosong = buang `self_pickup` + `sales_delivery`; diisi = tampilkan HANYA
 * itu), jadi memilih "Diambil Sendiri" MENAMBAH baris yang tadinya tak terlihat.
 * Ini satu-satunya saringan di bilah ini yang MELEBARKAN daftar — pemakai yang
 * mengira semua saringan menyempitkan akan salah membaca hasilnya.
 */
@Composable
private fun MetodeKirimChips(
    dipilih: String?,
    onPilih: (String?) -> Unit,
) {
    FilterChip(
        selected = dipilih == null,
        onClick = { onPilih(null) },
        label = { Text("Perlu dijadwalkan") },
    )
    SaringanAntrian.DELIVERY_METHOD_PILIHAN.forEach { (nilai, label) ->
        FilterChip(
            selected = dipilih == nilai,
            onClick = { onPilih(if (dipilih == nilai) null else nilai) },
            label = { Text(label) },
        )
    }
}

/**
 * Baris "Menampilkan N dari M". Tak merender apa pun kalau daftarnya utuh atau
 * kalau server belum mengirim `total` — lihat `indikatorTerpotong`.
 */
@Composable
fun IndikatorTerpotongRow(teks: String?, modifier: Modifier = Modifier) {
    if (teks == null) return
    Text(
        text = teks,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
    )
}
