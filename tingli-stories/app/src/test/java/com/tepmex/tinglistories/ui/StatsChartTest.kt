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
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import com.tepmex.tinglistories.domain.Evaluation
import com.tepmex.tinglistories.domain.Library
import com.tepmex.tinglistories.domain.Question
import com.tepmex.tinglistories.domain.Story
import com.tepmex.tinglistories.domain.StoryProgress
import java.io.File
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
    fun chartDrawsTheIndicatorAndBothListenWindows() {
        compose.setContent {
            TingliTheme {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    StatsScreen(state = TingliUiState(ready = true, library = chartLibrary()), onBack = {})
                }
            }
        }

        compose.onNodeWithText("Показатель").assertIsDisplayed()
        compose.onNodeWithText("Окно 5").assertIsDisplayed()
        compose.onNodeWithText("Окно 10").assertIsDisplayed()
        compose.onNodeWithText("Окно 5 и окно 10", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Средний показатель 66,7").assertIsDisplayed()
        compose.onNodeWithText("Без ответа не учтено 20 прослушиваний.", substring = true).assertIsDisplayed()
        compose.onNodeWithText("20 прослушиваний, без ответа").assertIsDisplayed()
        compose.onAllNodesWithText("100 · 1 прослушивание")[0].assertIsDisplayed()
        compose.onNodeWithText("Верные, %").assertDoesNotExist()
        compose.onNodeWithTag(StatsChartTag).assertIsDisplayed()

        val bitmap = chartBitmap()
        save(drawScreen(), "stats-chart-light.png")
        assertTrue("indicator line is visible", countNear(bitmap, 0xFF8E3428.toInt()) > 12)
        assertTrue("window of 5 is visible", countNear(bitmap, 0xFF1F6B4A.toInt()) > 12)
        assertTrue("window of 10 is visible", countNear(bitmap, 0xFF24557A.toInt()) > 8)
    }

    @Test
    @Config(sdk = [34], qualifiers = "w360dp-h800dp-night-xhdpi")
    fun darkChartKeepsTheSameSeries() {
        compose.setContent {
            TingliTheme {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    StatsScreen(state = TingliUiState(ready = true, library = chartLibrary()), onBack = {})
                }
            }
        }
        compose.onNodeWithText("Окно 5").assertIsDisplayed()
        compose.onNodeWithText("Окно 10").assertIsDisplayed()
        val bitmap = chartBitmap()
        save(drawScreen(), "stats-chart-dark.png")
        assertTrue(countNear(bitmap, 0xFFE7B2A8.toInt()) > 12)
        assertTrue(countNear(bitmap, 0xFF9ED4B8.toInt()) > 12)
        assertTrue(countNear(bitmap, 0xFF8FB8DC.toInt()) > 8)
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
        compose.onNodeWithText("Показатель").assertDoesNotExist()
    }

    private fun chartBitmap(): Bitmap {
        val bounds = compose.onNodeWithTag(StatsChartTag).getUnclippedBoundsInRoot()
        val screen = drawScreen()
        val density = Density(compose.activity)
        val left = with(density) { bounds.left.toPx() }.toInt().coerceIn(0, screen.width - 1)
        val top = with(density) { bounds.top.toPx() }.toInt().coerceIn(0, screen.height - 1)
        val right = with(density) { bounds.right.toPx() }.toInt().coerceIn(left + 1, screen.width)
        val bottom = with(density) { bounds.bottom.toPx() }.toInt().coerceIn(top + 1, screen.height)
        return Bitmap.createBitmap(screen, left, top, right - left, bottom - top)
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
        val dir = File("/opt/cursor/artifacts")
        if (!dir.isDirectory) return
        File(dir, name).outputStream().use { out ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out))
        }
    }
}

private fun chartLibrary(): Library {
    val scores = listOf(4, 2, 3, 1, 4, 2, 3, 1, 4, 2, 3, 1, 4, 2, 4)
    val stories = List(scores.size + 1) { index ->
        Story(
            id = index + 1,
            title = "故事${index + 1}",
            text = "文",
            questions = List(4) { Question("问", "答") },
            hasStoryAudio = true,
            hasQuestionsAudio = true,
        )
    }
    val progress = buildMap {
        put(1, StoryProgress(listenCount = 1, evaluation = Evaluation(scores[0], 4, "ok", emptyList())))
        put(2, StoryProgress(listenCount = 20))
        scores.drop(1).forEachIndexed { offset, score ->
            put(
                offset + 3,
                StoryProgress(listenCount = 1, evaluation = Evaluation(score, 4, "ok", emptyList())),
            )
        }
    }
    return Library(level = "HSK 4", stories = stories, progress = progress)
}
