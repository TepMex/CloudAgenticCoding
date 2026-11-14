package dev.eareye.t.collect

import android.app.AppOpsManager
import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import dev.eareye.t.domain.ClockSample

class DeviceStatus(
    private val context: Context,
    private val time: TimeSource = AndroidTimeSource,
) {
    fun usageAccessGranted(): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = usageMode(appOps)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun notificationListenerEnabled(): Boolean {
        val notifications = context.getSystemService(NotificationManager::class.java) ?: return false
        val component = ComponentName(context, MonitoringNotificationListenerService::class.java)
        return notifications.isNotificationListenerAccessGranted(component)
    }

    fun screenInteractive(): Boolean {
        val power = context.getSystemService(PowerManager::class.java) ?: return false
        return power.isInteractive
    }

    /** True when the lock screen is showing. This is not the same as a PIN being required. */
    fun keyguardLocked(): Boolean {
        val keyguard = context.getSystemService(KeyguardManager::class.java) ?: return false
        return keyguard.isKeyguardLocked
    }

    fun bootCount(): Int {
        return Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
    }

    fun sample(): ClockSample = ClockSample(
        wallMillis = time.wallTimeMillis(),
        elapsedMillis = time.elapsedRealtimeMillis(),
        bootCount = bootCount(),
    )

    fun manufacturer(): String = Build.MANUFACTURER.orEmpty()

    fun model(): String = Build.MODEL.orEmpty()

    fun sdkInt(): Int = Build.VERSION.SDK_INT

    fun release(): String = Build.VERSION.RELEASE.orEmpty()

    private fun usageMode(appOps: AppOpsManager): Int {
        val uid = Process.myUid()
        val packageName = context.packageName
        return appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, uid, packageName)
    }
}
