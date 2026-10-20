package com.tepmex.inbox.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.tepmex.inbox.domain.Capture

class ReminderScheduler(private val context: Context) : ReminderAlarms {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    override fun schedule(id: Long, atMillis: Long) {
        val am = alarmManager ?: return
        val pending = pendingIntent(id, create = true) ?: return
        try {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pending)
        } catch (_: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pending)
        }
    }

    override fun cancel(id: Long) {
        val am = alarmManager ?: return
        val pending = pendingIntent(id, create = false) ?: return
        am.cancel(pending)
        pending.cancel()
    }

    override fun reschedule(items: List<Capture>) {
        for (item in items) {
            val at = item.reminderAtMillis ?: continue
            schedule(item.id, at)
        }
    }

    private fun pendingIntent(id: Long, create: Boolean): PendingIntent? {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION
            data = Uri.parse("inbox://reminder/$id")
            putExtra(ReminderReceiver.EXTRA_ID, id)
        }
        val request = requestCode(id)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return if (create) {
            PendingIntent.getBroadcast(context, request, intent, flags)
        } else {
            PendingIntent.getBroadcast(context, request, intent, flags or PendingIntent.FLAG_NO_CREATE)
        }
    }

    companion object {
        fun requestCode(id: Long): Int = (id xor (id ushr 32)).toInt()
    }
}
