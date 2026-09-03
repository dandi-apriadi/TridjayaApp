package com.krisoft.tridjayaelektronik.util

import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * Setiap subdirektori `cacheDir`/`filesDir` yang disebut di kode WAJIB punya
 * entri padanannya di `res/xml/file_paths.xml`.
 *
 * KENAPA TES INI ADA — jebakan yang sama sudah menggigit TIGA KALI:
 * `event` (foto KTP prospek), `home_service` (foto komplain), dan yang terakhir
 * `serial` (foto usulan pendaftaran SN, 2026-08-14). Ketiganya kelas kegagalan
 * yang identik: `FileProvider.getUriForFile` melempar
 * `IllegalArgumentException: Failed to find configured root that contains …`
 * untuk direktori yang tak dideklarasikan.
 *
 * Yang membuatnya mahal: **kompilasi tetap hijau, lint tetap hijau, dan tak ada
 * satu pun test lama yang menyentuhnya** — kegagalannya baru muncul di HP
 * petugas di lapangan. Pada kasus `serial` bahkan lebih buruk daripada dua
 * pendahulunya: panggilan `getUriForFile` di `OpnameDetailScreen` ada DI DALAM
 * `remember`, jadi ia melempar saat KOMPOSISI, bukan saat tombol kamera
 * ditekan — app tutup begitu panelnya dirender. Akibatnya tabel
 * `serial_registration_requests` di produksi NOL BARIS sejak fitur usulan SN
 * mendarat 2026-07-29: tak seorang pun pernah berhasil mengusulkan, dan tak ada
 * yang tahu karena tak ada error yang tercatat di server.
 *
 * Aturannya sengaja LEBIH KETAT dari "yang dipakai FileProvider saja": tiap
 * subdirektori cache/files harus dideklarasikan, titik. Mendeklarasikan satu
 * baris XML tambahan ongkosnya nol; melewatkannya ongkosnya fitur yang mati
 * senyap di lapangan. Korelasi per-berkas juga tak bisa diandalkan — direktori
 * bisa ditulis di satu berkas lalu dibagikan lewat `getUriForFile` di
 * berkas lain.
 */
class FileProviderPathsTest {

    private val sumberRoot: File =
        sequenceOf(File("src/main/java"), File("app/src/main/java"))
            .firstOrNull { it.isDirectory }
            ?: error("pohon sumber tak ketemu — cwd=${File(".").absolutePath}")

    private val filePathsXml: File =
        sequenceOf(
            File("src/main/res/xml/file_paths.xml"),
            File("app/src/main/res/xml/file_paths.xml"),
        ).firstOrNull { it.isFile }
            ?: error("file_paths.xml tak ketemu — cwd=${File(".").absolutePath}")

    /** `<cache-path … path="x/" />` → "x", dipisah per jenis root. */
    private fun deklarasi(jenis: String): Set<String> =
        Regex("""<$jenis\b[^>]*\bpath="([^"]+)"""")
            .findAll(filePathsXml.readText())
            .map { it.groupValues[1].trim('/') }
            .toSet()

    /** `File(context.cacheDir, "x/y.jpg")` → "x". */
    private fun dipakai(properti: String): Map<String, String> {
        val hasil = mutableMapOf<String, String>()
        sumberRoot.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { berkas ->
            Regex("""\b$properti\s*,\s*"([^"]+)"""").findAll(berkas.readText()).forEach { m ->
                val dir = m.groupValues[1].substringBefore('/').trim()
                if (dir.isNotEmpty() && !dir.contains('$')) hasil.putIfAbsent(dir, berkas.name)
            }
        }
        return hasil
    }

    @Test
    fun `setiap subdirektori cacheDir punya entri cache-path`() {
        val terdaftar = deklarasi("cache-path")
        val kurang = dipakai("cacheDir").filterKeys { it !in terdaftar }
        if (kurang.isNotEmpty()) {
            fail(
                buildString {
                    appendLine("Subdirektori cacheDir tanpa <cache-path> di file_paths.xml.")
                    appendLine("Di lapangan ini = IllegalArgumentException saat getUriForFile,")
                    appendLine("dan pada layar Compose bisa berarti app tutup saat komposisi.")
                    kurang.forEach { (dir, berkas) ->
                        appendLine("""  - "$dir/"  (dipakai $berkas)""")
                    }
                    appendLine("Perbaikan: tambahkan <cache-path name=\"…\" path=\"<dir>/\" />")
                }
            )
        }
    }

    @Test
    fun `setiap subdirektori filesDir punya entri files-path`() {
        val terdaftar = deklarasi("files-path")
        val kurang = dipakai("filesDir").filterKeys { it !in terdaftar }
        assertTrue(
            "Subdirektori filesDir tanpa <files-path>: ${kurang.keys} — lihat file_paths.xml",
            kurang.isEmpty(),
        )
    }

    @Test
    fun `direktori usulan SN terdaftar — regresi force close 2026-08-14`() {
        // Tes eksplisit di samping yang generik: kalau suatu hari aturan generik
        // dilonggarkan, kasus yang benar-benar pernah memakan korban tetap dijaga.
        assertTrue(
            "cache-path 'serial/' hilang — panel usulan SN akan menutup app lagi",
            "serial" in deklarasi("cache-path"),
        )
    }

    @Test
    fun `direktori media-compress terdaftar — cegah regresi FileProvider video`() {
        // Sama pola dengan tes `serial` di atas: `VideoTranscoder` menulis keluaran
        // transcode ke `cacheDir/media-compress/` (`util/VideoTranscoder.kt`,
        // dipanggil dari `AktivitasViewModel.kirimVideo`) — kalau entrinya hilang,
        // pemakaian FileProvider berikutnya atas direktori ini akan menutup app
        // tanpa gejala di kompilasi/lint, kelas kegagalan yang sama seperti `serial`.
        assertTrue(
            "cache-path 'media-compress/' hilang — regresi FileProvider VideoTranscoder",
            "media-compress" in deklarasi("cache-path"),
        )
    }
}
