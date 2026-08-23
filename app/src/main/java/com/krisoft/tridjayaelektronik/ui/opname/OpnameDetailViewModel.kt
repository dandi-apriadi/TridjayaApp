package com.krisoft.tridjayaelektronik.ui.opname

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krisoft.tridjayaelektronik.data.AuthRepository
import com.krisoft.tridjayaelektronik.data.AuthResult
import com.krisoft.tridjayaelektronik.data.OpnameRepository
import com.krisoft.tridjayaelektronik.data.KONDISI_LAYAK
import com.krisoft.tridjayaelektronik.data.KONDISI_TIDAK_LAYAK
import com.krisoft.tridjayaelektronik.data.SerialInputRepository
import com.krisoft.tridjayaelektronik.data.pesanGagalKirim
import com.krisoft.tridjayaelektronik.data.STATUS_DRAFT
import com.krisoft.tridjayaelektronik.data.VALIDASI_PENDING
import com.krisoft.tridjayaelektronik.data.local.OpnameUnitEntity
import com.krisoft.tridjayaelektronik.data.model.OpnameDetailDto
import com.krisoft.tridjayaelektronik.data.model.OpnameStockItemDto
import com.krisoft.tridjayaelektronik.data.model.OpnameUnitDto
import com.krisoft.tridjayaelektronik.data.model.SerialRequestDto
import com.krisoft.tridjayaelektronik.util.PhotoWatermark
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

data class OpnameDetailUiState(
    val isLoading: Boolean = true,
    val detail: OpnameDetailDto? = null,
    val stock: List<OpnameStockItemDto> = emptyList(),
    /**
     * Permintaan daftar barang MASIH TERBANG.
     *
     * Wajib terpisah dari [isLoading]: `isLoading` dimatikan begitu detail sesi
     * tiba, sedangkan `stockList` baru ditembak SESUDAHNYA — dan layar mulai
     * merender isi begitu [detail] tidak `null`. Tanpa flag ini ada jeda satu
     * round-trip penuh di mana [stock] kosong dan [stockError] `null`, dan
     * satu-satunya kesimpulan yang bisa ditarik dari keadaan itu adalah "sesi
     * ini tidak punya daftar barang sama sekali". Sesi SEHAT dituduh kosong,
     * persis penyakit yang sedang diberantas.
     */
    val stockLoading: Boolean = false,
    /**
     * Kenapa permintaan daftar barang GAGAL — kalimat dari server apa adanya;
     * `null` = tak ada kegagalan yang tercatat. Dibaca BERSAMA [stockLoading];
     * `sebabDaftarBarangKosong` (OpnameDetailScreen.kt) menggabungkan keduanya
     * jadi satu vonis supaya tak ada layar yang menebak sendiri.
     *
     * Perlu dicatat karena seluruh blok "Daftar Barang" di layar hanya
     * dirender saat [stock] tidak kosong. Sebelum field ini ada, kegagalan
     * memuat daftar barang dibuang diam-diam (`as? AuthResult.Success`), jadi
     * petugas yang berdiri di gudang melihat sesi TANPA satu pun barang untuk
     * dihitung — tak ada error, tak ada tombol muat ulang, dan tak ada cara
     * membedakannya dari sesi yang memang rusak.
     */
    val stockError: String? = null,
    /**
     * `isManager` + `lingkup` dari `GET /inventory/opname/context` — dipakai
     * HANYA untuk memilih KALIMAT sebab seksi "Hitung Barang" tertutup
     * (`kalimatHitungTertutup`), TIDAK PERNAH untuk menggerbangi tombol apa pun.
     * Vonisnya tetap `canHitung` per-sesi dari server.
     *
     * Karena itu `null` (permintaan gagal / belum tiba) harus aman: kalimatnya
     * jatuh ke teks LAMA, bukan menuduh peran yang salah. Pola sama dengan
     * `konteksPenunjukan` di `InventoryOpnameDetailPage.tsx`.
     */
    val isManager: Boolean? = null,
    val lingkup: String? = null,
    /** Unit terscan sesi ini (buffer Room; baris tanpa syncedAtMillis masih diantre). */
    val units: List<OpnameUnitEntity> = emptyList(),
    /** Barang yang sedang dihitung — semua scan berikutnya masuk ke barang ini. */
    val selectedItem: OpnameStockItemDto? = null,
    /** Pesan hasil scan terakhir (tersimpan / diantre / ditolak). */
    val scanMessage: String? = null,
    /** Barang yang sedang dinyatakan nihil — tombolnya dikunci selama proses. */
    val nihilBusy: Boolean = false,
    val errorMessage: String? = null,
    /**
     * Boleh IKUT MENGHITUNG (buka sheet scan). Datang dari SERVER
     * (`canHitung`), bukan disimpulkan `isOwner && draft` seperti sebelum
     * 2026-08-09: sejak penulisan unit dibuka untuk se-cabang, kesimpulan itu
     * menyembunyikan tombol scan dari petugas yang justru berhak.
     */
    val canHitung: Boolean = false,
    /**
     * Boleh mencatat unit KETIK MANUAL (izin `tetapkan_sn`, migrasi 212).
     * Server lama tak mengirim flagnya → jatuh balik ke [canHitung], jadi APK
     * ini tak mencabut apa pun yang sudah jalan.
     */
    val canTetapkanSn: Boolean = false,
    /** Boleh mencatat unit hasil SCAN (izin `verifikasi_sn`, migrasi 212). */
    val canVerifikasiSn: Boolean = false,
    /** Sesi draft MILIK sendiri → tombol Selesaikan/Batalkan muncul. */
    val canManage: Boolean = false,
    /**
     * Serial yang di sesi ini dicatat oleh AKUN INI (menurut server). Dipakai
     * memutuskan tombol hapus per baris: petugas boleh mengoreksi salah scan
     * miliknya sendiri, tapi tak boleh membongkar klaim orang lain. Server
     * menegakkannya; ini cuma supaya tombolnya tak muncul lalu dijawab 403.
     *
     * Kosong saat offline/gagal muat — tombol hapus hilang sementara, dan itu
     * pilihan yang benar: tombol yang muncul lalu ditolak lebih membingungkan
     * daripada tombol yang belum muncul.
     */
    val serialMilikSaya: Set<String> = emptySet(),
    /** Sesi dibatalkan + milik sendiri → tombol Hapus Sesi muncul. */
    val canDelete: Boolean = false,
    val isSaving: Boolean = false,
    val saveError: String? = null,
    val isMutatingStatus: Boolean = false,
    val statusError: String? = null,
    /**
     * Boleh mengusulkan pendaftaran SN (`serial.propose`). Fail-soft `true`
     * saat peta kemampuan gagal dimuat — sama seperti gate menu: server tetap
     * menolak 403, jadi salah tebak di sini paling jauh berujung pesan error,
     * bukan petugas yang diam-diam kehilangan satu-satunya jalan melaporkan
     * unit tak terdaftar.
     */
    val canPropose: Boolean = true,
    /**
     * Unit yang temuan lapangannya BEDA dari kondisi yang ditetapkan admin-stok
     * di registry. Datang langsung dari server (`kondisiSelisih`) dan sengaja
     * TIDAK disimpan ke Room: vonis registry bisa berubah kapan saja, dan
     * daftar basi di layar verifikasi lebih buruk daripada daftar kosong.
     * Kosong juga saat offline — itu jujur: pembandingnya memang tak terbaca.
     */
    val selisihKondisi: List<OpnameUnitDto> = emptyList(),
    /** Usulan yang sedang disusun; `null` = dialog tertutup. */
    val proposal: SerialProposalDraft? = null,
    val proposalMessage: String? = null,
    /** Unit ketik-manual yang sedang disusun (wajib 2 foto); `null` = dialog tertutup. */
    val manualDraft: ManualUnitDraft? = null,
    /** Panel status usulan terbuka (dimuat saat dibuka, bukan saat layar dimuat). */
    val requestsOpen: Boolean = false,
    val requestsLoading: Boolean = false,
    val requests: List<SerialRequestDto> = emptyList(),
    val requestsError: String? = null
)

