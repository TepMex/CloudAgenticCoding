package dev.eareye.t.collect

import android.os.SystemClock

interface TimeSource {
    fun wallTimeMillis(): Long
    fun elapsedRealtimeMillis(): Long
}

object AndroidTimeSource : TimeSource {
    override fun wallTimeMillis(): Long = System.currentTimeMillis()
    override fun elapsedRealtimeMillis(): Long = SystemClock.elapsedRealtime()
}
