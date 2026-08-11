package com.tepmex.idealtiming.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class SectionChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_SECTION_CHANGE) return
        val message = intent.getStringExtra(EXTRA_MESSAGE)?.takeIf { it.isNotBlank() } ?: return
        val alarmId = intent.getIntExtra(EXTRA_ALARM_ID, 0)
        SectionNotificationScheduler(context.applicationContext).ensureChannel()
        SectionNotificationScheduler.showNotification(context.applicationContext, alarmId, message)
    }

    companion object {
        const val ACTION_SECTION_CHANGE = "com.tepmex.idealtiming.action.SECTION_CHANGE"
        const val EXTRA_MESSAGE = "message"
        const val EXTRA_ALARM_ID = "alarm_id"
    }
}
