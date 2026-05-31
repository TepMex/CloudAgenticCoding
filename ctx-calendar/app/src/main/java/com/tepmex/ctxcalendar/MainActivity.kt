package com.tepmex.ctxcalendar

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.maps.MapsInitializer
import com.tepmex.ctxcalendar.ui.calendar.CalendarViewModelFactory
import com.tepmex.ctxcalendar.ui.day.DayDetailViewModelFactory
import com.tepmex.ctxcalendar.ui.navigation.CtxCalendarNavHost
import com.tepmex.ctxcalendar.ui.settings.SettingsViewModelFactory
import com.tepmex.ctxcalendar.ui.theme.CtxCalendarTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        MapsInitializer.initialize(applicationContext)

        val app = application as CtxCalendarApp
        lifecycleScope.launch {
            val uri = app.preferences.takeoutDbUri.first()
            if (!uri.isNullOrBlank()) {
                app.takeoutRepository.openFromUri(uri)
            }
        }

        val calendarFactory = CalendarViewModelFactory(app.photoRepository)
        val dayDetailFactory = DayDetailViewModelFactory(app.takeoutRepository)
        val settingsFactory = SettingsViewModelFactory(app.preferences, app.takeoutRepository)

        setContent {
            CtxCalendarTheme {
                CtxCalendarNavHost(
                    viewModelFactory = calendarFactory,
                    dayDetailViewModelFactory = dayDetailFactory,
                    settingsViewModelFactory = settingsFactory,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
