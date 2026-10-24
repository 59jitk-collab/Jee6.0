package com.example.data.importer

import com.example.data.local.entities.ChapterEntity
import com.example.data.local.entities.DailyScheduleEntity
import com.example.data.local.entities.TestRecordEntity
import com.example.util.DateUtils
import org.json.JSONArray
import org.json.JSONObject

data class ImportValidationResult(
    val parsedChapters: List<ChapterEntity>,
    val newChapters: List<ChapterEntity>,
    val duplicateChapters: List<ChapterEntity>,
    val invalidLinesCount: Int,
    val warnings: List<String>,
    val isValid: Boolean,
    val summaryText: String
)

object BacklogImportExport {

    fun generate80DaySchedules(): List<DailyScheduleEntity> {
        val dates = DateUtils.generate80DayDates("2026-10-23", "2027-01-10")
        return dates.mapIndexed { index, (dateStr, isSunday) ->
            DailyScheduleEntity(
                date = dateStr,
                dayNumber = index + 1,
                targetLectures = if (isSunday) 1 else 4,
                completedLectures = 0,
                targetProblems = if (isSunday) 15 else 25,
                completedProblems = 0,
                notesReviewed = false,
                dibySolved = 0,
                pyqSolved = 0,
                studyMinutes = 0,
                breakMinutes = 0,
                sleepHours = 7.5f,
                isSunday = isSunday,
                isRestDay = false,
                notes = if (isSunday) "Sunday Mock Test + Detailed Analysis" else "Standard 4-Lecture Focus Block"
            )
        }
    }

    fun generateSampleSundayTests(): List<TestRecordEntity> {
        return listOf(
            TestRecordEntity(
                testName = "Sunday Test 01 (Kinematics, Mole Concept & Quadratic)",
                testDate = "2026-10-25",
                physicsMarks = 76,
                chemistryMarks = 68,
                mathMarks = 64,
                negativeMarks = 12,
                weakTopics = "Relative Velocity in 2D, Limiting Reagent",
                analysisNotes = "Good speed in Physics Round 1; avoid sign errors in Quadratic range questions.",
                isAssessed = true
            ),
            TestRecordEntity(
                testName = "Sunday Test 02 (NLM, Atomic Structure & Complex Numbers)",
                testDate = "2026-11-01",
                physicsMarks = 80,
                chemistryMarks = 72,
                mathMarks = 66,
                negativeMarks = 9,
                weakTopics = "Pseudo Force on Wedge, Argand Plane Locus",
                analysisNotes = "Accuracy improved to 88%; revise Bohr model spectral lines.",
                isAssessed = true
            ),
            TestRecordEntity(
                testName = "Sunday Test 03 (Work-Energy, Thermodynamics & Sequences)",
                testDate = "2026-11-08",
                physicsMarks = 78,
                chemistryMarks = 74,
                mathMarks = 70,
                negativeMarks = 8,
                weakTopics = "Vertical Circular Motion, Adiabatic Work Sign",
                analysisNotes = "Strong Physics & Chemistry conceptual accuracy; practice AGP summation.",
                isAssessed = true
            ),
            TestRecordEntity(
                testName = "Sunday Test 04 (Cumulative 1: Mechanics & Physical Chem)",
                testDate = "2026-11-15",
                physicsMarks = 82,
                chemistryMarks = 75,
                mathMarks = 72,
                negativeMarks = 7,
                weakTopics = "Rotational Rolling Friction, Ionic Equilibrium Buffer",
                analysisNotes = "Cumulative benchmark complete — 229/300.",
                isAssessed = true
            ),
            TestRecordEntity(
                testName = "Sunday Test 05 (Rotational, Equilibrium & Binomial/P&C)",
                testDate = "2026-11-22",
                isAssessed = false
            ),
            TestRecordEntity(
                testName = "Sunday Test 06 (Gravitation, Electrochemistry & Matrices)",
                testDate = "2026-11-29",
                isAssessed = false
            ),
            TestRecordEntity(
                testName = "Sunday Test 07 (Cumulative 2: SHM, Waves & Calculus I)",
                testDate = "2026-12-06",
                isAssessed = false
            ),
            TestRecordEntity(
                testName = "Sunday Test 08 (Full Syllabus Mock 1 — JEE Main Pattern)",
                testDate = "2026-12-13",
                isAssessed = false
            ),
            TestRecordEntity(
                testName = "Sunday Test 09 (Full Syllabus Mock 2 — High Weightage)",
                testDate = "2026-12-20",
                isAssessed = false
            ),
            TestRecordEntity(
                testName = "Sunday Test 10 (All India Rank Booster Mock)",
                testDate = "2026-12-27",
                isAssessed = false
            ),
            TestRecordEntity(
                testName = "Sunday Test 11 (Final Sprint 1 — Speed & Accuracy)",
                testDate = "2027-01-03",
                isAssessed = false
            ),
            TestRecordEntity(
                testName = "Sunday Test 12 (Final JEE Readiness Benchmark)",
                testDate = "2027-01-10",
                isAssessed = false
            )
        )
    }

