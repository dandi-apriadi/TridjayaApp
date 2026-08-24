package com.krisoft.tridjayaelektronik.data

import android.content.ContentResolver
import android.net.Uri
import com.krisoft.tridjayaelektronik.data.model.ApiErrorResponse
import com.krisoft.tridjayaelektronik.data.model.AktivitasPositionDto
import com.krisoft.tridjayaelektronik.ui.aktivitas.PenempatanSaya
import com.krisoft.tridjayaelektronik.data.model.AktivitasItemDto
import com.krisoft.tridjayaelektronik.data.model.AktivitasListData
import com.krisoft.tridjayaelektronik.data.model.ReviewAktivitasBody
import com.krisoft.tridjayaelektronik.data.model.ReviewAktivitasResult
import com.krisoft.tridjayaelektronik.data.model.SubmitAktivitasBody
import com.krisoft.tridjayaelektronik.data.model.SubmitAktivitasItem
import com.krisoft.tridjayaelektronik.data.model.SubmitAktivitasResult
import com.krisoft.tridjayaelektronik.data.remote.AktivitasApi
import com.krisoft.tridjayaelektronik.data.remote.AktivitasUploadApi
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Laporan aktivitas harian (raport) — langsung ke kinerja-service, tanpa cache
 * lokal: jendela pelaporan & status review dihitung server, dan raport basi
 * lebih berbahaya daripada satu panggilan jaringan.
 */
