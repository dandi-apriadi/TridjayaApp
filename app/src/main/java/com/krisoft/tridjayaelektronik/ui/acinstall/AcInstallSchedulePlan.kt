package com.krisoft.tridjayaelektronik.ui.acinstall

import com.krisoft.tridjayaelektronik.data.model.AcInstallStatus
import com.krisoft.tridjayaelektronik.data.model.AcInstallTaskDto
import com.krisoft.tridjayaelektronik.data.model.AcInstallTimMasterDto

/**
 * Aturan MURNI sisi verifikator pemasangan AC — cerminan
 * `pemasangan_ac::transisi::*` di server. Dipisah dari Compose supaya bisa
 * diuji tanpa perangkat, pola sama [AcInstallPlan].
 *
 * **Kenapa dicerminkan sama sekali, padahal server sudah menegakkannya?**
 * Supaya tombol yang pasti ditolak tidak pernah bisa ditekan. Mengandalkan 400
 * sebagai validasi berarti verifikator baru tahu setelah menunggu jaringan, dan
 * pesannya tiba sebagai kalimat merah tanpa konteks tombol mana yang salah.
 *
 * Kalau cerminan ini dan server berselisih, **server yang benar** — perbaiki di
 * sini, jangan melonggarkan di sana.
 */
object AcInstallSchedulePlan {

    /** Status yang tak bisa diapa-apakan lagi (`transisi::tertutup`). */
    fun tertutup(status: String): Boolean =
        status == AcInstallStatus.SELESAI || status == AcInstallStatus.DIBATALKAN

    /**
     * Menjadwalkan ULANG yang sudah dijadwalkan DIIZINKAN — jadwal geser itu hal
     * biasa di lapangan. Yang ditolak hanya yang sudah ditutup.
     */
    fun bolehJadwalkan(status: String): Boolean = !tertutup(status)

    /**
     * Menutup pekerjaan menuntut TIGA syarat sekaligus (`transisi::boleh_selesai`):
     * belum ditutup, status PERSIS `dijadwalkan`, DAN minimal satu foto bukti.
     *
     * Syarat foto itu yang paling mudah terlewat saat mencerminkan aturan ini —
     * ia tidak terbaca dari nama fungsinya, dan tanpa cerminan di klien tombol
     * "Tandai selesai" akan tampak aktif lalu dijawab 400 di lapangan. Fotonya
     * diunggah PETUGAS dari layar tugasnya, jadi verifikator memang bisa
     * berhadapan dengan pengajuan yang belum berfoto.
     */
    fun bolehSelesai(task: AcInstallTaskDto): Boolean =
        !tertutup(task.status) &&
            task.status == AcInstallStatus.DIJADWALKAN &&
            task.foto.isNotEmpty()

    /** Alasan tombol "Tandai selesai" mati — supaya layar bisa menjelaskannya,
     *  bukan sekadar meredupkan tombol tanpa sebab. */
    fun alasanTakBisaSelesai(task: AcInstallTaskDto): String? = when {
        task.status == AcInstallStatus.SELESAI -> "Sudah ditandai selesai"
        task.status == AcInstallStatus.DIBATALKAN -> "Sudah dibatalkan"
        task.status != AcInstallStatus.DIJADWALKAN -> "Jadwalkan dulu sebelum bisa ditutup"
        task.foto.isEmpty() -> "Menunggu foto bukti dari petugas"
        else -> null
    }

    fun bolehBatal(status: String): Boolean = !tertutup(status)

    /** Membatalkan WAJIB beralasan — server menolak yang kosong. */
    fun bolehSimpanBatal(alasan: String): Boolean = alasan.trim().isNotEmpty()

    /**
     * `YYYY-MM-DD`, cerminan `pemasangan_ac::tanggal_sah`.
     *
     * Diperiksa dengan aritmetika string + rentang angka, **bukan `java.time`**
     * (haram di `app/src/main` — minSdk 24 tanpa desugaring; lihat
     * `mobile/CLAUDE.md`). Rentangnya diperiksa karena pola digit saja
     * meloloskan bulan 13 dan tanggal 32 — pola yang sama dipakai
     * `ui/opname/OpnameJendela.kt`.
     *
     * Ini sengaja TIDAK memvalidasi jumlah hari per bulan (31 Februari lolos di
     * sini, ditolak server). Menyalin aturan kabisat ke klien menambah aturan
     * kedua yang bisa menyimpang, demi menangkap salah ketik yang server sudah
     * jawab dengan pesan jelas.
     */
    fun tanggalSah(raw: String): Boolean {
        val v = raw.trim()
        if (v.length != 10 || v[4] != '-' || v[7] != '-') return false
        val th = v.substring(0, 4).toIntOrNull() ?: return false
        val bl = v.substring(5, 7).toIntOrNull() ?: return false
        val tg = v.substring(8, 10).toIntOrNull() ?: return false
        return th in 2000..2999 && bl in 1..12 && tg in 1..31
    }

    /** `HH:MM`, cerminan `jam_sah` (server juga menerima `HH:MM:SS`; app hanya
     *  mengirim bentuk pendek). Kosong = tanpa jam, dan itu SAH. */
    fun jamSah(raw: String): Boolean {
        val v = raw.trim()
        if (v.isEmpty()) return true
        if (v.length != 5 || v[2] != ':') return false
        val j = v.substring(0, 2).toIntOrNull() ?: return false
        val m = v.substring(3, 5).toIntOrNull() ?: return false
        return j in 0..23 && m in 0..59
    }

    /**
     * Boleh menekan "Simpan jadwal"? Tim boleh KOSONG — server mengizinkannya,
     * dan itu cara mencabut penugasan yang salah tanpa membatalkan pengajuannya.
     */
    fun bolehSimpanJadwal(tanggal: String, jam: String): Boolean =
        tanggalSah(tanggal) && jamSah(jam)

    /**
     * Tim yang layak ditawarkan saat menjadwalkan: yang AKTIF saja.
     *
     * Tim nonaktif tetap ikut terkirim server dan tetap harus TERBACA pada
     * pengajuan lama yang terlanjur memakainya — yang disaring di sini hanya
     * daftar PILIHAN, bukan tampilan.
     */
    fun timBisaDipilih(semua: List<AcInstallTimMasterDto>): List<AcInstallTimMasterDto> =
        semua.filter { it.aktif }

    /**
     * Id tim yang sudah ditugaskan pada [task], untuk menyemai centang saat
     * membuka form jadwal.
     *
     * **Wajib disemai.** `teamIds` MENGGANTI seluruh daftar, jadi form yang
     * mulai dari kosong lalu dikirim apa adanya akan MENCABUT tim yang sudah
     * ada — tanpa error, dan terbaca sebagai "penugasannya hilang sendiri".
     */
    fun timTerpilihAwal(task: AcInstallTaskDto): Set<String> =
        task.tim.map { it.teamId }.toSet()

    /** Urutan tab daftar. `diajukan` didahulukan: itu yang menunggu keputusan. */
    val URUTAN_STATUS: List<String> = listOf(
        AcInstallStatus.DIAJUKAN,
        AcInstallStatus.DIJADWALKAN,
        AcInstallStatus.SELESAI,
        AcInstallStatus.DIBATALKAN,
    )

    fun labelStatus(status: String): String = when (status) {
        AcInstallStatus.DIAJUKAN -> "Perlu dijadwalkan"
        AcInstallStatus.DIJADWALKAN -> "Terjadwal"
        AcInstallStatus.SELESAI -> "Selesai"
        AcInstallStatus.DIBATALKAN -> "Dibatalkan"
        else -> status
    }
}
