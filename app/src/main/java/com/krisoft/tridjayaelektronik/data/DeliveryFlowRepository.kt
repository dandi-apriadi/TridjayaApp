package com.krisoft.tridjayaelektronik.data

import com.krisoft.tridjayaelektronik.data.model.SerialRegistryRow
import com.krisoft.tridjayaelektronik.data.model.ApiErrorResponse
import com.krisoft.tridjayaelektronik.data.model.AssignBody
import com.krisoft.tridjayaelektronik.data.model.ConfirmSpkBody
import com.krisoft.tridjayaelektronik.data.model.SetoranKasirBody
import com.krisoft.tridjayaelektronik.data.model.CreateDeliveryBody
import com.krisoft.tridjayaelektronik.data.model.DeliverBody
import com.krisoft.tridjayaelektronik.data.model.DeliveryContextDto
import com.krisoft.tridjayaelektronik.data.model.DeliveryCreateResult
import com.krisoft.tridjayaelektronik.data.model.DeliveryJobDto
import com.krisoft.tridjayaelektronik.data.model.DeliveryListData
import com.krisoft.tridjayaelektronik.data.model.SaringanAntrian
import com.krisoft.tridjayaelektronik.data.model.DeliveryNoteBody
import com.krisoft.tridjayaelektronik.data.model.PdiBody
import com.krisoft.tridjayaelektronik.data.model.KontributorDto
import com.krisoft.tridjayaelektronik.data.model.PetugasDirektoriDto
import com.krisoft.tridjayaelektronik.data.model.ReorderBody
import com.krisoft.tridjayaelektronik.data.model.SpkEditResultDto
import com.krisoft.tridjayaelektronik.data.local.DashboardCacheDao
import com.krisoft.tridjayaelektronik.data.local.DashboardCacheEntity
import com.krisoft.tridjayaelektronik.data.remote.DeliveryFlowApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Alur pengiriman SPK → antar, langsung ke backend `inventory-service` via [DeliveryFlowApi].
 * Tanpa cache lokal — data harus real-time (antrian per-tahap berpindah cepat, RBAC di server).
 * Setiap aksi tahap mengembalikan [DeliveryJobDto] terbaru dari server.
 *
 * SATU pengecualian: [petugas] (direktori + panduan alur) disalin ke
 * [DashboardCacheDao] karena isinya nyaris statis dan justru paling dibutuhkan
 * saat sinyal hilang di lapangan. Jangan jadikan ini preseden untuk antrian.
 */
