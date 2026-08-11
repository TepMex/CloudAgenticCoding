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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        maybeRequestNotificationPermission()
        val app = application as IdealTimingApp
        val factory = IdealTimingViewModelFactory(app.repository, app.sectionNotifications)
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
}
