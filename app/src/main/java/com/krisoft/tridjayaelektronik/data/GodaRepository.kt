package com.krisoft.tridjayaelektronik.data

import com.krisoft.tridjayaelektronik.data.model.ApiErrorResponse
import com.krisoft.tridjayaelektronik.data.model.GodaStokDto
import com.krisoft.tridjayaelektronik.data.model.GodaTambahHasilDto
import com.krisoft.tridjayaelektronik.data.model.GodaTambahSnBody
import com.krisoft.tridjayaelektronik.data.remote.GodaApi
import kotlinx.serialization.json.Json
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SN Goda — langsung ke kinerja-service via [GodaApi]. Tanpa cache lokal: stok
 * salinan GS dan registry SN berubah dari sisi lain (web, importir), dan daftar
 * SN yang basi di HP membuat orang mendaftarkan unit yang sudah terdaftar.
 */
@Singleton
class GodaRepository @Inject constructor(
    private val api: GodaApi
) {
    private val errorJson = Json { ignoreUnknownKeys = true }

    suspend fun stok(kodeDealer: String): AuthResult<GodaStokDto> = try {
        val response = api.stok(kodeDealer = kodeDealer)
        val data = response.body()?.data
        if (response.isSuccessful && data != null) AuthResult.Success(data)
        else parseError(response, "Gagal memuat stok GODA")
    } catch (e: Exception) {
        AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
    }

    /**
     * Daftarkan satu SN baru. Pesan galat server dipakai APA ADANYA — ia yang
     * tahu bedanya "SN sudah terdaftar di cabang itu" (1062) dari "barang ini
     * bukan unit GODA", dan dua-duanya harus terbaca petugas di depan unitnya.
     */
    suspend fun tambahSerial(
        kodeDealer: String,
        kodeBarang: String,
        serialNumber: String
    ): AuthResult<GodaTambahHasilDto> = try {
        val response = api.tambahSerial(
            GodaTambahSnBody(
                kodeDealer = kodeDealer,
                kodeBarang = kodeBarang,
                serialNumber = serialNumber
            )
        )
        val data = response.body()?.data
        if (response.isSuccessful && data != null) AuthResult.Success(data)
        else parseError(response, "Gagal menyimpan serial number")
    } catch (e: Exception) {
        AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
    }

    private fun <T> parseError(response: Response<*>, fallback: String): AuthResult<T> {
        val raw = response.errorBody()?.string()
        val parsed = raw?.let {
            runCatching { errorJson.decodeFromString(ApiErrorResponse.serializer(), it) }.getOrNull()
        }
        // `errors` (ApiError::Validation) memuat kalimat yang benar-benar
        // menjelaskan penolakan — `message`-nya sendiri cuma "Validasi gagal".
        val rinci = parsed?.errors?.firstOrNull()?.takeIf { it.isNotBlank() }
        return AuthResult.Failure(
            parsed?.code ?: "http_${response.code()}",
            rinci ?: parsed?.message ?: "$fallback (${response.code()})",
            response.code()
        )
    }
}
