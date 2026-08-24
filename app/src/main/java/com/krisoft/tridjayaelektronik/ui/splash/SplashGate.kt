package com.krisoft.tridjayaelektronik.ui.splash

/**
 * Keputusan "sesudah splash, ke mana?" — FUNGSI MURNI, supaya aturannya bisa
 * diuji tanpa emulator (audit keamanan 2026-08 temuan 3.3).
 *
 * MASALAH YANG DITUTUP. `sessionState` lahir `false` dan baru terisi setelah
 * `TokenStore.warmUp()` membaca DataStore di `Dispatchers.IO` — tanpa
 * koordinasi apa pun dengan komposisi pertama. Splash yang memutuskan pada
 * jendela itu mengirim orang yang SUDAH login ke layar Login, dan gate
 * `LaunchedEffect` di `TridjayaNavHost` tak menariknya kembali (cabangnya hanya
 * menangani kasus logout). Hasilnya: sesi masih sah, tapi orangnya diminta
 * mengetik ulang password — tanpa satu pun error, dan hanya pada cold-start yang
 * kebetulan kalah cepat. Di HP lambat itu bukan kebetulan yang jarang.
 */
enum class TujuanSplash { LOGIN, GANTI_PASSWORD, UTAMA }

/**
 * `null` = BELUM BOLEH memutuskan, tahan splash.
 *
 * [sesiTerbaca] adalah penanda dari `TokenStore` bahwa sesi sudah dibaca dari
 * disk; [batasTungguLewat] adalah klep pengaman waktu.
 *
 * KENAPA ADA KLEP WAKTU. Menunggu tanpa batas menukar satu kegagalan dengan
 * yang lebih buruk: DataStore yang tersendat (Keystore lambat, disk penuh,
 * berkas rusak) akan membuat app berhenti di splash SELAMANYA — dan orang di
 * lapangan tak punya jalan keluar selain menutup app. Saat batasnya lewat kita
 * putuskan dengan nilai yang ada; kalau ternyata salah, akibatnya "harus login
 * ulang", persis keadaan sebelum perbaikan ini dan bukan lebih buruk.
 */
fun tujuanSetelahSplash(
    sesiTerbaca: Boolean,
    batasTungguLewat: Boolean,
    login: Boolean,
    wajibGantiPassword: Boolean,
): TujuanSplash? {
    if (!sesiTerbaca && !batasTungguLewat) return null
    return when {
        !login -> TujuanSplash.LOGIN
        wajibGantiPassword -> TujuanSplash.GANTI_PASSWORD
        else -> TujuanSplash.UTAMA
    }
}

/** Batas tunggu penanda sesi. Cukup longgar untuk HP lambat, cukup pendek untuk tak terasa mati. */
const val BATAS_TUNGGU_SESI_MS = 3_000L
