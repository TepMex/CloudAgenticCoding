---
name: github-pages-socratic
description: GitHub Pages deploy for Socratic Reading Agent at /socratic/ — base path, workflow, and local dev.
---

# Socratic app on GitHub Pages (`/socratic/`)

The site is published from the `gh-pages` branch. The app lives under **`/socratic/`** on the user/org GitHub Pages URL (not at the domain root).

## How it works

1. **Build**: `bun run build` runs `build.ts` with `public-path` from the env var `GH_PAGES_PUBLIC_PATH` (defaults to `./` for local builds).
2. **CI** (`.github/workflows/deploy.yml`): sets `GH_PAGES_PUBLIC_PATH=/socratic/` so hashed JS/CSS URLs in `index.html` are absolute under `/socratic/`.
3. **Deploy layout**: the workflow copies `dist/*` into `deploy/socratic/` and adds `deploy/.nojekyll`, then publishes **`deploy`** as the site root. Resulting URLs: `https://<user>.github.io/<repo>/socratic/`.

## Commands

```bash
# Local dev (assets at ./)
bun dev

# Production-like build matching GitHub Pages
GH_PAGES_PUBLIC_PATH=/socratic/ bun run build
```

## Changing the subfolder

- Update `GH_PAGES_PUBLIC_PATH` in the deploy workflow (must start and end with `/`).
- Update the `mkdir` / `cp` paths in the “Prepare GitHub Pages site” step so the copy target matches.

## App behavior (summary)

Browser-only React app: EPUB → spine-based chapters → LLM segments text → Q/A → user answer → feedback → show passage. API base URL and key in `localStorage`.
