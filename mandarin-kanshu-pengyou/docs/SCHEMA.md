# IndexedDB schema

Database name: `mandarin-kanshu-pengyou` (Dexie v1).

| Store | Key | Purpose |
| --- | --- | --- |
| `books` | `id` | Metadata, spine ids, titles |
| `bookFiles` | `bookId` | Original EPUB blob |
| `chapters` | `id` | Parsed chapter HTML + plain text |
| `readingPositions` | `bookId` | Last spine + scroll ratio |
| `annotations` | `id` | Logical markers (explain/understand/companion) |
| `explanations` | `id` | Simplification levels tied to annotation |
| `assessmentAttempts` | `id` | Comprehension attempts |
| `assessmentAnswers` | `id` | Answers + structured assessments |
| `providerProfiles` | `id` | Named OpenAI-compatible profiles |
| `providerSecrets` | `reference` | Opt-in persisted API keys (deduped) |
| `taskModelAssignments` | `id` | explain/assess/memory/fallback profile ids |
| `bookMemory` | `bookId` | Structured canonical memory |
| `memoryRevisions` | `id` | Recent snapshots for debug/rollback |
| `pendingMemoryCandidates` | `id` | Queued passages awaiting consolidate |
| `settings` | `id` | Typography, language, appearance |
| `requestUsage` | `id` | Token usage metadata when available |
| `transientChapterCache` | `id` | Raw LLM responses — cleared on chapter leave |
| `companionReactions` | `id` | Optional friend-style reactions |
| `readingSessions` | `id` | Minimal session tracking for stats |

Transactions: book delete, memory merge, chapter leave cleanup, and assessment submit use Dexie transactions across related tables.

Session API keys live in memory (`providers/secrets.ts`), not IndexedDB, unless the learner opts into Remember.
