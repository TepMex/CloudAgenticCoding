# instant-pinyin (即时拼音) — SPEC

## Purpose

Offline Android camera for Mandarin learners. The phone is held like a pair of glasses: while the back camera looks at Chinese text, toned pinyin floats directly above each recognized character so the line can be read in place. A tap on a character opens it in Pleco.

Audience: someone standing in front of a sign, menu, or page who wants the pronunciation on the text itself, not a separate result list.

Built on the same on-device PP-OCRv5 stack as **paizhao-unknown-hanzi**. This app does not list unique hanzi; it registers pinyin onto the live camera image.

## Requirements

1. Display name **即时拼音**; project / Pages path **instant-pinyin**; application id `com.tepmex.instantpinyin`.
2. The main screen is a full-screen back-camera preview. The screen stays on while the reader is open.
3. Frames are read **on device** with no network. Engine: **LiteRT** running bundled **PP-OCRv5** detector + recognizer (`.tflite` from `litert-community/PP-OCRv5-LiteRT`). One frame is processed at a time; the camera keeps previewing, and the last overlay stays up until the next frame finishes.
4. Each recognized text line is split into per-character slots (whitespace does not take a slot; other symbols do, so hanzi stay aligned). Hanzi in a wide line are sliced left to right. Hanzi in a tall line are sliced top to bottom. Each hanzi gets its Hanyu pinyin with tone marks (multiple readings joined with `/`).
5. Pinyin is drawn as a small label registered to the camera image:
   - Horizontal characters: the label sits **above** the character.
   - Vertical characters: the label sits **beside** the character (to the right, or to the left if the right edge has no room).
   - Neighbors alternate **two tiers** so the pills do not cover each other. On a horizontal line, the 1st, 3rd, 5th… readings sit on the near tier (just above the ink) and the 2nd, 4th, 6th… sit one row higher. On a vertical column the far tier is one column further out. A reading that is still wider than the gap to the next same-tier neighbor is drawn smaller until the pills no longer overlap. If two pills still meet, the one farther from its character is pushed further out.
5a. A Russian gloss is drawn for recognized hanzi, from the bundled lexicon:
   - Inside one text line, matching is greedy: a 4-hanzi dictionary word wins, then 3, then 2. A gap (punctuation slot or a wide space between glyphs) starts a new run.
   - A matched word gets one gloss under the whole word when the line is horizontal (pinyin stays above each character).
   - A character that is not inside a matched word gets its own gloss from the 6500-character list, in the same place. No gloss when that character is missing too.
   - A vertical word puts the gloss on the left of the column (or the right if the left edge has no room).
   - The shown gloss is the first numbered sense, clipped to a short phrase so it fits under the word.
   - Tapping the gloss opens that word (or character) in Pleco. Tapping the character or its pinyin still opens that one character.
   - Preview mapping is center-crop (`FILL_CENTER`), the same scale the preview uses, including the CameraX viewport crop rotated into upright bitmap space.
   - A single missed detection keeps the previous label for one frame so the overlay does not flicker off.
6. Tapping a character or its label opens Pleco via `plecoapi://x-callback-url/s?q={hanzi}&x-source=instant-pinyin`. If Pleco is missing, show a snackbar instead of crashing.
7. A torch control toggles the back-camera flashlight.
8. **Сфотографировать** takes one still with CameraX `ImageCapture`. **Из галереи** opens the system photo picker. Either path leaves the live overlay and runs OCR once on that image (static reading, not a stream of frames). Gallery works without the camera permission. The back control returns to the live reader.
9. The still result is the recognized text in reading order, not a grid of unique characters. Each OCR line is a wrapping row. Consecutive hanzi are grouped with the same greedy match as the live overlay (`dict/words.txt`, at most 4 hanzi, then `dict/chars.txt`). A match of two or more characters is one word cell; everything else is a single-character cell. Cells are as wide as their text. Punctuation stays between cells and is not tappable.
10. A cell shows each character's toned pinyin above that character and one short Russian gloss under the word (an em dash when the lexicon has no gloss). Tapping a character or its pinyin opens that character in Pleco. Tapping the gloss opens the whole word, or that character when the cell is a single hanzi.
11. The window follows the device sensor (`fullSensor`), including when system rotation lock is on. A horizontal text line keeps pinyin above and the gloss below even when a single character slice is taller than it is wide. A vertical column keeps pinyin beside the column. Label text is rotated by the difference between that hold and the current window, so a landscape hold does not leave pinyin and Russian written vertically.
12. The APK works fully offline after install: models, dictionary, and pinyin data are bundled. No runtime download. No `INTERNET` permission.
13. minSdk 34, compile/targetSdk 36; Kotlin + Jetpack Compose + Material 3.
14. First camera use requests `CAMERA`. Denial explains why and offers another request plus a control that opens app settings. Gallery stays available.
15. Sign release (and debug when the keystore is present) with the shared committed sideload keystore. Publish a GitHub Pages landing at `/instant-pinyin/` with `instant-pinyin.apk`.
16. **Настройки** holds one multiline field. Every hanzi in that field is a Known Hanzi. **Сохранить** writes the field. Punctuation and other letters in the field do not count.
17. A Known Hanzi does not show pinyin, on the live overlay or on a still. The character stays visible and still opens Pleco. A Russian gloss is omitted when every hanzi in that word (or leftover character) is known. A mixed word keeps the gloss and hides pinyin only on the known characters.
18. The live reader has a **Только известные** checkbox, unchecked by default and remembered. When it is on, unknown hanzi are left out of the overlay and out of the still. Punctuation stays.
19. The live reader has a **Только пиньинь** checkbox, unchecked by default and remembered. When it is on, Russian glosses are hidden on the live overlay and on a still. Pinyin, known-hanzi hiding, and Pleco taps on characters stay.
20. The live reader draws a recognition frame. The area outside the frame is dimmed. White handles resize the frame; the amber handle on the top edge moves it. The frame cannot shrink below 18% of the preview on either axis. OCR runs only on the upright-bitmap slice that matches the frame (the same center-crop the preview uses). Dragging is remembered. Default inset: 6% from the left and right, 14% from the top, 22% from the bottom.

