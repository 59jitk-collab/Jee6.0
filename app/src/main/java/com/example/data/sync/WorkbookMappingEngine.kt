package com.example.data.sync

import com.example.data.importer.CurriculumScheduleIngestionUtility
import com.example.data.local.entities.ChapterEntity
import com.example.data.local.entities.DailyScheduleEntity
import com.example.data.local.entities.MistakeEntryEntity
import com.example.data.local.entities.SyncIdentityHelper
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

data class SheetTabInspection(
    val sheetName: String,
    val totalRows: Int,
    val dataRows: Int,
    val headers: List<String>,
    val mappedColumns: List<String>,
    val unmappedColumns: List<String>,
    val targetEntityType: String // "CHAPTER", "DAILY_SCHEDULE", "MISTAKE_ENTRY", "PRESERVED_USER_SHEET"
)

data class WorkbookDiscoveryPreview(
    val fileName: String,
    val sheetInspections: List<SheetTabInspection>,
    val extractedChapters: List<ChapterEntity>,
    val extractedSchedules: List<DailyScheduleEntity>,
    val extractedMistakes: List<MistakeEntryEntity>,
    val chaptersToAdd: Int,
    val chaptersToUpdate: Int,
    val potentialConflictsCount: Int,
    val unassessedChaptersCount: Int,
    val ignoredRowsCount: Int,
    val unsupportedColumnsSummary: List<String>,
    val warnings: List<String>,
    val isReadyToLink: Boolean
)

/**
 * Handles workbook discovery, column mapping, nullable-safe Google Sheets row conversion,
 * JSON baseline serialization, and field-level conflict diffing.
 */
object WorkbookMappingEngine {

    const val TAB_CHAPTERS = "App_Chapters_Sync"
    const val TAB_SCHEDULES = "App_Schedule_80D_Sync"
    const val TAB_MISTAKES = "App_Mistakes_Sync"
    const val TAB_META = "App_Sync_Meta"

    val CHAPTER_HEADERS = listOf(
        "SyncID", "Subject", "ChapterName", "OrderIndex",
        "TotalLectures", "CompletedLectures", "NotesDone",
        "DppTotal", "DppDone", "PyqTotal", "PyqDone",
        "DibyTotal", "DibyDone", "ClassProblemsTotal", "ClassProblemsDone",
        "WorksheetsTotal", "WorksheetsDone", "Status", "MasteryLevel",
        "IsWeak", "LastStudiedDate", "RevisionStage", "NextRevisionDate",
        "IsDeleted", "UpdatedAt", "OriginDeviceId", "VersionHash", "SchemaVersion"
    )

    val SCHEDULE_HEADERS = listOf(
        "SyncID", "Date", "DayNumber", "IsSunday", "IsRestDay",
        "TargetLectures", "CompletedLectures", "TargetProblems", "CompletedProblems",
        "NotesReviewed", "DibySolved", "PyqSolved", "StudyMinutes", "BreakMinutes",
        "SleepHours", "AssignedChapterNames", "Notes", "IsCompleted",
        "IsDeleted", "UpdatedAt", "OriginDeviceId", "VersionHash", "SchemaVersion"
    )

    val MISTAKE_HEADERS = listOf(
        "SyncID", "Subject", "ChapterName", "TestName", "QuestionSnippet",
        "ErrorType", "ConceptMissed", "CorrectTakeaway", "LessonLearned",
        "SolutionNotes", "ReviewCount", "IsResolved", "Timestamp",
        "IsDeleted", "UpdatedAt", "OriginDeviceId", "VersionHash", "SchemaVersion"
    )

    // --- Nullable-safe Sheet Row Conversion ---

    fun chapterToSheetRow(ch: ChapterEntity): List<String> {
        val syncId = ch.effectiveSyncId
        val hash = ch.computeContentHash()
        return listOf(
            syncId,
            ch.subject,
            ch.chapterName,
            ch.orderIndex.toString(),
            ch.totalLectures?.toString() ?: "", // Blank when null — NEVER 0!
            ch.completedLectures.toString(),
            if (ch.notesDone) "TRUE" else "FALSE",
            ch.dppTotal?.toString() ?: "",
            ch.dppDone?.toString() ?: "",
            ch.pyqTotal?.toString() ?: "",
            ch.pyqDone?.toString() ?: "",
            ch.dibyTotal?.toString() ?: "",
            ch.dibyDone?.toString() ?: "",
            ch.classProblemsTotal?.toString() ?: "",
            ch.classProblemsDone?.toString() ?: "",
            ch.worksheetsTotal?.toString() ?: "",
            ch.worksheetsDone?.toString() ?: "",
            ch.status,
            ch.masteryLevel,
            if (ch.isWeak) "TRUE" else "FALSE",
            ch.lastStudiedDate,
            ch.revisionStage.toString(),
            ch.nextRevisionDate,
            if (ch.isDeleted) "TRUE" else "FALSE",
            ch.lastUpdated.toString(),
            ch.originDeviceId,
            hash,
            ch.schemaVersion.toString()
        )
    }

