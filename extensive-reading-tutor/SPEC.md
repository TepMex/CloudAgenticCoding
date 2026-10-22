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
11. Knowledge state is recomputed from the ledger. Lessons stored as `strategy-v1` keep the rules in `docs/adr/0006-recomputable-knowledge.md`. New lessons use `strategy-v2` and `docs/adr/0007-evidence-not-mastery-labels.md`: evidence units, assistance, and the versioned `policy-v2` thresholds.
12. Prepare receives the full fragment, a short preceding tail, preferences, and a memory packet capped at 8 KiB of serialized JSON UTF-8 bytes. Evaluate receives the task, the answers, the introduction, the help that was shown, and frozen selections. It does not receive mastery labels.
13. The learner can switch provider and model. Old calls keep the provider and model they used.
14. API keys are encrypted with a keystore key. They are not logged, not placed in lesson tables, and not part of a history export.
15. Removing a book from the shelf and erasing its learning history are different operations.
16. Domain logic is covered by JVM tests with a fake model. Room is the on-device store.
17. Connection settings include an optional JSON object and a request timeout. The object is merged into each Chat Completions body for provider fields such as reasoning effort. It must be a JSON object and must not contain `model` or `messages`. The timeout is a whole number of seconds from 1 to 1800 and applies to lesson calls and the connection check. The default is 60. Invalid JSON or an out-of-range timeout is not saved. A call with invalid extra JSON is not sent.
18. Every model attempt, including a retry and the connection check, is appended to a local journal of the newest 40 exchanges. A row shows the operation, time, duration, HTTP status, and token counts when the response includes `usage`. The learner can open the request or the response and read pretty-printed headers and the body. JSON is syntax-colored. The API key is replaced with `[redacted]` before the journal is written. The journal is a file, not a lesson-database table, and it is not a history export.

## Interfaces

| Interface | Detail |
| --- | --- |
| Launcher | `com.tepmex.extensivereadingtutor.MainActivity` |
| Open EPUB | `ACTION_OPEN_DOCUMENT` and `ACTION_VIEW` for `application/epub+zip` |
| Lesson API | `LessonService` methods below |
| Model | `LessonAi.prepareLesson`, `LessonAi.evaluateAnswers`, and `LessonAi.explainSelection` when the learner asks to explain a selection now |
| HTTP | `LlmProvider.generate` via OpenAI-compatible `chat/completions` |
| Database | Room `tutor.db`, schema version 2. Migration 1→2 adds `pedagogy_documents` and does not drop lesson rows. |
| Settings | DataStore `tutor_settings` for base URL, model name, extra JSON, and timeout seconds |
| Journal | `LlmExchangeLog` file `llm-exchanges.json`, newest 40 |
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
Errors: nothing submitted, transport failure, truncated JSON, review JSON that does not cover every question or every frozen selection. A coverage or truncation failure does not commit a partial review. Transport failure leaves the submission in place. For a v2 lesson the status stays `EVALUATING` only after the evaluate call has started; a budget failure before that call leaves the submission and the draft in place.

`completeLesson(sessionId)`  
Precondition: `REVIEW_READY`, review saved, observation pass finished.  
Result: `COMPLETED`. The cursor moves to the end of the fragment. This is the only event that advances reading progress.

`getActiveLesson(bookId)`  
Result: the non-completed lesson, or null.

`getReadingProgress(bookId)`  
Result: cursor, active lesson id, completed fragment ids.

`recoverInterruptedSessions()`  
Local only. `PREPARING` with no saved lesson returns to `CREATED`. `EVALUATING` with a saved review becomes `REVIEW_READY`. `EVALUATING` without a review stays, with the submission intact.

`markSelection` / `markUtf16` / `updateSelection` / `deleteSelection`  
A selection is an app-owned range inside the current fragment. `WITH_REVIEW` is the default and does not call the model. `NOW` calls `explainSelection` only when the learner asks. UTF-16 offsets snap off surrogate pairs before they become code-point anchors. A range longer than 480 code points is rejected and is not truncated. Selections made before submit are frozen with the submission. A later `NOW` mark does not change that freeze.

