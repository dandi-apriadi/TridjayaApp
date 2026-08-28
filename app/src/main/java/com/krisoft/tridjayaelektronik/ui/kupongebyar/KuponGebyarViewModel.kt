package com.krisoft.tridjayaelektronik.ui.kupongebyar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krisoft.tridjayaelektronik.data.AuthRepository
import com.krisoft.tridjayaelektronik.data.AuthResult
import com.krisoft.tridjayaelektronik.data.KuponGebyarRepository
import com.krisoft.tridjayaelektronik.data.model.KuponGebyarBarisDto
import com.krisoft.tridjayaelektronik.data.model.KuponGebyarDaftarDto
import com.krisoft.tridjayaelektronik.ui.aktivitas.pesanGagalDekode
import com.krisoft.tridjayaelektronik.util.PhotoWatermark
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

data class KuponGebyarUiState(
    val loading: Boolean = false,
    /** Sedang mengambil halaman BERIKUTNYA — beda dari [loading], yang mengganti
     *  seluruh isi layar dengan skeleton dan akan menggeser posisi gulir. */
    val memuatLagi: Boolean = false,
    val items: List<KuponGebyarBarisDto> = emptyList(),
    /** Baris yang cocok — dasar tombol "muat lagi", bukan angka capaian. */
    val total: Int = 0,
    /** Kupon cabang ini — penyebut persentase. */
    val jumlahKupon: Int = 0,
    /** Capaian dari server. `null` = tak ada konsumen berhak, bukan 0%. */
    val persen: Double? = null,
    val halaman: Int = 1,
    val cari: String = "",
    val namaCabang: String = "",
    val sudahDikirim: Int = 0,
    /** Konsumen berhak yang server SEMBUNYIKAN karena nomornya tak ada. Bukan
     *  nol berarti ada pekerjaan yang tak bisa dikerjakan dari layar ini. */
    val tanpaNomor: Int = 0,
    val disinkronPada: String? = null,
    val error: String? = null,
    /** Galat aksi (unggah/simpan) — dipisah dari [error] supaya kegagalan satu
     *  tombol tak mengosongkan daftar yang sudah termuat. */
    val actionError: String? = null,
    val mengunggah: Boolean = false,
    /**
     * `kodeRekanan -> buktiUrl` untuk foto yang SUDAH terunggah tapi
     * pencatatannya gagal. Tanpa ini satu-satunya jalan maju adalah memotret
     * ulang — yang menambah berkas tanpa induk di server untuk pekerjaan yang
     * cuma kurang satu panggilan.
     */
    val buktiTertunda: Map<String, String> = emptyMap(),
    /** Kalimat sukses sekali-tampil (snackbar). */
    val pesanSukses: String? = null,
)

/**
 * Layar "Konsumen Gebyar" — daftar konsumen cabang yang berhak kupon doorprize,
 * plus pencatatan bukti pengiriman undangan.
 *
 * **Tak ada parameter cabang di mana pun**, dan itu bukan kelalaian: server
 * membacanya dari `auth_users.cabang_id`. Menambahkan pilihan cabang di layar
 * ini berarti membuka IDOR ke daftar konsumen cabang lain.
 *
 * **Daftar dimuat ULANG setelah tiap bukti tersimpan.** Terlihat boros, tapi
 * baris yang sudah dikerjakan HILANG dari antrean rekan secabang — menukar satu
 * baris di tempat akan membiarkan daftar yang lain basi, dan dua karyawan
 * mengirim undangan ke konsumen yang sama.
 */
