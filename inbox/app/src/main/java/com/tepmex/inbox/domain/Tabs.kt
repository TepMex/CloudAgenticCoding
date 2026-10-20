package com.tepmex.inbox.domain

enum class Tab {
    Reminders,
    Inbox,
    Capture,
    Archive,
    Settings,
}

enum class CenterAction {
    Send,
    SendDisabled,
    OpenCapture,
}

object Routes {
    const val CAPTURE = "capture"
    const val INBOX = "inbox"
    const val PROCESS = "process/{id}"
    const val REMINDERS = "reminders"
    const val ARCHIVE = "archive"
    const val SETTINGS = "settings"

    fun process(id: Long): String = "process/$id"
}

fun centerAction(tab: Tab, draft: String): CenterAction =
    if (tab == Tab.Capture) {
        if (draft.isNotBlank()) CenterAction.Send else CenterAction.SendDisabled
    } else {
        CenterAction.OpenCapture
    }

fun tabForRoute(route: String?): Tab = when {
    route == null || route == Routes.CAPTURE -> Tab.Capture
    route == Routes.INBOX || route.startsWith("process") -> Tab.Inbox
    route == Routes.REMINDERS -> Tab.Reminders
    route == Routes.ARCHIVE -> Tab.Archive
    route == Routes.SETTINGS -> Tab.Settings
    else -> Tab.Capture
}
