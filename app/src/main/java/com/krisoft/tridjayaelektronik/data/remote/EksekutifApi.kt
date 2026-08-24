package com.krisoft.tridjayaelektronik.data.remote

import com.krisoft.tridjayaelektronik.data.model.ApiResponse
import com.krisoft.tridjayaelektronik.data.model.EksekutifDetailCabangDto
import com.krisoft.tridjayaelektronik.data.model.EksekutifPapanDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Papan pantau eksekutif — kinerja-service modul `eksekutif` lewat gateway
 * `/api/eksekutif/…`, di belakang `require_monitoring_eksekutif`
 * (superadmin saja pada fase 1).
 *
 * **Jangan panggil ini tanpa memeriksa kemampuan `monitoring.eksekutif` lebih
 * dulu.** 403 bukan exception di Retrofit — `body()` jadi `null` dan pemanggil
 * yang memakai `.orEmpty()` melihatnya sebagai "tak ada data". Kelas kesalahan
 * itu sudah mahal di repo ini: `executive/…` ditembak buta 9.182 kali oleh 105
 * akun selama 14 hari, NOL yang pernah dijawab 200, tanpa satu pun galat
 * terlihat. Di sini pemeriksaannya sudah ada di lapisan tab (tab-nya tidak
 * dirender kalau kemampuannya tak ada), jadi layar ini hanya bisa dibuka orang
 * yang berhak — jangan memindahkan pemanggilan ini ke layar lain tanpa
 * memasang pemeriksaan yang sama.
 */
interface EksekutifApi {

    /** Ringkasan perusahaan + satu baris per cabang. */
    @GET("api/eksekutif/papan")
    suspend fun papan(
        @Query("start") start: String? = null,
        @Query("end") end: String? = null,
    ): Response<ApiResponse<EksekutifPapanDto>>

    /** Satu cabang + seluruh karyawannya. `kodeDealer` = `D-01`, dst. */
    @GET("api/eksekutif/cabang/{kodeDealer}")
    suspend fun cabang(
        @Path("kodeDealer") kodeDealer: String,
        @Query("start") start: String? = null,
        @Query("end") end: String? = null,
    ): Response<ApiResponse<EksekutifDetailCabangDto>>
}
