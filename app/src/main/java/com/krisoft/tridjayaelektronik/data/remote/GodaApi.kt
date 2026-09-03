package com.krisoft.tridjayaelektronik.data.remote

import com.krisoft.tridjayaelektronik.data.model.ApiResponse
import com.krisoft.tridjayaelektronik.data.model.GodaStokDto
import com.krisoft.tridjayaelektronik.data.model.GodaTambahHasilDto
import com.krisoft.tridjayaelektronik.data.model.GodaTambahSnBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * Menu SN Goda — kinerja-service `goda.rs` via gateway `/api/goda/…`.
 *
 * **`kodeDealer` WAJIB dikirim dari app, dan itu bukan sekadar penghemat byte.**
 * Server memperlakukan parameter kosong sebagai "semua cabang": 13 cabang ×
 * ribuan baris stok GODA berikut seluruh serial-nya dalam satu respons, di HP
 * lapangan. Layar ini memang dipakai per-cabang (petugas mendaftarkan SN unit
 * yang fisiknya ada di gudangnya), jadi cabang dipilih dulu, baru dimuat.
 *
 * `PUT /api/goda/serial/{id}` (GANTI SN) SENGAJA tidak diekspos di sini:
 * penggantian menghapus nilai lama secara permanen (registry-nya tak punya tabel
 * riwayat) dan itu keputusan meja, bukan pekerjaan sambil memegang unit. Jalur
 * web tetap satu-satunya pintunya.
 */
interface GodaApi {

    @GET("api/goda/stok")
    suspend fun stok(
        @Query("kodeDealer") kodeDealer: String,
        @Query("cari") cari: String? = null
    ): Response<ApiResponse<GodaStokDto>>

    @POST("api/goda/serial")
    suspend fun tambahSerial(@Body body: GodaTambahSnBody): Response<ApiResponse<GodaTambahHasilDto>>
}
