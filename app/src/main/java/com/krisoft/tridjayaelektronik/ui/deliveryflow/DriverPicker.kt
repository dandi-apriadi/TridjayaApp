package com.krisoft.tridjayaelektronik.ui.deliveryflow

import com.krisoft.tridjayaelektronik.data.model.DriverDto

/**
 * Aturan daftar driver untuk "Assign Driver + Jadwal", sebagai fungsi MURNI
 * (pola sama `LokasiPembayaran.kt` / `SpkEditFields.kt`) supaya bisa diuji tanpa
 * Compose maupun jaringan.
 *
 * ## Kenapa TIDAK ADA penyaringan region di sini
 *
 * Versi sebelumnya menyaring driver "se-region" dengan menebak region dari nama
 * cabang:
 *
 *     d.cabangName.contains("manado", ignoreCase = true) -> REGION_MANADO
 *     else                                               -> REGION_JAWA
 *
 * Tebakan itu MATI TOTAL sejak migrasi 126 (2026-08-01) memendekkan `cabang.nama`
 * D-06/D-07 jadi "Samrat"/"Bahu" — tak satu pun mengandung kata "manado".
 * `auth-service` menulis ulang `auth_users.cabang_name` dari `cabang.nama` setiap
 * kali user disimpan (`users/service.rs`), jadi bentuk nilainya ikut berubah
 * tanpa ada satu pun migrasi yang menyentuh `auth_users`.
 *
 * Akibatnya di lapangan (dilaporkan DC cabang Manado, 2026-08-27): SPK Samrat/Bahu
 * memberi daftar driver KOSONG lalu memaksa DC mengetik nama + user id driver
 * secara manual. Arah sebaliknya juga salah dan selama ini tak terlihat: karena
 * SEMUA driver jatuh ke cabang `else`, SPK Jawa justru ikut menampilkan driver
 * Manado. Filter itu rusak dua arah, bukan cuma ketat sebelah.
 *
 * Menambal substringnya jadi "samrat"/"bahu" hanya memindahkan tanggal matinya ke
 * penggantian nama cabang berikutnya: `cabang_name` adalah teks TAMPILAN, bukan
 * kunci. Peringatan yang sama sudah tertulis di `LokasiPembayaran.kt`
 * ("teks bebas yang ejaannya menyimpang di produksi").
 *
 * Lagi pula pembatasan regionnya memang SUDAH DICABUT: web menghapusnya
 * 2026-07-24 — `driversForJob` kini cuma MENGELOMPOKKAN (se-cabang vs cabang
 * lain), tak pernah membuang — dan backend `assign_driver` tak pernah menolak
 * lintas region. App ini satu-satunya lapisan yang masih memaksakan aturan yang
 * tak berlaku, dan memaksakannya dengan salah.
 *
 * Penggantinya: TAMPILKAN semua driver, tulis cabang asalnya apa adanya di tiap
 * baris, dan naikkan driver se-cabang ke atas. Lintas cabang jadi pilihan sadar
 * DC — bukan kecelakaan — tanpa pernah menyembunyikan siapa pun.
 */

/**
 * Driver se-cabang dengan SPK-nya — untuk URUTAN tampilan saja, tak pernah untuk
 * membuang.
 *
 * Perbandingannya masih lewat nama (satu-satunya petunjuk cabang yang dibawa
 * `/api/users`; `cabang_id` ada di server tapi butuh master cabang untuk
 * diterjemahkan, dan app ini tak punya kliennya). Itu boleh DI SINI justru
 * karena taruhannya nol: nama yang tak cocok cuma menaruh driver lebih bawah,
 * tak menghilangkannya. Pakai `contains` supaya bentuk panjang lama
 * ("Tridjaya Elektronik Manado Bahu") tetap kena bersama bentuk kanonik ("Bahu").
 */
fun driverSeCabang(driver: DriverDto, kodeDealerJob: String?): Boolean {
    val namaCabangJob = BranchRegions.DEALER_LABEL[kodeDealerJob?.trim()?.uppercase()].orEmpty()
    if (namaCabangJob.isBlank()) return false
    return driver.cabangName.contains(namaCabangJob, ignoreCase = true)
}

/**
 * Driver yang boleh dipilih di form assign: SEMUA driver terdaftar, lintas
 * cabang/region, diurutkan se-cabang dulu lalu menurut nama.
 *
 * Yang dibuang cuma dua hal yang membuat barisnya mustahil dipakai, bukan soal
 * wilayah:
 * - `effectiveId` kosong → tombol "Assign Driver" tetap mati (`driverId` wajib
 *   terisi), jadi barisnya cuma jebakan visual.
 * - `isActive == false` → akunnya tak bisa login, jadi driver itu takkan pernah
 *   bisa menyelesaikan pengiriman yang ditugaskan padanya. Server lama yang tak
 *   mengirim `is_active` ter-default `true` → perilakunya persis seperti dulu.
 */
fun driverBisaDitugaskan(drivers: List<DriverDto>, kodeDealerJob: String?): List<DriverDto> =
    drivers
        .filter { it.effectiveId.isNotBlank() && it.isActive }
        .sortedWith(
            compareByDescending<DriverDto> { driverSeCabang(it, kodeDealerJob) }
                .thenBy { it.name.trim().lowercase() }
        )
