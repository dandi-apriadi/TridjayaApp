package com.krisoft.tridjayaelektronik.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface LeadDao {

    @Query(
        """
        SELECT * FROM leads
        WHERE :search = '' OR nama LIKE '%' || :search || '%' OR phone LIKE '%' || :search || '%'
        ORDER BY updatedAt DESC
        """
    )
    suspend fun search(search: String): List<LeadEntity>

    /** Reactive variant of [search] — emits a fresh list whenever the leads cache changes (e.g. after
     *  a new lead is created or a stage/status mutation writes back), so the list updates live. */
    @Query(
        """
        SELECT * FROM leads
        WHERE :search = '' OR nama LIKE '%' || :search || '%' OR phone LIKE '%' || :search || '%'
        ORDER BY updatedAt DESC
        """
    )
    fun observe(search: String): Flow<List<LeadEntity>>

    @Query("SELECT * FROM leads")
    suspend fun all(): List<LeadEntity>

    /** Reactive full list, used to recompute the CRM summary live. */
    @Query("SELECT * FROM leads")
    fun observeAll(): Flow<List<LeadEntity>>

    /** Locally-created leads still waiting to be pushed to the server (the sync queue). */
    @Query("SELECT * FROM leads WHERE pendingSync = 1 ORDER BY id DESC")
    suspend fun pendingLeads(): List<LeadEntity>

    /**
     * Baris antrean yang MASIH masuk akal dikirim ulang — yang sudah divonis
     * permanen oleh server ([LeadEntity.syncRejectReason] terisi) dilewati.
     *
     * Beda dari [pendingLeads], dan bedanya penting: [pendingLeads] tetap
     * memuat baris tertolak karena penyegaran cache memakainya untuk memutuskan
     * baris mana yang TIDAK boleh tertimpa data server. Yang berhenti hanyalah
     * pengirimannya, bukan penyimpanannya.
     */
    @Query("SELECT * FROM leads WHERE pendingSync = 1 AND syncRejectReason IS NULL ORDER BY id DESC")
    suspend fun pendingPushableLeads(): List<LeadEntity>

    /** Catat vonis permanen server pada satu baris antrean. */
    @Query("UPDATE leads SET syncRejectReason = :reason WHERE id = :id")
    suspend fun markSyncRejected(id: Long, reason: String)

    /**
     * Catat bahwa WA penugasan untuk lead ini tak terkirim. Dipanggil dengan id
     * SERVER dan SESUDAH `replaceAll` sinkronisasi — baris temp-nya sudah
     * dihapus saat push berhasil, dan `replaceAll` akan menimpa apa pun yang
     * ditulis sebelum itu.
     *
     * No-op bila barisnya tak ada (lead dilempar ke sales lain DAN tarikan
     * `createdBy` kebetulan gagal). Peringatannya hilang, lead-nya tidak — itu
     * kompromi yang disengaja: mengarang baris demi menampung peringatan jauh
     * lebih buruk daripada peringatan yang tak sempat tampil.
     */
    @Query("UPDATE leads SET assignmentWarning = :pesan WHERE id = :id")
    suspend fun markAssignmentWarning(id: Long, pesan: String)

    @Query("SELECT * FROM leads WHERE id = :id")
    suspend fun byId(id: Long): LeadEntity?

    /** Server rows whose stage was moved offline and still needs pushing. */
    @Query("SELECT * FROM leads WHERE stageDirty = 1 AND id > 0")
    suspend fun dirtyStageLeads(): List<LeadEntity>

    /** Server rows whose won/lost/reopen outcome was set offline and still needs pushing. */
    @Query("SELECT * FROM leads WHERE statusDirtyOp IS NOT NULL AND id > 0")
    suspend fun dirtyStatusLeads(): List<LeadEntity>

    @Query("DELETE FROM leads WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rows: List<LeadEntity>)

    @Query("DELETE FROM leads")
    suspend fun clearAll()

    @Transaction
    suspend fun replaceAll(rows: List<LeadEntity>) {
        clearAll()
        insertAll(rows)
    }
}
