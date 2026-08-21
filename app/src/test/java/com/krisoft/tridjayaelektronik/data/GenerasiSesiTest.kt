package com.krisoft.tridjayaelektronik.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * **Logout harus LENGKET.**
 *
 * Urutan yang diuji di sini persis yang membuatnya tidak lengket sebelum
 * perbaikan: `TokenRefresher.refresh()` (thread OkHttp) membaca refresh token,
 * lalu MENUNGGU server sampai 8 detik; di tengah penantian itu orangnya menekan
 * Logout dan `TokenStore.clear()` mengosongkan sesi; lalu refresh-nya sukses dan
 * `updateSession()` menulis token + profil BARU — ke mirror memori DAN ke
 * DataStore. App dibuka lagi besok paginya: masih login.
 *
 * [TokenStore] tak bisa dihidupkan di unit test JVM (butuh Context + Android
 * Keystore + DataStore), jadi yang diuji adalah [GenerasiSesi] — penjaganya —
 * lewat [SesiPalsu] yang menyalin cara [TokenStore] memakainya: mirror sinkron,
 * penulisan disk yang dijadwalkan belakangan, dan pemeriksaan generasi di titik
 * COMMIT-nya. Bahwa [TokenStore] dan `TokenRefresher` benar-benar memakainya
 * dijaga terpisah oleh `KabelLogoutLengketTest`; dua-duanya diperlukan, karena
 * penjaga yang benar tapi tak terpasang persis sama nilainya dengan tak ada.
 */
class GenerasiSesiTest {

    /**
     * Tiruan [TokenStore] seukur perlunya.
     *
     * `cermin` = `TokenStore.cache` (dibaca thread OkHttp, disetel sinkron).
     * `disk` = DataStore. Penulisan disk TIDAK langsung: ia diantre dulu
     * (`scope.launch`) dan baru mendarat saat [kuras] dipanggil — supaya test
     * bisa memilih urutan commit-nya, termasuk urutan yang menyalip.
     */
    private class SesiPalsu {
        private val generasi = GenerasiSesi()
        private val antrean = ArrayDeque<Pair<Long, () -> String>>()

        @Volatile
        var cermin: String = ""
            private set

        @Volatile
        var disk: String = ""
            private set

        fun tandaSesi(): Long = generasi.sekarang()

        /** Seperti `TokenStore.saveLogin` — penulis tanpa syarat generasi. */
        fun masuk(token: String) = generasi.denganGenerasi { tanda -> terapkan(tanda, token) }

        /** Seperti `TokenStore.updateSession` — DIJAGA generasi. */
        fun updateSession(token: String, tanda: Long): Boolean =
            generasi.jalankanBila(tanda) { berlaku -> terapkan(berlaku, token) }

        /**
         * Seperti `TokenStore.clear`.
         *
         * Yang diantre `{ cermin }`, BUKAN konstanta `""` — persis seperti
         * [terapkan], dan itu yang membuat login-sesudah-logout selamat saat
         * urutan commit-nya menyalip. Lihat
         * `login sesudah logout tak terhapus walau penulisan logout commit belakangan`.
         */
        fun clear() {
            val tanda = generasi.akhiri { baru ->
                cermin = ""
                baru
            }
            antrean.addLast(tanda to { cermin })
        }

        /**
         * Meniru kolektor `dataStore.data` di `TokenStore.init` →
         * `pasangDariDisk`: pemeriksaan [bolehPasangDariDisk] DAN penulisan
         * mirror-nya berada di dalam kunci yang sama dengan kenaikan generasi.
         *
         * [jeda] kait khusus test, dijalankan DI ANTARA pemeriksaan dan
         * penulisan — di dalam kunci itu — supaya urutan yang jadi bug bisa
         * dipaksa tanpa `sleep`.
         */
        fun pasangDariDisk(emisi: String, jeda: () -> Unit = {}) = generasi.terkunci {
            val boleh = bolehPasangDariDisk(
                emisi = PersistedSession(accessToken = emisi),
                cermin = PersistedSession(accessToken = cermin),
                sudahTermuat = true,
            )
            jeda()
            if (!boleh) return@terkunci
            cermin = emisi
        }

