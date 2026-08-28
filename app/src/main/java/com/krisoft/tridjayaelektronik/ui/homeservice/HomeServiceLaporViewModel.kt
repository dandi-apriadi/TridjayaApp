package com.krisoft.tridjayaelektronik.ui.homeservice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krisoft.tridjayaelektronik.data.AuthResult
import com.krisoft.tridjayaelektronik.data.HomeServiceRepository
import com.krisoft.tridjayaelektronik.data.model.HsCreateTicketBody
import com.krisoft.tridjayaelektronik.data.model.HsCreateTicketItem
import com.krisoft.tridjayaelektronik.data.model.HsKontakDto
import com.krisoft.tridjayaelektronik.data.model.HsRingkasTransaksiDto
import com.krisoft.tridjayaelektronik.data.model.HsTicketDto
import com.krisoft.tridjayaelektronik.data.model.HsTransaksiItemDto
import com.krisoft.tridjayaelektronik.util.PhotoWatermark
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

data class HsLaporUiState(
    val cariNama: String = "",
    val cariHp: String = "",
    val mencari: Boolean = false,
    /** Hasil pencarian transaksi konsumen (maks 25 dari server). */
    val hasilCari: List<HsRingkasTransaksiDto> = emptyList(),
    /** `hp` atau `nama` — kunci mana yang benar-benar dipakai server. */
    val kunciCari: String = "",
    /**
     * Sebuah pencarian sudah SELESAI DENGAN SUKSES. Sengaja TIDAK diset saat
     * pencarian gagal: "transaksi tak ditemukan" dan "pencarian gagal" menuntut
     * layar berbeda, dan tawaran "lanjut tanpa data pembelian" hanya sah untuk
     * yang pertama — menawarkannya sesudah jaringan putus membuat pelapor
     * menerbitkan tiket tak terverifikasi padahal transaksinya mungkin ada.
     */
    val sudahMencari: Boolean = false,

    val noTransaksi: String? = null,
    val memuatRincian: Boolean = false,
    val barang: List<HsTransaksiItemDto> = emptyList(),
    val kontak: HsKontakDto = HsKontakDto(),
    /**
     * Baris transaksi yang dicentang — SATU tiket boleh memuat beberapa barang
     * (server 2026-08-13). Yang disimpan nomor `baris`, bukan objeknya, karena
     * baris itulah identitas yang dikirim & divalidasi server.
     */
    val barisTerpilih: Set<Int> = emptySet(),
    /**
     * Pelapor memilih maju tanpa data pembelian. Dibedakan dari "hasil
     * pencarian kosong" karena keduanya menuntut layar berbeda: yang satu
     * menawarkan pilihan, yang satu sudah memutuskan. TIDAK boleh diwakili
     * `noTransaksi = ""` — layar memakai `noTransaksi == null` untuk memutuskan
     * menampilkan pencarian, jadi sentinel kosong akan merender seksi transaksi
     * hampa.
     */
    val tanpaVerifikasi: Boolean = false,

    val fotoKwitansiUrl: String? = null,
    /** Teks yang tercetak di watermark kwitansi terunggah. Dipajang di layar
     *  supaya foto milik transaksi/konsumen LAIN kelihatan — tanpa ini satu-
     *  satunya isyarat cuma label tombol kamera yang berubah. */
    val fotoPenanda: String = "",
    val mengunggah: Boolean = false,

    val deskripsi: String = "",
    val prioritas: String = "normal",
    val customerNama: String = "",
    val customerHp: String = "",
    val customerAlamat: String = "",
    /** Pelapor sudah menyentuh salah satu kolom kontak sejak transaksi ini
     *  dipilih. Sentuhan manusia menang atas data server — lihat
     *  [kontakSetelahLookup]. */
    val kontakDisunting: KontakDisunting = KontakDisunting.NIHIL,

    val mengirim: Boolean = false,
    val error: String? = null,
    /** Terisi setelah tiket jadi — layar berpindah ke tampilan "berhasil". */
    val tiketJadi: HsTicketDto? = null,
)

/**
 * Buat tiket komplain. Alurnya mengikuti web: **cari dulu** (nama/HP), pilih
 * transaksi, baru `lookup` rincian barangnya — nomor transaksi GS jarang
 * dihafal orang lapangan, jadi pencarian konsumen adalah pintu utamanya.
 */
