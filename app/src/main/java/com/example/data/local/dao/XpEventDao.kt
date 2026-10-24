package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entities.XpEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface XpEventDao {
    @Query("SELECT * FROM xp_events ORDER BY timestamp DESC")
    fun getAllXpEvents(): Flow<List<XpEventEntity>>

    @Query("SELECT * FROM xp_events ORDER BY timestamp DESC")
    suspend fun getAllXpEventsSync(): List<XpEventEntity>

    @Query("SELECT * FROM xp_events WHERE eventKey = :eventKey LIMIT 1")
    suspend fun getEventByKey(eventKey: String): XpEventEntity?

    @Query("SELECT COALESCE(SUM(xpAmount), 0) FROM xp_events WHERE dateString = :dateString")
    suspend fun getXpEarnedOnDate(dateString: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertXpEventIgnoreConflict(event: XpEventEntity): Long

    @Query("DELETE FROM xp_events WHERE eventKey = :eventKey")
    suspend fun deleteEventByKey(eventKey: String)

    @Query("DELETE FROM xp_events WHERE eventKey LIKE :prefix || '%'")
    suspend fun deleteEventsWithPrefix(prefix: String)
}
