package com.tepmex.inbox.ui

import android.content.Context
import android.text.format.DateFormat
import java.util.Date

fun formatStamp(context: Context, millis: Long): String {
    val whenDate = Date(millis)
    val date = DateFormat.getMediumDateFormat(context).format(whenDate)
    val time = DateFormat.getTimeFormat(context).format(whenDate)
    return "$date, $time"
}
