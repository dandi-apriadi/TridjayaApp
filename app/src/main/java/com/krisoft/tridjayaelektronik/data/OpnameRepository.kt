package com.krisoft.tridjayaelektronik.data

import com.krisoft.tridjayaelektronik.data.local.OpnameUnitDao
import com.krisoft.tridjayaelektronik.data.local.OpnameUnitEntity
import com.krisoft.tridjayaelektronik.data.model.ApproveBatchData
import com.krisoft.tridjayaelektronik.data.model.ApproveBatchRequest
import com.krisoft.tridjayaelektronik.data.model.ApiErrorResponse
import com.krisoft.tridjayaelektronik.data.model.ApiResponse
import com.krisoft.tridjayaelektronik.data.model.CreateOpnameUnitsData
import com.krisoft.tridjayaelektronik.data.model.CreateOpnameUnitsRequest
import com.krisoft.tridjayaelektronik.data.model.CreateOpnameRequest
import com.krisoft.tridjayaelektronik.data.model.ManualUnitDto
import com.krisoft.tridjayaelektronik.data.model.OpnameContextDto
import com.krisoft.tridjayaelektronik.data.model.OpnameDeleteData
import com.krisoft.tridjayaelektronik.data.model.OpnameDetailDto
import com.krisoft.tridjayaelektronik.data.model.OpnameSessionDto
import com.krisoft.tridjayaelektronik.data.model.OpnameStockItemDto
import com.krisoft.tridjayaelektronik.data.model.OpnameUnitDto
import com.krisoft.tridjayaelektronik.data.model.OpnameUnitInput
import com.krisoft.tridjayaelektronik.data.model.RejectUnitBody
import com.krisoft.tridjayaelektronik.data.model.TandaiNihilRequest
import com.krisoft.tridjayaelektronik.data.remote.InventoryApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

const val KONDISI_LAYAK = "layak"
const val KONDISI_TIDAK_LAYAK = "tidak_layak"
/** Rusak tapi masih bisa diperbaiki. */
const val KONDISI_REPAIR = "repair"
/** Dikembalikan ke supplier/pusat. */
const val KONDISI_RETUR = "retur"

/**
 * Cermin `opname::KONDISI_VALID` di Rust (migrasi 194). Server MENOLAK nilai di
 * luar daftar ini per baris (`kondisi_tidak_dikenal`) sejak 2026-08-09 — dulu
 * dipaksa jadi `layak` diam-diam, sehingga unit rusak masuk hitungan stok layak
 * jual tanpa satu pun sinyal. Urutannya sekaligus urutan tampil di pemilih.
 */
val KONDISI_PILIHAN = listOf(KONDISI_LAYAK, KONDISI_TIDAK_LAYAK, KONDISI_REPAIR, KONDISI_RETUR)

/** Label Indonesia untuk nilai kondisi; nilai asing ditampilkan apa adanya. */
fun kondisiLabel(kondisi: String): String = when (kondisi) {
    KONDISI_LAYAK -> "Layak"
    KONDISI_TIDAK_LAYAK -> "Tidak layak"
    KONDISI_REPAIR -> "Repair"
    KONDISI_RETUR -> "Retur"
    else -> kondisi
}

/** Batas kolom `stock_opname_units.serial_number` di MySQL. */
const val SERIAL_MAX_LENGTH = 64

/**
 * Cermin persis `normalize_serial` di `inventory-service/src/opname.rs`: trim + huruf besar,
 * `null` bila kosong atau lebih dari 64 karakter. Aturan yang berbeda sedikit saja berarti
 * duplikat lolos di salah satu sisi — app menerima apa yang server tolak, atau sebaliknya.
 *
 * `uppercase()` (bukan `toUpperCase()`) memakai `Locale.ROOT`, jadi tak ikut aturan Turki
 * yang mengubah "i" jadi "İ" di HP berbahasa Turki; `codePointCount` (bukan `length`)
 * menghitung karakter seperti `chars().count()` Rust, bukan unit UTF-16.
 */
fun normalizeSerial(raw: String): String? {
    val serial = raw.trim().uppercase()
    if (serial.isEmpty()) return null
    if (serial.codePointCount(0, serial.length) > SERIAL_MAX_LENGTH) return null
    return serial
}

/** Kode penolakan gerbang sesi — cerminan konstanta `TOLAK_*` di `opname.rs`. */
const val TOLAK_SESI_TAK_DRAFT = "sesi_tak_draft"
const val TOLAK_JENDELA_BELUM_MULAI = "jendela_belum_mulai"
const val TOLAK_JENDELA_SUDAH_TUTUP = "jendela_sudah_tutup"

/**
 * Penunjukan petugas opname (migrasi 212). Server menolak PER BARIS di dalam
 * respons 200: satu batch boleh memuat campuran scan & ketik-manual sementara
 * petugasnya mungkin cuma dipercaya salah satunya. KEDUANYA PERMANEN — mencoba
 * lagi tak akan mengubah apa pun sampai admin-stok mengubah penunjukannya.
 */
const val TOLAK_IZIN_TETAPKAN_SN = "izin_tetapkan_sn"
const val TOLAK_IZIN_VERIFIKASI_SN = "izin_verifikasi_sn"

/**
 * Penolakan yang TIDAK akan berubah kalau dicoba lagi.
 *
 * Yang tidak ada di sini bersifat SEMENTARA, dan barisnya WAJIB tetap di
 * antrean lokal. Satu-satunya anggota sementara hari ini adalah
 * [TOLAK_JENDELA_BELUM_MULAI]: petugas yang men-scan sejam sebelum sesi dibuka
 * sedang melakukan pekerjaan yang benar, cuma kepagian — menghapus barisnya
 * membuang hasil kerjanya tanpa satu pun jejak, dan ia baru sadar saat
 * hitungannya kurang di akhir sesi.
 */
