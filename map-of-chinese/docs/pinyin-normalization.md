# Pinyin normalization

`src/lib/pinyin.ts` is a pure, dependency-free parser shared by the data build and browser search.

## Accepted forms

- tone-marked: `lǜ`, `xíng`
- tone-numbered: `lü4`, `xing2`
- source aliases: `lv4`, `lu:4`
- untoned: `ma` (neutral tone 5)
- NFC and decomposed Unicode input

The canonical numbered representation preserves `ü`, while the canonical search representation uses `v` so all three input spellings compare identically.

## Placement sequence

1. Normalize Unicode and lowercase.
2. Convert `u:`/`v` to `ü`.
3. Extract a final tone digit or infer the tone from a marked vowel.
4. Default to tone 5.
5. Rewrite orthographic zero-initial forms:
   - `ya → ia`, `you → iu`, `ying → ing`, `yong → iong`
   - `wa → ua`, `wei → ui`, `wen → un`
   - `yu → ü`, `yue → üe`, `yuan → üan`, `yun → ün`
6. Otherwise match `zh`, `ch`, and `sh` before single-letter initials.
7. Rewrite `j/q/x + u` to the ü family (`ju → j + ü`, `xuan → x + üan`).
8. Store the vowel in `zi/ci/si/zhi/chi/shi/ri` as `apical-i`.
9. Retain unmatched syllabic readings under `special:<form>`.

## Tone-mark placement

Display marks follow the standard priority: `a`, then `e`, then the `o` of `ou`, otherwise the last vowel. Neutral tone is unmarked.

## Test coverage

Unit tests cover marked/numbered/decomposed input, ü aliases, zero-initial y/w families, multiletter initials, j/q/x orthography, apical-i, rare special forms, and the known polyphonic characters 行、重、长、乐、还、得、着.
