package com.tepmex.cornegame.ui

import com.tepmex.cornegame.data.FileSessionHistory
import com.tepmex.cornegame.data.FileSettingsStore
import com.tepmex.cornegame.domain.Language
import com.tepmex.cornegame.domain.TrainerAction
import com.tepmex.cornegame.domain.actionsToType
import java.io.File
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TrainViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun unfinishedSessionIsNotStoredAndLanguageSwitchStartsAnother() {
        val dir = tempDir()
        val history = FileSessionHistory(File(dir, "sessions.json"))
        val settings = FileSettingsStore(File(dir, "settings.json"))
        var now = 5_000L
        val viewModel = TrainViewModel(history, settings, clock = { now }, random = Random(4))
        val firstTarget = viewModel.state.value.session.target
        viewModel.onTrainerAction(TrainerAction.Character('!'))
        assertEquals(1, viewModel.state.value.errorNonce)
        assertEquals(0, viewModel.state.value.session.index)
        assertTrue(history.load().isEmpty())

        val other = if (viewModel.state.value.settings.language == Language.EN) {
            Language.RU
        } else {
            Language.EN
        }
        viewModel.setLanguage(other)
        assertEquals(other, viewModel.state.value.settings.language)
        assertEquals(AppScreen.Train, viewModel.state.value.screen)
        assertEquals(0, viewModel.state.value.session.index)
        assertNotEquals(firstTarget, viewModel.state.value.session.target)
        assertTrue(history.load().isEmpty())
        assertEquals(other, settings.load().language)
    }

    @Test
    fun finishingWritesHistoryAndSameLanguageDoesNotResetProgress() {
        val dir = tempDir()
        val history = FileSessionHistory(File(dir, "sessions.json"))
        val settings = FileSettingsStore(File(dir, "settings.json"))
        var now = 1_000L
        val viewModel = TrainViewModel(history, settings, clock = { now }, random = Random(8))
        val target = viewModel.state.value.session.target
        val language = viewModel.state.value.session.language
        actionsToType(target, language).forEach { action ->
            now += 1_000
            viewModel.onTrainerAction(action)
        }
        assertEquals(AppScreen.Results, viewModel.state.value.screen)
        assertEquals(1, history.load().size)
        assertEquals(viewModel.state.value.session.language, history.load().single().language)
        assertTrue(history.load().single().durationMs > 0)

        viewModel.restart()
        val same = viewModel.state.value.settings.language
        val head = viewModel.state.value.session.target.first()
        viewModel.onTrainerAction(TrainerAction.Character(head))
        assertEquals(1, viewModel.state.value.session.index)
        viewModel.setLanguage(same)
        assertEquals(1, viewModel.state.value.session.index)
        assertTrue(viewModel.state.value.settings.highlight)

        viewModel.updateSettings { it.copy(highlight = false, fingerColors = true, keyScale = 0.7f) }
        assertFalse(viewModel.state.value.settings.highlight)
        assertEquals(1, viewModel.state.value.session.index)
        val stored = settings.load()
        assertFalse(stored.highlight)
        assertTrue(stored.fingerColors)
        assertEquals(0.7f, stored.keyScale, 0.001f)
    }

    private fun tempDir(): File = File.createTempFile("corne-vm", "dir").apply {
        delete()
        mkdirs()
    }
}
