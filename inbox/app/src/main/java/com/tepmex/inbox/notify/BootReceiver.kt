package com.tepmex.inbox.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.tepmex.inbox.InboxApp
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }
        val pending = goAsync()
        val app = context.applicationContext as InboxApp
        app.scope.launch {
            try {
                app.alarms.reschedule(app.repository.pendingReminders())
            } catch (error: Exception) {
                Log.e(TAG, "reschedule failed", error)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val TAG = "Inbox"
    }
}
