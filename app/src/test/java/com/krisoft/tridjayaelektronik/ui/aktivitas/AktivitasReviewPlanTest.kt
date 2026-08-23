package com.krisoft.tridjayaelektronik.ui.aktivitas

import com.krisoft.tridjayaelektronik.data.model.AktivitasItemDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Bagian murni layar Nilai Aktivitas (PIC raport). */
class AktivitasReviewPlanTest {

    private val base = "https://tridjaya.com/"

    // ── Gate putusan ─────────────────────────────────────────────────────────

    @Test
    fun `tolak wajib beralasan`() {
        assertFalse(bolehSimpanReview("rejected", null).ok)
        assertFalse(bolehSimpanReview("rejected", "   ").ok)
        assertTrue(bolehSimpanReview("rejected", "Foto tidak jelas").ok)
    }

    @Test
    fun `setuju tak perlu alasan`() {
        assertTrue(bolehSimpanReview("approved", null).ok)
    }

    @Test
    fun `status di luar dua itu ditolak di klien`() {
        // Server hanya mengenal pending/approved/rejected, dan layar ini tak
        // pernah mengirim `pending` — salah ketik harus mati di sini, bukan 422.
        assertFalse(bolehSimpanReview("approve", "x").ok)
        assertFalse(bolehSimpanReview("", null).ok)
    }

    // ── Skor: cerminan aturan server ─────────────────────────────────────────

    /**
     * Nilai review BINER sejak 2026-08-15 (keputusan user): setuju 100, tolak 0,
     * tanpa nilai antara. Test lama menguji `skorReview("approved", 85) == 85` —
     * itu justru perilaku yang DIBUANG, jadi ia diganti, bukan ditambah.
     *
     * Angka di sini cuma cerminan optimistis untuk layar. Yang menentukan tetap
     * server: `AktivitasService::review` menurunkan nilainya dari `status` dan
     * MENGABAIKAN `score` kiriman klien, jadi app tak bisa lagi menitipkan 85
     * walau seseorang memanggil fungsi ini dengan angka.
     */
    @Test
    fun `skor biner mengikuti aturan server`() {
        assertEquals(SKOR_SETUJU, skorReview("approved"))
        assertEquals(SKOR_TOLAK, skorReview("rejected"))
        assertNull(skorReview("pending"))
        // Nilai yang benar-benar dipakai, bukan sekadar konstanta yang cocok
        // dengan dirinya sendiri.
        assertEquals(100, skorReview("approved"))
        assertEquals(0, skorReview("rejected"))
    }

    // ── Bukti ────────────────────────────────────────────────────────────────

    @Test
    fun `evidenceUrl tunggal dan JSON array sama-sama terbaca`() {
        assertEquals(emptyList<String>(), parseEvidenceUrls(null))
        assertEquals(emptyList<String>(), parseEvidenceUrls("  "))
        assertEquals(listOf("/uploads/raport/a.jpg"), parseEvidenceUrls("/uploads/raport/a.jpg"))
        assertEquals(
            listOf("/uploads/raport/a.jpg", "/uploads/raport/b.jpg"),
            parseEvidenceUrls("""["/uploads/raport/a.jpg","/uploads/raport/b.jpg"]"""),
        )
    }

    @Test
    fun `bukti dipetakan ke endpoint terautentikasi, bukan path uploads mentah`() {
        // Bukti raport TIDAK disajikan sebagai berkas statis publik (S-02 web);
        // memuat "/uploads/..." apa adanya = gambar yang selalu gagal.
        assertEquals(
            "https://tridjaya.com/api/raport-harian/evidence/a_raport.jpg",
            evidenceImageUrl("/uploads/raport/a_raport.jpg", base),
        )
        assertNull(evidenceImageUrl(null, base))
        assertNull(evidenceImageUrl("   ", base))
    }

    @Test
    fun `url penuh dilewatkan apa adanya`() {
        val penuh = "https://cdn.example.com/x.jpg"
        assertEquals(penuh, evidenceImageUrl(penuh, base))
    }

