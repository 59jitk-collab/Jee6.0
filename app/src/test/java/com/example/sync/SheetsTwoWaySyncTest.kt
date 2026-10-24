package com.example.sync

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.importer.CurriculumScheduleIngestionUtility
import com.example.data.local.AppDatabase
import com.example.data.local.entities.ChapterEntity
import com.example.data.local.entities.MistakeEntryEntity
import com.example.data.local.entities.SyncRecordState
import com.example.data.repository.StudyRepository
import com.example.data.sync.ConflictResolutionChoice
import com.example.data.sync.GoogleAuthManager
import com.example.data.sync.GoogleAuthProvider
import com.example.data.sync.GoogleAuthSession
import com.example.data.sync.GoogleSheetsRemoteGateway
import com.example.data.sync.RemoteSpreadsheetMeta
import com.example.data.sync.RemoteWorkbookSnapshot
import com.example.data.sync.SheetValueRangeUpdate
import com.example.data.sync.SheetsApiException
import com.example.data.sync.SyncExecutionResult
import com.example.data.sync.TwoWaySheetsSyncEngine
import com.example.data.sync.WorkbookMappingEngine
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SheetsTwoWaySyncTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var repository: StudyRepository
    private lateinit var fakeAuth: FakeGoogleAuthProvider
    private lateinit var fakeGateway: FakeGoogleSheetsGateway
    private lateinit var syncEngine: TwoWaySheetsSyncEngine

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = StudyRepository(db)
        fakeAuth = FakeGoogleAuthProvider()
        fakeGateway = FakeGoogleSheetsGateway()
        syncEngine = TwoWaySheetsSyncEngine(
            db = db,
            authProvider = fakeAuth,
            remoteGateway = fakeGateway,
            deviceIdProvider = { "test-device-1" }
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `migration 4 to 5 preserves v4 data, nullable totals, and backfills collision-safe syncIds`() {
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(null)
                .callback(object : SupportSQLiteOpenHelper.Callback(4) {
                    override fun onCreate(sqldb: SupportSQLiteDatabase) {
                        sqldb.execSQL(
                            """
                            CREATE TABLE IF NOT EXISTS `chapters` (
                                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                `subject` TEXT NOT NULL,
                                `chapterName` TEXT NOT NULL,
                                `orderIndex` INTEGER NOT NULL,
                                `totalLectures` INTEGER,
                                `completedLectures` INTEGER NOT NULL,
                                `notesDone` INTEGER NOT NULL,
                                `dppTotal` INTEGER,
                                `dppDone` INTEGER,
                                `pyqTotal` INTEGER,
                                `pyqDone` INTEGER,
                                `dibyTotal` INTEGER,
                                `dibyDone` INTEGER,
                                `classProblemsTotal` INTEGER,
                                `classProblemsDone` INTEGER,
                                `worksheetsTotal` INTEGER,
                                `worksheetsDone` INTEGER,
                                `isWeak` INTEGER NOT NULL,
                                `status` TEXT NOT NULL,
                                `masteryLevel` TEXT NOT NULL,
                                `lastStudiedDate` TEXT NOT NULL,
                                `revisionStage` INTEGER NOT NULL,
                                `nextRevisionDate` TEXT NOT NULL,
                                `lastUpdated` INTEGER NOT NULL
                            )
                            """.trimIndent()
                        )
                        sqldb.execSQL(
                            """
                            CREATE TABLE IF NOT EXISTS `daily_schedules` (
                                `date` TEXT NOT NULL PRIMARY KEY,
                                `dayNumber` INTEGER NOT NULL,
                                `isSunday` INTEGER NOT NULL,
                                `isRestDay` INTEGER NOT NULL,
                                `targetLectures` INTEGER NOT NULL,
                                `completedLectures` INTEGER NOT NULL,
                                `targetProblems` INTEGER NOT NULL,
                                `completedProblems` INTEGER NOT NULL,
                                `notesReviewed` INTEGER NOT NULL,
                                `dibySolved` INTEGER NOT NULL,
                                `pyqSolved` INTEGER NOT NULL,
                                `studyMinutes` INTEGER NOT NULL,
                                `breakMinutes` INTEGER NOT NULL,
                                `sleepHours` REAL NOT NULL,
                                `assignedChapterNames` TEXT NOT NULL,
                                `notes` TEXT NOT NULL,
                                `isCompleted` INTEGER NOT NULL
                            )
                            """.trimIndent()
                        )
                        sqldb.execSQL(
                            """
                            CREATE TABLE IF NOT EXISTS `mistake_entries` (
                                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                `subject` TEXT NOT NULL,
                                `chapterName` TEXT NOT NULL,
                                `testName` TEXT NOT NULL,
                                `questionSnippet` TEXT NOT NULL,
                                `errorType` TEXT NOT NULL,
                                `conceptMissed` TEXT NOT NULL,
                                `correctTakeaway` TEXT NOT NULL,
                                `lessonLearned` TEXT NOT NULL,
                                `solutionNotes` TEXT NOT NULL,
                                `timestamp` INTEGER NOT NULL,
                                `reviewCount` INTEGER NOT NULL,
                                `isResolved` INTEGER NOT NULL
                            )
                            """.trimIndent()
                        )
                    }

                    override fun onUpgrade(sqldb: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
                })
                .build()
        )

        val sqldb = helper.writableDatabase
        sqldb.execSQL(
            """
            INSERT INTO `chapters` (`id`, `subject`, `chapterName`, `orderIndex`, `totalLectures`, `completedLectures`,
            `notesDone`, `dppTotal`, `dppDone`, `pyqTotal`, `pyqDone`, `dibyTotal`, `dibyDone`,
            `classProblemsTotal`, `classProblemsDone`, `worksheetsTotal`, `worksheetsDone`,
            `isWeak`, `status`, `masteryLevel`, `lastStudiedDate`, `revisionStage`,
            `nextRevisionDate`, `lastUpdated`)
            VALUES
            (1, 'Physics', 'Atomic Structure', 1, 8, 5, 1, 6, 3, 45, 20, 4, 2, 20, 10, 2, 1, 0, 'IN_PROGRESS', 'PRACTICING', '2026-10-20', 1, '2026-10-23', 1700000000000),
            (2, 'Chemistry', 'Atomic Structure', 1, 10, 4, 0, 6, 2, 40, 10, 4, 1, 20, 5, 2, 0, 0, 'IN_PROGRESS', 'LEARNING', '', 0, '', 1700000000000),
            (3, 'Chemistry', 'Aldehydes & Ketones (OC)', 15, NULL, 0, 0, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, 'NOT_ASSESSED', 'NOT_ASSESSED', '', 0, '', 1700000000000)
            """.trimIndent()
        )
        sqldb.execSQL(
            """
            INSERT INTO `daily_schedules` (`date`, `dayNumber`, `isSunday`, `isRestDay`, `targetLectures`, `completedLectures`,
            `targetProblems`, `completedProblems`, `notesReviewed`, `dibySolved`, `pyqSolved`, `studyMinutes`, `breakMinutes`, `sleepHours`,
            `assignedChapterNames`, `notes`, `isCompleted`)
            VALUES ('2026-10-23', 1, 0, 0, 4, 2, 25, 15, 1, 0, 0, 180, 30, 7.5, 'Physics: Atomic Structure', '', 0)
            """.trimIndent()
        )
        sqldb.execSQL(
            """
            INSERT INTO `mistake_entries` (`id`, `subject`, `chapterName`, `testName`, `questionSnippet`,
            `errorType`, `conceptMissed`, `correctTakeaway`, `lessonLearned`, `solutionNotes`, `timestamp`, `reviewCount`, `isResolved`)
            VALUES (10, 'Physics', 'Atomic Structure', 'Sunday Test 1', 'Bohr radius ratio', 'Concept Gap', 'Z dependence', 'r proportional to n^2/Z', 'Check Z', 'Review formula', 1700000050000, 2, 0)
            """.trimIndent()
        )

        // Run MIGRATION_4_5
        AppDatabase.MIGRATION_4_5.migrate(sqldb)

        // Verify migrated chapters & distinct syncIds
        val cursor = sqldb.query("SELECT id, subject, chapterName, totalLectures, dppTotal, syncId, syncState, isDeleted FROM chapters ORDER BY id ASC")
        val migratedSyncIds = mutableListOf<String>()
        var ocTotalLectures: Int? = -1
        var ocDppTotal: Int? = -1
        while (cursor.moveToNext()) {
            val id = cursor.getLong(0)
            val syncId = cursor.getString(5)
            val isDeleted = cursor.getInt(7)
            assertTrue("syncId must be non-blank after migration", syncId.isNotBlank())
            assertEquals(0, isDeleted)
            migratedSyncIds.add(syncId)
            if (id == 3L) {
                ocTotalLectures = if (cursor.isNull(3)) null else cursor.getInt(3)
                ocDppTotal = if (cursor.isNull(4)) null else cursor.getInt(4)
            }
        }
        cursor.close()

        assertEquals(3, migratedSyncIds.size)
        assertEquals("All 3 chapters must have distinct syncIds", 3, migratedSyncIds.distinct().size)
        assertNull("Organic Chemistry null totalLectures must remain NULL after migration", ocTotalLectures)
        assertNull("Organic Chemistry null dppTotal must remain NULL after migration", ocDppTotal)

        // Verify schedule syncId
        val schedCursor = sqldb.query("SELECT date, syncId FROM daily_schedules")
        assertTrue(schedCursor.moveToFirst())
        assertEquals("sched_2026-10-23", schedCursor.getString(1))
        schedCursor.close()

        // Verify mistake syncId
        val mistakeCursor = sqldb.query("SELECT id, syncId FROM mistake_entries")
        assertTrue(mistakeCursor.moveToFirst())
        assertTrue(mistakeCursor.getString(1).startsWith("mk_"))
        mistakeCursor.close()

        helper.close()
    }

    @Test
    fun `nullable totals are preserved across XLSX round-trip and Google Sheets row mapping`() {
        val chapters = listOf(
            ChapterEntity(
                id = 1,
                syncId = "ch_chem_oc_go_1",
                subject = "Chemistry",
                chapterName = "General Organic Chemistry (OC)",
                orderIndex = 1,
                totalLectures = null,
                completedLectures = 0,
                notesDone = false,
                dppTotal = null,
                dppDone = null,
                pyqTotal = null,
                pyqDone = null,
                dibyTotal = null,
                dibyDone = null,
                classProblemsTotal = null,
                classProblemsDone = null,
                worksheetsTotal = null,
                worksheetsDone = null,
                status = "NOT_ASSESSED"
            ),
            ChapterEntity(
                id = 2,
                syncId = "ch_phys_kinematics_1",
                subject = "Physics",
                chapterName = "Kinematics 1D & 2D",
                orderIndex = 1,
                totalLectures = 8,
                completedLectures = 6,
                notesDone = true,
                dppTotal = 6,
                dppDone = 4,
                pyqTotal = 45,
                pyqDone = 30,
                dibyTotal = 4,
                dibyDone = 2,
                classProblemsTotal = 20,
                classProblemsDone = 15,
                worksheetsTotal = 2,
                worksheetsDone = 1
            )
        )

        // 1. Verify XLSX round-trip preserves NULL totals and SyncID
        val xlsxBytes = CurriculumScheduleIngestionUtility.generateXlsxWorkbookBytes(chapters)
        val parsedFromXlsx = CurriculumScheduleIngestionUtility.parseXlsxBytes(xlsxBytes)
        assertEquals(2, parsedFromXlsx.size)

        val ocFromXlsx = parsedFromXlsx.first { it.chapterName.contains("Organic") }
        assertNull("XLSX round-trip must keep totalLectures null", ocFromXlsx.totalLectures)
        assertNull("XLSX round-trip must keep dppTotal null", ocFromXlsx.dppTotal)
        assertNull("XLSX round-trip must keep pyqTotal null", ocFromXlsx.pyqTotal)
        assertNull("XLSX round-trip must keep dibyTotal null", ocFromXlsx.dibyTotal)
        assertNull("XLSX round-trip must keep classProblemsTotal null", ocFromXlsx.classProblemsTotal)
        assertNull("XLSX round-trip must keep worksheetsTotal null", ocFromXlsx.worksheetsTotal)
        assertEquals("ch_chem_oc_go_1", ocFromXlsx.syncId)

        // 2. Verify Google Sheets row mapping preserves blank cells as NULL
        val sheetRows = listOf(WorkbookMappingEngine.CHAPTER_HEADERS) +
            chapters.map { WorkbookMappingEngine.chapterToSheetRow(it) }
        val parsedFromSheet = sheetRows.mapNotNull { WorkbookMappingEngine.sheetRowToChapter(it) }
        assertEquals(2, parsedFromSheet.size)

        val ocFromSheet = parsedFromSheet.first { it.chapterName.contains("Organic") }
        assertNull("Sheets row mapper must keep blank totalLectures null", ocFromSheet.totalLectures)
        assertNull("Sheets row mapper must keep blank dppTotal null", ocFromSheet.dppTotal)
        assertNull("Sheets row mapper must keep blank pyqTotal null", ocFromSheet.pyqTotal)
        assertEquals("NOT_ASSESSED", ocFromSheet.status)
    }

    @Test
    fun `atomic local mutations create single coalesced outbox entries and support two-way sync without echo writes`() = runBlocking {
        // 1. Link spreadsheet
        val linkRes = syncEngine.linkAndSyncSpreadsheet(
            spreadsheetIdOrUrl = "sheet-123",
            createNewSpreadsheetTitle = null
        )
        assertTrue(linkRes is SyncExecutionResult.Success)

        // 2. Save a chapter and a mistake via StudyRepository -> verify atomic outbox creation
        val chapter = ChapterEntity(
            subject = "Physics",
            chapterName = "Electrostatics",
            orderIndex = 1,
            totalLectures = 10,
            completedLectures = 3,
            notesDone = true,
            dppTotal = 6,
            dppDone = 2,
            pyqTotal = 50,
            pyqDone = 15,
            dibyTotal = 4,
            dibyDone = 1,
            classProblemsTotal = 20,
            classProblemsDone = 8,
            worksheetsTotal = 2,
            worksheetsDone = 1
        )
        repository.saveChapter(chapter)

        val savedChapter = db.chapterDao().getAllChaptersSync().first()
        // Update lecture twice before syncing -> outbox must coalesce to 1 pending entry (idempotent)
        repository.saveChapter(savedChapter.copy(completedLectures = 5))
        val pendingOutboxBeforeSync = db.syncDao().getPendingOutboxSync()
        assertEquals("Multiple edits to same chapter must coalesce into 1 pending outbox op", 1, pendingOutboxBeforeSync.size)

        // Create a Mistake Notebook entry
        val mistake = MistakeEntryEntity(
            subject = "Physics",
            chapterName = "Electrostatics",
            testName = "Sunday Mock 2",
            questionSnippet = "Flux through hemisphere",
            errorType = "Silly",
            conceptMissed = "Solid angle factor",
            correctTakeaway = "Use Gauss law symmetry",
            solutionNotes = "q / 2*epsilon_0"
        )
        repository.saveMistake(mistake)
        assertEquals(2, db.syncDao().getPendingOutboxSync().size)

        // 3. Perform Two-Way Sync -> pushes Chapter + Mistake to FakeGoogleSheetsGateway and prunes outbox
        val pushSync = syncEngine.performTwoWaySync()
        assertTrue(pushSync is SyncExecutionResult.Success)
        assertEquals(0, db.syncDao().getPendingOutboxSync().size)
        assertEquals(5, fakeGateway.getStoredChapter(savedChapter.effectiveSyncId)?.completedLectures)
        assertEquals(1, fakeGateway.mistakeRows.size - 1) // 1 header + 1 mistake row

        // 4. Simulate Remote-Only Edit in Google Sheets (user marks completedLectures = 8 and resolves mistake in Google Sheet)
        fakeGateway.modifyRemoteChapterLectures(savedChapter.effectiveSyncId, newCompletedLectures = 8)
        val savedMistake = db.mistakeEntryDao().getAllMistakesSync().first()
        fakeGateway.modifyRemoteMistakeResolved(savedMistake.effectiveSyncId, isResolved = true, reviewCount = 3)

        val pullSync = syncEngine.performTwoWaySync()
        assertTrue(pullSync is SyncExecutionResult.Success)
        val pullSuccess = pullSync as SyncExecutionResult.Success
        assertEquals(1, pullSuccess.pulledChapters)
        assertEquals(1, pullSuccess.pulledMistakes)
        assertEquals(0, pullSuccess.pushedChapters)

        // Verify Room was updated AND no echo outbox entry was queued
        val updatedLocalChapter = db.chapterDao().getChapterBySyncId(savedChapter.effectiveSyncId)!!
        assertEquals(8, updatedLocalChapter.completedLectures)
        assertEquals(SyncRecordState.SYNCED.dbKey, updatedLocalChapter.syncState)

        val updatedLocalMistake = db.mistakeEntryDao().getMistakeBySyncId(savedMistake.effectiveSyncId)!!
        assertTrue(updatedLocalMistake.isResolved)
        assertEquals(3, updatedLocalMistake.reviewCount)
        assertEquals("Remote-only pull must NOT create echo outbox entries", 0, db.syncDao().getPendingOutboxSync().size)
    }

    @Test
    fun `simultaneous incompatible edits trigger SyncConflictEntity and preserve losing version on resolution`() = runBlocking {
        syncEngine.linkAndSyncSpreadsheet(spreadsheetIdOrUrl = "sheet-conflict-test")

        // 1. Seed initial synced chapter
        repository.saveChapter(
            ChapterEntity(
                subject = "Mathematics",
                chapterName = "Definite Integration",
                orderIndex = 4,
                totalLectures = 12,
                completedLectures = 4,
                dppTotal = 8,
                dppDone = 2
            )
        )
        syncEngine.performTwoWaySync()

        val syncedChapter = db.chapterDao().getAllChaptersSync().first()
        assertEquals(SyncRecordState.SYNCED.dbKey, syncedChapter.syncState)

        // 2. User edits locally (completedLectures = 7) AND edits in Google Sheet (completedLectures = 10) before syncing
        repository.saveChapter(syncedChapter.copy(completedLectures = 7))
        fakeGateway.modifyRemoteChapterLectures(syncedChapter.effectiveSyncId, newCompletedLectures = 10)

        val conflictSync = syncEngine.performTwoWaySync()
        assertTrue(conflictSync is SyncExecutionResult.Success)
        val conflicts = db.syncDao().getUnresolvedConflictsSync()
        assertEquals("Simultaneous edits must record a SyncConflictEntity", 1, conflicts.size)

        val conflict = conflicts.first()
        assertEquals(syncedChapter.effectiveSyncId, conflict.syncId)
        assertTrue(conflict.conflictingFieldsSummary.contains("Lectures Done"))

        // Neither local (7) nor remote (10) was silently overwritten
        assertEquals(7, db.chapterDao().getChapterBySyncId(syncedChapter.effectiveSyncId)?.completedLectures)
        assertEquals(10, fakeGateway.getStoredChapter(syncedChapter.effectiveSyncId)?.completedLectures)

        // 3. Resolve conflict choosing KEEP_REMOTE -> local updates to 10 and local version (7) is archived in losingVersionArchiveJson
        val resolved = syncEngine.resolveConflict(conflict.conflictId, ConflictResolutionChoice.KEEP_REMOTE)
        assertTrue(resolved)
        assertEquals(0, db.syncDao().getUnresolvedConflictsSync().size)

        val resolvedConflictRecord = db.syncDao().getConflictById(conflict.conflictId)!!
        assertTrue(resolvedConflictRecord.isResolved)
        assertEquals("KEEP_REMOTE", resolvedConflictRecord.resolutionChoice)
        assertNotNull("Losing version must be archived so data is never lost", resolvedConflictRecord.losingVersionArchiveJson)
        assertTrue(resolvedConflictRecord.losingVersionArchiveJson.contains("\"completedLectures\":7"))

        val chapterAfterResolve = db.chapterDao().getChapterBySyncId(syncedChapter.effectiveSyncId)!!
        assertEquals(10, chapterAfterResolve.completedLectures)
    }

    @Test
    fun `tombstone deletion propagates to Google Sheets without resurrecting deleted mistake or chapter`() = runBlocking {
        syncEngine.linkAndSyncSpreadsheet(spreadsheetIdOrUrl = "sheet-tombstone-test")

        repository.saveMistake(
            MistakeEntryEntity(
                subject = "Chemistry",
                chapterName = "Chemical Equilibrium",
                testName = "Mock 4",
                questionSnippet = "Kp vs Kc",
                errorType = "Formula",
                conceptMissed = "Delta n gas only",
                correctTakeaway = "Ignore solid/liquid in delta n"
            )
        )
        syncEngine.performTwoWaySync()

        val activeMistakes = db.mistakeEntryDao().getAllMistakesSync()
        assertEquals(1, activeMistakes.size)
        val mistake = activeMistakes.first()

        // Delete mistake locally -> creates tombstone and hides from active UI query
        repository.deleteMistake(mistake.id)
        assertEquals("Active UI query must hide tombstoned mistake", 0, db.mistakeEntryDao().getAllMistakesSync().size)
        assertEquals("Tombstone row must still exist for sync propagation", 1, db.mistakeEntryDao().getAllMistakesIncludingDeletedSync().size)

        // Sync -> pushes tombstone to Google Sheet and does not resurrect
        val res = syncEngine.performTwoWaySync()
        assertTrue(res is SyncExecutionResult.Success)
        assertEquals(0, db.mistakeEntryDao().getAllMistakesSync().size)
        val remoteMistakes = fakeGateway.mistakeRows.mapNotNull { WorkbookMappingEngine.sheetRowToMistake(it) }
        assertTrue("Remote sheet row must carry isDeleted=true tombstone", remoteMistakes.first().isDeleted)
    }

    @Test
    fun `auth revocation 401 and rate limit 429 are handled cleanly without corrupting outbox`() = runBlocking {
        syncEngine.linkAndSyncSpreadsheet(spreadsheetIdOrUrl = "sheet-error-test")

        repository.saveChapter(
            ChapterEntity(
                subject = "Physics",
                chapterName = "Wave Optics",
                orderIndex = 9,
                totalLectures = 7,
                completedLectures = 2
            )
        )

        // Simulate 429 Rate Limit
        fakeGateway.nextException = SheetsApiException.RateLimitedException("Quota exceeded", retryAfterSeconds = 45)
        val rateLimitRes = syncEngine.performTwoWaySync()
        assertTrue(rateLimitRes is SyncExecutionResult.Failure)
        val rlFail = rateLimitRes as SyncExecutionResult.Failure
        assertTrue(rlFail.isRateLimited)
        assertEquals(45L, rlFail.retryAfterSeconds)
        assertEquals("RATE_LIMITED", db.syncDao().getWorkbookConfigSync()?.syncStatus)
        assertEquals("Outbox operation must remain queued for retry after 429", 1, db.syncDao().getPendingOutboxSync().size)

        // Simulate 401 Auth Revoked
        fakeGateway.nextException = SheetsApiException.AuthRevokedException("Token revoked")
        val authRes = syncEngine.performTwoWaySync()
        assertTrue(authRes is SyncExecutionResult.Failure)
        assertTrue((authRes as SyncExecutionResult.Failure).isAuthRevoked)
        assertEquals("AUTH_REQUIRED", db.syncDao().getWorkbookConfigSync()?.syncStatus)

        // Clear exception -> retry succeeds idempotently
        fakeGateway.nextException = null
        val recoveryRes = syncEngine.performTwoWaySync()
        assertTrue(recoveryRes is SyncExecutionResult.Success)
        assertEquals(0, db.syncDao().getPendingOutboxSync().size)
    }
}

