package com.krisoft.tridjayaelektronik.ui.aktivitas

/**
 * Aturan murni bukti Input Aktivitas — bentuk `evidenceUrl`, batas jumlah/ukuran,
 * dan pasangan ekstensi↔MIME video. Dipisah dari [AktivitasPlan] (yang isinya
 * pencocokan aktivitas) supaya kontrak lintas-repo di bawah terbaca utuh, dan
 * dipisah dari ViewModel supaya bisa diuji tanpa perangkat.
 *
 * ## Kenapa satu gambar TIDAK dibungkus array
 *
 * Server menyajikan bukti lewat `GET /api/raport-harian/evidence/{berkas}`, dan
 * untuk yang TIDAK punya role lihat-semua ia mencari pemiliknya begini
 * (`kinerja-service/src/raport/mysql.rs:163`):
 *
 * ```rust
 * let url = format!("/uploads/raport/{filename}");
 * sqlx::query_scalar("SELECT karyawan_id FROM raport_harian WHERE bukti_url = ? LIMIT 1")
 * ```
 *
 * Pencocokannya PERSIS. Baris yang menyimpan `["/uploads/raport/a.jpg"]` tak
 * akan pernah cocok → `None` → handler menjawab **404**. Yang kena: karyawan
 * membuka buktinya SENDIRI, dan `kepala-cabang` (peninjau berbatas cabang).
 * Yang aman: `pic_raport`, `hrd`, `admin`, `owner`, `manager` (jalur
 * lihat-semua, tak lewat lookup itu).
 *
 * Web SELALU membungkus array untuk mode image
 * (`KaryawanAktivitasPage.tsx:532`), jadi baris multi-gambar dari web sudah lama
 * terkena hal ini. Kalau app ikut membungkus, jalur PALING RAMAI — satu foto
 * kamera — ikut rusak, dan gejalanya "bukti saya hilang setelah update
 * aplikasi" tanpa satu pun error di server. Karena itu: 1 URL → polos, ≥2 →
 * array. Hanya baris yang memang multi-gambar yang masuk kelas bug yang SUDAH
 * ada hari ini; tidak ada penurunan dari perilaku sekarang.
 *
 * Kalau `karyawan_id_for_evidence` diperbaiki di server (JSON_CONTAINS/LIKE/
 * tabel anak), aturan ini bisa dibalik dengan mengubah [buildEvidenceUrl] saja.
 */

/**
 * Cerminan `MAX_IMAGE_FILES` di `frontend/src/pages/dashboard/KaryawanAktivitasPage.tsx`.
 *
 * Server TIDAK membatasi jumlah gambar sama sekali; pagar terakhirnya cuma
 * kolom `raport_harian.bukti_url` bertipe `text` (65.535 byte), yang kalau
 * jebol muncul sebagai 500 tanpa pesan berguna. Jadi batas ini satu-satunya
 * penjaga nyata — kalau angkanya berubah di web, ubah di sini juga.
 *
 * **Dinaikkan 6 → 10 pada 2026-08-16** atas permintaan user, setelah karyawan
 * melaporkan tak bisa melampirkan bukti secukupnya. Katalog aktivitas produksi
 * penuh target berjumlah SEPULUH ("FOTO DENGAN KONSUMEN MINIMAL 10", "UPDATE
 * POSTING DI MEDSOS … MINIMAL 10 POSTINGAN", "UPDATE STATUS WA MINIMAL 10
 * PRODUK"), jadi batas 6 memotong bukti pekerjaan yang sudah benar-benar
 * selesai. Jaraknya ke pagar teknis masih jauh: 10 URL + pembungkus JSON
 * ≈ 700 byte dari 65.535.
 */
internal const val MAX_GAMBAR = 10

/** Cerminan `MAX_IMAGE_INPUT_SIZE` web (25 MB, diukur SEBELUM watermark/kompresi). */
internal const val MAX_GAMBAR_INPUT_BYTES = 25L * 1024 * 1024

/**
 * Cerminan `MAX_EVIDENCE_BYTES` di `kinerja-service/src/raport.rs:14`.
 *
 * SENGAJA bernama beda dari `MAX_VIDEO_BYTES` milik bukti chat (50 MB) supaya
 * auto-import tak menyeret angka yang salah: keduanya endpoint berbeda dengan
 * batas berbeda.
 */
