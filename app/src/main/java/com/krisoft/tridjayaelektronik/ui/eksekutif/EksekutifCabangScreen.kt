package com.krisoft.tridjayaelektronik.ui.eksekutif

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.krisoft.tridjayaelektronik.data.model.EksekutifDetailCabangDto
import com.krisoft.tridjayaelektronik.data.model.EksekutifKaryawanDto
import com.krisoft.tridjayaelektronik.ui.theme.ClayCard
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveEmptyState
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveErrorState
import com.krisoft.tridjayaelektronik.ui.theme.TridjayaCollapsibleHeader
import com.krisoft.tridjayaelektronik.ui.theme.TridjayaPullRefresh

/** Detail satu cabang: angkanya sendiri + seluruh karyawan di dalamnya. */
@Composable
fun EksekutifCabangScreen(
    viewModel: EksekutifViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    val detail = state.detail
    val judul = detail?.cabang?.nama ?: state.kodeDealerDibuka ?: "Cabang"

    // Back SISTEM harus melewati jalur yang sama dengan tombol back di header.
    // Tanpa ini `tutupCabang()` dilewati, `kodeDealerDibuka` tetap terisi, dan
    // tiap penggantian chip rentang di papan menembak `GET /cabang/<kode>` untuk
    // layar yang sudah lama ditutup.
    //
    // BUKAN `DisposableEffect { onDispose { tutupCabang() } }` — itu ikut jalan
    // saat rotasi layar, yaitu membuang detail yang justru sedang dilihat orang.
    BackHandler(onBack = onBack)

    TridjayaCollapsibleHeader(title = judul, onBack = onBack) { modifier ->
        Box(modifier.fillMaxSize()) {
            when {
                detail == null && state.detailError != null -> ExpressiveErrorState(
                    message = state.detailError!!,
                    onRetry = { state.kodeDealerDibuka?.let(viewModel::bukaCabang) },
                )
                // Spinner, bukan `Box` kosong: pemuatan detail cabang terjadi di
                // SETIAP pembukaan (tanpa cache), jadi layar kosong tanpa tanda
                // adalah tampilan NORMAL-nya selama satu round-trip — dan itu
                // terbaca sebagai gagal muat.
                detail == null -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
                else -> IsiCabang(
                    detail = detail,
                    periode = state.periode,
                    // Galat PAPAN (`state.error`) tak ikut ditampilkan di sini:
                    // ia milik layar lain dan lengket sampai papan berhasil
                    // dimuat ulang, sehingga banner merah bertahan di atas
                    // detail cabang yang datanya baik-baik saja. Satu-satunya
                    // galat papan yang relevan bagi layar ini — periode yang
                    // ditolak validasi — sudah dijawab `pilihPeriode` dengan
                    // MEMPERTAHANKAN periode lama, jadi angka di layar tetap
                    // sesuai chip-nya.
                    error = state.detailError,
                    sedangMuat = state.detailLoading,
                    onPilihPeriode = viewModel::pilihPeriode,
                    onMuatUlang = { state.kodeDealerDibuka?.let(viewModel::bukaCabang) },
                )
            }
        }
    }
}

