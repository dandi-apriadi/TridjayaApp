package com.krisoft.tridjayaelektronik.data.model

import kotlinx.serialization.Serializable

/**
 * Deteksi otomatis jumlah chat WA via LLM (`kinerja-service::chat_deteksi`,
 * 2026-08-31) — menggantikan fitur "bukti chat" lama (`AktivitasChatModels.kt`,
 * dicabut vc93) yang direview manusia. Fitur baru ini TIDAK PUNYA endpoint
 * review: server memutuskan lolos/tidak lolos sendiri lewat LLM, dan
 * `targetMinimal` SELALU dihitung server (klien tak pernah mengirimnya).
 *
 * SEMUA field punya nilai default — lihat KDoc [AktivitasChatTodayDto] untuk
 * alasannya (satu field hilang tak boleh menjatuhkan seluruh respons).
 */

/** `POST /api/chat-deteksi` — video baru diterima, deteksi berjalan di latar
 *  belakang server. `status` di sini SELALU `"pending_review"`. */
@Serializable
data class KirimVideoChatDto(
    val id: String = "",
    val status: String = "pending_review",
)

/** `GET /api/chat-deteksi/status` — status submisi HARI INI milik pemanggil.
 *  Server membalas `data: null` (bukan objek kosong) kalau belum pernah
 *  mengirim hari ini. */
@Serializable
data class StatusChatDeteksiDto(
    val id: String = "",
    val status: String = "pending_review",
    val targetMinimal: Int = 100,
    val llmJumlahTerhitung: Int? = null,
    val llmConfidence: String? = null,
    val llmCatatan: String? = null,
    val llmError: String? = null,
)
