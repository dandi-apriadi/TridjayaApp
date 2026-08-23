package com.krisoft.tridjayaelektronik.ui.deliveryflow

import com.krisoft.tridjayaelektronik.data.model.DeliveryJobDto
import com.krisoft.tridjayaelektronik.data.model.DeliveryStatusKey

/**
 * Syarat kirim "Konfirmasi Pembayaran Diterima" (kasir menutup buku satu unit).
 *
 * Fungsi MURNI di file sendiri, pola sama [lokasiBayarKontrol] / `SpkEditFields.kt`
 * — composable-nya cuma memanggil [setoranKasirGate] dan merender hasilnya.
 * Alasannya bukan kerapian: aturan di sini adalah CERMINAN validasi server
 * (`record_kasir_setoran`, inventory-service `delivery.rs`) yang tak punya satu
 * pun pemeriksa kompiler lintas repo, dan pernah menyimpang tanpa terlihat
 * (lihat [SETORAN_NOMINAL_MINIMUM]).
 */

/**
 * Server menolak `nominal_diterima <= 0.0` untuk SEMUA jenis pembayaran sejak
 * 2026-07-28 — bukan cuma COD (dulu dibatasi `driver_terima_uang == true`).
 *
 * **Sejarahnya wajib dibaca sebelum melonggarkan ini lagi.** Klien pernah
 * memakai `>= 0` dengan alasan yang terdengar benar — "kredit tanpa uang muka
 * sah bernominal Rp 0 di titik ini" — dan itu membuat tombolnya AKTIF di Rp 0.
 * Yang membuatnya mahal adalah URUTAN di `DeliveryFlowViewModel.setoranKasir`:
 * fotonya di-upload DULU, baru nominalnya ditolak server. Jadi tiap percobaan
 * meninggalkan foto tanpa induk di `uploads/delivery` sementara kasir cuma
 * melihat "Nominal diterima harus > 0" di layar yang tak menjelaskan apa pun,
 * dan pekerjaannya tak pernah selesai. Nol error di sisi klien: 400 dari server
 * bukan exception, cuma `actionError` merah.
 *
 * Kalau kelak kredit-tanpa-uang-muka memang perlu bernominal Rp 0, yang berubah
 * lebih dulu adalah SERVER — jangan melonggarkan gerbang klien untuk mengejarnya.
 */
const val SETORAN_NOMINAL_MINIMUM = 0.0

/** Apa yang boleh dilakukan tombol + kalimat apa yang ia tulis. */
data class SetoranKasirGate(
    val bolehKirim: Boolean,
    /**
     * Label tombol. Tombol mati TANPA sebab terbaca sebagai app rusak, dan kasir
     * yang tak tahu apa yang kurang akan menekannya berulang kali.
     */
    val label: String,
)

/**
 * [nominalMentah] = digit polos dari `MoneyTextField` (bukan teks berformat).
 *
 * Urutan pemeriksaan sengaja menyebut FOTO lebih dulu: itu langkah yang lebih
 * lama dikerjakan, jadi menagihnya belakangan berarti kasir mengetik nominal,
 * menekan tombol, lalu baru diberi tahu harus memotret.
 */
