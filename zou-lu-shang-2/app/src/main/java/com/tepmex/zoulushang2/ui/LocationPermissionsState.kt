package com.tepmex.zoulushang2.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.tepmex.zoulushang2.location.LocationPermissions

data class LocationPermissionsState(
    val allGranted: Boolean,
    val request: () -> Unit,
)

@Composable
fun rememberLocationPermissionsState(): LocationPermissionsState {
    val context = LocalContext.current
    var allGranted by remember { mutableStateOf(false) }

    fun refreshState() {
        allGranted = LocationPermissions.hasAll(context)
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        refreshState()
    }

    LifecycleResumeEffect(Unit) {
        refreshState()
        onPauseOrDispose { }
    }

    return LocationPermissionsState(
        allGranted = allGranted,
        request = {
            val permissions = buildList {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
                add(Manifest.permission.ACCESS_COARSE_LOCATION)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    add(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            launcher.launch(permissions.toTypedArray())
        },
    )
}
