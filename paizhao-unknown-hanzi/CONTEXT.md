# paizhao-unknown-hanzi

Offline camera or gallery pass over Chinese text: show unique recognized characters, optionally hide those the learner marked as known, then jump into Pleco or share the recognized string.

## Language

**Known Text**:
The raw string saved from Settings. It may contain punctuation, spaces, and non-Chinese letters; only the hanzi inside it matter.
_Avoid_: word list, vocabulary file

**Known Hanzi**:
A unique Chinese character that appears at least once in Known Text. Identity is the code point, not a reading or a simplified/traditional pair.
_Avoid_: known word, known vocabulary

**Show Known**:
A persisted preference, on by default. When on, Known Hanzi stay in the results selection from a Scan.
_Avoid_: filter toggle, include-known flag

**Scan**:
One still photo (camera or gallery) plus the ordered OCR lines read from it.
_Avoid_: session, document

**Unknown Hanzi**:
A unique Chinese character that appears in the Scan and is not a Known Hanzi. Order is first appearance in reading order.
_Avoid_: new word, unseen token

**Hanzi Card**:
One selected character shown in the results grid together with its toned pinyin. The selection is all unique Scan hanzi when Show Known is on, otherwise Unknown Hanzi only.
_Avoid_: flashcard, tile (in domain talk; the UI may still say «ячейка»)

**Share Text**:
The unique recognized hanzi from a Scan, first-seen order, concatenated. It includes Known Hanzi even when Show Known is off.
_Avoid_: export, clipboard dump
