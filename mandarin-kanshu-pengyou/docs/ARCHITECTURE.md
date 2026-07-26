# Architecture

Feature-oriented layout under `src/`:

| Area | Responsibility |
| --- | --- |
| `app/` | Transient UI store (Zustand) |
| `db/` | Dexie schema + defaults |
| `books/` | Import, list, delete |
| `reader/` | EPUB adapter, selection, locations, annotations UX |
| `explain/` | Simplification service |
| `assessment/` | Understand flow + persistence |
| `memory/` | Patch merge, consolidate, chapter leave |
| `companion/` | Isolated friend-style reactions |
| `providers/` | Chat client, JSON parse, structured+repair, prompts, schemas, secrets |
| `settings/` | Profiles, typography, language |
| `statistics/` | First-attempt headline stats |
| `offline/` | SW registration (prompted updates) |
| `security/` | CSP + redaction |
| `shared/` | Domain types |
| `ui/` | Panels, toolbar, styles |

## Boundaries

- React components never call LLM endpoints directly.
- Provider wire formats stop at `providers/`; domain services consume typed results.
- IndexedDB repositories are the source of truth; Zustand is ephemeral.
- EPUB renderer choice (iframe) is hidden behind `createIframeRenderer`.
- Companion output never feeds memory or grading.
- Simplifications and learner answers never become canonical memory.

## Request lifecycle

1. Visible Explain/Understand runs first (single in-flight visible request by default).
2. On success, memory may queue/consolidate (ordinary cancellable session work — not marketed as “background processing”).
3. Cancel → abort fetch, no annotation, no memory write, no partial save.
4. Malformed JSON → one repair attempt → recoverable error card with raw text.
