package com.krisoft.tridjayaelektronik.ui.splash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Penjaga temuan audit 3.3 — balapan cold-start.
 *
 * Yang dikunci di sini adalah satu kalimat: **selama sesi belum dibaca, splash
 * TIDAK memutuskan apa pun.** Versi sebelum perbaikan memutuskan dari
 * `sessionState` yang masih `false` karena belum dibaca, lalu mengirim orang
 * yang sudah login ke layar Login — dan tak ada cabang navigasi yang
 * menariknya kembali.
 */
class SplashGateTest {

    @Test
    fun sesi_belum_terbaca_dan_batas_belum_lewat_menahan_keputusan() {
        assertNull(
            "splash tak boleh memutuskan sebelum sesi dibaca",
            tujuanSetelahSplash(sesiTerbaca = false, batasTungguLewat = false, login = false, wajibGantiPassword = false),
        )
        // Termasuk saat nilai `login` KEBETULAN sudah true — belum terbaca
        // berarti belum bisa dipercaya, ke arah mana pun.
        assertNull(
            tujuanSetelahSplash(sesiTerbaca = false, batasTungguLewat = false, login = true, wajibGantiPassword = false),
        )
    }

    @Test
    fun sesi_terbaca_dan_login_masuk_utama() {
        assertEquals(
            TujuanSplash.UTAMA,
            tujuanSetelahSplash(sesiTerbaca = true, batasTungguLewat = false, login = true, wajibGantiPassword = false),
        )
    }

    @Test
    fun sesi_terbaca_tanpa_login_ke_layar_login() {
        assertEquals(
            TujuanSplash.LOGIN,
            tujuanSetelahSplash(sesiTerbaca = true, batasTungguLewat = false, login = false, wajibGantiPassword = false),
        )
    }

    /** Gerbang ganti-password WAJIB menang atas UTAMA, sama seperti gate lama. */
    @Test
    fun wajib_ganti_password_menang_atas_utama() {
        assertEquals(
            TujuanSplash.GANTI_PASSWORD,
            tujuanSetelahSplash(sesiTerbaca = true, batasTungguLewat = false, login = true, wajibGantiPassword = true),
        )
    }

    /** Tapi TIDAK menang atas Login — orang yang belum login tak punya password untuk diganti. */
    @Test
    fun wajib_ganti_password_tak_berlaku_saat_belum_login() {
        assertEquals(
            TujuanSplash.LOGIN,
            tujuanSetelahSplash(sesiTerbaca = true, batasTungguLewat = false, login = false, wajibGantiPassword = true),
        )
    }

    /**
     * Klep waktu: DataStore yang tersendat tak boleh menahan app di splash
     * selamanya. Sesudah batasnya lewat, diputuskan dengan nilai yang ada —
     * akibat terburuknya "harus login ulang", persis keadaan sebelum perbaikan
     * dan bukan lebih buruk.
     */
    @Test
    fun batas_tunggu_lewat_memaksa_keputusan_walau_sesi_belum_terbaca() {
        assertEquals(
            TujuanSplash.LOGIN,
            tujuanSetelahSplash(sesiTerbaca = false, batasTungguLewat = true, login = false, wajibGantiPassword = false),
        )
        assertEquals(
            TujuanSplash.UTAMA,
            tujuanSetelahSplash(sesiTerbaca = false, batasTungguLewat = true, login = true, wajibGantiPassword = false),
        )
    }

    @Test
    fun batas_tunggu_cukup_longgar_untuk_hp_lambat_tapi_tak_terasa_mati() {
        // Angkanya bagian dari kontrak: menaikkannya jadi puluhan detik membuat
        // app terlihat mati; menurunkannya ke ratusan milidetik mengembalikan
        // balapan yang justru ditutup.
        assert(BATAS_TUNGGU_SESI_MS in 1_000L..5_000L)
    }
}
