package com.krisoft.tridjayaelektronik.ui.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krisoft.tridjayaelektronik.data.STATUS_DRAFT
import com.krisoft.tridjayaelektronik.data.AbsensiRepository
import com.krisoft.tridjayaelektronik.data.AcInstallRepository
import com.krisoft.tridjayaelektronik.data.AuthRepository
import com.krisoft.tridjayaelektronik.data.AuthResult
import com.krisoft.tridjayaelektronik.data.CrmRepository
import com.krisoft.tridjayaelektronik.data.DeliveryFlowRepository
import com.krisoft.tridjayaelektronik.data.GodaRepository
import com.krisoft.tridjayaelektronik.data.HomeServiceRepository
import com.krisoft.tridjayaelektronik.data.KuponGebyarRepository
import com.krisoft.tridjayaelektronik.data.OpnameRepository
import com.krisoft.tridjayaelektronik.data.AktivitasRepository
import com.krisoft.tridjayaelektronik.data.SpkTodayCounter
import com.krisoft.tridjayaelektronik.data.model.DeliveryStatusKey
import com.krisoft.tridjayaelektronik.data.model.jumlahButirAktif
import com.krisoft.tridjayaelektronik.data.model.ProspekTargetDto
import com.krisoft.tridjayaelektronik.data.model.UserDto
import com.krisoft.tridjayaelektronik.data.local.DealerAlias
import com.krisoft.tridjayaelektronik.domain.indent.ListIndentUseCase
import com.krisoft.tridjayaelektronik.domain.sales.KlasemenStandings
import com.krisoft.tridjayaelektronik.ui.home.PenyegarKemampuan
import com.krisoft.tridjayaelektronik.ui.acinstall.butuhJawabanSaya
import com.krisoft.tridjayaelektronik.ui.home.effectiveRoles
import com.krisoft.tridjayaelektronik.ui.home.sidikAkses
import com.krisoft.tridjayaelektronik.ui.goda.belumLengkap
import com.krisoft.tridjayaelektronik.ui.homeservice.HsMode
import com.krisoft.tridjayaelektronik.ui.homeservice.saringStatus
import com.krisoft.tridjayaelektronik.ui.aktivitas.pilihAktivitasUntukInput
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ActivityUiState(
    val isLoading: Boolean = true,
    val userName: String = "",
    val cabangName: String = "",
    val tasks: List<DailyTask> = emptyList(),
    val progress: String = "",
    val queueCards: List<ActivityCard> = emptyList(),
    val actions: List<ActivityItem> = emptyList(),
    /** Dipakai subtitle ubin "Buat SPK" — lokal per-device. */
    val spkToday: Int = 0,
    /** Tombol "Panduan Alur" di baris PINTASAN — lihat [panduanAlurVisible]. */
    val panduanVisible: Boolean = false,
)

/**
 * Layar pertama app: mengambil angka untuk kartu yang BOLEH dilihat user saja.
 *
 * Dua sifat penting:
 *  1. **dedup per endpoint** — dua item aki berbagi satu panggilan `akiForms()`;
 *  2. **gagal per-sumber** — satu endpoint mati hanya membuat kartunya sendiri
 *     bertanda "—", kartu lain tetap berangka. Tak ada layar error global:
 *     bagian tugas harian & aksi tak butuh jaringan.
 */
