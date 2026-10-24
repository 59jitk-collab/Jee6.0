package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entities.TestRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TestRecordDao {
    @Query("SELECT * FROM test_records ORDER BY testDate ASC")
    fun getAllTests(): Flow<List<TestRecordEntity>>

    @Query("SELECT * FROM test_records WHERE isAssessed = 1 ORDER BY testDate DESC")
    fun getCompletedTests(): Flow<List<TestRecordEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTest(test: TestRecordEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tests: List<TestRecordEntity>)

    @Update
    suspend fun updateTest(test: TestRecordEntity)

    @Query("DELETE FROM test_records WHERE id = :id")
    suspend fun deleteTest(id: Long)

    @Query("DELETE FROM test_records")
    suspend fun clearAllTests()

    @Query("SELECT COUNT(*) FROM test_records")
    suspend fun getTestCount(): Int
}
