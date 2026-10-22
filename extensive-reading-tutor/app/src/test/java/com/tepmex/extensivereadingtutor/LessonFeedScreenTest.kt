package com.tepmex.extensivereadingtutor

import android.app.Application
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import com.tepmex.extensivereadingtutor.domain.AnswerVerdict
import com.tepmex.extensivereadingtutor.domain.QuestionType
import com.tepmex.extensivereadingtutor.domain.SessionStatus
import com.tepmex.extensivereadingtutor.domain.SubjectiveDifficulty
import com.tepmex.extensivereadingtutor.ui.LessonActions
import com.tepmex.extensivereadingtutor.ui.LessonPanel
import com.tepmex.extensivereadingtutor.ui.LessonScreen
import com.tepmex.extensivereadingtutor.ui.QuestionUi
import com.tepmex.extensivereadingtutor.ui.ReviewUi
import com.tepmex.extensivereadingtutor.ui.TutorTheme
import com.tepmex.extensivereadingtutor.ui.TutorUiState
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w390dp-h844dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LessonFeedScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun questionsStayUnderTheFragment() {
        compose.setContent {
            TutorTheme {
                LessonScreen(TutorUiState(lesson = answeringLesson()), object : LessonActions {})
            }
        }
        compose.onNodeWithText("雷打不动，他每天来。").assertIsDisplayed()
        compose.onNodeWithText("Здесь устойчивое выражение.").assertIsDisplayed()
        compose.onNodeWithText("Что значит 雷打不动?").assertIsDisplayed()
        compose.onNodeWithText("неизменно").assertIsDisplayed()
        compose.onNodeWithText("Отправить ответы").assertIsDisplayed()
        absent("К ответам")
        absent("Разбор")
        compose.onNodeWithText("雷打不动，他每天来。").performScrollTo()
        save("lesson-feed-questions.png")
    }

    @Test
    fun reviewAppendsBelowAndTheFragmentScrollsBack() {
        compose.setContent {
            TutorTheme {
                LessonScreen(TutorUiState(lesson = reviewedLesson()), object : LessonActions {})
            }
        }
        present("雷打不动，他每天来。")
        present("Введение")
        present("Вопросы")
        compose.onNodeWithText("Разбор").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Верно по смыслу.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Дальше по книге").assertIsDisplayed()
        absent("1/4")
        save("lesson-feed-review.png")
        compose.onNodeWithText("雷打不动，他每天来。").performScrollTo().assertIsDisplayed()
        save("lesson-feed-fragment.png")
    }

    private fun present(text: String) {
        assertTrue(compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty())
    }

    private fun absent(text: String) {
        assertEquals(0, compose.onAllNodesWithText(text).fetchSemanticsNodes().size)
    }

    private fun save(name: String) {
        compose.waitForIdle()
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width.coerceAtLeast(1), view.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bitmap))
        val dir = File("/opt/cursor/artifacts")
        dir.mkdirs()
        File(dir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun answeringLesson() = panel(
        status = SessionStatus.QUESTIONS_READY,
        submitted = false,
        reviews = emptyList(),
    )

    private fun reviewedLesson() = panel(
        status = SessionStatus.REVIEW_READY,
        submitted = true,
        reviews = listOf(ReviewUi("q1", AnswerVerdict.CORRECT, "Верно по смыслу.", "")),
    )

    private fun panel(status: SessionStatus, submitted: Boolean, reviews: List<ReviewUi>) = LessonPanel(
        sessionId = "s",
        bookId = "b",
        status = status,
        chapter = "第一章",
        original = "雷打不动，他每天来。",
        introduction = "Здесь устойчивое выражение.",
        hints = listOf("雷打不动 — неизменно"),
        questions = listOf(QuestionUi("q1", QuestionType.IDIOM, "Что значит 雷打不动?", "雷打不动")),
        drafts = mapOf("q1" to "неизменно"),
        difficulty = SubjectiveDifficulty.SUITABLE,
        comment = "",
        submitted = submitted,
        reviews = reviews,
        recommendations = emptyList(),
    )
}
