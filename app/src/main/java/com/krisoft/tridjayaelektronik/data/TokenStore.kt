package com.krisoft.tridjayaelektronik.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.dataStoreFile
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.krisoft.tridjayaelektronik.data.model.SessionData
import com.krisoft.tridjayaelektronik.data.model.UserDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Persists the session (access/refresh tokens + cached profile) in an **encrypted DataStore**
 * (Android Keystore AES-GCM, see [SessionCrypto]) — it survives process death but never sits on
 * disk in plaintext.
 *
 * The public read surface is intentionally **synchronous** because OkHttp's auth interceptor and
 * authenticator run on background threads that cannot suspend. We keep an in-memory [cache] mirror
 * of the DataStore that those callers read instantly; every write updates the mirror synchronously
 * and persists to the DataStore asynchronously.
 */
class TokenStore(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val dataStore: DataStore<PersistedSession> = DataStoreFactory.create(
        serializer = SessionSerializer,
        scope = scope,
        produceFile = { context.dataStoreFile(DATASTORE_FILE) }
    )

    // Synchronous mirror for OkHttp-thread consumers. @Volatile so writes are visible across threads.
    @Volatile private var cache = PersistedSession()
    @Volatile private var loaded = false
    private val loadMutex = Mutex()

    /** Penjaga logout-lengket — baca KDoc [GenerasiSesi] sebelum menyentuh penulis mana pun. */
    private val generasi = GenerasiSesi()

    // Reactive login flag — flips false on logout or when a background refresh fails, so the nav
    // gate can react from anywhere without a per-screen callback.
    private val _sessionState = MutableStateFlow(false)
    val sessionState: StateFlow<Boolean> = _sessionState.asStateFlow()

    // Reactive forced-password-change flag so the nav gate can block/release live.
    private val _mustChangePassword = MutableStateFlow(false)
    val mustChangePasswordState: StateFlow<Boolean> = _mustChangePassword.asStateFlow()

    init {
        // Keep the mirror + flows live with any external write. Does NOT flip `loaded`:
        // the one-time migration in load() must run before we trust the store to be seeded.
        scope.launch { dataStore.data.collect { s -> pasangDariDisk(s) } }
    }

    /**
     * Pasang satu emisi DataStore ke mirror memori — **atomik terhadap
     * [clear]**.
     *
     * Emisi disk tak boleh MENGHIDUPKAN sesi yang baru dikosongkan [clear];
     * itu tugas [bolehPasangDariDisk], dan alasannya panjang di sana. Yang
     * ditambahkan DI SINI adalah kuncinya. Pemeriksaan itu MEMBACA `cache` dan
     * `loaded` lalu `cache = s` MENULISNYA; kalau keduanya berdiri di luar
     * monitor [GenerasiSesi], `clear()` bisa berjalan persis di antara
     * keduanya. Emisi yang lolos pemeriksaan (mirror masih berisi saat
     * diperiksa) lalu mendarat SESUDAH logout selesai: mirror hidup lagi,
     * `sessionState` kembali `true`, dan separuh kedua perbaikan
     * logout-lengket batal tanpa satu pun error. [GenerasiSesi.terkunci]
     * menyatukan pemeriksaan + penulisan ke dalam kunci yang sama dengan
     * kenaikan generasi, persis seperti [mutateBila] untuk jalur penulisan.
     *
     * **Kenapa ini tak menciptakan siklus kunci.** (1) Pembaca sinkron mirror
     * ([accessToken] dkk) sengaja TIDAK mengambil kunci apa pun — thread OkHttp
     * tak bisa suspend dan tak boleh diblokir. (2) Blok ini nol I/O, nol
     * suspensi, dan tak menunggu thread lain. (3) `clear()` menerbitkan KEDUA
     * StateFlow di dalam monitor, jadi kolektor `Main.immediate` bisa berjalan
     * inline di bawahnya — tapi ia tak bisa jatuh ke `runBlocking { load() }`
     * lewat [ensureLoaded], sebab tiap penerbit ([clear], [terapkan], [load])
     * menyetel `loaded = true` SEBELUM menerbitkan. Di fungsi ini penerbitannya
     * bahkan dijaga `if (loaded)`, jadi selagi `loaded` masih `false` tak ada
     * kolektor yang dijalankan sama sekali.
     */
    private fun pasangDariDisk(s: PersistedSession) = generasi.terkunci {
        if (!bolehPasangDariDisk(s, cache, loaded)) return@terkunci
        cache = s
        if (loaded) {
            _sessionState.value = s.accessToken.isNotBlank()
            _mustChangePassword.value = s.accessToken.isNotBlank() && s.mustChangePassword
        }
    }

    // --- Synchronous reads (seed lazily; every caller is on a background thread) ---

    val accessToken: String? get() { ensureLoaded(); return cache.accessToken.ifBlank { null } }
    val refreshToken: String? get() { ensureLoaded(); return cache.refreshToken.ifBlank { null } }
    val userId: String? get() { ensureLoaded(); return cache.userId.ifBlank { null } }
    val userName: String? get() { ensureLoaded(); return cache.userName.ifBlank { null } }
    val cabangName: String? get() { ensureLoaded(); return cache.cabangName.ifBlank { null } }
    val whatsapp: String? get() { ensureLoaded(); return cache.whatsapp.ifBlank { null } }
    val mustChangePassword: Boolean get() { ensureLoaded(); return cache.mustChangePassword }
    val isLoggedIn: Boolean get() { ensureLoaded(); return cache.accessToken.isNotBlank() }

    /** The last profile the server gave us, rebuilt as a [UserDto] — the offline fallback for the
     *  Settings/profile screen. Null until a login or profile fetch has ever populated it. */
    fun cachedProfile(): UserDto? {
        ensureLoaded()
        val s = cache
        if (s.accessToken.isBlank() || s.userName.isBlank()) return null
        return UserDto(
            id = s.userId,
            nik = s.nik,
            email = s.email,
            name = s.userName,
            role = s.role,
            roles = s.rolesCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() },
            pageGrants = s.grantsCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                .map { com.krisoft.tridjayaelektronik.data.model.PageGrantDto(prefix = it) },
            divisi = s.divisi,
            cabangName = s.cabangName,
            whatsapp = s.whatsapp,
            mustChangePassword = s.mustChangePassword
        )
    }

    /** True when the access token is missing or within [marginMillis] of expiry — used for proactive refresh. */
    fun accessTokenExpiresWithin(marginMillis: Long): Boolean {
        ensureLoaded()
        val exp = cache.accessTokenExpiresAtMillis
        if (cache.accessToken.isBlank()) return false // nothing to refresh proactively
        if (exp == 0L) return false // unknown expiry — let the 401 path handle it
        return System.currentTimeMillis() >= exp - marginMillis
    }

    // --- Writes (mirror updated synchronously, DataStore persisted async) ---

    /** Full session after a successful login. */
    fun saveLogin(session: SessionData) = mutate {
        it.copy(
            accessToken = session.accessToken,
            refreshToken = session.refreshToken,
            accessTokenExpiresAtMillis = expiryFrom(session.expiresIn),
            userId = session.user.id,
            userName = session.user.name,
            cabangName = session.user.cabangName,
            whatsapp = session.user.whatsapp,
            role = session.user.role,
            nik = session.user.nik,
            email = session.user.email,
            mustChangePassword = session.user.mustChangePassword,
            rolesCsv = session.user.roles.joinToString(","),
            divisi = session.user.divisi,
            grantsCsv = session.user.pageGrants.joinToString(",") { it.prefix }
        )
    }

    /**
     * Generasi sesi yang sedang berjalan. Diambil pemanggil SEBELUM kerja panjang
     * (mis. `POST /auth/refresh`) lalu disetorkan lagi ke [updateSession].
     *
     * Sinkron, seperti seluruh permukaan baca kelas ini, karena pemanggilnya
     * `TokenRefresher` di thread OkHttp yang tak bisa suspend.
     */
    fun tandaSesi(): Long = generasi.sekarang()

    /**
     * Hasil `/auth/refresh`: token baru **dan** profil segar dalam SATU mutasi.
     *
     * Sengaja menggantikan `updateTokens()` yang dulu hanya menyentuh token dan membuang
     * `SessionData.user` — lihat [sesiSetelahRefresh] untuk alasan lengkapnya (role tak
     * pernah ter-update tanpa logout, plus keadaan setengah jadi kalau ditulis dua kali).
     * Jangan menambahkan kembali penulis token-saja: dari `/auth/refresh` selalu ada
     * profil yang ikut, dan memisahkannya membuka lagi jendela yang baru ditutup ini.
     *
     * @param tanda hasil [tandaSesi] yang diambil SEBELUM panggilan refresh
     *   berangkat. Inilah yang membuat logout LENGKET: refresh yang masih terbang
     *   saat orangnya menekan Logout memegang generasi lama, jadi penulisan
     *   baliknya ditolak alih-alih menghidupkan sesi yang sudah dimatikan (lihat
     *   [GenerasiSesi]).
     * @return `false` bila penulisannya DIBUANG karena sesi keburu diakhiri.
     *   Pemanggil wajib memperlakukan token dalam [session] sebagai tak terpakai.
     */
    fun updateSession(session: SessionData, tanda: Long): Boolean = mutateBila(tanda) {
        sesiSetelahRefresh(it, session, expiryFrom(session.expiresIn))
    }

    /** Refreshed profile (no token change). */
    fun updateProfile(user: UserDto) = mutate {
        it.copy(
            userId = user.id,
            userName = user.name,
            cabangName = user.cabangName,
            whatsapp = user.whatsapp,
            role = user.role,
            nik = user.nik,
            email = user.email,
            mustChangePassword = user.mustChangePassword,
            rolesCsv = user.roles.joinToString(","),
            divisi = user.divisi,
            grantsCsv = user.pageGrants.joinToString(",") { it.prefix }
        )
    }

    /** After a successful password change, drop the forced-change flag. */
    fun markPasswordChanged() = mutate { it.copy(mustChangePassword = false) }

    /**
     * Alasan sesi terakhir berakhir, untuk ditampilkan di layar Login.
     *
     * IN-MEMORY, bukan DataStore: pencabutan sesi dan pindah-layar terjadi di
     * PROSES yang sama, jadi tak ada yang perlu bertahan melewati restart app.
     * Menyimpannya permanen justru berisiko memunculkan alasan basi berhari-hari
     * kemudian.
     *
     * Dibaca SEKALI lewat [ambilAlasanKeluar] lalu dikosongkan, supaya tak
     * muncul lagi di login berikutnya.
     */
    @Volatile
    private var alasanKeluar: String? = null

    fun catatAlasanKeluar(alasan: String?) {
        if (!alasan.isNullOrBlank()) alasanKeluar = alasan
    }

    fun ambilAlasanKeluar(): String? {
        val nilai = alasanKeluar
        alasanKeluar = null
        return nilai
    }

    /**
     * [alasan] dicatat SEBELUM state dibersihkan supaya layar Login punya
     * sesuatu untuk ditampilkan. Tanpa itu pengguna cuma melihat form kosong dan
     * menyimpulkan aplikasinya rusak.
     *
     * Menimpa LANGSUNG, bukan lewat [catatAlasanKeluar] yang mengabaikan null:
     * logout SUKARELA (`alasan = null`) harus MENGHAPUS alasan yang mungkin
     * tercatat sebelumnya — misal gangguan koneksi tadi pagi. Kalau tidak, orang
     * yang keluar sendiri disambut tuduhan sesi diambil alih.
     */
    fun clear(alasan: String? = null) {
        // Menaikkan generasi sesi DI DALAM kunci yang sama dengan pengosongan
        // mirror: penulis yang lahir sebelum baris ini (refresh yang masih
        // terbang) tak bisa menyelinap di antara keduanya. Lihat [GenerasiSesi].
        val tanda = generasi.akhiri { baru ->
            alasanKeluar = alasan
            cache = PersistedSession()
            loaded = true
            _sessionState.value = false
            _mustChangePassword.value = false
            baru
        }
        // Yang disimpan adalah `cache`, BUKAN `PersistedSession()` konstan —
        // sama seperti [terapkan], dan alasannya kembar.
        //
        // Login yang menyusul logout berjalan di generasi yang SAMA ([saveLogin]
        // sengaja tak dijaga generasi, lihat [mutate]), jadi penulisan clear()
        // ini dan penulisan login itu SAMA-SAMA lolos `masihBerlaku` di titik
        // commit. Urutan commit dua `scope.launch` di `Dispatchers.IO` tidak
        // ditentukan urutan launch-nya, jadi konstanta kosong bisa mendarat
        // BELAKANGAN dan menimpa sesi yang baru saja berhasil login. Akibatnya
        // berantai dan senyap: disk kosong sementara mirror berisi sesi hidup,
        // lalu emisi kosong yang menyusul TIDAK ditahan ([bolehPasangDariDisk]
        // sengaja satu arah — ia hanya menahan emisi BERISI), jadi mirror ikut
        // kosong, `sessionState` jadi `false`, dan orang yang BARU BERHASIL
        // LOGIN dilempar balik ke layar Login; cold start berikutnya pun tak
        // menemukan sesi. HP cabang bersama melakukan logout→login sepanjang
        // hari, jadi urutan itu bukan teoretis.
        //
        // Menyimpan `cache` membuat kedua penulisan konvergen ke niat TERAKHIR
        // proses ini: kalau ada login menyusul, yang tertulis sesi hidup itu;
        // kalau tidak, `cache` memang kosong (baru saja dikosongkan di atas).
        scope.launch { simpanDiamDiam(tanda) { cache } }
    }

    /** Pay the seed + legacy-migration cost early, off the main thread (called from Application). */
    suspend fun warmUp() = load()

    // --- internals ---

    /**
     * Penulis TANPA syarat generasi: [saveLogin], [updateProfile],
     * [markPasswordChanged].
     *
     * Ketiganya sengaja tak dijaga, dan alasannya sama: tak satu pun bisa
     * MENGHIDUPKAN sesi yang sudah dimatikan. [saveLogin] memang lahir SESUDAH
     * logout (orang login lagi) — menolaknya justru bug. Dua sisanya cuma
     * menyalin field profil ke atas mirror; sesudah [clear] `accessToken` tetap
     * kosong, jadi `sessionState` tetap `false` dan [cachedProfile] tetap `null`.
     * Yang WAJIB dijaga hanyalah penulis yang membawa TOKEN dari jaringan, dan
     * itu cuma [updateSession].
     */
    private fun mutate(transform: (PersistedSession) -> PersistedSession) {
        generasi.denganGenerasi { tanda -> terapkan(tanda, transform) }
    }

    /** [mutate] yang menolak penulisan dari generasi sesi yang sudah lewat. */
    private fun mutateBila(
        tanda: Long,
        transform: (PersistedSession) -> PersistedSession,
    ): Boolean = generasi.jalankanBila(tanda) { berlaku -> terapkan(berlaku, transform) }

    private fun terapkan(tanda: Long, transform: (PersistedSession) -> PersistedSession) {
        val updated = transform(cache)
        cache = updated
        loaded = true
        _sessionState.value = updated.accessToken.isNotBlank()
        _mustChangePassword.value = updated.accessToken.isNotBlank() && updated.mustChangePassword
        // Persist the latest mirror. DUA mekanisme yang berbeda, jangan dibaca
        // sebagai satu:
        //  - menulis `cache` (bukan potret yang ditangkap saat menjadwalkan)
        //    membuat penyimpanan yang bersamaan DI DALAM SATU GENERASI
        //    konvergen ke niat TERAKHIR proses ini, apa pun urutan commit-nya.
        //    Itu yang menutup lost-update, dan ia hanya sahih selama SETIAP
        //    penulis melakukan hal yang sama — termasuk [clear], yang dulu
        //    menulis konstanta kosong dan karena itu bisa menghapus login yang
        //    menyusul logout (lihat catatan panjang di sana).
        //  - yang menutup RESURRECTION bukan baris ini melainkan
        //    `masihBerlaku(tanda)` di dalam `updateData` ([simpanDiamDiam]):
        //    penulisan yang lahir di generasi yang sudah mati tak pernah
        //    commit sama sekali.
        scope.launch { simpanDiamDiam(tanda) { cache } }
    }

    /**
     * Menulis sesi ke DataStore tanpa pernah melempar.
     *
     * Dipanggil dari `scope.launch` yang TIDAK punya `CoroutineExceptionHandler`
     * (grep: nol handler di seluruh app), jadi apa pun yang lolos dari sini
     * naik sebagai uncaught exception dan MEMBUNUH proses — di background,
     * tanpa layar yang bisa menampilkan pesan. Yang bisa dilempar nyata:
     * IOException dari DataStore dan ProviderException/KeyStoreException dari
     * Android Keystore lewat serializer (lazim di HP hasil restore cloud).
     *
     * Gagal menyimpan HANYA berarti mirror di disk tertinggal dari `cache` di
     * memori: sesi berjalan tetap utuh, paling buruk user login ulang nanti.
     * Itu harga yang jauh lebih murah daripada app yang tertutup sendiri.
     *
     * **[tanda] diperiksa DI DALAM `updateData`, bukan sebelum `scope.launch`.**
     * Dua penyimpanan bisa berjalan bersamaan di `Dispatchers.IO` dan DataStore
     * yang menyerialkannya — urutan commit-nya tak ditentukan urutan `launch`.
     * Memeriksa generasi di titik commit itulah yang membuat penulisan basi
     * (sesi yang sudah di-logout) TAK PERNAH mendarat di disk, bagaimanapun
     * kedua coroutine itu terjadwal. Menjadikannya no-op — mengembalikan `lama`
     * — bukan melempar: batalnya penulisan ini normal, bukan kesalahan.
     */
    private suspend fun simpanDiamDiam(tanda: Long, nilai: () -> PersistedSession) {
        try {
            dataStore.updateData { lama -> if (generasi.masihBerlaku(tanda)) nilai() else lama }
        } catch (e: Exception) {
            // sengaja ditelan — lihat kdoc
        }
    }

    private fun ensureLoaded() {
        if (loaded) return
        // Safe: only background-thread callers reach here before warmUp() has run.
        runBlocking { load() }
    }

    private suspend fun load() = loadMutex.withLock {
        if (loaded) return@withLock
        migrateLegacyIfNeeded()
        cache = dataStore.data.first()
        loaded = true
        _sessionState.value = cache.accessToken.isNotBlank()
        _mustChangePassword.value = cache.accessToken.isNotBlank() && cache.mustChangePassword
    }

    /** One-time move of the legacy EncryptedSharedPreferences session into the DataStore. */
    private suspend fun migrateLegacyIfNeeded() {
        if (dataStore.data.first().accessToken.isNotBlank()) return // already have a session
        val legacy = readLegacySession() ?: return
        if (legacy.accessToken.isNotBlank()) {
            dataStore.updateData { legacy }
        }
        clearLegacyPrefs()
    }

    private fun readLegacySession(): PersistedSession? = runCatching {
        val prefs = legacyPrefs() ?: return null
        val access = prefs.getString("access_token", null).orEmpty()
        if (access.isBlank()) return null
        PersistedSession(
            accessToken = access,
            refreshToken = prefs.getString("refresh_token", null).orEmpty(),
            userId = prefs.getString("user_id", null).orEmpty(),
            userName = prefs.getString("user_name", null).orEmpty(),
            cabangName = prefs.getString("cabang_name", null).orEmpty(),
            whatsapp = prefs.getString("whatsapp", null).orEmpty()
        )
    }.getOrNull()

    private fun clearLegacyPrefs() {
        runCatching { legacyPrefs()?.edit()?.clear()?.apply() }
    }

    private fun legacyPrefs() = runCatching {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "tridjaya_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }.getOrNull()

    private fun expiryFrom(expiresInSeconds: Int): Long =
        if (expiresInSeconds > 0) System.currentTimeMillis() + expiresInSeconds * 1000L else 0L

    companion object {
        private const val DATASTORE_FILE = "tridjaya_session.pb"
    }
}
