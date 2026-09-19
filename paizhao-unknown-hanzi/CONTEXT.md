# paizhao-unknown-hanzi

Offline camera pass over photographed Chinese text: show only characters the learner has not marked as known, then jump into Pleco.

## Language

**Known Text**:
The raw string saved from Settings. It may contain punctuation, spaces, and non-Chinese letters; only the hanzi inside it matter.
_Avoid_: word list, vocabulary file

**Known Hanzi**:
A unique Chinese character that appears at least once in Known Text. Identity is the code point, not a reading or a simplified/traditional pair.
_Avoid_: known word, known vocabulary

**Scan**:
One still photo plus the ordered OCR lines read from it.
_Avoid_: session, document

**Unknown Hanzi**:
A unique Chinese character that appears in the Scan and is not a Known Hanzi. Order is first appearance in reading order.
_Avoid_: new word, unseen token

**Hanzi Card**:
One Unknown Hanzi shown in the results grid together with its toned pinyin.
_Avoid_: flashcard, tile (in domain talk; the UI may still say «ячейка»)
