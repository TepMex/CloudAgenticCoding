# Prompt templates and output schemas

Implemented in `src/providers/prompts.ts` and `src/providers/schemas.ts`.

| Task | System prompt fn | Zod schema |
| --- | --- | --- |
| Simplification L1–L3 | `simplificationSystemPrompt` | `simplificationSchema` |
| Understanding assessment | `assessmentSystemPrompt` | `understandingAssessmentSchema` |
| Follow-up assessment | same assess prompt + question index | `understandingAssessmentSchema` |
| Memory patch | `memoryPatchSystemPrompt` | `memoryPatchSchema` |
| Initial extraction | `initialMemorySystemPrompt` | `initialMemorySchema` |
| Companion reaction | `companionSystemPrompt` | `companionSchema` |
| Structured repair | `repairSystemPrompt` | re-validates original task schema |
| Chapter summary | `chapterSummarySystemPrompt` | inline `{ summary, unresolvedThreads }` |

All book/learner payloads go through `quoteUntrusted()` with explicit delimiters.
