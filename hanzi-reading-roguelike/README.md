# Hanzi Reading Roguelike

Mobile-friendly browser game: **Phaser 3**, **Bun** + **Vite**. Circles labeled with a random Hanzi drift toward a center crosshair; if one reaches the center, you lose. Tap a circle, type its **pinyin** (no tones) in the overlay input to clear it.

## Run

```bash
cd hanzi-reading-roguelike
bun install
bun run dev
```

Open the printed URL on a phone (same LAN) or use `adb reverse` for USB debugging.

## Build

```bash
bun run build
```

Static output is in `dist/` — serve with any static host.

## GitHub Pages (this monorepo)

The root workflow [`.github/workflows/deploy.yml`](../.github/workflows/deploy.yml) builds this app and copies `dist/` to `deploy/hanzi-reading-roguelike/` on the `gh-pages` branch. After a push to `master`, the game is available at:

`https://<user-or-org>.github.io/<repository>/hanzi-reading-roguelike/`

CI sets `GH_PAGES_PUBLIC_PATH` so Vite emits correct asset URLs under that prefix.

## Gameplay notes

- **Hints:** For each Hanzi, the first five times it appears in a spawn (persisted in `localStorage`), the pinyin hint is shown above the circle — matching the “first through fifth meeting” idea.
- **Roguelike pressure:** Spawn interval shortens and drift speed increases over time.
- **Images:** `hanziData.ts` includes `image` paths for future sprite work; the current build draws the character with `Noto Sans SC` text inside the circle.

## Creature PNG pipeline (optional assets)

Creature sprites can live in `assets/` — one PNG per label, cropped from the source grid. Regenerate with:

```bash
cd hanzi-reading-roguelike
python3 scripts/split-creature-grid.py
```

Source grid: `source/creature-grid.png` (8×8). The splitter skips the top-left label cell.