@Composable
private fun IsiCabang(
    detail: EksekutifDetailCabangDto,
    periode: PilihanPeriode,
    error: String?,
    sedangMuat: Boolean,
    onPilihPeriode: (PilihanPeriode) -> Unit,
    onMuatUlang: () -> Unit,
) {
    val c = detail.cabang
    TridjayaPullRefresh(
        // Penanda muat WAJIB ada di sini. Mengganti periode dari layar ini
        // memuat ulang detailnya, dan tanpa penanda apa pun yang terlihat cuma
        // chip yang berubah di atas angka LAMA — tak bisa dibedakan dari
        // "periodenya memang menghasilkan angka yang sama". Sekaligus ia jalan
        // muat-ulang saat galat: tanpanya, gagal muat di layar ini adalah jalan
        // buntu (chip yang sama tak bisa diketuk ulang, karena ViewModel
        // menolak pilihan yang tak berubah).
        isRefreshing = sedangMuat,
        onRefresh = onMuatUlang,
    ) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Pemilih periode ADA DI SINI JUGA, bukan hanya di papan. Sebelumnya
        // layar ini cuma menampilkan label periode apa adanya, sehingga
        // mengganti rentang saat sedang menelusuri satu cabang menuntut
        // kembali ke papan lalu masuk lagi — dan pertanyaan "cabang ini
        // kemarin bagaimana" adalah pertanyaan yang justru muncul DI SINI.
        // ViewModel-nya sama (`EksekutifNavHost` men-scope-nya ke route akar),
        // jadi papan ikut berganti periode dan keduanya tak pernah berselisih.
        item {
            PemilihPeriode(terpilih = periode, onPilih = onPilihPeriode)
        }
        item {
            Text(
                labelRentang(RentangTanggal(detail.periode.start, detail.periode.end)) +
                    " · ${detail.periode.hariKerja} hari kerja",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (error != null) {
            item {
                Column {
                    com.krisoft.tridjayaelektronik.ui.theme.ExpressiveInlineError(message = error)
                    Spacer(Modifier.height(6.dp))
                    androidx.compose.material3.TextButton(onClick = onMuatUlang) {
                        Text("Coba lagi")
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                KartuAngka(
                    judul = "Omset cabang",
                    nilai = formatRupiahRingkas(c.penjualan.omset),
                    keterangan = "${c.penjualan.transaksi} transaksi",
                    modifier = Modifier.weight(1f),
                )
                KartuAngka(
                    judul = "Kehadiran",
                    nilai = formatPersen(c.absensi.persenHadir),
                    keterangan = "${c.absensi.alpa} hari tanpa keterangan",
                    modifier = Modifier.weight(1f),
                )
            }
        }
        item {
            KartuKepatuhan(c.kepatuhan, judul = "Skor kepatuhan cabang")
        }
        item {
            ClayCard {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        "Target vs capaian cabang",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(6.dp))
                    PanelTarget(
                        t = c.target,
                        aktualOmset = c.penjualan.omset,
                        aktualUnit = c.penjualan.unitBesar,
                        // Sistem ini tak punya target UNIT per cabang; yang ada
                        // per ORANG. Menjumlahkannya jadi target cabang akan
                        // memakai cakupan dealer yang berbeda dari omset cabang
                        // di atasnya — dua angka yang tak sebanding, disandingkan.
                        tampilkanUnit = false,
                    )
                }
            }
        }
        item {
            ClayCard {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        "SPK dashboard vs GS",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(8.dp))
                    PanelKecocokan(c.spkVsGs, detail.kesegaran.spkMatangJam)
                }
            }
        }
        item {
            Text(
                "Karyawan (${detail.karyawan.size})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        if (detail.karyawan.isEmpty()) {
            item {
                ExpressiveEmptyState(
                    icon = {
                        androidx.compose.material3.Icon(
                            androidx.compose.material.icons.Icons.Rounded.Groups,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                    title = "Belum ada karyawan",
                    subtitle = "Tidak ada akun aktif yang tercatat di cabang ini. " +
                        "Bisa jadi `cabang_id` akunnya kosong atau basi, bukan berarti " +
                        "cabangnya tak berpenghuni.",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        items(detail.karyawan, key = { it.userId }) { k ->
            KartuKaryawan(k, detail.periode.hariKerja)
        }
    }
    }
}

@Composable
private fun KartuKaryawan(k: EksekutifKaryawanDto, detailHariKerja: Long) {
    ClayCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text(
                k.nama,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(
                    k.nik.takeIf { it.isNotBlank() },
                    k.divisi.takeIf { it.isNotBlank() } ?: k.role.takeIf { it.isNotBlank() },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))

            // Label menyebut cakupannya, dan itu WAJIB: angka ini milik ORANG
            // (seluruh dealer), sedangkan kartu cabang di atas layar yang sama
            // milik CABANG. Terukur pada mirror Agustus 2026: 6 dari 14 cabang
            // punya jumlah karyawan yang MELEBIHI kartu cabangnya — tanpa label
            // ini, dua angka yang sah terbaca sebagai satu angka yang salah.
            BarisRincian("Omset pribadi (semua cabang)", formatRupiah(k.penjualan.omset))
            BarisRincian("Unit besar", "${k.penjualan.unitBesar}")
            BarisRincian(
                "Kehadiran",
                // `null` = orang ini memang tidak diwajibkan absen (manager,
                // akun uji, atau NIK yang terdaftar dikecualikan). Ditulis
                // apa adanya — "0%" di sini akan terbaca sebagai pelanggaran
                // yang tak pernah terjadi.
                if (k.persenHadir == null && detailHariKerja > 0) {
                    // `null` HANYA boleh dibaca "tidak wajib absen" kalau
                    // rentangnya memang punya hari kerja. Pada rentang tanpa
                    // hari kerja (tanggal 1 jatuh Minggu, dsb) server juga
                    // menjawab `null` — untuk SEMUA orang — dan menyatakannya
                    // sebagai fakta tentang orangnya adalah karangan.
                    "tidak wajib absen"
                } else if (k.persenHadir == null) {
                    "—"
                } else {
                    "${formatPersen(k.persenHadir)} · ${k.hadir} hadir, ${k.offHari} izin"
                },
            )
            BarisRincian(
                "Pakai sistem",
                "${formatPersen(k.pemakaian.persen)} · " +
                    "${k.pemakaian.hariAktif}/${k.pemakaian.hariKerja} hari",
            )
            Spacer(Modifier.height(2.dp))
            // Rincian jejaknya ditulis supaya angka "pakai sistem" bisa
            // DIBANTAH, bukan cuma dipercaya: orang yang terlihat rendah bisa
            // saja pekerjaannya memang tak meninggalkan jejak jenis tertentu.
            Text(
                "jejak: absen ${k.pemakaian.rincian.absen} · " +
                    "aktivitas ${k.pemakaian.rincian.aktivitas} · " +
                    "SPK ${k.pemakaian.rincian.spk} · " +
                    "prospek ${k.pemakaian.rincian.prospek}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (k.spkVsGs.dinilai > 0) {
                Spacer(Modifier.height(4.dp))
                BarisRincian(
                    "SPK cocok di GS (nomor)",
                    "${k.spkVsGs.cocokNomor} / ${k.spkVsGs.dinilai}",
                )
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            val pita = pitaKepatuhan(k.kepatuhan.skor)
            BarisRincian(
                "Skor kepatuhan",
                "${formatSkor(k.kepatuhan.skor)} · ${pita.label}" +
                    " (bobot terukur ${formatBobot(k.kepatuhan.bobotTerpakai)})",
                warnaNilai = when (pita) {
                    PitaKepatuhan.PRIMA -> MaterialTheme.colorScheme.primary
                    PitaKepatuhan.PANTAU -> MaterialTheme.colorScheme.tertiary
                    PitaKepatuhan.PRIORITAS -> MaterialTheme.colorScheme.error
                    PitaKepatuhan.TAK_TERUKUR -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            // Rinciannya ditulis apa adanya supaya skor bisa DIPERIKSA, bukan
            // cuma dipercaya — sama seperti baris "jejak" di atasnya.
            Text(
                "aktivitas ${k.kepatuhan.rincian.aktivitasTerisi}/" +
                    "${k.kepatuhan.rincian.aktivitasWajib} butir · " +
                    "bukti ${k.kepatuhan.rincian.buktiSah}/" +
                    "${k.kepatuhan.rincian.buktiWajib}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(6.dp))
            PanelTarget(
                t = k.target,
                aktualOmset = k.penjualan.omset,
                aktualUnit = k.penjualan.unitBesar,
                // Target RUPIAH per orang tak pernah terisi di sistem ini
                // (`FinanceTargetsPage` mengirim `targetAmount: 0` tanpa
                // syarat), jadi barisnya tak dirender. Yang ada dan dipakai
                // adalah target UNIT dari `sales_targets`/GS.
                tampilkanOmset = false,
            )
        }
    }
}
