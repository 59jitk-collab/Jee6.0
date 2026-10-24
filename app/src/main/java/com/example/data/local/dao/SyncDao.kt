package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entities.SyncConflictEntity
import com.example.data.local.entities.SyncOutboxEntity
import com.example.data.local.entities.SyncWorkbookConfigEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncDao {
    // --- Outbox ---
    @Query("SELECT * FROM sync_outbox WHERE status IN ('PENDING', 'FAILED') ORDER BY createdAt ASC")
    fun getPendingOutboxFlow(): Flow<List<SyncOutboxEntity>>

    @Query("SELECT * FROM sync_outbox WHERE status IN ('PENDING', 'FAILED') ORDER BY createdAt ASC")
    suspend fun getPendingOutboxSync(): List<SyncOutboxEntity>

    @Query("SELECT * FROM sync_outbox WHERE entityType = :entityType AND syncId = :syncId AND status IN ('PENDING', 'FAILED') ORDER BY createdAt DESC LIMIT 1")
    suspend fun getActiveOutboxForRecord(entityType: String, syncId: String): SyncOutboxEntity?

    @Query("SELECT COUNT(*) FROM sync_outbox WHERE status IN ('PENDING', 'FAILED')")
    fun getPendingOutboxCountFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM sync_outbox WHERE status IN ('PENDING', 'FAILED')")
    suspend fun getPendingOutboxCountSync(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOutbox(entry: SyncOutboxEntity)

    @Update
    suspend fun updateOutbox(entry: SyncOutboxEntity)

    @Query("DELETE FROM sync_outbox WHERE operationId = :operationId")
    suspend fun deleteOutboxById(operationId: String)

    @Query("DELETE FROM sync_outbox WHERE entityType = :entityType AND syncId = :syncId AND status IN ('PENDING', 'FAILED')")
    suspend fun clearPendingOutboxForRecord(entityType: String, syncId: String)

    @Query("DELETE FROM sync_outbox WHERE status = 'COMPLETED'")
    suspend fun pruneCompletedOutbox()

    // --- Conflicts ---
    @Query("SELECT * FROM sync_conflicts WHERE isResolved = 0 ORDER BY detectedAt DESC")
    fun getUnresolvedConflictsFlow(): Flow<List<SyncConflictEntity>>

    @Query("SELECT * FROM sync_conflicts WHERE isResolved = 0 ORDER BY detectedAt DESC")
    suspend fun getUnresolvedConflictsSync(): List<SyncConflictEntity>

    @Query("SELECT * FROM sync_conflicts ORDER BY detectedAt DESC")
    fun getAllConflictsFlow(): Flow<List<SyncConflictEntity>>

    @Query("SELECT * FROM sync_conflicts ORDER BY detectedAt DESC")
    suspend fun getAllConflictsSync(): List<SyncConflictEntity>

    @Query("SELECT * FROM sync_conflicts WHERE conflictId = :conflictId LIMIT 1")
    suspend fun getConflictById(conflictId: String): SyncConflictEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateConflict(conflict: SyncConflictEntity)

    @Query("DELETE FROM sync_conflicts WHERE conflictId = :conflictId")
    suspend fun deleteConflict(conflictId: String)

    // --- Workbook Config ---
    @Query("SELECT * FROM sync_workbook_config WHERE id = 1 LIMIT 1")
    fun getWorkbookConfigFlow(): Flow<SyncWorkbookConfigEntity?>

    @Query("SELECT * FROM sync_workbook_config WHERE id = 1 LIMIT 1")
    suspend fun getWorkbookConfigSync(): SyncWorkbookConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateWorkbookConfig(config: SyncWorkbookConfigEntity)
}