@HiltViewModel
class KuponGebyarViewModel @Inject constructor(
    private val repository: KuponGebyarRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(KuponGebyarUiState())
    val state: StateFlow<KuponGebyarUiState> = _state.asStateFlow()

    /** Pencarian ditunda: tiap ketukan huruf = satu permintaan ke server kalau
     *  tidak. Job lama dibatalkan supaya jawaban yang datang belakangan untuk
     *  kata kunci LAMA tak menimpa hasil kata kunci baru. */
    private var jobCari: Job? = null

    fun muat() {
        jobCari?.cancel()
        ambil(halaman = 1, tambah = false)
    }

    fun onCariChange(nilai: String) {
        _state.update { it.copy(cari = nilai) }
        jobCari?.cancel()
        jobCari = viewModelScope.launch {
            delay(TUNDA_CARI_MS)
            ambil(halaman = 1, tambah = false)
        }
    }

    fun muatLagi() {
        val s = _state.value
        if (s.loading || s.memuatLagi || !adaHalamanLagi(s.items.size, s.total)) return
        ambil(halaman = s.halaman + 1, tambah = true)
    }

    fun bersihkanActionError() = _state.update { it.copy(actionError = null) }

    fun bersihkanPesanSukses() = _state.update { it.copy(pesanSukses = null) }

    private fun ambil(halaman: Int, tambah: Boolean) {
        _state.update {
            if (tambah) it.copy(memuatLagi = true, error = null)
            else it.copy(loading = true, error = null)
        }
        viewModelScope.launch {
            when (val r = repository.daftar(halaman, UKURAN_HALAMAN, _state.value.cari)) {
                is AuthResult.Success -> _state.update { s -> gabung(s, r.data, tambah) }
                is AuthResult.Failure -> _state.update {
                    it.copy(loading = false, memuatLagi = false, error = r.message)
                }
            }
        }
    }

    private fun gabung(
        s: KuponGebyarUiState,
        data: KuponGebyarDaftarDto,
        tambah: Boolean,
    ): KuponGebyarUiState = s.copy(
        loading = false,
        memuatLagi = false,
        error = null,
        // Dedup per `kodeRekanan` saat menambah halaman: baris bisa BERGESER
        // antar-halaman kalau rekan secabang menyimpan bukti di sela dua
        // permintaan (baris itu keluar dari daftar, semua yang di bawahnya naik
        // satu). Tanpa dedup, satu konsumen tampil dua kali dan karyawan
        // mengirimi undangan dua kali.
        items = if (tambah) {
            val ada = s.items.mapTo(mutableSetOf()) { it.kodeRekanan }
            s.items + data.items.filterNot { it.kodeRekanan in ada }
        } else {
            data.items
        },
        total = data.total,
        jumlahKupon = data.jumlahKupon,
        persen = data.persen,
        halaman = data.page,
        namaCabang = data.namaCabang,
        sudahDikirim = data.sudahDikirim,
        tanpaNomor = data.tanpaNomor,
        disinkronPada = data.disinkronPada,
    )

    /**
     * Kamera → watermark → unggah → catat.
     *
     * **Dua panggilan, dan urutannya mengikat.** Unggah dulu, baru
     * `POST /bukti`. Kalau langkah kedua gagal, berkasnya sudah telanjur ada di
     * `uploads/kupon-gebyar` tanpa induk — ongkos yang diterima, karena
     * kebalikannya (mendaftarkan URL sebelum berkasnya ada) menghasilkan baris
     * bukti yang menunjuk gambar yang tak pernah bisa dimuat siapa pun.
     *
     * Watermark memakai util yang sama dengan absensi/PDI/komplain: jam dan
     * identitas dicap KE PIKSEL. Bukti undangan yang tak bisa dipastikan kapan
     * diambil tak lebih berguna daripada tak ada foto — dan yang dipertaruhkan
     * di sini hadiah doorprize.
     */
    fun unggahBukti(
        baris: KuponGebyarBarisDto,
        file: File,
        catatan: String?,
        dariGaleri: Boolean = false,
    ) {
        if (_state.value.mengunggah) return
        _state.update { it.copy(mengunggah = true, actionError = null) }
        viewModelScope.launch {
            val user = authRepository.cachedUser
            // `withContext(Dispatchers.Default)` WAJIB — `viewModelScope`
            // berjalan di `Dispatchers.Main.immediate`, jadi tanpa ini seluruh
            // decode + penskalaan + rotasi EXIF + kompresi WebP mengunci UI
            // thread. Enam pemanggil `PhotoWatermark` lain sudah begitu
            // (`AttendanceViewModel`, `AktivitasViewModel`, `DeliveryFlowViewModel`,
            // `OpnameDetailViewModel` ×2, `HomeServiceLaporViewModel`); layar ini
            // ketinggalan. Yang membuatnya lebih menggigit di sini: foto galeri
            // bisa jauh lebih besar dari jepretan kamera, dan spinner
            // `mengunggah` bahkan tak sempat dirender karena coroutine-nya
            // mulai undispatched di thread yang sama.
            val siap = withContext(Dispatchers.Default) {
                PhotoWatermark.prepareWatermarkedJpeg(
                    file = file,
                    lat = null,
                    lng = null,
                    title = "TRIDJAYA · UNDANGAN GEBYAR",
                    subtitle = listOfNotNull(
                        baris.nama.takeIf { it.isNotBlank() },
                        user?.name?.takeIf { it.isNotBlank() },
                    ).joinToString(" · "),
                )
            }
            if (siap == null) {
                // Sebabnya BEDA menurut sumber foto, dan "ulangi" salah untuk
                // salah satunya. `BitmapFactory` baru bisa HEIF di API 28
                // sedangkan minSdk 24, jadi HEIC yang MASUK dari luar (iPhone,
                // Bluetooth, kartu SD) ke HP Android 7/8 gagal dekode
                // SELAMANYA untuk berkas itu — menyuruh mengulang berarti
                // menyuruh mengulang hal yang dijamin gagal lagi. Jalur kamera
                // tak punya masalah itu, foto barunya memang bisa menolong.
                // Kalimatnya dipakai ULANG dari `pesanGagalDekode` (jalur
                // raport sudah menyelesaikan ini lebih dulu) supaya satu
                // kegagalan tak dijelaskan dua cara berbeda di dua layar.
                _state.update {
                    it.copy(mengunggah = false, actionError = pesanGagalDekode(dariGaleri))
                }
                return@launch
            }
            val url = when (
                val up = repository.unggahFoto(
                    siap.first,
                    "gebyar_${baris.kodeRekanan}_${System.currentTimeMillis()}.webp",
                )
            ) {
                is AuthResult.Success -> up.data
                is AuthResult.Failure -> {
                    _state.update { it.copy(mengunggah = false, actionError = up.message) }
                    return@launch
                }
            }
            catat(baris.kodeRekanan, url, catatan)
        }
    }

    /** Coba lagi MENCATAT bukti yang unggahannya sudah berhasil. */
    fun simpanUlang(kodeRekanan: String, catatan: String?) {
        val url = _state.value.buktiTertunda[kodeRekanan] ?: return
        if (_state.value.mengunggah) return
        _state.update { it.copy(mengunggah = true, actionError = null) }
        viewModelScope.launch { catat(kodeRekanan, url, catatan) }
    }

    private suspend fun catat(kodeRekanan: String, url: String, catatan: String?) {
        when (val r = repository.simpanBukti(kodeRekanan, url, catatan)) {
            is AuthResult.Success -> {
                _state.update {
                    it.copy(
                        mengunggah = false,
                        actionError = null,
                        buktiTertunda = it.buktiTertunda - kodeRekanan,
                        pesanSukses = "Bukti undangan ${r.data.nama} tersimpan.",
                    )
                }
                // Muat ulang, jangan tukar satu baris — lihat KDoc kelas.
                ambil(halaman = 1, tambah = false)
            }
            is AuthResult.Failure -> {
                // 409 = rekan secabang sudah mengerjakannya. Pesannya sudah
                // menyebut nama pemegangnya (server mengisinya di `message`),
                // jadi jangan ditimpa kalimat generik — DAN jangan simpan
                // buktinya sebagai "tertunda": tak ada yang bisa diselamatkan
                // dengan mencoba ulang, pekerjaannya memang sudah selesai.
                val konflik = r.code == "http_409" || r.code.equals("conflict", ignoreCase = true)
                _state.update {
                    it.copy(
                        mengunggah = false,
                        actionError = if (konflik) r.message else pesanBuktiTertunda(r.message),
                        buktiTertunda = if (konflik) it.buktiTertunda - kodeRekanan
                        else it.buktiTertunda + (kodeRekanan to url),
                    )
                }
                if (konflik) ambil(halaman = 1, tambah = false)
            }
        }
    }

    private companion object {
        const val TUNDA_CARI_MS = 400L
    }
}