private class FakeGoogleAuthProvider : GoogleAuthProvider {
    var token: String = "fake-oauth-access-token"
    var email: String = "student@example.com"

    override suspend fun getValidAccessToken(): Result<String> {
        return Result.success(token)
    }

    override fun getCurrentSession(): GoogleAuthSession? {
        return GoogleAuthSession(
            accountEmail = email,
            accountName = "Student",
            accessToken = token,
            grantedScopes = setOf(GoogleAuthManager.SCOPE_SPREADSHEETS, GoogleAuthManager.SCOPE_DRIVE_FILE),
            expiresAtMillis = System.currentTimeMillis() + 3600_000L
        )
    }

    override fun markTokenRevokedOrExpired(reason: String) {}
}

private class FakeGoogleSheetsGateway : GoogleSheetsRemoteGateway {
    var chapterRows: List<List<String>> = listOf(WorkbookMappingEngine.CHAPTER_HEADERS)
    var scheduleRows: List<List<String>> = listOf(WorkbookMappingEngine.SCHEDULE_HEADERS)
    var mistakeRows: List<List<String>> = listOf(WorkbookMappingEngine.MISTAKE_HEADERS)
    var metaRows: List<List<String>> = listOf(listOf("Key", "Value", "UpdatedAt"))
    var nextException: SheetsApiException? = null

