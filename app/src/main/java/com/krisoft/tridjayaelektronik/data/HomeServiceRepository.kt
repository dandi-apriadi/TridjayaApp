package com.krisoft.tridjayaelektronik.data

import com.krisoft.tridjayaelektronik.data.model.ApiErrorResponse
import com.krisoft.tridjayaelektronik.data.model.HsAlasanBody
import com.krisoft.tridjayaelektronik.data.model.HsAmbilUnitBody
import com.krisoft.tridjayaelektronik.data.model.HsAssignBody
import com.krisoft.tridjayaelektronik.data.model.HsAssignTarikBody
import com.krisoft.tridjayaelektronik.data.model.HsCariData
import com.krisoft.tridjayaelektronik.data.model.HsCompleteBody
import com.krisoft.tridjayaelektronik.data.model.HsCreateTicketBody
import com.krisoft.tridjayaelektronik.data.model.HsListData
import com.krisoft.tridjayaelektronik.data.model.HsLookupData
import com.krisoft.tridjayaelektronik.data.model.HsStartBody
import com.krisoft.tridjayaelektronik.data.model.HsTicketDetailDto
import com.krisoft.tridjayaelektronik.data.model.HsTicketDto
import com.krisoft.tridjayaelektronik.data.remote.HomeServiceApi
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

/** Jenis penanganan — dipakai sebagai filter `jenis` DAN sebagai penanda alur. */
const val HS_JENIS_HOME_SERVICE = "home_service"
const val HS_JENIS_TARIK_UNIT = "tarik_unit"

/**
 * Komplain purna-jual (Home Service). Tanpa cache lokal, sama alasan dengan
 * antrian lain di app ini: isinya antrian kerja milik bersama — satu tiket yang
 * sudah ditugaskan CS lain tak boleh tetap tampil sebagai "baru" di HP kita.
 */
