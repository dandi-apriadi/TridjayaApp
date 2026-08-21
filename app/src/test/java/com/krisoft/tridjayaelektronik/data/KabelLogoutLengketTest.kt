package com.krisoft.tridjayaelektronik.data

import com.krisoft.tridjayaelektronik.ui.tanpaKomentarDanTeks
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * **Penjaga KABEL untuk logout-lengket.**
 *
 * [GenerasiSesi] diuji sebagai kelas murni di `GenerasiSesiTest`. Yang tak bisa
 * diuji begitu adalah pertanyaan yang menentukan apakah perbaikannya bekerja di
 * HP orang: **apakah [TokenStore] dan `TokenRefresher` benar-benar memakainya?**
 * [TokenStore] butuh Context + Android Keystore + DataStore, jadi ia tak bisa
 * dihidupkan di unit test JVM (repo ini juga nol test instrumented — lihat
 * "Known gaps" di `CLAUDE.md`), dan mencabut seluruh kabelnya akan tetap
 * meninggalkan `GenerasiSesiTest` HIJAU.
 *
 * Pola dan perkakasnya dipinjam dari `KabelPenyegaranKemampuanTest`, termasuk
 * yang membuatnya bergigi: komentar dan literal teks DIBUANG lebih dulu
 * ([tanpaKomentarDanTeks]), sebab berkas-berkas ini menyebut nama-nama yang
 * dicari berkali-kali di KDoc-nya.
 */
class KabelLogoutLengketTest {

    private fun sumber(jalurRelatif: String): String {
        val berkas = sequenceOf(
            File("src/main/java/com/krisoft/tridjayaelektronik/$jalurRelatif"),
            File("app/src/main/java/com/krisoft/tridjayaelektronik/$jalurRelatif"),
        ).firstOrNull { it.isFile }
            ?: error("$jalurRelatif tak ketemu — cwd=${File(".").absolutePath}")
        return berkas.readText().tanpaKomentarDanTeks()
    }

    private val tokenStore by lazy { sumber("data/TokenStore.kt") }
    private val networkModule by lazy { sumber("data/remote/NetworkModule.kt") }

    // ── TokenStore ───────────────────────────────────────────────────────────

    @Test
    fun `clear() menaikkan generasi sesi`() {
        val badan = potongan(tokenStore, "clear", "TokenStore.kt")
        wajibCocok(
            badan = badan,
            pola = Regex("""generasi\s*\.\s*akhiri\s*[({]"""),
            di = "TokenStore.clear()",
            kenapa = "tanpa kenaikan generasi, refresh yang masih terbang saat orangnya " +
                "menekan Logout akan menulis balik sesi yang baru dihapus — mirror DAN " +
                "DataStore — sehingga cold start berikutnya masih login",
        )
    }

    @Test
    fun `updateSession menolak penulisan dari generasi yang sudah lewat`() {
        val badan = potongan(tokenStore, "updateSession", "TokenStore.kt")
        wajibCocok(
            badan = badan,
            pola = Regex("""\btanda\s*:\s*Long"""),
            di = "TokenStore.updateSession()",
            kenapa = "generasi sesi harus DISETOR pemanggil (diambil sebelum panggilan " +
                "refresh berangkat); membacanya sendiri di sini berarti membaca generasi " +
                "SESUDAH logout — yaitu selalu lolos, penjaganya lumpuh",
        )
        wajibCocok(
            badan = badan,
            pola = Regex("""\bmutateBila\s*\("""),
            di = "TokenStore.updateSession()",
            kenapa = "`mutate` polos menulis tanpa syarat; hanya `mutateBila` yang " +
                "memeriksa generasi di dalam kunci yang sama dengan clear()",
        )

        wajibCocok(
            badan = potongan(tokenStore, "mutateBila", "TokenStore.kt"),
            pola = Regex("""jalankanBila\s*\("""),
            di = "TokenStore.mutateBila()",
            kenapa = "pemeriksaan dan penulisannya harus ATOMIK terhadap clear(); " +
                "`if (tanda != …)` di luar kunci menyisakan jendela yang sama persis",
        )
    }

    @Test
    fun `penulisan DataStore memeriksa generasi di titik commit`() {
        val badan = potongan(tokenStore, "simpanDiamDiam", "TokenStore.kt")
        wajibCocok(
            badan = badan,
            pola = Regex("""updateData\s*\{[^}]*masihBerlaku\s*\("""),
            di = "TokenStore.simpanDiamDiam()",
            kenapa = "urutan commit dua `scope.launch` di Dispatchers.IO tak ditentukan " +
                "urutan launch-nya; memeriksa generasi SEBELUM launch (atau tidak sama " +
                "sekali) membiarkan penulisan basi mendarat di disk",
        )
    }

