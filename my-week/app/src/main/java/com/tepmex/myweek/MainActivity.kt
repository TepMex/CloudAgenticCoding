package com.tepmex.myweek

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.tepmex.myweek.ui.WeekApp
import com.tepmex.myweek.ui.WeekViewModel
import com.tepmex.myweek.ui.theme.MyWeekTheme

class MainActivity : ComponentActivity() {
    private val viewModel: WeekViewModel by viewModels {
        WeekViewModel.factory(application as MyWeekApp)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyWeekTheme {
                WeekApp(viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.onVisible()
    }
}