    override suspend fun convertXlsxToGoogleSpreadsheet(
        accessToken: String,
        title: String,
        xlsxBytes: ByteArray
    ): Result<RemoteSpreadsheetMeta> {
        nextException?.let { return Result.failure(it) }
        return Result.success(
            RemoteSpreadsheetMeta(
                spreadsheetId = "converted-sheet-id",
                title = title,
                spreadsheetUrl = "https://docs.google.com/spreadsheets/d/converted-sheet-id/edit",
                sheetTitles = listOf(
                    WorkbookMappingEngine.TAB_CHAPTERS,
                    WorkbookMappingEngine.TAB_SCHEDULES,
                    WorkbookMappingEngine.TAB_MISTAKES,
                    WorkbookMappingEngine.TAB_META
                )
            )
        )
    }

    override suspend fun createSpreadsheet(
        accessToken: String,
        title: String
    ): Result<RemoteSpreadsheetMeta> {
        nextException?.let { return Result.failure(it) }
        return Result.success(
            RemoteSpreadsheetMeta(
                spreadsheetId = "created-sheet-id",
                title = title,
                spreadsheetUrl = "https://docs.google.com/spreadsheets/d/created-sheet-id/edit",
                sheetTitles = listOf(
                    WorkbookMappingEngine.TAB_CHAPTERS,
                    WorkbookMappingEngine.TAB_SCHEDULES,
                    WorkbookMappingEngine.TAB_MISTAKES,
                    WorkbookMappingEngine.TAB_META
                )
            )
        )
    }

