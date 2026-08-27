package com.krisoft.tridjayaelektronik.ui.aktivitas

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krisoft.tridjayaelektronik.data.AuthRepository
import com.krisoft.tridjayaelektronik.data.AuthResult
import com.krisoft.tridjayaelektronik.data.AktivitasRepository
import com.krisoft.tridjayaelektronik.data.model.AktivitasItemDto
import com.krisoft.tridjayaelektronik.data.model.AktivitasPositionDto
import com.krisoft.tridjayaelektronik.data.model.jumlahButirAktif
import com.krisoft.tridjayaelektronik.domain.sales.KlasemenStandings
import com.krisoft.tridjayaelektronik.ui.home.effectiveRoles
import com.krisoft.tridjayaelektronik.util.PhotoWatermark
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * Satu gambar di staging.
 *
 * [file] `null` = bukti LAMA yang sudah ada di server (tak ada salinan lokal,
 * tak perlu diunggah ulang). [url] terisi begitu unggahannya sukses — itulah
 * yang membuat percobaan ulang tidak mengunggah berkas yang sudah naik.
 */
data class GambarBukti(
    val file: File? = null,
    val url: String? = null,
    val dariGaleri: Boolean = false,
)

data class VideoBukti(val uri: Uri, val nama: String, val ukuranBytes: Long)

/** Berkas yang dipilih untuk satu baris aktivitas, belum tentu terkirim. */
data class PilihanBukti(
    val gambar: List<GambarBukti> = emptyList(),
    val video: VideoBukti? = null,
)

/** Progres pengiriman satu baris — menggantikan `busyIndex` telanjang. */
data class KirimProgres(val index: Int, val label: String)

/**
 * Penolakan yang TIDAK akan sembuh dengan menekan tombol yang sama lagi.
 *
 * [isi] sengaja memuat pesan server APA ADANYA, tidak diparafrase: kalimat
 * penolakan sudah ditulis lengkap dengan langkah konkretnya di
 * `aktivitas_harian::domain::pesan_bukti_hari_lain` dan saudara-saudaranya.
 * Menulis ulang di sini melahirkan versi kedua yang akan berselisih diam-diam
 * begitu salah satunya diperbarui.
 */
data class BlokirBukti(val judul: String, val isi: String)

