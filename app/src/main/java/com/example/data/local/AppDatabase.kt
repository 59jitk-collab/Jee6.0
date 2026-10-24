package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.local.dao.ChapterDao
import com.example.data.local.dao.DailyScheduleDao
import com.example.data.local.dao.FocusSessionDao
import com.example.data.local.dao.MistakeEntryDao
import com.example.data.local.dao.RevisionItemDao
import com.example.data.local.dao.SyncDao
import com.example.data.local.dao.TestRecordDao
import com.example.data.local.dao.UserSettingsDao
import com.example.data.local.dao.XpEventDao
import com.example.data.local.entities.ChapterEntity
import com.example.data.local.entities.DailyScheduleEntity
import com.example.data.local.entities.FocusSessionEntity
import com.example.data.local.entities.MistakeEntryEntity
import com.example.data.local.entities.RevisionItemEntity
import com.example.data.local.entities.SyncConflictEntity
import com.example.data.local.entities.SyncIdentityHelper
import com.example.data.local.entities.SyncOutboxEntity
import com.example.data.local.entities.SyncWorkbookConfigEntity
import com.example.data.local.entities.TestRecordEntity
import com.example.data.local.entities.UserSettingsEntity
import com.example.data.local.entities.XpEventEntity

@Database(
    entities = [
        ChapterEntity::class,
        DailyScheduleEntity::class,
        TestRecordEntity::class,
        MistakeEntryEntity::class,
        RevisionItemEntity::class,
        UserSettingsEntity::class,
        FocusSessionEntity::class,
        XpEventEntity::class,
        SyncOutboxEntity::class,
        SyncConflictEntity::class,
        SyncWorkbookConfigEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chapterDao(): ChapterDao
    abstract fun dailyScheduleDao(): DailyScheduleDao
    abstract fun testRecordDao(): TestRecordDao
    abstract fun mistakeEntryDao(): MistakeEntryDao
    abstract fun revisionItemDao(): RevisionItemDao
    abstract fun userSettingsDao(): UserSettingsDao
    abstract fun focusSessionDao(): FocusSessionDao
    abstract fun xpEventDao(): XpEventDao
    abstract fun syncDao(): SyncDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Non-destructive migration from version 4 to version 5:
         * - Preserves all existing chapters, daily schedules, tests, mistakes, revisions, settings, focus sessions, and XP events.
         * - Adds sync metadata columns (syncId, schemaVersion, createdAt, updatedAt, lastSyncedAt, remoteVersionHash,
         *   lastSyncedBaselineJson, originDeviceId, syncState, isDeleted) to chapters, daily_schedules, and mistake_entries.
         * - Backfills collision-safe, deterministic syncId values for all pre-existing rows so distinct chapters are never merged.
         * - Creates sync_outbox, sync_conflicts, and sync_workbook_config tables for local-first two-way Google Sheets sync.
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Alter chapters
                db.execSQL("ALTER TABLE chapters ADD COLUMN syncId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE chapters ADD COLUMN schemaVersion INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE chapters ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE chapters ADD COLUMN lastSyncedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE chapters ADD COLUMN remoteVersionHash TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE chapters ADD COLUMN lastSyncedBaselineJson TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE chapters ADD COLUMN originDeviceId TEXT NOT NULL DEFAULT 'LOCAL'")
                db.execSQL("ALTER TABLE chapters ADD COLUMN syncState TEXT NOT NULL DEFAULT 'LOCAL_ONLY'")
                db.execSQL("ALTER TABLE chapters ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0")

                // Backfill deterministic syncId for existing chapters without merging distinct chapters
                db.query("SELECT id, subject, chapterName, orderIndex, lastUpdated FROM chapters").use { cursor ->
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(0)
                        val subject = cursor.getString(1) ?: ""
                        val chapterName = cursor.getString(2) ?: ""
                        val orderIndex = cursor.getInt(3)
                        val lastUpdated = cursor.getLong(4)
                        val syncId = SyncIdentityHelper.deterministicChapterBackfillId(id, subject, chapterName, orderIndex)
                        db.execSQL(
                            "UPDATE chapters SET syncId = ?, createdAt = ? WHERE id = ?",
                            arrayOf<Any>(syncId, lastUpdated, id)
                        )
                    }
                }

                // 2. Alter daily_schedules
                db.execSQL("ALTER TABLE daily_schedules ADD COLUMN syncId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE daily_schedules ADD COLUMN schemaVersion INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE daily_schedules ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE daily_schedules ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE daily_schedules ADD COLUMN lastSyncedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE daily_schedules ADD COLUMN remoteVersionHash TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE daily_schedules ADD COLUMN lastSyncedBaselineJson TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE daily_schedules ADD COLUMN originDeviceId TEXT NOT NULL DEFAULT 'LOCAL'")
                db.execSQL("ALTER TABLE daily_schedules ADD COLUMN syncState TEXT NOT NULL DEFAULT 'LOCAL_ONLY'")
                db.execSQL("ALTER TABLE daily_schedules ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0")

                val nowMillis = System.currentTimeMillis()
                db.query("SELECT date FROM daily_schedules").use { cursor ->
                    while (cursor.moveToNext()) {
                        val date = cursor.getString(0) ?: ""
                        val syncId = SyncIdentityHelper.scheduleSyncId(date)
                        db.execSQL(
                            "UPDATE daily_schedules SET syncId = ?, createdAt = ?, updatedAt = ? WHERE date = ?",
                            arrayOf<Any>(syncId, nowMillis, nowMillis, date)
                        )
                    }
                }

                // 3. Alter mistake_entries
                db.execSQL("ALTER TABLE mistake_entries ADD COLUMN syncId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE mistake_entries ADD COLUMN schemaVersion INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE mistake_entries ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE mistake_entries ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE mistake_entries ADD COLUMN lastSyncedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE mistake_entries ADD COLUMN remoteVersionHash TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE mistake_entries ADD COLUMN lastSyncedBaselineJson TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE mistake_entries ADD COLUMN originDeviceId TEXT NOT NULL DEFAULT 'LOCAL'")
                db.execSQL("ALTER TABLE mistake_entries ADD COLUMN syncState TEXT NOT NULL DEFAULT 'LOCAL_ONLY'")
                db.execSQL("ALTER TABLE mistake_entries ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0")

                db.query("SELECT id, subject, chapterName, timestamp FROM mistake_entries").use { cursor ->
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(0)
                        val subject = cursor.getString(1) ?: ""
                        val chapterName = cursor.getString(2) ?: ""
                        val timestamp = cursor.getLong(3)
                        val syncId = SyncIdentityHelper.deterministicMistakeBackfillId(id, subject, chapterName, timestamp)
                        db.execSQL(
                            "UPDATE mistake_entries SET syncId = ?, createdAt = ?, updatedAt = ? WHERE id = ?",
                            arrayOf<Any>(syncId, timestamp, timestamp, id)
                        )
                    }
                }

                // 4. Create sync_outbox table
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `sync_outbox` (
                        `operationId` TEXT NOT NULL,
                        `entityType` TEXT NOT NULL,
                        `syncId` TEXT NOT NULL,
                        `operationType` TEXT NOT NULL,
                        `payloadJson` TEXT NOT NULL,
                        `baseVersionHash` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        `retryCount` INTEGER NOT NULL,
                        `status` TEXT NOT NULL,
                        `lastError` TEXT NOT NULL,
                        PRIMARY KEY(`operationId`)
                    )
                    """.trimIndent()
                )

                // 5. Create sync_conflicts table
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `sync_conflicts` (
                        `conflictId` TEXT NOT NULL,
                        `entityType` TEXT NOT NULL,
                        `syncId` TEXT NOT NULL,
                        `entityTitle` TEXT NOT NULL,
                        `baselinePayloadJson` TEXT NOT NULL,
                        `localPayloadJson` TEXT NOT NULL,
                        `remotePayloadJson` TEXT NOT NULL,
                        `conflictingFieldsSummary` TEXT NOT NULL,
                        `localUpdatedAt` INTEGER NOT NULL,
                        `remoteUpdatedAt` INTEGER NOT NULL,
                        `detectedAt` INTEGER NOT NULL,
                        `isResolved` INTEGER NOT NULL,
                        `resolutionChoice` TEXT NOT NULL,
                        `losingVersionArchiveJson` TEXT NOT NULL,
                        `resolvedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`conflictId`)
                    )
                    """.trimIndent()
                )

                // 6. Create sync_workbook_config table
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `sync_workbook_config` (
                        `id` INTEGER NOT NULL,
                        `connectedAccountEmail` TEXT NOT NULL,
                        `connectedAccountName` TEXT NOT NULL,
                        `grantedScopesCsv` TEXT NOT NULL,
                        `spreadsheetId` TEXT NOT NULL,
                        `spreadsheetTitle` TEXT NOT NULL,
                        `spreadsheetUrl` TEXT NOT NULL,
                        `sourceWorkbookFileName` TEXT NOT NULL,
                        `chaptersSheetName` TEXT NOT NULL,
                        `scheduleSheetName` TEXT NOT NULL,
                        `mistakesSheetName` TEXT NOT NULL,
                        `metaSheetName` TEXT NOT NULL,
                        `preservedSheetNamesCsv` TEXT NOT NULL,
                        `deviceId` TEXT NOT NULL,
                        `syncStatus` TEXT NOT NULL,
                        `lastSuccessfulSyncAt` INTEGER NOT NULL,
                        `lastAttemptedSyncAt` INTEGER NOT NULL,
                        `lastSyncSummary` TEXT NOT NULL,
                        `lastErrorMessage` TEXT NOT NULL,
                        `preLinkBackupDone` INTEGER NOT NULL,
                        `autoBackgroundSyncEnabled` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
            }
        }

        /**
         * Non-destructive migration from version 3 to version 4:
         * - Preserves all existing chapters, schedules, tests, mistakes, revisions, settings, and focus sessions.
         * - Adds masteryLevel, lastStudiedDate, revisionStage, and nextRevisionDate to chapters.
         * - Adds snoozedUntilDate and notes to revision_items.
         * - Adds revisionIntervalsCsv, revisionNotificationsEnabled, studyRemindersEnabled, studyReminderHour, studyReminderMinute to user_settings.
         * - Creates xp_events table for idempotent private XP tracking.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE chapters ADD COLUMN masteryLevel TEXT NOT NULL DEFAULT 'NOT_ASSESSED'")
                db.execSQL("ALTER TABLE chapters ADD COLUMN lastStudiedDate TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE chapters ADD COLUMN revisionStage INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE chapters ADD COLUMN nextRevisionDate TEXT NOT NULL DEFAULT ''")

                db.execSQL("ALTER TABLE revision_items ADD COLUMN snoozedUntilDate TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE revision_items ADD COLUMN notes TEXT NOT NULL DEFAULT ''")

                db.execSQL("ALTER TABLE user_settings ADD COLUMN revisionIntervalsCsv TEXT NOT NULL DEFAULT '1,3,7,21'")
                db.execSQL("ALTER TABLE user_settings ADD COLUMN revisionNotificationsEnabled INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE user_settings ADD COLUMN studyRemindersEnabled INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE user_settings ADD COLUMN studyReminderHour INTEGER NOT NULL DEFAULT 21")
                db.execSQL("ALTER TABLE user_settings ADD COLUMN studyReminderMinute INTEGER NOT NULL DEFAULT 0")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `xp_events` (
                        `eventKey` TEXT NOT NULL,
                        `category` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `xpAmount` INTEGER NOT NULL,
                        `dateString` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        PRIMARY KEY(`eventKey`)
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `focus_sessions` (
                        `id` INTEGER NOT NULL,
                        `sessionStatus` TEXT NOT NULL,
                        `studyDurationMinutes` INTEGER NOT NULL,
                        `permittedBreaks` INTEGER NOT NULL,
                        `breaksRemaining` INTEGER NOT NULL,
                        `breakDurationMinutes` INTEGER NOT NULL,
                        `sessionStartTimeMillis` INTEGER NOT NULL,
                        `targetEndTimeMillis` INTEGER NOT NULL,
                        `breakStartTimeMillis` INTEGER NOT NULL,
                        `breakEndTimeMillis` INTEGER NOT NULL,
                        `pauseStartTimeMillis` INTEGER NOT NULL,
                        `totalPausedDurationMillis` INTEGER NOT NULL,
                        `blockedPackagesCsv` TEXT NOT NULL,
                        `activeTaskName` TEXT NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "jee_study_os_db"
                )
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
