package com.tepmex.wozainaar

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tepmex.wozainaar.ui.MainScreen
import com.tepmex.wozainaar.ui.MainViewModel
import com.tepmex.wozainaar.ui.MainViewModelFactory
import com.tepmex.wozainaar.ui.SettingsScreen
import com.tepmex.wozainaar.ui.theme.WoZaiNaarTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as WoZaiNaarApp
        val factory = MainViewModelFactory(app.repository, applicationContext)
        setContent {
            WoZaiNaarTheme {
                val viewModel: MainViewModel = viewModel(factory = factory)
                var showSettings by rememberSaveable { mutableStateOf(false) }

                if (showSettings) {
                    BackHandler { showSettings = false }
                    SettingsScreen(
                        viewModel = viewModel,
                        onNavigateBack = { showSettings = false },
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    MainScreen(
                        viewModel = viewModel,
                        onOpenSettings = { showSettings = true },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}
