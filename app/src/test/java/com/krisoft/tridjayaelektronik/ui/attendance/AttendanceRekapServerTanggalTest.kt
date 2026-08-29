package com.krisoft.tridjayaelektronik.ui.attendance

import com.krisoft.tridjayaelektronik.data.model.AbsensiRecordDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Bug NYATA 2026-08-27: rekap "Hadir" bulan ini tampil jauh lebih rendah dari
 * jumlah baris absensi sungguhan di server (mis. "1 Hadir" padahal 19 hari
 * lengkap) — bukan karena fetch gagal (server mengembalikan 200 lengkap),
 * melainkan karena [currentMonthDays] memakai jam DEVICE (`Calendar.getInstance()`)
 * untuk merentang tanggal "1 s/d hari ini", dan jam/zona device yang salah
 * (kasus nyata: HP di WITA, atau jam yang melenceng) membuat rentang itu tak
 * pernah cocok dengan `tanggal` di record server — setiap hari jatuh ke
 * BELUM_ABSEN walau datanya lengkap. Perbaikannya: pakai `AbsensiTodayDto.tanggal`
 * (SERVER yang bicara "hari ini"), bukan jam device.
 */
class AttendanceRekapServerTanggalTest {

    @Test
    fun `currentMonthDays memakai serverToday, mengabaikan jam device`() {
        val hari = currentMonthDays(serverToday = "2026-08-27")
        assertEquals(27, hari.size)
        assertEquals("2026-08-01", hari.first())
        assertEquals("2026-08-27", hari.last())
    }

    @Test
    fun `serverToday awal bulan menghasilkan satu hari saja`() {
        assertEquals(listOf("2026-03-01"), currentMonthDays(serverToday = "2026-03-01"))
    }

    @Test
    fun `serverToday rusak jatuh balik ke jam device, bukan melempar`() {
        // Tak bisa memastikan NILAINYA tanpa injeksi jam device (lihat
        // HariIniUtcMidnightTest untuk pola itu di fungsi lain) - yang wajib
        // benar di sini cuma: tak melempar, dan tetap balik daftar tak kosong.
        val hari = currentMonthDays(serverToday = "bukan-tanggal")
        assertTrue(hari.isNotEmpty())
    }

    @Test
    fun `buildRekap menghitung HADIR pakai rentang dari serverToday`() {
        // Skenario persis kasus nyata: baris absensi lengkap di tanggal yang
        // SAMA dengan serverToday. Tanpa perbaikan ini (rentang dari jam
        // device yang salah), tanggal "2026-08-27" bisa tak pernah muncul di
        // `days` sama sekali, dan baris ini jatuh ke BELUM_ABSEN walau
        // `checkInAt` terisi.
        val history = listOf(
            AbsensiRecordDto(tanggal = "2026-08-27", checkInAt = "2026-08-27 09:58:58"),
        )
        val rekap = buildRekap(history, offRequests = emptyList(), serverToday = "2026-08-27")
        assertEquals(1, rekap.count(RekapStatus.HADIR))
        // Rentangnya 1-27 Agustus (27 hari) - cuma tanggal 27 yang ada baris
        // absensi, sisanya 26 hari lain memang belum ada datanya.
        assertEquals(26, rekap.count(RekapStatus.BELUM_ABSEN))
        assertEquals(27, rekap.totalHari)
    }

    @Test
    fun `buildRekap 19 hari hadir lengkap tak lagi tenggelam jadi belum absen`() {
        val tanggalHadir = listOf(
            "2026-08-03", "2026-08-04", "2026-08-05", "2026-08-06", "2026-08-07",
            "2026-08-10", "2026-08-11", "2026-08-12", "2026-08-13", "2026-08-17",
            "2026-08-18", "2026-08-19", "2026-08-20", "2026-08-21", "2026-08-23",
            "2026-08-24", "2026-08-25", "2026-08-26", "2026-08-27",
        )
        val history = tanggalHadir.map { AbsensiRecordDto(tanggal = it, checkInAt = "$it 09:00:00") }
        val rekap = buildRekap(history, offRequests = emptyList(), serverToday = "2026-08-27")
        assertEquals(19, rekap.count(RekapStatus.HADIR))
        assertEquals(27, rekap.totalHari)
        assertEquals(27 - 19, rekap.count(RekapStatus.BELUM_ABSEN))
    }
}
