package com.krisoft.tridjayaelektronik.data.model

import kotlinx.serialization.Serializable

@Serializable
data class PipelineStageDto(
    val id: Long = 0,
    val pipelineId: Long = 0,
    val nama: String = "",
    val urutan: Int = 0,
    val autoTaskJudul: String? = null,
    val autoTaskDueDays: Int? = null
)

@Serializable
data class PipelineDto(
    val id: Long = 0,
    val nama: String = "",
    val isDefault: Boolean = false,
    val stages: List<PipelineStageDto> = emptyList()
)

@Serializable
data class PipelinesData(
    val items: List<PipelineDto> = emptyList()
)

@Serializable
data class LeadDto(
    val id: Long = 0,
    val nama: String = "",
    val phone: String = "",
    val pipelineId: Long = 0,
    val stageId: Long = 0,
    val status: String = "",
    val assignedTo: String? = null,
    /** Nama karyawan pemilik (hydrated server-side dari auth_users) — tampilkan ini, bukan UUID. */
    val assignedName: String? = null,
    /** UUID penginput lead (beda dari assignedTo saat prospek dilempar ke sales lain). */
    val createdBy: String? = null,
    /** Nama penginput (hydrated server-side) — dipakai langsung, bukan lookup peta klien yang bisa
     *  meleset ke "Sales lain" untuk user yang tak ada di daftar assignee (mis. non-aktif). */
    val createdByName: String? = null,
    val estimatedValue: Double = 0.0,
    val source: String? = null,
    val lokasi: String? = null,
    val lostReason: String? = null,
    val catatan: String? = null,
    val minatBarang: String? = null,
    val kategoriProduk: String? = null,
    /** Cabang lead (dikembalikan backend Lead) — ditampilkan di detail. */
    val cabang: String? = null,
    val createdAt: String = "",
    val updatedAt: String = "",
    /** Client-only: set for optimistic local leads awaiting sync. Server responses default it to false. */
    val pendingSync: Boolean = false,
    /**
     * Client-only: alasan server MENOLAK baris antrean ini secara permanen.
     * `null` = masih benar-benar mengantre. Lihat `LeadEntity.syncRejectReason`
     * — layar WAJIB membedakannya dari "ANTRE", karena keduanya terlihat sama
     * bagi sales sampai targetnya meleset di akhir hari.
     */
    val syncRejectReason: String? = null,
    /**
     * Client-only: WhatsApp penugasan ke penerima lead ini TIDAK terkirim.
     * Lead-nya TERSIMPAN — beda tajam dari [syncRejectReason]. Lihat
     * `LeadEntity.assignmentWarning`.
     */
    val assignmentWarning: String? = null
)

@Serializable
data class LeadListData(
    val items: List<LeadDto> = emptyList(),
    val total: Long = 0,
    val page: Int = 1,
    val limit: Int = 20
)

@Serializable
data class LeadDetailData(
    val lead: LeadDto = LeadDto()
)

/**
 * Body for `POST /api/prospek-harian` — the SAME endpoint the web's "Submit Prospek" form uses
 * (kinerja-service). Unlike posting straight to `/api/crm/leads`, this path also records the
 * prospect against the daily prospek target/raport and fires the assignment notification.
 * Backend requires namaProspek + noWhatsapp + minatBarang; statusProspek defaults to "leads_baru".
 */
@Serializable
data class CreateProspekRequest(
    val namaProspek: String,
    val noWhatsapp: String,
    val minatBarang: String,
    val kategoriProduk: String? = null,
    val keteranganProspek: String? = null,
    val statusProspek: String = "leads_baru",
    val keteranganFincoy: String? = null,
    val tanggal: String? = null,
    val pipelineId: Long? = null,
    val source: String? = null,
    val estimatedValue: Double? = null,
    val lokasi: String? = null,
    val assignedTo: String? = null,
    /**
     * Path bukti hasil `POST /api/prospek-harian/bukti` — BUKAN path karangan.
     *
     * Server memvalidasi bentuknya (`/uploads/prospek/<nama>`, tanpa separator
     * di dalam nama) DAN keberadaan berkasnya; nilai yang tak lolos dibuang
     * jadi NULL. Untuk `trainee` bukti WAJIB, karena `closing_terverifikasi`
     * di scorecard training hanya menghitung closing yang punya bukti.
     */
    val buktiUrl: String? = null
)

/**
 * Balasan `POST /api/prospek-harian/bukti`. `url` sudah berbentuk
 * `/uploads/prospek/<nama>` dan dikirim BALIK apa adanya sebagai
 * [CreateProspekRequest.buktiUrl] — jangan disusun ulang di klien.
 */
