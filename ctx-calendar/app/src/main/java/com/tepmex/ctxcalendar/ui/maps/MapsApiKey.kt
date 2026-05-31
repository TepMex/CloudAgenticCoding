package com.tepmex.ctxcalendar.ui.maps

import android.content.Context
import android.content.pm.PackageManager
import com.tepmex.ctxcalendar.R

object MapsApiKey {
    fun isConfigured(context: Context): Boolean {
        val fromManifest = readManifestKey(context)
        if (!fromManifest.isNullOrBlank() && fromManifest != "YOUR_MAPS_API_KEY") {
            return true
        }
        val fromRes = context.getString(R.string.google_maps_api_key)
        return fromRes.isNotBlank() && fromRes != "YOUR_MAPS_API_KEY"
    }

    fun resolve(context: Context): String? {
        val fromManifest = readManifestKey(context)
        if (!fromManifest.isNullOrBlank() && fromManifest != "YOUR_MAPS_API_KEY") {
            return fromManifest
        }
        val fromRes = context.getString(R.string.google_maps_api_key)
        return fromRes.takeIf { it.isNotBlank() && it != "YOUR_MAPS_API_KEY" }
    }

    private fun readManifestKey(context: Context): String? = try {
        val appInfo = context.packageManager.getApplicationInfo(
            context.packageName,
            PackageManager.GET_META_DATA,
        )
        appInfo.metaData?.getString("com.google.android.geo.API_KEY")
    } catch (_: Exception) {
        null
    }
}
