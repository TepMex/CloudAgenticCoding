# LLM calls are at-least-once; local lesson writes are idempotent

The app does not promise that a model request runs once. It promises that a saved lesson, review, or observation is not duplicated by a retry.

`prepareLesson` and `evaluateAnswers` commit the in-flight status before the network call, then commit the result in a second transaction. If the process dies after the response and before that commit, the response is gone and the next call may spend another request. The first commit wins. A later response sees the saved lesson or review and does not insert questions, reviews, or observations again. Observation proposals are unique per session and content hash.

A failed prepare returns the lesson to `CREATED`. A failed evaluation leaves it in `EVALUATING` with the submission untouched, so a retry does not ask the learner to type again.
