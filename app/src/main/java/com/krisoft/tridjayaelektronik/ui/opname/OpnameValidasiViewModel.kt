package com.krisoft.tridjayaelektronik.ui.opname

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krisoft.tridjayaelektronik.data.AuthResult
import com.krisoft.tridjayaelektronik.data.OpnameRepository
import com.krisoft.tridjayaelektronik.data.VALIDASI_PENDING
import com.krisoft.tridjayaelektronik.data.model.ManualUnitDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Keadaan satu foto bukti. TIGA keadaan sengaja dibedakan — pemutus harus bisa
 * tahu apakah petugas memang tak memotret atau berkasnya hilang dari server.
 * Tak ada entri di peta = unit ini memang tanpa URL foto.
 */
sealed interface FotoBukti {
    data object Memuat : FotoBukti
    data class Ada(val bitmap: Bitmap) : FotoBukti
    data object Gagal : FotoBukti
}

/** Satu unit punya DUA foto, jadi kuncinya unit + slot, bukan unit saja. */
internal fun fotoKey(unitId: String, slot: String) = "$unitId:$slot"

/** Baris yang sudah diputus lenyap dari antrian — pemutus tak boleh melihat
 *  pekerjaan yang sudah selesai dan menekannya dua kali. */
internal fun buangUnit(items: List<ManualUnitDto>, unitId: String): List<ManualUnitDto> =
    items.filterNot { it.id == unitId }

data class OpnameValidasiUiState(
    val isLoading: Boolean = true,
    val items: List<ManualUnitDto> = emptyList(),
    /** Non-null = gagal MEMUAT. Layar wajib membedakannya dari daftar kosong. */
    val errorMessage: String? = null,
    val photos: Map<String, FotoBukti> = emptyMap(),
    /** Id unit yang keputusannya sedang dikirim (tombolnya dinonaktifkan). */
    val submittingId: String? = null,
    val actionError: String? = null,
    /**
     * `pending` (default) | `approved` | `rejected` — cerminan nilai yang
     * diterima `list_manual_units_handler`. Sebelum ini `load()` memanggil
     * `repository.manualUnits()` TANPA argumen, jadi riwayat keputusan tak
     * pernah bisa dibuka dari app sama sekali walau jalurnya sudah utuh sampai
     * server.
     */
    val status: String = VALIDASI_PENDING,
)

/**
 * Antrian validasi unit opname ketik-manual — admin-stok SAJA
 * (`inventory-service opname.rs has_admin_stok`).
 *
 * Foto TIDAK dimuat bersama daftar: satu unit = dua JPEG ber-watermark, dan
 * antrian panjang × 2 bitmap penuh di state bisa menghabiskan memori HP
 * lapangan. Kartu memanggil [muatFoto] saat dibuka.
 */
@HiltViewModel
class OpnameValidasiViewModel @Inject constructor(
    private val repository: OpnameRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(OpnameValidasiUiState())
    val uiState: StateFlow<OpnameValidasiUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    /**
     * Ganti tab status lalu muat ulang. Nilai divalidasi server (400 untuk yang
     * asing), jadi jangan mengarang nilai baru di sini — pakai konstanta
     * `VALIDASI_*`.
     */
    fun gantiStatus(baru: String) {
        if (baru == _uiState.value.status) return
        _uiState.update { it.copy(status = baru) }
        load()
    }

    fun load() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = repository.manualUnits(_uiState.value.status)) {
                is AuthResult.Success -> _uiState.update {
                    // Peta foto dikosongkan tiap muat ulang: bitmap lama tak
                    // boleh menumpuk, dan foto yang tadi gagal jadi bisa dicoba
                    // lagi lewat tarik-turun.
                    it.copy(isLoading = false, items = result.data, photos = emptyMap())
                }
                is AuthResult.Failure -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = result.message)
                }
            }
        }
    }

    /** Idempoten — kartu yang dibuka-tutup tak menarik ulang foto yang sama. */
    fun muatFoto(unit: ManualUnitDto) {
        listOf("sn" to unit.fotoSnUrl, "barang" to unit.fotoBarangUrl).forEach { (slot, url) ->
            if (url.isNullOrBlank()) return@forEach
            val key = fotoKey(unit.id, slot)
            if (_uiState.value.photos.containsKey(key)) return@forEach
            _uiState.update { it.copy(photos = it.photos + (key to FotoBukti.Memuat)) }
            viewModelScope.launch {
                val bytes = repository.fetchSerialPhoto(url)
                val bmp = bytes?.let {
                    withContext(Dispatchers.Default) {
                        // Setengah ukuran: yang dibutuhkan cuma keterbacaan nomor
                        // seri di dialog penuh, bukan resolusi kamera penuh.
                        val opsi = BitmapFactory.Options().apply { inSampleSize = 2 }
                        BitmapFactory.decodeByteArray(it, 0, it.size, opsi)
                    }
                }
                _uiState.update {
                    it.copy(photos = it.photos + (key to (bmp?.let(FotoBukti::Ada) ?: FotoBukti.Gagal)))
                }
            }
        }
    }

    fun setujui(unit: ManualUnitDto) =
        putuskan(unit) { repository.approveManualUnit(unit.sessionId, unit.id) }

    fun tolak(unit: ManualUnitDto, alasan: String) =
        putuskan(unit) { repository.rejectManualUnit(unit.sessionId, unit.id, alasan) }

    private fun putuskan(unit: ManualUnitDto, aksi: suspend () -> AuthResult<Unit>) {
        if (_uiState.value.submittingId != null) return
        _uiState.update { it.copy(submittingId = unit.id, actionError = null) }
        viewModelScope.launch {
            when (val result = aksi()) {
                is AuthResult.Success -> _uiState.update {
                    it.copy(submittingId = null, items = buangUnit(it.items, unit.id))
                }
                is AuthResult.Failure -> _uiState.update {
                    it.copy(submittingId = null, actionError = result.message)
                }
            }
        }
    }

    fun clearActionError() = _uiState.update { it.copy(actionError = null) }
}
