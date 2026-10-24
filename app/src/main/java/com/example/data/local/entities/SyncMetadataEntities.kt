package com.example.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.security.MessageDigest
import java.util.UUID

enum class SyncRecordState(val dbKey: String) {
    LOCAL_ONLY("LOCAL_ONLY"),
    PENDING("PENDING"),
    SYNCED("SYNCED"),
    CONFLICT("CONFLICT"),
    ERROR("ERROR")
}

enum class SyncEntityType(val dbKey: String) {
    CHAPTER("CHAPTER"),
    DAILY_SCHEDULE("DAILY_SCHEDULE"),
    MISTAKE_ENTRY("MISTAKE_ENTRY")
}

enum class SyncOperationType(val dbKey: String) {
    UPSERT("UPSERT"),
    DELETE("DELETE")
}

object SyncIdentityHelper {
    /**
     * Generates a random v4 UUID for newly created chapter or mistake records.
     */
    fun newUuid(prefix: String): String = "${prefix}_${UUID.randomUUID()}"

    /**
     * Generates a deterministic, collision-safe backfill ID for existing Room records during migration
     * or initial import so that distinct chapters (even with similar names) never merge accidentally.
     */
    fun deterministicChapterBackfillId(id: Long, subject: String, chapterName: String, orderIndex: Int): String {
        val raw = "chapter|$id|${subject.trim().lowercase()}|${chapterName.trim().lowercase()}|$orderIndex"
        return "ch_${sha256Short(raw)}"
    }

    /**
     * Generates a deterministic ID for an imported chapter when no explicit SyncID column was present
     * in the source spreadsheet, disambiguated by occurrence index within the import batch.
     */
    fun deterministicImportedChapterId(subject: String, chapterName: String, occurrenceIndex: Int = 0): String {
        val normSub = subject.trim().lowercase()
        val normName = chapterName.trim().lowercase().replace(Regex("\\s+"), " ")
        val suffix = if (occurrenceIndex <= 0) "" else "#$occurrenceIndex"
        return "ch_${sha256Short("import|$normSub|$normName$suffix")}"
    }

    /**
     * Stable composite identity for DailyScheduleEntity based on its primary key date (YYYY-MM-DD).
     */
    fun scheduleSyncId(date: String): String = "sched_${date.trim()}"

    /**
     * Generates a deterministic backfill ID for existing MistakeEntryEntity rows during migration.
     */
    fun deterministicMistakeBackfillId(id: Long, subject: String, chapterName: String, timestamp: Long): String {
        val raw = "mistake|$id|${subject.trim().lowercase()}|${chapterName.trim().lowercase()}|$timestamp"
        return "mk_${sha256Short(raw)}"
    }

    fun sha256Short(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.take(12).joinToString("") { "%02x".format(it) }
    }
}

@Entity(tableName = "sync_outbox")
data class SyncOutboxEntity(
    @PrimaryKey
    val operationId: String = UUID.randomUUID().toString(),
    val entityType: String, // "CHAPTER", "DAILY_SCHEDULE", "MISTAKE_ENTRY"
    val syncId: String,
    val operationType: String, // "UPSERT", "DELETE"
    val payloadJson: String,
    val baseVersionHash: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val retryCount: Int = 0,
    val status: String = "PENDING", // "PENDING", "IN_FLIGHT", "FAILED", "COMPLETED"
    val lastError: String = ""
)

@Entity(tableName = "sync_conflicts")
data class SyncConflictEntity(
    @PrimaryKey
    val conflictId: String, // "${entityType}_${syncId}"
    val entityType: String, // "CHAPTER", "DAILY_SCHEDULE", "MISTAKE_ENTRY"
    val syncId: String,
    val entityTitle: String,
    val baselinePayloadJson: String,
    val localPayloadJson: String,
    val remotePayloadJson: String,
    val conflictingFieldsSummary: String,
    val localUpdatedAt: Long,
    val remoteUpdatedAt: Long,
    val detectedAt: Long = System.currentTimeMillis(),
    val isResolved: Boolean = false,
    val resolutionChoice: String = "", // "KEEP_LOCAL", "KEEP_REMOTE", "MANUAL_MERGE"
    val losingVersionArchiveJson: String = "",
    val resolvedAt: Long = 0L
)

@Entity(tableName = "sync_workbook_config")
data class SyncWorkbookConfigEntity(
    @PrimaryKey
    val id: Int = 1,
    val connectedAccountEmail: String = "",
    val connectedAccountName: String = "",
    val grantedScopesCsv: String = "",
    val spreadsheetId: String = "",
    val spreadsheetTitle: String = "",
    val spreadsheetUrl: String = "",
    val sourceWorkbookFileName: String = "",
    val chaptersSheetName: String = "App_Chapters_Sync",
    val scheduleSheetName: String = "App_Schedule_80D_Sync",
    val mistakesSheetName: String = "App_Mistakes_Sync",
    val metaSheetName: String = "App_Sync_Meta",
    val preservedSheetNamesCsv: String = "",
    val deviceId: String = "android_local",
    val syncStatus: String = "DISCONNECTED",
    val lastSuccessfulSyncAt: Long = 0L,
    val lastAttemptedSyncAt: Long = 0L,
    val lastSyncSummary: String = "",
    val lastErrorMessage: String = "",
    val preLinkBackupDone: Boolean = false,
    val autoBackgroundSyncEnabled: Boolean = true
) {
    val isAccountConnected: Boolean
        get() = connectedAccountEmail.isNotBlank()

    val isSpreadsheetLinked: Boolean
        get() = spreadsheetId.isNotBlank()
}
