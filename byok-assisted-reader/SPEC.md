# BYOK-assisted-reader — SPEC

## Purpose

Android reader for a learner of Chinese. The user opens a DRM-free Chinese EPUB and reads it in a large typeface, then switches information layers on the same page: pinyin ruby, a colored STPVO breakdown of each full sentence, and one reference layer whose windows list unknown words, chengyu, and proper names, places, and terms. Explanations come from the user’s own OpenAI-compatible LLM (bring your own key).

## Requirements

1. Project folder **byok-assisted-reader**; launcher name **BYOK-assisted-reader**; application id `com.tepmex.byokassistedreader`.
2. minSdk 34, compileSdk/targetSdk 36. Kotlin, Jetpack Compose, Material 3.
3. **Настройки** stores:
   - LLM base URL
   - access token (optional; sent as `Authorization: Bearer` when non-blank)
   - model name
   - a list of known words. The editor is at most half the settings screen tall and scrolls inside the field. A copy icon copies that text. The derived-character preview under the count is capped and scrolls on its own, so one or two thousand words do not take over the settings screen.
   - a switch for volume-key layer changes (on by default)
   - characters per line. `0` keeps the largest type. The largest type is the current comfort size: each slot is at least the width of `zhuāng` at 12sp and at least the measured hanzi, and the hanzi does not grow past that size. The slider continues through 12 characters per line whenever that many slots fit in the width, including 9, 10, 11, and 12 past the old 8sp floor. A screen that already fits more than 12 at 8sp keeps those larger counts. Pinyin scales with the slot. While the slot can hold `zhuāng` at 8sp, pinyin stays at or above 8sp. From 9 to 12 characters per line the ruby keeps the proportional size and is still treated as readable. The value is clamped to the offered range.
4. Known words are split on whitespace and on `,` `，` `、` `;` `；`. A token that contains at least one hanzi is a known word. Known hanzi are every CJK ideograph that appears in that text, in first-seen order. The settings screen shows the count and the derived characters.
5. The shelf opens a local EPUB (`ACTION_OPEN_DOCUMENT` and `ACTION_VIEW` for `application/epub+zip`). The URI permission is persisted. The last book and page index are restored.
6. EPUB reading: `META-INF/container.xml` → OPF spine order → XHTML/HTML documents. Scripts, styles, and ruby `<rt>`/`<rp>` are dropped. Chapter text becomes paragraphs, then sentences.
7. A sentence ends at `。`. Pages contain whole sentences only. A sentence taller than the screen is its own scrollable page. The trailing fragment with no `。` is kept and is not sent to STPVO.
8. Every layer draws the same character grid: the same columns, the same slot width, and a reserved pinyin band, so switching layers does not move the hanzi. Columns are counted on the width inside the page padding. A row contains only as many slots as fit, and each hanzi is sized to stay inside its slot. Page capacity uses that grid and the full reader body, so the sentences on the page do not change with the layer. No layer reserves a bottom band for a list.
9. **Layer 0 — Текст.** The shared grid, with the pinyin band left empty.
10. **Layer 1 — Пиньинь.** Toned Hanyu pinyin (first reading) in the reserved band, only above hanzi that do not occur in a known word. A hanzi is familiar when it appears inside a known word; familiar hanzi keep an empty ruby slot. At the largest type, pinyin stays 12sp and does not exceed the slot. Smaller type scales pinyin down with the slot. The 8sp floor holds only while the slot can draw `zhuāng` at 8sp; 9–12 characters per line keep the proportional size below that. Punctuation keeps an empty ruby slot. Non-hanzi have no pinyin. Pinyin does not change the column count. The filled ruby stays visible on Пиньинь, Структура, and Справка. Switching among those layers does not clear it. Текст leaves the band empty.
11. **Layer 2 — Структура.** Each complete sentence on the page is sent to the LLM and painted in five roles:
    - subject / Кто
    - time / Когда
    - place / Где
    - verb / Что делает
    - object / С чем
    Spans must be exact substrings. Overlapping characters keep the first accepted span. The legend is a slightly transparent window over the text, not a band that shortens the page. A horizontal swipe in either direction shows it or hides it. The legend names the five colors on two rows: Кто, Когда, Где, then Что делает and С чем. Each name stays on one line. A row scrolls sideways instead of splitting «С чем» into letters.
