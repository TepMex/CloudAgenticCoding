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

**Track**:
A Reading Glyph kept from the previous Live Frame so a single missed detection does not blink the label off.
_Avoid_: cache, anchor

**Still**:
One photograph or gallery image, read once. The live overlay is not updating while the Still is open.
_Avoid_: scan, session, online mode

**Text Token**:
A word, a single hanzi, or a punctuation mark taken from one OCR line of a Still, in reading order. A word is a lexicon match of at least two characters. Pinyin is above the surface; Russian is below. Pleco receives the word when the token is a word, and the character otherwise.
_Avoid_: card, equal cell, unique-hanzi tile
