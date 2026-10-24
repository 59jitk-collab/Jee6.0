package com.example.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class MasteryLevel(val dbKey: String, val displayLabel: String) {
    NOT_ASSESSED("NOT_ASSESSED", "Not Assessed"),
    LEARNING("LEARNING", "Learning"),
    PRACTICING("PRACTICING", "Practicing"),
    CONFIDENT("CONFIDENT", "Confident"),
    NEEDS_REVISION("NEEDS_REVISION", "Needs Revision");

    companion object {
        fun fromKey(key: String?): MasteryLevel {
            return entries.find { it.dbKey.equals(key, ignoreCase = true) } ?: NOT_ASSESSED
        }
    }
}

@Entity(tableName = "chapters")
data class ChapterEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val subject: String, // "Physics", "Chemistry", "Mathematics"
    val chapterName: String,
    val orderIndex: Int = 0,
    val totalLectures: Int? = null, // null means "Not assessed" / pending count
    val completedLectures: Int = 0,
    val notesDone: Boolean = false,
    val dibyTotal: Int? = null,
    val dibyDone: Int? = null,
    val classProblemsTotal: Int? = null,
    val classProblemsDone: Int? = null,
    val dppTotal: Int? = null,
    val dppDone: Int? = null,
    val pyqTotal: Int? = null,
    val pyqDone: Int? = null,
    val worksheetsTotal: Int? = null,
    val worksheetsDone: Int? = null,
    val status: String = "NOT_ASSESSED", // "NOT_ASSESSED", "NOT_STARTED", "IN_PROGRESS", "COMPLETED", "WEAK"
    val isWeak: Boolean = false,
    val lastUpdated: Long = System.currentTimeMillis(),
    // Stage 2 & 3 additions (preserved across migrations with safe defaults)
    val masteryLevel: String = "NOT_ASSESSED", // Independent of task completion!
    val lastStudiedDate: String = "", // YYYY-MM-DD
    val revisionStage: Int = 0, // 0 = none, 1..N = active stage
    val nextRevisionDate: String = "", // YYYY-MM-DD
    // Stage 6: Two-Way Google Sheets Sync Metadata (preserved via MIGRATION_4_5)
    val syncId: String = "",
    val schemaVersion: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
    val lastSyncedAt: Long = 0L,
    val remoteVersionHash: String = "",
    val lastSyncedBaselineJson: String = "",
    val originDeviceId: String = "LOCAL",
    val syncState: String = "LOCAL_ONLY", // "LOCAL_ONLY", "PENDING", "SYNCED", "CONFLICT", "ERROR"
    val isDeleted: Boolean = false
) {
    val effectiveSyncId: String
        get() = syncId.ifBlank {
            if (id > 0L) {
                SyncIdentityHelper.deterministicChapterBackfillId(id, subject, chapterName, orderIndex)
            } else {
                SyncIdentityHelper.deterministicImportedChapterId(subject, chapterName, orderIndex)
            }
        }

    fun computeContentHash(): String {
        val canonical = buildString {
            append(subject.trim()).append('|')
            append(chapterName.trim()).append('|')
            append(orderIndex).append('|')
            append(totalLectures?.toString() ?: "NULL").append('|')
            append(completedLectures).append('|')
            append(notesDone).append('|')
            append(dibyTotal?.toString() ?: "NULL").append('|')
            append(dibyDone?.toString() ?: "NULL").append('|')
            append(classProblemsTotal?.toString() ?: "NULL").append('|')
            append(classProblemsDone?.toString() ?: "NULL").append('|')
            append(dppTotal?.toString() ?: "NULL").append('|')
            append(dppDone?.toString() ?: "NULL").append('|')
            append(pyqTotal?.toString() ?: "NULL").append('|')
            append(pyqDone?.toString() ?: "NULL").append('|')
            append(worksheetsTotal?.toString() ?: "NULL").append('|')
            append(worksheetsDone?.toString() ?: "NULL").append('|')
            append(status).append('|')
            append(masteryLevel).append('|')
            append(isWeak).append('|')
            append(lastStudiedDate).append('|')
            append(revisionStage).append('|')
            append(nextRevisionDate).append('|')
            append(isDeleted)
        }
        return SyncIdentityHelper.sha256Short(canonical)
    }

    val isAssessed: Boolean
        get() = totalLectures != null || dibyTotal != null || dppTotal != null || pyqTotal != null || worksheetsTotal != null || classProblemsTotal != null

    val resolvedMastery: MasteryLevel
        get() = MasteryLevel.fromKey(masteryLevel)

    val completionPercentage: Int
        get() {
            if (!isAssessed) return 0
            var totalTasks = 0
            var doneTasks = 0

            totalLectures?.let { total ->
                if (total > 0) {
                    totalTasks += total
                    doneTasks += completedLectures.coerceAtMost(total)
                }
            }
            if (notesDone) {
                totalTasks += 1
                doneTasks += 1
            } else if (totalLectures != null && totalLectures > 0) {
                totalTasks += 1
            }

            dppTotal?.let {
                if (it > 0) {
                    totalTasks += it
                    doneTasks += (dppDone ?: 0).coerceAtMost(it)
                }
            }
            pyqTotal?.let {
                if (it > 0) {
                    totalTasks += it
                    doneTasks += (pyqDone ?: 0).coerceAtMost(it)
                }
            }
            dibyTotal?.let {
                if (it > 0) {
                    totalTasks += it
                    doneTasks += (dibyDone ?: 0).coerceAtMost(it)
                }
            }
            classProblemsTotal?.let {
                if (it > 0) {
                    totalTasks += it
                    doneTasks += (classProblemsDone ?: 0).coerceAtMost(it)
                }
            }
            worksheetsTotal?.let {
                if (it > 0) {
                    totalTasks += it
                    doneTasks += (worksheetsDone ?: 0).coerceAtMost(it)
                }
            }

            return if (totalTasks > 0) ((doneTasks.toDouble() / totalTasks) * 100).toInt().coerceIn(0, 100) else 0
        }
}