@Singleton
class DeliveryFlowRepository @Inject constructor(
    private val api: DeliveryFlowApi,
    private val cacheDao: DashboardCacheDao,
) {
    private val errorJson = Json { ignoreUnknownKeys = true }

    /**
     * `limit = 200` — plafon backend (`list_delivery`, inventory-service
     * `delivery.rs`, clamp `.clamp(1, 200)`).
     *
     * KOREKSI 2026-08-23: komentar lama di sini menyatakan "Respons cuma balas
     * `{items, page, limit}` — TIDAK ada field total/count keseluruhan
     * (diverifikasi langsung di handler backend)". **Itu sudah SALAH.** Handler
     * mengirim `total` (baris yang lolos seluruh saringan SEBELUM `LIMIT`), dan
     * komentar servernya menyebut alasannya: "halaman yang menampilkan 200 dari
     * 431 diam saja". Yang membuang field itu adalah `DeliveryListData` kita
     * sendiri yang tak punya properti `total`, jadi kotlinx menelannya lewat
     * `ignoreUnknownKeys` — nol error, nol gejala.
     *
     * Kalimat lama itu bukan sekadar tidak akurat: ia sudah dipakai sebagai
     * dasar keputusan ("badge mentok di 200, naikkan limit saja"). Pakai
     * [listDenganTotal] kalau kamu butuh tahu daftarnya terpotong atau tidak.
     */
    suspend fun list(
        status: String? = null,
        view: String? = null,
        asDriver: Boolean = false,
        dari: String? = null,
        sampai: String? = null,
        saringan: SaringanAntrian = SaringanAntrian.KOSONG,
    ): AuthResult<List<DeliveryJobDto>> =
        when (val r = listDenganTotal(status, view, asDriver, dari, sampai, saringan)) {
            is AuthResult.Success -> AuthResult.Success(r.data.items)
            is AuthResult.Failure -> AuthResult.Failure(r.code, r.message)
        }

    /**
     * Sama dengan [list] tapi mengembalikan amplop utuh, jadi pemanggil bisa
     * membaca `total` dan tahu daftarnya terpotong di 200 atau tidak.
     *
     * Parameter saringan diteruskan ke server (`ListQuery` sudah menerima
     * semuanya — nol perubahan Rust). Nilai kosong DIBUANG di sini, bukan di
     * layar: Retrofit membuang `@Query` null tapi tetap mengirim string kosong
     * sebagai `?q=`.
     */
    suspend fun listDenganTotal(
        status: String? = null,
        view: String? = null,
        asDriver: Boolean = false,
        dari: String? = null,
        sampai: String? = null,
        saringan: SaringanAntrian = SaringanAntrian.KOSONG,
    ): AuthResult<DeliveryListData> = try {
        val response = api.list(
            status = status,
            view = view,
            limit = 200,
            asDriver = asDriver.takeIf { it },
            dari = dari,
            sampai = sampai,
            q = saringan.q.bersih(),
            kodeDealer = saringan.kodeDealer.bersih(),
            urut = saringan.urut.bersih(),
            deliveryMethod = saringan.deliveryMethod.bersih(),
        )
        val data = response.body()?.data
        if (response.isSuccessful && data != null) AuthResult.Success(data)
        else parseError(response, "Gagal memuat daftar pengiriman")
    } catch (e: Exception) {
        AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
    }

    private fun String?.bersih(): String? = this?.trim()?.takeIf { it.isNotBlank() }

    suspend fun detail(id: String): AuthResult<DeliveryJobDto> = call("Gagal memuat detail") { api.detail(id) }

    /**
     * Direktori petugas + panduan alur. Sukses = salinannya ditulis ke cache;
     * kegagalan TIDAK menyentuh cache (salinan lama tetap dipakai [cachedPetugas]).
     */
    suspend fun petugas(): AuthResult<PetugasDirektoriDto> = try {
        val response = api.petugas()
        val data = response.body()?.data
        if (response.isSuccessful && data != null) {
            runCatching {
                cacheDao.upsert(
                    DashboardCacheEntity(
                        key = DashboardCacheEntity.KEY_DELIVERY_PETUGAS,
                        jsonPayload = errorJson.encodeToString(PetugasDirektoriDto.serializer(), data),
                        cachedAtMillis = System.currentTimeMillis(),
                    )
                )
            }
            AuthResult.Success(data)
        } else parseError(response, "Gagal memuat direktori petugas")
    } catch (e: Exception) {
        AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
    }

    /** Salinan terakhir yang pernah sukses, atau null kalau layar ini belum pernah dibuka online. */
    suspend fun cachedPetugas(): PetugasDirektoriDto? {
        val row = runCatching { cacheDao.get(DashboardCacheEntity.KEY_DELIVERY_PETUGAS) }.getOrNull()
            ?: return null
        return runCatching {
            errorJson.decodeFromString(PetugasDirektoriDto.serializer(), row.jsonPayload)
        }.getOrNull()
    }

    /**
     * Karyawan yang sudah menangani unit ini. TIDAK di-cache: isinya berubah
     * tiap kali seseorang menyentuh unit, dan daftar basi di layar justru
     * menyesatkan orang yang sedang mencari "siapa yang pegang unit ini
     * sekarang" — beda dengan direktori petugas yang nyaris statis.
     */
    suspend fun kontributor(id: String): AuthResult<List<KontributorDto>> = try {
        val response = api.kontributor(id)
        val data = response.body()?.data
        if (response.isSuccessful && data != null) AuthResult.Success(data)
        else parseError(response, "Gagal memuat kontributor SPK")
    } catch (e: Exception) {
        AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
    }

    suspend fun context(): AuthResult<DeliveryContextDto> = try {
        val response = api.context()
        val data = response.body()?.data
        if (response.isSuccessful && data != null) AuthResult.Success(data)
        else parseError(response, "Gagal memuat konteks cabang")
    } catch (e: Exception) {
        AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
    }

    suspend fun create(body: CreateDeliveryBody): AuthResult<DeliveryCreateResult> = try {
        val response = api.create(body)
        val data = response.body()?.data
        if (response.isSuccessful && data != null) AuthResult.Success(data)
        else parseError(response, "Gagal membuat SPK")
    } catch (e: Exception) {
        AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
    }

    /** Sunting isi SPK (administrator) — patch parsial, lihat
     *  `SpkEditFields.buildSpkEditPatch`. Balasan membawa job terbaru SEKALIGUS
     *  jumlah unit yang ikut menerima perubahan data konsumen. */
    suspend fun editJob(id: String, patch: JsonObject): AuthResult<SpkEditResultDto> =
        call("Gagal menyimpan perubahan SPK") { api.editJob(id, patch) }

    /** 111: ambil klaim PDI. Pesan 409 dari server memuat nama pemegangnya —
     *  [parseError] meneruskannya apa adanya, jangan dikarang ulang di UI. */
    suspend fun claimPdi(id: String): AuthResult<DeliveryJobDto> =
        call("Gagal mengambil PDI") { api.claimPdi(id) }

    suspend fun releasePdiClaim(id: String): AuthResult<DeliveryJobDto> =
        call("Gagal melepas klaim PDI") { api.releasePdiClaim(id) }

    suspend fun submitPdi(id: String, body: PdiBody): AuthResult<DeliveryJobDto> =
        call("Gagal simpan PDI") { api.submitPdi(id, body) }

    /** PDI massal barang kecil se-SPK — [id] harus unit KECIL, lihat `submitPdiKecil` di API. */
    suspend fun submitPdiKecil(id: String): AuthResult<DeliveryJobDto> =
        call("Gagal menyelesaikan PDI barang kecil") { api.submitPdiKecil(id) }

    suspend fun confirmSpk(id: String, body: ConfirmSpkBody): AuthResult<DeliveryJobDto> =
        call("Gagal konfirmasi SPK") { api.confirmSpk(id, body) }

    suspend fun issueDeliveryNote(id: String, body: DeliveryNoteBody): AuthResult<DeliveryJobDto> =
        call("Gagal terbitkan surat jalan") { api.issueDeliveryNote(id, body) }

    suspend fun setoranKasir(id: String, body: SetoranKasirBody): AuthResult<DeliveryJobDto> =
        call("Gagal konfirmasi pembayaran") { api.setoranKasir(id, body) }

    suspend fun assign(id: String, body: AssignBody): AuthResult<DeliveryJobDto> =
        call("Gagal assign driver") { api.assign(id, body) }

    suspend fun dispatch(id: String): AuthResult<DeliveryJobDto> =
        call("Gagal berangkat") { api.dispatch(id) }

    suspend fun deliver(id: String, body: DeliverBody): AuthResult<DeliveryJobDto> =
        call("Gagal tandai terkirim") { api.deliver(id, body) }

    suspend fun cancel(id: String, reason: String): AuthResult<DeliveryJobDto> =
        call("Gagal membatalkan") { api.cancel(id, reason) }

    /** (2026-07-24) Tandai job `self_pickup` selesai — foto+rating wajib. */
    suspend fun selfPickupComplete(
        id: String,
        body: com.krisoft.tridjayaelektronik.data.model.SelfPickupCompleteBody
    ): AuthResult<DeliveryJobDto> = call("Gagal menandai diambil sendiri") { api.selfPickupComplete(id, body) }

    suspend fun checklist(kategori: String, stage: String? = null): AuthResult<List<com.krisoft.tridjayaelektronik.data.model.ChecklistItemDto>> = try {
        val response = api.checklist(kategori, stage)
        val data = response.body()?.data
        if (response.isSuccessful && data != null) AuthResult.Success(data.items.filter { it.aktif })
        else parseError(response, "Gagal memuat checklist PDI")
    } catch (e: Exception) {
        AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
    }

    /** 088: tandai sudah chat konsumen (H-1). */
    suspend fun chatConsumer(id: String): AuthResult<DeliveryJobDto> =
        call("Gagal mencatat chat konsumen") { api.chatConsumer(id) }

    /** Simpan urutan muatan driver; abaikan body sukses, hanya status. */
    suspend fun reorderLoads(ids: List<String>): AuthResult<Unit> = try {
        val response = api.reorderLoads(ReorderBody(ids))
        if (response.isSuccessful) AuthResult.Success(Unit)
        else parseError(response, "Gagal menyimpan urutan muatan")
    } catch (e: Exception) {
        AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
    }

    /** Autocomplete barang Input SPK — `search` min. 2 karakter, di-scope `kodeDealer`. */
    suspend fun stokCabang(search: String, kodeDealer: String): AuthResult<List<com.krisoft.tridjayaelektronik.data.model.StokCabangRow>> = try {
        val response = api.stokCabang(search = search, kodeDealer = kodeDealer, includeDipesan = true)
        val data = response.body()?.data
        if (response.isSuccessful && data != null) AuthResult.Success(data.items)
        else parseError(response, "Gagal memuat stok cabang")
    } catch (e: Exception) {
        AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
    }

    suspend fun categories(): AuthResult<List<com.krisoft.tridjayaelektronik.data.model.DeliveryCategoryDto>> = try {
        val response = api.categories()
        val data = response.body()?.data
        if (response.isSuccessful && data != null) AuthResult.Success(data.items)
        else parseError(response, "Gagal memuat kategori PDI")
    } catch (e: Exception) {
        AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
    }

    /** Autocomplete broker KBK (`q` min. 2 char). Fail-soft di caller. */
    suspend fun searchBrokers(q: String): AuthResult<List<com.krisoft.tridjayaelektronik.data.model.BrokerOption>> = try {
        val response = api.brokers(q)
        val data = response.body()?.data
        if (response.isSuccessful && data != null) AuthResult.Success(data.items)
        else parseError(response, "Gagal memuat broker")
    } catch (e: Exception) {
        AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
    }

    suspend fun jobAkiForms(id: String): AuthResult<List<com.krisoft.tridjayaelektronik.data.model.AkiFormDto>> = try {
        val response = api.jobAkiForms(id)
        val data = response.body()?.data
        if (response.isSuccessful && data != null) AuthResult.Success(data.items)
        else parseError(response, "Gagal memuat form pengambilan aki")
    } catch (e: Exception) {
        AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
    }

    /**
     * Serial tersedia utk cabang+barang. Mengembalikan BARIS registry utuh,
     * bukan cuma string serialnya: tiap baris membawa `kondisi` yang dipakai
     * memperingatkan sales sebelum unit repair/retur ikut terjual.
     */
    suspend fun serialNumbers(
        kodeDealer: String,
        kodeBarang: String
    ): AuthResult<List<SerialRegistryRow>> = try {
        val response = api.serialNumbers(kodeDealer = kodeDealer, kodeBarang = kodeBarang)
        val data = response.body()?.data
        if (response.isSuccessful && data != null) {
            AuthResult.Success(data.items.filter { it.serialNumber.isNotBlank() })
        } else parseError(response, "Gagal memuat serial")
    } catch (e: Exception) {
        AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
    }

    suspend fun createAkiForm(id: String, body: com.krisoft.tridjayaelektronik.data.model.CreateAkiFormBody): AuthResult<com.krisoft.tridjayaelektronik.data.model.AkiFormDto> = try {
        val response = api.createAkiForm(id, body)
        val data = response.body()?.data
        if (response.isSuccessful && data != null) AuthResult.Success(data.form)
        else parseError(response, "Gagal simpan form pengambilan aki")
    } catch (e: Exception) {
        AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
    }

    /** Daftar riwayat form aki (menu Pengambilan Aki). */
    suspend fun akiForms(
        dari: String? = null,
        sampai: String? = null,
    ): AuthResult<List<com.krisoft.tridjayaelektronik.data.model.AkiFormDto>> = try {
        val response = api.akiForms(dari = dari, sampai = sampai)
        val data = response.body()?.data
        if (response.isSuccessful && data != null) AuthResult.Success(data.items)
        else parseError(response, "Gagal memuat daftar form pengambilan aki")
    } catch (e: Exception) {
        AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
    }

    suspend fun returnAkiForm(id: String): AuthResult<com.krisoft.tridjayaelektronik.data.model.AkiFormDto> = try {
        val response = api.returnAkiForm(id, com.krisoft.tridjayaelektronik.data.model.ReturnAkiBody())
        val data = response.body()?.data
        if (response.isSuccessful && data != null) AuthResult.Success(data.form)
        else parseError(response, "Gagal menandai aki bekas dikembalikan")
    } catch (e: Exception) {
        AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
    }

    /** Setujui form aki — approval TUNGGAL (redesain 2026-07-24), tanpa body. */
    suspend fun approveAkiForm(id: String): AuthResult<com.krisoft.tridjayaelektronik.data.model.AkiFormDto> = try {
        val response = api.approveAkiForm(id)
        val data = response.body()?.data
        if (response.isSuccessful && data != null) AuthResult.Success(data.form)
        else parseError(response, "Gagal menyetujui form aki")
    } catch (e: Exception) {
        AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
    }

    suspend fun rejectAkiForm(id: String, reason: String): AuthResult<com.krisoft.tridjayaelektronik.data.model.AkiFormDto> = try {
        val response = api.rejectAkiForm(id, com.krisoft.tridjayaelektronik.data.model.RejectAkiBody(reason = reason))
        val data = response.body()?.data
        if (response.isSuccessful && data != null) AuthResult.Success(data.form)
        else parseError(response, "Gagal menolak form aki")
    } catch (e: Exception) {
        AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
    }

    suspend fun drivers(): AuthResult<List<com.krisoft.tridjayaelektronik.data.model.DriverDto>> = try {
        val response = api.users("driver")
        val data = response.body()?.data
        if (response.isSuccessful && data != null) AuthResult.Success(data.items)
        else parseError(response, "Gagal memuat daftar driver")
    } catch (e: Exception) {
        AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
    }

    /**
     * Teknisi PDI untuk dropdown penugasan Home Service (`GET /api/users?role=pdi`).
     *
     * Berbagi endpoint dengan [drivers] tapi TIDAK berbagi kelonggaran gate-nya:
     * gateway hanya melonggarkan `GET /api/users` untuk query PERSIS `role=driver`
     * (`is_users_driver_filter`), jadi permintaan ini dijaga `USERS_READ_ROLES`
     * yang TIDAK memuat `cs` — padahal `cs` justru yang berhak menugaskan.
     * Kegagalan 403 di sini normal untuk akun CS murni; pemanggil menampilkannya
     * sebagai keterangan, bukan menganggap tak ada teknisi (web melakukan hal sama).
     */
    suspend fun teknisiPdi(): AuthResult<List<com.krisoft.tridjayaelektronik.data.model.DriverDto>> = try {
        val response = api.users("pdi")
        val data = response.body()?.data
        if (response.isSuccessful && data != null) AuthResult.Success(data.items)
        else parseError(response, "Gagal memuat daftar teknisi")
    } catch (e: Exception) {
        AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
    }

    /** Riwayat diskon SPK (timeline detail). `baris` null = seluruh baris.
     *  `data` = array langsung. */
    suspend fun discountHistory(
        spkBatchKode: String,
        baris: Int? = null
    ): AuthResult<List<com.krisoft.tridjayaelektronik.data.model.DiscountRequestDto>> = try {
        val response = api.discountHistory(spkBatchKode, baris)
        val data = response.body()?.data
        if (response.isSuccessful && data != null) AuthResult.Success(data)
        else parseError(response, "Gagal memuat riwayat diskon")
    } catch (e: Exception) {
        AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
    }

    /**
     * `data` LENGKAP (`items` + `total`) — BUKAN cuma `items`. Backend
     * `list_discount_requests` default `limit = 20`; badge yang memakai
     * `.items.size` diam-diam terpotong 20 walau pengajuan pending lebih
     * banyak (I2 audit 2026-07-28). Pemanggil yang cuma butuh daftar (bukan
     * total) baca `.items`.
     */
    suspend fun discounts(
        status: String? = "pending",
        dari: String? = null,
        sampai: String? = null,
    ): AuthResult<com.krisoft.tridjayaelektronik.data.model.DiscountListData> = try {
        val response = api.discountRequests(status = status, dari = dari, sampai = sampai)
        val data = response.body()?.data
        if (response.isSuccessful && data != null) AuthResult.Success(data)
        else parseError(response, "Gagal memuat pengajuan diskon")
    } catch (e: Exception) {
        AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
    }

    /** SPK utuh di balik satu pengajuan diskon (kartu approval). */
    suspend fun spkDiscountContext(
        spkBatchKode: String
    ): AuthResult<com.krisoft.tridjayaelektronik.data.model.SpkDiscountContextDto> = try {
        val response = api.spkDiscountContext(spkBatchKode)
        val data = response.body()?.data
        if (response.isSuccessful && data != null) AuthResult.Success(data)
        else parseError(response, "Gagal memuat detail SPK")
    } catch (e: Exception) {
        AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
    }

    /** Ajukan (ulang) diskon satu baris SPK. Pesan 400 server ditampilkan apa
     *  adanya — "Baris ini masih menunggu keputusan diskon" adalah petunjuk
     *  yang berguna, bukan kegagalan teknis yang perlu disamarkan. */
    suspend fun ajukanDiskon(
        body: com.krisoft.tridjayaelektronik.data.model.CreateDiscountBody
    ): AuthResult<com.krisoft.tridjayaelektronik.data.model.DiscountRequestDto> =
        decision("Gagal mengajukan diskon") { api.createDiscountRequest(body) }

    suspend fun approveDiscount(id: String, note: String): AuthResult<com.krisoft.tridjayaelektronik.data.model.DiscountRequestDto> = decision("Gagal menyetujui diskon") {
        api.approveDiscount(id, com.krisoft.tridjayaelektronik.data.model.DecisionBody(note.ifBlank { null }))
    }

    suspend fun rejectDiscount(id: String, note: String): AuthResult<com.krisoft.tridjayaelektronik.data.model.DiscountRequestDto> = decision("Gagal menolak diskon") {
        api.rejectDiscount(id, com.krisoft.tridjayaelektronik.data.model.DecisionBody(note.ifBlank { null }))
    }

    /** Tandai pengajuan `rejected` jadi `dilepas`. Unit se-SPK baru lepas ke
     *  `pending_pdi` setelah SELURUH barangnya tuntas — lihat `DeliveryFlowApi`. */
    suspend fun lanjutTanpaDiskon(id: String): AuthResult<com.krisoft.tridjayaelektronik.data.model.DiscountRequestDto> =
        decision("Gagal melanjutkan tanpa diskon") { api.lanjutTanpaDiskon(id) }

    private inline fun decision(
        fallback: String,
        block: () -> Response<com.krisoft.tridjayaelektronik.data.model.ApiResponse<com.krisoft.tridjayaelektronik.data.model.DiscountRequestDto>>
    ): AuthResult<com.krisoft.tridjayaelektronik.data.model.DiscountRequestDto> = try {
        val response = block()
        val data = response.body()?.data
        if (response.isSuccessful && data != null) AuthResult.Success(data)
        else parseError(response, fallback)
    } catch (e: Exception) {
        AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
    }

    /** Upload foto (JPEG) → URL relatif untuk dikirim di body tahap (PDI/deliver). */
    suspend fun uploadPhoto(bytes: ByteArray, filename: String): AuthResult<String> = try {
        val part = MultipartBody.Part.createFormData("file", filename, bytes.toRequestBody("image/jpeg".toMediaType()))
        val response = api.uploadPhoto(part)
        val data = response.body()?.data
        if (response.isSuccessful && data != null && data.url.isNotBlank()) AuthResult.Success(data.url)
        else parseError(response, "Gagal mengunggah foto")
    } catch (e: Exception) {
        AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
    }

    /** Ambil bytes foto delivery ter-autentikasi. `url` = URL logis
     *  `/uploads/delivery/{file}` dari field job — dipetakan ke endpoint serve
     *  `GET /delivery/photo/{file}` (pola map frontend web). Fail-soft null
     *  (foto tak tampil, layar tetap jalan). */
    suspend fun fetchPhoto(url: String): ByteArray? = try {
        val filename = url.trim().substringAfterLast('/')
        if (filename.isBlank()) null
        else {
            val response = api.photo(filename)
            if (response.isSuccessful) response.body()?.bytes() else null
        }
    } catch (e: Exception) {
        null
    }

    /** Preferensi WA alur SPK (setting mobile). Fail-soft: gagal → default WA ON (optout=false). */
    suspend fun getWaPref(): com.krisoft.tridjayaelektronik.data.model.WaPrefDto = try {
        api.getWaPref().body()?.data ?: com.krisoft.tridjayaelektronik.data.model.WaPrefDto()
    } catch (e: Exception) {
        com.krisoft.tridjayaelektronik.data.model.WaPrefDto()
    }

    /** Simpan preferensi WA alur SPK. true bila server konfirmasi sukses. */
    suspend fun setWaPref(optout: Boolean): Boolean = try {
        api.setWaPref(com.krisoft.tridjayaelektronik.data.model.WaPrefDto(spkWaOptout = optout)).isSuccessful
    } catch (e: Exception) {
        false
    }

    private inline fun <T> call(
        fallback: String,
        block: () -> Response<com.krisoft.tridjayaelektronik.data.model.ApiResponse<T>>
    ): AuthResult<T> = try {
        val response = block()
        val data = response.body()?.data
        if (response.isSuccessful && data != null) AuthResult.Success(data)
        else parseError(response, fallback)
    } catch (e: Exception) {
        AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
    }

    private fun <T> parseError(response: Response<*>, fallback: String): AuthResult<T> {
        val raw = response.errorBody()?.string()
        val parsed = raw?.let {
            runCatching { errorJson.decodeFromString(ApiErrorResponse.serializer(), it) }.getOrNull()
        }
        // ApiError::Validation backend → message GENERIK "Input tidak valid" + alasan
        // SPESIFIK di `errors[]` (mis. "Serial number wajib diisi", "Form pengambilan
        // aki belum disetujui lengkap"). Utamakan errors[] supaya user tahu penyebab
        // asli — bukan cuma "Input tidak valid" yang tak bisa ditindaklanjuti.
        val detail = parsed?.errors?.filter { it.isNotBlank() }?.takeIf { it.isNotEmpty() }?.joinToString("; ")
        return AuthResult.Failure(
            parsed?.code ?: "http_${response.code()}",
            detail ?: parsed?.message ?: "$fallback (${response.code()})"
        )
    }
}