/** Foto mana yang sedang diambil — dua-duanya wajib, dan bukan foto yang sama. */
enum class SerialPhotoKind { SERIAL, BARANG }

/**
 * Usulan pendaftaran SN yang sedang disusun petugas di lapangan.
 *
 * Dua foto WAJIB: foto serialnya sendiri membuktikan nomornya terbaca, foto
 * barangnya membuktikan serial itu menempel pada unit yang benar-benar ada di
 * gudang. Satu foto saja tak bisa membuktikan keduanya, dan admin-stok yang
 * memutuskan tak sedang berdiri di depan barangnya.
 */
data class SerialProposalDraft(
    val kodeBarang: String,
    val namaBarang: String?,
    val serialNumber: String,
    val fotoSnUrl: String? = null,
    val fotoBarangUrl: String? = null,
    val catatan: String = "",
    val uploading: Boolean = false,
    val submitting: Boolean = false,
    val error: String? = null
) {
    val isValid: Boolean
        get() = serialNumber.isNotBlank() &&
            !fotoSnUrl.isNullOrBlank() &&
            !fotoBarangUrl.isNullOrBlank()

    val busy: Boolean get() = uploading || submitting
}

/**
 * Unit KETIK MANUAL yang sedang disusun — serial diketik karena barcode-nya
 * rusak/pudar, jadi klaimnya harus dibuktikan dua foto (label rusak dari dekat
 * + barang utuh) dan divonis admin-stok. Tanpa ini ketikan tangan tak bisa
 * dibedakan dari serial yang disalin dari daftar registry.
 */
data class ManualUnitDraft(
    val kodeBarang: String,
    val namaBarang: String?,
    val serialNumber: String,
    /** Nilai dari `KONDISI_PILIHAN`, bukan boolean lagi — sejak kosakata
     *  kondisi melebar jadi empat (migrasi 194). */
    val kondisi: String,
    val keterangan: String?,
    val fotoSnUrl: String? = null,
    val fotoBarangUrl: String? = null,
    val uploading: Boolean = false,
    val submitting: Boolean = false,
    val error: String? = null
) {
    val isValid: Boolean
        get() = serialNumber.isNotBlank() &&
            !fotoSnUrl.isNullOrBlank() &&
            !fotoBarangUrl.isNullOrBlank()

    val busy: Boolean get() = uploading || submitting
}

