package com.example.data.importer

import com.example.data.local.AppDatabase
import com.example.data.local.entities.ChapterEntity
import com.example.data.local.entities.DailyScheduleEntity
import com.example.data.local.entities.MasteryLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class CurriculumStandardSpec(
    val canonicalSubject: String,
    val canonicalName: String,
    val keywords: List<String>,
    val defaultLectures: Int?,
    val defaultDpp: Int?,
    val defaultPyq: Int?,
    val defaultDiby: Int? = if (defaultLectures != null) 20 else null,
    val defaultWorksheets: Int? = if (defaultLectures != null) 2 else null
)

data class CurriculumIngestionReport(
    val validatedChapters: List<ChapterEntity>,
    val mappedSchedules: List<DailyScheduleEntity>,
    val curriculumMatchedCount: Int,
    val customChaptersCount: Int,
    val totalRemainingLectures: Int,
    val totalRemainingProblems: Int,
    val assignedStudyDaysCount: Int,
    val warnings: List<String>,
    val summaryMessage: String
) {
    val scheduledDaysUpdated: Int get() = assignedStudyDaysCount
    val totalLecturesScheduled: Int get() = totalRemainingLectures
}

/**
 * Room-based data ingestion utility that:
 * 1. Parses raw chapter data from .xlsx spreadsheets (OpenXML ZIP), CSV, or JSON.
 * 2. Validates and normalizes chapters against JEE Main & Advanced curriculum standards.
 * 3. Maps the validated chapter backlog into the 80-day schedule database (DailyScheduleEntity)
 *    so each day receives an interleaved Physics, Chemistry, and Mathematics study plan.
 */
object CurriculumScheduleIngestionUtility {

    private val curriculumStandards: List<CurriculumStandardSpec> by lazy {
        BacklogImportExport.getStandardJeeTemplate().map { template ->
            val cleanBase = template.chapterName
                .replace("(Organic)", "", ignoreCase = true)
                .trim()
            val words = cleanBase.lowercase()
                .split(Regex("[^a-z0-9]+"))
                .filter { it.length >= 3 && it !in setOf("and", "the", "for") }
            CurriculumStandardSpec(
                canonicalSubject = template.subject,
                canonicalName = template.chapterName,
                keywords = words,
                defaultLectures = template.totalLectures,
                defaultDpp = template.dppTotal,
                defaultPyq = template.pyqTotal,
                defaultDiby = template.dibyTotal,
                defaultWorksheets = template.worksheetsTotal
            )
        }
    }

    /**
     * Normalizes subject aliases (e.g. "Phy", "Organic Chemistry", "Maths") to standard JEE subjects.
     */
    fun normalizeSubject(rawSubject: String?, chapterHint: String = ""): String {
        val s = (rawSubject ?: "").trim().lowercase()
        val hint = chapterHint.trim().lowercase()
        return when {
            s.startsWith("phy") || s.contains("mechanics") || s.contains("optics") || s.contains("electrostatics") -> "Physics"
            s.startsWith("chem") || s.contains("organic") || s.contains("inorganic") || s.contains("physical") -> "Chemistry"
            s.startsWith("math") || s.contains("calculus") || s.contains("algebra") || s.contains("coordinate") -> "Mathematics"
            hint.contains("goc") || hint.contains("hydrocarbon") || hint.contains("mole") ||
                    hint.contains("atomic") || hint.contains("bonding") || hint.contains("equilibrium") ||
                    hint.contains("electrochemistry") || hint.contains("kinetics") || hint.contains("coordination") -> "Chemistry"
            hint.contains("integral") || hint.contains("integration") || hint.contains("matrix") ||
                    hint.contains("matrices") || hint.contains("conic") || hint.contains("quadratic") ||
                    hint.contains("binomial") || hint.contains("probability") || hint.contains("complex") -> "Mathematics"
            else -> "Physics"
        }
    }

    /**
     * Matches a raw chapter name against the JEE curriculum standards registry.
     */
    fun findCurriculumMatch(subject: String, rawChapterName: String): CurriculumStandardSpec? {
        val normalizedSub = normalizeSubject(subject, rawChapterName)
        val cleanInput = rawChapterName.trim().lowercase()
        if (cleanInput.isEmpty()) return null

        val subjectCandidates = curriculumStandards.filter {
            it.canonicalSubject.equals(normalizedSub, ignoreCase = true)
        }

        // 1. Exact or substring match
        subjectCandidates.firstOrNull { spec ->
            val specClean = spec.canonicalName.replace("(Organic)", "", ignoreCase = true).trim().lowercase()
            specClean == cleanInput || specClean.contains(cleanInput) || cleanInput.contains(specClean)
        }?.let { return it }

        // 2. Keyword overlap match
        return subjectCandidates.maxByOrNull { spec ->
            spec.keywords.count { kw -> cleanInput.contains(kw) }
        }?.takeIf { best ->
            best.keywords.count { kw -> cleanInput.contains(kw) } >= 1
        }
    }

