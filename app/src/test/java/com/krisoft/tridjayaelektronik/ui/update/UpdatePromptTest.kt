package com.krisoft.tridjayaelektronik.ui.update

import com.krisoft.tridjayaelektronik.data.update.UpdateStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Aturan pemeriksaan pembaruan berulang + prompt per versi.
 *
 * Latar pengukuran produksi 2026-08-15 yang jadi dasarnya: dari ~106 orang di armada hanya 10
 * yang sampai di versionCode 85; rilis 84 naik 08:10 dan 85 naik 09:00, dan orang yang aplikasinya
 * dibiarkan hidup seharian tetap di 83 sementara orang yang me-restart aplikasinya melompat
 * 81 → 85. Angka 83/84/85 dipakai apa adanya di tes ini supaya skenarionya bisa dikenali.
 */
class UpdatePromptTest {

    private fun tersedia(versionCode: Long, force: Boolean = false) = UpdateStatus.Available(
        force = force,
        latestVersionCode = versionCode,
        latestVersionName = "2.$versionCode",
        releaseNotes = "",
    )

    // --- ambang jeda antar pemeriksaan -------------------------------------------------

    @Test
    fun `pemeriksaan pertama selalu boleh jalan`() {
        assertTrue(bolehCekOtomatis(percobaanTerakhirAt = null, percobaanTerakhirGagal = false, now = 0L))
    }

    @Test
    fun `foreground beruntun ditahan sampai ambang sukses terlampaui`() {
        val mulai = 1_000_000L
        // Kembali dari kamera selfie absen ~90 detik kemudian: TIDAK boleh mengirim permintaan.
        assertFalse(
            "ON_RESUME beruntun tak boleh mengalikan trafik ke server",
            bolehCekOtomatis(mulai, percobaanTerakhirGagal = false, now = mulai + 90_000L),
        )
        // Sedetik sebelum ambang: masih ditahan.
        assertFalse(bolehCekOtomatis(mulai, false, now = mulai + JEDA_CEK_SUKSES_MS - 1))
        // Tepat di ambang: jalan.
        assertTrue(bolehCekOtomatis(mulai, false, now = mulai + JEDA_CEK_SUKSES_MS))
    }

    @Test
    fun `kegagalan dicoba lagi lebih cepat daripada keberhasilan`() {
        val mulai = 1_000_000L
        val now = mulai + JEDA_CEK_GAGAL_MS
        assertTrue(
            "Unknown berarti belum tahu — jangan dihukum jeda penuh",
            bolehCekOtomatis(mulai, percobaanTerakhirGagal = true, now = now),
        )
        assertFalse(
            "pemeriksaan yang berhasil tak perlu diulang secepat itu",
            bolehCekOtomatis(mulai, percobaanTerakhirGagal = false, now = now),
        )
        assertTrue("ambang gagal harus lebih pendek", JEDA_CEK_GAGAL_MS < JEDA_CEK_SUKSES_MS)
    }

    @Test
    fun `ambang sukses cukup besar untuk armada 106 perangkat`() {
        // Plafon trafik = 1 perangkat × (1 jam / ambang) × 106 perangkat × 12 jam kerja.
        val perJamPerPerangkat = 3_600_000L / JEDA_CEK_SUKSES_MS
        val plafonHarian = perJamPerPerangkat * 106 * 12
        assertEquals(2L, perJamPerPerangkat)
        assertTrue("plafon armada harus tetap di orde ribuan/hari", plafonHarian <= 3_000)
    }

    // --- kegagalan tak boleh menghapus temuan ------------------------------------------

    @Test
    fun `Unknown tidak menghapus pembaruan yang sudah ditemukan`() {
        val sudahTahu = tersedia(85)
        val sesudah = statusSetelahCek(sebelum = sudahTahu, hasil = UpdateStatus.Unknown)
        assertSame(
            "sinyal hilang sesaat tak boleh membuat prompt lenyap tanpa ditutup pengguna",
            sudahTahu,
            sesudah,
        )
    }

    @Test
    fun `UpToDate tetap menimpa — itu jawaban tegas server, bukan ketidaktahuan`() {
        assertEquals(
            UpdateStatus.UpToDate,
            statusSetelahCek(sebelum = tersedia(85), hasil = UpdateStatus.UpToDate),
        )
    }

    @Test
    fun `temuan versi lebih baru menimpa temuan lama`() {
        val baru = tersedia(85)
        assertSame(baru, statusSetelahCek(sebelum = tersedia(84), hasil = baru))
    }

    // --- prompt ditutup PER VERSI ------------------------------------------------------

    @Test
    fun `menutup prompt 84 tidak menelan prompt 85`() {
        assertFalse(
            "versi yang barusan ditutup tak boleh muncul lagi",
            bolehTampilkanPrompt(tersedia(84), versiDitutup = 84),
        )
        assertTrue(
            "85 terbit sesudah 84 ditutup — wajib memunculkan prompt lagi",
            bolehTampilkanPrompt(tersedia(85), versiDitutup = 84),
        )
    }

    @Test
    fun `belum ada yang ditutup berarti prompt tampil`() {
        assertTrue(bolehTampilkanPrompt(tersedia(85), versiDitutup = null))
    }

    @Test
    fun `pembaruan wajib tampil walau versi yang sama pernah ditutup`() {
        assertTrue(
            bolehTampilkanPrompt(tersedia(85, force = true), versiDitutup = 85),
        )
    }

    @Test
    fun `versi lebih lama dari yang ditutup tidak memunculkan prompt`() {
        // Rilis ditarik kembali: server balik menunjuk 84 sesudah 85 ditutup pengguna.
        assertFalse(bolehTampilkanPrompt(tersedia(84), versiDitutup = 85))
    }

