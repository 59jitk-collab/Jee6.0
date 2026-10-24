package com.example.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.data.local.AppDatabase
import java.util.concurrent.TimeUnit

class SheetsSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val db = AppDatabase.getDatabase(applicationContext)
        val config = db.syncDao().getWorkbookConfigSync()
        if (config == null || !config.isSpreadsheetLinked) {
            return Result.success()
        }

        val engine = TwoWaySheetsSyncEngine(applicationContext, db)
        return when (val result = engine.performTwoWaySync(forceFullPush = false)) {
            is SyncExecutionResult.Success -> Result.success()
            is SyncExecutionResult.Skipped -> Result.success()
            is SyncExecutionResult.Failure -> {
                if (result.isAuthRevoked) {
                    // Auth revoked requires user interaction; do not spin retry loops
                    Result.failure()
                } else {
                    Result.retry()
                }
            }
        }
    }

    companion object {
        const val ONE_TIME_SYNC_WORK_NAME = "jee_sheets_one_time_sync"
        const val PERIODIC_SYNC_WORK_NAME = "jee_sheets_periodic_sync"

        fun enqueueImmediateSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<SheetsSyncWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    30,
                    TimeUnit.SECONDS
                )
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                ONE_TIME_SYNC_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request
            )
        }

        fun schedulePeriodicSync(context: Context, enabled: Boolean) {
            val workManager = WorkManager.getInstance(context)
            if (!enabled) {
                workManager.cancelUniqueWork(PERIODIC_SYNC_WORK_NAME)
                return
            }

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val periodicRequest = PeriodicWorkRequestBuilder<SheetsSyncWorker>(
                15,
                TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    30,
                    TimeUnit.SECONDS
                )
                .build()

            workManager.enqueueUniquePeriodicWork(
                PERIODIC_SYNC_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                periodicRequest
            )
        }
    }
}