    /**
     * Validates a list of raw chapters against JEE curriculum standards, clamping invalid numbers
     * and filling missing curriculum metadata while preserving unassessed Organic Chemistry chapters.
     */
    fun validateAgainstCurriculumStandards(
        rawChapters: List<ChapterEntity>,
        warningsOut: MutableList<String> = mutableListOf()
    ): Pair<List<ChapterEntity>, Int> {
        val validated = mutableListOf<ChapterEntity>()
        val seenKeys = mutableSetOf<String>()
        var matchedCurriculumCount = 0

        rawChapters.forEachIndexed { idx, raw ->
            val rawName = raw.chapterName.trim()
            if (rawName.isBlank()) {
                warningsOut.add("Row ${idx + 1}: Skipped blank chapter name.")
                return@forEachIndexed
            }

            val normSubject = normalizeSubject(raw.subject, rawName)
            val curriculumMatch = findCurriculumMatch(normSubject, rawName)
            if (curriculumMatch != null) {
                matchedCurriculumCount++
            } else {
                warningsOut.add("Custom chapter '$rawName' ($normSubject) accepted with curriculum bounds validation.")
            }

            val finalSubject = curriculumMatch?.canonicalSubject ?: normSubject
            val baseKey = "${finalSubject.lowercase()}::${rawName.lowercase()}"
            val explicitSyncKey = raw.syncId.trim()
            val dedupKey = if (explicitSyncKey.isNotEmpty()) "sync::$explicitSyncKey" else baseKey
            if (!seenKeys.add(dedupKey)) {
                warningsOut.add("Duplicate chapter '$rawName' ($finalSubject) skipped to avoid duplicate row.")
                return@forEachIndexed
            }

            // Never invent totals when raw.totalLectures is null; null means unknown / NOT_ASSESSED
            val resolvedTotalLectures: Int? = raw.totalLectures?.takeIf { it > 0 }?.coerceIn(1, 60)

            val resolvedCompletedLectures = if (resolvedTotalLectures != null) {
                raw.completedLectures.coerceIn(0, resolvedTotalLectures)
            } else {
                raw.completedLectures.coerceAtLeast(0)
            }

            val resolvedDppTotal = raw.dppTotal?.takeIf { it >= 0 }?.coerceIn(0, 100)
            val resolvedDppDone = if (resolvedDppTotal != null) {
                (raw.dppDone ?: 0).coerceIn(0, resolvedDppTotal)
            } else raw.dppDone

            val resolvedPyqTotal = raw.pyqTotal?.takeIf { it >= 0 }?.coerceIn(0, 500)
            val resolvedPyqDone = if (resolvedPyqTotal != null) {
                (raw.pyqDone ?: 0).coerceIn(0, resolvedPyqTotal)
            } else raw.pyqDone

            val resolvedDibyTotal = raw.dibyTotal?.takeIf { it >= 0 }?.coerceIn(0, 200)
            val resolvedDibyDone = if (resolvedDibyTotal != null) {
                (raw.dibyDone ?: 0).coerceIn(0, resolvedDibyTotal)
            } else raw.dibyDone

            val resolvedClassProbsTotal = raw.classProblemsTotal?.takeIf { it >= 0 }?.coerceIn(0, 300)
            val resolvedClassProbsDone = if (resolvedClassProbsTotal != null) {
                (raw.classProblemsDone ?: 0).coerceIn(0, resolvedClassProbsTotal)
            } else raw.classProblemsDone

            val resolvedWorksheetsTotal = raw.worksheetsTotal?.takeIf { it >= 0 }?.coerceIn(0, 50)
            val resolvedWorksheetsDone = if (resolvedWorksheetsTotal != null) {
                (raw.worksheetsDone ?: 0).coerceIn(0, resolvedWorksheetsTotal)
            } else raw.worksheetsDone

            val isAnyTaskAssessed = resolvedTotalLectures != null ||
                    resolvedDppTotal != null ||
                    resolvedPyqTotal != null ||
                    resolvedDibyTotal != null ||
                    resolvedClassProbsTotal != null ||
                    resolvedWorksheetsTotal != null

            val resolvedStatus = when {
                !isAnyTaskAssessed -> "NOT_ASSESSED"
                resolvedTotalLectures != null && resolvedCompletedLectures >= resolvedTotalLectures -> "COMPLETED"
                resolvedCompletedLectures > 0 ||
                        raw.notesDone ||
                        (resolvedDppDone ?: 0) > 0 ||
                        (resolvedPyqDone ?: 0) > 0 ||
                        (resolvedDibyDone ?: 0) > 0 ||
                        (resolvedClassProbsDone ?: 0) > 0 ||
                        (resolvedWorksheetsDone ?: 0) > 0 -> "IN_PROGRESS"
                else -> "NOT_STARTED"
            }

            val resolvedMastery = MasteryLevel.fromKey(raw.masteryLevel).dbKey
            val resolvedSyncId = raw.syncId.ifBlank {
                com.example.data.local.entities.SyncIdentityHelper.deterministicImportedChapterId(
                    subject = finalSubject,
                    chapterName = rawName,
                    occurrenceIndex = 0
                )
            }

            validated.add(
                raw.copy(
                    subject = finalSubject,
                    chapterName = rawName,
                    orderIndex = if (raw.orderIndex > 0) raw.orderIndex else (validated.size + 1),
                    totalLectures = resolvedTotalLectures,
                    completedLectures = resolvedCompletedLectures,
                    notesDone = raw.notesDone,
                    dppTotal = resolvedDppTotal,
                    dppDone = resolvedDppDone,
                    pyqTotal = resolvedPyqTotal,
                    pyqDone = resolvedPyqDone,
                    dibyTotal = resolvedDibyTotal,
                    dibyDone = resolvedDibyDone,
                    classProblemsTotal = resolvedClassProbsTotal,
                    classProblemsDone = resolvedClassProbsDone,
                    worksheetsTotal = resolvedWorksheetsTotal,
                    worksheetsDone = resolvedWorksheetsDone,
                    status = resolvedStatus,
                    masteryLevel = resolvedMastery,
                    syncId = resolvedSyncId
                )
            )
        }

        return Pair(validated, matchedCurriculumCount)
    }

