package com.tepmex.tinglistories.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.tepmex.tinglistories.domain.AudioKind
import com.tepmex.tinglistories.domain.Evaluation
import com.tepmex.tinglistories.domain.Library
import com.tepmex.tinglistories.domain.Question
import com.tepmex.tinglistories.domain.Story
import com.tepmex.tinglistories.domain.StoryProgress
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-notnight-xhdpi")
class AnswerReplayTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun replaySitsBesideTheStoryTextAfterAGradeAndDoesNotSubmit() {
        var replays = 0
        var submits = 0
        compose.setContent {
            TingliTheme {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    AnswerScreen(
                        state = TingliUiState(
                            ready = true,
                            library = library(completed = true),
                            playing = Playing(storyId = 1, kind = AudioKind.STORY),
                            playbackPositionMs = 42_000,
                            playbackDurationMs = 125_000,
                        ),
                        story = story(audio = true),
                        onBack = {},
                        onDraft = { _, _ -> },
                        onSubmit = { submits++ },
                        onReset = {},
                        onNext = {},
                        onReplay = { replays++ },
                        onSeek = {},
                    )
                }
            }
        }
        compose.onNodeWithText("Текст истории").assertIsDisplayed()
        compose.onNodeWithText("李红换了工作。").assertIsDisplayed()
        compose.onNodeWithText("0:42 / 2:05").assertIsDisplayed()
        compose.onNodeWithText("Прослушать ещё раз").assertIsDisplayed().performClick()
        save(drawScreen(), "answer-replay-light.png")
        assertEquals(1, replays)
        assertEquals(0, submits)
    }

    @Test
    fun replayStaysHiddenUntilTheStoryIsGraded() {
        compose.setContent {
            TingliTheme {
                AnswerScreen(
                    state = TingliUiState(ready = true, library = library(completed = false)),
                    story = story(audio = true),
                    onBack = {},
                    onDraft = { _, _ -> },
                    onSubmit = {},
                    onReset = {},
                    onNext = {},
                    onReplay = {},
                    onSeek = {},
                )
            }
        }
        compose.onNodeWithText("Прослушать ещё раз").assertDoesNotExist()
        compose.onNodeWithText("李红换了工作。").assertDoesNotExist()
        compose.onNodeWithText("Проверить").assertIsDisplayed()
    }

    @Test
    fun replayStaysHiddenWhenTheStoryHasNoAudio() {
        compose.setContent {
            TingliTheme {
                AnswerScreen(
                    state = TingliUiState(ready = true, library = library(completed = true)),
                    story = story(audio = false),
                    onBack = {},
                    onDraft = { _, _ -> },
                    onSubmit = {},
                    onReset = {},
                    onNext = {},
                    onReplay = {},
                    onSeek = {},
                )
            }
        }
        compose.onNodeWithText("Текст истории").assertIsDisplayed()
        compose.onNodeWithText("李红换了工作。").assertIsDisplayed()
        compose.onNodeWithText("Прослушать ещё раз").assertDoesNotExist()
    }

    private fun drawScreen(): Bitmap {
        compose.waitForIdle()
        val view = compose.activity.window.decorView.findViewById<android.view.View>(android.R.id.content)
        val bitmap = Bitmap.createBitmap(view.width.coerceAtLeast(1), view.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
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

private fun story(audio: Boolean) = Story(
    id = 1,
    title = "换工作",
    text = "李红换了工作。",
    questions = listOf(Question("以前做什么？", "会计")),
    hasStoryAudio = audio,
    hasQuestionsAudio = audio,
)

private fun library(completed: Boolean): Library {
    val progress = if (completed) {
        StoryProgress(
            listenCount = 3,
            answers = listOf("会计"),
            evaluation = Evaluation(1, 1, "Смысл совпал.", emptyList()),
        )
    } else {
        StoryProgress(listenCount = 3)
    }
    return Library(stories = listOf(story(audio = true)), progress = mapOf(1 to progress))
}
