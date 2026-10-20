package com.tepmex.extensivereadingtutor.books

import com.tepmex.extensivereadingtutor.domain.AppClock
import com.tepmex.extensivereadingtutor.domain.AppResult
import com.tepmex.extensivereadingtutor.domain.Book
import com.tepmex.extensivereadingtutor.domain.BookBlock
import com.tepmex.extensivereadingtutor.domain.BookId
import com.tepmex.extensivereadingtutor.domain.DomainError
import com.tepmex.extensivereadingtutor.domain.IdGenerator
import com.tepmex.extensivereadingtutor.domain.LOCAL_LEARNER
import com.tepmex.extensivereadingtutor.domain.ReadingProgress
import com.tepmex.extensivereadingtutor.domain.TextCursor
import com.tepmex.extensivereadingtutor.domain.defaultPreferences
import com.tepmex.extensivereadingtutor.learner.LearnerService
import com.tepmex.extensivereadingtutor.storage.TutorStore

interface BookFileStore {
    fun put(bookId: String, bytes: ByteArray): String
    fun delete(localRef: String)
}

class BookService(
    private val store: TutorStore,
    private val files: BookFileStore,
    private val extractor: EpubTextExtractor,
    private val clock: AppClock,
    private val ids: IdGenerator,
    private val learner: LearnerService,
) {
    suspend fun import(
        bytes: ByteArray,
        sourceUri: String,
        fallbackLanguage: String,
    ): AppResult<Book> {
        val hash = sha256(bytes)
        val existing = store.read { it.bookByHash(hash) }
        if (existing != null) {
            if (existing.removedAt == null) return AppResult.Ok(existing)
            val ref = files.put(existing.id.value, bytes)
            return store.transaction { db ->
                db.saveBook(existing.copy(removedAt = null, localRef = ref, sourceUri = sourceUri))
                AppResult.Ok(db.book(existing.id)!!)
            }
        }
        val extracted = extractor.extract(bytes)
        if (extracted is AppResult.Err) return extracted
        val bookData = (extracted as AppResult.Ok).value
        val now = clock.now()
        val bookId = BookId(ids.newId("book"))
        val language = bookData.language ?: primaryLanguage(fallbackLanguage) ?: "und"
        val localRef = files.put(bookId.value, bytes)
        val book = Book(
            id = bookId,
            title = bookData.title.ifBlank { "Untitled" },
            author = bookData.author,
            language = language,
            sourceUri = sourceUri,
            localRef = localRef,
            contentHash = hash,
            importedAt = now,
            metadata = bookData.metadata,
            removedAt = null,
        )
        val blocks = bookData.blocks.mapIndexed { order, block ->
            val text = nfc(block.text)
            BookBlock(
                id = blockIdOf(bookId, order),
                bookId = bookId,
                spineIndex = block.spineIndex,
                spineHref = block.href,
                orderInBook = order,
                text = text,
                textHash = sha256(text),
                chapterTitle = block.chapterTitle,
            )
        }
        return store.transaction { db ->
            db.ensureLearner(LOCAL_LEARNER, now)
            if (db.preferences(LOCAL_LEARNER) == null) db.savePreferences(defaultPreferences(now))
            db.insertBook(book)
            db.insertBlocks(blocks)
            db.saveProgress(
                ReadingProgress(
                    bookId = bookId,
                    cursor = TextCursor(blocks.first().orderInBook, 0),
                    activeSessionId = null,
                    completedFragmentIds = emptyList(),
                    updatedAt = now,
                ),
            )
            AppResult.Ok(book)
        }
    }

    suspend fun removeFromLibrary(bookId: BookId): AppResult<Unit> = store.transaction { db ->
        val book = db.book(bookId) ?: return@transaction AppResult.Err(DomainError.BookNotFound(bookId.value))
        files.delete(book.localRef)
        db.saveBook(book.copy(removedAt = clock.now()))
        AppResult.Ok(Unit)
    }

    suspend fun eraseLearningHistory(bookId: BookId): AppResult<Unit> = store.transaction { db ->
        if (db.book(bookId) == null) return@transaction AppResult.Err(DomainError.BookNotFound(bookId.value))
        db.eraseLearningHistory(bookId, clock.now())
        learner.replaceProjection(db, LOCAL_LEARNER)
        AppResult.Ok(Unit)
    }
}
