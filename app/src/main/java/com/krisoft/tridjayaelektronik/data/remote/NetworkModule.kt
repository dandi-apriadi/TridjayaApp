package com.krisoft.tridjayaelektronik.data.remote

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.krisoft.tridjayaelektronik.BuildConfig
import com.krisoft.tridjayaelektronik.data.TokenStore
import com.krisoft.tridjayaelektronik.data.AlasanSesiBerakhir
import com.krisoft.tridjayaelektronik.data.model.ApiErrorResponse
import com.krisoft.tridjayaelektronik.data.model.RefreshRequest
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.CertificatePinner
import okhttp3.Dispatcher
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json as KJson

object NetworkModule {

    /**
     * Audit S-1 (2026-08): certificate pinning SPKI untuk host produksi. Koneksi
     * lolos bila SALAH SATU pin cocok dengan sertifikat di rantai yang disajikan
     * server, dan tiga tingkatan dipasang sekaligus:
     *  - leaf `CN=tridjaya.com` (rotasi rutin ~90 hari),
     *  - intermediate Google Trust Services `WE1`,
     *  - root `GTS Root R4` (stabil bertahun-tahun).
     * Dengan begitu pembaruan leaf tidak pernah memutus app di lapangan — yang
     * menahan koneksi adalah kunci publik intermediate/root yang stabil. App
     * hanya boleh kehilangan ketiganya sekaligus jika domain pindah CA total;
     * saat itu terjadi, rilis versi ber-pin baru SEBELUM rantai lama ditarik.
     *
     * Regenerasi pin (jalankan dari mesin dev):
     *   openssl s_client -connect tridjaya.com:443 -servername tridjaya.com \
     *     -showcerts </dev/null 2>/dev/null > chain.txt
     *   # pisahkan tiap blok BEGIN/END CERTIFICATE ke file .pem, lalu per file:
     *   openssl x509 -in cert.pem -pubkey -noout | openssl pkey -pubin -outform DER \
     *     | openssl dgst -sha256 -binary | base64
     *
     * Build `-PlocalApi` (base URL localhost/IP LAN) otomatis tak terpengaruh:
     * pin terikat pada hostname, bukan pada client.
     */
    private const val HOST_PRODUKSI = "tridjaya.com"

    private fun certificatePinner(): CertificatePinner = CertificatePinner.Builder()
        .add(HOST_PRODUKSI, "sha256/rzN988lCk9HeBwkB5NZ6LHlc/UNmGjukswQoZo8xW6I=")
        .add(HOST_PRODUKSI, "sha256/kIdp6NNEd8wsugYyyIYFsi1ylMCED3hZbSR8ZFsa/A4=")
        .add(HOST_PRODUKSI, "sha256/mEflZT5enoR1FuXLgYYGqnVEoZvmf9c2bVBpiOjYQ0c=")
        .build()

