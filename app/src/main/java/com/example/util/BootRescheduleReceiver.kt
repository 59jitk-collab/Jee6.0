package com.example.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Reschedules persisted study and spaced-revision alarms after device reboot,
 * app update, or timezone/time changes when permission is granted.
 */
class BootRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == Intent.ACTION_TIMEZONE_CHANGED ||
            action == Intent.ACTION_TIME_CHANGED
        ) {
            StudyNotificationManager.reschedulePersistedAlarms(context)
        }
    }
}
