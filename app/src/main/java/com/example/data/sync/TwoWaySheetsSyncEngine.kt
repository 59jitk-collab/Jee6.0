package com.example.data.sync

import android.content.Context
import android.provider.Settings
import androidx.room.withTransaction
import com.example.data.importer.CurriculumScheduleIngestionUtility
import com.example.data.local.AppDatabase
import com.example.data.local.entities.ChapterEntity
import com.example.data.local.entities.DailyScheduleEntity
import com.example.data.local.entities.MistakeEntryEntity
import com.example.data.local.entities.SyncConflictEntity
import com.example.data.local.entities.SyncEntityType
import com.example.data.local.entities.SyncOperationType
import com.example.data.local.entities.SyncOutboxEntity
import com.example.data.local.entities.SyncRecordState
import com.example.data.local.entities.SyncWorkbookConfigEntity
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed class SyncExecutionResult {
    data class Success(
        val pulledChapters: Int,
        val pulledSchedules: Int,
        val pulledMistakes: Int,
        val pushedChapters: Int,
        val pushedSchedules: Int,
        val pushedMistakes: Int,
        val conflictsDetected: Int,
        val timestamp: Long = System.currentTimeMillis()
    ) : SyncExecutionResult()

    data class Skipped(val reason: String) : SyncExecutionResult()
    data class Failure(
        val errorMessage: String,
        val isAuthRevoked: Boolean = false,
        val isRateLimited: Boolean = false,
        val retryAfterSeconds: Long? = null
    ) : SyncExecutionResult()
}

enum class ConflictResolutionChoice {
    KEEP_LOCAL,
    KEEP_REMOTE,
    MANUAL_MERGE
}

