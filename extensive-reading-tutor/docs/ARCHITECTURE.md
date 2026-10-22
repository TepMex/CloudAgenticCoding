# Architecture

The device holds the book and the learning record. A new lesson asks the model to prepare and later to evaluate. «Объяснить сейчас» may add one narrow explain call. That call does not write knowledge and does not move the cursor. The model is not given a way to write the database.

```text
MainActivity / TutorViewModel
        |
        v
BookService     LessonService     LearnerService
        \            |              /
         v           v             v
              TutorStore  <--- Room or in-memory
                    |
                    v
              LessonAi
                    |
                    v
         Gateway + LlmProvider
                    |
                    v
         OpenAI-compatible HTTP
```

Domain types do not import Android or HTTP. Room entities are not the domain types. Prompt text is not built inside the HTTP client.

## Invariants

1. A fragment's text and anchors never change after insert.
2. Anchor quotes equal the block slice at their code-point range.
3. Completed fragments tile the book from the start with no gap and no overlap.
4. At most one non-completed lesson exists per book, and per fragment.
5. The cursor moves only in the transaction that marks a lesson `COMPLETED`.
6. A prepared lesson's questions quote the stored fragment. The model cannot replace that fragment.
7. A submission is written once. Drafts stop once it exists.
8. Observations are inserted only from a review transaction, and only when the quote and the answer check pass.
9. The same proposal hash in one lesson is stored once.
10. Knowledge rows are a function of non-retracted observations.
11. Provider secrets are not columns in the lesson database.
12. The exchange journal is written only after the API key is removed from headers and bodies. It is a file beside the provider, not a `TutorStore` table.

## State machine

```text
CREATED --begin prepare--> PREPARING --commit--> INTRO_READY
   ^                          |
   |                          +--abort / recover--> CREATED
   |
INTRO_READY --reveal--> QUESTIONS_READY --first draft or submit--> AWAITING_ANSWERS
AWAITING_ANSWERS --begin evaluate--> EVALUATING --commit review--> REVIEW_READY
EVALUATING --retry--> EVALUATING
REVIEW_READY --complete--> COMPLETED
```

`INTRO_READY` and `QUESTIONS_READY` are two views of one saved prepare result. `revealQuestions` does not call the model.

The lesson screen does not swap these statuses for exclusive pages. It keeps one feed: the fragment stays first, and the introduction, the questions with their answer fields, and the review are appended under it as the machine advances. Scrolling up shows the earlier blocks. `revealQuestions` still gates the question block. There is no separate answer page and no step counter.

## Transactions

| Step | Transaction | Network |
| --- | --- | --- |
| Import | book, blocks, initial cursor | no |
| Start lesson | fragment, `CREATED` lesson, active pointer | no |
| Begin prepare | status `PREPARING` | no |
| Model prepare | — | yes, may repeat |
| Commit prepare | call, questions, introduction, shown-hint assistance, `INTRO_READY` | no |
| Explain selection | explain record only | yes, only after «Объяснить сейчас» |
| Reveal explanation | reveal time and, before submit, an assistance event | no |
| Draft or submit | draft, or submission plus immutable selection and assistance freeze | no |
| Begin evaluate | status `EVALUATING` | no |
| Model review | — | yes, may repeat |
| Commit review | call, review, proposals, observations, knowledge, `REVIEW_READY` | no |
| Complete | `COMPLETED`, cursor, completed range, next target size | no |

Commit transactions roll back entirely. A duplicate commit hits the unique review or the unique proposal hash and keeps the first write.

## Recovery

| Died during | Restored state |
| --- | --- |
| `PREPARING`, lesson not saved | `CREATED`. Another prepare may call the model again. |
| Prepare response not committed | Same as above. The lost response is not reconstructed. |
| Drafts, before submit | `AWAITING_ANSWERS` or `QUESTIONS_READY`, drafts present. |
| After submit, before evaluate | `AWAITING_ANSWERS`, submission present, answers locked. |
| `EVALUATING`, review not saved | Stays `EVALUATING`. Submission remains. Retry may call the model again. |
| Review committed | `REVIEW_READY`. Retry does not call the model and does not add observations. |

## Project layout

```text
app/src/main/java/com/tepmex/extensivereadingtutor/
  domain/        model, errors, state machine
  books/         EPUB, anchors, fragments, import
  lessons/       lesson use cases
  learner/       observation policy, projection
  pedagogy/      strategy, prompts, context
  llm/           gateway, OpenAI adapter, JSON
  storage/       store port, in-memory, Room
  security/      keystore envelope
  ui/            debug harness
```

## Risks

- The EPUB extractor does not understand every publisher's markup. The original file is kept so a better extractor can replace it behind `EpubTextExtractor`.
- A retry can produce a different review than the lost response. The app keeps the one that committed.
- v2 knowledge rules are versioned heuristics. They are easy to recompute and easy to replace without rewriting the ledger. Passing the unit tests does not show that a live model teaches well.
- The debug screen will be thrown away. Use cases are the API that the next UI should call.
