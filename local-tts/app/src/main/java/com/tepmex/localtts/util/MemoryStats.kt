package com.tepmex.localtts.util

import android.app.ActivityManager
import android.content.Context
import java.util.Locale

object MemoryStats {

    fun format(context: Context? = null, label: String = "heap"): String {
        val rt = Runtime.getRuntime()
        val usedBytes = rt.totalMemory() - rt.freeMemory()
        val maxBytes = rt.maxMemory()
        val availBytes = maxBytes - usedBytes
        val sb = StringBuilder()
        sb.append(label)
        sb.append(": used=")
        sb.append(mb(usedBytes))
        sb.append(" MB, avail=")
        sb.append(mb(availBytes))
        sb.append(" MB, max=")
        sb.append(mb(maxBytes))
        sb.append(" MB")
        if (context != null) {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val info = ActivityManager.MemoryInfo()
            am.getMemoryInfo(info)
            sb.append("; device avail=")
            sb.append(mb(info.availMem))
            sb.append(" MB, lowMemory=")
            sb.append(info.lowMemory)
        }
        return sb.toString()
    }

    fun estimateTensorMb(elements: Long, bytesPerElement: Int): String {
        val mb = elements * bytesPerElement / (1024.0 * 1024.0)
        return String.format(Locale.US, "%.2f MB (%d elements)", mb, elements)
    }

    private fun mb(bytes: Long): String =
        String.format(Locale.US, "%.1f", bytes / (1024.0 * 1024.0))
}
