package com.tepmex.inbox

import android.app.Application
import com.tepmex.inbox.data.CaptureRepository
import com.tepmex.inbox.data.InboxDatabase
import com.tepmex.inbox.notify.InboxSettings
import com.tepmex.inbox.notify.ReminderAlarms
import com.tepmex.inbox.notify.ReminderNotifier
import com.tepmex.inbox.notify.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class InboxApp : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    lateinit var repository: CaptureRepository
        private set

    lateinit var alarms: ReminderAlarms
        private set

    lateinit var settings: InboxSettings
        private set

    override fun onCreate() {
        super.onCreate()
        settings = InboxSettings(this)
        repository = CaptureRepository(InboxDatabase.create(this).captureDao())
        alarms = ReminderScheduler(this)
        ReminderNotifier.ensureChannel(this)
        scope.launch {
            alarms.reschedule(repository.pendingReminders())
        }
    }
}