    fun sheetRowToChapter(row: List<String>, fallbackOrderIndex: Int = 1): ChapterEntity? {
        if (row.isEmpty() || row.all { it.isBlank() }) return null
        val first = row.getOrNull(0)?.trim().orEmpty()
        if (first.equals("SyncID", ignoreCase = true) || row.getOrNull(2)?.trim().equals("ChapterName", ignoreCase = true)) {
            return null // Header row
        }
        val subject = row.getOrNull(1)?.trim().orEmpty()
        val chapterName = row.getOrNull(2)?.trim().orEmpty()
        if (chapterName.isBlank()) return null

        val syncId = first.ifBlank {
            SyncIdentityHelper.deterministicImportedChapterId(subject.ifBlank { "Physics" }, chapterName, 0)
        }
        val orderIdx = parseNullableInt(row.getOrNull(3)) ?: fallbackOrderIndex
        val totalLectures = parseNullableInt(row.getOrNull(4))
        val completedLectures = parseNullableInt(row.getOrNull(5)) ?: 0
        val notesDone = parseBool(row.getOrNull(6))
        val dppTotal = parseNullableInt(row.getOrNull(7))
        val dppDone = parseNullableInt(row.getOrNull(8))
        val pyqTotal = parseNullableInt(row.getOrNull(9))
        val pyqDone = parseNullableInt(row.getOrNull(10))
        val dibyTotal = parseNullableInt(row.getOrNull(11))
        val dibyDone = parseNullableInt(row.getOrNull(12))
        val classProbTotal = parseNullableInt(row.getOrNull(13))
        val classProbDone = parseNullableInt(row.getOrNull(14))
        val worksheetsTotal = parseNullableInt(row.getOrNull(15))
        val worksheetsDone = parseNullableInt(row.getOrNull(16))

        val isAnyAssessed = totalLectures != null || dppTotal != null || pyqTotal != null ||
                dibyTotal != null || classProbTotal != null || worksheetsTotal != null

        val rawStatus = row.getOrNull(17)?.trim().orEmpty()
        val status = when {
            !isAnyAssessed -> "NOT_ASSESSED"
            rawStatus.isNotBlank() && rawStatus != "NOT_ASSESSED" -> rawStatus
            totalLectures != null && completedLectures >= totalLectures -> "COMPLETED"
            completedLectures > 0 || notesDone || (dppDone ?: 0) > 0 || (pyqDone ?: 0) > 0 -> "IN_PROGRESS"
            else -> "NOT_STARTED"
        }
        val mastery = row.getOrNull(18)?.trim().orEmpty().ifBlank { "NOT_ASSESSED" }
        val isWeak = parseBool(row.getOrNull(19))
        val lastStudied = row.getOrNull(20)?.trim().orEmpty()
        val revStage = parseNullableInt(row.getOrNull(21)) ?: 0
        val nextRevDate = row.getOrNull(22)?.trim().orEmpty()
        val isDeleted = parseBool(row.getOrNull(23))
        val updatedAt = row.getOrNull(24)?.trim()?.toLongOrNull() ?: System.currentTimeMillis()
        val originDevice = row.getOrNull(25)?.trim().orEmpty().ifBlank { "SHEET" }
        val remoteHash = row.getOrNull(26)?.trim().orEmpty()
        val schemaVer = parseNullableInt(row.getOrNull(27)) ?: 1

        val entity = ChapterEntity(
            subject = subject.ifBlank { "Physics" },
            chapterName = chapterName,
            orderIndex = orderIdx,
            totalLectures = totalLectures,
            completedLectures = if (totalLectures != null) completedLectures.coerceIn(0, totalLectures) else completedLectures.coerceAtLeast(0),
            notesDone = notesDone,
            dibyTotal = dibyTotal,
            dibyDone = dibyDone,
            classProblemsTotal = classProbTotal,
            classProblemsDone = classProbDone,
            dppTotal = dppTotal,
            dppDone = dppDone,
            pyqTotal = pyqTotal,
            pyqDone = pyqDone,
            worksheetsTotal = worksheetsTotal,
            worksheetsDone = worksheetsDone,
            status = status,
            masteryLevel = mastery,
            isWeak = isWeak,
            lastStudiedDate = lastStudied,
            revisionStage = revStage,
            nextRevisionDate = nextRevDate,
            lastUpdated = updatedAt,
            syncId = syncId,
            schemaVersion = schemaVer,
            originDeviceId = originDevice,
            isDeleted = isDeleted
        )
        val computedHash = entity.computeContentHash()
        return entity.copy(remoteVersionHash = remoteHash.ifBlank { computedHash })
    }