fun setoranKasirGate(nominalMentah: String, adaFoto: Boolean): SetoranKasirGate {
    val nominal = nominalMentah.toDoubleOrNull()
    val nominalSah = nominal != null && nominal.isFinite() && nominal > SETORAN_NOMINAL_MINIMUM
    return when {
        !adaFoto -> SetoranKasirGate(bolehKirim = false, label = "Ambil foto bukti dulu")
        !nominalSah -> SetoranKasirGate(bolehKirim = false, label = "Isi nominal yang diterima")
        else -> SetoranKasirGate(bolehKirim = true, label = "Konfirmasi Pembayaran")
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Setoran SE-SPK (2026-08-22) — satu foto bukti, satu tombol, nominal per unit.
// ─────────────────────────────────────────────────────────────────────────────

/*
 * KENAPA fan-out klien BOLEH di sini, padahal catatan arsitektur melarangnya di
 * tahap lain.
 *
 * Larangan itu (CLAUDE.md, "Jangan pernah memanggil endpoint tahap dalam loop
 * per unit") berlaku untuk endpoint yang SUDAH fan-out sendiri di server:
 * panggilan ke-2 dijawab 400 "sudah tidak di tahap ini" karena panggilan
 * pertama menutup seluruh batch. `setoran-kasir` bukan salah satunya, dan itu
 * terbaca dari SQL-nya:
 *
 *     UPDATE delivery_jobs SET setoran_kasir_* = … WHERE id = ? AND status = 'delivered'
 *     (inventory-service `delivery/mysql.rs`, `record_kasir_setoran`)
 *
 * Tiga sifat yang membuat loop aman, dan ketiganya harus tetap benar kalau
 * server berubah:
 *  1. **Tak menyentuh `status`** — handler-nya sendiri menyebut dirinya
 *     NON-BLOCKING. Jadi unit ke-2 masih `delivered` saat unit ke-1 selesai.
 *  2. **Tak ada guard `setoran_kasir_at IS NULL`** — mencatat ulang menimpa,
 *     bukan ditolak. Karena itu MENGULANG kiriman yang separuh berhasil aman:
 *     unit yang sudah tercatat cuma tertulis ulang dengan nilai yang sama.
 *  3. **Scope-nya per `id`** — tak ada validasi lintas-unit (tak seperti
 *     `confirm_spk` yang menuntut `units[]` lengkap sebatch), jadi kiriman
 *     separuh bukan keadaan haram di server.
 *
 * Kalau salah satu berubah, yang benar adalah menuntut endpoint se-batch di
 * server — bukan menambal loop ini.
 */

/** Satu unit yang menunggu setoran + nominal mentah yang sedang diketik kasir. */
data class SetoranBaris(
    val id: String,
    /** Digit polos dari `MoneyTextField`, bukan teks berformat. */
    val nominalMentah: String,
)

/** Satu POST siap kirim: `id` job + nominal yang sudah lolos [setoranKasirGate]. */
data class SetoranKiriman(
    val id: String,
    val nominal: Double,
)

/** Apa yang boleh dilakukan tombol se-SPK + kalimat apa yang ia tulis. */
data class SetoranSpkRencana(
    val bolehKirim: Boolean,
    val label: String,
    /**
     * Urutan kiriman = urutan baris di layar. Kosong selama [bolehKirim] false —
     * caller tak perlu memeriksa dua kali.
     */
    val kiriman: List<SetoranKiriman>,
)

/**
 * Unit se-SPK yang masih menunggu setoran, dalam urutan tampil.
 *
 * [semua] = `state.batchUnits` (seluruh unit se-SPK, dimuat `loadBatchUnits`),
 * [dibuka] = job yang sedang dibaca di layar detail.
 *
 * FAIL-SOFT, sengaja seperti [loadBatchUnits] sendiri: `batchUnits` kosong
 * (request-nya gagal / riwayat terpotong) TIDAK boleh berarti "tak ada yang
 * perlu disetor" — layarnya jatuh balik ke satu unit, yaitu perilaku sebelum
 * fitur ini ada. Kasir kehilangan kemudahan, bukan kehilangan jalan kerja.
 *
 * Saringannya cerminan `record_kasir_setoran`: hanya `delivered` yang diterima
 * server, dan unit yang `setoranKasirAt`-nya sudah terisi tak perlu ditawarkan
 * lagi — mencatat ulang memang tidak ditolak server (tak ada guard IS NULL),
 * tapi menimpa setoran orang lain dengan angka yang diketik ulang hari ini.
 */
fun unitMenungguSetoran(
    semua: List<DeliveryJobDto>,
    dibuka: DeliveryJobDto,
): List<DeliveryJobDto> {
    val kandidat = if (semua.any { it.id == dibuka.id }) semua else listOf(dibuka) + semua
    return kandidat
        .distinctBy { it.id }
        .filter {
            it.status == DeliveryStatusKey.DELIVERED &&
                it.setoranKasirAt.isNullOrBlank()
        }
}

/**
 * Gerbang tombol "Konfirmasi Pembayaran" versi se-SPK.
 *
 * Aturan per barisnya TIDAK ditulis ulang di sini — ia memanggil
 * [setoranKasirGate] yang sama, supaya ambang `> 0` cuma hidup di satu tempat
 * (lihat [SETORAN_NOMINAL_MINIMUM] soal kenapa ambang itu mahal untuk
 * menyimpang).
 *
 * Urutan pesan sama alasannya dengan versi satu unit: foto ditagih lebih dulu
 * karena ia langkah yang paling lama, lalu nominal. Bedanya cuma label yang
 * menyebut BERAPA baris yang masih kurang — dengan tiga kolom di layar, "Isi
 * nominal yang diterima" tak memberi tahu kolom mana yang kosong.
 */
fun setoranSpkRencana(baris: List<SetoranBaris>, adaFoto: Boolean): SetoranSpkRencana {
    val tolak = { label: String -> SetoranSpkRencana(bolehKirim = false, label = label, kiriman = emptyList()) }
    if (baris.isEmpty()) return tolak("Tak ada barang menunggu setoran")
    if (!adaFoto) return tolak("Ambil foto bukti dulu")
    val kurang = baris.count { !setoranKasirGate(it.nominalMentah, adaFoto = true).bolehKirim }
    if (kurang > 0) {
        return tolak(if (baris.size == 1) "Isi nominal yang diterima" else "Isi nominal $kurang barang lagi")
    }
    val kiriman = baris.map { SetoranKiriman(it.id, it.nominalMentah.toDouble()) }
    return SetoranSpkRencana(
        bolehKirim = true,
        label = if (kiriman.size == 1) "Konfirmasi Pembayaran" else "Konfirmasi Pembayaran ${kiriman.size} Barang",
        kiriman = kiriman,
    )
}
