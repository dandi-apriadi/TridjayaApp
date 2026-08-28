package com.krisoft.tridjayaelektronik.ui.deliveryflow

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krisoft.tridjayaelektronik.data.AuthRepository
import com.krisoft.tridjayaelektronik.data.AuthResult
import com.krisoft.tridjayaelektronik.data.DeliveryFlowRepository
import com.krisoft.tridjayaelektronik.data.SpkTodayCounter
import com.krisoft.tridjayaelektronik.data.model.SerialRegistryRow
import com.krisoft.tridjayaelektronik.data.model.AssignBody
import com.krisoft.tridjayaelektronik.data.model.ConfirmSpkBody
import com.krisoft.tridjayaelektronik.data.model.CreateDeliveryBody
import com.krisoft.tridjayaelektronik.data.model.CreateDeliveryItemBody
import com.krisoft.tridjayaelektronik.data.model.DeliverBody
import com.krisoft.tridjayaelektronik.data.model.DeliveryJobDto
import com.krisoft.tridjayaelektronik.data.model.SaringanAntrian
import com.krisoft.tridjayaelektronik.data.model.DeliveryNoteBody
import com.krisoft.tridjayaelektronik.data.model.PdiBody
import com.krisoft.tridjayaelektronik.data.model.PdiChecklistItemBody
import com.krisoft.tridjayaelektronik.domain.sales.KlasemenStandings
import com.krisoft.tridjayaelektronik.ui.attendance.LocationProvider
import com.krisoft.tridjayaelektronik.util.PhotoWatermark
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/** Keadaan foto bukti aki satu form. `Kosong` = `photoUrl` null/blank (form
 *  sebelum fitur foto, atau PDI tak mengunggah); `Gagal` = URL ada tapi
 *  file/jaringannya tidak menjawab — dua hal yang WAJIB terlihat berbeda oleh
 *  approver. */
/** Form mana yang fotonya perlu diambil. Dipisah jadi fungsi murni supaya
 *  invarian intinya bisa dikunci tanpa memalsukan repository: form BER-URL
 *  selalu dapat entri di peta status (jadi kartunya menampilkan Memuat/Ada/
 *  Gagal, tak pernah diam), form tanpa URL sengaja TIDAK dapat entri — itulah
 *  yang dibaca kartu sebagai "tanpa foto bukti". */
internal fun akiFormsNeedingPhoto(
    forms: List<com.krisoft.tridjayaelektronik.data.model.AkiFormDto>
): List<com.krisoft.tridjayaelektronik.data.model.AkiFormDto> =
    forms.filter { !it.photoUrl.isNullOrBlank() }

sealed interface AkiPhotoState {
    data object Memuat : AkiPhotoState
    data class Ada(val bitmap: Bitmap) : AkiPhotoState
    data object Gagal : AkiPhotoState
}

data class DeliveryFlowUiState(
    val loading: Boolean = false,
    val items: List<DeliveryJobDto> = emptyList(),
    /**
     * Baris yang lolos saringan SEBELUM `LIMIT` (server `total`). `null` =
     * server lama yang belum mengirimnya — perlakukan sebagai "tidak tahu",
     * bukan nol. Dipakai indikator "Menampilkan N dari M".
     */
    val totalAntrian: Int? = null,
    val detail: DeliveryJobDto? = null,
    /** Karyawan yang sudah menangani unit yang sedang dibuka. Gagal dimuat =
     *  dibiarkan kosong tanpa pesan error: ini informasi pelengkap, tak boleh
     *  menutupi detail SPK yang justru jadi alasan orang membuka layar ini. */
    val kontributor: List<com.krisoft.tridjayaelektronik.data.model.KontributorDto> = emptyList(),
    val error: String? = null,
    val submitting: Boolean = false,
    val actionError: String? = null,
    val actionDone: Boolean = false,
    /** Checklist PDI per-kategori (untuk tahap pending_pdi). */
    val checklist: List<com.krisoft.tridjayaelektronik.data.model.ChecklistItemDto> = emptyList(),
    /** Daftar driver (untuk tahap pending_scheduling); kosong → form fallback input manual. */
    val drivers: List<com.krisoft.tridjayaelektronik.data.model.DriverDto> = emptyList(),
    /** Pengajuan diskon menunggu approval (layar approval diskon). */
    val discounts: List<com.krisoft.tridjayaelektronik.data.model.DiscountRequestDto> = emptyList(),
    /**
     * `total` dari server = jumlah SELURUH pengajuan yang cocok filter, yang
     * bisa LEBIH BANYAK dari [discounts] karena responsnya berhalaman
     * (`limit` 100, plafon backend). Disimpan terpisah supaya baris "menampilkan
     * N …" tak mengarang: `discounts.size` adalah isi HALAMAN, dan memakainya
     * sebagai jumlah membuat approver yakin sudah melihat semuanya.
     */
    val diskonTotal: Int = 0,
    /**
     * Id PENGAJUAN yang keputusannya sedang dikirim — BUKAN boolean global,
     * dan sejak 2026-08-07 bukan kode SPK lagi.
     * [submitting] mematikan tombol SEMUA kartu di antrian sekaligus, jadi
     * approver dengan 8 SPK menunggu harus menonton satu kartu selesai sebelum
     * bisa menyentuh yang lain, dan kartu yang tak ditekan pun terlihat rusak.
     * Kunci per-SPK pun sudah terlalu lebar: keputusan sekarang per BARANG,
     * jadi mengunci se-kartu membuat 9 barang lain menunggu 1 barang terkirim.
     */
    val diskonSubmitting: Set<String> = emptySet(),
    /** Detail SPK yang sedang dibuka dari kartu diskon (satu slot: panelnya
     *  modal, hanya satu bisa terbuka). Kode-nya dipegang supaya panel bisa
     *  memperlihatkan judul & keadaan memuat sebelum datanya sampai. */
    val spkDiskonDetailKode: String? = null,
    val spkDiskonDetail: com.krisoft.tridjayaelektronik.data.model.SpkDiscountContextDto? = null,
    val spkDiskonDetailError: String? = null,
    /**
     * Pengajuan diskon SPK yang sedang dibuka detailnya.
     *
     * TERPISAH dari [discounts] karena layar detail punya ViewModel SENDIRI
     * (di-scope ke NavBackStackEntry oleh `hiltViewModel()`), jadi ia TIDAK
     * mewarisi antrian approval layar sebelumnya — membacanya dari [discounts]
     * selalu menghasilkan daftar kosong tanpa satu pun error, dan baris
     * "bila disetujui" tak akan pernah muncul.
     *
     * Kosong = belum termuat ATAU memang tak ada pengajuan; keduanya sama-sama
     * berarti "tak ada angka yang bisa ditampilkan", jadi tak perlu dibedakan.
     */
    val spkDiskonPengajuan: List<com.krisoft.tridjayaelektronik.data.model.DiscountRequestDto> = emptyList(),
    /** Konteks cabang login sales — default selektor Cabang SPK (Input SPK). */
    val deliveryContext: com.krisoft.tridjayaelektronik.data.model.DeliveryContextDto? = null,
    /** Hasil autocomplete stok GS (Input SPK). */
    val stokResults: List<com.krisoft.tridjayaelektronik.data.model.StokCabangRow> = emptyList(),
    /**
     * Cabang ASAL [stokResults] — baris stok tak membawa kodeDealer sendiri,
     * jadi tanpa penanda ini daftar di layar tak bisa dibedakan milik cabang
     * mana. Layar Input SPK hanya menampilkan hasil yang cabangnya sama dengan
     * "Cabang SPK" saat itu (insiden DLV-M84149DA0, 2026-07-29: barang Pagaden
     * ter-submit dengan kode dealer Soklat, unitnya masuk antrian PDI cabang
     * yang tak memegang barangnya).
     */
    val stokDealer: String = "",
    val stokLoading: Boolean = false,
    val stokAttempted: Boolean = false,
    /** Hasil autocomplete broker KBK (Input SPK section 3). */
    val brokerResults: List<com.krisoft.tridjayaelektronik.data.model.BrokerOption> = emptyList(),
    /** Serial per `"$kodeDealer|$kodeBarang"` — picker per-item SPK multi-unit. */
    val serialOptions: Map<String, List<SerialRegistryRow>> = emptyMap(),
    /** Checklist serah-terima stage=driver (088) — kosong bila kategori tak ber-item / pre-088. */
    val driverChecklist: List<com.krisoft.tridjayaelektronik.data.model.ChecklistItemDto> = emptyList(),
    /** Gagal memuat checklist driver — FAIL-HARD: submit serah terima diblok sampai
     *  retry sukses (checklist null terkirim = 400 backend tanpa petunjuk). */
    val driverChecklistError: String? = null,
    /** Foto job ter-autentikasi utk ditampilkan di detail (key "pdi"/"delivery"/"cash"). */
    val jobPhotos: Map<String, Bitmap> = emptyMap(),
    /** Gate form aki (tahap pending_pdi, kategori ber-flag `requiresAkiForm`). */
    val requiresAki: Boolean = false,
    val akiForms: List<com.krisoft.tridjayaelektronik.data.model.AkiFormDto> = emptyList(),
    /** Riwayat diskon baris SPK yang sedang dibuka — HANYA untuk timeline detail
     *  (beda dari [discounts] yang antrian approval). Kosong = tak pernah diajukan
     *  diskon, atau job lama dari worker GS (tak punya kode batch manual). */
    val jobDiscounts: List<com.krisoft.tridjayaelektronik.data.model.DiscountRequestDto> = emptyList(),
    /**
     * Unit SAUDARA se-SPK dari job yang sedang dibuka, termasuk job itu sendiri
     * — hanya diisi untuk tahap yang keputusannya memang per SPK (saat ini:
     * konfirmasi kasir). Kosong = belum termuat / gagal / tahap lain; pemakainya
     * WAJIB jatuh balik ke `listOf(detail)` supaya layar tetap bekerja sebagai
     * satu unit, persis seperti sebelum fitur ini ada.
     */
    val batchUnits: List<DeliveryJobDto> = emptyList(),
    /**
     * Form pengambilan aki SELURUH unit SPK yang sedang dibuka — sumber baris
     * "kelengkapan" (baterai/charger/kaca spion) di daftar barang.
     *
     * Beda dari [akiForms] yang di-scope SATU job dan hanya dimuat di tahap
     * PDI: yang ini se-SPK dan di semua tahap, karena kelengkapan itu bagian
     * dari isi penjualan, bukan urusan tahap PDI saja.
     *
     * FAIL-SOFT: `GET /delivery/{id}/aki-form` di-gate `can_use_aki ||
     * may_do_pdi_work`, jadi kasir/DC/driver dijawab 403 — daftarnya dibiarkan
     * kosong tanpa pesan error, dan kartu SPK kembali menampilkan unit fisik
     * saja. Menampilkan error di sini akan menutupi detail SPK yang justru jadi
     * alasan orang membuka layarnya.
     */
    val batchAkiForms: List<com.krisoft.tridjayaelektronik.data.model.AkiFormDto> = emptyList(),
    /** Daftar riwayat (menu "Pengambilan Aki", beda dari [akiForms] yang di-scope satu job). */
    val akiList: List<com.krisoft.tridjayaelektronik.data.model.AkiFormDto> = emptyList(),
    /** Status foto bukti aki per form id (key = form.id). Sengaja BUKAN
     *  `Map<String, Bitmap>` lagi: peta bitmap tak bisa membedakan "PDI memang
     *  tak memotret" dari "filenya gagal diambil", sehingga approver hanya
     *  melihat kartu kosong dan tak tahu harus menagih siapa. Insiden
     *  2026-07-29: 5 foto raib dari server, gejalanya identik dengan form lama
     *  yang wajar tanpa foto. */
    val akiPhotos: Map<String, AkiPhotoState> = emptyMap(),
    /** Status foto bukti acc diskon per pengajuan (key = `DiscountRequestDto.id`) —
     *  pola sama [akiPhotos]. */
    val diskonBuktiPhotos: Map<String, AkiPhotoState> = emptyMap(),
    /** Preview foto (sudah ber-watermark geotag+jam) — pola sama [AttendanceUiState.selfie]:
     *  bitmap dipegang di state, BUKAN dibaca ulang dari file (hindari cache-basi/race preview). */
    /** Hasil `POST /delivery` terakhir (2026-07-26) — dipakai `CreateSpkScreen` buat
     *  resolve id job PDI Mandiri dan auto-navigate langsung ke form PDI, tanpa
     *  balik dulu ke daftar. */
    val lastCreateResult: com.krisoft.tridjayaelektronik.data.model.DeliveryCreateResult? = null,
    val pdiPhoto: Bitmap? = null,
    val deliverPhoto: Bitmap? = null,
    val cashPhoto: Bitmap? = null,
    /** true setelah user tekan "Pakai Foto Ini" di dialog review pasca-jepret. Foto baru (belum
     *  di-retake) selalu mulai false → memaksa dialog review muncul sebelum foto dianggap final. */
    val pdiPhotoConfirmed: Boolean = false,
    val deliverPhotoConfirmed: Boolean = false,
    val cashPhotoConfirmed: Boolean = false,
    /** GPS untuk watermark foto — pola sama [com.krisoft.tridjayaelektronik.ui.attendance.AttendanceUiState]:
     *  diambil LEBIH AWAL (saat detail job dimuat), bukan baru dicoba saat jepret — kalau baru
     *  dicoba pas jepret, GPS cold-start belum sempat lock (ketemu nyata: PDI selalu "belum terkunci"). */
    val gpsLat: Double? = null,
    val gpsLng: Double? = null,
    val gpsAccuracyM: Float? = null,
    val gpsLocating: Boolean = false,
    val gpsError: String? = null,
    val gpsDenied: Boolean = false,
    /** Alamat terbaca hasil reverse-geocode (kota/kabupaten/jalan) — `null` selama proses / gagal
     *  (offline dsb.); UI+watermark fallback ke koordinat mentah saat itu. */
    val gpsAddress: String? = null,
    /** true selagi [refreshGps] menunggu hasil geocode — terpisah dari [gpsLocating] karena fix GPS
     *  biasanya selesai duluan, lookup alamat masih jalan beberapa saat lagi di background. */
    val gpsAddressLoading: Boolean = false
)

