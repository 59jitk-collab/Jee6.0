package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entities.RevisionItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RevisionItemDao {
    @Query("SELECT * FROM revision_items WHERE isCompleted = 0 ORDER BY dueDate ASC, id ASC")
    fun getPendingRevisions(): Flow<List<RevisionItemEntity>>

    @Query("SELECT * FROM revision_items ORDER BY isCompleted ASC, dueDate ASC, id DESC")
    fun getAllRevisions(): Flow<List<RevisionItemEntity>>

    @Query("SELECT * FROM revision_items WHERE isCompleted = 0 ORDER BY dueDate ASC")
    suspend fun getPendingRevisionsSync(): List<RevisionItemEntity>

    @Query("SELECT * FROM revision_items WHERE chapterId = :chapterId AND isCompleted = 0 LIMIT 1")
    suspend fun getActiveRevisionForChapter(chapterId: Long): RevisionItemEntity?

    @Query("SELECT * FROM revision_items WHERE dueDate <= :todayDate AND isCompleted = 0")
    fun getDueToday(todayDate: String): Flow<List<RevisionItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRevision(item: RevisionItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<RevisionItemEntity>)

    @Update
    suspend fun updateRevision(item: RevisionItemEntity)

    @Query("DELETE FROM revision_items WHERE id = :id")
    suspend fun deleteRevision(id: Long)
}
