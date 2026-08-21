package com.krisoft.tridjayaelektronik.ui

import com.krisoft.tridjayaelektronik.data.petaKemampuanSah
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * **Daftar TERTUTUP pembaca peta kemampuan — penjaga atas bug LATEN.**
 *
 * `ui/home/PenyegarKemampuan` menutup pembekuan peta `GET /api/me/capabilities`
 * untuk DUA ViewModel yang di-scope ke tab kept-alive (`HomeViewModel`,
 * `ActivityViewModel`). Empat pembaca lain memanggil
 * `AuthRepository.capabilities()` mentah, satu kali, tanpa penyegaran —
 * dan itu **aman hari ini, dengan sebab yang berbeda-beda** (diperiksa
 * 2026-08-20, jangan diringkas jadi "semuanya berumur pendek"):
 *
 *  - `ui/kpi/KpiViewModel` — VM `ROUTE_KPI`, di-push lalu di-pop
 *    (`ActivityNavHost`), mati bersama layarnya;
 *  - `ui/homeservice/HomeServiceDetailViewModel` — sama, `ROUTE_HS_DETAIL`;
 *  - `ui/opname/OpnameDetailViewModel` — **bukan** push/pop: detailnya
 *    state-swap di dalam `OpnameListScreen`, jadi VM-nya menempel pada
 *    `NavBackStackEntry` `ROUTE_OPNAME`. Yang menyelamatkannya bukan umur
 *    pendek melainkan letak pengambilannya: DI DALAM `load()`, jadi tiap
 *    detail dibuka & tiap tarik-turun mengambil ulang;
 *  - `ui/indent/IndentDetailScreen` (`IndentDetailViewModel`) — juga
 *    state-swap, VM-nya menempel pada `ROUTE_INDENT`, dan pengambilannya di
 *    `init`. Petanya karena itu berumur satu KUNJUNGAN layar Indent, bukan satu
 *    detail. Pulih sendiri dengan keluar-masuk layar.
 *
 * **Yang dijaga berkas ini adalah pindahnya salah satu dari mereka ke scope yang
 * hidup lebih lama.** Menaikkan VM mana pun di atas ke root tab (yang tetap
 * ter-compose seumur proses, lihat `visitedDestinations` di `MainActivity`)
 * membuat petanya beku sampai app dimatikan: akses baru tak pernah membuka
 * menunya, akses yang dicabut tetap menampilkan menu yang lalu dijawab 403.
 * Itu persis keluhan yang baru saja diperbaiki, kembali lewat pintu lain.
 *
 * Perubahan seperti itu **tak melewati satu pun pemeriksa**: ia terjadi di
 * `ActivityNavHost`/`MainActivity`, jauh dari berkas ViewModel-nya, dan tak
 * mengubah satu baris pun di sini. Daftar tertutup di bawah adalah tripwire-nya
 * — menambah pembaca baru (atau memindahkan yang lama) memerahkan build dengan
 * pesan yang menyebutkan apa yang harus dipikirkan.
 *
 * **Kenapa BUKAN "pasang PenyegarKemampuan di keempatnya juga".** Latch itu
 * gunanya menahan pengambilan BERULANG dalam satu umur ViewModel. Untuk VM yang
 * mengambil sekali per layar ia tak mengubah apa pun kecuali menambah satu
 * lapis tak terpakai; untuk `OpnameDetailViewModel` ia justru MENGURANGI
 * kesegaran (pengambilan per `load()` lebih sering daripada per perubahan kunci
 * latch). Menyeragamkan demi keseragaman menambah permukaan tanpa menutup satu
 * pun kegagalan.
 */
class PembacaPetaKemampuanTest {

    /**
     * Satu-satunya PANGGILAN `capabilities()` yang boleh ada di app, beserta
     * alasan tiap pemanggil boleh berdiri sendiri.
     */
    private val pembacaSah = mapOf(
        "data/AuthRepository.kt" to
            "satu-satunya yang benar-benar menembak `GET /api/me/capabilities`",
        "ui/home/HomeViewModel.kt" to
            "tab kept-alive — WAJIB lewat PenyegarKemampuan, dijaga KabelPenyegaranKemampuanTest",
        "ui/activity/ActivityViewModel.kt" to
            "tab kept-alive — WAJIB lewat PenyegarKemampuan, dijaga KabelPenyegaranKemampuanTest",
        "ui/opname/OpnameDetailViewModel.kt" to
            "di dalam load(), jadi tiap detail dibuka & tiap tarik-turun mengambil ulang",
        "ui/homeservice/HomeServiceDetailViewModel.kt" to
            "VM route ROUTE_HS_DETAIL yang di-push lalu di-pop; mati bersama layarnya",
        "ui/kpi/KpiViewModel.kt" to
            "VM route ROUTE_KPI yang di-push lalu di-pop; mati bersama layarnya",
        "ui/indent/IndentDetailScreen.kt" to
            "VM menempel ROUTE_INDENT; peta berumur satu kunjungan layar Indent",
    )

