package com.tepmex.cornegame

import android.view.ViewGroup
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Реальный запуск MainActivity. Падение в инициализаторе поля (getSystemService до
 * super.onCreate) роняет этот тест ещё до RESUMED. recreate проверяет onDestroy:
 * приёмник USB снимается, контроллер закрывается, новая activity снова стартует.
 */
@RunWith(AndroidJUnit4::class)
class MainActivityLaunchTest {
    @Test
    fun launchesPastOnCreateAndSurvivesRecreation() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.moveToState(Lifecycle.State.RESUMED)
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            assertShown(scenario)
            scenario.recreate()
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            assertShown(scenario)
        }
    }

    private fun assertShown(scenario: ActivityScenario<MainActivity>) {
        scenario.onActivity { activity ->
            assertFalse(activity.isFinishing)
            assertEquals(Lifecycle.State.RESUMED, activity.lifecycle.currentState)
            val content = activity.findViewById<ViewGroup>(android.R.id.content)
            assertTrue(content.height > 0)
            assertTrue(content.childCount > 0)
        }
    }
}
