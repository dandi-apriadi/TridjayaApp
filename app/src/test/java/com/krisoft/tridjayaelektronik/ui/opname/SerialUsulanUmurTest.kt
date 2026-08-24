package com.krisoft.tridjayaelektronik.ui.opname

import com.krisoft.tridjayaelektronik.data.model.SerialRequestDto
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Penanda menggantung di panel "Usulan Pendaftaran SN".
 *
 * Vonisnya milik server (`serials/requests.rs` `vonis_usulan`): `umurAntrianJam`
 * hanya diisi untuk usulan `pending`, dan `mandek` memakai ambang
 * `DELIVERY_STALL_HOURS` yang sama dengan pipeline SPK & form aki. Yang diuji di
 * sini adalah bahwa app MEMBACA jawaban itu apa adanya — tidak menghitung ulang,
 * tidak mengarang saat field-nya absen.
 */
class SerialUsulanUmurTest {

    private val json = Json { ignoreUnknownKeys = true }

    // ── Kabel ────────────────────────────────────────────────────────────────

    @Test
    fun `dua field baru terbaca dari kabel`() {
        // Repo ini NOL `@SerialName`: nama properti Kotlin ADALAH nama di kabel.
        // Salah eja = kotlinx mengisi default tanpa melempar, jadi lencananya
        // mati senyap dan tak ada satu pun error yang menyebutkannya.
        val dto = json.decodeFromString(
            SerialRequestDto.serializer(),
            """{"id":"R1","serialNumber":"SN-1","status":"pending",
                "umurAntrianJam":37,"mandek":true}""",
        )
        assertEquals(37L, dto.umurAntrianJam)
        assertEquals(true, dto.mandek)
    }

    @Test
    fun `server lama tanpa dua field itu tidak melahirkan vonis palsu`() {
        // Server MENGHILANGKAN keduanya saat kosong/false
        // (`skip_serializing_if`), jadi absennya harus terbaca "tak dihitung di
        // sini" — bukan "baru saja masuk" dan bukan "sudah mandek".
        val dto = json.decodeFromString(
            SerialRequestDto.serializer(),
            """{"id":"R1","serialNumber":"SN-1","status":"pending"}""",
        )
        assertNull(dto.umurAntrianJam)
        assertFalse(dto.mandek)
    }

    // ── Kalimat lencana ──────────────────────────────────────────────────────

    @Test
    fun `usulan yang belum melewati ambang cuma disebut menunggu`() {
        assertEquals("Menunggu 5j", labelUmurUsulan("pending", 5L, mandek = false))
    }

    @Test
    fun `usulan yang server vonis mandek disebut menggantung`() {
        assertEquals("Menggantung 37j", labelUmurUsulan("pending", 37L, mandek = true))
    }

    @Test
    fun `umur absen tidak diterjemahkan jadi nol jam`() {
        // "0j" adalah vonis, dan yang dimiliki app di sini cuma ketidaktahuan.
        assertNull(labelUmurUsulan("pending", null, mandek = false))
        assertNull(labelUmurUsulan("pending", null, mandek = true))
    }

    @Test
    fun `usulan yang sudah diputuskan tak pernah berpenanda menunggu`() {
        // Server memang tak mengirim umurnya untuk baris ini; kalaupun terkirim
        // (mis. jalur create/approve yang mengembalikan baris apa adanya),
        // "menunggu 3j" untuk usulan yang sudah disetujui adalah kebohongan.
        assertNull(labelUmurUsulan("approved", 3L, mandek = false))
        assertNull(labelUmurUsulan("rejected", 99L, mandek = true))
    }
}
