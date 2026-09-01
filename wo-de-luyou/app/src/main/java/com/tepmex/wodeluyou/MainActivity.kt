package com.tepmex.wodeluyou

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.tepmex.wodeluyou.ui.WoDeLuyouAppShell
import com.tepmex.wodeluyou.ui.theme.WoDeLuyouTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as WoDeLuyouApp
        setContent {
            WoDeLuyouTheme {
                WoDeLuyouAppShell(
                    catalog = app.repository.catalog,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