    override suspend fun getSpreadsheetMetadata(
        accessToken: String,
        spreadsheetId: String
    ): Result<RemoteSpreadsheetMeta> {
        nextException?.let { return Result.failure(it) }
        return Result.success(
            RemoteSpreadsheetMeta(
                spreadsheetId = spreadsheetId,
                title = "JEE 80-Day Sync Sheet",
                spreadsheetUrl = "https://docs.google.com/spreadsheets/d/$spreadsheetId/edit",
                sheetTitles = listOf(
                    WorkbookMappingEngine.TAB_CHAPTERS,
                    WorkbookMappingEngine.TAB_SCHEDULES,
                    WorkbookMappingEngine.TAB_MISTAKES,
                    WorkbookMappingEngine.TAB_META
                )
            )
        )
    }

    override suspend fun ensureSyncTabsAndHeaders(
        accessToken: String,
        spreadsheetId: String,
        existingSheetTitles: Set<String>
    ): Result<Unit> {
        nextException?.let { return Result.failure(it) }
        return Result.success(Unit)
    }

    override suspend fun fetchAllSyncTabs(
        accessToken: String,
        spreadsheetId: String
    ): Result<RemoteWorkbookSnapshot> {
        nextException?.let { return Result.failure(it) }
        return Result.success(
            RemoteWorkbookSnapshot(
                spreadsheetId = spreadsheetId,
                chaptersRows = chapterRows,
                scheduleRows = scheduleRows,
                mistakeRows = mistakeRows,
                metaRows = metaRows
            )
        )
    }

