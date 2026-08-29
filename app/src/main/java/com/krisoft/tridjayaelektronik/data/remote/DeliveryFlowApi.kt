package com.krisoft.tridjayaelektronik.data.remote

import com.krisoft.tridjayaelektronik.data.model.AkiFormCreateData
import com.krisoft.tridjayaelektronik.data.model.AkiFormsData
import com.krisoft.tridjayaelektronik.data.model.ApiResponse
import com.krisoft.tridjayaelektronik.data.model.ConfirmSpkBody
import com.krisoft.tridjayaelektronik.data.model.AssignBody
import com.krisoft.tridjayaelektronik.data.model.ReassignBody
import com.krisoft.tridjayaelektronik.data.model.UnassignBody
import com.krisoft.tridjayaelektronik.data.model.BrokerListData
import com.krisoft.tridjayaelektronik.data.model.ChecklistConfigData
import com.krisoft.tridjayaelektronik.data.model.CreateAkiFormBody
import com.krisoft.tridjayaelektronik.data.model.CreateSerialNumbersBody
import com.krisoft.tridjayaelektronik.data.model.CreateSerialRequestBody
import com.krisoft.tridjayaelektronik.data.model.GenerateSerialBody
import com.krisoft.tridjayaelektronik.data.model.GenerateSerialData
import com.krisoft.tridjayaelektronik.data.model.SerialRequestDto
import com.krisoft.tridjayaelektronik.data.model.SerialRequestListData
import com.krisoft.tridjayaelektronik.data.model.DecisionBody
import com.krisoft.tridjayaelektronik.data.model.DeliveryCategoriesData
import com.krisoft.tridjayaelektronik.data.model.CreateDiscountBody
import com.krisoft.tridjayaelektronik.data.model.DiscountListData
import com.krisoft.tridjayaelektronik.data.model.DiscountRequestDto
import com.krisoft.tridjayaelektronik.data.model.SpkDiscountContextDto
import com.krisoft.tridjayaelektronik.data.model.WaPrefDto
import com.krisoft.tridjayaelektronik.data.model.UsersListData
import com.krisoft.tridjayaelektronik.data.model.CreateDeliveryBody
import com.krisoft.tridjayaelektronik.data.model.DeliverBody
import com.krisoft.tridjayaelektronik.data.model.DeliveryContextDto
import com.krisoft.tridjayaelektronik.data.model.DeliveryCreateResult
import com.krisoft.tridjayaelektronik.data.model.DeliveryJobDto
import com.krisoft.tridjayaelektronik.data.model.DeliveryListData
import com.krisoft.tridjayaelektronik.data.model.DeliveryNoteBody
import com.krisoft.tridjayaelektronik.data.model.DeliveryUploadResponse
import com.krisoft.tridjayaelektronik.data.model.MutasiContextDto
import com.krisoft.tridjayaelektronik.data.model.MutasiHistoriDetailListDto
import com.krisoft.tridjayaelektronik.data.model.MutasiHistoriListDto
import com.krisoft.tridjayaelektronik.data.model.PdiBody
import com.krisoft.tridjayaelektronik.data.model.KontributorDto
import com.krisoft.tridjayaelektronik.data.model.PetugasDirektoriDto
import com.krisoft.tridjayaelektronik.data.model.ReorderBody
import com.krisoft.tridjayaelektronik.data.model.ReorderResult
import com.krisoft.tridjayaelektronik.data.model.RejectAkiBody
import com.krisoft.tridjayaelektronik.data.model.ReturnAkiBody
import com.krisoft.tridjayaelektronik.data.model.SelfPickupCompleteBody
import com.krisoft.tridjayaelektronik.data.model.SetoranKasirBody
import com.krisoft.tridjayaelektronik.data.model.SerialCoverageData
import com.krisoft.tridjayaelektronik.data.model.SerialKondisiLogData
import com.krisoft.tridjayaelektronik.data.model.SetKondisiBody
import com.krisoft.tridjayaelektronik.data.model.SetKondisiResultDto
import com.krisoft.tridjayaelektronik.data.model.SerialCreateResultDto
import com.krisoft.tridjayaelektronik.data.model.SerialListData
import com.krisoft.tridjayaelektronik.data.model.SpkEditResultDto
import com.krisoft.tridjayaelektronik.data.model.StokCabangData
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import kotlinx.serialization.json.JsonObject
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

