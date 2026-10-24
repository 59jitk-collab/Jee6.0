package com.example.data.focus

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.TextUtils
import com.example.data.local.entities.FocusSessionEntity

object FocusGuardManager {

    const val EXTRA_INTERCEPTED_PACKAGE = "extra_intercepted_package"

    private val SYSTEM_WHITELIST = setOf(
        "com.android.phone",
        "com.android.dialer",
        "com.android.server.telecom",
        "com.android.emergency",
        "com.android.settings",
        "com.android.systemui",
        "com.google.android.dialer",
        "com.google.android.inputmethod",
        "com.android.inputmethod",
        "com.google.android.permissioncontroller",
        "com.android.permissioncontroller",
        "com.example",
        "com.aistudio"
    )

    @Volatile
    var currentActiveSession: FocusSessionEntity? = null
        private set

    @Volatile
    var lastInterceptedPackage: String? = null

    fun syncActiveSession(session: FocusSessionEntity?) {
        currentActiveSession = session
        if (session == null || session.sessionStatus != "RUNNING") {
            lastInterceptedPackage = null
        }
    }

    fun isProtectedSystemPackage(pkgName: String): Boolean {
        val clean = pkgName.trim()
        if (clean.isEmpty()) return true
        return SYSTEM_WHITELIST.any { clean.startsWith(it, ignoreCase = true) }
    }

