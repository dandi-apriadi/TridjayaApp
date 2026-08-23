package com.krisoft.tridjayaelektronik.data

import com.krisoft.tridjayaelektronik.data.model.ApiErrorResponse
import com.krisoft.tridjayaelektronik.data.model.EksekutifDetailCabangDto
import com.krisoft.tridjayaelektronik.data.model.EksekutifPapanDto
import com.krisoft.tridjayaelektronik.data.remote.EksekutifApi
import kotlinx.serialization.json.Json
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Papan pantau eksekutif — langsung ke `kinerja-service` via [EksekutifApi].
 *
 * **Tanpa cache Room, sengaja.** Pola 5 jam yang dipakai Inventory/Home/Leads
 * cocok untuk daftar yang isinya jarang berubah dan dipakai offline. Papan ini
 * kebalikannya: ia dibuka untuk mengambil keputusan atas keadaan HARI INI, dan
 * angka kemarin yang tampil tanpa penanda lebih buruk daripada layar yang jujur
 * mengaku gagal memuat. Kesegaran salinan GS pun ikut di setiap respons
 * (`kesegaran`) justru supaya pembaca tahu umur angkanya.
 */
@Singleton
class EksekutifRepository @Inject constructor(
    private val api: EksekutifApi,
) {
    private val errorJson = Json { ignoreUnknownKeys = true }

    suspend fun papan(start: String?, end: String?): AuthResult<EksekutifPapanDto> = try {
        val response = api.papan(start, end)
        val data = response.body()?.data
        if (response.isSuccessful && data != null) AuthResult.Success(data)
        else parseError(response, "Gagal memuat papan eksekutif")
    } catch (e: Exception) {
        AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
    }

    suspend fun cabang(
        kodeDealer: String,
        start: String?,
        end: String?,
    ): AuthResult<EksekutifDetailCabangDto> = try {
        val response = api.cabang(kodeDealer, start, end)
        val data = response.body()?.data
        if (response.isSuccessful && data != null) AuthResult.Success(data)
        else parseError(response, "Gagal memuat detail cabang")
    } catch (e: Exception) {
        AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
    }

    private fun <T> parseError(response: Response<*>, fallback: String): AuthResult<T> {
        val raw = response.errorBody()?.string()
        val parsed = raw?.let {
            runCatching { errorJson.decodeFromString(ApiErrorResponse.serializer(), it) }.getOrNull()
        }
        // `errors[0]` DULU — `ApiError::Validation` selalu mengirim
        // `message: "Input tidak valid"` dan menaruh alasannya di `errors`.
        // Di layar ini alasan itu yang bisa ditindaklanjuti ("rentang maksimal
        // 366 hari"), sedangkan pesan generiknya tidak.
        val detail = parsed?.errors?.firstOrNull()?.takeIf { it.isNotBlank() }
        // 503 dari `ApiError::Unavailable` punya arti khusus di sini: papannya
        // berjalan, DB-nya yang tak terjangkau. Dibedakan supaya tak terbaca
        // sebagai "tak ada data".
        val fallbackPesan = if (response.code() == 503) {
            "Data papan sedang tidak tersedia — coba lagi sebentar lagi"
        } else {
            "$fallback (${response.code()})"
        }
        return AuthResult.Failure(
            parsed?.code ?: "http_${response.code()}",
            detail ?: parsed?.message ?: fallbackPesan,
        )
    }
}
