package com.krisoft.tridjayaelektronik.domain.media

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Matematika murni di belakang `util.ImagePixelPipeline` — dekode & rotasi.
 *
 * Kasus [sampleSizeUntuk] di sini menggantikan salinan lama yang dulu hidup di
 * `domain/leads/BuktiProspekPlan.kt` (`UkuranBuktiProspekTest` di
 * `BuktiProspekPlanTest.kt`) — dihapus 2026-08-29 bersama migrasi `AddLeadViewModel` ke
 * `ImagePixelPipeline`, satu-satunya pemanggil salinan itu.
 */
class SampleSizeUntukTest {

    /** Layar HP lazim (1080×2400, 1440×3200) HARUS lolos tanpa disusutkan pada maxDimensi 2560. */
    @Test
    fun `dimensi di bawah target tidak disusutkan`() {
        assertEquals(1, sampleSizeUntuk(1080, 2400, maxDimensi = 2560))
        assertEquals(1, sampleSizeUntuk(1440, 3200, maxDimensi = 2560))
    }

    /**
     * Pembagian SENGAJA berhenti sebelum melewati target: yang dicari adalah pangkat dua
     * terbesar yang hasilnya MASIH ≥ [maxDimensi]. Foto 12 MP (4000×3000) pada maxDimensi 2560
     * tetap sample 1 — 4000/2 = 2000 sudah di bawah 2560, dan menyusutkannya di tahap dekode
     * berarti membuang detail secara permanen; penyesuaian halus dikerjakan `createScaledBitmap`
     * sesudahnya.
     */
    @Test
    fun `pembagian berhenti sebelum melewati target`() {
        assertEquals(1, sampleSizeUntuk(4000, 3000, maxDimensi = 2560))
        assertEquals(2, sampleSizeUntuk(8000, 6000, maxDimensi = 2560))
        assertEquals(4, sampleSizeUntuk(16000, 12000, maxDimensi = 2560))
    }

    /** Angka absen/delivery (maxDimensi 1600) — nilai yang dipakai `PhotoWatermark`. */
    @Test
    fun `maxDimensi lebih kecil menyusutkan lebih agresif`() {
        assertEquals(2, sampleSizeUntuk(4000, 3000, maxDimensi = 1600))
    }

    /** Dimensi tak masuk akal (0/negatif) tak boleh jadi pembagian nol atau loop tak berujung. */
    @Test
    fun `dimensi tak masuk akal jatuh ke satu`() {
        assertEquals(1, sampleSizeUntuk(0, 0, maxDimensi = 2560))
        assertEquals(1, sampleSizeUntuk(-10, 100, maxDimensi = 2560))
        assertEquals(1, sampleSizeUntuk(1000, 1000, maxDimensi = 0))
    }
}

/**
 * `ExifInterface.ORIENTATION_*` → derajat rotasi. Hanya empat nilai yang berarti bagi kamera HP;
 * nilai lain jatuh ke 0° (lihat KDoc [exifDegreesFor]).
 */
class ExifDegreesForTest {

    @Test
    fun `empat orientasi standar terpetakan benar`() {
        assertEquals(
            90f,
            exifDegreesFor(androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90),
        )
        assertEquals(
            180f,
            exifDegreesFor(androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180),
        )
        assertEquals(
            270f,
            exifDegreesFor(androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270),
        )
        assertEquals(
            0f,
            exifDegreesFor(androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL),
        )
    }

    /** `FLIP_*`/`TRANSPOSE`/`TRANSVERSE`/`UNDEFINED` — jarang dari sensor kamera nyata, jatuh ke 0°. */
    @Test
    fun `orientasi tak dikenal jatuh ke nol derajat`() {
        assertEquals(
            0f,
            exifDegreesFor(androidx.exifinterface.media.ExifInterface.ORIENTATION_FLIP_HORIZONTAL),
        )
        assertEquals(0f, exifDegreesFor(-1))
        assertEquals(0f, exifDegreesFor(999))
    }
}
