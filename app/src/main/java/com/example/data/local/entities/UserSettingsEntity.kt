package com.example.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_settings")
data class UserSettingsEntity(
    @PrimaryKey
    val id: Int = 1,
    val dailyLectureTarget: Int = 4,
    val dailyProblemTarget: Int = 25,
    val sleepStartTime: String = "23:00",
    val sleepEndTime: String = "06:30",
    val pomodoroWorkMinutes: Int = 50,
    val pomodoroBreakMinutes: Int = 10,
    val isDimmerActive: Boolean = false,
    val textScalePercent: Int = 100,
    val revisionIntervalsCsv: String = "1,3,7,21",
    val revisionNotificationsEnabled: Boolean = true,
    val studyRemindersEnabled: Boolean = false,
    val studyReminderHour: Int = 21,
    val studyReminderMinute: Int = 0
) {
    val parsedRevisionIntervals: List<Int>
        get() {
            val parsed = revisionIntervalsCsv.split(",")
                .mapNotNull { it.trim().toIntOrNull() }
                .filter { it in 1..180 }
            return if (parsed.isNotEmpty()) parsed else listOf(1, 3, 7, 21)
        }
}
