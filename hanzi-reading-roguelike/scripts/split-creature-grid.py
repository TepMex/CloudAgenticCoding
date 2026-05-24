#!/usr/bin/env python3
"""Split an 8×8 hanzi creature grid into per-label PNG assets (creatures only)."""

from __future__ import annotations

import argparse
from collections import Counter
from pathlib import Path

from PIL import Image

# Row-major labels matching the source creature grid.
LABELS: list[list[str]] = [
    ["人", "口", "手", "书", "桌", "椅", "门", "窗"],
    ["车", "路", "桥", "山", "水", "树", "花", "草"],
    ["家", "房", "城", "村", "校", "医", "院", "店"],
    ["书", "笔", "纸", "字", "词", "句", "篇", "报"],
    ["米", "饭", "菜", "肉", "鱼", "蛋", "奶", "茶"],
    ["衣", "裤", "鞋", "帽", "包", "钟", "表", "镜"],
    ["电", "灯", "机", "视", "手", "脑", "相", "伞"],
    ["天", "云", "雨", "风", "雪", "月", "星", "日"],
]

ROWS = len(LABELS)
COLS = len(LABELS[0])

# Fraction of each cell to trim from the top (hanzi label) and sides.
LABEL_TOP_FRAC = 0.24
SIDE_PAD_FRAC = 0.06


def output_name(label: str, seen: Counter[str]) -> str:
    seen[label] += 1
    if seen[label] == 1:
        return f"{label}.png"
    return f"{label}-{seen[label]}.png"


def split_grid(
    source: Path,
    out_dir: Path,
    *,
    label_top_frac: float = LABEL_TOP_FRAC,
    side_pad_frac: float = SIDE_PAD_FRAC,
) -> list[Path]:
    img = Image.open(source).convert("RGBA")
    width, height = img.size
    cell_w = width // COLS
    cell_h = height // ROWS

    out_dir.mkdir(parents=True, exist_ok=True)
    written: list[Path] = []
    seen: Counter[str] = Counter()

    for row in range(ROWS):
        for col in range(COLS):
            label = LABELS[row][col]
            left = col * cell_w
            top = row * cell_h
            right = left + cell_w
            bottom = top + cell_h

            pad_x = int(cell_w * side_pad_frac)
            pad_top = int(cell_h * label_top_frac)
            pad_bottom = int(cell_h * 0.04)

            crop = img.crop(
                (
                    left + pad_x,
                    top + pad_top,
                    right - pad_x,
                    bottom - pad_bottom,
                )
            )

            dest = out_dir / output_name(label, seen)
            crop.save(dest, format="PNG", optimize=True)
            written.append(dest)

    return written


def main() -> None:
    root = Path(__file__).resolve().parents[1]
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--source",
        type=Path,
        default=root / "source" / "creature-grid.png",
        help="Path to the 8×8 source grid image",
    )
    parser.add_argument(
        "--out",
        type=Path,
        default=root / "assets",
        help="Output directory for creature PNGs",
    )
    args = parser.parse_args()

    if not args.source.is_file():
        raise SystemExit(f"Source image not found: {args.source}")

    files = split_grid(args.source, args.out)
    print(f"Wrote {len(files)} creature assets to {args.out}")


if __name__ == "__main__":
    main()