    fun scheduleToSheetRow(s: DailyScheduleEntity): List<String> {
        val syncId = s.effectiveSyncId
        val hash = s.computeContentHash()
        return listOf(
            syncId,
            s.date,
            s.dayNumber.toString(),
            if (s.isSunday) "TRUE" else "FALSE",
            if (s.isRestDay) "TRUE" else "FALSE",
            s.targetLectures.toString(),
            s.completedLectures.toString(),
            s.targetProblems.toString(),
            s.completedProblems.toString(),
            if (s.notesReviewed) "TRUE" else "FALSE",
            s.dibySolved.toString(),
            s.pyqSolved.toString(),
            s.studyMinutes.toString(),
            s.breakMinutes.toString(),
            s.sleepHours.toString(),
            s.assignedChapterNames,
            s.notes,
            if (s.isCompleted) "TRUE" else "FALSE",
            if (s.isDeleted) "TRUE" else "FALSE",
            s.updatedAt.toString(),
            s.originDeviceId,
            hash,
            s.schemaVersion.toString()
        )
    }

    fun sheetRowToSchedule(row: List<String>): DailyScheduleEntity? {
        if (row.isEmpty() || row.all { it.isBlank() }) return null
        val first = row.getOrNull(0)?.trim().orEmpty()
        val date = row.getOrNull(1)?.trim().orEmpty()
        if (first.equals("SyncID", ignoreCase = true) || date.equals("Date", ignoreCase = true) || date.isBlank()) {
            return null
        }
        val dayNumber = parseNullableInt(row.getOrNull(2)) ?: 1
        val isSunday = parseBool(row.getOrNull(3))
        val isRestDay = parseBool(row.getOrNull(4))
        val targetLectures = parseNullableInt(row.getOrNull(5)) ?: 4
        val completedLectures = parseNullableInt(row.getOrNull(6)) ?: 0
        val targetProblems = parseNullableInt(row.getOrNull(7)) ?: 25
        val completedProblems = parseNullableInt(row.getOrNull(8)) ?: 0
        val notesReviewed = parseBool(row.getOrNull(9))
        val dibySolved = parseNullableInt(row.getOrNull(10)) ?: 0
        val pyqSolved = parseNullableInt(row.getOrNull(11)) ?: 0
        val studyMinutes = parseNullableInt(row.getOrNull(12)) ?: 0
        val breakMinutes = parseNullableInt(row.getOrNull(13)) ?: 0
        val sleepHours = row.getOrNull(14)?.trim()?.toFloatOrNull() ?: 7.5f
        val assignedChapters = row.getOrNull(15)?.trim().orEmpty()
        val notes = row.getOrNull(16)?.trim().orEmpty()
        val isCompleted = parseBool(row.getOrNull(17)) || (completedLectures >= targetLectures && completedProblems >= targetProblems)
        val isDeleted = parseBool(row.getOrNull(18))
        val updatedAt = row.getOrNull(19)?.trim()?.toLongOrNull() ?: System.currentTimeMillis()
        val originDevice = row.getOrNull(20)?.trim().orEmpty().ifBlank { "SHEET" }
        val remoteHash = row.getOrNull(21)?.trim().orEmpty()
        val schemaVer = parseNullableInt(row.getOrNull(22)) ?: 1

        val entity = DailyScheduleEntity(
            date = date,
            dayNumber = dayNumber,
            isSunday = isSunday,
            isRestDay = isRestDay,
            targetLectures = targetLectures,
            completedLectures = completedLectures,
            targetProblems = targetProblems,
            completedProblems = completedProblems,
            notesReviewed = notesReviewed,
            dibySolved = dibySolved,
            pyqSolved = pyqSolved,
            studyMinutes = studyMinutes,
            breakMinutes = breakMinutes,
            sleepHours = sleepHours,
            assignedChapterNames = assignedChapters,
            notes = notes,
            isCompleted = isCompleted,
            syncId = first.ifBlank { SyncIdentityHelper.scheduleSyncId(date) },
            schemaVersion = schemaVer,
            updatedAt = updatedAt,
            originDeviceId = originDevice,
            isDeleted = isDeleted
        )
        return entity.copy(remoteVersionHash = remoteHash.ifBlank { entity.computeContentHash() })
    }

    fun mistakeToSheetRow(m: MistakeEntryEntity): List<String> {
        val syncId = m.effectiveSyncId
        val hash = m.computeContentHash()
        return listOf(
            syncId,
            m.subject,
            m.chapterName,
            m.testName,
            m.questionSnippet,
            m.errorType,
            m.conceptMissed,
            m.correctTakeaway,
            m.lessonLearned,
            m.solutionNotes,
            m.reviewCount.toString(),
            if (m.isResolved) "TRUE" else "FALSE",
            m.timestamp.toString(),
            if (m.isDeleted) "TRUE" else "FALSE",
            m.updatedAt.toString(),
            m.originDeviceId,
            hash,
            m.schemaVersion.toString()
        )
    }