private val TOLAK_PERMANEN = setOf(
    "duplikat_dalam_sesi",
    "foto_wajib_untuk_manual",
    "kondisi_tidak_dikenal",
    TOLAK_SESI_TAK_DRAFT,
    TOLAK_JENDELA_SUDAH_TUTUP,
    TOLAK_IZIN_TETAPKAN_SN,
    TOLAK_IZIN_VERIFIKASI_SN,
)

/** `true` = barisnya boleh dibuang dari antrean lokal. */
fun tolakPermanen(reason: String): Boolean = reason in TOLAK_PERMANEN

/** Alasan penolakan dari server dalam bahasa yang bisa dibaca petugas di lapangan. */
fun alasanTolakLabel(reason: String): String = when (reason) {
    "duplikat_dalam_sesi" -> "serial ini sudah discan di sesi ini"
    "foto_wajib_untuk_manual" -> "input manual wajib menyertakan dua foto bukti"
    "kondisi_tidak_dikenal" -> "kondisi barang tidak dikenali server"
    TOLAK_SESI_TAK_DRAFT -> "sesi opname sudah ditutup"
    TOLAK_JENDELA_BELUM_MULAI -> "sesi opname belum dibuka — tersimpan, dikirim otomatis nanti"
    TOLAK_JENDELA_SUDAH_TUTUP -> "jendela opname sudah tutup"
    // Kalimatnya menyebut JALAN KELUARNYA, bukan cuma vonisnya: petugas yang
    // cuma diberi tahu "ditolak" akan mencoba lagi seharian: menyuruhnya
    // menghubungi admin-stok adalah satu-satunya tindakan yang mengubah apa pun.
    TOLAK_IZIN_TETAPKAN_SN ->
        "kamu belum ditunjuk untuk mengetik SN manual di cabang ini — hubungi admin stok"
    TOLAK_IZIN_VERIFIKASI_SN ->
        "kamu belum ditunjuk untuk men-scan SN di cabang ini — hubungi admin stok"
    else -> reason
}

/**
 * Sifat sebuah kegagalan pengiriman — penentu apakah barisnya boleh tetap
 * mengendap di antrean Room.
 *
 * Sampai 2026-08-12 SETIAP kegagalan (termasuk 403) dilaporkan `Queued` alias
 * "tersimpan offline, menunggu jaringan". Akibatnya petugas yang izinnya dicabut
 * men-scan seharian mengira datanya tersimpan: barisnya mengendap selamanya di
 * Room dan TETAP terhitung di daftar unit serta PDF hitung fisik — angka opname
 * yang salah tanpa satu pun error terlihat.
 */
enum class SifatGagal {
    /** Vonis melekat pada permintaannya sendiri; mengulangnya percuma. */
    PERMANEN,

    /** Permintaannya sah, SESINYA yang mati. Sah lagi setelah user masuk lagi. */
    SESI,

    /** Jaringan/serverkan sedang tak bisa menjawab — antrean WAJIB bertahan. */
    SEMENTARA
}

/**
 * Status HTTP yang jawabannya TIDAK akan berubah kalau permintaan yang sama
 * diulang: 400/422 (server menolak isi permintaannya), 403 (tak berhak — dan
 * sejak migrasi 212 inilah jawaban untuk petugas yang tak ditunjuk sama sekali,
 * lihat `authorize_hitung` di `opname.rs`), 404 (sesi/rute tak ada), 409.
 */
private val HTTP_PERMANEN = setOf(400, 403, 404, 409, 422)

/**
 * Yang barisnya boleh DIBUANG dari antrean lokal — lebih sempit dari
 * [HTTP_PERMANEN], dan sengaja.
 *
 * 403/409/422 = server menolak ISI permintaan ini, jadi menyimpannya cuma
 * menggelembungkan hitungan lokal. TIGA status yang PERMANEN untuk pelaporan
 * tapi barisnya tetap BERTAHAN, masing-masing karena alasan berbeda:
 * - **404**: rute gateway yang belum terpasang menjawab 404 juga (insiden APK
 *   2.67) — membuang hasil scan karena backend-nya belum ter-deploy jauh lebih
 *   mahal daripada baris yang tertahan sampai app/server diperbarui.
 * - **401**: permintaannya sah, SESINYA yang mati; push berikutnya sesudah
 *   login ulang akan mengirimnya.
 * - **400**: `authorize_hitung` menjawab 400 "Akun belum terikat cabang —
 *   hubungi admin" (`opname.rs`), dan kalimat itu sendiri menyebut jalan keluar
 *   yang MENGUBAH jawabannya. Membuang antrean di situ memaksa petugas
 *   mengulang keliling gudang sesudah admin membetulkan data akunnya.
 *
 * Syarat KEDUA (asal vonisnya) ada di [bolehBuangAntrean] — status saja tidak
 * cukup.
 */
private val HTTP_BUANG_ANTREAN = setOf(403, 409, 422)

/**
 * Prefiks `code` yang dipasang [parseError] ketika badan errornya BUKAN JSON
 * aplikasi kita: ia memakai `parsed.code` bila badannya bisa di-parse, kalau
 * tidak jatuh ke prefiks ini digabung kode status HTTP-nya.
 *
 * Ini pembeda yang menentukan boleh-tidaknya membuang hasil kerja petugas:
 * badan JSON ber-`code` (mis. `forbidden`, `validation_error`) berarti
 * inventory-service SENDIRI yang memvonis permintaan ini, sedangkan `http_403`
 * berarti sesuatu di DEPAN origin menjawab — Cloudflare/WAF, rate-limit, atau
 * proxy — dan itu keadaan SEMENTARA yang tak tahu apa-apa soal penunjukan
 * petugas.
 */
