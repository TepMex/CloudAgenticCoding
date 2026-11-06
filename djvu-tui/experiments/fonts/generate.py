#!/usr/bin/env python3
"""Generate two experimental PUA fonts. Requires fonttools (see README)."""
from pathlib import Path
import sys
from fontTools.fontBuilder import FontBuilder
from fontTools.pens.ttGlyphPen import TTGlyphPen

BASE = 0xF0000
COUNT = 1 << 15
WIDTH, HEIGHT = 600, 1200


def polygon(pen, points):
    if len(points) < 3:
        return
    pen.moveTo(points[0])
    for point in points[1:]:
        pen.lineTo(point)
    pen.closePath()


def triangle(pen, vertices):
    """Clip one triangle to its black half; crossings lie at edge midpoints."""
    result = []
    for i, (p, ink) in enumerate(vertices):
        q, next_ink = vertices[(i + 1) % 3]
        if ink:
            result.append(p)
        if ink != next_ink:
            result.append(((p[0] + q[0]) // 2, (p[1] + q[1]) // 2))
    polygon(pen, result)


def make_glyph(mask, contour):
    pen = TTGlyphPen(None)
    if not contour:
        for y in range(5):
            for x in range(3):
                if mask & (1 << (y * 3 + x)):
                    left, top = x * 200, HEIGHT - y * 240
                    polygon(pen, [(left, top), (left + 200, top),
                                  (left + 200, top - 240), (left, top - 240)])
    else:
        for y in range(4):
            for x in range(2):
                left, top = x * 300, HEIGHT - y * 300
                corners = [(left, top), (left + 300, top),
                           (left + 300, top - 300), (left, top - 300)]
                bits = [bool(mask & (1 << (yy * 3 + xx))) for xx, yy in
                        [(x, y), (x + 1, y), (x + 1, y + 1), (x, y + 1)]]
                # Opposite black corners are disconnected; other two-corner
                # cases have an ink centre so their shared edge stays filled.
                opposite = bits in ([True, False, True, False], [False, True, False, True])
                centre_ink = sum(bits) >= 2 and not opposite
                centre = (left + 150, top - 150)
                for i in range(4):
                    triangle(pen, [(corners[i], bits[i]),
                                   (corners[(i + 1) % 4], bits[(i + 1) % 4]),
                                   (centre, centre_ink)])
    return pen.glyph()


def build(path, contour):
    family = 'Djvu Contour Experiment' if contour else 'Djvu Pixels Experiment'
    names = ['.notdef'] + [f'm{i:04x}' for i in range(COUNT)]
    builder = FontBuilder(1200, isTTF=True)
    builder.setupGlyphOrder(names)
    builder.setupCharacterMap({BASE + i: names[i + 1] for i in range(COUNT)})
    glyphs = {'.notdef': TTGlyphPen(None).glyph()}
    glyphs.update((names[i + 1], make_glyph(i, contour)) for i in range(COUNT))
    builder.setupGlyf(glyphs)
    builder.setupHorizontalMetrics({name: (WIDTH, 0) for name in names})
    builder.setupHorizontalHeader(ascent=HEIGHT, descent=0)
    builder.setupNameTable({'familyName': family, 'styleName': 'Regular',
                            'uniqueFontIdentifier': family + ' 1.0',
                            'fullName': family, 'psName': family.replace(' ', '')})
    builder.setupOS2(sTypoAscender=HEIGHT, sTypoDescender=0, usWinAscent=HEIGHT, usWinDescent=0)
    builder.setupPost(isFixedPitch=1)
    builder.setupMaxp()
    builder.save(path)
    print(path)

if __name__ == '__main__':
    out = Path(sys.argv[1] if len(sys.argv) > 1 else 'target/experimental-fonts')
    out.mkdir(parents=True, exist_ok=True)
    build(out / 'djvu-pixels.ttf', False)
    build(out / 'djvu-contour.ttf', True)