/** Alur pengiriman SPK — inventory-service via gateway `/api/inventory/delivery`. */
interface DeliveryFlowApi {

    @GET("api/inventory/delivery")
    suspend fun list(
        @Query("status") status: String? = null,
        @Query("view") view: String? = null,
        @Query("page") page: Int? = null,
        @Query("limit") limit: Int? = null,
        /** Sales antar sendiri (2026-07-24): treat aktor sales sbg driver
         *  (job self-delivery miliknya sendiri) — paritas web `asDriver`. */
        @Query("asDriver") asDriver: Boolean? = null,
        /** Rentang tanggal dibuat (YYYY-MM-DD, inklusif dua-duanya di sisi
         *  server). Tak dikirim = tanpa batas tanggal, persis perilaku lama. */
        @Query("dari") dari: String? = null,
        @Query("sampai") sampai: String? = null,
        /**
         * Saringan antrian (2026-08-23). Server sudah menerima semuanya di
         * `ListQuery` (inventory-service `delivery.rs`) — nol perubahan Rust.
         *
         * EJAANNYA HARUS PERSIS INI. Repo ini nol `@SerialName` dan server
         * memakai `serde(rename_all = "camelCase")`, jadi nama parameter di
         * sinilah nama di kabel. Salah eja tidak melempar apa pun: Retrofit
         * tetap mengirim, serde mengabaikan, saringannya cuma diam-diam tak
         * berefek.
         *
         * Retrofit MEMBUANG `@Query` bernilai null (itu yang bikin aman ke
         * server lama), TAPI string kosong tetap terkirim sebagai `?q=` —
         * pemanggil wajib `trim().takeIf { it.isNotBlank() }`.
         */
        @Query("q") q: String? = null,
        @Query("kodeDealer") kodeDealer: String? = null,
        /**
         * `urut`/`sumbu` MENJAWAB 400 untuk nilai asing — satu-satunya param
         * saringan di sini yang tidak fail-open. `null` dan string kosong aman
         * (server punya arm `None | Some("")`), tapi JANGAN menambah nilai baru
         * di klien sebelum server yang mengenalnya benar-benar ter-deploy.
         */
        @Query("urut") urut: String? = null,
        @Query("sumbu") sumbu: String? = null,
        /**
         * CSV metode kirim, dan artinya MEMBALIK default: kosong = server
         * MEMBUANG `self_pickup` + `sales_delivery`; diisi = tampilkan HANYA
         * metode itu. Jadi ini satu-satunya saringan di berkas ini yang bisa
         * MELEBARKAN daftar. Hanya berlaku di tahap `pending_scheduling`.
         */
        @Query("deliveryMethod") deliveryMethod: String? = null
    ): Response<ApiResponse<DeliveryListData>>

    @GET("api/inventory/delivery/context")
    suspend fun context(): Response<ApiResponse<DeliveryContextDto>>

    /**
     * Direktori petugas + panduan alur (WP7). Route STATIS — di backend ia
     * sengaja didaftarkan sebelum `/{id}`, jadi jangan mengubah urutannya jadi
     * segmen dinamis di sini juga.
     *
     * Tanpa parameter `kodeDealer`: server memaksa peran cabang ke cabangnya
     * sendiri dan hanya menghormati parameter itu untuk manager/owner/admin —
     * app selalu ingin cabang si pemanggil, jadi tak ada gunanya mengirimnya.
     */
    @GET("api/inventory/delivery/petugas")
    suspend fun petugas(): Response<ApiResponse<PetugasDirektoriDto>>