    /**
     * Maps validated chapters into the 80-day schedule database so every day in the 80-day plan
     * receives a balanced, interleaved Physics + Chemistry + Mathematics chapter schedule.
     * Unassessed chapters (totalLectures == null) are never given fake lecture counts.
     */
    fun mapChaptersInto80DaySchedules(
        validatedChapters: List<ChapterEntity>,
        existingSchedules: List<DailyScheduleEntity> = emptyList()
    ): List<DailyScheduleEntity> {
        val baseSchedules = if (existingSchedules.size >= 80) {
            existingSchedules.sortedBy { it.dayNumber }
        } else {
            BacklogImportExport.generate80DaySchedules()
        }

        if (validatedChapters.isEmpty()) return baseSchedules

        data class LectureUnit(
            val subject: String,
            val chapterName: String,
            val lectureNumber: Int,
            val totalLectures: Int,
            val problemsPerLecture: Int
        )

        fun buildSubjectQueue(subject: String): ArrayDeque<LectureUnit> {
            val queue = ArrayDeque<LectureUnit>()
            val subChapters = validatedChapters.filter { it.subject.equals(subject, ignoreCase = true) }
            for (ch in subChapters) {
                val totalLecs = ch.totalLectures
                if (totalLecs != null && totalLecs > 0) {
                    val remainingLecs = (totalLecs - ch.completedLectures).coerceAtLeast(0)
                    val remDpp = ((ch.dppTotal ?: 0) - (ch.dppDone ?: 0)).coerceAtLeast(0) * 5
                    val remPyq = ((ch.pyqTotal ?: 0) - (ch.pyqDone ?: 0)).coerceAtLeast(0)
                    val probsPerLec = if (remainingLecs > 0) {
                        ((remDpp + remPyq) / remainingLecs).coerceIn(6, 20)
                    } else 8
                    for (lecNum in (ch.completedLectures + 1)..totalLecs) {
                        queue.addLast(
                            LectureUnit(
                                subject = ch.subject,
                                chapterName = ch.chapterName,
                                lectureNumber = lecNum,
                                totalLectures = totalLecs,
                                problemsPerLecture = probsPerLec
                            )
                        )
                    }
                }
                // Note: Chapters with totalLectures == null are unassessed (NOT_ASSESSED);
                // we never fabricate synthetic lecture counts for them.
            }
            return queue
        }

        val physQueue = buildSubjectQueue("Physics")
        val chemQueue = buildSubjectQueue("Chemistry")
        val mathQueue = buildSubjectQueue("Mathematics")
        val queues = listOf(physQueue, chemQueue, mathQueue)

        val totalRemainingUnits = physQueue.size + chemQueue.size + mathQueue.size
        val nonSundayCount = baseSchedules.count { !it.isSunday }.coerceAtLeast(1)
        val recommendedDailyLectures = ((totalRemainingUnits + nonSundayCount - 1) / nonSundayCount).coerceIn(3, 6)

        var rotationIdx = 0
        val currentWeekChapters = linkedSetOf<String>()

        // Also prepare a fallback revision cycle once primary backlog lectures finish in later days
        val allChapterLabels = validatedChapters.map { "${it.subject}: ${it.chapterName}" }
        var revisionCycleIdx = 0

        return baseSchedules.map { daySchedule ->
            val syncId = daySchedule.effectiveSyncId
            if (daySchedule.isSunday) {
                val weekReviewSummary = if (currentWeekChapters.isNotEmpty()) {
                    currentWeekChapters.take(4).joinToString(", ")
                } else {
                    allChapterLabels.take(3).joinToString(", ")
                }
                currentWeekChapters.clear()
                daySchedule.copy(
                    targetLectures = 1,
                    targetProblems = 20,
                    assignedChapterNames = "Sunday Mock Test & Audit, Weekly Revision ($weekReviewSummary)",
                    notes = "3-Hour Sunday Mock Test + Mistake Notebook Audit for: $weekReviewSummary",
                    syncId = syncId
                )
            } else {
                val pickedUnits = mutableListOf<LectureUnit>()
                var attempts = 0
                while (pickedUnits.size < recommendedDailyLectures &&
                    (physQueue.isNotEmpty() || chemQueue.isNotEmpty() || mathQueue.isNotEmpty()) &&
                    attempts < 18
                ) {
                    val q = queues[rotationIdx % queues.size]
                    rotationIdx++
                    attempts++
                    if (q.isNotEmpty()) {
                        pickedUnits.add(q.removeFirst())
                    }
                }

                if (pickedUnits.isNotEmpty()) {
                    val grouped = pickedUnits.groupBy { "${it.subject}: ${it.chapterName}" }
                    val assignedLabels = grouped.map { (chKey, units) ->
                        currentWeekChapters.add(chKey)
                        val firstLec = units.first().lectureNumber
                        val lastLec = units.last().lectureNumber
                        if (firstLec == lastLec) {
                            "$chKey (L$firstLec)"
                        } else {
                            "$chKey (L$firstLec-L$lastLec)"
                        }
                    }
                    val computedTargetProblems = pickedUnits.sumOf { it.problemsPerLecture }.coerceIn(20, 60)
                    daySchedule.copy(
                        targetLectures = pickedUnits.size.coerceAtLeast(1),
                        targetProblems = computedTargetProblems,
                        assignedChapterNames = assignedLabels.joinToString(", "),
                        notes = "Day ${daySchedule.dayNumber} Plan: ${assignedLabels.joinToString(" • ")}",
                        syncId = syncId
                    )
                } else {
                    val revChapters = if (allChapterLabels.isNotEmpty()) {
                        val c1 = allChapterLabels[revisionCycleIdx % allChapterLabels.size]
                        val c2 = allChapterLabels[(revisionCycleIdx + 1) % allChapterLabels.size]
                        revisionCycleIdx += 2
                        listOf(c1, c2).distinct()
                    } else {
                        listOf("Physics & Math Full-Syllabus PYQ Sprint")
                    }
                    daySchedule.copy(
                        targetLectures = 3,
                        targetProblems = 35,
                        assignedChapterNames = revChapters.joinToString(", ") { "$it (PYQ Sprint)" },
                        notes = "Final Sprint Revision & PYQ Drill: ${revChapters.joinToString(" • ")}",
                        syncId = syncId
                    )
                }
            }
        }
    }

