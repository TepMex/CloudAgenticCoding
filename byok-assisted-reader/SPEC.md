# BYOK-assisted-reader — SPEC

## Purpose

Android reader for a learner of Chinese. The user opens a DRM-free Chinese EPUB and reads it in a large typeface, then switches information layers on the same page: pinyin ruby, a colored STPVO breakdown of each full sentence, and a glossary of unknown words (simple Chinese, then Russian). Explanations come from the user’s own OpenAI-compatible LLM (bring your own key).

## Requirements

1. Project folder **byok-assisted-reader**; launcher name **BYOK-assisted-reader**; application id `com.tepmex.byokassistedreader`.
2. minSdk 34, compileSdk/targetSdk 36. Kotlin, Jetpack Compose, Material 3.
3. **Настройки** stores:
   - LLM base URL
   - access token (optional; sent as `Authorization: Bearer` when non-blank)
   - model name
   - a list of known words
   - a switch for volume-key layer changes (on by default)
4. Known words are split on whitespace and on `,` `，` `、` `;` `；`. A token that contains at least one hanzi is a known word. Known hanzi are every CJK ideograph that appears in that text, in first-seen order. The settings screen shows the count and the derived characters.
5. The shelf opens a local EPUB (`ACTION_OPEN_DOCUMENT` and `ACTION_VIEW` for `application/epub+zip`). The URI permission is persisted. The last book and page index are restored.
6. EPUB reading: `META-INF/container.xml` → OPF spine order → XHTML/HTML documents. Scripts, styles, and ruby `<rt>`/`<rp>` are dropped. Chapter text becomes paragraphs, then sentences.
7. A sentence ends at `。`. Pages contain whole sentences only. A sentence taller than the screen is its own scrollable page. The trailing fragment with no `。` is kept and is not sent to STPVO.
8. Page capacity is the pinyin layout, so switching layers does not change which sentences are on the page. Layer 0 draws those sentences in the same large hanzi size, without ruby.
9. **Layer 0 — Текст.** The page text only, large hanzi.
10. **Layer 1 — Пиньинь.** Each hanzi has toned Hanyu pinyin above it (first reading). The hanzi em-square is at least as wide as the six-letter syllable `zhuāng` set at 12sp, so that pinyin stays at 12sp, does not exceed the hanzi width, and stays readable. Punctuation keeps an empty ruby slot. Non-hanzi have no pinyin.
11. **Layer 2 — Структура.** Each complete sentence on the page is sent to the LLM and painted in five roles:
    - subject / Кто
    - time / Когда
    - place / Где
    - verb / Что делает
    - object / С чем
    Spans must be exact substrings. Overlapping characters keep the first accepted span. A legend names the five colors.
12. **Layer 3 — 简单.** The LLM explains words on the page that are not in the known-word list, in the simplest Chinese. Chengyu present on the page are listed with explanations even when the word is known.
13. **Layer 4 — По-русски.** The same glossary shape as layer 3, with Russian explanations.
14. Volume Up moves to the next layer, Volume Down to the previous, wrapping 0↔4, only while the reader is open and the switch is on. Those keys are consumed. With the switch off, they change the system volume.
15. Swipe or **Назад** / **Дальше** turns pages. The top bar shows the book title, the layer name, and the page index, plus settings and open-file actions.
16. LLM calls use `POST {base}/v1/chat/completions` (or `{base}/chat/completions` when the base URL already ends at one of those suffixes). Results are cached in memory by layer, endpoint, model, known words, and page text. Missing base URL or model shows an error and does not call the network. Failures stay on screen with **Повторить**.
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

Roles: `subject`, `time`, `place`, `verb`, `object`.

## Data model

- **Known word** — one token from the settings field that contains a hanzi.
- **Known hanzi** — one ideograph derived from the known-word text.
- **Sentence** — text ending in `。` (`complete`) or the final fragment (`incomplete`).
- **Page** — an ordered list of sentences chosen to fit the pinyin row budget.
- **Ruby cell** — one code point plus its pinyin (empty when it is not a hanzi).
- **STPVO span** — a half-open range in the sentence and one role.
- **Gloss entry** — surface word plus an explanation. Unknown-word entries that are already known, or that do not occur on the page, are dropped. Chengyu that do not occur on the page are dropped.

The book text lives in memory. The EPUB file is not copied. The API token is stored in DataStore and is not written to logs.

## UI / UX

Paper background, large black hanzi, a thin top bar. Layer 2 colors: Кто blue, Когда amber, Где green, Что делает red, С чем purple, each as a light background behind the span. Layers 3 and 4 split the screen between the passage and the glossary (unknown words, then 成语).

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
4. `装` is `zhuāng` (caron, six letters). The hanzi slot width equals the measured width of `zhuāng` at 12sp, so the pinyin size stays 12sp.
5. STPVO parts align onto exact substrings; a part that overlaps an earlier part is skipped; a part missing from the sentence is skipped.
6. Glossary parsing drops a known word, keeps an unknown word that occurs in the page, and keeps a chengyu that occurs in the page.
7. A minimal EPUB zip yields its spine text in order, without ruby pronunciation dumped into the paragraph.
8. Volume-key stepping wraps `TEXT → … → GLOSS_RU → TEXT`.
9. `./gradlew test assembleRelease` succeeds and the APK verifies with `android/verify-apk-sideload-cert.sh`.
10. Deploy workflow includes `byok-assisted-reader` in `ANDROID_APPS`.