    /**
     * `internal`, bukan `private`: unit test memakai INSTANS INI untuk mengunci perilaku
     * converter yang benar-benar dipakai Retrofit. Menyalin konfigurasinya ke dalam test
     * berarti test-nya menguji salinan yang bisa melenceng diam-diam dari yang asli.
     */
    internal val json = KJson {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    @Volatile
    private var retrofit: Retrofit? = null
    private val retrofitLock = Any()

    /**
     * Builds (once) the authenticated Retrofit instance shared by every API interface. Double-checked
     * locking so two API providers initializing concurrently can't each build their own Retrofit +
     * TokenRefresher (which would break the single-refresher / single-use-refresh-token invariant).
     */
    private fun authenticatedRetrofit(tokenStore: TokenStore): Retrofit {
        retrofit?.let { return it }
        synchronized(retrofitLock) {
            retrofit?.let { return it }
            return buildAuthenticatedRetrofit(tokenStore).also { retrofit = it }
        }
    }

    private fun buildAuthenticatedRetrofit(tokenStore: TokenStore): Retrofit {
        // A dedicated Retrofit/OkHttp instance without the auth interceptor/authenticator,
        // used only for calling /api/auth/refresh so it can never recurse into itself.
        //
        // Timeout SENGAJA lebih pendek dari baseClientBuilder() + retry OkHttp DIMATIKAN:
        // TokenRefresher.refresh() blocking-synchronized SEMUA request lain yang lewat client
        // authenticatedRetrofit (bukan cuma request pemicu refresh) selama call ini berjalan.
        // Dengan timeout 15s+20s bawaan + retryOnConnectionFailure(true), satu refresh yang
        // stall di jaringan lapangan bisa nge-block SEMUA layar (riwayat SPK, approval diskon,
        // notifikasi, dst — apa pun yang lagi fetch bareng) sampai ~70 detik — persis gejala
        // "banyak menu delivery flow timeout" yang dilaporkan user (2026-07-24). Refresh gagal
        // cepat (6s connect, 8s read, sekali coba) jauh lebih baik daripada menyandera semua
        // request lain — token lama tetap dipakai (AuthHeaderInterceptor fallback ke token
        // lama saat refresh gagal), request individual retry lewat 401 Authenticator sendiri.
        val refreshClient = baseClientBuilder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .writeTimeout(8, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            // Audit S-2 lanjutan (2026-08-23): `redactHeader("Authorization")` di
            // baseClientBuilder() cuma menutup HEADER. Pada level BODY (aktif di
            // build debug) interceptor ini tetap mencetak seluruh badan request —
            // `RefreshRequest.refreshToken` mentah — dan badan respons —
            // `access_token`/`refresh_token` baru — apa adanya ke logcat. Client
            // ini HANYA memanggil /api/auth/refresh (lihat komentar di atas), jadi
            // membuang loggernya di sini tak mengurangi kegunaan log debug untuk
            // trafik lain — pola sama createProspekUploadApi/createAktivitasUploadApi.
            .apply { interceptors().removeAll { it is HttpLoggingInterceptor } }
            .build()
        val plainAuthApi = buildRetrofit(refreshClient).create(AuthApi::class.java)

        // Single refresher shared by the proactive interceptor and the 401 authenticator so only one
        // /auth/refresh ever fires per rotation (the refresh token is single-use).
        val refresher = TokenRefresher(tokenStore, plainAuthApi)

        val client = baseClientBuilder()
            .addInterceptor(AuthHeaderInterceptor(tokenStore, refresher))
            .authenticator(TokenRefreshAuthenticator(refresher, tokenStore))
            .build()

        return buildRetrofit(client)
    }

    /**
     * Audit S-2 lanjutan (2026-08-23): client TERPISAH dari yang dipakai API lain,
     * sama seperti `createProspekUploadApi`/`createAktivitasUploadApi` — bukan
     * karena badannya besar, tapi karena badannya RAHASIA. `AuthApi` membawa
     * `LoginRequest.password`, `ChangePasswordRequest.oldPassword`/`newPassword`,
     * dan `ResetPasswordRequest.newPassword` mentah di body; client bersama
     * (`baseClientBuilder()`) mencetak badan APA ADANYA ke logcat di build debug
     * — `redactHeader("Authorization")` cuma menutup HEADER, sama sekali tak
     * menyentuh body. Token cabut lewat revoke; password sering dipakai ulang di
     * sistem lain (72,3% password=username, catatan audit GS 2026-08-15), jadi
     * kebocorannya lebih berbahaya daripada kebocoran header yang sudah ditutup.
     */
    fun createAuthApi(tokenStore: TokenStore): AuthApi {
        val base = authenticatedRetrofit(tokenStore)
        val authClient = (base.callFactory() as OkHttpClient).newBuilder()
            .apply { interceptors().removeAll { it is HttpLoggingInterceptor } }
            .build()
        return base.newBuilder()
            .client(authClient)
            .build()
            .create(AuthApi::class.java)
    }

    fun createInventoryApi(tokenStore: TokenStore): InventoryApi =
        authenticatedRetrofit(tokenStore).create(InventoryApi::class.java)

    fun createSalesApi(tokenStore: TokenStore): SalesApi =
        authenticatedRetrofit(tokenStore).create(SalesApi::class.java)

    fun createCrmApi(tokenStore: TokenStore): CrmApi =
        authenticatedRetrofit(tokenStore).create(CrmApi::class.java)

    fun createAbsensiApi(tokenStore: TokenStore): AbsensiApi =
        authenticatedRetrofit(tokenStore).create(AbsensiApi::class.java)

    fun createEventApi(tokenStore: TokenStore): EventApi =
        authenticatedRetrofit(tokenStore).create(EventApi::class.java)

    fun createOffApi(tokenStore: TokenStore): OffApi =
        authenticatedRetrofit(tokenStore).create(OffApi::class.java)

    fun createAktivitasApi(tokenStore: TokenStore): AktivitasApi =
        authenticatedRetrofit(tokenStore).create(AktivitasApi::class.java)

    /**
     * Client unggah bukti PROSPEK. Timeout-nya lebih pendek dari raport karena
     * batasnya 8 MB, bukan 30 MB — tapi tetap jauh di atas 20 detik milik
     * client bersama, yang tak cukup untuk 8 MB di jaringan cabang.
     *
     * `HttpLoggingInterceptor` dibuang dengan alasan yang sama: pada level
     * `BODY` ia menyalin seluruh badan ke heap hanya untuk mencetak "binary
     * body omitted".
     */
    fun createProspekUploadApi(tokenStore: TokenStore): ProspekUploadApi {
        val base = authenticatedRetrofit(tokenStore)
        val uploadClient = (base.callFactory() as OkHttpClient).newBuilder()
            .writeTimeout(120, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.SECONDS)
            .apply { interceptors().removeAll { it is HttpLoggingInterceptor } }
            .build()
        return base.newBuilder()
            .client(uploadClient)
            .build()
            .create(ProspekUploadApi::class.java)
    }

    /**
     * Client khusus unggah bukti raport: badan sampai 30 MB tak akan selesai
     * dalam 20 detik milik client bersama, jadi ia butuh timeout sendiri.
     *
     * `HttpLoggingInterceptor` DIBUANG di sini: pada level `BODY` — yang aktif di build debug — ia
     * memanggil `requestBody.writeTo(Buffer())` untuk memeriksa isinya, jadi
     * seluruh video masuk heap dulu HANYA supaya bisa dicetak sebagai "binary
     * body omitted". Itu membatalkan seluruh gunanya [UriRequestBody] justru
     * di build yang dipakai menguji fitur ini di HP.
     */
    fun createAktivitasUploadApi(tokenStore: TokenStore): AktivitasUploadApi {
        val base = authenticatedRetrofit(tokenStore)
        val uploadClient = (base.callFactory() as OkHttpClient).newBuilder()
            .writeTimeout(300, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.SECONDS)
            .apply { interceptors().removeAll { it is HttpLoggingInterceptor } }
            .build()
        return base.newBuilder()
            .client(uploadClient)
            .build()
            .create(AktivitasUploadApi::class.java)
    }

    fun createHomeServiceApi(tokenStore: TokenStore): HomeServiceApi =
        authenticatedRetrofit(tokenStore).create(HomeServiceApi::class.java)

    /** Pemasangan AC (sisi petugas). Menumpang wildcard gateway
     *  `/api/inventory/delivery/{*rest}` yang sudah ada, jadi client-nya sama
     *  dengan API lain — tak ada base URL atau timeout khusus. */
    fun createAcInstallApi(tokenStore: TokenStore): AcInstallApi =
        authenticatedRetrofit(tokenStore).create(AcInstallApi::class.java)

    fun createDeviceApi(tokenStore: TokenStore): DeviceApi =
        authenticatedRetrofit(tokenStore).create(DeviceApi::class.java)

    fun createBirthdayApi(tokenStore: TokenStore): BirthdayApi =
        authenticatedRetrofit(tokenStore).create(BirthdayApi::class.java)

    fun createDeliveryFlowApi(tokenStore: TokenStore): DeliveryFlowApi =
        authenticatedRetrofit(tokenStore).create(DeliveryFlowApi::class.java)

    fun createNotificationsApi(tokenStore: TokenStore): NotificationsApi =
        authenticatedRetrofit(tokenStore).create(NotificationsApi::class.java)

    fun createPayrollApi(tokenStore: TokenStore): PayrollApi =
        authenticatedRetrofit(tokenStore).create(PayrollApi::class.java)

    fun createKpiApi(tokenStore: TokenStore): KpiApi =
        authenticatedRetrofit(tokenStore).create(KpiApi::class.java)

    fun createErpPriceChangesApi(tokenStore: TokenStore): ErpPriceChangesApi =
        authenticatedRetrofit(tokenStore).create(ErpPriceChangesApi::class.java)

    fun createDeadstockApi(tokenStore: TokenStore): DeadstockApi =
        authenticatedRetrofit(tokenStore).create(DeadstockApi::class.java)

    fun createApkApi(tokenStore: TokenStore): ApkApi =
        authenticatedRetrofit(tokenStore).create(ApkApi::class.java)

    // OkHttp default Dispatcher.maxRequestsPerHost = 5 — SEMUA request app ini
    // (auth, sales, delivery, notif, dst) satu host yang sama (API_BASE_URL),
    // jadi cap itu berlaku global, bukan per-endpoint. Layar yang nembak >5
    // request paralel (mis. dashboard sales: kpi+target+leaderboard+sparkline
    // = 4, ditambah notif badge dsb) bikin request ke-6+ ANTRI DIAM-DIAM di
    // client sebelum sempat connect — tak kelihatan di UI, user cuma lihat
    // "loading lama" lalu timeout. Root cause analisa 2026-07-24. Dinaikkan
    // ke 16 (workspace maxRequests tetap default 64, cukup).
    private val sharedDispatcher = Dispatcher().apply { maxRequestsPerHost = 16 }

    private fun baseClientBuilder(): OkHttpClient.Builder {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
        // Audit S-2 (2026-08): pada level BODY, interceptor ini mencetak semua
        // header request apa adanya — termasuk `Authorization: Bearer <JWT>`
        // yang sah — ke logcat. Redact header-nya supaya log debug tetap
        // berguna tanpa menyimpan sesi sungguhan di buffer log perangkat.
        logging.redactHeader("Authorization")
        return OkHttpClient.Builder()
            .dispatcher(sharedDispatcher)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .certificatePinner(certificatePinner())
            // SEBELUM logging: interceptor aplikasi berjalan sesuai urutan
            // penambahannya, jadi kalau logging didaftarkan lebih dulu ia mencatat
            // request yang belum bertanda versi dan log debug jadi menyesatkan.
            .addInterceptor(AppVersionInterceptor())
            .addInterceptor(logging)
    }

    private fun buildRetrofit(client: OkHttpClient): Retrofit {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }
}

/**
 * Owns the one-refresh-per-rotation logic. Refresh tokens are single-use/rotating on the server, so
 * concurrent 401s (or concurrent proactive refreshes) must not each fire their own `/auth/refresh`
 * with the same token — the losers would fail and wipe a session the winner just renewed. The
 * `synchronized` block plus the "did someone already rotate?" check guarantees exactly one refresh.
 *
 * [staleToken] is the access token the caller is dissatisfied with — either near-expiry (proactive)
 * or just-401'd (reactive). If the store already holds a *different* token, another thread refreshed
 * while we waited on the lock, so we reuse that instead of refreshing again.
 *
 * Returns the usable access token, or null if refresh failed (session is cleared → UI logs out).
 */
private class TokenRefresher(
    private val tokenStore: TokenStore,
    private val plainAuthApi: AuthApi
) {
    fun refresh(staleToken: String?): String? = synchronized(this) {
        val current = tokenStore.accessToken
        if (!current.isNullOrBlank() && current != staleToken) return current

        // Generasi sesi diambil SEBELUM apa pun berangkat ke jaringan — inilah
        // yang membuat Logout LENGKET. `synchronized(this)` di atas cuma
        // menyerialkan refresh terhadap refresh LAIN; ia tak menahan
        // `TokenStore.clear()` yang dipanggil dari layar Settings di thread
        // Main. Jadi orang bisa menekan Logout tepat selagi `runBlocking` di
        // bawah menunggu server, dan tanpa tanda ini `updateSession()` yang
        // menyusul akan menulis balik sesi yang baru saja dihapus — termasuk
        // ke DataStore, sehingga app dibuka lagi dalam keadaan MASIH LOGIN.
        // Lihat `data/GenerasiSesi.kt`.
        val tandaSesi = tokenStore.tandaSesi()

        // Tak ada token refresh. DUA keadaan yang berbeda:
        //  - masih ada access token  -> sesi memang habis, beri alasannya;
        //  - tak ada apa-apa         -> BELUM PERNAH login (atau sudah logout
        //    bersih). Mencatat alasan di sini menyambut orang yang baru membuka
        //    app dengan "Sesi tidak valid" padahal ia belum sempat login.
        val refreshToken = tokenStore.refreshToken ?: run {
            if (current.isNullOrBlank()) {
                tokenStore.clear()
            } else {
                tokenStore.clear(AlasanSesiBerakhir.dari(null, null))
            }
            return null
        }

        val response = try {
            runBlocking { plainAuthApi.refresh(RefreshRequest(refreshToken)) }
        } catch (_: Exception) {
            null // network error — leave the session intact, let the request fail/retry naturally
        }

        val body = response?.body()
        if (response?.isSuccessful != true || body == null) {
            // A genuine rejection (not a transient network error) means the refresh token is dead.
            //
            // ALASANNYA dicatat sebelum `clear()`. Jalur INI yang paling sering
            // jalan (refresh proaktif sebelum token kedaluwarsa), dan dulu ia
            // membuang body galat mentah — pengguna dilempar ke layar Login
            // tanpa satu pun keterangan. `session_revoked` vs `session_expired`
            // ada di body itu sejak server 2026-08-07.
            if (response != null && !response.isSuccessful) {
                tokenStore.clear(alasanDariGalat(response.errorBody()?.string()))
            }
            return null
        }

        // Token BARU + PROFIL segar sekaligus. `body.data.user` dulu dibuang di sini, dan
        // karena refresh inilah satu-satunya panggilan yang berjalan terus sepanjang sesi,
        // itu berarti role/roles/pageGrants/divisi tak pernah berubah sampai orangnya
        // logout lalu login lagi. Satu panggilan, bukan dua — lihat `sesiSetelahRefresh`.
        //
        // DITOLAK = sesi diakhiri selagi refresh ini terbang (Logout, atau
        // refresh lain yang ditolak server). Token baru di tangan kita SAH di
        // server — server menjawab `Ok(())` diam-diam untuk pencabutan atas
        // refresh token yang sudah terlanjur dirotasi — jadi kalau ia dipasang,
        // logoutnya batal tanpa satu pun error. Dibuang, dan `null` dikembalikan
        // supaya pemanggil memperlakukan permintaannya sebagai tak terautentikasi.
        if (!tokenStore.updateSession(body.data, tandaSesi)) return null
        return body.data.accessToken
    }
}

/**
 * Attaches the Bearer header, refreshing the token *before* the request when it is within
 * [REFRESH_MARGIN_MILLIS] of expiry (proactive) so most requests never have to eat a 401 round-trip.
 */
/** Nama header pembawa `versionCode` app. Dibaca gateway (`activity_log.rs`). */
private const val APP_VERSION_HEADER = "X-App-Version"

/**
 * Menempelkan `versionCode` app di SETIAP request.
 *
 * Sampai 2026-07-31 server tak punya cara apa pun mengetahui versi app pemanggil:
 * seluruh trafik ber-`User-Agent` `okhttp/4.12.0` (versi pustaka HTTP, bukan versi
 * app), `device_tokens` tak menyimpannya, dan [UpdateManager] membandingkan
 * versi DI SINI lalu tak pernah memberi tahu server hasilnya. Akibatnya
 * "siapa yang belum memperbarui" cuma bisa ditebak dari perilaku — dan itu jadi
 * mahal persis saat penting: gate bukti chat harian mengunci absen pulang
 * karyawan yang app-nya belum punya layar unggahnya, tanpa satu pun query yang
 * bisa menyebut siapa mereka.
 *
 * Dipasang di `baseClientBuilder()` (BUKAN per-API) supaya SEMUA client ikut,
 * termasuk client refresh token dan client unggah video yang diturunkan lewat
 * `newBuilder()`.
 *
 * `header()` bukan `addHeader()`: menimpa, jadi tak mungkin terkirim ganda kalau
 * suatu saat ada pemanggil yang menyetelnya sendiri.
 */
private class AppVersionInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response = chain.proceed(
        chain.request()
            .newBuilder()
            .header(APP_VERSION_HEADER, BuildConfig.VERSION_CODE.toString())
            .build()
    )
}

private class AuthHeaderInterceptor(
    private val tokenStore: TokenStore,
    private val refresher: TokenRefresher
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        var token = tokenStore.accessToken
        if (!token.isNullOrBlank() && tokenStore.accessTokenExpiresWithin(REFRESH_MARGIN_MILLIS)) {
            // On failure fall through with the old token: a real 401 then triggers the authenticator.
            token = refresher.refresh(token) ?: token
        }
        val request = if (!token.isNullOrBlank()) {
            original.newBuilder().header("Authorization", "Bearer $token").build()
        } else {
            original
        }
        return chain.proceed(request)
    }

