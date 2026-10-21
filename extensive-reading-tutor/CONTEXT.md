# Extensive Reading Tutor

A local-first tutor that walks one learner through an imported book in short original fragments, keeps the lesson record, and stores what the learner showed they understood.

## Language

**Book**:
A work the learner imported, identified by the bytes of its file.
_Avoid_: Title, document

**Book block**:
One paragraph of the book in spine order, after markup is removed and the text is normalized.
_Avoid_: Page, screen

**Text anchor**:
A half-open range of Unicode code points inside one book block, plus the exact quote of that range.
_Avoid_: Page number, location, CFI

**Book fragment**:
An immutable span of one or more anchors chosen for a single lesson, together with the original text of that span.
_Avoid_: Page, chunk, summary

**Text cursor**:
The first code point of the book that no completed lesson has covered.
_Avoid_: Bookmark, page index

**Reading progress**:
The cursor, the active lesson, and the list of fragments whose lessons are completed.
_Avoid_: Percent read, scroll position

**Lesson**:
One pass over one fragment: introduction, questions, the learner's answers, and the review. The app owns the lesson; the model only proposes content.
_Avoid_: Chat, conversation, turn

**Lesson status**:
The step the lesson is allowed to be in. Only the app changes it.
_Avoid_: Screen, phase number

**Prepared lesson**:
The introduction, hints, and questions saved for a lesson. The book fragment is not part of this object.
_Avoid_: Prompt, completion

**Question**:
One task tied to a quote in the fragment.
_Avoid_: Item, card

**Submission**:
The learner's locked answers and optional notes about difficulty. Drafts are not a submission.
_Avoid_: Message, chat reply

**Review**:
The saved check of one submission: a verdict and explanation per question, plus the ids of observation proposals.
_Avoid_: Grade, score

**Observation**:
One claim about what the learner did in one lesson, kept with the answer and the book quote it rests on.
_Avoid_: Level, skill score, memory summary

**Retraction**:
A later note that an observation should not be trusted, without deleting it.
_Avoid_: Edit, undo of history

**Concept**:
A stable label for an expression, pattern, or reading habit that observations can point at.
_Avoid_: Dictionary entry, word id

**Knowledge state**:
The current reading of one concept and one skill, recomputed from observations.
_Avoid_: Truth, probability, level

**Strategy**:
The versioned rules and prompt templates that tell the model how to teach. It is not the lesson history.
_Avoid_: Persona, system prompt dump

**Local learner**:
The single learner stored on this device in the first version.
_Avoid_: Account, user profile

**Model exchange**:
One HTTP attempt to the model, kept with its time, token counts, and the redacted request and response.
_Avoid_: Lesson, chat turn

**Extra fields**:
A JSON object the learner adds to the Chat Completions body, for options such as reasoning effort. It does not replace the lesson messages.
_Avoid_: System prompt, provider SDK
