package com.tepmex.ankientertainer.ui

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import androidx.test.core.app.ApplicationProvider
import com.tepmex.ankientertainer.AnkiEntertainerApp
import com.tepmex.ankientertainer.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class SystemThemeTest {

    @Test
    fun applicationFollowsSystemNightMode() {
        val app = ApplicationProvider.getApplicationContext<AnkiEntertainerApp>()
        assertEquals(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, AppCompatDelegate.getDefaultNightMode())
        assertTrue(app.packageName == "com.tepmex.ankientertainer")
    }

    @Test
    @Config(qualifiers = "notnight")
    fun lightPrimaryContainerUsesDayColor() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals(0xFFE1BEE7.toInt(), context.getColor(R.color.primary_container))
    }

    @Test
    @Config(qualifiers = "night")
    fun darkPrimaryContainerUsesNightColor() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals(0xFF4A148C.toInt(), context.getColor(R.color.primary_container))
    }

    @Test
    fun dayAndNightPrimaryContainersDiffer() {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val day = configurationContext(base, night = false)
        val night = configurationContext(base, night = true)
        assertNotEquals(day.getColor(R.color.primary_container), night.getColor(R.color.primary_container))
    }

    private fun configurationContext(base: Context, night: Boolean): Context {
        val config = Configuration(base.resources.configuration)
        val nightBit = if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightBit
        return base.createConfigurationContext(config)
    }
}