12. **Layer 3 — Справка.** One layer replaces the former names layer and both dictionaries. The LLM still lists proper names (people, characters, organizations, and works), place names, and specialized terms, plus unknown words in simple Chinese and in Russian, plus chengyu in both languages. Reference rules are unchanged: an item stays even when the word is known, the same surface is listed once in the earliest category, a shorter item that occurs only inside a longer item is dropped, and each word is an exact substring of the page. Unknown words that are already known, or that do not occur on the page, are dropped. Chengyu that occur on the page are kept even when the word is known. Nothing is docked to the bottom of the screen. A horizontal swipe cycles slightly transparent windows, and the cycle includes a screen with no window: none → unknown words → chengyu → names and places → none. The opposite swipe steps backward. Each window has its own color (dictionary, chengyu, names/places) and floats over the full-height text. The names window tints matching hanzi with the name, place, and term shades and lists terms in that same window. The other windows leave the hanzi untinted. Unknown-word and chengyu windows show the simple-Chinese list and the Russian list.
13. The passage keeps the full reader height on every layer, so a window does not reflow the hanzi. The passage keeps the system background. Window colors and the three reference tints stay distinct from the page and from each other. Windows are slightly transparent (alpha 0.86) so the text behind them remains visible.
14. Volume Up moves to the next layer, Volume Down to the previous, wrapping 0↔3 (`TEXT` ↔ `ASSIST`), only while the reader is open and the switch is on. Those keys are consumed. With the switch off, they change the system volume.
15. Horizontal swipe does not turn pages on any layer. **Назад** and **Дальше** are arrow icons and are the only page controls. Settings, open file, retry, copy, save, and show/hide token are icons as well. The top bar shows the book title, the layer name, and the page index.
16. LLM calls use `POST {base}/v1/chat/completions` (or `{base}/chat/completions` when the base URL already ends at one of those suffixes). Results are cached in memory by layer, endpoint, model, known words, and page text. Missing base URL or model shows an error and does not call the network. Failures stay on screen with a retry icon.
17. Sign release and debug (when the keystore is present) with the shared sideload keystore. GitHub Pages landing at `/byok-assisted-reader/` serves `byok-assisted-reader.apk`.

## Interfaces

| Interface | Detail |
| --------- | ------ |
| Launcher | `com.tepmex.byokassistedreader.MainActivity` |
| Open EPUB | `ACTION_OPEN_DOCUMENT`; `ACTION_VIEW` + `application/epub+zip` |
| LLM | OpenAI-compatible chat completions, JSON in the message content |
| Settings | DataStore `byok_reader` |
| Permission | `INTERNET` |
| Gradle | `./gradlew test assembleRelease` |
| Pages | `https://<host>/<repo>/byok-assisted-reader/byok-assisted-reader.apk` |

STPVO JSON:

```json
{"sentences":[{"text":"昨天我在学校看书。","parts":[{"role":"time","text":"昨天"},{"role":"subject","text":"我"},{"role":"place","text":"在学校"},{"role":"verb","text":"看"},{"role":"object","text":"书"}]}]}
```

Glossary JSON:

```json
{"words":[{"word":"学校","explanation":"学习的地方。"}],"chengyu":[{"word":"一心一意","explanation":"很专心。"}]}
```

Reference JSON:

```json
{"names":[{"word":"孔子","explanation":"Конфуций, философ."}],"places":[{"word":"长安","explanation":"Древняя столица."}],"terms":[{"word":"科举","explanation":"Экзамены на службу."}]}
```

Roles: `subject`, `time`, `place`, `verb`, `object`. Reference kinds: `name`, `place`, `term`.

## Data model

- **Known word** — one token from the settings field that contains a hanzi.
- **Known hanzi** — one ideograph derived from the known-word text.
- **Sentence** — text ending in `。` (`complete`) or the final fragment (`incomplete`).
- **Page** — an ordered list of sentences chosen to fit the pinyin row budget.
- **Ruby cell** — one code point plus its pinyin (empty when it is not a hanzi).
- **STPVO span** — a half-open range in the sentence and one role.
- **Reference entry** — a proper name, place, or term from the page, plus a short Russian explanation. Known words are kept. A surface listed in more than one category stays in the earliest one. A shorter item that occurs only inside a longer item is dropped.
- **Reference span** — a half-open range on the page and one reference kind. A longer match keeps the characters it covers.
- **Gloss entry** — surface word plus an explanation. Unknown-word entries that are already known, or that do not occur on the page, are dropped. Chengyu that do not occur on the page are dropped.
- **Assist card** — the floating window on the reference layer: none, unknown words, chengyu, or names and places. The cycle includes the empty screen.