        private fun terapkan(tanda: Long, token: String) {
            cermin = token
            // Meniru `simpanDiamDiam(tanda) { cache }`: yang ditulis adalah
            // mirror TERAKHIR saat commit, bukan potret saat dijadwalkan.
            antrean.addLast(tanda to { cermin })
        }

        /**
         * Meniru DataStore: penulisan diserialkan dan generasinya diperiksa
         * SAAT COMMIT. [terbalik] mensimulasikan dua `scope.launch` di
         * `Dispatchers.IO` yang urutan commit-nya berbeda dari urutan `launch`.
         */
        fun kuras(terbalik: Boolean = false) {
            val tugas = antrean.toList().let { if (terbalik) it.reversed() else it }
            antrean.clear()
            tugas.forEach { (tanda, nilai) ->
                if (generasi.masihBerlaku(tanda)) disk = nilai()
            }
        }
    }

    // ── Urutan yang jadi bug ─────────────────────────────────────────────────

    @Test
    fun `refresh yang lahir sebelum clear() tidak menghidupkan sesi`() {
        val sesi = SesiPalsu()
        sesi.masuk("token-lama")
        sesi.kuras()
        assertEquals("token-lama", sesi.disk)

        // 1. refresh MULAI (thread OkHttp): generasi diambil sebelum jaringan.
        val tanda = sesi.tandaSesi()
        // 2. orangnya menekan Logout selagi refresh masih terbang.
        sesi.clear()
        // 3. refresh SELESAI dan mencoba menulis balik.
        val dipasang = sesi.updateSession("token-baru", tanda)
        sesi.kuras()

        assertFalse("penulisan dari generasi sesi yang sudah mati harus DITOLAK", dipasang)
        assertEquals("mirror memori harus tetap kosong sesudah logout", "", sesi.cermin)
        assertEquals(
            "DataStore harus tetap kosong — kalau tidak, cold start berikutnya " +
                "mendapati orangnya MASIH LOGIN padahal ia sudah menekan Logout",
            "",
            sesi.disk,
        )
    }

    /**
     * Kontrol negatif. Tanpa ini, penjaga yang SELALU menolak juga akan membuat
     * test di atas hijau — dan app-nya tak akan pernah bisa merotasi token.
     */
    @Test
    fun `refresh tanpa logout di tengahnya tetap dipasang`() {
        val sesi = SesiPalsu()
        sesi.masuk("token-lama")

        val tanda = sesi.tandaSesi()
        val dipasang = sesi.updateSession("token-baru", tanda)
        sesi.kuras()

        assertTrue("rotasi token normal tak boleh ikut tertolak", dipasang)
        assertEquals("token-baru", sesi.cermin)
        assertEquals("token-baru", sesi.disk)
    }

    /**
     * Login SESUDAH logout memakai generasi yang baru, jadi ia lolos. Kalau
     * penjaga ini memakai sesuatu yang lebih kasar (mis. bendera "pernah
     * di-clear"), orang tak akan pernah bisa login lagi tanpa membunuh app.
     */
    @Test
    fun `login sesudah logout tetap bisa mengisi sesi`() {
        val sesi = SesiPalsu()
        sesi.masuk("token-lama")
        sesi.clear()

        sesi.masuk("token-orang-berikutnya")
        sesi.kuras()

        assertEquals("token-orang-berikutnya", sesi.cermin)
        assertEquals("token-orang-berikutnya", sesi.disk)
    }