@HiltViewModel
class OpnameDetailViewModel @Inject constructor(
    private val repository: OpnameRepository,
    private val authRepository: AuthRepository,
    /** Registry SN — dipakai di sini HANYA untuk mengusulkan, tak pernah menulis
     *  registry: penulisnya admin-stok saat menyetujui. */
    private val serialRepository: SerialInputRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(OpnameDetailUiState())
    val uiState: StateFlow<OpnameDetailUiState> = _uiState.asStateFlow()

    private var sessionId: String = ""
    private var unitsJob: Job? = null

    /**
     * [paksaStock] dipakai jalur tarik-turun: tanpa itu daftar barang hanya diambil sekali
     * (guard `stock.isEmpty()`), jadi refresh cuma menyegarkan angka header sementara daftar
     * barangnya tetap basi.
     */
    fun load(id: String, paksaStock: Boolean = false) {
        sessionId = id
        observeUnits(id)
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        // Coroutine terpisah: gate usulan tak boleh menahan detail sesi tampil.
        //
        // Diambil ULANG tiap `load()` (tiap detail dibuka & tiap tarik-turun),
        // BUKAN sekali di `init` — jadi tak ada `PenyegarKemampuan` di sini dan
        // memang tak perlu: pemicunya sudah lebih sering daripada latch itu.
        // Yang WAJIB diperiksa kalau VM ini kelak pindah ke scope kept-alive
        // (root tab, bukan turunan `ROUTE_OPNAME`) — lihat
        // `PembacaPetaKemampuanTest`.
        viewModelScope.launch {
            authRepository.capabilities()?.let { caps ->
                _uiState.update { it.copy(canPropose = caps["serial.propose"] == true) }
            }
        }
        // Konteks pemantauan — coroutine SENDIRI, alasan yang sama dengan blok
        // di atas: ia cuma memilih kalimat, jadi tak boleh menahan detail sesi
        // tampil dan kegagalannya tak boleh mematikan apa pun. Sengaja TIDAK
        // menyentuh `errorMessage`.
        viewModelScope.launch {
            (repository.context() as? AuthResult.Success)?.let { ctx ->
                _uiState.update {
                    it.copy(isManager = ctx.data.isManager, lingkup = ctx.data.lingkup)
                }
            }
        }
        viewModelScope.launch {
            when (val result = repository.detail(id)) {
                is AuthResult.Success -> {
                    applyDetail(result.data)
                    // Diputuskan DI SINI, bukan di dekat panggilan `stockList` di
                    // bawah, dan dipasang pada emisi yang SAMA dengan
                    // `isLoading = false`: emisi itulah yang pertama kali dilihat
                    // layar dengan `detail != null`, jadi keadaan "stok sedang
                    // dimuat" harus sudah berdiri di situ. Memasangnya belakangan
                    // menyisakan satu emisi berisi daftar kosong tanpa sebab —
                    // yang terbaca sebagai "sesi ini memang tak punya barang".
                    val muatStock = result.data.status == STATUS_DRAFT &&
                        (paksaStock || _uiState.value.stock.isEmpty())
                    _uiState.update { it.copy(isLoading = false, stockLoading = muatStock) }
                    // Vonis admin-stok (pending → approved/rejected) hidup di server;
                    // fail-soft, badge lama bertahan bila gagal. Menunggu detail dulu
                    // karena rekonsiliasi hanya boleh untuk sesi draft (repositori
                    // menegakkannya) — sesi batal/selesai buffernya sengaja kosong.
                    launch {
                        val serverUnits = repository.refreshValidationStatuses(id, result.data.status)
                        val saya = authRepository.currentUserId
                        _uiState.update {
                            it.copy(
                                selisihKondisi = serverUnits.filter { u -> u.kondisiSelisih },
                                serialMilikSaya = serverUnits
                                    .filter { u -> saya != null && u.countedByUserId == saya }
                                    .map { u -> u.serialNumber.uppercase() }
                                    .toSet(),
                            )
                        }
                    }
                    // Coverage list matters for any viewer while the session is still
                    // draft (owner counting, or kepala-cabang/manager verifying progress)
                    // — completed sessions already have their own reconciled `items`.
                    if (muatStock) {
                        // Kegagalan DICATAT, bukan dibuang. Blok "Daftar Barang"
                        // di layar hanya muncul kalau `stock` terisi, jadi
                        // membuang error di sini berarti sesi yang sehat terlihat
                        // persis seperti sesi tanpa barang — dan petugasnya tak
                        // punya satu pun petunjuk untuk mencoba lagi.
                        //
                        // `stockLoading` dimatikan di KEDUA cabang: satu cabang
                        // yang lupa meninggalkan layar menunggu selamanya, dan
                        // itu keadaan keempat yang tak seorang pun bisa keluar
                        // darinya (tombol "Coba lagi" cuma dirender pada vonis
                        // GAGAL_DIMUAT).
                        when (val stock = repository.stockList(id)) {
                            is AuthResult.Success -> _uiState.update {
                                it.copy(stock = stock.data, stockError = null, stockLoading = false)
                            }
                            is AuthResult.Failure -> _uiState.update {
                                it.copy(stockError = stock.message, stockLoading = false)
                            }
                        }
                    }
                }
                is AuthResult.Failure -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = result.message)
                }
            }
        }
    }

    private fun observeUnits(id: String) {
        unitsJob?.cancel()
        unitsJob = viewModelScope.launch {
            repository.observeUnits(id).collect { units ->
                _uiState.update { it.copy(units = units) }
            }
        }
    }

    private fun applyDetail(detail: OpnameDetailDto) {
        // Server yang tahu jawabannya; aturan lama cuma dipakai saat server
        // BELUM mengirim flagnya (`null`). Memakai `false` di situ akan
        // mencabut tombol scan bahkan dari pemilik sesi pada server lama —
        // mengurangi fungsi yang sudah jalan, bukan sekadar konservatif.
        val isOwner = detail.createdByUserId.isNotBlank() &&
            detail.createdByUserId == authRepository.currentUserId
        val bolehKelola = detail.canManage ?: isOwner
        val bolehHitung = detail.canHitung ?: (isOwner && detail.status == "draft")
        // Rantai `?:` yang sama: `null` = server belum mengenal penunjukan
        // petugas (pra-migrasi 212), dan aturan lama berbunyi "yang boleh
        // menghitung boleh scan MAUPUN ketik manual". `false` sebagai default
        // akan mencabut dua alur kerja yang sudah jalan begitu APK ini beredar
        // di atas server lama.
        val bolehTetapkan = izinPenunjukan(detail.canTetapkanSn, bolehHitung)
        val bolehVerifikasi = izinPenunjukan(detail.canVerifikasiSn, bolehHitung)
        _uiState.update {
            it.copy(
                detail = detail,
                canHitung = bolehHitung,
                canTetapkanSn = bolehTetapkan,
                canVerifikasiSn = bolehVerifikasi,
                canManage = bolehKelola && detail.status == "draft",
                canDelete = bolehKelola && detail.status == "cancelled",
            )
        }
    }

    /**
     * Nyatakan barang NIHIL: sudah dicari di gudang, tak ada satu pun.
     *
     * Ini yang membuat sesi bisa DITUTUP — barang yang fisiknya habis tak bisa
     * di-scan, jadi tanpa penanda ini ia menahan sesinya selamanya (diukur di
     * produksi 2026-08-09: 0 dari 7 sesi pernah selesai).
     *
     * Hasilnya dilaporkan sebagai selisih PENUH, bukan "dilewati" — karena itu
     * layar memintanya dikonfirmasi dulu.
     */
    fun tandaiNihil(kodeBarang: List<String>) {
        if (kodeBarang.isEmpty() || _uiState.value.nihilBusy) return
        _uiState.update { it.copy(nihilBusy = true, statusError = null) }
        viewModelScope.launch {
            when (val hasil = repository.tandaiNihil(sessionId, kodeBarang)) {
                is AuthResult.Success -> {
                    applyDetail(hasil.data)
                    _uiState.update {
                        it.copy(
                            nihilBusy = false,
                            scanMessage = "${kodeBarang.size} barang ditandai nihil",
                        )
                    }
                    // Daftar barang ikut disegarkan: baris yang barusan
                    // dinyatakan nihil pindah dari "belum" ke "sudah".
                    refreshDetail()
                }
                is AuthResult.Failure -> _uiState.update {
                    it.copy(nihilBusy = false, statusError = hasil.message)
                }
            }
        }
    }

    fun selectItem(item: OpnameStockItemDto?) {
        _uiState.update { it.copy(selectedItem = item, saveError = null, scanMessage = null) }
    }

    /**
     * Catat satu unit hasil scan/ketik. Tersimpan lokal dulu lalu dikirim; hasilnya
     * dilaporkan apa adanya supaya petugas tahu bedanya "tersimpan", "menunggu jaringan",
     * dan "ditolak".
     */
    fun scan(serialNumber: String, kondisi: String = KONDISI_LAYAK, keterangan: String? = null) {
        val item = _uiState.value.selectedItem ?: return
        // Vonis server dipatuhi SEBELUM barisnya ditulis ke Room. Menulis dulu
        // lalu dijawab 403 meninggalkan baris yang ikut terhitung di layar & PDF
        // sampai push berikutnya membuangnya — dan selama itu petugas mengira
        // unitnya tercatat. Server tetap wasit terakhir; ini cuma menutup jendela.
        if (!_uiState.value.canVerifikasiSn) {
            _uiState.update {
                it.copy(
                    isSaving = false,
                    scanMessage = null,
                    saveError = ALASAN_TAK_BOLEH_SCAN
                )
            }
            return
        }
        _uiState.update { it.copy(isSaving = true, saveError = null, scanMessage = null) }
        viewModelScope.launch {
            val result = runCatching {
                repository.scanUnit(
                    sessionId = sessionId,
                    kodeBarang = item.kodeBarang,
                    namaBarang = item.namaBarang,
                    serialNumberRaw = serialNumber,
                    kondisi = kondisi,
                    keterangan = keterangan
                )
            }.getOrElse { error ->
                _uiState.update {
                    it.copy(isSaving = false, saveError = error.message ?: "Gagal menyimpan unit")
                }
                return@launch
            }
            _uiState.update { state ->
                when (result) {
                    is OpnameRepository.ScanResult.Accepted -> state.copy(
                        isSaving = false,
                        scanMessage = if (result.temuan != null) {
                            "${result.serialNumber} tersimpan — ${temuanLabel(result.temuan)}"
                        } else {
                            "${result.serialNumber} tersimpan"
                        }
                    )
                    // SEBABNYA ikut ditampilkan. Tanpa itu penolakan sementara
                    // yang punya kalimat rinci dari server — "sesi opname baru
                    // dibuka 12/08 08:00", satu-satunya tempat JAM jendelanya
                    // muncul — hilang di balik kalimat generik, dan petugas cuma
                    // melihat barisnya diam tanpa tahu apa yang ditunggu.
                    is OpnameRepository.ScanResult.Queued -> state.copy(
                        isSaving = false,
                        scanMessage = pesanAntre(result.serialNumber, result.reason)
                    )
                    is OpnameRepository.ScanResult.Rejected -> state.copy(
                        isSaving = false,
                        saveError = "${result.serialNumber}: ${result.reason}"
                    )
                }
            }
            // Angka pada header berasal dari server; segarkan setelah unit bertambah.
            refreshDetail()
        }
    }

    fun deleteUnit(unit: OpnameUnitEntity) {
        _uiState.update { it.copy(isSaving = true, saveError = null) }
        viewModelScope.launch {
            when (val result = repository.deleteUnit(sessionId, unit)) {
                is AuthResult.Success -> {
                    _uiState.update { it.copy(isSaving = false, scanMessage = "${unit.serialNumber} dihapus") }
                    refreshDetail()
                }
                is AuthResult.Failure -> _uiState.update {
                    it.copy(isSaving = false, saveError = result.message)
                }
            }
        }
    }

    // ── Unit ketik-manual (barcode rusak → wajib 2 foto + validasi) ─────────

    /**
     * Buka dialog foto untuk serial yang diketik tangan. Mengembalikan `false` bila
     * dialognya TIDAK jadi terbuka — pemanggil memakai itu untuk memutuskan boleh-tidaknya
     * mengosongkan kolom ketikan (menghapus ketikan panjang petugas lalu menolaknya sama
     * saja menyuruh mengetik ulang tanpa tahu salahnya di mana).
     */
    fun startManualUnit(serialNumberRaw: String, kondisi: String, keterangan: String?): Boolean {
        val item = _uiState.value.selectedItem ?: return false
        // Sama seperti scan: penolakannya disebutkan, dan dialog dua-foto tak
        // dibuka sama sekali — menyuruh petugas memotret dua kali lalu menjawab
        // 403 di akhir adalah kerja yang dibuang percuma.
        if (!_uiState.value.canTetapkanSn) {
            _uiState.update { it.copy(saveError = ALASAN_TAK_BOLEH_MANUAL, scanMessage = null) }
            return false
        }
        val serial = com.krisoft.tridjayaelektronik.data.normalizeSerial(serialNumberRaw)
        if (serial == null) {
            _uiState.update { it.copy(saveError = "Serial kosong atau lebih dari 64 karakter") }
            return false
        }
        _uiState.update {
            it.copy(
                manualDraft = ManualUnitDraft(
                    kodeBarang = item.kodeBarang,
                    namaBarang = item.namaBarang,
                    serialNumber = serial,
                    kondisi = kondisi,
                    keterangan = keterangan
                ),
                saveError = null,
                scanMessage = null
            )
        }
        return true
    }

    fun cancelManualUnit() = _uiState.update { it.copy(manualDraft = null) }

    /** Foto unit manual — kompres + watermark + unggah, pola persis foto usulan SN. */
    fun uploadManualPhoto(file: File, kind: SerialPhotoKind) {
        val draft = _uiState.value.manualDraft ?: return
        updateManual { it.copy(uploading = true, error = null) }
        viewModelScope.launch {
            val judul = if (kind == SerialPhotoKind.SERIAL) "TRIDJAYA · LABEL RUSAK" else "TRIDJAYA · FOTO BARANG"
            val bytes = withContext(Dispatchers.Default) {
                PhotoWatermark.prepareWatermarkedJpeg(
                    file = file,
                    lat = null,
                    lng = null,
                    title = judul,
                    subtitle = draft.serialNumber
                )
            }?.first
            if (bytes == null) {
                updateManual { it.copy(uploading = false, error = "Foto tidak terbaca, ambil ulang") }
                return@launch
            }
            val nama = "opname_manual_${if (kind == SerialPhotoKind.SERIAL) "label" else "barang"}_${System.currentTimeMillis()}.jpg"
            when (val up = serialRepository.uploadPhoto(bytes, nama)) {
                is AuthResult.Success -> updateManual {
                    if (kind == SerialPhotoKind.SERIAL) {
                        it.copy(uploading = false, fotoSnUrl = up.data)
                    } else {
                        it.copy(uploading = false, fotoBarangUrl = up.data)
                    }
                }
                is AuthResult.Failure -> updateManual {
                    it.copy(
                        uploading = false,
                        error = if (up.code == "network_error") {
                            "Butuh koneksi untuk mengirim foto — coba lagi saat sinyal kembali"
                        } else {
                            up.message
                        }
                    )
                }
            }
        }
    }

    fun submitManualUnit() {
        val draft = _uiState.value.manualDraft ?: return
        if (!draft.isValid) {
            updateManual { it.copy(error = "Dua foto wajib diambil sebelum menyimpan") }
            return
        }
        updateManual { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            // Pola sama scan(): tanpa runCatching, kegagalan tulis Room (disk penuh)
            // meledak SETELAH server menerima unitnya — app mati, atau dialognya
            // membeku dengan submitting=true (tombol Simpan & Batal ikut mati).
            val result = runCatching {
                repository.manualUnit(
                    sessionId = sessionId,
                    kodeBarang = draft.kodeBarang,
                    namaBarang = draft.namaBarang,
                    serialNumberRaw = draft.serialNumber,
                    kondisi = draft.kondisi,
                    fotoSnUrl = draft.fotoSnUrl.orEmpty(),
                    fotoBarangUrl = draft.fotoBarangUrl.orEmpty(),
                    keterangan = draft.keterangan
                )
            }.getOrElse { error ->
                updateManual {
                    it.copy(submitting = false, error = error.message ?: "Gagal menyimpan unit")
                }
                return@launch
            }
            when (result) {
                is OpnameRepository.ScanResult.Accepted -> {
                    _uiState.update {
                        it.copy(
                            manualDraft = null,
                            scanMessage = pesanUnitManual(result.serialNumber, result.validationStatus)
                        )
                    }
                    refreshDetail()
                }
                is OpnameRepository.ScanResult.Rejected ->
                    updateManual { it.copy(submitting = false, error = "${result.serialNumber}: ${result.reason}") }
                // manualUnit tak pernah mengantre — cabang ini cuma penenang kompilator.
                is OpnameRepository.ScanResult.Queued ->
                    updateManual { it.copy(submitting = false, error = result.reason) }
            }
        }
    }

    private fun updateManual(block: (ManualUnitDraft) -> ManualUnitDraft) {
        _uiState.update { state ->
            state.manualDraft?.let { state.copy(manualDraft = block(it)) } ?: state
        }
    }

    // ── Usulan pendaftaran SN (unit ber-temuan `tidak_terdaftar`) ───────────

    fun startProposal(unit: OpnameUnitEntity) {
        _uiState.update {
            it.copy(
                proposal = SerialProposalDraft(
                    kodeBarang = unit.kodeBarang,
                    namaBarang = unit.namaBarang,
                    serialNumber = unit.serialNumber
                ),
                proposalMessage = null
            )
        }
    }

    fun cancelProposal() = _uiState.update { it.copy(proposal = null) }

    fun onProposalCatatan(text: String) = updateProposal { it.copy(catatan = text, error = null) }

    fun clearProposalMessage() = _uiState.update { it.copy(proposalMessage = null) }

    /**
     * Foto dikompres + di-watermark (jam & nama, pola bukti foto lain) SEBELUM
     * diunggah. Tanpa kompresi, foto kamera 8-12MP menembus batas 5MB server
     * dan ditolak justru di lapangan yang sinyalnya paling buruk.
     */
    fun uploadProposalPhoto(file: File, kind: SerialPhotoKind) {
        val draft = _uiState.value.proposal ?: return
        updateProposal { it.copy(uploading = true, error = null) }
        viewModelScope.launch {
            val judul = if (kind == SerialPhotoKind.SERIAL) "TRIDJAYA · FOTO SN" else "TRIDJAYA · FOTO BARANG"
            val bytes = withContext(Dispatchers.Default) {
                PhotoWatermark.prepareWatermarkedJpeg(
                    file = file,
                    lat = null,
                    lng = null,
                    title = judul,
                    subtitle = draft.serialNumber
                )
            }?.first
            if (bytes == null) {
                updateProposal { it.copy(uploading = false, error = "Foto tidak terbaca, ambil ulang") }
                return@launch
            }
            val nama = "sn_${if (kind == SerialPhotoKind.SERIAL) "serial" else "barang"}_${System.currentTimeMillis()}.jpg"
            when (val up = serialRepository.uploadPhoto(bytes, nama)) {
                is AuthResult.Success -> updateProposal {
                    if (kind == SerialPhotoKind.SERIAL) {
                        it.copy(uploading = false, fotoSnUrl = up.data)
                    } else {
                        it.copy(uploading = false, fotoBarangUrl = up.data)
                    }
                }
                // Usulan SENGAJA tidak diantre offline seperti scan unit: foto
                // 2MB × 2 per usulan akan menumpuk di HP tanpa batas, dan usulan
                // yang "terkirim" menurut petugas tapi belum sampai jauh lebih
                // menyesatkan daripada penolakan yang jelas di depan.
                is AuthResult.Failure -> updateProposal {
                    it.copy(
                        uploading = false,
                        error = if (up.code == "network_error") {
                            "Butuh koneksi untuk mengirim foto usulan — coba lagi saat sinyal kembali"
                        } else {
                            up.message
                        }
                    )
                }
            }
        }
    }

    fun submitProposal() {
        val draft = _uiState.value.proposal ?: return
        val dealer = _uiState.value.detail?.dealerCode.orEmpty()
        if (!draft.isValid || dealer.isBlank()) {
            updateProposal { it.copy(error = "Dua foto wajib diambil sebelum mengirim usulan") }
            return
        }
        updateProposal { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            val result = serialRepository.proposeSerial(
                kodeDealer = dealer,
                kodeBarang = draft.kodeBarang,
                namaBarang = draft.namaBarang,
                serialNumberRaw = draft.serialNumber,
                fotoSnUrl = draft.fotoSnUrl.orEmpty(),
                fotoBarangUrl = draft.fotoBarangUrl.orEmpty(),
                // Merunut keputusan admin-stok ke sesi tempat temuannya muncul.
                opnameSessionId = sessionId.ifBlank { null },
                catatan = draft.catatan.trim().ifBlank { null }
            )
            when (result) {
                is AuthResult.Success -> _uiState.update {
                    it.copy(
                        proposal = null,
                        proposalMessage = "Usulan ${draft.serialNumber} terkirim, menunggu admin-stok"
                    )
                }
                is AuthResult.Failure -> updateProposal { it.copy(submitting = false, error = result.message) }
            }
        }
    }

    /**
     * Status usulan yang sudah dikirim. Tanpa panel ini pengusul buta: satu-
     * satunya tanda usulannya disetujui adalah temuan "belum terdaftar" yang
     * berhenti muncul pada scan berikutnya — dan yang DITOLAK tak pernah
     * memberi tanda apa pun.
     */
    fun openRequests() {
        _uiState.update { it.copy(requestsOpen = true, requestsLoading = true, requestsError = null) }
        val dealer = _uiState.value.detail?.dealerCode
        viewModelScope.launch {
            when (val res = serialRepository.serialRequests(dealer)) {
                is AuthResult.Success -> _uiState.update {
                    it.copy(requestsLoading = false, requests = res.data)
                }
                is AuthResult.Failure -> _uiState.update {
                    it.copy(requestsLoading = false, requestsError = res.message)
                }
            }
        }
    }

    fun closeRequests() = _uiState.update { it.copy(requestsOpen = false) }

    private fun updateProposal(block: (SerialProposalDraft) -> SerialProposalDraft) {
        _uiState.update { state ->
            state.proposal?.let { state.copy(proposal = block(it)) } ?: state
        }
    }

    /** Kirim ulang antrean yang tertinggal saat sinyal hilang. */
    fun retryPending() {
        viewModelScope.launch {
            when (val pushed = repository.pushPending(sessionId)) {
                is AuthResult.Success -> {
                    _uiState.update { it.copy(scanMessage = "Antrean terkirim") }
                    refreshDetail()
                }
                // Tombol "Kirim ulang" adalah tempat kedua petugas bertemu 403.
                // Pesan mentah server di situ ("Akses ditolak") tak memberi tahu
                // siapa pun harus berbuat apa — pakai kalimat yang sama dengan
                // jalur scan.
                is AuthResult.Failure -> _uiState.update {
                    it.copy(saveError = pesanGagalKirim(pushed))
                }
            }
        }
    }

    private suspend fun refreshDetail() {
        (repository.detail(sessionId) as? AuthResult.Success)?.let { applyDetail(it.data) }
    }

    fun clearSaveError() {
        _uiState.update { it.copy(saveError = null) }
    }

    fun clearScanMessage() {
        _uiState.update { it.copy(scanMessage = null) }
    }

    /** Kirim sisa antrean dulu, baru tutup sesi. */
    fun complete() = mutateStatus { repository.finalize(sessionId) }

    fun cancel() = mutateStatus { repository.cancel(sessionId) }

    /** Hapus permanen. `onDeleted` dipanggil hanya setelah server benar-benar mengonfirmasi. */
    fun deleteSession(onDeleted: () -> Unit) {
        _uiState.update { it.copy(isMutatingStatus = true, statusError = null) }
        viewModelScope.launch {
            when (val result = repository.deleteSession(sessionId)) {
                is AuthResult.Success -> {
                    _uiState.update { it.copy(isMutatingStatus = false) }
                    onDeleted()
                }
                is AuthResult.Failure -> _uiState.update {
                    it.copy(isMutatingStatus = false, statusError = result.message)
                }
            }
        }
    }

    private fun mutateStatus(block: suspend () -> AuthResult<OpnameDetailDto>) {
        _uiState.update { it.copy(isMutatingStatus = true, statusError = null) }
        viewModelScope.launch {
            when (val result = block()) {
                is AuthResult.Success -> {
                    applyDetail(result.data)
                    _uiState.update { it.copy(isMutatingStatus = false) }
                }
                is AuthResult.Failure -> _uiState.update {
                    it.copy(isMutatingStatus = false, statusError = result.message)
                }
            }
        }
    }
}

