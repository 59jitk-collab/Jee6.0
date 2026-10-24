package com.example.phase4

import com.example.data.ai.GeminiChatService
import com.example.data.analytics.MasterySummaryCalculator
import com.example.data.local.entities.ChapterEntity
import com.example.data.local.entities.DailyScheduleEntity
import com.example.data.local.entities.MasteryLevel
import com.example.data.local.entities.PlanTaskActionType
import com.example.data.local.entities.RevisionItemEntity
import com.example.data.local.entities.TestRecordEntity
import com.example.data.local.entities.XpEventEntity
import com.example.data.progression.ProgressionManager
import com.example.data.revision.RevisionBucket
import com.example.data.revision.SpacedRevisionManager
import com.example.util.DateUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase4FeaturesTest {

    @Test
    fun `mastery calculator separates checklist completion from conceptual mastery and marks missing totals Not Assessed`() {
        val chapters = listOf(
            ChapterEntity(
                id = 1,
                subject = "Physics",
                chapterName = "Rotational Motion",
                totalLectures = 10,
                completedLectures = 10,
                notesDone = true,
                dppTotal = 5,
                dppDone = 5,
                masteryLevel = MasteryLevel.NEEDS_REVISION.dbKey
            ),
            ChapterEntity(
                id = 2,
                subject = "Chemistry",
                chapterName = "Aldehydes and Ketones (OC)",
                totalLectures = null,
                completedLectures = 0,
                dppTotal = null,
                dppDone = null,
                masteryLevel = MasteryLevel.NOT_ASSESSED.dbKey
            )
        )

        val physicsSummary = MasterySummaryCalculator.computeSummary(
            "Physics",
            chapters.filter { it.subject == "Physics" }
        )
        assertEquals(1, physicsSummary.totalChapters)
        assertEquals(1, physicsSummary.assessedCount)
        assertEquals(1, physicsSummary.checklistCompletedCount)
        assertEquals(1, physicsSummary.needsRevisionCount)
        assertEquals(0, physicsSummary.confidentCount)

        val chemSummary = MasterySummaryCalculator.computeSummary(
            "Chemistry",
            chapters.filter { it.subject == "Chemistry" }
        )
        assertEquals(1, chemSummary.totalChapters)
        assertEquals(0, chemSummary.assessedCount)
        assertEquals(1, chemSummary.unassessedCount)
        assertEquals(0, chemSummary.checklistCompletedCount)
        assertEquals(1, chemSummary.notAssessedMasteryCount)
    }

    @Test
    fun `spaced revision manager categorizes overdue, due today, upcoming, and completed accurately`() {
        val today = "2026-10-25"
        val items = listOf(
            RevisionItemEntity(
                id = 1,
                chapterId = 10,
                subject = "Physics",
                chapterName = "Electrostatics",
                stage = 1,
                dueDate = "2026-10-23",
                isCompleted = false
            ),
            RevisionItemEntity(
                id = 2,
                chapterId = 11,
                subject = "Chemistry",
                chapterName = "Thermodynamics",
                stage = 2,
                dueDate = "2026-10-25",
                isCompleted = false
            ),
            RevisionItemEntity(
                id = 3,
                chapterId = 12,
                subject = "Mathematics",
                chapterName = "Vectors",
                stage = 1,
                dueDate = "2026-10-24",
                snoozedUntilDate = "2026-10-28",
                isCompleted = false
            ),
            RevisionItemEntity(
                id = 4,
                chapterId = 13,
                subject = "Physics",
                chapterName = "Kinematics",
                stage = 4,
                dueDate = "2026-10-20",
                lastRevisedDate = "2026-10-20",
                isCompleted = true
            )
        )

        val categorized = SpacedRevisionManager.categorize(items, today)
        assertEquals(1, categorized.overdue.size)
        assertEquals("Electrostatics", categorized.overdue.first().chapterName)
        assertEquals(
            RevisionBucket.OVERDUE,
            SpacedRevisionManager.getBucketForRevision(categorized.overdue.first(), today)
        )

        assertEquals(1, categorized.dueToday.size)
        assertEquals("Thermodynamics", categorized.dueToday.first().chapterName)

        assertEquals(1, categorized.upcoming.size)
        assertEquals("Vectors", categorized.upcoming.first().chapterName)
        assertEquals("2026-10-28", categorized.upcoming.first().effectiveDueDate)

        assertEquals(1, categorized.completed.size)
        assertEquals(2, categorized.totalActionable)
    }

    @Test
    fun `spaced revision stage advancement follows 1-3-7-21 intervals and marks completed after final stage`() {
        val intervals = listOf(1, 3, 7, 21)
        val chapter = ChapterEntity(
            id = 5,
            subject = "Physics",
            chapterName = "Optics",
            totalLectures = 8,
            completedLectures = 8
        )
        val stage1 = SpacedRevisionManager.createOrResetRevisionForChapter(
            chapter = chapter,
            existingActive = null,
            intervals = intervals,
            todayDateStr = "2026-10-20"
        )
        assertEquals(1, stage1.stage)
        assertEquals("2026-10-21", stage1.dueDate)
        assertFalse(stage1.isCompleted)

        val stage2 = SpacedRevisionManager.advanceStage(stage1, intervals, "2026-10-21")
        assertEquals(2, stage2.stage)
        assertEquals("2026-10-24", stage2.dueDate)
        assertFalse(stage2.isCompleted)

        val stage3 = SpacedRevisionManager.advanceStage(stage2, intervals, "2026-10-24")
        assertEquals(3, stage3.stage)
        assertEquals("2026-10-31", stage3.dueDate)
        assertFalse(stage3.isCompleted)

        val stage4 = SpacedRevisionManager.advanceStage(stage3, intervals, "2026-10-31")
        assertEquals(4, stage4.stage)
        assertEquals("2026-11-21", stage4.dueDate)
        assertFalse(stage4.isCompleted)

        val afterFinal = SpacedRevisionManager.advanceStage(stage4, intervals, "2026-11-21")
        assertTrue("After stage 4 of 4, revision item should be marked completed", afterFinal.isCompleted)
    }

    @Test
    fun `progression manager enforces daily XP cap, deterministic keys, and local-date streak continuity`() {
        val key1 = ProgressionManager.lectureEventKey(chapterId = 42, lectureIndex = 3)
        val key2 = ProgressionManager.lectureEventKey(chapterId = 42, lectureIndex = 3)
        assertEquals("Deterministic keys must match to prevent duplicate XP farming", key1, key2)

        val awardedFirst = ProgressionManager.clampAwardToDailyCap(
            requestedXp = 25,
            alreadyEarnedToday = 285
        )
        assertEquals(15, awardedFirst)

        val awardedWhenFull = ProgressionManager.clampAwardToDailyCap(
            requestedXp = 20,
            alreadyEarnedToday = 300
        )
        assertEquals(0, awardedWhenFull)

        val schedules = listOf(
            DailyScheduleEntity(dayNumber = 1, date = "2026-10-23", completedLectures = 2),
            DailyScheduleEntity(dayNumber = 2, date = "2026-10-24", studyMinutes = 45),
            DailyScheduleEntity(dayNumber = 3, date = "2026-10-25", completedProblems = 15)
        )
        val events = listOf(
            XpEventEntity(
                eventKey = key1,
                category = "LECTURE",
                description = "Completed Lecture 3",
                xpAmount = 15,
                dateString = "2026-10-25",
                timestamp = 1700000000L
            )
        )
        val status = ProgressionManager.computeProgressionStatus(
            xpEvents = events,
            schedules = schedules,
            todayDateStr = "2026-10-25"
        )
        assertEquals(15, status.totalXp)
        assertEquals(3, status.currentStreakDays)
        assertEquals(3, status.longestStreakDays)
        assertTrue(status.isActiveToday)
        assertFalse(status.healthReminderNote.isBlank())
    }

    @Test
    fun `todays plan single source of truth synchronizes Home and 80-Day Schedule without fabricating chapters`() {
        val schedules = listOf(
            DailyScheduleEntity(
                date = "2026-10-23",
                dayNumber = 1,
                targetLectures = 4,
                completedLectures = 0,
                targetProblems = 25,
                completedProblems = 0,
                notesReviewed = false,
                assignedChapterNames = ""
            ),
            DailyScheduleEntity(
                date = "2026-10-24",
                dayNumber = 2,
                targetLectures = 4,
                completedLectures = 1,
                targetProblems = 25,
                completedProblems = 25,
                notesReviewed = true,
                assignedChapterNames = "Physics: Electrostatics, Chemistry: Chemical Kinetics"
            )
        )

        // 1. Unassigned day never fabricates "Rotational Motion" or "Motion in a Straight Line"
        val day1Resolved = DateUtils.resolveScheduleForDate(schedules, "2026-10-23", "2026-10-23")
        assertNotNull(day1Resolved)
        val day1Tasks = day1Resolved!!.buildSynchronizedPlanTasks()
        assertEquals(3, day1Tasks.size)
        assertTrue(day1Tasks[0].title.contains("Day 1 Lecture Target"))
        assertFalse(day1Tasks[0].title.contains("Rotational"))
        assertFalse(day1Tasks[0].title.contains("Motion in a Straight Line"))
        assertFalse(day1Tasks[0].isCompleted)

        // 2. Assigned day produces identical tasks for both Home and 80-Day Schedule
        val day2Resolved = DateUtils.resolveScheduleForDate(schedules, "2026-10-24", "2026-10-23")
        assertNotNull(day2Resolved)
        val day2Tasks = day2Resolved!!.buildSynchronizedPlanTasks()
        assertEquals(4, day2Tasks.size) // 2 assigned chapters + problems + notes
        assertEquals("Physics: Electrostatics", day2Tasks[0].title)
        assertTrue("1st assigned chapter should be marked complete when completedLectures == 1", day2Tasks[0].isCompleted)
        assertEquals("Chemistry: Chemical Kinetics", day2Tasks[1].title)
        assertFalse("2nd assigned chapter should be pending when completedLectures == 1", day2Tasks[1].isCompleted)

        val problemTask = day2Tasks.first { it.actionType == PlanTaskActionType.INCREMENT_PROBLEMS }
        assertTrue(problemTask.isCompleted)

        val notesTask = day2Tasks.first { it.actionType == PlanTaskActionType.TOGGLE_NOTES }
        assertTrue(notesTask.isCompleted)
    }

    @Test
    fun `unassessed Sunday mock test does not fabricate scores and AI mentor formats verified context accurately`() {
        val unassessedTest = TestRecordEntity(
            id = 1,
            testName = "Sunday Test 01",
            testDate = "2026-10-25",
            isAssessed = false
        )
        assertFalse(unassessedTest.isAssessed)
        assertNull(unassessedTest.totalScore)

        val service = GeminiChatService()
        val unassessedOrganic = ChapterEntity(
            id = 99,
            subject = "Chemistry",
            chapterName = "Hydrocarbons (Organic)",
            totalLectures = null,
            completedLectures = 0,
            status = "NOT_ASSESSED"
        )
        val verifiedContext = service.formatVerifiedChapterContext(unassessedOrganic)
        assertNotNull(verifiedContext)
        assertTrue(verifiedContext!!.contains("Status = Not Assessed"))

        val localGuide = service.generateLocalStructuredGuide("Explain GOC stability", unassessedOrganic)
        assertTrue(localGuide.contains("Educational Suggestion"))
    }

    @Test
    fun `curriculum schedule ingestion utility validates curriculum standards, parses xlsx workbooks, and maps 80-day schedules`() {
        val rawChapters = listOf(
            ChapterEntity(
                subject = "Phy",
                chapterName = "Electrostatics",
                totalLectures = 14,
                completedLectures = 2,
                dppTotal = 10,
                dppDone = 1
            ),
            ChapterEntity(
                subject = "Chem",
                chapterName = "Chemical Bonding",
                totalLectures = 12,
                completedLectures = 0,
                dppTotal = 8,
                dppDone = 0
            ),
            ChapterEntity(
                subject = "Maths",
                chapterName = "Vectors and 3D Geometry",
                totalLectures = 11,
                completedLectures = 0,
                dppTotal = 8,
                dppDone = 0
            ),
            ChapterEntity(
                subject = "Organic Chemistry",
                chapterName = "Aldehydes and Ketones (OC)",
                totalLectures = null,
                completedLectures = 0,
                status = "NOT_ASSESSED"
            )
        )

        // 1. Validate against curriculum standards
        val (validatedChapters, matchedCount) = com.example.data.importer.CurriculumScheduleIngestionUtility.validateAgainstCurriculumStandards(rawChapters)
        assertEquals(4, validatedChapters.size)
        assertTrue(matchedCount >= 3)
        assertEquals("Physics", validatedChapters[0].subject)
        assertEquals("Chemistry", validatedChapters[1].subject)
        assertEquals("Mathematics", validatedChapters[2].subject)
        assertEquals("Chemistry", validatedChapters[3].subject)
        assertNull(validatedChapters[3].totalLectures)

        // 2. Round-trip .xlsx workbook export and import
        val xlsxBytes = com.example.data.importer.CurriculumScheduleIngestionUtility.generateXlsxWorkbookBytes(validatedChapters)
        assertTrue(xlsxBytes.isNotEmpty())
        val parsedFromXlsx = com.example.data.importer.CurriculumScheduleIngestionUtility.parseXlsxBytes(xlsxBytes)
        assertEquals(4, parsedFromXlsx.size)
        assertEquals("Electrostatics", parsedFromXlsx[0].chapterName)
        assertEquals(14, parsedFromXlsx[0].totalLectures)
        assertNull(parsedFromXlsx[3].totalLectures)

        // 3. Map validated chapters into 80-day schedule
        val schedules = com.example.data.importer.CurriculumScheduleIngestionUtility.mapChaptersInto80DaySchedules(
            validatedChapters = parsedFromXlsx,
            existingSchedules = emptyList()
        )
        assertEquals(80, schedules.size)
        assertTrue(schedules.first().assignedChapterNames.isNotBlank())
        assertTrue(schedules.first().assignedChapterNames.contains("Physics: Electrostatics"))
    }
}
