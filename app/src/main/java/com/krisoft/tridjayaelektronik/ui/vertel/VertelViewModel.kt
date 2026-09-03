package com.krisoft.tridjayaelektronik.ui.vertel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krisoft.tridjayaelektronik.data.AuthResult
import com.krisoft.tridjayaelektronik.data.VertelRepository
import com.krisoft.tridjayaelektronik.data.model.DaftarVertelDto
import com.krisoft.tridjayaelektronik.data.model.VertelCatatBody
import com.krisoft.tridjayaelektronik.domain.sales.KlasemenStandings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class VertelUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val actionError: String? = null,
    val submitting: Boolean = false,
    val daftar: DaftarVertelDto? = null,
    /**
     * Tanggal yang SEDANG ditampilkan. `null` = belum pernah memuat, jadi
     * permintaan berikutnya menyerahkan penentuan "kemarin" ke SERVER.
     *
     * Sesudah muatan pertama, nilainya diisi dari `daftar.tanggal` — jawaban
     * server, bukan hitungan sendiri. Itu yang membuat tombol geser hari tetap
     * benar di HP yang zona waktunya bukan WIB.
     */
    val tanggal: String? = null,
    /** `noTransaksi` baris yang form catatannya sedang dibuka. */
    val terbuka: String? = null,
)

/**
 * Verifikasi telepon penjualan kemarin (`vertel.manage`).
 *
 * **Tanggal "kemarin" TIDAK dihitung di sini untuk muatan pertama.** Server
 * memakai `kemarin_wib()`; menghitungnya di HP berarti memakai zona waktu
 * perangkat, dan HP yang zonanya bukan WIB akan meminta tanggal yang salah
 * tanpa satu pun galat. Baru setelah server menjawab, tanggalnya diketahui dan
 * geser-hari bisa dihitung dari jawaban itu.
 */
@HiltViewModel
class VertelViewModel @Inject constructor(
    private val repository: VertelRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(VertelUiState())
    val state: StateFlow<VertelUiState> = _state.asStateFlow()

    init {
        muat()
    }

    /** [tanggal] `null` = biarkan server memutuskan (kemarin WIB). */
    fun muat(tanggal: String? = _state.value.tanggal) {
        _state.update { it.copy(loading = true, error = null, actionError = null) }
        viewModelScope.launch {
            when (val r = repository.daftar(tanggal)) {
                is AuthResult.Success -> _state.update {
                    it.copy(
                        loading = false,
                        error = null,
                        daftar = r.data,
                        // Tanggal diambil dari JAWABAN server, bukan dari yang
                        // kita minta: kalau permintaannya `null`, hanya inilah
                        // satu-satunya cara tahu hari apa yang sedang dibaca.
                        tanggal = r.data.tanggal.takeIf { t -> t.isNotBlank() } ?: it.tanggal,
                    )
                }
                // Daftar lama dipertahankan — verifikator yang sedang menelepon
                // tak boleh kehilangan nomor yang sudah ada di layarnya karena
                // satu refresh gagal.
                is AuthResult.Failure -> _state.update { it.copy(loading = false, error = r.message) }
            }
        }
    }

    /**
     * Geser hari. Memakai `KlasemenStandings.shiftDays` (Calendar) — **bukan
     * `java.time`**, yang haram di `app/src/main` (minSdk 24 tanpa desugaring;
     * di Android 7 ia melempar `NoClassDefFoundError`, turunan `Error` yang
     * lolos dari `catch (e: Exception)` dan menutup app).
     *
     * Tanpa tanggal terbaca (muatan pertama masih gagal) tombolnya tak berbuat
     * apa-apa: menebak titik awal di sini akan menghasilkan hari yang salah.
     */
    fun geserHari(hari: Int) {
        val sekarang = _state.value.tanggal ?: return
        muat(KlasemenStandings.shiftDays(sekarang, hari))
    }

    fun buka(noTransaksi: String?) = _state.update { it.copy(terbuka = noTransaksi, actionError = null) }

    fun bersihkanActionError() = _state.update { it.copy(actionError = null) }

    /**
     * [tanggalTransaksi] adalah tanggal TRANSAKSI dari barisnya, BUKAN tanggal
     * hari ini dan bukan `state.tanggal`. Ia bagian dari kunci upsert di server;
     * mengirim tanggal lain menaruh catatannya di baris yang salah.
     */
    fun catat(
        noTransaksi: String,
        tanggalTransaksi: String,
        kanal: String,
        hasil: String,
        adaKomplain: Boolean,
        catatan: String,
        onSukses: () -> Unit,
    ) {
        val gate = VertelPlan.catatGate(kanal, hasil, adaKomplain, catatan)
        if (!gate.bolehSimpan) {
            _state.update { it.copy(actionError = gate.alasan) }
            return
        }
        _state.update { it.copy(submitting = true, actionError = null) }
        viewModelScope.launch {
            val body = VertelCatatBody(
                noTransaksi = noTransaksi,
                tanggal = tanggalTransaksi,
                kanal = kanal,
                hasil = hasil,
                adaKomplain = adaKomplain,
                catatan = catatan.trim().takeIf { it.isNotBlank() },
            )
            when (val r = repository.catat(body)) {
                is AuthResult.Success -> {
                    val baris = r.data
                    _state.update { s ->
                        val lama = s.daftar
                        s.copy(
                            submitting = false,
                            actionError = null,
                            // Baris ditimpa DI TEMPAT. Ringkasannya sengaja
                            // TIDAK dihitung ulang di klien: aturan `ringkas`
                            // hidup di server, dan menyalinnya ke sini
                            // melahirkan angka kedua yang bisa berselisih.
                            // Angka yang benar datang pada refresh berikutnya.
                            daftar = lama?.copy(
                                baris = lama.baris.map {
                                    if (it.noTransaksi == baris.noTransaksi) baris else it
                                },
                            ),
                            terbuka = null,
                        )
                    }
                    onSukses()
                }
                is AuthResult.Failure -> _state.update {
                    it.copy(submitting = false, actionError = r.message)
                }
            }
        }
    }
}
