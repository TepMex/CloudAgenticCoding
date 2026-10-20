package com.tepmex.inbox.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.tepmex.inbox.InboxApp
import kotlinx.coroutines.launch

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val id = intent.getLongExtra(EXTRA_ID, -1L)
        if (id < 0L) return
        val pending = goAsync()
        val app = context.applicationContext as InboxApp
        app.scope.launch {
            try {
                val fired = app.repository.fire(id, System.currentTimeMillis()) ?: return@launch
                ReminderNotifier.show(app, app.settings, fired)
            } catch (error: Exception) {
                Log.e(TAG, "reminder $id failed", error)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION = "com.tepmex.inbox.REMIND"
        const val EXTRA_ID = "capture_id"
        private const val TAG = "Inbox"
    }
}
