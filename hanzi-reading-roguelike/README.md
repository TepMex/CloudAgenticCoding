# Hanzi Reading Roguelike

Mobile-friendly Phaser game built with Bun + Vite.

## Run locally

```bash
cd hanzi-reading-roguelike
bun install
bun run dev
```

Open `http://localhost:5173` and use responsive/mobile mode for Android-like layout testing.

## Gameplay

- Main menu with **New Game**, **How To Play**, and **Exit**.
- Enemies are circles with one random hanzi, moving toward the center each tick.
- If an enemy touches the center marker, game over.
- Tap an enemy to open the pinyin input; on mobile this brings up the software keyboard.
- Type correct pinyin and submit to destroy the enemy.
- For each hanzi, pinyin hints are shown only for first 5 encounters.

## Asset pipeline (optional)

Regenerate creature PNGs from the source sheet:

```bash
cd hanzi-reading-roguelike
python3 scripts/split-creature-grid.py
```
