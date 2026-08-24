package com.krisoft.tridjayaelektronik.ui.aktivitas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krisoft.tridjayaelektronik.data.AuthResult
import com.krisoft.tridjayaelektronik.data.AktivitasRepository
import com.krisoft.tridjayaelektronik.data.TokenStore
import com.krisoft.tridjayaelektronik.domain.sales.KlasemenStandings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AktivitasReviewUiState(
    val loading: Boolean = false,
    val error: String? = null,
    /** `yyyy-MM-dd` — PIC sering menilai kiriman KEMARIN, jadi tanggal bisa digeser. */
    val tanggal: String = KlasemenStandings.todayIso(),
    /** `pending` (bawaan) / `approved` / `rejected` / `all`. */
    val status: String = "pending",
    val cari: String = "",
    val grup: List<GrupAktivitas> = emptyList(),
    /** `total` dari server — bisa LEBIH BESAR dari baris yang termuat. */
    val total: Int = 0,
    /** Id baris yang sedang dikirim putusannya (tombolnya dimatikan). */
    val memutuskanId: String? = null,
    /** Pesan sukses singkat setelah satu putusan tersimpan. */
    val pesan: String? = null,
    /**
     * Tujuh hari terakhir (tertua dulu) untuk chip pemilih tanggal — dihitung
     * SEKALI saat ViewModel lahir supaya seluruh layar memakai daftar yang
     * sama. Menghitungnya ulang per komposisi membuat chip bergeser sendiri di
     * tengah malam saat layar sedang terbuka.
     */
    val hariTerakhir: List<String> = emptyList(),
    /**
     * Jumlah baris MENUNGGU per tanggal (`yyyy-MM-dd`). Hari tanpa entri =
     * tidak ada yang menunggu ATAU angkanya belum termuat; keduanya tak
     * berlencana, karena lencana "0" sama menyesatkannya dengan lencana palsu.
     */
    val lencanaPending: Map<String, Int> = emptyMap(),
    /**
     * Angka lencana adalah BATAS BAWAH — server memotong rentangnya di `limit`.
     * Dipajang, tidak disembunyikan: PIC yang mengira antriannya habis berhenti
     * bekerja, dan tak ada error yang memberitahunya.
     */
    val lencanaTerpotong: Boolean = false,
)

/**
 * Antrian penilaian PIC raport. Tanpa cache lokal — sama alasan dengan
 * [AktivitasRepository]: status review dihitung server dan antrian basi membuat
 * dua PIC menilai baris yang sama.
 */
