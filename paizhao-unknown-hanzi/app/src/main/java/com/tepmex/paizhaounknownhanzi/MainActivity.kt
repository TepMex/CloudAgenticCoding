package com.tepmex.paizhaounknownhanzi

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.tepmex.paizhaounknownhanzi.ui.PaizhaoAppShell
import com.tepmex.paizhaounknownhanzi.ui.PaizhaoViewModel
import com.tepmex.paizhaounknownhanzi.ui.theme.PaizhaoTheme

class MainActivity : ComponentActivity() {
    private val viewModel: PaizhaoViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PaizhaoTheme {
                PaizhaoAppShell(
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