    @Test
    fun `tak ada pembaca peta kemampuan di luar daftar`() {
        val ditemukan = berkasSumber()
            .filter { (_, isi) -> PANGGILAN.containsMatchIn(isi) }
            .map { (jalur, _) -> jalur }
            .toSortedSet()

        val baru = ditemukan - pembacaSah.keys
        val hilang = pembacaSah.keys - ditemukan

        if (baru.isEmpty() && hilang.isEmpty()) return
        fail(
            buildString {
                appendLine("Daftar pembaca peta kemampuan tak lagi cocok dengan kode.")
                if (baru.isNotEmpty()) {
                    appendLine()
                    appendLine("PEMBACA BARU (belum dinilai): ${baru.joinToString()}")
                    appendLine("Sebelum menambahkannya ke daftar, jawab dua pertanyaan ini:")
                    appendLine()
                    appendLine("  1. SCOPE. ViewModel-nya di-scope ke mana? Kalau ia hidup")
                    appendLine("     selama proses app — root tab, atau apa pun yang tak")
                    appendLine("     pernah di-pop — pengambilan SEKALI berarti gerbangnya")
                    appendLine("     beku: akses yang baru diberi tak pernah membuka menunya,")
                    appendLine("     akses yang dicabut tetap tampil lalu dijawab 403. Untuk")
                    appendLine("     scope seperti itu pakai `ui/home/PenyegarKemampuan`.")
                    appendLine("     Perhatikan: layar yang di-'buka' lewat state-swap di dalam")
                    appendLine("     layar daftar (pola Indent/Opname) TIDAK mati saat ditutup —")
                    appendLine("     VM-nya menempel pada NavBackStackEntry layar DAFTAR-nya.")
                    appendLine()
                    appendLine("  2. ARTI `null`. `AuthRepository.capabilities()` menjawab")
                    appendLine("     `null` untuk gagal/offline/server lama DAN untuk badan 200")
                    appendLine("     yang terdekode jadi peta kosong (lihat petaKemampuanSah).")
                    appendLine("     `null` berarti TIDAK TAHU, bukan `false` — dan yang")
                    appendLine("     menentukan akibatnya adalah NILAI DEFAULT gerbangmu, sebab")
                    appendLine("     pola `capabilities()?.let { … }` tak menjalankan apa pun")
                    appendLine("     saat `null`. Default `false` = tombol tetap hilang")
                    appendLine("     (HomeServiceDetail, Kpi); default `true` = tombol tetap")
                    appendLine("     tampil lalu dijawab 403 (OpnameDetail.canPropose). Pilih")
                    appendLine("     defaultnya dengan sadar, jangan menyalin pola tetangga.")
                    appendLine("     Peta yang ADA tapi tak memuat kuncinya tetap fail-closed:")
                    appendLine("     `caps[\"x\"] == true` dan `gateAllows` sama-sama `false`.")
                }
                if (hilang.isNotEmpty()) {
                    appendLine()
                    appendLine("TAK LAGI MEMBACA: ${hilang.joinToString()}")
                    appendLine("Kalau pembacanya memang dihapus, hapus juga barisnya dari")
                    appendLine("`pembacaSah` — daftar yang memuat berkas mati akan menutupi")
                    appendLine("pembaca baru yang kebetulan berbagi nama.")
                }
            }
        )
    }

