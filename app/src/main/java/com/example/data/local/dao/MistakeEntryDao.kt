package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entities.MistakeEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MistakeEntryDao {
    @Query("SELECT * FROM mistake_entries WHERE isDeleted = 0 ORDER BY timestamp DESC")
    fun getAllMistakes(): Flow<List<MistakeEntryEntity>>

    @Query("SELECT * FROM mistake_entries WHERE isDeleted = 0 ORDER BY timestamp DESC")
    suspend fun getAllMistakesSync(): List<MistakeEntryEntity>

    @Query("SELECT * FROM mistake_entries ORDER BY timestamp DESC")
    suspend fun getAllMistakesIncludingDeletedSync(): List<MistakeEntryEntity>

    @Query("SELECT * FROM mistake_entries WHERE id = :id LIMIT 1")
    suspend fun getMistakeById(id: Long): MistakeEntryEntity?

    @Query("SELECT * FROM mistake_entries WHERE syncId = :syncId LIMIT 1")
    suspend fun getMistakeBySyncId(syncId: String): MistakeEntryEntity?

    @Query("SELECT * FROM mistake_entries WHERE isDeleted = 0 AND isResolved = 0 ORDER BY timestamp DESC")
    fun getUnresolvedMistakes(): Flow<List<MistakeEntryEntity>>

    @Query("SELECT * FROM mistake_entries WHERE isDeleted = 0 AND subject = :subject ORDER BY timestamp DESC")
    fun getMistakesBySubject(subject: String): Flow<List<MistakeEntryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMistake(mistake: MistakeEntryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(mistakes: List<MistakeEntryEntity>)

    @Update
    suspend fun updateMistake(mistake: MistakeEntryEntity)

    @Query("UPDATE mistake_entries SET isDeleted = 1, updatedAt = :updatedAt, syncState = :syncState WHERE id = :id")
    suspend fun markMistakeDeleted(id: Long, updatedAt: Long, syncState: String = "PENDING")

    @Query("DELETE FROM mistake_entries WHERE id = :id")
    suspend fun deleteMistake(id: Long)
}
