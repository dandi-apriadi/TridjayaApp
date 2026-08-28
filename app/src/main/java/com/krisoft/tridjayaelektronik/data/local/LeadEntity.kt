package com.krisoft.tridjayaelektronik.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Local cache of the logged-in user's own leads, mirroring `GET /api/crm/leads?assignedTo=me`. */
@Entity(
    tableName = "leads",
    indices = [Index(value = ["nama"]), Index(value = ["status"]), Index(value = ["updatedAt"])]
)
data class LeadEntity(
    @PrimaryKey val id: Long,
    val nama: String,
    val phone: String,
    val pipelineId: Long,
    val stageId: Long,
    val status: String,
    val assignedTo: String?,
    /** Nama pemilik lead (hydrated server-side); null utk baris lokal/lama. */
    val assignedName: String? = null,
    /** UUID penginput — dipakai membedakan prospek yang dilempar antar sales. */
    val createdBy: String? = null,
    /** Nama penginput (hydrated server-side); dipakai langsung di kartu list. */
    val createdByName: String? = null,
    /** Cabang lead (hydrated server-side); ditampilkan di detail. */
    val cabang: String? = null,
    val estimatedValue: Double,
    val source: String?,
    val lokasi: String?,
    val lostReason: String?,
    val catatan: String?,
    val createdAt: String,
    val updatedAt: String,
    /** True while this lead was created locally (optimistic) and hasn't been pushed to the server yet.
     *  Such rows use a temporary negative [id] until the queued sync replaces them with the server row. */
    val pendingSync: Boolean = false,
    /**
     * Alasan server MENOLAK baris antrean ini secara permanen — kosong (`null`)
     * selama barisnya masih benar-benar mengantre.
     *
     * Ada karena antrean create prospek dulu memperlakukan SEMUA kegagalan
     * sebagai "belum ada sinyal": baris yang dijawab 400 tetap `pendingSync`
     * selamanya, terus dikirim ulang tiap create/refresh/buka-app, dan di layar
     * tetap berlabel "ANTRE". Sales-nya mengira prospeknya tersimpan, padahal
     * server tak pernah menerimanya — jadi prospek itu tidak ikut menghitung
     * target harian dan aktivitas raportnya tak pernah otomatis disetujui.
     * Terukur di nginx: 390 penolakan 400 pada `POST /api/prospek-harian` dalam
     * tujuh hari (8-14 Agt 2026), seluruhnya dari app (okhttp), naik 40/hari
     * menjadi 93/hari seiring baris macet menumpuk.
     *
     * Terisi = baris berhenti dikirim ulang dan layar menyebut sebabnya.
     * Barisnya SENGAJA tidak dihapus: isinya hasil kerja orang, hanya nomornya
     * yang perlu dibetulkan.
     */
    val syncRejectReason: String? = null,
    /**
     * Bukti yang SUDAH terunggah (path server), bukan berkas lokal.
     *
     * Disimpan di antrean supaya prospek yang menunggu sinkron tak kehilangan
     * buktinya — tanpa kolom ini, baris yang dibuat saat sinyal putus akan
     * terkirim TANPA bukti begitu sinyal pulih, dan untuk `trainee` itu
     * ditolak 400 yang divonis PERMANEN oleh `vonisPermanenProspek`.
     *
     * Unggahannya sendiri menuntut jaringan, jadi kolom ini hanya terisi untuk
     * prospek yang buktinya sempat terunggah sebelum disimpan.
     */
    val buktiUrl: String? = null,
    /** True while a stage move done offline/optimistically hasn't been pushed to the server yet.
     *  Only meaningful for server rows (positive id); temp pending rows sync via the create queue. */
    val stageDirty: Boolean = false,
    /** Pending offline outcome op ("won" | "lost" | "reopen") not yet pushed to the server.
     *  Only meaningful for server rows (positive id). */
    val statusDirtyOp: String? = null,
    // Draft-only fields kept so an offline-created prospect can be pushed later with the full
    // /api/prospek-harian payload (they aren't part of the CRM lead rows synced from the server).
    val minatBarang: String? = null,
    val kategoriProduk: String? = null,
    val keteranganFincoy: String? = null,
    /**
     * Peringatan bahwa WhatsApp PENUGASAN ke penerima lead ini TIDAK terkirim —
     * `assignmentNotification.message` dari `POST /api/prospek-harian`.
     *
     * **Bukan penolakan.** Beda tajam dari [syncRejectReason]: lead-nya
     * TERSIMPAN dengan benar di server, hanya pemberitahuannya yang gagal. Dua
     * kolom terpisah karena akibatnya berbeda — baris ditolak butuh diperbaiki
     * lalu dikirim ulang, baris ini butuh DIHUBUNGI MANUAL.
     *
     * **Kenapa disimpan di baris, bukan ditampilkan sekali sebagai snackbar.**
     * Web punya momen itu (`ProspekSubmitForm` merakit pesan sukses dari respons
     * yang baru datang), app TIDAK: `createLead` menulis ke Room lalu langsung
     * mengembalikan sukses, dan push-nya jalan di `appScope` yang bisa selesai
     * bermenit-menit kemudian tanpa layar apa pun yang hidup. Satu-satunya
     * tempat yang masih ada saat jawabannya tiba adalah barisnya sendiri.
     *
     * Diisi SETELAH `replaceAll` sinkronisasi (baris server, bukan baris temp
     * yang sudah dihapus), memakai id yang dipulangkan server.
     */
    val assignmentWarning: String? = null
)
