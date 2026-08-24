package com.krisoft.tridjayaelektronik.ui.acinstall

import com.krisoft.tridjayaelektronik.data.model.AcInstallPetugasDto
import com.krisoft.tridjayaelektronik.data.model.AcInstallRespon
import com.krisoft.tridjayaelektronik.data.model.AcInstallStatus
import com.krisoft.tridjayaelektronik.data.model.AcInstallTaskDto

/**
 * Aturan layar "Tugas Pemasangan AC" sebagai fungsi MURNI — pola sama
 * `HomeServicePlan.kt` / `SetoranKasirGate.kt`.
 *
 * Alasannya bukan kerapian: seluruh isi berkas ini adalah CERMINAN aturan yang
 * hidup di repo lain (`inventory-service/src/pemasangan_ac.rs` + migrasi
 * 253/255/256) dan tak punya satu pun pemeriksa kompiler lintas repo. Test di
 * `AcInstallPlanTest` adalah penggantinya.
 */

/**
 * Jabatan yang dianggap petugas pemasangan.
 *
 * **`teknisi` adalah JABATAN (`auth_users.divisi`), BUKAN role — dan itu inti
 * persoalannya.** Migrasi 195 menjadikannya jabatan berkategori `label` dengan
 * `akses_slugs = '[]'`, jadi ia **tidak melipat jadi role apa pun**; kedua
 * teknisi produksi ber-`role = "karyawan"`. Konsekuensinya dua, dan keduanya
 * mudah salah:
 *
 * 1. **Gate berbasis role di sini menyaring NOL orang.** Memakai
 *    `allowedRoles = setOf("karyawan")` akan memunculkan kartunya untuk hampir
 *    seluruh pegawai (sejak migrasi 144), bukan untuk dua teknisi.
 * 2. **Tak ada kunci `GET /api/me/capabilities` yang bisa dicerminkan.**
 *    Endpoint `tugas-saya` LOGIN-ONLY dan self-scoped dengan sengaja: anggota
 *    tim dipilih per-ORANG dari akun aktif, jadi memang tak ada daftar role yang
 *    benar untuk "petugas pemasangan" (keputusan yang sama sudah diambil untuk
 *    `DRIVER_TASKS_ROLES` — "kepemilikan job tak bisa ditebak dari role, dan
 *    menebaknya justru yang melahirkan menu nyasar").
 *
 * Karena itu gate kartunya memakai JABATAN, dan bentuk pencocokannya menyalin
 * migrasi 256 apa adanya: `FIND_IN_SET('teknisi', REPLACE(divisi,' ',''))` —
 * CSV, spasi dibuang, cocok PER-ELEMEN (bukan `contains` atas seluruh string).
 */
internal val JABATAN_PETUGAS_PEMASANGAN = setOf("teknisi")

/**
 * Apakah [divisi] memuat salah satu [JABATAN_PETUGAS_PEMASANGAN].
 *
 * Cerminan `FIND_IN_SET('teknisi', REPLACE(divisi,' ',''))`. Tiga sifat yang
 * ditiru dan tak boleh disederhanakan:
 * - **CSV, bukan nilai tunggal** — satu akun bisa `"sales,teknisi"`.
 * - **Spasi dibuang SEBELUM dipisah**, bukan sesudah: `REPLACE(divisi,' ','')`
 *   di server membuang spasi di SELURUH string, jadi `"sales, teknisi"` cocok.
 * - **Cocok per-elemen, BUKAN `contains`** — `contains("teknisi")` akan ikut
 *   mencocokkan jabatan lain yang kebetulan memuatnya sebagai substring
 *   (mis. `"asisten-teknisi-magang"`), yaitu orang yang picker verifikator
 *   sendiri tidak tawarkan.
 *
 * Perbandingannya case-insensitive walau server tidak: `FIND_IN_SET` di MySQL
 * memakai collation kolom yang `_ci`, jadi server MEMANG tak peduli besar-kecil
 * huruf — menyamakannya di sini justru menjaga keduanya sepakat.
 */
internal fun punyaJabatanPetugasPemasangan(divisi: String?): Boolean {
    if (divisi.isNullOrBlank()) return false
    return divisi
        .replace(" ", "")
        .split(",")
        .any { it.isNotEmpty() && it.lowercase() in JABATAN_PETUGAS_PEMASANGAN }
}

/**
 * Jawaban SAYA atas satu tugas. `null` = belum menjawab — keadaan yang paling
 * sering, karena server tidak membuat baris "menunggu" di muka.
 *
 * Dicari lewat `userId`, BUKAN nama: dua orang bisa bernama mirip, dan nama
 * bukan kunci apa pun di server.
 */
internal fun jawabanSaya(task: AcInstallTaskDto, userId: String?): AcInstallPetugasDto? {
    if (userId.isNullOrBlank()) return null
    return task.petugas.firstOrNull { it.userId == userId }
}

