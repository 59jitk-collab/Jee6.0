package com.example

import com.example.data.importer.BacklogImportExport
import com.example.data.local.entities.ChapterEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleUnitTest {

    @Test
    fun test80DayScheduleGeneration() {
        val days = BacklogImportExport.generate80DaySchedules()
        assertEquals("Schedule must contain exactly 80 days", 80, days.size)
        assertEquals("Mission start date must be 2026-10-23", "2026-10-23", days.first().date)
        assertEquals("Mission end date must be 2027-01-10", "2027-01-10", days.last().date)
        assertEquals("Day 1 index", 1, days.first().dayNumber)
        assertEquals("Day 80 index", 80, days.last().dayNumber)

        val sundays = days.filter { it.isSunday }
        assertTrue("Must contain multiple Sunday test days", sundays.size >= 11)
        assertTrue("2026-10-25 must be a Sunday", sundays.any { it.date == "2026-10-25" })
    }

    @Test
    fun testOrganicChemistryZeroFabricationPolicy() {
        val template = BacklogImportExport.getStandardJeeTemplate()
        val organicChapters = template.filter { it.chapterName.contains("(Organic)") }
        assertTrue("Template must contain Organic Chemistry placeholders", organicChapters.isNotEmpty())

        for (ch in organicChapters) {
            assertNull("Organic Chemistry lecture counts must NOT be fabricated", ch.totalLectures)
            assertEquals("Unassessed chapters must have status NOT_ASSESSED", "NOT_ASSESSED", ch.status)
            assertFalse("Unassessed chapters must report isAssessed = false", ch.isAssessed)
            assertEquals("Unassessed chapter completion percentage must be 0", 0, ch.completionPercentage)
        }
    }

    @Test
    fun testChapterCompletionFormula() {
        val chapter = ChapterEntity(
            subject = "Physics",
            chapterName = "Kinematics",
            totalLectures = 10,
            completedLectures = 5,
            notesDone = true,
            dppTotal = 5,
            dppDone = 5
        )
        assertTrue(chapter.isAssessed)
        val percent = chapter.completionPercentage
        assertTrue("Percentage should be between 0 and 100", percent in 1..99)
    }

    @Test
    fun testNullTotalLecturesNeverReports100Percent() {
        val unassessedChapter = ChapterEntity(
            subject = "Chemistry",
            chapterName = "GOC (Organic)",
            totalLectures = null,
            completedLectures = 0,
            status = "NOT_ASSESSED"
        )
        assertFalse("Unassessed chapter must return isAssessed = false", unassessedChapter.isAssessed)
        assertEquals("Unassessed completion percentage must be 0", 0, unassessedChapter.completionPercentage)
        assertEquals("NOT_ASSESSED", unassessedChapter.status)
    }

    @Test
    fun testJsonAndCsvExportImport() {
        val original = listOf(
            ChapterEntity(
                subject = "Mathematics",
                chapterName = "Calculus Integration",
                totalLectures = 12,
                completedLectures = 6,
                dppTotal = 10,
                dppDone = 5
            )
        )
        val json = BacklogImportExport.exportChaptersToJson(original)
        val parsed = BacklogImportExport.parseChaptersFromJson(json)
        assertEquals(1, parsed.size)
        assertEquals("Calculus Integration", parsed.first().chapterName)
        assertEquals(12, parsed.first().totalLectures)

        val csv = BacklogImportExport.exportChaptersToCsv(original)
        val parsedCsv = BacklogImportExport.parseChaptersFromCsv(csv)
        assertEquals(1, parsedCsv.size)
        assertEquals("Calculus Integration", parsedCsv.first().chapterName)
    }

    @Test
    fun testImportValidationAndDuplicateDetection() {
        val existing = listOf(
            ChapterEntity(subject = "Physics", chapterName = "Kinematics 1D & 2D", totalLectures = 8)
        )

        val rawInput = """
            Subject, ChapterName, TotalLectures, CompletedLectures, DPP, PYQ
            Physics, Kinematics 1D & 2D, 8, 4, 6, 45
            Chemistry, Mole Concept, 7, 0, 6, 45
        """.trimIndent()

        val preview = BacklogImportExport.validateAndPreviewImport(rawInput, existing)
        assertTrue(preview.isValid)
        assertEquals(2, preview.parsedChapters.size)
        assertEquals(1, preview.newChapters.size)
        assertEquals("Mole Concept", preview.newChapters.first().chapterName)
        assertEquals(1, preview.duplicateChapters.size)
        assertEquals("Kinematics 1D & 2D", preview.duplicateChapters.first().chapterName)
    }

    @Test
    fun testInvalidImportHandling() {
        val invalidInput = "gibberish non-csv content without commas or json structure"
        val preview = BacklogImportExport.validateAndPreviewImport(invalidInput, emptyList())
        assertFalse(preview.isValid)
        assertEquals(0, preview.parsedChapters.size)
    }

    @Test
    fun testFocusGuardStartAndRemainingTime() {
        val now = 1000000000000L
        val session = com.example.data.focus.FocusGuardManager.startSession(
            studyDurationMinutes = 45,
            permittedBreaks = 2,
            breakDurationMinutes = 5,
            blockedPackagesCsv = "com.instagram.android,com.twitter.android",
            activeTaskName = "Physics - Laws of Motion",
            nowMillis = now
        )

        assertEquals("RUNNING", session.sessionStatus)
        assertEquals(2, session.breaksRemaining)
        assertEquals(45 * 60 * 1000L, session.targetEndTimeMillis - session.sessionStartTimeMillis)

        val remainingSec = com.example.data.focus.FocusGuardManager.getRemainingStudySeconds(session, now)
        assertEquals(45 * 60L, remainingSec)

        val remainingSecHalfway = com.example.data.focus.FocusGuardManager.getRemainingStudySeconds(session, now + 15 * 60 * 1000L)
        assertEquals(30 * 60L, remainingSecHalfway)
    }

    @Test
    fun testFocusGuardControlledBreaksAndLimits() {
        val now = 1000000000000L
        var session = com.example.data.focus.FocusGuardManager.startSession(
            studyDurationMinutes = 50,
            permittedBreaks = 1,
            breakDurationMinutes = 5,
            blockedPackagesCsv = "com.instagram.android",
            activeTaskName = "Chemistry Deep Work",
            nowMillis = now
        )

        assertEquals(1, session.breaksRemaining)

        // Take Break 1
        session = com.example.data.focus.FocusGuardManager.takeBreak(session, nowMillis = now)
        assertEquals("BREAK", session.sessionStatus)
        assertEquals(0, session.breaksRemaining)
        assertEquals(5 * 60L, com.example.data.focus.FocusGuardManager.getRemainingBreakSeconds(session, now))

        // End Break
        session = com.example.data.focus.FocusGuardManager.endBreak(session)
        assertEquals("RUNNING", session.sessionStatus)

        // Attempting Break when allowance exhausted
        var thrown = false
        try {
            com.example.data.focus.FocusGuardManager.takeBreak(session, nowMillis = now + 1000L)
        } catch (e: IllegalStateException) {
            thrown = true
        }
        assertTrue("Attempting extra break must throw IllegalStateException", thrown)
    }

    @Test
    fun testFocusGuardPauseResumeDurationMath() {
        val now = 1000000000000L
        var session = com.example.data.focus.FocusGuardManager.startSession(
            studyDurationMinutes = 60,
            permittedBreaks = 2,
            breakDurationMinutes = 5,
            blockedPackagesCsv = "com.instagram.android",
            activeTaskName = "Mathematics Calculus",
            nowMillis = now
        )

        // Pause after 10 mins
        val pauseTime = now + 10 * 60 * 1000L
        session = com.example.data.focus.FocusGuardManager.pauseSession(session, nowMillis = pauseTime)
        assertEquals("PAUSED", session.sessionStatus)

        // Resume 5 mins later
        val resumeTime = pauseTime + 5 * 60 * 1000L
        session = com.example.data.focus.FocusGuardManager.resumeSession(session, nowMillis = resumeTime)
        assertEquals("RUNNING", session.sessionStatus)
        assertEquals(5 * 60 * 1000L, session.totalPausedDurationMillis)

        // Check remaining time now includes the 5 min pause compensation
        val remainingSec = com.example.data.focus.FocusGuardManager.getRemainingStudySeconds(session, nowMillis = resumeTime)
        assertEquals(50 * 60L, remainingSec)
    }

    @Test
    fun testFocusGuardSystemWhitelistAndAppBlocking() {
        val now = 1000000000000L
        val session = com.example.data.focus.FocusGuardManager.startSession(
            studyDurationMinutes = 45,
            permittedBreaks = 2,
            breakDurationMinutes = 5,
            blockedPackagesCsv = "com.instagram.android,com.twitter.android",
            activeTaskName = "Physics L1",
            nowMillis = now
        )

        // Emergency phone dialer & system settings must NEVER be blocked
        assertFalse(com.example.data.focus.FocusGuardManager.shouldBlockPackage(session, "com.android.phone", now))
        assertFalse(com.example.data.focus.FocusGuardManager.shouldBlockPackage(session, "com.android.dialer", now))
        assertFalse(com.example.data.focus.FocusGuardManager.shouldBlockPackage(session, "com.android.emergency", now))
        assertFalse(com.example.data.focus.FocusGuardManager.shouldBlockPackage(session, "com.android.settings", now))
        assertFalse(com.example.data.focus.FocusGuardManager.shouldBlockPackage(session, "com.android.systemui", now))

        // Selected distracting app MUST be blocked during RUNNING
        assertTrue(com.example.data.focus.FocusGuardManager.shouldBlockPackage(session, "com.instagram.android", now))
        assertTrue(com.example.data.focus.FocusGuardManager.shouldBlockPackage(session, "com.twitter.android", now))

        // Non-selected app must NOT be blocked
        assertFalse(com.example.data.focus.FocusGuardManager.shouldBlockPackage(session, "com.calculator.app", now))

        // During BREAK, distracting apps are temporarily ALLOWED (not blocked)
        val breakSession = com.example.data.focus.FocusGuardManager.takeBreak(session, now)
        assertFalse(com.example.data.focus.FocusGuardManager.shouldBlockPackage(breakSession, "com.instagram.android", now))
    }

    @Test
    fun testFocusGuardClockManipulationProtection() {
        val now = 1000000000000L
        val session = com.example.data.focus.FocusGuardManager.startSession(
            studyDurationMinutes = 45,
            permittedBreaks = 2,
            breakDurationMinutes = 5,
            blockedPackagesCsv = "com.instagram.android",
            activeTaskName = "Physics L1",
            nowMillis = now
        )

        // Setting clock backwards by 1 year must NOT extend session beyond original 45 minutes
        val manipulatedPast = now - (365L * 24L * 3600L * 1000L)
        val remainingSecPast = com.example.data.focus.FocusGuardManager.getRemainingStudySeconds(session, manipulatedPast)
        assertEquals("Remaining study seconds capped at 45 mins", 45 * 60L, remainingSecPast)

        // Setting clock forward past target end time expires the session
        val manipulatedFuture = session.targetEndTimeMillis + 1000L
        val remainingSecFuture = com.example.data.focus.FocusGuardManager.getRemainingStudySeconds(session, manipulatedFuture)
        assertEquals("Session expires when clock moved past target end time", 0L, remainingSecFuture)
    }
}
