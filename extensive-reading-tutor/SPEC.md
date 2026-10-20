# Extensive Reading Tutor — SPEC

## Purpose

A local-first Russian-language tutor for extensive reading in Chinese. A learner keeps a small library, reads a short authentic fragment, answers a handful of questions, and gets a calm review — meaning, words, and grammar — without turning the book into a drill deck. Optional grading and lesson authoring go to whatever OpenAI-compatible Chat Completions endpoint the learner configures. Books, notes, and the API key stay in the browser.

## Requirements

1. Library of books with filters Читаю / Завершено / Все, progress, and last session.
2. Book screen with progress, a reading map, chapter list, notes, and bookmarks.
3. Lesson in four phases: text and intro, questions, answer review, scored разбор.
4. Questions filter by смысл, слова, and грамматика. Multiple choice and open answers.
5. Tappable words open a card with pinyin, gloss, example, and speech when the device has a Chinese voice.
6. Notes and bookmarks can be added during a lesson and listed on the book.
7. Pedagogical memory: words and expressions, grammar, skills, with statuses Нужно повторить / В процессе / Знаю.
8. Profile with library totals and an activity grid.
9. Settings for an OpenAI-compatible provider: base URL, API key, model id, plus learning preferences (HSK, explanation language, difficulty, depth, two practice toggles).
10. Connection test calls `POST {base}/v1/chat/completions` (the `/v1` segment is not doubled if the user already included it).
11. With a configured endpoint, adding a passage asks the model for a lesson JSON; submitting answers asks for a review JSON. One repair retry if the body is not JSON. If the call fails, the app keeps a local lesson or a local review and says so.
12. Without an endpoint, the sample library and a generic question template still run entirely on device.
13. Learner data persists in `localStorage`. Reset clears it.

## Interfaces

- **UI**: Russian, mobile column (full width on a phone, centered frame on a wide screen). Routes: library, study, book, lesson phases 1–4, knowledge, settings, profile. Hash URLs (`#/library`, `#/lesson/book-tgcf/2`, …).
- **LLM**: browser `fetch` to an OpenAI-compatible Chat Completions API. Request body: `model`, `messages`, `temperature` (omitted and retried once if the provider rejects temperature). Auth: `Authorization: Bearer <key>` when a key is set. No backend proxy.
- **Storage**: `localStorage` key `extensive-reading-tutor-v1`. No accounts.

## Data model

- **Book** — title, author, cover tone, reading/done, progress, chapters, recent sessions, per-book counts.
- **Lesson** — intro, hints, tokens (text + optional gloss id), questions, key moments.
- **Gloss** — hanzi, pinyin, Russian gloss, kind (word / idiom / grammar), example, status, last seen.
- **Note** — note or bookmark, tied to a book.
- **Skill** — title, detail, status.
- **Attempt** — phase, answers, difficulty rating, comment, review, whether stats were already credited.
- **Settings** — `apiBaseUrl`, `apiKey`, `model`, HSK, explanation language, difficulty, depth, two booleans.
- **Activity** — map of `YYYY-MM-DD` to light reading (1) or a lesson (2).

## UI / UX

Match the attached phone mockup: white surfaces, iOS-like grouped cards, blue primary actions, green / amber / red for верно / частично / нужно внимание, a four-item tab bar (Книги, Учёба, Словарь, Профиль). Lesson and book screens replace the tab bar with a back button. No fake status-bar clock. Sample covers are original color plates with the title, not publisher artwork.

## Out of scope

Accounts, sync, a backend, EPUB/PDF import, a full dictionary, spaced-repetition scheduling, TTS audio files, Android packaging, streaming responses.

## Acceptance criteria

1. Library filters and the sample books render; opening 天官赐福 shows progress, map, chapters, notes, bookmarks.
2. Продолжить walks phases 1 → 4 offline, including word cards, answer editing, and a local review whose counts match the answers.
3. Knowledge filters and status badges render; profile shows totals and the activity grid.
4. Settings save an arbitrary base URL and model id; the chat URL normalizes with and without a trailing `/v1`.
5. `bun test` covers URL normalization, JSON extraction, local grading, lesson parsing, routes, and the reading map.
6. `bun run build` emits `dist/index.html`.
