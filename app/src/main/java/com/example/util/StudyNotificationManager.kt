package com.example.util

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import java.util.Calendar
import java.util.Locale

data class NotificationStatusInfo(
    val isPermissionGranted: Boolean,
    val areSystemNotificationsEnabled: Boolean,
    val isChannelCreated: Boolean,
    val isReminderScheduled: Boolean,
    val scheduledTimeFormatted: String?,
    val isRevisionReminderScheduled: Boolean,
    val diagnosticMessage: String
)

data class NotificationActionResult(
    val success: Boolean,
    val diagnosticMessage: String
)

object StudyNotificationManager {

    const val CHANNEL_STUDY_REMINDERS = "jee_study_reminders_channel"
    const val CHANNEL_FOCUS_SESSIONS = "jee_focus_session_channel"
    const val CHANNEL_REVISION_REMINDERS = "jee_revision_reminders_channel"

    const val EXTRA_NOTIFICATION_TITLE = "extra_notification_title"
    const val EXTRA_NOTIFICATION_MESSAGE = "extra_notification_message"
    const val EXTRA_TARGET_SCREEN = "extra_target_screen"
    const val EXTRA_TARGET_CHAPTER_ID = "extra_target_chapter_id"
    const val EXTRA_NOTIFICATION_ID = "extra_notification_id"

    const val TEST_NOTIFICATION_ID = 4001
    const val SCHEDULED_NOTIFICATION_ID = 4002
    const val REVISION_NOTIFICATION_ID = 4003
    const val FOCUS_COMPLETE_NOTIFICATION_ID = 4004

    private const val REMINDER_ALARM_REQUEST_CODE = 5001
    private const val REVISION_ALARM_REQUEST_CODE = 5002

    private const val PREFS_NAME = "jee_notification_prefs"
    private const val KEY_REMINDER_ENABLED = "reminder_enabled"
    private const val KEY_REMINDER_HOUR = "reminder_hour"
    private const val KEY_REMINDER_MINUTE = "reminder_minute"
    private const val KEY_REVISION_ALARM_ENABLED = "revision_alarm_enabled"
    private const val KEY_REVISION_SUMMARY_TEXT = "revision_summary_text"

    /**
     * Creates required Android 8.0+ Notification Channels.
     */
    fun ensureChannelsCreated(context: Context): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                    ?: return false

                val reminderChannel = NotificationChannel(
                    CHANNEL_STUDY_REMINDERS,
                    "Study & Sleep Reminders",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Daily study block reminders, sleep window alerts, and Sunday test notifications"
                }

                val revisionChannel = NotificationChannel(
                    CHANNEL_REVISION_REMINDERS,
                    "Spaced Revision Reminders",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Scheduled 1/3/7/21-day spaced revision due reminders"
                }

                val focusChannel = NotificationChannel(
                    CHANNEL_FOCUS_SESSIONS,
                    "Focus Session Alerts",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Alerts when a Focus Lock session or break completes"
                }

                nm.createNotificationChannel(reminderChannel)
                nm.createNotificationChannel(revisionChannel)
                nm.createNotificationChannel(focusChannel)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Checks whether POST_NOTIFICATIONS runtime permission is granted on Android 13+ (API 33+).
     */
    fun hasPostNotificationsPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun getStatusInfo(context: Context): NotificationStatusInfo {
        val channelOk = ensureChannelsCreated(context)
        val runtimeGranted = hasPostNotificationsPermission(context)
        val systemEnabled = try {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        } catch (_: Exception) {
            false
        }

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isScheduled = prefs.getBoolean(KEY_REMINDER_ENABLED, false)
        val isRevisionScheduled = prefs.getBoolean(KEY_REVISION_ALARM_ENABLED, false)
        val hour = prefs.getInt(KEY_REMINDER_HOUR, 21)
        val minute = prefs.getInt(KEY_REMINDER_MINUTE, 0)
        val timeFormatted = if (isScheduled) {
            String.format(Locale.US, "%02d:%02d", hour, minute)
        } else {
            null
        }

        val diagnostic = when {
            !runtimeGranted -> "POST_NOTIFICATIONS runtime permission is not granted (Android 13+). Tap 'Grant Notification Permission' to allow study and revision alerts."
            !systemEnabled -> "Notifications are disabled for this app in Android System Settings."
            isScheduled || isRevisionScheduled -> "Alarms scheduled via AlarmManager (${if (isScheduled) "Daily Study @ $timeFormatted" else ""}${if (isScheduled && isRevisionScheduled) " • " else ""}${if (isRevisionScheduled) "Spaced Revision @ 09:00" else ""}). Delivery depends on OS battery optimization rules."
            else -> "Notifications permitted and channels ready. No recurring alarm currently scheduled."
        }

        return NotificationStatusInfo(
            isPermissionGranted = runtimeGranted,
            areSystemNotificationsEnabled = systemEnabled,
            isChannelCreated = channelOk,
            isReminderScheduled = isScheduled,
            scheduledTimeFormatted = timeFormatted,
            isRevisionReminderScheduled = isRevisionScheduled,
            diagnosticMessage = diagnostic
        )
    }