@Singleton
class AktivitasRepository @Inject constructor(
    private val api: AktivitasApi,
    private val uploadApi: AktivitasUploadApi,
) {
    private val errorJson = Json { ignoreUnknownKeys = true }

    /** Master aktivitas semua posisi — pemilihan posisi milik user dilakukan UI. */
    suspend fun aktivitasPositions(): AuthResult<List<AktivitasPositionDto>> = try {
        val response = api.divisions()
        val data = response.body()?.data
        if (response.isSuccessful && data != null) AuthResult.Success(data.divisions)
        else parseError(response, "Gagal memuat master aktivitas")
    } catch (e: Exception) {
        AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
    }

    /**
     * Penempatan KPI orang yang sedang login — sumber daftar aktivitas yang
     * BENAR sejak gerbang absen pulang & KPI pindah ke penempatan (2026-08-18).
     *
     * **Mengembalikan `PenempatanSaya`, bukan `String?`, dan itu disengaja.**
     * Ada TIGA keadaan yang menuntut perilaku berbeda, dan `String?` cuma bisa
     * membawa dua. Lihat KDoc `PenempatanSaya` di `ui/aktivitas/AktivitasPlan.kt`.
     *
     * **Kegagalan dipetakan ke [PenempatanSaya.TidakAda], BUKAN dilempar.**
     * Arahnya sengaja: tanpa penempatan, layar jatuh ke jalur TAG — persis
     * perilaku sebelum perbaikan ini. Jadi endpoint yang sekejap gagal tak
     * memperkenalkan keadaan baru, dan yang paling penting: TIDAK membuat
     * orang kehilangan seluruh kotak isiannya. Sama dengan penanganan web.
     */
    // `internal`, bukan publik: ia mengembalikan `PenempatanSaya` yang juga
    // internal. Menaikkan TIPE-nya jadi publik demi fungsi ini akan membocorkan
    // model tri-state ke luar modul tanpa ada yang membutuhkannya — pemanggilnya
    // cuma ViewModel di modul `app`.
    internal suspend fun penempatanSaya(): PenempatanSaya = try {
        val response = api.penempatanSaya()
        val data = response.body()?.data
        if (response.isSuccessful && data != null) {
            data.penempatanId?.takeIf { it.isNotBlank() }
                ?.let { PenempatanSaya.Ada(it) }
                ?: PenempatanSaya.TidakAda
        } else {
            PenempatanSaya.TidakAda
        }
    } catch (e: Exception) {
        PenempatanSaya.TidakAda
    }

    /**
     * Aktivitas yang sudah terkirim pada [tanggal] (`YYYY-MM-DD`) milik
     * [karyawanId]. Selain dikirim sebagai filter server, hasilnya disaring
     * ULANG di sini: `list_raport` hanya memaksa scope diri sendiri saat role
     * PRIMARY user = `karyawan`, jadi user multi-role (mis. primary `sales` +
     * extra `karyawan`) bisa menerima baris karyawan lain — dan angka "sudah
     * berapa aktivitas hari ini" di layar Activity akan ikut salah.
     */
    suspend fun raportOfDay(tanggal: String, karyawanId: String?): AuthResult<List<AktivitasItemDto>> = try {
        val response = api.list(tanggal = tanggal, karyawanId = karyawanId?.takeIf { it.isNotBlank() })
        val data = response.body()?.data
        if (response.isSuccessful && data != null) {
            val items = if (karyawanId.isNullOrBlank()) data.items
            else data.items.filter { it.employeeId.isBlank() || it.employeeId == karyawanId }
            AuthResult.Success(items)
        } else parseError(response, "Gagal memuat laporan hari ini")
    } catch (e: Exception) {
        AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
    }

    /**
     * Antrian PIC: SELURUH karyawan pada [tanggal], bukan cuma milik sendiri.
     *
     * Sengaja TIDAK memakai [raportOfDay]: fungsi itu menyaring ulang ke satu
     * `karyawanId`, yang untuk PIC berarti daftar kosong. Yang dikembalikan
     * seluruh `AktivitasListData` (bukan `items` saja) karena badge antrian harus
     * memakai `total` — `items` dipotong server ke `limit`.
     */
    suspend fun antrianReview(
        tanggal: String,
        status: String = "pending",
        cari: String? = null,
        limit: Int = 200,
    ): AuthResult<AktivitasListData> = try {
        val response = api.list(
            tanggal = tanggal,
            karyawanId = null,
            limit = limit,
            status = status,
            q = cari?.trim()?.takeIf { it.isNotBlank() },
        )
        val data = response.body()?.data
        if (response.isSuccessful && data != null) AuthResult.Success(data)
        else parseError(response, "Gagal memuat antrian penilaian")
    } catch (e: Exception) {
        AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
    }

    /**
     * Baris yang MASIH menunggu penilaian sepanjang rentang [dari]..[sampai]
     * (inklusif, `yyyy-MM-dd`) — bahan lencana angka per hari di layar PIC.
     *
     * `tanggal` SENGAJA tidak dikirim: server memenangkannya atas rentang, jadi
     * mengirim keduanya membuat lencana tujuh hari melaporkan satu hari yang
     * sama tujuh kali (cerminan `doRefresh` di `PicAktivitasDashboardPage.tsx`,
     * yang juga memuat rentangnya sekali lalu menghitung per hari di klien).
     *
     * Satu panggilan, bukan tujuh: tujuh permintaan berurutan di jaringan
     * lapangan berarti tujuh peluang gagal untuk satu papan angka, dan
     * kegagalan sebagian akan menampilkan lencana yang saling bertentangan.
     *
     * [limit] mengikuti plafon server (2.000 setelah clamp). Pemotongan TIDAK
     * disembunyikan — pemanggil membandingkan `total` dengan `items.size`
     * (lihat `hitungPendingPerHari`).
     */
    suspend fun antrianPendingRentang(
        dari: String,
        sampai: String,
        limit: Int = 2000,
    ): AuthResult<AktivitasListData> = try {
        val response = api.list(
            tanggal = null,
            tanggalFrom = dari,
            tanggalTo = sampai,
            karyawanId = null,
            limit = limit,
            status = "pending",
        )
        val data = response.body()?.data
        if (response.isSuccessful && data != null) AuthResult.Success(data)
        else parseError(response, "Gagal memuat ringkasan antrian")
    } catch (e: Exception) {
        AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
    }

    /**
     * Putusan PIC atas satu baris. [skor] boleh `null` — server mengisi sendiri
     * (`rejected` → 0, selainnya 100). Tolak WAJIB ber-[komentar]: itu satu-
     * satunya cara karyawan tahu apa yang harus diperbaiki.
     */
    suspend fun review(
        id: String,
        status: String,
        skor: Int? = null,
        komentar: String? = null,
    ): AuthResult<ReviewAktivitasResult> = try {
        val response = api.review(
            id = id,
            body = ReviewAktivitasBody(
                status = status,
                score = skor,
                comment = komentar?.trim()?.takeIf { it.isNotBlank() },
            ),
        )
        val data = response.body()?.data
        if (response.isSuccessful && data != null) AuthResult.Success(data)
        else parseError(response, "Gagal menyimpan penilaian")
    } catch (e: Exception) {
        AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
    }

    /**
     * Upload bukti GAMBAR → URL relatif untuk dikirim di [submitItem].
     *
     * Selalu `image/jpeg`: keluaran `PhotoWatermark.prepareWatermarkedJpeg`
     * memang selalu JPEG apa pun format sumbernya, termasuk PNG/WEBP yang
     * dipilih dari galeri. Server memvalidasi ekstensi × MIME × magic bytes
     * serentak, jadi [filename] wajib berakhiran `.jpg`.
     */
    suspend fun uploadEvidence(bytes: ByteArray, filename: String): AuthResult<String> = try {
        val part = MultipartBody.Part.createFormData(
            "file", filename, bytes.toRequestBody("image/jpeg".toMediaType())
        )
        val response = uploadApi.uploadEvidence(part)
        val data = response.body()?.data
        if (response.isSuccessful && data != null && data.url.isNotBlank()) AuthResult.Success(data.url)
        else parseError(response, "Gagal mengunggah bukti")
    } catch (e: Exception) {
        AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
    }

    /**
     * Upload bukti VIDEO → URL relatif.
     *
     * STREAMING dari [ContentResolver] lewat [UriRequestBody], tidak pernah
     * lewat `ByteArray`: batas server 30 MB sedangkan heap HP lapangan bisa
     * <128 MB, jadi `readBytes()` di sini = `OutOfMemoryError` di HP yang
     * justru paling sering dipakai.
     *
     * [mimeType] dan ekstensi pada [namaFile] WAJIB sepasang — server memeriksa
     * keduanya bersama magic bytes, dan pasangan yang meleset ditolak 400
     * SETELAH seluruh berkas terkirim. Pakai `ekstensiVideo`/`mimeVideo`
     * (`ui/raport/AktivitasBuktiPlan.kt`), jangan menebak sendiri.
     *
     * [ukuranBytes] hanya untuk header `Content-Length`; `0` = biarkan OkHttp
     * mengirim chunked (kolom `SIZE` tak selalu terbaca dari penyedia galeri).
     */
    suspend fun uploadEvidenceVideo(
        resolver: ContentResolver,
        uri: Uri,
        namaFile: String,
        mimeType: String,
        ukuranBytes: Long = 0L,
    ): AuthResult<String> = try {
        val body = UriRequestBody(resolver, uri, mimeType.toMediaTypeOrNull(), ukuranBytes)
        val part = MultipartBody.Part.createFormData("file", namaFile, body)
        val response = uploadApi.uploadEvidence(part)
        val data = response.body()?.data
        if (response.isSuccessful && data != null && data.url.isNotBlank()) AuthResult.Success(data.url)
        else parseError(response, "Gagal mengunggah video bukti")
    } catch (e: Exception) {
        AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
    }

    /**
     * Kirim SATU aktivitas. Server-nya upsert per (karyawan, tanggal,
     * `jobdeskIndex`) — mengirim ulang aktivitas yang sama menimpa bukti lama
     * dan mengembalikan statusnya ke `pending` (persis perilaku web).
     *
     * Nama field kiriman tetap `jobdeskIndex`/`jobdeskText`: itu nama DI KABEL
     * (repo ini nol `@SerialName`), bukan istilah layar.
     */
    suspend fun submitItem(
        aktivitasIndex: Int,
        aktivitasText: String,
        mode: String,
        evidenceUrl: String? = null,
        employeeNote: String? = null,
    ): AuthResult<SubmitAktivitasResult> = try {
        val response = api.submit(
            SubmitAktivitasBody(
                items = listOf(
                    SubmitAktivitasItem(
                        jobdeskIndex = aktivitasIndex,
                        jobdeskText = aktivitasText,
                        mode = mode,
                        evidenceUrl = evidenceUrl,
                        employeeNote = employeeNote,
                    )
                )
            )
        )
        val data = response.body()?.data
        if (response.isSuccessful && data != null) AuthResult.Success(data)
        else parseError(response, "Gagal menyimpan laporan")
    } catch (e: Exception) {
        AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
    }

    /**
     * Beda dari repository lain di app ini: pesan `errors[0]` dipakai LEBIH
     * DULU. `ApiError::Validation` selalu ber-`message` generik "Input tidak
     * valid" — detail sebenarnya ("Laporan aktivitas harian sedang tutup. Jam
     * bukanya 08:00 - 22:00 WIB (waktu Jakarta). …", "alasan wajib minimal 10
     * karakter", dst) hanya ada di `errors`, dan tanpa itu user tak tahu harus
     * berbuat apa.
     *
     * Kutipan di atas disegarkan 2026-08-15: kalimat servernya dulu menulis
     * "WITA" padahal gerbangnya dihitung WIB (`raport/service.rs`
     * `ensure_window_open` → `chrono::Local`, zona VPS `Asia/Jakarta`), jadi 12
     * karyawan cabang Manado membaca jam yang meleset 1 jam. Kalimat barunya
     * sampai ke HP TANPA rilis APK — persis karena `errors[0]` dipakai apa
     * adanya di sini.
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
