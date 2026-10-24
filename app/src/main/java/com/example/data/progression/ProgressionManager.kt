package com.example.data.progression

import com.example.data.local.entities.DailyScheduleEntity
import com.example.data.local.entities.XpEventEntity
import com.example.util.DateUtils

data class ProgressionStatus(
    val totalXp: Int = 0,
    val currentLevel: Int = 1,
    val levelTitle: String = "Foundation Cadet • Tier I",
    val xpInCurrentLevel: Int = 0,
    val xpRequiredForNextLevel: Int = 150,
    val todayXpEarned: Int = 0,
    val dailyXpCap: Int = 300,
    val currentStreakDays: Int = 0,
    val longestStreakDays: Int = 0,
    val isActiveToday: Boolean = false,
    val recentEvents: List<XpEventEntity> = emptyList(),
    val healthReminderNote: String = ProgressionManager.HEALTH_REMINDER
) {
    val levelProgressFraction: Float
        get() = (xpInCurrentLevel.toFloat() / xpRequiredForNextLevel.coerceAtLeast(1)).coerceIn(0f, 1f)
}

object ProgressionManager {

    // Transparent, single-source XP configuration
    const val XP_LECTURE_COMPLETED = 15
    const val XP_NOTES_COMPLETED = 10
    const val XP_DPP_COMPLETED = 10
    const val XP_PYQ_COMPLETED = 5
    const val XP_DIBY_COMPLETED = 5
    const val XP_WORKSHEET_COMPLETED = 10
    const val XP_CLASS_PROBLEM_COMPLETED = 5
    const val XP_FOCUS_SESSION_COMPLETED = 25
    const val XP_REVISION_COMPLETED = 20

    const val XP_PER_LEVEL = 150
    const val MAX_DAILY_XP = 300

    const val HEALTH_REMINDER =
        "Consistency beats intensity. Protect your 7.5h sleep window, meals, and rest days—never sacrifice health for a streak."

    fun lectureEventKey(chapterId: Long, lectureIndex: Int): String = "LEC_${chapterId}_$lectureIndex"
    fun notesEventKey(chapterId: Long): String = "NOTES_$chapterId"
    fun dppEventKey(chapterId: Long, dppIndex: Int): String = "DPP_${chapterId}_$dppIndex"
    fun pyqEventKey(chapterId: Long, pyqIndex: Int): String = "PYQ_${chapterId}_$pyqIndex"
    fun dibyEventKey(chapterId: Long, dibyIndex: Int): String = "DIBY_${chapterId}_$dibyIndex"
    fun worksheetEventKey(chapterId: Long, wsIndex: Int): String = "WS_${chapterId}_$wsIndex"
    fun classProblemEventKey(chapterId: Long, cpIndex: Int): String = "CP_${chapterId}_$cpIndex"
    fun focusSessionEventKey(sessionStartMillis: Long): String = "FOCUS_$sessionStartMillis"
    fun revisionEventKey(revisionId: Long, stage: Int, dateStr: String): String = "REV_${revisionId}_S${stage}_$dateStr"

    /**
     * Checks how much XP can still be awarded on [dateStr] under the daily anti-farming cap.
     */
    fun clampAwardToDailyCap(requestedXp: Int, alreadyEarnedToday: Int): Int {
        if (requestedXp <= 0) return 0
        val remainingAllowance = (MAX_DAILY_XP - alreadyEarnedToday).coerceAtLeast(0)
        return requestedXp.coerceAtMost(remainingAllowance)
    }

    fun getLevelTitle(level: Int): String {
        return when {
            level >= 15 -> "Scholar • Tier V"
            level >= 10 -> "Problem Solver • Tier IV"
            level >= 6 -> "Focused Learner • Tier III"
            level >= 3 -> "Consistent Builder • Tier II"
            else -> "Foundation Cadet • Tier I"
        }
    }

    /**
     * Computes private progression status and local-date study streak strictly from persisted records.
     */
    fun computeProgressionStatus(
        xpEvents: List<XpEventEntity>,
        schedules: List<DailyScheduleEntity>,
        todayDateStr: String = DateUtils.todayDateString()
    ): ProgressionStatus {
        val totalXp = xpEvents.sumOf { it.xpAmount.coerceAtLeast(0) }
        val currentLevel = (totalXp / XP_PER_LEVEL) + 1
        val xpInLevel = totalXp % XP_PER_LEVEL
        val todayXp = xpEvents
            .filter { it.dateString == todayDateStr }
            .sumOf { it.xpAmount.coerceAtLeast(0) }

        // Qualifying local dates from either verifiable XP events or recorded daily schedule activity
        val qualifyingDates = mutableSetOf<String>()
        xpEvents.forEach { if (it.xpAmount > 0 && it.dateString.isNotBlank()) qualifyingDates.add(it.dateString) }
        schedules.forEach { s ->
            if (s.studyMinutes > 0 || s.completedLectures > 0 || s.completedProblems > 0 || s.notesReviewed) {
                qualifyingDates.add(s.date)
            }
        }

        val (currentStreak, longestStreak) = calculateStreaks(qualifyingDates, todayDateStr)

        return ProgressionStatus(
            totalXp = totalXp,
            currentLevel = currentLevel,
            levelTitle = getLevelTitle(currentLevel),
            xpInCurrentLevel = xpInLevel,
            xpRequiredForNextLevel = XP_PER_LEVEL,
            todayXpEarned = todayXp,
            dailyXpCap = MAX_DAILY_XP,
            currentStreakDays = currentStreak,
            longestStreakDays = longestStreak,
            isActiveToday = qualifyingDates.contains(todayDateStr),
            recentEvents = xpEvents.take(15),
            healthReminderNote = HEALTH_REMINDER
        )
    }

    /**
     * Calculates current and longest streak using local YYYY-MM-DD dates.
     * If today is not yet active, checks whether yesterday was active so the streak isn't lost mid-day.
     */
    fun calculateStreaks(
        qualifyingDates: Set<String>,
        todayDateStr: String
    ): Pair<Int, Int> {
        if (qualifyingDates.isEmpty()) return Pair(0, 0)

        val sortedDates = qualifyingDates.sorted()
        var longest = 1
        var running = 1
        for (i in 1 until sortedDates.size) {
            val prev = sortedDates[i - 1]
            val curr = sortedDates[i]
            if (DateUtils.addDays(prev, 1) == curr) {
                running++
                if (running > longest) longest = running
            } else if (prev != curr) {
                running = 1
            }
        }

        // Current streak ending today or yesterday
        val yesterdayStr = DateUtils.addDays(todayDateStr, -1)
        var probeDate = when {
            qualifyingDates.contains(todayDateStr) -> todayDateStr
            qualifyingDates.contains(yesterdayStr) -> yesterdayStr
            else -> return Pair(0, longest)
        }

        var currentStreak = 0
        while (qualifyingDates.contains(probeDate)) {
            currentStreak++
            probeDate = DateUtils.addDays(probeDate, -1)
        }

        return Pair(currentStreak, maxOf(longest, currentStreak))
    }
}
