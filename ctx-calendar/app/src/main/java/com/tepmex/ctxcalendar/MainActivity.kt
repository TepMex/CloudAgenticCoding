package com.tepmex.ctxcalendar

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.tepmex.ctxcalendar.ui.calendar.CalendarViewModelFactory
import com.tepmex.ctxcalendar.ui.navigation.CtxCalendarNavHost
import com.tepmex.ctxcalendar.ui.theme.CtxCalendarTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as CtxCalendarApp
        val factory = CalendarViewModelFactory(app.photoRepository)
        setContent {
            CtxCalendarTheme {
                CtxCalendarNavHost(
                    viewModelFactory = factory,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
