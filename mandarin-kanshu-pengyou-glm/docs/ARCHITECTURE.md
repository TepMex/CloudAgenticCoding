# Architecture

## Layered, feature-oriented structure

```
src/
  app/            UI shell, routing, Zustand UI store, views
  db/             Dexie (IndexedDB) schema + repositories (source of truth)
  reader/
    epub-adapter/ JSZip + DOMPurify behind EpubRendererAdapter seam
    selection/    Chinese sentence-boundary expansion (pure)
    locations/    ReaderLocation serialization + quote-based recovery (pure)
  books/          EPUB import / library / chapter listing
  explain/        Explain service (Level 1/2/3 + recursive + fresh)
  assessment/     Understand service (scoring, follow-ups, attempts)
  memory/         Structured memory: patches, merge, debug view, chapter cleanup
  companion/      Isolated companion reaction service
  providers/      OpenAI-compatible client, defensive JSON, repair, secrets, prompts/schemas
  statistics/     First-attempt statistics
  offline/        Service-worker registration + update prompt
  shared/         Domain types + pure utils (uuid, redactKeys, approxTokens)
```

## Seams (enforced boundaries)

- **EPUB renderer** → `EpubRendererAdapter` interface. Reader/persistence never import JSZip or iframe internals.
- **Persistence** → Dexie repositories in `db/database.ts`. React components never call Dexie directly except through service modules.
- **Provider client** → `ProviderClient` + `ProviderClientOptions`. Domain logic never builds OpenAI payloads directly.
- **Prompt construction** → `providers/prompts.ts` (7 separate prompts). Each task has its own system prompt + zod schema + JSON schema.
- **Response validation** → `providers/parse.ts` (extractJson, validateWithSchema) + one repair attempt in `ProviderClient.structured`.
- **Memory merging** → `memory/service.ts` (`applyMemoryPatch`) — transactional, validated.
- **UI state** → Zustand store (`app/store.ts`) for transient UI only; IndexedDB is the source of truth.

## Request lifecycle

All LLM requests go through `ProviderClient.chat` (or `.structured`), which uses `AbortController` for cancellation. On cancellation: stop the request, do not create an annotation, do not save partial output, do not update memory. No streaming. One automatic repair attempt for malformed structured responses; no infinite retry loops.

## Memory-update policy

- Immediate update when a likely-new named term appears (`shouldUpdateMemoryImmediately` heuristic).
- Otherwise queue candidates locally; consolidate after ~3 successful interactions or on chapter exit.
- Never update after a cancelled/failed/malformed visible request.
- Memory updater never receives learner answers, simplifications, companion predictions, or malformed responses.
- On chapter exit: replace detailed current-chapter events with one concise summary; retain persistent entities + unresolved threads; delete raw LLM responses and transient cache.

## Chapter transition cleanup

`onLeaveChapter` → `clearTransientChapterCache` + `summarizeChapter`. Visible annotations, explanations, and assessments are retained.

## Offline

Service worker (vite-plugin-pwa) precaches the app shell + assets. `navigator.onLine` toggles the `online` flag; LLM action buttons are disabled when offline. Reading, navigation, position restore, annotations, memory, typography, and deletion all work offline. SW updates use `registerType: "prompt"` — the `UpdatePrompt` component shows "Update available" and reloads only after explicit user action.