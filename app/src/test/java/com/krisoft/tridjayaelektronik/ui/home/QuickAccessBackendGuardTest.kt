package com.krisoft.tridjayaelektronik.ui.home

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * `backendGuard` di registri ubin WAJIB menunjuk berkas & simbol Rust yang
 * benar-benar ADA — tanpa penjaga ini field itu cuma prosa yang ikut membusuk
 * seperti komentar yang digantikannya.
 *
 * Web sudah punya padanannya sejak lama (`navCatalog.test.ts`, "backendGuard
 * menyebut simbol & berkas Rust yang benar-benar ada"); sisi mobile TIDAK.
 *
 * **YANG DITANGKAP: simbol/berkas yang TIDAK ADA** — salah ketik, simbol yang
 * di-rename, berkas yang pindah/hilang.
 *
 * **YANG TIDAK DITANGKAP: simbol yang ADA tapi BUKAN guard-nya.** Ini bukan
 * kekurangan yang bisa ditambal di sini, dan sengaja ditulis supaya tak ada
 * yang mengira penjaga ini lebih kuat dari kenyataannya — padanan web-nya punya
 * batas yang persis sama. Contoh nyatanya justru kasus yang memicu penjaga ini
 * dibuat: entri `opname` menyimpan `"… opname.rs has_admin/has_manager"` sampai
 * 2026-08-21 padahal `authorize_view`-lah guard sebenarnya. Kedua simbol itu
 * ADA di `opname.rs`, jadi test ini TETAP HIJAU atasnya — dibuktikan dengan
 * mengembalikan string lamanya lalu menjalankan ulang. Yang menangkap kelas itu
 * cuma pembacaan manusia saat guard-nya berubah.
 *
 * **Batasnya jujur**: pohon Rust hanya ada saat modul ini dibuka dari monorepo
 * (`<repo>/mobile`). Di checkout mandiri `TridjayaApp` ia tak ada, dan test ini
 * `assumeTrue` — DILEWATI, bukan hijau palsu. Pesannya mencetak alasan supaya
 * "dilewati" tak terbaca sebagai "lulus".
 */
class QuickAccessBackendGuardTest {

    /** Naik dari direktori kerja modul sampai menemukan pohon Rust monorepo. */
    private fun akarRust(): File? {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(6) {
            val kandidat = dir?.let { File(it, "services") }
            val shared = dir?.let { File(it, "packages/rust-shared") }
            if (kandidat?.isDirectory == true && shared?.isDirectory == true) return dir
            dir = dir?.parentFile
        }
        return null
    }

    private fun sumberRust(akar: File): Pair<String, Set<String>> {
        val isi = StringBuilder()
        val berkas = mutableSetOf<String>()
        for (sub in listOf("services", "packages/rust-shared", "gateway")) {
            val d = File(akar, sub)
            if (!d.isDirectory) continue
            d.walkTopDown()
                .onEnter { it.name != "target" && it.name != "node_modules" }
                .filter { it.isFile && it.name.endsWith(".rs") }
                .forEach { berkas.add(it.name); isi.append(it.readText()).append('\n') }
        }
        return isi.toString() to berkas
    }

    @Test
    fun `setiap backendGuard menunjuk berkas dan simbol Rust yang ada`() {
        val akar = akarRust()
        assumeTrue(
            "Pohon Rust tak ditemukan (checkout mandiri TridjayaApp?) — penjaga ini DILEWATI, " +
                "bukan lulus. Jalankan dari monorepo untuk benar-benar mengujinya.",
            akar != null,
        )
        val (sumber, berkasRust) = sumberRust(akar!!)
        assertTrue("pohon Rust terbaca tapi kosong", sumber.length > 10_000)

        // Deklarasi, BUKAN sekadar penyebutan: simbol yang cuma muncul di
        // komentar tak boleh meloloskan `backendGuard` yang sudah basi.
        val dideklarasikan = mutableSetOf<String>()
        Regex("""\b(?:fn|const|static|struct|enum|trait|type|mod)\s+([A-Za-z_][A-Za-z0-9_]*)""")
            .findAll(sumber).forEach { dideklarasikan.add(it.groupValues[1]) }

        // Guard yang menyatakan TIDAK ADA gerbang backend tak punya simbol Rust
        // untuk diperiksa. Registri ini memakai TIGA ejaan untuk maksud yang
        // sama — ketiganya diakui apa adanya alih-alih diseragamkan di sini,
        // supaya penjaga ini tak menyeret perubahan teks yang bukan urusannya.
        // (Web memakai satu ejaan baku, "tanpa guard:"; menyamakannya di sisi
        // mobile layak dikerjakan, tapi terpisah.)
        val tanpaGerbang = listOf("tanpa guard:", "tanpa gate role", "tidak ada")

        val salah = mutableListOf<String>()
        for (menu in QUICK_ACCESS_MENUS) {
            val guard = menu.backendGuard
            if (tanpaGerbang.any { guard.startsWith(it) }) continue
            for (berkas in Regex("""\b[a-z_]+\.rs\b""").findAll(guard).map { it.value }) {
                if (berkas !in berkasRust) salah += "${menu.id}: berkas '$berkas' tak ada di pohon Rust"
            }
            // Simbol = token ber-underscore atau HURUF_BESAR; kata biasa
            // ("gateway", "service") dilewati supaya prosa tetap boleh.
            for (simbol in Regex("""\b[A-Za-z][A-Za-z0-9]*(?:_[A-Za-z0-9]+)+\b""")
                .findAll(guard).map { it.value }) {
                if (simbol.endsWith(".rs")) continue
                if (simbol !in dideklarasikan) {
                    salah += "${menu.id}: simbol '$simbol' tak DIDEKLARASIKAN di Rust mana pun"
                }
            }
        }
        assertTrue("backendGuard basi:\n" + salah.joinToString("\n"), salah.isEmpty())
    }
}
