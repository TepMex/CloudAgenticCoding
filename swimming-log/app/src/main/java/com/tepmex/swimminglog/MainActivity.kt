package com.tepmex.swimminglog

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.tepmex.swimminglog.ui.SwimmingLogAppShell
import com.tepmex.swimminglog.ui.SwimmingLogViewModelFactory
import com.tepmex.swimminglog.ui.theme.SwimmingLogTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as SwimmingLogApp
        val factory = SwimmingLogViewModelFactory(app.repository)
        setContent {
            SwimmingLogTheme {
                SwimmingLogAppShell(
                    factory = factory,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
