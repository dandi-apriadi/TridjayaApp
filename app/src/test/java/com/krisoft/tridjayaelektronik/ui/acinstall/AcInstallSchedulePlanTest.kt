package com.krisoft.tridjayaelektronik.ui.acinstall

import com.krisoft.tridjayaelektronik.data.model.AcInstallFotoDto
import com.krisoft.tridjayaelektronik.data.model.AcInstallStatus
import com.krisoft.tridjayaelektronik.data.model.AcInstallTaskDto
import com.krisoft.tridjayaelektronik.data.model.AcInstallTimDto
import com.krisoft.tridjayaelektronik.data.model.AcInstallTimMasterDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cerminan `pemasangan_ac::transisi::*`. Kalau salah satu test di sini gagal
 * setelah server berubah, **server yang benar** — perbaiki cerminannya, jangan
 * melonggarkan aturannya di sana.
 */
class AcInstallSchedulePlanTest {

    private fun task(
        status: String = AcInstallStatus.DIAJUKAN,
        foto: Int = 0,
        tim: List<String> = emptyList(),
    ) = AcInstallTaskDto(
        id = "p1",
        status = status,
        foto = List(foto) { AcInstallFotoDto(id = "f$it", url = "/uploads/delivery/$it.jpg") },
        tim = tim.map { AcInstallTimDto(teamId = it, nama = "Tim $it") },
    )

    @Test
    fun `menjadwalkan ulang yang sudah dijadwalkan diizinkan`() {
        // Jadwal geser itu hal biasa di lapangan — yang ditolak hanya yang
        // sudah DITUTUP.
        assertTrue(AcInstallSchedulePlan.bolehJadwalkan(AcInstallStatus.DIAJUKAN))
        assertTrue(AcInstallSchedulePlan.bolehJadwalkan(AcInstallStatus.DIJADWALKAN))
        assertFalse(AcInstallSchedulePlan.bolehJadwalkan(AcInstallStatus.SELESAI))
        assertFalse(AcInstallSchedulePlan.bolehJadwalkan(AcInstallStatus.DIBATALKAN))
    }

    @Test
    fun `menutup menuntut status dijadwalkan DAN minimal satu foto`() {
        // Syarat foto adalah yang paling mudah terlewat saat mencerminkan
        // `boleh_selesai` — ia tak terbaca dari nama fungsinya, dan tanpa
        // cerminan di klien tombolnya tampak aktif lalu dijawab 400.
        assertFalse(
            "belum dijadwalkan",
            AcInstallSchedulePlan.bolehSelesai(task(AcInstallStatus.DIAJUKAN, foto = 3)),
        )
        assertFalse(
            "dijadwalkan tapi belum ada foto",
            AcInstallSchedulePlan.bolehSelesai(task(AcInstallStatus.DIJADWALKAN, foto = 0)),
        )
        assertTrue(
            AcInstallSchedulePlan.bolehSelesai(task(AcInstallStatus.DIJADWALKAN, foto = 1)),
        )
        assertFalse(
            "sudah ditutup",
            AcInstallSchedulePlan.bolehSelesai(task(AcInstallStatus.SELESAI, foto = 2)),
        )
    }

    @Test
    fun `alasan tombol mati selalu menyebutkan sebab yang benar`() {
        // Tombol yang hilang tanpa keterangan terbaca sebagai app rusak.
        assertEquals(
            "Menunggu foto bukti dari petugas",
            AcInstallSchedulePlan.alasanTakBisaSelesai(task(AcInstallStatus.DIJADWALKAN, foto = 0)),
        )
        assertEquals(
            "Jadwalkan dulu sebelum bisa ditutup",
            AcInstallSchedulePlan.alasanTakBisaSelesai(task(AcInstallStatus.DIAJUKAN, foto = 1)),
        )
        assertNotNull(AcInstallSchedulePlan.alasanTakBisaSelesai(task(AcInstallStatus.SELESAI)))
        // Yang MEMANG boleh ditutup tak punya alasan — kalau ini pernah
        // mengembalikan string, layar akan mencetak peringatan di bawah tombol
        // yang justru aktif.
        assertNull(
            AcInstallSchedulePlan.alasanTakBisaSelesai(task(AcInstallStatus.DIJADWALKAN, foto = 1)),
        )
    }

