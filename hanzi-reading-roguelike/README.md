# Hanzi Reading Roguelike

Mobile-friendly browser game: **Phaser 3**, **Bun** + **Vite**. Mythological character sprites with a Hanzi on each belly drift toward a center crosshair; if one reaches the center, you lose. Tap a character, type its **pinyin** (no tones) in the overlay input to clear it.

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

- **RTH lists:** Enemies spawn from one Remembering the Hanzi / RSH list at a time. Every character in the current list shares the same difficulty (spawn rate + drift speed). Clear each unique hanzi in the list to unlock the next list; difficulty then increases.
- **Quiz mode:** Settings lets you choose **Reading (pinyin)** or **Meaning (keyword)**. The choice is saved in `localStorage`.
- **Hints:** For each Hanzi, the first five times it appears in a spawn (persisted in `localStorage`), a hint for the active quiz mode is shown above the character.
- **Images:** Enemies use mythological character sprites (财神, 猪八戒, 关羽, 孙悟空) with the Hanzi drawn in each belly placeholder. Regenerate sprites from `source/character-grid.png` via `scripts/split-character-grid.py`.

## Vocabulary data

Game lists live in `src/data/rshLists.json` (110 lists, ~2974 characters with toneless pinyin). Regenerate from the slim knowledge base:

```bash
cd hanzi-reading-roguelike
bun run build:rsh-lists -- data/rsh_knowledge_base.slim.json
```

Pinyin is joined from `../map-of-chinese/src/data/characters.json` (with a small fallback map for rare glyphs).

## Character sprite pipeline

Mythological enemy sprites live in `assets/characters/`, cropped from a 2×2 source grid:

```bash
cd hanzi-reading-roguelike
python3 scripts/split-character-grid.py
```

Source grid: `source/character-grid.png` (Caishen, Zhu Bajie, Guan Yu, Sun Wukong).

## Creature PNG pipeline (optional assets)

Creature sprites can live in `assets/` — one PNG per label, cropped from the source grid. Regenerate with:

```bash
cd hanzi-reading-roguelike
python3 scripts/split-creature-grid.py
```

Source grid: `source/creature-grid.png` (8×8). The splitter skips the top-left label cell.