    /**
     * Test di atas menguras antrean dalam URUTAN SISIP, dan urutan itulah yang
     * menguntungkan. Yang ini membalikkannya — dua `scope.launch` di
     * `Dispatchers.IO` tak menjamin urutan commit-nya (alasan yang sama dengan
     * `penulisan disk yang basi tetap tertolak walau commit-nya menyalip`).
     *
     * Logout dan login-sesudahnya berjalan di generasi yang SAMA: `saveLogin`
     * sengaja tak dijaga generasi, jadi KEDUA penulisan lolos `masihBerlaku`.
     * Kalau `clear()` menyimpan KONSTANTA kosong, penulisan miliknya yang
     * commit belakangan menimpa sesi yang baru berhasil login — dan rantainya
     * berlanjut: emisi kosong dari disk itu TIDAK ditahan [bolehPasangDariDisk]
     * (penjaga itu sengaja satu arah, ia cuma menahan emisi BERISI), jadi
     * mirror ikut kosong dan orang yang baru login dilempar balik ke layar
     * Login. Karena itu `clear()` menyimpan `cache`, bukan konstanta.
     *
     * HP cabang bersama melakukan logout→login sepanjang hari.
     */
    @Test
    fun `login sesudah logout tak terhapus walau penulisan logout commit belakangan`() {
        val sesi = SesiPalsu()
        sesi.masuk("token-lama")
        sesi.clear()
        sesi.masuk("token-orang-berikutnya")

        sesi.kuras(terbalik = true)

        assertEquals(
            "penulisan clear() yang commit belakangan tak boleh menghapus sesi yang " +
                "baru saja berhasil login",
            "token-orang-berikutnya",
            sesi.disk,
        )

        // Rantai lanjutannya: DataStore memancarkan apa yang tersimpan, dan
        // emisi KOSONG tak pernah ditahan. Kalau disk sempat kosong, mirror
        // ikut kosong dan `sessionState` jatuh ke false.
        sesi.pasangDariDisk(sesi.disk)
        assertEquals(
            "emisi dari disk mengosongkan mirror → orang yang BARU BERHASIL LOGIN " +
                "dilempar balik ke layar Login, dan cold start tak menemukan sesi",
            "token-orang-berikutnya",
            sesi.cermin,
        )
    }

    // ── Titik commit disk, bukan titik penjadwalan ───────────────────────────

    /**
     * Dua penyimpanan berjalan di `Dispatchers.IO`; yang menentukan isi disk
     * adalah urutan COMMIT-nya, dan itu tak dijamin sama dengan urutan
     * `scope.launch`. Karena itu `simpanDiamDiam` memeriksa generasi DI DALAM
     * `dataStore.updateData`, bukan sebelum `launch`. Test ini membalik urutan
     * commit-nya supaya versi yang memeriksa terlalu awal jadi merah.
     */
    @Test
    fun `penulisan disk yang basi tetap tertolak walau commit-nya menyalip`() {
        val sesi = SesiPalsu()
        sesi.masuk("token-lama")
        val tanda = sesi.tandaSesi()
        sesi.clear()
        sesi.updateSession("token-baru", tanda)

        sesi.kuras(terbalik = true)

        assertEquals("", sesi.disk)
    }

    // ── Dua thread: persis pembagian kerja aslinya ───────────────────────────

    /**
     * Versi dua-thread dari test pertama — thread "OkHttp" memegang generasi
     * lamanya melewati seluruh logout yang berjalan di thread "Main". Tanpa
     * `sleep`: dua [CountDownLatch] yang mengunci urutannya, jadi test ini tak
     * bisa jadi rewel (flaky) maupun lambat.
     */
    @Test
    fun `thread refresh yang tertahan jaringan tak bisa membatalkan logout`() {
        val sesi = SesiPalsu()
        sesi.masuk("token-lama")

        val tandaTerambil = CountDownLatch(1)
        val logoutSelesai = CountDownLatch(1)
        val dipasang = AtomicBoolean(true)

        val okhttp = Thread {
            val tanda = sesi.tandaSesi()
            tandaTerambil.countDown()
            // "jaringan": tertahan persis selama logout berjalan.
            logoutSelesai.await()
            dipasang.set(sesi.updateSession("token-baru", tanda))
        }
        okhttp.start()

        assertTrue("thread refresh tak pernah mulai", tandaTerambil.await(5, TimeUnit.SECONDS))
        sesi.clear()
        logoutSelesai.countDown()
        okhttp.join(TimeUnit.SECONDS.toMillis(5))

        assertFalse(okhttp.isAlive)
        assertFalse(dipasang.get())
        assertEquals("", sesi.cermin)
    }

    // ── Emisi disk yang terlambat ────────────────────────────────────────────

    @Test
    fun `emisi disk berisi sesi ditolak saat mirror sudah dikosongkan logout`() {
        val emisiBasi = PersistedSession(accessToken = "token-lama", userName = "Budi")
        assertFalse(
            "kolektor dataStore.data yang memasang emisi ini akan menghidupkan lagi " +
                "sesi yang baru dihapus clear(), berikut sessionState = true",
            bolehPasangDariDisk(emisiBasi, PersistedSession(), sudahTermuat = true),
        )
    }