    fun sheetRowToMistake(row: List<String>): MistakeEntryEntity? {
        if (row.isEmpty() || row.all { it.isBlank() }) return null
        val syncId = row.getOrNull(0)?.trim().orEmpty()
        val subject = row.getOrNull(1)?.trim().orEmpty()
        val chapterName = row.getOrNull(2)?.trim().orEmpty()
        if (syncId.equals("SyncID", ignoreCase = true) || chapterName.equals("ChapterName", ignoreCase = true)) {
            return null
        }
        if (chapterName.isBlank() && row.getOrNull(4).isNullOrBlank()) return null

        val testName = row.getOrNull(3)?.trim().orEmpty().ifBlank { "Sunday Test Analysis" }
        val questionSnippet = row.getOrNull(4)?.trim().orEmpty()
        val errorType = row.getOrNull(5)?.trim().orEmpty().ifBlank { "Concept Gap" }
        val conceptMissed = row.getOrNull(6)?.trim().orEmpty()
        val correctTakeaway = row.getOrNull(7)?.trim().orEmpty()
        val lessonLearned = row.getOrNull(8)?.trim().orEmpty()
        val solutionNotes = row.getOrNull(9)?.trim().orEmpty()
        val reviewCount = parseNullableInt(row.getOrNull(10)) ?: 0
        val isResolved = parseBool(row.getOrNull(11))
        val timestamp = row.getOrNull(12)?.trim()?.toLongOrNull() ?: System.currentTimeMillis()
        val isDeleted = parseBool(row.getOrNull(13))
        val updatedAt = row.getOrNull(14)?.trim()?.toLongOrNull() ?: timestamp
        val originDevice = row.getOrNull(15)?.trim().orEmpty().ifBlank { "SHEET" }
        val remoteHash = row.getOrNull(16)?.trim().orEmpty()
        val schemaVer = parseNullableInt(row.getOrNull(17)) ?: 1

        val entity = MistakeEntryEntity(
            subject = subject.ifBlank { "Physics" },
            chapterName = chapterName.ifBlank { "General" },
            testName = testName,
            questionSnippet = questionSnippet,
            errorType = errorType,
            conceptMissed = conceptMissed,
            correctTakeaway = correctTakeaway,
            lessonLearned = lessonLearned,
            solutionNotes = solutionNotes,
            reviewCount = reviewCount,
            isResolved = isResolved,
            timestamp = timestamp,
            syncId = syncId.ifBlank { SyncIdentityHelper.newUuid("mk") },
            schemaVersion = schemaVer,
            createdAt = timestamp,
            updatedAt = updatedAt,
            originDeviceId = originDevice,
            isDeleted = isDeleted
        )
        return entity.copy(remoteVersionHash = remoteHash.ifBlank { entity.computeContentHash() })
    }

    // --- JSON Serialization for Outbox & Baseline Comparison ---

    fun serializeChapter(ch: ChapterEntity): String {
        val obj = JSONObject()
        obj.put("id", ch.id)
        obj.put("syncId", ch.effectiveSyncId)
        obj.put("subject", ch.subject)
        obj.put("chapterName", ch.chapterName)
        obj.put("orderIndex", ch.orderIndex)
        if (ch.totalLectures != null) obj.put("totalLectures", ch.totalLectures) else obj.put("totalLectures", JSONObject.NULL)
        obj.put("completedLectures", ch.completedLectures)
        obj.put("notesDone", ch.notesDone)
        if (ch.dppTotal != null) obj.put("dppTotal", ch.dppTotal) else obj.put("dppTotal", JSONObject.NULL)
        if (ch.dppDone != null) obj.put("dppDone", ch.dppDone) else obj.put("dppDone", JSONObject.NULL)
        if (ch.pyqTotal != null) obj.put("pyqTotal", ch.pyqTotal) else obj.put("pyqTotal", JSONObject.NULL)
        if (ch.pyqDone != null) obj.put("pyqDone", ch.pyqDone) else obj.put("pyqDone", JSONObject.NULL)
        if (ch.dibyTotal != null) obj.put("dibyTotal", ch.dibyTotal) else obj.put("dibyTotal", JSONObject.NULL)
        if (ch.dibyDone != null) obj.put("dibyDone", ch.dibyDone) else obj.put("dibyDone", JSONObject.NULL)
        if (ch.classProblemsTotal != null) obj.put("classProblemsTotal", ch.classProblemsTotal) else obj.put("classProblemsTotal", JSONObject.NULL)
        if (ch.classProblemsDone != null) obj.put("classProblemsDone", ch.classProblemsDone) else obj.put("classProblemsDone", JSONObject.NULL)
        if (ch.worksheetsTotal != null) obj.put("worksheetsTotal", ch.worksheetsTotal) else obj.put("worksheetsTotal", JSONObject.NULL)
        if (ch.worksheetsDone != null) obj.put("worksheetsDone", ch.worksheetsDone) else obj.put("worksheetsDone", JSONObject.NULL)
        obj.put("status", ch.status)
        obj.put("masteryLevel", ch.masteryLevel)
        obj.put("isWeak", ch.isWeak)
        obj.put("lastStudiedDate", ch.lastStudiedDate)
        obj.put("revisionStage", ch.revisionStage)
        obj.put("nextRevisionDate", ch.nextRevisionDate)
        obj.put("lastUpdated", ch.lastUpdated)
        obj.put("isDeleted", ch.isDeleted)
        obj.put("originDeviceId", ch.originDeviceId)
        obj.put("schemaVersion", ch.schemaVersion)
        return obj.toString()
    }

