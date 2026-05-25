# Hanzi Reading Roguelike

Browser game project (scaffold). Creature sprites live in `assets/` — one PNG per hanzi label, cropped from the source 8×8 grid.

## Asset pipeline

Regenerate creature PNGs from the source sheet:

```bash
cd hanzi-reading-roguelike
python3 scripts/split-creature-grid.py
```

Source grid: `source/creature-grid.png` (8 rows × 8 columns). The splitter crops each cell to the creature illustration and skips the top-left hanzi label.

Duplicate labels (`书`, `手`) get suffixed filenames on repeat: `书.png`, `书-2.png`, `手.png`, `手-2.png`.
