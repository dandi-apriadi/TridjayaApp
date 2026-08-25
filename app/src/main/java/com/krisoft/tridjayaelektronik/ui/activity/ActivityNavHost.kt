package com.krisoft.tridjayaelektronik.ui.activity

import android.net.Uri
import androidx.compose.animation.core.EaseInOutQuart
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.krisoft.tridjayaelektronik.ui.acinstall.AcInstallScheduleScreen
import com.krisoft.tridjayaelektronik.ui.acinstall.AcInstallScreen
import com.krisoft.tridjayaelektronik.ui.vertel.VertelScreen
import com.krisoft.tridjayaelektronik.ui.attendance.AttendanceScreen
import com.krisoft.tridjayaelektronik.ui.deadstock.DeadstockScreen
import com.krisoft.tridjayaelektronik.ui.event.EventLeadScreen
import com.krisoft.tridjayaelektronik.ui.indent.IndentListScreen
import com.krisoft.tridjayaelektronik.ui.kupongebyar.KuponGebyarScreen
import com.krisoft.tridjayaelektronik.ui.opname.OpnameListScreen
import com.krisoft.tridjayaelektronik.ui.opname.OpnameValidasiScreen
import com.krisoft.tridjayaelektronik.ui.sales.SalesScreen
import com.krisoft.tridjayaelektronik.data.model.DeliveryStatusKey
import com.krisoft.tridjayaelektronik.data.model.KontrolSaringan
import com.krisoft.tridjayaelektronik.ui.deliveryflow.AkiListScreen
import com.krisoft.tridjayaelektronik.ui.deliveryflow.CreateSpkScreen
import com.krisoft.tridjayaelektronik.ui.deliveryflow.DiscountApprovalScreen
import com.krisoft.tridjayaelektronik.ui.deliveryflow.SpkDiskonDetailScreen
import com.krisoft.tridjayaelektronik.ui.deliveryflow.DeliveryJobDetailScreen
import com.krisoft.tridjayaelektronik.ui.deliveryflow.DeliveryQueueScreen
import com.krisoft.tridjayaelektronik.ui.deliveryflow.SpkHubScreen
import com.krisoft.tridjayaelektronik.ui.kpi.KpiScreen
import com.krisoft.tridjayaelektronik.ui.leads.AddLeadScreen
import com.krisoft.tridjayaelektronik.ui.leads.LeadDetailScreen
import com.krisoft.tridjayaelektronik.ui.leads.LeadsListScreen
import com.krisoft.tridjayaelektronik.ui.leads.LeadsListViewModel
import com.krisoft.tridjayaelektronik.ui.mutasi.MutasiHistoriScreen
import com.krisoft.tridjayaelektronik.ui.notifications.NotificationCenterScreen
import com.krisoft.tridjayaelektronik.ui.payroll.PayrollScreen
import com.krisoft.tridjayaelektronik.ui.priceerp.ErpPriceChangesScreen
import com.krisoft.tridjayaelektronik.ui.homeservice.HomeServiceDetailScreen
import com.krisoft.tridjayaelektronik.ui.homeservice.HomeServiceLaporScreen
import com.krisoft.tridjayaelektronik.ui.homeservice.HomeServiceListScreen
import com.krisoft.tridjayaelektronik.ui.homeservice.HsMode
import com.krisoft.tridjayaelektronik.ui.aktivitas.AktivitasReviewScreen
import com.krisoft.tridjayaelektronik.ui.aktivitas.AktivitasRiwayatScreen
import com.krisoft.tridjayaelektronik.ui.aktivitas.AktivitasScreen
import com.krisoft.tridjayaelektronik.ui.serials.SerialInputScreen
// Berikut masih tinggal di package ui.home (hanya HomeNavHost yang pindah ke
// ui.activity) — perlu diimpor eksplisit karena tak lagi satu paket.
import com.krisoft.tridjayaelektronik.ui.home.HomeScreen
import com.krisoft.tridjayaelektronik.ui.home.HomeViewModel
import com.krisoft.tridjayaelektronik.ui.home.RankingKind
import com.krisoft.tridjayaelektronik.ui.home.RankingListScreen
import com.krisoft.tridjayaelektronik.ui.home.TransactionListScreen

// Root Activity — layar pertama app (Task B6). Tabel route di bawah dipakai
// DUA tab: lihat dok [ActivityNavHost].
const val ACTIVITY_ROUTE_ROOT = "activity_root"
const val HOME_ROUTE_DASHBOARD = "home_dashboard"
private const val ROUTE_NOTIFICATIONS = "home_notifications"
private const val ROUTE_RANKING = "home_ranking/{kind}"
private const val ROUTE_TRANSACTIONS = "home_ranking_transactions/{kind}/{code}?name={name}"
private const val ROUTE_INDENT = "home_indent"
private const val ROUTE_SALES = "home_sales"
private const val ROUTE_OPNAME = "home_opname"

/** PUBLIK: tujuan deep-link notif `opname_manual_submitted` (channel `approval`,
 *  route key `opname_validasi`). Namanya kontrak — jangan diubah lagi. */