@HiltViewModel
class AktivitasReviewViewModel @Inject constructor(
    private val repository: AktivitasRepository,
    private val tokenStore: TokenStore,
) : ViewModel() {

    private val _state = MutableStateFlow(
        AktivitasReviewUiState(
            hariTerakhir = tujuhHariTerakhir(
                hariIni = KlasemenStandings.todayIso(),
                geser = KlasemenStandings::shiftDays,
            ),
        )
    )
    val state: StateFlow<AktivitasReviewUiState> = _state.asStateFlow()

    /**
     * Angka lencana sudah pernah terbaca dari server. BUKAN diturunkan dari
     * `lencanaPending.isEmpty()`: peta kosong juga berarti "tujuh hari ini
     * memang bersih", dan menyamakan keduanya membuat papan yang benar-benar
     * kosong menarik ulang 2.000 baris tiap kali chip disentuh.
     */
    private var lencanaTermuat = false

    /** Bearer token untuk Coil memuat bukti privat (`AuthedImage`). */
    fun bearerToken(): String? = tokenStore.accessToken

    /**
     * Muat antrian tanggal yang sedang dipilih.
     *
     * [segarkanLencana] hanya untuk tarik-segarkan & tombol "Coba lagi". Ganti
     * tanggal / filter / cari SENGAJA tidak menariknya ulang: angkanya sudah
     * benar (putusan menurunkannya secara lokal), sementara satu tarikan berarti
     * s/d 2.000 baris tujuh hari — di jaringan lapangan itu ongkos nyata untuk
     * satu ketukan chip.
     */
    fun muat(segarkanLencana: Boolean = false) {
        val snapshot = _state.value
        _state.update { it.copy(loading = true, error = null, pesan = null) }
        viewModelScope.launch {
            when (
                val r = repository.antrianReview(
                    tanggal = snapshot.tanggal,
                    status = snapshot.status,
                    cari = snapshot.cari,
                )
            ) {
                is AuthResult.Success -> _state.update {
                    it.copy(
                        loading = false,
                        error = null,
                        grup = grupPerKaryawan(r.data.items),
                        total = r.data.total,
                    )
                }
                is AuthResult.Failure -> _state.update {
                    // Daftar lama SENGAJA dikosongkan: menahan antrian tanggal/
                    // filter sebelumnya di layar yang kepalanya sudah berganti
                    // membuat PIC menilai baris yang bukan bagian filter itu.
                    it.copy(loading = false, error = r.message, grup = emptyList(), total = 0)
                }
            }
        }
        muatLencana(paksa = segarkanLencana)
    }

    /**
     * Angka lencana tujuh hari — SATU permintaan rentang, bukan tujuh
     * permintaan per hari (lihat `antrianPendingRentang`).
     *
     * Coroutine terpisah dari [muat] dengan sengaja: papan angka ini pelengkap,
     * jadi kegagalannya TIDAK boleh menjatuhkan antrian yang sedang dinilai —
     * dan sebaliknya, antrian yang gagal tak boleh menghapus angka yang sudah
     * benar. Kegagalannya juga tidak mengisi `error`: dua pesan merah untuk satu
     * layar membuat PIC menyangka antriannya ikut rusak.
     *
     * Lencana LAMA dipertahankan saat gagal, bukan dikosongkan. Angka basi
     * beberapa detik jauh lebih jujur daripada papan yang tiba-tiba nol —
     * "tidak ada yang menunggu" adalah pernyataan, dan itu bukan yang
     * diketahui saat permintaannya gagal.
     */
    private fun muatLencana(paksa: Boolean) {
        if (!paksa && lencanaTermuat) return
        val hari = _state.value.hariTerakhir
        if (hari.isEmpty()) return
        viewModelScope.launch {
            when (val r = repository.antrianPendingRentang(dari = hari.first(), sampai = hari.last())) {
                is AuthResult.Success -> {
                    lencanaTermuat = true
                    _state.update {
                        it.copy(
                            lencanaPending = hitungPendingPerHari(r.data.items),
                            lencanaTerpotong = lencanaTerpotong(r.data.total, r.data.items.size),
                        )
                    }
                }
                // Bendera TIDAK dinaikkan saat gagal: percobaan berikutnya harus
                // tetap mencoba, kalau tidak papan angkanya kosong seumur layar.
                is AuthResult.Failure -> Unit
            }
        }
    }

    fun gantiTanggal(tanggal: String) {
        if (tanggal == _state.value.tanggal) return
        _state.update { it.copy(tanggal = tanggal) }
        muat()
    }

    /** Geser [hari] hari dari tanggal yang sedang tampil (−1 = kemarin). */
    fun geserHari(hari: Int) = gantiTanggal(KlasemenStandings.shiftDays(_state.value.tanggal, hari))

    fun gantiStatus(status: String) {
        if (status == _state.value.status) return
        _state.update { it.copy(status = status) }
        muat()
    }

    fun ketikCari(teks: String) = _state.update { it.copy(cari = teks) }

    /** Dipanggil saat user menekan "cari" di papan ketik — bukan tiap ketikan. */
    fun terapkanCari() = muat()

    /**
     * Putusan atas satu baris. Nilainya TIDAK dikirim: sejak 2026-08-15 server
     * menentukannya sendiri dari `status` (setuju 100 / tolak 0) dan mengabaikan
     * `score` kiriman klien
     * (100 saat setuju); tolak WAJIB ber-[komentar] — dijaga [bolehSimpanReview]
     * di sini JUGA, bukan cuma di tombol, supaya jalur mana pun tak bisa
     * mengirim penolakan tanpa alasan.
     */
    fun putuskan(id: String, status: String, komentar: String? = null) {
        val gate = bolehSimpanReview(status, komentar)
        if (!gate.ok) {
            _state.update { it.copy(error = gate.alasan) }
            return
        }
        _state.update { it.copy(memutuskanId = id, error = null, pesan = null) }
        viewModelScope.launch {
            when (val r = repository.review(id, status, skorReview(status), komentar)) {
                is AuthResult.Success -> {
                    // Baris yang sudah diputus dibuang dari daftar HANYA saat
                    // filternya "pending" — di filter lain ia memang masih milik
                    // daftar itu, cuma statusnya berubah.
                    val pending = _state.value.status == "pending"
                    // Tanggal baris yang BARU diputus, diambil dari barisnya
                    // sendiri dan bukan dari `state.tanggal`: lencana harus
                    // turun untuk hari yang barisnya memang berpindah status.
                    // Diambil SEBELUM `update` membuang barisnya dari daftar.
                    val tanggalDiputus = _state.value.grup
                        .asSequence()
                        .flatMap { it.baris.asSequence() }
                        .firstOrNull { it.id == id && it.reviewStatus == "pending" }
                        ?.tanggal
                        ?.takeIf { it.isNotBlank() }
                    _state.update { lama ->
                        val grup = if (pending) {
                            lama.grup.map { g -> g.copy(baris = g.baris.filterNot { it.id == id }) }
                                .filter { it.baris.isNotEmpty() }
                        } else {
                            lama.grup.map { g ->
                                g.copy(
                                    baris = g.baris.map { baris ->
                                        if (baris.id == id) {
                                            baris.copy(
                                                reviewStatus = r.data.status.ifBlank { status },
                                                score = r.data.score ?: skorReview(status),
                                                reviewerComment = komentar,
                                            )
                                        } else baris
                                    }
                                )
                            }
                        }
                        lama.copy(
                            grup = grup,
                            total = if (pending) (lama.total - 1).coerceAtLeast(0) else lama.total,
                            // Lencana diturunkan LOKAL, tidak dimuat ulang:
                            // memuat ulang rentang tujuh hari (s/d 2.000 baris)
                            // tiap satu putusan membuat sesi penilaian normal
                            // menarik puluhan megabyte. Entri yang habis DIBUANG
                            // (bukan disisakan `0`) — lihat `hitungPendingPerHari`.
                            lencanaPending = tanggalDiputus?.let { hari ->
                                val sisa = (lama.lencanaPending[hari] ?: 0) - 1
                                if (sisa > 0) lama.lencanaPending + (hari to sisa)
                                else lama.lencanaPending - hari
                            } ?: lama.lencanaPending,
                            memutuskanId = null,
                            pesan = if (status == "rejected") "Laporan ditolak." else "Laporan disetujui.",
                        )
                    }
                }
                is AuthResult.Failure -> _state.update {
                    it.copy(memutuskanId = null, error = r.message)
                }
            }
        }
    }

    fun hapusPesan() = _state.update { it.copy(pesan = null, error = null) }
}