/** Apakah saya termasuk orang yang ditugaskan di tugas ini. */
internal fun sayaDitugaskan(task: AcInstallTaskDto, userId: String?): Boolean =
    jawabanSaya(task, userId) != null

/**
 * Tugas yang MASIH menunggu jawaban saya — dasar angka lencana kartu Activity.
 *
 * Sengaja BUKAN `daftar.size`: tugas yang sudah saya terima tetap ada di daftar
 * (saya memang harus mengerjakannya), tapi ia bukan lagi sesuatu yang menuntut
 * tindakan saya sekarang. Lencana yang menghitung seluruh daftar tak pernah
 * turun ke nol dan berhenti dibaca.
 */
internal fun butuhJawabanSaya(daftar: List<AcInstallTaskDto>, userId: String?): Int =
    daftar.count { jawabanSaya(it, userId)?.status.isNullOrBlank() }

/** Alasan penolakan minimum — server hanya menuntut "tidak kosong". */
internal const val ALASAN_TOLAK_MIN = 1

/**
 * Gerbang tombol Tolak. Menolak WAJIB beralasan (`tolak_handler`), dan tombol
 * yang aktif di alasan kosong berarti petugas menekan lalu menerima 400 yang
 * tak menjelaskan apa-apa.
 */
internal fun bolehTolak(alasan: String): Boolean = alasan.trim().length >= ALASAN_TOLAK_MIN

/**
 * Apakah tugas ini masih boleh disentuh petugas.
 *
 * `selesai`/`dibatalkan` = beku (`transisi::boleh_ubah_foto` dan aturan respon
 * di server). Tugas yang sudah beku BISA muncul di layar kalau daftar dimuat
 * dengan `?status` eksplisit atau kalau verifikator menutupnya sementara layar
 * terbuka — menampilkan tombol yang pasti dijawab 4xx adalah kegagalan yang
 * bisa dicegah tanpa satu pun request.
 */
internal fun bolehDijawab(task: AcInstallTaskDto): Boolean =
    task.status != AcInstallStatus.SELESAI && task.status != AcInstallStatus.DIBATALKAN

/** Label jawaban untuk ditampilkan; `null` = belum menjawab. */
internal fun labelRespon(status: String?): String? = when (status) {
    AcInstallRespon.DITERIMA -> "Diterima"
    AcInstallRespon.DITOLAK -> "Ditolak"
    else -> null
}

/**
 * Baris "kapan" untuk kartu tugas — tanggal + jam apa adanya dari server.
 *
 * **Tidak diformat ulang dan tidak di-parse jadi tanggal.** Dua alasan yang
 * berdiri sendiri: (a) `java.time` haram di `app/src/main` (minSdk 24), dan
 * (b) jamnya WIB apa adanya seperti `jadwalAt` di Home Service — mengubahnya
 * jadi objek waktu lalu memformat ulang membuka pintu pergeseran zona untuk
 * angka yang seharusnya cuma disalin ke layar.
 */
internal fun labelJadwal(task: AcInstallTaskDto): String? {
    val tanggal = task.jadwalTanggal?.trim().orEmpty()
    val jam = task.jadwalJam?.trim().orEmpty()
    return when {
        tanggal.isEmpty() && jam.isEmpty() -> null
        jam.isEmpty() -> tanggal
        tanggal.isEmpty() -> jam
        else -> "$tanggal · $jam"
    }
}

/**
 * Alamat pemasangan yang dipakai layar.
 *
 * `alamatPemasangan` boleh kosong — pengaju tak selalu mengisinya, dan pada SPK
 * yang dipasang di rumah konsumen alamat yang benar memang alamat SPK. Jatuh ke
 * `spk.customerAddress` supaya petugas tak melihat baris alamat kosong pada
 * tugas yang alamatnya sebenarnya diketahui.
 */
internal fun alamatEfektif(task: AcInstallTaskDto): String? =
    task.alamatPemasangan?.trim()?.takeIf { it.isNotEmpty() }
        ?: task.spk.customerAddress?.trim()?.takeIf { it.isNotEmpty() }

/** Nama kontak di lokasi; jatuh ke nama konsumen SPK dengan alasan sama dengan [alamatEfektif]. */
internal fun kontakNamaEfektif(task: AcInstallTaskDto): String? =
    task.kontakNama?.trim()?.takeIf { it.isNotEmpty() }
        ?: task.spk.customerName?.trim()?.takeIf { it.isNotEmpty() }

/** Nomor kontak di lokasi; jatuh ke HP konsumen SPK. */
internal fun kontakHpEfektif(task: AcInstallTaskDto): String? =
    task.kontakHp?.trim()?.takeIf { it.isNotEmpty() }
        ?: task.spk.customerPhone?.trim()?.takeIf { it.isNotEmpty() }
