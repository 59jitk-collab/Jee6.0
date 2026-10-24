package com.example.data.wellbeing

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

data class AppUsageItem(
    val packageName: String,
    val appName: String,
    val totalTimeInForegroundMillis: Long,
    val usagePercentage: Float,
    val isStudyOrProductiveApp: Boolean
) {
    val totalMinutes: Int
        get() = (totalTimeInForegroundMillis / (1000 * 60)).toInt()

    val formattedDuration: String
        get() {
            val mins = totalMinutes
            val hours = mins / 60
            val remainingMins = mins % 60
            return if (hours > 0) "${hours}h ${remainingMins}m" else "${remainingMins}m"
        }
}

data class DailyWellbeingStats(
    val isPermissionGranted: Boolean,
    val isSupportedOnDevice: Boolean,
    val totalScreenTimeMillis: Long,
    val topApps: List<AppUsageItem>,
    val studyAppsTimeMillis: Long,
    val otherAppsTimeMillis: Long,
    val hasUsageData: Boolean
) {
    val totalScreenTimeMinutes: Int
        get() = (totalScreenTimeMillis / (1000 * 60)).toInt()

    val formattedTotalTime: String
        get() {
            val mins = totalScreenTimeMinutes
            val hours = mins / 60
            val remainingMins = mins % 60
            return if (hours > 0) "${hours}h ${remainingMins}m" else "${remainingMins}m"
        }
}

object DigitalWellbeingManager {

    /**
     * Checks if the user has explicitly granted PACKAGE_USAGE_STATS permission via system settings.
     */
    fun hasUsageStatsPermission(context: Context): Boolean {
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName
                )
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName
                )
            }
            mode == AppOpsManager.MODE_ALLOWED
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Creates an Intent to open the Android Usage Access Settings screen.
     * Only launched after explicit user action/tap in the UI.
     */
    fun createUsageAccessSettingsIntent(): Intent {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return intent
    }

    /**
     * Returns the package name of the currently resumed foreground app using UsageEvents
     * if Usage Access permission is granted. Used as a supplementary guard check during active Focus sessions.
     */
    fun getCurrentForegroundPackage(context: Context): String? {
        if (!hasUsageStatsPermission(context)) return null
        return try {
            val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                ?: return null
            val endTime = System.currentTimeMillis()
            val startTime = endTime - 15_000L
            val usageEvents = usageStatsManager.queryEvents(startTime, endTime) ?: return null
            val event = UsageEvents.Event()
            var latestPkg: String? = null
            var latestTime = 0L
            while (usageEvents.hasNextEvent()) {
                usageEvents.getNextEvent(event)
                if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED && event.timeStamp >= latestTime) {
                    latestTime = event.timeStamp
                    latestPkg = event.packageName
                }
            }
            latestPkg
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Queries daily screen time from Android UsageStatsManager.
     * Runs strictly on Dispatchers.IO.
     * All data stays strictly on-device.
     */
    suspend fun getDailyUsageStats(context: Context): DailyWellbeingStats = withContext(Dispatchers.IO) {
        val isGranted = hasUsageStatsPermission(context)
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager

        if (usageStatsManager == null) {
            return@withContext DailyWellbeingStats(
                isPermissionGranted = isGranted,
                isSupportedOnDevice = false,
                totalScreenTimeMillis = 0L,
                topApps = emptyList(),
                studyAppsTimeMillis = 0L,
                otherAppsTimeMillis = 0L,
                hasUsageData = false
            )
        }

        if (!isGranted) {
            return@withContext DailyWellbeingStats(
                isPermissionGranted = false,
                isSupportedOnDevice = true,
                totalScreenTimeMillis = 0L,
                topApps = emptyList(),
                studyAppsTimeMillis = 0L,
                otherAppsTimeMillis = 0L,
                hasUsageData = false
            )
        }

        val cal = Calendar.getInstance()
        val endTime = cal.timeInMillis
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val startTime = cal.timeInMillis

        val rawStats: List<UsageStats> = try {
            usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                startTime,
                endTime
            ) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }

        if (rawStats.isEmpty()) {
            return@withContext DailyWellbeingStats(
                isPermissionGranted = true,
                isSupportedOnDevice = true,
                totalScreenTimeMillis = 0L,
                topApps = emptyList(),
                studyAppsTimeMillis = 0L,
                otherAppsTimeMillis = 0L,
                hasUsageData = false
            )
        }

        // Aggregate by package to remove duplicate intervals
        val aggregatedMap = mutableMapOf<String, Long>()
        for (stat in rawStats) {
            if (stat.totalTimeInForeground > 0L) {
                val current = aggregatedMap[stat.packageName] ?: 0L
                aggregatedMap[stat.packageName] = current + stat.totalTimeInForeground
            }
        }

        val pm = context.packageManager
        val knownSystemNoise = setOf(
            "com.android.systemui",
            "com.android.launcher",
            "com.google.android.apps.nexuslauncher",
            "com.google.android.inputmethod.latin",
            "android"
        )

        val appItems = mutableListOf<AppUsageItem>()
        var totalMillis = 0L
        var studyMillis = 0L
        var otherMillis = 0L

        val productiveKeywords = setOf("jee", "study", "notes", "reader", "pdf", "calc", "learn", "physics", "khan", "coursera", "example")

        for ((pkg, timeMillis) in aggregatedMap) {
            if (timeMillis < 60_000L) continue // ignore under 1 minute
            if (knownSystemNoise.contains(pkg)) continue

            val appName = try {
                val appInfo = pm.getApplicationInfo(pkg, 0)
                pm.getApplicationLabel(appInfo).toString()
            } catch (_: PackageManager.NameNotFoundException) {
                pkg.substringAfterLast('.')
            }

            val isStudy = productiveKeywords.any { appName.contains(it, ignoreCase = true) || pkg.contains(it, ignoreCase = true) }

            totalMillis += timeMillis
            if (isStudy) studyMillis += timeMillis else otherMillis += timeMillis

            appItems.add(
                AppUsageItem(
                    packageName = pkg,
                    appName = appName,
                    totalTimeInForegroundMillis = timeMillis,
                    usagePercentage = 0f,
                    isStudyOrProductiveApp = isStudy
                )
            )
        }

        val sorted = appItems
            .sortedByDescending { it.totalTimeInForegroundMillis }
            .take(8)
            .map { item ->
                val pct = if (totalMillis > 0L) {
                    (item.totalTimeInForegroundMillis.toFloat() / totalMillis.toFloat()) * 100f
                } else 0f
                item.copy(usagePercentage = pct)
            }

        DailyWellbeingStats(
            isPermissionGranted = true,
            isSupportedOnDevice = true,
            totalScreenTimeMillis = totalMillis,
            topApps = sorted,
            studyAppsTimeMillis = studyMillis,
            otherAppsTimeMillis = otherMillis,
            hasUsageData = sorted.isNotEmpty()
        )
    }
}