    /**
     * Automatically generates a realistic, syllabus-aligned Sunday Mock Test evaluation based on the
     * user's current chapter mastery & completion progress so the user never has to fill out a form manually.
     */
    fun autoGenerateMockScoreForTest(
        test: TestRecordEntity,
        chapters: List<ChapterEntity>
    ): TestRecordEntity {
        val seed = (test.id.toInt().coerceAtLeast(1) * 7) + test.testName.hashCode().let { kotlin.math.abs(it % 13) }
        val physChapters = chapters.filter { it.subject.equals("Physics", ignoreCase = true) && it.isAssessed }
        val chemChapters = chapters.filter { it.subject.equals("Chemistry", ignoreCase = true) && it.isAssessed }
        val mathChapters = chapters.filter { it.subject.equals("Mathematics", ignoreCase = true) && it.isAssessed }

        val physRatio = if (physChapters.isNotEmpty()) physChapters.map { it.completionPercentage }.average() / 100.0 else 0.72
        val chemRatio = if (chemChapters.isNotEmpty()) chemChapters.map { it.completionPercentage }.average() / 100.0 else 0.70
        val mathRatio = if (mathChapters.isNotEmpty()) mathChapters.map { it.completionPercentage }.average() / 100.0 else 0.68

        val phyScore = (64 + (physRatio * 24).toInt() + (seed % 9)).coerceIn(48, 96)
        val chemScore = (62 + (chemRatio * 24).toInt() + ((seed + 3) % 9)).coerceIn(45, 95)
        val mathScore = (58 + (mathRatio * 26).toInt() + ((seed + 5) % 9)).coerceIn(44, 96)
        val negMarks = (6 + (seed % 7)).coerceIn(4, 15)

        val candidateWeak = chapters.filter { it.isWeak || it.completionPercentage < 50 }
            .take(2)
            .map { it.chapterName }
            .ifEmpty {
                listOf("Rotational Dynamics", "Ionic Equilibrium", "Definite Integration", "Electrostatics")
                    .shuffled()
                    .take(2)
            }

        return test.copy(
            physicsMarks = phyScore,
            chemistryMarks = chemScore,
            mathMarks = mathScore,
            negativeMarks = negMarks,
            weakTopics = candidateWeak.joinToString(", "),
            analysisNotes = "Auto-evaluated from 80-Day Mission progress • Target PYQ drill on ${candidateWeak.firstOrNull() ?: "core topics"}.",
            isAssessed = true
        )
    }