internal const val MAX_VIDEO_BUKTI_BYTES = 30L * 1024 * 1024

/** Hasil pemeriksaan sebelum unggah. [alasan] null saat [ok]. */
internal data class BuktiGate(val ok: Boolean, val alasan: String? = null)

/**
 * Kebalikan [parseEvidenceUrls]. Bentuk keluarannya WAJIB bisa dibaca ulang
 * oleh parser itu: kutip ganda, tanpa spasi, tanpa escape.
 *
 * - `image`: 1 URL → polos, ≥2 → JSON array (alasan panjang di KDoc berkas)
 * - `video`: selalu polos — web pun begitu (`KaryawanAktivitasPage.tsx:545`)
 * - `none` : selalu null; server menolak `none` yang membawa evidenceUrl
 *   (`raport/service.rs:248`)
 */
internal fun buildEvidenceUrl(mode: String, urls: List<String>): String? {
    val bersih = urls.map { it.trim() }.filter { it.isNotEmpty() }
    return when (mode) {
        "image" -> when {
            bersih.isEmpty() -> null
            bersih.size == 1 -> bersih.first()
            else -> bersih.joinToString(separator = ",", prefix = "[", postfix = "]") { "\"$it\"" }
        }
        "video" -> bersih.firstOrNull()
        else -> null
    }
}

/**
 * Gerbang sebelum satu byte pun bergerak. Semua aturan hidup di sini, bukan di
 * `enabled` tombol — kalau tidak, aturan yang sama akan ditulis dua kali dan
 * salah satunya basi.
 *
 * [ukuranVideoBytes] `0` berarti kolom `SIZE` tak terbaca dari ContentResolver
 * (normal untuk sebagian penyedia), BUKAN "video kosong" — jadi hanya yang
 * TERBUKTI kebesaran yang ditolak. Server tetap punya kata akhir.
 */
internal fun gateKirimBukti(
    jumlahGambar: Int,
    adaVideo: Boolean,
    ukuranVideoBytes: Long,
): BuktiGate = when {
    adaVideo && jumlahGambar > 0 ->
        BuktiGate(false, "Satu aktivitas hanya boleh foto ATAU video, tidak keduanya.")

    !adaVideo && jumlahGambar == 0 ->
        BuktiGate(false, "Pilih bukti dulu (kamera, galeri, atau video).")

    jumlahGambar > MAX_GAMBAR ->
        BuktiGate(false, "Maksimal $MAX_GAMBAR gambar per aktivitas.")

    ukuranVideoBytes > MAX_VIDEO_BUKTI_BYTES ->
        BuktiGate(
            false,
            "Video terlalu besar (maks ${MAX_VIDEO_BUKTI_BYTES / (1024 * 1024)} MB). " +
                "Potong videonya atau turunkan kualitas perekam ke 720p.",
        )

    else -> BuktiGate(true)
}

/**
 * Ekstensi video yang server terima, atau null kalau tak dikenal.
 *
 * `when` Kotlin murni, BUKAN `MimeTypeMap`: kelas itu melempar
 * `RuntimeException("Stub!")` di unit test JVM, dan tabelnya milik ROM sehingga
 * jawabannya bisa berbeda antar-HP. Daftarnya = irisan video dari
 * `is_valid_raport_evidence_content` (`raport/domain.rs:188-195`).
 *
 * Nama berkas didahulukan atas MIME karena penyedia galeri kadang menjawab
 * `video/mp4` untuk berkas `.mov` — dan server memvalidasi ekstensi × MIME ×
 * magic bytes SERENTAK, jadi pasangan yang meleset ditolak 400 SETELAH seluruh
 * berkas terunggah.
 */
internal fun ekstensiVideo(namaAsli: String?, mime: String?): String? {
    val dariNama = namaAsli?.substringAfterLast('.', "")?.lowercase()
    if (dariNama != null && dariNama in setOf("mp4", "webm", "mov")) return dariNama
    return when (mime?.substringBefore(';')?.trim()?.lowercase()) {
        "video/mp4" -> "mp4"
        "video/webm" -> "webm"
        "video/quicktime" -> "mov"
        else -> null
    }
}