    /**
     * Verifies whether the user has explicitly enabled FocusGuardAccessibilityService
     * in Android System Accessibility Settings. Never claims active without verification.
     */
    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        return try {
            val accessibilityEnabled = Settings.Secure.getInt(
                context.contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED,
                0
            )
            if (accessibilityEnabled != 1) return false

            val enabledServicesStr = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false

            val expectedComponent = ComponentName(context, FocusGuardAccessibilityService::class.java)
            val colonSplitter = TextUtils.SimpleStringSplitter(':')
            colonSplitter.setString(enabledServicesStr)
            while (colonSplitter.hasNext()) {
                val componentNameString = colonSplitter.next()
                val enabledComponent = ComponentName.unflattenFromString(componentNameString)
                if (enabledComponent != null && enabledComponent == expectedComponent) {
                    return true
                }
            }
            false
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Creates an Intent to open Android Accessibility Settings so the user can manually
     * enable or disable JEE Study OS Focus Guard.
     */
    fun createAccessibilitySettingsIntent(): Intent {
        return Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun getRemainingStudySeconds(session: FocusSessionEntity, nowMillis: Long = System.currentTimeMillis()): Long {
        if (session.sessionStatus == "IDLE" || session.sessionStatus == "COMPLETED" || session.sessionStatus == "CANCELLED") {
            return 0L
        }

        val configuredDurationSeconds = ((session.targetEndTimeMillis - session.sessionStartTimeMillis) / 1000L)
            .coerceAtLeast(session.studyDurationMinutes * 60L)
        val effectiveTargetEnd = session.targetEndTimeMillis + session.totalPausedDurationMillis

        val remainingSec = if (session.sessionStatus == "PAUSED") {
            val pausedAt = if (session.pauseStartTimeMillis > 0) session.pauseStartTimeMillis else nowMillis
            ((effectiveTargetEnd - pausedAt) / 1000L)
        } else {
            ((effectiveTargetEnd - nowMillis) / 1000L)
        }

        return remainingSec.coerceIn(0L, configuredDurationSeconds)
    }

    fun getRemainingBreakSeconds(session: FocusSessionEntity, nowMillis: Long = System.currentTimeMillis()): Long {
        if (session.sessionStatus != "BREAK" || session.breakEndTimeMillis <= 0L) {
            return 0L
        }
        return ((session.breakEndTimeMillis - nowMillis) / 1000L).coerceAtLeast(0L)
    }

    fun isSessionExpired(session: FocusSessionEntity, nowMillis: Long = System.currentTimeMillis()): Boolean {
        if (session.sessionStatus == "IDLE" || session.sessionStatus == "COMPLETED" || session.sessionStatus == "CANCELLED") {
            return true
        }
        return getRemainingStudySeconds(session, nowMillis) <= 0L
    }

    fun isBreakExpired(session: FocusSessionEntity, nowMillis: Long = System.currentTimeMillis()): Boolean {
        if (session.sessionStatus != "BREAK") return true
        return getRemainingBreakSeconds(session, nowMillis) <= 0L
    }

    fun startSession(
        studyDurationMinutes: Int,
        permittedBreaks: Int,
        breakDurationMinutes: Int,
        blockedPackagesCsv: String,
        activeTaskName: String,
        nowMillis: Long = System.currentTimeMillis(),
        extraSeconds: Int = 0
    ): FocusSessionEntity {
        val totalSeconds = (studyDurationMinutes * 60L + extraSeconds).coerceAtLeast(10L)
        val durationMillis = totalSeconds * 1000L
        val session = FocusSessionEntity(
            id = 1L,
            sessionStatus = "RUNNING",
            studyDurationMinutes = studyDurationMinutes.coerceAtLeast(1),
            permittedBreaks = permittedBreaks,
            breaksRemaining = permittedBreaks,
            breakDurationMinutes = breakDurationMinutes,
            sessionStartTimeMillis = nowMillis,
            targetEndTimeMillis = nowMillis + durationMillis,
            breakStartTimeMillis = 0L,
            breakEndTimeMillis = 0L,
            pauseStartTimeMillis = 0L,
            totalPausedDurationMillis = 0L,
            blockedPackagesCsv = blockedPackagesCsv,
            activeTaskName = activeTaskName
        )
        syncActiveSession(session)
        return session
    }

    fun takeBreak(session: FocusSessionEntity, nowMillis: Long = System.currentTimeMillis()): FocusSessionEntity {
        check(session.breaksRemaining > 0) { "No breaks remaining in allowance!" }
        check(session.sessionStatus == "RUNNING" || session.sessionStatus == "PAUSED") { "Can only take break during active study session!" }

        val breakDurationMillis = session.breakDurationMinutes * 60 * 1000L

        val updated = session.copy(
            sessionStatus = "BREAK",
            breaksRemaining = session.breaksRemaining - 1,
            breakStartTimeMillis = nowMillis,
            breakEndTimeMillis = nowMillis + breakDurationMillis
        )
        syncActiveSession(updated)
        return updated
    }

    fun endBreak(session: FocusSessionEntity): FocusSessionEntity {
        if (session.sessionStatus != "BREAK") return session
        val updated = session.copy(
            sessionStatus = "RUNNING",
            breakStartTimeMillis = 0L,
            breakEndTimeMillis = 0L
        )
        syncActiveSession(updated)
        return updated
    }

    fun pauseSession(session: FocusSessionEntity, nowMillis: Long = System.currentTimeMillis()): FocusSessionEntity {
        if (session.sessionStatus != "RUNNING") return session
        val updated = session.copy(
            sessionStatus = "PAUSED",
            pauseStartTimeMillis = nowMillis
        )
        syncActiveSession(updated)
        return updated
    }

    fun resumeSession(session: FocusSessionEntity, nowMillis: Long = System.currentTimeMillis()): FocusSessionEntity {
        if (session.sessionStatus != "PAUSED") return session
        val additionalPause = if (session.pauseStartTimeMillis > 0) nowMillis - session.pauseStartTimeMillis else 0L
        val updated = session.copy(
            sessionStatus = "RUNNING",
            pauseStartTimeMillis = 0L,
            totalPausedDurationMillis = session.totalPausedDurationMillis + additionalPause
        )
        syncActiveSession(updated)
        return updated
    }

    fun cancelSession(session: FocusSessionEntity): FocusSessionEntity {
        val updated = session.copy(sessionStatus = "CANCELLED")
        syncActiveSession(updated)
        return updated
    }

    fun completeSession(session: FocusSessionEntity): FocusSessionEntity {
        val updated = session.copy(sessionStatus = "COMPLETED")
        syncActiveSession(updated)
        return updated
    }

    fun shouldBlockPackage(
        session: FocusSessionEntity,
        pkgName: String,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean {
        if (session.sessionStatus != "RUNNING") return false
        if (isSessionExpired(session, nowMillis)) return false
        if (isProtectedSystemPackage(pkgName)) return false

        return session.blockedPackageList.any { pkgName.equals(it, ignoreCase = true) }
    }
}