    fun getStandardJeeTemplate(): List<ChapterEntity> {
        val chapters = mutableListOf<ChapterEntity>()
        var idx = 1

        fun addPhys(name: String, lecs: Int, diby: Int, dpp: Int, pyq: Int, worksheets: Int) {
            chapters.add(
                ChapterEntity(
                    subject = "Physics",
                    chapterName = name,
                    orderIndex = idx++,
                    totalLectures = lecs,
                    completedLectures = 0,
                    notesDone = false,
                    dibyTotal = diby,
                    dibyDone = 0,
                    classProblemsTotal = 30,
                    classProblemsDone = 0,
                    dppTotal = dpp,
                    dppDone = 0,
                    pyqTotal = pyq,
                    pyqDone = 0,
                    worksheetsTotal = worksheets,
                    worksheetsDone = 0,
                    status = "NOT_STARTED",
                    isWeak = false
                )
            )
        }

        fun addMath(name: String, lecs: Int, diby: Int, dpp: Int, pyq: Int, worksheets: Int) {
            chapters.add(
                ChapterEntity(
                    subject = "Mathematics",
                    chapterName = name,
                    orderIndex = idx++,
                    totalLectures = lecs,
                    completedLectures = 0,
                    notesDone = false,
                    dibyTotal = diby,
                    dibyDone = 0,
                    classProblemsTotal = 35,
                    classProblemsDone = 0,
                    dppTotal = dpp,
                    dppDone = 0,
                    pyqTotal = pyq,
                    pyqDone = 0,
                    worksheetsTotal = worksheets,
                    worksheetsDone = 0,
                    status = "NOT_STARTED",
                    isWeak = false
                )
            )
        }

        fun addInorganic(name: String, lecs: Int, dpp: Int, pyq: Int) {
            chapters.add(
                ChapterEntity(
                    subject = "Chemistry",
                    chapterName = name,
                    orderIndex = idx++,
                    totalLectures = lecs,
                    completedLectures = 0,
                    notesDone = false,
                    dibyTotal = 15,
                    dibyDone = 0,
                    classProblemsTotal = 25,
                    classProblemsDone = 0,
                    dppTotal = dpp,
                    dppDone = 0,
                    pyqTotal = pyq,
                    pyqDone = 0,
                    worksheetsTotal = 2,
                    worksheetsDone = 0,
                    status = "NOT_STARTED",
                    isWeak = false
                )
            )
        }

        fun addOrganicUnassessed(name: String) {
            chapters.add(
                ChapterEntity(
                    subject = "Chemistry",
                    chapterName = "$name (Organic)",
                    orderIndex = idx++,
                    totalLectures = null,
                    completedLectures = 0,
                    notesDone = false,
                    dibyTotal = null,
                    dibyDone = null,
                    classProblemsTotal = null,
                    classProblemsDone = null,
                    dppTotal = null,
                    dppDone = null,
                    pyqTotal = null,
                    pyqDone = null,
                    worksheetsTotal = null,
                    worksheetsDone = null,
                    status = "NOT_ASSESSED",
                    isWeak = false
                )
            )
        }

        // Physics Chapters
        addPhys("Kinematics 1D & 2D", 8, 20, 6, 45, 2)
        addPhys("NLM & Friction", 9, 25, 7, 50, 3)
        addPhys("Work, Power & Energy", 7, 20, 5, 40, 2)
        addPhys("Circular Motion & COM", 8, 22, 6, 42, 2)
        addPhys("Rotational Dynamics", 12, 35, 10, 65, 4)
        addPhys("Gravitation", 5, 15, 4, 35, 2)
        addPhys("Fluid Mechanics", 7, 20, 5, 40, 2)
        addPhys("Thermodynamics & KTG", 8, 24, 6, 48, 3)
        addPhys("Simple Harmonic Motion", 6, 18, 5, 38, 2)
        addPhys("Wave Motion & Sound", 7, 20, 5, 42, 2)
        addPhys("Electrostatics", 11, 30, 9, 60, 3)
        addPhys("Current Electricity", 10, 28, 8, 55, 3)
        addPhys("Magnetic Effects & Magnetism", 9, 25, 7, 50, 3)
        addPhys("EMI & Alternating Current", 8, 22, 6, 45, 2)
        addPhys("Ray & Wave Optics", 13, 35, 10, 65, 4)
        addPhys("Modern Physics & Semiconductors", 10, 30, 8, 70, 3)

        // Physical & Inorganic Chemistry
        addInorganic("Mole Concept & Stoichiometry", 7, 6, 45)
        addInorganic("Atomic Structure", 7, 6, 45)
        addInorganic("Chemical Thermodynamics", 9, 8, 55)
        addInorganic("Chemical & Ionic Equilibrium", 11, 9, 60)
        addInorganic("Electrochemistry & Solutions", 10, 8, 55)
        addInorganic("Chemical Kinetics", 6, 5, 40)
        addInorganic("Periodic Table & Periodicity", 5, 4, 40)
        addInorganic("Chemical Bonding & Molecular Structure", 10, 8, 65)
        addInorganic("Coordination Compounds", 8, 7, 55)
        addInorganic("p-Block Elements", 9, 7, 50)
        addInorganic("d & f-Block Elements", 6, 5, 40)

        // Organic Chemistry (Marked strictly NOT_ASSESSED with null counts)
        addOrganicUnassessed("General Organic Chemistry (GOC)")
        addOrganicUnassessed("Hydrocarbons")
        addOrganicUnassessed("Haloalkanes & Haloarenes")
        addOrganicUnassessed("Alcohols, Phenols & Ethers")
        addOrganicUnassessed("Aldehydes, Ketones & Carboxylic Acids")
        addOrganicUnassessed("Amines & Diazonium Salts")
        addOrganicUnassessed("Biomolecules & Polymers")

        // Mathematics Chapters
        addMath("Sets, Relations & Functions", 7, 20, 5, 40, 2)
        addMath("Quadratic Equations", 6, 18, 5, 38, 2)
        addMath("Complex Numbers", 9, 25, 7, 50, 3)
        addMath("Sequences & Series", 6, 18, 5, 40, 2)
        addMath("Binomial Theorem", 6, 18, 5, 35, 2)
        addMath("Permutations & Combinations", 8, 24, 6, 45, 3)
        addMath("Matrices & Determinants", 8, 22, 6, 48, 2)
        addMath("Limits, Continuity & Differentiability", 10, 30, 8, 55, 3)
        addMath("Application of Derivatives (AOD)", 9, 28, 7, 52, 3)
        addMath("Indefinite & Definite Integration", 13, 38, 10, 70, 4)
        addMath("Differential Equations & Area", 7, 20, 6, 42, 2)
        addMath("Coordinate Geometry: Straight Lines & Circles", 11, 32, 9, 60, 3)
        addMath("Conic Sections (Parabola, Ellipse, Hyperbola)", 12, 35, 10, 65, 4)
        addMath("Vectors & 3D Geometry", 10, 30, 8, 65, 3)
        addMath("Probability & Statistics", 8, 24, 6, 45, 2)

        return chapters
    }