## Interfaces

| Interface | Detail |
| --------- | ------ |
| Launcher activity | `com.tepmex.instantpinyin.MainActivity` |
| OCR models | `assets/ocr/ppocr_det_fp16.tflite`, `assets/ocr/ppocr_rec_fp16.tflite` |
| OCR charset | `assets/ocr/ppocrv5_dict.txt` (CTC: blank + dict lines + space) |
| Word glosses | `assets/dict/words.txt` — 12 500 entries, `hanzi<TAB>russian` (live overlay and still) |
| Character glosses | `assets/dict/chars.txt` — 6 500 entries, `hanzi<TAB>russian` (live overlay and still) |
| Pleco search | `plecoapi://x-callback-url/s?q={query}&x-source=instant-pinyin` (one character, or the whole word when the gloss is tapped) |
| Camera | `android.permission.CAMERA`; CameraX `Preview` + `ImageAnalysis` (`STRATEGY_KEEP_ONLY_LATEST`, RGBA) + `ImageCapture` |
| Gallery | `ActivityResultContracts.PickVisualMedia` (images only); no extra storage permission |
| Preview | `PreviewView` `FILL_CENTER`, `COMPATIBLE` (so the overlay draws above the camera) |
| Gradle | `./gradlew assembleRelease` → `app/build/outputs/apk/release/app-release.apk` |
| Pages download | `https://<host>/<repo>/instant-pinyin/instant-pinyin.apk` |
| Known Hanzi | DataStore `instant_pinyin`, key `known_hanzi_text` |
| Only Known | DataStore `instant_pinyin`, key `only_known` (boolean, default `false`) |
| Pinyin only | DataStore `instant_pinyin`, key `pinyin_only` (boolean, default `false`) |
| Recognition zone | DataStore `instant_pinyin`, keys `zone_left`, `zone_top`, `zone_right`, `zone_bottom` (view fractions, default inset) |

No accounts, no incoming URL scheme in v1. Known Hanzi, Only Known, Pinyin only, and the recognition zone are the persisted state.

## Data model

- **Live Frame** — one upright viewport bitmap from the camera (buffer crop, rotated by `ImageProxy` rotation).
- **Text Line** — OCR text plus its axis-aligned box in Live Frame pixels.
- **Reading Glyph** — one hanzi from a Text Line, its toned pinyin, and the slice of the line box that belongs to that character.
- **Pinyin Label** — a Reading Glyph mapped into view pixels, plus the pill rectangle drawn above (horizontal) or beside (vertical) it.
- **Gloss** — Russian text for one greedy word (or one leftover character) and the pill under that span (beside it when the span is vertical).
- **Track** — a Reading Glyph kept across frames. A matched detection eases the box toward the new one. One missed frame keeps the track; the next miss drops it.
- **Still** — one captured or gallery image plus the OCR lines read from it once. It replaces the live overlay until the user goes back. The photo is not stored.
- **Text Token** — one word of at most four hanzi, one hanzi, or one punctuation mark from a Still line, in reading order. The word match is the same greedy lexicon as the live overlay. Pinyin sits above each character. The Russian gloss sits under the word. Tapping a character opens that character; tapping the gloss opens the word surface.

- **Known Text** — the raw string saved from Settings. Only the hanzi in it are Known Hanzi.
- **Known Hanzi** — one ideograph from Known Text. Its pinyin is not drawn. A word made only of Known Hanzi has no Russian gloss.
- **Only Known** — the main-screen checkbox. When on, the overlay and the still keep Known Hanzi and drop the rest.
- **Pinyin only** — the main-screen checkbox. When on, Russian glosses are not drawn.
- **Recognition zone** — a rectangle in preview fractions. Live OCR sees only the matching slice of the upright frame.

Closing the app drops the overlay and the still. Known Text, Only Known, Pinyin only, and the recognition zone stay.

## UI / UX