/** Pasangan MIME yang divalidasi silang server bersama magic bytes. */
internal fun mimeVideo(ext: String): String = when (ext) {
    "webm" -> "video/webm"
    "mov" -> "video/quicktime"
    else -> "video/mp4"
}

/**
 * Keluaran `PhotoWatermark.prepareWatermarkedJpeg` SELALU WEBP apa pun format
 * sumbernya (2026-08-28, sebelumnya JPEG), jadi ekstensi & MIME dipaku
 * `.webp`/`image/webp` — termasuk untuk PNG/JPEG yang dipilih dari galeri.
 * Server (`is_valid_raport_evidence_content`) memvalidasi ekstensi & MIME
 * HARUS cocok — ubah salah satu tanpa yang lain akan menolak upload.
 */
internal fun namaBerkasGambar(urutan: Int, millis: Long): String = "raport_${millis}_$urutan.webp"

internal fun namaBerkasVideo(ext: String, millis: Long): String = "raport_$millis.$ext"

/**
 * Judul watermark. Galeri DIBEDAKAN supaya PIC penilai tahu buktinya bukan
 * jepretan saat itu juga: stempel jam di bar watermark adalah jam PROSES, bukan
 * jam foto diambil, jadi tanpa label ini foto tahun lalu terlihat seperti foto
 * hari ini.
 */
internal fun watermarkTitleBukti(dariGaleri: Boolean): String =
    if (dariGaleri) "TRIDJAYA · AKTIVITAS (GALERI)" else "TRIDJAYA · AKTIVITAS"

/**
 * Gabung bukti LAMA (sudah di server) dengan yang baru, dipotong di [MAX_GAMBAR].
 *
 * Urutan lama-dulu disengaja: server melakukan upsert dan MENIMPA `bukti_url`
 * seluruhnya, jadi mengirim hanya berkas baru = menghapus bukti lama tanpa satu
 * pun error. Kalau batasnya terlampaui, yang BARU yang dibuang — bukti yang
 * sudah tersimpan tak boleh hilang gara-gara penambahan.
 */
internal fun gabungBukti(lama: List<String>, baru: List<String>): List<String> =
    (lama + baru).distinct().take(MAX_GAMBAR)

/** Ukuran berkas untuk ditampilkan ke user, mis. "12,4 MB". */
internal fun formatUkuranBerkas(bytes: Long): String = when {
    bytes <= 0L -> "ukuran tak diketahui"
    bytes >= 1024L * 1024L -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
    else -> "${(bytes + 1023) / 1024} KB"
}

/**
 * Pesan gagal dekode. Jalur galeri butuh pesan sendiri: `BitmapFactory` baru
 * bisa HEIF di API 28 sedangkan minSdk repo ini 24, jadi HEIC dari iPhone/HP
 * baru gagal senyap di Android 7/8 dan "coba jepret ulang" jadi saran yang
 * tidak nyambung sama sekali.
 *
 * [sebab] = nama kelas pengecualian (`e.javaClass.simpleName`), ditempelkan di
 * ekor kalau ada. Bukan hiasan: `FileNotFoundException` (berkas cloud yang
 * belum terunduh), `SecurityException` (grant Uri sudah dicabut), dan
 * `IOException` (unduhan Google Foto gagal di tengah) adalah tiga masalah
 * BERBEDA dengan jalan keluar berbeda, dan tanpa ekor ini ketiganya terbaca
 * sebagai satu kalimat tentang HEIC yang cuma benar untuk salah satunya.
 */
internal fun pesanGagalDekode(dariGaleri: Boolean, sebab: String? = null): String {
    val inti = if (dariGaleri) {
        "Format foto ini tidak didukung HP kamu (mis. HEIC). Buka di Galeri, " +
            "simpan/bagikan sebagai JPG, lalu pilih lagi."
    } else {
        "Foto gagal diproses, coba jepret ulang"
    }
    return sebab?.takeIf { it.isNotBlank() }?.let { "$inti ($it)" } ?: inti
}

