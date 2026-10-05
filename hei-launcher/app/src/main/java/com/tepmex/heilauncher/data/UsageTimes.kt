package com.tepmex.heilauncher.data

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process

class UsageTimes(private val context: Context) {
    fun canRead(): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun read(): Map<String, Long> {
        if (!canRead()) return emptyMap()
        val usm = context.getSystemService(UsageStatsManager::class.java) ?: return emptyMap()
        val end = System.currentTimeMillis()
        val start = end - LOOKBACK_MS
        return try {
            usm.queryAndAggregateUsageStats(start, end)
                .mapNotNull { (packageName, stats) ->
                    val at = stats.lastTimeUsed
                    if (at <= 0L) null else packageName to at
                }
                .toMap()
        } catch (_: SecurityException) {
            emptyMap()
        }
    }

    companion object {
        private const val LOOKBACK_MS = 400L * 24 * 60 * 60 * 1000
    }
}
