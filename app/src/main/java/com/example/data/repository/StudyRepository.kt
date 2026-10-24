package com.example.data.repository

import androidx.room.withTransaction
import com.example.data.focus.FocusGuardManager
import com.example.data.importer.BacklogImportExport
import com.example.data.importer.CurriculumIngestionReport
import com.example.data.importer.CurriculumScheduleIngestionUtility
import com.example.data.local.AppDatabase
import com.example.data.local.entities.ChapterEntity
import com.example.data.local.entities.DailyScheduleEntity
import com.example.data.local.entities.FocusSessionEntity
import com.example.data.local.entities.MasteryLevel
import com.example.data.local.entities.MistakeEntryEntity
import com.example.data.local.entities.RevisionItemEntity
import com.example.data.local.entities.SyncConflictEntity
import com.example.data.local.entities.SyncEntityType
import com.example.data.local.entities.SyncIdentityHelper
import com.example.data.local.entities.SyncOperationType
import com.example.data.local.entities.SyncOutboxEntity
import com.example.data.local.entities.SyncRecordState
import com.example.data.local.entities.SyncWorkbookConfigEntity
import com.example.data.local.entities.TestRecordEntity
import com.example.data.local.entities.UserSettingsEntity
import com.example.data.local.entities.XpEventEntity
import com.example.data.progression.ProgressionManager
import com.example.data.revision.SpacedRevisionManager
import com.example.data.sync.WorkbookMappingEngine
import com.example.util.DateUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class StudyRepository(private val db: AppDatabase) {

    val allChapters: Flow<List<ChapterEntity>> = db.chapterDao().getAllChapters()
    val weakChapters: Flow<List<ChapterEntity>> = db.chapterDao().getWeakChapters()
    val allSchedules: Flow<List<DailyScheduleEntity>> = db.dailyScheduleDao().getAllDays()
    val allTests: Flow<List<TestRecordEntity>> = db.testRecordDao().getAllTests()
    val completedTests: Flow<List<TestRecordEntity>> = db.testRecordDao().getCompletedTests()
    val allMistakes: Flow<List<MistakeEntryEntity>> = db.mistakeEntryDao().getAllMistakes()
    val pendingRevisions: Flow<List<RevisionItemEntity>> = db.revisionItemDao().getPendingRevisions()
    val allRevisions: Flow<List<RevisionItemEntity>> = db.revisionItemDao().getAllRevisions()
    val userSettings: Flow<UserSettingsEntity?> = db.userSettingsDao().getSettings()
    val activeFocusSession: Flow<FocusSessionEntity?> = db.focusSessionDao().getActiveSession()
    val allXpEvents: Flow<List<XpEventEntity>> = db.xpEventDao().getAllXpEvents()

    // Two-Way Google Sheets Sync reactive streams
    val workbookConfig: Flow<SyncWorkbookConfigEntity?> = db.syncDao().getWorkbookConfigFlow()
    val pendingOutboxCount: Flow<Int> = db.syncDao().getPendingOutboxCountFlow()
    val unresolvedConflicts: Flow<List<SyncConflictEntity>> = db.syncDao().getUnresolvedConflictsFlow()

    suspend fun initializeIfEmpty() = withContext(Dispatchers.IO) {
        val chapterCount = db.chapterDao().getChapterCount()
        val scheduleCount = db.dailyScheduleDao().getScheduleCount()
        if (chapterCount == 0) {
            val standardChapters = BacklogImportExport.getStandardJeeTemplate()
            CurriculumScheduleIngestionUtility.ingestIntoRoomDatabase(
                db = db,
                rawChapters = standardChapters,
                replaceExistingChapters = true
            )
        } else if (scheduleCount == 0) {
            val existingChapters = db.chapterDao().getAllChaptersSync()
            val mappedDays = CurriculumScheduleIngestionUtility.mapChaptersInto80DaySchedules(
                validatedChapters = existingChapters,
                existingSchedules = emptyList()
            )
            db.dailyScheduleDao().insertAll(mappedDays)
        } else {
            val existingDays = db.dailyScheduleDao().getAllDaysSync()
            val hasAssignedSchedules = existingDays.any { it.assignedChapterNames.isNotBlank() }
            if (!hasAssignedSchedules) {
                val existingChapters = db.chapterDao().getAllChaptersSync()
                if (existingChapters.isNotEmpty()) {
                    val mappedDays = CurriculumScheduleIngestionUtility.mapChaptersInto80DaySchedules(
                        validatedChapters = existingChapters,
                        existingSchedules = existingDays
                    )
                    db.dailyScheduleDao().insertAll(mappedDays)
                }
            }
        }

        val testCount = db.testRecordDao().getTestCount()
        if (testCount == 0) {
            val tests = BacklogImportExport.generateSampleSundayTests()
            db.testRecordDao().insertAll(tests)
        }

        val settings = db.userSettingsDao().getSettingsSync()
        if (settings == null) {
            db.userSettingsDao().insertOrUpdate(UserSettingsEntity())
        }

        // Restore active Focus Session across process death
        val persistedSession = db.focusSessionDao().getActiveSessionSync()
        FocusGuardManager.syncActiveSession(persistedSession)
    }

    /**
     * Awards XP idempotently using [eventKey] as primary key and enforcing the daily XP cap.
     * Never awards duplicate XP on repeated taps or imports.
     */
    suspend fun awardIdempotentXp(
        eventKey: String,
        category: String,
        description: String,
        requestedXp: Int,
        dateStr: String = DateUtils.todayDateString()
    ): Int = withContext(Dispatchers.IO) {
        val existing = db.xpEventDao().getEventByKey(eventKey)
        if (existing != null) return@withContext 0

        val earnedToday = db.xpEventDao().getXpEarnedOnDate(dateStr)
        val clampedXp = ProgressionManager.clampAwardToDailyCap(requestedXp, earnedToday)
        if (clampedXp <= 0) return@withContext 0

        val rowId = db.xpEventDao().insertXpEventIgnoreConflict(
            XpEventEntity(
                eventKey = eventKey,
                category = category,
                description = description,
                xpAmount = clampedXp,
                dateString = dateStr
            )
        )
        if (rowId != -1L) clampedXp else 0
    }

    suspend fun revokeXpByKey(eventKey: String) = withContext(Dispatchers.IO) {
        db.xpEventDao().deleteEventByKey(eventKey)
    }

    suspend fun seedOfficialTemplate(): CurriculumIngestionReport = withContext(Dispatchers.IO) {
        val chapters = BacklogImportExport.getStandardJeeTemplate()
        val report = CurriculumScheduleIngestionUtility.ingestIntoRoomDatabase(
            db = db,
            rawChapters = chapters,
            replaceExistingChapters = true
        )
        enqueueAllChaptersAndSchedulesForSync()
        report
    }

    /**
     * Imports chapters from JSON or CSV text, validates against curriculum standards,
     * and maps them into the 80-day schedule database without awarding XP.
     */
    suspend fun importData(content: String, replaceExisting: Boolean = false): CurriculumIngestionReport = withContext(Dispatchers.IO) {
        val parsed = BacklogImportExport.parseChaptersFromJson(content)
        val report = CurriculumScheduleIngestionUtility.ingestIntoRoomDatabase(
            db = db,
            rawChapters = parsed,
            replaceExistingChapters = replaceExisting
        )
        enqueueAllChaptersAndSchedulesForSync()
        report
    }

    /**
     * Imports chapters directly from a binary .xlsx spreadsheet, validates against curriculum standards,
     * and maps them into the 80-day schedule database.
     */
    suspend fun importXlsxBytes(xlsxBytes: ByteArray, replaceExisting: Boolean = false): CurriculumIngestionReport = withContext(Dispatchers.IO) {
        val parsed = CurriculumScheduleIngestionUtility.parseXlsxBytes(xlsxBytes)
        val report = CurriculumScheduleIngestionUtility.ingestIntoRoomDatabase(
            db = db,
            rawChapters = parsed,
            replaceExistingChapters = replaceExisting
        )
        enqueueAllChaptersAndSchedulesForSync()
        report
    }

    /**
     * Re-maps the current backlog chapters into the 80-day schedule database so each day receives
     * an updated, interleaved Physics, Chemistry, and Mathematics study plan.
     */
    suspend fun syncScheduleFromCurrentBacklog(): CurriculumIngestionReport = withContext(Dispatchers.IO) {
        val currentChapters = db.chapterDao().getAllChaptersSync()
        val existingSchedules = db.dailyScheduleDao().getAllDaysSync()
        val report = CurriculumScheduleIngestionUtility.buildIngestionPlan(
            rawChapters = currentChapters,
            existingSchedules = existingSchedules
        )
        if (report.mappedSchedules.isNotEmpty()) {
            db.withTransaction {
                val now = System.currentTimeMillis()
                val updatedSchedules = report.mappedSchedules.map { day ->
                    val syncId = day.effectiveSyncId
                    day.copy(
                        syncId = syncId,
                        updatedAt = now,
                        syncState = SyncRecordState.PENDING.dbKey
                    )
                }
                db.dailyScheduleDao().insertAll(updatedSchedules)
                for (day in updatedSchedules) {
                    enqueueOutboxOperation(
                        entityType = SyncEntityType.DAILY_SCHEDULE,
                        syncId = day.effectiveSyncId,
                        operationType = SyncOperationType.UPSERT,
                        payloadJson = WorkbookMappingEngine.serializeSchedule(day),
                        baseVersionHash = day.remoteVersionHash
                    )
                }
            }
        }
        report
    }

    private suspend fun enqueueAllChaptersAndSchedulesForSync() {
        db.withTransaction {
            val allCh = db.chapterDao().getAllChaptersSync()
            for (ch in allCh) {
                val syncId = ch.effectiveSyncId
                enqueueOutboxOperation(
                    entityType = SyncEntityType.CHAPTER,
                    syncId = syncId,
                    operationType = SyncOperationType.UPSERT,
                    payloadJson = WorkbookMappingEngine.serializeChapter(ch.copy(syncId = syncId)),
                    baseVersionHash = ch.remoteVersionHash
                )
            }
            val allSched = db.dailyScheduleDao().getAllDaysSync()
            for (day in allSched) {
                val syncId = day.effectiveSyncId
                enqueueOutboxOperation(
                    entityType = SyncEntityType.DAILY_SCHEDULE,
                    syncId = syncId,
                    operationType = SyncOperationType.UPSERT,
                    payloadJson = WorkbookMappingEngine.serializeSchedule(day.copy(syncId = syncId)),
                    baseVersionHash = day.remoteVersionHash
                )
            }
        }
    }

    /**
     * Saves chapter task progress atomically with a SyncOutboxEntity record.
     * Note: Task completion (status = COMPLETED) is strictly separate from conceptual mastery (masteryLevel).
     */
    suspend fun saveChapter(chapter: ChapterEntity) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val newStatus = when {
            !chapter.isAssessed -> "NOT_ASSESSED"
            chapter.completionPercentage >= 100 -> "COMPLETED"
            chapter.completionPercentage > 0 -> "IN_PROGRESS"
            else -> "NOT_STARTED"
        }
        db.withTransaction {
            val provisionalSyncId = chapter.syncId.ifBlank {
                if (chapter.id > 0L) {
                    SyncIdentityHelper.deterministicChapterBackfillId(
                        id = chapter.id,
                        subject = chapter.subject,
                        chapterName = chapter.chapterName,
                        orderIndex = chapter.orderIndex
                    )
                } else {
                    SyncIdentityHelper.newUuid("ch")
                }
            }
            val toSave = chapter.copy(
                syncId = provisionalSyncId,
                status = newStatus,
                lastUpdated = now,
                syncState = SyncRecordState.PENDING.dbKey,
                isDeleted = false
            )
            val finalEntity = if (toSave.id == 0L) {
                val newId = db.chapterDao().insertChapter(toSave)
                val finalSyncId = chapter.syncId.ifBlank {
                    SyncIdentityHelper.deterministicChapterBackfillId(
                        id = newId,
                        subject = chapter.subject,
                        chapterName = chapter.chapterName,
                        orderIndex = chapter.orderIndex
                    )
                }
                val withId = toSave.copy(id = newId, syncId = finalSyncId)
                if (finalSyncId != provisionalSyncId) {
                    db.chapterDao().updateChapter(withId)
                }
                withId
            } else {
                db.chapterDao().updateChapter(toSave)
                toSave
            }

            enqueueOutboxOperation(
                entityType = SyncEntityType.CHAPTER,
                syncId = finalEntity.effectiveSyncId,
                operationType = SyncOperationType.UPSERT,
                payloadJson = WorkbookMappingEngine.serializeChapter(finalEntity),
                baseVersionHash = finalEntity.remoteVersionHash
            )
        }
    }

    suspend fun updateChapterMastery(chapter: ChapterEntity, mastery: MasteryLevel) = withContext(Dispatchers.IO) {
        val updated = chapter.copy(
            masteryLevel = mastery.dbKey,
            isWeak = mastery == MasteryLevel.NEEDS_REVISION || chapter.isWeak,
            lastUpdated = System.currentTimeMillis()
        )
        saveChapter(updated)
    }

    suspend fun deleteChapter(id: Long) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        db.withTransaction {
            val existing = db.chapterDao().getChapterById(id)
            if (existing != null) {
                val syncId = existing.effectiveSyncId
                val tombstone = existing.copy(
                    syncId = syncId,
                    isDeleted = true,
                    lastUpdated = now,
                    syncState = SyncRecordState.PENDING.dbKey
                )
                db.chapterDao().updateChapter(tombstone)
                enqueueOutboxOperation(
                    entityType = SyncEntityType.CHAPTER,
                    syncId = syncId,
                    operationType = SyncOperationType.DELETE,
                    payloadJson = WorkbookMappingEngine.serializeChapter(tombstone),
                    baseVersionHash = existing.remoteVersionHash
                )
            }
        }
    }

    suspend fun clearChapters() = withContext(Dispatchers.IO) {
        db.chapterDao().clearAllChapters()
    }

    suspend fun updateSchedule(schedule: DailyScheduleEntity) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val isTargetMet = schedule.completedLectures >= schedule.targetLectures &&
                schedule.completedProblems >= schedule.targetProblems
        val syncId = schedule.effectiveSyncId
        val updated = schedule.copy(
            syncId = syncId,
            isCompleted = isTargetMet,
            updatedAt = now,
            syncState = SyncRecordState.PENDING.dbKey,
            isDeleted = false
        )
        db.withTransaction {
            db.dailyScheduleDao().updateSchedule(updated)
            enqueueOutboxOperation(
                entityType = SyncEntityType.DAILY_SCHEDULE,
                syncId = syncId,
                operationType = SyncOperationType.UPSERT,
                payloadJson = WorkbookMappingEngine.serializeSchedule(updated),
                baseVersionHash = schedule.remoteVersionHash
            )
        }
    }

    suspend fun recordStudySession(date: String, studyMinutes: Int, breakMinutes: Int) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        db.withTransaction {
            val current = db.dailyScheduleDao().getScheduleForDateSync(date)
            if (current != null) {
                val syncId = current.effectiveSyncId
                val updated = current.copy(
                    syncId = syncId,
                    studyMinutes = current.studyMinutes + studyMinutes,
                    breakMinutes = current.breakMinutes + breakMinutes,
                    updatedAt = now,
                    syncState = SyncRecordState.PENDING.dbKey
                )
                db.dailyScheduleDao().updateSchedule(updated)
                enqueueOutboxOperation(
                    entityType = SyncEntityType.DAILY_SCHEDULE,
                    syncId = syncId,
                    operationType = SyncOperationType.UPSERT,
                    payloadJson = WorkbookMappingEngine.serializeSchedule(updated),
                    baseVersionHash = current.remoteVersionHash
                )
            }
        }
    }

    suspend fun rebalanceSchedule(fromDayNumber: Int) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val currentChapters = db.chapterDao().getAllChaptersSync()
        val existingSchedules = db.dailyScheduleDao().getAllDaysSync()
        db.withTransaction {
            if (currentChapters.isNotEmpty()) {
                val remapped = CurriculumScheduleIngestionUtility.mapChaptersInto80DaySchedules(
                    validatedChapters = currentChapters,
                    existingSchedules = existingSchedules
                )
                for (day in remapped) {
                    if (day.dayNumber >= fromDayNumber && !day.isCompleted) {
                        val syncId = day.effectiveSyncId
                        val updated = day.copy(
                            syncId = syncId,
                            sleepHours = 7.5f,
                            updatedAt = now,
                            syncState = SyncRecordState.PENDING.dbKey
                        )
                        db.dailyScheduleDao().updateSchedule(updated)
                        enqueueOutboxOperation(
                            entityType = SyncEntityType.DAILY_SCHEDULE,
                            syncId = syncId,
                            operationType = SyncOperationType.UPSERT,
                            payloadJson = WorkbookMappingEngine.serializeSchedule(updated),
                            baseVersionHash = day.remoteVersionHash
                        )
                    }
                }
            } else {
                for (day in fromDayNumber..80) {
                    val schedule = db.dailyScheduleDao().getScheduleForDayNumber(day)
                    if (schedule != null && !schedule.isCompleted) {
                        val targetLec = if (schedule.isSunday) 1 else 4
                        val syncId = schedule.effectiveSyncId
                        val updated = schedule.copy(
                            syncId = syncId,
                            targetLectures = targetLec,
                            targetProblems = if (schedule.isSunday) 15 else 25,
                            sleepHours = 7.5f,
                            updatedAt = now,
                            syncState = SyncRecordState.PENDING.dbKey
                        )
                        db.dailyScheduleDao().updateSchedule(updated)
                        enqueueOutboxOperation(
                            entityType = SyncEntityType.DAILY_SCHEDULE,
                            syncId = syncId,
                            operationType = SyncOperationType.UPSERT,
                            payloadJson = WorkbookMappingEngine.serializeSchedule(updated),
                            baseVersionHash = schedule.remoteVersionHash
                        )
                    }
                }
            }
        }
    }

    suspend fun autoEvaluateSundayTest(test: TestRecordEntity) = withContext(Dispatchers.IO) {
        val currentChapters = db.chapterDao().getAllChaptersSync()
        val evaluated = BacklogImportExport.autoGenerateMockScoreForTest(test, currentChapters)
        saveTestRecord(evaluated)
    }

    suspend fun autoEvaluateAllSundayTests() = withContext(Dispatchers.IO) {
        val currentChapters = db.chapterDao().getAllChaptersSync()
        val tests = BacklogImportExport.generateSampleSundayTests()
        db.testRecordDao().clearAllTests()
        val evaluatedList = tests.mapIndexed { idx, t ->
            if (idx < 6 || t.isAssessed) {
                if (t.isAssessed) t else BacklogImportExport.autoGenerateMockScoreForTest(t.copy(id = (idx + 1).toLong()), currentChapters)
            } else {
                BacklogImportExport.autoGenerateMockScoreForTest(t.copy(id = (idx + 1).toLong()), currentChapters)
            }
        }
        db.testRecordDao().insertAll(evaluatedList)
    }

    suspend fun saveTestRecord(test: TestRecordEntity) = withContext(Dispatchers.IO) {
        val id = if (test.id == 0L) {
            db.testRecordDao().insertTest(test)
        } else {
            db.testRecordDao().updateTest(test)
            test.id
        }

        if (test.isAssessed && test.weakTopics.isNotBlank()) {
            val weakTopicList = test.weakTopics.split(",").map { it.trim() }.filter { it.isNotBlank() }
            val todayStr = DateUtils.todayDateString()
            for ((idx, topic) in weakTopicList.withIndex()) {
                val syntheticChapterId = id * 1000L + idx
                val existing = db.revisionItemDao().getActiveRevisionForChapter(syntheticChapterId)
                if (existing == null) {
                    db.revisionItemDao().insertRevision(
                        RevisionItemEntity(
                            chapterId = syntheticChapterId,
                            chapterName = topic.replaceFirstChar { it.uppercase() },
                            subject = "Test Review",
                            stage = 1,
                            dueDate = todayStr,
                            lastRevisedDate = todayStr,
                            isCompleted = false
                        )
                    )
                }
            }
        }
    }

    suspend fun deleteTest(id: Long) = withContext(Dispatchers.IO) {
        db.testRecordDao().deleteTest(id)
    }

    /**
     * Saves or updates a Mistake Notebook entry atomically with a SyncOutboxEntity record.
     */
    suspend fun saveMistake(mistake: MistakeEntryEntity) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        db.withTransaction {
            val existing = if (mistake.id != 0L) {
                db.mistakeEntryDao().getMistakeById(mistake.id)
            } else if (mistake.syncId.isNotBlank()) {
                db.mistakeEntryDao().getMistakeBySyncId(mistake.syncId)
            } else {
                null
            }

            val provisionalSyncId = mistake.syncId.ifBlank {
                existing?.syncId?.takeIf { it.isNotBlank() } ?: SyncIdentityHelper.newUuid("mk")
            }

            val toSave = mistake.copy(
                id = existing?.id ?: mistake.id,
                syncId = provisionalSyncId,
                createdAt = existing?.createdAt ?: mistake.createdAt,
                updatedAt = now,
                lastSyncedAt = existing?.lastSyncedAt ?: mistake.lastSyncedAt,
                remoteVersionHash = existing?.remoteVersionHash ?: mistake.remoteVersionHash,
                lastSyncedBaselineJson = existing?.lastSyncedBaselineJson ?: mistake.lastSyncedBaselineJson,
                syncState = SyncRecordState.PENDING.dbKey,
                isDeleted = false
            )

            val savedEntity = if (toSave.id == 0L) {
                val newId = db.mistakeEntryDao().insertMistake(toSave)
                toSave.copy(id = newId)
            } else {
                db.mistakeEntryDao().updateMistake(toSave)
                toSave
            }

            enqueueOutboxOperation(
                entityType = SyncEntityType.MISTAKE_ENTRY,
                syncId = savedEntity.effectiveSyncId,
                operationType = SyncOperationType.UPSERT,
                payloadJson = WorkbookMappingEngine.serializeMistake(savedEntity),
                baseVersionHash = savedEntity.remoteVersionHash
            )
        }
    }

    /**
     * Soft-deletes a Mistake Notebook entry using a tombstone (`isDeleted = true`) and atomically queues
     * a DELETE outbox operation so Google Sheets sync deletes/marks it deleted without ghost resurrection.
     */
    suspend fun deleteMistake(id: Long) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        db.withTransaction {
            val existing = db.mistakeEntryDao().getMistakeById(id)
            if (existing != null) {
                val syncId = existing.effectiveSyncId
                val tombstone = existing.copy(
                    syncId = syncId,
                    isDeleted = true,
                    updatedAt = now,
                    syncState = SyncRecordState.PENDING.dbKey
                )
                db.mistakeEntryDao().updateMistake(tombstone)
                enqueueOutboxOperation(
                    entityType = SyncEntityType.MISTAKE_ENTRY,
                    syncId = syncId,
                    operationType = SyncOperationType.DELETE,
                    payloadJson = WorkbookMappingEngine.serializeMistake(tombstone),
                    baseVersionHash = existing.remoteVersionHash
                )
            }
        }
    }

    private suspend fun enqueueOutboxOperation(
        entityType: SyncEntityType,
        syncId: String,
        operationType: SyncOperationType,
        payloadJson: String,
        baseVersionHash: String
    ) {
        val existingPending = db.syncDao().getActiveOutboxForRecord(entityType.dbKey, syncId)
        val opId = existingPending?.operationId ?: "${entityType.dbKey}_${syncId}_pending"
        val now = System.currentTimeMillis()
        db.syncDao().insertOutbox(
            SyncOutboxEntity(
                operationId = opId,
                entityType = entityType.dbKey,
                syncId = syncId,
                operationType = operationType.dbKey,
                payloadJson = payloadJson,
                baseVersionHash = baseVersionHash.ifBlank { existingPending?.baseVersionHash.orEmpty() },
                status = SyncRecordState.PENDING.dbKey,
                retryCount = existingPending?.retryCount ?: 0,
                lastError = "",
                createdAt = existingPending?.createdAt ?: now,
                updatedAt = now
            )
        )
    }

    /**
     * Marks a chapter studied/revised today and creates or resets its Stage 1 spaced revision schedule
     * without creating duplicate active rows.
     */
    suspend fun markChapterStudiedAndScheduleRevision(
        chapter: ChapterEntity,
        todayStr: String = DateUtils.todayDateString()
    ): RevisionItemEntity = withContext(Dispatchers.IO) {
        val settings = db.userSettingsDao().getSettingsSync()
        val intervals = settings?.parsedRevisionIntervals ?: SpacedRevisionManager.DEFAULT_INTERVALS
        val existingActive = db.revisionItemDao().getActiveRevisionForChapter(chapter.id)

        val revisionItem = SpacedRevisionManager.createOrResetRevisionForChapter(
            chapter = chapter,
            existingActive = existingActive,
            intervals = intervals,
            todayDateStr = todayStr
        )

        val savedId = if (revisionItem.id == 0L) {
            db.revisionItemDao().insertRevision(revisionItem)
        } else {
            db.revisionItemDao().updateRevision(revisionItem)
            revisionItem.id
        }

        val updatedChapter = chapter.copy(
            lastStudiedDate = todayStr,
            revisionStage = 1,
            nextRevisionDate = revisionItem.dueDate,
            lastUpdated = System.currentTimeMillis()
        )
        saveChapter(updatedChapter)

        revisionItem.copy(id = savedId)
    }

    /**
     * Completes the current revision stage and advances to the next configured interval (e.g. 1 -> 3 -> 7 -> 21d),
     * awarding idempotent XP for the completed revision stage.
     */
    suspend fun completeRevision(item: RevisionItemEntity) = withContext(Dispatchers.IO) {
        val todayStr = DateUtils.todayDateString()
        val settings = db.userSettingsDao().getSettingsSync()
        val intervals = settings?.parsedRevisionIntervals ?: SpacedRevisionManager.DEFAULT_INTERVALS

        val advanced = SpacedRevisionManager.advanceStage(
            item = item,
            intervals = intervals,
            todayDateStr = todayStr
        )
        db.revisionItemDao().updateRevision(advanced)

        // Update linked chapter's revision metadata if present
        val linkedChapter = db.chapterDao().getChapterById(item.chapterId)
        if (linkedChapter != null) {
            saveChapter(
                linkedChapter.copy(
                    lastStudiedDate = todayStr,
                    revisionStage = if (advanced.isCompleted) intervals.size + 1 else advanced.stage,
                    nextRevisionDate = if (advanced.isCompleted) "" else advanced.effectiveDueDate
                )
            )
        }

        awardIdempotentXp(
            eventKey = ProgressionManager.revisionEventKey(item.id, item.stage, todayStr),
            category = "REVISION",
            description = "Completed Stage ${item.stage} revision: ${item.chapterName}",
            requestedXp = ProgressionManager.XP_REVISION_COMPLETED,
            dateStr = todayStr
        )
    }

    suspend fun snoozeRevision(item: RevisionItemEntity, days: Int) = withContext(Dispatchers.IO) {
        val snoozed = SpacedRevisionManager.snoozeRevision(item, days)
        db.revisionItemDao().updateRevision(snoozed)
        val linkedChapter = db.chapterDao().getChapterById(item.chapterId)
        if (linkedChapter != null) {
            saveChapter(linkedChapter.copy(nextRevisionDate = snoozed.effectiveDueDate))
        }
    }

    suspend fun rescheduleRevision(item: RevisionItemEntity, newDateStr: String) = withContext(Dispatchers.IO) {
        val rescheduled = SpacedRevisionManager.rescheduleRevision(item, newDateStr)
        db.revisionItemDao().updateRevision(rescheduled)
        val linkedChapter = db.chapterDao().getChapterById(item.chapterId)
        if (linkedChapter != null) {
            saveChapter(linkedChapter.copy(nextRevisionDate = rescheduled.effectiveDueDate))
        }
    }

    suspend fun deleteRevision(id: Long) = withContext(Dispatchers.IO) {
        db.revisionItemDao().deleteRevision(id)
    }

    suspend fun updateSettings(settings: UserSettingsEntity) = withContext(Dispatchers.IO) {
        db.userSettingsDao().insertOrUpdate(settings)
    }

    suspend fun startFocusSession(
        studyDurationMinutes: Int,
        permittedBreaks: Int,
        breakDurationMinutes: Int,
        blockedPackagesCsv: String,
        activeTaskName: String,
        extraSeconds: Int = 0
    ) = withContext(Dispatchers.IO) {
        val session = FocusGuardManager.startSession(
            studyDurationMinutes = studyDurationMinutes,
            permittedBreaks = permittedBreaks,
            breakDurationMinutes = breakDurationMinutes,
            blockedPackagesCsv = blockedPackagesCsv,
            activeTaskName = activeTaskName,
            extraSeconds = extraSeconds
        )
        db.focusSessionDao().insertOrUpdate(session)
    }

    suspend fun takeFocusBreak() = withContext(Dispatchers.IO) {
        val current = db.focusSessionDao().getActiveSessionSync() ?: return@withContext
        if (current.breaksRemaining > 0 && (current.sessionStatus == "RUNNING" || current.sessionStatus == "PAUSED")) {
            val updated = FocusGuardManager.takeBreak(current)
            db.focusSessionDao().insertOrUpdate(updated)
        }
    }

    suspend fun endFocusBreak() = withContext(Dispatchers.IO) {
        val current = db.focusSessionDao().getActiveSessionSync() ?: return@withContext
        val updated = FocusGuardManager.endBreak(current)
        db.focusSessionDao().insertOrUpdate(updated)
    }

    suspend fun pauseFocusSession() = withContext(Dispatchers.IO) {
        val current = db.focusSessionDao().getActiveSessionSync() ?: return@withContext
        val updated = FocusGuardManager.pauseSession(current)
        db.focusSessionDao().insertOrUpdate(updated)
    }

    suspend fun resumeFocusSession() = withContext(Dispatchers.IO) {
        val current = db.focusSessionDao().getActiveSessionSync() ?: return@withContext
        val updated = FocusGuardManager.resumeSession(current)
        db.focusSessionDao().insertOrUpdate(updated)
    }

    suspend fun cancelFocusSession() = withContext(Dispatchers.IO) {
        val current = db.focusSessionDao().getActiveSessionSync() ?: return@withContext
        val updated = FocusGuardManager.cancelSession(current)
        db.focusSessionDao().insertOrUpdate(updated)
    }

    /**
     * Completes an active Focus Session once and prevents duplicate completion rewards.
     */
    suspend fun completeFocusSession(): FocusSessionEntity? = withContext(Dispatchers.IO) {
        val current = db.focusSessionDao().getActiveSessionSync() ?: return@withContext null
        if (current.sessionStatus == "COMPLETED" || current.sessionStatus == "CANCELLED") {
            return@withContext null
        }
        val updated = FocusGuardManager.completeSession(current)
        db.focusSessionDao().insertOrUpdate(updated)

        val todayStr = DateUtils.todayDateString()
        recordStudySession(
            date = todayStr,
            studyMinutes = current.studyDurationMinutes,
            breakMinutes = (current.permittedBreaks - current.breaksRemaining).coerceAtLeast(0) * current.breakDurationMinutes
        )

        awardIdempotentXp(
            eventKey = ProgressionManager.focusSessionEventKey(current.sessionStartTimeMillis),
            category = "FOCUS",
            description = "Completed ${current.studyDurationMinutes}m Focus Session: ${current.activeTaskName}",
            requestedXp = ProgressionManager.XP_FOCUS_SESSION_COMPLETED,
            dateStr = todayStr
        )

        updated
    }
}