class TwoWaySheetsSyncEngine(
    private val db: AppDatabase,
    private val authProvider: GoogleAuthProvider,
    private val remoteGateway: GoogleSheetsRemoteGateway,
    private val deviceIdProvider: () -> String
) {
    constructor(context: Context, db: AppDatabase) : this(
        db = db,
        authProvider = GoogleAuthManager(context),
        remoteGateway = GoogleSheetsApiService(),
        deviceIdProvider = {
            try {
                Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
                    ?.takeIf { it.isNotBlank() } ?: "android-jee-os-device"
            } catch (_: Exception) {
                "android-jee-os-device"
            }
        }
    )

    private val syncDao = db.syncDao()
    private val chapterDao = db.chapterDao()
    private val scheduleDao = db.dailyScheduleDao()
    private val mistakeDao = db.mistakeEntryDao()

    companion object {
        private val syncMutex = Mutex()
    }

    val originDeviceId: String
        get() = deviceIdProvider()

    /**
     * Converts or links a Google Spreadsheet, optionally seeds `.xlsx` discovery rows into Room first,
     * ensures the 4 canonical sync tabs exist without touching original user tabs, and runs the initial two-way sync.
     */
    suspend fun linkAndSyncSpreadsheet(
        spreadsheetIdOrUrl: String?,
        xlsxBytesToImport: ByteArray? = null,
        xlsxFileName: String? = null,
        convertXlsxOnDrive: Boolean = false,
        createNewSpreadsheetTitle: String? = null
    ): SyncExecutionResult {
        val tokenRes = authProvider.getValidAccessToken()
        val accessToken = tokenRes.getOrElse { err ->
            val msg = err.message ?: "Google authorization required before linking spreadsheet."
            val isRevoked = msg.contains("AUTH_REVOKED", ignoreCase = true) || msg.contains("401")
            updateConfigStatus(status = if (isRevoked) "AUTH_REQUIRED" else "ERROR", error = msg)
            return SyncExecutionResult.Failure(msg, isAuthRevoked = isRevoked)
        }

        return try {
            // 1. If user provided local .xlsx bytes, parse and non-destructively upsert into Room first
            if (xlsxBytesToImport != null && xlsxBytesToImport.isNotEmpty()) {
                val parsed = CurriculumScheduleIngestionUtility.parseXlsxBytes(xlsxBytesToImport)
                CurriculumScheduleIngestionUtility.ingestIntoRoomDatabase(
                    db = db,
                    rawChapters = parsed,
                    replaceExistingChapters = false
                )
            }

            // 2. Determine target spreadsheet metadata
            val metadataResult = when {
                convertXlsxOnDrive && xlsxBytesToImport != null && xlsxBytesToImport.isNotEmpty() -> {
                    val baseTitle = (xlsxFileName ?: "JEE_80_Day_Backlog_Tracker")
                        .removeSuffix(".xlsx")
                        .removeSuffix(".XLSX")
                    remoteGateway.convertXlsxToGoogleSpreadsheet(
                        accessToken = accessToken,
                        title = "$baseTitle (JEE Study OS Sync)",
                        xlsxBytes = xlsxBytesToImport
                    )
                }
                !spreadsheetIdOrUrl.isNullOrBlank() -> {
                    val extractedId = GoogleSheetsApiService.extractSpreadsheetId(spreadsheetIdOrUrl)
                    remoteGateway.getSpreadsheetMetadata(accessToken, extractedId)
                }
                !createNewSpreadsheetTitle.isNullOrBlank() -> {
                    remoteGateway.createSpreadsheet(accessToken, createNewSpreadsheetTitle)
                }
                else -> {
                    return SyncExecutionResult.Failure("Please select an XLSX file, provide a Spreadsheet ID/URL, or enter a new Sheet title.")
                }
            }

            val metadata = metadataResult.getOrElse { err ->
                if (err is SheetsApiException) return handleSheetsException(err)
                val msg = err.message ?: "Failed to resolve Google Spreadsheet."
                updateConfigStatus(status = "ERROR", error = msg)
                return SyncExecutionResult.Failure(msg)
            }

            // 3. Ensure canonical sync tabs exist alongside any original tabs
            remoteGateway.ensureSyncTabsAndHeaders(
                accessToken = accessToken,
                spreadsheetId = metadata.spreadsheetId,
                existingSheetTitles = metadata.sheetTitles.toSet()
            ).getOrElse { err ->
                if (err is SheetsApiException) return handleSheetsException(err)
                val msg = err.message ?: "Failed to ensure sync tabs in Google Sheet."
                updateConfigStatus(status = "ERROR", error = msg)
                return SyncExecutionResult.Failure(msg)
            }

            // 4. Save workbook configuration in Room
            val now = System.currentTimeMillis()
            val existingConfig = syncDao.getWorkbookConfigSync()
            val session = authProvider.getCurrentSession()
            val preservedTabs = metadata.sheetTitles.filterNot {
                it.equals(WorkbookMappingEngine.TAB_CHAPTERS, ignoreCase = true) ||
                    it.equals(WorkbookMappingEngine.TAB_SCHEDULES, ignoreCase = true) ||
                    it.equals(WorkbookMappingEngine.TAB_MISTAKES, ignoreCase = true) ||
                    it.equals(WorkbookMappingEngine.TAB_META, ignoreCase = true)
            }.joinToString(",")

            val config = (existingConfig ?: SyncWorkbookConfigEntity(
                id = 1,
                deviceId = originDeviceId
            )).copy(
                spreadsheetId = metadata.spreadsheetId,
                spreadsheetTitle = metadata.title,
                spreadsheetUrl = metadata.spreadsheetUrl,
                sourceWorkbookFileName = xlsxFileName ?: existingConfig?.sourceWorkbookFileName.orEmpty(),
                connectedAccountEmail = session?.accountEmail ?: existingConfig?.connectedAccountEmail.orEmpty(),
                connectedAccountName = session?.accountName ?: existingConfig?.connectedAccountName.orEmpty(),
                grantedScopesCsv = session?.grantedScopes?.joinToString(",") ?: existingConfig?.grantedScopesCsv.orEmpty(),
                preservedSheetNamesCsv = preservedTabs,
                syncStatus = "LINKED",
                lastErrorMessage = "",
                lastAttemptedSyncAt = now,
                deviceId = originDeviceId
            )
            syncDao.insertOrUpdateWorkbookConfig(config)

            // 5. Run initial two-way sync to push local state & pull any existing rows in the Sheet
            performTwoWaySync(forceFullPush = true)
        } catch (e: SheetsApiException) {
            handleSheetsException(e)
        } catch (e: Exception) {
            val msg = e.message ?: "Failed to link Google Sheet."
            updateConfigStatus(status = "ERROR", error = msg)
            SyncExecutionResult.Failure(msg)
        }
    }

    /**
     * Pulls remote changes from Google Sheets and reconciles via two-way sync.
     */
    suspend fun refreshFromSheet(): SyncExecutionResult {
        return performTwoWaySync(forceFullPush = false)
    }

    /**
     * Core single-flight Two-Way Synchronization loop:
     * 1. Acquires single-flight Mutex so manual Sync Now and WorkManager never collide.
     * 2. Fetches all 4 canonical tabs from Google Sheets.
     * 3. Reconciles Chapters, 80-Day Schedule, and Mistake Notebook rows using 3-way baseline comparison:
     *    - Remote-only edit -> applies to Room with syncState=SYNCED and NO echo outbox entry.
     *    - Local-only edit -> pushes to Google Sheets and updates baseline & remoteVersionHash.
     *    - Simultaneous incompatible edit -> records SyncConflictEntity (preserving both versions) and skips overwriting either side until resolved.
     *    - Tombstone (isDeleted=true) -> propagates deletion unless the other side edited since last sync (conflict).
     * 4. Writes updated canonical tables to Google Sheets in a single values:batchUpdate call and marks outbox items completed.
     */
    suspend fun performTwoWaySync(forceFullPush: Boolean = false): SyncExecutionResult = syncMutex.withLock {
        val config = syncDao.getWorkbookConfigSync()
        if (config == null || !config.isSpreadsheetLinked) {
            return@withLock SyncExecutionResult.Skipped("No linked Google Sheet configured.")
        }

        updateConfigStatus(status = "SYNCING", error = "")

        val tokenRes = authProvider.getValidAccessToken()
        val accessToken = tokenRes.getOrElse { err ->
            val msg = err.message ?: "Google authorization required. Please reconnect your account."
            val isRevoked = msg.contains("AUTH_REVOKED", ignoreCase = true) || msg.contains("401")
            updateConfigStatus(status = if (isRevoked) "AUTH_REQUIRED" else "ERROR", error = msg)
            return@withLock SyncExecutionResult.Failure(msg, isAuthRevoked = isRevoked)
        }

        try {
            val meta = remoteGateway.getSpreadsheetMetadata(accessToken, config.spreadsheetId).getOrThrow()
            remoteGateway.ensureSyncTabsAndHeaders(
                accessToken = accessToken,
                spreadsheetId = config.spreadsheetId,
                existingSheetTitles = meta.sheetTitles.toSet()
            ).getOrThrow()

            val snapshot = remoteGateway.fetchAllSyncTabs(accessToken, config.spreadsheetId).getOrThrow()
            val now = System.currentTimeMillis()
            val devId = originDeviceId

            var pulledChapters = 0
            var pulledSchedules = 0
            var pulledMistakes = 0
            var pushedChapters = 0
            var pushedSchedules = 0
            var pushedMistakes = 0
            var conflictsDetected = 0

            val remoteChaptersList = snapshot.chaptersRows.mapIndexedNotNull { idx, row ->
                WorkbookMappingEngine.sheetRowToChapter(row, fallbackOrderIndex = idx)
            }
            val remoteSchedulesList = snapshot.scheduleRows.mapNotNull { row ->
                WorkbookMappingEngine.sheetRowToSchedule(row)
            }
            val remoteMistakesList = snapshot.mistakeRows.mapNotNull { row ->
                WorkbookMappingEngine.sheetRowToMistake(row)
            }

            val remoteChaptersBySyncId = remoteChaptersList.associateBy { it.effectiveSyncId }.toMutableMap()
            val remoteSchedulesBySyncId = remoteSchedulesList.associateBy { it.effectiveSyncId }.toMutableMap()
            val remoteMistakesBySyncId = remoteMistakesList.associateBy { it.effectiveSyncId }.toMutableMap()

            val finalRemoteChapters = mutableMapOf<String, ChapterEntity>()
            remoteChaptersBySyncId.forEach { (k, v) -> finalRemoteChapters[k] = v }

            val finalRemoteSchedules = mutableMapOf<String, DailyScheduleEntity>()
            remoteSchedulesBySyncId.forEach { (k, v) -> finalRemoteSchedules[k] = v }

            val finalRemoteMistakes = mutableMapOf<String, MistakeEntryEntity>()
            remoteMistakesBySyncId.forEach { (k, v) -> finalRemoteMistakes[k] = v }

            val activeOutbox = syncDao.getPendingOutboxSync()
            val outboxByEntityKey = activeOutbox.groupBy { "${it.entityType}:${it.syncId}" }
            val completedOperationIds = mutableListOf<String>()

            db.withTransaction {
                // =========================================================================
                // 1. RECONCILE CHAPTERS
                // =========================================================================
                val localChapters = chapterDao.getAllChaptersIncludingDeletedSync()
                val localChaptersBySyncId = mutableMapOf<String, ChapterEntity>()
                for (ch in localChapters) {
                    val ensuredSyncId = ch.effectiveSyncId
                    val normalized = if (ch.syncId != ensuredSyncId) ch.copy(syncId = ensuredSyncId) else ch
                    localChaptersBySyncId[ensuredSyncId] = normalized
                }

                val allChapterSyncIds = (localChaptersBySyncId.keys + remoteChaptersBySyncId.keys).toSet()
                for (syncId in allChapterSyncIds) {
                    val local = localChaptersBySyncId[syncId]
                    val remote = remoteChaptersBySyncId[syncId]
                    val entityOutbox = outboxByEntityKey["${SyncEntityType.CHAPTER.dbKey}:$syncId"].orEmpty()

                    if (local != null && remote == null) {
                        val hash = local.computeContentHash()
                        val baseline = WorkbookMappingEngine.serializeChapter(local)
                        val syncedLocal = local.copy(
                            syncId = syncId,
                            lastSyncedAt = now,
                            remoteVersionHash = hash,
                            lastSyncedBaselineJson = baseline,
                            originDeviceId = local.originDeviceId.ifBlank { devId },
                            syncState = SyncRecordState.SYNCED.dbKey
                        )
                        chapterDao.updateChapter(syncedLocal)
                        finalRemoteChapters[syncId] = syncedLocal
                        pushedChapters++
                        completedOperationIds.addAll(entityOutbox.map { it.operationId })
                    } else if (local == null && remote != null) {
                        if (!remote.isDeleted) {
                            val matchingLocal = localChapters.firstOrNull {
                                !it.isDeleted &&
                                    it.subject.trim().equals(remote.subject.trim(), ignoreCase = true) &&
                                    it.chapterName.trim().equals(remote.chapterName.trim(), ignoreCase = true) &&
                                    it.effectiveSyncId !in remoteChaptersBySyncId
                            }
                            val remoteHash = remote.computeContentHash()
                            val baseline = WorkbookMappingEngine.serializeChapter(remote)
                            if (matchingLocal != null) {
                                val merged = remote.copy(
                                    id = matchingLocal.id,
                                    syncId = syncId,
                                    lastSyncedAt = now,
                                    remoteVersionHash = remoteHash,
                                    lastSyncedBaselineJson = baseline,
                                    syncState = SyncRecordState.SYNCED.dbKey
                                )
                                chapterDao.updateChapter(merged)
                                finalRemoteChapters[syncId] = merged
                            } else {
                                val inserted = remote.copy(
                                    id = 0,
                                    syncId = syncId,
                                    lastSyncedAt = now,
                                    remoteVersionHash = remoteHash,
                                    lastSyncedBaselineJson = baseline,
                                    syncState = SyncRecordState.SYNCED.dbKey
                                )
                                chapterDao.insertChapter(inserted)
                                finalRemoteChapters[syncId] = inserted
                            }
                            pulledChapters++
                        }
                    } else if (local != null && remote != null) {
                        val localHash = local.computeContentHash()
                        val remoteHash = remote.computeContentHash()
                        val baselineChapter = WorkbookMappingEngine.deserializeChapter(local.lastSyncedBaselineJson)
                        val baselineHash = baselineChapter?.computeContentHash()
                            ?: local.remoteVersionHash.takeIf { it.isNotBlank() }

                        val localChanged = if (baselineHash != null) {
                            localHash != baselineHash || local.syncState == SyncRecordState.PENDING.dbKey || entityOutbox.isNotEmpty()
                        } else {
                            local.syncState == SyncRecordState.PENDING.dbKey || entityOutbox.isNotEmpty()
                        }
                        val remoteChanged = if (baselineHash != null) {
                            remoteHash != baselineHash
                        } else {
                            localHash != remoteHash
                        }

                        when {
                            localHash == remoteHash && local.isDeleted == remote.isDeleted -> {
                                val baseline = WorkbookMappingEngine.serializeChapter(local)
                                val synced = local.copy(
                                    syncId = syncId,
                                    lastSyncedAt = now,
                                    remoteVersionHash = localHash,
                                    lastSyncedBaselineJson = baseline,
                                    syncState = SyncRecordState.SYNCED.dbKey
                                )
                                chapterDao.updateChapter(synced)
                                finalRemoteChapters[syncId] = synced
                                completedOperationIds.addAll(entityOutbox.map { it.operationId })
                            }
                            localChanged && !remoteChanged -> {
                                val baseline = WorkbookMappingEngine.serializeChapter(local)
                                val synced = local.copy(
                                    syncId = syncId,
                                    lastSyncedAt = now,
                                    remoteVersionHash = localHash,
                                    lastSyncedBaselineJson = baseline,
                                    originDeviceId = devId,
                                    syncState = SyncRecordState.SYNCED.dbKey
                                )
                                chapterDao.updateChapter(synced)
                                finalRemoteChapters[syncId] = synced
                                pushedChapters++
                                completedOperationIds.addAll(entityOutbox.map { it.operationId })
                            }
                            !localChanged && remoteChanged -> {
                                val applied = remote.copy(
                                    id = local.id,
                                    syncId = syncId,
                                    createdAt = local.createdAt,
                                    lastSyncedAt = now,
                                    remoteVersionHash = remoteHash,
                                    lastSyncedBaselineJson = WorkbookMappingEngine.serializeChapter(remote),
                                    syncState = SyncRecordState.SYNCED.dbKey
                                )
                                chapterDao.updateChapter(applied)
                                finalRemoteChapters[syncId] = applied
                                pulledChapters++
                                completedOperationIds.addAll(entityOutbox.map { it.operationId })
                            }
                            else -> {
                                val conflictSummary = WorkbookMappingEngine.computeChapterFieldDiffs(local, remote)
                                val conflictId = "${SyncEntityType.CHAPTER.dbKey}_$syncId"
                                val conflict = SyncConflictEntity(
                                    conflictId = conflictId,
                                    entityType = SyncEntityType.CHAPTER.dbKey,
                                    syncId = syncId,
                                    entityTitle = "${local.subject} — ${local.chapterName}",
                                    baselinePayloadJson = local.lastSyncedBaselineJson,
                                    localPayloadJson = WorkbookMappingEngine.serializeChapter(local),
                                    remotePayloadJson = WorkbookMappingEngine.serializeChapter(remote),
                                    conflictingFieldsSummary = conflictSummary,
                                    localUpdatedAt = local.lastUpdated,
                                    remoteUpdatedAt = remote.lastUpdated,
                                    detectedAt = now
                                )
                                syncDao.insertOrUpdateConflict(conflict)
                                chapterDao.updateChapter(local.copy(syncId = syncId, syncState = SyncRecordState.CONFLICT.dbKey))
                                finalRemoteChapters[syncId] = remote
                                conflictsDetected++
                            }
                        }
                    }
                }

                // =========================================================================
                // 2. RECONCILE 80-DAY SCHEDULE
                // =========================================================================
                val localSchedules = scheduleDao.getAllDaysSync()
                val localSchedulesBySyncId = localSchedules.associateBy { it.effectiveSyncId }
                val allScheduleSyncIds = (localSchedulesBySyncId.keys + remoteSchedulesBySyncId.keys).toSet()

                for (syncId in allScheduleSyncIds) {
                    val local = localSchedulesBySyncId[syncId]
                    val remote = remoteSchedulesBySyncId[syncId]
                    val entityOutbox = outboxByEntityKey["${SyncEntityType.DAILY_SCHEDULE.dbKey}:$syncId"].orEmpty()

                    if (local != null && remote == null) {
                        val baseline = WorkbookMappingEngine.serializeSchedule(local)
                        val synced = local.copy(
                            syncId = syncId,
                            lastSyncedAt = now,
                            remoteVersionHash = local.computeContentHash(),
                            lastSyncedBaselineJson = baseline,
                            originDeviceId = local.originDeviceId.ifBlank { devId },
                            syncState = SyncRecordState.SYNCED.dbKey
                        )
                        scheduleDao.insertOrUpdate(synced)
                        finalRemoteSchedules[syncId] = synced
                        pushedSchedules++
                        completedOperationIds.addAll(entityOutbox.map { it.operationId })
                    } else if (local == null && remote != null) {
                        if (!remote.isDeleted) {
                            val baseline = WorkbookMappingEngine.serializeSchedule(remote)
                            val inserted = remote.copy(
                                syncId = syncId,
                                lastSyncedAt = now,
                                remoteVersionHash = remote.computeContentHash(),
                                lastSyncedBaselineJson = baseline,
                                syncState = SyncRecordState.SYNCED.dbKey
                            )
                            scheduleDao.insertOrUpdate(inserted)
                            finalRemoteSchedules[syncId] = inserted
                            pulledSchedules++
                        }
                    } else if (local != null && remote != null) {
                        val localHash = local.computeContentHash()
                        val remoteHash = remote.computeContentHash()
                        val baselineObj = WorkbookMappingEngine.deserializeSchedule(local.lastSyncedBaselineJson)
                        val baselineHash = baselineObj?.computeContentHash()
                            ?: local.remoteVersionHash.takeIf { it.isNotBlank() }

                        val localChanged = if (baselineHash != null) {
                            localHash != baselineHash || local.syncState == SyncRecordState.PENDING.dbKey || entityOutbox.isNotEmpty()
                        } else {
                            local.syncState == SyncRecordState.PENDING.dbKey || entityOutbox.isNotEmpty()
                        }
                        val remoteChanged = if (baselineHash != null) {
                            remoteHash != baselineHash
                        } else {
                            localHash != remoteHash
                        }

                        when {
                            localHash == remoteHash && local.isDeleted == remote.isDeleted -> {
                                val synced = local.copy(
                                    syncId = syncId,
                                    lastSyncedAt = now,
                                    remoteVersionHash = localHash,
                                    lastSyncedBaselineJson = WorkbookMappingEngine.serializeSchedule(local),
                                    syncState = SyncRecordState.SYNCED.dbKey
                                )
                                scheduleDao.insertOrUpdate(synced)
                                finalRemoteSchedules[syncId] = synced
                                completedOperationIds.addAll(entityOutbox.map { it.operationId })
                            }
                            localChanged && !remoteChanged -> {
                                val synced = local.copy(
                                    syncId = syncId,
                                    lastSyncedAt = now,
                                    remoteVersionHash = localHash,
                                    lastSyncedBaselineJson = WorkbookMappingEngine.serializeSchedule(local),
                                    originDeviceId = devId,
                                    syncState = SyncRecordState.SYNCED.dbKey
                                )
                                scheduleDao.insertOrUpdate(synced)
                                finalRemoteSchedules[syncId] = synced
                                pushedSchedules++
                                completedOperationIds.addAll(entityOutbox.map { it.operationId })
                            }
                            !localChanged && remoteChanged -> {
                                val applied = remote.copy(
                                    date = local.date,
                                    syncId = syncId,
                                    createdAt = local.createdAt,
                                    lastSyncedAt = now,
                                    remoteVersionHash = remoteHash,
                                    lastSyncedBaselineJson = WorkbookMappingEngine.serializeSchedule(remote),
                                    syncState = SyncRecordState.SYNCED.dbKey
                                )
                                scheduleDao.insertOrUpdate(applied)
                                finalRemoteSchedules[syncId] = applied
                                pulledSchedules++
                                completedOperationIds.addAll(entityOutbox.map { it.operationId })
                            }
                            else -> {
                                val conflictSummary = WorkbookMappingEngine.computeScheduleFieldDiffs(local, remote)
                                val conflictId = "${SyncEntityType.DAILY_SCHEDULE.dbKey}_$syncId"
                                val conflict = SyncConflictEntity(
                                    conflictId = conflictId,
                                    entityType = SyncEntityType.DAILY_SCHEDULE.dbKey,
                                    syncId = syncId,
                                    entityTitle = "Day ${local.dayNumber} (${local.date})",
                                    baselinePayloadJson = local.lastSyncedBaselineJson,
                                    localPayloadJson = WorkbookMappingEngine.serializeSchedule(local),
                                    remotePayloadJson = WorkbookMappingEngine.serializeSchedule(remote),
                                    conflictingFieldsSummary = conflictSummary,
                                    localUpdatedAt = local.updatedAt,
                                    remoteUpdatedAt = remote.updatedAt,
                                    detectedAt = now
                                )
                                syncDao.insertOrUpdateConflict(conflict)
                                scheduleDao.insertOrUpdate(local.copy(syncId = syncId, syncState = SyncRecordState.CONFLICT.dbKey))
                                finalRemoteSchedules[syncId] = remote
                                conflictsDetected++
                            }
                        }
                    }
                }

                // =========================================================================
                // 3. RECONCILE MISTAKE NOTEBOOK
                // =========================================================================
                val localMistakes = mistakeDao.getAllMistakesIncludingDeletedSync()
                val localMistakesBySyncId = localMistakes.associateBy { it.effectiveSyncId }
                val allMistakeSyncIds = (localMistakesBySyncId.keys + remoteMistakesBySyncId.keys).toSet()

                for (syncId in allMistakeSyncIds) {
                    val local = localMistakesBySyncId[syncId]
                    val remote = remoteMistakesBySyncId[syncId]
                    val entityOutbox = outboxByEntityKey["${SyncEntityType.MISTAKE_ENTRY.dbKey}:$syncId"].orEmpty()

                    if (local != null && remote == null) {
                        val baseline = WorkbookMappingEngine.serializeMistake(local)
                        val synced = local.copy(
                            syncId = syncId,
                            lastSyncedAt = now,
                            remoteVersionHash = local.computeContentHash(),
                            lastSyncedBaselineJson = baseline,
                            originDeviceId = local.originDeviceId.ifBlank { devId },
                            syncState = SyncRecordState.SYNCED.dbKey
                        )
                        mistakeDao.updateMistake(synced)
                        finalRemoteMistakes[syncId] = synced
                        pushedMistakes++
                        completedOperationIds.addAll(entityOutbox.map { it.operationId })
                    } else if (local == null && remote != null) {
                        if (!remote.isDeleted) {
                            val baseline = WorkbookMappingEngine.serializeMistake(remote)
                            val inserted = remote.copy(
                                id = 0,
                                syncId = syncId,
                                lastSyncedAt = now,
                                remoteVersionHash = remote.computeContentHash(),
                                lastSyncedBaselineJson = baseline,
                                syncState = SyncRecordState.SYNCED.dbKey
                            )
                            mistakeDao.insertMistake(inserted)
                            finalRemoteMistakes[syncId] = inserted
                            pulledMistakes++
                        }
                    } else if (local != null && remote != null) {
                        val localHash = local.computeContentHash()
                        val remoteHash = remote.computeContentHash()
                        val baselineObj = WorkbookMappingEngine.deserializeMistake(local.lastSyncedBaselineJson)
                        val baselineHash = baselineObj?.computeContentHash()
                            ?: local.remoteVersionHash.takeIf { it.isNotBlank() }

                        val localChanged = if (baselineHash != null) {
                            localHash != baselineHash || local.syncState == SyncRecordState.PENDING.dbKey || entityOutbox.isNotEmpty()
                        } else {
                            local.syncState == SyncRecordState.PENDING.dbKey || entityOutbox.isNotEmpty()
                        }
                        val remoteChanged = if (baselineHash != null) {
                            remoteHash != baselineHash
                        } else {
                            localHash != remoteHash
                        }

                        when {
                            localHash == remoteHash && local.isDeleted == remote.isDeleted -> {
                                val synced = local.copy(
                                    syncId = syncId,
                                    lastSyncedAt = now,
                                    remoteVersionHash = localHash,
                                    lastSyncedBaselineJson = WorkbookMappingEngine.serializeMistake(local),
                                    syncState = SyncRecordState.SYNCED.dbKey
                                )
                                mistakeDao.updateMistake(synced)
                                finalRemoteMistakes[syncId] = synced
                                completedOperationIds.addAll(entityOutbox.map { it.operationId })
                            }
                            localChanged && !remoteChanged -> {
                                val synced = local.copy(
                                    syncId = syncId,
                                    lastSyncedAt = now,
                                    remoteVersionHash = localHash,
                                    lastSyncedBaselineJson = WorkbookMappingEngine.serializeMistake(local),
                                    originDeviceId = devId,
                                    syncState = SyncRecordState.SYNCED.dbKey
                                )
                                mistakeDao.updateMistake(synced)
                                finalRemoteMistakes[syncId] = synced
                                pushedMistakes++
                                completedOperationIds.addAll(entityOutbox.map { it.operationId })
                            }
                            !localChanged && remoteChanged -> {
                                val applied = remote.copy(
                                    id = local.id,
                                    syncId = syncId,
                                    createdAt = local.createdAt,
                                    lastSyncedAt = now,
                                    remoteVersionHash = remoteHash,
                                    lastSyncedBaselineJson = WorkbookMappingEngine.serializeMistake(remote),
                                    syncState = SyncRecordState.SYNCED.dbKey
                                )
                                mistakeDao.updateMistake(applied)
                                finalRemoteMistakes[syncId] = applied
                                pulledMistakes++
                                completedOperationIds.addAll(entityOutbox.map { it.operationId })
                            }
                            else -> {
                                val conflictSummary = WorkbookMappingEngine.computeMistakeFieldDiffs(local, remote)
                                val conflictId = "${SyncEntityType.MISTAKE_ENTRY.dbKey}_$syncId"
                                val conflict = SyncConflictEntity(
                                    conflictId = conflictId,
                                    entityType = SyncEntityType.MISTAKE_ENTRY.dbKey,
                                    syncId = syncId,
                                    entityTitle = "${local.subject}: ${local.chapterName} (${local.errorType})",
                                    baselinePayloadJson = local.lastSyncedBaselineJson,
                                    localPayloadJson = WorkbookMappingEngine.serializeMistake(local),
                                    remotePayloadJson = WorkbookMappingEngine.serializeMistake(remote),
                                    conflictingFieldsSummary = conflictSummary,
                                    localUpdatedAt = local.updatedAt,
                                    remoteUpdatedAt = remote.updatedAt,
                                    detectedAt = now
                                )
                                syncDao.insertOrUpdateConflict(conflict)
                                mistakeDao.updateMistake(local.copy(syncId = syncId, syncState = SyncRecordState.CONFLICT.dbKey))
                                finalRemoteMistakes[syncId] = remote
                                conflictsDetected++
                            }
                        }
                    }
                }
            }

            // 4. Push reconciled canonical tables to Google Sheets if anything was pushed or forceFullPush is active
            val shouldWriteToSheet = forceFullPush ||
                pushedChapters > 0 || pushedSchedules > 0 || pushedMistakes > 0 ||
                snapshot.chaptersRows.size <= 1

            if (shouldWriteToSheet) {
                val sortedChapters = finalRemoteChapters.values.sortedWith(
                    compareBy<ChapterEntity> { it.subject }.thenBy { it.orderIndex }.thenBy { it.chapterName }
                )
                val sortedSchedules = finalRemoteSchedules.values.sortedBy { it.dayNumber }
                val sortedMistakes = finalRemoteMistakes.values.sortedByDescending { it.timestamp }

                val chapterSheetRows = mutableListOf<List<String>>()
                chapterSheetRows.add(WorkbookMappingEngine.CHAPTER_HEADERS)
                sortedChapters.forEach {
                    chapterSheetRows.add(WorkbookMappingEngine.chapterToSheetRow(it.copy(originDeviceId = devId)))
                }

                val scheduleSheetRows = mutableListOf<List<String>>()
                scheduleSheetRows.add(WorkbookMappingEngine.SCHEDULE_HEADERS)
                sortedSchedules.forEach {
                    scheduleSheetRows.add(WorkbookMappingEngine.scheduleToSheetRow(it.copy(originDeviceId = devId)))
                }

                val mistakeSheetRows = mutableListOf<List<String>>()
                mistakeSheetRows.add(WorkbookMappingEngine.MISTAKE_HEADERS)
                sortedMistakes.forEach {
                    mistakeSheetRows.add(WorkbookMappingEngine.mistakeToSheetRow(it.copy(originDeviceId = devId)))
                }

                val metaSheetRows = listOf(
                    listOf("Key", "Value", "UpdatedAt"),
                    listOf("schemaVersion", "1", now.toString()),
                    listOf("lastSyncAt", now.toString(), now.toString()),
                    listOf("lastOriginDeviceId", devId, now.toString()),
                    listOf("chapterCount", sortedChapters.count { !it.isDeleted }.toString(), now.toString()),
                    listOf("scheduleCount", sortedSchedules.count { !it.isDeleted }.toString(), now.toString()),
                    listOf("mistakeCount", sortedMistakes.count { !it.isDeleted }.toString(), now.toString())
                )

                val rangeUpdates = listOf(
                    SheetValueRangeUpdate(
                        range = "${WorkbookMappingEngine.TAB_CHAPTERS}!A1:AB${chapterSheetRows.size}",
                        values = chapterSheetRows
                    ),
                    SheetValueRangeUpdate(
                        range = "${WorkbookMappingEngine.TAB_SCHEDULES}!A1:W${scheduleSheetRows.size}",
                        values = scheduleSheetRows
                    ),
                    SheetValueRangeUpdate(
                        range = "${WorkbookMappingEngine.TAB_MISTAKES}!A1:R${mistakeSheetRows.size}",
                        values = mistakeSheetRows
                    ),
                    SheetValueRangeUpdate(
                        range = "${WorkbookMappingEngine.TAB_META}!A1:C${metaSheetRows.size}",
                        values = metaSheetRows
                    )
                )

                remoteGateway.batchUpdateSyncTabs(
                    accessToken = accessToken,
                    spreadsheetId = config.spreadsheetId,
                    updates = rangeUpdates
                ).getOrThrow()
            }

            // 5. Mark completed outbox operations and prune
            db.withTransaction {
                for (opId in completedOperationIds.distinct()) {
                    syncDao.deleteOutboxById(opId)
                }
                syncDao.pruneCompletedOutbox()
            }

            val unresolvedCount = syncDao.getUnresolvedConflictsSync().size
            val finalStatus = if (unresolvedCount > 0) "CONFLICTS ($unresolvedCount)" else "SYNCED"
            val summaryText = "↑${pushedChapters + pushedSchedules + pushedMistakes} pushed · ↓${pulledChapters + pulledSchedules + pulledMistakes} pulled"
            val updatedConfig = config.copy(
                lastSuccessfulSyncAt = now,
                lastAttemptedSyncAt = now,
                syncStatus = finalStatus,
                lastSyncSummary = summaryText,
                lastErrorMessage = ""
            )
            syncDao.insertOrUpdateWorkbookConfig(updatedConfig)

            SyncExecutionResult.Success(
                pulledChapters = pulledChapters,
                pulledSchedules = pulledSchedules,
                pulledMistakes = pulledMistakes,
                pushedChapters = pushedChapters,
                pushedSchedules = pushedSchedules,
                pushedMistakes = pushedMistakes,
                conflictsDetected = unresolvedCount,
                timestamp = now
            )
        } catch (e: SheetsApiException) {
            handleSheetsException(e)
        } catch (e: Exception) {
            val msg = e.message ?: "Unexpected sync failure."
            updateConfigStatus(status = "ERROR", error = msg)
            SyncExecutionResult.Failure(msg)
        }
    }

    /**
     * Resolves a persisted SyncConflictEntity using KEEP_LOCAL, KEEP_REMOTE, or MANUAL_MERGE,
     * archives the losing version JSON on the conflict record so no data is ever lost,
     * updates the local Room entity, and queues an outbox push if local/merged state was chosen.
     */
    suspend fun resolveConflict(
        conflictId: String,
        choice: ConflictResolutionChoice,
        manualMergedPayloadJson: String? = null
    ): Boolean {
        val conflict = syncDao.getConflictById(conflictId) ?: return false
        val now = System.currentTimeMillis()
        val devId = originDeviceId

        val chosenPayloadJson = when (choice) {
            ConflictResolutionChoice.KEEP_LOCAL -> conflict.localPayloadJson
            ConflictResolutionChoice.KEEP_REMOTE -> conflict.remotePayloadJson
            ConflictResolutionChoice.MANUAL_MERGE -> manualMergedPayloadJson ?: conflict.localPayloadJson
        }
        val losingArchiveJson = when (choice) {
            ConflictResolutionChoice.KEEP_LOCAL -> conflict.remotePayloadJson
            ConflictResolutionChoice.KEEP_REMOTE -> conflict.localPayloadJson
            ConflictResolutionChoice.MANUAL_MERGE -> """{"local":${conflict.localPayloadJson},"remote":${conflict.remotePayloadJson}}"""
        }

        db.withTransaction {
            when (conflict.entityType) {
                SyncEntityType.CHAPTER.dbKey -> {
                    val existing = chapterDao.getChapterBySyncId(conflict.syncId)
                    val resolvedEntity = WorkbookMappingEngine.deserializeChapter(chosenPayloadJson)
                    if (resolvedEntity != null) {
                        val needsPush = choice != ConflictResolutionChoice.KEEP_REMOTE
                        val updated = resolvedEntity.copy(
                            id = existing?.id ?: 0,
                            syncId = conflict.syncId,
                            lastUpdated = now,
                            lastSyncedAt = if (needsPush) (existing?.lastSyncedAt ?: 0L) else now,
                            remoteVersionHash = resolvedEntity.computeContentHash(),
                            lastSyncedBaselineJson = chosenPayloadJson,
                            originDeviceId = devId,
                            syncState = if (needsPush) SyncRecordState.PENDING.dbKey else SyncRecordState.SYNCED.dbKey
                        )
                        if (existing != null) {
                            chapterDao.updateChapter(updated)
                        } else {
                            chapterDao.insertChapter(updated)
                        }
                        if (needsPush) {
                            enqueueOutboxInTransaction(
                                entityType = SyncEntityType.CHAPTER,
                                syncId = conflict.syncId,
                                operationType = if (updated.isDeleted) SyncOperationType.DELETE else SyncOperationType.UPSERT,
                                payloadJson = WorkbookMappingEngine.serializeChapter(updated),
                                baseVersionHash = updated.remoteVersionHash
                            )
                        }
                    }
                }
                SyncEntityType.DAILY_SCHEDULE.dbKey -> {
                    val existing = scheduleDao.getScheduleBySyncId(conflict.syncId)
                    val resolvedEntity = WorkbookMappingEngine.deserializeSchedule(chosenPayloadJson)
                    if (resolvedEntity != null) {
                        val needsPush = choice != ConflictResolutionChoice.KEEP_REMOTE
                        val updated = resolvedEntity.copy(
                            syncId = conflict.syncId,
                            updatedAt = now,
                            lastSyncedAt = if (needsPush) (existing?.lastSyncedAt ?: 0L) else now,
                            remoteVersionHash = resolvedEntity.computeContentHash(),
                            lastSyncedBaselineJson = chosenPayloadJson,
                            originDeviceId = devId,
                            syncState = if (needsPush) SyncRecordState.PENDING.dbKey else SyncRecordState.SYNCED.dbKey
                        )
                        scheduleDao.insertOrUpdate(updated)
                        if (needsPush) {
                            enqueueOutboxInTransaction(
                                entityType = SyncEntityType.DAILY_SCHEDULE,
                                syncId = conflict.syncId,
                                operationType = if (updated.isDeleted) SyncOperationType.DELETE else SyncOperationType.UPSERT,
                                payloadJson = WorkbookMappingEngine.serializeSchedule(updated),
                                baseVersionHash = updated.remoteVersionHash
                            )
                        }
                    }
                }
                SyncEntityType.MISTAKE_ENTRY.dbKey -> {
                    val existing = mistakeDao.getMistakeBySyncId(conflict.syncId)
                    val resolvedEntity = WorkbookMappingEngine.deserializeMistake(chosenPayloadJson)
                    if (resolvedEntity != null) {
                        val needsPush = choice != ConflictResolutionChoice.KEEP_REMOTE
                        val updated = resolvedEntity.copy(
                            id = existing?.id ?: 0,
                            syncId = conflict.syncId,
                            updatedAt = now,
                            lastSyncedAt = if (needsPush) (existing?.lastSyncedAt ?: 0L) else now,
                            remoteVersionHash = resolvedEntity.computeContentHash(),
                            lastSyncedBaselineJson = chosenPayloadJson,
                            originDeviceId = devId,
                            syncState = if (needsPush) SyncRecordState.PENDING.dbKey else SyncRecordState.SYNCED.dbKey
                        )
                        if (existing != null) {
                            mistakeDao.updateMistake(updated)
                        } else {
                            mistakeDao.insertMistake(updated)
                        }
                        if (needsPush) {
                            enqueueOutboxInTransaction(
                                entityType = SyncEntityType.MISTAKE_ENTRY,
                                syncId = conflict.syncId,
                                operationType = if (updated.isDeleted) SyncOperationType.DELETE else SyncOperationType.UPSERT,
                                payloadJson = WorkbookMappingEngine.serializeMistake(updated),
                                baseVersionHash = updated.remoteVersionHash
                            )
                        }
                    }
                }
            }

            syncDao.insertOrUpdateConflict(
                conflict.copy(
                    isResolved = true,
                    resolutionChoice = choice.name,
                    losingVersionArchiveJson = losingArchiveJson,
                    resolvedAt = now
                )
            )
        }
        return true
    }

    /**
     * Enqueues or coalesces a pending outbox operation for the given entity inside an active Room transaction.
     * Uses a deterministic idempotency key per pending entity so retries never duplicate rows.
     */
    suspend fun enqueueOutboxInTransaction(
        entityType: SyncEntityType,
        syncId: String,
        operationType: SyncOperationType,
        payloadJson: String,
        baseVersionHash: String
    ) {
        val existingPending = syncDao.getActiveOutboxForRecord(entityType.dbKey, syncId)
        val opId = existingPending?.operationId ?: "${entityType.dbKey}_${syncId}_pending"
        val now = System.currentTimeMillis()
        val entry = SyncOutboxEntity(
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
        syncDao.insertOutbox(entry)
    }

    suspend fun disconnectWorkbook(clearOutbox: Boolean = false) {
        if (authProvider is GoogleAuthManager) {
            authProvider.disconnectAndRevoke()
        } else {
            authProvider.markTokenRevokedOrExpired("User disconnected")
        }
        db.withTransaction {
            val current = syncDao.getWorkbookConfigSync()
            if (current != null) {
                syncDao.insertOrUpdateWorkbookConfig(
                    current.copy(
                        spreadsheetId = if (clearOutbox) "" else current.spreadsheetId,
                        connectedAccountEmail = "",
                        connectedAccountName = "",
                        syncStatus = "DISCONNECTED",
                        lastErrorMessage = "",
                        lastAttemptedSyncAt = System.currentTimeMillis()
                    )
                )
            }
            if (clearOutbox) {
                val allPending = syncDao.getPendingOutboxSync()
                for (op in allPending) {
                    syncDao.deleteOutboxById(op.operationId)
                }
            }
        }
    }

    private suspend fun handleSheetsException(e: SheetsApiException): SyncExecutionResult.Failure {
        return when (e) {
            is SheetsApiException.AuthRevokedException -> {
                authProvider.markTokenRevokedOrExpired(e.message ?: "Auth revoked")
                val msg = "Google authorization expired or revoked. Please reconnect in Sync Settings."
                updateConfigStatus(status = "AUTH_REQUIRED", error = msg)
                SyncExecutionResult.Failure(msg, isAuthRevoked = true)
            }
            is SheetsApiException.RateLimitedException -> {
                val msg = "Google Sheets API rate limit reached (429). Backing off (${e.retryAfterSeconds}s)."
                markPendingOutboxFailed(msg)
                updateConfigStatus(status = "RATE_LIMITED", error = msg)
                SyncExecutionResult.Failure(msg, isRateLimited = true, retryAfterSeconds = e.retryAfterSeconds)
            }
            is SheetsApiException.NotFoundException -> {
                val msg = "Linked Google Spreadsheet was not found (404). Verify the Sheet ID or relink."
                updateConfigStatus(status = "NOT_FOUND", error = msg)
                SyncExecutionResult.Failure(msg)
            }
            else -> {
                val msg = e.message ?: "Google Sheets network error."
                markPendingOutboxFailed(msg)
                updateConfigStatus(status = "ERROR", error = msg)
                SyncExecutionResult.Failure(msg)
            }
        }
    }

    private suspend fun markPendingOutboxFailed(errorMsg: String) {
        val pending = syncDao.getPendingOutboxSync()
        val now = System.currentTimeMillis()
        for (op in pending) {
            syncDao.updateOutbox(
                op.copy(
                    status = "FAILED",
                    retryCount = op.retryCount + 1,
                    lastError = errorMsg,
                    updatedAt = now
                )
            )
        }
    }

    private suspend fun updateConfigStatus(status: String, error: String) {
        val current = syncDao.getWorkbookConfigSync() ?: return
        syncDao.insertOrUpdateWorkbookConfig(
            current.copy(
                syncStatus = status,
                lastErrorMessage = error,
                lastAttemptedSyncAt = System.currentTimeMillis()
            )
        )
    }
}
