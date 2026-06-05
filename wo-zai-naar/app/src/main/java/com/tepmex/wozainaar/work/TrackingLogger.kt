package com.tepmex.wozainaar.work

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object TrackingLogger {
    private const val TAG = "WoZaiNaarTracking"
    private const val MAX_LINES = 40

    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = _lines.asStateFlow()

    fun log(message: String) {
        Log.i(TAG, message)
        val timestamp = Instant.now()
            .atZone(ZoneId.systemDefault())
            .format(timeFormatter)
        val line = "$timestamp  $message"
        _lines.update { current -> (listOf(line) + current).take(MAX_LINES) }
    }

    fun clear() {
        _lines.value = emptyList()
    }
}