    @GET("api/inventory/delivery/{id}")
    suspend fun detail(@Path("id") id: String): Response<ApiResponse<DeliveryJobDto>>

    /**
     * Karyawan yang sudah menangani unit ini + nomor WA-nya. Guard-nya sama
     * dengan detail SPK, jadi siapa pun yang bisa membuka unitnya bisa membaca
     * daftar ini — tak perlu pengecekan role tambahan di klien.
     */
    @GET("api/inventory/delivery/{id}/kontributor")
    suspend fun kontributor(@Path("id") id: String): Response<ApiResponse<List<KontributorDto>>>

    /**
     * Sunting isi SPK (2026-08-01, administrator) — patch PARSIAL: field yang
     * tak ada di [body] dipertahankan server apa adanya. Bentuk badannya
     * `JsonObject` (bukan data class 29 field) supaya "tak dikirim" benar-benar
     * berarti tak dikirim; lihat `SpkEditFields.buildSpkEditPatch`.
     *
     * 400 = tahapnya sudah lewat / sudah tercatat di GS / isinya tak valid;
     * 403 = bukan admin. Pesannya ada di `message` server.
     */
    @PATCH("api/inventory/delivery/{id}")
    suspend fun editJob(
        @Path("id") id: String,
        @Body body: JsonObject
    ): Response<ApiResponse<SpkEditResultDto>>

    @POST("api/inventory/delivery")
    suspend fun create(@Body body: CreateDeliveryBody): Response<ApiResponse<DeliveryCreateResult>>

    /** 111: kunci job ke petugas PDI yang menekan "Ambil PDI" (tanpa body).
     *  409 = sudah dipegang orang lain, NAMANYA ada di `message` server. Klaim
     *  OPSIONAL — `submitPdi` tidak mensyaratkannya (APK lama tetap jalan). */
    @POST("api/inventory/delivery/{id}/claim-pdi")
    suspend fun claimPdi(@Path("id") id: String): Response<ApiResponse<DeliveryJobDto>>

    /** 111: lepas klaim — pemegangnya sendiri, atau admin/manager melepas paksa. */
    @DELETE("api/inventory/delivery/{id}/claim-pdi")
    suspend fun releasePdiClaim(@Path("id") id: String): Response<ApiResponse<DeliveryJobDto>>

    @POST("api/inventory/delivery/{id}/pdi")
    suspend fun submitPdi(@Path("id") id: String, @Body body: PdiBody): Response<ApiResponse<DeliveryJobDto>>

    /**
     * PDI MASSAL barang kecil se-SPK (2026-08-05) — tanpa body, tanpa
     * checklist/serial, tapi jejak `pdiBy`/`pdiAt` tetap tercatat. Satu
     * panggilan menuntaskan SEMUA unit `pending_pdi` sebatch yang harga OTR-nya
     * <= `barangBesarThreshold` (`/context`).
     *
     * [id] WAJIB unit KECIL — unit besar dijawab 400 (barang besar tetap lewat
     * [submitPdi] per unit). Unit kecil berkategori wajib-aki yang formnya belum
     * disetujui DILEWATI diam-diam (tetap `pending_pdi`, disebut di `message`)
     * kecuali kalau dia sendiri yang jadi [id] → 400.
     */
    @POST("api/inventory/delivery/{id}/pdi-kecil")
    suspend fun submitPdiKecil(@Path("id") id: String): Response<ApiResponse<DeliveryJobDto>>

    @POST("api/inventory/delivery/{id}/spk")
    suspend fun confirmSpk(@Path("id") id: String, @Body body: ConfirmSpkBody): Response<ApiResponse<DeliveryJobDto>>

    @POST("api/inventory/delivery/{id}/delivery-note")
    suspend fun issueDeliveryNote(@Path("id") id: String, @Body body: DeliveryNoteBody): Response<ApiResponse<DeliveryJobDto>>