    /**
     * Inspects, validates against JEE curriculum standards, detects duplicates, and previews 80-day schedule mapping.
     */
    fun validateAndPreviewImport(
        rawContent: String,
        existingChapters: List<ChapterEntity>
    ): ImportValidationResult {
        val rawParsed = parseChaptersFromJson(rawContent)
        return buildValidationPreview(rawParsed, existingChapters)
    }

    /**
     * Parses a binary .xlsx spreadsheet, validates against JEE curriculum standards, and previews 80-day schedule mapping.
     */
    fun validateAndPreviewXlsxImport(
        xlsxBytes: ByteArray,
        existingChapters: List<ChapterEntity>
    ): ImportValidationResult {
        val rawParsed = CurriculumScheduleIngestionUtility.parseXlsxBytes(xlsxBytes)
        return buildValidationPreview(rawParsed, existingChapters)
    }

    private fun buildValidationPreview(
        rawParsed: List<ChapterEntity>,
        existingChapters: List<ChapterEntity>
    ): ImportValidationResult {
        val curriculumWarnings = mutableListOf<String>()
        val (validated, _) = CurriculumScheduleIngestionUtility.validateAgainstCurriculumStandards(
            rawChapters = rawParsed,
            warningsOut = curriculumWarnings
        )
        val warnings = mutableListOf<String>()
        warnings.addAll(curriculumWarnings.take(4))

        if (validated.isEmpty()) {
            return ImportValidationResult(
                parsedChapters = emptyList(),
                newChapters = emptyList(),
                duplicateChapters = emptyList(),
                invalidLinesCount = 1,
                warnings = listOf("Unable to parse valid .xlsx, JSON, or CSV chapter records."),
                isValid = false,
                summaryText = "Import Failed: Format not recognized or empty."
            )
        }

        val existingKeys = existingChapters.map { "${it.subject.lowercase()}:${it.chapterName.lowercase()}" }.toSet()
        val newChapters = mutableListOf<ChapterEntity>()
        val duplicates = mutableListOf<ChapterEntity>()

        for (item in validated) {
            val key = "${item.subject.lowercase()}:${item.chapterName.lowercase()}"
            if (existingKeys.contains(key)) {
                duplicates.add(item)
            } else {
                newChapters.add(item)
            }
        }

        if (duplicates.isNotEmpty()) {
            warnings.add("${duplicates.size} existing chapters will be updated & remapped into the 80-day schedule.")
        }

        val unassessedCount = validated.count { !it.isAssessed }
        if (unassessedCount > 0) {
            warnings.add("$unassessedCount chapters are marked 'Not assessed' (counts pending).")
        }

        val totalRemainingLecs = validated.filter { it.isAssessed }
            .sumOf { ((it.totalLectures ?: 0) - it.completedLectures).coerceAtLeast(0) }

        return ImportValidationResult(
            parsedChapters = validated,
            newChapters = newChapters,
            duplicateChapters = duplicates,
            invalidLinesCount = 0,
            warnings = warnings,
            isValid = true,
            summaryText = "Validated ${validated.size} chapters (${newChapters.size} new, ${duplicates.size} updates) • Maps $totalRemainingLecs remaining lectures across the 80-Day Schedule."
        )
    }

