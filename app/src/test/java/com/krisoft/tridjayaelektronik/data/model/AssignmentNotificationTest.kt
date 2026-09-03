package com.krisoft.tridjayaelektronik.data.model

import com.krisoft.tridjayaelektronik.data.remote.NetworkModule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Status pengiriman WA PENUGASAN prospek — `assignmentNotification` pada respons
 * `POST /api/prospek-harian`.
 *
 * **Kelas bug yang dijaga: kegagalan yang terlihat seperti keberhasilan.**
 * `prospek.rs` memancarkan field ini sejak lama dan kodenya sendiri berkomentar
 * bahwa app Android tak pernah membacanya, sehingga "WA gagal kirim" identik
 * dengan "berhasil" di layar. Akibatnya lead yang penerimanya tak pernah tahu
 * duduk diam sampai mati — nol error, nol jejak di app.
 *
 * Dekode diuji lewat instans converter SUNGGUHAN ([NetworkModule.json]) karena
 * `ignoreUnknownKeys = true` membuat nama field yang meleset gagal SENYAP.
 */
class AssignmentNotificationTest {

    private val json = NetworkModule.json

    // ── Aturan "perlu diberitahukan" ─────────────────────────────────────────

    @Test
    fun `sent dan skipped_self bukan kegagalan`() {
        assertFalse(AssignmentNotificationDto(status = "sent").perluDiberitahukan)
        // Penugasan ke DIRI SENDIRI memang sengaja tak dikirimi WA
        // (`AssignmentNotification::skipped_self`) — memperingatkannya akan
        // menuduh kegagalan atas keputusan yang disengaja server.
        assertFalse(AssignmentNotificationDto(status = "skipped_self").perluDiberitahukan)
    }

    @Test
    fun `send_failed perlu diberitahukan`() {
        assertTrue(AssignmentNotificationDto(status = "send_failed").perluDiberitahukan)
    }

    /**
     * Fail-CLOSED atas status yang belum dikenal. `AssignmentNotification::failed`
     * dipanggil dari LIMA tempat berbeda di `assignment.rs` dengan status yang
     * masing-masing beda, dan daftar itu bisa bertambah tanpa APK ikut naik.
     * Satu peringatan berlebih ongkosnya sebaris teks; kegagalan yang lolos
     * ongkosnya lead mati tanpa ada yang tahu.
     */
    @Test
    fun `status tak dikenal dihitung gagal`() {
        assertTrue(AssignmentNotificationDto(status = "penerima_tak_ditemukan").perluDiberitahukan)
        assertTrue(AssignmentNotificationDto(status = "nomor_wa_kosong").perluDiberitahukan)
        assertTrue(AssignmentNotificationDto(status = "status_yang_belum_ada").perluDiberitahukan)
        assertTrue("status kosong pun bukan bukti berhasil", AssignmentNotificationDto().perluDiberitahukan)
    }

    // ── Kontrak kabel ────────────────────────────────────────────────────────

    @Test
    fun `respons sukses membawa notifikasi gagal kirim`() {
        val d = json.decodeFromString<CreateProspekData>(
            """{"id":881,"aktivitasSync":{"synced":true},
                "assignmentNotification":{"status":"send_failed",
                  "message":"Lead tersimpan, tetapi WhatsApp gagal dikirim: timeout"}}"""
        )
        assertEquals(881L, d.id)
        val n = d.assignmentNotification!!
        assertEquals("send_failed", n.status)
        assertTrue(n.message.contains("WhatsApp gagal dikirim"))
        assertTrue(n.perluDiberitahukan)
        assertNull("`to` absen saat gagal (skip_serializing_if)", n.to)
    }

    @Test
    fun `respons berhasil membawa nomor tujuan`() {
        val n = json.decodeFromString<CreateProspekData>(
            """{"id":9,"assignmentNotification":{"status":"sent",
                  "message":"WhatsApp penugasan berhasil dikirim ke Budi.","to":"628123"}}"""
        ).assignmentNotification!!
        assertEquals("sent", n.status)
        assertEquals("628123", n.to)
        assertFalse(n.perluDiberitahukan)
    }

    /**
     * Server LAMA (atau jalur yang memang tak mengirim field ini) tak boleh
     * membuat dekode gagal — APK baru bisa jalan di atas server lama. `null`
     * berarti "tak ada kabarnya", dan repository memperlakukannya sebagai
     * TIDAK ADA peringatan, bukan sebagai kegagalan.
     */
    @Test
    fun `respons tanpa assignmentNotification tetap terdekode`() {
        val d = json.decodeFromString<CreateProspekData>("""{"id":5}""")
        assertEquals(5L, d.id)
        assertNull(d.assignmentNotification)
    }
}
