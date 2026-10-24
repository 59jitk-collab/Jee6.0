package com.example.data.analytics

import com.example.data.local.entities.ChapterEntity
import com.example.data.local.entities.DailyScheduleEntity
import com.example.data.local.entities.MistakeEntryEntity
import com.example.data.local.entities.TestRecordEntity
import com.example.util.DateUtils
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Pure domain models and calculations for Study Analytics and Weekly Reports.
 * Follows zero-invention policy: never fabricates fake scores, counts, or dates.
 */
data class DayStudyPoint(
    val dateStr: String,
    val dayLabel: String,      // e.g. "Mon", "Tue"
    val dayNumber: Int,
    val studyMinutes: Int,
    val targetMinutes: Int = 240, // 4 hours standard benchmark
    val completedLectures: Int,
    val targetLectures: Int,
    val completedProblems: Int,
    val targetProblems: Int,
    val isSunday: Boolean,
    val isTargetMet: Boolean
)

data class WeakChapterDiagnostic(
    val chapterName: String,
    val subject: String,
    val evidenceSnippet: String,
    val mistakeCount: Int,
    val isFromTestRecord: Boolean
)

data class WeeklyReport(
    val weekStartDate: String,
    val weekEndDate: String,
    val hasEnoughData: Boolean,
    val totalStudyMinutes: Int,
    val averageDailyStudyMinutes: Int,
    val daysTrackedCount: Int,
    val daysWithRecordedStudy: Int,
    val targetDaysMetCount: Int,
    val totalLecturesCompleted: Int,
    val totalLecturesPlanned: Int,
    val lectureAdherencePercent: Int,
    val totalProblemsSolved: Int,
    val totalProblemsPlanned: Int,
    val problemAdherencePercent: Int,
    val dailyTrend: List<DayStudyPoint>,
    val syllabusCompletionPercent: Int,
    val averageTestScorePercent: Int?,
    val assessedTestCount: Int,
    val activeMistakesCount: Int,
    val resolvedMistakesCount: Int,
    val weakChapters: List<WeakChapterDiagnostic>,
    val recommendation: String
)

object StudyAnalyticsCalculator {

    private const val DATE_PATTERN = "yyyy-MM-dd"

    private fun parseDate(dateStr: String): Date? {
        return try {
            val sdf = SimpleDateFormat(DATE_PATTERN, Locale.US)
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            sdf.parse(dateStr)
        } catch (_: Exception) {
            null
        }
    }

