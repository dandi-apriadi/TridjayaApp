package com.krisoft.tridjayaelektronik.ui.home

import com.krisoft.tridjayaelektronik.ui.activity.KUPON_GEBYAR_MENU_ROLES
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * **Daftar role CADANGAN OFFLINE vs sumbernya di rust-shared.**
 *
 * Tiap `*_MENU_ROLES` di app ini adalah SALINAN sebuah konstanta di
 * `packages/rust-shared/src/capabilities.rs`. Salinan itu cuma dipakai saat peta
 * `GET /api/me/capabilities` belum termuat (sinyal lemah, bukan cuma mode
 * pesawat) — jadi ketika ia melenceng, gejalanya **tak pernah** berupa error:
 * menunya hilang/muncul untuk sebagian orang selama beberapa detik pertama tiap
 * kali app dibuka, lalu "sembuh sendiri" begitu peta datang. Tak ada yang
 * melaporkannya sebagai bug; yang tercatat cuma "kadang menunya nggak ada".
 *
 * Audit 2026-08-28 menemukan tiga penyimpangan sekaligus yang semuanya lolos
 * bertahun-berminggu:
 *  - `trainee` (role primary kelima sejak 17 Agt) tak pernah ditambahkan ke
 *    `STAFF_MENU_ROLES`/`CRM_MENU_ROLES` walau rust-shared memuatnya sejak hari
 *    pertama — trainee offline kehilangan Absen, Slip Gaji, dan Input Prospek,
 *    yaitu SELURUH pekerjaannya;
 *  - `KUPON_GEBYAR_MENU_ROLES` salah eja (`admin-sales` vs `admin-penjualan`),
 *    kurang `kasir`, DAN kelebihan empat role yang doc server eksplisit
 *    mengecualikannya.
 *
 * **Kenapa membaca sumber Rust, bukan menyalin daftarnya ke test.** Salinan
 * kedua membusuk dengan cara yang sama persis dengan salinan pertama — test-nya
 * akan tetap hijau sambil ikut salah. Pola ini dipinjam dari
 * [QuickAccessBackendGuardTest] (dan `navCatalog.test.ts` di web).
 *
 * **Batasnya jujur**: pohon Rust hanya ada saat modul ini dibuka dari monorepo
 * (`<repo>/mobile`). Di checkout mandiri `TridjayaApp` ia tak ada, dan test ini
 * `assumeTrue` — DILEWATI, bukan hijau palsu.
 */
class CadanganRoleCerminRustTest {

    /** Naik dari direktori kerja modul sampai menemukan pohon Rust monorepo. */
    private fun akarRust(): File? {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(6) {
            val shared = dir?.let { File(it, "packages/rust-shared") }
            if (shared?.isDirectory == true) return dir
            dir = dir?.parentFile
        }
        return null
    }

    /**
     * Ekstrak slug dari badan `pub const <NAMA>: &[&str] = &[ ... ];`.
     *
     * Komentar dibuang DULU, dan itu bukan kehati-hatian berlebih: badan
     * konstanta di `capabilities.rs` penuh komentar penjelas, dan `capabilities.rs`
     * sendiri memperingatkan bahwa kata berkutip di dalam komentar pernah
     * melahirkan role hantu (`aktif-penuh`) pada pemindai serupa di web.
     */
    private fun slugRust(sumber: String, nama: String): Set<String> {
        val awal = Regex("""pub\s+const\s+$nama\s*:\s*&\[&str\]\s*=\s*&\[""").find(sumber)
            ?: fail("konstanta `$nama` tak ada lagi di rust-shared — perbarui test ini").let { return emptySet() }
        val mulai = awal.range.last + 1
        val tutup = sumber.indexOf("];", mulai)
        if (tutup < 0) fail("badan `$nama` tak tertutup `];` — pemindai perlu diperbaiki")
        val badan = sumber.substring(mulai, tutup)
            .replace(Regex("""//[^\n]*"""), "")          // komentar baris
            .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "") // komentar blok
        return Regex(""""([^"]+)"""").findAll(badan).map { it.groupValues[1] }.toSet()
    }

    private fun sumberCapabilities(akar: File): String =
        File(akar, "packages/rust-shared/src/capabilities.rs").readText()

