# Mandarin Koan

Initial **Bun + React + Tailwind CSS v4 + shadcn-style UI** app, scaffolded to match the [Socratus](../socratus/) project conventions (`components.json`, `build.ts`, path alias `@/*`, and `bun-plugin-tailwind`).

## Develop

```bash
cd mandarin-koan
bun install
bun dev
```

Open the URL printed by Bun.

## Build

```bash
cd mandarin-koan
bun run build
```

Optional public path for static hosting (same pattern as Socratus):

```bash
GH_PAGES_PUBLIC_PATH=/your-repo/mandarin-koan/ bun run build
```

## Add UI

This repo uses the same shadcn setup as Socratus. From `mandarin-koan/`, add components with the shadcn CLI when needed (see project `components.json`).
