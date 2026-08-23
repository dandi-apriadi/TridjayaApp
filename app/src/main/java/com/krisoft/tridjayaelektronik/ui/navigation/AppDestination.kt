package com.krisoft.tridjayaelektronik.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChecklistRtl
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.krisoft.tridjayaelektronik.ui.home.ALL_LOGGED_IN
import com.krisoft.tridjayaelektronik.ui.home.gateAllows

/**
 * Single source of truth for every app destination. [bottomNavItems] is the FULL
 * pill roster; apa yang benar-benar dirender adalah hasil [visibleBottomNavItems]
 * — lihat catatan gating di bawah. Layar pertama app kini Activity ("hari ini
 * aku harus ngapain?"), dashboard lama pindah ke tab Operasional. INVENTORY
 * tetap ada sebagai destination tapi dibuka dari Activity (ubin "Cari Barang")
 * atau dari grid Akses Cepat di Operasional, daripada menempati slot bottom
 * nav sendiri. Prospek/CRM BUKAN destination lagi (dulu LEADS) — sejak
 * 2026-07-30 ia route biasa di dalam `ActivityNavHost` (`ROUTE_LEADS_LIST`),
 * persis sibling home_* lain, supaya mewarisi header+back+hilangnya pill yang
 * sama alih-alih jadi tab sejajar tanpa keduanya.
 *
 * **Gating per-peran ditambahkan 2026-08-23 (tab EKSEKUTIF).** Sebelum itu
 * `bottomNavItems` adalah `listOf` statis yang dibaca apa adanya oleh
 * `MainActivity` — nol filter di antaranya, jadi setiap tab yang pernah
 * ditambahkan otomatis muncul untuk SEMUA orang. Gating peran selama ini hidup
 * satu tingkat di bawah pill (isi layar: `ACTIVITY_ITEMS`, `QUICK_ACCESS_MENUS`).
 * Sekarang tab pun bisa ber-gate, memakai [gateAllows] yang SAMA supaya tiga
 * registri (tab, Activity, Akses Cepat) tak bisa menyimpang semantiknya —
 * termasuk sifat **fail-closed**-nya: kunci yang absen di peta server berarti
 * `false`, jadi tab baru TIDAK muncul di HP sampai backend-nya naik. Urutan
 * deploy karena itu mengikat: gateway/service dulu, APK belakangan.
 */
