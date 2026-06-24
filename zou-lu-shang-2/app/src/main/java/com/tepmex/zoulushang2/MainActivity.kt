package com.tepmex.zoulushang2

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tepmex.zoulushang2.ui.AppViewModel
import com.tepmex.zoulushang2.ui.AppViewModelFactory
import com.tepmex.zoulushang2.ui.MainScreen
import com.tepmex.zoulushang2.ui.theme.ZouLuShang2Theme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as ZouLuShang2App
        val factory = AppViewModelFactory(app.repository, applicationContext)
        setContent {
            ZouLuShang2Theme {
                val viewModel: AppViewModel = viewModel(factory = factory)
                MainScreen(
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