    /** Kasir konfirmasi pembayaran diterima (semua jenis bayar, setelah `delivered`). */
    @POST("api/inventory/delivery/{id}/setoran-kasir")
    suspend fun setoranKasir(@Path("id") id: String, @Body body: SetoranKasirBody): Response<ApiResponse<DeliveryJobDto>>

    @POST("api/inventory/delivery/{id}/assign")
    suspend fun assign(@Path("id") id: String, @Body body: AssignBody): Response<ApiResponse<DeliveryJobDto>>

    /**
     * BATALKAN penjadwalan — hanya selama driver BELUM berangkat. Jendelanya
     * sempit disengaja: begitu unit `in_transit`, barangnya fisik di tangan
     * orang dan "batal" tak punya arti operasional; jalurnya jadi [reassign].
     */
    @POST("api/inventory/delivery/{id}/unassign")
    suspend fun unassign(@Path("id") id: String, @Body body: UnassignBody): Response<ApiResponse<DeliveryJobDto>>

    /**
     * PINDAHKAN ke driver lain. Berlaku juga saat unit sudah `in_transit` —
     * motor mogok / driver sakit di tengah jalan itu kejadian nyata. Batasnya
     * "sudah diterima konsumen", bukan "sudah berangkat".
     *
     * **TIDAK di-fan-out se-SPK** (beda dari [assign]) — justru itu yang membuat
     * pemecahan satu SPK ke dua driver tetap mungkin.
     */
    @POST("api/inventory/delivery/{id}/reassign")
    suspend fun reassign(@Path("id") id: String, @Body body: ReassignBody): Response<ApiResponse<DeliveryJobDto>>

    @POST("api/inventory/delivery/{id}/dispatch")
    suspend fun dispatch(@Path("id") id: String): Response<ApiResponse<DeliveryJobDto>>

    @POST("api/inventory/delivery/{id}/deliver")
    suspend fun deliver(@Path("id") id: String, @Body body: DeliverBody): Response<ApiResponse<DeliveryJobDto>>

    /** (2026-07-24) Delivery Control: tandai job `self_pickup` selesai — foto+rating
     *  wajib, langsung transisi pending_scheduling → delivered. */
    @POST("api/inventory/delivery/{id}/self-pickup-complete")
    suspend fun selfPickupComplete(@Path("id") id: String, @Body body: SelfPickupCompleteBody): Response<ApiResponse<DeliveryJobDto>>

    @POST("api/inventory/delivery/{id}/cancel")
    suspend fun cancel(@Path("id") id: String, @Query("reason") reason: String): Response<ApiResponse<DeliveryJobDto>>

    @Multipart
    @POST("api/inventory/delivery/upload-photo")
    suspend fun uploadPhoto(@Part file: MultipartBody.Part): Response<ApiResponse<DeliveryUploadResponse>>

    /** Serve foto delivery ter-autentikasi (S-02) — `filename` diambil dari URL
     *  logis `/uploads/delivery/{file}` di field job (pdiReadyPhotoUrl/
     *  deliveryPhotoUrl/cashPhotoUrl). Response = bytes gambar mentah. */
    @GET("api/inventory/delivery/photo/{filename}")
    suspend fun photo(@Path("filename") filename: String): Response<okhttp3.ResponseBody>

    @GET("api/inventory/delivery/config/checklist")
    suspend fun checklist(
        @Query("kategori") kategori: String,
        @Query("stage") stage: String? = null
    ): Response<ApiResponse<ChecklistConfigData>>

    /** 088: catat driver sudah chat konsumen H-1 (idempoten, fan-out per batch SPK). */
    @POST("api/inventory/delivery/{id}/chat-consumer")
    suspend fun chatConsumer(@Path("id") id: String): Response<ApiResponse<DeliveryJobDto>>