const val ROUTE_OPNAME_VALIDASI = "home_opname_validasi"
private const val ROUTE_ABSEN = "home_absen"
private const val ROUTE_AKTIVITAS = "home_aktivitas"
private const val ROUTE_AKTIVITAS_REVIEW = "home_aktivitas_review"

/**
 * Riwayat aktivitas milik sendiri. SENGAJA tak punya entri di
 * [routeForNavKey] maupun di registri kartu: ia sub-layar dari
 * [ROUTE_AKTIVITAS], dibuka dari tombol di dalamnya — persis seperti web, yang
 * menaruh `karyawan/raport/history` sebagai sub-route tanpa menu sendiri.
 * Menambahkannya ke `routeForNavKey` berarti membuka pintu deep-link notifikasi
 * ke layar yang tak pernah jadi sasaran notifikasi apa pun.
 */
private const val ROUTE_AKTIVITAS_RIWAYAT = "home_aktivitas_riwayat"
// Komplain (Home Service). Empat daftar berbagi SATU layar (`HsMode`), tapi
// route-nya tetap terpisah supaya deep-link notif bisa menunjuk antrian yang
// tepat dan tombol back tiap peran tak saling menimpa.
private const val ROUTE_HS_LAPOR = "home_hs_lapor"
private const val ROUTE_HS_TRIASE = "home_hs_triase"
private const val ROUTE_HS_TEKNISI = "home_hs_teknisi"
private const val ROUTE_HS_TARIK = "home_hs_tarik"
private const val ROUTE_HS_DRIVER = "home_hs_driver"
private const val ROUTE_HS_DETAIL = "home_hs_detail/{id}"

/** Tugas pemasangan AC (sisi petugas). Prefiks `home_` mengikuti seluruh route
 *  anak tabel ini — lihat catatan penamaan di CLAUDE.md. */
private const val ROUTE_PEMASANGAN_AC = "home_pemasangan_ac"
// Sisi VERIFIKATOR pemasangan AC + verifikasi telepon (2026-08-25). Keduanya
// dijangkau dari ubin Akses Cepat, BUKAN dari kartu Activity: ini pekerjaan
// meja yang dibuka saat dibutuhkan, bukan antrian harian yang menunggu jawaban.
private const val ROUTE_PEMASANGAN_AC_KONTROL = "home_pemasangan_ac_kontrol"
private const val ROUTE_VERTEL = "home_vertel"

private fun hsDetailRoute(id: String) = "home_hs_detail/${Uri.encode(id)}"
private const val ROUTE_GAJI = "home_gaji"
private const val ROUTE_KPI = "home_kpi"
private const val ROUTE_HARGA_GS = "home_harga_gs"
private const val ROUTE_SERIAL_INPUT = "home_serial_input"
private const val ROUTE_DEADSTOCK = "home_deadstock"
/** Konsumen Gebyar — daftar konsumen berhak kupon doorprize di cabang sendiri.
 *  Prefiks `home_` mengikuti seluruh route anak tabel ini. */
private const val ROUTE_KUPON_GEBYAR = "home_kupon_gebyar"
private const val ROUTE_MUTASI_HISTORI = "home_mutasi_histori"
private const val ROUTE_PANDUAN_ALUR = "home_panduan_alur"
// Prospek event lapangan — dibuka HANYA dari kartu event yang menggantikan sapaan
// di tab Operasional (spec §7.3: sengaja tanpa tile di Akses Cepat/registri Activity,
// supaya tak ada registri kedua yang harus dijaga sinkron).
private const val ROUTE_EVENT_LEAD = "home_event_lead/{eventId}"
// Prospek/CRM — dulu NavHost sejajar tab (LeadsNavHost) tanpa pill/back yang
// benar (destination-nya sengaja tak masuk bottomNavItems, jadi pill tetap
// tampil tanpa yang menyala). Pindah ke tabel ini supaya mewarisi header +
// pop-back + hilangnya pill yang sama seperti sibling home_* lain.
private const val ROUTE_LEADS_LIST = "home_leads_list"
private const val ROUTE_LEADS_ADD = "home_leads_add"
private const val ROUTE_LEADS_DETAIL = "home_leads_detail/{leadId}"
// Public (bukan private lagi) — MainActivity deep-link tap-notif buka langsung
// halaman tahap terkait (akses cepat, route dari payload FCM delivery_notif).
const val ROUTE_DLV_CREATE = "home_dlv_create"
const val ROUTE_DLV_DISKON = "home_dlv_diskon"
const val ROUTE_DLV_PDI = "home_dlv_pdi"
const val ROUTE_DLV_AKI = "home_dlv_aki"
const val ROUTE_DLV_KASIR = "home_dlv_kasir"
const val ROUTE_DLV_NOTE = "home_dlv_note"
const val ROUTE_DLV_SCHEDULE = "home_dlv_schedule"
const val ROUTE_DLV_DRIVER = "home_dlv_driver"
private const val ROUTE_DLV_DETAIL = "home_dlv_detail/{id}"
// Detail SPK milik approval diskon: HALAMAN sendiri, bukan dialog. Isinya
// (pembiayaan + seluruh unit se-SPK + tiga total) tak muat di AlertDialog —
// di layar sempit ia jadi kotak bergulir di dalam kotak, dan tombol tutupnya
// terdorong keluar. Route TERPISAH dari ROUTE_DLV_DETAIL karena kuncinya
// berbeda: yang itu id job, yang ini kode batch SPK.
private const val ROUTE_DLV_DISKON_DETAIL = "home_dlv_diskon_detail/{kode}"
const val ROUTE_DLV_HISTORY = "home_dlv_history"
const val ROUTE_DLV_PENDING_PAYMENT = "home_dlv_pending_payment"
const val ROUTE_SPK_HUB = "home_spk_hub"