    @Test
    fun `kolektor dataStore tidak menghidupkan sesi yang sudah dikosongkan`() {
        wajibCocok(
            badan = potongan(tokenStore, "init", "TokenStore.kt", kataKunci = "init"),
            pola = Regex("""dataStore\s*\.\s*data\s*\.\s*collect\s*\{[^}]*pasangDariDisk\s*\("""),
            di = "TokenStore.init { … }",
            kenapa = "tiap emisi DataStore harus lewat `pasangDariDisk`; memasangnya " +
                "langsung di badan `collect` menaruh pemeriksaan dan penulisannya di luar " +
                "kunci generasi (lihat test berikutnya)",
        )

        val pasang = potongan(tokenStore, "pasangDariDisk", "TokenStore.kt")
        wajibCocok(
            badan = pasang,
            pola = Regex("""bolehPasangDariDisk\s*\("""),
            di = "TokenStore.pasangDariDisk()",
            kenapa = "emisi dari penulisan yang SAH sebelum logout bisa tiba SESUDAHNYA; " +
                "`cache = s` tanpa penjaga ini mengembalikan sesi ke mirror dan " +
                "menyalakan lagi sessionState",
        )
    }

    @Test
    fun `pemasangan emisi disk atomik terhadap kenaikan generasi`() {
        wajibCocok(
            badan = potongan(tokenStore, "pasangDariDisk", "TokenStore.kt"),
            pola = Regex("""generasi\s*\.\s*terkunci\s*[({]"""),
            di = "TokenStore.pasangDariDisk()",
            kenapa = "`bolehPasangDariDisk` MEMBACA cache/loaded dan `cache = s` MENULISNYA — " +
                "dua langkah. Di luar kunci, clear() bisa berjalan persis di antaranya: " +
                "emisi lolos pemeriksaan selagi mirror masih berisi, logout selesai, lalu " +
                "emisinya mendarat dan menghidupkan lagi sesi yang sudah dimatikan. " +
                "Penjaga yang benar di tempat yang salah sama nilainya dengan tak ada",
        )
    }

    @Test
    fun `clear() menyimpan mirror, bukan konstanta kosong`() {
        wajibCocok(
            badan = potongan(tokenStore, "clear", "TokenStore.kt"),
            pola = Regex("""simpanDiamDiam\s*\(\s*tanda\s*\)\s*\{\s*cache\s*\}"""),
            di = "TokenStore.clear()",
            kenapa = "login yang menyusul logout berjalan di generasi yang SAMA (saveLogin " +
                "sengaja tak dijaga generasi), jadi penulisan clear() dan penulisan login " +
                "sama-sama lolos `masihBerlaku`. Urutan commit dua `scope.launch` di " +
                "Dispatchers.IO tak dijamin, jadi konstanta kosong bisa mendarat BELAKANGAN " +
                "dan menghapus sesi yang baru berhasil login — lalu emisi kosongnya " +
                "mengosongkan mirror juga (bolehPasangDariDisk sengaja satu arah) dan " +
                "orangnya dilempar balik ke layar Login",
        )
    }

    // ── TokenRefresher (NetworkModule) ───────────────────────────────────────

    @Test
    fun `refresh mengambil generasi SEBELUM apa pun yang bisa disela logout`() {
        val badan = potongan(networkModule, "refresh", "NetworkModule.kt")

        val ambilTanda = Regex("""tokenStore\s*\.\s*tandaSesi\s*\(\s*\)""").find(badan)
            ?: run {
                fail(
                    "TokenRefresher.refresh() tak lagi mengambil `tokenStore.tandaSesi()`.\n" +
                        "Tanpa itu `updateSession` tak punya generasi untuk dibandingkan dan " +
                        "logout kembali bisa dibatalkan refresh yang terbang."
                )
                error("tak terjangkau")
            }
        val panggilServer = Regex("""plainAuthApi\s*\.\s*refresh\s*\(""").find(badan)
            ?: run {
                fail("TokenRefresher.refresh() tak lagi memanggil plainAuthApi.refresh()")
                error("tak terjangkau")
            }
        val bacaRefreshToken = Regex("""tokenStore\s*\.\s*refreshToken\b""").find(badan)
            ?: run {
                fail("TokenRefresher.refresh() tak lagi membaca `tokenStore.refreshToken`")
                error("tak terjangkau")
            }

        // Batas yang LEBIH KETAT dari "sebelum panggilan jaringan", dan ia yang
        // benar. Membaca refresh token adalah langkah pertama yang bisa disela
        // logout: sesudah baris itu kita memegang token yang tak kosong,
        // sementara `tandaSesi()` yang diambil BELAKANGAN akan mengembalikan
        // generasi BARU hasil clear(). `updateSession` lalu menyetorkan generasi
        // berjalan ke dirinya sendiri — selalu cocok, selalu diterima — dan
        // sesinya hidup lagi di mirror DAN di disk. Menuntut urutan ini juga
        // otomatis menuntut "sebelum jaringan", sebab jaringan berangkat sesudah
        // token itu dibaca; asersi kedua tetap ditulis supaya pesan gagalnya
        // menyebut kedua batas.
        assertTrue(
            "`tandaSesi()` harus diambil SEBELUM `tokenStore.refreshToken` dibaca. " +
                "Logout yang mendarat di antara keduanya meninggalkan refresh token yang " +
                "sudah terbaca (tak kosong) sementara tandaSesi() mengembalikan generasi " +
                "BARU — updateSession DITERIMA dan sesi yang sudah dimatikan hidup lagi.\n" +
                "tandaSesi() di indeks ${ambilTanda.range.first}, " +
                "tokenStore.refreshToken di indeks ${bacaRefreshToken.range.first}",
            ambilTanda.range.first < bacaRefreshToken.range.first,
        )
        assertTrue(
            "`tandaSesi()` harus diambil SEBELUM panggilan jaringan. Mengambilnya sesudah " +
                "berarti generasi yang dibandingkan adalah generasi SESUDAH logout — " +
                "pemeriksaannya selalu lolos dan penjaganya jadi hiasan.\n" +
                "tandaSesi() di indeks ${ambilTanda.range.first}, " +
                "plainAuthApi.refresh() di indeks ${panggilServer.range.first}",
            ambilTanda.range.first < panggilServer.range.first,
        )
    }

