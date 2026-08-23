package com.krisoft.tridjayaelektronik.ui.eksekutif

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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

const val EKSEKUTIF_ROUTE_ROOT = "eksekutif_papan"
private const val ROUTE_CABANG = "eksekutif_cabang/{kodeDealer}"

/**
 * `kode_dealer` bentuknya `D-01` — tak ada karakter yang perlu di-escape hari
 * ini, tapi ia datang dari server dan bukan dari konstanta di sini. Encode-nya
 * ongkos nol; melewatkannya adalah kelas bug yang sudah menjatuhkan app di
 * `InventoryNavHost` (kode barang ber-`/` memecah pola rute lalu `navigate`
 * melempar `IllegalArgumentException`).
 */
private fun ruteCabang(kodeDealer: String) = "eksekutif_cabang/${Uri.encode(kodeDealer)}"

/**
 * Tab "Eksekutif" — papan pantau lintas cabang untuk superadmin.
 *
 * Dua route, bukan state-swap di dalam satu layar: pill bawah HARUS hilang di
 * layar detail (`MainActivity.showBottomNav` menilainya dari route), dan
 * state-swap tak punya route untuk dinilai.
 *
 * **Satu ViewModel dipakai bersama kedua route**, diambil lewat entri back-stack
 * route AKAR. Kalau tiap route punya instansnya sendiri, layar detail akan
 * memuat ulang seluruh papan hanya untuk tahu rentang tanggal yang sedang
 * dipilih — dan pilihan rentang di detail tak akan pernah sampai ke papan.
 */
@Composable
fun EksekutifNavHost(
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController = navController,
        startDestination = EKSEKUTIF_ROUTE_ROOT,
        // Transisi sub-layar Rhythm yang sama dengan NavHost tab lain.
        enterTransition = {
            fadeIn(tween(300)) + slideInVertically(
                initialOffsetY = { it / 4 },
                animationSpec = tween(350, easing = EaseInOutQuart),
            )
        },
        exitTransition = { fadeOut(tween(300)) },
        popEnterTransition = { fadeIn(tween(300)) },
        popExitTransition = {
            fadeOut(tween(300)) + slideOutVertically(
                targetOffsetY = { it / 4 },
                animationSpec = tween(350, easing = EaseInOutQuart),
            )
        },
    ) {
        composable(EKSEKUTIF_ROUTE_ROOT) { entry ->
            val viewModel: EksekutifViewModel = hiltViewModel(entry)
            EksekutifScreen(
                viewModel = viewModel,
                onBukaCabang = { kode ->
                    viewModel.bukaCabang(kode)
                    navController.navigate(ruteCabang(kode)) { launchSingleTop = true }
                },
            )
        }
        composable(ROUTE_CABANG) { entriIni ->
            // ViewModel milik route AKAR, di-`remember` dengan kunci
            // `NavBackStackEntry` layar INI — bukan `navController`.
            //
            // Bedanya bukan gaya: `navController` adalah objek yang sama seumur
            // NavHost, jadi meng-`remember`-nya menahan entri AKAR yang mungkin
            // sudah dibuang dan dibuat ulang (proses di-restore, atau tumpukan
            // di-pop lalu diisi lagi) — ViewModel yang dipakai layar ini lalu
            // menempel pada entri MATI, sehingga `pilihRentang` di sini tak
            // pernah sampai ke papan. Lint `UnrememberedGetBackStackEntry`
            // menolak bentuk yang salah, dan itu satu-satunya pemeriksa yang
            // menangkapnya — gejalanya di lapangan cuma "layarnya tak ikut
            // berubah", tanpa galat.
            val akar = remember(entriIni) {
                navController.getBackStackEntry(EKSEKUTIF_ROUTE_ROOT)
            }
            val viewModel: EksekutifViewModel = hiltViewModel(akar)
            EksekutifCabangScreen(
                viewModel = viewModel,
                onBack = {
                    viewModel.tutupCabang()
                    navController.popBackStack()
                },
            )
        }
    }
}
