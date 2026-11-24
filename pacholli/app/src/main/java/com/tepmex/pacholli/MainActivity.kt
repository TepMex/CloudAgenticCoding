package com.tepmex.pacholli

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.tepmex.pacholli.ui.PacholliShell
import com.tepmex.pacholli.ui.PacholliViewModel
import com.tepmex.pacholli.ui.theme.PacholliTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as PacholliApp
        setContent {
            PacholliTheme {
                PacholliShell(
                    factory = PacholliViewModel.factory(app.repository),
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
