package com.tepmex.idealtiming.notification

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.tepmex.idealtiming.MainActivity
import com.tepmex.idealtiming.R
import com.tepmex.idealtiming.domain.DailyCues
import com.tepmex.idealtiming.domain.SectionNotification
import com.tepmex.idealtiming.domain.SectionNotificationPlanner
import java.time.LocalDate
import java.time.ZoneId

/**
 * Schedules exact alarms for remaining same-day cues (sectors, meals, dog walk).
 */
class SectionNotificationScheduler(
    private val context: Context,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun ensureChannel() {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Ideal day cues",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Sector changes, meals, and dog walk"
        }
        manager.createNotificationChannel(channel)
    }

    /**
     * Cancel prior alarms and schedule [plan] for remaining same-day boundaries.
     * Records [localDateKey] as the day notifications were scheduled for.
     */
    fun scheduleForDay(
        wakeEpochSec: Long,
        nowEpochSec: Long = System.currentTimeMillis() / 1000L,
        localDate: LocalDate = LocalDate.now(zoneId),
    ): List<SectionNotification> {
        ensureChannel()
        cancelAll()
        val dayEnd = localDate.plusDays(1).atStartOfDay(zoneId).toEpochSecond()
        val plan = SectionNotificationPlanner.plan(wakeEpochSec, nowEpochSec, dayEnd, zoneId)
        for (item in plan) {
            scheduleOne(item)
        }
        prefs.edit()
            .putString(KEY_SCHEDULED_DATE, localDate.toString())
            .putLong(KEY_WAKE, wakeEpochSec)
            .apply()
        return plan
    }

    fun scheduledForDate(): LocalDate? =
        prefs.getString(KEY_SCHEDULED_DATE, null)?.let {
            runCatching { LocalDate.parse(it) }.getOrNull()
        }

    fun needsDailySchedule(today: LocalDate = LocalDate.now(zoneId)): Boolean =
        scheduledForDate() != today

    fun cancelAll() {
        for (id in 1..DailyCues.ALARM_ID_MAX) {
            val pi = pendingIntent(id, message = "", create = false) ?: continue
            alarmManager?.cancel(pi)
            pi.cancel()
        }
        prefs.edit()
            .remove(KEY_SCHEDULED_DATE)
            .remove(KEY_WAKE)
            .apply()
    }

    private fun scheduleOne(item: SectionNotification) {
        val am = alarmManager ?: return
        val triggerAt = item.fireEpochSec * 1000L
        if (triggerAt <= System.currentTimeMillis()) return
        val pi = pendingIntent(item.alarmId, item.message, create = true) ?: return
        try {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        } catch (_: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    private fun pendingIntent(alarmId: Int, message: String, create: Boolean): PendingIntent? {
        val intent = Intent(context, SectionChangeReceiver::class.java).apply {
            action = SectionChangeReceiver.ACTION_SECTION_CHANGE
            putExtra(SectionChangeReceiver.EXTRA_MESSAGE, message)
            putExtra(SectionChangeReceiver.EXTRA_ALARM_ID, alarmId)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return if (create) {
            PendingIntent.getBroadcast(context, alarmId, intent, flags)
        } else {
            PendingIntent.getBroadcast(
                context,
                alarmId,
                intent,
                flags or PendingIntent.FLAG_NO_CREATE,
            )
        }
    }

    companion object {
        const val CHANNEL_ID = "section_changes"
        private const val PREFS = "ideal_timing_section_alarms"
        private const val KEY_SCHEDULED_DATE = "scheduled_local_date"
        private const val KEY_WAKE = "scheduled_wake_epoch"

        fun canPostNotifications(context: Context): Boolean =
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED

        fun showNotification(context: Context, alarmId: Int, message: String) {
            if (!canPostNotifications(context)) return
            val open = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(context.getString(R.string.app_name))
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(open)
                .build()
            NotificationManagerCompat.from(context).notify(NOTIFICATION_BASE + alarmId, notification)
        }

        private const val NOTIFICATION_BASE = 4100
    }
}
