package com.example.analytics

import com.example.data.analytics.StudyAnalyticsCalculator
import com.example.data.local.entities.ChapterEntity
import com.example.data.local.entities.DailyScheduleEntity
import com.example.data.local.entities.MistakeEntryEntity
import com.example.data.local.entities.TestRecordEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyAnalyticsCalculatorTest {

    @Test
    fun testEmptyDataReturnsCleanEmptyStateWithoutFabrication() {
        val report = StudyAnalyticsCalculator.computeWeeklyReport(
            schedules = emptyList(),
            chapters = emptyList(),
            tests = emptyList(),
            mistakes = emptyList(),
            referenceDateStr = "2026-10-23"
        )

        assertFalse("Should report not enough data when records are empty", report.hasEnoughData)
        assertEquals(0, report.totalStudyMinutes)
        assertEquals(0, report.averageDailyStudyMinutes)
        assertEquals(0, report.daysWithRecordedStudy)
        assertEquals(0, report.totalLecturesCompleted)
        assertEquals(0, report.totalProblemsSolved)
        assertEquals(0, report.syllabusCompletionPercent)
        assertNull("Test score should be null when no assessed tests exist", report.averageTestScorePercent)
        assertTrue(report.weakChapters.isEmpty())
        assertTrue(report.recommendation.contains("Not enough data yet"))
    }

    @Test
    fun testWeeklyAggregationWithin7DayDateBoundaries() {
        val refDate = "2026-10-25"
        val schedules = listOf(
            // Inside 7-day window (2026-10-19 to 2026-10-25)
            DailyScheduleEntity(date = "2026-10-25", dayNumber = 25, studyMinutes = 180, completedLectures = 3, targetLectures = 4, completedProblems = 20, targetProblems = 25),
            DailyScheduleEntity(date = "2026-10-24", dayNumber = 24, studyMinutes = 240, completedLectures = 4, targetLectures = 4, completedProblems = 25, targetProblems = 25),
            DailyScheduleEntity(date = "2026-10-23", dayNumber = 23, studyMinutes = 120, completedLectures = 2, targetLectures = 4, completedProblems = 15, targetProblems = 25),
            // Outside 7-day window (older than 7 days)
            DailyScheduleEntity(date = "2026-10-10", dayNumber = 10, studyMinutes = 300, completedLectures = 5, targetLectures = 4, completedProblems = 30, targetProblems = 25)
        )

        val report = StudyAnalyticsCalculator.computeWeeklyReport(
            schedules = schedules,
            chapters = emptyList(),
            tests = emptyList(),
            mistakes = emptyList(),
            referenceDateStr = refDate
        )

        assertTrue(report.hasEnoughData)
        // Total minutes should only sum the 3 inside days: 180 + 240 + 120 = 540
        assertEquals(540, report.totalStudyMinutes)
        assertEquals(3, report.daysWithRecordedStudy)
        assertEquals(180, report.averageDailyStudyMinutes) // 540 / 3
        assertEquals(9, report.totalLecturesCompleted) // 3 + 4 + 2
        assertEquals(60, report.totalProblemsSolved) // 20 + 25 + 15
        assertEquals(1, report.targetDaysMetCount) // only 10-24 met both targets
    }

    @Test
    fun testDuplicateDateHandlingDoesNotDoubleStudyTime() {
        val refDate = "2026-10-23"
        val schedules = listOf(
            DailyScheduleEntity(date = "2026-10-23", dayNumber = 23, studyMinutes = 120, completedLectures = 2, targetLectures = 4, completedProblems = 15, targetProblems = 25),
            // Duplicate entry for same date (e.g. from sync or retry)
            DailyScheduleEntity(date = "2026-10-23", dayNumber = 23, studyMinutes = 120, completedLectures = 2, targetLectures = 4, completedProblems = 15, targetProblems = 25)
        )

        val report = StudyAnalyticsCalculator.computeWeeklyReport(
            schedules = schedules,
            chapters = emptyList(),
            tests = emptyList(),
            mistakes = emptyList(),
            referenceDateStr = refDate
        )

        assertEquals("Duplicate date should be de-duplicated without inflating total minutes", 120, report.totalStudyMinutes)
        assertEquals(1, report.daysWithRecordedStudy)
    }

    @Test
    fun testEmpiricalWeakChapterDetectionRequiresEvidence() {
        val chapters = listOf(
            ChapterEntity(id = 1, subject = "Physics", chapterName = "Kinematics", totalLectures = 8, completedLectures = 8, dppTotal = 5, dppDone = 5),
            ChapterEntity(id = 2, subject = "Chemistry", chapterName = "Thermodynamics", totalLectures = 10, completedLectures = 10, dppTotal = 6, dppDone = 6),
            ChapterEntity(id = 3, subject = "Mathematics", chapterName = "Calculus", totalLectures = 12, completedLectures = 12, dppTotal = 8, dppDone = 8)
        )

        // Only Kinematics has >= 2 mistakes logged
        val mistakes = listOf(
            MistakeEntryEntity(id = 1, subject = "Physics", chapterName = "Kinematics", questionSnippet = "Trajectory equation", errorType = "Concept Gap"),
            MistakeEntryEntity(id = 2, subject = "Physics", chapterName = "Kinematics", questionSnippet = "Relative velocity", errorType = "Calculation Error"),
            // Only 1 mistake for Thermodynamics (not enough to establish chronic weakness)
            MistakeEntryEntity(id = 3, subject = "Chemistry", chapterName = "Thermodynamics", questionSnippet = "First law sign convention", errorType = "Misread Question")
        )

        // Thermodynamics is also NOT in test weak topics
        val tests = listOf(
            TestRecordEntity(id = 1, testName = "Sunday Test 01", testDate = "2026-10-20", isAssessed = true, physicsMarks = 60, chemistryMarks = 70, mathMarks = 65, weakTopics = "Calculus")
        )

        val report = StudyAnalyticsCalculator.computeWeeklyReport(
            schedules = emptyList(),
            chapters = chapters,
            tests = tests,
            mistakes = mistakes,
            referenceDateStr = "2026-10-23"
        )

        val weakNames = report.weakChapters.map { it.chapterName }
        assertTrue("Kinematics should be flagged due to 2+ logged errors", weakNames.contains("Kinematics"))
        assertTrue("Calculus should be flagged due to Sunday test weak topics diagnosis", weakNames.contains("Calculus"))
        assertFalse("Thermodynamics should NOT be flagged as it only has 1 mistake and is absent from test diagnostics", weakNames.contains("Thermodynamics"))
    }

    @Test
    fun testMasteryAndCompletionAreSeparated() {
        val chapters = listOf(
            ChapterEntity(id = 1, subject = "Physics", chapterName = "Kinematics", totalLectures = 10, completedLectures = 10, notesDone = true, dppTotal = 5, dppDone = 5, pyqTotal = 10, pyqDone = 10)
        )
        // 100% syllabus completion for assessed chapter
        assertEquals(100, chapters[0].completionPercentage)

        // But test mastery score is 50% (150/300)
        val tests = listOf(
            TestRecordEntity(id = 1, testName = "Sunday Mock Test 01", testDate = "2026-10-22", isAssessed = true, physicsMarks = 50, chemistryMarks = 50, mathMarks = 50, totalMarks = 300)
        )

        val report = StudyAnalyticsCalculator.computeWeeklyReport(
            schedules = emptyList(),
            chapters = chapters,
            tests = tests,
            mistakes = emptyList(),
            referenceDateStr = "2026-10-23"
        )

        assertEquals("Syllabus completion percentage should reflect chapter tasks", 100, report.syllabusCompletionPercent)
        assertNotNull(report.averageTestScorePercent)
        assertEquals("Test score percentage should reflect real marks (150/300 = 50%)", 50, report.averageTestScorePercent!!)
    }
}
