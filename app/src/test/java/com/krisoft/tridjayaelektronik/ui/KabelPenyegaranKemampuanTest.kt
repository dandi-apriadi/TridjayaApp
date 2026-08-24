package com.krisoft.tridjayaelektronik.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * **Penjaga KABEL, bukan penjaga logika.**
 *
 * `sidikAkses` + `PenyegarKemampuan` (`ui/home/SidikAkses.kt`) sudah diuji
 * sebagai fungsi murni di `SidikAksesTest`. Yang TIDAK bisa diuji begitu adalah
 * pertanyaan yang justru menentukan apakah perbaikannya bekerja di HP orang:
 * **apakah keduanya benar-benar DIPANGGIL dari jalur yang hidup?**
 *
 * Kenapa penjaga ini ada, konkret: gerbang independen mencabut SELURUH kabel
 * penyegaran dari `ActivityViewModel` + `HomeViewModel` (mengembalikan keduanya
 * ke versi `HEAD`) lalu menjalankan seluruh suite — **exit 0, 793 lulus, nol
 * failure**. Artinya angka "793 test hijau" saat itu bukan bukti apa pun tentang
 * perbaikan ini. Repo ini nol test instrumented/Compose (lihat "Known gaps" di
 * `CLAUDE.md`), jadi ViewModel-nya memang tak bisa dijalankan di unit test JVM —
 * dan menunggu test instrumented berarti merilis APK tanpa penjaga sama sekali.
 * APK adalah artefak yang paling mahal ditarik kembali.
 *
 * Polanya dipinjam dari saudara backend (`tridjaya`), yang memakai
 * `include_str!` + asersi atas ISI berkas untuk menjaga bahwa fungsi murninya
 * dipanggil dan bukan menganggur — mis.
 * `gs_snapshot::tests::closure_live_tak_pernah_dipanggil_di_luar_pembungkus`
 * dan `baca.rs::tak_ada_koneksi_gs_tanpa_batas_waktu_di_crate_ini`.
 *
 * **Yang membuatnya bergigi: komentar DIBUANG lebih dulu.** Kedua ViewModel
 * menyebut `segarkanKemampuan` berkali-kali di KDoc-nya ("Lihat
 * [segarkanKemampuan]", "lewat [segarkanKemampuan]"). Pemindai polos ala
 * `berkas.contains("segarkanKemampuan")` karena itu akan tetap HIJAU sesudah
 * pemanggilannya dihapus — persis kegagalan senyap yang sedang ditutup. Semua
 * asersi di bawah berjalan atas [tanpaKomentarDanTeks], dan sifat itu diuji
 * sendiri di [pemindai tidak tertipu penyebutan di komentar].
 *
 * **Yang membuatnya tidak rapuh:** pencocokan lewat regex ber-`\s*`, jadi ganti
 * baris, indentasi, penambahan/pengurangan argumen bernama, dan pemformatan
 * ulang tidak mengubah vonisnya. Yang dituntut cuma satu: pemanggilannya masih
 * ADA, di dalam badan fungsi yang benar.
 */
class KabelPenyegaranKemampuanTest {

    // ── Lokasi berkas ────────────────────────────────────────────────────────

    /** cwd `:app:testDebugUnitTest` bisa modul ATAU root repo — pola sama `FileProviderPathsTest`. */
    private fun sumber(jalurRelatif: String): String {
        val berkas = sequenceOf(
            File("src/main/java/com/krisoft/tridjayaelektronik/$jalurRelatif"),
            File("app/src/main/java/com/krisoft/tridjayaelektronik/$jalurRelatif"),
        ).firstOrNull { it.isFile }
            ?: error("$jalurRelatif tak ketemu — cwd=${File(".").absolutePath}")
        return berkas.readText().tanpaKomentarDanTeks()
    }

    private val activityViewModel by lazy { sumber("ui/activity/ActivityViewModel.kt") }
    private val homeViewModel by lazy { sumber("ui/home/HomeViewModel.kt") }
    private val eksekutifViewModel by lazy { sumber("ui/eksekutif/EksekutifViewModel.kt") }

    // ── ActivityViewModel ────────────────────────────────────────────────────

    @Test
    fun `ActivityViewModel menyegarkan kemampuan di dalam load()`() {
        val badan = badanFungsi(activityViewModel, "load", "ActivityViewModel.kt")

        wajibMemanggil(
            badan = badan,
            nama = "segarkanKemampuan",
            di = "ActivityViewModel.load()",
            kenapa = "tanpa panggilan ini peta kemampuan kembali diambil SEKALI seumur " +
                "proses: akses yang baru diberi tak pernah membuka menunya, dan akses " +
                "yang dicabut tetap menampilkan menu yang lalu dijawab 403",
        )
    }

    @Test
    fun `segarkanKemampuan ActivityViewModel memakai penyegar lalu memasang petanya`() {
        val badan = badanFungsi(activityViewModel, "segarkanKemampuan", "ActivityViewModel.kt")

        wajibMemanggil(badan, "sidikAkses", "ActivityViewModel.segarkanKemampuan()", PEMICU)
        wajibCocok(
            badan = badan,
            pola = Regex("""penyegarKemampuan\s*\.\s*segarkan\s*\("""),
            di = "ActivityViewModel.segarkanKemampuan()",
            kenapa = "penjaga badai-request DAN penjaga peta-kosong dua-duanya hidup di " +
                "PenyegarKemampuan; memanggil authRepository.capabilities() langsung " +
                "melewati keduanya",
        )
        wajibCocok(
            badan = badan,
            pola = PENUGASAN_CAPABILITIES,
            di = "ActivityViewModel.segarkanKemampuan()",
            kenapa = "peta yang diambil harus DIPASANG; kalau tidak, gerbangnya tetap " +
                "memakai peta lama dan seluruh pengambilan ini mubazir",
        )
        wajibCocok(
            badan = badan,
            pola = Regex("""\bload\s*\(\s*\)"""),
            di = "ActivityViewModel.segarkanKemampuan()",
            kenapa = "item yang baru terbuka harus ikut fan-out angka, bukan tampil kosong",
        )
    }

    // ── HomeViewModel ────────────────────────────────────────────────────────

    @Test
    fun `HomeViewModel menyegarkan kemampuan di dalam loadDashboard()`() {
        val badan = badanFungsi(homeViewModel, "loadDashboard", "HomeViewModel.kt")

        wajibMemanggil(
            badan = badan,
            nama = "segarkanKemampuan",
            di = "HomeViewModel.loadDashboard()",
            kenapa = "grid Akses Cepat dinilai `gateAllows` yang MENDAHULUKAN peta " +
                "kemampuan; tanpa panggilan ini petanya beku sampai app dimatikan",
        )
    }

    @Test
    fun `segarkanKemampuan HomeViewModel memakai penyegar lalu memasang petanya`() {
        val badan = badanFungsi(homeViewModel, "segarkanKemampuan", "HomeViewModel.kt")

        wajibMemanggil(badan, "sidikAkses", "HomeViewModel.segarkanKemampuan()", PEMICU)
        wajibCocok(
            badan = badan,
            pola = Regex("""penyegarKemampuan\s*\.\s*segarkan\s*\("""),
            di = "HomeViewModel.segarkanKemampuan()",
            kenapa = "lihat alasan yang sama di ActivityViewModel",
        )
        wajibCocok(
            badan = badan,
            pola = PENUGASAN_CAPABILITIES,
            di = "HomeViewModel.segarkanKemampuan()",
            kenapa = "peta baru harus masuk ke _uiState; grid dirender dari state itu",
        )
    }

    // ── Identitas token ikut ke latch (temuan 4a) ────────────────────────────

    /**
     * Konstruktor [com.krisoft.tridjayaelektronik.ui.home.PenyegarKemampuan]
     * memang menuntut pemasok identitas token, jadi MELUPAKANNYA tak akan
     * kompilasi. Yang tak dijaga kompiler adalah memasok nilai MATI (`{ "" }`,
     * konstanta, atau apa pun yang tak pernah berubah) — dan itu memulihkan
     * pembekuan permanen persis seperti sebelum perbaikan, tanpa satu pun error.
     */
    @Test
    fun `kedua ViewModel memasok identitas token yang hidup ke PenyegarKemampuan`() {
        listOf(
            "ActivityViewModel.kt" to activityViewModel,
            "HomeViewModel.kt" to homeViewModel,
        ).forEach { (berkas, isi) ->
            val mulai = isi.indexOf("PenyegarKemampuan(")
            if (mulai < 0) fail("$berkas tak lagi membangun PenyegarKemampuan — kabelnya putus")
            val argumen = blokBerpasangan(isi, isi.indexOf('(', mulai), '(', ')', berkas)

            assertTrue(
                "$berkas: PenyegarKemampuan dibangun tanpa `authRepository.sidikTokenAkses`.\n" +
                    "Peta /api/me/capabilities dihitung server dari klaim TOKEN, sedangkan " +
                    "profil bisa mendahului token (updateProfile menulis role tanpa rotasi).\n" +
                    "Identitas token yang MATI di sini = latch membeku seumur proses app.\n" +
                    "Argumen yang terbaca: $argumen",
                argumen.contains("sidikTokenAkses"),
            )
            assertTrue(
                "$berkas: PenyegarKemampuan tak lagi mengambil dari authRepository.capabilities()",
                Regex("""\bcapabilities\s*\(\s*\)""").containsMatchIn(argumen),
            )
        }
    }

    // ── Meta: pemindainya sendiri harus bergigi ──────────────────────────────

    /**
     * Kegagalan yang paling mungkin dari test bergaya pemindai adalah HIJAU
     * PALSU — ia mencocokkan nama yang cuma disebut di komentar, lalu terus
     * hijau sesudah kabel aslinya dicabut. Kasus di bawah persis bentuk kedua
     * ViewModel sesudah pencabutan: KDoc-nya masih menyebut `segarkanKemampuan`,
     * badan `load()` tidak.
     */
    @Test
    fun `pemindai tidak tertipu penyebutan di komentar`() {
        val palsu = """
            /** Lihat [segarkanKemampuan] dan penyegarKemampuan.segarkan(). */
            class Palsu {
                // segarkanKemampuan(user) dinonaktifkan sementara
                private fun load() {
                    val teks = "segarkanKemampuan(user)"
                    hitung()
                }
            }
        """.trimIndent().tanpaKomentarDanTeks()

        assertFalse(
            "pemindai masih melihat `segarkanKemampuan` padahal ia cuma ada di komentar & " +
                "string — dengan begitu seluruh test di berkas ini tak menjaga apa pun",
            Regex("""\bsegarkanKemampuan\s*\(""").containsMatchIn(palsu),
        )
        assertFalse(
            "penyebutan `penyegarKemampuan.segarkan(` di KDoc juga tak boleh dihitung",
            Regex("""penyegarKemampuan\s*\.\s*segarkan\s*\(""").containsMatchIn(palsu),
        )
        assertTrue("kode di luar komentar/string harus tetap utuh", palsu.contains("hitung()"))
    }

    /**
     * Penjaga atas penjaga: [PENUGASAN_CAPABILITIES] harus membedakan `=` dari
     * `==`. Bentuk "sesudah mutasi" di bawah persis apa yang lolos hijau sebelum
     * `(?!=)` ditambahkan — peta diambil, dibandingkan, lalu DIBUANG.
     */
    @Test
    fun `pola penugasan menolak pembanding`() {
        val sesudahMutasi = """
            private fun segarkanKemampuan(user: UserDto?) {
                val peta = penyegarKemampuan.segarkan(sidikAkses(user)) ?: return
                if (capabilities == peta) return
                load()
            }
        """.trimIndent().tanpaKomentarDanTeks()

        assertFalse(
            "`capabilities ==` itu PEMBANDING; menghitungnya sebagai pemasangan membuat " +
                "seluruh penjaga ini hijau padahal petanya tak pernah dipasang",
            PENUGASAN_CAPABILITIES.containsMatchIn(sesudahMutasi),
        )

        assertTrue(
            "penugasan sungguhan tetap harus terbaca",
            PENUGASAN_CAPABILITIES.containsMatchIn("capabilities = peta"),
        )
        assertTrue(
            "argumen bernama (bentuk HomeViewModel) juga penugasan",
            PENUGASAN_CAPABILITIES.containsMatchIn("it.copy(capabilities = peta)"),
        )
        assertFalse(
            "`!=` juga pembanding, bukan pemasangan",
            PENUGASAN_CAPABILITIES.containsMatchIn("if (capabilities != peta) return"),
        )
    }

    @Test
    fun `pemindai menemukan panggilan yang formatnya berubah`() {
        val terformat = """
            private fun load() {
                segarkanKemampuan(
                    user,
                )
            }
        """.trimIndent().tanpaKomentarDanTeks()

        assertTrue(
            "regex ber-\\s* wajib memaafkan ganti baris — kalau tidak, pemformatan ulang " +
                "yang tak mengubah arti akan memerahkan build",
            Regex("""\bsegarkanKemampuan\s*\(""").containsMatchIn(terformat),
        )
    }

    // ── EksekutifViewModel ───────────────────────────────────────────────────

    /**
     * **Bentuknya SENGAJA berbeda dari dua VM di atas, jangan diseragamkan.**
     *
     * `ActivityViewModel`/`HomeViewModel` memakai petanya untuk gerbang di
     * layarnya sendiri, jadi keduanya WAJIB memasang `capabilities = peta` lalu
     * `load()` ulang. `EksekutifViewModel` tidak: gerbang tab Eksekutif dinilai
     * `MainActivity` dari cermin `AuthRepository.petaKemampuanTerakhir`, dan
     * layarnya sendiri tak punya satu pun item ber-gate.
     *
     * Yang dijaga di sini karena itu cuma dua: penyegarnya DIPANGGIL, dan
     * pemicunya sidik akses. Menuntut `capabilities = ...` di sini akan memaksa
     * menambah field yang tak pernah dibaca — permukaan tanpa manfaat.
     */
    @Test
    fun `EksekutifViewModel menyegarkan kemampuan di dalam muat()`() {
        val badan = badanFungsi(eksekutifViewModel, "muat", "EksekutifViewModel.kt")

        wajibMemanggil(
            badan = badan,
            nama = "segarkanKemampuan",
            di = "EksekutifViewModel.muat()",
            kenapa = "superadmin MENDARAT di tab ini dan bisa tak pernah membuka " +
                "Activity/Operasional lagi; tanpa panggilan ini cermin peta kemampuan " +
                "beku seumur proses, sehingga akses yang DICABUT tetap menampilkan " +
                "tab-nya (lalu dijawab 403) dan akses BARU tak pernah terbaca",
        )
    }

    @Test
    fun `segarkanKemampuan EksekutifViewModel memakai penyegar dan sidik akses`() {
        val badan = badanFungsi(eksekutifViewModel, "segarkanKemampuan", "EksekutifViewModel.kt")

        wajibMemanggil(badan, "sidikAkses", "EksekutifViewModel.segarkanKemampuan()", PEMICU)
        wajibCocok(
            badan = badan,
            pola = Regex("""penyegarKemampuan\s*\.\s*segarkan\s*\("""),
            di = "EksekutifViewModel.segarkanKemampuan()",
            kenapa = "penjaga badai-request DAN penjaga peta-kosong dua-duanya hidup di " +
                "PenyegarKemampuan; memanggil authRepository.capabilities() langsung " +
                "melewati keduanya",
        )
    }

    // ── Perkakas ─────────────────────────────────────────────────────────────

    private companion object {
        const val PEMICU = "sidik akses adalah PEMICU-nya; tanpa ini tak ada yang tahu kapan " +
            "peta perlu diambil ulang"

        /**
         * PENUGASAN ke `capabilities`, bukan PEMBANDINGAN dengannya.
         *
         * `(?!=)` bukan hiasan. Versi pertama pemindai ini berbunyi
         * `\bcapabilities\s*=` — yang juga cocok dengan `capabilities ==`. Kedua
         * `segarkanKemampuan` memang memuat pembanding (`if (peta ==
         * capabilities) return@launch` di `ActivityViewModel`), jadi mutasi
         * sekecil menukar sisi pembanding lalu MENGHAPUS baris pemasangannya —
         *
         *     - if (peta == capabilities) return@launch
         *     - capabilities = peta
         *     + if (capabilities == peta) return@launch
         *
         * — tetap HIJAU: petanya tak pernah dipasang, gerbang menu tetap memakai
         * peta jam 08:00, dan penjaga ini menyatakan semuanya baik-baik saja.
         * Penjaga palsu lebih berbahaya daripada tidak ada penjaga.
         *
         * Sifat itu dikunci [pola penugasan menolak pembanding].
         */
        val PENUGASAN_CAPABILITIES = Regex("""\bcapabilities\s*=(?!=)""")
    }

    private fun wajibMemanggil(badan: String, nama: String, di: String, kenapa: String) =
        wajibCocok(badan, Regex("""\b$nama\s*\("""), di, kenapa)

    private fun wajibCocok(badan: String, pola: Regex, di: String, kenapa: String) {
        if (pola.containsMatchIn(badan)) return
        fail(
            buildString {
                appendLine("Kabel penyegaran kemampuan PUTUS di $di.")
                appendLine("Dicari (di luar komentar & string): ${pola.pattern}")
                appendLine("Kenapa penting: $kenapa")
                appendLine("Badan fungsi yang terbaca:")
                appendLine(badan.trim().lines().joinToString("\n") { "  | $it" })
            }
        )
    }

    /**
     * Badan `fun <nama>(…) { … }`, dipotong lewat pencocokan kurung kurawal.
     *
     * Sengaja BUKAN "seluruh berkas memuat X": beda antara "dideklarasikan" dan
     * "dipanggil dari jalur hidup" persis yang dijaga berkas ini. Fungsi yang
     * masih ADA tapi tak pernah dipanggil lagi harus tetap merah.
     */
    private fun badanFungsi(sumber: String, nama: String, berkas: String): String {
        val tandaTangan = Regex("""\bfun\s+$nama\s*\([^)]*\)[^{;=]*\{""")
        val cocok = tandaTangan.find(sumber)
            ?: run {
                fail("$berkas tak lagi punya `fun $nama(…)` — kabelnya hilang, bukan cuma berubah")
                error("tak terjangkau")
            }
        return blokBerpasangan(sumber, cocok.range.last, '{', '}', berkas)
    }

    /** Potongan dari [mulai] (harus berisi [buka]) sampai pasangan [tutup]-nya. */
    private fun blokBerpasangan(sumber: String, mulai: Int, buka: Char, tutup: Char, berkas: String): String {
        var kedalaman = 0
        var i = mulai
        while (i < sumber.length) {
            when (sumber[i]) {
                buka -> kedalaman++
                tutup -> {
                    kedalaman--
                    if (kedalaman == 0) return sumber.substring(mulai, i + 1)
                }
            }
            i++
        }
        fail("$berkas: blok '$buka' tak tertutup — pemindai tak bisa memutus apa pun")
        error("tak terjangkau")
    }
}

/**
 * Buang komentar (baris, blok, blok bersarang) dan literal teks/char.
 *
 * INI yang membuat seluruh berkas bergigi — lihat KDoc kelas. Baris baru di
 * dalam komentar dipertahankan supaya pesan gagal tetap terbaca per baris.
 *
 * Keterbatasan yang disengaja: literal teks dibuang UTUH, termasuk isi template
 * string di dalamnya. Untuk dua ViewModel yang dipindai itu tak jadi soal
 * (keduanya tak memuat template ber-tanda-kutip), dan menukarnya dengan parser
 * Kotlin sungguhan jauh lebih mahal daripada yang dijaga.
 */
internal fun String.tanpaKomentarDanTeks(): String {
    val keluar = StringBuilder(length)
    var i = 0
    var kedalamanBlok = 0
    while (i < length) {
        val ini = this[i]
        val depan = if (i + 1 < length) this[i + 1] else ' '
        when {
            kedalamanBlok > 0 -> when {
                ini == '/' && depan == '*' -> { kedalamanBlok++; i += 2 }
                ini == '*' && depan == '/' -> { kedalamanBlok--; i += 2 }
                else -> { if (ini == '\n') keluar.append('\n'); i++ }
            }
            ini == '/' && depan == '*' -> { kedalamanBlok = 1; i += 2 }
            ini == '/' && depan == '/' -> while (i < length && this[i] != '\n') i++
            startsWith("\"\"\"", i) -> {
                i += 3
                while (i < length && !startsWith("\"\"\"", i)) {
                    if (this[i] == '\n') keluar.append('\n')
                    i++
                }
                i = minOf(length, i + 3)
            }
            ini == '"' || ini == '\'' -> {
                val batas = ini
                i++
                while (i < length && this[i] != batas) {
                    if (this[i] == '\\') i++
                    i++
                }
                i++
            }
            else -> { keluar.append(ini); i++ }
        }
    }
    return keluar.toString()
}
