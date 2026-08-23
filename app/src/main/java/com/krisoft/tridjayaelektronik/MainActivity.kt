package com.krisoft.tridjayaelektronik

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.EaseInOutQuart
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.krisoft.tridjayaelektronik.push.FcmService
import com.krisoft.tridjayaelektronik.ui.attendance.LocationProvider
import com.krisoft.tridjayaelektronik.ui.birthday.BirthdayPopupHost
import com.krisoft.tridjayaelektronik.ui.activity.ROUTE_SPK_HUB
import com.krisoft.tridjayaelektronik.ui.activity.ROUTE_DLV_DISKON
import com.krisoft.tridjayaelektronik.ui.activity.ROUTE_DLV_PDI
import com.krisoft.tridjayaelektronik.ui.activity.ROUTE_DLV_AKI
import com.krisoft.tridjayaelektronik.ui.activity.ROUTE_DLV_KASIR
import com.krisoft.tridjayaelektronik.ui.activity.ROUTE_DLV_NOTE
import com.krisoft.tridjayaelektronik.ui.activity.ROUTE_DLV_SCHEDULE
import com.krisoft.tridjayaelektronik.ui.activity.ROUTE_DLV_DRIVER
import com.krisoft.tridjayaelektronik.ui.activity.ROUTE_DLV_HISTORY
import com.krisoft.tridjayaelektronik.ui.activity.ROUTE_DLV_CREATE
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.krisoft.tridjayaelektronik.ui.security.SecurityBlockScreen
import com.krisoft.tridjayaelektronik.ui.security.SecurityGuard
import com.krisoft.tridjayaelektronik.ui.security.Threat
import androidx.compose.material3.CircularProgressIndicator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.krisoft.tridjayaelektronik.data.ThemePreferences
import com.krisoft.tridjayaelektronik.ui.activity.ACTIVITY_ROUTE_ROOT
import com.krisoft.tridjayaelektronik.ui.activity.ActivityNavHost
import com.krisoft.tridjayaelektronik.ui.activity.HOME_ROUTE_DASHBOARD
import com.krisoft.tridjayaelektronik.ui.activity.landsOnSummary
import com.krisoft.tridjayaelektronik.ui.activity.routeForNavKey
import com.krisoft.tridjayaelektronik.ui.home.effectiveRoles
import com.krisoft.tridjayaelektronik.ui.home.findLifecycle
import com.krisoft.tridjayaelektronik.ui.eksekutif.EKSEKUTIF_ROUTE_ROOT
import com.krisoft.tridjayaelektronik.ui.eksekutif.EksekutifNavHost
import com.krisoft.tridjayaelektronik.ui.inventory.InventoryNavHost
import com.krisoft.tridjayaelektronik.ui.inventory.SEARCH_ROUTE_ROOT
import com.krisoft.tridjayaelektronik.ui.login.ChangePasswordScreen
import com.krisoft.tridjayaelektronik.ui.login.ForgotPasswordScreen
import com.krisoft.tridjayaelektronik.ui.login.LoginScreen
import com.krisoft.tridjayaelektronik.ui.login.ResetPasswordScreen
import com.krisoft.tridjayaelektronik.ui.navigation.AppDestination
import com.krisoft.tridjayaelektronik.ui.session.SessionViewModel
import com.krisoft.tridjayaelektronik.data.update.UpdateStatus
import com.krisoft.tridjayaelektronik.ui.settings.SettingsScreen
import com.krisoft.tridjayaelektronik.ui.splash.SplashScreen
import com.krisoft.tridjayaelektronik.ui.update.UpdateDialog
import com.krisoft.tridjayaelektronik.ui.update.UpdateViewModel
import com.krisoft.tridjayaelektronik.ui.update.bolehTampilkanPrompt
import com.krisoft.tridjayaelektronik.ui.theme.TridjayaAppTheme
import com.krisoft.tridjayaelektronik.ui.theme.TridjayaFloatingNav
import com.krisoft.tridjayaelektronik.ui.theme.TridjayaNavItem
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

