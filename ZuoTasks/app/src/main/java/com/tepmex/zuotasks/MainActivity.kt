package com.tepmex.zuotasks

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.tepmex.zuotasks.ui.ZuoTasksAppShell
import com.tepmex.zuotasks.ui.ZuoTasksViewModelFactory
import com.tepmex.zuotasks.ui.theme.ZuoTasksTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as ZuoTasksApp
        val factory = ZuoTasksViewModelFactory(app.repository)
        setContent {
            ZuoTasksTheme {
                ZuoTasksAppShell(
                    factory = factory,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