private fun dlvDetailRoute(id: String) = "home_dlv_detail/${Uri.encode(id)}"
private fun dlvDiskonDetailRoute(kode: String) = "home_dlv_diskon_detail/${Uri.encode(kode)}"

private fun eventLeadRoute(eventId: String) = "home_event_lead/${Uri.encode(eventId)}"

private fun branchTransactionsRoute(kodeDealer: String, branchName: String) =
    "home_ranking_transactions/${RankingKind.BRANCH.name}/${Uri.encode(kodeDealer)}?name=${Uri.encode(branchName)}"

private fun salesTransactionsRoute(kodePegawai: String, salesName: String) =
    "home_ranking_transactions/${RankingKind.SALES.name}/${Uri.encode(kodePegawai)}?name=${Uri.encode(salesName)}"

/**
 * Kunci tahap delivery → route child — bagian yang SAMA dipakai [routeForNavKey],
 * `onSpkMenu` (tab Operasional), dan `onOpenDelivery` (deep-link notifikasi).
 * Minor 1 audit final-fix-2: dulu tersalin 3× di file ini (drift risk — satu
 * diperbarui, dua lainnya lupa). Tiap pemanggil menambah kunci sendiri
 * (`hub`/`input`/`history`) dan fallback berbeda DI ATAS fungsi ini —
 * perbedaan itu SENGAJA, jangan disamakan.
 */
private fun deliveryStageRoute(key: String): String? = when (key) {
    "diskon" -> ROUTE_DLV_DISKON
    "pdi" -> ROUTE_DLV_PDI
    "aki" -> ROUTE_DLV_AKI
    "kasir" -> ROUTE_DLV_KASIR
    "note" -> ROUTE_DLV_NOTE
    "jadwal" -> ROUTE_DLV_SCHEDULE
    "driver" -> ROUTE_DLV_DRIVER
    else -> null
}

/**
 * Peta `navKey` (kontrak `ActivityRegistry.ACTIVITY_ITEMS`) → route child di
 * tabel ini. Fungsi MURNI (tanpa Compose) supaya bisa diuji JUnit biasa —
 * `navKey` adalah kontrak stringly-typed tanpa pemeriksa kompiler, jadi satu
 * salah ketik di sini berarti kartu yang diam tak melakukan apa-apa. `null`
 * berarti `navKey` tak dikenal (typo) — KECUALI `"inventory"` dan
 * `"cari_semua"`, yang sengaja tak masuk peta ini karena punya NavHost/tab sendiri
 * dan dibuka lewat callback pindah-tab, bukan navigasi di tabel route ini (lihat
 * pemanggil di [ActivityNavHost]). Diuji di `ActivityNavHostRouteTest`.
 */
