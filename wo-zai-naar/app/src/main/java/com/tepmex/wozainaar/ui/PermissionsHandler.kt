package com.tepmex.wozainaar.ui

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
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import androidx.lifecycle.compose.LifecycleResumeEffect

data class LocationPermissionsState(
    val allGranted: Boolean,
    val needsBackgroundStep: Boolean,
    val request: () -> Unit,
)

@Composable
fun rememberLocationPermissionsState(): LocationPermissionsState {
    val context = LocalContext.current
    var allGranted by remember { mutableStateOf(false) }
    var needsBackgroundStep by remember { mutableStateOf(false) }

    fun isGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PermissionChecker.PERMISSION_GRANTED

    fun hasForegroundLocation(): Boolean =
        isGranted(Manifest.permission.ACCESS_FINE_LOCATION) ||
            isGranted(Manifest.permission.ACCESS_COARSE_LOCATION)

    fun hasNotifications(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            isGranted(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            true
        }

    fun hasBackgroundLocation(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            isGranted(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        } else {
            true
        }

    fun refreshState() {
        val foreground = hasForegroundLocation()
        val notifications = hasNotifications()
        val background = hasBackgroundLocation()
        needsBackgroundStep = foreground && notifications && !background
        allGranted = foreground && notifications && background
    }

    val backgroundLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        refreshState()
    }

    val foregroundLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        if (hasForegroundLocation() && hasNotifications() && !hasBackgroundLocation()) {
            backgroundLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        } else {
            refreshState()
        }
    }

    LifecycleResumeEffect(Unit) {
        refreshState()
        onPauseOrDispose { }
    }

    return LocationPermissionsState(
        allGranted = allGranted,
        needsBackgroundStep = needsBackgroundStep,
        request = {
            when {
                !hasForegroundLocation() || !hasNotifications() -> {
                    val permissions = buildList {
                        add(Manifest.permission.ACCESS_FINE_LOCATION)
                        add(Manifest.permission.ACCESS_COARSE_LOCATION)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            add(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                    foregroundLauncher.launch(permissions.toTypedArray())
                }
                !hasBackgroundLocation() -> {
                    backgroundLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                }
                else -> refreshState()
            }
        },
    )
}
