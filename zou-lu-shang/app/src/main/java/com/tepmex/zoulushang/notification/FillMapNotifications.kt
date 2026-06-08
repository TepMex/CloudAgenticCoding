package com.tepmex.zoulushang.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.tepmex.zoulushang.R

object FillMapNotifications {
    const val CHANNEL_ID = "fill_map_sampling"
    const val FOREGROUND_NOTIFICATION_ID = 8001

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_fill_map),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.notification_text_fill_map)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    fun buildForegroundNotification(context: Context, samplesTaken: Int, minutesRemaining: Int): Notification =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle(context.getString(R.string.notification_title_fill_map))
            .setContentText(
                context.getString(
                    R.string.notification_text_fill_map_progress,
                    samplesTaken,
                    minutesRemaining,
                ),
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
}
