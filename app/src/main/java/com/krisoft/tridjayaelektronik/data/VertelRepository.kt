package com.krisoft.tridjayaelektronik.data

import com.krisoft.tridjayaelektronik.data.model.ApiErrorResponse
import com.krisoft.tridjayaelektronik.data.model.BarisVertelDto
import com.krisoft.tridjayaelektronik.data.model.DaftarVertelDto
import com.krisoft.tridjayaelektronik.data.model.VertelCatatBody
import com.krisoft.tridjayaelektronik.data.remote.VertelApi
import kotlinx.serialization.json.Json
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Antrian verifikasi telepon. **Tanpa cache Room, disengaja** — dua alasan yang
 * masing-masing cukup sendiri:
 *
 *  1. Isinya PII konsumen (nama, nomor HP, nilai belanja). Room di app ini ikut
 *     `adb backup` di Android 7–11 tanpa root, dan `tridjaya.db` baru-baru ini
 *     justru DIKECUALIKAN dari backup karena alasan itu (lihat `mobile/CLAUDE.md`
 *     bagian backup). Menambah tabel berisi nomor konsumen membalik pekerjaan itu.
 *  2. Baris yang sudah ditelepon rekan HILANG dari daftar kerja. Salinan basi =
 *     dua orang menelepon konsumen yang sama — persis alasan Kupon Gebyar juga
 *     tak di-cache.
 */
@Singleton
class VertelRepository @Inject constructor(
    private val api: VertelApi,
) {
    private val errorJson = Json { ignoreUnknownKeys = true }

    /**
     * [tanggal] `null` = biarkan SERVER memutuskan (kemarin WIB). Ini bukan
     * kemalasan: "kemarin" versi perangkat memakai zona waktu HP, dan HP yang
     * zonanya bukan WIB akan meminta tanggal yang salah tanpa satu pun galat.
     */
    suspend fun daftar(tanggal: String? = null): AuthResult<DaftarVertelDto> = try {
        val response = api.daftar(tanggal)
        val data = response.body()?.data
        if (response.isSuccessful && data != null) AuthResult.Success(data)
        else parseError(response, "Gagal memuat antrian verifikasi")
    } catch (e: Exception) {
        AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
    }

    suspend fun catat(body: VertelCatatBody): AuthResult<BarisVertelDto> = try {
        val response = api.catat(body)
        val data = response.body()?.data
        if (response.isSuccessful && data != null) AuthResult.Success(data)
        else parseError(response, "Gagal menyimpan hasil verifikasi")
    } catch (e: Exception) {
        AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
    }

    /**
     * `errors[0]` diutamakan (pola `AcInstallRepository`/`HomeServiceRepository`):
     * modul ini mengirim `message` generik sementara sebab sebenarnya — "Tanggal
     * harus berformat YYYY-MM-DD", "kanal tidak dikenal" — hanya ada di `errors`.
     */
    private fun <T> parseError(response: Response<*>, fallback: String): AuthResult<T> {
        val raw = response.errorBody()?.string()
        val parsed = raw?.let {
            runCatching { errorJson.decodeFromString(ApiErrorResponse.serializer(), it) }.getOrNull()
        }
        val detail = parsed?.errors?.firstOrNull()?.takeIf { it.isNotBlank() }
        return AuthResult.Failure(
            parsed?.code ?: "http_${response.code()}",
            detail ?: parsed?.message ?: "$fallback (${response.code()})",
        )
    }
}