data class AktivitasUiState(
    val isLoading: Boolean = true,
    /** Gagal MEMUAT daftar (layar tak bisa dipakai). Beda dari [message]. */
    val error: String? = null,
    val posisi: String = "",
    val divisi: String = "",
    /**
     * Penempatan KPI yang dipakai memilih daftar aktivitas — kosong kalau
     * orangnya memang tak punya penempatan (jalur cadangan tag).
     *
     * Ada khusus untuk pesan layar-kosong. Sejak daftar aktivitas mengikuti
     * PENEMPATAN, sebab layar kosong yang paling mungkin adalah penempatan yang
     * belum punya divisi di master — dan menyebut TAG di pesan itu menyuruh PIC
     * menambahkan divisi yang salah.
     */
    val penempatanId: String = "",
    /**
     * Role efektif orangnya lolos gerbang BACA `GET /raport-harian`
     * (`LIST_ROLES`), jadi tombol "Lihat Riwayat" boleh ditampilkan.
     *
     * Default `false` = fail-closed: pemanggil/keadaan yang belum sempat
     * menghitungnya menyembunyikan tombol, bukan memunculkan tombol yang
     * berujung 403. Lihat [AKTIVITAS_BACA_ROLES] untuk kenapa gerbang baca
     * lebih sempit dari gerbang kartu ini.
     */
    val bolehLihatRiwayat: Boolean = false,
    val aktivitas: List<String> = emptyList(),
    /**
     * Berapa butir dari [aktivitas] yang benar-benar DITAGIH — sudah dikurangi
     * penanda `nonaktif` dari master.
     *
     * Dipisah dari [aktivitas] dan BUKAN dipakai menyaringnya: daftar yang
     * dirender harus tetap utuh karena index loop-nya ADALAH `jobdeskIndex`
     * yang dikirim ke server, dan submit itu upsert atas
     * `(karyawan_id, tanggal, divisi, jobdesk_index)`. Menyaring daftar akan
     * menggeser index butir sesudahnya dan menimpa bukti yang salah, tanpa
     * satu pun error. Yang dikurangi hanya PENYEBUT.
     *
     * 0 = belum termuat; [butirDitagih] yang memberi lantainya.
     */
    val butirAktif: Int = 0,
    val submitted: Map<Int, AktivitasItemDto> = emptyMap(),
    /** Berkas terpilih per index aktivitas, belum dikirim. */
    val pilihan: Map<Int, PilihanBukti> = emptyMap(),
    /** Baris yang sedang diunggah/dikirim, beserta labelnya. */
    val kirim: KirimProgres? = null,
    /**
     * Kegagalan satu AKSI (kirim/unggah), bukan kegagalan memuat layar. Sukses
     * sengaja tak berpesan: barisnya sendiri langsung berubah jadi "menunggu
     * review", itu umpan balik yang lebih jujur daripada toast.
     */
    val message: String? = null,
    /**
     * Kegagalan PERMANEN — server sudah memutuskan, mencoba lagi menghasilkan
     * jawaban yang sama persis. Dipisah dari [message] karena presentasinya
     * harus berbeda: [message] dirender sebagai item DI DALAM daftar, jadi
     * karyawan yang sudah menggulir layar tak melihatnya sama sekali.
     */
    val blokir: BlokirBukti? = null,
) {
    val terkirim: Int get() = aktivitas.indices.count { it in submitted }

    /**
     * Penyebut "x/N aktivitas terkirim" — cerminan
     * `resolveExpectedAktivitasCount` web: `max(butir aktif, index tertinggi
     * yang sudah terkirim + 1, 1)`.
     *
     * Suku KEDUA bukan hiasan. Butir yang ditandai nonaktif SESUDAH seseorang
     * mengirimnya tetap punya baris di server; tanpa suku itu layar menulis
     * "13/12" — pecahan yang lebih dari satu, dan itu terbaca sebagai layar
     * yang rusak, bukan sebagai master yang berubah.
     */
    val butirDitagih: Int
        get() {
            val tertinggiTerkirim = submitted.keys.maxOrNull()?.plus(1) ?: 0
            return maxOf(butirAktif, tertinggiTerkirim, 1)
        }

    /**
     * Turunan dari [kirim]. Kunci tetap GLOBAL (satu baris sibuk = seluruh
     * layar terkunci) karena `finish()` bersandar pada satu identitas "yang
     * sedang sibuk"; memecahnya jadi per-baris tanpa menulis ulang seluruh
     * jalur kegagalan akan meninggalkan baris terkunci selamanya.
     */
    val busyIndex: Int? get() = kirim?.index
}

/**
 * Input Aktivitas (raport harian) — BETA.
 *
 * Satu aktivitas dikirim satu per satu (server-nya memang upsert per baris).
 * Bukti boleh: foto kamera, sampai [MAX_GAMBAR] gambar dari galeri, satu video,
 * atau "tanpa bukti + alasan". Foto — dari kamera MAUPUN galeri — selalu
 * di-watermark; judulnya dibedakan untuk galeri (lihat [watermarkTitleBukti]).
 *
 * Alur pilih → staging → "Kirim bukti" disengaja, bukan auto-kirim seperti
 * dulu: satu baris kini boleh punya beberapa gambar, dan menambah satu foto
 * harus mengirim ULANG daftar lengkapnya karena server menimpa `bukti_url`
 * seluruhnya.
 */