private const val ROUTE_SPLASH = "splash"
private const val ROUTE_LOGIN = "login"
private const val ROUTE_MAIN = "main"
private const val ROUTE_CHANGE_PW = "change_password" // forced (must_change_password)
private const val ROUTE_FORGOT_PW = "forgot_password"
private const val ROUTE_RESET_PW = "reset_password"

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var themePreferences: ThemePreferences

    // Compose state field on the Activity (not `remember`-scoped) so a tap-notification deep link
    // survives whichever screen (splash/login/main) happens to be composed when it arrives, and
    // `onNewIntent` (app already running) can update it from outside any composition.
    private var pendingNotifChannel by mutableStateOf<String?>(null)
    // Deep-link HALUS (key hub SPK) dari tap notif — buka langsung halaman tahap terkait.
    private var pendingNotifRoute by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Fallback ke DATA_KEY_CHANNEL: notif dirender OS sendiri (app background, payload
        // notification+data, FcmService.onMessageReceived tak dipanggil) meneruskan `data` FCM
        // sbg extras dgn key "channel", bukan EXTRA_NOTIF_CHANNEL punya kita.
        pendingNotifChannel = intent?.getStringExtra(FcmService.EXTRA_NOTIF_CHANNEL)
            ?: intent?.getStringExtra(FcmService.DATA_KEY_CHANNEL)
        pendingNotifRoute = intent?.getStringExtra(FcmService.EXTRA_NOTIF_ROUTE)
            ?: intent?.getStringExtra(FcmService.DATA_KEY_ROUTE)

        setContent {
            val themeState by themePreferences.state.collectAsState()
            TridjayaAppTheme(themeState = themeState) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SecurityGate {
                        TridjayaNavHost(
                            pendingNotifChannel = pendingNotifChannel,
                            pendingNotifRoute = pendingNotifRoute,
                            onConsumeNotifChannel = { pendingNotifChannel = null; pendingNotifRoute = null }
                        )
                    }
                }
            }
        }
    }

    /** App already running (FLAG_ACTIVITY_CLEAR_TOP reuses this instance) — new tap, new channel. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingNotifChannel = intent.getStringExtra(FcmService.EXTRA_NOTIF_CHANNEL)
            ?: intent.getStringExtra(FcmService.DATA_KEY_CHANNEL)
        pendingNotifRoute = intent.getStringExtra(FcmService.EXTRA_NOTIF_ROUTE)
            ?: intent.getStringExtra(FcmService.DATA_KEY_ROUTE)
    }
}

/**
 * Gerbang integritas: bila [SecurityGuard] mendeteksi aplikasi mock location / perangkat berbahaya,
 * seluruh aplikasi diganti dengan [SecurityBlockScreen] sampai ancaman dicopot. Deteksi diulang tiap
 * kali app kembali ke foreground (ON_RESUME) dan lewat tombol "Cek Ulang".
 */
@Composable
private fun SecurityGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    var recheckKey by remember { mutableStateOf(0) }

    // Deteksi (scan semua paket + cek root berbasis file) dijalankan di Dispatchers.IO — sebelumnya
    // sinkron di main thread saat start & tiap resume → risiko ANR/jank. Nilai lama DITAHAN saat
    // re-cek sehingga layar tak berkedip; hanya run pertama menampilkan status "memeriksa".
    //
    // Dulu ini `produceState(initialValue = null, recheckKey) { value = ... }` dan bentuk itu
    // memicu error lint ProduceStateDoesNotAssignValue — FALSE POSITIVE di compose-runtime 1.7.5
    // (BOM 2024.10.01). Pemicunya BUKAN bentuk penugasannya melainkan ADANYA argumen key:
    // dibuktikan dengan menjalankan lint atas tujuh varian sekaligus — `produceState<T?>(null) {
    // value = f() }` LOLOS, sedangkan keenam varian ber-key gagal semua, termasuk
    // `this.value = h` eksplisit dan versi dua langkah `val h = ...; value = h`. Jadi detektornya
    // memang tak pernah menemukan lambda `producer` begitu ada key di antara argumen.
    // remember + LaunchedEffect memberi semantik yang sama persis (key berubah → producer lama
    // dibatalkan, producer baru jalan, nilai lama tetap terpasang sampai hasil baru datang) tanpa
    // menekan error apa pun. Kalau nanti Compose naik versi, silakan uji ulang produceState.
    var threats by remember { mutableStateOf<List<Threat>?>(null) }
    LaunchedEffect(recheckKey) {
        threats = withContext(Dispatchers.IO) { SecurityGuard.detect(context) }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) recheckKey++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    when (val t = threats) {
        null -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        else -> if (t.isEmpty()) content() else SecurityBlockScreen(threats = t, onRecheck = { recheckKey++ })
    }
}

