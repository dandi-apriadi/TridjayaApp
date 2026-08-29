package com.krisoft.tridjayaelektronik.domain.media

/**
 * Matematika murni di belakang [util.VideoTranscoder] — target dimensi sesudah transcode video
 * bukti Input Aktivitas (>30 MB) sebelum diunggah.
 *
 * Cermin SENGAJA dari server (`kinerja-service/src/aktivitas_harian/video_compress.rs`, filter
 * ffmpeg `scale='min(1280,iw)':-2`): mengecilkan LEBAR ke `min(maxWidth, iw)`, BUKAN mencap sisi
 * terpanjang secara umum. Video PORTRAIT dengan lebar ≤ [maxWidth] (kasus paling lazim rekaman HP
 * dipegang tegak) TIDAK disusutkan sama sekali oleh aturan ini — itu bukan bug, itu paritas: kalau
 * server tak menyusutkannya, klien pun tak boleh, supaya "video sudah dikompres server" dan
 * "video sudah dikompres klien" berakhir di dimensi yang sama untuk sumber yang sama.
 */
internal fun targetDimensions(sourceWidth: Int, sourceHeight: Int, maxWidth: Int = 1280): Pair<Int, Int> {
    if (sourceWidth <= 0 || sourceHeight <= 0 || maxWidth <= 0) return sourceWidth to sourceHeight
    if (sourceWidth <= maxWidth) return evenFloor(sourceWidth) to evenFloor(sourceHeight)
    val scale = maxWidth.toFloat() / sourceWidth
    return evenFloor(maxWidth) to evenFloor((sourceHeight * scale).toInt().coerceAtLeast(2))
}

/**
 * Sebagian besar encoder H.264 (termasuk MediaCodec hardware Android) MENSYARATKAN lebar & tinggi
 * genap untuk profil YUV 4:2:0 yang umum dipakai — dimensi ganjil bisa gagal konfigurasi encoder
 * atau menghasilkan artefak chroma di baris/kolom terakhir. Membulatkan KE BAWAH (bukan ke atas)
 * supaya hasilnya tak pernah melebihi [targetDimensions] yang diminta.
 */
private fun evenFloor(v: Int): Int = if (v % 2 == 0) v else v - 1