    /** Urutan muatan driver (posisi array = urutan muat); hanya job milik driver pemanggil. */
    @POST("api/inventory/delivery/driver/reorder")
    suspend fun reorderLoads(@Body body: ReorderBody): Response<ApiResponse<ReorderResult>>

    /**
     * Autocomplete barang Input SPK, di-scope satu cabang.
     *
     * `inStock` default TRUE dan itu disengaja: SP GS `GetStokCabang`
     * mengembalikan katalog penuh per cabang (~5.500 baris) yang cuma 4-16%-nya
     * berstok, jadi tanpa filter ini `limit` baris pertama (urut nama) hampir
     * seluruhnya barang stok 0 — barang yang benar-benar ada tak pernah muncul.
     * Filter DITEGAKKAN SERVER karena paging diiris di sana; menyaring hasilnya
     * di klien cuma membuang sebagian halaman yang sudah telanjur terpilih.
     * Dua pemakai method ini (picker SPK & picker input serial admin-stok)
     * sama-sama cuma peduli barang yang fisiknya ada.
     */
    @GET("api/inventory/stok-cabang")
    suspend fun stokCabang(
        @Query("search") search: String,
        @Query("kodeDealer") kodeDealer: String,
        @Query("limit") limit: Int = 24,
        @Query("inStock") inStock: Boolean = true,
        /**
         * `true` mengembalikan SATU kelas barang stok-nol: yang sudah punya SPK
         * belum tuntas di cabang itu, ditandai `SudahDipesan`. Bukan pelonggaran
         * `inStock` — stok nol tanpa SPK tetap dibuang server. Default `false`
         * supaya picker input serial admin-stok (pemakai kedua method ini) tak
         * ikut menerima baris kosong.
         */
        @Query("includeDipesan") includeDipesan: Boolean = false
    ): Response<ApiResponse<StokCabangData>>

    /** Autocomplete broker KBK — di-scope query. */
    @GET("api/inventory/delivery/brokers")
    suspend fun brokers(@Query("q") q: String): Response<ApiResponse<BrokerListData>>

    /** Registry serial per cabang+barang (picker No. Rangka Input SPK; juga dipakai layar
     *  Input Serial Number admin-stok utk hitung SN yang sudah tercatat — panggil dengan
     *  onlySerial=false, excludeAssigned=false supaya dapat SEMUA baris registry). */
    @GET("api/inventory/serial-numbers")
    suspend fun serialNumbers(
        @Query("kodeDealer") kodeDealer: String,
        @Query("kodeBarang") kodeBarang: String,
        @Query("onlySerial") onlySerial: Boolean = true,
        @Query("excludeAssigned") excludeAssigned: Boolean = true
    ): Response<ApiResponse<SerialListData>>

    /** Cakupan SN per produk satu cabang — jawaban atas "produk mana yang SN-nya
     *  belum lengkap". SENGAJA tidak dipakai picker SPK: sales butuh daftar SN satu
     *  produk, bukan peta kelengkapan satu gudang. Server memotong di 8.000 kode dan
     *  menandainya lewat `truncated`; saat true, produk yang absen dari `items` TIDAK
     *  boleh divonis nol SN. */
    @GET("api/inventory/serial-numbers/summary")
    suspend fun serialCoverage(
        @Query("kodeDealer") kodeDealer: String
    ): Response<ApiResponse<SerialCoverageData>>

    /** Konteks mutasi (dealer sendiri) — reuse utk resolve `sourceDealerCode` akun
     *  admin-stok sebelum input SN manual (grup gateway `mutasi`, login-only). */
    @GET("api/inventory/mutasi/context")
    suspend fun mutasiContext(): Response<ApiResponse<MutasiContextDto>>

    /** Input manual SN massal — role admin-stok saja, dipaksa dealer sendiri di backend
     *  (mismatch → 403 Forbidden eksplisit, beda dari GET yang auto-floor). */
    @POST("api/inventory/serial-numbers")
    suspend fun createSerialNumbers(@Body body: CreateSerialNumbersBody): Response<ApiResponse<SerialCreateResultDto>>