`explainSelection` / `revealExplanation`  
The explain call does not change lesson status or the cursor. Saving the response is not a reveal. A reveal before submit records help. A transport failure records no help. Repeating the same selection revision returns the stored response. A changed revision does not reuse the old response.

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

v1 lessons still use the observation counts in `docs/adr/0006-recomputable-knowledge.md`. v2 projection ignores retracted rows and collapses observations that share an attempt, a concept or diagnostic key, and a skill into one evidence unit. Raw success and error counts stay on the row and do not raise status.

- One independent positive: `LEARNING`.
- Two independent positives in two contexts, with two of them after the latest independent error: `LIKELY_KNOWN`.
- Three independent positives, three contexts, two study days, and at least seven days between the first and last of those successes, with the last three diagnostic units positive: `STABLE`. These are `policy-v2` starting values, not a validated memory model.
- An error after a success: `NEEDS_REVIEW`. Errors alone: `LEARNING` with the reason «пока затрудняется».
- A self-report does not by itself remove `STABLE`. Help does not lower a row by itself. Missing data is an absent row, not «не знает».
- Study days use the saved timezone. Legacy rows without one do not invent a zone and cannot satisfy the day count.
- Rows are keyed by learner, language, concept or diagnostic key, skill, and sense scope. `CONTEXTUAL_ONLY` does not mean every sense of the word. Active production does not update a comprehension row.

### Context budget

The fragment text is copied in full. Preceding text is at most 400 code points. Prepare memory is at most 8 KiB of UTF-8 JSON, counted in bytes. The builder also estimates the whole request against the provider window, reserves output tokens, and keeps a safety margin. When the tokenizer is unknown the estimate is UTF-8 bytes divided by four, and the report says the estimate is uncertain. The fallback window is 16384 tokens: the v2 system prompt, schema, and output reserve do not fit in 8192 under that estimate. A provider that advertises a smaller window still gets an explicit error instead of a trimmed fragment. Optional memory is dropped before preceding context. If the fragment itself does not fit, prepare returns an error. Frozen selections are mandatory on evaluate and are not trimmed to fit. Nothing in memory is loaded from a vector index. Book continuity notes are off unless `continuityNotesEnabled` is set; the preceding text is the continuity that is sent.

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

v1 JSON above is what already-prepared `strategy-v1` lessons still parse. New lessons ask for `prepare-v2`, `evaluate-v2`, and `explain-selection-v1`. Those schemas live in `pedagogy/JsonSchema.kt` and are appended to the system message. The user message is only the serialized input. Default prepare size is 1–3 questions and 0–2 hints. A pre-answer hint cannot be `ANSWER_GIVEN`. Every question review needs a verdict and a non-empty explanation. Verdicts shown in the UI are «Верно», «Частично верно», «Неверно», «Нет ответа», and «Нельзя надёжно оценить».

Book text and answers travel in the user message as data. The system message is the versioned prompt pack plus the schema.

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
- **Concept** — v1 id `language:label` stays as stored. New lexical and idiom ids are `language:kind:nfcLabel` with sense scope `CONTEXTUAL_ONLY`. Grammar ids come from the local catalogue, such as `zh.grammar.ba_disposal`.
- **Knowledge state** — derived row and the observation ids it used.
- **Preferences** — study language, explanation language, self-assessed level, difficulty, hint depth, target fragment size.
- **Provider config** — id, display name, kind, base URL, capabilities. No secret.
- **AI call** — lesson, operation, provider, model, prompt version, strategy version, token counts, raw JSON, time.
- **Model exchange** — one HTTP attempt: purpose, model, URL, start, finish, duration, HTTP status, failure kind, whether it was sent, redacted request headers, request body, redacted response headers, response body, token counts. Stored in `llm-exchanges.json`, newest 40. Not in `tutor.db`.

Deleting the shelf entry sets `removedAt` and deletes the local EPUB file. Blocks, fragments, lessons, and observations stay. Erasing history deletes those rows for that book, resets the cursor, and rebuilds knowledge. The file and the book row stay.

## UI / UX