    /**
     * Parses an .xlsx spreadsheet stream (OpenXML ZIP archive) directly into raw ChapterEntity records.
     * Supports multi-sheet workbooks (e.g., Physics, Chemistry, Mathematics tabs or a unified Backlog sheet).
     */
    fun parseXlsxStream(inputStream: InputStream): List<ChapterEntity> {
        return parseXlsxBytes(inputStream.readBytes())
    }

    fun parseXlsxBytes(bytes: ByteArray): List<ChapterEntity> {
        if (bytes.isEmpty()) return emptyList()

        // Check if user selected a plain text CSV/JSON file with .xlsx extension or via generic picker
        if (bytes.size >= 2 && (bytes[0].toInt() != 0x50 || bytes[1].toInt() != 0x4B)) {
            val rawText = bytes.toString(Charsets.UTF_8)
            return BacklogImportExport.parseChaptersFromJson(rawText)
        }

        val sharedStrings = mutableListOf<String>()
        var workbookXml = ""
        var workbookRelsXml = ""
        val worksheetXmlByFile = linkedMapOf<String, String>()

        ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                val name = entry.name
                if (!entry.isDirectory) {
                    when {
                        name.equals("xl/sharedStrings.xml", ignoreCase = true) -> {
                            val xml = zis.readBytes().toString(Charsets.UTF_8)
                            sharedStrings.addAll(parseSharedStringsXml(xml))
                        }
                        name.equals("xl/workbook.xml", ignoreCase = true) -> {
                            workbookXml = zis.readBytes().toString(Charsets.UTF_8)
                        }
                        name.equals("xl/_rels/workbook.xml.rels", ignoreCase = true) -> {
                            workbookRelsXml = zis.readBytes().toString(Charsets.UTF_8)
                        }
                        name.startsWith("xl/worksheets/", ignoreCase = true) && name.endsWith(".xml", ignoreCase = true) -> {
                            val xml = zis.readBytes().toString(Charsets.UTF_8)
                            val shortKey = name.substringAfterLast('/').removeSuffix(".xml").lowercase()
                            worksheetXmlByFile[shortKey] = xml
                        }
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        val sheetNamesByFile = parseWorkbookSheetNames(workbookXml, workbookRelsXml)
        val extractedChapters = mutableListOf<ChapterEntity>()
        var orderIdx = 1

        for ((sheetKey, sheetXml) in worksheetXmlByFile) {
            val sheetTitle = sheetNamesByFile[sheetKey] ?: sheetKey
            // Skip dedicated schedule, mistake, or metadata tabs when extracting chapters
            if (sheetTitle.contains("Schedule", ignoreCase = true) ||
                sheetTitle.contains("Mistake", ignoreCase = true) ||
                sheetTitle.contains("Sync_Meta", ignoreCase = true)
            ) {
                continue
            }

            val rows = parseWorksheetRows(sheetXml, sharedStrings)
            if (rows.isEmpty()) continue

            var syncIdCol = -1
            var subjectCol = -1
            var chapterCol = -1
            var totalLecCol = -1
            var compLecCol = -1
            var notesDoneCol = -1
            var dppTotalCol = -1
            var dppDoneCol = -1
            var pyqTotalCol = -1
            var pyqDoneCol = -1
            var dibyTotalCol = -1
            var dibyDoneCol = -1
            var classProbTotalCol = -1
            var classProbDoneCol = -1
            var worksheetTotalCol = -1
            var worksheetDoneCol = -1
            var statusCol = -1
            var masteryCol = -1
            var isWeakCol = -1
            var lastStudiedCol = -1
            var headerRowIndex = -1

            // Detect header row in the first 10 rows
            for (rIdx in 0 until minOf(10, rows.size)) {
                val row = rows[rIdx]
                val joined = row.joinToString(" ").lowercase()
                if (joined.contains("chapter") || joined.contains("topic") || joined.contains("subject") || joined.contains("lecture")) {
                    headerRowIndex = rIdx
                    row.forEachIndexed { cIdx, cellText ->
                        val h = cellText.trim().lowercase().replace("_", "").replace(" ", "")
                        when {
                            h == "syncid" || h == "recordid" || h == "uuid" -> syncIdCol = cIdx
                            h.contains("subject") -> subjectCol = cIdx
                            (h.contains("chapter") || h.contains("topic") || h == "name") && chapterCol == -1 -> chapterCol = cIdx
                            h == "totallectures" || h == "lectures" || h == "lec" || (h.contains("total") && h.contains("lec")) -> totalLecCol = cIdx
                            h == "completedlectures" || h == "donelectures" || ((h.contains("comp") || h.contains("done")) && h.contains("lec")) -> compLecCol = cIdx
                            h.contains("notes") -> notesDoneCol = cIdx
                            h == "dpptotal" || h == "dpp" -> dppTotalCol = cIdx
                            h == "dppdone" || h == "dppcompleted" -> dppDoneCol = cIdx
                            h == "pyqtotal" || h == "pyq" -> pyqTotalCol = cIdx
                            h == "pyqdone" || h == "pyqcompleted" -> pyqDoneCol = cIdx
                            h == "dibytotal" || h == "diby" -> dibyTotalCol = cIdx
                            h == "dibydone" || h == "dibycompleted" -> dibyDoneCol = cIdx
                            h == "classproblemstotal" || h == "classproblems" -> classProbTotalCol = cIdx
                            h == "classproblemsdone" -> classProbDoneCol = cIdx
                            h == "worksheetstotal" || h == "worksheets" || h == "worksheet" -> worksheetTotalCol = cIdx
                            h == "worksheetsdone" || h == "worksheetdone" -> worksheetDoneCol = cIdx
                            h.contains("status") -> statusCol = cIdx
                            h.contains("mastery") -> masteryCol = cIdx
                            h == "isweak" || h == "weak" -> isWeakCol = cIdx
                            h.contains("laststudied") -> lastStudiedCol = cIdx
                        }
                    }
                    break
                }
            }

            val startRow = if (headerRowIndex >= 0) headerRowIndex + 1 else 0
            val sheetImpliedSubject = when {
                sheetTitle.contains("phys", ignoreCase = true) -> "Physics"
                sheetTitle.contains("chem", ignoreCase = true) -> "Chemistry"
                sheetTitle.contains("math", ignoreCase = true) -> "Mathematics"
                else -> null
            }

            fun parseNullableInt(cell: String?): Int? {
                val trimmed = cell?.trim().orEmpty()
                if (trimmed.isEmpty() || trimmed.equals("null", ignoreCase = true) || trimmed.equals("N/A", ignoreCase = true) || trimmed == "-") {
                    return null
                }
                return trimmed.toDoubleOrNull()?.toInt()
            }

            fun parseBool(cell: String?): Boolean {
                val trimmed = cell?.trim()?.lowercase().orEmpty()
                return trimmed == "true" || trimmed == "1" || trimmed == "yes" || trimmed == "done" || trimmed == "✓"
            }

            for (rIdx in startRow until rows.size) {
                val row = rows[rIdx]
                if (row.all { it.isBlank() }) continue

                val rawSyncId: String
                val rawSubject: String
                val rawChapterName: String
                val rawTotalLecs: Int?
                val rawCompLecs: Int
                val rawNotesDone: Boolean
                val rawDppTotal: Int?
                val rawDppDone: Int?
                val rawPyqTotal: Int?
                val rawPyqDone: Int?
                val rawDibyTotal: Int?
                val rawDibyDone: Int?
                val rawClassProbTotal: Int?
                val rawClassProbDone: Int?
                val rawWorksheetsTotal: Int?
                val rawWorksheetsDone: Int?
                val rawStatus: String?
                val rawMastery: String?
                val rawIsWeak: Boolean
                val rawLastStudied: String

                if (chapterCol >= 0) {
                    rawSyncId = if (syncIdCol >= 0) row.getOrNull(syncIdCol)?.trim().orEmpty() else ""
                    rawChapterName = row.getOrNull(chapterCol)?.trim().orEmpty()
                    rawSubject = if (subjectCol >= 0) {
                        row.getOrNull(subjectCol)?.trim().orEmpty().ifBlank { sheetImpliedSubject ?: normalizeSubject(null, rawChapterName) }
                    } else {
                        sheetImpliedSubject ?: normalizeSubject(null, rawChapterName)
                    }
                    rawTotalLecs = if (totalLecCol >= 0) parseNullableInt(row.getOrNull(totalLecCol)) else null
                    rawCompLecs = if (compLecCol >= 0) parseNullableInt(row.getOrNull(compLecCol)) ?: 0 else 0
                    rawNotesDone = if (notesDoneCol >= 0) parseBool(row.getOrNull(notesDoneCol)) else false
                    rawDppTotal = if (dppTotalCol >= 0) parseNullableInt(row.getOrNull(dppTotalCol)) else null
                    rawDppDone = if (dppDoneCol >= 0) parseNullableInt(row.getOrNull(dppDoneCol)) else if (rawDppTotal != null) 0 else null
                    rawPyqTotal = if (pyqTotalCol >= 0) parseNullableInt(row.getOrNull(pyqTotalCol)) else null
                    rawPyqDone = if (pyqDoneCol >= 0) parseNullableInt(row.getOrNull(pyqDoneCol)) else if (rawPyqTotal != null) 0 else null
                    rawDibyTotal = if (dibyTotalCol >= 0) parseNullableInt(row.getOrNull(dibyTotalCol)) else null
                    rawDibyDone = if (dibyDoneCol >= 0) parseNullableInt(row.getOrNull(dibyDoneCol)) else if (rawDibyTotal != null) 0 else null
                    rawClassProbTotal = if (classProbTotalCol >= 0) parseNullableInt(row.getOrNull(classProbTotalCol)) else null
                    rawClassProbDone = if (classProbDoneCol >= 0) parseNullableInt(row.getOrNull(classProbDoneCol)) else if (rawClassProbTotal != null) 0 else null
                    rawWorksheetsTotal = if (worksheetTotalCol >= 0) parseNullableInt(row.getOrNull(worksheetTotalCol)) else null
                    rawWorksheetsDone = if (worksheetDoneCol >= 0) parseNullableInt(row.getOrNull(worksheetDoneCol)) else if (rawWorksheetsTotal != null) 0 else null
                    rawStatus = if (statusCol >= 0) row.getOrNull(statusCol)?.trim()?.takeIf { it.isNotBlank() } else null
                    rawMastery = if (masteryCol >= 0) row.getOrNull(masteryCol)?.trim()?.takeIf { it.isNotBlank() } else null
                    rawIsWeak = if (isWeakCol >= 0) parseBool(row.getOrNull(isWeakCol)) else false
                    rawLastStudied = if (lastStudiedCol >= 0) row.getOrNull(lastStudiedCol)?.trim().orEmpty() else ""
                } else {
                    val firstCell = row.getOrNull(0)?.trim().orEmpty()
                    val secondCell = row.getOrNull(1)?.trim().orEmpty()
                    val firstLooksLikeSubject = firstCell.equals("Physics", ignoreCase = true) ||
                            firstCell.equals("Chemistry", ignoreCase = true) ||
                            firstCell.equals("Mathematics", ignoreCase = true) ||
                            firstCell.equals("Maths", ignoreCase = true)

                    rawSyncId = ""
                    rawNotesDone = false
                    rawClassProbTotal = null
                    rawClassProbDone = null
                    rawWorksheetsTotal = null
                    rawWorksheetsDone = null
                    rawStatus = null
                    rawMastery = null
                    rawIsWeak = false
                    rawLastStudied = ""

                    if (firstLooksLikeSubject && secondCell.isNotBlank()) {
                        rawSubject = firstCell
                        rawChapterName = secondCell
                        rawTotalLecs = parseNullableInt(row.getOrNull(2))
                        rawCompLecs = parseNullableInt(row.getOrNull(3)) ?: 0
                        rawDppTotal = parseNullableInt(row.getOrNull(4))
                        rawDppDone = if (rawDppTotal != null) 0 else null
                        rawPyqTotal = parseNullableInt(row.getOrNull(5))
                        rawPyqDone = if (rawPyqTotal != null) 0 else null
                        rawDibyTotal = parseNullableInt(row.getOrNull(6))
                        rawDibyDone = if (rawDibyTotal != null) 0 else null
                    } else if (sheetImpliedSubject != null && firstCell.isNotBlank()) {
                        rawSubject = sheetImpliedSubject
                        rawChapterName = firstCell
                        rawTotalLecs = parseNullableInt(secondCell)
                        rawCompLecs = parseNullableInt(row.getOrNull(2)) ?: 0
                        rawDppTotal = parseNullableInt(row.getOrNull(3))
                        rawDppDone = if (rawDppTotal != null) 0 else null
                        rawPyqTotal = parseNullableInt(row.getOrNull(4))
                        rawPyqDone = if (rawPyqTotal != null) 0 else null
                        rawDibyTotal = null
                        rawDibyDone = null
                    } else {
                        continue
                    }
                }

                if (rawChapterName.isBlank() ||
                    rawChapterName.equals("Chapter", ignoreCase = true) ||
                    rawChapterName.equals("ChapterName", ignoreCase = true) ||
                    rawChapterName.equals("Total", ignoreCase = true)
                ) {
                    continue
                }

                val normSub = normalizeSubject(rawSubject, rawChapterName)
                val computedSyncId = rawSyncId.ifBlank {
                    com.example.data.local.entities.SyncIdentityHelper.deterministicImportedChapterId(
                        subject = normSub,
                        chapterName = rawChapterName,
                        occurrenceIndex = 0
                    )
                }

                extractedChapters.add(
                    ChapterEntity(
                        subject = normSub,
                        chapterName = rawChapterName,
                        orderIndex = orderIdx++,
                        totalLectures = rawTotalLecs,
                        completedLectures = rawCompLecs,
                        notesDone = rawNotesDone,
                        dibyTotal = rawDibyTotal,
                        dibyDone = rawDibyDone,
                        classProblemsTotal = rawClassProbTotal,
                        classProblemsDone = rawClassProbDone,
                        dppTotal = rawDppTotal,
                        dppDone = rawDppDone,
                        pyqTotal = rawPyqTotal,
                        pyqDone = rawPyqDone,
                        worksheetsTotal = rawWorksheetsTotal,
                        worksheetsDone = rawWorksheetsDone,
                        status = rawStatus ?: if (rawTotalLecs == null) "NOT_ASSESSED" else "NOT_STARTED",
                        masteryLevel = rawMastery ?: "NOT_ASSESSED",
                        isWeak = rawIsWeak,
                        lastStudiedDate = rawLastStudied,
                        syncId = computedSyncId
                    )
                )
            }
        }

        return extractedChapters
    }

    /**
     * Generates a valid OpenXML .xlsx spreadsheet binary (ByteArray) from chapters,
     * preserving all totals (keeping null totals blank rather than 0), completions, and stable SyncID.
     */
    fun generateXlsxWorkbookBytes(chapters: List<ChapterEntity>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zos ->
            fun writeEntry(path: String, content: String) {
                zos.putNextEntry(ZipEntry(path))
                zos.write(content.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }

            writeEntry(
                "[Content_Types].xml",
                """<?xml version="1.0" encoding="UTF-8"?>
                <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                  <Default Extension="xml" ContentType="application/xml"/>
                  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
                  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
                </Types>""".trimIndent()
            )

            writeEntry(
                "xl/workbook.xml",
                """<?xml version="1.0" encoding="UTF-8"?>
                <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                  <sheets>
                    <sheet name="JEE_Backlog_Tracker" sheetId="1" r:id="rId1" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"/>
                  </sheets>
                </workbook>""".trimIndent()
            )

            writeEntry(
                "xl/_rels/workbook.xml.rels",
                """<?xml version="1.0" encoding="UTF-8"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
                </Relationships>""".trimIndent()
            )

            val rowsXml = StringBuilder()
            val headers = listOf(
                "Subject", "ChapterName", "TotalLectures", "CompletedLectures",
                "DPP", "DppDone", "PYQ", "PyqDone", "DIBY", "DibyDone",
                "ClassProblemsTotal", "ClassProblemsDone", "WorksheetsTotal", "WorksheetsDone",
                "NotesDone", "Status", "MasteryLevel", "IsWeak", "LastStudiedDate", "SyncID"
            )
            rowsXml.append("<row r=\"1\">")
            headers.forEachIndexed { cIdx, h ->
                val colRef = indexToExcelColumn(cIdx) + "1"
                rowsXml.append("<c r=\"$colRef\" t=\"inlineStr\"><is><t>${escapeXml(h)}</t></is></c>")
            }
            rowsXml.append("</row>")

            chapters.forEachIndexed { idx, ch ->
                val r = idx + 2
                rowsXml.append("<row r=\"$r\">")
                fun appendStr(cIdx: Int, text: String) {
                    val colRef = indexToExcelColumn(cIdx) + r
                    rowsXml.append("<c r=\"$colRef\" t=\"inlineStr\"><is><t>${escapeXml(text)}</t></is></c>")
                }
                fun appendNum(cIdx: Int, num: Int?) {
                    if (num != null) {
                        val colRef = indexToExcelColumn(cIdx) + r
                        rowsXml.append("<c r=\"$colRef\"><v>$num</v></c>")
                    }
                }
                appendStr(0, ch.subject)
                appendStr(1, ch.chapterName)
                appendNum(2, ch.totalLectures)
                appendNum(3, ch.completedLectures)
                appendNum(4, ch.dppTotal)
                appendNum(5, ch.dppDone)
                appendNum(6, ch.pyqTotal)
                appendNum(7, ch.pyqDone)
                appendNum(8, ch.dibyTotal)
                appendNum(9, ch.dibyDone)
                appendNum(10, ch.classProblemsTotal)
                appendNum(11, ch.classProblemsDone)
                appendNum(12, ch.worksheetsTotal)
                appendNum(13, ch.worksheetsDone)
                appendStr(14, if (ch.notesDone) "TRUE" else "FALSE")
                appendStr(15, ch.status)
                appendStr(16, ch.masteryLevel)
                appendStr(17, if (ch.isWeak) "TRUE" else "FALSE")
                appendStr(18, ch.lastStudiedDate)
                appendStr(19, ch.effectiveSyncId)
                rowsXml.append("</row>")
            }

            writeEntry(
                "xl/worksheets/sheet1.xml",
                """<?xml version="1.0" encoding="UTF-8"?>
                <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                  <sheetData>$rowsXml</sheetData>
                </worksheet>""".trimIndent()
            )
        }
        return out.toByteArray()
    }

    private fun indexToExcelColumn(colIndex: Int): String {
        var n = colIndex
        val sb = StringBuilder()
        while (n >= 0) {
            sb.insert(0, ('A' + (n % 26)))
            n = (n / 26) - 1
        }
        return sb.toString()
    }

    /**
     * Full pipeline: Validates raw chapters against JEE curriculum standards, maps them into
     * the 80-day schedule, and returns a complete [CurriculumIngestionReport].
     */
    fun buildIngestionPlan(
        rawChapters: List<ChapterEntity>,
        existingSchedules: List<DailyScheduleEntity> = emptyList()
    ): CurriculumIngestionReport {
        val warnings = mutableListOf<String>()
        val (validated, matchedCount) = validateAgainstCurriculumStandards(rawChapters, warnings)
        val mappedSchedules = mapChaptersInto80DaySchedules(validated, existingSchedules)

        val totalRemainingLecs = validated.sumOf { ch ->
            val total = ch.totalLectures ?: 0
            (total - ch.completedLectures).coerceAtLeast(0)
        }
        val totalRemainingProbs = validated.sumOf { ch ->
            val dppRem = ((ch.dppTotal ?: 0) - (ch.dppDone ?: 0)).coerceAtLeast(0) * 5
            val pyqRem = ((ch.pyqTotal ?: 0) - (ch.pyqDone ?: 0)).coerceAtLeast(0)
            dppRem + pyqRem
        }
        val assignedDays = mappedSchedules.count { it.assignedChapterNames.isNotBlank() }
        val customCount = (validated.size - matchedCount).coerceAtLeast(0)

        val summary = "Validated ${validated.size} chapters ($matchedCount JEE curriculum standard, $customCount custom) • " +
                "Auto-mapped $totalRemainingLecs remaining lectures across $assignedDays days in the 80-Day Schedule."

        return CurriculumIngestionReport(
            validatedChapters = validated,
            mappedSchedules = mappedSchedules,
            curriculumMatchedCount = matchedCount,
            customChaptersCount = customCount,
            totalRemainingLectures = totalRemainingLecs,
            totalRemainingProblems = totalRemainingProbs,
            assignedStudyDaysCount = assignedDays,
            warnings = warnings,
            summaryMessage = summary
        )
    }

    /**
     * Persists the validated chapters and the generated 80-day daily schedules into the Room database.
     * Defaults to non-destructive upsert (replaceExistingChapters = false) so existing chapters and IDs
     * are preserved and matched by syncId or unambiguous (subject, chapterName).
     */
    suspend fun ingestIntoRoomDatabase(
        db: AppDatabase,
        rawChapters: List<ChapterEntity>,
        replaceExistingChapters: Boolean = false
    ): CurriculumIngestionReport = withContext(Dispatchers.IO) {
        val existingSchedules = db.dailyScheduleDao().getAllDaysSync()
        val report = buildIngestionPlan(rawChapters, existingSchedules)

        if (report.validatedChapters.isNotEmpty()) {
            if (replaceExistingChapters) {
                db.chapterDao().clearAllChapters()
                db.chapterDao().insertAll(report.validatedChapters)
            } else {
                val existingChapters = db.chapterDao().getAllChaptersIncludingDeletedSync()
                val bySyncId = existingChapters.associateBy { it.effectiveSyncId }
                val bySubjectAndName = existingChapters
                    .filter { !it.isDeleted }
                    .groupBy { "${it.subject.trim().lowercase()}::${it.chapterName.trim().lowercase()}" }

                for (incoming in report.validatedChapters) {
                    val matchBySync = bySyncId[incoming.effectiveSyncId]
                    val nameKey = "${incoming.subject.trim().lowercase()}::${incoming.chapterName.trim().lowercase()}"
                    val candidatesByName = bySubjectAndName[nameKey].orEmpty()
                    val existing = matchBySync ?: if (candidatesByName.size == 1) candidatesByName.first() else null

                    if (existing != null) {
                        val merged = incoming.copy(
                            id = existing.id,
                            syncId = existing.effectiveSyncId,
                            createdAt = existing.createdAt,
                            lastUpdated = System.currentTimeMillis(),
                            syncState = "PENDING",
                            isDeleted = false
                        )
                        db.chapterDao().updateChapter(merged)
                    } else {
                        db.chapterDao().insertChapter(
                            incoming.copy(
                                syncId = incoming.effectiveSyncId,
                                lastUpdated = System.currentTimeMillis(),
                                syncState = "PENDING"
                            )
                        )
                    }
                }
            }
            db.dailyScheduleDao().insertAll(report.mappedSchedules)
        }
        report
    }

    private fun parseSharedStringsXml(xml: String): List<String> {
        val result = mutableListOf<String>()
        val siRegex = Regex("<si\\b[^>]*>(.*?)</si>", RegexOption.DOT_MATCHES_ALL)
        val tRegex = Regex("<t(?:\\s[^>]*)?>(.*?)</t>", RegexOption.DOT_MATCHES_ALL)
        for (siMatch in siRegex.findAll(xml)) {
            val inner = siMatch.groupValues[1]
            val sb = StringBuilder()
            for (tMatch in tRegex.findAll(inner)) {
                sb.append(unescapeXml(tMatch.groupValues[1]))
            }
            result.add(sb.toString())
        }
        return result
    }

    fun parseWorkbookSheetNames(workbookXml: String, relsXml: String = ""): Map<String, String> {
        if (workbookXml.isBlank()) return emptyMap()
        val relIdToTarget = mutableMapOf<String, String>()
        if (relsXml.isNotBlank()) {
            val relRegex = Regex("<Relationship\\b[^>]*/>")
            val idAttr = Regex("\\bId=\"([^\"]+)\"")
            val targetAttr = Regex("\\bTarget=\"([^\"]+)\"")
            for (m in relRegex.findAll(relsXml)) {
                val tag = m.value
                val rId = idAttr.find(tag)?.groupValues?.get(1)
                val target = targetAttr.find(tag)?.groupValues?.get(1)
                if (!rId.isNullOrBlank() && !target.isNullOrBlank()) {
                    val shortKey = target.substringAfterLast('/').removeSuffix(".xml").lowercase()
                    relIdToTarget[rId] = shortKey
                }
            }
        }

        val map = mutableMapOf<String, String>()
        val sheetTagRegex = Regex("<sheet\\b[^>]*(?:/>|>.*?</sheet>)", RegexOption.DOT_MATCHES_ALL)
        val nameAttr = Regex("\\bname=\"([^\"]+)\"")
        val rIdAttr = Regex("\\br:id=\"([^\"]+)\"")
        var idx = 1
        for (m in sheetTagRegex.findAll(workbookXml)) {
            val tag = m.value
            val name = nameAttr.find(tag)?.groupValues?.get(1)?.let { unescapeXml(it) } ?: continue
            val rId = rIdAttr.find(tag)?.groupValues?.get(1)
            val sheetKey = (if (rId != null) relIdToTarget[rId] else null) ?: "sheet$idx"
            map[sheetKey] = name
            idx++
        }
        return map
    }

    private fun parseWorksheetRows(xml: String, sharedStrings: List<String>): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val rowRegex = Regex("<row\\b[^>]*>(.*?)</row>", RegexOption.DOT_MATCHES_ALL)
        val cellRegex = Regex("<c\\b([^>]*?)(?:/>|>(.*?)</c>)", RegexOption.DOT_MATCHES_ALL)
        val refAttrRegex = Regex("\\br=\"([A-Za-z]+)\\d+\"")
        val typeAttrRegex = Regex("\\bt=\"([^\"]+)\"")
        val vTagRegex = Regex("<v>(.*?)</v>", RegexOption.DOT_MATCHES_ALL)
        val tTagRegex = Regex("<t(?:\\s[^>]*)?>(.*?)</t>", RegexOption.DOT_MATCHES_ALL)

        for (rowMatch in rowRegex.findAll(xml)) {
            val rowInner = rowMatch.groupValues[1]
            val cellsByCol = sortedMapOf<Int, String>()
            var fallbackCol = 0

            for (cellMatch in cellRegex.findAll(rowInner)) {
                val attrs = cellMatch.groupValues[1]
                val cellInner = cellMatch.groupValues[2]
                val colLetters = refAttrRegex.find(attrs)?.groupValues?.get(1)
                val colIndex = if (colLetters != null) excelColumnToIndex(colLetters) else fallbackCol
                fallbackCol = colIndex + 1

                val cellType = typeAttrRegex.find(attrs)?.groupValues?.get(1) ?: ""
                val value = when (cellType) {
                    "s" -> {
                        val sIdx = vTagRegex.find(cellInner)?.groupValues?.get(1)?.trim()?.toIntOrNull()
                        if (sIdx != null && sIdx in sharedStrings.indices) sharedStrings[sIdx] else ""
                    }
                    "inlineStr" -> {
                        tTagRegex.findAll(cellInner).joinToString("") { unescapeXml(it.groupValues[1]) }
                    }
                    else -> {
                        val vText = vTagRegex.find(cellInner)?.groupValues?.get(1)
                            ?: tTagRegex.find(cellInner)?.groupValues?.get(1)
                            ?: ""
                        unescapeXml(vText.trim())
                    }
                }
                cellsByCol[colIndex] = value
            }

            if (cellsByCol.isNotEmpty()) {
                val maxCol = cellsByCol.keys.maxOrNull() ?: 0
                val rowList = (0..maxCol).map { cellsByCol[it] ?: "" }
                rows.add(rowList)
            }
        }
        return rows
    }

    private fun excelColumnToIndex(colLetters: String): Int {
        var result = 0
        for (ch in colLetters.uppercase()) {
            if (ch in 'A'..'Z') {
                result = result * 26 + (ch - 'A' + 1)
            }
        }
        return (result - 1).coerceAtLeast(0)
    }

    private fun escapeXml(input: String): String {
        return input
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    private fun unescapeXml(input: String): String {
        return input
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
    }
}
