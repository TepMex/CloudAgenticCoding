# Extensive Reading Tutor — SPEC

## Purpose

Android tutor for one learner who reads a real book in a foreign language with a model they pay for themselves. The first check is Chinese prose with Russian explanations. The app keeps the book position, the lesson, the answers, and the evidence of what the learner understood. The model proposes wording and judgments. It does not own the record.

## Requirements

1. Project folder `extensive-reading-tutor`. Application id `com.tepmex.extensivereadingtutor`. minSdk 34, compileSdk 36.
2. One local learner. No account and no backend.
3. Import a DRM-free EPUB, store the original bytes, and extract paragraphs without changing their wording.
4. The same file hash imports once. A removed book can be restored from those bytes without a second history.
5. Choose the next unread fragment on paragraph boundaries, then sentence, clause, and word boundaries. Do not skip or repeat text. A later change of fragment size does not rewrite old fragments.
6. A lesson is a state machine the app alone advances: `CREATED → PREPARING → INTRO_READY → QUESTIONS_READY → AWAITING_ANSWERS → EVALUATING → REVIEW_READY → COMPLETED`.
7. One lesson request prepares the introduction and the questions. A second request reviews the locked answers. The fragment text stored on the lesson is the extracted original, never the model text.
8. Draft answers persist before submit. A network failure does not delete them. Submit locks them.
9. A review can be saved when some observation proposals are rejected. Rejected proposals do not enter the ledger. Applying the same result twice does not duplicate observations.
10. Observations are append-only. A retraction keeps the row and drops it from the knowledge projection.
11. Knowledge state is recomputed from the ledger with the rules in `docs/adr/0006-recomputable-knowledge.md`.
12. The next lesson receives a bounded memory packet: the full fragment, a short preceding tail, preferences, relevant knowledge, and recent question prompts.
13. The learner can switch provider and model. Old calls keep the provider and model they used.
14. API keys are encrypted with a keystore key. They are not logged, not placed in lesson tables, and not part of a history export.
15. Removing a book from the shelf and erasing its learning history are different operations.
16. Domain logic is covered by JVM tests with a fake model. Room is the on-device store.

## Interfaces

| Interface | Detail |
| --- | --- |
| Launcher | `com.tepmex.extensivereadingtutor.MainActivity` |
| Open EPUB | `ACTION_OPEN_DOCUMENT` and `ACTION_VIEW` for `application/epub+zip` |
| Lesson API | `LessonService` methods below |
| Model | `LessonAi.prepareLesson`, `LessonAi.evaluateAnswers` |
| HTTP | `LlmProvider.generate` via OpenAI-compatible `chat/completions` |
| Database | Room `tutor.db`, schema version 1 |
| Settings | DataStore `tutor_settings` for base URL and model name |
| Secrets | `SecretStore`, Android Keystore AES-GCM on device |
| Gradle | `./gradlew test assembleRelease` |

### LessonService

`startOrResumeLesson(bookId)`  
Precondition: the book exists and is on the shelf.  
Result: the non-completed lesson for that book, or a new `CREATED` lesson on the next fragment.  
Errors: unknown book, book removed, no unread text left.

`prepareLesson(sessionId)`  
Precondition: lesson is `CREATED` or `PREPARING`, and a model name is set. If the lesson already has a prepared lesson, the saved one is returned and the model is not called.  
Result: `INTRO_READY` and a `PreparedLesson`.  
Errors: illegal status, model not configured, transport failure, unusable model JSON. A transport or JSON failure returns the lesson to `CREATED`.

`revealQuestions(sessionId)`  
Precondition: `INTRO_READY`. Later answering states return the lesson unchanged.  
Result: `QUESTIONS_READY`.

`saveAnswerDraft(sessionId, questionId, text)`  
Precondition: `QUESTIONS_READY` or `AWAITING_ANSWERS`, and no submission yet. The first draft moves `QUESTIONS_READY` to `AWAITING_ANSWERS`.  
Errors: unknown question, answers already locked, illegal status.

`submitAnswers(sessionId, submission)`  
Precondition: `QUESTIONS_READY` or `AWAITING_ANSWERS`, no submission yet, and one answer per question. Blank answers are allowed.  
Result: submission stored, status `AWAITING_ANSWERS`. A second submit with the same answers returns the lesson. A different payload is rejected.

`evaluateAnswers(sessionId)`  
Precondition: submission present and status `AWAITING_ANSWERS` or `EVALUATING`. If a review is already stored, it is returned and the model is not called.  
Result: `REVIEW_READY`, review saved, valid observations inserted, knowledge rebuilt.  
Errors: nothing submitted, transport failure, review JSON that does not cover every question. Those failures leave the submission in place and the status at `EVALUATING`.

`completeLesson(sessionId)`  
Precondition: `REVIEW_READY`, review saved, observation pass finished.  
Result: `COMPLETED`. The cursor moves to the end of the fragment. This is the only event that advances reading progress.

`getActiveLesson(bookId)`  
Result: the non-completed lesson, or null.

`getReadingProgress(bookId)`  
Result: cursor, active lesson id, completed fragment ids.

`recoverInterruptedSessions()`  
Local only. `PREPARING` with no saved lesson returns to `CREATED`. `EVALUATING` with a saved review becomes `REVIEW_READY`. `EVALUATING` without a review stays, with the submission intact.

### Offsets

A text anchor's `startCodePoint` and `endCodePoint` are a half-open range of Unicode code points in the anchor's block. The quote equals that slice. The fragment's `originalText` is the anchor quotes in order, with `\n` between anchors that belong to different blocks.

### Observation outcomes

