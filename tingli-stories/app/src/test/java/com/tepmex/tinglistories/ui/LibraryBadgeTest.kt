package com.tepmex.tinglistories.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
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
@Config(sdk = [34], qualifiers = "w360dp-h800dp-night-xhdpi")
class LibraryBadgeTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun circleShowsTheListenCountAfterAGradeAndFillsByTheScore() {
        compose.setContent {
            TingliTheme {
                LibraryScreen(
                    state = TingliUiState(ready = true, library = sampleLibrary()),
                    onOpen = {},
                    onImport = {},
                    onSample = {},
                    onStats = {},
                    onSettings = {},
                )
            }
        }

        compose.onNodeWithText("换工作").assertIsDisplayed()
        compose.onNodeWithText("满分").assertIsDisplayed()
        compose.onNodeWithText("零分").assertIsDisplayed()
        compose.onNodeWithText("雨").assertDoesNotExist()
        compose.onNodeWithText("Пройдена", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Не пройдена", substring = true).assertDoesNotExist()
        compose.onAllNodesWithText("прослуш", substring = true).assertCountEquals(0)
        compose.onNodeWithText("0").assertDoesNotExist()
        compose.onNodeWithText("7").assertDoesNotExist()

        compose.onNodeWithContentDescription(
            "31 прослушивание, верных 2 из 4",
            useUnmergedTree = true,
        ).assertIsDisplayed()
        compose.onNodeWithContentDescription("верных 4 из 4", useUnmergedTree = true)
            .assertIsDisplayed()
        compose.onNodeWithContentDescription(
            "3 прослушивания, верных 0 из 4",
            useUnmergedTree = true,
        ).assertIsDisplayed()
        compose.onAllNodesWithContentDescription("Нет ответов", useUnmergedTree = true)
            .assertCountEquals(2)

        val half = sample("listen-mark-1")
        val empty = sample("listen-mark-2")
        val listened = sample("listen-mark-3")
        val full = sample("listen-mark-4")
        val missed = sample("listen-mark-5")

        assertTrue(
            "half fill should cover the right side, right=${hex(half.right)} left=${hex(half.left)}",
            distance(half.right, half.left) > 40,
        )
        assertTrue("empty circle stays unfilled", distance(empty.right, empty.left) < 20)
        assertTrue("listens without a grade stay unfilled", distance(listened.right, listened.left) < 20)
        assertTrue("a perfect score fills both sides", distance(full.right, full.left) < 20)
        assertTrue("a perfect score differs from an empty circle", distance(full.right, empty.right) > 40)
        assertTrue("a zero score leaves the disk empty", distance(missed.right, missed.left) < 20)

        save(drawScreen(), "library-listen-rings-dark.png")
    }

    @Test
    @Config(sdk = [34], qualifiers = "w360dp-h800dp-notnight-xhdpi")
    fun lightLibraryKeepsTheSameCircle() {
        compose.setContent {
            TingliTheme {
                LibraryScreen(
                    state = TingliUiState(ready = true, library = sampleLibrary()),
                    onOpen = {},
                    onImport = {},
                    onSample = {},
                    onStats = {},
                    onSettings = {},
                )
            }
        }
        compose.onNodeWithText("Пройдена", substring = true).assertDoesNotExist()
        compose.onNodeWithContentDescription(
            "31 прослушивание, верных 2 из 4",
            useUnmergedTree = true,
        ).assertIsDisplayed()
        val half = sample("listen-mark-1")
        assertTrue(distance(half.right, half.left) > 40)
        save(drawScreen(), "library-listen-rings-light.png")
    }

    private fun drawScreen(): Bitmap {
        compose.waitForIdle()
        val view = compose.activity.window.decorView.findViewById<android.view.View>(android.R.id.content)
        val width = view.width.coerceAtLeast(1)
        val height = view.height.coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        return bitmap
    }

    private fun hex(color: Int): String = color.toUInt().toString(16)

    private fun sample(tag: String): Sample {
        val bounds = compose.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val screen = drawScreen()
        val density = Density(compose.activity)
        val width = bounds.right - bounds.left
        val height = bounds.bottom - bounds.top
        fun at(xFrac: Float, yFrac: Float): Int {
            val x = with(density) { (bounds.left + width * xFrac).toPx() }.toInt()
                .coerceIn(0, screen.width - 1)
            val y = with(density) { (bounds.top + height * yFrac).toPx() }.toInt()
                .coerceIn(0, screen.height - 1)
            return screen.getPixel(x, y)
        }
        return Sample(left = at(0.18f, 0.5f), right = at(0.82f, 0.5f))
    }

    private fun save(bitmap: Bitmap, name: String) {
        val dir = File("/opt/cursor/artifacts")
        if (!dir.isDirectory) return
        File(dir, name).outputStream().use { out ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out))
        }
    }

    private data class Sample(val left: Int, val right: Int)

    private fun distance(a: Int, b: Int): Int =
        abs(Color.red(a) - Color.red(b)) +
            abs(Color.green(a) - Color.green(b)) +
            abs(Color.blue(a) - Color.blue(b)) +
            abs(Color.alpha(a) - Color.alpha(b))
}

private fun sampleLibrary(): Library {
    fun story(id: Int, title: String) = Story(
        id = id,
        title = title,
        text = "文本",
        questions = List(4) { Question("问", "答") },
        hasStoryAudio = true,
        hasQuestionsAudio = true,
    )
    return Library(
        level = "HSK 4",
        stories = listOf(
            story(1, "换工作"),
            story(2, "雨"),
            story(3, "猫"),
            story(4, "满分"),
            story(5, "零分"),
        ),
        progress = mapOf(
            1 to StoryProgress(listenCount = 31, evaluation = Evaluation(2, 4, "ok", emptyList())),
            2 to StoryProgress(listenCount = 0),
            3 to StoryProgress(listenCount = 7),
            4 to StoryProgress(listenCount = 0, evaluation = Evaluation(4, 4, "ok", emptyList())),
            5 to StoryProgress(listenCount = 3, evaluation = Evaluation(0, 4, "ok", emptyList())),
        ),
    )
}