    private fun formatDate(date: Date): String {
        val sdf = SimpleDateFormat(DATE_PATTERN, Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(date)
    }

    private fun getDayOfWeekLabel(dateStr: String): String {
        val date = parseDate(dateStr) ?: return ""
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.US)
        cal.time = date
        return when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.SUNDAY -> "Sun"
            Calendar.MONDAY -> "Mon"
            Calendar.TUESDAY -> "Tue"
            Calendar.WEDNESDAY -> "Wed"
            Calendar.THURSDAY -> "Thu"
            Calendar.FRIDAY -> "Fri"
            Calendar.SATURDAY -> "Sat"
            else -> ""
        }
    }

    /**
     * Computes the 7-day Weekly Report up to [referenceDateStr] (inclusive).
     * Strictly computes from persisted Room records without fabricating data.
     */
    fun computeWeeklyReport(
        schedules: List<DailyScheduleEntity>,
        chapters: List<ChapterEntity>,
        tests: List<TestRecordEntity>,
        mistakes: List<MistakeEntryEntity>,
        referenceDateStr: String = DateUtils.todayDateString()
    ): WeeklyReport {
        // De-duplicate schedules by date (protecting against duplicate entries)
        val scheduleMap = schedules.groupBy { it.date }.mapValues { (_, list) -> list.first() }

        // Compute 7 days back from referenceDateStr
        val last7Dates = mutableListOf<String>()
        for (i in 6 downTo 0) {
            last7Dates.add(DateUtils.addDays(referenceDateStr, -i))
        }

        val weekStartDate = last7Dates.firstOrNull() ?: referenceDateStr
        val weekEndDate = last7Dates.lastOrNull() ?: referenceDateStr

        val dayPoints = mutableListOf<DayStudyPoint>()
        var totalStudyMinutes = 0
        var totalLecturesCompleted = 0
        var totalLecturesPlanned = 0
        var totalProblemsSolved = 0
        var totalProblemsPlanned = 0
        var daysWithStudy = 0
        var targetDaysMet = 0
        var daysTracked = 0

        for (dateStr in last7Dates) {
            val item = scheduleMap[dateStr]
            if (item != null) {
                daysTracked++
                // Handle paused or interrupted durations: studyMinutes must be non-negative
                val studyMins = item.studyMinutes.coerceAtLeast(0)
                if (studyMins > 0 || item.completedLectures > 0 || item.completedProblems > 0) {
                    daysWithStudy++
                }

                totalStudyMinutes += studyMins
                totalLecturesCompleted += item.completedLectures
                totalLecturesPlanned += item.targetLectures
                totalProblemsSolved += item.completedProblems
                totalProblemsPlanned += item.targetProblems

                if (item.isCompleted || (item.completedLectures >= item.targetLectures && item.completedProblems >= item.targetProblems)) {
                    targetDaysMet++
                }

                dayPoints.add(
                    DayStudyPoint(
                        dateStr = dateStr,
                        dayLabel = getDayOfWeekLabel(dateStr),
                        dayNumber = item.dayNumber,
                        studyMinutes = studyMins,
                        targetMinutes = if (item.isSunday) 120 else 240,
                        completedLectures = item.completedLectures,
                        targetLectures = item.targetLectures,
                        completedProblems = item.completedProblems,
                        targetProblems = item.targetProblems,
                        isSunday = item.isSunday,
                        isTargetMet = item.isCompleted || (item.completedLectures >= item.targetLectures && item.completedProblems >= item.targetProblems)
                    )
                )
            } else {
                // Unscheduled or missing day within window
                dayPoints.add(
                    DayStudyPoint(
                        dateStr = dateStr,
                        dayLabel = getDayOfWeekLabel(dateStr),
                        dayNumber = 0,
                        studyMinutes = 0,
                        targetMinutes = 240,
                        completedLectures = 0,
                        targetLectures = 0,
                        completedProblems = 0,
                        targetProblems = 0,
                        isSunday = DateUtils.isSunday(dateStr),
                        isTargetMet = false
                    )
                )
            }
        }

        val hasEnoughData = daysWithStudy > 0 || totalLecturesCompleted > 0 || totalProblemsSolved > 0
        val avgDailyMins = if (daysWithStudy > 0) totalStudyMinutes / daysWithStudy else 0

        val lectureAdherence = if (totalLecturesPlanned > 0) {
            ((totalLecturesCompleted.toDouble() / totalLecturesPlanned) * 100).toInt().coerceIn(0, 100)
        } else 0

        val problemAdherence = if (totalProblemsPlanned > 0) {
            ((totalProblemsSolved.toDouble() / totalProblemsPlanned) * 100).toInt().coerceIn(0, 100)
        } else 0

        // Syllabus coverage from chapters
        val assessedChapters = chapters.filter { it.isAssessed }
        val syllabusCompletionPercent = if (assessedChapters.isNotEmpty()) {
            assessedChapters.map { it.completionPercentage }.average().toInt().coerceIn(0, 100)
        } else 0

        // Test Mastery: only from assessed tests
        val assessedTests = tests.filter { it.isAssessed && it.totalScore != null }
        val avgTestScorePercent = if (assessedTests.isNotEmpty()) {
            val avg = assessedTests.mapNotNull { it.scorePercentage }.average()
            avg.toInt().coerceIn(0, 100)
        } else null

        // Mistakes breakdown
        val activeMistakes = mistakes.count { !it.isResolved }
        val resolvedMistakes = mistakes.count { it.isResolved }

        // Empirical Weak Chapter detection:
        // Rule: Only flag as weak if there is concrete evidence:
        // 1. Chapter has >= 2 mistake entries logged, OR
        // 2. Chapter is explicitly listed in weakTopics of an assessed test, OR
        // 3. Chapter is marked isWeak AND has non-zero attempted questions.
        val weakDiagnostics = mutableListOf<WeakChapterDiagnostic>()
        val mistakeGroup = mistakes.groupBy { it.chapterName.trim().lowercase() }
        val testWeakList = assessedTests.flatMap { test ->
            test.weakTopics.split(",").map { it.trim().lowercase() }
        }.filter { it.isNotBlank() }.toSet()

        for (chapter in chapters) {
            val key = chapter.chapterName.trim().lowercase()
            val chapterMistakes = mistakeGroup[key] ?: emptyList()
            val isMentionedInTests = testWeakList.contains(key)

            if (chapterMistakes.size >= 2) {
                weakDiagnostics.add(
                    WeakChapterDiagnostic(
                        chapterName = chapter.chapterName,
                        subject = chapter.subject,
                        evidenceSnippet = "${chapterMistakes.size} mistakes logged (${chapterMistakes.firstOrNull()?.errorType ?: "Review needed"})",
                        mistakeCount = chapterMistakes.size,
                        isFromTestRecord = isMentionedInTests
                    )
                )
            } else if (isMentionedInTests) {
                weakDiagnostics.add(
                    WeakChapterDiagnostic(
                        chapterName = chapter.chapterName,
                        subject = chapter.subject,
                        evidenceSnippet = "Flagged during Sunday Test diagnostic review",
                        mistakeCount = chapterMistakes.size,
                        isFromTestRecord = true
                    )
                )
            } else if (chapter.isWeak && (chapter.dppDone ?: 0) > 0) {
                weakDiagnostics.add(
                    WeakChapterDiagnostic(
                        chapterName = chapter.chapterName,
                        subject = chapter.subject,
                        evidenceSnippet = "Practice accuracy below threshold in DPP solving",
                        mistakeCount = chapterMistakes.size,
                        isFromTestRecord = false
                    )
                )
            }
        }

        // Objective, non-judgmental recommendation based strictly on data
        val recommendation = when {
            !hasEnoughData -> "Not enough data yet. Complete daily lecture and problem targets to generate your personalized weekly report."
            lectureAdherence >= 80 && problemAdherence >= 80 ->
                "Excellent consistency this week with ${totalLecturesCompleted} lectures and ${totalProblemsSolved} problems solved. Maintain current pacing into Sunday Test."
            lectureAdherence >= 80 && problemAdherence < 60 ->
                "Lecture completion is on track (${lectureAdherence}%), but problem solving is lagging (${problemAdherence}%). Prioritize scheduled DPPs before opening new lecture blocks."
            lectureAdherence < 60 && problemAdherence >= 80 ->
                "Strong question practice (${problemAdherence}%), but lecture targets fell behind (${lectureAdherence}%). Consider rebalancing lectures using the 80-Day Chrono Scheduler."
            totalStudyMinutes < 600 ->
                "Recorded study time is below weekly benchmark (${totalStudyMinutes / 60}h logged). Protect a fixed 3-hour evening block for focused preparation."
            weakDiagnostics.isNotEmpty() ->
                "Identified ${weakDiagnostics.size} chapter(s) with recurring errors (${weakDiagnostics.first().chapterName}). Schedule a 45-minute revision block before next Sunday test."
            else ->
                "Pacing is steady across ${daysWithStudy} active days. Keep daily study duration consistent and review notebook mistakes prior to Sunday tests."
        }

        return WeeklyReport(
            weekStartDate = weekStartDate,
            weekEndDate = weekEndDate,
            hasEnoughData = hasEnoughData,
            totalStudyMinutes = totalStudyMinutes,
            averageDailyStudyMinutes = avgDailyMins,
            daysTrackedCount = daysTracked,
            daysWithRecordedStudy = daysWithStudy,
            targetDaysMetCount = targetDaysMet,
            totalLecturesCompleted = totalLecturesCompleted,
            totalLecturesPlanned = totalLecturesPlanned,
            lectureAdherencePercent = lectureAdherence,
            totalProblemsSolved = totalProblemsSolved,
            totalProblemsPlanned = totalProblemsPlanned,
            problemAdherencePercent = problemAdherence,
            dailyTrend = dayPoints,
            syllabusCompletionPercent = syllabusCompletionPercent,
            averageTestScorePercent = avgTestScorePercent,
            assessedTestCount = assessedTests.size,
            activeMistakesCount = activeMistakes,
            resolvedMistakesCount = resolvedMistakes,
            weakChapters = weakDiagnostics,
            recommendation = recommendation
        )
    }
}