enum class AppDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
    /**
     * Kunci `GET /api/me/capabilities`. `null` = tab tak ber-gate; ia tampil
     * untuk siapa pun yang login (yang menyaring adalah isi layarnya).
     */
    val capability: String? = null,
    /**
     * Cadangan OFFLINE — dipakai HANYA saat peta kemampuan belum ada sama
     * sekali (`null`), bukan saat kuncinya kebetulan absen dari peta.
     */
    val allowedRoles: Set<String> = ALL_LOGGED_IN,
    /**
     * Guard backend aslinya, ditulis `"<file> <SIMBOL>"`. Bukan hiasan: pola
     * yang sama di `QuickAccessMenus.kt` ada supaya perubahan peran di backend
     * bisa ditelusuri ke cerminannya di klien lewat grep nama simbol.
     */
    val backendGuard: String = "tanpa guard: tab hanya wadah, isinya yang ber-gate",
) {
    /**
     * Papan pantau eksekutif. SENGAJA entri PERTAMA: `MainActivity` memilih tab
     * awal lewat `bottomNavItems.first()` yang lolos gate, jadi urutan di sini
     * ADALAH urutan prioritas pendaratan — superadmin mendarat di sini, orang
     * lain (yang tak lolos gate) tetap mendarat di ACTIVITY.
     */
    EKSEKUTIF(
        route = "eksekutif",
        label = "Eksekutif",
        icon = Icons.Rounded.QueryStats,
        capability = "monitoring.eksekutif",
        allowedRoles = setOf("superadmin", "admin"),
        backendGuard = "gateway lib.rs require_monitoring_eksekutif",
    ),
    ACTIVITY("activity", "Activity", Icons.Rounded.ChecklistRtl),
    SUMMARY("summary", "Operasional", Icons.Rounded.Insights),
    INVENTORY("inventory", "Cari", Icons.Rounded.Search),
    SETTINGS("settings", "Pengaturan", Icons.Rounded.Settings);

    companion object {
        // Pill Rhythm dirancang 2-3 item; EKSEKUTIF menjadikannya 4 untuk
        // superadmin saja (orang lain tetap 3). Item TERPILIH mengambil sisa
        // ruang dan yang lain intrinsic 48dp, jadi 4 item masih memuat label
        // terpanjang ("Operasional") di HP 393dp — hitungannya ada di komentar
        // `TridjayaFloatingNav`. Menambah item KELIMA ke pill wajib mengukur
        // ulang; jangan asumsikan masih muat.
        //
        // INVENTORY menyusul dilepas 2026-07-29 (tombol Cari/search FAB dihapus
        // atas permintaan user). Destination-nya SENGAJA tetap ada — ia host
        // `InventoryNavHost` (jelajah barang, detail produk, flyer) — dan
        // dijangkau lewat callback pindah-tab `onQuickAccessInventory`:
        // ubin "Cari Barang" di Activity + tile "Inventory" di grid Akses Cepat
        // Operasional. Menghapus baris ini dari enum akan mematikan seluruh
        // menu Inventory, bukan cuma tombolnya.
        // SETTINGS masuk pill 2026-07-29 (permintaan user: lebih mudah
        // dijangkau). Sebelumnya hanya lewat ikon gear di pojok header Activity
        // & Operasional — ikon itu DIHAPUS bersamaan supaya tak ada dua pintu ke
        // layar yang sama.
        val bottomNavItems = listOf(EKSEKUTIF, ACTIVITY, SUMMARY, SETTINGS)

        /**
         * Tab yang boleh dilihat pemilik [effectiveRoles] / [capabilities].
         *
         * Urutannya SAMA dengan [bottomNavItems] — pill merender apa adanya dan
         * `MainActivity` membaca elemen pertamanya sebagai kandidat tab awal.
         *
         * **Tak pernah kosong.** Semua tab selain EKSEKUTIF ber-`capability =
         * null` + `allowedRoles = ALL_LOGGED_IN`, jadi satu-satunya cara daftar
         * ini kosong adalah `effectiveRoles` kosong (profil belum termuat) —
         * dan itu ditangani [tabAwal] dengan mengembalikan ACTIVITY, bukan
         * dengan meledak. Kalau kelak ada tab ber-gate KEDUA, periksa ulang
         * asumsi ini sebelum memanggil `.first()` di mana pun.
         */
        fun visibleBottomNavItems(
            effectiveRoles: Set<String>,
            capabilities: Map<String, Boolean>?,
        ): List<AppDestination> = bottomNavItems.filter { tab ->
            // Tab tanpa gate lolos TANPA memanggil `gateAllows`, dan itu
            // disengaja: `gateAllows` menjawab `false` untuk role KOSONG
            // (profil belum termuat) — benar untuk sebuah MENU, salah untuk
            // seluruh bilah navigasi. Memakainya apa adanya membuat app tanpa
            // satu pun tab selama beberapa ratus milidetik tiap start dingin.
            val tanpaGate = tab.capability == null && tab.allowedRoles == ALL_LOGGED_IN
            tanpaGate || gateAllows(tab.capability, tab.allowedRoles, effectiveRoles, capabilities)
        }

        /**
         * Tab yang harus terpilih, dihitung dari pengetahuan yang ADA SAAT INI.
         *
         * Fungsi murni supaya aturannya bisa diuji tanpa Compose. Pemanggilnya
         * (`MainActivity`) menerapkannya HANYA selama user belum menyentuh
         * pill — begitu ia memilih tab sendiri, hasil fungsi ini diabaikan.
         *
         * Urutan keputusan:
         * 1. EKSEKUTIF kalau tersedia — papan pantau itu memang diminta jadi
         *    layar pertama, bukan sekadar tab tambahan.
         * 2. SUMMARY untuk manager/owner ([landsOnSummary]) — aturan lama, tak
         *    disentuh.
         * 3. elemen pertama yang tersedia (praktisnya ACTIVITY).
         *
         * [tabTersedia] kosong (mustahil hari ini, lihat [visibleBottomNavItems])
         * dijawab ACTIVITY, bukan lemparan — bilah navigasi bukan tempat yang
         * pantas untuk crash.
         */
        fun tabAwal(
            tabTersedia: List<AppDestination>,
            landsOnSummary: Boolean,
        ): AppDestination = when {
            EKSEKUTIF in tabTersedia -> EKSEKUTIF
            landsOnSummary && SUMMARY in tabTersedia -> SUMMARY
            else -> tabTersedia.firstOrNull() ?: ACTIVITY
        }
    }
}
