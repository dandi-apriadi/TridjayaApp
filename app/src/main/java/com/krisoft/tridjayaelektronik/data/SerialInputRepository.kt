package com.krisoft.tridjayaelektronik.data

import com.krisoft.tridjayaelektronik.data.model.ApiErrorResponse
import com.krisoft.tridjayaelektronik.data.model.CreateSerialNumbersBody
import com.krisoft.tridjayaelektronik.data.model.CreateSerialRequestBody
import com.krisoft.tridjayaelektronik.data.model.GenerateSerialBody
import com.krisoft.tridjayaelektronik.data.model.MutasiContextDto
import com.krisoft.tridjayaelektronik.data.model.SerialCoverageData
import com.krisoft.tridjayaelektronik.data.model.SerialCreateResultDto
import com.krisoft.tridjayaelektronik.data.model.SerialRegistryRow
import com.krisoft.tridjayaelektronik.data.model.SerialKondisiLogData
import com.krisoft.tridjayaelektronik.data.model.SetKondisiBody
import com.krisoft.tridjayaelektronik.data.model.SetKondisiResultDto
import com.krisoft.tridjayaelektronik.data.model.SerialRequestDto
import com.krisoft.tridjayaelektronik.data.model.StokCabangRow
import com.krisoft.tridjayaelektronik.data.remote.DeliveryFlowApi
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

private const val STOK_FETCH_LIMIT = 5000

/**
 * Input Serial Number (admin-stok) — reuse [DeliveryFlowApi] (endpoint mutasi/stok-cabang/
 * serial-numbers sudah ada di sana utk picker SPK) daripada bikin Retrofit interface baru.
 * Tanpa cache lokal — data harus real-time (stok GS + registry SN berubah cepat).
 */
