package com.example.data.focus

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.example.MainActivity
import com.example.data.local.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

/**
 * Transparent, user-enabled AccessibilityService that enforces Focus Guard during active study sessions.
 * - Requires explicit manual activation by the user in Android Accessibility Settings.
 * - Does NOT retrieve window text or personal content (canRetrieveWindowContent="false").
 * - Always permits dialer, emergency calling, Android Settings, SystemUI, and input methods.
 * - Immediately exits blocked apps using GLOBAL_ACTION_HOME and redirects back to MainActivity.
 */
class FocusGuardAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var lastRedirectTimestampMillis: Long = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        observeActiveSessionFromDb()
    }

    private fun observeActiveSessionFromDb() {
        serviceScope.launch {
            try {
                val db = AppDatabase.getDatabase(applicationContext)
                db.focusSessionDao().getActiveSession().collectLatest { session ->
                    FocusGuardManager.syncActiveSession(session)
                }
            } catch (_: Exception) {
                // Ignore DB initialization issues in restricted contexts
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val type = event.eventType
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            type != AccessibilityEvent.TYPE_WINDOWS_CHANGED
        ) {
            return
        }

        val foregroundPkg = event.packageName?.toString() ?: return
        if (foregroundPkg == applicationContext.packageName ||
            FocusGuardManager.isProtectedSystemPackage(foregroundPkg)
        ) {
            return
        }

        val session = FocusGuardManager.currentActiveSession
        if (session == null) {
            // Ensure session is synced from Room if service restarted
            serviceScope.launch {
                try {
                    val dbSession = AppDatabase.getDatabase(applicationContext)
                        .focusSessionDao()
                        .getActiveSession()
                        .firstOrNull()
                    FocusGuardManager.syncActiveSession(dbSession)
                } catch (_: Exception) {}
            }
            return
        }

        val now = System.currentTimeMillis()
        if (FocusGuardManager.shouldBlockPackage(session, foregroundPkg, now)) {
            // Debounce rapid window state transitions (minimum 900ms between redirects)
            if (now - lastRedirectTimestampMillis < 900L) return
            lastRedirectTimestampMillis = now
            FocusGuardManager.lastInterceptedPackage = foregroundPkg

            // 1. Immediately exit the blocked app to Home screen via OS Accessibility global action
            try {
                performGlobalAction(GLOBAL_ACTION_HOME)
            } catch (_: Exception) {}

            // 2. Redirect back to JEE Study OS Focus Guard timer screen
            val redirectIntent = Intent(applicationContext, MainActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                )
                putExtra(FocusGuardManager.EXTRA_INTERCEPTED_PACKAGE, foregroundPkg)
            }
            try {
                startActivity(redirectIntent)
            } catch (_: Exception) {
                // Safe catch if activity launch is restricted by OEM background policy
            }
        }
    }

    override fun onInterrupt() {
        // No-op: service cleanly releases any state on interrupt
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
