package com.krisoft.tridjayaelektronik.ui.acinstall

import com.krisoft.tridjayaelektronik.data.model.AcInstallPetugasDto
import com.krisoft.tridjayaelektronik.data.model.AcInstallSpkDto
import com.krisoft.tridjayaelektronik.data.model.AcInstallStatus
import com.krisoft.tridjayaelektronik.data.model.AcInstallTaskDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Aturan layar "Tugas Pemasangan AC".
 *
 * Yang dijaga di sini KESEPAKATAN LINTAS REPO, bukan tampilan: cocok-jabatan
 * menyalin `FIND_IN_SET('teknisi', REPLACE(divisi,' ',''))` (migrasi 256),
 * kewajiban alasan menyalin `tolak_handler`, dan "belum menjawab = tak ada
 * baris" menyalin keputusan `ac_install_task_responses` yang sengaja tidak
 * membuat baris menunggu di muka. Tak ada pemeriksa kompiler untuk satu pun
 * dari itu.
 */
class AcInstallPlanTest {

    private fun petugas(id: String, status: String? = null) =
        AcInstallPetugasDto(userId = id, nama = "Petugas $id", status = status)

    private fun tugas(
        id: String = "t1",
        status: String = AcInstallStatus.DIJADWALKAN,
        petugas: List<AcInstallPetugasDto> = emptyList(),
        alamat: String? = null,
        spkAlamat: String? = null,
        jadwalTanggal: String? = null,
        jadwalJam: String? = null,
    ) = AcInstallTaskDto(
        id = id,
        status = status,
        petugas = petugas,
        alamatPemasangan = alamat,
        jadwalTanggal = jadwalTanggal,
        jadwalJam = jadwalJam,
        spk = AcInstallSpkDto(customerAddress = spkAlamat),
    )

    // ── jabatan: cerminan FIND_IN_SET, bukan `contains` ──────────────────────

    @Test
    fun `divisi teknisi tunggal cocok`() {
        assertTrue(punyaJabatanPetugasPemasangan("teknisi"))
    }

    /** Satu akun bisa memegang beberapa jabatan; kolomnya CSV. */
    @Test
    fun `teknisi di tengah CSV cocok`() {
        assertTrue(punyaJabatanPetugasPemasangan("sales,teknisi,driver"))
    }

    /** Server membuang spasi di SELURUH string sebelum memisah (`REPLACE`). */
    @Test
    fun `spasi setelah koma tidak menggagalkan pencocokan`() {
        assertTrue(punyaJabatanPetugasPemasangan("sales, teknisi"))
        assertTrue(punyaJabatanPetugasPemasangan(" teknisi "))
    }

    /**
     * INI alasan pencocokannya per-elemen, bukan `contains`: jabatan lain yang
     * memuat "teknisi" sebagai substring adalah orang yang picker verifikator
     * sendiri TIDAK tawarkan. Meloloskannya di app = kartu tugas untuk orang
     * yang tak akan pernah punya tugas.
     */
    @Test
    fun `jabatan lain yang memuat teknisi sebagai substring TIDAK cocok`() {
        assertFalse(punyaJabatanPetugasPemasangan("asisten-teknisi-magang"))
        assertFalse(punyaJabatanPetugasPemasangan("teknisi-ac-senior"))
    }

    @Test
    fun `besar-kecil huruf diabaikan - collation kolom server juga _ci`() {
        assertTrue(punyaJabatanPetugasPemasangan("TEKNISI"))
        assertTrue(punyaJabatanPetugasPemasangan("Sales,Teknisi"))
    }

    @Test
    fun `kosong dan null tidak cocok - jangan menebak`() {
        assertFalse(punyaJabatanPetugasPemasangan(null))
        assertFalse(punyaJabatanPetugasPemasangan(""))
        assertFalse(punyaJabatanPetugasPemasangan("   "))
        assertFalse(punyaJabatanPetugasPemasangan(",,"))
    }

    /**
     * Role BUKAN jabatan. `teknisi` ber-`akses_slugs = '[]'` (migrasi 195) jadi
     * ia tak melipat jadi role apa pun, dan kedua teknisi produksi ber-role
     * `karyawan` — gate berbasis role di sini menyaring NOL orang.
     */
    @Test
    fun `role karyawan saja tidak membuat seseorang petugas pemasangan`() {
        assertFalse(punyaJabatanPetugasPemasangan("karyawan"))
    }