    fun parseChaptersFromJson(jsonString: String): List<ChapterEntity> {
        val list = mutableListOf<ChapterEntity>()
        try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val subject = obj.optString("subject", "Physics")
                val name = obj.optString("chapterName", "Chapter ${i + 1}")
                val totalLecs = if (obj.has("totalLectures") && !obj.isNull("totalLectures")) obj.getInt("totalLectures") else null
                val compLecs = obj.optInt("completedLectures", 0)
                val notes = obj.optBoolean("notesDone", false)
                val diby = if (obj.has("dibyTotal") && !obj.isNull("dibyTotal")) obj.getInt("dibyTotal") else null
                val dibyDone = if (obj.has("dibyDone") && !obj.isNull("dibyDone")) obj.getInt("dibyDone") else 0
                val dpp = if (obj.has("dppTotal") && !obj.isNull("dppTotal")) obj.getInt("dppTotal") else null
                val dppDone = if (obj.has("dppDone") && !obj.isNull("dppDone")) obj.getInt("dppDone") else 0
                val pyq = if (obj.has("pyqTotal") && !obj.isNull("pyqTotal")) obj.getInt("pyqTotal") else null
                val pyqDone = if (obj.has("pyqDone") && !obj.isNull("pyqDone")) obj.getInt("pyqDone") else 0
                val worksheets = if (obj.has("worksheetsTotal") && !obj.isNull("worksheetsTotal")) obj.getInt("worksheetsTotal") else null
                val worksheetsDone = if (obj.has("worksheetsDone") && !obj.isNull("worksheetsDone")) obj.getInt("worksheetsDone") else 0
                val status = obj.optString("status", if (totalLecs == null) "NOT_ASSESSED" else "NOT_STARTED")
                val isWeak = obj.optBoolean("isWeak", false)
                val masteryLevel = obj.optString("masteryLevel", "NOT_ASSESSED")
                val lastStudiedDate = obj.optString("lastStudiedDate", "")
                val revisionStage = obj.optInt("revisionStage", 0)
                val nextRevisionDate = obj.optString("nextRevisionDate", "")

                list.add(
                    ChapterEntity(
                        subject = subject,
                        chapterName = name,
                        orderIndex = i + 1,
                        totalLectures = totalLecs,
                        completedLectures = compLecs,
                        notesDone = notes,
                        dibyTotal = diby,
                        dibyDone = dibyDone,
                        classProblemsTotal = if (totalLecs != null) 30 else null,
                        classProblemsDone = 0,
                        dppTotal = dpp,
                        dppDone = dppDone,
                        pyqTotal = pyq,
                        pyqDone = pyqDone,
                        worksheetsTotal = worksheets,
                        worksheetsDone = worksheetsDone,
                        status = status,
                        isWeak = isWeak,
                        masteryLevel = masteryLevel,
                        lastStudiedDate = lastStudiedDate,
                        revisionStage = revisionStage,
                        nextRevisionDate = nextRevisionDate
                    )
                )
            }
        } catch (_: Exception) {
            return parseChaptersFromCsv(jsonString)
        }
        return list
    }

    fun parseChaptersFromCsv(csvContent: String): List<ChapterEntity> {
        val list = mutableListOf<ChapterEntity>()
        val lines = csvContent.lines()
        var idx = 1
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("Subject", ignoreCase = true)) {
                continue
            }
            val parts = trimmed.split(",")
            if (parts.size >= 2) {
                val subject = parts[0].trim().removeSurrounding("\"")
                val chapterName = parts[1].trim().removeSurrounding("\"")
                val lecs = parts.getOrNull(2)?.trim()?.toIntOrNull()
                val compLecs = parts.getOrNull(3)?.trim()?.toIntOrNull() ?: 0
                val dpp = parts.getOrNull(4)?.trim()?.toIntOrNull()
                val pyq = parts.getOrNull(5)?.trim()?.toIntOrNull()

                val status = if (lecs == null) "NOT_ASSESSED" else if (compLecs >= lecs) "COMPLETED" else if (compLecs > 0) "IN_PROGRESS" else "NOT_STARTED"

                list.add(
                    ChapterEntity(
                        subject = subject,
                        chapterName = chapterName,
                        orderIndex = idx++,
                        totalLectures = lecs,
                        completedLectures = compLecs,
                        notesDone = false,
                        dibyTotal = if (lecs != null) 20 else null,
                        dibyDone = 0,
                        classProblemsTotal = if (lecs != null) 30 else null,
                        classProblemsDone = 0,
                        dppTotal = dpp,
                        dppDone = 0,
                        pyqTotal = pyq,
                        pyqDone = 0,
                        worksheetsTotal = if (lecs != null) 2 else null,
                        worksheetsDone = 0,
                        status = status,
                        isWeak = false
                    )
                )
            }
        }
        return list
    }

    fun exportChaptersToJson(chapters: List<ChapterEntity>): String {
        val array = JSONArray()
        for (c in chapters) {
            val obj = JSONObject()
            obj.put("subject", c.subject)
            obj.put("chapterName", c.chapterName)
            if (c.totalLectures != null) obj.put("totalLectures", c.totalLectures) else obj.put("totalLectures", JSONObject.NULL)
            obj.put("completedLectures", c.completedLectures)
            obj.put("notesDone", c.notesDone)
            if (c.dibyTotal != null) obj.put("dibyTotal", c.dibyTotal) else obj.put("dibyTotal", JSONObject.NULL)
            obj.put("dibyDone", c.dibyDone ?: 0)
            if (c.dppTotal != null) obj.put("dppTotal", c.dppTotal) else obj.put("dppTotal", JSONObject.NULL)
            obj.put("dppDone", c.dppDone ?: 0)
            if (c.pyqTotal != null) obj.put("pyqTotal", c.pyqTotal) else obj.put("pyqTotal", JSONObject.NULL)
            obj.put("pyqDone", c.pyqDone ?: 0)
            if (c.worksheetsTotal != null) obj.put("worksheetsTotal", c.worksheetsTotal) else obj.put("worksheetsTotal", JSONObject.NULL)
            obj.put("worksheetsDone", c.worksheetsDone ?: 0)
            obj.put("status", c.status)
            obj.put("isWeak", c.isWeak)
            obj.put("masteryLevel", c.masteryLevel)
            obj.put("lastStudiedDate", c.lastStudiedDate)
            obj.put("revisionStage", c.revisionStage)
            obj.put("nextRevisionDate", c.nextRevisionDate)
            array.put(obj)
        }
        return array.toString(2)
    }

    fun exportChaptersToCsv(chapters: List<ChapterEntity>): String {
        val sb = StringBuilder()
        sb.append("Subject,ChapterName,TotalLectures,CompletedLectures,DPP,PYQ,Status\n")
        for (c in chapters) {
            sb.append("${c.subject},\"${c.chapterName}\",${c.totalLectures ?: ""},${c.completedLectures},${c.dppTotal ?: ""},${c.pyqTotal ?: ""},${c.status}\n")
        }
        return sb.toString()
    }
}
