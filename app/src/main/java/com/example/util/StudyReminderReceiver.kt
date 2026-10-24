package com.example.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * BroadcastReceiver triggered by AlarmManager for scheduled study & revision reminders.
 * Never automatically marks study or revision tasks completed upon delivery.
 */
class StudyReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val title = intent?.getStringExtra(StudyNotificationManager.EXTRA_NOTIFICATION_TITLE)
            ?: "JEE Study OS • Daily Study Target"
        val message = intent?.getStringExtra(StudyNotificationManager.EXTRA_NOTIFICATION_MESSAGE)
            ?: "Time to begin your scheduled lectures and DPP problem-solving block."
        val targetScreen = intent?.getStringExtra(StudyNotificationManager.EXTRA_TARGET_SCREEN)
            ?: "SCHEDULER"
        val notificationId = intent?.getIntExtra(
            StudyNotificationManager.EXTRA_NOTIFICATION_ID,
            StudyNotificationManager.SCHEDULED_NOTIFICATION_ID
        ) ?: StudyNotificationManager.SCHEDULED_NOTIFICATION_ID

        val channelId = if (targetScreen == "REVISION") {
            StudyNotificationManager.CHANNEL_REVISION_REMINDERS
        } else {
            StudyNotificationManager.CHANNEL_STUDY_REMINDERS
        }

        StudyNotificationManager.postNotificationNow(
            context = context,
            notificationId = notificationId,
            title = title,
            message = message,
            channelId = channelId,
            targetScreen = targetScreen
        )
    }
}
