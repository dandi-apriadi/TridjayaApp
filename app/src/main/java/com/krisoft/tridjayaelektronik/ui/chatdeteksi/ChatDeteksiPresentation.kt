package com.krisoft.tridjayaelektronik.ui.chatdeteksi

/**
 * Fungsi murni layar deteksi chat — tanpa Android, tanpa jaringan, supaya
 * seluruh aturan tampil/kirim bisa diuji di JVM.
 *
 * TIDAK ADA `java.time` di berkas ini: `minSdk = 24` tanpa core library
 * desugaring melempar `NoClassDefFoundError` untuk `java.time.*` (lihat
 * catatan yang sama di `ui/chatactivity/ChatActivityPresentation.kt` lama).
 * Layar ini tak butuh tanggal sama sekali (server yang menentukan "hari ini").
 */

/**
 * Batas server (`chat_deteksi::handlers::MAX_VIDEO_BYTES`). Ditegakkan di
 * klien juga supaya karyawan tak menunggu unggahan bermenit-menit di
 * jaringan cabang hanya untuk ditolak 400 di ujung sana.
 *
 * KENAIKAN ANGKA INI DI SERVER BUTUH PERUBAHAN DI SINI JUGA — tak ada satu
 * sumber kebenaran lintas repo untuk angka ini (pola sama masalahnya dengan
 * `MAX_VIDEO_BYTES` lama di `ChatActivityPresentation.kt`).
 */
const val MAX_VIDEO_BYTES: Long = 20L * 1024 * 1024

data class KirimGate(val ok: Boolean, val alasan: String? = null)

fun bolehKirim(adaVideo: Boolean, ukuranBytes: Long): KirimGate {
    if (!adaVideo) return KirimGate(false, "Pilih video bukti dulu.")
    // Hanya yang TERBUKTI kebesaran ditolak — sebagian ContentProvider
    // mengembalikan kolom SIZE null (ukuranBytes 0), dan menolaknya akan
    // mengunci karyawan dari fitur ini tanpa jalan keluar. Server tetap
    // punya batasnya sendiri.
    if (ukuranBytes > MAX_VIDEO_BYTES) {
        return KirimGate(
            false,
            "Video terlalu besar (maks ${MAX_VIDEO_BYTES / (1024 * 1024)} MB). Rekam lebih pendek atau " +
                "turunkan kualitas perekam layar ke 720p.",
        )
    }
    return KirimGate(true)
}

/**
 * Ukuran berkas terpilih untuk ditampilkan. `0` = kolom `SIZE` tak diisi
 * ContentProvider — bukan berkas kosong, dan bukan alasan menolak kirim
 * (lihat [bolehKirim]).
 */
fun formatUkuranBerkas(bytes: Long): String = when {
    bytes <= 0 -> "ukuran tak terbaca"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> String.format(java.util.Locale("in", "ID"), "%.1f MB", bytes / (1024.0 * 1024.0))
}

/** `status` dari `GET /api/chat-deteksi/status` — lihat `chat_deteksi::domain`
 *  di backend untuk daftar nilai yang sah. */
fun statusLabel(status: String): String = when (status) {
    "pending_review" -> "Sedang diproses"
    "lolos_otomatis" -> "Lolos"
    "tidak_lolos_otomatis" -> "Belum memenuhi target"
    else -> "Belum pernah mengirim"
}

/**
 * `true` kalau baris `pending_review` PUNYA `llmError` terisi — berarti
 * panggilan LLM SUDAH GAGAL (timeout/rate-limit/router down), bukan "baru
 * saja dikirim, tinggal tunggu sebentar".
 *
 * **Server BELUM punya worker retry otomatis** (per 2026-08-31) — baris ini
 * akan diam di `pending_review` selamanya sampai ada yang memicu percobaan
 * ulang secara manual. Layar HARUS bilang "gagal, kirim ulang" di keadaan
 * ini, BUKAN "sedang diproses, tunggu saja", supaya karyawan tak menunggu
 * sesuatu yang tak akan pernah selesai sendiri.
 */
fun gagalPerluKirimUlang(status: String, llmError: String?): Boolean =
    status == "pending_review" && !llmError.isNullOrBlank()