internal fun routeForNavKey(navKey: String): String? = when (navKey) {
    "absen" -> ROUTE_ABSEN
    // Bukan item registri (tombol kecil di baris "PINTASAN"), tapi tetap lewat
    // peta ini supaya kontraknya diuji sama seperti navKey lain.
    "panduan_alur" -> ROUTE_PANDUAN_ALUR
    // Laporan aktivitas: layar karyawan (mengisi) vs antrian PIC (menilai) —
    // dua route, sama pasangannya seperti bukti chat di bawah.
    //
    // Ejaan LAMA `raport`/`raport_review` tetap diterima. Bukan hiasan: server
    // mengirim `route` = navKey di payload push (`delivery_notif::route_for_kind`,
    // dipetakan `MainActivity` lewat fungsi INI), jadi navKey adalah kontrak
    // wire — bukan nama internal. Hari ini `route_for_kind` memang belum pernah
    // memancarkan navKey ber-raport (diperiksa: 22 cabangnya nol), tapi
    // membiarkan ejaan lama menganga berarti notifikasi lama/antrean yang
    // sempat tertahan mendarat di `null` alias TIDAK PINDAH LAYAR — gagal
    // senyap, bukan galat. Pola zero-lockout yang sama dengan
    // `kacab`->`kepala-cabang` dan `ads-manager`->`digital-team` di rust-shared.
    "aktivitas", "raport" -> ROUTE_AKTIVITAS
    "aktivitas_review", "raport_review" -> ROUTE_AKTIVITAS_REVIEW
    // Komplain: satu pintu lapor + tiga antrian peran.
    "hs_lapor" -> ROUTE_HS_LAPOR
    "hs_triase" -> ROUTE_HS_TRIASE
    "hs_teknisi" -> ROUTE_HS_TEKNISI
    "hs_tarik" -> ROUTE_HS_TARIK
    "hs_driver" -> ROUTE_HS_DRIVER
    "pemasangan_ac" -> ROUTE_PEMASANGAN_AC
    // Bukti chat harian: layar karyawan (kirim) vs antrian kepala cabang (periksa).
    "indent" -> ROUTE_INDENT
    // Daftar sesi opname cabang (petugas yang ikut menghitung). Route-nya SUDAH
    // ter-mount sejak lama sebagai anak tab Operasional — kartu Activity cuma
    // menambah pintu, bukan layar baru.
    "opname" -> ROUTE_OPNAME
    // Antrian validasi unit opname ketik-manual (admin-stok).
    "opname_validasi" -> ROUTE_OPNAME_VALIDASI
    "spk_input" -> ROUTE_DLV_CREATE
    "spk_history" -> ROUTE_DLV_HISTORY
    "spk_gantung" -> ROUTE_DLV_PENDING_PAYMENT
    // Prospek/CRM: dulu dispesialkan lewat callback pindah-tab (LeadsNavHost
    // sejajar tab), kini route biasa di tabel ini seperti sibling lain.
    "crm" -> ROUTE_LEADS_LIST
    // Konsumen Gebyar. Kartunya di seksi ANTRIAN; gerbangnya kunci kemampuan
    // `kupon_gebyar.lihat` DITAMBAH vonis cabang dari server.
    "kupon_gebyar" -> ROUTE_KUPON_GEBYAR
    else -> deliveryStageRoute(navKey)
}

/**
 * SATU tabel route dipakai DUA tab: tab Activity memulai di
 * [ACTIVITY_ROUTE_ROOT], tab Operasional di [HOME_ROUTE_DASHBOARD]. Masing-masing
 * tab punya `NavHostController` sendiri, jadi keduanya berdiri sendiri.
 *
 * Sengaja tidak memecah jadi dua file: route anak (`home_dlv_*`, `home_opname`,
 * `home_spk_hub`, …) dipakai dari kedua sisi (kartu Activity, grid Akses Cepat
 * di Operasional, dan deep-link push FCM). Memecahnya berarti memindahkan route —
 * hal yang justru dilarang Global Constraints (nama route anak tak boleh berubah).
 */
