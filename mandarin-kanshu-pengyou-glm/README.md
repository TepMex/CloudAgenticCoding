# 看书朋友 · Mandarin Reading Companion (GLM)

A local-first web app for intermediate Chinese learners reading native DRM-free EPUB books. It is **not** a dictionary, translator, flashcard system, or chatbot — it helps you keep reading difficult native Chinese fiction without leaving the book.

Built for an ~HSK 4 learner who reads modern Chinese web novels and answers comprehension questions in Russian (learner language is configurable).

## Quick start

```bash
bun install
bun run dev        # http://localhost:5173
bun run test       # unit + integration tests (mock LLM, no paid calls)
bun run build      # production build → dist/
bun run preview    # preview the production build
```

No backend. No accounts. No cloud. The complete EPUB stays on your device; only selected passages, nearby context, compact book memory, and your answers are sent to a configured OpenAI-compatible LLM endpoint.

## What it does

- **Import & store multiple DRM-free EPUBs** locally (IndexedDB). Reopen offline.
- **Reader**: continuous vertical scroll, one chapter at a time, prev/next prefetch, position restore, configurable font size / line height / content width, light & dark cozy themes, reader-first stylesheet that overrides publisher formatting, native copy/paste and selection preserved.
- **Selection**: character-level selection inside the book; expand to Chinese sentence boundaries (`。！？`); cross paragraphs; strong highlight for manual selection, faint for expanded passage; floating toolbar with `Explain` / `Understand` / copy.
- **Explain** (3 levels): Level 1 easier rewrite → Level 2 explicit rewrite → Level 3 minimal meaning. Recursive simplification; max 3 levels; "Try a fresh explanation" restarts from the original (uses the configured fallback model). Compares original and simplified side by side on large screens.
- **Understand**: write an open-ended explanation in your native language; receive a 0–4 comprehension score with friendly labels (Missed / Emerging / Main idea / Strong / Deep); 1–3 adaptive follow-up questions in simple Chinese with an optional native-language reveal; "Continue reading" anytime.
- **Book memory**: structured canonical memory (characters, aliases, places, organizations, terms, current-chapter events, completed-chapter summaries, unresolved threads) with confidence levels; readable debug view; memory patches merged transactionally; chapter events compressed into summaries on chapter exit; simplifications and learner answers never become canonical memory.
- **Companion reaction** (optional): a quiet, isolated "friend discussing the book" card with emotion/suspicion/speculation — predictions never become facts, never stored in memory, never affect grading.
- **Provider profiles**: multiple named OpenAI-compatible profiles; per-task assignment (Explain / Assess / Memory / Fallback); `Test connection` reports CORS, auth, JSON/structured-output support, token usage; defensive JSON extraction + one repair attempt; raw response shown on failure.
- **API keys**: session-only by default; explicit "Remember API keys" opt-in; one-click "Forget"; never in logs/errors/exports/URLs/debug views.
- **Offline**: read, navigate, restore position, inspect annotations/memory, change typography, delete books — all without network. LLM actions disabled clearly while offline. Service-worker updates prompt before activating.
- **Statistics**: comprehension attempts, first-attempt score distribution, average assistance level, unassisted vs assisted, passages explained, reading sessions, book progress.

## Architecture

See [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md). IndexedDB schema: [`docs/SCHEMA.md`](docs/SCHEMA.md). Domain types: [`src/shared/domain.ts`](src/shared/domain.ts). Provider profiles: [`docs/PROVIDERS.md`](docs/PROVIDERS.md). Prompt templates & output schemas: [`docs/PROMPTS.md`](docs/PROMPTS.md). Threat model: [`docs/THREAT_MODEL.md`](docs/THREAT_MODEL.md). Known limitations: [`docs/LIMITATIONS.md`](docs/LIMITATIONS.md).

## Testing

```bash
bun run test
```

Unit tests cover sentence-boundary expansion, multi-paragraph selection normalization, reader-location serialization, quote-based annotation recovery, structured-output parsing, fenced JSON extraction, schema validation, memory-patch merging, confidence handling, assistance-level tracking, API-key redaction. Integration tests cover EPUB import, chapter text extraction, adjacent chapters (using a mock EPUB fixture in `tests/fixtures/`). All LLM calls are mocked — no real paid API calls are required.

## Non-goals (MVP)

Accounts, cloud sync, backend proxy, social features, dictionary lookup, pinyin, direct translation, vocabulary lists, flashcards, Anki export, TTS, speech recognition, voice answers, DRM, PDF, MOBI, whole-book preprocessing, arbitrary provider headers, streaming, custom pricing, automatic model routing, mobile-first, full-text annotation search, EPUB editing, backup import/export, third-party analytics, PWA install onboarding.

## License

Private/local.