/**
 * Kalimat untuk gambar yang TIDAK jadi masuk staging, dipisah per SEBAB.
 * `null` = tak ada yang diabaikan (jangan tampilkan apa-apa).
 *
 * ## Kenapa penghitungnya dipisah
 *
 * Sampai vc116 ketiga sebab menaikkan SATU penghitung `gagal`, lalu dirender
 * dengan satu kalimat tetap: *"$n gambar diabaikan — maksimal
 * [MAX_GAMBAR] gambar per aktivitas."* Kuota adalah sebab yang paling jarang
 * dan paling mudah dibantah pengguna: seseorang yang memilih 2 gambar dan
 * melihat "maksimal 10" tahu persis bahwa penjelasannya salah, lalu berhenti
 * mempercayai pesan berikutnya juga.
 *
 * Yang sebenarnya menyala hampir selalu [takTerbaca]. Ambang [terlaluBesar]
 * 25 MB praktis tak pernah tertembus foto HP (foto 108 MP JPEG ≈ 8-15 MB), jadi
 * cabang itu pun jarang — sementara berkas cloud yang gagal diunduh dan HEIC
 * yang tak terdekode adalah kejadian sehari-hari.
 *
 * Ketiganya sengaja ditulis sebagai kalimat terpisah, bukan digabung jadi satu
 * angka: satu pemilihan bisa memuat ketiganya sekaligus, dan penjumlahan
 * menghapus justru keterangan yang menentukan langkah pengguna berikutnya.
 */
internal fun pesanGambarDiabaikan(
    takMuat: Int,
    terlaluBesar: Int,
    takTerbaca: Int,
    sebabTakTerbaca: String? = null,
): String? {
    val kalimat = buildList {
        if (takMuat > 0) {
            add("$takMuat gambar tidak ditambahkan — maksimal $MAX_GAMBAR gambar per aktivitas.")
        }
        if (terlaluBesar > 0) {
            add(
                "$terlaluBesar gambar dilewati karena lebih besar dari " +
                    "${formatUkuranBerkas(MAX_GAMBAR_INPUT_BYTES)}.",
            )
        }
        if (takTerbaca > 0) {
            add("$takTerbaca gambar tidak bisa dibaca. ${pesanGagalDekode(true, sebabTakTerbaca)}")
        }
    }
    return kalimat.takeIf { it.isNotEmpty() }?.joinToString(" ")
}

// ── Gerbang hari Minggu (2026-08-16) ─────────────────────────────────────────

/**
 * Pesan penolakan hari Minggu — SALINAN PERSIS milik server
 * (`kinerja-service/src/raport/service.rs`). Sengaja identik supaya orang tak
 * membaca dua kalimat berbeda untuk satu aturan yang sama.
 */
internal const val PESAN_HARI_MINGGU = "Hari Minggu tidak wajib mengisi laporan raport."

/**
 * Hari Minggu menurut jam PERANGKAT.
 *
 * `Calendar`, BUKAN `java.time`: `java.time` haram di `app/src/main` (minSdk 24
 * tanpa desugaring — lihat CLAUDE.md), dan pelanggarannya tak ketahuan sampai
 * HP Android 7 di lapangan melempar `NoClassDefFoundError`.
 *
 * Zona waktu dibiarkan default perangkat. Server memutuskan dengan
 * `chrono::Local` di VPS (WIB) dan seluruh armada HP ada di WIB juga, jadi
 * keduanya sepakat; kalau kelak ada perangkat berzona lain, servernya tetap
 * yang berkuasa — gerbang ini cuma mendahulukan kabarnya, tak menggantikannya.
 */
internal fun hariMinggu(millis: Long): Boolean {
    val kalender = java.util.Calendar.getInstance()
    kalender.timeInMillis = millis
    return kalender.get(java.util.Calendar.DAY_OF_WEEK) == java.util.Calendar.SUNDAY
}

/**
 * Boleh mengirim bukti hari ini?
 *
 * KENAPA ADA: server MENOLAK `POST /raport-harian` pada hari Minggu, tapi
 * `POST /raport-harian/upload` (di biner yang beredar saat ini) TIDAK — dan app
 * mengunggah SELURUH berkas dulu baru mengirim barisnya. Hasilnya, Minggu 16
 * Agustus 2026: **36 berkas terunggah ke server dan NOL baris raport tercatat**;
 * seluruhnya jadi berkas yatim, dan orangnya cuma melihat penolakan setelah
 * menunggu semua unggahan selesai. Web sudah lama menutupnya di klien
 * (`isSundayReportDay` di `KaryawanAktivitasPage.tsx`); app-lah yang tak punya
 * cerminannya.
 *
 * Ini MENDAHULUKAN kabar, bukan menambah aturan baru: yang menolak tetap server.
 */
