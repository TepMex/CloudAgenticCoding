package com.tepmex.inbox.notify

import com.tepmex.inbox.domain.Capture

interface ReminderAlarms {
    fun schedule(id: Long, atMillis: Long)
    fun cancel(id: Long)
    fun reschedule(items: List<Capture>)
}
