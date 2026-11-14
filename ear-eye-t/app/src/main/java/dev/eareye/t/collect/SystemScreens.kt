package dev.eareye.t.collect

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

fun openUsageAccessSettings(context: Context): Boolean {
    val packageUri = Uri.fromParts("package", context.packageName, null)
    return startFirst(
        context,
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).setData(packageUri),
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS),
        applicationDetailsIntent(context),
    )
}

fun openNotificationListenerSettings(context: Context): Boolean {
    return startFirst(
        context,
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS),
        applicationDetailsIntent(context),
    )
}

fun openAppDetailsSettings(context: Context): Boolean {
    return startFirst(context, applicationDetailsIntent(context))
}

fun openBatterySettings(context: Context): Boolean {
    return startFirst(
        context,
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
        Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS),
        applicationDetailsIntent(context),
    )
}

private fun applicationDetailsIntent(context: Context): Intent {
    return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        .setData(Uri.fromParts("package", context.packageName, null))
}

private fun startFirst(context: Context, vararg intents: Intent): Boolean {
    for (intent in intents) {
        val launch = Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (context is Activity) {
            launch.flags = launch.flags and Intent.FLAG_ACTIVITY_NEW_TASK.inv()
        }
        try {
            context.startActivity(launch)
            return true
        } catch (_: ActivityNotFoundException) {
        } catch (_: SecurityException) {
        } catch (_: IllegalArgumentException) {
        }
    }
    return false
}