    /**
     * Buang varian EJAAN garis-bawah yang kembaran ber-tanda-hubungnya sudah ada
     * di daftar yang sama (`kepala_cabang` di samping `kepala-cabang`).
     *
     * Rust menulis kedua ejaan karena ia mencocokkan langsung ke kolom DB yang
     * isinya bercampur; app menormalkan role ke bentuk ber-tanda-hubung lebih
     * dulu (`effectiveRoles`), jadi varian garis-bawah tak akan pernah muncul di
     * sisi Kotlin. Ditulis sebagai ATURAN, bukan daftar slug: versi pertama test
     * ini mendaftar `kepala_cabang`+`admin_sales` satu per satu lalu langsung
     * merah atas `admin_stok`+`delivery_control` yang terlewat — daftar manual
     * untuk pola yang bisa dirumuskan hanya memindahkan pembusukannya ke test.
     *
     * Syarat "kembarannya ADA" itu yang menjaga ketelitiannya: slug garis-bawah
     * yang berdiri SENDIRI (tanpa kembaran) tetap dilaporkan sebagai selisih,
     * karena itu memang ejaan yang app tak akan pernah cocokkan.
     */
    private fun tanpaVarianGarisBawah(slug: Set<String>): Set<String> =
        slug.filterNot { it.contains('_') && it.replace('_', '-') in slug }.toSet()

    /**
     * `pengecualianSah` = slug yang SENGAJA ada di satu sisi saja, dengan
     * alasannya. Menambah baris di sini adalah keputusan sadar, bukan cara
     * mendiamkan test — pola `SENGAJA_TANPA_MENU` di `navCatalog.test.ts` web.
     */
    private fun bandingkan(
        label: String,
        rust: Set<String>,
        kotlin: Set<String>,
        hanyaDiRust: Map<String, String> = emptyMap(),
        hanyaDiKotlin: Map<String, String> = emptyMap(),
    ) {
        val rustDinilai = tanpaVarianGarisBawah(rust)
        val kurang = rustDinilai - kotlin - hanyaDiRust.keys
        val lebih = kotlin - rustDinilai - hanyaDiKotlin.keys
        if (kurang.isEmpty() && lebih.isEmpty()) return
        fail(
            buildString {
                appendLine("Cadangan offline `$label` tak lagi cocok dengan rust-shared.")
                appendLine()
                if (kurang.isNotEmpty()) {
                    appendLine("KURANG (server mengizinkan, cadangan offline tidak): ${kurang.sorted()}")
                    appendLine("  Akibatnya: role itu kehilangan menunya selama peta kemampuan belum")
                    appendLine("  termuat — persis 'orang yang diam-diam tak bisa bekerja'.")
                }
                if (lebih.isNotEmpty()) {
                    appendLine("LEBIH (cadangan offline mengizinkan, server tidak): ${lebih.sorted()}")
                    appendLine("  Akibatnya: menu tampil saat offline lalu endpoint-nya 403 begitu")
                    appendLine("  online — pola 'menu mati' yang CLAUDE.md repo ini ingin cegah.")
                }
                appendLine()
                appendLine("Kalau selisihnya MEMANG disengaja, daftarkan slug-nya di parameter")
                appendLine("`hanyaDiRust`/`hanyaDiKotlin` BESERTA alasannya — jangan menghapus")
                appendLine("perbandingannya.")
            }
        )
    }

    private fun jalankan(blok: (String) -> Unit) {
        val akar = akarRust()
        assumeTrue(
            "Pohon Rust tak ditemukan (checkout mandiri TridjayaApp?) — penjaga ini DILEWATI, " +
                "bukan lulus. Jalankan dari monorepo untuk benar-benar mengujinya.",
            akar != null,
        )
        blok(sumberCapabilities(akar!!))
    }

    @Test
    fun `STAFF_MENU_ROLES mencerminkan STAFF_SELF_SERVICE_ROLES`() = jalankan { sumber ->
        bandingkan(
            label = "STAFF_MENU_ROLES",
            rust = slugRust(sumber, "STAFF_SELF_SERVICE_ROLES"),
            kotlin = STAFF_MENU_ROLES,
            // Varian ejaan garis-bawah ditangani `tanpaVarianGarisBawah` sebagai
            // aturan, bukan didaftar di sini.
        )
    }

    @Test
    fun `CRM_MENU_ROLES mencerminkan CRM_INPUT_ROLES`() = jalankan { sumber ->
        bandingkan(
            label = "CRM_MENU_ROLES",
            rust = slugRust(sumber, "CRM_INPUT_ROLES"),
            kotlin = CRM_MENU_ROLES,
        )
    }

    @Test
    fun `KUPON_GEBYAR_MENU_ROLES mencerminkan KUPON_GEBYAR_LIHAT_ROLES`() = jalankan { sumber ->
        bandingkan(
            label = "KUPON_GEBYAR_MENU_ROLES",
            rust = slugRust(sumber, "KUPON_GEBYAR_LIHAT_ROLES"),
            kotlin = KUPON_GEBYAR_MENU_ROLES,
        )
    }
}
