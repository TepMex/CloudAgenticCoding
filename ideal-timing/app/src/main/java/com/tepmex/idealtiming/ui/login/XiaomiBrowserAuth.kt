package com.tepmex.idealtiming.ui.login

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri

object XiaomiBrowserAuth {
    fun openCustomTab(context: Context, url: String) {
        val uri: Uri = url.toUri()
        try {
            CustomTabsIntent.Builder()
                .setShowTitle(true)
                .setUrlBarHidingEnabled(true)
                .build()
                .launchUrl(context, uri)
        } catch (_: Exception) {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}