private const val PREFIKS_KODE_NON_APLIKASI = "http_"

/**
 * Daftar-putih, arah aman ke SEMENTARA: status yang belum dikenal (dan
 * kegagalan tanpa status sama sekali — lempar IOException) diperlakukan
 * sementara, pola yang sudah dipakai [tolakPermanen]. APK yang tertinggal versi
 * tak boleh membuang hasil kerja petugas hanya karena jawabannya belum dikenal.
 */
fun sifatGagal(httpStatus: Int?): SifatGagal = when {
    httpStatus == null -> SifatGagal.SEMENTARA
    httpStatus == 401 -> SifatGagal.SESI
    httpStatus in HTTP_PERMANEN -> SifatGagal.PERMANEN
    // 5xx, 408, 429, dan apa pun yang belum dikenal.
    else -> SifatGagal.SEMENTARA
}

/**
 * `true` = barisnya boleh DIBUANG dari antrean lokal. Lihat [HTTP_BUANG_ANTREAN].
 *
 * DUA syarat, dan syarat kedua yang menyelamatkan hasil kerja sehari penuh:
 * statusnya harus salah satu yang memvonis isi permintaan, **DAN** vonis itu
 * harus datang dari aplikasi kita sendiri (badan JSON ber-`code`), bukan dari
 * lapisan di depannya.
 *
 * Skenario yang ditutup syarat kedua (temuan review 2026-08-12): petugas
 * men-scan 40 unit seharian tanpa sinyal — semuanya benar tertahan di antrean.
 * Begitu sinyal kembali, satu POST berisi 40 baris ditembakkan dan **Cloudflare**
 * (tridjaya.com memang di belakangnya) menjawab **403 HTML** karena WAF /
 * rate-limit, tanpa pernah menyentuh origin. Tanpa syarat kedua, keempat puluh
 * baris hitung fisik itu DIHAPUS dari Room dan layarnya menuduh petugas "belum
 * ditunjuk" — kehilangan data permanen untuk kondisi yang justru sementara.
 * Kelas yang sama muncul pada akun yang sejenak dinonaktifkan HRD.
 *
 * 400 SENGAJA di luar [HTTP_BUANG_ANTREAN]: `authorize_hitung` menjawab 400
 * "Akun belum terikat cabang — hubungi admin" (opname.rs), dan pesan itu sendiri
 * menyebutkan jalan keluar yang mengubah jawabannya. Membuang antrean di situ
 * berarti petugas mengulang keliling gudang sesudah admin membetulkan datanya.
 * 400 tetap PERMANEN untuk PELAPORAN (petugas wajib melihat sebabnya), cuma
 * barisnya yang bertahan — perlakuan sama dengan 404/401.
 */
fun bolehBuangAntrean(failure: AuthResult.Failure): Boolean =
    failure.httpStatus in HTTP_BUANG_ANTREAN &&
        !failure.code.startsWith(PREFIKS_KODE_NON_APLIKASI)

/**
 * Kalimat untuk kegagalan yang BUKAN sekadar sinyal hilang. Pesan server dipakai
 * apa adanya, lalu dilengkapi tindakan yang bisa dilakukan petugas — badan error
 * 403 server berbunyi "Akses ditolak" saja (`ApiError::Forbidden` di
 * `rust-shared/src/error.rs`), yang tak memberi tahu siapa pun harus berbuat apa.
 */
fun pesanGagalKirim(failure: AuthResult.Failure): String {
    val dasar = failure.message.trim().ifEmpty { "Permintaan ditolak server" }
    return when (failure.httpStatus) {
        403 -> "$dasar — kamu belum ditunjuk sebagai petugas opname di cabang ini " +
            "(atau sesi ini milik cabang lain). Hubungi admin stok."
        401 -> "Sesi kamu berakhir — masuk lagi. Hasil scan yang sudah tercatat " +
            "tetap tersimpan di HP dan dikirim otomatis setelah kamu masuk."
        else -> dasar
    }
}

const val INPUT_SCAN = "scan"
const val INPUT_MANUAL = "manual"
const val VALIDASI_PENDING = "pending"
const val VALIDASI_REJECTED = "rejected"

/**
 * Cerminan `VALIDASI_APPROVED` di `inventory-service opname.rs`. Konstanta ini
 * sebelumnya tak ada di sini karena app tak pernah meminta riwayat — server
 * menerima `pending|approved|rejected` dan MENOLAK sisanya dengan 400
 * ("status harus pending|approved|rejected"), jadi ejaannya tak boleh ditebak.
 */
const val VALIDASI_APPROVED = "approved"

/** Satu-satunya status sesi yang buffer lokalnya boleh diisi ulang dari server. */
const val STATUS_DRAFT = "draft"

/**
 * Stock opname (hitung fisik) client. Penghitungan berbasis UNIT: satu baris per serial
 * number, bukan angka jumlah per SKU.
 *
 * Tiap scan disimpan ke Room DULU (cepat, tetap jalan tanpa sinyal) lalu langsung dikirim.
 * Push per-scan, bukan sekali batch saat sesi ditutup, karena duplikat hanya bisa divonis
 * server: beberapa petugas memakai HP berbeda pada sesi yang sama, dan menunda pengiriman
 * berarti tabrakan baru ketahuan saat penutupan.
 */
