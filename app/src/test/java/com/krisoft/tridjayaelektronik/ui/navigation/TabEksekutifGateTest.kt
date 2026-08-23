package com.krisoft.tridjayaelektronik.ui.navigation

import com.krisoft.tridjayaelektronik.ui.navigation.AppDestination.Companion.bottomNavItems
import com.krisoft.tridjayaelektronik.ui.navigation.AppDestination.Companion.tabAwal
import com.krisoft.tridjayaelektronik.ui.navigation.AppDestination.Companion.visibleBottomNavItems
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Penjaga gerbang TAB.** Sebelum tab Eksekutif ada, `bottomNavItems` adalah
 * `listOf` statis yang dibaca apa adanya — nol filter, dan **nol test yang
 * menguncinya**. Artinya menambah tab tidak memerahkan satu pun pemeriksa;
 * satu-satunya yang menahan adalah lima `when` exhaustive di `MainActivity`,
 * dan itu cuma menjamin tab-nya PUNYA cabang, bukan bahwa orang yang tepat yang
 * melihatnya.
 *
 * Berkas ini menutup kedua arah: tab tak boleh bocor ke yang tak berhak, dan tak
 * boleh hilang dari yang berhak. Arah kedua yang lebih mudah lolos — menu yang
 * HILANG tak menimbulkan galat apa pun, ia hanya terlihat sebagai orang yang
 * diam-diam tak bisa bekerja.
 */
class TabEksekutifGateTest {

    private val semuaKemampuanMati =
        mapOf("monitoring.eksekutif" to false)
    private val kemampuanEksekutif =
        mapOf("monitoring.eksekutif" to true)

    @Test
    fun `eksekutif adalah entri pertama supaya jadi tab pendaratan`() {
        assertEquals(
            "urutan bottomNavItems ADALAH urutan prioritas pendaratan",
            AppDestination.EKSEKUTIF,
            bottomNavItems.first(),
        )
    }

    /**
     * Peta kemampuan `null` = belum pernah berhasil diambil (start dingin,
     * offline, server lama). Tab ber-gate HARUS sembunyi; tab lain HARUS tetap
     * ada. Kalau `gateAllows` dipakai apa adanya untuk semua tab, role kosong
     * membuatnya menjawab `false` untuk SEMUANYA dan app tampil tanpa satu pun
     * tab selama beberapa ratus milidetik tiap start.
     */
    @Test
    fun `tanpa peta kemampuan dan tanpa role, tab dasar tetap ada dan eksekutif sembunyi`() {
        val terlihat = visibleBottomNavItems(emptySet(), null)
        assertTrue(AppDestination.ACTIVITY in terlihat)
        assertTrue(AppDestination.SUMMARY in terlihat)
        assertTrue(AppDestination.SETTINGS in terlihat)
        assertFalse(AppDestination.EKSEKUTIF in terlihat)
        assertEquals(AppDestination.ACTIVITY, tabAwal(terlihat, landsOnSummary = false))
    }

    @Test
    fun `superadmin melihat tab eksekutif dan mendarat di sana`() {
        val terlihat = visibleBottomNavItems(setOf("superadmin"), kemampuanEksekutif)
        assertTrue(AppDestination.EKSEKUTIF in terlihat)
        assertEquals(AppDestination.EKSEKUTIF, tabAwal(terlihat, landsOnSummary = false))
    }

    @Test
    fun `karyawan biasa tidak melihat tab eksekutif`() {
        val terlihat = visibleBottomNavItems(setOf("karyawan"), semuaKemampuanMati)
        assertFalse(AppDestination.EKSEKUTIF in terlihat)
        assertEquals(3, terlihat.size)
    }

    /**
     * **Fail-closed**: server yang belum mengenal kuncinya (APK lebih baru dari
     * backend) TIDAK boleh membuat klien jatuh ke daftar role lokal. Kalau ia
     * jatuh, orang yang kebetulan ber-role superadmin akan melihat tab yang
     * seluruh endpoint-nya dijawab 404 — layar yang ada tapi tak pernah berisi.
     */
    @Test
    fun `kunci absen dari peta server berarti sembunyi walau rolenya superadmin`() {
        val terlihat = visibleBottomNavItems(setOf("superadmin"), mapOf("finance.view" to true))
        assertFalse(AppDestination.EKSEKUTIF in terlihat)
    }

    /**
     * Cadangan OFFLINE: peta `null` + role superadmin. Di sini daftar role lokal
     * memang dipakai — itu satu-satunya keadaan yang boleh memakainya.
     */
    @Test
    fun `tanpa peta kemampuan, superadmin tetap melihat tab lewat cadangan role`() {
        val terlihat = visibleBottomNavItems(setOf("superadmin"), null)
        assertTrue(AppDestination.EKSEKUTIF in terlihat)
        // Alias wire `admin` (migrasi 075) WAJIB ikut — gateway melipat
        // `superadmin` jadi `admin` sebelum meneruskan ke service, jadi
        // menghapusnya membuat tab hilang tanpa satu pun galat.
        assertTrue(AppDestination.EKSEKUTIF in visibleBottomNavItems(setOf("admin"), null))
    }

    /**
     * Aturan pendaratan LAMA tak boleh terinjak: manager/owner tetap mendarat di
     * Operasional selama mereka belum memegang kemampuan eksekutif.
     */
    @Test
    fun `manager tetap mendarat di operasional`() {
        val terlihat = visibleBottomNavItems(setOf("manager"), semuaKemampuanMati)
        assertFalse(AppDestination.EKSEKUTIF in terlihat)
        assertEquals(AppDestination.SUMMARY, tabAwal(terlihat, landsOnSummary = true))
    }

    /**
     * Eksekutif MENANG atas aturan `landsOnSummary`. Kalau kelak manager ikut
     * diberi kemampuan ini (fase 2 owner, atau perluasan lain), papan pantau
     * itulah yang diminta jadi layar pertama — bukan dashboard operasional.
     */
    @Test
    fun `eksekutif menang atas aturan pendaratan operasional`() {
        val terlihat = visibleBottomNavItems(setOf("manager", "superadmin"), kemampuanEksekutif)
        assertEquals(AppDestination.EKSEKUTIF, tabAwal(terlihat, landsOnSummary = true))
    }

    /**
     * `tabAwal` dipanggil dengan hasil `visibleBottomNavItems`, yang hari ini tak
     * pernah kosong. Tapi bilah navigasi bukan tempat yang pantas untuk crash
     * kalau asumsi itu kelak berubah.
     */
    @Test
    fun `daftar kosong dijawab activity bukan lemparan`() {
        assertEquals(AppDestination.ACTIVITY, tabAwal(emptyList(), landsOnSummary = true))
    }

    /**
     * Setiap tab ber-`capability` WAJIB menyebut guard backend-nya. Bukan
     * hiasan: itu satu-satunya cara perubahan peran di Rust bisa ditelusuri ke
     * cerminannya di sini lewat grep nama simbol — pola yang sama sudah dipakai
     * `QuickAccessMenus.kt` dan `navCatalog.ts`.
     */
    @Test
    fun `tab ber-gate menyebut guard backendnya`() {
        AppDestination.entries
            .filter { it.capability != null }
            .forEach { tab ->
                assertTrue(
                    "${tab.name} ber-capability tapi backendGuard-nya masih teks bawaan",
                    tab.backendGuard.isNotBlank() && !tab.backendGuard.startsWith("tanpa guard:"),
                )
            }
        assertEquals(
            "gateway lib.rs require_monitoring_eksekutif",
            AppDestination.EKSEKUTIF.backendGuard,
        )
    }
}