/**
 * Pesan yang dibaca petugas setelah unit ketik-manual tersimpan — mengikuti vonis
 * server, bukan mengarang.
 *
 * Backend yang belum mengenal input manual menerima unitnya sebagai scan biasa dan
 * tak membalas `validationStatus`; mengabarkan "menunggu validasi admin stok" di situ
 * menjanjikan vonis yang tak akan pernah datang. Kelas kesalahan yang sama dengan badge
 * `pending` karangan, cuma pindah kanal ke pesan.
 */
fun pesanUnitManual(serialNumber: String, validationStatus: String?): String =
    when (validationStatus) {
        null -> "$serialNumber tersimpan"
        VALIDASI_PENDING -> "$serialNumber tersimpan — menunggu validasi admin stok"
        // Status baru dari server tampil apa adanya, bukan disalahartikan jadi "menunggu".
        else -> "$serialNumber tersimpan — $validationStatus"
    }

/**
 * Aturan jatuh-balik dua flag penunjukan petugas (migrasi 212).
 *
 * [flagServer] `null` berarti server BELUM MENGENAL fieldnya — bukan "tidak
 * boleh". Memperlakukannya `false` akan mencabut scan/ketik-manual dari SEMUA
 * orang begitu APK ini beredar di atas server lama; itu bukan sikap konservatif
 * melainkan mematikan fungsi yang sudah jalan. Aturan pra-212: yang boleh
 * menghitung boleh keduanya.
 *
 * `false` EKSPLISIT dari server adalah VONIS dan wajib dipatuhi, walau
 * [bolehHitung] `true` — server memang bisa mengizinkan seseorang mencatat
 * sebagian saja (ditunjuk scan tapi tidak ketik-manual).
 */
