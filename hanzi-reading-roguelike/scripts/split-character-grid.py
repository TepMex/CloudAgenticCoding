#!/usr/bin/env python3
"""Split a 2×2 mythological character grid into per-character PNG assets."""

from __future__ import annotations

import argparse
from pathlib import Path

from PIL import Image

CHARACTERS = [
    ("caishen", 0, 0),
    ("zhu-bajie", 1, 0),
    ("guan-yu", 0, 1),
    ("sun-wukong", 1, 1),
]


def split_grid(source: Path, out_dir: Path) -> list[Path]:
    img = Image.open(source).convert("RGBA")
    width, height = img.size
    cell_w = width // 2
    cell_h = height // 2

    out_dir.mkdir(parents=True, exist_ok=True)
    written: list[Path] = []

    for name, col, row in CHARACTERS:
        left = col * cell_w
        top = row * cell_h
        crop = img.crop((left, top, left + cell_w, top + cell_h))
        dest = out_dir / f"{name}.png"
        crop.save(dest, format="PNG", optimize=True)
        written.append(dest)

    return written


def main() -> None:
    root = Path(__file__).resolve().parents[1]
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--source",
        type=Path,
        default=root / "source" / "character-grid.png",
        help="Path to the 2×2 source grid image",
    )
    parser.add_argument(
        "--out",
        type=Path,
        default=root / "assets" / "characters",
        help="Output directory for character PNGs",
    )
    args = parser.parse_args()

    if not args.source.is_file():
        raise SystemExit(f"Source image not found: {args.source}")

    files = split_grid(args.source, args.out)
    print(f"Wrote {len(files)} character assets to {args.out}")


if __name__ == "__main__":
    main()
