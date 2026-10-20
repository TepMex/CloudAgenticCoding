package com.tepmex.extensivereadingtutor.domain

sealed interface DomainError {
    data class BookNotFound(val bookId: String) : DomainError
    data class BookRemoved(val bookId: String) : DomainError
    data class SessionNotFound(val sessionId: String) : DomainError
    data class QuestionNotFound(val questionId: String) : DomainError
    data class IllegalTransition(val from: String, val event: String) : DomainError
    data class LessonNotReady(val reason: String) : DomainError
    data class AnswersLocked(val sessionId: String) : DomainError
    data class SubmissionMismatch(val reason: String) : DomainError
    data object NothingLeftToRead : DomainError
    data object ModelNotConfigured : DomainError
    data class IngestionFailed(val reason: String) : DomainError
    data class LlmFailed(val kind: LlmFailureKind, val detail: String) : DomainError
    data class InvalidModelOutput(val reason: String) : DomainError
}

sealed interface AppResult<out T> {
    data class Ok<T>(val value: T) : AppResult<T>
    data class Err(val error: DomainError) : AppResult<Nothing>
}

fun DomainError.explain(): String = when (this) {
    is DomainError.BookNotFound -> "Книга не найдена."
    is DomainError.BookRemoved -> "Книга убрана с полки."
    is DomainError.SessionNotFound -> "Занятие не найдено."
    is DomainError.QuestionNotFound -> "Вопрос не найден."
    is DomainError.IllegalTransition -> "Переход $event из $from запрещён."
    is DomainError.LessonNotReady -> reason
    is DomainError.AnswersLocked -> "Ответы уже отправлены."
    is DomainError.SubmissionMismatch -> reason
    DomainError.NothingLeftToRead -> "В книге не осталось непрочитанного текста."
    DomainError.ModelNotConfigured -> "Укажите адрес и имя модели."
    is DomainError.IngestionFailed -> reason
    is DomainError.LlmFailed -> detail
    is DomainError.InvalidModelOutput -> reason
}
