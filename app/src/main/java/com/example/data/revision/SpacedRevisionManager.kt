package com.example.data.revision

import com.example.data.local.entities.ChapterEntity
import com.example.data.local.entities.RevisionItemEntity
import com.example.util.DateUtils

enum class RevisionBucket {
    OVERDUE,
    DUE_TODAY,
    UPCOMING,
    COMPLETED
}

data class CategorizedRevisions(
    val overdue: List<RevisionItemEntity>,
    val dueToday: List<RevisionItemEntity>,
    val upcoming: List<RevisionItemEntity>,
    val completed: List<RevisionItemEntity>
) {
    val totalActionable: Int get() = overdue.size + dueToday.size
}

object SpacedRevisionManager {

    val DEFAULT_INTERVALS = listOf(1, 3, 7, 21)

    fun parseIntervals(csv: String?): List<Int> {
        if (csv.isNullOrBlank()) return DEFAULT_INTERVALS
        val parsed = csv.split(",")
            .mapNotNull { it.trim().toIntOrNull() }
            .filter { it in 1..180 }
            .distinct()
        return if (parsed.isNotEmpty()) parsed else DEFAULT_INTERVALS
    }

    fun getBucketForRevision(item: RevisionItemEntity, todayDateStr: String): RevisionBucket {
        if (item.isCompleted) return RevisionBucket.COMPLETED
        val effectiveDue = item.effectiveDueDate
        return when {
            effectiveDue < todayDateStr -> RevisionBucket.OVERDUE
            effectiveDue == todayDateStr -> RevisionBucket.DUE_TODAY
            else -> RevisionBucket.UPCOMING
        }
    }

    fun categorize(
        items: List<RevisionItemEntity>,
        todayDateStr: String = DateUtils.todayDateString()
    ): CategorizedRevisions {
        val overdue = mutableListOf<RevisionItemEntity>()
        val dueToday = mutableListOf<RevisionItemEntity>()
        val upcoming = mutableListOf<RevisionItemEntity>()
        val completed = mutableListOf<RevisionItemEntity>()

        // Deduplicate active items by chapterId + chapterName if any legacy duplicates exist
        val seenActiveKeys = mutableSetOf<String>()
        for (item in items) {
            if (!item.isCompleted) {
                val key = "${item.chapterId}:${item.chapterName.lowercase()}"
                if (!seenActiveKeys.add(key)) continue
            }
            when (getBucketForRevision(item, todayDateStr)) {
                RevisionBucket.OVERDUE -> overdue.add(item)
                RevisionBucket.DUE_TODAY -> dueToday.add(item)
                RevisionBucket.UPCOMING -> upcoming.add(item)
                RevisionBucket.COMPLETED -> completed.add(item)
            }
        }

        return CategorizedRevisions(
            overdue = overdue.sortedBy { it.effectiveDueDate },
            dueToday = dueToday.sortedBy { it.stage },
            upcoming = upcoming.sortedBy { it.effectiveDueDate },
            completed = completed.sortedByDescending { it.lastRevisedDate }
        )
    }

    /**
     * Creates or updates the initial Stage 1 revision item when a chapter is marked studied.
     */
    fun createOrResetRevisionForChapter(
        chapter: ChapterEntity,
        existingActive: RevisionItemEntity?,
        intervals: List<Int> = DEFAULT_INTERVALS,
        todayDateStr: String = DateUtils.todayDateString()
    ): RevisionItemEntity {
        val firstIntervalDays = intervals.firstOrNull() ?: 1
        val nextDue = DateUtils.addDays(todayDateStr, firstIntervalDays)
        return if (existingActive != null) {
            existingActive.copy(
                chapterName = chapter.chapterName,
                subject = chapter.subject,
                stage = 1,
                dueDate = nextDue,
                snoozedUntilDate = "",
                lastRevisedDate = todayDateStr,
                isCompleted = false
            )
        } else {
            RevisionItemEntity(
                chapterId = chapter.id,
                chapterName = chapter.chapterName,
                subject = chapter.subject,
                stage = 1,
                dueDate = nextDue,
                lastRevisedDate = todayDateStr,
                isCompleted = false,
                snoozedUntilDate = "",
                notes = "Spaced revision cycle (${intervals.joinToString("/")}d)"
            )
        }
    }

    /**
     * Advances a revision item to the next interval stage, or marks it completed if it finished the final stage.
     */
    fun advanceStage(
        item: RevisionItemEntity,
        intervals: List<Int> = DEFAULT_INTERVALS,
        todayDateStr: String = DateUtils.todayDateString()
    ): RevisionItemEntity {
        val safeIntervals = if (intervals.isNotEmpty()) intervals else DEFAULT_INTERVALS
        val nextStageIndex = item.stage // 1-based stage -> next index in 0-based list
        return if (nextStageIndex < safeIntervals.size) {
            val daysToAdd = safeIntervals[nextStageIndex]
            val nextDue = DateUtils.addDays(todayDateStr, daysToAdd)
            item.copy(
                stage = item.stage + 1,
                dueDate = nextDue,
                snoozedUntilDate = "",
                lastRevisedDate = todayDateStr,
                isCompleted = false
            )
        } else {
            item.copy(
                lastRevisedDate = todayDateStr,
                snoozedUntilDate = "",
                isCompleted = true
            )
        }
    }

    fun snoozeRevision(
        item: RevisionItemEntity,
        daysToSnooze: Int,
        todayDateStr: String = DateUtils.todayDateString()
    ): RevisionItemEntity {
        val baseDate = if (item.effectiveDueDate > todayDateStr) item.effectiveDueDate else todayDateStr
        val snoozedDate = DateUtils.addDays(baseDate, daysToSnooze.coerceIn(1, 14))
        return item.copy(snoozedUntilDate = snoozedDate)
    }

    fun rescheduleRevision(
        item: RevisionItemEntity,
        newDueDateStr: String
    ): RevisionItemEntity {
        return item.copy(
            dueDate = newDueDateStr,
            snoozedUntilDate = ""
        )
    }
}
