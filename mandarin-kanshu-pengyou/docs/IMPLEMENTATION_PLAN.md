# Implementation plan

## Proposed libraries

| Concern | Choice |
| --- | --- |
| UI | React 19 + TypeScript + Vite |
| Package / scripts / tests | Bun |
| Persistence | Dexie (IndexedDB) |
| Transient UI state | Zustand |
| Schema / validation | Zod |
| EPUB parse | JSZip + custom OPF/NCX/nav parser |
| HTML sanitize | DOMPurify |
| EPUB render | Custom iframe adapter (style isolation) |
| Offline / assets | vite-plugin-pwa (Workbox), skipWaiting deferred |
| E2E | Playwright (Chromium), mocked fetch |

## Data model

IndexedDB stores listed in `docs/SCHEMA.md`. Domain types in `src/shared/domain.ts`. UI store holds only ephemeral selection, panel open state, offline flag, pending request handles.

## EPUB renderer strategy

1. Parse container → OPF spine + nav TOC defensively.
2. Store raw chapter XHTML blobs; sanitize on render.
3. Render **one** active spine item in an iframe with a reader stylesheet override.
4. Prefetch adjacent chapters into memory/cache, not into the active DOM.
5. Locations use `ReaderLocation` (CFI optional + quote/prefix/suffix fallback).

## Selection strategy

Browser native selection → capture character offsets within chapter text → expand to `。！？` sentence boundaries across paragraphs → learner can narrow before submit → dual highlight (strong manual / faint expanded).

## Provider abstraction

`providers/client.ts` speaks Chat Completions only. Profiles + secrets + task assignments in Dexie. Components call domain services (`explain/`, `assessment/`, `memory/`), never `fetch` directly. No silent provider switching.

## Prompt and validation strategy

Separate system prompts + Zod schemas for: simplify, assess, follow-up assess, memory patch, initial extract, companion, repair. Book content wrapped in untrusted delimiters. One repair attempt on invalid JSON; expose raw on failure.

## Security boundaries

- Strict CSP (no remote scripts/fonts/CDNs).
- API keys session-first; persistent opt-in; redaction helpers.
- EPUB HTML sanitized; never executed as app code.
- Prompt-injection resistant system instructions.
- Arbitrary endpoints allowed but CORS-tested; warn about data sent.

## Testing strategy

- **Unit** (`bun test`): selection expansion, locations, JSON extract, memory merge, stats, redaction, cache cleanup.
- **Integration**: import fixture EPUB, position restore, flows with mock provider, cancel, repair.
- **E2E**: Playwright Chromium happy path with mock LLM.

## Major risks

1. Imperfect EPUB structure → defensive nav/spine fallbacks.
2. CORS on learner endpoints → clear Test connection UX.
3. Weak models failing JSON → repair + recoverable error cards.
4. Memory drift / spoilers → confidence tags, no learner answers/simplifications in memory updater.
5. SW activating mid-answer → require explicit update reload.