/**
 * Alur pengiriman SPK NYATA — satu VM dipakai layar antrian per-tahap & detail, lewat
 * [DeliveryFlowRepository] (inventory-service). Tanpa cache: tiap load memanggil server; tiap aksi
 * tahap memutakhirkan job lalu memicu kembali ke daftar.
 */
@HiltViewModel
class DeliveryFlowViewModel @Inject constructor(
    private val repository: DeliveryFlowRepository,
    authRepository: AuthRepository,
    private val spkTodayCounter: SpkTodayCounter,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _state = MutableStateFlow(DeliveryFlowUiState())
    val state: StateFlow<DeliveryFlowUiState> = _state.asStateFlow()

    val currentUserName: String = authRepository.currentUserName?.trim().orEmpty().ifBlank { "Pengguna" }
    val currentUserId: String = authRepository.currentUserId?.trim().orEmpty()

    // ── Akses viewer (SpkAccessPolicy — mirror gate backend, backend tetap
    // otoritatif). REAKTIF (temuan review): dihitung dari cache saat konstruksi,
    // lalu di-refresh SEKALI dari server — approver page-grant/extra-role yang
    // di-grant SETELAH cache profil terbentuk tak kehilangan tombol.
    var isAdminViewer by androidx.compose.runtime.mutableStateOf(false)
        private set
    var canApproveAki by androidx.compose.runtime.mutableStateOf(false)
        private set
    /** Akses per-tahap (dipakai menyaring aksi di layar detail job). */
    var access by androidx.compose.runtime.mutableStateOf(SpkAccessPolicy.accessOf(null))
        private set

    private fun recomputeAccess(user: com.krisoft.tridjayaelektronik.data.model.UserDto?) {
        val roles = SpkAccessPolicy.rolesOf(user)
        val grants = SpkAccessPolicy.grantPrefixesOf(user)
        isAdminViewer = SpkAccessPolicy.isAdmin(roles)
        canApproveAki = SpkAccessPolicy.canApproveAki(roles, grants)
        access = SpkAccessPolicy.accessOf(user)
    }

    init {
        recomputeAccess(authRepository.cachedUser)
        // Refresh profil dari server SEKALI PER PROSES APP (bukan per layar —
        // VM ini dibuat ulang tiap buka layar delivery; refresh tiap kali =
        // 1 roundtrip ekstra per navigasi, terasa di jaringan lapangan).
        // Cache TokenStore sudah ter-update oleh refresh pertama.
        if (!accessProfileRefreshed) {
            accessProfileRefreshed = true
            viewModelScope.launch {
                // profile() meng-update TokenStore + fallback ke cache saat offline.
                (authRepository.profile() as? AuthResult.Success)?.let { recomputeAccess(it.data) }
            }
        }
    }

    companion object {
        /** Sekali per proses — lihat init. Login ulang me-restart proses (reset otomatis). */
        @Volatile
        private var accessProfileRefreshed = false
    }

    /** Foto serah-terima terkompres siap upload (dipisah dari state). */
    private var deliverPhotoBytes: ByteArray? = null

    /**
     * URL foto bukti setoran yang SUDAH terunggah, ditahan supaya percobaan
     * ulang [setoranKasirSpk] setelah kiriman separuh tidak mengunggah foto yang
     * sama dua kali. Dikosongkan tiap slot fotonya berubah (jepret ulang / ganti
     * job) — kalau tidak, foto SPK sebelumnya ikut terkirim ke SPK berikutnya.
     */
    private var setoranPhotoUrl: String? = null
    private var pdiPhotoBytes: ByteArray? = null
    private var cashPhotoBytes: ByteArray? = null

    private val serialFetched = mutableSetOf<String>()

    fun loadQueue(
        status: String?,
        view: String? = null,
        asDriver: Boolean = false,
        dari: String? = null,
        sampai: String? = null,
        saringan: SaringanAntrian = SaringanAntrian.KOSONG,
    ) {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (
                val res = repository.listDenganTotal(
                    status = status,
                    view = view,
                    asDriver = asDriver,
                    dari = dari,
                    sampai = sampai,
                    saringan = saringan,
                )
            ) {
                is AuthResult.Success -> _state.update {
                    it.copy(
                        loading = false,
                        items = res.data.items,
                        // `total` server, BUKAN `items.size` — daftar dipotong
                        // di 200 dan tanpa angka ini layar tak punya cara tahu.
                        totalAntrian = res.data.total,
                        error = null,
                    )
                }
                is AuthResult.Failure -> _state.update { it.copy(loading = false, error = res.message) }
            }
        }
        // Antrian PDI butuh `barangBesarThreshold` untuk memisah barang besar
        // (PDI per unit) dari barang kecil (satu klik se-SPK). Cached +
        // fail-soft: gagal = ambang null = SEMUA unit dianggap besar, antrian
        // kembali ke perilaku per unit yang lama, bukan salah menawarkan jalur
        // massal.
        loadDeliveryContextForCreate()
    }

    /** Geser urutan muatan driver (manifest). Optimistic; gagal → reload + error. */
    /**
     * Geser urutan muatan driver SATU SPK PENUH (2026-08-06), bukan satu unit.
     *
     * Manifest driver kini satu kartu per SPK — satu konsumen, satu alamat, satu
     * pemberhentian. Mengurutkan per unit tak pernah masuk akal di lapangan:
     * dua barang untuk rumah yang sama tak bisa dimuat di urutan berjauhan.
     *
     * Kontrak servernya TIDAK berubah: `POST /delivery/driver/reorder` tetap
     * menerima daftar ID UNIT: grup ditukar posisinya, lalu diratakan lagi jadi
     * urutan id. Jadi seluruh unit satu SPK selalu berdampingan — yang justru
     * mustahil dijamin oleh penggeseran per unit yang lama.
     *
     * Optimistic: daftar lokal digeser lebih dulu; gagal → pesan + muat ulang.
     */
    fun moveLoadSpk(kode: String, up: Boolean) {
        val groups = groupJobsBySpk(_state.value.items)
        val idx = groups.indexOfFirst { it.kode == kode }
        val target = if (up) idx - 1 else idx + 1
        if (idx == -1 || target < 0 || target >= groups.size) return
        val swapped = groups.toMutableList().apply { val t = this[idx]; this[idx] = this[target]; this[target] = t }
        val rata = swapped.flatMap { it.jobs }
        _state.update { it.copy(items = rata) }
        viewModelScope.launch {
            when (val res = repository.reorderLoads(rata.map { it.id })) {
                is AuthResult.Success -> {}
                is AuthResult.Failure -> {
                    _state.update { it.copy(actionError = res.message) }
                    loadQueue(status = null, view = null)
                }
            }
        }
    }

    fun loadDetail(id: String) {
        _state.update {
            it.copy(
                kontributor = emptyList(), driverChecklist = emptyList(), batchUnits = emptyList(), batchAkiForms = emptyList(),
                driverChecklistError = null, jobPhotos = emptyMap(),
                pdiPhoto = null, deliverPhoto = null, cashPhoto = null,
                pdiPhotoConfirmed = false, deliverPhotoConfirmed = false, cashPhotoConfirmed = false
            )
        }
        deliverPhotoBytes = null
        pdiPhotoBytes = null
        cashPhotoBytes = null
        setoranPhotoUrl = null
        refreshDetail(id)
    }

    /**
     * Muat ulang detail + timeline + kontributor job yang SEDANG dibuka —
     * dipakai tarik-turun di layar detail.
     *
     * Sengaja BUKAN [loadDetail]: fungsi itu mengosongkan slot foto yang sudah
     * dijepret tapi belum terkirim (`pdiPhoto`/`deliverPhoto`/`cashPhoto` +
     * bytes-nya + flag `*Confirmed`). Benar saat berpindah job, tapi mematikan
     * sebagai refresh: satu tarikan tak sengaja setelah petugas memotret bukti
     * serah terima = bukti hilang tanpa satu pun peringatan.
     *
     * Kegagalan dilaporkan lewat `actionError`, bukan `error` — `error` membuat
     * layar detail jatuh ke keadaan "Data tidak ditemukan" dan menyembunyikan
     * foto yang masih tertahan di slot.
     */
    fun refreshDetail(id: String) {
        if (_state.value.loading) return
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            when (val res = repository.detail(id)) {
                is AuthResult.Success -> {
                    _state.update { it.copy(loading = false, detail = res.data, error = null) }
                    loadAuxFor(res.data)
                    loadBatchUnits(res.data)
                    loadTimelineExtras(res.data)
                    loadJobPhotos(res.data)
                    loadKontributor(id)
                }
                is AuthResult.Failure -> _state.update { it.copy(loading = false, actionError = res.message) }
            }
        }
        // Dua panggilan ini datang dari implementasi kembar yang dibuang saat
        // penggabungan 2026-08-07 (dua sesi menulis `refreshDetail` sendiri-sendiri).
        // Konteks dibutuhkan layar detail untuk flag `driverGateEnabled` — gate
        // serah-terima klien mengikuti kill-switch server, jadi tanpa ini tarik-turun
        // meninggalkan gate memakai nilai basi. Keduanya cached & fail-soft.
        loadDeliveryContextForCreate()
        refreshGps()
    }

    /** Muat foto job ter-autentikasi (bukti PDI / serah terima / uang) utk preview
     *  di detail — fail-soft per foto (gagal = tak tampil, tanpa error). */
    /** Fail-soft: gagal = daftar dibiarkan kosong, layar detail tetap utuh. */
    private fun loadKontributor(id: String) {
        viewModelScope.launch {
            when (val res = repository.kontributor(id)) {
                is AuthResult.Success -> _state.update { it.copy(kontributor = res.data) }
                is AuthResult.Failure -> Unit
            }
        }
    }

    private fun loadJobPhotos(job: DeliveryJobDto) {
        val urls = listOfNotNull(
            job.pdiReadyPhotoUrl?.takeIf { it.isNotBlank() }?.let { "pdi" to it },
            job.deliveryPhotoUrl?.takeIf { it.isNotBlank() }?.let { "delivery" to it },
            job.cashPhotoUrl?.takeIf { it.isNotBlank() }?.let { "cash" to it },
        )
        urls.forEach { (key, url) ->
            viewModelScope.launch {
                val bytes = repository.fetchPhoto(url) ?: return@launch
                val bmp = withContext(Dispatchers.Default) {
                    android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                } ?: return@launch
                _state.update { it.copy(jobPhotos = it.jobPhotos + (key to bmp)) }
            }
        }
    }

    /** Ambil satu titik GPS lebih awal (dipakai watermark foto PDI/serah-terima/uang saat jepret). */
    fun refreshGps() {
        if (_state.value.gpsLocating) return
        _state.update { it.copy(gpsLocating = true, gpsError = null, gpsDenied = false, gpsAddress = null, gpsAddressLoading = false) }
        viewModelScope.launch {
            if (!LocationProvider.hasPermission(appContext)) {
                _state.update { it.copy(gpsLocating = false, gpsDenied = true) }
                return@launch
            }
            val loc = LocationProvider.current(appContext)
            if (loc == null) {
                _state.update { it.copy(gpsLocating = false, gpsError = "Tidak bisa mendapatkan lokasi. Pastikan GPS aktif.") }
            } else {
                _state.update {
                    it.copy(
                        gpsLocating = false, gpsError = null,
                        gpsLat = loc.latitude, gpsLng = loc.longitude,
                        gpsAccuracyM = if (loc.hasAccuracy()) loc.accuracy else null,
                        gpsAddressLoading = true
                    )
                }
                // Alamat terbaca (kota/kabupaten/tempat) dicari terpisah, tak menahan fix GPS —
                // gagal/lambat (offline dsb.) tetap fail-soft, UI+watermark fallback ke koordinat.
                val address = LocationProvider.addressFor(appContext, loc.latitude, loc.longitude)
                _state.update { it.copy(gpsAddress = address, gpsAddressLoading = false) }
            }
        }
    }

    /**
     * Data timeline yang hidup di TABEL SAMPING — approval diskon
     * (`discount_requests`) dan form aki (`delivery_aki_forms`). Dimuat untuk
     * SEMUA status, sengaja TERPISAH dari [loadAuxFor] yang stage-specific:
     * approval diskon terjadi di `pending_discount` dan form aki di
     * `pending_pdi`, tapi keduanya harus tetap kelihatan di timeline setelah
     * tahapnya lewat. Bug 2026-07-27: form aki cuma dimuat saat status
     * `pending_pdi`, jadi SPK sepeda listrik yang tertahan di
     * `pending_discount` tak pernah menampilkan approval aki maupun diskon.
     *
     * Fail-soft penuh: gagal/kosong = step-nya tak muncul, detail tetap kebuka.
     */
    private fun loadTimelineExtras(job: DeliveryJobDto) {
        viewModelScope.launch {
            val forms = (repository.jobAkiForms(job.id) as? AuthResult.Success)?.data.orEmpty()
            _state.update { it.copy(akiForms = if (it.akiForms.isEmpty()) forms else it.akiForms) }
        }
        // Kode batch cuma ada di job input manual sales (`DLV-M{batch}-{baris}u{seq}`);
        // job worker GS lama tak pernah punya discount_request.
        val baris = job.baris ?: return
        val unitSeq = job.unitSeq ?: return
        val suffix = "-${baris}u$unitSeq"
        if (job.inputChannel != "manual" || !job.kodePengiriman.endsWith(suffix)) return
        val batch = job.kodePengiriman.removeSuffix(suffix)
        viewModelScope.launch {
            val history = (repository.discountHistory(batch, baris) as? AuthResult.Success)?.data.orEmpty()
            _state.update { it.copy(jobDiscounts = history) }
        }
    }

    /**
     * Muat unit saudara se-SPK untuk job yang sedang dibuka. Dipanggil di SEMUA
     * tahap sejak 2026-08-06.
     *
     * Dulu hanya `pending_spk` (satu-satunya tahap yang isiannya butuh daftar
     * ini: `units[]` menuntut nominal DP tiap unit COD `dp`). Dilebarkan setelah
     * antrian Riwayat & Konfirmasi Pembayaran ikut tampil satu baris per SPK:
     * kartunya menjanjikan "buka untuk rinciannya", lalu layar detail cuma
     * memperlihatkan SATU barang — janji yang tak pernah ditepati. Seksi "Total"
     * lebih parah lagi: ia menulis "1 unit" dan menjumlah harga satu barang saja
     * untuk SPK berisi tiga, yaitu angka salah yang tidak terlihat salah.
     *
     * DUA sumber, dan bedanya bukan selera:
     * - `pending_spk` → `?status=pending_spk`. Himpunannya WAJIB sama persis
     *   dengan `siblings` yang divalidasi `confirm_spk` (unit sebatch berstatus
     *   `pending_spk` dalam scope cabang). Memakai riwayat di sini akan menarik
     *   unit yang TIDAK ikut dikonfirmasi, lalu form menagih nominal DP untuk
     *   unit itu dan tombolnya terkunci selamanya.
     * - tahap lain → `?view=history` (semua status dalam scope role). Ini yang
     *   memberi gambaran SPK utuh untuk daftar barang + total.
     *
     * `?q=` TIDAK dipakai walau backend punya pencarian bebas atas
     * `kode_pengiriman`: `filter.q` hanya diisi di cabang admin/manager
     * (`delivery.rs`), sengaja, supaya kotak cari tak jadi pintu enumerasi SPK
     * lintas cabang. Mengirimkannya sebagai role cabang cuma diabaikan diam-diam.
     *
     * ONGKOS: satu request tambahan tiap membuka detail. Tak di-cache — antrian
     * bergerak (petugas lain ikut memproses), dan daftar basi di sini berarti
     * `units[]` yang salah.
     *
     * FAIL-SOFT: gagal / kosong = `batchUnits` dibiarkan kosong dan layar jatuh
     * balik ke satu unit (perilaku lama). Riwayat di luar 200 baris terakhir
     * juga mendarat di sini — tak lengkap, tapi tak pernah salah alamat.
     */
    private fun loadBatchUnits(job: DeliveryJobDto) {
        val kasir = job.status == com.krisoft.tridjayaelektronik.data.model.DeliveryStatusKey.PENDING_SPK
        val prefix = spkBatchPrefix(job.kodePengiriman)
        viewModelScope.launch {
            val res = if (kasir) repository.list(status = job.status) else repository.list(view = "history")
            val semua = (res as? AuthResult.Success)?.data.orEmpty()
            val sebatch = semua.filter { spkBatchPrefix(it.kodePengiriman) == prefix }
            // Job yang dibuka WAJIB ada di daftar walau antrian tak memuatnya
            // (halaman terpotong / baru berpindah status) — kalau tidak, layar
            // memperlihatkan SPK tanpa unit yang justru sedang dibaca orangnya.
            val lengkap = if (sebatch.any { it.id == job.id }) sebatch else listOf(job) + sebatch
            _state.update { it.copy(batchUnits = lengkap) }
            loadBatchAkiForms(lengkap)
        }
    }

    /**
     * Form aki SELURUH unit SPK — sumber baris kelengkapan (baterai/charger/
     * kaca spion) di daftar barang.
     *
     * Satu request PER UNIT karena endpoint-nya memang per job
     * (`GET /delivery/{id}/aki-form`); tak ada rute "form aki se-batch". N di
     * sini jumlah barang satu SPK — praktis 1-5, bukan skala yang perlu
     * dioptimasi. Dijalankan paralel dan tiap unit fail-soft sendiri, jadi satu
     * unit yang dijawab 403 (kasir/DC/driver tak berhak baca form aki) tidak
     * menghapus kelengkapan unit lain yang berhasil dibaca.
     */
    private fun loadBatchAkiForms(units: List<DeliveryJobDto>) {
        if (units.isEmpty()) return
        viewModelScope.launch {
            val semua = units.map { u ->
                async { (repository.jobAkiForms(u.id) as? AuthResult.Success)?.data.orEmpty() }
            }.flatMap { it.await() }
            // Satu job bisa punya beberapa form (pengajuan ulang setelah
            // ditolak); dedup by id supaya baris kelengkapan tak berlipat kalau
            // dua unit entah bagaimana mengembalikan form yang sama.
            _state.update { it.copy(batchAkiForms = semua.distinctBy { f -> f.id }) }
        }
    }

    /** Muat data pendukung sesuai tahap: checklist PDI (pending_pdi) atau daftar driver (pending_scheduling). */
    private fun loadAuxFor(job: DeliveryJobDto) {
        when (job.status) {
            com.krisoft.tridjayaelektronik.data.model.DeliveryStatusKey.PENDING_PDI,
            com.krisoft.tridjayaelektronik.data.model.DeliveryStatusKey.PENDING_PERBAIKAN -> {
                val kategori = job.kategori?.trim().orEmpty()
                if (kategori.isNotEmpty()) viewModelScope.launch {
                    (repository.checklist(kategori) as? AuthResult.Success)?.let { r -> _state.update { it.copy(checklist = r.data) } }
                }
                viewModelScope.launch {
                    val cats = (repository.categories() as? AuthResult.Success)?.data.orEmpty()
                    val need = cats.any { it.requiresAkiForm && it.kategori.equals(job.kategori?.trim(), ignoreCase = true) }
                    val forms = if (need) (repository.jobAkiForms(job.id) as? AuthResult.Success)?.data.orEmpty() else emptyList()
                    _state.update { it.copy(requiresAki = need, akiForms = forms) }
                }
            }
            com.krisoft.tridjayaelektronik.data.model.DeliveryStatusKey.PENDING_SCHEDULING -> viewModelScope.launch {
                (repository.drivers() as? AuthResult.Success)?.let { r -> _state.update { it.copy(drivers = r.data) } }
            }
            com.krisoft.tridjayaelektronik.data.model.DeliveryStatusKey.ASSIGNED,
            com.krisoft.tridjayaelektronik.data.model.DeliveryStatusKey.IN_TRANSIT ->
                loadDriverChecklist(job)
        }
    }

    /** Checklist serah-terima stage=driver (088) — FAIL-HARD: gagal fetch →
     *  `driverChecklistError` terisi, tombol serah terima diblok sampai retry
     *  sukses. Tanpa ini checklist null terkirim → 400 backend "Checklist serah
     *  terima driver wajib diisi" tanpa petunjuk di UI (temuan audit 2026-07-23). */
    fun loadDriverChecklist(job: DeliveryJobDto) {
        // 088 aktif? (driverTerimaUang selalu terisi pasca-088). Pre-088 JANGAN
        // fetch stage=driver — backend lama abaikan param & balik item PDI.
        val kategori = job.kategori?.trim().orEmpty()
        if (job.driverTerimaUang == null || kategori.isEmpty()) return
        _state.update { it.copy(driverChecklistError = null) }
        viewModelScope.launch {
            when (val r = repository.checklist(kategori, stage = "driver")) {
                is AuthResult.Success ->
                    _state.update { it.copy(driverChecklist = r.data, driverChecklistError = null) }
                is AuthResult.Failure ->
                    _state.update { it.copy(driverChecklistError = r.message) }
            }
        }
    }

    fun clearActionError() = _state.update { it.copy(actionError = null) }

    // ── Approval diskon per-baris ────────────────────────────────────────────
    fun loadDiscounts(status: String? = "pending", dari: String? = null, sampai: String? = null) {
        // Peta foto dikosongkan di sini, bukan cuma ditimpa: item yang sudah
        // diputuskan hilang dari antrian, dan bitmap-nya ikut dibuang.
        _state.update { it.copy(loading = true, error = null, diskonBuktiPhotos = emptyMap()) }
        viewModelScope.launch {
            when (val res = repository.discounts(status, dari = dari, sampai = sampai)) {
                is AuthResult.Success -> {
                    _state.update {
                        it.copy(
                            loading = false,
                            discounts = res.data.items,
                            diskonTotal = res.data.total,
                            error = null,
                        )
                    }
                    loadDiscountPhotos(res.data.items)
                }
                is AuthResult.Failure -> _state.update { it.copy(loading = false, error = res.message) }
            }
        }
    }

    /** Muat foto bukti acc diskon per pengajuan — pola sama [loadAkiPhotos].
     *  Tiga keadaan dibedakan (memuat / ada / gagal) supaya approver tahu
     *  bedanya "sales tak melampirkan" dan "filenya hilang dari server". */
    private fun loadDiscountPhotos(items: List<com.krisoft.tridjayaelektronik.data.model.DiscountRequestDto>) {
        items.filter { !it.buktiUrl.isNullOrBlank() }.forEach { d ->
            val url = d.buktiUrl.orEmpty()
            _state.update { it.copy(diskonBuktiPhotos = it.diskonBuktiPhotos + (d.id to AkiPhotoState.Memuat)) }
            viewModelScope.launch {
                val bytes = repository.fetchPhoto(url)
                val bmp = bytes?.let {
                    withContext(Dispatchers.Default) {
                        android.graphics.BitmapFactory.decodeByteArray(it, 0, it.size)
                    }
                }
                _state.update {
                    it.copy(
                        diskonBuktiPhotos = it.diskonBuktiPhotos +
                            (d.id to (bmp?.let(AkiPhotoState::Ada) ?: AkiPhotoState.Gagal))
                    )
                }
            }
        }
    }

    fun approveDiscount(id: String, note: String) =
        discountAction(id) { repository.approveDiscount(id, note) }

    fun rejectDiscount(id: String, note: String) =
        discountAction(id) { repository.rejectDiscount(id, note) }

    /** Buka panel detail SPK dari kartu diskon. Gagal = pesan DI DALAM panel,
     *  bukan `error` layar — antrian approval yang sudah termuat tak boleh
     *  tergantikan layar error karena panel pelengkap gagal dimuat. */
    fun bukaDetailSpkDiskon(spkKode: String) {
        _state.update {
            it.copy(
                spkDiskonDetailKode = spkKode,
                spkDiskonDetail = null,
                spkDiskonDetailError = null,
                spkDiskonPengajuan = emptyList(),
            )
        }
        viewModelScope.launch {
            when (val res = repository.spkDiscountContext(spkKode)) {
                is AuthResult.Success -> _state.update {
                    // Panel keburu ditutup / pindah SPK selagi request jalan =
                    // buang hasilnya, jangan memaksa panel terbuka lagi.
                    if (it.spkDiskonDetailKode == spkKode) it.copy(spkDiskonDetail = res.data) else it
                }
                is AuthResult.Failure -> _state.update {
                    if (it.spkDiskonDetailKode == spkKode) it.copy(spkDiskonDetailError = res.message) else it
                }
            }
        }
        // Pengajuan SPK-nya (SELURUH baris, `baris` tak dikirim) — sumber baris
        // "sedang diajukan"/"bila disetujui". FAIL-SOFT dan TANPA
        // `spkDiskonDetailError`: detail SPK tetap berguna tanpa angka ini,
        // dan menutupinya dengan layar error menukar satu kekurangan dengan
        // kekurangan yang lebih besar.
        viewModelScope.launch {
            val res = repository.discountHistory(spkKode)
            if (res is AuthResult.Success) {
                _state.update {
                    if (it.spkDiskonDetailKode == spkKode) it.copy(spkDiskonPengajuan = res.data) else it
                }
            }
        }
    }

    fun tutupDetailSpkDiskon() = _state.update {
        it.copy(
            spkDiskonDetailKode = null,
            spkDiskonDetail = null,
            spkDiskonDetailError = null,
            spkDiskonPengajuan = emptyList(),
        )
    }

    /**
     * Sales mengajukan ULANG diskon baris yang ditolak (2026-08-07) — jalan
     * keluar ketiga yang dulu cuma ada di web.
     *
     * `nilai` = rupiah TAMBAHAN di atas diskon yang sudah menempel (server
     * menjumlahkannya ke `diskon_current`), sama seperti web.
     */
    fun ajukanUlangDiskon(
        spkBatchKode: String,
        baris: Int,
        nilai: Double,
        alasan: String,
        jobId: String,
        onSukses: () -> Unit = {},
    ) {
        if (_state.value.submitting) return
        _state.update { it.copy(submitting = true, actionError = null) }
        viewModelScope.launch {
            val body = com.krisoft.tridjayaelektronik.data.model.CreateDiscountBody(
                spkBatchKode = spkBatchKode, baris = baris, value = nilai, reason = alasan,
            )
            when (val res = repository.ajukanDiskon(body)) {
                is AuthResult.Success -> {
                    _state.update { it.copy(submitting = false) }
                    onSukses()
                    // Muat ulang detail: status unit & riwayat diskon berubah,
                    // dan tanpa ini layar masih menawarkan "Lanjut Tanpa Diskon"
                    // atas pengajuan yang barusan digantikan.
                    loadDetail(jobId)
                }
                is AuthResult.Failure -> _state.update { it.copy(submitting = false, actionError = res.message) }
            }
        }
    }

    /**
     * Sales menyerah pada diskon yang ditolak: baris ini DITANDAI `dilepas`
     * (2026-08-07, membalik perilaku lama yang langsung melepas se-batch).
     * Unitnya baru pindah `pending_discount` → `pending_pdi` kalau SELURUH
     * barang SPK sudah tuntas — jadi jangan menjanjikan "SPK masuk PDI" di UI.
     *
     * Sengaja BUKAN [discountAction]: pemanggilnya layar DETAIL SPK, bukan
     * antrian approval, jadi memuat ulang antrian `pending` tak ada gunanya
     * sementara yang justru harus berubah adalah status unit yang sedang
     * dibuka. Tanpa `loadDetail` di sini, layar tetap memperlihatkan
     * `pending_discount` beserta tombolnya — persis gejala "tombolnya tak
     * bereaksi" walau servernya sudah memindahkan unitnya.
     */
    fun lanjutTanpaDiskon(discountId: String, jobId: String) {
        if (_state.value.submitting) return
        _state.update { it.copy(submitting = true, actionError = null) }
        viewModelScope.launch {
            when (val res = repository.lanjutTanpaDiskon(discountId)) {
                is AuthResult.Success -> {
                    _state.update { it.copy(submitting = false) }
                    loadDetail(jobId)
                }
                is AuthResult.Failure -> _state.update { it.copy(submitting = false, actionError = res.message) }
            }
        }
    }

    /**
     * Kunci per-PENGAJUAN (2026-08-07), bukan per-SPK dan bukan global: sejak
     * keputusan diambil per barang, mengunci se-kartu membuat 9 barang lain
     * menunggu 1 barang selesai terkirim. Tekanan kedua pada barang YANG SAMA
     * tetap ditolak — server menjawabnya "sudah diputuskan".
     *
     * Sukses TIDAK memuat ulang antrian melainkan MENAMBAL barisnya dengan DTO
     * balasan server (sudah ter-hydrate: `jobSummary`, `deliveryJobIds`,
     * harga). Muat ulang membuang barang yang barusan diputus dari antrian
     * `pending`, sehingga kartu kehilangan penyebut kemajuannya dan approver
     * tak pernah melihat "2 dari 3 barang tuntas" — persis informasi yang
     * paling ia butuhkan sekarang, karena SPK baru lanjut setelah semuanya
     * tuntas. Kartu yang seluruh barangnya tuntas hilang saat muat ulang
     * berikutnya, bukan seketika.
     */
    private fun discountAction(
        id: String,
        block: suspend () -> AuthResult<com.krisoft.tridjayaelektronik.data.model.DiscountRequestDto>,
    ) {
        if (id in _state.value.diskonSubmitting) return
        _state.update { it.copy(diskonSubmitting = it.diskonSubmitting + id, actionError = null) }
        viewModelScope.launch {
            val res = block()
            _state.update { it.copy(diskonSubmitting = it.diskonSubmitting - id) }
            when (res) {
                is AuthResult.Success -> _state.update { s ->
                    s.copy(discounts = s.discounts.map { if (it.id == res.data.id) res.data else it })
                }
                is AuthResult.Failure -> _state.update { it.copy(actionError = res.message) }
            }
        }
    }

    // ── Foto (PDI ready / serah terima / terima uang) — watermark geotag+jam, pola SAMA
    // [com.krisoft.tridjayaelektronik.ui.attendance.AttendanceViewModel.onSelfieCaptured]: preview
    // dipegang sebagai Bitmap DI STATE (bukan dibaca ulang dari file lewat Coil/AsyncImage) —
    // menghindari 2 footgun yang sempat ketemu di sini: (a) race kalau UI flip "foto siap" sebelum
    // watermark async selesai, (b) Coil meng-cache bitmap mentah berbasis path file yang isinya
    // berubah-ubah (file capture ditulis ulang tiap retake, key cache Coil tidak tahu itu).
    fun onPdiPhotoCaptured(file: File) = viewModelScope.launch {
        val prepared = watermarked(file, "TRIDJAYA · PDI")
        pdiPhotoBytes = prepared?.first
        _state.update { it.copy(pdiPhoto = prepared?.second, pdiPhotoConfirmed = false) }
    }

    fun hasPdiPhoto(): Boolean = pdiPhotoBytes != null

    /** User menekan "Pakai Foto Ini" di dialog review pasca-jepret. */
    fun confirmPdiPhoto() = _state.update { it.copy(pdiPhotoConfirmed = true) }

    /** User menekan "Ambil Ulang" — buang hasil jepretan, biar tombol kamera bisa dipakai lagi. */
    fun retakePdiPhoto() {
        pdiPhotoBytes = null
        _state.update { it.copy(pdiPhoto = null, pdiPhotoConfirmed = false) }
    }

    fun onDeliverPhotoCaptured(file: File) = viewModelScope.launch {
        val prepared = watermarked(file, "TRIDJAYA · SERAH TERIMA")
        deliverPhotoBytes = prepared?.first
        setoranPhotoUrl = null
        _state.update { it.copy(deliverPhoto = prepared?.second, deliverPhotoConfirmed = false) }
    }

    fun hasDeliverPhoto(): Boolean = deliverPhotoBytes != null

    fun confirmDeliverPhoto() = _state.update { it.copy(deliverPhotoConfirmed = true) }

    fun retakeDeliverPhoto() {
        deliverPhotoBytes = null
        setoranPhotoUrl = null
        _state.update { it.copy(deliverPhoto = null, deliverPhotoConfirmed = false) }
    }

    fun onCashPhotoCaptured(file: File) = viewModelScope.launch {
        val prepared = watermarked(file, "TRIDJAYA · TERIMA UANG")
        cashPhotoBytes = prepared?.first
        _state.update { it.copy(cashPhoto = prepared?.second, cashPhotoConfirmed = false) }
    }

    fun hasCashPhoto(): Boolean = cashPhotoBytes != null

    fun confirmCashPhoto() = _state.update { it.copy(cashPhotoConfirmed = true) }

    fun retakeCashPhoto() {
        cashPhotoBytes = null
        _state.update { it.copy(cashPhoto = null, cashPhotoConfirmed = false) }
    }

    /** Foto PO per-barang (2026-07-24, koreksi dari slot global — Pre Order
     *  melekat ke produk, SPK bisa multi-barang tiap satu foto sendiri).
     *  Watermark+upload langsung (bukan slot review terpisah spt PDI/deliver
     *  — tak ada GPS-timing kritis di sini, cukup capture→upload sekali jalan,
     *  pola sama web `uploadDeliveryPhoto` on-file-select). Return `null` kalau
     *  watermark/upload gagal — caller (kartu barang) tampilkan toast error.
     *  Subtitle watermark ikut fallback [watermarked] (nama user saja, kode
     *  SPK belum ada saat ini). */
    suspend fun uploadPoPhoto(file: File): String? {
        val prepared = watermarked(file, "TRIDJAYA · NO PO") ?: return null
        return when (val up = repository.uploadPhoto(prepared.first, "po_${System.currentTimeMillis()}.webp")) {
            is AuthResult.Success -> up.data
            is AuthResult.Failure -> null
        }
    }

    /** Foto bukti acc diskon (2026-08-01) — pola sama [uploadPoPhoto]:
     *  watermark lalu unggah ke endpoint foto delivery yang sama. Return
     *  `null` kalau gagal. */
    suspend fun uploadBuktiAccPhoto(file: File): String? {
        val prepared = watermarked(file, "TRIDJAYA · ACC DISKON") ?: return null
        return when (val up = repository.uploadPhoto(prepared.first, "acc_diskon_${System.currentTimeMillis()}.webp")) {
            is AuthResult.Success -> up.data
            is AuthResult.Failure -> null
        }
    }

    /** Foto bukti aki (2026-07-24, wajib) — capture→watermark→upload langsung,
     *  pola sama [uploadPoPhoto]. Return `null` kalau gagal. */
    suspend fun uploadAkiPhoto(file: File): String? {
        val prepared = watermarked(file, "TRIDJAYA · BUKTI AKI") ?: return null
        return when (val up = repository.uploadPhoto(prepared.first, "aki_${System.currentTimeMillis()}.webp")) {
            is AuthResult.Success -> up.data
            is AuthResult.Failure -> null
        }
    }

    /**
     * GPS best-effort: pakai titik yang SUDAH di-prime oleh [refreshGps] (dipanggil saat detail job
     * dimuat) — bukan menarik lokasi baru di sini. Gagal/izin ditolak → watermark timestamp saja,
     * JANGAN blokir foto. Subtitle = nama · kode SPK job aktif (kalau sudah termuat).
     */
    private suspend fun watermarked(file: File, title: String): Pair<ByteArray, Bitmap>? {
        val s = _state.value
        val kode = s.detail?.kodePengiriman.orEmpty()
        val subtitle = listOf(currentUserName, kode).filter { it.isNotBlank() }.joinToString(" · ")
        return withContext(Dispatchers.Default) {
            PhotoWatermark.prepareWatermarkedJpeg(file, s.gpsLat, s.gpsLng, title, subtitle, s.gpsAccuracyM, s.gpsAddress)
        }
    }

    // ── Aksi tahap ───────────────────────────────────────────────────────────

    // ── Input SPK: cabang + autocomplete stok ────────────────────────────────

    /** Muat konteks cabang login sekali (default selektor Cabang SPK). Fail-soft. */
    fun loadDeliveryContextForCreate() {
        if (_state.value.deliveryContext != null) return
        viewModelScope.launch {
            (repository.context() as? AuthResult.Success)?.let { r ->
                _state.update { it.copy(deliveryContext = r.data) }
            }
        }
    }

    /** Pencarian stok yang sedang berjalan — dibatalkan tiap pencarian baru.
     *  Tanpa ini respons cabang LAMA mendarat setelah user pindah cabang dan
     *  mengisi ulang daftar (lihat [DeliveryFlowUiState.stokDealer]). */
    private var stokJob: Job? = null

    /** Autocomplete barang — dipanggil UI setelah debounce. `query` < 2 char atau
     *  `kodeDealer` kosong → kosongkan hasil tanpa panggil server. */
    fun searchStok(query: String, kodeDealer: String) {
        stokJob?.cancel()
        val term = query.trim()
        val dealer = kodeDealer.trim()
        if (term.length < 2 || dealer.isBlank()) {
            _state.update {
                it.copy(stokResults = emptyList(), stokDealer = dealer, stokLoading = false, stokAttempted = false)
            }
            return
        }
        _state.update { it.copy(stokLoading = true) }
        stokJob = viewModelScope.launch {
            when (val res = repository.stokCabang(term, dealer)) {
                is AuthResult.Success -> _state.update {
                    it.copy(stokLoading = false, stokResults = res.data, stokDealer = dealer, stokAttempted = true)
                }
                is AuthResult.Failure -> _state.update {
                    it.copy(stokLoading = false, stokResults = emptyList(), stokDealer = dealer, stokAttempted = true)
                }
            }
        }
    }

    fun searchBrokers(q: String) {
        val term = q.trim()
        if (term.length < 2) { _state.update { it.copy(brokerResults = emptyList()) }; return }
        viewModelScope.launch {
            (repository.searchBrokers(term) as? AuthResult.Success)?.let { r ->
                _state.update { it.copy(brokerResults = r.data) }
            }
        }
    }

    fun clearBrokerResults() = _state.update { it.copy(brokerResults = emptyList()) }

    /** Fetch serial sekali per `cabang|kode` (cache); fail-soft. */
    fun ensureSerials(kodeDealer: String, kodeBarang: String) {
        if (kodeDealer.isBlank() || kodeBarang.isBlank()) return
        val key = "$kodeDealer|$kodeBarang"
        if (!serialFetched.add(key)) return
        viewModelScope.launch {
            (repository.serialNumbers(kodeDealer, kodeBarang) as? AuthResult.Success)?.let { r ->
                _state.update { it.copy(serialOptions = it.serialOptions + (key to r.data)) }
            }
        }
    }

    /** Reset cache serial (ganti cabang SPK). */
    fun clearSerialCache() {
        serialFetched.clear()
        _state.update { it.copy(serialOptions = emptyMap()) }
    }

    // Foto PO (2026-07-24, per-barang): sudah ter-upload & ter-set ke
    // `item.poPhotoUrl` masing-masing SEBELUM submit (lihat [uploadPoPhoto],
    // dipanggil kartu barang saat capture) — body di sini sudah lengkap.
    fun createSpk(body: CreateDeliveryBody) {
        if (_state.value.submitting) return
        _state.update { it.copy(submitting = true, actionError = null, actionDone = false, lastCreateResult = null) }
        viewModelScope.launch {
            when (val res = repository.create(body)) {
                is AuthResult.Success -> {
                    // Angka informatif kartu "Buat SPK" di layar Activity (lokal per-device).
                    spkTodayCounter.increment(KlasemenStandings.todayIso())
                    _state.update {
                        it.copy(submitting = false, actionDone = true, actionError = null, lastCreateResult = res.data)
                    }
                }
                is AuthResult.Failure -> _state.update { it.copy(submitting = false, actionError = res.message) }
            }
        }
    }


    fun submitPdi(id: String, serial: String, engine: String, checklist: List<PdiChecklistItemBody>, onDone: () -> Unit) = action {
        val photoUrl = pdiPhotoBytes?.let { bytes ->
            when (val up = repository.uploadPhoto(bytes, "pdi_${System.currentTimeMillis()}.webp")) {
                is AuthResult.Success -> up.data
                is AuthResult.Failure -> return@action up
            }
        }
        val res = repository.submitPdi(id, PdiBody(serialNumber = serial.trim(), engineNumber = engine.trim().ifBlank { null }, readyPhotoUrl = photoUrl, checklist = checklist))
        if (res is AuthResult.Success &&
            res.data.status == com.krisoft.tridjayaelektronik.data.model.DeliveryStatusKey.PENDING_PERBAIKAN
        ) {
            // Unit DITAHAN (ada jawaban "Tidak" + saklar cabang menyala). Server
            // menjawab 200, tapi meneruskannya sebagai Success membuat wrapper
            // `action` menyetel `actionDone` → layar pop-back ke antrian TANPA
            // SATU KALIMAT PUN; unit tampak "beres" padahal justru berhenti.
            // Dipulangkan sebagai Failure SENGAJA: wrapper menaruh pesannya di
            // `actionError` (satu-satunya kanal pesan layar ini — dan ini memang
            // peringatan) dan petugas TETAP di detail, yang di-reload dulu
            // supaya badge merah "Ditahan — Perbaikan" ikut tampil.
            loadDetail(id)
            return@action AuthResult.Failure(
                "pdi_ditahan",
                "Unit DITAHAN — ada item checklist dijawab \"Tidak\". Perbaiki lalu PDI ulang, atau minta kepala cabang melepaskannya.",
            )
        }
        res.mapOk { onDone() }
    }

    /**
     * PDI MASSAL barang kecil se-SPK (2026-08-05). Tak ada checklist, tak ada
     * serial — server menutup semua unit kecil `pending_pdi` sebatch sekaligus.
     *
     * BEDA dari [submitPdi]: tak ada foto yang diunggah dan tak ada cabang
     * `pending_perbaikan` untuk ditangani — unit yang lewat jalur ini memang
     * tak dijawab checklist-nya, jadi tak mungkin ada jawaban "Tidak" yang
     * menahannya.
     */
    fun submitPdiKecil(id: String, onDone: () -> Unit) = action {
        repository.submitPdiKecil(id).mapOk { onDone() }
    }

    /** Simpan satu form pengambilan aki (gate PDI kategori ber-flag `requiresAkiForm`). */
    fun createAkiForm(id: String, body: com.krisoft.tridjayaelektronik.data.model.CreateAkiFormBody, onDone: () -> Unit) {
        if (_state.value.submitting) return
        _state.update { it.copy(submitting = true, actionError = null) }
        viewModelScope.launch {
            when (val res = repository.createAkiForm(id, body)) {
                is AuthResult.Success -> {
                    _state.update { it.copy(submitting = false, akiForms = it.akiForms + res.data) }
                    onDone()
                }
                is AuthResult.Failure -> _state.update { it.copy(submitting = false, actionError = res.message) }
            }
        }
    }

    /** Riwayat form aki (menu "Pengambilan Aki"). */
    fun loadAkiForms(dari: String? = null, sampai: String? = null) {
        _state.update { it.copy(loading = true, error = null, akiPhotos = emptyMap()) }
        viewModelScope.launch {
            when (val res = repository.akiForms(dari = dari, sampai = sampai)) {
                is AuthResult.Success -> {
                    _state.update { it.copy(loading = false, akiList = res.data, error = null) }
                    loadAkiPhotos(res.data)
                }
                is AuthResult.Failure -> _state.update { it.copy(loading = false, error = res.message) }
            }
        }
    }

    /** Muat foto bukti aki ter-autentikasi per form — fail-soft per foto (pola
     *  sama [loadJobPhotos]). */
    private fun loadAkiPhotos(forms: List<com.krisoft.tridjayaelektronik.data.model.AkiFormDto>) {
        akiFormsNeedingPhoto(forms).forEach { form ->
            val url = form.photoUrl.orEmpty()
            _state.update { it.copy(akiPhotos = it.akiPhotos + (form.id to AkiPhotoState.Memuat)) }
            viewModelScope.launch {
                // Gagal di tahap MANA pun berakhir sama bagi approver: fotonya
                // tak bisa dilihat. Yang penting ia tahu itu kegagalan, bukan
                // ketiadaan — dulu keduanya sama-sama senyap.
                val bytes = repository.fetchPhoto(url)
                val bmp = bytes?.let {
                    withContext(Dispatchers.Default) {
                        android.graphics.BitmapFactory.decodeByteArray(it, 0, it.size)
                    }
                }
                _state.update {
                    it.copy(
                        akiPhotos = it.akiPhotos +
                            (form.id to (bmp?.let(AkiPhotoState::Ada) ?: AkiPhotoState.Gagal))
                    )
                }
            }
        }
    }

    fun markAkiReturned(id: String) {
        if (_state.value.submitting) return
        _state.update { it.copy(submitting = true, actionError = null) }
        viewModelScope.launch {
            when (val res = repository.returnAkiForm(id)) {
                is AuthResult.Success -> {
                    _state.update { it.copy(submitting = false) }
                    loadAkiForms()
                }
                is AuthResult.Failure -> _state.update { it.copy(submitting = false, actionError = res.message) }
            }
        }
    }

    /** Setujui form aki — approval TUNGGAL (redesain 2026-07-24), tanpa slot.
     *  Muat ulang daftar setelah sukses. */
    fun approveAki(id: String) {
        if (_state.value.submitting) return
        _state.update { it.copy(submitting = true, actionError = null) }
        viewModelScope.launch {
            when (val res = repository.approveAkiForm(id)) {
                is AuthResult.Success -> {
                    _state.update { it.copy(submitting = false) }
                    loadAkiForms()
                }
                is AuthResult.Failure -> _state.update { it.copy(submitting = false, actionError = res.message) }
            }
        }
    }

    /** Tolak form aki (alasan wajib). Muat ulang daftar setelah sukses. */
    fun rejectAki(id: String, reason: String) {
        if (_state.value.submitting) return
        _state.update { it.copy(submitting = true, actionError = null) }
        viewModelScope.launch {
            when (val res = repository.rejectAkiForm(id, reason)) {
                is AuthResult.Success -> {
                    _state.update { it.copy(submitting = false) }
                    loadAkiForms()
                }
                is AuthResult.Failure -> _state.update { it.copy(submitting = false, actionError = res.message) }
            }
        }
    }

    /**
     * Konfirmasi SPK kasir. Sejak 2026-08-05 server mem-FAN-OUT panggilan ini
     * ke seluruh unit `pending_spk` sebatch dengan `noTransaksi` yang sama —
     * satu SPK = satu transaksi GS, seperti di GS sendiri.
     *
     * [units] diisi HANYA kalau kasir benar-benar mengetik nominal DP per unit
     * (SPK ber-unit COD `dp` lebih dari satu). Mengirim daftar setengah terisi
     * lebih buruk daripada tidak mengirim sama sekali: server memvalidasi tiap
     * unit COD `dp` sebatch wajib bernominal dan menolak 400 — sementara tanpa
     * daftar itu unit lain memakai fallback `codDpAmount` rencana sales.
     */
    fun confirmSpk(
        id: String,
        noTransaksi: String,
        kasirKonfirmasiPembayaran: Boolean? = null,
        kasirDpDiterima: Double? = null,
        units: List<com.krisoft.tridjayaelektronik.data.model.ConfirmSpkUnitBody>? = null,
        onDone: () -> Unit,
    ) = action {
        repository.confirmSpk(
            id,
            ConfirmSpkBody(
                noTransaksi = noTransaksi.trim(),
                kasirKonfirmasiPembayaran = kasirKonfirmasiPembayaran,
                kasirDpDiterima = kasirDpDiterima,
                units = units?.takeIf { it.isNotEmpty() },
            ),
        ).mapOk { onDone() }
    }

    /**
     * Kasir: konfirmasi uang penjualan sudah diterima (semua jenis pembayaran,
     * bukan cuma COD) — SATU KALI untuk SELURUH SPK sejak 2026-08-22. Foto bukti
     * dipakai dari slot `deliverPhoto` yang sama — job berstatus `delivered` tak
     * pernah bersamaan dengan job in_transit di layar yang sama, pola persis
     * [selfPickupComplete].
     *
     * **Yang diperbaiki:** dulu satu unit per panggilan, jadi kasir memotret slip
     * setor yang SAMA sebanyak jumlah barang di SPK — padahal antrian sudah
     * menampilkannya sebagai satu kartu per SPK sejak 2026-08-06, sehingga
     * kartunya tetap muncul setelah satu barang dikonfirmasi dan terbaca sebagai
     * gagal-simpan. Yang berubah cuma jumlah pekerjaan manusia: nominal tetap
     * DIKIRIM PER UNIT (tiap barang beda harganya) dan server tetap mencatat per
     * baris `delivery_jobs`.
     *
     * Fan-out-nya di KLIEN, dan justru di endpoint ini itu aman — tiga alasannya
     * (status tak berubah, tak ada guard `IS NULL`, scope per `id`) ditulis di
     * `SetoranKasirGate.kt`. Jangan menyalin polanya ke tahap lain tanpa membaca
     * catatan itu; di sana loop per unit memang terlarang.
     *
     * Foto diunggah SEKALI lalu URL-nya dipakai ulang seluruh unit, dan
     * [setoranPhotoUrl] menahannya melewati kegagalan supaya percobaan ulang tak
     * menumpuk foto tanpa induk di `uploads/delivery` — keluhan yang persis sama
     * dengan yang tercatat di [SETORAN_NOMINAL_MINIMUM].
     *
     * **Kiriman separuh bukan kegagalan total, dan tak boleh terbaca begitu.**
     * Server tak punya transaksi lintas unit, jadi unit ke-2 bisa gagal setelah
     * unit ke-1 tersimpan. Karena itu: jumlah yang berhasil disebut apa adanya,
     * `batchUnits` dimuat ulang supaya unit yang sudah beres HILANG dari daftar,
     * dan tekanan tombol berikutnya cuma menagih sisanya. Tanpa itu kasir
     * mengulang seluruh SPK dan menimpa catatan yang sudah benar dengan angka
     * yang diketik ulang.
     */
    fun setoranKasirSpk(kiriman: List<SetoranKiriman>, onDone: () -> Unit) = action {
        if (kiriman.isEmpty()) return@action AuthResult.Failure("validation", "Tak ada barang yang menunggu setoran")
        val bytes = deliverPhotoBytes ?: return@action AuthResult.Failure("validation", "Foto bukti wajib diambil")
        val photoUrl = setoranPhotoUrl
            ?: when (val up = repository.uploadPhoto(bytes, "setoran_${System.currentTimeMillis()}.webp")) {
                is AuthResult.Success -> up.data.also { setoranPhotoUrl = it }
                is AuthResult.Failure -> return@action up
            }
        var berhasil = 0
        var pesanGagal: String? = null
        for (k in kiriman) {
            val res = repository.setoranKasir(
                k.id,
                com.krisoft.tridjayaelektronik.data.model.SetoranKasirBody(nominalDiterima = k.nominal, photoUrl = photoUrl),
            )
            when (res) {
                is AuthResult.Success -> berhasil++
                // Pesan PERTAMA saja: tiga unit yang gagal karena sebab yang sama
                // menghasilkan satu kalimat, bukan tiga kalimat kembar.
                is AuthResult.Failure -> if (pesanGagal == null) pesanGagal = res.message
            }
        }
        val gagalPesan = pesanGagal ?: return@action AuthResult.Success(Unit).mapOk { onDone() }
        _state.value.detail?.let { loadBatchUnits(it) }
        AuthResult.Failure(
            "partial",
            "Tersimpan $berhasil dari ${kiriman.size} barang. Sisanya gagal: $gagalPesan " +
                "Tekan lagi untuk mengirim sisanya — yang sudah tersimpan tidak diulang.",
        )
    }

    fun issueDeliveryNote(id: String, sourceBranch: String, onDone: () -> Unit) = action {
        repository.issueDeliveryNote(id, DeliveryNoteBody(sourceBranch = sourceBranch.trim())).mapOk { onDone() }
    }

    fun assign(id: String, driverId: String, driverName: String, scheduledDate: String, customerMapUrl: String?, onDone: () -> Unit) = action {
        repository.assign(id, AssignBody(driverId = driverId.trim(), driverName = driverName.trim().ifBlank { null }, scheduledDate = scheduledDate.trim(), customerMapUrl = customerMapUrl))
            .mapOk { onDone() }
    }

    /**
     * BATALKAN penjadwalan — hanya selama driver belum berangkat (server yang
     * menegakkan; kalau sudah `in_transit` ia menjawab validasi, dan jalurnya
     * memang [reassign]).
     *
     * TIDAK di-fan-out se-SPK, sama seperti server: unit lain di SPK yang sama
     * bisa saja sudah berangkat, dan menariknya sekaligus akan membatalkan
     * pekerjaan yang sedang berjalan.
     */
    fun unassign(id: String, reason: String, onDone: () -> Unit) = action {
        repository.unassign(id, reason).mapOk { onDone() }
    }

    /**
     * PINDAHKAN unit ini ke driver lain. [scheduledDate] kosong = pertahankan
     * tanggal yang ada (server: `COALESCE(NULLIF(?, ''), scheduled_date)`).
     *
     * Inilah satu-satunya jalur yang bisa MEMECAH satu SPK ke dua driver —
     * `assign` di-fan-out se-SPK, `reassign` sengaja tidak.
     */
    fun reassign(
        id: String,
        driverId: String,
        driverName: String,
        scheduledDate: String,
        onDone: () -> Unit,
    ) = action {
        repository.reassign(id, driverId, driverName, scheduledDate).mapOk { onDone() }
    }

    fun dispatch(id: String, onDone: () -> Unit) = action { repository.dispatch(id).mapOk { onDone() } }

    /** 088: tandai sudah chat konsumen — refresh detail job (consumerChatAt terisi). */
    fun chatConsumer(id: String) = jobUpdate { repository.chatConsumer(id) }

    /**
     * 111: ambil / lepas klaim PDI. Klaim OPSIONAL di server (job tak diklaim
     * tetap boleh di-PDI), jadi kegagalan di sini TIDAK boleh menutup jalan
     * kerja: form PDI tetap seperti sebelum tombol ditekan, dan pesan server
     * ditampilkan apa adanya — pada 409 pesan itulah satu-satunya tempat nama
     * pemegang klaim disebutkan.
     */
    /**
     * Sunting isi SPK (administrator). Sengaja BUKAN [jobUpdate]: balasannya
     * membungkus job di dalam `{job, konsumenDiubah}`, dan memaksanya ke bentuk
     * yang sama akan membuang angka fan-out konsumen yang justru ingin
     * dilaporkan ke penyuntingnya.
     *
     * `onDone` dipanggil HANYA saat sukses — dialog menutup dirinya di situ.
     * Gagal tetap membiarkan dialog terbuka dengan isian utuh, supaya koreksi
     * yang ditolak server (mis. NIK kurang digit) tak perlu diketik ulang.
     */
    fun editJob(id: String, patch: kotlinx.serialization.json.JsonObject, onDone: (Int) -> Unit) {
        if (_state.value.submitting) return
        _state.update { it.copy(submitting = true, actionError = null) }
        viewModelScope.launch {
            when (val res = repository.editJob(id, patch)) {
                is AuthResult.Success -> {
                    _state.update { it.copy(submitting = false, detail = res.data.job) }
                    onDone(res.data.konsumenDiubah)
                }
                is AuthResult.Failure ->
                    _state.update { it.copy(submitting = false, actionError = res.message) }
            }
        }
    }

    fun claimPdi(id: String) = jobUpdate { repository.claimPdi(id) }

    fun releasePdiClaim(id: String) = jobUpdate { repository.releasePdiClaim(id) }

    /** Aksi yang MEMUTAKHIRKAN job yang sedang dibuka, bukan menyelesaikan
     *  tahapnya — sengaja TIDAK menyetel `actionDone` (layar detail memakai
     *  flag itu untuk menutup dirinya sendiri). */
    private fun jobUpdate(block: suspend () -> AuthResult<DeliveryJobDto>) {
        if (_state.value.submitting) return
        _state.update { it.copy(submitting = true, actionError = null) }
        viewModelScope.launch {
            when (val res = block()) {
                is AuthResult.Success -> _state.update { it.copy(submitting = false, detail = res.data) }
                is AuthResult.Failure -> _state.update { it.copy(submitting = false, actionError = res.message) }
            }
        }
    }

    fun deliver(id: String, rating: Int, comment: String, checklist: List<PdiChecklistItemBody>, onDone: () -> Unit) = action {
        val bytes = deliverPhotoBytes ?: return@action AuthResult.Failure("validation", "Foto serah terima wajib diambil")
        val photoUrl = when (val up = repository.uploadPhoto(bytes, "deliver_${System.currentTimeMillis()}.webp")) {
            is AuthResult.Success -> up.data
            is AuthResult.Failure -> return@action up
        }
        // Foto uang (088) — hanya di-upload bila diambil; gate wajib ada di UI + backend.
        val cashUrl = cashPhotoBytes?.let { cb ->
            when (val up = repository.uploadPhoto(cb, "cash_${System.currentTimeMillis()}.webp")) {
                is AuthResult.Success -> up.data
                is AuthResult.Failure -> return@action up
            }
        }
        // GPS best-effort (pola sama absensi): null bila izin ditolak/gagal fix — JANGAN blokir serah terima.
        //
        // PAKAI ULANG fix yang sudah dipanaskan `refreshGps()` saat layar detail
        // dibuka; minta baru HANYA kalau memang belum ada. Dulu selalu meminta
        // ulang di sini, jadi driver menunggu fix kedua tepat saat menekan kirim
        // — keluhan "geotag lama" 2026-07-28. Bonus kebenaran: koordinat yang
        // dikirim kini SAMA dengan yang tercetak di watermark foto (keduanya
        // dari `gpsLat`/`gpsLng`); sebelumnya dua fix berbeda bisa berselisih.
        val warm = _state.value
        val lat = warm.gpsLat
        val lng = warm.gpsLng
        val loc = if (lat != null && lng != null) null else LocationProvider.current(appContext)
        repository.deliver(
            id,
            DeliverBody(
                photoUrl = photoUrl, lat = lat ?: loc?.latitude, lng = lng ?: loc?.longitude, reviewRating = rating,
                reviewComment = comment.trim().ifBlank { null },
                checklist = checklist.ifEmpty { null }, cashPhotoUrl = cashUrl
            )
        ).mapOk { onDone() }
    }

    fun cancel(id: String, reason: String, onDone: () -> Unit) = action {
        repository.cancel(id, reason.trim().ifBlank { "-" }).mapOk { onDone() }
    }

    /** (2026-07-24) DC/admin tandai job `self_pickup` selesai — reuse slot foto
     *  [deliverPhotoBytes] (tidak bentrok: self-pickup-complete di `pending_scheduling`,
     *  `deliver` di `in_transit`, tak pernah sama job di saat sama). */
    fun selfPickupComplete(id: String, rating: Int, comment: String, onDone: () -> Unit) = action {
        val bytes = deliverPhotoBytes ?: return@action AuthResult.Failure("validation", "Foto wajib diambil")
        val photoUrl = when (val up = repository.uploadPhoto(bytes, "selfpickup_${System.currentTimeMillis()}.webp")) {
            is AuthResult.Success -> up.data
            is AuthResult.Failure -> return@action up
        }
        repository.selfPickupComplete(
            id,
            com.krisoft.tridjayaelektronik.data.model.SelfPickupCompleteBody(
                photoUrl = photoUrl, reviewRating = rating, reviewComment = comment.trim().ifBlank { null }
            )
        ).mapOk { onDone() }
    }

    private inline fun <T> AuthResult<T>.mapOk(onOk: () -> Unit): AuthResult<T> {
        if (this is AuthResult.Success) onOk()
        return this
    }

    private fun action(block: suspend () -> AuthResult<*>) {
        if (_state.value.submitting) return
        _state.update { it.copy(submitting = true, actionError = null, actionDone = false) }
        viewModelScope.launch {
            when (val res = block()) {
                is AuthResult.Success -> _state.update { it.copy(submitting = false, actionDone = true, actionError = null) }
                is AuthResult.Failure -> _state.update { it.copy(submitting = false, actionError = res.message) }
            }
        }
    }

}
