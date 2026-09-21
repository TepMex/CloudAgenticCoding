package com.tepmex.duoshaoqian

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tepmex.duoshaoqian.ui.GameScreen
import com.tepmex.duoshaoqian.ui.GameViewModel
import com.tepmex.duoshaoqian.ui.theme.DuoShaoQianTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DuoShaoQianTheme {
                val vm: GameViewModel = viewModel()
                GameScreen(viewModel = vm, modifier = Modifier.fillMaxSize())
            }
        }
    }
}