    @Test
    fun `emisi disk berisi sesi DITERIMA saat mirror belum termuat`() {
        val sesiTersimpan = PersistedSession(accessToken = "token-lama", userName = "Budi")
        assertTrue(
            "sebelum load() selesai, emisi berisi sesi justru yang ditunggu — ia sesi " +
                "tersimpan yang baru dibaca dari disk. Memblokirnya membuat SEMUA orang " +
                "harus login ulang tiap app dibuka.",
            bolehPasangDariDisk(sesiTersimpan, PersistedSession(), sudahTermuat = false),
        )
    }

    /**
     * [bolehPasangDariDisk] adalah fungsi murni; yang menentukan apakah ia
     * benar-benar menutup jendelanya adalah TEMPAT ia dipanggil. Pemeriksaannya
     * MEMBACA mirror dan `cache = s` yang menyusul MENULISNYA — dua langkah.
     * Kalau keduanya berdiri di luar monitor [GenerasiSesi], `clear()` bisa
     * berjalan persis di antaranya: emisi lolos pemeriksaan selagi mirror masih
     * berisi, lalu logout selesai, lalu emisinya mendarat. Sesi hidup lagi.
     *
     * Tanpa `sleep`. Kolektor ditahan DI DALAM daerah kritisnya oleh kait
     * [SesiPalsu.pasangDariDisk], lalu test menunggu thread logout mencapai
     * keadaan STABIL — `BLOCKED` (implementasi benar: ia terhalang kunci) atau
     * `TERMINATED` (implementasi bocor: ia sudah selesai duluan). Dua-duanya
     * keadaan yang tak berubah lagi, jadi tak ada tebakan waktu di sini.
     */
    @Test
    fun `emisi disk tak bisa menyelinap di antara pemeriksaan dan pemasangannya`() {
        val sesi = SesiPalsu()
        sesi.masuk("token-lama")

        val didalam = CountDownLatch(1)
        val bolehLanjut = CountDownLatch(1)

        val kolektor = Thread({
            sesi.pasangDariDisk("token-emisi-basi") {
                didalam.countDown()
                bolehLanjut.await()
            }
        }, "kolektor-disk")
        val logout = Thread({ sesi.clear() }, "logout")

        kolektor.start()
        assertTrue("kolektor tak pernah masuk daerah kritisnya", didalam.await(5, TimeUnit.SECONDS))

        logout.start()
        tungguStabil(logout)
        bolehLanjut.countDown()

        kolektor.join(TimeUnit.SECONDS.toMillis(5))
        logout.join(TimeUnit.SECONDS.toMillis(5))
        assertFalse("thread kolektor menggantung", kolektor.isAlive)
        assertFalse("thread logout menggantung", logout.isAlive)

        assertEquals(
            "emisi disk yang lolos pemeriksaan SEBELUM logout tak boleh mendarat " +
                "sesudahnya. Pemeriksaan dan penulisannya harus berada di dalam kunci " +
                "yang sama dengan kenaikan generasi (GenerasiSesi.terkunci), persis " +
                "seperti jalur penulisan memakai jalankanBila.",
            "",
            sesi.cermin,
        )
    }

    /** Tunggu [t] mencapai keadaan yang tak berubah lagi: terhalang kunci, atau selesai. */
    private fun tungguStabil(t: Thread) {
        val batas = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (t.state != Thread.State.BLOCKED && t.state != Thread.State.TERMINATED) {
            if (System.nanoTime() > batas) {
                fail("thread `${t.name}` tak pernah stabil dalam 5 detik (keadaan: ${t.state})")
            }
            Thread.yield()
        }
    }

    @Test
    fun `emisi disk biasa tetap lewat`() {
        val cermin = PersistedSession(accessToken = "token-lama")
        val baru = PersistedSession(accessToken = "token-baru")
        assertTrue(
            "rotasi token normal harus tetap sampai ke mirror",
            bolehPasangDariDisk(baru, cermin, sudahTermuat = true),
        )
        assertTrue(
            "emisi KOSONG (hasil clear() sendiri) tak pernah ditahan",
            bolehPasangDariDisk(PersistedSession(), cermin, sudahTermuat = true),
        )
    }
}
