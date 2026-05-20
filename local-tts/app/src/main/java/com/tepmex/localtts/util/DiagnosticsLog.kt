package com.tepmex.localtts.util

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Ring buffer of recent diagnostic lines mirrored to logcat and exposed to the UI.
 */
object DiagnosticsLog {
    private const val MAX_LINES = 250
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = _lines.asStateFlow()

    fun clear() {
        _lines.value = emptyList()
    }

    fun text(): String = _lines.value.joinToString("\n")

    fun d(tag: String, message: String) = append("D", tag, message) { Log.d(tag, message) }

    fun i(tag: String, message: String) = append("I", tag, message) { Log.i(tag, message) }

    fun w(tag: String, message: String, throwable: Throwable? = null) =
        append("W", tag, message, throwable) { Log.w(tag, message, throwable) }

    fun e(tag: String, message: String, throwable: Throwable? = null) =
        append("E", tag, message, throwable) { Log.e(tag, message, throwable) }

    private fun append(
        level: String,
        tag: String,
        message: String,
        throwable: Throwable? = null,
        logcat: () -> Unit,
    ) {
        logcat()
        val ts = timeFormat.format(Date())
        val line = buildString {
            append(ts)
            append(' ')
            append(level)
            append('/')
            append(tag)
            append(": ")
            append(message)
            throwable?.let { t ->
                append(" — ")
                append(t.javaClass.simpleName)
                t.message?.let { m ->
                    append(": ")
                    append(m)
                }
            }
        }
        _lines.update { prev ->
            (prev + line).takeLast(MAX_LINES)
        }
        throwable?.let { t ->
            val stack = Log.getStackTraceString(t)
            _lines.update { prev ->
                (prev + stack.lines().take(40)).takeLast(MAX_LINES)
            }
        }
    }
}
