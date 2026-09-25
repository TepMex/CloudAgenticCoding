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

**Still**:
One photograph or gallery image, read once. The live overlay is not updating while the Still is open.
_Avoid_: scan, session, online mode

**Known Hanzi**:
One ideograph saved from Settings. It is already familiar, so its pinyin is not drawn. A word made only of these has no Russian gloss.
_Avoid_: known word, vocabulary item

**Only Known**:
The main-screen checkbox. When it is on, the reader keeps Known Hanzi and leaves the rest out.
_Avoid_: show known, hide unknown filter

**Pinyin only**:
The main-screen checkbox. When it is on, Russian glosses are hidden and pinyin stays.
_Avoid_: hide translation, no dictionary

**Recognition zone**:
The adjustable frame on the live preview. OCR reads only the text inside it.
_Avoid_: crop tool, viewport, scan box

**Pinyin tier**:
One of two rows (or columns) of pinyin on a single text line. Odd characters in reading order sit on the near tier; even characters sit one step further out, so neighboring pills do not cover each other.
_Avoid_: ruby line, subtitle row

**Text Token**:
A word of at most four hanzi, a single hanzi, or a punctuation mark taken from one OCR line of a Still, in reading order. The match uses the same glossary as the live overlay. Pinyin is above each character; Russian is under the word. Pleco receives one character from a character tap, and the whole word from a gloss tap.
_Avoid_: card, equal cell, unique-hanzi tile