    /**
     * Posts an immediate test notification after verifying permission and channel state.
     */
    @SuppressLint("MissingPermission")
    fun sendTestNotification(context: Context): NotificationActionResult {
        ensureChannelsCreated(context)
        if (!hasPostNotificationsPermission(context)) {
            return NotificationActionResult(
                success = false,
                diagnosticMessage = "Cannot post test notification: POST_NOTIFICATIONS permission is denied."
            )
        }
        val nmCompat = NotificationManagerCompat.from(context)
        if (!nmCompat.areNotificationsEnabled()) {
            return NotificationActionResult(
                success = false,
                diagnosticMessage = "Cannot post test notification: Notifications are turned off in Android system settings."
            )
        }

        return postNotificationNow(
            context = context,
            notificationId = TEST_NOTIFICATION_ID,
            title = "JEE Study OS • Diagnostic Test Alert",
            message = "Notification channel verified! Tapping this notification opens the Rest & Reminders center.",
            targetScreen = "REST_SLEEP"
        )
    }

    /**
     * Posts a notification when a Focus Session completes.
     */
    fun notifyFocusSessionCompleted(
        context: Context,
        taskName: String,
        durationMinutes: Int
    ): NotificationActionResult {
        return postNotificationNow(
            context = context,
            notificationId = FOCUS_COMPLETE_NOTIFICATION_ID,
            title = "Focus Session Complete (${durationMinutes}m)!",
            message = "Great discipline on '$taskName'. Take a restorative break or review your progress.",
            channelId = CHANNEL_FOCUS_SESSIONS,
            targetScreen = "FOCUS_MODE"
        )
    }