    @Test
    fun `mode video tidak dianggap gambar`() {
        assertTrue(buktiVideo(AktivitasItemDto(mode = "video")))
        assertTrue(buktiVideo(AktivitasItemDto(mode = "VIDEO")))
        assertFalse(buktiVideo(AktivitasItemDto(mode = "image")))
        assertFalse(buktiVideo(AktivitasItemDto(mode = "none")))
    }

    // ── Pengelompokan ────────────────────────────────────────────────────────

    @Test
    fun `baris dikelompokkan per karyawan dan aktivitas terurut`() {
        val grup = grupPerKaryawan(
            listOf(
                AktivitasItemDto(id = "1", employeeId = "A", employeeName = "Andi", jobdeskIndex = 2),
                AktivitasItemDto(id = "2", employeeId = "B", employeeName = "Budi", jobdeskIndex = 0),
                AktivitasItemDto(id = "3", employeeId = "A", employeeName = "Andi", jobdeskIndex = 0),
            )
        )
        assertEquals(listOf("A", "B"), grup.map { it.employeeId })
        assertEquals(listOf(0, 2), grup.first().baris.map { it.jobdeskIndex })
    }

    @Test
    fun `nama kosong tidak menghapus nama dari baris lain`() {
        // Server mengisi employeeName dari profil; satu baris lama tanpa nama tak
        // boleh membuat seluruh grupnya jadi "(tanpa nama)".
        val grup = grupPerKaryawan(
            listOf(
                AktivitasItemDto(id = "1", employeeId = "A", employeeName = ""),
                AktivitasItemDto(id = "2", employeeId = "A", employeeName = "Andi", divisiName = "Sales"),
            )
        )
        assertEquals("Andi", grup.single().nama)
        assertEquals("Sales", grup.single().divisi)
    }

    @Test
    fun `karyawan tanpa nama sama sekali tetap punya label`() {
        val grup = grupPerKaryawan(listOf(AktivitasItemDto(id = "1", employeeId = "A")))
        assertEquals("(tanpa nama)", grup.single().nama)
    }
}

/**
 * Kalimat lencana bukti daur-ulang.
 *
 * Kelas yang dijaga: aturan ini memutuskan apakah seseorang disebut menyalin
 * milik ORANG LAIN atau milik dirinya sendiri, dan sebelum ini ia hidup di
 * dalam `@Composable` — 718 test modul hijau tanpa menyentuh satu barisnya.
 */
class KalimatDuplikatBuktiTest {
    @Test
    fun `id berbeda disebut karyawan lain berikut namanya`() {
        val kalimat = kalimatDuplikatBukti(
            asliKaryawanId = "k-2",
            asliKaryawanNama = "BUDI",
            asliDiunggahAt = "2026-08-19T10:12:00",
            asliDisetujui = false,
            pemilikBarisId = "k-1",
        )
        assertEquals("Bukti sama dengan unggahan BUDI (karyawan lain) · 2026-08-19 10:12:00", kalimat)
    }

    @Test
    fun `unggahan sendiri tidak pernah disebut karyawan lain`() {
        val kalimat = kalimatDuplikatBukti(
            asliKaryawanId = "k-1",
            asliKaryawanNama = "BUDI",
            asliDiunggahAt = "",
            asliDisetujui = true,
            pemilikBarisId = "k-1",
        )
        assertEquals("Bukti sama dengan unggahan sebelumnya · sudah disetujui", kalimat)
    }

    /**
     * Server MENYENSOR identitas asli untuk penilai berbatas cabang (id dan URL
     * dikosongkan). Kalau klien tetap mencetak nama yang tersisa, sensornya
     * bocor lewat pintu lain — jadi id kosong WAJIB jatuh ke "sebelumnya",
     * apa pun isi kolom nama.
     */
    @Test
    fun `identitas yang disensor server tidak dipakai menuduh`() {
        val kalimat = kalimatDuplikatBukti(
            asliKaryawanId = "",
            asliKaryawanNama = "karyawan cabang lain",
            asliDiunggahAt = "2026-08-19T08:00:00",
            asliDisetujui = false,
            pemilikBarisId = "k-1",
        )
        assertEquals("Bukti sama dengan unggahan sebelumnya · 2026-08-19 08:00:00", kalimat)
    }

