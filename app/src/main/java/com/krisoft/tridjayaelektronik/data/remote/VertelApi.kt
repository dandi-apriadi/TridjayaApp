package com.krisoft.tridjayaelektronik.data.remote

import com.krisoft.tridjayaelektronik.data.model.ApiResponse
import com.krisoft.tridjayaelektronik.data.model.BarisVertelDto
import com.krisoft.tridjayaelektronik.data.model.DaftarVertelDto
import com.krisoft.tridjayaelektronik.data.model.VertelCatatBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * Verifikasi telepon (`inventory-service/src/vertel.rs`, migrasi 257).
 *
 * Rutenya MENUMPANG wildcard gateway `/api/inventory/delivery/{*rest}` yang
 * sudah ada — tak ada rute gateway baru yang perlu di-deploy untuk layar ini.
 * Yang harus sudah ada di server: migrasi 257 + biner inventory-service yang
 * memuat modul `vertel`.
 *
 * Guard-nya `boleh_vertel` (rust-shared `VERTEL_ROLES` = cs/admin/superadmin).
 */
interface VertelApi {

    /**
     * Antrian verifikasi untuk satu TANGGAL TRANSAKSI.
     *
     * [tanggal] kosong = **KEMARIN menurut WIB**, dan itu memang alur hariannya
     * — biarkan server yang menentukannya. Menghitung "kemarin" di HP berarti
     * memakai zona waktu perangkat, yang tak dijamin WIB; server sengaja tak
     * memakai `Utc::now()` untuk alasan yang sama (pada 00:00–07:00 WIB, UTC
     * masih di tanggal sebelumnya).
     *
     * Daftarnya **TIDAK disaring per cabang** untuk role cs/admin/superadmin —
     * `batas_dealer` menjawab `None`, jadi seluruh 13 cabang ikut. Jangan
     * menambahkan penyaring cabang di klien.
     */
    @GET("api/inventory/delivery/vertel")
    suspend fun daftar(
        @Query("tanggal") tanggal: String? = null,
    ): Response<ApiResponse<DaftarVertelDto>>

    /**
     * Catat hasil satu panggilan. **Upsert** — menelepon ulang dan mencatat lagi
     * MENIMPA catatan sebelumnya, bukan menambah baris.
     *
     * Server memvalidasi `kanal` dan `hasil` terhadap daftar tertutup
     * (`KANAL_SAH`/`HASIL_SAH`); nilai di luar itu dijawab 400 dengan sebabnya
     * di `errors[0]`.
     */
    @POST("api/inventory/delivery/vertel/catat")
    suspend fun catat(
        @Body body: VertelCatatBody,
    ): Response<ApiResponse<BarisVertelDto>>
}
