package com.tepmex.extensivereadingtutor

import com.tepmex.extensivereadingtutor.domain.Book
import com.tepmex.extensivereadingtutor.domain.BookBlock
import com.tepmex.extensivereadingtutor.domain.BookId
import com.tepmex.extensivereadingtutor.domain.BlockId
import com.tepmex.extensivereadingtutor.domain.EpubMetadata
import com.tepmex.extensivereadingtutor.domain.ReadingProgress
import com.tepmex.extensivereadingtutor.domain.SessionStatus
import com.tepmex.extensivereadingtutor.domain.TextCursor
import com.tepmex.extensivereadingtutor.storage.InMemoryTutorStore
import com.tepmex.extensivereadingtutor.ui.ChapterMark
import com.tepmex.extensivereadingtutor.ui.LessonFeedSection
import com.tepmex.extensivereadingtutor.ui.chapterRows
import com.tepmex.extensivereadingtutor.ui.finished
import com.tepmex.extensivereadingtutor.ui.loadShelf
import com.tepmex.extensivereadingtutor.ui.visibleLessonSections
import com.tepmex.extensivereadingtutor.ui.progressPercent
import com.tepmex.extensivereadingtutor.ui.readCodePoints
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShelfMathTest {
    @Test
    fun cursorSplitsProgressAndChapters() {
        val blocks = listOf(
            block(0, "你好世界", "Глава 1"),
            block(1, "再见", "Глава 2"),
        )
        val cursor = TextCursor(0, 2)
        assertEquals(2, readCodePoints(blocks, cursor))
        assertEquals(33, progressPercent(2, 6))
        assertFalse(finished(blocks, cursor))
        assertTrue(finished(blocks, TextCursor(2, 0)))
        val chapters = chapterRows(blocks, cursor)
        assertEquals(ChapterMark.CURRENT, chapters[0].mark)
        assertEquals(ChapterMark.AHEAD, chapters[1].mark)
        assertEquals(ChapterMark.DONE, chapterRows(blocks, TextCursor(1, 0))[0].mark)
    }

    @Test
    fun lessonFeedKeepsEarlierSections() {
        assertEquals(
            listOf(LessonFeedSection.FRAGMENT),
            visibleLessonSections(SessionStatus.PREPARING, submitted = false),
        )
        assertEquals(
            listOf(LessonFeedSection.FRAGMENT, LessonFeedSection.INTRODUCTION),
            visibleLessonSections(SessionStatus.INTRO_READY, submitted = false),
        )
        assertEquals(
            listOf(LessonFeedSection.FRAGMENT, LessonFeedSection.INTRODUCTION, LessonFeedSection.QUESTIONS),
            visibleLessonSections(SessionStatus.AWAITING_ANSWERS, submitted = false),
        )
        assertEquals(
            listOf(
                LessonFeedSection.FRAGMENT,
                LessonFeedSection.INTRODUCTION,
                LessonFeedSection.QUESTIONS,
                LessonFeedSection.REVIEW,
            ),
            visibleLessonSections(SessionStatus.AWAITING_ANSWERS, submitted = true),
        )
        assertEquals(
            listOf(
                LessonFeedSection.FRAGMENT,
                LessonFeedSection.INTRODUCTION,
                LessonFeedSection.QUESTIONS,
                LessonFeedSection.REVIEW,
            ),
            visibleLessonSections(SessionStatus.REVIEW_READY, submitted = true),
        )
    }

    @Test
    fun shelfListsOnlyBooksStillOnTheShelf() = runBlocking {
        val store = InMemoryTutorStore()
        val zone = ZoneOffset.UTC
        store.transaction { db ->
            db.insertBook(book("book-1", removed = false))
            db.insertBook(book("book-2", removed = true))
            db.insertBlocks(listOf(block(0, "你好世界", "Глава 1").copy(bookId = BookId("book-1"))))
            db.saveProgress(
                ReadingProgress(
                    BookId("book-1"),
                    TextCursor(0, 2),
                    null,
                    emptyList(),
                    Instant.parse("2026-10-02T00:00:00Z"),
                ),
            )
        }
        val shelf = store.read { loadShelf(it, zone) }
        assertEquals(listOf("book-1"), shelf.books.map { it.id })
        assertEquals(50, shelf.books.single().percent)
        assertEquals("Глава 1", shelf.books.single().place)
    }

    private fun block(order: Int, text: String, chapter: String) = BookBlock(
        id = BlockId("b$order"),
        bookId = BookId("book"),
        spineIndex = 0,
        spineHref = "c.xhtml",
        orderInBook = order,
        text = text,
        textHash = "h$order",
        chapterTitle = chapter,
    )

    private fun book(id: String, removed: Boolean) = Book(
        id = BookId(id),
        title = "天官赐福",
        author = "墨香铜臭",
        language = "zh",
        sourceUri = "file://$id.epub",
        localRef = "/tmp/$id.epub",
        contentHash = "hash-$id",
        importedAt = Instant.parse("2026-10-01T00:00:00Z"),
        metadata = EpubMetadata(null, null, null, "zh"),
        removedAt = if (removed) Instant.parse("2026-10-03T00:00:00Z") else null,
    )
}
