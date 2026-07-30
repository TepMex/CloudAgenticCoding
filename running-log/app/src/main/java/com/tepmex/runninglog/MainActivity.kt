package com.tepmex.runninglog

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.tepmex.runninglog.ui.RunningLogAppShell
import com.tepmex.runninglog.ui.RunningLogViewModelFactory
import com.tepmex.runninglog.ui.theme.RunningLogTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as RunningLogApp
        val factory = RunningLogViewModelFactory(app.repository)
        setContent {
            RunningLogTheme {
                RunningLogAppShell(
                    factory = factory,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
