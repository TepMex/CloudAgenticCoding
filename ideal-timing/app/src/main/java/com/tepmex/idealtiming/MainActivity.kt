package com.tepmex.idealtiming

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.tepmex.idealtiming.ui.IdealTimingAppShell
import com.tepmex.idealtiming.ui.IdealTimingViewModelFactory
import com.tepmex.idealtiming.ui.theme.IdealTimingTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as IdealTimingApp
        val factory = IdealTimingViewModelFactory(app.repository)
        setContent {
            IdealTimingTheme {
                IdealTimingAppShell(
                    factory = factory,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
