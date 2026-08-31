package com.krisoft.tridjayaelektronik.ui.goda

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krisoft.tridjayaelektronik.data.AuthResult
import com.krisoft.tridjayaelektronik.data.GodaRepository
import com.krisoft.tridjayaelektronik.data.TokenStore
import com.krisoft.tridjayaelektronik.data.local.DealerAlias
import com.krisoft.tridjayaelektronik.data.model.GodaBarisDto
import com.krisoft.tridjayaelektronik.data.model.GodaSerialDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GodaSnUiState(
    /** Kosong = belum ada cabang terpilih; daftar TIDAK dimuat sampai ada. */
    val kodeDealer: String = "",
    val loading: Boolean = false,
    val menyimpan: Boolean = false,
    val baris: List<GodaBarisDto> = emptyList(),
    val totalStok: Long = 0,
    val totalSn: Long = 0,
    val syncedAt: String? = null,
    val cari: String = "",
    val hanyaBelumLengkap: Boolean = false,
    /** Kode barang yang panel input SN-nya sedang terbuka. */
    val kodeBarangTerpilih: String? = null,
    val entri: String = "",
    val entriError: String? = null,
    /** Peringatan yang TIDAK memblokir simpan (SN dipakai barang lain). */
    val entriPeringatan: String? = null,
    val error: String? = null,
    val pesanSukses: String? = null
)

/**
 * Menu **SN Goda** - daftarkan serial number unit sepeda listrik GODA dari HP,
 * dengan scan kamera sebagai jalur utamanya.
 *
 * **Cabang dipilih EKSPLISIT, dan itu penjaga data, bukan sekadar filter.**
 * `POST /goda/serial` menulis SN ke pasangan (kodeDealer, kodeBarang) yang
 * dikirim app; cabang yang salah = unit terdaftar di gudang yang tak
 * memegangnya, tanpa satu pun galat karena keduanya sama-sama sah di server.
 * Tebakan dari profil hanya dipakai sebagai NILAI AWAL yang TERLIHAT di layar
 * dan bisa diganti - bukan sebagai jawaban diam-diam.
 *
 * Menu ini SENGAJA tak mengganti SN yang sudah ada (`PUT /goda/serial/{id}`):
 * penggantian menghapus nilai lama permanen (registry tanpa tabel riwayat) dan
 * tetap jadi pekerjaan meja lewat web.
 */