1. Cold start without camera permission → short explanation and **Разрешить камеру**. After a denial, **Открыть настройки** is also shown. **Из галереи** stays available.
2. With permission → camera fills the screen. While the model loads, a bottom chip says **Загружаю модель…**. With no characters in frame, **Наведите камеру на иероглифы**.
3. Recognized hanzi keep the real character visible. Pinyin is a light-on-dark pill just above it (or beside it for a vertical column). The Russian gloss is a warm pill under the word (or on the other side of a vertical column).
4. Torch button at the top end. Settings at the top start, with **Только известные** and **Только пиньинь** under it. A white frame marks the recognition zone; drag a handle to change it. Tap a character to open Pleco. Bottom of the live reader: **Сфотографировать** and **Из галереи**.
5. Still reading: title **Текст**. Each line wraps. Word cells are wider than character cells. Pinyin is above each character, Russian under the word. Tap a character for that character; tap the translation for the word. Empty result: **Текст не найден**.
6. Live labels stay upright for the way the phone is held. Horizontal text keeps horizontal pinyin and Russian; turning the phone sideways does not leave those labels written vertically.
7. Landing page: brand 即时拼音 / instant-pinyin, APK download, update note.

## Out of scope

- Share sheets (those stay in paizhao-unknown-hanzi)
- Full dictionary senses, examples, or traditional-character glosses (the overlay and the still view show one short Russian phrase; Pleco remains the full dictionary)
- Segmenting across separate OCR lines
- An in-app dictionary browser
- ARCore world anchors; registration is the camera image
- Cloud OCR, accounts, sync, analytics
- Traditional/simplified conversion
- Play Store / App Bundle
- Incoming deep links

## Acceptance criteria

1. OCR line `你好` in a 100×40 box yields two Reading Glyphs, `你` then `好`, each half the width, each with toned pinyin.
2. OCR line `你，好` keeps three slots so `好` occupies the last third. A space in `你 好` does not take a slot.
3. A tall box splits top to bottom in recognition order.
4. A horizontal line's glyph with room above it gets a label whose bottom edge is at or above the glyph top, even when that slice is taller than it is wide. A glyph from a vertical line gets a label to its right.
5. Center-crop mapping sends frame pixels onto the view the way `FILL_CENTER` does. A 90° buffer crop maps into the upright bitmap (a top-left buffer quarter becomes the top-right of the upright image).
6. One empty detection keeps the previous glyph; the next empty detection drops it. A matched box moves partway toward the new rectangle.
7. A tap target for `汉` builds `plecoapi://x-callback-url/s?q=%E6%B1%89&x-source=instant-pinyin`. Pinyin uses tone marks; third tone is a caron (`你` → `nǐ`), not the breve pinyin4j emits.
8. Greedy gloss on the live overlay: `你好世界` is one 4-hanzi hit when that word is in the lexicon; `中国人` is one 3-hanzi hit even if `中国` is also present; `我们` plus a following character uses the 2-hanzi word and then that character's own gloss. A wide gap does not join `你` and `好`. A horizontal word's gloss pill starts at or below the word. A character missing from both lists draws no gloss.
9. Forward maximum matching on a still of `你好朋友` with dictionary entries `你好` and `朋友` yields two word tokens. The gloss opens `plecoapi://x-callback-url/s?q=%E4%BD%A0%E5%A5%BD&x-source=instant-pinyin`; the first character opens `你`. `研究生命` with both `研究生` and `生命` present keeps `研究生` and then `命`. A five-hanzi dictionary entry is not one cell. A comma between words is not a Pleco target. Pinyin is per character; the caption under the word is the Russian gloss (`привет`).
10. A horizontal OCR line marks its glyphs horizontal even when each slice is taller than it is wide, so pinyin sits above. A portrait window with the phone held so its left side is up rotates label text 270°. When the window already matches that hold, label text stays at 0°.
11. `./gradlew test assembleRelease` succeeds and the APK verifies with `android/verify-apk-sideload-cert.sh`.
12. The release APK contains the two `.tflite` models, `ppocrv5_dict.txt`, `dict/words.txt`, and `dict/chars.txt`; the app declares no `INTERNET` permission.
13. Deploy workflow includes `instant-pinyin` in `ANDROID_APPS` and rebuilds when `instant-pinyin/**` changes.
14. Known Text `你好世界` marks those four characters. On a still of `你好朋友`, `你` and `好` have no pinyin and `你好` has no Russian gloss; `朋` and `友` keep pinyin and `朋友` keeps its gloss. With Only Known on, `朋友` is left out and `你好` remains, still without pinyin or gloss. A known live glyph keeps a tap on the character and draws no pinyin pill.
15. On one horizontal line, `你` `好` `吗` place `好` on the upper tier and `你` / `吗` on the lower tier, and no two pills intersect. Two long readings on the same tier shrink until their pills no longer meet. A vertical column puts the middle reading one column farther out.
16. Dragging the recognition frame keeps it inside the preview and at least 18% wide. A full-frame window does not crop the bitmap. A left-half window on a center-cropped preview maps to the visible left half of the upright image.