    /** Vonis kondisi unit yang SUDAH terdaftar (layak/tidak_layak/repair/retur) —
     *  admin-stok saja, sama sempitnya dengan yang menulis registry. Satu panggilan =
     *  satu nilai kondisi; batch berkondisi berbeda dikirim terpisah. */
    @POST("api/inventory/serial-numbers/kondisi")
    suspend fun setSerialKondisi(@Body body: SetKondisiBody): Response<ApiResponse<SetKondisiResultDto>>

    /** Riwayat perubahan kondisi unit — admin-stok & manager bebas cabang, role
     *  cabang dipaksa cabangnya sendiri. Terbaru dulu; `truncated` = masih ada yang
     *  lebih tua di luar batas. */
    @GET("api/inventory/serial-numbers/kondisi-log")
    suspend fun serialKondisiLog(
        @Query("kodeDealer") kodeDealer: String,
        @Query("kodeBarang") kodeBarang: String? = null,
        @Query("serialNumber") serialNumber: String? = null,
        @Query("limit") limit: Int? = null
    ): Response<ApiResponse<SerialKondisiLogData>>

    /** Foto bukti usulan SN (multipart `file`, maks 5MB) → `{ url }` relatif
     *  `/uploads/serial/...`. Endpoint TERPISAH dari foto delivery: direktorinya
     *  sendiri (`SERIAL_UPLOAD_DIR`) dan gate-nya pengusul SN, bukan aktor SPK. */
    @Multipart
    @POST("api/inventory/serial-numbers/photo")
    suspend fun uploadSerialPhoto(@Part file: MultipartBody.Part): Response<ApiResponse<DeliveryUploadResponse>>

    /** Usulkan pendaftaran SN dari cabang (keputusan tetap di admin-stok). */
    @POST("api/inventory/serial-numbers/requests")
    suspend fun createSerialRequest(@Body body: CreateSerialRequestBody): Response<ApiResponse<SerialRequestDto>>

    /** Antrian usulan: pengusul cabang di-scope cabangnya sendiri oleh server. */
    @GET("api/inventory/serial-numbers/requests")
    suspend fun serialRequests(
        @Query("kodeDealer") kodeDealer: String? = null,
        @Query("status") status: String? = null,
        @Query("limit") limit: Int? = null
    ): Response<ApiResponse<SerialRequestListData>>

    /** Kode pengganti SN (`GEN-…`) untuk barang tanpa serial pabrik — admin-stok
     *  saja. Backend LANGSUNG menuliskannya ke registry, jadi respons ini bukan
     *  sekadar usulan kode: barangnya sudah punya nomor begitu dipanggil. */
    @POST("api/inventory/serial-numbers/generate")
    suspend fun generateSerials(@Body body: GenerateSerialBody): Response<ApiResponse<GenerateSerialData>>

    /** Arsip mutasi ERP (histori-only, baca saja) — tanpa gate role server-side; RBAC
     *  halaman (admin/admin-stok) direplikasi di client (`InventoryMutasiPage.tsx`). */
    @GET("api/inventory/mutasi-histori")
    suspend fun mutasiHistori(
        @Query("dealer") dealer: String? = null,
        @Query("arah") arah: String? = null
    ): Response<ApiResponse<MutasiHistoriListDto>>

    /** Detail barang 1 transaksi mutasi (kodeBarang/nama/jumlah/sn). */
    @GET("api/inventory/mutasi-histori/detail")
    suspend fun mutasiHistoriDetail(
        @Query("noTransaksi") noTransaksi: String,
        @Query("arah") arah: String
    ): Response<ApiResponse<MutasiHistoriDetailListDto>>

