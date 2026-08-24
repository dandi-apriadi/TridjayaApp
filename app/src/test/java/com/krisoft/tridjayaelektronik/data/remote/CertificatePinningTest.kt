package com.krisoft.tridjayaelektronik.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Penjaga certificate pinning (audit keamanan 2026-08, temuan S-1) dan redaksi
 * header Authorization di log debug (S-2).
 *
 * KENAPA MEMBACA SUMBER, BUKAN MEMANGGIL FUNGSINYA. `certificatePinner()` dan
 * builder OkHttp-nya `private`, dan menaikkannya jadi `internal` hanya demi
 * test berarti mengubah permukaan kode karena alat ujinya — sementara yang
 * ingin dijaga di sini justru hal-hal yang TIDAK terlihat dari perilaku unit
 * test: bahwa pin-nya ADA, jumlahnya TIGA (leaf + intermediate + root), dan
 * bahwa `redactHeader` benar-benar dipasang pada interceptor logging.
 *
 * Pinning yang salah TIDAK menghasilkan bug halus: app gagal konek total di
 * lapangan dan satu-satunya obatnya rilis APK baru. Karena itu penjaga ini
 * sengaja kasar dan berisik — ia berbunyi begitu daftar pin disunting, supaya
 * penyuntingnya membaca prosedur regenerasi di NetworkModule lebih dulu.
 */
class CertificatePinningTest {

    private val sumber: String by lazy {
        val jalur = listOf(
            "app/src/main/java/com/krisoft/tridjayaelektronik/data/remote/NetworkModule.kt",
            "src/main/java/com/krisoft/tridjayaelektronik/data/remote/NetworkModule.kt",
        ).map { File(it) }.firstOrNull { it.exists() }
        requireNotNull(jalur) { "NetworkModule.kt tak ditemukan dari ${File(".").absolutePath}" }
            .readText()
            .replace("\r\n", "\n")
    }

    @Test
    fun pinning_terpasang_pada_client_dasar() {
        assertTrue(
            "OkHttpClient dasar harus memanggil .certificatePinner(...)",
            sumber.contains(".certificatePinner(certificatePinner())"),
        )
        assertTrue(
            "host produksi harus tridjaya.com",
            sumber.contains("private const val HOST_PRODUKSI = \"tridjaya.com\""),
        )
    }

    /**
     * TIGA pin, bukan satu. Satu pin leaf saja memutus app tiap rotasi
     * sertifikat rutin (~90 hari) — kegagalan yang datangnya terjadwal.
     */
    @Test
    fun ada_tiga_pin_leaf_intermediate_dan_root() {
        val pin = Regex("""\.add\(HOST_PRODUKSI, "sha256/([A-Za-z0-9+/=]+)"\)""")
            .findAll(sumber)
            .map { it.groupValues[1] }
            .toList()
        assertEquals("jumlah pin berubah — baca prosedur regenerasi di NetworkModule", 3, pin.size)
        assertEquals("pin tidak boleh duplikat", pin.size, pin.toSet().size)
        // Nilai yang diverifikasi terhadap rantai live 2026-08-24. Kalau salah
        // satunya berubah, test ini berbunyi SEBELUM APK dirilis.
        assertTrue("pin leaf tridjaya.com hilang", pin.contains("rzN988lCk9HeBwkB5NZ6LHlc/UNmGjukswQoZo8xW6I="))
        assertTrue("pin intermediate GTS WE1 hilang", pin.contains("kIdp6NNEd8wsugYyyIYFsi1ylMCED3hZbSR8ZFsa/A4="))
        assertTrue("pin root GTS Root R4 hilang", pin.contains("mEflZT5enoR1FuXLgYYGqnVEoZvmf9c2bVBpiOjYQ0c="))
    }

    @Test
    fun header_authorization_diredaksi_di_log() {
        assertTrue(
            "HttpLoggingInterceptor harus meredaksi header Authorization",
            sumber.contains("""logging.redactHeader("Authorization")"""),
        )
    }
}
