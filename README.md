# Socratic Reading Agent

AI-assisted **educational reading** helper. It runs **entirely in the browser**: no backend. You provide an **OpenAI-compatible API base URL** and **API key** (stored in `localStorage`), upload an **EPUB**, choose a **chapter**, then work through **questions** before each **text segment** is revealed.

## Features

- EPUB upload and text extraction along the **spine** (typical chapter order)
- Chapter picker
- For each segment: model finds a **logical block** → generates **one Q&A pair** → asks you → **analysis vs. the book text** (sense, alignment and gaps). Optionally a **separate tutor opinion** (Settings) → shows the **passage**

## Develop

```bash
bun install
bun dev
```

Open the URL printed by Bun (local assets use relative paths).

## Build

```bash
bun run build
```

For the same asset paths as **GitHub Pages** (`/socratic/`):

```bash
GH_PAGES_PUBLIC_PATH=/socratic/ bun run build
```

## GitHub Pages

On push to `master`, CI builds with `GH_PAGES_PUBLIC_PATH=/socratic/` and deploys to `gh-pages` so the app is available at:

`https://<owner>.github.io/<repository>/socratic/`

Enable GitHub Pages from the `gh-pages` branch / root in repository settings if needed.

## Cursor

See `.cursor/skills/github-pages-socratic/SKILL.md` for deploy layout and path conventions.