fun izinPenunjukan(flagServer: Boolean?, bolehHitung: Boolean): Boolean =
    flagServer ?: bolehHitung

/**
 * Kalimat sebab untuk jalur yang TERTUTUP penunjukan petugas (migrasi 212).
 *
 * Wajib disebut, bukan sekadar tombol yang lenyap: orang yang tombolnya hilang
 * tanpa penjelasan menyimpulkan aplikasinya rusak, lalu menutup-buka app,
 * memasang ulang, dan akhirnya melapor ke orang yang salah. Menyebut
 * admin-stok adalah satu-satunya tindakan yang benar-benar mengubah keadaan.
 */
const val ALASAN_TAK_BOLEH_SCAN =
    "Kamu belum ditunjuk untuk men-scan SN di cabang ini — hubungi admin stok."
const val ALASAN_TAK_BOLEH_MANUAL =
    "Kamu belum ditunjuk untuk mengetik SN manual di cabang ini — hubungi admin stok."

/**
 * Kalimat untuk unit yang MASIH di antrean lokal. [reason] datang dari server
 * (mis. jam jendela opname) atau dari lapisan jaringan; ia disebutkan apa adanya
 * karena ia satu-satunya keterangan tentang APA yang sedang ditunggu.
 *
 * Kosong = sinyal hilang biasa, kalimat lamanya tetap dipakai.
 */
