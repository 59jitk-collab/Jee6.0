package com.example.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

data class DailyPlanTaskItem(
    val id: String,
    val categoryBadge: String,
    val title: String,
    val subtitle: String,
    val progressLabel: String,
    val isCompleted: Boolean,
    val actionType: PlanTaskActionType
)

enum class PlanTaskActionType {
    INCREMENT_LECTURE,
    INCREMENT_PROBLEMS,
    TOGGLE_NOTES
}

@Entity(tableName = "daily_schedules")
data class DailyScheduleEntity(
    @PrimaryKey
    val date: String, // Format: YYYY-MM-DD
    val dayNumber: Int, // 1 to 80
    val targetLectures: Int = 4, // 4 lectures benchmark
    val completedLectures: Int = 0,
    val targetProblems: Int = 25,
    val completedProblems: Int = 0,
    val notesReviewed: Boolean = false,
    val dibySolved: Int = 0,
    val pyqSolved: Int = 0,
    val studyMinutes: Int = 0,
    val breakMinutes: Int = 0,
    val sleepHours: Float = 7.5f,
    val isSunday: Boolean = false,
    val isRestDay: Boolean = false,
    val assignedChapterNames: String = "", // Comma-separated user-assigned chapters
    val notes: String = "",
    val isCompleted: Boolean = false,
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
        get() = syncId.ifBlank { SyncIdentityHelper.scheduleSyncId(date) }

    fun computeContentHash(): String {
        val canonical = buildString {
            append(date.trim()).append('|')
            append(dayNumber).append('|')
            append(targetLectures).append('|')
            append(completedLectures).append('|')
            append(targetProblems).append('|')
            append(completedProblems).append('|')
            append(notesReviewed).append('|')
            append(dibySolved).append('|')
            append(pyqSolved).append('|')
            append(studyMinutes).append('|')
            append(breakMinutes).append('|')
            append(sleepHours).append('|')
            append(isSunday).append('|')
            append(isRestDay).append('|')
            append(assignedChapterNames.trim()).append('|')
            append(notes.trim()).append('|')
            append(isCompleted).append('|')
            append(isDeleted)
        }
        return SyncIdentityHelper.sha256Short(canonical)
    }

    val isTargetMet: Boolean
        get() = completedLectures >= targetLectures && completedProblems >= targetProblems

    val parsedAssignedChapters: List<String>
        get() = assignedChapterNames
            .split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }

    /**
     * Single source of truth for actionable study tasks on this schedule day.
     * Used identically by both DashboardScreen ("Today's Plan") and SchedulerScreen ("80-Day Schedule").
     * Never fabricates hardcoded placeholder chapters when assignedChapterNames is empty.
     */
    fun buildSynchronizedPlanTasks(): List<DailyPlanTaskItem> {
        val assigned = parsedAssignedChapters
        val tasks = mutableListOf<DailyPlanTaskItem>()

        if (isSunday) {
            tasks.add(
                DailyPlanTaskItem(
                    id = "${date}_sunday_lecture",
                    categoryBadge = "Sunday Test & Review",
                    title = if (assigned.isNotEmpty()) assigned.first() else "Sunday Mock Test & Mistake Audit",
                    subtitle = notes.ifBlank { "Complete 3-hour mock test + post-test mistake analysis" },
                    progressLabel = "$completedLectures/$targetLectures Block",
                    isCompleted = completedLectures >= targetLectures && targetLectures > 0,
                    actionType = PlanTaskActionType.INCREMENT_LECTURE
                )
            )
        } else if (assigned.isNotEmpty()) {
            // Render user-assigned chapters mapped against the day's lecture target
            assigned.forEachIndexed { idx, chapterLabel ->
                val requiredLec = (idx + 1).coerceAtMost(targetLectures.coerceAtLeast(1))
                val isDone = completedLectures >= requiredLec
                val subjectTag = when {
                    chapterLabel.startsWith("Physics", ignoreCase = true) -> "Physics"
                    chapterLabel.startsWith("Chemistry", ignoreCase = true) -> "Chemistry"
                    chapterLabel.startsWith("Math", ignoreCase = true) -> "Mathematics"
                    else -> "Assigned Chapter"
                }
                tasks.add(
                    DailyPlanTaskItem(
                        id = "${date}_assigned_$idx",
                        categoryBadge = subjectTag,
                        title = chapterLabel,
                        subtitle = "Day $dayNumber Assigned Lecture Block ($completedLectures/$targetLectures done)",
                        progressLabel = "$completedLectures/$targetLectures Lec",
                        isCompleted = isDone,
                        actionType = PlanTaskActionType.INCREMENT_LECTURE
                    )
                )
            }
        } else {
            // No chapters assigned yet: show truthful Day Lecture Target without fake chapter names
            tasks.add(
                DailyPlanTaskItem(
                    id = "${date}_lectures",
                    categoryBadge = "Lectures",
                    title = "Day $dayNumber Lecture Target ($completedLectures/$targetLectures)",
                    subtitle = "No specific chapters assigned yet • Tap +1 Lec as you complete lectures",
                    progressLabel = "$completedLectures/$targetLectures Lec",
                    isCompleted = completedLectures >= targetLectures && targetLectures > 0,
                    actionType = PlanTaskActionType.INCREMENT_LECTURE
                )
            )
        }

        // Problem Solving Target Task
        tasks.add(
            DailyPlanTaskItem(
                id = "${date}_problems",
                categoryBadge = "Problem Drill",
                title = "Daily DPP & PYQ Practice ($completedProblems/$targetProblems)",
                subtitle = if (assigned.isNotEmpty()) {
                    "Solve problems for ${assigned.joinToString(", ")}"
                } else {
                    "Target: $targetProblems solved problems for Day $dayNumber"
                },
                progressLabel = "$completedProblems/$targetProblems Qs",
                isCompleted = completedProblems >= targetProblems && targetProblems > 0,
                actionType = PlanTaskActionType.INCREMENT_PROBLEMS
            )
        )

        // Notes & Formula Revision Task
        tasks.add(
            DailyPlanTaskItem(
                id = "${date}_notes",
                categoryBadge = "Revision",
                title = "Class Notes & Formula Consolidation",
                subtitle = if (notesReviewed) "Completed for Day $dayNumber ($date)" else "Review today's lecture notes & key formulas",
                progressLabel = if (notesReviewed) "Reviewed" else "Pending",
                isCompleted = notesReviewed,
                actionType = PlanTaskActionType.TOGGLE_NOTES
            )
        )

        return tasks
    }
}
