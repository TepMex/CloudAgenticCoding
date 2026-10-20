package com.tepmex.inbox.notify

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.tepmex.inbox.MainActivity
import com.tepmex.inbox.R
import com.tepmex.inbox.domain.Capture

object ReminderNotifier {
    const val CHANNEL_ID = "reminders"

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.channel_reminders),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.channel_reminders_desc)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(channel)
    }

    fun show(context: Context, settings: InboxSettings, capture: Capture) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_OPEN_ARCHIVE_ID, capture.id)
        }
        val content = PendingIntent.getActivity(
            context,
            ReminderScheduler.requestCode(capture.id),
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val visibility = if (settings.showOnLockScreen.value) {
            NotificationCompat.VISIBILITY_PUBLIC
        } else {
            NotificationCompat.VISIBILITY_PRIVATE
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_title))
            .setContentText(capture.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(capture.text))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(visibility)
            .setAutoCancel(true)
            .setContentIntent(content)
            .setWhen(capture.notifiedAtMillis ?: System.currentTimeMillis())
            .setShowWhen(true)
            .build()
        try {
            NotificationManagerCompat.from(context)
                .notify(ReminderScheduler.requestCode(capture.id), notification)
        } catch (_: SecurityException) {
            // Permission was revoked between the check and the post.
        }
    }
}
