# IndexedDB schema

Database name: `mandarin-kanshu-pengyou-glm` (Dexie v1).

| Store | Key | Indexes | Purpose |
|---|---|---|---|
| `books` | `id` | `addedAt`, `lastOpenedAt` | Local book metadata |
| `bookFiles` | `bookId` | — | Raw EPUB Blob (full book on device) |
| `chapters` | `id` (`bookId:spineId`) | `bookId`, `index` | Spine items / chapter list |
| `readingPositions` | `bookId` | — | Last reading position per book |
| `annotations` | `id` | `bookId` | Logical annotations (location + passage) |
| `explanations` | `id` | `annotationId`, `bookId`, `level` | Simplification rewrites |
| `assessmentAttempts` | `id` | `annotationId`, `bookId`, `createdAt` | One per passage; first = headline |
| `assessmentAnswers` | `id` | `attemptId`, `createdAt` | Initial + follow-up answers |
| `providerProfiles` | `id` | — | OpenAI-compatible provider configs |
| `providerSecrets` | `id` | `endpointHint` | Key metadata (only when "Remember" on) |
| `taskModelAssignments` | `bookId` (or `_global`) | — | Per-task profile assignment |
| `bookMemory` | `bookId` | — | Structured canonical memory |
| `memoryRevisions` | `id` | `bookId`, `revision` | Last ~10 memory snapshots |
| `pendingMemoryCandidates` | `id` | `bookId`, `createdAt` | Queued memory candidates |
| `settings` | `key` | — | App settings (key `app`) |
| `requestUsage` | `id` | `profileId`, `at` | Token usage per request |
| `transientChapterCache` | `id` | `bookId`, `chapterId`, `createdAt` | Raw technical LLM responses (deleted on chapter exit) |

## ReaderLocation (stored on every annotation)

```ts
{ bookId, spineItemId, epubCfi?, textQuote, prefix, suffix, approximateProgress? }
```

Quote + prefix/suffix matching is the fallback when a precise CFI cannot be restored (see `src/reader/locations`).

## Deletion

`deleteBook(bookId)` runs a transaction that deletes every related record across all stores.