fun pesanAntre(serialNumber: String, reason: String?): String {
    val sebab = reason?.trim().orEmpty()
    return if (sebab.isEmpty() || sebabTeknis(sebab)) {
        "$serialNumber tersimpan offline, menunggu jaringan"
    } else {
        "$serialNumber tersimpan di HP, belum terkirim — $sebab"
    }
}

/**
 * Kalimat untuk daftar barang sesi yang GAGAL dimuat.
 *
 * [sebab] adalah `message` dari `AuthResult.Failure` milik
 * `OpnameRepository.stockList`. Isinya salah satu dari dua hal yang sangat
 * berbeda: kalimat server (`parseError` mengambil `errors[0]` lalu `message`
 * dari badan error, dan gateway menulis kalimatnya dalam Bahasa Indonesia),
 * atau teks exception jaringan mentah bila permintaannya tak pernah terjawab
 * (`call` menangkap `Exception` lalu memakai `e.message` apa adanya). Yang
 * pertama layak dibaca petugas; yang kedua tidak, dan disaring [sebabTeknis]
 * persis seperti di [pesanAntre].
 *
 * Kenapa bukan satu kalimat tetap saja: "coba lagi setelah sinyal stabil"
 * melebur SEMUA sebab jadi "sinyal jelek". Petugas yang membaca itu akan
 * menunggu sinyal membaik — dan kalau sebabnya bukan sinyal, ia menunggu
 * sesuatu yang tak akan pernah mengubah keadaan.
 */