The launcher is the phone UI. A bottom bar switches Книги, Учёба, Словарь, and Профиль. Book and lesson screens have a back control and hide the bar. There is no second web app.

1. **Книги** lists EPUB still on the shelf. Filters are Читаю, Завершено, and Все. A book is finished when the cursor is past the last block. Progress is code points read over the book. The + button imports a DRM-free EPUB. The shelf is empty until that import.
2. **Book** shows the cover, percent, Продолжить, chapter marks from block titles and the cursor, and recent lessons. Убрать с полки calls `removeFromLibrary` and does not erase history.
3. **Lesson** is one downward feed on the existing state machine. Earlier blocks stay on screen; the learner scrolls up to reread them, including the original fragment. The fragment is the first block from `CREATED` onward. The learner can drag any range, including across blocks, and mark «Не понимаю». The default timing is «Разобрать вместе с ответами». «Объяснить сейчас» is a separate action. Подготовить calls `prepareLesson` and appends the introduction and hints under the fragment. К вопросам calls `revealQuestions` and appends each question with its free-text answer, the unclear-notes field, and «Нужно подробнее». Отправить calls `submitAnswers` and `evaluateAnswers`. After submit the answers stay visible and locked, and the review appends below: a verdict and explanation for every answer, a nuance only when one was returned, production feedback separately, and one review per frozen selection. A new mark after submit does not change that freeze. Дальше по книге calls `completeLesson`, which is still the only cursor move. There is no step counter and no screen that replaces the fragment.
4. **Word sheet** still opens from a tap on a Han run or a knowledge label. Dragging a range is the way to mark a difficulty. The sheet is not a dictionary.
5. **Учёба** resumes the current book and lists knowledge in `NEEDS_REVIEW`.
6. **Словарь** is the knowledge projection: words, `GRAMMAR_COMPREHENSION` rows, and skill counts, with search.
7. **Профиль** counts shelf and ledger figures. The activity grid uses completed-lesson days and observation days.
8. **Настройки** edits the OpenAI-compatible base URL, the keystore key, the model name, the timeout in seconds, and a text field of extra Chat Completions JSON. It also saves `LearnerPreferences`: self-assessed level, explanation language, difficulty, hint depth, and target fragment size. Toggles that are not preferences are not shown. Журнал запросов opens from this screen.
9. **Журнал запросов** lists model attempts, newest first. A row shows the operation, time, duration, status, and token counts. The row opens one exchange. Запрос and Ответ each open a raw screen: HTTP headers and the body, both pretty-printed JSON when the text is JSON, with syntax coloring. The Authorization value is `[redacted]`. Очистить журнал deletes the file.

## Out of scope

Accounts, sync, shared study, voice, audio, a scheduler such as Anki, embeddings, multi-agent orchestration, official HSK or CEFR certificates, a book recommender, a separate notes store, and a backend whose only job is to proxy the model. Contracts keep a learner id, a provider id, and a strategy version so those can be added later.

## Acceptance criteria

1. A small EPUB imports, and a second import of the same bytes returns the same book.
2. Walking fragments from the start covers every code point once. Chinese punctuation and a supplementary character are not split in half.
3. A lesson runs from create through complete on a fake model. The stored fragment text equals the extracted book text.
4. Drafts survive a new service instance. A second prepare does not call the model. A second evaluate does not add an observation.
5. Crash cases match `recoverInterruptedSessions` and the status rules above.
6. One bad quote rejects one proposal and still saves the review. A retraction removes that observation from the rebuilt projection and leaves the row.
7. The second lesson's prepare payload includes the full next fragment. A concept from the previous lesson is included only when its label is relevant to that fragment. Changing the model id does not delete the first call.
8. Changing the target size leaves the first fragment's hash unchanged.
9. `./gradlew test assembleRelease` succeeds.
10. A custom JSON object is merged into the chat body, including nested fields. An extra `messages` key does not call the network. The journal stores that attempt with `Authorization` redacted and keeps token counts from `usage`. A timeout typed in settings is the timeout passed to HTTP.
11. Pretty-printed JSON uses separate colors for keys, strings, numbers, and `true` / `false` / `null`.
