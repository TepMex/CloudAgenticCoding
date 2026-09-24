# instant-pinyin

The phone is a lens. Pinyin is painted onto the camera image, just above the characters it belongs to.

## Language

**Live Frame**:
One upright camera viewport: the analysis crop, rotated so text is upright.
_Avoid_: photo, scan, snapshot

**Text Line**:
Detector box plus the recognizer string for that box, in Live Frame pixels.
_Avoid_: paragraph, block

**Reading Glyph**:
One hanzi taken from a Text Line, with toned pinyin and the slice of the line box that belongs to that character. Horizontal lines slice left to right. Vertical lines slice top to bottom.
_Avoid_: card, token, word

**Pinyin Label**:
A Reading Glyph drawn in view pixels. On a horizontal character the pill sits above it. On a vertical character the pill sits beside it.
_Avoid_: subtitle, caption bar

**Gloss**:
The short Russian reading of one greedy word, or of one character when no 4-, 3-, or 2-hanzi word claimed it. On a horizontal word the pill sits under the word. On a vertical word it sits on the other side from the pinyin.
_Avoid_: definition, subtitle, sense list

**Track**:
A Reading Glyph kept from the previous Live Frame so a single missed detection does not blink the label off.
_Avoid_: cache, anchor
