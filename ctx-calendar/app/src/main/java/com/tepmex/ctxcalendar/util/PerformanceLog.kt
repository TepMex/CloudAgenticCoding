package com.tepmex.ctxcalendar.util

import android.os.SystemClock
import android.util.Log
/**
 * Lightweight timing logs for profiling swipe/navigation hot paths.
 * Filter logcat with tag [TAG].
 */
object PerformanceLog {
    const val TAG = "CtxPerf"

    fun log(message: String) {
        Log.d(TAG, message)
    }

    inline fun <T> trace(label: String, block: () -> T): T {
        val start = SystemClock.elapsedRealtime()
        return try {
            block()
        } finally {
            val elapsed = SystemClock.elapsedRealtime() - start
            Log.d(TAG, "$label took ${elapsed}ms")
        }
    }

    suspend inline fun <T> traceSuspend(label: String, crossinline block: suspend () -> T): T {
        val start = SystemClock.elapsedRealtime()
        return try {
            block()
        } finally {
            val elapsed = SystemClock.elapsedRealtime() - start
            Log.d(TAG, "$label took ${elapsed}ms")
        }
    }
}
