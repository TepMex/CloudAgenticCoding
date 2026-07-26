# Mandarin Kanshu Pengyou — SPEC

## Purpose

A local-first web reading companion for intermediate Chinese learners (≈HSK 4) reading native DRM-free EPUB fiction. It helps the learner keep reading without leaving the book: explain difficult passages in simpler Chinese, assess comprehension in the learner’s native language, and maintain structured book memory — all on-device except optional LLM calls.

## Requirements

1. Import and store multiple DRM-free EPUB books in IndexedDB; complete files stay on device.
2. Continuous vertical-scroll reading, one chapter at a time, with prev/next prefetch and position restore.
3. Configurable typography (font size, line height, content width) and light/dark appearance.
4. Character-level selection with sentence-boundary expansion, Explain / Understand actions, and distinct manual vs expanded highlights.
5. Three-level Chinese simplification (never translation), recursive up to Level 3, with fresh restart and optional fallback model.
6. Native-language comprehension assessment (configurable language; Russian default), adaptive follow-ups (1–3), five-level scoring, first-attempt statistics.
7. Structured book memory with confidence, patches, chapter summarization, and readable debug view.
8. Optional companion reaction isolated from grading and memory.
9. Multiple OpenAI-compatible provider profiles, task assignments, connection testing, defensive JSON parsing, one repair attempt, cancellable requests.
10. Session-only API keys by default; persistent only with opt-in; forget key; strict CSP; no key leakage.
11. Offline reading of books and past assistance; LLM actions disabled offline; service-worker updates require explicit reload.
12. Minimal local statistics; cozy reading UI; accessibility basics.

## Interfaces

- **UI**: Library, Reader (main + assistance panel/sheet), Settings (providers, typography, language), Memory debug, Statistics.
- **LLM**: OpenAI-compatible Chat Completions over HTTPS from the browser (CORS required).
- **Storage**: IndexedDB (Dexie). No backend, cloud, or accounts.
- **EPUB**: Local file import only; sanitized HTML rendered via renderer adapter (iframe isolation).

## Data model

See `docs/SCHEMA.md` and `src/shared/domain.ts`. Key entities: Book, Chapter, ReadingPosition, Annotation, Explanation, AssessmentAttempt, BookMemory, ProviderProfile, Settings.

## UI / UX

Cozy paper/charcoal reading tool — not chat or SaaS. Original text dominates; assistance in quiet side panel (desktop) or bottom sheet (mobile). Soft assessment labels; no confetti/streaks/avatars.

## Out of scope (v1)

Accounts, cloud sync, backend proxy, dictionary/pinyin/translation, flashcards/Anki, TTS, DRM/PDF/MOBI, streaming LLM, PWA install onboarding, backup import/export, analytics, arbitrary provider headers.

## Acceptance criteria

All 55 items in the product prompt §28 must work for the MVP. Automated unit/integration tests cover core pure logic; Chromium E2E covers the primary workflow with mocked LLM.