@Serializable
data class ProspekUploadData(
    val url: String = "",
)

/**
 * Hasil pengiriman WhatsApp PENUGASAN ke penerima lead —
 * `AssignmentNotification` (kinerja-service `prospek/assignment.rs`).
 *
 * `status`: `sent` | `skipped_self` | `send_failed` | dan beberapa varian gagal
 * lain (`failed(...)`: penerima tak ditemukan, nomor WA kosong, dst). Diperlakukan
 * sebagai STRING, bukan enum: nilai baru dari server tak boleh membuat dekode
 * gagal, dan yang app butuhkan cuma "berhasil atau tidak".
 */
@Serializable
data class AssignmentNotificationDto(
    val status: String = "",
    val message: String = "",
    val to: String? = null
) {
    /**
     * `true` bila penerima lead ini TIDAK diberitahu. `skipped_self` bukan
     * kegagalan — penugasan ke diri sendiri memang sengaja tak dikirimi WA.
     *
     * Status yang TIDAK DIKENAL dihitung gagal (fail-closed): satu peringatan
     * berlebih ongkosnya sebaris teks, sementara kegagalan yang lolos berarti
     * lead mati tanpa ada yang tahu.
     */
    val perluDiberitahukan: Boolean get() = status != "sent" && status != "skipped_self"
}

/** Loose response payload of `POST /api/prospek-harian` — we only care that it succeeded (+ id). */
@Serializable
data class CreateProspekData(
    val id: Long? = null,
    /**
     * Dibaca sejak 2026-08-28. Sebelumnya field ini di-drop `ignoreUnknownKeys`,
     * dan `prospek.rs` sendiri berkomentar bahwa app Android tak pernah
     * membacanya sehingga "WA gagal kirim" terlihat identik dengan "berhasil".
     */
    val assignmentNotification: AssignmentNotificationDto? = null
)

/**
 * `GET /api/prospek-harian/my-target` — target prospek harian MILIK pemegang
 * token (login-only, isinya tak pernah berisi angka orang lain).
 *
 * Angka ini SENGAJA tidak dihitung ulang di klien: server menghitung per
 * `karyawan_id` (penerima penugasan), sedangkan cache lokal hanya tahu
 * `createdBy` (penginput). Prospek yang dilempar ke sales lain membuat kedua
 * angka itu berselisih, dan raport/summary/KPI memakai angka server.
 *
 * `target = 0` berarti setelan target tak diketahui/nonaktif — perlakukan sama
 * dengan tak ada data (lihat `buildDailyTasks`), jangan dirender "3/0".
 */
@Serializable
data class ProspekTargetDto(
    val target: Int = 0,
    val aktual: Int = 0,
    val tercapai: Boolean = false
)

/** One selectable assignment target from `GET /api/prospek-harian/assignees` (active employees). */
@Serializable
data class AssigneeDto(
    val id: String = "",
    val name: String = "",
    val cabang: String? = null,
    val divisi: String? = null
)

@Serializable
data class AssigneesData(
    val items: List<AssigneeDto> = emptyList()
)

/** Local (non-network) draft of a new prospect, held in Room while offline until it can be pushed. */
data class ProspekDraft(
    val nama: String,
    val phone: String,
    val minatBarang: String,
    val kategoriProduk: String?,
    val keteranganFincoy: String?,
    val pipelineId: Long?,
    val assignedTo: String?,
    val estimatedValue: Double?,
    val source: String?,
    val lokasi: String?,
    val catatan: String?,
    /**
     * Path bukti yang SUDAH terunggah (`/uploads/prospek/<nama>`), bukan URI
     * berkas lokal. Unggahnya terjadi SEBELUM draft ini dibuat, karena server
     * memeriksa keberadaan berkasnya saat prospek disimpan.
     */
    val buktiUrl: String? = null
)

@Serializable
data class MoveStageRequest(
    val stageId: Long
)

@Serializable
data class LostLeadRequest(
    val reason: String
)

/**
 * Catatan aktivitas pada sebuah lead (`POST /api/crm/leads/{id}/activities`).
 *
 * `jenis` mengikuti enum backend `ActivityJenis` (`call|wa|visit|meeting|note`;
 * `system` HANYA ditulis server). Dipakai untuk merekam bahwa tombol WhatsApp
 * ditekan — worker pengingat crm-service menilai "sudah di-follow-up" dari
 * ada-tidaknya baris non-`system`, jadi tanpa jejak ini sales yang rajin
 * menghubungi prospeknya tetap terhitung belum follow-up.
 */
@Serializable
data class CreateActivityRequest(
    val jenis: String,
    val isi: String
)
