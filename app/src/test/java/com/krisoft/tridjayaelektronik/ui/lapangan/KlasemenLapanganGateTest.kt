package com.krisoft.tridjayaelektronik.ui.lapangan

import com.krisoft.tridjayaelektronik.ui.home.QUICK_ACCESS_MENUS
import com.krisoft.tridjayaelektronik.ui.home.gateAllows
import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KlasemenLapanganGateTest {

    private val tile = QUICK_ACCESS_MENUS.first { it.id == "klasemen_lapangan" }

    /**
     * Kunci kemampuannya WAJIB `klasemen.operasional`, bukan `klasemen.view`.
     * `klasemen.view` adalah audiens klasemen PENJUALAN — daftar yang justru tak
     * memuat `driver` maupun `pdi`, yaitu orang yang dinilai papan ini.
     */
    @Test
    fun `tile memakai kunci kemampuan sendiri, bukan milik klasemen penjualan`() {
        assertEquals("klasemen.operasional", tile.capability)
    }

    @Test
    fun `peta kemampuan server MENANG atas daftar role cadangan`() {
        // Server bilang tidak — walau role-nya ada di daftar cadangan.
        assertFalse(
            gateAllows(tile.capability, tile.allowedRoles, setOf("driver"), mapOf("klasemen.operasional" to false)),
        )
        // Server bilang ya.
        assertTrue(
            gateAllows(tile.capability, tile.allowedRoles, setOf("driver"), mapOf("klasemen.operasional" to true)),
        )
        // Kunci tak dikenal server = fail-closed, bukan jatuh ke daftar role.
        assertFalse(gateAllows(tile.capability, tile.allowedRoles, setOf("driver"), emptyMap()))
    }

    @Test
    fun `cadangan offline memuat yang dinilai dan atasannya, bukan orang penjualan`() {
        // Peta kemampuan null = server belum terjawab (offline / server lama).
        for (role in listOf("driver", "pdi", "delivery-control", "kepala-cabang", "manager", "owner")) {
            assertTrue(role, gateAllows(tile.capability, tile.allowedRoles, setOf(role), null))
        }
        for (role in listOf("karyawan", "admin-sales", "trainee", "kasir")) {
            assertFalse(role, gateAllows(tile.capability, tile.allowedRoles, setOf(role), null))
        }
    }

    @Test
    fun `backendGuard menyebut modul dan konstanta aslinya`() {
        assertTrue(tile.backendGuard, tile.backendGuard.contains("klasemen.rs"))
        assertTrue(tile.backendGuard, tile.backendGuard.contains("LIHAT_ROLES"))
    }

    /**
     * `java.time` haram di `app/src/main` (minSdk 24 tanpa desugaring), jadi
     * periodenya dihitung `Calendar`. Test ini sekaligus mengunci bentuk
     * `YYYY-MM` — bulan berpadding nol, yang gampang hilang kalau kelak
     * seseorang menggantinya dengan interpolasi biasa.
     */
    @Test
    fun `periode berjalan berbentuk YYYY-MM dengan bulan berpadding`() {
        val januari = Calendar.getInstance().apply { set(2026, Calendar.JANUARY, 15) }
        assertEquals("2026-01", periodeBerjalan(januari))
        val desember = Calendar.getInstance().apply { set(2026, Calendar.DECEMBER, 1) }
        assertEquals("2026-12", periodeBerjalan(desember))
    }

    @Test
    fun `dua peran punya slug yang dipakai di path endpoint`() {
        assertEquals(listOf("driver", "pdi"), PeranPapan.entries.map { it.slug })
        // Judul dipakai sebagai label chip — kosong berarti tab tanpa nama.
        // (`assertNotNull` atas properti non-nullable Kotlin = tautologi.)
        assertTrue(PeranPapan.entries.all { it.judul.isNotBlank() })
    }
}