    @GET("api/inventory/delivery/config/categories")
    suspend fun categories(): Response<ApiResponse<DeliveryCategoriesData>>

    @GET("api/inventory/delivery/{id}/aki-form")
    suspend fun jobAkiForms(@Path("id") id: String): Response<ApiResponse<AkiFormsData>>

    @POST("api/inventory/delivery/{id}/aki-form")
    suspend fun createAkiForm(@Path("id") id: String, @Body body: CreateAkiFormBody): Response<ApiResponse<AkiFormCreateData>>

    /** Daftar riwayat form aki (admin/manager lintas cabang; PDI cabang sendiri).
     *  [dari]/[sampai] YYYY-MM-DD, tak dikirim = tanpa batas tanggal. */
    @GET("api/inventory/delivery/aki-forms")
    suspend fun akiForms(
        @Query("dari") dari: String? = null,
        @Query("sampai") sampai: String? = null
    ): Response<ApiResponse<AkiFormsData>>

    /** Tandai aki bekas dikembalikan. */
    @POST("api/inventory/delivery/aki-forms/{id}/return")
    suspend fun returnAkiForm(@Path("id") id: String, @Body body: ReturnAkiBody): Response<ApiResponse<AkiFormCreateData>>

    /** Setujui form aki — approval TUNGGAL (redesain 2026-07-24, dulu 3-pihak/089),
     *  tanpa body: approver pusat (page-grant) atau admin/manager. */
    @POST("api/inventory/delivery/aki-forms/{id}/approve")
    suspend fun approveAkiForm(@Path("id") id: String): Response<ApiResponse<AkiFormCreateData>>

    /** Tolak form aki (alasan wajib). */
    @POST("api/inventory/delivery/aki-forms/{id}/reject")
    suspend fun rejectAkiForm(@Path("id") id: String, @Body body: RejectAkiBody): Response<ApiResponse<AkiFormCreateData>>

    @GET("api/users")
    suspend fun users(@Query("role") role: String): Response<ApiResponse<UsersListData>>

    @GET("api/inventory/discount-requests")
    suspend fun discountRequests(
        @Query("status") status: String? = null,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 100,
        /** Rentang tanggal pengajuan (YYYY-MM-DD); tak dikirim = tanpa batas. */
        @Query("dari") dari: String? = null,
        @Query("sampai") sampai: String? = null
    ): Response<ApiResponse<DiscountListData>>

    /** Riwayat pengajuan diskon satu SPK — dipakai timeline detail SPK dan
     *  baris "bila disetujui" di detail SPK approval.
     *
     *  `baris` null = SELURUH baris SPK (server: `find_by_batch` menerima
     *  `Option<i32>`, dan Retrofit menghilangkan query param yang null).
     *  Balasannya array langsung di `data` (bukan `{items:[...]}` seperti
     *  `discountRequests`). Terbuka untuk semua aktor pipeline, bukan cuma
     *  approver — itu sebabnya ia dipakai di layar detail, yang ViewModel-nya
     *  BARU (di-scope ke NavBackStackEntry) sehingga tak mewarisi antrian
     *  approval layar sebelumnya. */
    @GET("api/inventory/discount-requests/by-batch")
    suspend fun discountHistory(
        @Query("spkBatchKode") spkBatchKode: String,
        @Query("baris") baris: Int? = null
    ): Response<ApiResponse<List<DiscountRequestDto>>>

    /**
     * SPK utuh di balik satu pengajuan diskon (2026-08-07) — seluruh unit
     * se-batch termasuk yang tak berdiskon, plus total OTR/diskon berjalan.
     *
     * Boleh dibaca approver (admin/superadmin atau pemegang page-grant
     * `/dashboard/discount-approval`) DAN sales pemilik SPK-nya; selain itu
     * 403. Kode batch wajib berbentuk `DLV-M{8hex}` (selain itu 400).
     */
    @GET("api/inventory/discount-requests/spk/{kode}")
    suspend fun spkDiscountContext(@Path("kode") kode: String): Response<ApiResponse<SpkDiscountContextDto>>

