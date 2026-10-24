package com.example.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "focus_sessions")
data class FocusSessionEntity(
    @PrimaryKey
    val id: Long = 1L,
    val sessionStatus: String = "IDLE", // "IDLE", "RUNNING", "BREAK", "PAUSED", "COMPLETED", "CANCELLED"
    val studyDurationMinutes: Int = 50,
    val permittedBreaks: Int = 2,
    val breaksRemaining: Int = 2,
    val breakDurationMinutes: Int = 5,
    val sessionStartTimeMillis: Long = 0L,
    val targetEndTimeMillis: Long = 0L,
    val breakStartTimeMillis: Long = 0L,
    val breakEndTimeMillis: Long = 0L,
    val pauseStartTimeMillis: Long = 0L,
    val totalPausedDurationMillis: Long = 0L,
    val blockedPackagesCsv: String = "com.instagram.android,com.zhiliaoapp.musically,com.twitter.android,com.facebook.katana,com.google.android.youtube,com.pubg.krmobile",
    val activeTaskName: String = "Deep Work Focus Block"
) {
    val blockedPackageList: List<String>
        get() = blockedPackagesCsv.split(",").map { it.trim() }.filter { it.isNotBlank() }
}