    @Test
    fun `AuthRepository menyaring peta kosong sebelum menyerahkannya`() {
        val isi = berkasSumber().first { it.first == "data/AuthRepository.kt" }.second
        val awal = Regex("""\bfun\s+capabilities\s*\(""").find(isi)?.range?.first
            ?: run {
                fail("AuthRepository tak lagi punya `fun capabilities(` — kabelnya hilang")
                error("tak terjangkau")
            }
        val akhir = Regex("""\bfun\s+\w""").find(isi, awal + 4)?.range?.first ?: isi.length
        val badan = isi.substring(awal, akhir)

        if (Regex("""petaKemampuanSah\s*\(""").containsMatchIn(badan)) return
        fail(
            buildString {
                appendLine("`AuthRepository.capabilities()` tak lagi menyaring lewat petaKemampuanSah().")
                appendLine("Badan 200 yang rusak (`{\"data\":{}}`, `capabilities: null`) terdekode")
                appendLine("jadi peta KOSONG — `CapabilitiesDto.capabilities` ber-default emptyMap()")
                appendLine("dan converter-nya coerceInputValues.")
                appendLine()
                appendLine("Yang HILANG kalau peta kosong itu lolos ke pemanggil (diperiksa satu")
                appendLine("per satu — efeknya BERBEDA per pembaca, jangan diringkas):")
                appendLine("  - Setujui/Tolak indent LENYAP. `gateAllows` fail-closed atas kunci")
                appendLine("    yang absen, dan peta kosong itu 'ada' jadi cadangan role lokal")
                appendLine("    tak pernah dipakai. Ini kerugian yang sebenarnya ditutup di sini.")
                appendLine("  - Tombol Usulan SN opname BERBALIK jadi fail-CLOSED (canPropose")
                appendLine("    ber-default true, peta kosong menyetelnya false) — hilang, padahal")
                appendLine("    server belum tentu menolak.")
                appendLine("  - Triase/atur-tarik home service dan daftar karyawan KPI TIDAK")
                appendLine("    berubah: keduanya sudah false secara default, jadi peta kosong")
                appendLine("    dan null menghasilkan hasil yang identik. Menyaring di sini tak")
                appendLine("    memperbaiki keduanya — ia cuma menjaga aturannya seragam.")
                appendLine("Yang terbaca:")
                appendLine(badan.trim().lines().joinToString("\n") { "  | $it" })
            }
        )
    }

    // ── petaKemampuanSah sebagai fungsi ──────────────────────────────────────

    @Test
    fun `peta kosong dihitung gagal`() {
        assertNull("peta kosong = badan rusak, bukan 'tak boleh apa-apa'", petaKemampuanSah(emptyMap()))
        assertNull(petaKemampuanSah(null))
    }

    @Test
    fun `peta berisi diteruskan apa adanya`() {
        val peta = mapOf("kpi.manage" to false, "serial.propose" to true)
        assertSame("tak ada penyalinan — pemanggil membandingkan peta dengan ==", peta, petaKemampuanSah(peta))
    }

    @Test
    fun `peta yang semua nilainya false tetap SAH`() {
        // `capabilities_for` memancarkan seluruh kunci walau role-nya kosong,
        // jadi "semua false" adalah jawaban server yang WAJAR — menolaknya akan
        // membuat gerbang jatuh ke daftar role lokal untuk orang yang memang
        // tak punya akses apa pun, yaitu kebalikan dari yang diinginkan.
        val peta = mapOf("kpi.manage" to false)
        assertEquals(peta, petaKemampuanSah(peta))
    }

    // ── Perkakas ─────────────────────────────────────────────────────────────

    private companion object {
        /**
         * PANGGILAN, bukan deklarasi: `fun capabilities()` di `AuthApi`/
         * `AuthRepository` bukan pembaca peta, dan memasukkannya membuat daftar
         * ini bercerita tentang berkas yang salah.
         */
        val PANGGILAN = Regex("""\.\s*capabilities\s*\(\s*\)""")
    }

    /** Seluruh sumber `app/src/main`, komentar & literal teks sudah dibuang. */
    private fun berkasSumber(): List<Pair<String, String>> {
        val akar = sequenceOf(
            File("src/main/java/com/krisoft/tridjayaelektronik"),
            File("app/src/main/java/com/krisoft/tridjayaelektronik"),
        ).firstOrNull { it.isDirectory }
            ?: error("sumber app tak ketemu — cwd=${File(".").absolutePath}")

        return akar.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .map { berkas ->
                berkas.relativeTo(akar).path.replace(File.separatorChar, '/') to
                    berkas.readText().tanpaKomentarDanTeks()
            }
            .toList()
            .also { require(it.size > 100) { "pemindai cuma menemukan ${it.size} berkas — akar salah?" } }
    }
}