@Singleton
class OpnameRepository @Inject constructor(
    private val api: InventoryApi,
    private val unitDao: OpnameUnitDao
) {

    private val errorJson = Json { ignoreUnknownKeys = true }
    private val pushMutex = Mutex()

    /**
     * Kapan terakhir sebuah unit dihapus per sesi. Dipakai rekonsiliasi untuk
     * MENOLAK menyisipkan dari snapshot yang lebih tua dari penghapusan itu —
     * tanpa ini GET yang berangkat sebelum petugas menghapus akan menghidupkan
     * lagi baris yang sudah tak ada di server (baris hantu yang ikut terhitung
     * di layar dan PDF, tanpa satu pun indikator). Cukup satu penanda per sesi:
     * rekonsiliasi yang kalah balapan dilewati sekali, tick berikutnya benar.
     */
    private val terakhirHapusMillis = java.util.concurrent.ConcurrentHashMap<String, Long>()

    suspend fun context(): AuthResult<OpnameContextDto> =
        call("Gagal memuat konteks opname") { api.opnameContext() }

    suspend fun list(status: String? = null): AuthResult<List<OpnameSessionDto>> =
        when (val result = call("Gagal memuat daftar opname") { api.listOpname(status) }) {
            is AuthResult.Success -> AuthResult.Success(result.data.items)
            // `result` diteruskan apa adanya (Failure : AuthResult<Nothing>) supaya
            // `httpStatus` ikut terbawa — membangun ulang Failure membuangnya.
            is AuthResult.Failure -> result
        }

    suspend fun create(request: CreateOpnameRequest): AuthResult<OpnameDetailDto> =
        call("Gagal membuat sesi opname") { api.createOpname(request) }

    suspend fun detail(id: String): AuthResult<OpnameDetailDto> =
        call("Gagal memuat detail opname") { api.opnameDetail(id) }

    suspend fun stockList(id: String): AuthResult<List<OpnameStockItemDto>> =
        when (val result = call("Gagal memuat daftar barang opname") { api.opnameStock(id) }) {
            is AuthResult.Success -> AuthResult.Success(result.data.items)
            // `result` diteruskan apa adanya (Failure : AuthResult<Nothing>) supaya
            // `httpStatus` ikut terbawa — membangun ulang Failure membuangnya.
            is AuthResult.Failure -> result
        }

    // ---- Antrian validasi unit ketik-manual (admin-stok) ----

    /** Antrian lintas sesi. Gagal muat TIDAK boleh jadi daftar kosong — pemutus
     *  harus tahu bedanya "tak ada yang menunggu" dan "tak bisa membaca". */
    suspend fun manualUnits(status: String = VALIDASI_PENDING): AuthResult<List<ManualUnitDto>> =
        when (val result = call("Gagal memuat antrian validasi") { api.manualUnits(status) }) {
            is AuthResult.Success -> AuthResult.Success(result.data.items)
            // `result` diteruskan apa adanya (Failure : AuthResult<Nothing>) supaya
            // `httpStatus` ikut terbawa — membangun ulang Failure membuangnya.
            is AuthResult.Failure -> result
        }

    suspend fun approveManualUnit(sessionId: String, unitId: String): AuthResult<Unit> =
        when (val r = call<OpnameDetailDto>("Gagal menyetujui unit") {
            api.approveManualUnit(sessionId, unitId)
        }) {
            is AuthResult.Success -> AuthResult.Success(Unit)
            is AuthResult.Failure -> r
        }

    /**
     * Approve MASSAL satu sesi. [unitIds] wajib tidak kosong — daftar kosong
     * berarti "seluruh pending sesi ini" bagi server, dan itu bisa melampaui
     * yang tampil di layar.
     */
    suspend fun approveManualUnitsBatch(
        sessionId: String,
        unitIds: List<String>,
    ): AuthResult<ApproveBatchData> {
        if (unitIds.isEmpty()) {
            return AuthResult.Failure("validation", "Tak ada unit yang dipilih")
        }
        return call("Gagal menyetujui unit") {
            api.approveManualUnitsBatch(sessionId, ApproveBatchRequest(unitIds))
        }
    }

    /** [alasan] WAJIB. Ditolak di sini dulu supaya pemutus dapat pesan yang jelas,
     *  bukan 400 generik dari server. */
    suspend fun rejectManualUnit(
        sessionId: String,
        unitId: String,
        alasan: String
    ): AuthResult<Unit> {
        val bersih = alasan.trim()
        if (bersih.isEmpty()) {
            return AuthResult.Failure("validation", "Alasan penolakan wajib diisi")
        }
        return when (val r = call<OpnameDetailDto>("Gagal menolak unit") {
            api.rejectManualUnit(sessionId, unitId, RejectUnitBody(bersih))
        }) {
            is AuthResult.Success -> AuthResult.Success(Unit)
            is AuthResult.Failure -> r
        }
    }

    /**
     * Foto bukti unit manual (ter-autentikasi). Hanya NAMA BERKAS yang dikirim —
     * respons menyimpan path logis `/uploads/serial/...`. Fail-soft `null`:
     * foto gagal tak boleh menyandera daftar maupun tombol putusan.
     */
    suspend fun fetchSerialPhoto(url: String): ByteArray? = try {
        val filename = url.trim().substringAfterLast('/')
        if (filename.isBlank()) null
        else api.serialPhoto(filename).let { if (it.isSuccessful) it.body()?.bytes() else null }
    } catch (e: Exception) {
        null
    }

    // ---- Buffer unit lokal (offline-first) ----

    fun observeUnits(sessionId: String): Flow<List<OpnameUnitEntity>> = unitDao.observe(sessionId)

    suspend fun unitCount(sessionId: String): Int = unitDao.countAll(sessionId)

    sealed interface ScanResult {
        /** Tersimpan di server; [temuan] terisi bila serialnya janggal.
         *  [validationStatus] `pending` bila server MENGENAL input manual;
         *  `null` bila server tak mengirim vonis sama sekali — backend lama
         *  menerima unitnya sebagai scan biasa, jadi tak ada vonis yang akan
         *  datang dan pemanggil TIDAK BOLEH mengarang `pending`. */
        data class Accepted(
            val serialNumber: String,
            val temuan: String?,
            val validationStatus: String? = null
        ) : ScanResult
        data class Rejected(val serialNumber: String, val reason: String) : ScanResult
        /** Tersimpan lokal, menunggu jaringan. */
        data class Queued(val serialNumber: String, val reason: String) : ScanResult
    }

    /**
     * Catat satu unit. Duplikat lokal ditolak seketika tanpa menunggu jaringan; server
     * tetap wasit terakhir untuk duplikat lintas-perangkat.
     */
    suspend fun scanUnit(
        sessionId: String,
        kodeBarang: String,
        namaBarang: String?,
        serialNumberRaw: String,
        kondisi: String = KONDISI_LAYAK,
        keterangan: String? = null
    ): ScanResult {
        val serial = normalizeSerial(serialNumberRaw)
            ?: return ScanResult.Rejected(
                serialNumberRaw.trim(),
                "serial kosong atau lebih dari $SERIAL_MAX_LENGTH karakter"
            )
        if (unitDao.countSerial(sessionId, serial) > 0) {
            return ScanResult.Rejected(serial, alasanTolakLabel("duplikat_dalam_sesi"))
        }
        val now = System.currentTimeMillis()
        unitDao.upsert(
            OpnameUnitEntity(
                sessionId = sessionId,
                serialNumber = serial,
                kodeBarang = kodeBarang,
                namaBarang = namaBarang,
                kondisi = kondisi,
                keterangan = keterangan?.takeIf { it.isNotBlank() },
                temuan = null,
                updatedAtMillis = now,
                syncedAtMillis = null
            )
        )
        return when (val pushed = pushPending(sessionId)) {
            is AuthResult.Success -> {
                val rejected = pushed.data.rejected.firstOrNull { it.serialNumber == serial }
                if (rejected != null) {
                    val pesan = rejected.reasonText?.takeIf { it.isNotBlank() }
                        ?: alasanTolakLabel(rejected.reason)
                    // Penolakan SEMENTARA dilaporkan `Queued`, bukan `Rejected`:
                    // barisnya memang masih ada di antrean dan akan terkirim
                    // sendiri. Menyebutnya "ditolak" menyuruh petugas men-scan
                    // ulang unit yang sebenarnya sudah tercatat.
                    if (tolakPermanen(rejected.reason)) {
                        ScanResult.Rejected(serial, pesan)
                    } else {
                        ScanResult.Queued(serial, pesan)
                    }
                } else {
                    ScanResult.Accepted(
                        serial,
                        pushed.data.accepted.firstOrNull { it.serialNumber == serial }?.temuan
                    )
                }
            }
            // Kegagalan seluruh permintaan (bukan penolakan per baris). "Queued"
            // untuk SEMUANYA adalah bug rilis 2.68 ke bawah: 403 pun dilaporkan
            // "tersimpan offline, menunggu jaringan", jadi petugas yang tak
            // ditunjuk terus men-scan sementara barisnya mengendap di Room dan
            // ikut terhitung di daftar unit + PDF. Barisnya sendiri sudah dibuang
            // di `pushPendingLocked` untuk status yang memang layak dibuang.
            is AuthResult.Failure -> when (sifatGagal(pushed.httpStatus)) {
                SifatGagal.SEMENTARA -> ScanResult.Queued(serial, pushed.message)
                SifatGagal.PERMANEN, SifatGagal.SESI ->
                    ScanResult.Rejected(serial, pesanGagalKirim(pushed))
            }
        }
    }

    /**
     * Catat satu unit KETIK MANUAL (barcode rusak) — wajib dua foto bukti.
     * Masuk berstatus `pending` sampai admin-stok memvalidasi, TAPI hanya bila
     * server mengenal input manual; backend lama menerimanya sebagai scan biasa
     * dan baris lokalnya ditulis apa adanya (`scan`, tanpa vonis).
     *
     * SENGAJA tidak diantre offline seperti scan: fotonya harus terunggah dulu,
     * dan unit yang "tersimpan" menurut petugas tapi belum sampai jauh lebih
     * menyesatkan daripada penolakan yang jelas (pola sama usulan SN).
     */
    suspend fun manualUnit(
        sessionId: String,
        kodeBarang: String,
        namaBarang: String?,
        serialNumberRaw: String,
        kondisi: String,
        fotoSnUrl: String,
        fotoBarangUrl: String,
        keterangan: String? = null
    ): ScanResult {
        val serial = normalizeSerial(serialNumberRaw)
            ?: return ScanResult.Rejected(
                serialNumberRaw.trim(),
                "serial kosong atau lebih dari $SERIAL_MAX_LENGTH karakter"
            )
        if (unitDao.countSerial(sessionId, serial) > 0) {
            return ScanResult.Rejected(serial, alasanTolakLabel("duplikat_dalam_sesi"))
        }
        val request = CreateOpnameUnitsRequest(
            items = listOf(
                OpnameUnitInput(
                    kodeBarang = kodeBarang,
                    serialNumber = serial,
                    kondisi = kondisi,
                    keterangan = keterangan?.takeIf { it.isNotBlank() },
                    inputMethod = INPUT_MANUAL,
                    fotoSnUrl = fotoSnUrl,
                    fotoBarangUrl = fotoBarangUrl
                )
            )
        )
        val result = call("Gagal mengirim unit manual") { api.createOpnameUnits(sessionId, request) }
        return when (result) {
            is AuthResult.Success -> {
                val rejected = result.data.rejected.firstOrNull { it.serialNumber == serial }
                if (rejected != null) {
                    ScanResult.Rejected(serial, alasanTolakLabel(rejected.reason))
                } else {
                    val accepted = result.data.accepted.firstOrNull { it.serialNumber == serial }
                    // Server yang belum mengenal input manual menerima unitnya sebagai scan
                    // biasa dan tak membalas validationStatus. Mengarang `pending` di situ
                    // menempelkan badge merah yang TAK PERNAH bisa lepas — tak ada vonis yang
                    // akan datang. Ikuti jawaban server apa adanya.
                    val statusServer = accepted?.validationStatus
                    // Baris lokal ditulis SETELAH server menerima — langsung synced,
                    // supaya pushPending tak pernah mengirim ulang tanpa metadata foto.
                    val now = System.currentTimeMillis()
                    unitDao.upsert(
                        OpnameUnitEntity(
                            sessionId = sessionId,
                            serialNumber = serial,
                            kodeBarang = kodeBarang,
                            namaBarang = namaBarang,
                            kondisi = kondisi,
                            keterangan = keterangan?.takeIf { it.isNotBlank() },
                            temuan = accepted?.temuan,
                            inputMethod = if (statusServer == null) INPUT_SCAN else INPUT_MANUAL,
                            validationStatus = statusServer,
                            rejectReason = null,
                            updatedAtMillis = now,
                            syncedAtMillis = now
                        )
                    )
                    ScanResult.Accepted(serial, accepted?.temuan, accepted?.validationStatus)
                }
            }
            // Unit manual tak pernah mengantre, jadi tak ada baris yang perlu
            // dibuang — yang penting alasannya tampil apa adanya, bukan "coba
            // lagi nanti" untuk penolakan yang tak akan pernah berubah.
            is AuthResult.Failure -> ScanResult.Rejected(
                serial,
                if (result.httpStatus == null) {
                    // Tak pernah sampai server (sinyal hilang / timeout koneksi).
                    "Butuh koneksi untuk mengirim unit manual — coba lagi saat sinyal kembali"
                } else {
                    pesanGagalKirim(result)
                }
            )
        }
    }

    /**
     * Tarik vonis validasi admin-stok dari server ke buffer lokal — tanpa ini
     * badge "menunggu" tak pernah berubah jadi "ditolak: <alasan>" dan petugas
     * tak tahu harus scan ulang. Fail-soft: gagal baca = badge lama bertahan.
     *
     * Sekaligus jalur MASUK satu-satunya dari server ke Room: unit yang ada di
     * server tapi tak ada di HP ini tak punya cara lain muncul, dan tanpa itu ia
     * memblokir penutupan sesi tanpa jalan keluar — proses app bisa mati di antara
     * POST yang sukses dan penulisan Room, dan HP kedua yang menghitung sesi sama
     * tak pernah punya barisnya sama sekali. Scan ulang tak menolong: server
     * menjawab `duplikat_dalam_sesi` sementara baris lokal tetap tak pernah ada.
     *
     * HANYA untuk sesi draft — lihat penjaga [sessionStatus] di bawah.
     */
    /**
     * Mengembalikan daftar unit versi SERVER yang barusan dibaca — bukan sekadar
     * efek samping ke Room. Perbandingan kondisi registry vs temuan lapangan
     * (`kondisiRegistry`/`kondisiSelisih`) SENGAJA tidak ikut disimpan ke Room:
     * nilainya berubah tiap kali admin-stok mengubah vonis registry, jadi
     * menyalinnya ke buffer lokal berarti menampilkan vonis basi di layar yang
     * sedang dipakai memverifikasi. Ia data rujukan, bukan hasil kerja petugas.
     *
     * Daftar kosong = sesi bukan draft, atau permintaannya gagal (fail-soft,
     * sama seperti sebelum ini).
     */
    suspend fun refreshValidationStatuses(
        sessionId: String,
        sessionStatus: String
    ): List<OpnameUnitDto> {
        // `cancel`/`finalize` sengaja mengosongkan buffer (clearSession), tapi server TIDAK
        // menghapus unitnya saat sesi dibatalkan. Tanpa penjaga ini, membuka lagi sesi yang
        // sudah ditutup menyisipkan seluruh unitnya kembali ke Room dan tinggal permanen —
        // clearSession tak akan pernah jalan lagi untuk sesi itu.
        if (sessionStatus != STATUS_DRAFT) return emptyList()
        // Dicatat SEBELUM GET: apa pun yang ditulis setelah ini lebih baru dari
        // snapshot yang sedang dibaca, jadi tak boleh ditimpa olehnya.
        val mulai = System.currentTimeMillis()
        val listed = call("Gagal memuat unit") { api.listOpnameUnits(sessionId) }
        val items = (listed as? AuthResult.Success)?.data?.items ?: return emptyList()
        // Nama barang tak dibawa DTO unit. Diambil dari daftar barang sesi, sekali dan hanya
        // bila memang ada baris baru yang perlu disisipkan — tanpa itu unit hasil rekonsiliasi
        // tercetak "-" di PDF hitung fisik sementara unit hasil scan di HP ini bernama lengkap.
        var namaByKode: Map<String, String?>? = null
        suspend fun namaBarang(kode: String): String? {
            if (namaByKode == null) {
                // Gagal memuat = peta kosong (bukan null): jangan ulangi permintaannya
                // per baris, dan nama kosong tetap lebih baik daripada rekonsiliasi batal.
                namaByKode = (stockList(sessionId) as? AuthResult.Success)
                    ?.data
                    ?.associate { it.kodeBarang.uppercase() to it.namaBarang }
                    ?: emptyMap()
            }
            return namaByKode?.get(kode.uppercase())
        }
        // Dibaca SESUDAH GET selesai, jadi sudah memuat scan ulang yang terjadi selagi GET
        // terbang — itulah yang membuat penjaga di bawah bisa menolak vonis basi.
        val lokal = unitDao.all(sessionId).associateBy { it.serialNumber.uppercase() }
        // Penghapusan yang terjadi SELAGI GET terbang membuat snapshot ini lebih
        // tua dari kenyataan: baris yang barusan dihapus tak ada di `lokal`, jadi
        // cabang sisip di bawah akan menghidupkannya lagi sebagai baris hantu.
        // Cabang UPDATE tak perlu penjaga ini (ia sudah dijaga updatedAtMillis).
        val bolehSisip = (terakhirHapusMillis[sessionId] ?: 0L) <= mulai
        items.forEach { dto ->
            val serial = normalizeSerial(dto.serialNumber) ?: return@forEach
            val row = lokal[serial]
            if (row == null) {
                if (!bolehSisip) return@forEach
                unitDao.upsert(
                    OpnameUnitEntity(
                        sessionId = sessionId,
                        serialNumber = serial,
                        kodeBarang = dto.kodeBarang,
                        namaBarang = namaBarang(dto.kodeBarang),
                        kondisi = dto.kondisi,
                        keterangan = dto.keterangan,
                        temuan = dto.temuan,
                        inputMethod = dto.inputMethod,
                        validationStatus = dto.validationStatus,
                        rejectReason = dto.rejectReason,
                        updatedAtMillis = mulai,
                        // Sudah ada di server — jangan pernah masuk antrean kirim ulang.
                        syncedAtMillis = mulai
                    )
                )
            } else if (
                dto.inputMethod == INPUT_MANUAL &&
                row.inputMethod == INPUT_MANUAL &&
                row.updatedAtMillis <= mulai
            ) {
                // Baris lokal yang sudah discan ulang (jadi `scan`) atau ditulis sesudah GET
                // dimulai lebih baru dari snapshot ini — mengecapnya `rejected` membuat unit
                // yang justru diterima server terlihat gagal selamanya, dan `countSerial`
                // menganggapnya tak ada sehingga scan berikutnya dijawab duplikat.
                // Penjaga kembar ada di WHERE `updateValidation` untuk tulisan yang menyelinap
                // di antara pembacaan di atas dan baris ini.
                unitDao.updateValidation(sessionId, serial, dto.validationStatus, dto.rejectReason, mulai)
            }
        }
        return items
    }

    /**
     * Nyatakan sekumpulan barang NIHIL — sudah dicari, tak ada satu pun.
     *
     * SENGAJA tidak diantre offline seperti scan. Nihil adalah PERNYATAAN,
     * bukan temuan fisik yang bisa hilang kalau tak segera dikirim: petugas
     * bisa mengulanginya kapan saja, dan pernyataan yang "tersimpan" menurut
     * layar tapi belum sampai server jauh lebih menyesatkan — ia menyangkut
     * barang yang dilaporkan HILANG. Pola sama `manualUnit` dan usulan SN.
     */
    suspend fun tandaiNihil(sessionId: String, kodeBarang: List<String>): AuthResult<OpnameDetailDto> {
        val bersih = kodeBarang.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (bersih.isEmpty()) {
            return AuthResult.Failure("validation", "Tidak ada barang yang dipilih")
        }
        return call("Gagal menandai barang nihil") {
            api.tandaiOpnameNihil(sessionId, TandaiNihilRequest(bersih))
        }
    }

    /**
     * Kirim semua baris yang belum tersinkron untuk sesi ini.
     *
     * Digembok satu-per-satu (pola `CrmRepository.syncPendingLeads`): scan berikutnya dan
     * tombol "Kirim ulang" bisa memanggil ini bersamaan, dan dua pengiriman paralel akan
     * membawa baris yang SAMA — yang kedua dijawab `duplikat_dalam_sesi` lalu barisnya
     * dihapus dari buffer, padahal itu unit sah milik petugas ini.
     */
    suspend fun pushPending(sessionId: String): AuthResult<CreateOpnameUnitsData> =
        pushMutex.withLock { pushPendingLocked(sessionId) }

    private suspend fun pushPendingLocked(sessionId: String): AuthResult<CreateOpnameUnitsData> {
        val pending = unitDao.pending(sessionId)
        if (pending.isEmpty()) return AuthResult.Success(CreateOpnameUnitsData())
        val request = CreateOpnameUnitsRequest(
            items = pending.map {
                OpnameUnitInput(
                    kodeBarang = it.kodeBarang,
                    serialNumber = it.serialNumber,
                    kondisi = it.kondisi,
                    keterangan = it.keterangan
                )
            }
        )
        val result = call("Gagal mengirim hasil scan") { api.createOpnameUnits(sessionId, request) }
        if (result is AuthResult.Failure && bolehBuangAntrean(result)) {
            // Seluruh permintaan ditolak dan vonisnya melekat pada isinya (403
            // petugas tak ditunjuk, 400 validasi, dst). Barisnya TIDAK BOLEH
            // tetap di antrean: ia tak akan pernah diterima server, tapi terus
            // dihitung di layar dan di PDF hitung fisik seolah sudah tercatat —
            // opname yang salah tanpa satu pun error yang terlihat.
            //
            // Yang dibuang hanya baris yang BARUSAN dikirim; scan yang menyelinap
            // masuk selagi permintaan ini terbang belum pernah dicoba, jadi
            // penolakan ini bukan vonis atasnya.
            pending.forEach { unitDao.delete(sessionId, it.serialNumber) }
        }
        if (result is AuthResult.Success) {
            val now = System.currentTimeMillis()
            result.data.accepted.forEach { unitDao.markSynced(sessionId, it.serialNumber, now, it.temuan) }
            // Ditolak server — dibuang HANYA bila penolakannya permanen.
            //
            // Penolakan SEMENTARA (`jendela_belum_mulai`) barisnya dibiarkan
            // tetap pending, jadi `pushPending` berikutnya mengirimnya lagi
            // begitu jendela terbuka. Menghapusnya membuang hasil scan petugas
            // yang cuma kepagian — kesalahan yang tak menimbulkan error, tak
            // terlihat di layar, dan baru ketahuan saat hitungan akhir kurang.
            result.data.rejected
                .filter { tolakPermanen(it.reason) }
                .forEach { unitDao.delete(sessionId, it.serialNumber) }
        }
        return result
    }

    /**
     * Hapus satu unit (salah scan). Baris yang sudah tersinkron dihapus di server dulu —
     * kalau tidak, hitungan server tetap menghitungnya.
     */
    suspend fun deleteUnit(sessionId: String, unit: OpnameUnitEntity): AuthResult<Unit> {
        if (unit.syncedAtMillis != null) {
            val serverId = serverUnitId(sessionId, unit.serialNumber)
            if (serverId != null) {
                val removed = call<OpnameDetailDto>("Gagal menghapus unit") {
                    api.deleteOpnameUnit(sessionId, serverId)
                }
                if (removed is AuthResult.Failure) {
                    return removed
                }
            }
        }
        unitDao.delete(sessionId, unit.serialNumber)
        terakhirHapusMillis[sessionId] = System.currentTimeMillis()
        return AuthResult.Success(Unit)
    }

    /** Id unit di server — dibutuhkan untuk menghapus baris yang sudah tersinkron. */
    private suspend fun serverUnitId(sessionId: String, serialNumber: String): String? {
        val listed = call("Gagal memuat unit") { api.listOpnameUnits(sessionId) }
        return (listed as? AuthResult.Success)
            ?.data
            ?.items
            ?.firstOrNull { it.serialNumber.equals(serialNumber, ignoreCase = true) }
            ?.id
    }

    /**
     * Tutup sesi: pastikan antrean terkirim dulu, baru complete. Buffer dibuang hanya
     * setelah server benar-benar menutup sesi.
     */
    suspend fun finalize(sessionId: String): AuthResult<OpnameDetailDto> {
        when (val pushed = pushPending(sessionId)) {
            is AuthResult.Failure -> return pushed
            is AuthResult.Success -> Unit
        }
        val completed = call<OpnameDetailDto>("Gagal menyelesaikan sesi") { api.completeOpname(sessionId) }
        if (completed is AuthResult.Success) {
            unitDao.clearSession(sessionId)
        }
        return completed
    }

    suspend fun cancel(id: String): AuthResult<OpnameDetailDto> {
        val result = call<OpnameDetailDto>("Gagal membatalkan sesi") { api.cancelOpname(id) }
        if (result is AuthResult.Success) {
            unitDao.clearSession(id)
        }
        return result
    }

    /** Hapus permanen sesi yang sudah dibatalkan. Server menegakkan status; klien cuma menampilkan tombolnya. */
    suspend fun deleteSession(id: String): AuthResult<Unit> {
        val result = call<OpnameDeleteData>("Gagal menghapus sesi") { api.deleteOpname(id) }
        if (result is AuthResult.Success) {
            unitDao.clearSession(id)
        }
        return when (result) {
            is AuthResult.Success -> AuthResult.Success(Unit)
            is AuthResult.Failure -> result
        }
    }

    private suspend fun <T> call(
        fallback: String,
        block: suspend () -> Response<ApiResponse<T>>
    ): AuthResult<T> {
        return try {
            val response = block()
            val data = response.body()?.data
            if (response.isSuccessful && data != null) {
                AuthResult.Success(data)
            } else {
                parseError(response, fallback)
            }
        } catch (e: Exception) {
            AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
        }
    }

    /** Same shape as InventoryRepository.parseError: validation detail in errors[0] wins. */
    private fun <T> parseError(response: Response<*>, fallback: String): AuthResult<T> {
        val raw = response.errorBody()?.string()
        val parsed = raw?.let {
            runCatching { errorJson.decodeFromString(ApiErrorResponse.serializer(), it) }.getOrNull()
        }
        val detail = parsed?.errors?.firstOrNull() ?: parsed?.message
        return AuthResult.Failure(
            parsed?.code ?: "http_${response.code()}",
            detail ?: "$fallback (${response.code()})",
            // Status HTTP dibawa terpisah dari `code`: `code` bisa sama untuk
            // status yang berbeda (`gateway_error` = 404/502/503), dan justru
            // pembedaan permanen-vs-sementara yang bergantung padanya.
            httpStatus = response.code()
        )
    }
}