@HiltViewModel
class ActivityViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val absensiRepository: AbsensiRepository,
    private val deliveryRepository: DeliveryFlowRepository,
    private val crmRepository: CrmRepository,
    private val raportRepository: AktivitasRepository,
    private val homeServiceRepository: HomeServiceRepository,
    private val acInstallRepository: AcInstallRepository,
    private val listIndentUseCase: ListIndentUseCase,
    private val opnameRepository: OpnameRepository,
    private val kuponGebyarRepository: KuponGebyarRepository,
    private val godaRepository: GodaRepository,
    private val spkTodayCounter: SpkTodayCounter,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ActivityUiState())
    val uiState: StateFlow<ActivityUiState> = _uiState.asStateFlow()

    private var capabilities: Map<String, Boolean>? = null
    private var lastLoadedAtMs: Long = 0L

    /** Lihat [segarkanKemampuan]. */
    private val penyegarKemampuan = PenyegarKemampuan(
        identitasToken = { authRepository.sidikTokenAkses },
        ambil = { authRepository.capabilities() },
    )

    /**
     * F2 audit final-fix-3 (2026-07-28): penghitung generasi — dulu `init` memicu DUA
     * `load()` fan-out (refresh(force=true) awal + susulan setelah peta capabilities
     * tiba), tanpa pengait urutan sama sekali.
     *
     * ASAL-USUL ITU SUDAH BERUBAH (2026-08-20): `init` tak lagi mengambil peta
     * kemampuan, jadi tak ada lagi "fan-out kedua dari `init`". Penggantinya
     * `load() -> segarkanKemampuan() -> load()`, yang bisa berulang KAPAN SAJA
     * sidik akses atau identitas token berubah — bukan lagi terbatas dua kali dan
     * bukan lagi terbatas di `init`. Penghitung ini karena itu makin diperlukan,
     * bukan makin tidak. Jangan mencari `launch` kedua di `init`; ia tak ada.
     *
     * Alasan aslinya tetap berlaku: kalau fan-out pertama kebetulan
     * SELESAI belakangan, penulisan `_uiState.value = ActivityUiState(...)` PENUH
     * (bukan `.copy()`) bisa mundur ke versi `capabilities = null`. Generation
     * counter dipilih ketimbang `Job.cancel()`: `load()` selalu jalan sampai akhir
     * (repository balikin `AuthResult`, tak pernah throw) jadi tak ada risiko
     * `isLoading` nyangkut `true` akibat dibatalkan di tengah — panggilan
     * TERAKHIR (generasi tertinggi) selalu yang menang, apa pun urutan selesainya.
     */
    private var loadGeneration: Long = 0L

    init {
        // Peta kemampuan TIDAK lagi diambil di sini: `load()` sendiri yang
        // memintanya lewat [segarkanKemampuan] setiap kali SIDIK AKSES user
        // berbeda dari yang terakhir berhasil diambil. Pengambilan pertama tetap
        // terjadi (sidik awal `null` selalu berbeda), bedanya ia kini juga
        // terjadi LAGI saat hak akses orangnya berubah di tengah sesi.
        refresh(force = true)
    }

    /**
     * `force = false` dipakai observer ON_RESUME di `ActivityScreen` (I1 audit
     * 2026-07-28) — tanpa jendela cache ini, bolak-balik cepat antara layar ini
     * dan layar anak (absen/PDI/kasir/dst, ON_RESUME ikut jalan tiap balik ke
     * root) akan menembak ulang seluruh endpoint tiap kali.
     */
    fun refresh(force: Boolean) {
        val now = System.currentTimeMillis()
        if (!force && now - lastLoadedAtMs < CACHE_WINDOW_MS) return
        lastLoadedAtMs = now
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val myGeneration = ++loadGeneration
        _uiState.value = _uiState.value.copy(isLoading = true)

        val user = authRepository.cachedUser
        val roles = effectiveRoles(user)
        // Peta kemampuan disegarkan dari sini, bukan sekali di `init` — tanpa ini
        // `roles` boleh berubah sesukanya, gerbangnya tetap memakai peta jam 08:00.
        // Tidak di-await: lihat [segarkanKemampuan].
        segarkanKemampuan(user)
        // `divisi` dioper karena sebagian item disaring per JABATAN, bukan role —
        // lihat [ActivityItem.jabatan]. Lupa mengopernya menyembunyikan item itu
        // (default `null`), bukan membocorkannya.
        val items = visibleActivityItems(
            roles,
            capabilities,
            akunUji(user?.name, user?.nik),
            divisi = user?.divisi,
        )
        val todayIso = KlasemenStandings.todayIso()

        val counts = mutableMapOf<ActivitySource, Int?>()
        val failed = mutableSetOf<ActivitySource>()

        val sources = sourcesToFetch(items)
        var checkInAt: String? = null
        var checkOutAt: String? = null
        /** `null` = penyebut aktivitas tak diketahui — lihat `aktivitasDetail`. */
        var aktivitasExpected: Int? = null
        /** `null` = target prospek tak diketahui — lihat `prospekTaskDetail`. */
        var prospekTarget: ProspekTargetDto? = null
        /** `null` = tak ada SPK gantung yang lewat tenggat (atau tak diambil). */
        var gantungAlert: String? = null
        /** Vonis cabang Kupon Gebyar. `null` = belum/gagal diketahui — kartunya
         *  TETAP tampil (bertanda gagal muat), lihat `kuponGebyarCardVisible`. */
        var kuponGebyarBoleh: Boolean? = null
        /** `null` = status bukti chat tak diketahui — lihat `buildDailyTasks`. */

        coroutineScope {
            val jobs = mutableListOf<kotlinx.coroutines.Deferred<Unit>>()

            if (ActivitySource.ABSENSI_TODAY in sources) jobs += async {
                when (val r = absensiRepository.today()) {
                    is AuthResult.Success -> {
                        checkInAt = r.data.record?.checkInAt
                        checkOutAt = r.data.record?.checkOutAt
                    }
                    is AuthResult.Failure -> failed += ActivitySource.ABSENSI_TODAY
                }
            }

            // Dua item (milik PDI & milik approver) — SATU panggilan.
            if (ActivitySource.AKI_FORMS_MINE in sources ||
                ActivitySource.AKI_FORMS_APPROVAL in sources
            ) jobs += async {
                when (val r = deliveryRepository.akiForms()) {
                    is AuthResult.Success -> {
                        counts[ActivitySource.AKI_FORMS_MINE] =
                            r.data.count { it.akiBekasStatus == "belum" }
                        counts[ActivitySource.AKI_FORMS_APPROVAL] =
                            r.data.count { it.approvalStatus == "pending" }
                    }
                    is AuthResult.Failure -> {
                        failed += ActivitySource.AKI_FORMS_MINE
                        failed += ActivitySource.AKI_FORMS_APPROVAL
                    }
                }
            }

            fun antrianStatus(source: ActivitySource, status: String) {
                if (source in sources) jobs += async {
                    when (val r = deliveryRepository.list(status = status)) {
                        is AuthResult.Success -> counts[source] = r.data.size
                        is AuthResult.Failure -> failed += source
                    }
                }
            }
            antrianStatus(ActivitySource.DLV_PENDING_PDI, DeliveryStatusKey.PENDING_PDI)
            antrianStatus(ActivitySource.DLV_PENDING_SPK, DeliveryStatusKey.PENDING_SPK)
            antrianStatus(ActivitySource.DLV_PENDING_NOTE, DeliveryStatusKey.PENDING_DELIVERY_NOTE)
            antrianStatus(ActivitySource.DLV_PENDING_SCHEDULING, DeliveryStatusKey.PENDING_SCHEDULING)

            // C2 audit 2026-07-28: role di DELIVERY_READ_ALL_ROLES mendapat SELURUH
            // job perusahaan dari `list_delivery` (cabang `is_manager || is_admin`
            // mengabaikan `asDriver`), bukan job miliknya — kartunya tak pernah
            // tampil (driverCardVisible), jadi jangan tembak endpointnya sama sekali.
            if (ActivitySource.DLV_AS_DRIVER in sources &&
                roles.none { it in DELIVERY_READ_ALL_ROLES }
            ) jobs += async {
                when (val r = deliveryRepository.list(asDriver = true)) {
                    is AuthResult.Success -> counts[ActivitySource.DLV_AS_DRIVER] = r.data.size
                    is AuthResult.Failure -> failed += ActivitySource.DLV_AS_DRIVER
                }
            }

            // SPK Gantung: server memberi seluruh unit terkirim yang belum
            // dikonfirmasi pembayarannya. Angka kartu = SEMUA baris itu (kasir
            // harus tahu ada kerjaan sejak menit pertama); umur >24 jam turun
            // pangkat jadi baris penanda mendesak — lihat `spkGantungRingkas`.
            if (ActivitySource.DLV_PENDING_PAYMENT in sources) jobs += async {
                when (val r = deliveryRepository.list(view = "pending_payment")) {
                    is AuthResult.Success -> {
                        val ringkas = spkGantungRingkas(r.data.map { it.deliveredAt })
                        counts[ActivitySource.DLV_PENDING_PAYMENT] = ringkas.total
                        gantungAlert = spkGantungAlert(ringkas)
                    }
                    is AuthResult.Failure -> failed += ActivitySource.DLV_PENDING_PAYMENT
                }
            }

            if (ActivitySource.DISCOUNT_PENDING in sources) jobs += async {
                when (val r = deliveryRepository.discounts(status = "pending")) {
                    // `.total`, BUKAN `.items.size` — backend membatasi `items`
                    // ke `limit = 20`, badge tak boleh ikut terpotong (I2).
                    is AuthResult.Success -> counts[ActivitySource.DISCOUNT_PENDING] = r.data.total
                    is AuthResult.Failure -> failed += ActivitySource.DISCOUNT_PENDING
                }
            }

            // Komplain. Angkanya dihitung dari `items` yang disaring KLIEN (bukan
            // `total`): server hanya menerima satu nilai `status` per permintaan
            // sementara tiap antrian butuh beberapa, jadi `total` di sini berarti
            // "semua tiket yang terambil", bukan "yang menunggu kamu".
            fun antrianKomplain(source: ActivitySource, mode: HsMode) {
                if (source in sources) jobs += async {
                    when (
                        val r = homeServiceRepository.list(jenis = mode.jenis, mine = mode.mine)
                    ) {
                        is AuthResult.Success ->
                            counts[source] = saringStatus(r.data.items, mode.statusAktif).size
                        is AuthResult.Failure -> failed += source
                    }
                }
            }
            antrianKomplain(ActivitySource.HS_TRIASE, HsMode.TRIASE)
            antrianKomplain(ActivitySource.HS_TUGAS_TEKNISI, HsMode.TEKNISI)
            antrianKomplain(ActivitySource.HS_TARIK, HsMode.TARIK)
            antrianKomplain(ActivitySource.HS_TUGAS_DRIVER, HsMode.DRIVER)

            // Antrian PIC. `.total` (bukan `items.size`) — server memotong `items`
            // ke `limit`, badge tak boleh ikut terpotong (pola DISCOUNT_PENDING).
            // Tugas pemasangan AC. Angkanya yang BELUM dijawab, bukan `size` —
            // alasannya di `AcInstallPlan.butuhJawabanSaya`. Hanya ditembak kalau
            // itemnya memang tampil (yaitu: pemegang jabatan teknisi), jadi tak ada
            // ongkos request untuk karyawan lain.
            if (ActivitySource.AC_INSTALL_TUGAS in sources) jobs += async {
                when (val r = acInstallRepository.tugasSaya()) {
                    is AuthResult.Success ->
                        counts[ActivitySource.AC_INSTALL_TUGAS] = butuhJawabanSaya(r.data, user?.id)
                    is AuthResult.Failure -> failed += ActivitySource.AC_INSTALL_TUGAS
                }
            }

            if (ActivitySource.AKTIVITAS_REVIEW_PENDING in sources) jobs += async {
                when (val r = raportRepository.antrianReview(tanggal = todayIso)) {
                    is AuthResult.Success -> counts[ActivitySource.AKTIVITAS_REVIEW_PENDING] = r.data.total
                    is AuthResult.Failure -> failed += ActivitySource.AKTIVITAS_REVIEW_PENDING
                }
            }

            if (ActivitySource.RAPORT_TODAY in sources) {
                jobs += async {
                    when (val r = raportRepository.raportOfDay(todayIso, user?.id)) {
                        is AuthResult.Success -> counts[ActivitySource.RAPORT_TODAY] = r.data.size
                        is AuthResult.Failure -> failed += ActivitySource.RAPORT_TODAY
                    }
                }
                // Penyebut "x/y aktivitas". Panggilan TERPISAH dari yang di atas dan
                // sengaja TIDAK menandai RAPORT_TODAY gagal saat ia sendiri gagal:
                // jumlah yang sudah terkirim tetap benar, cuma penyebutnya yang tak
                // diketahui — dan kartu bertanda "gagal muat" gara-gara master
                // aktivitas tak terambil justru menyembunyikan angka yang valid.
                // `pilihAktivitasUntukInput` dipakai ulang apa adanya (BUKAN
                // matcher baru) supaya penyebut di kartu ini identik dengan
                // daftar aktivitas yang user lihat begitu kartunya dibuka.
                // Itu sebabnya ia WAJIB ikut pindah ke penempatan bersama
                // layar raport: kalau cuma salah satu yang pindah, kartunya
                // menjanjikan "x/8" sementara layarnya menampilkan 6 kotak.
                jobs += async {
                    // Penempatan diambil BARENG master aktivitas, bukan
                    // sesudahnya: kartu ini dirender di layar Home yang dibuka
                    // paling sering, dan satu round-trip tambahan di sini
                    // terasa di tiap pembukaan.
                    // `.penempatan` — respons endpoint itu sejak vc123 juga
                    // membawa blok `chatTrainee`, tapi kartu ini cuma butuh
                    // penyebutnya. Gerbang chat dirender di layar Input
                    // Aktivitas, bukan di kartu ringkas ini.
                    val (r, penempatan) = coroutineScope {
                        val pos = async { raportRepository.aktivitasPositions() }
                        val tempat = async { raportRepository.penempatanSaya() }
                        pos.await() to tempat.await().penempatan
                    }
                    if (r is AuthResult.Success) {
                        // `.jobdesks` = nama field DI KABEL, ejaan lama.
                        // `jumlahButirAktif(...)`, BUKAN `.jobdesks.size`:
                        // butir yang ditandai `nonaktif` tak ditagih — penyebut
                        // KPI & gerbang absen pulang sudah menghormatinya,
                        // kartu ini dulu tidak. Posisi `null` (tak ketemu
                        // divisinya) TETAP `null` di sini, bukan 0 —
                        // `jumlahButirAktif(null)` sendiri mengembalikan 0,
                        // yang akan salah dibaca sebagai "0 butir wajib"
                        // alih-alih "belum diketahui".
                        aktivitasExpected =
                            pilihAktivitasUntukInput(user?.divisi.orEmpty(), r.data, penempatan)
                                ?.let { jumlahButirAktif(it) }
                    }
                }
            }

            // Kartu "Input Prospek": aktual/target dari SERVER. Ikut fan-out ini
            // (bukan panggilan berurutan) supaya tak menambah waktu buka layar.
            // Gagal = diamkan `null` → kartu jatuh ke hitungan cache lama, TIDAK
            // ditandai "gagal muat": angka cache tetap ada gunanya offline, dan
            // menandainya gagal justru menyembunyikan tugasnya dari progres.
            if (ActivitySource.LEADS_CACHE in sources) jobs += async {
                val r = crmRepository.myProspekTarget(todayIso)
                if (r is AuthResult.Success) prospekTarget = r.data
            }

            if (ActivitySource.INDENT_PENDING in sources) jobs += async {
                when (val r = listIndentUseCase(status = "menunggu")) {
                    is AuthResult.Success -> counts[ActivitySource.INDENT_PENDING] = r.data.count
                    is AuthResult.Failure -> failed += ActivitySource.INDENT_PENDING
                }
            }

            if (ActivitySource.OPNAME_SESI_DRAFT in sources) jobs += async {
                // Server yang men-scope daftarnya ke cabang akun (`list_opname`),
                // jadi app TIDAK mengirim filter cabang — mengirimnya akan jadi
                // parameter yang bisa dipalsukan untuk hal yang sudah dijaga.
                when (val r = opnameRepository.list(STATUS_DRAFT)) {
                    is AuthResult.Success -> counts[ActivitySource.OPNAME_SESI_DRAFT] = r.data.size
                    is AuthResult.Failure -> failed += ActivitySource.OPNAME_SESI_DRAFT
                }
            }

            // Kupon Gebyar. Satu panggilan `meta` memberi DUA hal: angka kartu
            // dan vonis cabang. Server sengaja TIDAK menghitung apa pun untuk
            // cabang yang tak berhak (`bolehLihat=false` ⇒ semua angka 0), jadi
            // menembaknya untuk Manado tak menarik 13 ribu baris percuma.
            //
            // `bolehLihat=false` BUKAN kegagalan: `failed` sengaja tak diisi,
            // karena kartunya akan disembunyikan seluruhnya — menandainya gagal
            // akan membuatnya tampil bertuliskan "ketuk untuk coba lagi" untuk
            // cabang yang memang tak punya pekerjaannya.
            if (ActivitySource.KUPON_GEBYAR_SISA in sources) jobs += async {
                when (val r = kuponGebyarRepository.meta()) {
                    is AuthResult.Success -> {
                        kuponGebyarBoleh = r.data.bolehLihat
                        if (r.data.bolehLihat) {
                            counts[ActivitySource.KUPON_GEBYAR_SISA] = r.data.sisa
                        }
                    }
                    is AuthResult.Failure -> failed += ActivitySource.KUPON_GEBYAR_SISA
                }
            }

            // SN Goda: unit GODA yang belum punya serial number di gudang
            // petugas. Angkanya dihitung KLIEN dari daftar stok — `goda.rs`
            // belum punya endpoint ringkasan, dan menambahnya adalah perubahan
            // server tersendiri.
            //
            // Tanpa cabang di profil (akun pusat), panggilan ini TIDAK
            // dilakukan sama sekali: `kodeDealer` kosong membuat server
            // menjawab 13 cabang berikut seluruh serialnya — beban yang tak
            // sebanding untuk satu lencana, dan angka "seluruh perusahaan" pun
            // bukan antrian milik siapa pun. Kartunya tetap tampil dengan
            // angka `null` (= belum diketahui), bukan 0 yang berbohong.
            if (ActivitySource.GODA_SN_BELUM_LENGKAP in sources) jobs += async {
                val dealer = DealerAlias.resolveFromBranchName(user?.cabangName)
                if (dealer != null) {
                    when (val r = godaRepository.stok(dealer)) {
                        is AuthResult.Success ->
                            counts[ActivitySource.GODA_SN_BELUM_LENGKAP] =
                                r.data.baris.count { belumLengkap(it) }
                        is AuthResult.Failure -> failed += ActivitySource.GODA_SN_BELUM_LENGKAP
                    }
                }
            }

            if (ActivitySource.OPNAME_MANUAL_PENDING in sources) jobs += async {
                when (val r = opnameRepository.manualUnits()) {
                    is AuthResult.Success -> counts[ActivitySource.OPNAME_MANUAL_PENDING] = r.data.size
                    is AuthResult.Failure -> failed += ActivitySource.OPNAME_MANUAL_PENDING
                }
            }

            jobs.forEach { it.await() }
        }

        val leadsToday = if (ActivitySource.LEADS_CACHE in sources) {
            // `cachedLeads(search)` — kirim "" untuk seluruh cache. Room, bukan
            // jaringan: seksi tugas harian tetap benar saat offline.
            leadsCreatedTodayBy(
                leads = crmRepository.cachedLeads("").map { it.createdAt to it.createdBy },
                userId = user?.id,
                todayIso = todayIso,
            )
        } else 0

        val tasks = buildDailyTasks(
            items = items,
            checkInAt = checkInAt,
            checkOutAt = checkOutAt,
            leadsToday = leadsToday,
            // I3 audit 2026-07-28: tanpa ini, gagal jaringan tampil identik dgn
            // "belum absen" → user yang sudah check-in didorong absen lagi.
            absensiFailed = ActivitySource.ABSENSI_TODAY in failed,
            aktivitasToday = counts[ActivitySource.RAPORT_TODAY] ?: 0,
            aktivitasFailed = ActivitySource.RAPORT_TODAY in failed,
            aktivitasExpected = aktivitasExpected,
            prospekTarget = prospekTarget,
        )

        // Ada load() yang lebih baru sudah dipanggil sejak kita mulai (atau sedang
        // menulis state akhirnya) — jangan timpa dgn hasil yang sudah basi.
        if (myGeneration != loadGeneration) return

        _uiState.value = ActivityUiState(
            isLoading = false,
            userName = user?.name.orEmpty(),
            cabangName = user?.cabangName.orEmpty(),
            tasks = tasks,
            progress = dailyProgressLabel(tasks),
            queueCards = buildQueueCards(
                items, counts, failed, roles,
                alerts = gantungAlert
                    ?.let { mapOf(ActivitySource.DLV_PENDING_PAYMENT to it) }
                    ?: emptyMap(),
                kuponGebyarBoleh = kuponGebyarBoleh,
            ),
            actions = items.filter { it.kind == ActivityKind.AKSI },
            spkToday = spkTodayCounter.todayCount(todayIso),
            panduanVisible = panduanAlurVisible(roles, capabilities),
        )
    }

    /**
     * Ambil ulang peta kemampuan bila kunci latch berbeda dari yang terakhir
     * berhasil diambil — SIDIK AKSES [user] berubah, ATAU token berotasi (lihat
     * [com.krisoft.tridjayaelektronik.ui.home.kunciLatchKemampuan]) — lalu muat
     * ulang PENUH dengan peta baru itu.
     *
     * **Kenapa perlu.** Gerbang menu di sini dinilai `visibleActivityItems(roles,
     * capabilities, …)`, dan `gateAllows` MENDAHULUKAN peta kemampuan lalu
     * fail-closed. `capabilities_for` di server mengisi SEMUA kunci dengan
     * boolean eksplisit, jadi cabang cadangan berbasis role tak pernah tersentuh
     * saat online: 23 dari 26 item registri sepenuhnya ditentukan peta itu
     * (hanya `aktivitas`, `lapor_komplain`, & `pemasangan_ac` yang
     * ber-`capability = null`). Satu item — `kupon_gebyar` — punya kunci
     * kemampuan TAPI gerbang sesungguhnya vonis cabang dari server; lihat
     * `kuponGebyarCardVisible`.
     * Menyegarkan `role`/`roles`/`page_grants` saja (perbaikan `sesiSetelahRefresh`)
     * karena itu belum mengubah satu pun menu — petanya harus ikut disegarkan.
     *
     * **Coroutine TERPISAH, bukan `await` di dalam `load()`** — I2 audit
     * 2026-07-28: mengambilnya inline memblokir SELURUH layar (termasuk HARI INI
     * & PINTASAN yang sengaja tak butuh jaringan) sampai timeout OkHttp saat
     * sinyal jelek. Layar dirender duluan dengan peta yang ada (atau `null` →
     * gerbang jatuh ke `allowedRoles`), lalu dimuat ulang PENUH begitu peta baru
     * tiba supaya item yang baru terbuka ikut fan-out — bukan tampil tanpa angka.
     *
     * Muat-ulang itu aman terhadap urutan: [loadGeneration] membuat panggilan
     * TERAKHIR yang menang, apa pun urutan selesainya.
     *
     * Penjaga badai-request dan penjaga peta-baik ada di [PenyegarKemampuan] —
     * baca KDoc-nya sebelum mengubah pemicu di sini.
     */
    private fun segarkanKemampuan(user: UserDto?) {
        val sidik = sidikAkses(user)
        viewModelScope.launch {
            val peta = penyegarKemampuan.segarkan(sidik) ?: return@launch
            // Rotasi token membuka latch tiap ~15 menit, dan jawabannya hampir
            // selalu peta yang isinya SAMA. Tanpa perbandingan nilai ini, tiap
            // rotasi memicu satu fan-out penuh belasan endpoint yang tak
            // mengubah satu piksel pun. Yang berhak memicu muat-ulang adalah
            // perubahan ISI peta, bukan kejadian pengambilannya.
            if (peta == capabilities) return@launch
            capabilities = peta
            load()
        }
    }

    private companion object {
        const val CACHE_WINDOW_MS = 60_000L
    }
}
