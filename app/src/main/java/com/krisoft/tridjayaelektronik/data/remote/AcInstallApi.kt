package com.krisoft.tridjayaelektronik.data.remote

import com.krisoft.tridjayaelektronik.data.model.AcInstallFotoBody
import com.krisoft.tridjayaelektronik.data.model.AcInstallResponBody
import com.krisoft.tridjayaelektronik.data.model.AcInstallTaskDto
import com.krisoft.tridjayaelektronik.data.model.ApiResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Pemasangan AC, sisi PETUGAS saja (`inventory-service/src/pemasangan_ac.rs`).
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
}