    fun deserializeChapter(json: String): ChapterEntity? {
        if (json.isBlank()) return null
        return try {
            val obj = JSONObject(json)
            fun optNullableInt(key: String): Int? =
                if (obj.isNull(key) || !obj.has(key)) null else obj.optInt(key)

            ChapterEntity(
                id = obj.optLong("id", 0L),
                syncId = obj.optString("syncId", ""),
                subject = obj.optString("subject", "Physics"),
                chapterName = obj.optString("chapterName", ""),
                orderIndex = obj.optInt("orderIndex", 0),
                totalLectures = optNullableInt("totalLectures"),
                completedLectures = obj.optInt("completedLectures", 0),
                notesDone = obj.optBoolean("notesDone", false),
                dppTotal = optNullableInt("dppTotal"),
                dppDone = optNullableInt("dppDone"),
                pyqTotal = optNullableInt("pyqTotal"),
                pyqDone = optNullableInt("pyqDone"),
                dibyTotal = optNullableInt("dibyTotal"),
                dibyDone = optNullableInt("dibyDone"),
                classProblemsTotal = optNullableInt("classProblemsTotal"),
                classProblemsDone = optNullableInt("classProblemsDone"),
                worksheetsTotal = optNullableInt("worksheetsTotal"),
                worksheetsDone = optNullableInt("worksheetsDone"),
                status = obj.optString("status", "NOT_ASSESSED"),
                masteryLevel = obj.optString("masteryLevel", "NOT_ASSESSED"),
                isWeak = obj.optBoolean("isWeak", false),
                lastStudiedDate = obj.optString("lastStudiedDate", ""),
                revisionStage = obj.optInt("revisionStage", 0),
                nextRevisionDate = obj.optString("nextRevisionDate", ""),
                lastUpdated = obj.optLong("lastUpdated", System.currentTimeMillis()),
                isDeleted = obj.optBoolean("isDeleted", false),
                originDeviceId = obj.optString("originDeviceId", "LOCAL"),
                schemaVersion = obj.optInt("schemaVersion", 1)
            )
        } catch (_: Exception) {
            null
        }
    }

    fun serializeSchedule(s: DailyScheduleEntity): String {
        val obj = JSONObject()
        obj.put("syncId", s.effectiveSyncId)
        obj.put("date", s.date)
        obj.put("dayNumber", s.dayNumber)
        obj.put("isSunday", s.isSunday)
        obj.put("isRestDay", s.isRestDay)
        obj.put("targetLectures", s.targetLectures)
        obj.put("completedLectures", s.completedLectures)
        obj.put("targetProblems", s.targetProblems)
        obj.put("completedProblems", s.completedProblems)
        obj.put("notesReviewed", s.notesReviewed)
        obj.put("dibySolved", s.dibySolved)
        obj.put("pyqSolved", s.pyqSolved)
        obj.put("studyMinutes", s.studyMinutes)
        obj.put("breakMinutes", s.breakMinutes)
        obj.put("sleepHours", s.sleepHours.toDouble())
        obj.put("assignedChapterNames", s.assignedChapterNames)
        obj.put("notes", s.notes)
        obj.put("isCompleted", s.isCompleted)
        obj.put("updatedAt", s.updatedAt)
        obj.put("isDeleted", s.isDeleted)
        obj.put("originDeviceId", s.originDeviceId)
        obj.put("schemaVersion", s.schemaVersion)
        return obj.toString()
    }

