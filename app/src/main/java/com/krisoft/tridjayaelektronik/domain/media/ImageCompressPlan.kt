package com.krisoft.tridjayaelektronik.domain.media

import androidx.exifinterface.media.ExifInterface
import kotlin.math.max

/**
 * Matematika murni di belakang pipa kompresi gambar bersama ([util.ImagePixelPipeline]).
 * Dipisah dari situ karena inilah satu-satunya bagian dari jalur dekode yang bisa salah TANPA
 * gejala — nilai keliru tak melempar apa pun, ia diam-diam memboroskan memori atau membuang
 * detail yang justru jadi isi buktinya. `Bitmap`/`BitmapFactory`/`ExifInterface` sendiri adalah
 * stub di unit test JVM (tak ada Robolectric di repo ini), jadi fungsi yang MENYENTUHNYA harus
 * diuji lewat guard test pemindai sumber; yang TIDAK menyentuhnya — di sini — diuji sungguhan.
 *
 * Diekstrak dari `util/PhotoWatermark.kt` (`olahPiksel`) dan `domain/leads/BuktiProspekPlan.kt`
 * (`sampleSizeUntuk`, duplikat nyaris identik) supaya empat pemanggil (`PhotoWatermark`,
 * `IndentCreateViewModel`, `AddLeadViewModel`, `EventViewModel`) berbagi satu implementasi.
 * Salinan lama di `domain/leads/BuktiProspekPlan.kt` sudah DIHAPUS (2026-08-29) — `AddLeadViewModel`
 * kini memanggil fungsi di sini lewat `util.ImagePixelPipeline.compress`, satu-satunya pemanggil
 * salinan lama itu.
 */

/**
 * `inSampleSize` terbesar (pangkat dua) yang masih menyisakan sisi terpanjang ≥ [maxDimensi].
 *
 * Sengaja berhenti SEBELUM melewati target, bukan sedekat mungkin ke bawahnya: menyusutkan lebih
 * jauh di tahap dekode membuang detail secara permanen, sedangkan penyesuaian halus ke
 * [maxDimensi] dikerjakan `createScaledBitmap` sesudahnya (bisa memakai rasio bebas dan
 * menyaring piksel, bukan cuma membuang baris/kolom).
 */
internal fun sampleSizeUntuk(lebar: Int, tinggi: Int, maxDimensi: Int): Int {
    if (lebar <= 0 || tinggi <= 0 || maxDimensi <= 0) return 1
    var sample = 1
    while (max(lebar, tinggi) / (sample * 2) >= maxDimensi) sample *= 2
    return sample
}

/**
 * `ExifInterface.ORIENTATION_*` → derajat rotasi searah jarum jam yang harus diterapkan supaya
 * foto tegak. Cuma empat nilai yang benar-benar berarti bagi kamera HP (0/90/180/270); nilai lain
 * (`FLIP_*`, `TRANSPOSE`, `TRANSVERSE`, `UNDEFINED`) jatuh ke 0° — jarang muncul dari sensor
 * kamera nyata, dan mengembalikan 0° jauh lebih aman daripada menebak sebuah flip yang salah.
 */
internal fun exifDegreesFor(orientation: Int): Float = when (orientation) {
    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
    else -> 0f
}
