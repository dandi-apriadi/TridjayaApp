package com.krisoft.tridjayaelektronik.data

import com.krisoft.tridjayaelektronik.data.model.ApiErrorResponse
import com.krisoft.tridjayaelektronik.data.model.KuponGebyarBuktiBody
import com.krisoft.tridjayaelektronik.data.model.KuponGebyarBuktiDto
import com.krisoft.tridjayaelektronik.data.model.KuponGebyarDaftarDto
import com.krisoft.tridjayaelektronik.data.model.KuponGebyarMetaDto
import com.krisoft.tridjayaelektronik.data.remote.KuponGebyarApi
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Kupon Gebyar — daftar konsumen berhak kupon doorprize untuk cabang si
 * pemanggil, plus pencatatan bukti pengiriman undangan.
 *
 * **Tanpa cache Room, dan itu keputusan bukan kelalaian.** Isinya nama + nomor
 * HP + nilai belanja konsumen: data paling sensitif yang pernah disentuh app
 * ini. Menyimpannya di HP berarti ia bertahan setelah orangnya pindah cabang
 * atau berhenti, di luar jangkauan pencabutan hak akses mana pun. Alasan kedua
 * yang sama kuatnya: baris yang sudah dikerjakan rekan secabang HILANG dari
 * daftar, jadi salinan basi = dua orang mengirim undangan ke konsumen yang sama.
 */
@Singleton
class KuponGebyarRepository @Inject constructor(
    private val api: KuponGebyarApi,
) {
    private val errorJson = Json { ignoreUnknownKeys = true }

    /**
     * Info program + angka ringkas. **Gagal ≠ tak berhak**: pemanggil harus
     * membedakan [AuthResult.Failure] (jaringan/server) dari `bolehLihat=false`
     * (vonis server). Menyamakannya membuat kartu Activity lenyap tiap kali
     * sinyal jelek, dan karyawan melapor "menunya hilang".
     */
    suspend fun meta(): AuthResult<KuponGebyarMetaDto> = try {
        val response = api.meta()
        val data = response.body()?.data
        if (response.isSuccessful && data != null) AuthResult.Success(data)
        else parseError(response, "Gagal memuat info Kupon Gebyar")
    } catch (e: Exception) {
        AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
    }

    /** [cari] dikirim ke SERVER, bukan disaring di klien: satu halaman hanya
     *  memuat sebagian kecil dari ribuan baris cabang. */
    suspend fun daftar(page: Int, pageSize: Int, cari: String?): AuthResult<KuponGebyarDaftarDto> = try {
        val response = api.daftar(page, pageSize, cari?.trim()?.takeIf { it.isNotEmpty() })
        val data = response.body()?.data
        if (response.isSuccessful && data != null) AuthResult.Success(data)
        else parseError(response, "Gagal memuat daftar konsumen Gebyar")
    } catch (e: Exception) {
        AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
    }

    /**
     * Unggah foto → dapat URL logis `/uploads/kupon-gebyar/{uuid}.jpg`.
     *
     * `image/jpeg` mati-matian benar: keluaran `PhotoWatermark.prepareWatermarkedJpeg`
     * SELALU JPEG apa pun format sumbernya, dan server memeriksa
     * ekstensi × content-type × magic bytes SERENTAK.
     */
    suspend fun unggahFoto(bytes: ByteArray, filename: String): AuthResult<String> = try {
        val part = MultipartBody.Part.createFormData(
            "file",
            filename,
            bytes.toRequestBody("image/jpeg".toMediaType()),
        )
        val response = api.uploadBukti(part)
        val url = response.body()?.data?.url
        if (response.isSuccessful && !url.isNullOrBlank()) AuthResult.Success(url)
        else parseError(response, "Gagal mengunggah foto bukti")
    } catch (e: Exception) {
        AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
    }

    /** [buktiUrl] wajib hasil [unggahFoto] — server memvalidasi bentuknya. */
    suspend fun simpanBukti(
        kodeRekanan: String,
        buktiUrl: String,
        catatan: String?,
    ): AuthResult<KuponGebyarBuktiDto> = try {
        val response = api.simpanBukti(
            KuponGebyarBuktiBody(
                kodeRekanan = kodeRekanan,
                buktiUrl = buktiUrl,
                catatan = catatan?.trim().orEmpty(),
            ),
        )
        val data = response.body()?.data
        if (response.isSuccessful && data != null) AuthResult.Success(data)
        else parseError(response, "Gagal menyimpan bukti undangan")
    } catch (e: Exception) {
        AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
    }

    /**
     * `errors[0]` diutamakan (pola `AcInstallRepository`) — TAPI **409 dari
     * `/bukti` mengisi `message` dan MENGOSONGKAN `errors`**, dan nama pemegang
     * kuponnya ada di `message` itu. Kedua jalur karena itu wajib dibaca; hanya
     * membaca `errors` akan menampilkan "Gagal menyimpan bukti undangan (409)"
     * untuk keadaan yang sebenarnya punya jawaban jelas ("sudah dikirim oleh
     * Budi"), dan karyawan akan mencoba ulang berkali-kali.
     */
    private fun <T> parseError(response: Response<*>, fallback: String): AuthResult<T> {
        val raw = response.errorBody()?.string()
        val parsed = raw?.let {
            runCatching { errorJson.decodeFromString(ApiErrorResponse.serializer(), it) }.getOrNull()
        }
        val detail = parsed?.errors?.firstOrNull()?.takeIf { it.isNotBlank() }
            ?: parsed?.message?.takeIf { it.isNotBlank() }
        return AuthResult.Failure(
            parsed?.code ?: "http_${response.code()}",
            detail ?: "$fallback (${response.code()})",
        )
    }
}
