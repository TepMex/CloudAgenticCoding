package com.tepmex.idealtiming

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.tepmex.idealtiming.ui.IdealTimingAppShell
import com.tepmex.idealtiming.ui.IdealTimingViewModelFactory
import com.tepmex.idealtiming.ui.theme.IdealTimingTheme

class MainActivity : ComponentActivity() {
    private val requestNotifications = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* scheduled alarms still fire; posts no-op if denied */ }

    private val requestLocation = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { /* ViewModel refreshes on resume / sync; markers omit until coords exist */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        maybeRequestNotificationPermission()
        maybeRequestLocationPermission()
        val app = application as IdealTimingApp
        val factory = IdealTimingViewModelFactory(
            repository = app.repository,
            sectionNotifications = app.sectionNotifications,
            locationSource = app.locationSource,
            nfcCheckInStore = app.nfcCheckInStore,
        )
        setContent {
            IdealTimingTheme {
                IdealTimingAppShell(
                    factory = factory,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    private fun maybeRequestNotificationPermission() {
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun maybeRequestLocationPermission() {
        val fine = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) {
            requestLocation.launch(
                arrayOf(
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                ),
            )
        }
    }
}
