package com.tepmex.zoulushang

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tepmex.zoulushang.ui.AppViewModel
import com.tepmex.zoulushang.ui.AppViewModelFactory
import com.tepmex.zoulushang.ui.MainScreen
import com.tepmex.zoulushang.ui.theme.ZouLuShangTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as ZouLuShangApp
        val factory = AppViewModelFactory(app.repository)
        setContent {
            ZouLuShangTheme {
                val viewModel: AppViewModel = viewModel(factory = factory)
                MainScreen(
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