@HiltViewModel
class GodaSerialViewModel @Inject constructor(
    private val repository: GodaRepository,
    tokenStore: TokenStore
) : ViewModel() {

    private val _state = MutableStateFlow(
        GodaSnUiState(kodeDealer = DealerAlias.resolveFromBranchName(tokenStore.cabangName).orEmpty())
    )
    val state: StateFlow<GodaSnUiState> = _state.asStateFlow()

    /** Dipanggil sekali saat layar masuk komposisi. Tanpa cabang = diam, bukan galat. */
    fun mulai() {
        if (_state.value.kodeDealer.isNotBlank() && _state.value.baris.isEmpty()) muat()
    }

    fun pilihCabang(kodeDealer: String) {
        if (kodeDealer == _state.value.kodeDealer) return
        _state.update {
            // Daftar cabang lama DIBUANG, bukan dibiarkan sambil memuat yang
            // baru: kartu yang masih menampilkan barang cabang lain sementara
            // header sudah berganti nama adalah cara termudah menambahkan SN ke
            // cabang yang salah.
            it.copy(
                kodeDealer = kodeDealer,
                baris = emptyList(),
                totalStok = 0,
                totalSn = 0,
                syncedAt = null,
                kodeBarangTerpilih = null,
                entri = "",
                entriError = null,
                entriPeringatan = null,
                error = null,
                pesanSukses = null
            )
        }
        muat()
    }

    fun muat() {
        val dealer = _state.value.kodeDealer
        if (dealer.isBlank()) return
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val res = repository.stok(dealer)) {
                is AuthResult.Success -> _state.update {
                    // Cabang bisa sudah diganti selagi permintaan ini terbang -
                    // hasil yang datang terlambat TIDAK boleh menimpa pilihan
                    // yang lebih baru.
                    if (it.kodeDealer != dealer) it
                    else it.copy(
                        loading = false,
                        baris = res.data.baris,
                        totalStok = res.data.totalStok,
                        totalSn = res.data.totalSn,
                        syncedAt = res.data.syncedAt
                    )
                }
                is AuthResult.Failure -> _state.update {
                    if (it.kodeDealer != dealer) it else it.copy(loading = false, error = res.message)
                }
            }
        }
    }

    fun onCariChange(nilai: String) = _state.update { it.copy(cari = nilai) }

    fun toggleBelumLengkap() = _state.update { it.copy(hanyaBelumLengkap = !it.hanyaBelumLengkap) }

    /** Buka/tutup panel input SN sebuah barang. Menutup = membuang ketikan yang belum disimpan. */
    fun pilihBarang(kodeBarang: String?) = _state.update {
        it.copy(
            kodeBarangTerpilih = if (it.kodeBarangTerpilih == kodeBarang) null else kodeBarang,
            entri = "",
            entriError = null,
            entriPeringatan = null,
            pesanSukses = null
        )
    }

    fun onEntriChange(nilai: String) = _state.update { st ->
        st.copy(
            entri = nilai,
            entriError = null,
            entriPeringatan = peringatanUntuk(st, nilai),
            pesanSukses = null
        )
    }

    /**
     * SATU pintu untuk scan kamera DAN ketikan manual - aturan normalisasi,
     * duplikat, dan penyimpanan tak boleh bercabang menurut cara SN itu masuk
     * (pola yang sama dipakai layar Input SN).
     */
    fun tambah(masukan: String) {
        val st = _state.value
        val kodeBarang = st.kodeBarangTerpilih ?: return
        val baris = st.baris.firstOrNull { it.kodeBarang == kodeBarang } ?: return
        val sn = rapikanSn(masukan)
        periksaSn(masukan)?.let { pesan ->
            _state.update { it.copy(entri = masukan, entriError = pesan) }
            return
        }
        if (sudahTerdaftarDiBarangIni(baris, sn)) {
            _state.update { it.copy(entri = sn, entriError = "SN $sn sudah terdaftar untuk barang ini") }
            return
        }
        if (st.menyimpan) return

        _state.update { it.copy(menyimpan = true, entri = sn, entriError = null, pesanSukses = null) }
        viewModelScope.launch {
            when (val res = repository.tambahSerial(st.kodeDealer, kodeBarang, sn)) {
                is AuthResult.Success -> _state.update { lama ->
                    lama.copy(
                        menyimpan = false,
                        entri = "",
                        entriError = null,
                        entriPeringatan = null,
                        pesanSukses = "SN ${res.data.serialNumber} tersimpan",
                        // Baris diperbarui di memori memakai ID dari server, bukan
                        // dengan memuat ulang seluruh cabang: petugas men-scan unit
                        // beruntun, dan satu putaran jaringan per unit membuat layar
                        // ini berhenti sanggup mengikuti kecepatan kerjanya.
                        baris = lama.baris.map { b ->
                            if (b.kodeBarang != kodeBarang) {
                                b
                            } else {
                                b.copy(
                                    serials = b.serials + GodaSerialDto(
                                        id = res.data.id,
                                        serialNumber = res.data.serialNumber
                                    ),
                                    jumlahSn = b.jumlahSn + 1
                                )
                            }
                        },
                        totalSn = lama.totalSn + 1
                    )
                }
                is AuthResult.Failure -> _state.update {
                    it.copy(menyimpan = false, entriError = res.message)
                }
            }
        }
    }

    fun bersihkanPesan() = _state.update { it.copy(pesanSukses = null, error = null) }

    private fun peringatanUntuk(st: GodaSnUiState, masukan: String): String? {
        val sn = rapikanSn(masukan)
        if (sn.isEmpty()) return null
        val kodeBarang = st.kodeBarangTerpilih ?: return null
        val lain = barangLainDenganSn(st.baris, sn, kodeBarang) ?: return null
        val nama = lain.namaBarang.ifBlank { lain.kodeBarang }
        return "SN ini sudah terdaftar di $nama - pastikan barangnya benar"
    }
}
