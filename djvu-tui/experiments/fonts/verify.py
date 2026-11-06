#!/usr/bin/env python3
"""Check generated fonts' character maps, geometry and fixed advance."""
from pathlib import Path
import sys
from fontTools.ttLib import TTFont

root = Path(sys.argv[1] if len(sys.argv) > 1 else 'target/experimental-fonts')
for name in ('djvu-pixels.ttf', 'djvu-contour.ttf'):
    path = root / name
    font = TTFont(path)
    cmap = font.getBestCmap()
    assert set(cmap) == set(range(0xF0000, 0xF8000))
    assert font['maxp'].numGlyphs == 32769
    assert all(font['hmtx'][glyph][0] == 600 for glyph in cmap.values())
    assert font['glyf'][cmap[0xF0000]].numberOfContours == 0
    solid = font['glyf'][cmap[0xF7FFF]]
    assert (solid.xMin, solid.yMin, solid.xMax, solid.yMax) == (0, 0, 600, 1200)
    print(f'{path}: 32768 masks, fixed advance, blank and full bounds OK')