    @Test
    fun `membatalkan wajib beralasan`() {
        assertTrue(AcInstallSchedulePlan.bolehBatal(AcInstallStatus.DIJADWALKAN))
        assertFalse(AcInstallSchedulePlan.bolehBatal(AcInstallStatus.DIBATALKAN))
        assertFalse(AcInstallSchedulePlan.bolehSimpanBatal(""))
        assertFalse("spasi saja bukan alasan", AcInstallSchedulePlan.bolehSimpanBatal("   "))
        assertTrue(AcInstallSchedulePlan.bolehSimpanBatal("konsumen menunda"))
    }

    @Test
    fun `tanggal harus YYYY-MM-DD dengan rentang angka yang masuk akal`() {
        assertTrue(AcInstallSchedulePlan.tanggalSah("2026-08-25"))
        assertTrue("spasi di tepi dipangkas", AcInstallSchedulePlan.tanggalSah(" 2026-08-25 "))
        assertFalse(AcInstallSchedulePlan.tanggalSah(""))
        assertFalse(AcInstallSchedulePlan.tanggalSah("25-08-2026"))
        assertFalse(AcInstallSchedulePlan.tanggalSah("2026-8-5"))
        // Pola digit saja meloloskan bulan 13 / tanggal 32 — itu sebabnya
        // rentangnya ikut diperiksa.
        assertFalse(AcInstallSchedulePlan.tanggalSah("2026-13-01"))
        assertFalse(AcInstallSchedulePlan.tanggalSah("2026-00-10"))
        assertFalse(AcInstallSchedulePlan.tanggalSah("2026-08-32"))
        assertFalse(AcInstallSchedulePlan.tanggalSah("2026-08-ab"))
    }

    @Test
    fun `jam opsional tapi harus HH MM kalau diisi`() {
        assertTrue("kosong = tanpa jam, dan itu sah", AcInstallSchedulePlan.jamSah(""))
        assertTrue(AcInstallSchedulePlan.jamSah("09:30"))
        assertTrue(AcInstallSchedulePlan.jamSah("23:59"))
        assertFalse(AcInstallSchedulePlan.jamSah("24:00"))
        assertFalse(AcInstallSchedulePlan.jamSah("09:60"))
        assertFalse(AcInstallSchedulePlan.jamSah("9:30"))
        // Server menerima `HH:MM:SS`, form ini tidak — layar memangkas detik
        // saat menyemai dari server, dan test ini mengunci alasan pemangkasan itu.
        assertFalse(AcInstallSchedulePlan.jamSah("09:30:00"))
    }

    @Test
    fun `tim terpilih awal disemai dari penugasan yang sudah ada`() {
        // WAJIB. `teamIds` MENGGANTI seluruh daftar tim di server, jadi form yang
        // mulai dari kosong lalu dikirim apa adanya akan MENCABUT penugasan yang
        // sudah ada — tanpa error, dan terbaca sebagai "penugasannya hilang
        // sendiri".
        assertEquals(
            setOf("t1", "t2"),
            AcInstallSchedulePlan.timTerpilihAwal(task(tim = listOf("t1", "t2"))),
        )
        assertEquals(emptySet<String>(), AcInstallSchedulePlan.timTerpilihAwal(task()))
    }

    @Test
    fun `hanya tim aktif yang ditawarkan`() {
        val semua = listOf(
            AcInstallTimMasterDto(id = "t1", nama = "Tim A", aktif = true),
            AcInstallTimMasterDto(id = "t2", nama = "Tim B", aktif = false),
        )
        assertEquals(listOf("t1"), AcInstallSchedulePlan.timBisaDipilih(semua).map { it.id })
    }

    @Test
    fun `urutan tab menaruh yang menunggu keputusan lebih dulu`() {
        assertEquals(AcInstallStatus.DIAJUKAN, AcInstallSchedulePlan.URUTAN_STATUS.first())
        assertEquals(4, AcInstallSchedulePlan.URUTAN_STATUS.size)
        // Semua status punya label manusia; status tanpa label akan tampil
        // sebagai slug mentah di chip.
        AcInstallSchedulePlan.URUTAN_STATUS.forEach {
            assertTrue(it, AcInstallSchedulePlan.labelStatus(it) != it)
        }
    }
}
