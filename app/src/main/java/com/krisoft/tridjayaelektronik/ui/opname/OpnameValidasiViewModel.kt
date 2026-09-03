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

/**
 * Rencana approve MASSAL: id unit dikelompokkan per SESI.
 *
 * **Pengelompokan ini wajib, bukan kerapian.** `approve-batch` di server
 * ber-path `/opname/{id}/units/approve-batch` — satu panggilan hanya menyentuh
 * SATU sesi, sedangkan antrian di layar ini datang dari
 * `GET /opname/manual-units` yang lintas-sesi. Mengirim seluruh id ke satu
 * sessionId berarti sisanya dijawab "bukan milik sesi ini" dan dilaporkan gagal.
 *
 * Urutan sesi mengikuti kemunculan pertamanya di daftar supaya laporan hasilnya
 * bisa dibaca berurutan dengan yang terlihat di layar.
 */
internal fun rencanaApproveMassal(items: List<ManualUnitDto>): List<Pair<String, List<String>>> =
    items.filter { it.sessionId.isNotBlank() }
        .groupBy { it.sessionId }
        .map { (sessionId, unit) -> sessionId to unit.map { it.id } }

/**
 * Kalimat hasil approve massal. `gagal > 0` BUKAN kegagalan operasi: server
 * menghitung unit yang sudah diputus orang lain di sela sebagai gagal, dan itu
 * hasil yang sah — pemutusnya perlu tahu angkanya, bukan disodori pesan error.
 */
internal fun ringkasHasilMassal(disetujui: Int, gagal: Int): String = when {
    disetujui == 0 && gagal == 0 -> "Tak ada unit yang diproses."
    gagal == 0 -> "$disetujui unit disetujui."
    disetujui == 0 -> "$gagal unit gagal diproses — mungkin sudah diputus orang lain."
    else -> "$disetujui unit disetujui, $gagal gagal (mungkin sudah diputus orang lain)."
}

data class OpnameValidasiUiState(
    val isLoading: Boolean = true,
    val items: List<ManualUnitDto> = emptyList(),
    /** Non-null = gagal MEMUAT. Layar wajib membedakannya dari daftar kosong. */
    val errorMessage: String? = null,
    val photos: Map<String, FotoBukti> = emptyMap(),
    /** Id unit yang keputusannya sedang dikirim (tombolnya dinonaktifkan). */
    val submittingId: String? = null,
    /** Approve massal sedang berjalan — tombolnya dan tombol per-baris dikunci. */
    val massalBerjalan: Boolean = false,
    /** Ringkasan hasil approve massal, termasuk yang sebagian gagal. */
    val hasilMassal: String? = null,
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

    /**
     * Setujui SELURUH unit yang sedang tampil.
     *
     * Yang dikirim adalah id yang BENAR-BENAR ada di layar, bukan daftar kosong
     * (= "semua pending sesi ini" bagi server): antrian bisa tersaring status,
     * dan menyetujui unit yang tak pernah dilihat pemutusnya adalah persis
     * kelas kesalahan yang tombol massal ini paling mudah menciptakan.
     *
     * Reject TIDAK punya padanan massal — menolak mengubah hitungan dan bisa
     * menghapus baris item, jadi ia tetap satu-per-satu dengan alasan wajib.
     */
    fun setujuiSemua() {
        val rencana = rencanaApproveMassal(_uiState.value.items)
        if (rencana.isEmpty() || _uiState.value.massalBerjalan) return
        _uiState.update { it.copy(massalBerjalan = true, actionError = null, hasilMassal = null) }
        viewModelScope.launch {
            var disetujui = 0
            var gagal = 0
            var pesanGagal: String? = null
            val selesai = mutableSetOf<String>()
            for ((sessionId, unitIds) in rencana) {
                when (val r = repository.approveManualUnitsBatch(sessionId, unitIds)) {
                    is AuthResult.Success -> {
                        disetujui += r.data.disetujui
                        gagal += r.data.gagal
                        // Seluruh id sesi ini dibuang dari layar apa pun hasilnya:
                        // yang `gagal` pun sudah TIDAK pending lagi (diputus orang
                        // lain di sela), jadi menahannya di antrian menyuruh
                        // pemutus menekan sesuatu yang sudah selesai.
                        selesai += unitIds
                    }
                    // Sesi yang gagal TIDAK dibuang — unitnya masih pending dan
                    // pemutusnya harus bisa mencoba lagi.
                    is AuthResult.Failure -> pesanGagal = r.message
                }
            }
            _uiState.update {
                it.copy(
                    massalBerjalan = false,
                    items = it.items.filterNot { u -> u.id in selesai },
                    hasilMassal = if (disetujui > 0 || gagal > 0) ringkasHasilMassal(disetujui, gagal) else null,
                    actionError = pesanGagal,
                )
            }
        }
    }

    fun clearHasilMassal() = _uiState.update { it.copy(hasilMassal = null) }

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
