package com.krisoft.tridjayaelektronik.ui.home

import com.krisoft.tridjayaelektronik.ui.activity.KUPON_GEBYAR_MENU_ROLES
import com.krisoft.tridjayaelektronik.ui.activity.SPK_CREATE_BLOCKED_ROLES
import com.krisoft.tridjayaelektronik.ui.activity.SPK_CREATE_ROLES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
 * **Butir pertama di atas adalah CATATAN SEJARAH, bukan keadaan sekarang.** Pada
 * 2026-08-31 `trainee` DICABUT dari `CRM_INPUT_ROLES` (rust-shared) dan dari
 * `CRM_MENU_ROLES` (di sini) atas keputusan user — masa training dipersempit ke
 * Data Inventory + Aktivitas Harian + Pengaturan Akun. `STAFF_MENU_ROLES` TIDAK
 * ikut: `STAFF_SELF_SERVICE_ROLES` memikul absen hari pertama trainee dan
 * `attendance/report.rs` menyaring roster dari daftar yang sama. Jadi kalau
 * test `CRM_MENU_ROLES` merah dengan `trainee` di sisi mana pun, yang benar
 * bukan "kembalikan trainee" melainkan periksa apakah KEDUA sisi sudah dicabut.
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
     * Varian EJAAN garis-bawah yang kembaran ber-tanda-hubungnya ada di daftar
     * Rust yang sama (`kepala_cabang` di samping `kepala-cabang`).
     *
     * **INI CELAH NYATA, BUKAN VARIAN YANG BOLEH DIABAIKAN — baca sebelum
     * menganggapnya beres.** Versi pertama test ini membuang slug-slug tersebut
     * dengan alasan "app menormalkan role ke bentuk ber-tanda-hubung lebih dulu
     * lewat `effectiveRoles`". Review adversarial 2026-08-28 membuktikan premis
     * itu SALAH: `HomeScreen.kt::effectiveRoles` hanya `trim()` + `lowercase()`,
     * dan `grep -rn "replace('_'" app/src/main` nihil. Rust menulis kedua ejaan
     * justru karena `AuthUser.role` adalah string MENTAH dari kolom DB yang
     * isinya bercampur — jadi akun ber-`role` `kepala_cabang` benar-benar tak
     * akan cocok dengan `STAFF_MENU_ROLES`, dan kehilangan kartu Absen & Slip
     * Gaji selama peta kemampuan belum termuat.
     *
     * Dipertahankan sebagai PENGECUALIAN BERNAMA (bukan filter diam-diam) supaya
     * selisihnya tetap TERBACA di berkas ini alih-alih terhapus dari
     * perbandingan. Menutupnya sungguhan = menormalkan `_`→`-` di
     * `effectiveRoles`, dan itu perubahan PERILAKU gate yang layak dinilai
     * sendiri — bukan ditumpangkan ke commit sinkronisasi daftar role.
     */
    private fun varianGarisBawahDenganKembaran(slug: Set<String>): Set<String> =
        slug.filter { it.contains('_') && it.replace('_', '-') in slug }.toSet()

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
        val ejaanGarisBawah = varianGarisBawahDenganKembaran(rust)
        val kurang = rust - kotlin - hanyaDiRust.keys - ejaanGarisBawah
        val lebih = kotlin - rust - hanyaDiKotlin.keys
        if (kurang.isEmpty() && lebih.isEmpty()) return
        fail(
            buildString {
                appendLine("Cadangan offline `$label` tak lagi cocok dengan rust-shared.")
                if (ejaanGarisBawah.isNotEmpty()) {
                    appendLine("(catatan: ${ejaanGarisBawah.sorted()} adalah celah ejaan `_` yang")
                    appendLine(" DIKETAHUI & belum ditutup — lihat KDoc varianGarisBawahDenganKembaran)")
                }
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

    /**
     * Ubin **SN Goda** mencerminkan PENAMBAH registry (`GODA_SERIAL_ADD_ROLES`,
     * dipisah dari `GODA_SERIAL_EDIT_ROLES` 2026-09-03), bukan pembacanya
     * (`GODA_VIEW_ROLES`) maupun penulis-ganti — layarnya cuma bisa menambah SN
     * (`POST`, tak pernah `PUT`). Kalau kelak server melebarkan penambahnya,
     * cadangan offline di sini harus ikut; kalau ia menyempit, ubin yang
     * tertinggal berarti tombol simpan yang dijawab 403 di depan orang yang
     * sedang memegang unitnya.
     */
    @Test
    fun `GODA_SERIAL_MENU_ROLES mencerminkan GODA_SERIAL_ADD_ROLES`() = jalankan { sumber ->
        bandingkan(
            label = "GODA_SERIAL_MENU_ROLES",
            rust = slugRust(sumber, "GODA_SERIAL_ADD_ROLES"),
            kotlin = GODA_SERIAL_MENU_ROLES,
        )
    }

    // ── DENYLIST SPK ─────────────────────────────────────────────────────────
    //
    // Dua daftar di bawah dibandingkan sebagai DENYLIST, bukan allowlist, karena
    // begitulah backend menghitungnya (`can_view_spk_pipeline`/`can_create_spk`
    // memakai `SPK_BLOCKED_ROLES`/`SPK_CREATE_BLOCKED_ROLES`).
    //
    // **Kenapa dua test ini ada.** Sisi Kotlin dulu menurunkan keduanya sebagai
    // SELISIH dari `KNOWN_ROLES` (`KNOWN_ROLES - "ai-engineer"`), dengan alasan
    // "role baru otomatis ikut, sama seperti backend". Premis itu benar hanya
    // selama backend meloloskan tiap role baru — dan sejak `trainee` lahir ia
    // tidak benar lagi. Akibatnya: menambahkan `trainee` ke `KNOWN_ROLES` (demi
    // Absen & Input Prospek yang memang haknya) diam-diam memberinya kartu SPK
    // juga, tanpa satu pun test merah. Ditemukan review adversarial 2026-08-28,
    // sebelum landing. Dua test inilah yang membuat kelas itu tak bisa terulang.

    @Test
    fun `SPK_BLOCKED_ROLES mencerminkan denylist pipeline di rust-shared`() = jalankan { sumber ->
        val rust = slugRust(sumber, "SPK_BLOCKED_ROLES")
        assertEquals(
            "Denylist `spk.pipeline` berbeda dari rust-shared — role yang HILANG dari sisi " +
                "Kotlin akan melihat kartu antrian/riwayat SPK saat offline lalu dijawab 403.",
            rust,
            SPK_BLOCKED_ROLES,
        )
        // Arah kedua, dinyatakan terpisah supaya kegagalannya menyebut akibatnya:
        // daftar TAMPIL-nya benar-benar tak memuat satu pun role terlarang.
        for (role in rust) {
            assertFalse("`$role` diblokir server tapi masih ada di SPK_MENU_ROLES", role in SPK_MENU_ROLES)
        }
    }

    @Test
    fun `SPK_CREATE_ROLES menolak seluruh SPK_CREATE_BLOCKED_ROLES rust-shared`() = jalankan { sumber ->
        val rust = slugRust(sumber, "SPK_CREATE_BLOCKED_ROLES")
        assertEquals(
            "Denylist `spk.create` berbeda dari rust-shared — role yang hilang dari sisi Kotlin " +
                "akan melihat kartu 'Buat SPK' lalu `create_delivery` menjawab 403.",
            rust,
            SPK_CREATE_BLOCKED_ROLES,
        )
        for (role in rust) {
            assertFalse("`$role` diblokir server tapi masih ada di SPK_CREATE_ROLES", role in SPK_CREATE_ROLES)
        }
    }
}
