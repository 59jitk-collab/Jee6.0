package com.example.focus

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.focus.FocusGuardManager
import com.example.data.focus.InstalledAppInfo
import com.example.data.focus.InstalledAppsRepository
import com.example.util.StudyNotificationManager
import com.example.util.SystemBarHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FocusGuardAndSystemTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        FocusGuardManager.syncActiveSession(null)
        FocusGuardManager.lastInterceptedPackage = null
        SystemBarHelper.ensureNormalSystemBars(null)
    }

    @Test
    fun systemBarHelper_defaultsToNormalSystemBars_andRestoresWhenImmersiveDisabled() {
        assertFalse(SystemBarHelper.isImmersiveModeActive)
        // Opt-in immersive mode activates when explicitly requested
        SystemBarHelper.setOptInImmersiveMode(null, true)
        assertTrue(SystemBarHelper.isImmersiveModeActive)
        // Disabling opt-in or calling ensureNormalSystemBars immediately restores normal bars
        SystemBarHelper.setOptInImmersiveMode(null, false)
        assertFalse(SystemBarHelper.isImmersiveModeActive)
        SystemBarHelper.setOptInImmersiveMode(null, true)
        SystemBarHelper.ensureNormalSystemBars(null)
        assertFalse(SystemBarHelper.isImmersiveModeActive)
    }

    @Test
    fun installedAppsRepository_filtersByQuery_andHandlesMultiSelectToggle() {
        val sampleApps = listOf(
            InstalledAppInfo(packageName = "com.instagram.android", appName = "Instagram", icon = null, isSystemApp = false),
            InstalledAppInfo(packageName = "com.google.android.youtube", appName = "YouTube", icon = null, isSystemApp = false),
            InstalledAppInfo(packageName = "com.android.calculator2", appName = "Calculator", icon = null, isSystemApp = true)
        )

        // Default: exclude system apps unless selected or includeSystemApps=true
        val userOnly = InstalledAppsRepository.filterApps(
            apps = sampleApps,
            searchQuery = "",
            includeSystemApps = false,
            selectedPackages = emptySet()
        )
        assertEquals(2, userOnly.size)
        assertFalse(userOnly.any { it.packageName == "com.android.calculator2" })

        // Search filter by app name or package
        val searchYoutube = InstalledAppsRepository.filterApps(
            apps = sampleApps,
            searchQuery = "you",
            includeSystemApps = true,
            selectedPackages = emptySet()
        )
        assertEquals(1, searchYoutube.size)
        assertEquals("com.google.android.youtube", searchYoutube.first().packageName)

        // Multi-select toggle add and remove
        var selected = emptySet<String>()
        selected = InstalledAppsRepository.togglePackageSelection(selected, "com.instagram.android")
        assertTrue(selected.contains("com.instagram.android"))

        selected = InstalledAppsRepository.togglePackageSelection(selected, "com.google.android.youtube")
        assertEquals(2, selected.size)

        selected = InstalledAppsRepository.togglePackageSelection(selected, "com.instagram.android")
        assertFalse(selected.contains("com.instagram.android"))
        assertEquals(1, selected.size)
    }

    @Test
    fun focusGuardManager_whitelistsDialerSettingsAndSystemPackages_andStopsCleanly() {
        assertTrue(FocusGuardManager.isProtectedSystemPackage("com.android.settings"))
        assertTrue(FocusGuardManager.isProtectedSystemPackage("com.google.android.dialer"))
        assertTrue(FocusGuardManager.isProtectedSystemPackage("com.android.systemui"))
        assertFalse(FocusGuardManager.isProtectedSystemPackage("com.instagram.android"))

        val startMs = 1_000_000L
        val session = FocusGuardManager.startSession(
            studyDurationMinutes = 45,
            permittedBreaks = 2,
            breakDurationMinutes = 5,
            blockedPackagesCsv = "com.instagram.android,com.android.settings",
            activeTaskName = "Physics Rotational Motion",
            nowMillis = startMs
        )

        assertNotNull(FocusGuardManager.currentActiveSession)

        // Restricted user app is blocked while RUNNING, but system settings is never blocked
        assertTrue(FocusGuardManager.shouldBlockPackage(session, "com.instagram.android", startMs + 10_000L))
        assertFalse(FocusGuardManager.shouldBlockPackage(session, "com.android.settings", startMs + 10_000L))

        // Pause disables blocking
        val paused = FocusGuardManager.pauseSession(session, startMs + 20_000L)
        assertEquals("PAUSED", paused.sessionStatus)
        assertFalse(FocusGuardManager.shouldBlockPackage(paused, "com.instagram.android", startMs + 25_000L))

        // Resume re-enables blocking
        val resumed = FocusGuardManager.resumeSession(paused, startMs + 30_000L)
        assertEquals("RUNNING", resumed.sessionStatus)
        assertTrue(FocusGuardManager.shouldBlockPackage(resumed, "com.instagram.android", startMs + 35_000L))

        // Stop / Cancel immediately disables blocking and clears active session when synced null
        val cancelled = FocusGuardManager.cancelSession(resumed)
        FocusGuardManager.syncActiveSession(null)
        assertEquals("CANCELLED", cancelled.sessionStatus)
        assertFalse(FocusGuardManager.shouldBlockPackage(cancelled, "com.instagram.android", startMs + 45_000L))
        assertNull(FocusGuardManager.currentActiveSession)
    }

    @Test
    fun studyNotificationManager_createsChannels_andSchedulesOrReportsDiagnostics() {
        assertTrue(StudyNotificationManager.ensureChannelsCreated(context))
        val status = StudyNotificationManager.getStatusInfo(context)
        assertTrue(status.isChannelCreated)
        assertTrue(status.diagnosticMessage.isNotBlank())

        val scheduleResult = StudyNotificationManager.scheduleDailyStudyReminder(
            context = context,
            hourOfDay = 8,
            minute = 0
        )
        assertTrue(scheduleResult.diagnosticMessage.isNotBlank())

        val cancelResult = StudyNotificationManager.cancelDailyStudyReminder(context)
        assertTrue(cancelResult.success)
    }
}
