package com.tepmex.instantpinyin

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.tepmex.instantpinyin.ui.ReaderScreen
import com.tepmex.instantpinyin.ui.ReaderViewModel
import com.tepmex.instantpinyin.ui.theme.InstantPinyinTheme

class MainActivity : ComponentActivity() {
    private val viewModel: ReaderViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()
        setContent {
            InstantPinyinTheme {
                ReaderScreen(
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
