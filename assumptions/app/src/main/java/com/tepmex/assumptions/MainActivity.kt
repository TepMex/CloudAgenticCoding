package com.tepmex.assumptions

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.tepmex.assumptions.ui.AssumptionsRoot
import com.tepmex.assumptions.ui.AssumptionsViewModel
import com.tepmex.assumptions.ui.theme.AssumptionsTheme

class MainActivity : ComponentActivity() {
    private val viewModel: AssumptionsViewModel by viewModels {
        AssumptionsViewModel.factory(application as AssumptionsApp)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(0x00000000, 0x00000000),
            navigationBarStyle = SystemBarStyle.light(0x00000000, 0x00000000),
        )
        setContent {
            AssumptionsTheme {
                AssumptionsRoot(viewModel)
            }
        }
    }
}
