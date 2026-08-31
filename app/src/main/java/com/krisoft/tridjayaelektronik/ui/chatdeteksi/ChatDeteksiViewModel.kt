package com.krisoft.tridjayaelektronik.ui.chatdeteksi

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krisoft.tridjayaelektronik.data.AuthResult
import com.krisoft.tridjayaelektronik.data.ChatDeteksiRepository
import com.krisoft.tridjayaelektronik.data.model.StatusChatDeteksiDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatDeteksiUiState(
    val loading: Boolean = true,
    val status: StatusChatDeteksiDto? = null,
    val videoUri: Uri? = null,
    val videoUkuranBytes: Long = 0,
    val videoNama: String = "",
    val mengirim: Boolean = false,
    val pesanError: String? = null,
    val pesanSukses: String? = null,
) {
    val gate: KirimGate get() = bolehKirim(videoUri != null, videoUkuranBytes)

    /** Sudah ada keputusan LLM final hari ini — tak ada lagi yang bisa
     *  dilakukan karyawan selain lihat hasilnya. */
    val sudahFinal: Boolean
        get() = status?.status == "lolos_otomatis" || status?.status == "tidak_lolos_otomatis"
}

/**
 * Deteksi otomatis jumlah chat via LLM. Pola sama
 * [com.krisoft.tridjayaelektronik.ui.chatactivity.ChatActivityViewModel] lama
 * TAPI jauh lebih sederhana: satu panggilan unggah, tanpa langkah "kirim
 * jumlah" terpisah (LLM yang menghitung, bukan karyawan yang mengetik) dan
 * tanpa antrian review (tidak ada endpoint review di backend baru ini).
 */
@HiltViewModel
class ChatDeteksiViewModel @Inject constructor(
    private val repository: ChatDeteksiRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatDeteksiUiState())
    val uiState: StateFlow<ChatDeteksiUiState> = _uiState.asStateFlow()

    init {
        muat()
    }

    fun muat() {
        _uiState.update { it.copy(loading = true, pesanError = null) }
        viewModelScope.launch {
            when (val res = repository.status()) {
                is AuthResult.Success ->
                    _uiState.update { it.copy(loading = false, status = res.data) }
                is AuthResult.Failure ->
                    _uiState.update { it.copy(loading = false, pesanError = res.message) }
            }
        }
    }

    fun pilihVideo(uri: Uri, ukuran: Long, nama: String) {
        _uiState.update {
            it.copy(videoUri = uri, videoUkuranBytes = ukuran, videoNama = nama, pesanError = null)
        }
    }

    fun bersihkanPesan() = _uiState.update { it.copy(pesanError = null, pesanSukses = null) }

    /** Gate diperiksa SEBELUM unggahan: menolak di klien lebih murah daripada
     *  menghabiskan kuota karyawan mengirim video yang pasti ditolak server. */
    fun kirim(resolver: ContentResolver) {
        val state = _uiState.value
        if (state.mengirim) return
        val gate = state.gate
        if (!gate.ok) {
            _uiState.update { it.copy(pesanError = gate.alasan) }
            return
        }
        val uri = state.videoUri ?: return
        _uiState.update { it.copy(mengirim = true, pesanError = null, pesanSukses = null) }
        viewModelScope.launch {
            val nama = state.videoNama.ifBlank { "chat_deteksi_${System.currentTimeMillis()}.mp4" }
            val mime = resolver.getType(uri) ?: "video/mp4"
            when (val res = repository.kirimVideo(resolver, uri, nama, mime, state.videoUkuranBytes)) {
                is AuthResult.Failure ->
                    _uiState.update { it.copy(mengirim = false, pesanError = res.message) }
                is AuthResult.Success -> {
                    _uiState.update {
                        it.copy(
                            mengirim = false,
                            videoUri = null,
                            videoUkuranBytes = 0,
                            videoNama = "",
                            pesanSukses = "Video terkirim, sedang diproses.",
                        )
                    }
                    tungguHasil()
                }
            }
        }
    }

    /**
     * Polling singkat SETELAH kirim sukses, supaya karyawan yang menunggu
     * tak perlu keluar-masuk layar untuk lihat hasilnya. Berhenti sendiri
     * begitu statusnya final ATAU gagal ([gagalPerluKirimUlang]), dan
     * menyerah setelah [MAKS_POLLING] percobaan — deteksi LLM terukur bisa
     * makan sampai ~90 detik untuk model reasoning (lihat `chat_video_llm.rs`
     * dan memory `ai-trading-llm-9router`), bukan sesuatu yang selalu selesai
     * dalam hitungan detik. Kalau menyerah, layar tetap menunjukkan
     * "sedang diproses" — [muat] berikutnya (mis. karyawan membuka layar lagi)
     * akan menemukan hasilnya kalau sudah selesai di server.
     */
    private suspend fun tungguHasil() {
        repeat(MAKS_POLLING) {
            delay(JEDA_POLLING_MS)
            when (val res = repository.status()) {
                is AuthResult.Success -> {
                    val data = res.data
                    _uiState.update { it.copy(status = data) }
                    if (data == null) return
                    if (data.status != "pending_review" || gagalPerluKirimUlang(data.status, data.llmError)) {
                        return
                    }
                }
                is AuthResult.Failure -> return
            }
        }
    }

    private companion object {
        const val MAKS_POLLING = 24
        const val JEDA_POLLING_MS = 4000L
    }
}