    fun deserializeSchedule(json: String): DailyScheduleEntity? {
        if (json.isBlank()) return null
        return try {
            val obj = JSONObject(json)
            val date = obj.optString("date", "")
            if (date.isBlank()) return null
            DailyScheduleEntity(
                date = date,
                dayNumber = obj.optInt("dayNumber", 1),
                isSunday = obj.optBoolean("isSunday", false),
                isRestDay = obj.optBoolean("isRestDay", false),
                targetLectures = obj.optInt("targetLectures", 4),
                completedLectures = obj.optInt("completedLectures", 0),
                targetProblems = obj.optInt("targetProblems", 25),
                completedProblems = obj.optInt("completedProblems", 0),
                notesReviewed = obj.optBoolean("notesReviewed", false),
                dibySolved = obj.optInt("dibySolved", 0),
                pyqSolved = obj.optInt("pyqSolved", 0),
                studyMinutes = obj.optInt("studyMinutes", 0),
                breakMinutes = obj.optInt("breakMinutes", 0),
                sleepHours = obj.optDouble("sleepHours", 7.5).toFloat(),
                assignedChapterNames = obj.optString("assignedChapterNames", ""),
                notes = obj.optString("notes", ""),
                isCompleted = obj.optBoolean("isCompleted", false),
                syncId = obj.optString("syncId", SyncIdentityHelper.scheduleSyncId(date)),
                updatedAt = obj.optLong("updatedAt", System.currentTimeMillis()),
                isDeleted = obj.optBoolean("isDeleted", false),
                originDeviceId = obj.optString("originDeviceId", "LOCAL"),
                schemaVersion = obj.optInt("schemaVersion", 1)
            )
        } catch (_: Exception) {
            null
        }
    }

    fun serializeMistake(m: MistakeEntryEntity): String {
        val obj = JSONObject()
        obj.put("id", m.id)
        obj.put("syncId", m.effectiveSyncId)
        obj.put("subject", m.subject)
        obj.put("chapterName", m.chapterName)
        obj.put("testName", m.testName)
        obj.put("questionSnippet", m.questionSnippet)
        obj.put("errorType", m.errorType)
        obj.put("conceptMissed", m.conceptMissed)
        obj.put("correctTakeaway", m.correctTakeaway)
        obj.put("lessonLearned", m.lessonLearned)
        obj.put("solutionNotes", m.solutionNotes)
        obj.put("reviewCount", m.reviewCount)
        obj.put("isResolved", m.isResolved)
        obj.put("timestamp", m.timestamp)
        obj.put("updatedAt", m.updatedAt)
        obj.put("isDeleted", m.isDeleted)
        obj.put("originDeviceId", m.originDeviceId)
        obj.put("schemaVersion", m.schemaVersion)
        return obj.toString()
    }

    fun deserializeMistake(json: String): MistakeEntryEntity? {
        if (json.isBlank()) return null
        return try {
            val obj = JSONObject(json)
            val ts = obj.optLong("timestamp", System.currentTimeMillis())
            MistakeEntryEntity(
                id = obj.optLong("id", 0L),
                syncId = obj.optString("syncId", ""),
                subject = obj.optString("subject", "Physics"),
                chapterName = obj.optString("chapterName", ""),
                testName = obj.optString("testName", "Sunday Test Analysis"),
                questionSnippet = obj.optString("questionSnippet", ""),
                errorType = obj.optString("errorType", "Concept Gap"),
                conceptMissed = obj.optString("conceptMissed", ""),
                correctTakeaway = obj.optString("correctTakeaway", ""),
                lessonLearned = obj.optString("lessonLearned", ""),
                solutionNotes = obj.optString("solutionNotes", ""),
                reviewCount = obj.optInt("reviewCount", 0),
                isResolved = obj.optBoolean("isResolved", false),
                timestamp = ts,
                createdAt = ts,
                updatedAt = obj.optLong("updatedAt", ts),
                isDeleted = obj.optBoolean("isDeleted", false),
                originDeviceId = obj.optString("originDeviceId", "LOCAL"),
                schemaVersion = obj.optInt("schemaVersion", 1)
            )
        } catch (_: Exception) {
            null
        }
    }

    // --- Field-level Diff Summaries for Conflict UI ---

    fun computeChapterFieldDiffs(local: ChapterEntity, remote: ChapterEntity): String {
        val diffs = mutableListOf<String>()
        fun check(label: String, lVal: Any?, rVal: Any?) {
            val lStr = lVal?.toString() ?: "Not Assessed (null)"
            val rStr = rVal?.toString() ?: "Not Assessed (null)"
            if (lStr != rStr) {
                diffs.add("$label: App=$lStr vs Sheet=$rStr")
            }
        }
        check("Lectures Done", local.completedLectures, remote.completedLectures)
        check("Total Lectures", local.totalLectures, remote.totalLectures)
        check("Notes Done", local.notesDone, remote.notesDone)
        check("DPP Done", local.dppDone, remote.dppDone)
        check("DPP Total", local.dppTotal, remote.dppTotal)
        check("PYQ Done", local.pyqDone, remote.pyqDone)
        check("PYQ Total", local.pyqTotal, remote.pyqTotal)
        check("DIBY Done", local.dibyDone, remote.dibyDone)
        check("DIBY Total", local.dibyTotal, remote.dibyTotal)
        check("Class Problems Done", local.classProblemsDone, remote.classProblemsDone)
        check("Worksheets Done", local.worksheetsDone, remote.worksheetsDone)
        check("Mastery", local.masteryLevel, remote.masteryLevel)
        check("Deleted", local.isDeleted, remote.isDeleted)
        return if (diffs.isEmpty()) "Metadata / Timestamp diverged" else diffs.joinToString(" • ")
    }

