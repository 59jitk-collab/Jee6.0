package com.example.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "mistake_entries")
data class MistakeEntryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val subject: String,
    val chapterName: String,
    val testName: String = "Sunday Test Analysis", // Associated Sunday Test or DPP session
    val questionSnippet: String,
    val errorType: String, // "Concept Gap", "Calculation Error", "Time Pressure", "Misread Question", "Formula Forgot", "Trap Option"
    val conceptMissed: String = "", // Specific concept/theorem missed
    val correctTakeaway: String = "", // Solution takeaway
    val lessonLearned: String = "", // Actionable lesson learned for next Sunday test
    val solutionNotes: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val reviewCount: Int = 0,
    val isResolved: Boolean = false,
    // Stage 6: Two-Way Google Sheets Sync Metadata (preserved via MIGRATION_4_5)
    val syncId: String = "",
    val schemaVersion: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val lastSyncedAt: Long = 0L,
    val remoteVersionHash: String = "",
    val lastSyncedBaselineJson: String = "",
    val originDeviceId: String = "LOCAL",
    val syncState: String = "LOCAL_ONLY",
    val isDeleted: Boolean = false
) {
    val effectiveSyncId: String
        get() = syncId.ifBlank {
            if (id > 0L) {
                SyncIdentityHelper.deterministicMistakeBackfillId(id, subject, chapterName, timestamp)
            } else {
                SyncIdentityHelper.newUuid("mk")
            }
        }

    fun computeContentHash(): String {
        val canonical = buildString {
            append(subject.trim()).append('|')
            append(chapterName.trim()).append('|')
            append(testName.trim()).append('|')
            append(questionSnippet.trim()).append('|')
            append(errorType.trim()).append('|')
            append(conceptMissed.trim()).append('|')
            append(correctTakeaway.trim()).append('|')
            append(lessonLearned.trim()).append('|')
            append(solutionNotes.trim()).append('|')
            append(reviewCount).append('|')
            append(isResolved).append('|')
            append(isDeleted)
        }
        return SyncIdentityHelper.sha256Short(canonical)
    }
}