| Type | Outcome |
| --- | --- |
| `CORRECT_UNDERSTANDING`, `SUCCESSFUL_CONTEXT_INFERENCE`, `SUCCESSFUL_ACTIVE_USAGE` | positive |
| `PARTIAL_UNDERSTANDING`, `HINT_DEPENDENT_SUCCESS` | weak |
| `SEMANTIC_MISINTERPRETATION`, `GRAMMAR_DIFFICULTY`, `UNKNOWN_VOCABULARY`, `UNSUPPORTED_TEXT_INFERENCE` | negative |

A proposal is rejected when the type and outcome disagree, the book quote is not in the fragment, the student evidence is not in the stored answer, a vocabulary or idiom label is not in the fragment, or the interpretation is blank. Structural rejection is stored on the proposal row.

### Knowledge rules

Projection ignores retracted observations and sorts by time, then sequence.

- One observation, or only weak ones, or only errors: `LEARNING`.
- Any error, and at least one positive, and fewer than two positives after the latest error: `NEEDS_REVIEW`.
- At least two positives and the latest outcome positive, without needing a clean lifetime: `LIKELY_KNOWN`.
- The latest three outcomes positive, across at least two lessons: `STABLE`.
- Evidence strength is `LOW` (one observation), `MEDIUM` (two), or `HIGH` (three or more). It is not a probability.
- Rows are keyed by language, concept or diagnostic type, and skill. Active production does not update a comprehension row.

### Context budget

The fragment text is copied in full. Preceding text is at most 400 code points. Memory text is at most 3500 characters. Rows already marked likely known or stable are included only when their label appears in the fragment, and they lose the budget first. Nothing in memory is loaded from a vector index.

### Model JSON

Prepare:

```json
{"introduction":"...","hints":["..."],"questions":[{"type":"IDIOM","prompt":"...","targetSkill":"IDIOM_COMPREHENSION","targetExpression":"雷打不动","sourceQuote":"雷打不动"}]}
```

Evaluate:

```json
{"questionReviews":[{"questionId":"q1","verdict":"INCORRECT","explanation":"...","errorNotes":"...","evidenceSufficient":true,"examples":[]}],"observations":[{"questionId":"q1","conceptLabel":"雷打不动","skillType":"IDIOM_COMPREHENSION","observationType":"SEMANTIC_MISINTERPRETATION","outcome":"NEGATIVE","studentEvidence":"...","sourceQuote":"雷打不动","interpretation":"...","confidence":"MEDIUM"}],"nextRecommendations":[]}
```

Question types: `MAIN_IDEA`, `DETAIL_LOOKUP`, `CONTEXT_VOCABULARY`, `IDIOM`, `GRAMMAR`, `INFERENCE_CHECK`, `ACTIVE_PRODUCTION`.

Book text and answers travel in the user message as data. The system message is the strategy text only.

## Data model

- **Book** — id, title, author, language (BCP-47 primary subtag), source URI, local file, SHA-256 of the EPUB bytes, EPUB metadata, imported time, removed time.
- **Book block** — id, book, spine index, href, order, NFC text, hash, chapter title.
- **Text anchor** — book, spine, href, block, code-point range, quote, quote hash.
- **Book fragment** — id, book, ordered anchors, original text, text hash, chapter, code-point length, segmentation version `paragraph-sentence-v1`.
- **Reading progress** — cursor, active lesson, completed fragment ids.
- **Lesson** — id, book, fragment, learner, status, strategy version, prepare call, evaluate call, prepared lesson, submission, review, timestamps.
- **Question** — id, lesson, type, prompt, skill, optional expression, source spans, order.
- **Draft** — lesson, question, text, time.
- **Submission** — answers, subjective difficulty, explanation request, unclear notes, free comment, hint count, time.
- **Review** — per-question verdict, explanation, error notes, evidence flag, examples, proposal ids, call id, time.
- **Observation** — the fields in the product contract, plus a monotonic sequence.
- **Retraction** — observation id, reason, time.
- **Concept** — id `language:label`, language, label.
- **Knowledge state** — derived row and the observation ids it used.
- **Preferences** — study language, explanation language, self-assessed level, difficulty, hint depth, target fragment size.
- **Provider config** — id, display name, kind, base URL, capabilities. No secret.
- **AI call** — lesson, operation, provider, model, prompt version, strategy version, token counts, raw JSON, time.

Deleting the shelf entry sets `removedAt` and deletes the local EPUB file. Blocks, fragments, lessons, and observations stay. Erasing history deletes those rows for that book, resets the cursor, and rebuilds knowledge. The file and the book row stay.

## UI / UX

The activity shipped here is a debug harness: import, settings, the lesson status, the original fragment, and the lesson actions. It is not the product UI. Layout and visual design are out of this iteration. The harness exists so the use cases can be driven on a device.

## Out of scope

Screen design, accounts, sync, shared study, voice, audio, a scheduler such as Anki, embeddings, multi-agent orchestration, official HSK or CEFR certificates, a book recommender, and a backend whose only job is to proxy the model. Contracts keep a learner id, a provider id, and a strategy version so those can be added later.

## Acceptance criteria

1. A small EPUB imports, and a second import of the same bytes returns the same book.
2. Walking fragments from the start covers every code point once. Chinese punctuation and a supplementary character are not split in half.
3. A lesson runs from create through complete on a fake model. The stored fragment text equals the extracted book text.
4. Drafts survive a new service instance. A second prepare does not call the model. A second evaluate does not add an observation.
5. Crash cases match `recoverInterruptedSessions` and the status rules above.
6. One bad quote rejects one proposal and still saves the review. A retraction removes that observation from the rebuilt projection and leaves the row.
7. The second lesson's prepare payload includes the concept recorded by the first lesson and the full fragment. Changing the model id does not delete the first call.
8. Changing the target size leaves the first fragment's hash unchanged.
9. `./gradlew test assembleRelease` succeeds.