    fun computeScheduleFieldDiffs(local: DailyScheduleEntity, remote: DailyScheduleEntity): String {
        val diffs = mutableListOf<String>()
        fun check(label: String, lVal: Any?, rVal: Any?) {
            if (lVal != rVal) diffs.add("$label: App=$lVal vs Sheet=$rVal")
        }
        check("Completed Lectures", local.completedLectures, remote.completedLectures)
        check("Completed Problems", local.completedProblems, remote.completedProblems)
        check("Notes Reviewed", local.notesReviewed, remote.notesReviewed)
        check("Assigned Chapters", local.assignedChapterNames, remote.assignedChapterNames)
        check("Study Minutes", local.studyMinutes, remote.studyMinutes)
        return if (diffs.isEmpty()) "Schedule metadata diverged" else diffs.joinToString(" • ")
    }

    fun computeMistakeFieldDiffs(local: MistakeEntryEntity, remote: MistakeEntryEntity): String {
        val diffs = mutableListOf<String>()
        fun check(label: String, lVal: Any?, rVal: Any?) {
            if (lVal != rVal) diffs.add("$label: App=$lVal vs Sheet=$rVal")
        }
        check("Resolved", local.isResolved, remote.isResolved)
        check("Error Type", local.errorType, remote.errorType)
        check("Question Snippet", local.questionSnippet, remote.questionSnippet)
        check("Concept Missed", local.conceptMissed, remote.conceptMissed)
        check("Lesson Learned", local.lessonLearned, remote.lessonLearned)
        check("Deleted", local.isDeleted, remote.isDeleted)
        return if (diffs.isEmpty()) "Mistake metadata diverged" else diffs.joinToString(" • ")
    }

    // --- Workbook Discovery & Mapping Preview ---