    private companion object {
        const val REFRESH_MARGIN_MILLIS = 60_000L // refresh ~1 min before the 15-min token expires
    }
}

/**
 * Reactive fallback: when a protected request still gets a 401 (token expired unexpectedly, or the
 * proactive refresh raced), rotate once and retry. If refresh fails the session is cleared.
 */
/** Batas baca body galat: cukup untuk `{code,message}`, tak menahan memori. */
private const val BATAS_BACA_GALAT = 4096L

/**
 * Ubah body galat JSON jadi kalimat siap tampil.
 *
 * Body tak terbaca / bukan JSON (mis. 502 HTML dari proxy) -> kalimat bawaan
 * lewat [AlasanSesiBerakhir.dari]; JANGAN mengembalikan null, karena null
 * berarti layar Login kosong lagi — persis bug yang sedang ditutup.
 */
private fun alasanDariGalat(raw: String?): String {
    val galat = raw?.let {
        runCatching { KJson { ignoreUnknownKeys = true }.decodeFromString<ApiErrorResponse>(it) }.getOrNull()
    }
    return AlasanSesiBerakhir.dari(galat?.code, galat?.message)
}

private class TokenRefreshAuthenticator(
    private val refresher: TokenRefresher,
    private val tokenStore: TokenStore
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        if (responseCount(response) >= 2) return null
        // Diambil SEBELUM refresh, karena `refresher.refresh()` bisa menghapus
        // sesi di tengah jalan. Tanpa penanda ini, "tidak sedang login" pada
        // akhir fungsi punya DUA arti yang tak bisa dibedakan lagi: sesi baru
        // saja mati, ATAU memang belum pernah ada sesi.
        //
        // Arti kedua itulah yang membuat layar Login menampilkan "Sesi tidak
        // valid" pada orang yang BELUM login sama sekali: app boot di layar
        // Login, sebuah request terlindungi menembak tanpa token, kena 401
        // `unauthorized`, dan pesan servernya dicatat sebagai alasan keluar.
        val adaSesiSebelumnya = tokenStore.isLoggedIn
        val failedToken = response.request.header("Authorization")?.removePrefix("Bearer ")
        val fresh = refresher.refresh(failedToken)
        if (fresh != null) {
            return response.request.newBuilder()
                .header("Authorization", "Bearer $fresh")
                .build()
        }
        // Refresh gagal. DUA sebab yang WAJIB dibedakan, karena keduanya sampai
        // di baris ini dengan `fresh == null` yang sama persis:
        //
        //  - Sesi masih hidup  -> refresh tak pernah sampai ke server (sinyal
        //    mati / timeout); `TokenRefresher` sengaja tidak menghapus sesi.
        //    Menuduhnya "sesi diakhiri" membuat orang mengira akunnya dipakai
        //    orang lain lalu buru-buru ganti password.
        //  - Sesi sudah mati   -> server benar-benar menolak.
        // Belum pernah ada sesi -> tak ada yang "berakhir". DIAM. Mencatat apa
        // pun di sini menyambut orang yang baru membuka app dengan tuduhan
        // sesinya bermasalah, padahal ia belum sempat login.
        if (!adaSesiSebelumnya) return null

        if (tokenStore.isLoggedIn) {
            tokenStore.catatAlasanKeluar(AlasanSesiBerakhir.gagalJaringan())
            return null
        }
        // Respons 401 RUTE TERLINDUNGI yang sedang dipegang di sini bisa membawa
        // `session_superseded` dari gateway — sebab yang PASTI, lebih baik
        // daripada kalimat serba-mungkin milik /auth/refresh, jadi ia menimpa
        // alasan yang tadi dicatat `TokenRefresher`.
        //
        // `peekBody` (bukan `body()`): stream respons ini masih milik pemanggil
        // OkHttp; mengonsumsinya membuat lapisan di atas membaca body kosong.
        val alasan = alasanDariGalat(runCatching { response.peekBody(BATAS_BACA_GALAT).string() }.getOrNull())
        tokenStore.catatAlasanKeluar(alasan)
        return null
    }

    private fun responseCount(response: Response): Int {
        var result = 1
        var prior = response.priorResponse
        while (prior != null) {
            result++
            prior = prior.priorResponse
        }
        return result
    }
}