    @Test
    fun `nama kosong tapi id berbeda tetap terbaca sebagai orang lain`() {
        val kalimat = kalimatDuplikatBukti(
            asliKaryawanId = "k-9",
            asliKaryawanNama = "",
            asliDiunggahAt = "",
            asliDisetujui = false,
            pemilikBarisId = "k-1",
        )
        assertEquals("Bukti sama dengan unggahan karyawan lain (karyawan lain)", kalimat)
    }

    // ── Lencana angka 7 hari (cerminan chip web) ─────────────────────────────

    @Test
    fun `tujuh hari terakhir berakhir di hari ini dan tertua di depan`() {
        val hari = tujuhHariTerakhir("2026-08-23") { iso, geser ->
            // Penggeser palsu yang cukup untuk rentang di dalam satu bulan;
            // yang diuji urutan & panjangnya, bukan aritmetika kalendernya
            // (itu milik `KlasemenStandings.shiftDays`).
            val tgl = iso.takeLast(2).toInt() + geser
            iso.dropLast(2) + tgl.toString().padStart(2, '0')
        }
        assertEquals(7, hari.size)
        assertEquals("2026-08-17", hari.first())
        assertEquals("2026-08-23", hari.last())
    }

    @Test
    fun `lencana hanya menghitung baris yang masih menunggu`() {
        // Fungsi ini juga dipakai atas daftar dari filter lain; tanpa saringan
        // ini lencana "menunggu" ikut menghitung baris yang sudah dinilai dan
        // menyuruh PIC mengerjakan pekerjaan yang sudah selesai.
        val hitung = hitungPendingPerHari(
            listOf(
                AktivitasItemDto(id = "1", tanggal = "2026-08-22", reviewStatus = "pending"),
                AktivitasItemDto(id = "2", tanggal = "2026-08-22", reviewStatus = "pending"),
                AktivitasItemDto(id = "3", tanggal = "2026-08-22", reviewStatus = "approved"),
                AktivitasItemDto(id = "4", tanggal = "2026-08-23", reviewStatus = "pending"),
            )
        )
        assertEquals(2, hitung["2026-08-22"])
        assertEquals(1, hitung["2026-08-23"])
    }

    @Test
    fun `hari tanpa antrian tidak punya entri, bukan nol`() {
        // Pemanggil membedakan "kosong" dari "tak ada angkanya"; entri bernilai
        // 0 akan dirender sebagai lencana merah — kebalikan dari gunanya.
        val hitung = hitungPendingPerHari(
            listOf(AktivitasItemDto(id = "1", tanggal = "2026-08-23", reviewStatus = "approved"))
        )
        assertNull(hitung["2026-08-23"])
        assertTrue(hitung.isEmpty())
    }

    @Test
    fun `baris tanpa tanggal tidak jadi lencana hantu`() {
        val hitung = hitungPendingPerHari(
            listOf(AktivitasItemDto(id = "1", tanggal = "", reviewStatus = "pending"))
        )
        assertTrue(hitung.isEmpty())
    }

    @Test
    fun `pemotongan server ditandai, bukan disembunyikan`() {
        assertTrue(lencanaTerpotong(total = 2500, termuat = 2000))
        assertFalse(lencanaTerpotong(total = 12, termuat = 12))
    }

    @Test
    fun `label chip menyebut hari ini dan kemarin, sisanya tanggal-bulan`() {
        assertEquals("Hari ini", labelChipHari("2026-08-23", "2026-08-23", "2026-08-22"))
        assertEquals("Kemarin", labelChipHari("2026-08-22", "2026-08-23", "2026-08-22"))
        assertEquals("17/08", labelChipHari("2026-08-17", "2026-08-23", "2026-08-22"))
    }

    @Test
    fun `label chip tak pecah untuk nilai yang bukan tanggal`() {
        // Tanggal datang dari server; bentuk asing dikembalikan apa adanya
        // supaya chip-nya tetap bisa ditekan, bukan melempar.
        assertEquals("besok", labelChipHari("besok", "2026-08-23", "2026-08-22"))
    }
}
