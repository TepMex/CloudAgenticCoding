package com.tepmex.heilauncher

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import com.tepmex.heilauncher.domain.AppIntentSpec
import com.tepmex.heilauncher.domain.DefaultApps
import com.tepmex.heilauncher.domain.isSystemChooser

/** Starts the default calendar or clock, trying each spec until one resolves. */
internal class DefaultAppLauncher(private val context: Context) {
    fun openCalendar(nowMillis: Long = System.currentTimeMillis()): Boolean {
        return open(DefaultApps.calendar(nowMillis))
    }

    fun openClock(): Boolean = open(DefaultApps.clock())

    private fun open(specs: List<AppIntentSpec>): Boolean {
        val pm = context.packageManager
        for (spec in specs) {
            val implicit = spec.toIntent()
            val resolved = pm.resolveActivity(implicit, PackageManager.MATCH_DEFAULT_ONLY) ?: continue
            val info = resolved.activityInfo
            val appPackage = info?.packageName
            if (spec.openLauncher && !isSystemChooser(appPackage, info?.name) && appPackage != null) {
                val launch = pm.getLaunchIntentForPackage(appPackage)?.apply {
                    addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                }
                if (launch != null && start(launch)) return true
            }
            if (start(implicit)) return true
        }
        return false
    }

    private fun start(intent: Intent): Boolean {
        return try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }
}

private fun AppIntentSpec.toIntent(): Intent {
    val spec = this
    return Intent(spec.action).apply {
        spec.categories.forEach(::addCategory)
        addCategory(Intent.CATEGORY_DEFAULT)
        spec.data?.let { setData(Uri.parse(it)) }
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
    }
}