fun pesanDaftarBarangGagal(sebab: String?): String {
    val inti = "Daftar barang sesi ini gagal dimuat — bukan berarti tidak ada barang " +
        "untuk dihitung."
    val rinci = sebab?.trim().orEmpty()
    return if (rinci.isEmpty() || sebabTeknis(rinci)) {
        "$inti Coba lagi setelah sinyal stabil."
    } else {
        "$inti Sebab dari server: $rinci"
    }
}

/**
 * `true` = [sebab] itu teks exception jaringan mentah, bukan keterangan yang
 * berguna bagi petugas.
 *
 * `OpnameRepository.call` memakai `e.message` apa adanya saat request gagal,
 * dan OkHttp menulisnya dalam bahasa Inggris teknis — di layar hitung fisik
 * gudang, "Unable to resolve host tridjaya.com: No address associated with
 * hostname" terbaca sebagai APP RUSAK dan mendorong petugas men-scan ulang unit
 * yang sebenarnya sudah aman tersimpan. Yang benar-benar layak ditampilkan
 * hanyalah keterangan dari SERVER (mis. jam jendela opname pada
 * `jendela_belum_mulai`), karena cuma itu yang memberi tahu APA yang ditunggu.
 */
private fun sebabTeknis(sebab: String): Boolean {
    val l = sebab.lowercase()
    // Alamat internal TIDAK pernah layak dibaca petugas gudang, dan
    // kehadirannya menandai kalimat mesin apa pun bentuknya. Ini penjaga
    // umum: daftar penanda di bawah selalu ketinggalan satu kalimat baru.
    if ("http://" in l || "https://" in l) return true
    return PENANDA_SEBAB_TEKNIS.any { it in l }
}