@Singleton
class HomeServiceRepository @Inject constructor(
    private val api: HomeServiceApi
) {
    private val errorJson = Json { ignoreUnknownKeys = true }

    /**
     * [status] `null` = tanpa filter. Sengaja dibiarkan longgar dan pemilahan
     * status dilakukan layar (pola web): endpoint ini hanya menerima SATU status,
     * sementara tiap layar butuh beberapa sekaligus (papan CS: baru + menunggu
     * tindak lanjut; tarik unit: menunggu_tarik + tarik_ditugaskan).
     */
    suspend fun list(
        status: String? = null,
        jenis: String? = null,
        mine: Boolean? = null,
        limit: Int = 200,
    ): AuthResult<HsListData> = try {
        val response = api.list(status = status, jenis = jenis, mine = mine, limit = limit)
        val data = response.body()?.data
        if (response.isSuccessful && data != null) AuthResult.Success(data)
        else parseError(response, "Gagal memuat daftar komplain")
    } catch (e: Exception) {
        AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
    }

    /** Detail + riwayat kunjungan. Server menjawab 404 (bukan 403) untuk tiket
     *  di luar hak baca — layar memperlakukannya sebagai "tiket tak ditemukan". */
    suspend fun detail(id: String): AuthResult<HsTicketDetailDto> = try {
        val response = api.detail(id)
        val data = response.body()?.data
        if (response.isSuccessful && data != null) AuthResult.Success(data)
        else parseError(response, "Gagal memuat detail komplain")
    } catch (e: Exception) {
        AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
    }

    suspend fun cari(nama: String?, hp: String?): AuthResult<HsCariData> = try {
        val response = api.cari(
            nama = nama?.trim()?.takeIf { it.isNotBlank() },
            hp = hp?.trim()?.takeIf { it.isNotBlank() },
        )
        val data = response.body()?.data
        if (response.isSuccessful && data != null) AuthResult.Success(data)
        else parseError(response, "Gagal mencari transaksi konsumen")
    } catch (e: Exception) {
        AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
    }

    suspend fun lookup(noTransaksi: String): AuthResult<HsLookupData> = try {
        val response = api.lookup(noTransaksi.trim())
        val data = response.body()?.data
        if (response.isSuccessful && data != null) AuthResult.Success(data)
        else parseError(response, "Gagal memuat rincian transaksi")
    } catch (e: Exception) {
        AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
    }

    suspend fun create(body: HsCreateTicketBody): AuthResult<HsTicketDto> = try {
        val response = api.create(body)
        val data = response.body()?.data
        if (response.isSuccessful && data != null) AuthResult.Success(data)
        else parseError(response, "Gagal membuat tiket komplain")
    } catch (e: Exception) {
        AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
    }

    /** Foto (kwitansi/bukti kerja/bukti bayar/unit ditarik) → URL relatif. */
    suspend fun uploadPhoto(bytes: ByteArray, filename: String): AuthResult<String> = try {
        val part = MultipartBody.Part.createFormData(
            "file", filename, bytes.toRequestBody("image/webp".toMediaType())
        )
        val response = api.uploadPhoto(part)
        val url = response.body()?.data?.url
        if (response.isSuccessful && !url.isNullOrBlank()) AuthResult.Success(url)
        else parseError(response, "Gagal mengunggah foto")
    } catch (e: Exception) {
        AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
    }

    suspend fun assign(id: String, teknisiId: String, jadwalAt: String?): AuthResult<HsTicketDto> =
        aksi("Gagal menugaskan teknisi") { api.assign(id, HsAssignBody(teknisiId, jadwalAt?.takeIf { it.isNotBlank() })) }

    suspend fun start(id: String, lat: Double?, lng: Double?): AuthResult<HsTicketDto> =
        aksi("Gagal memulai kunjungan") { api.start(id, HsStartBody(lat, lng)) }

    suspend fun complete(id: String, body: HsCompleteBody): AuthResult<HsTicketDto> =
        aksi("Gagal menutup kunjungan") { api.complete(id, body) }

    suspend fun cancel(id: String, alasan: String): AuthResult<HsTicketDto> =
        aksi("Gagal membatalkan tiket") { api.cancel(id, HsAlasanBody(alasan.trim())) }

    suspend fun mintaTarik(id: String, alasan: String): AuthResult<HsTicketDto> =
        aksi("Gagal meminta penarikan unit") { api.mintaTarik(id, HsAlasanBody(alasan.trim())) }

    suspend fun assignTarik(id: String, driverId: String, jadwalAt: String?): AuthResult<HsTicketDto> =
        aksi("Gagal menugaskan driver") {
            api.assignTarik(id, HsAssignTarikBody(driverId, jadwalAt?.takeIf { it.isNotBlank() }))
        }

    suspend fun ambilUnit(id: String, fotoUrl: String?, catatan: String?): AuthResult<HsTicketDto> =
        aksi("Gagal menandai unit sudah diambil") {
            api.ambilUnit(id, HsAmbilUnitBody(fotoUrl, catatan?.trim()?.takeIf { it.isNotBlank() }))
        }

    suspend fun batalTarik(id: String, alasan: String): AuthResult<HsTicketDto> =
        aksi("Gagal membatalkan penarikan") { api.batalTarik(id, HsAlasanBody(alasan.trim())) }

    private suspend fun aksi(
        fallback: String,
        panggil: suspend () -> Response<com.krisoft.tridjayaelektronik.data.model.ApiResponse<HsTicketDto>>,
    ): AuthResult<HsTicketDto> = try {
        val response = panggil()
        val data = response.body()?.data
        if (response.isSuccessful && data != null) AuthResult.Success(data)
        else parseError(response, fallback)
    } catch (e: Exception) {
        AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
    }

    /**
     * `errors[0]` diutamakan (pola `AktivitasRepository`): validasi modul ini
     * mengirim `message` generik sementara sebab sebenarnya ("hasil selesai
     * wajib foto", "jadwal harus YYYY-MM-DD…") hanya ada di `errors`.
     */
    private fun <T> parseError(response: Response<*>, fallback: String): AuthResult<T> {
        val raw = response.errorBody()?.string()
        val parsed = raw?.let {
            runCatching { errorJson.decodeFromString(ApiErrorResponse.serializer(), it) }.getOrNull()
        }
        val detail = parsed?.errors?.firstOrNull()?.takeIf { it.isNotBlank() }
        return AuthResult.Failure(
            parsed?.code ?: "http_${response.code()}",
            detail ?: parsed?.message ?: "$fallback (${response.code()})"
        )
    }
}
