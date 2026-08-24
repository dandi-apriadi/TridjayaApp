package com.krisoft.tridjayaelektronik.data

import com.krisoft.tridjayaelektronik.data.model.AcInstallFotoBody
import com.krisoft.tridjayaelektronik.data.model.AcInstallResponBody
import com.krisoft.tridjayaelektronik.data.model.AcInstallTaskDto
import com.krisoft.tridjayaelektronik.data.model.ApiErrorResponse
import com.krisoft.tridjayaelektronik.data.model.ApiResponse
import com.krisoft.tridjayaelektronik.data.remote.AcInstallApi
import kotlinx.serialization.json.Json
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tugas pemasangan AC milik petugas. **Tanpa cache lokal**, alasan sama dengan
 * antrian lain di app ini: isinya jadwal kerja yang bisa diubah verifikator
 * kapan saja (penjadwalan ulang MENGGANTI daftar tim, bukan menambahinya), jadi
 * daftar basi di HP berarti orang berangkat ke pekerjaan yang sudah dicabut
 * darinya.
 *
 * **Unggah fotonya sengaja MEMINJAM [DeliveryFlowRepository.uploadPhoto]**, tak
 * menulis jalur unggah sendiri: server pun menumpang direktori yang sama
 * (`DELIVERY_UPLOAD_DIR`, di-pin sejak 2026-07-31) dan memvalidasi bahwa URL
 * foto memang berasal dari endpoint unggah itu (`foto_url_sah`). Jalur unggah
 * kedua di app = satu lagi kesempatan mengirim URL yang ditolak server.
 */
@Singleton
class AcInstallRepository @Inject constructor(
    private val api: AcInstallApi,
) {
    private val errorJson = Json { ignoreUnknownKeys = true }

    /**
     * [status] `null` = perilaku default server, yaitu **hanya yang
     * `dijadwalkan`** — bukan "semua status". Itu yang diinginkan layar tugas:
     * pengajuan yang sudah selesai/dibatalkan bukan pekerjaan.
     */
    suspend fun tugasSaya(status: String? = null): AuthResult<List<AcInstallTaskDto>> = try {
        val response = api.tugasSaya(status)
        val data = response.body()?.data
        if (response.isSuccessful && data != null) AuthResult.Success(data)
        else parseError(response, "Gagal memuat tugas pemasangan")
    } catch (e: Exception) {
        AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
    }

    suspend fun terima(id: String): AuthResult<AcInstallTaskDto> =
        aksi("Gagal menerima tugas") { api.terima(id, AcInstallResponBody()) }

    /** [alasan] wajib terisi — server menolak yang kosong. Gerbang klien ada di
     *  `AcInstallPlan.bolehTolak`, jangan mengandalkan 400 sebagai validasi. */
    suspend fun tolak(id: String, alasan: String): AuthResult<AcInstallTaskDto> =
        aksi("Gagal menolak tugas") { api.tolak(id, AcInstallResponBody(alasan.trim())) }

    suspend fun tambahFoto(id: String, url: String, keterangan: String?): AuthResult<AcInstallTaskDto> =
        aksi("Gagal melampirkan bukti foto") {
            api.tambahFoto(id, AcInstallFotoBody(url, keterangan?.trim()?.takeIf { it.isNotBlank() }))
        }

    private suspend fun aksi(
        fallback: String,
        panggil: suspend () -> Response<ApiResponse<AcInstallTaskDto>>,
    ): AuthResult<AcInstallTaskDto> = try {
        val response = panggil()
        val data = response.body()?.data
        if (response.isSuccessful && data != null) AuthResult.Success(data)
        else parseError(response, fallback)
    } catch (e: Exception) {
        AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
    }

    /**
     * `errors[0]` diutamakan (pola `AktivitasRepository`/`HomeServiceRepository`):
     * modul ini mengirim `message` generik sementara sebab sebenarnya — "alasan
     * penolakan wajib diisi", "pengajuan sudah ditutup" — hanya ada di `errors`.
     * Tanpa ini petugas cuma melihat kalimat yang tak menjelaskan apa pun.
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