private val PENANDA_SEBAB_TEKNIS = listOf(
    "unable to resolve host",
    "failed to connect",
    "timeout",
    "timed out",
    "no address associated",
    "network is unreachable",
    "connection reset",
    "connection refused",
    "sslhandshake",
    "certpathvalidator",
    "econnaborted",
    "software caused connection abort",
    // Kalimat GATEWAY, bukan OkHttp: saat inventory-service mati, gateway
    // menjawab 502 dengan `upstream tidak merespons: {reqwest::Error}`, yang
    // Display-nya berbunyi `error sending request for url (http://host:port/...)`.
    // Tanpa penanda ini kalimat itu lolos ke layar sebagai "sebab dari server"
    // — persis kebalikan dari alasan fungsi ini ada.
    "upstream tidak merespons",
    "error sending request",
    "route bukan milik",
)

/** Temuan server: serial tak ada di registry cabang mana pun. */
const val TEMUAN_TIDAK_TERDAFTAR = "tidak_terdaftar"

/**
 * Unit yang layak diusulkan pendaftarannya.
 *
 * Tiga syarat, semuanya perlu: (a) pemakainya boleh mengusulkan
 * (`serial.propose`); (b) server memvonis serialnya belum terdaftar — temuan
 * LAIN (`cabang_lain`, `sudah_terjual`) bukan urusan pendaftaran dan usulannya
 * pasti ditolak; (c) unitnya SUDAH terkirim. Unit yang masih mengantre belum
 * punya vonis temuan sama sekali, jadi `temuan == null` di sana berarti "belum
 * tahu", bukan "terdaftar".
 */
fun bolehUsulkanSn(unit: OpnameUnitEntity, canPropose: Boolean): Boolean =
    canPropose && unit.temuan == TEMUAN_TIDAK_TERDAFTAR && unit.syncedAtMillis != null

/**
 * Penanda umur antrian satu usulan SN, atau `null` kalau tak ada yang layak
 * dipajang.
 *
 * Vonisnya MILIK SERVER (`umurAntrianJam` + `mandek` dari `vonis_usulan`) —
 * fungsi ini cuma memilih kalimatnya. Tiga aturan yang gampang salah:
 * - `umur == null` → tak ada yang ditampilkan. Itu berarti "tak dihitung di
 *   sini" (usulan sudah diputuskan, atau APK ini bicara ke server lama), BUKAN
 *   "baru saja masuk"; menulis "0j" mengubah ketidaktahuan jadi vonis.
 * - `mandek` dipercaya APA ADANYA, tidak diturunkan ulang dari jam di klien.
 *   Ambangnya `DELIVERY_STALL_HOURS` yang bisa diubah lewat env server; angka
 *   24 yang di-hardcode di sini akan berselisih diam-diam begitu env-nya
 *   digeser.
 * - Usulan yang sudah `approved`/`rejected` tak pernah diberi penanda: server
 *   memang tak mengirim umurnya, dan "menunggu 3j" untuk baris yang sudah
 *   diputus adalah kebohongan langsung.
 */
internal fun labelUmurUsulan(status: String, umurAntrianJam: Long?, mandek: Boolean): String? {
    if (status != "pending") return null
    val jam = umurAntrianJam ?: return null
    return if (mandek) "Menggantung ${jam}j" else "Menunggu ${jam}j"
}

/** Label temuan dalam Bahasa Indonesia; nilai tak dikenal ditampilkan apa adanya. */
fun temuanLabel(temuan: String): String = when (temuan) {
    "tidak_terdaftar" -> "belum terdaftar di registry"
    "cabang_lain" -> "terdaftar di cabang lain"
    "sudah_terjual" -> "tercatat sudah terjual"
    else -> temuan
}
