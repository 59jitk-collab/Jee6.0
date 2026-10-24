package com.example.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "test_records")
data class TestRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val testName: String,
    val testDate: String, // YYYY-MM-DD
    val physicsMarks: Int? = null,
    val chemistryMarks: Int? = null,
    val mathMarks: Int? = null,
    val totalMarks: Int = 300,
    val negativeMarks: Int = 0,
    val weakTopics: String = "", // Comma-separated chapter names
    val analysisNotes: String = "",
    val isAssessed: Boolean = false // Only true if the student entered real marks
) {
    val totalScore: Int?
        get() = if (physicsMarks != null || chemistryMarks != null || mathMarks != null) {
            (physicsMarks ?: 0) + (chemistryMarks ?: 0) + (mathMarks ?: 0)
        } else null

    val scorePercentage: Float?
        get() = totalScore?.let { (it.toFloat() / totalMarks.coerceAtLeast(1)) * 100f }
}
