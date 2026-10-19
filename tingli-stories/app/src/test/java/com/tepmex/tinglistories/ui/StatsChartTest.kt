package com.tepmex.tinglistories.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.tepmex.tinglistories.domain.Evaluation
import com.tepmex.tinglistories.domain.Library
import com.tepmex.tinglistories.domain.Question
import com.tepmex.tinglistories.domain.Story
import com.tepmex.tinglistories.domain.StoryProgress
import kotlin.math.abs
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-notnight-xhdpi")
class StatsChartTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun chartDrawsGreenBarsAboveTheMeanAndGrayBarsBelowIt() {
        compose.setContent {
            TingliTheme {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    StatsScreen(state = TingliUiState(ready = true, library = chartLibrary()), onBack = {})
                }
            }
        }

        compose.onNodeWithTag(StatsChartTag).assertIsDisplayed()
        compose.onNodeWithContentDescription("02 50 зелёный", substring = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("04 0 серый", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Окно 5").assertDoesNotExist()
        compose.onNodeWithText("Окно 10").assertDoesNotExist()
        compose.onNodeWithText("Средний показатель", substring = true).assertDoesNotExist()
        compose.onNodeWithText("без ответа", substring = true).assertDoesNotExist()
        compose.onNodeWithText("故事1").assertDoesNotExist()

        val bitmap = drawScreen()
        save(bitmap, "stats-bars-light.png")
        assertTrue("green bars are visible", countNear(bitmap, 0xFF1F6B4A.toInt()) > 400)
        assertTrue("gray bars are visible", countNear(bitmap, 0xFF5C564C.toInt()) > 400)
        assertTrue("the old cinnabar line is gone", countNear(bitmap, 0xFF8E3428.toInt()) < 40)
    }

    @Test
    @Config(sdk = [34], qualifiers = "w360dp-h800dp-night-xhdpi")
    fun darkChartUsesTheLearningScreenGreenAndGray() {
        compose.setContent {
            TingliTheme {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    StatsScreen(state = TingliUiState(ready = true, library = chartLibrary()), onBack = {})
                }
            }
        }
        compose.onNodeWithContentDescription("зелёный", substring = true).assertIsDisplayed()
        val bitmap = drawScreen()
        save(bitmap, "stats-bars-dark.png")
        assertTrue(countNear(bitmap, 0xFF9ED4B8.toInt()) > 400)
        assertTrue(countNear(bitmap, 0xFFD0C6B8.toInt()) > 400)
    }

    @Test
    fun emptyLibraryHasNoChart() {
        compose.setContent {
            TingliTheme {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    StatsScreen(state = TingliUiState(ready = true, library = Library()), onBack = {})
                }
            }
        }
        compose.onNodeWithText("Импортируйте архив, чтобы увидеть статистику.").assertIsDisplayed()
        compose.onNodeWithTag(StatsChartTag).assertDoesNotExist()
    }

    @Test
    fun unansweredStoriesLeaveTheChartEmpty() {
        compose.setContent {
            TingliTheme {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    StatsScreen(
                        state = TingliUiState(ready = true, library = chartLibrary().copy(progress = emptyMap())),
                        onBack = {},
                    )
                }
            }
        }
        compose.onNodeWithText("Нет пройденных историй.").assertIsDisplayed()
        compose.onNodeWithTag(StatsChartTag).assertDoesNotExist()
    }

    private fun countNear(bitmap: Bitmap, argb: Int): Int {
        var count = 0
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        for (pixel in pixels) {
            if (distance(pixel, argb) <= 48) count++
        }
        return count
    }

    private fun distance(a: Int, b: Int): Int =
        abs(Color.red(a) - Color.red(b)) +
            abs(Color.green(a) - Color.green(b)) +
            abs(Color.blue(a) - Color.blue(b))

    private fun drawScreen(): Bitmap {
        compose.waitForIdle()
        val view = compose.activity.window.decorView.findViewById<android.view.View>(android.R.id.content)
        val width = view.width.coerceAtLeast(1)
        val height = view.height.coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        return bitmap
    }

    private fun save(bitmap: Bitmap, name: String) {
        val bytes = java.io.ByteArrayOutputStream()
        check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, bytes))
        val proc = ProcessBuilder(
            "bash",
            "-c",
            "mkdir -p /opt/cursor/artifacts && cat > \"/opt/cursor/artifacts/$name\"",
        ).start()
        proc.outputStream.use { it.write(bytes.toByteArray()) }
        check(proc.waitFor() == 0) { "could not save $name" }
    }
}

private fun chartLibrary(): Library {
    val stories = List(4) { index ->
        Story(
            id = index + 1,
            title = "故事${index + 1}",
            text = "文",
            questions = List(4) { Question("问", "答") },
            hasStoryAudio = true,
            hasQuestionsAudio = true,
        )
    }
    val progress = mapOf(
        1 to StoryProgress(listenCount = 3, evaluation = Evaluation(2, 4, "ok", emptyList())),
        2 to StoryProgress(listenCount = 2, evaluation = Evaluation(4, 4, "ok", emptyList())),
        3 to StoryProgress(listenCount = 10),
        4 to StoryProgress(listenCount = 4, evaluation = Evaluation(0, 4, "ok", emptyList())),
    )
    return Library(level = "HSK 4", stories = stories, progress = progress)
}