The book text lives in memory. The EPUB file is not copied. The API token is stored in DataStore and is not written to logs.

## UI / UX

Paper background, large black hanzi, a thin top bar of icons. The passage is one character grid on the full reader height on every layer and keeps that system background. Layer 2 colors the hanzi cell: Кто blue, Когда amber, Где green, Что делает red, С чем purple. The legend is a translucent window, two rows, so «С чем» stays intact; swipe either way shows or hides it. Layer 3 floats one translucent window at a time: none, unknown words, chengyu, or names and places. Swipe left steps forward through that loop, swipe right steps back. Page turns are the back and forward arrows. Settings includes **Символов в строке**, from the largest type through 12 per line, with pinyin scaled to the slot. The known-words field is at most half the screen, scrolls inside itself, and has a copy icon.

## Out of scope

- Accounts, sync, or a backend proxy
- DRM, PDF, MOBI
- Word-level polyphonic pinyin (layer 1 uses the first reading of each character)
- Dictionary editing inside the reader, Anki export, TTS
- Translating the whole page
- Play Store / App Bundle
- Encrypted token storage

## Acceptance criteria

1. Known text `我\n你好，猫` yields words `我`, `你好`, `猫` and hanzi `我`, `你`, `好`, `猫`.
2. `昨天我看书。今天` splits into a complete sentence `昨天我看书。` and an incomplete `今天`. Pages never cut a sentence in half; an oversized sentence is a one-sentence page.
3. Ruby layout of `你好` at 4 columns is one row of two cells. A newline starts a new row. `你` has pinyin and `。` does not.
4. `装` is `zhuāng` (caron, six letters). A content width of 1000px with a comfort cell of 180px and a minimum cell of 120px allows 5..12 characters per line. Every count in that range places slots that fit in the width. Counts 5..8 keep pinyin within 8sp..12sp. Counts 9..12 scale pinyin proportionally below 8sp, and the drawn ruby still does not exceed the slot. Preferred 0 selects 5. When the hanzi glyph is wider than `zhuāng` at 12sp, the comfort cell grows to the glyph, so the extra character is not placed on the row. The passage height is the full reader body.
5. STPVO parts align onto exact substrings; a part that overlaps an earlier part is skipped; a part missing from the sentence is skipped.
6. Glossary parsing drops a known word, keeps an unknown word that occurs in the page, and keeps a chengyu that occurs in the page.
7. A minimal EPUB zip yields its spine text in order, without ruby pronunciation dumped into the paragraph.
8. Volume-key stepping wraps `TEXT → PINYIN → STRUCTURE → ASSIST → TEXT`. Pinyin is shown on every layer except `TEXT`. Assist windows cycle `NONE → WORDS → CHENGYU → REFERENCE → NONE`. A horizontal swipe on Текст or Пиньинь does nothing; on Структура it toggles the legend either way; on Справка it steps the window. It never turns the page.
9. `./gradlew test assembleRelease` succeeds and the APK verifies with `android/verify-apk-sideload-cert.sh`.
10. Deploy workflow includes `byok-assisted-reader` in `ANDROID_APPS`.
11. Known words `你好，猫` mark `你`, `好`, and `猫` as familiar. Pinyin for those glyphs is blank; the reading for `看` is kept. With no known words, `你` still shows its reading.
12. Page, dictionary, chengyu, the names window, and the three reference shades are seven different colors in both light and dark themes. Overlay windows use alpha 0.86.
13. On `孔子在长安谈科举和北京。我从北京去北京大学。`, the reference list keeps `孔子`, `长安`, `科举`, `北京`, and `北京大学`. `长安` claimed as both a name and a place stays a name. `北京` stays even though it is a known word. `孔` is dropped when it occurs only inside `孔子`. `外星` and an empty explanation are dropped. `北京大学` covers its own span, and the separate `北京` keeps another.