    @SuppressLint("MissingPermission")
    fun postNotificationNow(
        context: Context,
        notificationId: Int,
        title: String,
        message: String,
        channelId: String = CHANNEL_STUDY_REMINDERS,
        targetScreen: String = "DASHBOARD",
        targetChapterId: Long = -1L
    ): NotificationActionResult {
        ensureChannelsCreated(context)
        if (!hasPostNotificationsPermission(context)) {
            return NotificationActionResult(
                success = false,
                diagnosticMessage = "Notification blocked: POST_NOTIFICATIONS permission denied."
            )
        }

        return try {
            val tapIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_TARGET_SCREEN, targetScreen)
                if (targetChapterId > 0L) {
                    putExtra(EXTRA_TARGET_CHAPTER_ID, targetChapterId)
                }
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                notificationId,
                tapIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(
                    if (channelId == CHANNEL_FOCUS_SESSIONS) NotificationCompat.PRIORITY_HIGH
                    else NotificationCompat.PRIORITY_DEFAULT
                )
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()

            NotificationManagerCompat.from(context).notify(notificationId, notification)
            NotificationActionResult(
                success = true,
                diagnosticMessage = "Notification posted to the Android status bar tray."
            )
        } catch (e: Exception) {
            NotificationActionResult(
                success = false,
                diagnosticMessage = "Failed to post notification: ${e.localizedMessage ?: "System error"}"
            )
        }
    }

    /**
     * Schedules a real daily study reminder via AlarmManager using PendingIntent.FLAG_UPDATE_CURRENT
     * to prevent duplicate alarms.
     */
    fun scheduleDailyStudyReminder(
        context: Context,
        hourOfDay: Int,
        minute: Int,
        title: String = "JEE Study OS • Study & Sleep Check-In",
        message: String = "Review today's completed lectures, log mistakes, and protect your 7.5h sleep window."
    ): NotificationActionResult {
        ensureChannelsCreated(context)
        if (!hasPostNotificationsPermission(context)) {
            return NotificationActionResult(
                success = false,
                diagnosticMessage = "Scheduling aborted: Grant POST_NOTIFICATIONS permission first so scheduled reminders can be displayed."
            )
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            ?: return NotificationActionResult(
                success = false,
                diagnosticMessage = "Scheduling failed: AlarmManager service unavailable on this device."
            )

        return try {
            val intent = Intent(context, StudyReminderReceiver::class.java).apply {
                putExtra(EXTRA_NOTIFICATION_TITLE, title)
                putExtra(EXTRA_NOTIFICATION_MESSAGE, message)
                putExtra(EXTRA_TARGET_SCREEN, "SCHEDULER")
                putExtra(EXTRA_NOTIFICATION_ID, SCHEDULED_NOTIFICATION_ID)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                REMINDER_ALARM_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val now = Calendar.getInstance()
            val target = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hourOfDay.coerceIn(0, 23))
                set(Calendar.MINUTE, minute.coerceIn(0, 59))
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (!after(now)) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    target.timeInMillis,
                    pendingIntent
                )
            } else {
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    target.timeInMillis,
                    pendingIntent
                )
            }

            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putBoolean(KEY_REMINDER_ENABLED, true)
                .putInt(KEY_REMINDER_HOUR, hourOfDay.coerceIn(0, 23))
                .putInt(KEY_REMINDER_MINUTE, minute.coerceIn(0, 59))
                .apply()

            val formatted = String.format(Locale.US, "%02d:%02d", hourOfDay, minute)
            NotificationActionResult(
                success = true,
                diagnosticMessage = "Daily study alarm scheduled for $formatted via AlarmManager (replaces any prior duplicate)."
            )
        } catch (e: Exception) {
            NotificationActionResult(
                success = false,
                diagnosticMessage = "Alarm scheduling error: ${e.localizedMessage ?: "Unknown error"}"
            )
        }
    }

    /**
     * Schedules or updates the single Spaced Revision reminder alarm (09:00 AM) when due/upcoming items exist.
     * Uses FLAG_UPDATE_CURRENT to prevent duplicate revision notifications.
     */
    fun scheduleRevisionReminder(
        context: Context,
        actionableCount: Int,
        nextChapterName: String
    ): NotificationActionResult {
        ensureChannelsCreated(context)
        if (!hasPostNotificationsPermission(context)) {
            return NotificationActionResult(
                success = false,
                diagnosticMessage = "Revision reminder not scheduled: POST_NOTIFICATIONS permission not granted."
            )
        }
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            ?: return NotificationActionResult(false, "AlarmManager unavailable.")

        val summary = if (actionableCount > 1) {
            "$actionableCount chapters due for spaced revision, starting with $nextChapterName."
        } else {
            "Spaced revision due for $nextChapterName. Tap to review and log completion."
        }

        return try {
            val intent = Intent(context, StudyReminderReceiver::class.java).apply {
                putExtra(EXTRA_NOTIFICATION_TITLE, "JEE Study OS • Spaced Revision Due")
                putExtra(EXTRA_NOTIFICATION_MESSAGE, summary)
                putExtra(EXTRA_TARGET_SCREEN, "REVISION")
                putExtra(EXTRA_NOTIFICATION_ID, REVISION_NOTIFICATION_ID)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                REVISION_ALARM_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val now = Calendar.getInstance()
            val target = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 9)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (!after(now)) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, target.timeInMillis, pendingIntent)
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, target.timeInMillis, pendingIntent)
            }

            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putBoolean(KEY_REVISION_ALARM_ENABLED, true)
                .putString(KEY_REVISION_SUMMARY_TEXT, summary)
                .apply()

            NotificationActionResult(
                success = true,
                diagnosticMessage = "Spaced revision reminder scheduled for 09:00 (no auto-completion on delivery)."
            )
        } catch (e: Exception) {
            NotificationActionResult(false, "Failed to schedule revision alarm: ${e.localizedMessage}")
        }
    }

    fun cancelRevisionReminder(context: Context): NotificationActionResult {
        return try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            val intent = Intent(context, StudyReminderReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                REVISION_ALARM_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager?.cancel(pendingIntent)
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putBoolean(KEY_REVISION_ALARM_ENABLED, false)
                .apply()
            NotificationActionResult(true, "Spaced revision reminder alarm cancelled.")
        } catch (e: Exception) {
            NotificationActionResult(false, "Failed to cancel revision alarm: ${e.localizedMessage}")
        }
    }

    fun cancelDailyStudyReminder(context: Context): NotificationActionResult {
        return try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            val intent = Intent(context, StudyReminderReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                REMINDER_ALARM_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager?.cancel(pendingIntent)

            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putBoolean(KEY_REMINDER_ENABLED, false).apply()

            NotificationActionResult(
                success = true,
                diagnosticMessage = "Scheduled study reminder cancelled."
            )
        } catch (e: Exception) {
            NotificationActionResult(
                success = false,
                diagnosticMessage = "Failed to cancel reminder: ${e.localizedMessage ?: "Error"}"
            )
        }
    }

    /**
     * Called on device reboot, app update, or timezone change to reschedule persisted reminders.
     */
    fun reschedulePersistedAlarms(context: Context) {
        if (!hasPostNotificationsPermission(context)) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_REMINDER_ENABLED, false)) {
            val h = prefs.getInt(KEY_REMINDER_HOUR, 21)
            val m = prefs.getInt(KEY_REMINDER_MINUTE, 0)
            scheduleDailyStudyReminder(context, h, m)
        }
        if (prefs.getBoolean(KEY_REVISION_ALARM_ENABLED, false)) {
            val summary = prefs.getString(KEY_REVISION_SUMMARY_TEXT, "Spaced revision due today.") ?: "Spaced revision due today."
            scheduleRevisionReminder(context, 1, summary)
        }
    }
}