    fun discoverAndPreviewXlsxWorkbook(
        bytes: ByteArray,
        fileName: String,
        existingChapters: List<ChapterEntity>,
        existingSchedules: List<DailyScheduleEntity>,
        existingMistakes: List<MistakeEntryEntity>
    ): WorkbookDiscoveryPreview {
        val warnings = mutableListOf<String>()
        val sheetInspections = mutableListOf<SheetTabInspection>()
        val unsupportedColumnsSummary = mutableListOf<String>()

        if (bytes.isEmpty()) {
            return WorkbookDiscoveryPreview(
                fileName = fileName,
                sheetInspections = emptyList(),
                extractedChapters = emptyList(),
                extractedSchedules = emptyList(),
                extractedMistakes = emptyList(),
                chaptersToAdd = 0,
                chaptersToUpdate = 0,
                potentialConflictsCount = 0,
                unassessedChaptersCount = 0,
                ignoredRowsCount = 0,
                unsupportedColumnsSummary = emptyList(),
                warnings = listOf("Selected file is empty."),
                isReadyToLink = false
            )
        }

        // Inspect raw sheets inside ZIP if it is an OpenXML .xlsx archive
        val isZip = bytes.size >= 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()
        var ignoredRowsTotal = 0

        if (isZip) {
            var workbookXml = ""
            var relsXml = ""
            val sheetXmlMap = linkedMapOf<String, String>()
            try {
                ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
                    var entry: ZipEntry? = zis.nextEntry
                    while (entry != null) {
                        val name = entry.name
                        if (!entry.isDirectory) {
                            when {
                                name.equals("xl/workbook.xml", ignoreCase = true) -> workbookXml = zis.readBytes().toString(Charsets.UTF_8)
                                name.equals("xl/_rels/workbook.xml.rels", ignoreCase = true) -> relsXml = zis.readBytes().toString(Charsets.UTF_8)
                                name.startsWith("xl/worksheets/", ignoreCase = true) && name.endsWith(".xml", ignoreCase = true) -> {
                                    val key = name.substringAfterLast('/').removeSuffix(".xml").lowercase()
                                    sheetXmlMap[key] = zis.readBytes().toString(Charsets.UTF_8)
                                }
                            }
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            } catch (e: Exception) {
                warnings.add("ZIP inspection warning: ${e.message}")
            }

            val sheetNames = CurriculumScheduleIngestionUtility.parseWorkbookSheetNames(workbookXml, relsXml)
            val knownChapterKeywords = listOf(
                "syncid", "recordid", "uuid", "subject", "chapter", "topic", "name",
                "lec", "notes", "dpp", "pyq", "diby", "classprob", "worksheet", "status",
                "mastery", "weak", "laststudied"
            )

            for ((key, xml) in sheetXmlMap) {
                val title = sheetNames[key] ?: key
                val rowTagCount = Regex("<row\\b").findAll(xml).count()
                val inlineTexts = Regex("<t(?:\\s[^>]*)?>(.*?)</t>").findAll(xml).take(25).map { it.groupValues[1].trim() }.toList()
                val mapped = mutableListOf<String>()
                val unmapped = mutableListOf<String>()
                for (h in inlineTexts) {
                    val norm = h.lowercase().replace("_", "").replace(" ", "")
                    if (knownChapterKeywords.any { norm.contains(it) }) {
                        mapped.add(h)
                    } else if (h.isNotBlank() && h.toDoubleOrNull() == null && h.length <= 28) {
                        unmapped.add(h)
                    }
                }
                val targetType = when {
                    title.contains("Schedule", ignoreCase = true) -> "DAILY_SCHEDULE"
                    title.contains("Mistake", ignoreCase = true) -> "MISTAKE_ENTRY"
                    else -> "CHAPTER"
                }
                if (unmapped.isNotEmpty()) {
                    unsupportedColumnsSummary.add("Tab '$title': preserved unmapped columns (${unmapped.take(4).joinToString(", ")})")
                }
                sheetInspections.add(
                    SheetTabInspection(
                        sheetName = title,
                        totalRows = rowTagCount,
                        dataRows = (rowTagCount - 1).coerceAtLeast(0),
                        headers = (mapped + unmapped).distinct().take(12),
                        mappedColumns = mapped.distinct(),
                        unmappedColumns = unmapped.distinct(),
                        targetEntityType = targetType
                    )
                )
            }
        }

        val rawExtractedChapters = CurriculumScheduleIngestionUtility.parseXlsxBytes(bytes)
        val (validatedChapters, _) = CurriculumScheduleIngestionUtility.validateAgainstCurriculumStandards(
            rawChapters = rawExtractedChapters,
            warningsOut = warnings
        )

        val totalSheetDataRows = sheetInspections.filter { it.targetEntityType == "CHAPTER" }.sumOf { it.dataRows }
        ignoredRowsTotal = (totalSheetDataRows - validatedChapters.size).coerceAtLeast(0)

        val existingBySyncId = existingChapters.associateBy { it.effectiveSyncId }
        val existingByName = existingChapters.associateBy { "${it.subject.trim().lowercase()}::${it.chapterName.trim().lowercase()}" }

        var toAdd = 0
        var toUpdate = 0
        var conflicts = 0

        for (ch in validatedChapters) {
            val match = existingBySyncId[ch.effectiveSyncId]
                ?: existingByName["${ch.subject.trim().lowercase()}::${ch.chapterName.trim().lowercase()}"]
            if (match == null) {
                toAdd++
            } else {
                if (match.computeContentHash() != ch.computeContentHash()) {
                    toUpdate++
                    if (match.completedLectures > 0 && ch.completedLectures > 0 && match.completedLectures != ch.completedLectures) {
                        conflicts++
                    }
                }
            }
        }

        val unassessedCount = validatedChapters.count { it.totalLectures == null }
        if (unassessedCount > 0) {
            warnings.add("$unassessedCount chapter(s) have blank/unknown lecture totals and will be preserved explicitly as NOT_ASSESSED (never converted to 0).")
        }

        return WorkbookDiscoveryPreview(
            fileName = fileName,
            sheetInspections = sheetInspections,
            extractedChapters = validatedChapters,
            extractedSchedules = existingSchedules,
            extractedMistakes = existingMistakes,
            chaptersToAdd = toAdd,
            chaptersToUpdate = toUpdate,
            potentialConflictsCount = conflicts,
            unassessedChaptersCount = unassessedCount,
            ignoredRowsCount = ignoredRowsTotal,
            unsupportedColumnsSummary = unsupportedColumnsSummary,
            warnings = warnings,
            isReadyToLink = validatedChapters.isNotEmpty()
        )
    }

    private fun parseNullableInt(cell: String?): Int? {
        val trimmed = cell?.trim().orEmpty()
        if (trimmed.isEmpty() || trimmed.equals("null", ignoreCase = true) || trimmed.equals("N/A", ignoreCase = true) || trimmed == "-") {
            return null
        }
        return trimmed.toDoubleOrNull()?.toInt()
    }

    private fun parseBool(cell: String?): Boolean {
        val trimmed = cell?.trim()?.lowercase().orEmpty()
        return trimmed == "true" || trimmed == "1" || trimmed == "yes" || trimmed == "done" || trimmed == "✓"
    }
}
