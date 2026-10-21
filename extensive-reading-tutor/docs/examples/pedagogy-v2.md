# Pedagogy v2 examples

These traces use the local fake model and the v2 contracts. They contain no API key. They show wiring, not that a live model follows the pedagogy. Semantic evaluation against a hosted model was not run.

## Prepare, mark for review, answer, evaluate

1. The app opens `他天天读书，手不释卷。` and the learner marks `手不释卷` with timing `WITH_REVIEW` and no comment. That mark does not call the model.
2. Prepare sends `prepare-v2`. The user message is `PrepareInputV2`: the fragment, preferences, lesson policy, and `learnerFocus` with the selection id, revision, anchors, and quoted text. The system message is the shared policy, the prepare instructions, and `PREPARE_SCHEMA_JSON`.
3. The saved lesson has an introduction that is not a copy of the fragment, one to three questions, and any returned hint already recorded as shown help.
4. The learner answers and submits. The same transaction freezes the selection id and revision. A mark added after that freeze is not inserted into the frozen evaluate input.
5. Evaluate sends `evaluate-v2` with the introduction, target skill, rubric, answer, shown help, and the frozen selection. The payload does not include mastery labels. The response needs one question review and one selection review. A correct answer still has a non-empty explanation. `nuance` stays null when there is no useful subtlety.
6. Accepted observations collapse to one evidence unit per attempt, concept, and skill. The next prepare includes that concept only when the new fragment actually contains it.

## Explain now, then a correct answer

1. The learner marks `手不释卷` and chooses «Объяснить сейчас».
2. `explain-selection-v1` receives the selection, its surrounding context, and the explanation language. It does not receive mastery history. The call does not change the lesson status or the cursor.
3. The response is stored with `revealedAt = null`. Repeating the same revision returns that response and does not call the model again. A transport failure stores no assistance event.
4. The UI reveal sets `revealedAt` and, because submit has not happened, writes a `MEANING_GIVEN` assistance event with unknown question scope.
5. The later correct answer may be `CORRECT`. The evidence unit is assisted. `independentSuccessUnits` stays 0, so the row is not raised as an independent success.
6. A reveal that happens only after submit does not rewrite the frozen assistance list.

## What the code rejects

A blank explanation, a duplicate question review, truncated JSON (`finish_reason = length`), an unknown selection id, or a stale selection revision does not become `REVIEW_READY`. A selection comment «не понимаю» is only a self-report. The book span itself is never stored as `studentEvidence`.
