package com.tepmex.extensivereadingtutor.domain

enum class SessionEvent {
    BEGIN_PREPARE,
    COMMIT_PREPARE,
    ABORT_PREPARE,
    REVEAL_QUESTIONS,
    BEGIN_ANSWERING,
    BEGIN_EVALUATE,
    COMMIT_REVIEW,
    COMPLETE,
}

fun SessionStatus.next(event: SessionEvent): SessionStatus? = when (this to event) {
    SessionStatus.CREATED to SessionEvent.BEGIN_PREPARE -> SessionStatus.PREPARING
    SessionStatus.PREPARING to SessionEvent.BEGIN_PREPARE -> SessionStatus.PREPARING
    SessionStatus.PREPARING to SessionEvent.COMMIT_PREPARE -> SessionStatus.INTRO_READY
    SessionStatus.PREPARING to SessionEvent.ABORT_PREPARE -> SessionStatus.CREATED
    SessionStatus.INTRO_READY to SessionEvent.REVEAL_QUESTIONS -> SessionStatus.QUESTIONS_READY
    SessionStatus.QUESTIONS_READY to SessionEvent.BEGIN_ANSWERING -> SessionStatus.AWAITING_ANSWERS
    SessionStatus.AWAITING_ANSWERS to SessionEvent.BEGIN_EVALUATE -> SessionStatus.EVALUATING
    SessionStatus.EVALUATING to SessionEvent.BEGIN_EVALUATE -> SessionStatus.EVALUATING
    SessionStatus.EVALUATING to SessionEvent.COMMIT_REVIEW -> SessionStatus.REVIEW_READY
    SessionStatus.REVIEW_READY to SessionEvent.COMPLETE -> SessionStatus.COMPLETED
    else -> null
}

fun SessionStatus.require(event: SessionEvent): AppResult<SessionStatus> {
    val next = next(event)
    return if (next == null) {
        AppResult.Err(DomainError.IllegalTransition(name, event.name))
    } else {
        AppResult.Ok(next)
    }
}
