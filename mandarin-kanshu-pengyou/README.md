# 看书朋友 (mandarin-kanshu-pengyou)

Local-first Chinese EPUB reading companion for intermediate learners. No backend — books, annotations, and memory stay in your browser; optional OpenAI-compatible LLM endpoints are called directly from the client.

## Requirements

- [Bun](https://bun.sh) 1.1+
- Chromium-based desktop browser (primary)

## Commands

```bash
bun install
bun run fixture:epub   # builds fixtures/sample.epub
bun run dev
bun run test
bun run build
```

Optional E2E (Playwright / Chromium):

```bash
bunx playwright install chromium
bun run test:e2e
```

## First run

1. Open the app (`bun run dev`).
2. **Settings** → add a provider preset, paste an API key, **Test connection**.
3. Assign Explain / Assess / Memory profiles.
4. Import a DRM-free EPUB (or `fixtures/sample.epub`).
5. Select text → **Explain** or **Understand**.

API keys are session-only unless you check **Remember API keys**. IndexedDB is not secure against malicious same-origin scripts.

## Docs

- `SPEC.md` — product spec
- `docs/IMPLEMENTATION_PLAN.md` — libraries and strategy
- `docs/ARCHITECTURE.md` — module boundaries
- `docs/SCHEMA.md` — IndexedDB
- `docs/PROVIDERS.md` — profiles and tasks
- `docs/THREAT_MODEL.md` — keys, EPUB, injection, endpoints
- `docs/LIMITATIONS.md` — known MVP limits
- `prompts/` — prompt/schema reference

## Production build

```bash
bun run build
```

Output is in `dist/`. For GitHub Pages, set `GH_PAGES_PUBLIC_PATH` (e.g. `/CloudAgenticCoding/mandarin-kanshu-pengyou/`).

The service worker caches app assets for offline reading. Updates require an explicit reload so unfinished answers are not lost.
