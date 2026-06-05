package com.tepmex.wozainaar.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        TrackingLogger.log("Boot completed; re-scheduling periodic location work if permitted")
        LocationWorkScheduler.scheduleIfReady(context.applicationContext)
    }
}
