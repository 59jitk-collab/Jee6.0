package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entities.DailyScheduleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyScheduleDao {
    @Query("SELECT * FROM daily_schedules ORDER BY dayNumber ASC")
    fun getAllDays(): Flow<List<DailyScheduleEntity>>

    @Query("SELECT * FROM daily_schedules ORDER BY dayNumber ASC")
    suspend fun getAllDaysSync(): List<DailyScheduleEntity>

    @Query("SELECT * FROM daily_schedules WHERE date = :date LIMIT 1")
    fun getScheduleForDate(date: String): Flow<DailyScheduleEntity?>

    @Query("SELECT * FROM daily_schedules WHERE date = :date LIMIT 1")
    suspend fun getScheduleForDateSync(date: String): DailyScheduleEntity?

    @Query("SELECT * FROM daily_schedules WHERE dayNumber = :dayNumber LIMIT 1")
    suspend fun getScheduleForDayNumber(dayNumber: Int): DailyScheduleEntity?

    @Query("SELECT * FROM daily_schedules WHERE syncId = :syncId LIMIT 1")
    suspend fun getScheduleBySyncId(syncId: String): DailyScheduleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(schedules: List<DailyScheduleEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(schedule: DailyScheduleEntity)

    @Update
    suspend fun updateSchedule(schedule: DailyScheduleEntity)

    @Query("SELECT COUNT(*) FROM daily_schedules")
    suspend fun getScheduleCount(): Int

    @Query("SELECT SUM(completedLectures) FROM daily_schedules")
    fun getTotalCompletedLectures(): Flow<Int?>

    @Query("SELECT SUM(completedProblems) FROM daily_schedules")
    fun getTotalCompletedProblems(): Flow<Int?>
}
