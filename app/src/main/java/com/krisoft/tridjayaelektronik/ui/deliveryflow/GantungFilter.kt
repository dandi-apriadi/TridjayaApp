package com.krisoft.tridjayaelektronik.ui.deliveryflow

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.krisoft.tridjayaelektronik.ui.activity.GANTUNG_TENGGAT_MS
import com.krisoft.tridjayaelektronik.ui.activity.deliveredAtUtcMillis

/**
 * Saringan UMUR untuk antrian "Konfirmasi Pembayaran" (SPK Gantung) — layar
 * divisi kasir.
 *
 * **Persoalannya**: server mengirim SELURUH unit terkirim yang bayarnya belum
 * dikonfirmasi, berapa pun umurnya, dan itu memang disengaja (lihat
 * `spkGantungRingkas`: menyaring >24 jam DULU membuat kartu Activity nol
 * sepanjang hari pertama). Konsekuensinya di layar: kartu Activity berkata
 * "4 lewat tenggat 24 jam", kasir membukanya, lalu mendapat daftar bercampur
 * tanpa satu pun cara menemukan keempatnya. Angka yang mendesak itu hanya bisa
 * dibaca, tak bisa dituju.
 *
 * **Kenapa saringan ini boleh, padahal saringan PERIODE dilarang di antrian
 * kerja** (lihat `DeliveryQueueScreen.periodeFilter`): saringan periode
 * menyembunyikan tunggakan LAMA di balik "hari ini" — persis pekerjaan yang
 * paling perlu dilihat. Saringan ini bergerak ke arah SEBALIKNYA: embernya
 * dibelah oleh tenggat yang sama yang dipakai kartu Activity, dan ember
 * "Lewat tenggat" berisi justru yang tertua. Tak ada arah pemakaian yang
 * membuatnya menyembunyikan tunggakan tanpa jejak — angka di tiap chip tetap
 * menyebut yang tak sedang tampil.
 *
 * Ambang + parser `deliveredAt` DIPINJAM dari `ui/activity/ActivityPlan.kt`,
 * tidak ditulis ulang. Dua salinan aturan "24 jam" berarti chip dan kartu
 * Activity bisa berselisih angka tanpa satu pun galat, dan parser tanggal
 * kedua di app ini adalah kesempatan kedua untuk menyelundupkan `java.time`
 * ke `app/src/main` (dilarang: minSdk 24 tanpa desugaring).
 */

/** Pilihan chip. Urutan enum = urutan tampil. */
enum class GantungSaring(val label: String) {
    SEMUA("Semua"),
    LEWAT_TENGGAT("Lewat tenggat"),
    BELUM_TENGGAT("Belum 24 jam"),
}

/**
 * Hasil penyaringan + bahan label chip.
 *
 * [tampilkanChip] dan [terlihat] dikembalikan BERSAMA dengan alasan yang sama
 * seperti [CabangFilterHasil]: memisahkannya membuka celah "chip hilang tapi
 * saringannya masih menyala".
 */
data class GantungFilterHasil(
    val tampilkanChip: Boolean,
    val jumlahLewatTenggat: Int,
    val jumlahBelumTenggat: Int,
    /** Grup yang boleh dirender, urutannya tak diubah. */
    val terlihat: List<SpkBatchGroup>,
)

/**
 * Satu grup SPK dinilai LEWAT TENGGAT bila SALAH SATU unitnya sudah melewati
 * [tenggatMs] sejak `deliveredAt`.
 *
 * `any`, bukan `all`: satu unit yang menunggak sudah cukup membuat SPK-nya
 * perlu ditutup kasir, dan menuntut SELURUH unit menunggak akan membuang SPK
 * yang unitnya diantar bertahap dari ember yang justru dicarinya. Pola yang
 * sama dengan [grupMilikSaya] dan dengan badge COD/PDI Mandiri di
 * `SpkRingkasCard`.
 *
 * `deliveredAt` kosong/tak terbaca → BUKAN lewat tenggat, cerminan
 * `spkGantungRingkas` yang menghitungnya di total tapi tak pernah menuduhnya
 * lewat tenggat. Menebak "lewat" atas timestamp yang tak terbaca akan menaruh
 * SPK yang baru saja diantar di ember mendesak.
 */
internal fun grupLewatTenggat(
    grup: SpkBatchGroup,
    nowMillis: Long,
    tenggatMs: Long = GANTUNG_TENGGAT_MS,
): Boolean = grup.jobs.any { job ->
    deliveredAtUtcMillis(job.deliveredAt)?.let { nowMillis - it > tenggatMs } ?: false
}

