package com.example.data.focus

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Process
import android.provider.Settings

data class AppUsageInfo(
    val packageName: String,
    val appName: String,
    val totalTimeInForegroundMillis: Long,
    val isDistracting: Boolean = false
)

object UsageStatsHelper {

    fun hasUsageStatsPermission(context: Context): Boolean {
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
            mode == AppOpsManager.MODE_ALLOWED
        } catch (_: Exception) {
            false
        }
    }

    fun openUsageStatsSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (_: Exception) {
            val intent = Intent(Settings.ACTION_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    fun hasOverlayPermission(context: Context): Boolean {
        return Settings.canDrawOverlays(context)
    }

    fun openOverlaySettings(context: Context) {
        try {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (_: Exception) {
            val intent = Intent(Settings.ACTION_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    fun getTodayAppUsageStats(context: Context): List<AppUsageInfo> {
        if (!hasUsageStatsPermission(context)) return emptyList()

        return try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val endTime = System.currentTimeMillis()
            val startTime = endTime - (1000L * 60L * 60L * 24L) // Last 24 hours

            val statsMap = usm.queryAndAggregateUsageStats(startTime, endTime)
            val pm = context.packageManager

            val list = mutableListOf<AppUsageInfo>()
            val distractingKeywords = listOf("instagram", "tiktok", "youtube", "facebook", "twitter", "snapchat", "pubg", "game")

            for ((pkg, stats) in statsMap) {
                if (stats.totalTimeInForeground > 60 * 1000L) { // > 1 min
                    val appName = try {
                        val appInfo = pm.getApplicationInfo(pkg, 0)
                        pm.getApplicationLabel(appInfo).toString()
                    } catch (_: Exception) {
                        pkg
                    }
                    val isDistracting = distractingKeywords.any { pkg.lowercase().contains(it) || appName.lowercase().contains(it) }
                    list.add(
                        AppUsageInfo(
                            packageName = pkg,
                            appName = appName,
                            totalTimeInForegroundMillis = stats.totalTimeInForeground,
                            isDistracting = isDistracting
                        )
                    )
                }
            }
            list.sortedByDescending { it.totalTimeInForegroundMillis }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
