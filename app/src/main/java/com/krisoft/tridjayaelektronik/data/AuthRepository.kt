package com.krisoft.tridjayaelektronik.data

import com.krisoft.tridjayaelektronik.data.local.AppDatabase
import com.krisoft.tridjayaelektronik.data.model.ApiErrorResponse
import com.krisoft.tridjayaelektronik.data.model.ChangePasswordRequest
import com.krisoft.tridjayaelektronik.data.model.ForgotPasswordRequest
import com.krisoft.tridjayaelektronik.data.model.LoginRequest
import com.krisoft.tridjayaelektronik.data.model.LogoutRequest
import com.krisoft.tridjayaelektronik.data.model.ResetPasswordRequest
import com.krisoft.tridjayaelektronik.data.model.UserDto
import com.krisoft.tridjayaelektronik.data.remote.AuthApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

sealed class AuthResult<out T> {
    data class Success<T>(val data: T) : AuthResult<T>()

    /**
     * [code] datang dari BADAN error server (`ErrorBody.code`) dan jatuh ke
     * `http_<status>` bila badannya tak bisa diurai; [httpStatus] adalah status
     * HTTP mentahnya, `null` bila permintaannya tak pernah sampai (lempar
     * IOException) atau kegagalannya dibuat klien sendiri (mis. validasi lokal).
     *
     * [httpStatus] ADA karena [code] TIDAK cukup untuk memutuskan sebuah
     * kegagalan permanen atau sementara: gateway memakai SATU kode
     * `gateway_error` untuk 502 (upstream mati — sementara), 503 (service
     * dimatikan — sementara), DAN 404 (rute tak dikenal — permanen), lihat
     * `GatewayError::{new,not_found,service_unavailable}` di
     * `gateway/src/lib.rs`. Menebak dari kode saja berarti memilih antara
     * membuang data saat server sekarat atau menyimpan selamanya data yang
     * ditolak — dua-duanya salah. Yang mengisinya baru `OpnameRepository`;
     * pemanggil lain membiarkannya `null` (= sementara, arah aman).
     */
    data class Failure(
        val code: String,
        val message: String,
        val httpStatus: Int? = null
    ) : AuthResult<Nothing>()
}

