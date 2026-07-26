# Known limitations

- **CSP**: `connect-src` allows `https:` and `http:` to support arbitrary user-configured OpenAI-compatible endpoints. A stricter CSP would require pre-listing endpoints, which conflicts with the multi-profile, user-configured design.
- **IndexedDB key persistence**: keys stored with "Remember API keys" are readable by any same-origin JavaScript. The app is intended for private, trusted devices.
- **EPUB structure**: the custom JSZip adapter handles EPUB 2 (NCX) and EPUB 3 (nav) tables of contents defensively, but malformed or highly non-standard EPUBs may parse incorrectly. DRM-protected EPUBs are not supported.
- **Selection precision**: character-level selection relies on the browser's native selection inside the sandboxed iframe; rare publisher markup may interfere with sentence-boundary expansion.
- **Location recovery**: CFI is approximate; quote + prefix/suffix matching is the robust fallback. After major renderer changes, annotations recover by text match.
- **Structured output**: depends on the provider's JSON/structured-output support. When unsupported, the client falls back to defensive JSON extraction + one repair attempt; some providers may still fail.
- **Memory heuristic**: `shouldUpdateMemoryImmediately` is a simple 2–4 char CJK unknown-term heuristic; it may trigger too eagerly or miss entities. It is conservative (queue by default).
- **Companion predictions**: explicitly isolated; they never become facts or canonical memory.
- **No streaming**: responses appear after completion (calm loading state, no typing simulation).
- **No backup import/export** in the MVP.
- **Mobile**: best-effort Chromium usability; desktop is the primary experience.
- **Bundle size**: the single chunk is >500 KB due to JSZip + Dexie + zod; future work could code-split the reader.