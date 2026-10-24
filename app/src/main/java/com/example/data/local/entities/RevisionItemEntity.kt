package com.example.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "revision_items")
data class RevisionItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val chapterId: Long,
    val chapterName: String,
    val subject: String,
    val stage: Int = 1, // 1 (interval 1), 2 (interval 2), 3 (interval 3), 4 (interval 4)
    val dueDate: String, // YYYY-MM-DD
    val lastRevisedDate: String = "",
    val isCompleted: Boolean = false,
    val snoozedUntilDate: String = "",
    val notes: String = ""
) {
    val effectiveDueDate: String
        get() = if (snoozedUntilDate.isNotBlank() && snoozedUntilDate > dueDate) snoozedUntilDate else dueDate
}