    /**
     * Ajukan diskon baru untuk SATU baris SPK — jalur REVISI setelah ditolak.
     *
     * Server MENOLAK (400 "Baris ini masih menunggu keputusan diskon") bila
     * baris itu masih punya pengajuan `pending`; yang `rejected` justru boleh
     * diajukan ulang, itulah alur ini.
     */
    @POST("api/inventory/discount-requests")
    suspend fun createDiscountRequest(@Body body: CreateDiscountBody): Response<ApiResponse<DiscountRequestDto>>

    /**
     * Menyetujui SATU pengajuan — HANYA pengajuan `{id}` (2026-08-07,
     * membalik fan-out se-SPK 2026-08-06).
     *
     * Unit BELUM tentu lepas ke PDI: pelepasan sekarang tingkat BATCH, terjadi
     * hanya kalau SELURUH barang SPK sudah tuntas (`approved` atau `dilepas`),
     * dan saat itu seluruh unit dilepas bersamaan. App yang mengasumsikan
     * "disetujui → barang ini jalan" akan salah melaporkan SPK yang masih
     * menunggu barang lain.
     */
    @POST("api/inventory/discount-requests/{id}/approve")
    suspend fun approveDiscount(@Path("id") id: String, @Body body: DecisionBody): Response<ApiResponse<DiscountRequestDto>>

    /**
     * Menolak SATU pengajuan — HANYA pengajuan `{id}` (2026-08-07, membalik
     * fan-out se-SPK 2026-08-06).
     *
     * Penolakan TIDAK melepas unit dari `pending_discount`. Barisnya TETAP
     * tertahan dan bolanya kembali ke sales, yang memilih salah satu dari tiga:
     * ajukan ulang diskon, sunting isi SPK (`PATCH /delivery/{id}`, menerima
     * sales PEMILIK saat `pending_discount`), atau [lanjutTanpaDiskon]. App
     * yang masih mengasumsikan "ditolak → unit masuk antrian PDI" akan
     * meninggalkan SPK mandek tanpa satu pun pesan error.
     */
    @POST("api/inventory/discount-requests/{id}/reject")
    suspend fun rejectDiscount(@Path("id") id: String, @Body body: DecisionBody): Response<ApiResponse<DiscountRequestDto>>

    /**
     * Sales menyerah pada diskon (tanpa body): pengajuan `{id}` DITANDAI
     * `dilepas` — status BARU 2026-08-07 yang menggantikan pelepasan langsung.
     *
     * TIDAK melepas unit dengan sendirinya: unit se-SPK baru pindah
     * `pending_discount` → `pending_pdi` setelah SELURUH barangnya tuntas.
     * Tanpa penanda ini, "ditolak lalu sales merelakan" tak bisa dibedakan dari
     * "ditolak dan sales belum bertindak" — dan yang kedua tak boleh melepas
     * SPK.
     *
     * Kode galat: 400 kalau baris itu sudah punya pengajuan lebih baru, 409
     * kalau statusnya bukan `rejected` lagi (mis. ditekan dua kali), 403 kalau
     * pemanggil bukan pengaju maupun admin.
     */
    @POST("api/inventory/discount-requests/{id}/lanjut-tanpa-diskon")
    suspend fun lanjutTanpaDiskon(@Path("id") id: String): Response<ApiResponse<DiscountRequestDto>>

    // Preferensi per-user: terima/opt-out notifikasi WhatsApp alur SPK (setting mobile).
    @GET("api/inventory/discount-requests/wa-pref")
    suspend fun getWaPref(): Response<ApiResponse<WaPrefDto>>

    @retrofit2.http.PUT("api/inventory/discount-requests/wa-pref")
    suspend fun setWaPref(@Body body: WaPrefDto): Response<ApiResponse<WaPrefDto>>
}
