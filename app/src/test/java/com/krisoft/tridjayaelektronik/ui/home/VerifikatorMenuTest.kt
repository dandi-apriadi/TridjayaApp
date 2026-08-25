package com.krisoft.tridjayaelektronik.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Dua ubin VERIFIKATOR (divisi `verificator-dan-reporting`, slug `cs`):
 * "Penugasan AC" (`acinstall.schedule`) dan "Verifikasi Telepon"
 * (`vertel.manage`), ditambahkan 2026-08-25.
 *
 * **Kenapa test ini ada.** Kelas bug yang sudah berulang di repo ini: menu
 * tampil ke role yang backend-nya menjawab 403, atau — yang jauh lebih sulit
 * dilihat — menu HILANG dari orang yang sebenarnya berhak. Yang kedua tak
 * menimbulkan error apa pun; ia hanya terlihat sebagai orang yang diam-diam
 * tak bisa bekerja. Modul pemasangan AC sendiri hidup berbulan-bulan dengan
 * sisi petugasnya saja di app, sementara penugasannya web-only.
 */
class VerifikatorMenuTest {

    private fun idTerlihat(
        roles: Set<String>,
        capabilities: Map<String, Boolean>? = null,
    ): List<String> = visibleQuickAccessMenus(roles, capabilities).map { it.id }

    @Test
    fun `verifikator melihat kedua ubin lewat cadangan offline`() {
        // Peta kemampuan `null` = offline / panggilan gagal → jatuh ke daftar
        // role lokal. Inilah satu-satunya jalur yang daftar role masih menentukan,
        // dan justru itu yang dulu mustahil dilewati slug `cs`: ia tak ada di
        // KNOWN_ROLES, jadi menulisnya di `allowedRoles` divonis salah ketik.
        val cs = idTerlihat(setOf("karyawan", "cs"))
        assertTrue("pemasangan_ac_kontrol" in cs)
        assertTrue("vertel" in cs)
    }

    @Test
    fun `karyawan biasa tidak melihat ubin verifikator`() {
        val karyawan = idTerlihat(setOf("karyawan", "sales"))
        assertFalse("pemasangan_ac_kontrol" in karyawan)
        assertFalse("vertel" in karyawan)

        // PDI mengerjakan pemasangan, tapi TIDAK menugaskannya — dua sisi modul
        // yang sama dengan hak yang berbeda.
        val pdi = idTerlihat(setOf("pdi"))
        assertFalse("pemasangan_ac_kontrol" in pdi)
        assertFalse("vertel" in pdi)
    }

    @Test
    fun `admin dan superadmin ikut, sesuai konstanta server`() {
        // Cerminan `AC_INSTALL_SCHEDULE_ROLES` / `VERTEL_ROLES` di rust-shared,
        // yang keduanya = ["cs", "admin", "superadmin"].
        listOf("admin", "superadmin").forEach { role ->
            val terlihat = idTerlihat(setOf(role))
            assertTrue("$role harus melihat pemasangan_ac_kontrol", "pemasangan_ac_kontrol" in terlihat)
            assertTrue("$role harus melihat vertel", "vertel" in terlihat)
        }
        // Manager SENGAJA di luar keduanya — ia tak ada di konstanta server.
        val manager = idTerlihat(setOf("manager"))
        assertFalse("pemasangan_ac_kontrol" in manager)
        assertFalse("vertel" in manager)
    }

    @Test
    fun `peta kemampuan server menang atas daftar role lokal`() {
        // Server mencabut satu kunci → ubinnya hilang walau role-nya masih `cs`.
        // Ini jalur NORMAL-nya: verifikator memegang `cs` lewat divisi, dan peta
        // kemampuan selalu ada saat online.
        val dicabut = mapOf("acinstall.schedule" to false, "vertel.manage" to true)
        val terlihat = idTerlihat(setOf("karyawan", "cs"), dicabut)
        assertFalse("pemasangan_ac_kontrol" in terlihat)
        assertTrue("vertel" in terlihat)

        // Sebaliknya: server MEMBERI kunci ke role yang daftar lokalnya tak
        // memuatnya → ubinnya muncul. Tanpa arah ini, penambahan role di server
        // menuntut rilis APK baru sebelum orangnya bisa bekerja.
        val diberi = mapOf("acinstall.schedule" to true, "vertel.manage" to true)
        val karyawan = idTerlihat(setOf("karyawan"), diberi)
        assertTrue("pemasangan_ac_kontrol" in karyawan)
        assertTrue("vertel" in karyawan)
    }

    @Test
    fun `kunci kemampuan persis seperti di katalog server`() {
        // Peta kemampuan FAIL-CLOSED: kunci yang tak dikenal server dianggap
        // `false`, jadi salah ketik di sini menyembunyikan menu dari SEMUA
        // orang — termasuk superadmin — tanpa satu pun galat.
        val menus = QUICK_ACCESS_MENUS.associateBy { it.id }
        assertEquals("acinstall.schedule", menus.getValue("pemasangan_ac_kontrol").capability)
        assertEquals("vertel.manage", menus.getValue("vertel").capability)
    }

    @Test
    fun `slug cs diakui sebagai role yang dikenal`() {
        // Penjaga terhadap kemunduran: kalau "cs" dicabut lagi dari KNOWN_ROLES,
        // test `tidak ada role salah ketik di registri` akan gagal atas dua ubin
        // di atas — tapi pesannya akan menyebut "salah ketik", yang MENYESATKAN.
        // Baris ini menjelaskan sebab sebenarnya di tempat yang tepat.
        //
        // `cs` lahir dari `divisi_access_slugs("verificator-dan-reporting")`
        // (rust-shared `auth.rs`, migrasi 223). Diperiksa di produksi
        // 2026-08-25: 3 akun aktif memegang divisi itu.
        assertTrue("slug `cs` harus ada di KNOWN_ROLES", "cs" in KNOWN_ROLES)
    }
}
