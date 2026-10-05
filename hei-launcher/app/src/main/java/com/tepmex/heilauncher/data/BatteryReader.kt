package com.tepmex.heilauncher.data

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.tepmex.heilauncher.domain.BatteryStatus
import com.tepmex.heilauncher.domain.estimateRemainingMillis

class BatteryReader(private val context: Context) {
    fun read(): BatteryStatus {
        val sticky = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = sticky?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = sticky?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val manager = context.getSystemService(BatteryManager::class.java)
        val percent = when {
            level >= 0 && scale > 0 -> (level * 100) / scale
            manager != null -> manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            else -> 0
        }.coerceIn(0, 100)
        val status = sticky?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        val remaining = if (charging || manager == null) {
            null
        } else {
            val charge = manager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
            val current = manager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            if (charge == Long.MIN_VALUE || current == Long.MIN_VALUE) {
                null
            } else {
                estimateRemainingMillis(charge, current)
            }
        }
        return BatteryStatus(percent, charging, remaining)
    }
}
