---
name: github-pages-socratus
description: GitHub Pages deploy for Socratus at /socratus/ — base path, workflow, and local dev.
---

# Socratus on GitHub Pages (`/socratus/`)

The site is published from the `gh-pages` branch. The app lives under **`/<repository>/socratus/`** on the project GitHub Pages URL (not at the domain root).

## How it works

1. **Build**: from `socratus/`, `bun run build` runs `build.ts` with `public-path` from the env var `GH_PAGES_PUBLIC_PATH` (defaults to `./` for local builds).
2. **CI** (`.github/workflows/deploy.yml`): runs install and build in `socratus/`, sets `GH_PAGES_PUBLIC_PATH` to `/<repository>/socratus/` (via `github.event.repository.name`) so hashed JS/CSS URLs resolve under the **project site** root.
3. **Deploy layout**: the workflow copies `socratus/dist/*` into `deploy/socratus/` and adds `deploy/.nojekyll`, then publishes **`deploy`** as the site root. Resulting URLs: `https://<user>.github.io/<repo>/socratus/`.

## Commands

```bash
cd socratus
bun install

# Local dev (assets at ./)
bun dev

# Production-like build matching GitHub Pages (replace my-repo with your repo name)
GH_PAGES_PUBLIC_PATH=/my-repo/socratus/ bun run build
```

## Changing the subfolder

- Update `GH_PAGES_PUBLIC_PATH` in the deploy workflow (must start and end with `/`).
- Update the `mkdir` / `cp` paths in the “Prepare GitHub Pages site” step so the copy target matches.

## App behavior (summary)

Browser-only React app: EPUB → spine-based chapters → LLM segments text → Q/A → user answer → feedback → show passage. API base URL and key in `localStorage`.