@Composable
private fun TridjayaNavHost(
    pendingNotifChannel: String? = null,
    pendingNotifRoute: String? = null,
    onConsumeNotifChannel: () -> Unit = {},
    sessionViewModel: SessionViewModel = hiltViewModel(),
    updateViewModel: UpdateViewModel = hiltViewModel()
) {
    val navController: NavHostController = rememberNavController()
    // Cached locally, available instantly — no network wait. SessionViewModel silently
    // validates/refreshes this in the background and the value here updates live if that fails.
    val isLoggedIn by sessionViewModel.sessionState.collectAsState()
    val mustChangePassword by sessionViewModel.mustChangePassword.collectAsState()

    val updateStatus by updateViewModel.status.collectAsState()
    val versiPromptDitutup by updateViewModel.versiPromptDitutup.collectAsState()
    val updateDownload by updateViewModel.download.collectAsState()

    // Pemeriksaan pembaruan diulang tiap app kembali ke foreground. Tanpa ini pemeriksaannya
    // cuma sekali di `UpdateViewModel.init`, dan karena ViewModel-nya hidup selama Activity
    // hidup, aplikasi yang dibuka pagi lalu dibiarkan hidup seharian tak pernah bertanya lagi.
    // Ambang jeda ada di ViewModel (`bolehCekOtomatis`) — ON_RESUME sering, permintaannya tidak.
    //
    // WAJIB lifecycle ACTIVITY lewat `findLifecycle()`, bukan `LocalLifecycleOwner.current`.
    // Di posisi ini keduanya kebetulan sama (TridjayaNavHost dikomposisi langsung di
    // `setContent`, di LUAR NavHost mana pun), tapi helper itulah yang tetap benar kalau
    // pemanggilnya nanti bergeser ke dalam NavHost — jebakan NavBackStackEntry yang mandek di
    // STARTED sudah dua kali menggigit di app ini (lihat `NotificationPermissionBanner` dan
    // `ActivityScreen`).
    val updateCtx = LocalContext.current
    val updateLifecycle = remember(updateCtx) { updateCtx.findLifecycle() }
    DisposableEffect(updateLifecycle, updateViewModel) {
        if (updateLifecycle == null) return@DisposableEffect onDispose {}
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) updateViewModel.onForeground()
        }
        updateLifecycle.addObserver(observer)
        onDispose { updateLifecycle.removeObserver(observer) }
    }

    // Single reactive source of truth for which gate we belong on: logout / background
    // session-invalidation → Login; server-flagged forced change → Change Password; otherwise Main.
    // Covers changes originating anywhere in the app without per-screen callbacks.
    LaunchedEffect(isLoggedIn, mustChangePassword) {
        val route = navController.currentDestination?.route
        when {
            !isLoggedIn -> {
                // Login + the public forgot/reset screens are all valid while logged out.
                val onAuthGate = route in setOf(ROUTE_SPLASH, ROUTE_LOGIN, ROUTE_FORGOT_PW, ROUTE_RESET_PW)
                if (!onAuthGate) navController.navigate(ROUTE_LOGIN) {
                    popUpTo(0) { inclusive = true }; launchSingleTop = true
                }
            }
            mustChangePassword -> {
                if (route != ROUTE_CHANGE_PW) navController.navigate(ROUTE_CHANGE_PW) {
                    popUpTo(0) { inclusive = true }; launchSingleTop = true
                }
            }
            // Logged in, no forced change: if the forced screen is still up (change just completed),
            // move into the app.
            route == ROUTE_CHANGE_PW -> navController.navigate(ROUTE_MAIN) {
                popUpTo(0) { inclusive = true }; launchSingleTop = true
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = ROUTE_SPLASH,
        // Smooth crossfade between the splash and the first real screen.
        enterTransition = { fadeIn(tween(400)) },
        exitTransition = { fadeOut(tween(400)) }
    ) {
        composable(ROUTE_SPLASH) {
            SplashScreen(
                onFinished = {
                    val dest = when {
                        !isLoggedIn -> ROUTE_LOGIN
                        mustChangePassword -> ROUTE_CHANGE_PW
                        else -> ROUTE_MAIN
                    }
                    navController.navigate(dest) {
                        popUpTo(ROUTE_SPLASH) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }
        composable(ROUTE_LOGIN) {
            LoginScreen(
                // Always go to Main; the gate LaunchedEffect redirects to Change Password if the
                // server flagged must_change_password.
                onLoginSuccess = {
                    navController.navigate(ROUTE_MAIN) {
                        popUpTo(ROUTE_LOGIN) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onForgotPassword = { navController.navigate(ROUTE_FORGOT_PW) { launchSingleTop = true } }
            )
        }
        composable(ROUTE_CHANGE_PW) {
            ChangePasswordScreen(
                forced = true,
                onDone = {
                    navController.navigate(ROUTE_MAIN) {
                        popUpTo(0) { inclusive = true }; launchSingleTop = true
                    }
                },
                onBack = {}
            )
        }
        composable(ROUTE_FORGOT_PW) {
            ForgotPasswordScreen(
                onBack = { navController.popBackStack() },
                onHaveCode = { navController.navigate(ROUTE_RESET_PW) { launchSingleTop = true } }
            )
        }
        composable(ROUTE_RESET_PW) {
            ResetPasswordScreen(
                onBack = { navController.popBackStack() },
                // Reset done → back to Login to sign in with the new password.
                onDone = {
                    navController.navigate(ROUTE_LOGIN) {
                        popUpTo(ROUTE_LOGIN) { inclusive = true }; launchSingleTop = true
                    }
                }
            )
        }
        composable(ROUTE_MAIN) {
            MainScreen(
                pendingNotifChannel = pendingNotifChannel,
                pendingNotifRoute = pendingNotifRoute,
                onConsumeNotifChannel = onConsumeNotifChannel,
                sessionViewModel = sessionViewModel
            )
        }
    }

    // Update gate — a force update blocks the whole app (over any screen incl. login) and its
    // download already auto-started (UpdateViewModel detects it); an optional update shows a
    // dismissible prompt, download starts on tap. AlertDialog renders in its own window above the
    // NavHost. "Nanti" kini menutup SATU VERSI saja (`bolehTampilkanPrompt`), bukan seluruh sesi:
    // menutup prompt 84 tak boleh ikut menelan prompt 85 yang terbit sesudahnya.
    //
    // `UpdateStatus.Unknown` (pemeriksaan gagal: 401/403/5xx, offline, timeout, TLS, gagal parse —
    // lihat `UpdateManager.check`) sengaja TIDAK menampilkan apa pun di sini. Orang sedang
    // bekerja; kegagalan jaringan biasa bukan alasan memasang dialog di atas layarnya. Pemeriksaan
    // ulangnya lebih cepat (`JEDA_CEK_GAGAL_MS`), dan jalur yang pengguna sendiri yang memintanya
    // — "Cek Pembaruan" di Settings — tetap melaporkan kegagalan apa adanya lewat
    // `updateCheckMessage`, jadi kegagalan tak pernah menyamar jadi "sudah versi terbaru".
    (updateStatus as? UpdateStatus.Available)?.let { available ->
        if (bolehTampilkanPrompt(available, versiPromptDitutup)) {
            UpdateDialog(
                available = available,
                download = updateDownload,
                onUpdate = { updateViewModel.startDownload() },
                onDismiss = if (available.force) null else ({ updateViewModel.dismissOptional() })
            )
        }
    }

    // Ucapan ulang tahun — sekali sehari, di atas layar mana pun setelah login
    // (cerminan popup web). Ditahan saat gate paksa-update sedang tampil: dua
    // dialog bertumpuk membuat tombol update tak bisa ditekan, dan update wajib
    // jelas lebih mendesak daripada ucapan.
    val forceUpdate = (updateStatus as? UpdateStatus.Available)?.force == true
    if (isLoggedIn && !mustChangePassword && !forceUpdate) {
        BirthdayPopupHost()
    }
}

@Composable
private fun DestinationContent(
    destination: AppDestination,
    onSettingsBack: () -> Unit,
    onCloseSearch: () -> Unit,
    onQuickAccessInventory: () -> Unit,
    onQuickAccessSearch: () -> Unit,
    onOpenSummaryTab: () -> Unit,
    inventoryOpenListSignal: Int,
    inventoryOpenSearchSignal: Int,
    activityTabSelectedSignal: Int,
    eksekutifNav: NavHostController,
    activityNav: NavHostController,
    summaryNav: NavHostController,
    inventoryNav: NavHostController
) {
    when (destination) {
        AppDestination.EKSEKUTIF -> EksekutifNavHost(navController = eksekutifNav)
        AppDestination.ACTIVITY -> ActivityNavHost(
            startDestination = ACTIVITY_ROUTE_ROOT,
            onOpenSummaryTab = onOpenSummaryTab,
            onQuickAccessInventory = onQuickAccessInventory,
            onQuickAccessSearch = onQuickAccessSearch,
            activityTabSelectedSignal = activityTabSelectedSignal,
            navController = activityNav
        )
        AppDestination.SUMMARY -> ActivityNavHost(
            startDestination = HOME_ROUTE_DASHBOARD,
            onQuickAccessInventory = onQuickAccessInventory,
            onQuickAccessSearch = onQuickAccessSearch,
            navController = summaryNav
        )
        AppDestination.INVENTORY -> InventoryNavHost(
            navController = inventoryNav,
            onCloseSearch = onCloseSearch,
            openListSignal = inventoryOpenListSignal,
            openSearchSignal = inventoryOpenSearchSignal,
            // Same "leave this tab, land on Home" semantics as closing search — reused here for
            // quick-access entries where there's nothing left in this tab's own back stack to pop.
            onExitToHome = onCloseSearch
        )
        AppDestination.SETTINGS -> SettingsScreen(onBack = onSettingsBack)
    }
}

/**
 * Minta izin lokasi + notifikasi begitu masuk area utama app (bukan menunggu user buka layar
 * Absensi/PDI dulu) — dua-duanya dipakai operasional (watermark GPS foto PDI/serah-terima/absensi,
 * push channel `delivery`/`crm`). Sekali per proses; sudah granted → no-op langsung.
 */
@Composable
private fun RequestOperationalPermissions() {
    val context = LocalContext.current
    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (!LocationProvider.hasPermission(context)) {
            locationLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(
    pendingNotifChannel: String? = null,
    pendingNotifRoute: String? = null,
    onConsumeNotifChannel: () -> Unit = {},
    sessionViewModel: SessionViewModel = hiltViewModel()
) {
    RequestOperationalPermissions()

    // Peta kemampuan hanya DITERUSKAN dari cermin `AuthRepository` (diisi
    // `ActivityViewModel`/`HomeViewModel` lewat `PenyegarKemampuan`) — MainScreen
    // TIDAK boleh mengambilnya sendiri: ia hidup seumur proses, jadi pengambilan
    // sekali di sini berarti gerbang tab beku sampai app dimatikan. Lihat
    // `PembacaPetaKemampuanTest`.
    val petaKemampuan by sessionViewModel.petaKemampuan.collectAsState()
    val destinations = AppDestination.visibleBottomNavItems(
        effectiveRoles(sessionViewModel.cachedUser),
        petaKemampuan,
    )
    // Tab awal DULU ditentukan sekali saja di komposisi pertama, dan komentar di
    // sini menerangkan alasannya: supaya profil yang termuat belakangan tak
    // membuat tab melompat sesudah user melihat/menyentuh layarnya.
    //
    // Alasan itu masih benar, TAPI premisnya tak lagi memadai sejak ada tab
    // ber-gate. Gerbangnya fail-closed dan bergantung pada `GET /api/me/
    // capabilities` yang tiba SESUDAH komposisi pertama — jadi "sekali di awal"
    // berarti tab Eksekutif tak akan pernah jadi layar pertama, padahal justru
    // itu yang diminta. (Bug lama yang sekaligus tertutup: `cachedUser` juga
    // masih `null` di komposisi pertama pada start dingin, jadi manager/owner
    // pun sudah lama mendarat di Activity alih-alih Operasional.)
    //
    // Yang dilakukan sekarang: hitung ulang tab pendaratan tiap kali pengetahuan
    // bertambah, tapi TERAPKAN hanya selama user belum memilih tab sendiri.
    // Begitu ia menyentuh pill (atau membuka notifikasi, atau menekan ubin yang
    // memindah tab), `tabDipilihUser` menyala dan hitungan ini berhenti
    // mengganggu — itulah bagian dari alasan lama yang tetap dipegang.
    var selected by remember { mutableStateOf(AppDestination.ACTIVITY) }
    // `remember`, BUKAN `rememberSaveable` — sengaja seumur `selected`. Kalau
    // penanda ini bertahan rotasi sementara `selected` tidak, layar kembali ke
    // ACTIVITY lalu MENETAP di sana karena auto-pendaratan sudah dianggap
    // "dibatalkan user" — yaitu tab yang salah, permanen, hanya karena HP
    // diputar. Keduanya lahir & mati bersama.
    var tabDipilihUser by remember { mutableStateOf(false) }
    /**
     * Satu-satunya jalan mengubah tab atas kehendak user. Memakainya (bukan
     * `selected = …` langsung) yang membuat auto-pendaratan berhenti — kalau ada
     * pemanggil yang lupa, layarnya akan "ditarik balik" ke tab awal pada emisi
     * peta kemampuan berikutnya.
     */
    val pilihTab: (AppDestination) -> Unit = { tujuan ->
        tabDipilihUser = true
        selected = tujuan
    }
    val tabPendaratan = AppDestination.tabAwal(
        tabTersedia = destinations,
        landsOnSummary = landsOnSummary(effectiveRoles(sessionViewModel.cachedUser)),
    )
    LaunchedEffect(tabPendaratan, tabDipilihUser) {
        if (!tabDipilihUser) selected = tabPendaratan
    }
    // Jaring pengaman terpisah dari auto-pendaratan: tab yang sedang dibuka bisa
    // LENYAP dari daftar di tengah sesi (akses dicabut admin → peta kemampuan
    // segar → EKSEKUTIF hilang). Tanpa ini `selected` menunjuk tab yang tak lagi
    // punya tombol di pill — isinya tetap terender dan tak ada cara keluar
    // selain tombol back. Berlaku juga sesudah user memilih tab sendiri; itu
    // memang maksudnya.
    LaunchedEffect(destinations) {
        if (selected !in destinations) selected = destinations.firstOrNull() ?: AppDestination.ACTIVITY
    }
    // Bumped by Home's "Akses Cepat" Inventory tile — see the LaunchedEffect inside
    // InventoryNavHost for why the actual navigate() call lives there, not here.
    var inventoryOpenListTrigger by remember { mutableStateOf(0) }
    // Kembarannya untuk ubin "Cari Semua": tab INVENTORY yang sama, tapi berhenti di
    // SEARCH_ROUTE_ROOT (pencarian gabungan produk+prospek) alih-alih daftar barang.
    var inventoryOpenSearchTrigger by remember { mutableStateOf(0) }

    // Sisa temuan I1 (merge-gate): tab Activity/Operasional sama-sama tetap ter-compose
    // (lihat `visitedDestinations` di bawah) — tukar tab murni TIDAK memicu lifecycle
    // maupun komposisi ulang apa pun, jadi angka/centang di Activity bisa basi kalau
    // ditinggal ke Operasional lalu balik lagi. Pola sama `inventoryOpenListTrigger`:
    // counter dinaikkan di sini (bukan di ActivityScreen) lalu dikonsumsi lewat
    // `LaunchedEffect(signal)` di ujung rantai. `previousSelected` dipakai supaya
    // counter HANYA naik saat tab BERUBAH MENJADI Activity (bukan tiap recomposition,
    // dan bukan saat tab lain dipilih) — nilai awal `null` sengaja tak dianggap
    // "berubah" walau `selected` awal memang ACTIVITY, jadi mount pertama tak ikut
    // menaikkan counter (ActivityScreen sendiri sudah punya `LaunchedEffect(Unit)`
    // untuk itu → tanpa guard ini keduanya akan fetch dobel di layar pertama).
    var activityTabSelectedTrigger by remember { mutableStateOf(0) }
    var previousSelectedForActivitySignal by remember { mutableStateOf<AppDestination?>(null) }
    LaunchedEffect(selected) {
        if (selected == AppDestination.ACTIVITY &&
            previousSelectedForActivitySignal != null &&
            previousSelectedForActivitySignal != AppDestination.ACTIVITY
        ) {
            activityTabSelectedTrigger++
        }
        previousSelectedForActivitySignal = selected
    }

    // Hoisted so we can watch each tab's inner route and hide the floating nav on detail screens.
    val eksekutifNav = rememberNavController()
    val activityNav = rememberNavController()
    val summaryNav = rememberNavController()
    val inventoryNav = rememberNavController()
    // "Semua menu →" di Activity = pindah tab, bukan navigasi dalam tab.
    val onOpenSummaryTab: () -> Unit = { pilihTab(AppDestination.SUMMARY) }
    val onQuickAccessInventory: () -> Unit = {
        pilihTab(AppDestination.INVENTORY)
        inventoryOpenListTrigger++
    }
    // Ubin "Cari Semua" (Activity → PINTASAN). Tab yang sama dengan "Cari Barang", tujuan
    // berbeda: SENGAJA tidak menaikkan `inventoryOpenListTrigger`, sebab sinyal itulah yang
    // mem-pop SEARCH_ROUTE_ROOT dan membuat pencarian gabungan tak terjangkau sejak 41f570d.
    val onQuickAccessSearch: () -> Unit = {
        pilihTab(AppDestination.INVENTORY)
        inventoryOpenSearchTrigger++
    }

    // Deep-link tap-notifikasi → layar relevan. One-shot: dijalankan sekali per nilai channel baru
    // lalu langsung dikonsumsi (di-null-kan) supaya tak ternavigasi ulang saat MainScreen
    // recompose karena alasan lain (mis. ganti tab manual sesudahnya).
    LaunchedEffect(pendingNotifChannel, pendingNotifRoute) {
        when (pendingNotifChannel) {
            "delivery" -> {
                // Komplen (home service) MENUMPANG channel ini — satu channel
                // notifikasi lapangan — tapi layarnya BUKAN turunan hub SPK.
                // Karena itu kunci `hs_*` dipisahkan dan dipetakan lewat
                // `routeForNavKey`, peta yang sama dipakai kartu Activity.
                //
                // Tanpa pemisahan ini, cabang di bawah membuka hub SPK LEBIH
                // DULU tanpa syarat — jadi teknisi yang menerima "Tugas home
                // service" mendarat di layar SPK yang tak ada hubungannya
                // dengan pemberitahuannya, dan itulah perilaku sampai 2026-08-15
                // (route-nya bahkan dikirim `null`, jadi tak ada yang bisa
                // membedakannya dari notifikasi SPK biasa).
                val komplen = pendingNotifRoute
                    ?.takeIf { it.startsWith("hs_") }
                    ?.let { routeForNavKey(it) }
                if (komplen != null) {
                    pilihTab(AppDestination.ACTIVITY)
                    activityNav.navigate(komplen) { launchSingleTop = true }
                    return@LaunchedEffect
                }
                pilihTab(AppDestination.ACTIVITY)
                activityNav.navigate(ROUTE_SPK_HUB) { launchSingleTop = true }
                // Deep-link halus: buka LANGSUNG halaman tahap terkait (di atas hub, jadi
                // back → hub). Route dari payload FCM (delivery_notif route_for_kind).
                val sub = when (pendingNotifRoute) {
                    "diskon" -> ROUTE_DLV_DISKON
                    "pdi" -> ROUTE_DLV_PDI
                    "aki" -> ROUTE_DLV_AKI
                    "kasir" -> ROUTE_DLV_KASIR
                    "note" -> ROUTE_DLV_NOTE
                    "jadwal" -> ROUTE_DLV_SCHEDULE
                    "driver" -> ROUTE_DLV_DRIVER
                    "history" -> ROUTE_DLV_HISTORY
                    "input" -> ROUTE_DLV_CREATE
                    else -> null
                }
                if (sub != null) activityNav.navigate(sub) { launchSingleTop = true }
            }
            // Notif persetujuan (absen/izin, dan sejak 2026-07-29 inden). Backend
            // mengirim `route` = navKey layar tujuan (`delivery_notif::route_for_kind`,
            // mis. "indent"), dipetakan lewat `routeForNavKey` — peta yang SAMA
            // dengan kartu Activity, jadi navKey baru dari server tak menuntut
            // cabang `when` baru di sini. Tanpa `route` yang dikenal: notif tetap
            // tampil, tap-nya cuma membuka app (perilaku lama channel ini).
            "approval" -> {
                val sub = pendingNotifRoute?.let { routeForNavKey(it) }
                if (sub != null) {
                    pilihTab(AppDestination.ACTIVITY)
                    activityNav.navigate(sub) { launchSingleTop = true }
                }
            }
            // Prospek/CRM (dulu tab LEADS sendiri) kini route biasa di ActivityNavHost —
            // pola SAMA dengan cabang "approval" di atas: `routeForNavKey` yang sama
            // dipakai kartu Activity, jadi jalur ini otomatis ikut kalau navKey-nya
            // berubah nama, tanpa `!!` (route tak dikenal → notif tetap tampil, tap-nya
            // cuma membuka app, bukan crash).
            "crm" -> {
                val sub = routeForNavKey("crm")
                if (sub != null) {
                    pilihTab(AppDestination.ACTIVITY)
                    activityNav.navigate(sub) { launchSingleTop = true }
                }
            }
            null -> return@LaunchedEffect
        }
        onConsumeNotifChannel()
    }

    val eksekutifEntry by eksekutifNav.currentBackStackEntryAsState()
    val activityEntry by activityNav.currentBackStackEntryAsState()
    val summaryEntry by summaryNav.currentBackStackEntryAsState()
    val inventoryEntry by inventoryNav.currentBackStackEntryAsState()

    // Show the bottom nav only on each tab's root list screen — hide it on any pushed detail
    // (product/lead/ranking/add) and on Settings, so those full-screen sub-pages own the frame.
    val showBottomNav = when (selected) {
        AppDestination.EKSEKUTIF -> eksekutifEntry?.destination?.route == EKSEKUTIF_ROUTE_ROOT
        AppDestination.ACTIVITY -> activityEntry?.destination?.route == ACTIVITY_ROUTE_ROOT
        AppDestination.SUMMARY -> summaryEntry?.destination?.route == HOME_ROUTE_DASHBOARD
        // Inventory tak lagi punya slot di pill (tombol Cari dihapus 2026-07-29) — ia dibuka
        // sebagai layar penuh dari Activity/Operasional, jadi nav-nya tetap disembunyikan.
        // Back tetap punya jalan keluar: BackHandler di bawah memulangkan ke Activity.
        AppDestination.INVENTORY -> false
        // Settings kini salah satu tab pill — pill-nya HARUS ikut tampil di sana,
        // kalau tidak tab yang baru dipilih langsung menyembunyikan alat untuk
        // pindah tab lagi (hanya tombol back yang tersisa).
        AppDestination.SETTINGS -> true
    }

    // Tab switching here is driven by `selected` state, not a NavController, so system back has
    // nothing on a back stack to pop when a non-Activity tab is at its root — it would fall straight
    // through and exit the app (e.g. pressing back on the Cari/global-search screen). This single
    // handler owns back for the *selected* tab: pop that tab's own nested stack first (detail →
    // list), then fall back to Activity, and only exit from Activity's root. Reading the observed
    // entry keeps `canPopSelected` fresh across navigations.
    val selectedNav: NavHostController? = when (selected) {
        AppDestination.EKSEKUTIF -> eksekutifNav
        AppDestination.ACTIVITY -> activityNav
        AppDestination.SUMMARY -> summaryNav
        AppDestination.INVENTORY -> inventoryNav
        AppDestination.SETTINGS -> null
    }
    val selectedEntry = when (selected) {
        AppDestination.EKSEKUTIF -> eksekutifEntry
        AppDestination.ACTIVITY -> activityEntry
        AppDestination.SUMMARY -> summaryEntry
        AppDestination.INVENTORY -> inventoryEntry
        AppDestination.SETTINGS -> null
    }
    val canPopSelected = selectedEntry != null && selectedNav?.previousBackStackEntry != null

    Scaffold(
        // Each destination owns its own header/insets; the floating nav overlays content and
        // consumes its own nav-bar inset, so the Scaffold reserves nothing.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        fun navItem(destination: AppDestination) = TridjayaNavItem(
            icon = destination.icon,
            label = destination.label,
            selected = selected == destination,
            onClick = { pilihTab(destination) }
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                // Every tab is composed once (on first visit) and then kept alive for the rest of
                // the session — only its visibility toggles. This is what stops switching tabs from
                // tearing down and recreating each tab's NavHost/ViewModels (and re-fetching data)
                // every single time.
                val visitedDestinations = remember { mutableStateListOf(selected) }
                LaunchedEffect(selected) {
                    if (selected !in visitedDestinations) visitedDestinations.add(selected)
                }
                // Horizontal shared-axis slide between tabs (Rhythm-style), instead of an instant
                // swap. Kept-alive tabs just animate translationX/alpha — nothing is torn down. A
                // tab left of the selected one sits one screen-width to the left, one to the right
                // sits to the right; switching slides the incoming tab in from its side.
                val selectedOrder = tabOrder(selected)
                Box(modifier = Modifier.fillMaxSize()) {
                    visitedDestinations.forEach { destination ->
                        val isActive = destination == selected
                        val targetOffset = (tabOrder(destination) - selectedOrder)
                            .toFloat().coerceIn(-1f, 1f)
                        // Match Rhythm's horizontal shared-axis timing: 500ms slide, 400ms fade.
                        val slide by animateFloatAsState(
                            targetValue = targetOffset,
                            animationSpec = tween(durationMillis = 500, easing = EaseInOutQuart),
                            label = "tab_slide"
                        )
                        val tabAlpha by animateFloatAsState(
                            targetValue = if (isActive) 1f else 0f,
                            animationSpec = tween(durationMillis = 400),
                            label = "tab_alpha"
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .zIndex(if (isActive) 1f else 0f)
                                .graphicsLayer {
                                    translationX = slide * size.width
                                    alpha = tabAlpha
                                }
                                .blockInputWhen(disabled = !isActive)
                        ) {
                            DestinationContent(
                                destination = destination,
                                onSettingsBack = { pilihTab(AppDestination.ACTIVITY) },
                                onCloseSearch = { pilihTab(AppDestination.ACTIVITY) },
                                onQuickAccessInventory = onQuickAccessInventory,
                                onQuickAccessSearch = onQuickAccessSearch,
                                onOpenSummaryTab = onOpenSummaryTab,
                                inventoryOpenListSignal = inventoryOpenListTrigger,
                                inventoryOpenSearchSignal = inventoryOpenSearchTrigger,
                                activityTabSelectedSignal = activityTabSelectedTrigger,
                                eksekutifNav = eksekutifNav,
                                activityNav = activityNav,
                                summaryNav = summaryNav,
                                inventoryNav = inventoryNav
                            )
                        }
                    }
                }
            }

            // Rhythm layout: floating pill (Activity + Operasional) overlaying
            // the content — the content scrolls behind it instead of being pushed above a bar.
            // (Scrollable screens add ~100dp bottom clearance so nothing hides permanently.)
            // Slides away on detail/sub-screens.
            AnimatedVisibility(
                visible = showBottomNav,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                TridjayaFloatingNav(pillItems = destinations.map { navItem(it) })
            }
        }
    }

    // Composed after the Scaffold so it registers last and takes priority over the (kept-alive)
    // per-tab NavHosts' own back callbacks — this stops a background tab from stealing a back press
    // and routes it to the selected tab instead. Disabled only on Activity's root (the new first
    // tab), where back should exit the app as usual.
    BackHandler(enabled = canPopSelected || selected != AppDestination.ACTIVITY) {
        when {
            canPopSelected -> selectedNav?.popBackStack()
            else -> pilihTab(AppDestination.ACTIVITY)
        }
    }
}

/** Left-to-right screen order used to decide which side a tab slides in from on switch. */
private fun tabOrder(destination: AppDestination): Int = when (destination) {
    AppDestination.EKSEKUTIF -> -1
    AppDestination.ACTIVITY -> 0
    AppDestination.SUMMARY -> 1
    AppDestination.INVENTORY -> 2
    AppDestination.SETTINGS -> 3
}

/** Swallows all pointer input for this subtree so an off-screen (alpha=0) kept-alive tab can't steal touches from the active one. */
private fun Modifier.blockInputWhen(disabled: Boolean): Modifier {
    if (!disabled) return this
    return this.pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
            }
        }
    }
}
