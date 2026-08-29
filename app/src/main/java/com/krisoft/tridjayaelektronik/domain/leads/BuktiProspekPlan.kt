package com.krisoft.tridjayaelektronik.domain.leads

/**
 * Aturan murni bukti prospek — siapa yang WAJIB melampirkannya, batas ukuran, dan
 * keputusan penyusutan sebelum unggah. Dipisah dari ViewModel supaya bisa diuji
 * tanpa perangkat, dan dari layar supaya kontrak lintas-repo di bawah terbaca utuh.
 *
 * ## Kenapa gambar SELALU dikodekan ulang (WebP sejak 2026-08-29, dulu JPEG)
 *
 * `POST /prospek-harian/bukti` memvalidasi **ekstensi × content-type × magic byte
 * SERENTAK** (`prospek.rs` → `is_valid_raport_evidence_content`). Pasangan yang
 * meleset ditolak 400 SESUDAH seluruh berkas terkirim. Klien ini mengirim nama
 * berkas `.webp` dengan `image/webp` (lihat `CrmRepository.uploadBuktiProspek`),
 * jadi PNG/JPEG apa adanya dari galeri — bentuk paling lazim tangkapan layar
 * percakapan — akan ditolak isinya walau ukurannya sah. Mengodekan ulang membuat
 * ketiganya sepakat dengan sendirinya, sekaligus memangkas foto kamera yang
 * melewati batas 8 MB. Sebelum 2026-08-29 ini keliru JPEG (`AddLeadViewModel.siapkanJpeg`
 * mengembalikan bytes JPEG memakai nama+content-type `.webp`) — bug hidup yang membuat
 * SETIAP upload di layar ini ditolak 400, diperbaiki bersamaan migrasi ke `ImagePixelPipeline`.
 */

/**
 * ## Akibat yang diketahui: trainee TIDAK bisa menyimpan prospek saat offline
 *
 * Buktinya diunggah SEKARANG (grant `Uri` dari Photo Picker tak persistable),
 * jadi tanpa jaringan tak ada `buktiUrl`, dan tanpa `buktiUrl` prospek trainee
 * ditolak validasi sebelum masuk antrean. Peran lain tak terpengaruh — bagi
 * mereka bukti opsional dan alur "Antre" tetap utuh.
 *
 * Ini dipilih SADAR, bukan kelalaian. Dua alternatifnya lebih buruk:
 * membolehkan antre tanpa bukti berarti barisnya dijawab 400 selamanya begitu
 * saklar `prospek_bukti_wajib` menyala — gagal senyap sambil berlabel "Antre",
 * kelas kegagalan yang sudah dijelaskan panjang di `ProspekNomor.kt`. Menahan
 * gambarnya di penyimpanan lokal lalu mengunggah saat sinkron memang jalan
 * keluar yang benar, tapi ia menuntut antrean berkas tersendiri (salin ke
 * `filesDir`, status per berkas, coba-ulang) — pekerjaan tersendiri, bukan
 * tempelan di sini. Sampai itu ada, kegagalannya SEGERA DAN TERLIHAT di layar,
 * bukan tertunda dan tak terbaca.
 */

/**
 * Cerminan `MAX_BUKTI_PROSPEK_BYTES` di `kinerja-service/src/prospek.rs`.
 *
 * SENGAJA jauh lebih kecil dari bukti raport (30 MB): yang diminta tangkapan
 * layar percakapan, bukan video.
 */
internal const val MAX_BUKTI_PROSPEK_BYTES = 8L * 1024 * 1024

/**
 * Batas berkas MASUKAN — diperiksa sebelum dekode, bukan sesudah.
 *
 * Lebih longgar dari [MAX_BUKTI_PROSPEK_BYTES] karena yang dikirim adalah hasil
 * kode ulang, bukan berkas aslinya: PNG 12 MB dari galeri lazimnya jadi JPEG
 * ratusan KB. Yang dijaga di sini murni ongkos dekode — bitmap sebesar itu
 * mengancam OOM di HP kelas bawah, dan menolaknya lebih jujur daripada app
 * tertutup tanpa pesan.
 */
internal const val MAX_BUKTI_INPUT_BYTES = 25L * 1024 * 1024

/**
 * Sisi terpanjang maksimum sesudah penyusutan.
 *
 * SENGAJA 2560, bukan 1600 seperti bukti absen/indent. Yang diunggah di sini
 * adalah TANGKAPAN LAYAR PERCAKAPAN, dan nilainya ada pada teks yang terbaca.
 * Layar 1080×2400 (paling lazim di lapangan) lolos tanpa disusutkan sama sekali
 * pada angka ini; pada 1600 ia menyusut ~33% dan percakapannya jadi buram —
 * bukti yang tak terbaca sama saja dengan tak ada bukti saat mentor menilai.
 */
internal const val MAX_BUKTI_DIMENSI = 2560

/**
 * Peran yang WAJIB melampirkan bukti — cerminan `wajib_bukti_prospek` di
 * `packages/rust-shared/src/bukti.rs`.
 *
 * **Dinilai dari peran EFEKTIF**, bukan role primary. Web menilai
 * `user?.role === 'trainee'` saja (`ProspekSubmitForm.tsx`); itu lebih longgar
 * dari servernya, jadi trainee yang membawa role kedua akan lolos form lalu
 * ditolak 400 begitu saklarnya menyala. Di sini sengaja disamakan dengan server.
 */
internal fun wajibBuktiProspek(peran: List<String>): Boolean =
    peran.any { it.trim().lowercase() == "trainee" }

/** Gabungan role primary + `roles[]`, dibersihkan. Kosong dan duplikat dibuang. */
internal fun peranEfektif(role: String?, roles: List<String>): List<String> =
    (listOf(role.orEmpty()) + roles).map { it.trim() }.filter { it.isNotEmpty() }.distinct()

/**
 * Pesan penolakan berkas masukan, atau `null` kalau boleh lanjut.
 *
 * Ukuran 0 SENGAJA diloloskan: penyedia galeri boleh tak melaporkan `SIZE`, dan
 * "tak terbaca" bukan "kebesaran" — kegagalan sesungguhnya akan muncul saat
 * dekode, dengan pesan yang benar.
 */
internal fun masalahUkuranBukti(bytes: Long): String? {
    if (bytes <= MAX_BUKTI_INPUT_BYTES) return null
    // MB dibulatkan KE ATAS dengan aritmetika bulat, bukan `String.format("%.1f")`:
    // pemformat itu memakai locale default, jadi angkanya berubah bentuk antara
    // HP berbahasa Indonesia (koma) dan JVM test (titik) — pesan yang benar tapi
    // tesnya rapuh. Membulatkan ke bawah juga salah: berkas 25,4 MB akan berbunyi
    // "maksimal 25 MB — berkas ini 25 MB", yang terbaca seperti app-nya yang keliru.
    val mb = (bytes + 1024 * 1024 - 1) / (1024 * 1024)
    val batas = MAX_BUKTI_INPUT_BYTES / (1024 * 1024)
    return "Ukuran gambar maksimal $batas MB — berkas ini $mb MB. " +
        "Pakai tangkapan layar percakapan, bukan foto asli."
}