@HiltViewModel
class HomeServiceLaporViewModel @Inject constructor(
    private val repository: HomeServiceRepository
) : ViewModel() {

    private val _state = MutableStateFlow(HsLaporUiState())
    val state: StateFlow<HsLaporUiState> = _state.asStateFlow()

    /**
     * Nomor urut permintaan `lookup`. Respons yang BASI harus dibuang, bukan
     * ditulis: menekan "Ganti" lalu memilih transaksi lain tidak membatalkan
     * lookup pertama, jadi respons lambat milik transaksi LAMA bisa mendarat
     * belakangan dan menukar daftar barang di bawah judul transaksi yang baru —
     * tiket lalu menunjuk unit yang tak pernah dilihat pelapor, tanpa galat.
     */
    private var seqLookup = 0

    fun ketikNama(v: String) = _state.update { it.copy(cariNama = v) }
    fun ketikHp(v: String) = _state.update { it.copy(cariHp = v) }
    fun ketikDeskripsi(v: String) = _state.update { it.copy(deskripsi = v) }
    fun pilihPrioritas(v: String) = _state.update { it.copy(prioritas = v) }
    fun ketikCustomerNama(v: String) =
        _state.update { it.copy(customerNama = v, kontakDisunting = it.kontakDisunting.copy(nama = true)) }
    fun ketikCustomerHp(v: String) =
        _state.update { it.copy(customerHp = v, kontakDisunting = it.kontakDisunting.copy(hp = true)) }
    fun ketikCustomerAlamat(v: String) =
        _state.update { it.copy(customerAlamat = v, kontakDisunting = it.kontakDisunting.copy(alamat = true)) }
    fun hapusPesan() = _state.update { it.copy(error = null) }

    /**
     * Maju tanpa kecocokan data penjualan (cerminan `lanjutTanpaVerifikasi`
     * web). Nama & HP yang sudah diketik di kotak pencarian DIBAWA ke form
     * konsumen: server mewajibkan keduanya di jalur ini, jadi menyalinnya
     * mencegah pelapor mengetik hal yang sama dua kali.
     */
    fun lanjutTanpaVerifikasi() {
        seqLookup++ // buang respons lookup yang masih terbang
        _state.update {
            it.copy(
                tanpaVerifikasi = true,
                noTransaksi = null,
                memuatRincian = false,
                barang = emptyList(),
                barisTerpilih = emptySet(),
                kontak = HsKontakDto(),
                error = null,
            ).let { baru ->
                // ATURAN YANG SAMA dengan sesudah lookup, cuma tanpa sumber
                // server (jalur ini memang tak punya transaksi): ketikan pelapor
                // menang, selebihnya diisi kotak pencarian.
                //
                // ALAMAT ikut lewat sini justru untuk kasus yang mudah terlewat:
                // pelapor sempat memilih transaksi (alamat terisi OTOMATIS dari
                // SPK), menekan "Ganti", lalu mencari konsumen lain dan berakhir
                // di jalur ini. Tanpa aturan ini alamat konsumen SEBELUMNYA
                // bertahan, memuaskan gerbang tanpa disadari, dan teknisi
                // didatangkan ke rumah yang salah.
                val kontak = kontakSetelahLookup(
                    disunting = it.kontakDisunting,
                    sekarang = KontakIsian(it.customerNama, it.customerHp, it.customerAlamat),
                    kontakNama = null,
                    kontakHp = null,
                    kontakAlamat = null,
                    cariNama = it.cariNama,
                    cariHp = it.cariHp,
                )
                baru.copy(
                    customerNama = kontak.nama,
                    customerHp = kontak.hp,
                    customerAlamat = kontak.alamat,
                )
            }
        }
    }

    fun cari() {
        val s = _state.value
        if (s.mencari) return
        if (s.cariNama.isBlank() && s.cariHp.isBlank()) {
            _state.update { it.copy(error = "Isi nama atau nomor HP konsumen.") }
            return
        }
        // SESUDAH penjaga di atas: pencarian yang tak jadi berjalan tak boleh
        // ikut membatalkan lookup yang sedang terbang (spinner-nya tak akan
        // pernah selesai kalau responsnya dibuang).
        seqLookup++
        // Pencarian baru MEMBATALKAN jalur yang sedang dipilih — termasuk
        // "tanpa verifikasi" dan centangan barang milik transaksi lama. Tanpa
        // ini, mencari ulang lalu menekan Kirim mengirimkan baris transaksi
        // yang sudah tidak ada hubungannya dengan layar.
        _state.update {
            it.copy(
                mencari = true,
                error = null,
                sudahMencari = false,
                hasilCari = emptyList(),
                tanpaVerifikasi = false,
                noTransaksi = null,
                barang = emptyList(),
                barisTerpilih = emptySet(),
                kontak = HsKontakDto(),
                // INVARIAN: kolom kontak hanya boleh memuat identitas milik
                // jalur yang SEDANG dipilih. Mencari konsumen lain = identitas
                // lama mati, termasuk yang sempat diketik pelapor — kalau tidak,
                // ia diam-diam memuaskan gerbang dan server memenangkannya atas
                // data SPK, jadi teknisi didatangkan ke rumah konsumen lama.
                // (Foto kwitansi SENGAJA dipertahankan: itu bukti yang mahal
                // diulang, dan `fotoPenanda` membuatnya terlihat di layar.)
                customerNama = "",
                customerHp = "",
                customerAlamat = "",
                kontakDisunting = KontakDisunting.NIHIL,
            )
        }
        viewModelScope.launch {
            when (val r = repository.cari(s.cariNama, s.cariHp)) {
                is AuthResult.Success -> {
                    _state.update {
                        it.copy(
                            mencari = false,
                            hasilCari = r.data.transaksi,
                            kunciCari = r.data.kunci,
                            sudahMencari = true,
                        )
                    }
                    // Satu hasil = tak ada yang perlu dipilih; langsung buka
                    // rinciannya (perilaku sama dengan web).
                    r.data.transaksi.singleOrNull()?.let { pilihTransaksi(it.noTransaksi) }
                }
                is AuthResult.Failure -> _state.update {
                    // `sudahMencari` TETAP false — lihat KDoc-nya di UiState.
                    it.copy(mencari = false, error = r.message)
                }
            }
        }
    }

    fun pilihTransaksi(noTransaksi: String) {
        val seq = ++seqLookup
        _state.update {
            it.copy(
                memuatRincian = true,
                noTransaksi = noTransaksi,
                // Centangan milik transaksi SEBELUMNYA tak boleh menyeberang —
                // barisnya divalidasi server terhadap transaksi yang baru.
                barisTerpilih = emptySet(),
                barang = emptyList(),
                // Transaksi baru = kontaknya diisi ulang dari server. Kolomnya
                // DIKOSONGKAN dulu: seluruh form tetap terender selama rincian
                // dimuat, jadi identitas transaksi sebelumnya akan terbaca
                // sebagai identitas transaksi ini — dan bila pelapor menyunting
                // satu kolom di detik itu, dua kolom sisanya ikut terkunci ke
                // konsumen yang salah.
                customerNama = "",
                customerHp = "",
                customerAlamat = "",
                kontakDisunting = KontakDisunting.NIHIL,
                error = null,
            )
        }
        viewModelScope.launch {
            val r = repository.lookup(noTransaksi)
            if (seq != seqLookup) return@launch // respons basi — jalurnya sudah berganti
            when (r) {
                is AuthResult.Success -> _state.update {
                    it.copy(
                        memuatRincian = false,
                        barang = r.data.items,
                        kontak = r.data.kontak,
                        // Satu barang = centangkan (sama dengan web); transaksi
                        // multi-barang tetap menuntut pilihan — server yang
                        // tidak menerima pilihan diam-diam memakai barang
                        // PERTAMA, bukan menolak.
                        barisTerpilih = r.data.items.singleOrNull()?.let { satu -> setOf(satu.baris) }
                            ?: emptySet(),
                    ).let { baru ->
                        // Aturan MURNI (diuji di HomeServicePlanTest) — KDoc-nya
                        // menerangkan dua kesalahan berlawanan yang harus
                        // dihindari sekaligus: kontak lama menyeberang, vs
                        // ketikan pelapor dihapus respons yang telat mendarat.
                        val kontak = kontakSetelahLookup(
                            disunting = it.kontakDisunting,
                            sekarang = KontakIsian(it.customerNama, it.customerHp, it.customerAlamat),
                            kontakNama = r.data.kontak.customerNama,
                            kontakHp = r.data.kontak.customerHp,
                            kontakAlamat = r.data.kontak.customerAlamat,
                            cariNama = it.cariNama,
                            cariHp = it.cariHp,
                        )
                        baru.copy(
                            customerNama = kontak.nama,
                            customerHp = kontak.hp,
                            customerAlamat = kontak.alamat,
                        )
                    }
                }
                // Rincian gagal dimuat = kembali ke daftar hasil cari. Membiarkan
                // `noTransaksi` terisi menyisakan seksi transaksi TANPA satu pun
                // barang untuk dicentang, sementara gerbang menuntut "barang yang
                // dikomplainkan" — jalan buntu yang pesannya pun ikut terhapus
                // begitu foto diunggah.
                is AuthResult.Failure -> _state.update {
                    it.copy(memuatRincian = false, noTransaksi = null, error = r.message)
                }
            }
        }
    }

    /** Centang/lepas satu barang. Idiom Set yang sama dengan filter kategori
     *  Input SN (`SerialInputViewModel.toggleKategori`). */
    fun toggleBarang(baris: Int) = _state.update {
        val sekarang = it.barisTerpilih
        it.copy(barisTerpilih = if (baris in sekarang) sekarang - baris else sekarang + baris)
    }

    fun gantiTransaksi() {
        seqLookup++ // buang respons lookup yang masih terbang
        _state.update {
            it.copy(
                noTransaksi = null,
                memuatRincian = false,
                barang = emptyList(),
                barisTerpilih = emptySet(),
                kontak = HsKontakDto(),
                // Foto kwitansi & isian kontak SENGAJA DIPERTAHANKAN (sama
                // seperti web). "Ganti" paling sering berarti "salah pilih
                // transaksi milik konsumen yang SAMA", dan membuang foto yang
                // barusan dijepret di depan konsumen memaksa memotret ulang
                // tanpa satu pun peringatan. Yang menutup kebocoran identitas
                // bukan penghapusan bukti melainkan `kontakSetelahLookup` +
                // `fotoPenanda` yang membuat kwitansi milik transaksi lain
                // terlihat di layar.
                //
                // IDENTITAS tetap dimatikan (beda dengan bukti): nama/HP/alamat
                // milik konsumen yang barusan ditinggalkan tak boleh menyeberang
                // — lihat invarian di `cari()`.
                customerNama = "",
                customerHp = "",
                customerAlamat = "",
                kontakDisunting = KontakDisunting.NIHIL,
                // Jalur tanpa-verifikasi ikut dibatalkan: "Ganti" berarti kembali
                // memilih, dan meninggalkannya menyala membuat layar menampilkan
                // pencarian sambil diam-diam masih berjanji mengirim tiket tanpa
                // transaksi.
                tanpaVerifikasi = false,
            )
        }
    }

    /** Foto kwitansi — OPSIONAL sejak 2026-08-22, tetap di-watermark seperti
     *  bukti foto lain di app bila pelapor memang memotretnya. */
    fun unggahKwitansi(file: File) {
        // URL lama DIBUANG saat unggahan baru mulai (persis web). Tanpa ini,
        // memotret ulang kwitansi yang buram menyisakan URL foto LAMA di state
        // — dan karena tombol Kirim tak menunggu unggahan, tiket bisa terbit
        // membawa foto yang justru sedang diganti.
        _state.update { it.copy(mengunggah = true, fotoKwitansiUrl = null, error = null) }
        viewModelScope.launch {
            // Subtitle TIDAK boleh kosong: pada jalur tanpa verifikasi foto ini
            // satu-satunya bukti yang dipunya CS, dan `noTransaksi` memang null
            // di situ — watermark tanpa penanda apa pun menyisakan baris hampa.
            val s = _state.value
            // Di jalur tanpa transaksi, HP-lah pembeda yang selalu ada (pencarian
            // memang boleh diisi HP saja). Tanpa itu dua kwitansi milik dua
            // konsumen berbeda sama-sama berlabel "Tanpa nomor transaksi", dan
            // label yang identik tak menjaga apa pun.
            val penanda = s.noTransaksi?.takeIf { it.isNotBlank() }
                ?: listOf(
                    "Tanpa nomor transaksi",
                    s.customerNama.trim().ifBlank { s.cariNama.trim() },
                    s.customerHp.trim().ifBlank { s.cariHp.trim() },
                )
                    .filter { it.isNotBlank() }
                    .distinct()
                    .joinToString(" · ")
            // Decode bitmap + rotasi EXIF + gambar watermark + loop compress
            // JPEG BUKAN pekerjaan UI thread: `viewModelScope` berjalan di
            // Dispatchers.Main.immediate, jadi tanpa `withContext` foto besar
            // membekukan layar. `runCatching` menangkap `OutOfMemoryError`
            // (turunan Error, BUKAN Exception — repository di bawah hanya
            // menangkap Exception) supaya foto raksasa berakhir sebagai pesan,
            // bukan proses yang mati.
            val siap = withContext(Dispatchers.Default) {
                runCatching {
                    PhotoWatermark.prepareWatermarkedJpeg(
                        file = file,
                        lat = null,
                        lng = null,
                        title = "Kwitansi komplain",
                        subtitle = penanda,
                    )
                }.getOrNull()
            }
            if (siap == null) {
                _state.update { it.copy(mengunggah = false, error = "Foto tidak terbaca, ulangi.") }
                return@launch
            }
            when (val r = repository.uploadPhoto(siap.first, "kwitansi.webp")) {
                is AuthResult.Success -> _state.update {
                    it.copy(mengunggah = false, fotoKwitansiUrl = r.data, fotoPenanda = penanda)
                }
                is AuthResult.Failure -> _state.update { it.copy(mengunggah = false, error = r.message) }
            }
        }
    }

    fun kirim() {
        val s = _state.value
        // Dua ketukan dalam satu frame lolos dari `enabled` tombol (rekomposisi
        // belum sempat mematikannya) — dan hasilnya DUA tiket untuk satu keluhan.
        if (s.mengirim || s.mengunggah) return
        val gate = bolehBuatTiket(
            tanpaVerifikasi = s.tanpaVerifikasi,
            noTransaksi = s.noTransaksi,
            barisTerpilih = s.barisTerpilih,
            fotoKwitansiUrl = s.fotoKwitansiUrl,
            deskripsi = s.deskripsi,
            customerNama = s.customerNama,
            customerHp = s.customerHp,
            customerAlamat = s.customerAlamat,
        )
        if (!gate.ok) {
            _state.update { it.copy(error = gate.alasan) }
            return
        }
        // Barang dikirim URUT MENGIKUTI TRANSAKSI, bukan urutan centang: server
        // memang mengurutkannya ulang begitu (`ORDER BY baris`), jadi mengirim
        // urutan klik cuma menciptakan harapan palsu bahwa centangan pertama
        // menentukan barang utama tiket.
        val terpilih = s.barang.filter { it.baris in s.barisTerpilih }
        val utama = terpilih.firstOrNull()
        _state.update { it.copy(mengirim = true, error = null) }
        viewModelScope.launch {
            val r = repository.create(
                HsCreateTicketBody(
                    // Kosong = minta server menerbitkan tiket belum-terverifikasi.
                    noTransaksi = if (s.tanpaVerifikasi) "" else s.noTransaksi.orEmpty(),
                    fotoKwitansiUrl = s.fotoKwitansiUrl.orEmpty(),
                    deskripsi = s.deskripsi.trim(),
                    items = terpilih.map { HsCreateTicketItem(barisTransaksi = it.baris) },
                    // Redundan dengan `items` di server baru, dan itu memang
                    // disengaja — lihat KDoc `HsCreateTicketBody.items`.
                    barisTransaksi = utama?.baris,
                    kodeBarang = utama?.kodeBarang?.takeIf { it.isNotBlank() },
                    prioritas = s.prioritas,
                    sumber = "android",
                    customerNama = s.customerNama.trim().takeIf { it.isNotBlank() },
                    customerHp = s.customerHp.trim().takeIf { it.isNotBlank() },
                    customerAlamat = s.customerAlamat.trim().takeIf { it.isNotBlank() },
                )
            )
            when (r) {
                is AuthResult.Success -> _state.update { it.copy(mengirim = false, tiketJadi = r.data) }
                is AuthResult.Failure -> _state.update { it.copy(mengirim = false, error = r.message) }
            }
        }
    }

    /** Kembali ke form kosong untuk melaporkan komplain berikutnya. */
    fun laporLagi() = _state.value.let { _state.value = HsLaporUiState() }
}