    // ── jawaban saya ─────────────────────────────────────────────────────────

    @Test
    fun `jawaban saya dicari lewat userId, bukan nama`() {
        val t = tugas(petugas = listOf(petugas("u1", "diterima"), petugas("u2")))
        assertEquals("diterima", jawabanSaya(t, "u1")?.status)
        assertNull(jawabanSaya(t, "u2")?.status)
    }

    @Test
    fun `orang yang tak ditugaskan tak punya baris`() {
        val t = tugas(petugas = listOf(petugas("u1")))
        assertNull(jawabanSaya(t, "u9"))
        assertFalse(sayaDitugaskan(t, "u9"))
        assertTrue(sayaDitugaskan(t, "u1"))
    }

    /** userId kosong (profil belum termuat) tak boleh dianggap cocok siapa pun. */
    @Test
    fun `userId kosong tidak mencocokkan apa pun`() {
        val t = tugas(petugas = listOf(petugas("")))
        assertNull(jawabanSaya(t, null))
        assertNull(jawabanSaya(t, ""))
    }

    // ── lencana ──────────────────────────────────────────────────────────────

    /**
     * Lencana menghitung yang BELUM dijawab, bukan seluruh daftar. Tugas yang
     * sudah diterima tetap harus dikerjakan tapi tak menuntut tindakan sekarang;
     * lencana yang tak pernah turun ke nol berhenti dibaca.
     */
    @Test
    fun `lencana hanya menghitung yang belum saya jawab`() {
        val daftar = listOf(
            tugas("a", petugas = listOf(petugas("u1"))),
            tugas("b", petugas = listOf(petugas("u1", "diterima"))),
            tugas("c", petugas = listOf(petugas("u1", "ditolak"))),
            tugas("d", petugas = listOf(petugas("u1"))),
        )
        assertEquals(2, butuhJawabanSaya(daftar, "u1"))
    }

    /** `status` blank dari server lama diperlakukan sama dengan null. */
    @Test
    fun `status kosong dihitung belum menjawab`() {
        val daftar = listOf(tugas("a", petugas = listOf(petugas("u1", "  "))))
        assertEquals(1, butuhJawabanSaya(daftar, "u1"))
    }

    // ── gerbang aksi ─────────────────────────────────────────────────────────

    @Test
    fun `tolak wajib beralasan`() {
        assertFalse(bolehTolak(""))
        assertFalse(bolehTolak("   "))
        assertTrue(bolehTolak("hujan"))
    }

    @Test
    fun `tugas selesai dan dibatalkan tak boleh dijawab lagi`() {
        assertFalse(bolehDijawab(tugas(status = AcInstallStatus.SELESAI)))
        assertFalse(bolehDijawab(tugas(status = AcInstallStatus.DIBATALKAN)))
        assertTrue(bolehDijawab(tugas(status = AcInstallStatus.DIJADWALKAN)))
    }

    // ── label & fallback tampilan ────────────────────────────────────────────

    @Test
    fun `label jadwal menyalin apa adanya, tanpa parse tanggal`() {
        assertEquals("2026-08-25 · 09:00", labelJadwal(tugas(jadwalTanggal = "2026-08-25", jadwalJam = "09:00")))
        assertEquals("2026-08-25", labelJadwal(tugas(jadwalTanggal = "2026-08-25")))
        assertNull(labelJadwal(tugas()))
    }

    @Test
    fun `alamat jatuh ke alamat konsumen SPK saat alamat pemasangan kosong`() {
        assertEquals("Jl. Mawar 1", alamatEfektif(tugas(alamat = "Jl. Mawar 1", spkAlamat = "Jl. Melati 2")))
        assertEquals("Jl. Melati 2", alamatEfektif(tugas(alamat = "   ", spkAlamat = "Jl. Melati 2")))
        assertNull(alamatEfektif(tugas()))
    }

    @Test
    fun `label respon menerjemahkan slug server, belum menjawab jadi null`() {
        assertEquals("Diterima", labelRespon("diterima"))
        assertEquals("Ditolak", labelRespon("ditolak"))
        assertNull(labelRespon(null))
        assertNull(labelRespon("entah"))
    }
}
