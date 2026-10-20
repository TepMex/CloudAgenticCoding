package com.tepmex.extensivereadingtutor

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.tepmex.extensivereadingtutor.domain.AppResult
import com.tepmex.extensivereadingtutor.domain.LOCAL_LEARNER
import com.tepmex.extensivereadingtutor.domain.SessionStatus
import com.tepmex.extensivereadingtutor.storage.room.RoomTutorStore
import com.tepmex.extensivereadingtutor.storage.room.TutorDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class RoomPersistenceTest {
    @Test
    fun lessonSurvivesANewDatabaseConnection() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val first = Room.databaseBuilder(context, TutorDatabase::class.java, "tutor-cycle.db")
            .allowMainThreadQueries()
            .build()
        val harness = Harness(RoomTutorStore(first))
        val book = harness.import()
        val session = harness.lessons.startOrResumeLesson(book).unwrap()
        val prepared = harness.lessons.prepareLesson(session.id).unwrap()
        val question = prepared.prepared!!.questions.single()
        harness.lessons.revealQuestions(session.id).unwrap()
        harness.lessons.saveAnswerDraft(session.id, question.id, "черновик из базы").unwrap()
        harness.lessons.submitAnswers(
            session.id,
            com.tepmex.extensivereadingtutor.domain.StudentSubmission(
                session.id,
                listOf(com.tepmex.extensivereadingtutor.domain.SubmittedAnswer(question.id, "это бесстрашие")),
                null, false, null, null, 0, java.time.Instant.parse("2026-01-01T00:00:00Z"),
            ),
        ).unwrap()
        val reviewed = harness.lessons.evaluateAnswers(session.id).unwrap()
        assertEquals(SessionStatus.REVIEW_READY, reviewed.status)
        first.close()

        val second = Room.databaseBuilder(context, TutorDatabase::class.java, "tutor-cycle.db")
            .allowMainThreadQueries()
            .build()
        val store = RoomTutorStore(second)
        val restored = store.read { it.session(session.id) }!!
        assertEquals(SessionStatus.REVIEW_READY, restored.status)
        assertEquals("это бесстрашие", restored.submission!!.answers.single().text)
        assertEquals("черновик из базы", store.read { it.drafts(session.id) }.single().text)
        assertEquals(1, store.read { it.observations(LOCAL_LEARNER) }.size)
        assertTrue(store.read { it.fragment(restored.fragmentId) }!!.originalText.contains("雷打不动"))
        val again = Harness(store, harness.ai, harness.clock, harness.models)
        val duplicate = again.lessons.evaluateAnswers(session.id)
        assertTrue(duplicate is AppResult.Ok)
        assertEquals(1, store.read { it.observations(LOCAL_LEARNER) }.size)
        second.close()
    }
}

private fun <T> AppResult<T>.unwrap(): T = when (this) {
    is AppResult.Ok -> value
    is AppResult.Err -> error(error.toString())
}
