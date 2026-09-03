package com.krisoft.tridjayaelektronik.ui.aktivitas

import android.content.ContentResolver
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krisoft.tridjayaelektronik.data.AuthRepository
import com.krisoft.tridjayaelektronik.data.AuthResult
import com.krisoft.tridjayaelektronik.data.AktivitasRepository
import com.krisoft.tridjayaelektronik.data.model.UserDto
import com.krisoft.tridjayaelektronik.data.model.AktivitasItemDto
import com.krisoft.tridjayaelektronik.data.model.AktivitasPositionDto
import com.krisoft.tridjayaelektronik.data.model.jumlahButirAktif
import com.krisoft.tridjayaelektronik.domain.sales.KlasemenStandings
import com.krisoft.tridjayaelektronik.ui.home.effectiveRoles
import com.krisoft.tridjayaelektronik.util.PhotoWatermark
import com.krisoft.tridjayaelektronik.util.VideoTranscoder
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
import java.util.UUID
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
    /**
     * Jumlah chat yang diketik untuk butir CHAT trainee — `null` = kotaknya
     * masih kosong, BUKAN nol. Dua keadaan itu tak boleh disamakan: `0` adalah
     * klaim "saya chat nol orang" dan server memang menolaknya sebagai angka
     * kurang, sedangkan kosong berarti orangnya belum mengisi.
     *
     * Baris non-chat tak pernah mengisinya, dan `submitItem` mengirim `null`
     * apa adanya untuk mereka.
     */
    val jumlah: Int? = null,
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
    /**
     * Gerbang chat trainee dari server — `null` = TIDAK BERLAKU untuk orang
     * ini (bukan trainee, saklar mati, setelan rusak, atau server lama).
     *
     * Default `null` = fail-open, dan itu keputusan, bukan kelalaian: layar
     * yang mengunci karena ambang belum/gagal termuat akan menahan orangnya
     * dari pekerjaan yang justru dinilai. Lihat KDoc [AmbangChatTrainee].
     */
    val chatTrainee: AmbangChatTrainee? = null,
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
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(AktivitasUiState())
    val state: StateFlow<AktivitasUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val user = authRepository.cachedUser
            val today = KlasemenStandings.todayIso()

            // TIGA panggilan tak saling bergantung — jalankan bareng.
            //
            // `penempatanSaya()` ikut fan-out ini, BUKAN berurutan sesudahnya:
            // ia menentukan daftar aktivitas yang dirender (DAN, sejak vc123,
            // ambang chat trainee), jadi memanggilnya belakangan menambah satu
            // round-trip tepat di jalur yang paling sering dibuka orang tiap
            // pagi. Ambang chat menumpang respons yang sama — nol panggilan
            // tambahan, dan gagal mengambilnya FAIL-OPEN (lihat `chatTrainee`).
            val positionsResult: AuthResult<List<AktivitasPositionDto>>
            val todayResult: AuthResult<List<AktivitasItemDto>>
            val hasilPenempatan: PenempatanSayaHasil
            val profilResult: AuthResult<UserDto>
            coroutineScope {
                val positions = async { repository.aktivitasPositions() }
                val terkirim = async { repository.raportOfDay(today, user?.id) }
                val tempat = async { repository.penempatanSaya() }
                // Profil SEGAR dari server, bukan `cachedUser`.
                //
                // `divisi` adalah jalur CADANGAN daftar aktivitas (dipakai orang
                // yang belum punya baris `kpi_assignments`), dan cache-nya cuma
                // ikut segar saat rotasi token — ~15 menit sekali, atau tak
                // sama sekali kalau app dibuka lalu ditutup di antaranya. Jadi
                // jabatan yang baru diganti admin bisa belum sampai ke layar
                // ini, dan orangnya mengisi daftar aktivitas jabatan LAMA.
                //
                // Ikut fan-out, bukan berurutan: ia menentukan daftar yang
                // dirender, jadi memanggilnya belakangan menambah satu
                // round-trip di jalur yang dibuka tiap pagi.
                val profil = async { authRepository.profile() }
                positionsResult = positions.await()
                todayResult = terkirim.await()
                hasilPenempatan = tempat.await()
                profilResult = profil.await()
            }
            val penempatan = hasilPenempatan.penempatan
            // FAIL-SOFT ke cache: profil yang sekejap gagal diambil tak boleh
            // mengosongkan daftar aktivitas orang yang sedang mengisinya.
            val divisi = (profilResult as? AuthResult.Success)?.data?.divisi
                ?: user?.divisi.orEmpty()

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
                            butirAktif = jumlahButirAktif(posisi),
                            chatTrainee = hasilPenempatan.chatTrainee,
                            // Gagal memuat yang sudah terkirim TIDAK mengunci layar:
                            // user tetap boleh mengirim (server upsert, aman diulang).
                            // Disaring terhadap daftar yang SEDANG dirender:
                            // baris raport milik jabatan lama ber-index sama
                            // akan menempel ke aktivitas yang berbeda kalau
                            // dipetakan lewat index saja. Lihat
                            // [terkirimUntukAktivitas].
                            submitted = (todayResult as? AuthResult.Success)
                                ?.let { r -> terkirimUntukAktivitas(r.data, posisi?.jobdesks.orEmpty()) }
                                .orEmpty(),
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
        // `jumlah` lama ikut di-seed dengan alasan yang SAMA seperti bukti
        // gambar: server upsert dan menimpa kolomnya seluruhnya, jadi mengirim
        // ulang baris tanpa angka akan menghapus angka yang sudah tercatat.
        // Untuk butir CHAT trainee server memang akan menolaknya, tapi
        // penolakan itu datang SETELAH videonya terunggah penuh — dan yang
        // hilang bukan cuma waktu, melainkan angka yang tadinya benar.
        return PilihanBukti(
            gambar = urlLama.map { GambarBukti(file = null, url = it) },
            jumlah = lama?.jumlah,
        )
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
     * [terlaluBesar] dan [takTerbaca] adalah dua sebab BERBEDA yang sampai vc116
     * ditumpuk jadi satu penghitung lalu dijelaskan dengan satu kalimat tentang
     * kuota — lihat [pesanGambarDiabaikan] untuk kenapa itu merugikan. Dilaporkan
     * per sebab, tidak ditelan dan tidak disamarkan.
     */
    fun tambahFotoGaleri(
        index: Int,
        files: List<File>,
        terlaluBesar: Int = 0,
        takTerbaca: Int = 0,
        sebabTakTerbaca: String? = null,
    ) {
        if (files.isEmpty() && terlaluBesar == 0 && takTerbaca == 0) return
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
        // SETELAH setPilihan — `setPilihan` menulis `message = null`, jadi pesan
        // yang disusun sebelumnya akan terhapus tanpa pernah terlihat.
        pesanGambarDiabaikan(takMuat, terlaluBesar, takTerbaca, sebabTakTerbaca)?.let { pesan ->
            _state.update { it.copy(message = pesan) }
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
            // `jumlah` DIBAWA ikut, bukan direset. Konstruktor telanjang di sini
            // (bukan `copy`) sengaja membuang gambar — server cuma punya satu
            // `mode` per baris — tapi angka chat bukan bukti: membuangnya berarti
            // trainee yang mengetik 200 lalu memilih videonya kehilangan angkanya
            // tanpa satu pun tanda di layar, lalu ditolak server setelah video
            // puluhan MB terunggah penuh.
            PilihanBukti(
                gambar = emptyList(),
                video = VideoBukti(uri, nama, ukuranBytes),
                jumlah = it.jumlah,
            )
        }
    }

    /**
     * Angka chat untuk butir CHAT trainee. Menerima TEKS mentah dari kotak
     * isian dan menormalkannya di sini, bukan di layar: aturannya (hanya digit,
     * dipotong 6 digit) adalah bagian dari kontrak field `jumlah`, dan aturan
     * yang hidup di Composable tak bisa diuji tanpa perangkat.
     *
     * Kosong → `null`, BUKAN 0. Lihat KDoc [PilihanBukti.jumlah].
     */
    fun setJumlah(index: Int, teks: String) {
        val angka = teks.filter { it.isDigit() }.take(6).toIntOrNull()
        setPilihan(index) { it.copy(jumlah = angka) }
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
        // Gerbang butir CHAT trainee, DIDAHULUKAN sebelum satu byte pun naik —
        // alasan yang sama persis dengan gerbang Minggu di atas: server sudah
        // pasti menolak angka yang kurang atau bukti yang bukan video, dan
        // videonya bisa puluhan MB di sinyal lapangan.
        //
        // Dinilai `gerbangChatBerlaku` (TEKS master), bukan nomor butir dari
        // server — lihat KDoc-nya untuk kenapa arahnya sengaja lebih sempit di
        // sisi yang MENAHAN.
        if (gerbangChatBerlaku(_state.value.chatTrainee, aktivitas)) {
            val chat = gateJumlahChat(
                jumlah = pilihan.jumlah,
                ambang = _state.value.chatTrainee?.ambang ?: 0,
                adaVideo = pilihan.video != null,
            )
            if (!chat.ok) {
                _state.update { it.copy(message = chat.alasan) }
                return
            }
        }

        viewModelScope.launch {
            val video = pilihan.video
            if (video != null) kirimVideo(index, aktivitas, video, pilihan.jumlah, resolver)
            else kirimGambar(index, aktivitas, pilihan.gambar, pilihan.jumlah)
        }
    }

    private suspend fun kirimGambar(
        index: Int,
        aktivitas: String,
        awal: List<GambarBukti>,
        jumlah: Int?,
    ) {
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
                simpanParsial(index, daftar, jumlah)
                finish(index, pesanGagalDekode(item.dariGaleri))
                return
            }

            when (val up = repository.uploadEvidence(bytes, namaBerkasGambar(i, stempel))) {
                is AuthResult.Failure -> {
                    simpanParsial(index, daftar, jumlah)
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

        simpanParsial(index, daftar, jumlah)
        val urls = daftar.mapNotNull { it.url }
        val evidenceUrl = buildEvidenceUrl("image", urls)
        if (evidenceUrl == null) {
            finish(index, "Tidak ada gambar yang berhasil diunggah.")
            return
        }
        setProgres(index, "Menyimpan laporan…")
        send(index, aktivitas, mode = "image", evidenceUrl = evidenceUrl, jumlah = jumlah)
    }

    /**
     * Kirim video bukti. Video di atas [MAX_VIDEO_BUKTI_BYTES] (30 MB) DICOBA
     * dikompres dulu (2026-08-29, `VideoTranscoder`) sebelum ditolak — video
     * yang dulu ditolak seketika di [gateKirimBukti] sekarang bisa terkirim
     * kalau hasil kompresinya muat.
     *
     * Video yang SUDAH di bawah budget TIDAK disentuh sama sekali: mengompres
     * ulang video yang sudah muat cuma membakar CPU/baterai petugas (dan
     * waktu tunggu nyata — transcode 30 MB butuh beberapa detik di HP kelas
     * bawah) untuk hasil yang tak berarti apa pun.
     *
     * Kompresi FAIL-SOFT sepenuhnya: gagal/timeout/hasil tetap kebesaran →
     * jatuh ke perilaku LAMA ([pesanVideoTerlaluBesarSetelahKompresi]), bukan
     * kegagalan upload baru. Berkas sementara SELALU dihapus di akhir — baik
     * upload sukses maupun gagal — persis pola `pdf_compress.rs` sisi server.
     */
    private suspend fun kirimVideo(
        index: Int,
        aktivitas: String,
        video: VideoBukti,
        jumlah: Int?,
        resolver: ContentResolver,
    ) {
        val ext = ekstensiVideo(video.nama, resolver.getType(video.uri))
        if (ext == null) {
            finish(index, "Format video harus MP4, WEBM, atau MOV.")
            return
        }

        var uploadUri = video.uri
        var uploadNamaBerkas = namaBerkasVideo(ext, System.currentTimeMillis())
        var uploadMime = mimeVideo(ext)
        var uploadBytes = video.ukuranBytes
        var localFile: File? = null

        if (video.ukuranBytes > MAX_VIDEO_BUKTI_BYTES) {
            setProgres(index, "Mengompres video (${formatUkuranBerkas(video.ukuranBytes)})…")
            val terkompresi = kompresVideoBukti(video)
            if (terkompresi != null && terkompresi.length() in 1 until video.ukuranBytes) {
                localFile = terkompresi
                uploadUri = Uri.fromFile(terkompresi)
                uploadBytes = terkompresi.length()
                // Transformer SELALU menulis kontainer MP4 (muxer bawaan
                // media3), apa pun format sumbernya — mengunggah dengan
                // ekstensi/MIME video ASLI (mis. .mov/.webm) akan membuat
                // magic bytes (MP4 sungguhan) tak cocok dengan yang diklaim
                // nama berkas, dan server memvalidasi keduanya SERENTAK
                // (`is_valid_raport_evidence_content`) → 400 SETELAH upload
                // penuh, kelas kegagalan yang sama persis yang sudah
                // didokumentasikan `ekstensiVideo`/`mimeVideo` untuk pasangan
                // lain.
                uploadNamaBerkas = namaBerkasVideo("mp4", System.currentTimeMillis())
                uploadMime = mimeVideo("mp4")
            } else {
                terkompresi?.delete()
            }
        }

        if (uploadBytes > MAX_VIDEO_BUKTI_BYTES) {
            localFile?.delete()
            finish(index, pesanVideoTerlaluBesarSetelahKompresi())
            return
        }

        setProgres(index, "Mengunggah video (${formatUkuranBerkas(uploadBytes)})…")
        val hasil = repository.uploadEvidenceVideo(
            resolver = resolver,
            uri = uploadUri,
            namaFile = uploadNamaBerkas,
            mimeType = uploadMime,
            ukuranBytes = uploadBytes,
            localFile = localFile,
        )
        // Semantik "finally": berkas sementara tak berguna lagi apa pun hasil
        // upload-nya (sukses = sudah di server, gagal = akan dicoba ulang
        // dari `video.uri` asli via staging, bukan dari salinan ini).
        runCatching { localFile?.delete() }
        when (hasil) {
            is AuthResult.Failure ->
                if (gagalPermanen(hasil.httpStatus)) blokir(index, "Video ditolak", hasil.message)
                else finish(index, hasil.message)
            is AuthResult.Success -> {
                setProgres(index, "Menyimpan laporan…")
                // POLOS, bukan array — sama seperti web (`KaryawanAktivitasPage.tsx`).
                send(index, aktivitas, mode = "video", evidenceUrl = hasil.data, jumlah = jumlah)
            }
        }
    }

    /**
     * Coba kompres [video] lewat [VideoTranscoder] ke berkas sementara di
     * `cacheDir/media-compress/` (dijaga `FileProviderPathsTest`), nama UUID
     * acak — pola sama server (`pdf_compress.rs`): tak pernah menimpa berkas
     * lain, dan tak menyisakan petunjuk isi kalau tertinggal.
     *
     * `null` = dimensi tak terbaca ATAU transcode gagal/timeout — pemanggil
     * ([kirimVideo]) fallback ke berkas asli (fail-soft).
     *
     * **WAJIB dipanggil TANPA `withContext(Dispatchers.…)` di sekitarnya** —
     * lihat KDoc kelas [VideoTranscoder] soal `verifyApplicationThread()`.
     * Dijaga `VideoTranscoderGuardTest`.
     */
    private suspend fun kompresVideoBukti(video: VideoBukti): File? {
        val (lebar, tinggi) = dimensiVideoPascaRotasi(appContext, video.uri) ?: return null
        val output = File(appContext.cacheDir, "media-compress/${UUID.randomUUID()}.mp4")
        output.parentFile?.mkdirs()
        return VideoTranscoder.transcode(
            context = appContext,
            sourceUri = video.uri,
            sourceWidth = lebar,
            sourceHeight = tinggi,
            outputFile = output,
        )
    }

    /**
     * Dimensi video SESUDAH rotasi tampilan. `METADATA_KEY_VIDEO_WIDTH/HEIGHT`
     * mengembalikan ukuran MENTAH encoder; `METADATA_KEY_VIDEO_ROTATION`
     * memberi derajat putar untuk tampil benar — keduanya ditukar posisi kalau
     * putarannya 90/270. `VideoTranscoder`/`Presentation` butuh dimensi
     * TAMPILAN: video portrait yang direkam sensor landscape + rotasi
     * metadata 90° akan salah target scale (lebar↔tinggi tertukar) kalau ini
     * diabaikan.
     *
     * Dijalankan di [Dispatchers.Default] (bukan Main) — `setDataSource`
     * melakukan I/O baca berkas, sama alasannya dengan kenapa `ImagePixelPipeline`
     * tak boleh dipanggil dari Main. TIDAK berkonflik dengan larangan
     * `withContext` di sekitar [VideoTranscoder.transcode]: fungsi ini fungsi
     * TERPISAH yang selesai (dan `withContext`-nya sudah keluar) SEBELUM
     * [kompresVideoBukti] memanggil `transcode`.
     *
     * `null` = metadata tak terbaca (berkas rusak/tak didukung) atau
     * `Throwable` apa pun tertangkap — pemanggil fallback ke berkas asli,
     * bukan mencoba transcode dengan dimensi 0×0.
     */
    private suspend fun dimensiVideoPascaRotasi(context: Context, uri: Uri): Pair<Int, Int>? =
        withContext(Dispatchers.Default) {
            runCatching {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(context, uri)
                    val lebar = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                        ?.toIntOrNull() ?: 0
                    val tinggi = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                        ?.toIntOrNull() ?: 0
                    val rotasi = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                        ?.toIntOrNull() ?: 0
                    when {
                        lebar <= 0 || tinggi <= 0 -> null
                        rotasi == 90 || rotasi == 270 -> tinggi to lebar
                        else -> lebar to tinggi
                    }
                } finally {
                    retriever.release()
                }
            }.getOrNull()
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
        // Butir CHAT trainee tak punya jalur "tanpa bukti" sama sekali —
        // justru `mode="none"` + alasan 10 karakter itulah celah yang gerbang
        // ini tutup: baris yang lahir dari situ langsung menaikkan hitungan
        // `terisi` di gerbang absen pulang tanpa satu pun chat benar-benar
        // terjadi. Server menolaknya; ini cuma mendahulukan kabarnya, dan
        // ongkos tak-digerbang di sini bukan unggahan melainkan satu tombol
        // yang tak pernah bisa berhasil.
        if (gerbangChatBerlaku(_state.value.chatTrainee, aktivitas)) {
            // `?:` bukan hiasan: `alasan` null berarti gerbangnya meloloskan,
            // dan meloloskan di sini akan meninggalkan tombol yang tak
            // menghasilkan apa-apa TANPA pesan — kegagalan senyap yang justru
            // paling mahal di layar ini.
            val pesan = gateJumlahChat(
                jumlah = _state.value.pilihan[index]?.jumlah,
                ambang = _state.value.chatTrainee?.ambang ?: 0,
                adaVideo = false,
            ).alasan ?: "Bukti butir chat harus VIDEO."
            _state.update { it.copy(message = pesan) }
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

    /**
     * Simpan URL yang SUDAH didapat supaya percobaan ulang melanjutkan, bukan
     * mengulang.
     *
     * [jumlah] ikut dibawa karena fungsi ini MENIMPA seluruh `PilihanBukti`
     * baris itu; menghilangkannya akan mengosongkan kotak angka tepat setelah
     * unggahan gagal di tengah — persis saat orangnya akan menekan "Kirim
     * bukti" lagi.
     */
    private fun simpanParsial(index: Int, daftar: List<GambarBukti>, jumlah: Int? = null) =
        _state.update {
            it.copy(pilihan = it.pilihan + (index to PilihanBukti(gambar = daftar, jumlah = jumlah)))
        }

    private suspend fun send(
        index: Int,
        aktivitas: String,
        mode: String,
        evidenceUrl: String? = null,
        employeeNote: String? = null,
        /**
         * Angka chat butir CHAT trainee. `null` untuk semua jalur lain —
         * termasuk `mode = "none"`, yang memang tak pernah punya angka.
         */
        jumlah: Int? = null,
    ) {
        when (
            val result =
                repository.submitItem(index, aktivitas, mode, evidenceUrl, employeeNote, jumlah)
        ) {
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
                        // Penjaga yang sama dengan `refresh()`: dipetakan
                        // terhadap daftar yang SEDANG dirender, bukan lewat
                        // index saja.
                        submitted = (terkirim as? AuthResult.Success)
                            ?.let { terkirimUntukAktivitas(it.data, state.aktivitas) }
                            ?: state.submitted,
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
