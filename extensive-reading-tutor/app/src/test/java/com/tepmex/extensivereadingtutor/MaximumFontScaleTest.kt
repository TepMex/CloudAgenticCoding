package com.tepmex.extensivereadingtutor

import android.app.Application
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import com.tepmex.extensivereadingtutor.ui.LessonActions
import com.tepmex.extensivereadingtutor.ui.LibraryScreen
import com.tepmex.extensivereadingtutor.ui.ProfileScreen
import com.tepmex.extensivereadingtutor.ui.ReaderPageUi
import com.tepmex.extensivereadingtutor.ui.ReaderScreen
import com.tepmex.extensivereadingtutor.ui.ReaderUi
import com.tepmex.extensivereadingtutor.ui.ShelfBook
import com.tepmex.extensivereadingtutor.ui.Tab
import com.tepmex.extensivereadingtutor.ui.TutorTabs
import com.tepmex.extensivereadingtutor.ui.TutorTheme
import com.tepmex.extensivereadingtutor.ui.TutorUiState
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w390dp-h844dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MaximumFontScaleTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun shelfFiltersTabsAndCoverStayWhole() {
        compose.setContent {
            AtMaximumFont {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f)) {
                        LibraryScreen(
                            state = TutorUiState(shelf = listOf(book())),
                            onOpen = {},
                            onImport = {},
                            onAskRemove = {},
                            onCancelRemove = {},
                            onRemove = {},
                        )
                    }
                    TutorTabs(Tab.BOOKS) {}
                }
            }
        }
        listOf("Читаю 1", "Завершено 0", "Все 1", "Книги", "Учёба", "Словарь", "Профиль", "书").forEach { fullyVisible(it) }
        save("font-scale-shelf.png")
    }

    @Test
    fun profileCountsStayWhole() {
        compose.setContent {
            AtMaximumFont {
                ProfileScreen(TutorUiState(), onSettings = {})
            }
        }
        listOf("наблюдений", "Профиль", "Занятие", "Наблюдение", "Нет").forEach { fullyVisible(it) }
        save("font-scale-profile.png")
    }

    @Test
    fun readerBackControlStaysWhole() {
        compose.setContent {
            AtMaximumFont {
                ReaderScreen(
                    reader = ReaderUi(
                        bookId = "b",
                        title = "红楼梦",
                        pages = listOf(ReaderPageUi("0:0", "雷打不动，他每天来。", "第一章", null)),
                        index = 0,
                        showPinyin = false,
                    ),
                    onBack = {},
                    onSettle = {},
                    onRequest = {},
                    actions = object : LessonActions {},
                )
            }
        }
        fullyVisible("‹")
        fullyVisible("红楼梦")
        save("font-scale-reader.png")
    }

    @Composable
    private fun AtMaximumFont(content: @Composable () -> Unit) {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
            TutorTheme(content)
        }
    }

    private fun fullyVisible(text: String) {
        val node = compose.onNodeWithText(text)
        try {
            node.performScrollTo()
        } catch (error: AssertionError) {
            if (error.message?.contains("Scroll SemanticsAction") != true) throw error
        }
        node.assertIsDisplayed()
        val clipped = node.getBoundsInRoot()
        val full = node.getUnclippedBoundsInRoot()
        assertEquals("$text left $full vs $clipped", full.left.value, clipped.left.value, 0.5f)
        assertEquals("$text top $full vs $clipped", full.top.value, clipped.top.value, 0.5f)
        assertEquals("$text right $full vs $clipped", full.right.value, clipped.right.value, 0.5f)
        assertEquals("$text bottom $full vs $clipped", full.bottom.value, clipped.bottom.value, 0.5f)
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

    private fun book() = ShelfBook(
        id = "b",
        title = "书虫夜读",
        author = "曹雪芹",
        percent = 12,
        done = false,
        place = "第一章",
        lastSession = "",
        charsRead = 10,
        charsTotal = 100,
        completedLessons = 0,
        chapters = emptyList(),
        sessions = emptyList(),
        activeStatus = null,
        activeSubmitted = false,
        coverSeed = 1,
    )
}