@Singleton
class SerialInputRepository @Inject constructor(
    private val api: DeliveryFlowApi
) {
    private val errorJson = Json { ignoreUnknownKeys = true }

    /** Dealer akun login (admin-stok terikat satu cabang). */
    suspend fun context(): AuthResult<MutasiContextDto> = try {
        val response = api.mutasiContext()
        val data = response.body()?.data
        if (response.isSuccessful && data != null) AuthResult.Success(data)
        else parseError(response, "Gagal memuat konteks cabang")
    } catch (e: Exception) {
        AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
    }

    /**
     * Produk BERSTOK di cabang (search kosong = tanpa filter teks server; UI
     * filter client-side). Filter `Stok > 0` datang dari default `inStock` di
     * [DeliveryFlowApi.stokCabang] — tanpa itu katalog GS penuh (~5.500 baris
     * per cabang) MELEBIHI [STOK_FETCH_LIMIT] dan daftar produknya terpotong
     * senyap. SN memang cuma diinput untuk unit yang fisiknya ada di gudang.
     */
    suspend fun stokCabang(kodeDealer: String): AuthResult<List<StokCabangRow>> = try {
        val response = api.stokCabang(search = "", kodeDealer = kodeDealer, limit = STOK_FETCH_LIMIT)
        val data = response.body()?.data
        if (response.isSuccessful && data != null) AuthResult.Success(data.items)
        else parseError(response, "Gagal memuat stok cabang")
    } catch (e: Exception) {
        AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
    }

    /**
     * Cakupan SN seluruh produk satu cabang — bahan badge "SN 3/5" + filter
     * "belum lengkap". Dipanggil SEKALI per cabang, bukan per produk: alur
     * kerjanya menetapkan SN ke SEMUA produk, jadi yang dibutuhkan pertama
     * adalah peta gudang, bukan hitungan satu barang.
     */
    suspend fun serialCoverage(kodeDealer: String): AuthResult<SerialCoverageData> = try {
        val response = api.serialCoverage(kodeDealer = kodeDealer)
        val data = response.body()?.data
        if (response.isSuccessful && data != null) AuthResult.Success(data)
        else parseError(response, "Gagal memuat cakupan serial number")
    } catch (e: Exception) {
        AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
    }

    /**
     * SN yang sudah tercatat untuk satu produk — BARISNYA, bukan cuma
     * jumlahnya: petugas yang men-scan unit yang ternyata sudah terdaftar harus
     * diberi tahu SAAT ITU JUGA, bukan sesudah menekan simpan lewat daftar
     * `skipped`. Sebagian besar barang di gudang sudah bernomor pabrik dan
     * belum terdata, jadi "sudah/belum terdaftar" adalah pertanyaan yang
     * ditanyakan puluhan kali per produk.
     *
     * `onlySerial=false`/`excludeAssigned=false` = SEMUA baris. Tag leasing ikut
     * SENGAJA: kunci unik registry `(dealer, barang, serial)` tak peduli
     * `is_serial`, jadi serial yang bentrok dengan tag leasing pun akan ditolak
     * server — menyembunyikannya di sini membuat penolakannya tak bisa dijelaskan.
     *
     * **Server memotong di 500 baris** (`DEFAULT_LIMIT`) dan app tak mengirim
     * `limit`. Untuk produk dengan >500 baris, deteksi "sudah terdaftar" jadi
     * TIDAK lengkap — itu fail-open yang disengaja: server tetap menolak
     * duplikat saat simpan dan melaporkannya di `skipped`. Yang tak boleh
     * diturunkan dari daftar ini adalah HITUNGAN cakupan; itu diambil dari
     * [serialCoverage] yang dihitung server tanpa batas.
     */
    suspend fun existingSerials(kodeDealer: String, kodeBarang: String): AuthResult<List<SerialRegistryRow>> = try {
        val response = api.serialNumbers(
            kodeDealer = kodeDealer,
            kodeBarang = kodeBarang,
            onlySerial = false,
            excludeAssigned = false
        )
        val data = response.body()?.data
        if (response.isSuccessful && data != null) AuthResult.Success(data.items)
        else parseError(response, "Gagal memuat serial tercatat")
    } catch (e: Exception) {
        AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
    }

    suspend fun createSerialNumbers(
        kodeDealer: String,
        kodeBarang: String,
        namaBarang: String?,
        serialNumbers: List<String>
    ): AuthResult<SerialCreateResultDto> = try {
        val response = api.createSerialNumbers(
            CreateSerialNumbersBody(
                kodeDealer = kodeDealer,
                kodeBarang = kodeBarang,
                namaBarang = namaBarang,
                serialNumbers = serialNumbers
            )
        )
        val data = response.body()?.data
        if (response.isSuccessful && data != null) AuthResult.Success(data)
        else parseError(response, "Gagal menyimpan serial number")
    } catch (e: Exception) {
        AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
    }

    /**
     * Vonis kondisi atas unit yang SUDAH terdaftar. Endpoint TERPISAH dari
     * pendaftaran (`createSerialNumbers` tak menerima `kondisi`), jadi menetapkan
     * kondisi selalu berarti panggilan kedua — dan kegagalannya TIDAK membatalkan
     * pendaftaran yang sudah berhasil.
     *
     * Satu panggilan = satu nilai kondisi untuk sekumpulan serial. Kondisi
     * berbeda wajib jadi panggilan berbeda — lihat [com.krisoft.tridjayaelektronik.ui.serials.kelompokkanKondisi].
     */
    suspend fun setKondisi(
        kodeDealer: String,
        kodeBarang: String,
        serialNumbers: List<String>,
        kondisi: String,
        keterangan: String?
    ): AuthResult<SetKondisiResultDto> = try {
        val response = api.setSerialKondisi(
            SetKondisiBody(
                kodeDealer = kodeDealer,
                kodeBarang = kodeBarang,
                serialNumbers = serialNumbers,
                kondisi = kondisi,
                keterangan = keterangan?.takeIf { it.isNotBlank() }
            )
        )
        val data = response.body()?.data
        if (response.isSuccessful && data != null) AuthResult.Success(data)
        else parseError(response, "Gagal menyimpan kondisi unit")
    } catch (e: Exception) {
        AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
    }

    /**
     * Riwayat perubahan kondisi satu unit. Registry hanya menyimpan keadaan
     * TERAKHIR; tanpa riwayat, vonis yang ditimpa orang lain hilang tanpa jejak
     * — padahal vonis kondisi menahan barang dari penjualan.
     *
     * Fail-soft di pemanggil: gagal memuat riwayat TIDAK boleh memblokir
     * penyuntingan kondisi, karena riwayat itu alat baca, bukan syarat tulis.
     */
    suspend fun kondisiLog(
        kodeDealer: String,
        kodeBarang: String?,
        serialNumber: String?,
        limit: Int? = null
    ): AuthResult<SerialKondisiLogData> = try {
        val response = api.serialKondisiLog(
            kodeDealer = kodeDealer,
            kodeBarang = kodeBarang?.takeIf { it.isNotBlank() },
            serialNumber = serialNumber?.takeIf { it.isNotBlank() },
            limit = limit
        )
        val data = response.body()?.data
        if (response.isSuccessful && data != null) AuthResult.Success(data)
        else parseError(response, "Gagal memuat riwayat kondisi")
    } catch (e: Exception) {
        AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
    }

    /** Foto bukti usulan (JPEG sudah dikompres pemanggil) → URL relatif. */
    suspend fun uploadPhoto(bytes: ByteArray, filename: String): AuthResult<String> = try {
        val part = MultipartBody.Part.createFormData("file", filename, bytes.toRequestBody("image/webp".toMediaType()))
        val response = api.uploadSerialPhoto(part)
        val data = response.body()?.data
        if (response.isSuccessful && data != null && data.url.isNotBlank()) AuthResult.Success(data.url)
        else parseError(response, "Gagal mengunggah foto")
    } catch (e: Exception) {
        AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
    }

    /**
     * Usulkan pendaftaran SN dari cabang. Serial dinormalkan di sini dengan
     * [normalizeSerial] yang SAMA dengan scan opname — aturan berbeda sedikit
     * saja berarti usulan lolos untuk serial yang server tolak, atau usulan
     * ganda untuk serial yang server anggap sama.
     */
    suspend fun proposeSerial(
        kodeDealer: String,
        kodeBarang: String,
        namaBarang: String?,
        serialNumberRaw: String,
        fotoSnUrl: String,
        fotoBarangUrl: String,
        opnameSessionId: String?,
        catatan: String?
    ): AuthResult<SerialRequestDto> {
        val serial = normalizeSerial(serialNumberRaw)
            ?: return AuthResult.Failure(
                "validation",
                "Serial kosong atau lebih dari $SERIAL_MAX_LENGTH karakter"
            )
        return try {
            val response = api.createSerialRequest(
                CreateSerialRequestBody(
                    kodeDealer = kodeDealer,
                    kodeBarang = kodeBarang,
                    namaBarang = namaBarang,
                    serialNumber = serial,
                    fotoSnUrl = fotoSnUrl,
                    fotoBarangUrl = fotoBarangUrl,
                    opnameSessionId = opnameSessionId,
                    catatan = catatan
                )
            )
            val data = response.body()?.data
            if (response.isSuccessful && data != null) AuthResult.Success(data)
            else parseError(response, "Gagal mengirim usulan SN")
        } catch (e: Exception) {
            AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
        }
    }

    /** Antrian usulan cabang. Server yang men-scope cabangnya untuk pengusul. */
    suspend fun serialRequests(kodeDealer: String?): AuthResult<List<SerialRequestDto>> = try {
        val response = api.serialRequests(kodeDealer = kodeDealer)
        val data = response.body()?.data
        if (response.isSuccessful && data != null) AuthResult.Success(data.items)
        else parseError(response, "Gagal memuat usulan SN")
    } catch (e: Exception) {
        AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
    }

    /**
     * Buat kode pengganti SN. Backend LANGSUNG menulis registry — memanggil ini
     * dua kali berarti dua set kode nyata untuk barang yang sama, bukan sekadar
     * pratinjau yang bisa dibuang.
     */
    suspend fun generateSerials(
        kodeDealer: String,
        kodeBarang: String,
        namaBarang: String?,
        jumlah: Int
    ): AuthResult<List<String>> = try {
        val response = api.generateSerials(
            GenerateSerialBody(
                kodeDealer = kodeDealer,
                kodeBarang = kodeBarang,
                namaBarang = namaBarang,
                jumlah = jumlah
            )
        )
        val data = response.body()?.data
        if (response.isSuccessful && data != null) AuthResult.Success(data.generated)
        else parseError(response, "Gagal membuat kode serial")
    } catch (e: Exception) {
        AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
    }

    private fun <T> parseError(response: Response<*>, fallback: String): AuthResult<T> {
        val raw = response.errorBody()?.string()
        val parsed = raw?.let {
            runCatching { errorJson.decodeFromString(ApiErrorResponse.serializer(), it) }.getOrNull()
        }
        return AuthResult.Failure(
            parsed?.code ?: "http_${response.code()}",
            parsed?.message ?: "$fallback (${response.code()})"
        )
    }
}