@Composable
fun ActivityNavHost(
    startDestination: String = ACTIVITY_ROUTE_ROOT,
    onOpenSummaryTab: () -> Unit = {},
    onQuickAccessInventory: () -> Unit = {},
    /** Ubin "Cari Semua" → SEARCH_ROUTE_ROOT (`GlobalSearchScreen`), bukan daftar barang. */
    onQuickAccessSearch: () -> Unit = {},
    // Sisa I1: dinaikkan MainScreen tiap tab terpilih berubah JADI Activity (lihat
    // `activityTabSelectedTrigger` di MainActivity.kt) — diteruskan apa adanya ke
    // ActivityScreen, cuma dipakai di root Activity (bukan tab Operasional yang juga
    // memakai NavHost ini dgn startDestination beda).
    activityTabSelectedSignal: Int = 0,
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = {
            fadeIn(tween(300)) + slideInVertically(
                initialOffsetY = { it / 4 },
                animationSpec = tween(350, easing = EaseInOutQuart)
            )
        },
        exitTransition = { fadeOut(tween(300)) },
        popEnterTransition = { fadeIn(tween(300)) },
        popExitTransition = {
            fadeOut(tween(300)) + slideOutVertically(
                targetOffsetY = { it / 4 },
                animationSpec = tween(350, easing = EaseInOutQuart)
            )
        }
    ) {
        composable(ACTIVITY_ROUTE_ROOT) {
            ActivityScreen(
                onOpenNotifications = { navController.navigate(ROUTE_NOTIFICATIONS) { launchSingleTop = true } },
                onOpenAllMenus = onOpenSummaryTab,
                tabSelectedSignal = activityTabSelectedSignal,
                onOpen = { navKey ->
                    // "inventory" & "cari_semua" sengaja tak masuk `routeForNavKey`: keduanya
                    // punya NavHost/tab sendiri (InventoryNavHost), jadi dibuka lewat callback
                    // pindah-tab, bukan route di tabel ini. Mereka berbagi tab yang sama tapi
                    // BERBEDA tujuan — daftar barang vs pencarian gabungan; jangan disatukan.
                    // "crm" TIDAK lagi di sini sejak Prospek pindah jadi route biasa — jatuh
                    // ke cabang `else` seperti sibling lain.
                    when (navKey) {
                        "inventory" -> onQuickAccessInventory()
                        "cari_semua" -> onQuickAccessSearch()
                        else -> routeForNavKey(navKey)?.let { route ->
                            navController.navigate(route) { launchSingleTop = true }
                        }
                    }
                },
            )
        }
        composable(HOME_ROUTE_DASHBOARD) { entry ->
            val viewModel: HomeViewModel = hiltViewModel(entry)
            HomeScreen(
                viewModel = viewModel,
                onViewMoreBranches = {
                    navController.navigate("home_ranking/${RankingKind.BRANCH.name}") { launchSingleTop = true }
                },
                onViewMoreSales = {
                    navController.navigate("home_ranking/${RankingKind.SALES.name}") { launchSingleTop = true }
                },
                onBranchClick = { branch ->
                    navController.navigate(branchTransactionsRoute(branch.kodeDealer, branch.cabang)) { launchSingleTop = true }
                },
                onSalesClick = { sales ->
                    navController.navigate(salesTransactionsRoute(sales.sourceCode, sales.name)) { launchSingleTop = true }
                },
                onOpenNotifications = { navController.navigate(ROUTE_NOTIFICATIONS) { launchSingleTop = true } },
                onQuickAccessInventory = onQuickAccessInventory,
                onQuickAccessSearch = onQuickAccessSearch,
                // Sendiri (bukan diteruskan dari luar) — beda dari sebelumnya, karena
                // ActivityNavHost DIPASANG DUA KALI (Activity & Operasional) dengan
                // NavHostController masing-masing; hanya host ini yang tahu controller
                // mana yang harus dipakai.
                onQuickAccessLeads = { navController.navigate(ROUTE_LEADS_LIST) { launchSingleTop = true } },
                onQuickAccessIndent = { navController.navigate(ROUTE_INDENT) { launchSingleTop = true } },
                onQuickAccessSales = { navController.navigate(ROUTE_SALES) { launchSingleTop = true } },
                onQuickAccessOpname = { navController.navigate(ROUTE_OPNAME) { launchSingleTop = true } },
                onQuickAccessAbsen = { navController.navigate(ROUTE_ABSEN) { launchSingleTop = true } },
                onQuickAccessGaji = { navController.navigate(ROUTE_GAJI) { launchSingleTop = true } },
                onQuickAccessKpi = { navController.navigate(ROUTE_KPI) { launchSingleTop = true } },
                onQuickAccessHargaGs = { navController.navigate(ROUTE_HARGA_GS) { launchSingleTop = true } },
                onQuickAccessSerialInput = { navController.navigate(ROUTE_SERIAL_INPUT) { launchSingleTop = true } },
                onQuickAccessDeadstock = { navController.navigate(ROUTE_DEADSTOCK) { launchSingleTop = true } },
                onQuickAccessMutasiHistori = { navController.navigate(ROUTE_MUTASI_HISTORI) { launchSingleTop = true } },
                // Home Service: dua ubin Akses Cepat (2026-08-15). Route-nya sudah
                // lama ada di tabel ini — yang selama ini hilang cuma pintunya.
                onKomplainLapor = { navController.navigate(ROUTE_HS_LAPOR) { launchSingleTop = true } },
                onKomplainTugas = { navController.navigate(ROUTE_HS_TEKNISI) { launchSingleTop = true } },
                onPemasanganAcKontrol = {
                    navController.navigate(ROUTE_PEMASANGAN_AC_KONTROL) { launchSingleTop = true }
                },
                onVertel = { navController.navigate(ROUTE_VERTEL) { launchSingleTop = true } },
                onSpkMenu = { key ->
                    val route = when (key) {
                        "hub" -> ROUTE_SPK_HUB
                        "input" -> ROUTE_DLV_CREATE
                        "history" -> ROUTE_DLV_HISTORY
                        else -> deliveryStageRoute(key) ?: ROUTE_DLV_CREATE
                    }
                    navController.navigate(route) { launchSingleTop = true }
                },
                onOpenEvent = { eventId ->
                    navController.navigate(eventLeadRoute(eventId)) { launchSingleTop = true }
                }
            )
        }
        composable(
            route = ROUTE_RANKING,
            arguments = listOf(navArgument("kind") { type = NavType.StringType })
        ) {
            RankingListScreen(
                onBack = { navController.popBackStack() },
                onBranchClick = { branch ->
                    navController.navigate(branchTransactionsRoute(branch.kodeDealer, branch.cabang)) { launchSingleTop = true }
                },
                onSalesClick = { sales ->
                    navController.navigate(salesTransactionsRoute(sales.sourceCode, sales.name)) { launchSingleTop = true }
                }
            )
        }
        composable(
            route = ROUTE_TRANSACTIONS,
            arguments = listOf(
                navArgument("kind") { type = NavType.StringType },
                navArgument("code") { type = NavType.StringType },
                navArgument("name") { type = NavType.StringType; defaultValue = "" }
            )
        ) {
            TransactionListScreen(onBack = { navController.popBackStack() })
        }
        composable(ROUTE_NOTIFICATIONS) {
            NotificationCenterScreen(
                onBack = { navController.popBackStack() },
                // Tap notif delivery → langsung halaman tahap terkait (key sama
                // dgn onSpkMenu HomeScreen + deep-link push FcmService).
                onOpenDelivery = { key ->
                    val route = when (key) {
                        "history" -> ROUTE_DLV_HISTORY
                        // `routeForNavKey`, BUKAN `deliveryStageRoute`: yang kedua
                        // cuma tahu 7 kunci tahap SPK, jadi kunci non-SPK apa pun
                        // (mis. "opname_validasi") mendarat di hub SPK — layar
                        // salah, tanpa satu pun error.
                        else -> routeForNavKey(key) ?: ROUTE_SPK_HUB
                    }
                    navController.navigate(route) { launchSingleTop = true }
                },
                onOpenLeads = { navController.navigate(ROUTE_LEADS_LIST) { launchSingleTop = true } }
            )
        }
        composable(ROUTE_INDENT) {
            IndentListScreen(onBack = { navController.popBackStack() })
        }
        composable(ROUTE_OPNAME) {
            OpnameListScreen(onBack = { navController.popBackStack() })
        }
        composable(ROUTE_OPNAME_VALIDASI) {
            OpnameValidasiScreen(onBack = { navController.popBackStack() })
        }
        composable(ROUTE_ABSEN) {
            AttendanceScreen(onBack = { navController.popBackStack() })
        }
        composable(ROUTE_AKTIVITAS) {
            AktivitasScreen(
                onBack = { navController.popBackStack() },
                onLihatRiwayat = {
                    navController.navigate(ROUTE_AKTIVITAS_RIWAYAT) { launchSingleTop = true }
                },
            )
        }
        composable(ROUTE_AKTIVITAS_RIWAYAT) {
            AktivitasRiwayatScreen(onBack = { navController.popBackStack() })
        }
        composable(ROUTE_AKTIVITAS_REVIEW) {
            AktivitasReviewScreen(onBack = { navController.popBackStack() })
        }
        composable(ROUTE_HS_LAPOR) {
            HomeServiceLaporScreen(
                onBack = { navController.popBackStack() },
                onLihatTiket = { id ->
                    navController.navigate(hsDetailRoute(id)) { launchSingleTop = true }
                },
            )
        }
        listOf(
            ROUTE_HS_TRIASE to HsMode.TRIASE,
            ROUTE_HS_TEKNISI to HsMode.TEKNISI,
            ROUTE_HS_TARIK to HsMode.TARIK,
            ROUTE_HS_DRIVER to HsMode.DRIVER,
        ).forEach { (route, mode) ->
            composable(route) {
                HomeServiceListScreen(
                    mode = mode,
                    onBack = { navController.popBackStack() },
                    onOpen = { id ->
                        navController.navigate(hsDetailRoute(id)) { launchSingleTop = true }
                    },
                )
            }
        }
        composable(ROUTE_PEMASANGAN_AC) {
            AcInstallScreen(onBack = { navController.popBackStack() })
        }
        composable(ROUTE_PEMASANGAN_AC_KONTROL) {
            AcInstallScheduleScreen(onBack = { navController.popBackStack() })
        }
        composable(ROUTE_VERTEL) {
            VertelScreen(onBack = { navController.popBackStack() })
        }
        composable(
            route = ROUTE_HS_DETAIL,
            arguments = listOf(navArgument("id") { type = NavType.StringType })
        ) {
            HomeServiceDetailScreen(onBack = { navController.popBackStack() })
        }
        composable(ROUTE_GAJI) {
            PayrollScreen(onBack = { navController.popBackStack() })
        }
        composable(ROUTE_KPI) {
            KpiScreen(onBack = { navController.popBackStack() })
        }
        composable(ROUTE_HARGA_GS) {
            ErpPriceChangesScreen(onBack = { navController.popBackStack() })
        }
        composable(ROUTE_SERIAL_INPUT) {
            SerialInputScreen(onBack = { navController.popBackStack() })
        }
        composable(ROUTE_DEADSTOCK) {
            DeadstockScreen(onBack = { navController.popBackStack() })
        }
        composable(ROUTE_KUPON_GEBYAR) {
            KuponGebyarScreen(onBack = { navController.popBackStack() })
        }
        composable(ROUTE_MUTASI_HISTORI) {
            MutasiHistoriScreen(onBack = { navController.popBackStack() })
        }
        composable(ROUTE_PANDUAN_ALUR) {
            PanduanAlurScreen(onBack = { navController.popBackStack() })
        }
        composable(
            route = ROUTE_EVENT_LEAD,
            arguments = listOf(navArgument("eventId") { type = NavType.StringType })
        ) { entry ->
            EventLeadScreen(
                eventId = entry.arguments?.getString("eventId").orEmpty(),
                onBack = { navController.popBackStack() },
            )
        }
        composable(ROUTE_SPK_HUB) {
            SpkHubScreen(onBack = { navController.popBackStack() }, onNavigate = { key ->
                val route = when (key) {
                    "input" -> ROUTE_DLV_CREATE
                    "diskon" -> ROUTE_DLV_DISKON
                    "pdi" -> ROUTE_DLV_PDI
                    "aki" -> ROUTE_DLV_AKI
                    "kasir" -> ROUTE_DLV_KASIR
                    "note" -> ROUTE_DLV_NOTE
                    "jadwal" -> ROUTE_DLV_SCHEDULE
                    "driver" -> ROUTE_DLV_DRIVER
                    "history" -> ROUTE_DLV_HISTORY
                    else -> ROUTE_DLV_CREATE
                }
                navController.navigate(route) { launchSingleTop = true }
            })
        }
        composable(ROUTE_DLV_CREATE) {
            CreateSpkScreen(
                onBack = { navController.popBackStack() },
                onCreated = { id ->
                    // PDI Mandiri: ganti layar Input SPK dgn Detail (popUpTo dirinya sendiri)
                    // biar tombol back dari Detail balik ke Hub, bukan ke form SPK yg sudah terkirim.
                    navController.navigate(dlvDetailRoute(id)) {
                        popUpTo(ROUTE_DLV_CREATE) { inclusive = true }
                    }
                },
            )
        }
        composable(ROUTE_DLV_DISKON) {
            DiscountApprovalScreen(
                onBack = { navController.popBackStack() },
                onDetailSpk = { kode ->
                    navController.navigate(dlvDiskonDetailRoute(kode)) { launchSingleTop = true }
                },
            )
        }
        composable(
            route = ROUTE_DLV_DISKON_DETAIL,
            arguments = listOf(navArgument("kode") { type = NavType.StringType })
        ) { entry ->
            SpkDiskonDetailScreen(
                kode = entry.arguments?.getString("kode").orEmpty(),
                onBack = { navController.popBackStack() },
            )
        }
        composable(ROUTE_DLV_PDI) {
            // Cabang BOLEH di sini: rantai peran PDI non-history sengaja tidak
            // mengisi `filter.kode_dealer`, jadi penjaga `is_none()` di server
            // memakai pilihan klien apa adanya — murni menyempitkan.
            DeliveryQueueScreen("Antri PDI", DeliveryStatusKey.PENDING_PDI, onBack = { navController.popBackStack() },
                kontrolSaringan = KontrolSaringan(cari = true, cabang = true, urut = true),
                onOpen = { id -> navController.navigate(dlvDetailRoute(id)) { launchSingleTop = true } })
        }
        composable(ROUTE_DLV_AKI) {
            AkiListScreen(
                onBack = { navController.popBackStack() },
                // [dlvDetailRoute], BUKAN [dlvDiskonDetailRoute]: kartu aki
                // menunjuk satu JOB (unit), sementara detail diskon berkunci
                // kode batch SPK — dua route berbeda dengan bentuk id yang
                // mirip, jadi salah pilih tak menghasilkan galat kompilasi,
                // cuma halaman kosong.
                onDetailSpk = { id ->
                    navController.navigate(dlvDetailRoute(id)) { launchSingleTop = true }
                },
            )
        }
        composable(ROUTE_DLV_KASIR) {
            // SENGAJA TANPA `cabang`. Rantai kasir mengisi `filter.cabang_bayar`,
            // BUKAN `kode_dealer`, jadi penjaga `is_none()` di server tidak
            // menahan pilihan klien dan kedua klausa di-AND di SQL — memilih
            // cabang lain menghasilkan daftar KOSONG tanpa error, yang terbaca
            // sebagai "SPK saya hilang".
            DeliveryQueueScreen("Antri Kasir", DeliveryStatusKey.PENDING_SPK, onBack = { navController.popBackStack() },
                kontrolSaringan = KontrolSaringan(cari = true, urut = true),
                onOpen = { id -> navController.navigate(dlvDetailRoute(id)) { launchSingleTop = true } })
        }
        composable(ROUTE_DLV_NOTE) {
            DeliveryQueueScreen("Surat Jalan", DeliveryStatusKey.PENDING_DELIVERY_NOTE, onBack = { navController.popBackStack() },
                kontrolSaringan = KontrolSaringan(cari = true, cabang = true, urut = true),
                onOpen = { id -> navController.navigate(dlvDetailRoute(id)) { launchSingleTop = true } })
        }
        composable(ROUTE_DLV_SCHEDULE) {
            // `metode` HANYA di sini — server membacanya cuma di tahap ini, dan
            // artinya MEMBALIK default (kosong = buang self_pickup +
            // sales_delivery). Chip-nya melebarkan daftar, bukan menyempitkan.
            DeliveryQueueScreen("Penjadwalan", DeliveryStatusKey.PENDING_SCHEDULING, onBack = { navController.popBackStack() },
                kontrolSaringan = KontrolSaringan(cari = true, cabang = true, urut = true, metode = true),
                onOpen = { id -> navController.navigate(dlvDetailRoute(id)) { launchSingleTop = true } })
        }
        composable(ROUTE_DLV_DRIVER) {
            // Driver: backend meng-scope antrian (assigned + in_transit) berdasarkan role, tanpa filter status.
            // HANYA `cari`. JANGAN beri `urut`: layar ini punya pengurutan
            // manifest manual (`moveLoadSpk` → POST /delivery/driver/reorder),
            // dan urutan muatan ITULAH arti daftarnya — `urut=terlama` akan
            // menabraknya. Pemilih cabang pun tak berguna: muatan satu driver
            // hampir selalu satu cabang.
            DeliveryQueueScreen("Tugas Antar", status = null, reorderable = true, asDriver = true, onBack = { navController.popBackStack() },
                kontrolSaringan = KontrolSaringan(cari = true),
                onOpen = { id -> navController.navigate(dlvDetailRoute(id)) { launchSingleTop = true } })
        }
        composable(ROUTE_DLV_PENDING_PAYMENT) {
            // Server mengirim SEMUA unit terkirim yang belum dikonfirmasi; ambang
            // "gantung 24 jam" dihitung di kartu Activity, bukan di sini — kasir
            // tetap perlu bisa menutup yang baru sebelum jatuh tempo.
            DeliveryQueueScreen("Konfirmasi Pembayaran", status = null, view = "pending_payment", onBack = { navController.popBackStack() },
                kontrolSaringan = KontrolSaringan(cari = true),
                onOpen = { id -> navController.navigate(dlvDetailRoute(id)) { launchSingleTop = true } })
        }
        composable(ROUTE_DLV_HISTORY) {
            // SATU-SATUNYA pemakai `periodeFilter`: riwayat itu arsip, jadi
            // menyaringnya per periode aman. Enam layar lain di berkas ini
            // adalah antrian kerja — lihat KDoc `periodeFilter`.
            // TANPA `cabang`: di jalur history rantai peran SUDAH mengisi
            // `kode_dealer`, jadi penjaga `is_none()` membuat pilihan klien
            // diabaikan diam-diam — kontrolnya jadi tombol mati rasa. Kontrol
            // yang berbohong lebih merusak daripada kontrol yang tidak ada.
            DeliveryQueueScreen("Riwayat SPK", status = null, view = "history", periodeFilter = true,
                kontrolSaringan = KontrolSaringan(cari = true),
                onBack = { navController.popBackStack() },
                onOpen = { id -> navController.navigate(dlvDetailRoute(id)) { launchSingleTop = true } })
        }
        composable(
            route = ROUTE_DLV_DETAIL,
            arguments = listOf(navArgument("id") { type = NavType.StringType })
        ) { entry ->
            DeliveryJobDetailScreen(
                id = entry.arguments?.getString("id").orEmpty(),
                onBack = { navController.popBackStack() }
            )
        }
        composable(ROUTE_SALES) {
            SalesScreen(
                onBack = { navController.popBackStack() },
                onViewMoreBranches = {
                    navController.navigate("home_ranking/${RankingKind.BRANCH.name}") { launchSingleTop = true }
                },
                onViewMoreSales = {
                    navController.navigate("home_ranking/${RankingKind.SALES.name}") { launchSingleTop = true }
                },
                onBranchClick = { branch ->
                    navController.navigate(branchTransactionsRoute(branch.kodeDealer, branch.cabang)) { launchSingleTop = true }
                },
                onSalesClick = { sales ->
                    navController.navigate(salesTransactionsRoute(sales.sourceCode, sales.name)) { launchSingleTop = true }
                }
            )
        }
        composable(ROUTE_LEADS_LIST) { entry ->
            val listViewModel: LeadsListViewModel = hiltViewModel(entry)
            LeadsListScreen(
                viewModel = listViewModel,
                onBack = { navController.popBackStack() },
                onAddClick = { navController.navigate(ROUTE_LEADS_ADD) { launchSingleTop = true } },
                onLeadClick = { id -> navController.navigate("home_leads_detail/$id") { launchSingleTop = true } }
            )
        }
        composable(ROUTE_LEADS_ADD) { entry ->
            // ViewModel MILIK layar daftar, bukan instance baru — refresh() di bawah harus
            // menyegarkan cache yang sama yang ditampilkan daftar, kalau tidak lead baru
            // tak pernah muncul sampai layar daftar di-restart sendiri.
            // Kunci `entry` wajib: `remember {}` tanpa kunci membuat lint (dan Navigation)
            // menganggap entry-nya bisa basi lintas recomposition. Runtime-nya sama —
            // `entry` konstan selama lambda ini hidup — tapi invariannya jadi tertulis.
            val listEntry = remember(entry) { navController.getBackStackEntry(ROUTE_LEADS_LIST) }
            val listViewModel: LeadsListViewModel = hiltViewModel(listEntry)
            AddLeadScreen(
                onBack = { navController.popBackStack() },
                onLeadCreated = {
                    listViewModel.refresh()
                    navController.popBackStack()
                }
            )
        }
        composable(
            route = ROUTE_LEADS_DETAIL,
            arguments = listOf(navArgument("leadId") { type = NavType.LongType })
        ) {
            LeadDetailScreen(onBack = { navController.popBackStack() })
        }
    }
}
