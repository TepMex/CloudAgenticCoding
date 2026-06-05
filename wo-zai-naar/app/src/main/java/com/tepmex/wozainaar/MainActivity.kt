package com.tepmex.wozainaar

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tepmex.wozainaar.ui.MainScreen
import com.tepmex.wozainaar.ui.MainViewModel
import com.tepmex.wozainaar.ui.MainViewModelFactory
import com.tepmex.wozainaar.ui.theme.WoZaiNaarTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as WoZaiNaarApp
        val factory = MainViewModelFactory(app.repository)
        setContent {
            WoZaiNaarTheme {
                val viewModel: MainViewModel = viewModel(factory = factory)
                MainScreen(
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