/**
 * Saring [groups] menurut [saring].
 *
 * **Dua penjagaan yang tak boleh dilepas** — keduanya salinan sengaja dari
 * [saringPerCabang], karena kelas kekeliruannya sama persis:
 *
 * 1. **Chip hanya ditawarkan saat daftarnya BENAR-BENAR bercampur.** Kalau
 *    semuanya lewat tenggat (atau tak satu pun), tak ada yang bisa disaring —
 *    chip-nya cuma menambah baris di layar dan menyiratkan ada tumpukan lain
 *    yang sebenarnya tak ada.
 *
 * 2. **Saat chip tak ditampilkan, saringannya DIABAIKAN.** Ini pencegah
 *    kebuntuan, bukan kerapian, dan di sini kebuntuannya lebih mudah terjadi
 *    daripada di saringan cabang: embernya digerakkan JAM, bukan perbuatan
 *    petugas. Kasir memilih "Belum 24 jam", meninggalkan HP-nya, lalu
 *    tarik-refresh esok pagi ketika semua sisanya sudah lewat tenggat — kalau
 *    saringannya tetap berlaku sementara chip-nya lenyap, layarnya kosong TANPA
 *    satu pun jalan kembali, dan antrian yang justru paling mendesak terbaca
 *    sebagai antrian yang sudah beres.
 *
 * [nowMillis] dioper, tidak dibaca di dalam, supaya aturannya bisa diuji tanpa
 * menunggu 24 jam — sama seperti `spkGantungRingkas`.
 */
internal fun saringPerGantung(
    groups: List<SpkBatchGroup>,
    nowMillis: Long,
    saring: GantungSaring,
    tenggatMs: Long = GANTUNG_TENGGAT_MS,
): GantungFilterHasil {
    val lewat = groups.filter { grupLewatTenggat(it, nowMillis, tenggatMs) }
    val belum = groups.filterNot { grupLewatTenggat(it, nowMillis, tenggatMs) }
    val bercampur = lewat.isNotEmpty() && belum.isNotEmpty()

    val terlihat = when {
        !bercampur -> groups
        saring == GantungSaring.LEWAT_TENGGAT -> lewat
        saring == GantungSaring.BELUM_TENGGAT -> belum
        else -> groups
    }
    return GantungFilterHasil(
        tampilkanChip = bercampur,
        jumlahLewatTenggat = lewat.size,
        jumlahBelumTenggat = belum.size,
        terlihat = terlihat,
    )
}

/**
 * Label chip BESERTA angkanya, mis. `"Lewat tenggat (4)"`.
 *
 * Angkanya wajib, dengan alasan yang sama seperti [labelChipCabang]: tanpa
 * angka, pekerjaan yang tersaring HILANG dari pandangan alih-alih sekadar
 * tersembunyi. Di layar ini ia juga yang menyambung dengan kartu Activity —
 * "4 lewat tenggat 24 jam" di sana harus bertemu chip "Lewat tenggat (4)"
 * di sini, kalau tidak kasir mengira salah satunya salah hitung.
 */
internal fun labelChipGantung(saring: GantungSaring, hasil: GantungFilterHasil): String = when (saring) {
    GantungSaring.SEMUA -> "${saring.label} (${hasil.jumlahLewatTenggat + hasil.jumlahBelumTenggat})"
    GantungSaring.LEWAT_TENGGAT -> "${saring.label} (${hasil.jumlahLewatTenggat})"
    GantungSaring.BELUM_TENGGAT -> "${saring.label} (${hasil.jumlahBelumTenggat})"
}

/**
 * Baris chip pemilih umur — bentuknya sengaja kembar dengan [PeriodeFilterRow]
 * dan [CabangFilterRow] supaya tiga baris chip di app ini tak terlihat seperti
 * tiga mekanisme berbeda.
 *
 * Bergulir horizontal: chip BERANGKA ("Lewat tenggat (12)") lebih lebar
 * daripada chip periode, dan chip yang terpotong diam-diam menyembunyikan
 * pilihan terakhir.
 */
@Composable
fun GantungFilterRow(
    dipilih: GantungSaring,
    hasil: GantungFilterHasil,
    onPilih: (GantungSaring) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        GantungSaring.entries.forEach { s ->
            val aktif = s == dipilih
            FilterChip(
                selected = aktif,
                onClick = { onPilih(s) },
                label = {
                    Text(
                        labelChipGantung(s, hasil),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (aktif) FontWeight.Bold else FontWeight.Medium,
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        }
    }
}
