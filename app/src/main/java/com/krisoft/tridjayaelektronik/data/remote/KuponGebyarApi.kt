package com.krisoft.tridjayaelektronik.data.remote

import com.krisoft.tridjayaelektronik.data.model.ApiResponse
import com.krisoft.tridjayaelektronik.data.model.KuponGebyarBuktiBody
import com.krisoft.tridjayaelektronik.data.model.KuponGebyarBuktiDto
import com.krisoft.tridjayaelektronik.data.model.KuponGebyarDaftarDto
import com.krisoft.tridjayaelektronik.data.model.KuponGebyarMetaDto
import com.krisoft.tridjayaelektronik.data.model.KuponGebyarUploadDto
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Query

/**
 * Kupon Gebyar (`kinerja-service/src/kupon_gebyar/`).
 *
 * **Rutenya BARU di gateway** (`/api/kupon-gebyar` + `/api/kupon-gebyar/{*rest}`,
 * `gateway/src/lib.rs`) — beda dari `AcInstallApi` yang menumpang wildcard yang
 * sudah ada. Artinya APK yang memuat layar ini WAJIB menunggu gateway naik:
 * gateway lama menjawab 404 untuk keempat endpoint di bawah, dan kartunya akan
 * tampil lalu gagal muat. Urutan deploy ada di `docs/codebase-map/modul-lapangan.md`.
 *
 * Tak satu pun endpoint di sini menerima parameter cabang, dan itu disengaja:
 * cabang selalu dibaca server dari `auth_users.cabang_id`. Menambahkannya kelak
 * = IDOR ke daftar konsumen cabang lain.
 */
interface KuponGebyarApi {

    /**
     * Info program + angka ringkas untuk cabang pemanggil. Dipakai kartu
     * Activity, jadi ia ditembak SETIAP layar Activity dimuat — server sengaja
     * TIDAK menghitung apa pun untuk cabang yang tak berhak (`bolehLihat=false`
     * ⇒ semua angka 0), jadi ongkosnya nol untuk Manado.
     *
     * Menjawab **200 dengan `bolehLihat=false`**, bukan 403 — kartunya perlu
     * tahu bedanya "tak berhak" dari "server mati".
     */
    @GET("api/kupon-gebyar/meta")
    suspend fun meta(): Response<ApiResponse<KuponGebyarMetaDto>>

    /**
     * Daftar konsumen berhak untuk cabang pemanggil.
     *
     * **403 untuk cabang di luar program, BUKAN daftar kosong** — kosong akan
     * terbaca karyawan sebagai "cabang saya memang tak punya konsumen".
     *
     * [cari] dicocokkan SERVER ke nama & nomor. Menyaring di klien tak bisa
     * jadi pengganti: satu halaman hanya memuat `pageSize` baris dari ribuan.
     */
    @GET("api/kupon-gebyar/daftar")
    suspend fun daftar(
        @Query("page") page: Int? = null,
        @Query("pageSize") pageSize: Int? = null,
        @Query("cari") cari: String? = null,
    ): Response<ApiResponse<KuponGebyarDaftarDto>>

    /**
     * Catat bahwa satu konsumen sudah dikirimi undangan.
     *
     * **409 = sudah dikerjakan orang lain di cabang ini**, dan nama pemegangnya
     * ada di `message` (bukan `errors`, yang kosong untuk konflik). Repository
     * WAJIB membaca kedua jalur itu — kalau tidak, karyawan cuma melihat
     * kalimat generik dan tak tahu bahwa rekannya sudah mengerjakannya.
     */
    @POST("api/kupon-gebyar/bukti")
    suspend fun simpanBukti(
        @Body body: KuponGebyarBuktiBody,
    ): Response<ApiResponse<KuponGebyarBuktiDto>>

    /**
     * Unggah foto bukti. Nama part WAJIB `file` — itu yang dibaca `upload_bukti`
     * (`kupon_gebyar/upload.rs`; ia juga menerima `image`, tapi jangan
     * mengandalkan itu). Batasnya 5 MB, dan keluaran
     * `PhotoWatermark.prepareWatermarkedJpeg` selalu di bawah 2 MB.
     *
     * Gerbangnya SAMA dengan `/bukti` — role DAN vonis cabang — jadi akun
     * Manado ditolak di sini juga, bukan setelah 5 MB telanjur terkirim.
     */
    @Multipart
    @POST("api/kupon-gebyar/upload-bukti")
    suspend fun uploadBukti(
        @Part file: MultipartBody.Part,
    ): Response<ApiResponse<KuponGebyarUploadDto>>
}