    override suspend fun batchUpdateSyncTabs(
        accessToken: String,
        spreadsheetId: String,
        updates: List<SheetValueRangeUpdate>
    ): Result<Unit> {
        nextException?.let { return Result.failure(it) }
        for (u in updates) {
            when {
                u.range.startsWith(WorkbookMappingEngine.TAB_CHAPTERS) && u.values.size > 1 -> chapterRows = u.values
                u.range.startsWith(WorkbookMappingEngine.TAB_SCHEDULES) && u.values.size > 1 -> scheduleRows = u.values
                u.range.startsWith(WorkbookMappingEngine.TAB_MISTAKES) && u.values.size > 1 -> mistakeRows = u.values
                u.range.startsWith(WorkbookMappingEngine.TAB_META) && u.values.size > 1 -> metaRows = u.values
            }
        }
        return Result.success(Unit)
    }

    fun getStoredChapter(syncId: String): ChapterEntity? {
        return chapterRows.mapNotNull { WorkbookMappingEngine.sheetRowToChapter(it) }
            .firstOrNull { it.effectiveSyncId == syncId }
    }

    fun modifyRemoteChapterLectures(syncId: String, newCompletedLectures: Int) {
        val current = chapterRows.mapNotNull { WorkbookMappingEngine.sheetRowToChapter(it) }
        val updated = current.map { ch ->
            if (ch.effectiveSyncId == syncId) {
                ch.copy(
                    completedLectures = newCompletedLectures,
                    lastUpdated = System.currentTimeMillis() + 5000L
                )
            } else {
                ch
            }
        }
        chapterRows = listOf(WorkbookMappingEngine.CHAPTER_HEADERS) +
            updated.map { WorkbookMappingEngine.chapterToSheetRow(it.copy(originDeviceId = "google-sheets-web")) }
    }

    fun modifyRemoteMistakeResolved(syncId: String, isResolved: Boolean, reviewCount: Int) {
        val current = mistakeRows.mapNotNull { WorkbookMappingEngine.sheetRowToMistake(it) }
        val updated = current.map { m ->
            if (m.effectiveSyncId == syncId) {
                m.copy(
                    isResolved = isResolved,
                    reviewCount = reviewCount,
                    updatedAt = System.currentTimeMillis() + 5000L
                )
            } else {
                m
            }
        }
        mistakeRows = listOf(WorkbookMappingEngine.MISTAKE_HEADERS) +
            updated.map { WorkbookMappingEngine.mistakeToSheetRow(it.copy(originDeviceId = "google-sheets-web")) }
    }
}