    // --- pembaruan wajib: dialog blokir ditahan sampai unduhan diam-diam siap ----------
    //
    // Sebelum ini `force` selalu `return true` tanpa syarat, jadi dialog blokir muncul
    // sedetik setelah versi baru terdeteksi — sebelum unduhannya (yang auto-mulai) bahkan
    // sempat berjalan. Karyawan lapangan kehilangan app-nya justru selama unduhan
    // berlangsung, yang bisa berarti menit-menitan di sinyal lapangan yang lemah.

    @Test
    fun `pembaruan wajib ditahan selama unduhan belum siap dilihat`() {
        assertFalse(
            "unduhan Idle/Downloading — jangan kunci app, biarkan diam-diam di latar",
            bolehTampilkanPrompt(tersedia(85, force = true), versiDitutup = null, unduhanSiapDilihat = false),
        )
    }

    @Test
    fun `pembaruan wajib tampil begitu unduhan siap dipasang atau gagal`() {
        assertTrue(
            "ReadyToInstall/Failed — sekarang baru wajib menunjukkan sesuatu ke pengguna",
            bolehTampilkanPrompt(tersedia(85, force = true), versiDitutup = null, unduhanSiapDilihat = true),
        )
    }

    @Test
    fun `pembaruan opsional tak terpengaruh parameter unduhan siap`() {
        // Update opsional tak pernah mengunduh diam-diam — unduhannya baru mulai
        // setelah "Perbarui Sekarang" ditekan, jadi dialog tawarannya harus tetap
        // tampil terlepas dari nilai unduhanSiapDilihat.
        assertTrue(
            bolehTampilkanPrompt(tersedia(85, force = false), versiDitutup = null, unduhanSiapDilihat = false),
        )
    }

    // --- penundaan SEMENTARA (Back / ketuk di luar dialog) -----------------------------
    //
    // Kelas bug yang dijaga di sini: sebelum pemisahan ini, Back dan ketuk di
    // luar dialog memanggil `dismissOptional()` yang SAMA dengan tombol
    // "Nanti", sehingga satu ketukan meleset mengunci prompt selama Activity
    // hidup. `_versiPromptDitutup` cuma MutableStateFlow di ViewModel — hanya
    // kematian proses yang membersihkannya, dan HP kerja jarang benar-benar
    // ditutup. Efeknya hanya bisa terjadi saat update TIDAK wajib, karena saat
    // wajib kedua flag DialogProperties bernilai false.

    @Test
    fun `penundaan sementara menyembunyikan prompt versi itu saja`() {
        assertFalse(
            "versi yang barusan ditunda tak boleh langsung muncul lagi",
            bolehTampilkanPrompt(tersedia(85), versiDitutup = null, versiDitundaSementara = 85),
        )
        assertTrue(
            "versi LAIN tak ikut tertunda",
            bolehTampilkanPrompt(tersedia(86), versiDitutup = null, versiDitundaSementara = 85),
        )
    }

    @Test
    fun `pembaruan wajib menembus penundaan sementara`() {
        // Penjaga terhadap kemunduran: kalau penundaan bisa menelan update
        // wajib, satu ketukan tak sengaja akan melepas orang dari rilis yang
        // justru dipaksa karena operasional rusak.
        assertTrue(
            bolehTampilkanPrompt(tersedia(85, force = true), versiDitutup = null, versiDitundaSementara = 85),
        )
    }

    @Test
    fun `penundaan sementara yang sudah dibersihkan memunculkan prompt lagi`() {
        // `null` = ViewModel sudah membersihkannya di pemeriksaan berikutnya.
        // Inilah beda pokoknya dengan "Nanti", yang bertahan seumur Activity.
        assertTrue(bolehTampilkanPrompt(tersedia(85), versiDitutup = null, versiDitundaSementara = null))
    }

    @Test
    fun `nanti tetap mengunci walau penundaan sementara sudah bersih`() {
        // Penolakan SENGAJA tidak boleh ikut terhapus oleh pembersihan
        // penundaan sementara — kalau ikut, prompt yang sudah ditolak akan
        // muncul lagi tiap 30 menit dan berubah jadi gangguan.
        assertFalse(bolehTampilkanPrompt(tersedia(85), versiDitutup = 85, versiDitundaSementara = null))
    }

    // --- unduhan basi ------------------------------------------------------------------

    @Test
    fun `unduhan 84 yang sudah selesai dibuang saat 85 ditemukan`() {
        assertTrue(
            "kalau tidak, installer dipanggil ulang untuk berkas 84",
            unduhanBasi(unduhanUntukVersi = 84, sedangMengunduh = false, versiTerbaru = 85),
        )
    }

    @Test
    fun `unduhan versi yang sama tidak dibuang`() {
        assertFalse(unduhanBasi(unduhanUntukVersi = 85, sedangMengunduh = false, versiTerbaru = 85))
    }

    @Test
    fun `unduhan yang sedang berjalan tidak diganggu`() {
        assertFalse(
            "pembatalan di tengah aliran tak dipasang di downloadApk",
            unduhanBasi(unduhanUntukVersi = 84, sedangMengunduh = true, versiTerbaru = 85),
        )
    }

    @Test
    fun `tanpa unduhan atau tanpa versi baru tak ada yang dibuang`() {
        assertFalse(unduhanBasi(unduhanUntukVersi = null, sedangMengunduh = false, versiTerbaru = 85))
        assertFalse(unduhanBasi(unduhanUntukVersi = 84, sedangMengunduh = false, versiTerbaru = null))
    }
}