    @Test
    fun `refresh menyetor generasi itu dan membuang token yang ditolak`() {
        val badan = potongan(networkModule, "refresh", "NetworkModule.kt")

        val penugasan = Regex("""val\s+(\w+)\s*=\s*tokenStore\s*\.\s*tandaSesi\s*\(\s*\)""")
            .find(badan)
            ?: run {
                fail("hasil `tokenStore.tandaSesi()` tak disimpan ke variabel apa pun")
                error("tak terjangkau")
            }
        val namaTanda = penugasan.groupValues[1]

        wajibCocok(
            badan = badan,
            pola = Regex("""updateSession\s*\([^)]*\b$namaTanda\b[^)]*\)"""),
            di = "TokenRefresher.refresh()",
            kenapa = "generasi yang diambil sebelum jaringan harus DIPAKAI; menyetor nilai " +
                "lain (atau memanggil tandaSesi() lagi di sini) memulihkan bug aslinya",
        )
        wajibCocok(
            badan = badan,
            pola = Regex("""!\s*tokenStore\s*\.\s*updateSession\s*\("""),
            di = "TokenRefresher.refresh()",
            kenapa = "penolakan harus DIPERIKSA lalu `return null`. Mengabaikan nilai " +
                "baliknya membuat refresh mengembalikan access token yang tak pernah " +
                "tersimpan — request berikutnya memakai token sesi yang sudah di-logout",
        )
    }

    // ── Perkakas ─────────────────────────────────────────────────────────────

    private fun wajibCocok(badan: String, pola: Regex, di: String, kenapa: String) {
        if (pola.containsMatchIn(badan)) return
        fail(
            buildString {
                appendLine("Kabel logout-lengket PUTUS di $di.")
                appendLine("Dicari (di luar komentar & string): ${pola.pattern}")
                appendLine("Kenapa penting: $kenapa")
                appendLine("Yang terbaca:")
                appendLine(badan.trim().lines().joinToString("\n") { "  | $it" })
            }
        )
    }

    /**
     * Potongan sumber milik satu deklarasi, dari `fun <nama>` sampai penutup
     * blok `{ … }` pertamanya.
     *
     * Sengaja BUKAN [badanFungsi]-nya `KabelPenyegaranKemampuanTest`: fungsi
     * yang dijaga di sini ada yang ber-BADAN EKSPRESI (`= mutateBila(tanda) { … }`),
     * yang polanya tak cocok dengan tanda tangan ber-`{`. Bentuk ini memuat
     * tanda tangannya juga — memang perlu, sebab salah satu asersi menuntut
     * parameter `tanda: Long` ada di situ.
     */
    private fun potongan(
        sumber: String,
        nama: String,
        berkas: String,
        kataKunci: String = "fun",
    ): String {
        val pola = if (kataKunci == "fun") Regex("""\bfun\s+$nama\s*\(""") else Regex("""\b$nama\s*\{""")
        val awal = pola.find(sumber)?.range?.first
            ?: run {
                fail("$berkas tak lagi punya `$kataKunci $nama` — kabelnya hilang, bukan cuma berubah")
                error("tak terjangkau")
            }
        val buka = sumber.indexOf('{', awal)
        if (buka < 0) {
            fail("$berkas: `$kataKunci $nama` tak punya blok `{` — pemindai tak bisa memutus apa pun")
        }
        var kedalaman = 0
        var i = buka
        while (i < sumber.length) {
            when (sumber[i]) {
                '{' -> kedalaman++
                '}' -> {
                    kedalaman--
                    if (kedalaman == 0) return sumber.substring(awal, i + 1)
                }
            }
            i++
        }
        fail("$berkas: blok `$kataKunci $nama` tak tertutup")
        error("tak terjangkau")
    }
}
