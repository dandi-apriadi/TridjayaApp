package com.krisoft.tridjayaelektronik.data.remote

import com.krisoft.tridjayaelektronik.data.model.AcInstallBatalBody
import com.krisoft.tridjayaelektronik.data.model.AcInstallFotoBody
import com.krisoft.tridjayaelektronik.data.model.AcInstallJadwalBody
import com.krisoft.tridjayaelektronik.data.model.AcInstallResponBody
import com.krisoft.tridjayaelektronik.data.model.AcInstallSelesaiBody
import com.krisoft.tridjayaelektronik.data.model.AcInstallTaskDto
import com.krisoft.tridjayaelektronik.data.model.AcInstallTimMasterDto
import com.krisoft.tridjayaelektronik.data.model.ApiResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Pemasangan AC (`inventory-service/src/pemasangan_ac.rs`) — sisi PETUGAS dan,
 * sejak 2026-08-25, sisi VERIFIKATOR (`acinstall.schedule`).
 *
 * Rutenya MENUMPANG wildcard gateway `/api/inventory/delivery/{*rest}` yang
 * sudah ada (`proxy_delivery`) — tak ada rute gateway baru, jadi APK ini bisa
 * bicara dengan gateway produksi yang sekarang tanpa deploy gateway. Yang harus
 * sudah ada di server: migrasi 253, 255, dan 256.
 */
interface AcInstallApi {

    /**
     * Tugas milik pemanggil — pengajuan yang salah satu tim penugasannya
     * beranggotakan dia. **Login-only dan self-scoped**; tak ada kunci kemampuan
     * yang menjaganya, dan itu disengaja di server (anggota tim dipilih
     * per-orang, jadi tak ada daftar role yang benar untuk "petugas pemasangan").
     *
     * TANPA [status] server hanya mengirim yang `dijadwalkan` — itulah yang
     * dipakai layar ini. Mengirim status lain memang bisa, tapi tugas yang sudah
     * `selesai`/`dibatalkan` bukan pekerjaan dan cuma memanjangkan daftar.
     *
     * Query-nya **TIDAK menyaring cabang**, sengaja: tim pemasangan berpindah
     * cabang (`ac_install_teams.kode_dealer` boleh NULL), jadi batas cabang di
     * sini akan menyembunyikan tugas dari orang yang sedang mengerjakannya.
     */
    @GET("api/inventory/delivery/pemasangan-ac/tugas-saya")
    suspend fun tugasSaya(
        @Query("status") status: String? = null,
    ): Response<ApiResponse<List<AcInstallTaskDto>>>

    /**
     * Menerima penugasan. Otorisasinya **KEPEMILIKAN TUGAS**
     * (`petugas_ditugaskan`), bukan jabatan — anggota tim lama yang bukan
     * teknisi tetap berhak menjawab.
     *
     * Upsert: berubah pikiran menimpa baris yang sama, dan alasan penolakan lama
     * ikut terbuang (alasan yang menempel pada "diterima" terbaca verifikator
     * sebagai penolakan yang masih berlaku).
     */
    @POST("api/inventory/delivery/pemasangan-ac/{id}/terima")
    suspend fun terima(
        @Path("id") id: String,
        @Body body: AcInstallResponBody,
    ): Response<ApiResponse<AcInstallTaskDto>>

    /** Menolak WAJIB beralasan — server menolak `alasan` kosong. Menolak TIDAK
     *  mencabut penugasan; ia menandai lalu menotifikasi penjadwalnya. */
    @POST("api/inventory/delivery/pemasangan-ac/{id}/tolak")
    suspend fun tolak(
        @Path("id") id: String,
        @Body body: AcInstallResponBody,
    ): Response<ApiResponse<AcInstallTaskDto>>

    /**
     * Lampirkan bukti foto. [AcInstallFotoBody.url] wajib hasil
     * `POST /inventory/delivery/upload-photo` — server memvalidasi asalnya
     * (`foto_url_sah`).
     *
     * Foto BEKU setelah pengajuan ditutup (`transisi::boleh_ubah_foto`), jadi
     * 4xx di sini bisa berarti "sudah selesai", bukan cuma "URL salah".
     */
    @POST("api/inventory/delivery/pemasangan-ac/{id}/foto")
    suspend fun tambahFoto(
        @Path("id") id: String,
        @Body body: AcInstallFotoBody,
    ): Response<ApiResponse<AcInstallTaskDto>>

    // -----------------------------------------------------------------------
    // Sisi VERIFIKATOR — `acinstall.schedule` (cs / admin / superadmin)
    // -----------------------------------------------------------------------

    /**
     * Seluruh pengajuan, disaring [status].
     *
     * **Kosong = SEMUA status, dipotong 300 TERBARU oleh server** — bukan "yang
     * belum dikerjakan". Beda dari [tugasSaya], yang tanpa argumen justru
     * menyempit ke `dijadwalkan`. Dua rute bertetangga dengan default yang
     * berlawanan: layar penugasan SELALU mengirim status yang diinginkannya
     * secara eksplisit supaya perilakunya tak bergantung pada default itu.
     *
     * Potongan 300 itu senyap. Kalau suatu saat antriannya melewati angka itu,
     * yang hilang adalah baris TERTUA — dan justru itu yang paling perlu
     * dikerjakan. Karena itu layar ini memuat per-status, bukan sekali ambil
     * lalu disaring di klien.
     */
    @GET("api/inventory/delivery/pemasangan-ac")
    suspend fun daftar(
        @Query("status") status: String? = null,
    ): Response<ApiResponse<List<AcInstallTaskDto>>>

    /** Master tim yang bisa ditugaskan. App hanya MEMBACA — membuat/mengubah tim
     *  tetap di web (lihat catatan di `AcInstallModels.kt`). */
    @GET("api/inventory/delivery/pemasangan-ac/tim")
    suspend fun tim(): Response<ApiResponse<List<AcInstallTimMasterDto>>>

    /**
     * Menjadwalkan atau MENJADWALKAN ULANG.
     *
     * [AcInstallJadwalBody.teamIds] MENGGANTI seluruh daftar tim — mengirim
     * daftar kosong mencabut semua penugasan. Menjadwalkan ulang yang sudah
     * dijadwalkan diizinkan (jadwal geser itu hal biasa di lapangan); yang
     * ditolak hanya pengajuan yang sudah ditutup.
     */
    @POST("api/inventory/delivery/pemasangan-ac/{id}/jadwal")
    suspend fun jadwalkan(
        @Path("id") id: String,
        @Body body: AcInstallJadwalBody,
    ): Response<ApiResponse<AcInstallTaskDto>>

    /** Menutup pekerjaan. Server MENUNTUT jadwal sudah ada — tanpa itu sebuah
     *  pengajuan bisa melompat dari "diajukan" langsung ke "selesai". */
    @POST("api/inventory/delivery/pemasangan-ac/{id}/selesai")
    suspend fun selesai(
        @Path("id") id: String,
        @Body body: AcInstallSelesaiBody,
    ): Response<ApiResponse<AcInstallTaskDto>>

    /** Membatalkan. [AcInstallBatalBody.alasan] WAJIB — server menolak yang kosong. */
    @POST("api/inventory/delivery/pemasangan-ac/{id}/batal")
    suspend fun batal(
        @Path("id") id: String,
        @Body body: AcInstallBatalBody,
    ): Response<ApiResponse<AcInstallTaskDto>>
}
