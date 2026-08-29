package com.krisoft.tridjayaelektronik.domain.leads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Aturan bukti prospek menentukan apakah closing seorang trainee bisa DIHITUNG
 * saat kelulusannya dinilai (`closing_terverifikasi` hanya menghitung closing
 * berbukti). Salah longgar = prospeknya tersimpan tanpa bukti dan tak pernah
 * bisa dihitung kelak; salah ketat = orang yang tak wajib ikut terhalang.
 */
class WajibBuktiProspekTest {

    /** Cerminan `wajib_bukti_prospek` di `packages/rust-shared/src/bukti.rs`. */
    @Test
    fun `hanya trainee yang wajib`() {
        assertTrue(wajibBuktiProspek(listOf("trainee")))
        assertFalse(wajibBuktiProspek(listOf("karyawan")))
        assertFalse(wajibBuktiProspek(listOf("sales", "admin-penjualan")))
        assertFalse(wajibBuktiProspek(emptyList()))
    }

    /** Server menormalkan `trim().to_ascii_lowercase()`; klien harus sama. */
    @Test
    fun `ejaan dinormalkan seperti server`() {
        assertTrue(wajibBuktiProspek(listOf("TRAINEE")))
        assertTrue(wajibBuktiProspek(listOf(" Trainee ")))
    }

    /**
     * Ini pembeda dari web, dan sengaja. `ProspekSubmitForm.tsx` menilai
     * `user?.role === 'trainee'` — role PRIMARY saja — sedangkan server menilai
     * seluruh `roles[]`. Trainee yang membawa role kedua lolos form web lalu
     * ditolak 400 begitu saklarnya menyala; di sini ia tetap tertangkap.
     */
    @Test
    fun `trainee di role kedua tetap tertangkap`() {
        assertTrue(wajibBuktiProspek(peranEfektif("karyawan", listOf("karyawan", "trainee"))))
    }

    @Test
    fun `peran efektif menggabungkan primary lalu membuang kosong dan duplikat`() {
        assertEquals(listOf("karyawan", "sales"), peranEfektif("karyawan", listOf(" karyawan ", "sales", "")))
        assertEquals(listOf("sales"), peranEfektif(null, listOf("sales")))
        assertEquals(emptyList<String>(), peranEfektif("", emptyList()))
    }
}

/**
 * Penyusutan sebelum unggah. Yang diunggah adalah TANGKAPAN LAYAR PERCAKAPAN,
 * jadi kesalahan di sini tak muncul sebagai error — muncul sebagai bukti yang
 * tak terbaca saat mentor menilai.
 */
class UkuranBuktiProspekTest {

    // `sampleSizeUntuk` (kasus 1080×2400/pembagian-berhenti/dimensi-tak-masuk-akal) DIPINDAH
    // ke `domain/media/ImageCompressPlanTest.kt` (`SampleSizeUntukTest`) 2026-08-29 bersama
    // migrasi `AddLeadViewModel` ke `ImagePixelPipeline` — salinan fungsinya di berkas ini
    // (`BuktiProspekPlan.sampleSizeUntuk`) sudah dihapus, jadi test-nya ikut pindah, bukan
    // dihapus tanpa jejak.

    @Test
    fun `berkas di atas batas masukan ditolak dengan angkanya`() {
        val pesan = masalahUkuranBukti(30L * 1024 * 1024)
        assertNotNull(pesan)
        assertTrue(pesan!!.contains("30 MB"))
        assertTrue(pesan.contains("maksimal 25 MB"))
    }

    /**
     * Dibulatkan KE ATAS. Ke bawah akan berbunyi "maksimal 25 MB — berkas ini
     * 25 MB", yang terbaca seperti app-nya yang keliru, bukan berkasnya.
     */
    @Test
    fun `kelebihan tipis dibulatkan ke atas`() {
        val pesan = masalahUkuranBukti(MAX_BUKTI_INPUT_BYTES + 1)
        assertNotNull(pesan)
        assertTrue(pesan!!.contains("26 MB"))
    }

    /**
     * Ukuran 0 = penyedia galeri tak melaporkan `SIZE`, BUKAN "kosong".
     * Menolaknya di sini membuang gambar yang sebenarnya sah; kegagalan
     * sesungguhnya akan muncul saat dekode dengan pesan yang benar.
     */
    @Test
    fun `ukuran tak terlapor diloloskan`() {
        assertNull(masalahUkuranBukti(0))
        assertNull(masalahUkuranBukti(MAX_BUKTI_INPUT_BYTES))
    }

    /** Batas unggah WAJIB sama dengan `MAX_BUKTI_PROSPEK_BYTES` server (8 MB). */
    @Test
    fun `batas unggah sejajar server`() {
        assertEquals(8L * 1024 * 1024, MAX_BUKTI_PROSPEK_BYTES)
    }
}