@HiltViewModel
class AktivitasViewModel @Inject constructor(
    private val repository: AktivitasRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AktivitasUiState())
    val state: StateFlow<AktivitasUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val user = authRepository.cachedUser
            val divisi = user?.divisi.orEmpty()
            val today = KlasemenStandings.todayIso()

            // TIGA panggilan tak saling bergantung — jalankan bareng.
            //
            // `penempatanSaya()` ikut fan-out ini, BUKAN berurutan sesudahnya:
            // ia menentukan daftar aktivitas yang dirender, jadi memanggilnya
            // belakangan menambah satu round-trip tepat di jalur yang paling
            // sering dibuka orang tiap pagi.
            val positionsResult: AuthResult<List<AktivitasPositionDto>>
            val todayResult: AuthResult<List<AktivitasItemDto>>
            val penempatan: PenempatanSaya
            coroutineScope {
                val positions = async { repository.aktivitasPositions() }
                val terkirim = async { repository.raportOfDay(today, user?.id) }
                val tempat = async { repository.penempatanSaya() }
                positionsResult = positions.await()
                todayResult = terkirim.await()
                penempatan = tempat.await()
            }

            when (positionsResult) {
                is AuthResult.Failure -> {
                    _state.update { it.copy(isLoading = false, error = positionsResult.message) }
                    return@launch
                }
                is AuthResult.Success -> {
                    // PENEMPATAN, bukan tag. Sampai 2026-08-18 baris ini
                    // memakai `matchAktivitasPosition(divisi, ...)` sementara
                    // gerbang absen pulang & KPI sudah memakai penempatan —
                    // pemegang tag `admin-penjualan,kasir` karena itu melihat 8
                    // butir KASIR di HP padahal dinilai atas 6 butir ADMIN
                    // PENJUALAN. Jalur tag tetap dipakai sebagai CADANGAN di
                    // dalam `pilihAktivitasUntukInput`.
                    val posisi = pilihAktivitasUntukInput(divisi, positionsResult.data, penempatan)
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = null,
                            divisi = divisi,
                            // Dihitung dari role EFEKTIF profil, bukan role
                            // utama: akun multi-role primary `sales` + sekunder
                            // `karyawan` tetap berhak membaca riwayatnya.
                            bolehLihatRiwayat = bolehLihatRiwayat(effectiveRoles(user)),
                            penempatanId = (penempatan as? PenempatanSaya.Ada)?.positionId.orEmpty(),
                            posisi = posisi?.posisi.orEmpty(),
                            // `.jobdesks` = nama field DI KABEL, ejaan lama.
                            // Daftar UTUH (index-nya = `jobdeskIndex` yang
                            // dikirim); penanda `nonaktif` cuma mengurangi
                            // PENYEBUT lewat `butirAktif`.
                            aktivitas = posisi?.jobdesks.orEmpty(),
                            butirAktif = posisi?.jumlahButirAktif() ?: 0,
                            // Gagal memuat yang sudah terkirim TIDAK mengunci layar:
                            // user tetap boleh mengirim (server upsert, aman diulang).
                            submitted = (todayResult as? AuthResult.Success)
                                ?.let { r -> submittedByIndex(r.data) }.orEmpty(),
                        )
                    }
                }
            }
        }
    }

    // ── Staging ──────────────────────────────────────────────────────────────

    /**
     * Isi staging baris [index]. Kalau belum pernah disentuh dan barisnya SUDAH
     * punya bukti gambar di server, bukti lama itu ikut dimuat — server upsert
     * dan MENIMPA `bukti_url` seluruhnya, jadi mengirim hanya berkas baru sama
     * dengan menghapus bukti lama tanpa satu pun error.
     */
    private fun pilihanUntuk(index: Int): PilihanBukti {
        _state.value.pilihan[index]?.let { return it }
        val lama = _state.value.submitted[index]
        val urlLama = if (lama?.mode.equals("image", ignoreCase = true)) {
            parseEvidenceUrls(lama?.evidenceUrl)
        } else {
            emptyList()
        }
        return PilihanBukti(gambar = urlLama.map { GambarBukti(file = null, url = it) })
    }

    private fun setPilihan(index: Int, ubah: (PilihanBukti) -> PilihanBukti) {
        val baru = ubah(pilihanUntuk(index))
        _state.update { it.copy(pilihan = it.pilihan + (index to baru), message = null) }
    }

    fun tambahFotoKamera(index: Int, file: File) {
        setPilihan(index) { p ->
            if (p.gambar.size >= MAX_GAMBAR) {
                _state.update { it.copy(message = "Maksimal $MAX_GAMBAR gambar per aktivitas.") }
                p
            } else {
                p.copy(gambar = p.gambar + GambarBukti(file = file, dariGaleri = false), video = null)
            }
        }
    }

    /**
     * [files] sudah disalin ke cache oleh layar — grant Uri dari picker tidak
     * persistable dan hilang saat proses mati, jadi menyalinnya nanti (saat
     * Kirim) berarti kehilangan berkas tanpa penjelasan.
     *
     * [diabaikan] = jumlah yang dibuang layar karena kelebihan slot / terlalu
     * besar / tak terbaca. Dilaporkan, tidak ditelan.
     */
    fun tambahFotoGaleri(index: Int, files: List<File>, diabaikan: Int = 0) {
        if (files.isEmpty() && diabaikan == 0) return
        // Sisa slot dihitung SEBELUM state berubah — menghitungnya sesudah akan
        // membaca daftar yang sudah bertambah dan selalu melaporkan 0 diabaikan.
        val muat = (MAX_GAMBAR - pilihanUntuk(index).gambar.size).coerceAtLeast(0)
        val takMuat = (files.size - muat).coerceAtLeast(0)
        setPilihan(index) { p ->
            p.copy(
                gambar = p.gambar + files.take(muat).map { GambarBukti(file = it, dariGaleri = true) },
                video = null,
            )
        }
        val total = diabaikan + takMuat
        if (total > 0) {
            _state.update {
                it.copy(message = "$total gambar diabaikan — maksimal $MAX_GAMBAR gambar per aktivitas.")
            }
        }
    }

    /** Video menggantikan gambar: server hanya punya SATU `mode` per baris. */
    fun pilihVideo(index: Int, uri: Uri, nama: String, ukuranBytes: Long) {
        val gate = gateKirimBukti(jumlahGambar = 0, adaVideo = true, ukuranVideoBytes = ukuranBytes)
        if (!gate.ok) {
            _state.update { it.copy(message = gate.alasan) }
            return
        }
        setPilihan(index) {
            PilihanBukti(gambar = emptyList(), video = VideoBukti(uri, nama, ukuranBytes))
        }
    }

    fun hapusGambar(index: Int, posisi: Int) {
        setPilihan(index) { p ->
            p.copy(gambar = p.gambar.filterIndexed { i, _ -> i != posisi })
        }
    }

    fun hapusVideo(index: Int) = setPilihan(index) { it.copy(video = null) }

    // ── Kirim ────────────────────────────────────────────────────────────────

    /**
     * Unggah seluruh berkas staging baris [index] lalu kirim barisnya.
     *
     * Gambar diunggah satu per satu dan URL hasilnya DISIMPAN per berkas: kalau
     * gagal di tengah (jendela jam ditutup, sinyal putus), yang sudah naik tak
     * diunggah ulang saat dicoba lagi. `POST /raport-harian/upload` memanggil
     * `ensure_window_open()` di SETIAP request, jadi kegagalan di gambar ke-4
     * dari 6 itu skenario nyata, bukan teoretis.
     *
     * [send] TIDAK pernah dipanggil dengan daftar parsial — bukti yang tak
     * lengkap lebih buruk daripada kegagalan yang terlihat.
     */
    fun kirimBukti(index: Int, resolver: ContentResolver) {
        val aktivitas = _state.value.aktivitas.getOrNull(index) ?: return
        if (_state.value.kirim != null) return
        // Hari Minggu dihadang SEBELUM satu berkas pun naik. Server menolak di
        // `POST /raport-harian` tapi (di biner yang beredar) MEMBIARKAN
        // `/raport-harian/upload`, dan fungsi ini mengunggah semuanya dulu baru
        // mengirim — jadi tanpa gerbang ini orang membayar seluruh unggahan
        // untuk penolakan yang sudah pasti. Minggu 16 Agustus 2026: 36 berkas
        // terunggah, nol baris tercatat. Lihat `gerbangHariIni`.
        val hari = gerbangHariIni(System.currentTimeMillis())
        if (!hari.ok) {
            _state.update { it.copy(message = hari.alasan) }
            return
        }
        // Sama alasannya dengan gerbang Minggu: server sudah pasti menolak,
        // jadi mengunggah dulu berarti membayar penuh untuk jawaban yang sudah
        // diketahui. Lihat `terkunciPic`.
        if (terkunciPic(_state.value.submitted[index]?.reviewStatus)) {
            _state.update { it.copy(message = PESAN_TERKUNCI_PIC) }
            return
        }
        val pilihan = pilihanUntuk(index)
        val gate = gateKirimBukti(
            jumlahGambar = pilihan.gambar.size,
            adaVideo = pilihan.video != null,
            ukuranVideoBytes = pilihan.video?.ukuranBytes ?: 0L,
        )
        if (!gate.ok) {
            _state.update { it.copy(message = gate.alasan) }
            return
        }

        viewModelScope.launch {
            val video = pilihan.video
            if (video != null) kirimVideo(index, aktivitas, video, resolver)
            else kirimGambar(index, aktivitas, pilihan.gambar)
        }
    }

    private suspend fun kirimGambar(index: Int, aktivitas: String, awal: List<GambarBukti>) {
        val user = authRepository.cachedUser
        val subtitle = listOfNotNull(user?.name, user?.cabangName)
            .filter { it.isNotBlank() }.joinToString(" · ")
        val daftar = awal.toMutableList()
        val stempel = System.currentTimeMillis()

        for (i in daftar.indices) {
            val item = daftar[i]
            if (item.url != null) continue          // bukti lama, atau sisa percobaan sebelumnya
            val berkas = item.file ?: continue
            setProgres(index, "Mengunggah gambar ${i + 1} dari ${daftar.size}…")

            val bytes = withContext(Dispatchers.Default) {
                PhotoWatermark.prepareWatermarkedJpeg(
                    file = berkas, lat = null, lng = null,
                    title = watermarkTitleBukti(item.dariGaleri), subtitle = subtitle,
                )
            }?.first
            if (bytes == null) {
                simpanParsial(index, daftar)
                finish(index, pesanGagalDekode(item.dariGaleri))
                return
            }

            when (val up = repository.uploadEvidence(bytes, namaBerkasGambar(i, stempel))) {
                is AuthResult.Failure -> {
                    simpanParsial(index, daftar)
                    // Ekor "Tekan Kirim bukti lagi" HANYA untuk kegagalan yang
                    // memang bisa sembuh (jaringan putus, 5xx). Sampai vc97 ia
                    // ditempelkan TANPA SYARAT — termasuk pada 400 permanen —
                    // sehingga karyawan membaca dua perintah yang bertabrakan
                    // ("ganti fotonya" lalu "tekan lagi") dan menuruti yang
                    // terakhir. Terukur 2026-08-21: tiga orang menekan ulang
                    // 10-13 kali dalam setengah menit atas foto yang sama, dan
                    // setiap kali dijawab 400 yang identik.
                    if (gagalPermanen(up.httpStatus)) {
                        blokir(
                            index,
                            "Gambar ke-${i + 1} ditolak",
                            up.message,
                        )
                    } else {
                        finish(
                            index,
                            "Gambar ke-${i + 1} dari ${daftar.size} gagal: ${up.message} " +
                                "Tekan \"Kirim bukti\" lagi untuk melanjutkan dari gambar ini.",
                        )
                    }
                    return
                }
                is AuthResult.Success -> daftar[i] = item.copy(url = up.data)
            }
        }

        simpanParsial(index, daftar)
        val urls = daftar.mapNotNull { it.url }
        val evidenceUrl = buildEvidenceUrl("image", urls)
        if (evidenceUrl == null) {
            finish(index, "Tidak ada gambar yang berhasil diunggah.")
            return
        }
        setProgres(index, "Menyimpan laporan…")
        send(index, aktivitas, mode = "image", evidenceUrl = evidenceUrl)
    }

    private suspend fun kirimVideo(
        index: Int,
        aktivitas: String,
        video: VideoBukti,
        resolver: ContentResolver,
    ) {
        val ext = ekstensiVideo(video.nama, resolver.getType(video.uri))
        if (ext == null) {
            finish(index, "Format video harus MP4, WEBM, atau MOV.")
            return
        }
        setProgres(index, "Mengunggah video (${formatUkuranBerkas(video.ukuranBytes)})…")
        val hasil = repository.uploadEvidenceVideo(
            resolver = resolver,
            uri = video.uri,
            namaFile = namaBerkasVideo(ext, System.currentTimeMillis()),
            mimeType = mimeVideo(ext),
            ukuranBytes = video.ukuranBytes,
        )
        when (hasil) {
            is AuthResult.Failure ->
                if (gagalPermanen(hasil.httpStatus)) blokir(index, "Video ditolak", hasil.message)
                else finish(index, hasil.message)
            is AuthResult.Success -> {
                setProgres(index, "Menyimpan laporan…")
                // POLOS, bukan array — sama seperti web (`KaryawanAktivitasPage.tsx`).
                send(index, aktivitas, mode = "video", evidenceUrl = hasil.data)
            }
        }
    }

    /**
     * "Tidak ada bukti" — server mewajibkan alasan minimal 10 karakter; dicek
     * juga di sini supaya user tak menunggu satu putaran jaringan untuk tahu.
     */
    fun submitWithoutEvidence(index: Int, alasan: String) {
        val aktivitas = _state.value.aktivitas.getOrNull(index) ?: return
        if (_state.value.kirim != null) return
        val reason = alasan.trim()
        if (reason.length < MIN_REASON_LENGTH) {
            _state.update { it.copy(message = "Alasan wajib diisi minimal $MIN_REASON_LENGTH karakter") }
            return
        }
        // Ikut digerbang supaya kedua tombol menjawab hal yang sama di hari yang
        // sama. Jalur ini tak mengunggah apa pun, jadi ongkos tak-digerbang cuma
        // satu putaran jaringan — tapi dua tombol yang berbeda vonis untuk satu
        // aturan adalah cara termudah membuat orang mengira ini bug acak.
        val hari = gerbangHariIni(System.currentTimeMillis())
        if (!hari.ok) {
            _state.update { it.copy(message = hari.alasan) }
            return
        }
        if (terkunciPic(_state.value.submitted[index]?.reviewStatus)) {
            _state.update { it.copy(message = PESAN_TERKUNCI_PIC) }
            return
        }
        viewModelScope.launch {
            // Staging WAJIB dibuang dulu: server menolak `none` yang membawa
            // evidenceUrl, dan sisa pilihan di layar akan terbaca seolah masih
            // akan ikut terkirim.
            _state.update {
                it.copy(
                    pilihan = it.pilihan - index,
                    kirim = KirimProgres(index, "Menyimpan laporan…"),
                )
            }
            send(index, aktivitas, mode = "none", employeeNote = reason)
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    fun consumeBlokir() = _state.update { it.copy(blokir = null) }

    // ── Internal ─────────────────────────────────────────────────────────────

    private fun setProgres(index: Int, label: String) =
        _state.update { it.copy(kirim = KirimProgres(index, label)) }

    /** Simpan URL yang SUDAH didapat supaya percobaan ulang melanjutkan, bukan mengulang. */
    private fun simpanParsial(index: Int, daftar: List<GambarBukti>) =
        _state.update { it.copy(pilihan = it.pilihan + (index to PilihanBukti(gambar = daftar))) }

    private suspend fun send(
        index: Int,
        aktivitas: String,
        mode: String,
        evidenceUrl: String? = null,
        employeeNote: String? = null,
    ) {
        when (val result = repository.submitItem(index, aktivitas, mode, evidenceUrl, employeeNote)) {
            // Penolakan gerbang batas-atas `jobdeskIndex` (server sejak
            // 2026-08-21) mendarat DI SINI, dan ia permanen: indeksnya tak akan
            // berubah dengan mencoba lagi. Dialog, bukan baris di tengah daftar.
            is AuthResult.Failure ->
                if (gagalPermanen(result.httpStatus)) blokir(index, "Laporan ditolak", result.message)
                else finish(index, result.message)
            is AuthResult.Success -> {
                // Muat ulang baris hari ini supaya status (menunggu/disetujui) dan
                // bukti datang dari server, bukan ditebak di klien.
                val today = KlasemenStandings.todayIso()
                val terkirim = repository.raportOfDay(today, authRepository.cachedUser?.id)
                _state.update { state ->
                    state.copy(
                        kirim = null,
                        message = null,
                        pilihan = state.pilihan - index,
                        submitted = (terkirim as? AuthResult.Success)
                            ?.let { submittedByIndex(it.data) } ?: state.submitted,
                    )
                }
            }
        }
    }

    /**
     * Kegagalan permanen: buka dialog, dan KOSONGKAN [AktivitasUiState.message]
     * supaya keluhan yang sama tidak terbaca dua kali dalam dua bentuk.
     */
    private fun blokir(index: Int, judul: String, isi: String) {
        _state.update {
            val dasar = if (it.busyIndex == index) it.copy(kirim = null) else it
            dasar.copy(message = null, blokir = BlokirBukti(judul, isi))
        }
    }

    private fun finish(index: Int, message: String) {
        _state.update {
            if (it.busyIndex == index) it.copy(kirim = null, message = message)
            else it.copy(message = message)
        }
    }

    companion object {
        /** Sama dengan `MIN_NO_EVIDENCE_REASON_LENGTH` web & guard `upsert` server. */
        const val MIN_REASON_LENGTH = 10
    }
}
