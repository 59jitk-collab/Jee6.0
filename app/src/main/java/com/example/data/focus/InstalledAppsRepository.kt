package com.example.data.focus

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Represents a real launchable application installed on the user's Android device.
 */
data class InstalledAppInfo(
    val packageName: String,
    val appName: String,
    val isSystemApp: Boolean,
    val icon: Drawable? = null
)

object InstalledAppsRepository {

    /**
     * Queries PackageManager for actual launchable applications installed on the device.
     * Uses the <queries> MAIN/LAUNCHER declaration in AndroidManifest.xml.
     * Never fabricates or mocks applications.
     */
    suspend fun getLaunchableApps(context: Context): List<InstalledAppInfo> = withContext(Dispatchers.IO) {
        queryLaunchableAppsSync(context)
    }

    fun queryLaunchableAppsSync(context: Context): List<InstalledAppInfo> {
        return try {
            val pm = context.packageManager
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }

            val resolveInfos: List<ResolveInfo> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(
                    mainIntent,
                    PackageManager.ResolveInfoFlags.of(0L)
                )
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(mainIntent, 0)
            }

            val ownPackage = context.packageName
            val uniquePackages = mutableMapOf<String, InstalledAppInfo>()

            for (resolveInfo in resolveInfos) {
                val activityInfo = resolveInfo.activityInfo ?: continue
                val pkgName = activityInfo.packageName ?: continue

                // Never allow restricting our own app or essential emergency/dialer/settings packages
                if (pkgName == ownPackage || FocusGuardManager.isProtectedSystemPackage(pkgName)) {
                    continue
                }

                if (!uniquePackages.containsKey(pkgName)) {
                    val appInfo = activityInfo.applicationInfo
                    val label = try {
                        resolveInfo.loadLabel(pm).toString().takeIf { it.isNotBlank() }
                            ?: appInfo?.loadLabel(pm)?.toString()
                            ?: pkgName
                    } catch (_: Exception) {
                        pkgName
                    }

                    val iconDrawable = try {
                        resolveInfo.loadIcon(pm)
                    } catch (_: Exception) {
                        null
                    }

                    val isSystem = appInfo != null &&
                            ((appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0) &&
                            ((appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0)

                    uniquePackages[pkgName] = InstalledAppInfo(
                        packageName = pkgName,
                        appName = label,
                        isSystemApp = isSystem,
                        icon = iconDrawable
                    )
                }
            }

            uniquePackages.values.sortedWith(
                compareBy<InstalledAppInfo> { it.isSystemApp }
                    .thenBy { it.appName.lowercase() }
            )
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Filters installed applications by search query (matches app name or package name)
     * and optionally hides pre-installed system utilities unless searched or selected.
     */
    fun filterApps(
        apps: List<InstalledAppInfo>,
        searchQuery: String,
        includeSystemApps: Boolean = true,
        selectedPackages: Set<String> = emptySet()
    ): List<InstalledAppInfo> {
        val trimmed = searchQuery.trim().lowercase()
        return apps.filter { app ->
            val matchesVisibility = includeSystemApps || !app.isSystemApp || selectedPackages.contains(app.packageName)
            val matchesSearch = if (trimmed.isEmpty()) {
                true
            } else {
                app.appName.lowercase().contains(trimmed) || app.packageName.lowercase().contains(trimmed)
            }
            matchesVisibility && matchesSearch
        }
    }

    /**
     * Toggles selection for a package name, ignoring protected system packages.
     */
    fun togglePackageSelection(currentSelection: Set<String>, packageName: String): Set<String> {
        val cleanPkg = packageName.trim()
        if (cleanPkg.isEmpty() || FocusGuardManager.isProtectedSystemPackage(cleanPkg)) {
            return currentSelection
        }
        return if (currentSelection.contains(cleanPkg)) {
            currentSelection - cleanPkg
        } else {
            currentSelection + cleanPkg
        }
    }
}
