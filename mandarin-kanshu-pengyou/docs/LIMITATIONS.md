# Known limitations

- PWA installability / install onboarding not prioritized.
- Firefox/Safari are best-effort; Chromium desktop is primary.
- EPUB CFI is optional; quote/prefix/suffix recovery is the reliable path.
- Selection→plainText offset mapping is heuristic when HTML whitespace differs.
- Passage viewport collapse uses simplified heuristics (manual expand via markers/panel).
- Whole-chapter analysis UI reuses the lightweight extractor with a cost warning (still sends a limited sample unless expanded later).
- No backup import/export.
- epubjs is listed for ecosystem familiarity; runtime parsing uses JSZip + custom OPF/nav adapter.
- Companion and memory updates require configured profiles; without them, reading still works.
- E2E coverage is a smoke import path; full LLM flows are unit/integration-mocked.