internal fun gerbangHariIni(millis: Long): BuktiGate =
    if (hariMinggu(millis)) BuktiGate(false, PESAN_HARI_MINGGU) else BuktiGate(true, null)

// ── Gerbang "sudah disetujui PIC" (2026-08-16) ───────────────────────────────

/**
 * Kalimat untuk aktivitas yang sudah disetujui PIC. Seirama dengan
 * `pesan_raport_terkunci` milik server (`kinerja-service/src/raport/domain.rs`)
 * — dipendekkan untuk layar HP, tapi menyebut jalan keluar yang SAMA, karena
 * dua kalimat berbeda untuk satu aturan terbaca sebagai dua aturan.
 */
internal const val PESAN_TERKUNCI_PIC =
    "Aktivitas ini sudah disetujui PIC, jadi buktinya dikunci. Minta PIC Aktivitas " +
        "mengembalikannya ke status Menunggu dulu kalau isinya perlu diperbaiki."

/**
 * Aktivitas yang buktinya TIDAK BISA diganti lagi.
 *
 * Server menolak penimpaan baris ber-`review_status = 'approved'`
 * (`boleh_timpa_raport`) karena menimpanya menghapus nilai dan komentar PIC.
 * App tak pernah mencerminkannya: tombolnya tetap hidup, orangnya memotret,
 * MENUNGGU SELURUH UNGGAHAN SELESAI, lalu ditolak — dan yang terbaca olehnya
 * adalah "buktinya tidak bisa dikirim". Dengan batas 10 gambar, ongkos salah
 * paham itu sepuluh kali unggahan.
 *
 * Status apa pun selain `approved` (termasuk null = belum pernah dikirim, dan
 * `rejected` yang justru MEMANG boleh dikirim ulang) tidak dikunci.
 */
internal fun terkunciPic(reviewStatus: String?): Boolean =
    reviewStatus?.trim()?.equals("approved", ignoreCase = true) == true

/**
 * `true` = server MENOLAK isinya, bukan gagal menyampaikannya — jadi menekan
 * tombol yang sama lagi menghasilkan jawaban yang sama persis.
 *
 * ## Kenapa fungsi sekecil ini punya nama dan test sendiri
 *
 * Sampai vc97, `AktivitasViewModel` menempelkan ekor
 * `Tekan "Kirim bukti" lagi untuk melanjutkan dari gambar ini.` ke SETIAP
 * kegagalan unggah, termasuk 400 permanen. Karyawan lalu membaca dua perintah
 * yang bertabrakan — pesan server ("GANTI dengan foto baru") diikuti perintah
 * app ("tekan lagi") — dan menuruti yang terakhir.
 *
 * Terukur di produksi 2026-08-21: tiga orang (Tresandila, Dita, Mamun) menekan
 * ulang 10-13 kali dalam setengah menit atas foto yang sama, tiap kali dijawab
 * 400 identik oleh gerbang bukti-lintas-hari. Seorang lagi (Agus) berhenti
 * setelah sekali — dia yang membaca kalimat servernya, bukan ekornya.
 *
 * ## Kenapa HANYA 400 dan 422
 *
 * - `401`/`403` — ditangani jalur sesi/izin, bukan di sini.
 * - `408`/`429`/`5xx` — memang layak diulang.
 * - kegagalan jaringan datang TANPA `httpStatus` (`null`).
 *
 * Defaultnya karena itu "boleh coba lagi", dan itu arah yang aman: salah
 * menebak permanen sebagai sementara cuma menyisakan satu tombol yang tak
 * menolong; salah menebak sementara sebagai permanen menyuruh orang menyerah
 * atas kegagalan yang sebenarnya sembuh sendiri.
 */
internal fun gagalPermanen(httpStatus: Int?): Boolean = httpStatus == 400 || httpStatus == 422