@Singleton
class AuthRepository @Inject constructor(
    private val api: AuthApi,
    private val tokenStore: TokenStore,
    private val appDatabase: AppDatabase
) {

    private val errorJson = Json { ignoreUnknownKeys = true }

    suspend fun login(identifier: String, password: String): AuthResult<UserDto> {
        return try {
            val response = api.login(LoginRequest(identifier = identifier, password = password))
            if (response.isSuccessful) {
                val session = response.body()?.data
                    ?: return AuthResult.Failure("unknown_error", "Response kosong dari server")
                tokenStore.saveLogin(session)
                // Cermin peta kemampuan WAJIB dikosongkan di sini, bukan cuma di
                // `logout()`. Jalur berakhirnya sesi yang NORMAL adalah
                // `tokenStore.clear()` langsung dari interceptor OkHttp saat
                // refresh token ditolak — `logout()` tak pernah lewat. Di HP
                // dinas yang dipakai bergantian, cermin superadmin yang
                // tertinggal membuat karyawan berikutnya MENDARAT di tab
                // Eksekutif sampai pengambilan pertamanya selesai. Servernya
                // tetap menolak (403), jadi datanya tak bocor — tapi tab yang
                // sekejap terlihat lalu hilang terbaca sebagai bug, dan
                // ongkos menutupnya satu baris.
                _petaKemampuanTerakhir.value = null
                AuthResult.Success(session.user)
            } else {
                parseError(response)
            }
        } catch (e: Exception) {
            AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
        }
    }

    /**
     * Profile with an offline fallback: a plain network error (no connection) serves the profile
     * cached in [TokenStore] from the last login/fetch, so Settings still renders offline. A real
     * HTTP rejection (e.g. 401 with a dead refresh token) still surfaces as a Failure — offline
     * must never mask a genuinely invalid session.
     */
    /** Kemampuan efektif user dari server (sumber tunggal gate menu). Gagal /
     *  offline / server lama / **badan rusak yang terdekode jadi peta kosong** =
     *  `null`. Yang dilakukan pemanggil atas `null` BERBEDA-BEDA — `gateAllows`
     *  jatuh ke gate role lokal, pembaca ber-`?.let` membiarkan gerbangnya apa
     *  adanya; [petaKemampuanSah] merinci efeknya per pembaca. */
    suspend fun capabilities(): Map<String, Boolean>? {
        val peta = try {
            val response = api.capabilities()
            if (response.isSuccessful) petaKemampuanSah(response.body()?.data?.capabilities) else null
        } catch (_: Exception) {
            null
        }
        // Cermin hanya ditulis saat BERHASIL — lihat KDoc di bawah.
        if (peta != null) _petaKemampuanTerakhir.value = peta
        return peta
    }

    private val _petaKemampuanTerakhir = MutableStateFlow<Map<String, Boolean>?>(null)

    /**
     * Cermin baca-saja dari peta kemampuan yang TERAKHIR berhasil diambil, untuk
     * pemakai di luar ViewModel — hari ini hanya gerbang TAB di `MainActivity`.
     *
     * **Kenapa cermin, bukan panggilan `capabilities()` langsung.** Pemanggil
     * ber-scope seumur proses (root `MainScreen`) yang mengambil sendiri akan
     * membekukan petanya sampai app dimatikan: akses baru tak pernah membuka
     * tab-nya, akses yang dicabut tetap menampilkannya lalu dijawab 403. Itu
     * persis bug yang ditutup `PenyegarKemampuan`, dan `PembacaPetaKemampuanTest`
     * menjaga daftar tertutup pemanggilnya justru supaya pintu itu tak dibuka
     * lewat berkas lain. Cermin ini menumpang penyegaran yang SUDAH benar —
     * `ActivityViewModel`/`HomeViewModel` mengambil ulang tiap sidik akses atau
     * identitas token berubah — tanpa menambah pembaca kedelapan.
     *
     * **`null` = belum pernah berhasil.** Perhatikan: di keadaan itu `gateAllows`
     * JATUH KE DAFTAR ROLE LOKAL, bukan fail-closed — cabang kemampuannya cuma
     * dimasuki bila petanya bukan `null`. Fail-closed berlaku untuk kunci yang
     * ABSEN dari peta yang ADA. Dua keadaan berbeda, dan menyamakannya sudah
     * membuat tiga komentar di repo ini salah sekaligus.
     *
     * Nilainya TIDAK pernah dikosongkan oleh pengambilan yang gagal (alasan sama
     * dengan `PenyegarKemampuan`: peta baik tak boleh tergantikan peta kosong).
     * Yang mengosongkannya: [logout] dan [login] yang berhasil.
     */
    val petaKemampuanTerakhir: StateFlow<Map<String, Boolean>?> = _petaKemampuanTerakhir

    suspend fun profile(): AuthResult<UserDto> {
        return try {
            val response = api.profile()
            if (response.isSuccessful) {
                val user = response.body()?.data
                    ?: return AuthResult.Failure("unknown_error", "Response kosong dari server")
                tokenStore.updateProfile(user)
                AuthResult.Success(user)
            } else {
                parseError(response)
            }
        } catch (e: Exception) {
            tokenStore.cachedProfile()?.let { return AuthResult.Success(it) }
            AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
        }
    }

    /**
     * Perbarui field profil milik sendiri (`PUT /auth/profile`). Backend
     * `UpdateProfileRequest` menerima `name`/`whatsapp`/`email`, semuanya
     * opsional — kirim hanya yang diubah.
     *
     * CATATAN KEAMANAN: `whatsapp` adalah kanal OTP & reset password. Mengubahnya
     * dari sesi yang sudah login berarti sesi yang dicuri bisa mengalihkan reset
     * password ke nomor lain. Endpoint-nya memang sudah mengizinkan ini sejak
     * sebelum app mobile memakainya (web juga bisa), jadi bukan celah baru — tapi
     * kalau kelak perlu diperketat, tempatnya di auth-service, bukan di sini.
     */
    suspend fun updateProfile(fields: Map<String, String>): AuthResult<UserDto> {
        return try {
            val response = api.updateProfile(fields)
            if (response.isSuccessful) {
                val user = response.body()?.data
                    ?: return AuthResult.Failure("unknown_error", "Response kosong dari server")
                tokenStore.updateProfile(user)
                AuthResult.Success(user)
            } else {
                parseError(response)
            }
        } catch (e: Exception) {
            AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
        }
    }

    suspend fun logout() {
        try {
            api.logout(LogoutRequest(tokenStore.refreshToken ?: ""))
        } catch (_: Exception) {
            // Best-effort: clear local session regardless of server reachability.
        } finally {
            tokenStore.clear()
            // Peta kemampuan ikut dibuang di sini, bukan dibiarkan basi: HP ini
            // dipakai bergantian, dan cermin yang tertinggal akan memberi user
            // BERIKUTNYA tab yang bukan haknya selama beberapa ratus milidetik
            // sebelum pengambilan pertamanya selesai. Servernya tetap menolak,
            // tapi "tab yang sekejap terlihat lalu hilang" adalah kebocoran
            // informasi yang tak perlu dan terbaca sebagai bug.
            _petaKemampuanTerakhir.value = null
            // Wipe every cached table (stock, leads, dashboard cache, sync meta) so the next
            // login — possibly a different user on a shared device — never sees stale or
            // another account's data before the first fresh sync completes.
            withContext(Dispatchers.IO) { appDatabase.clearAllTables() }
        }
    }

    /** Change the logged-in user's password. On success clears the forced-change flag. */
    suspend fun changePassword(oldPassword: String, newPassword: String): AuthResult<Unit> {
        return try {
            val response = api.changePassword(ChangePasswordRequest(oldPassword, newPassword))
            if (response.isSuccessful) {
                tokenStore.markPasswordChanged()
                AuthResult.Success(Unit)
            } else {
                parseError(response)
            }
        } catch (e: Exception) {
            AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
        }
    }

    /** Public: request a password reset via WhatsApp. Server always returns 200 (no account enumeration). */
    suspend fun forgotPassword(identifier: String): AuthResult<Unit> {
        return try {
            val response = api.forgotPassword(ForgotPasswordRequest(identifier))
            if (response.isSuccessful) AuthResult.Success(Unit) else parseError(response)
        } catch (e: Exception) {
            AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
        }
    }

    /** Public: complete a reset with the emailed token + a new password. */
    suspend fun resetPassword(token: String, newPassword: String): AuthResult<Unit> {
        return try {
            val response = api.resetPassword(ResetPasswordRequest(token, newPassword))
            if (response.isSuccessful) AuthResult.Success(Unit) else parseError(response)
        } catch (e: Exception) {
            AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
        }
    }

    val isLoggedIn: Boolean get() = tokenStore.isLoggedIn
    val mustChangePassword: Boolean get() = tokenStore.mustChangePassword
    val currentUserId: String? get() = tokenStore.userId
    val currentUserName: String? get() = tokenStore.userName
    val currentCabangName: String? get() = tokenStore.cabangName
    val currentUserWhatsapp: String? get() = tokenStore.whatsapp
    /** Profil dari cache sesi (sinkron, tanpa network) — untuk render instan sebelum refresh. */
    val cachedUser get() = tokenStore.cachedProfile()

    /**
     * Identitas token akses yang SEDANG dipakai (hash, bukan tokennya) — bahan
     * kunci latch `ui/home/PenyegarKemampuan`.
     *
     * Ada karena peta `GET /api/me/capabilities` dihitung server dari klaim
     * TOKEN, sedangkan [cachedUser] bisa lebih baru dari token (profil ditulis
     * `GET /auth/profile` tanpa rotasi token). Mengunci latch pada profil SAJA
     * membekukannya seumur proses; menyertakan nilai ini membuat rotasi token
     * berikutnya membukanya. Lihat [sidikToken].
     *
     * Sinkron seperti [cachedUser] dan dengan alasan yang sama: mirror
     * [TokenStore] sudah di-seed `warmUp()` dari `TridjayaApplication` jauh
     * sebelum ViewModel mana pun lahir, jadi tak ada `runBlocking` yang benar-
     * benar berjalan di sini.
     */
    val sidikTokenAkses: String get() = sidikToken(tokenStore.accessToken)

    /** Reactive login state — flips to false on logout or when a background refresh fails. */
    val sessionState: StateFlow<Boolean> get() = tokenStore.sessionState

    /** Reactive "server requires a password change" flag — drives the forced change-password gate. */
    val mustChangePasswordState: StateFlow<Boolean> get() = tokenStore.mustChangePasswordState

    /**
     * Silently confirms (and, via [AuthApi]'s authenticator, opportunistically refreshes) the
     * current session. A plain network error leaves the session untouched — only a genuine auth
     * failure (refresh itself rejected) clears it, which [sessionState] then reflects.
     */
    suspend fun validateSession(): Boolean = profile() is AuthResult.Success<UserDto>

    private fun <T> parseError(response: Response<*>): AuthResult<T> {
        val raw = response.errorBody()?.string()
        val parsed = raw?.let {
            runCatching { errorJson.decodeFromString(ApiErrorResponse.serializer(), it) }.getOrNull()
        }
        return AuthResult.Failure(
            parsed?.code ?: "http_${response.code()}",
            parsed?.message ?: "Terjadi kesalahan (${response.code()})"
        )
    }
}
