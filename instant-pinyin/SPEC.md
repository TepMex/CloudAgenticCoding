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
   - Preview mapping is center-crop (`FILL_CENTER`), the same scale the preview uses, including the CameraX viewport crop rotated into upright bitmap space.
   - A single missed detection keeps the previous label for one frame so the overlay does not flicker off.
6. Tapping a character or its label opens Pleco via `plecoapi://x-callback-url/s?q={hanzi}&x-source=instant-pinyin`. If Pleco is missing, show a snackbar instead of crashing.
7. A torch control toggles the back-camera flashlight.
8. **Сфотографировать** takes one still with CameraX `ImageCapture`. **Из галереи** opens the system photo picker. Either path leaves the live overlay and runs OCR once on that image (static reading, not a stream of frames). Gallery works without the camera permission. The back control returns to the live reader.
9. The still result is the recognized text in reading order, not a grid of unique characters. Each OCR line is a wrapping row. Consecutive hanzi are grouped by forward maximum matching against the bundled lexicon: a match of two or more characters is one word cell; everything else is a single-character cell. Cells are as wide as their text. Punctuation stays between cells and is not tappable.
10. A word or character cell shows toned pinyin above the surface and a short Russian gloss below (an em dash when the lexicon has no gloss). Tapping a word cell opens Pleco with that word. Tapping a character cell opens Pleco with that character.
11. The APK works fully offline after install: models, dictionary, and pinyin data are bundled. No runtime download. No `INTERNET` permission.
12. minSdk 34, compile/targetSdk 36; Kotlin + Jetpack Compose + Material 3.
13. First camera use requests `CAMERA`. Denial explains why and offers another request plus a control that opens app settings. Gallery stays available.
14. Sign release (and debug when the keystore is present) with the shared committed sideload keystore. Publish a GitHub Pages landing at `/instant-pinyin/` with `instant-pinyin.apk`.

## Interfaces

| Interface | Detail |
| --------- | ------ |
| Launcher activity | `com.tepmex.instantpinyin.MainActivity` |
| OCR models | `assets/ocr/ppocr_det_fp16.tflite`, `assets/ocr/ppocr_rec_fp16.tflite` |
| OCR charset | `assets/ocr/ppocrv5_dict.txt` (CTC: blank + dict lines + space) |
| Pleco search | `plecoapi://x-callback-url/s?q={query}&x-source=instant-pinyin` (character or whole word) |
| Word lexicon | `assets/lexicon/words.tsv` — simplified word, toned pinyin, Russian gloss (may be empty). Built by `scripts/build-lexicon.py`. See `NOTICE.md`. |
| Camera | `android.permission.CAMERA`; CameraX `Preview` + `ImageAnalysis` (`STRATEGY_KEEP_ONLY_LATEST`, RGBA) + `ImageCapture` |
| Gallery | `ActivityResultContracts.PickVisualMedia` (images only); no extra storage permission |
| Preview | `PreviewView` `FILL_CENTER`, `COMPATIBLE` (so the overlay draws above the camera) |
| Gradle | `./gradlew assembleRelease` → `app/build/outputs/apk/release/app-release.apk` |
| Pages download | `https://<host>/<repo>/instant-pinyin/instant-pinyin.apk` |

No accounts, no persistence, no incoming URL scheme in v1.

## Data model

- **Live Frame** — one upright viewport bitmap from the camera (buffer crop, rotated by `ImageProxy` rotation).
- **Text Line** — OCR text plus its axis-aligned box in Live Frame pixels.
- **Reading Glyph** — one hanzi from a Text Line, its toned pinyin, and the slice of the line box that belongs to that character.
- **Pinyin Label** — a Reading Glyph mapped into view pixels, plus the pill rectangle drawn above (horizontal) or beside (vertical) it.
- **Track** — a Reading Glyph kept across frames. A matched detection eases the box toward the new one. One missed frame keeps the track; the next miss drops it.
- **Still** — one captured or gallery image plus the OCR lines read from it once. It replaces the live overlay until the user goes back. The photo is not stored.
- **Text Token** — one word, one hanzi, or one punctuation mark from a Still line, in reading order. A word token is a lexicon match of two or more characters (forward maximum matching). Its Pleco query is the whole surface. A character token’s query is that character. Pinyin sits above the surface; the Russian gloss sits below.

Nothing is stored. Closing the app drops the overlay and the still.

## UI / UX

1. Cold start without camera permission → short explanation and **Разрешить камеру**. After a denial, **Открыть настройки** is also shown. **Из галереи** stays available.
2. With permission → camera fills the screen. While the model loads, a bottom chip says **Загружаю модель…**. With no characters in frame, **Наведите камеру на иероглифы**.
3. Recognized hanzi keep the real character visible. Pinyin is a light-on-dark pill just above it (or beside it for a vertical column).
4. Torch button at the top end. Tap a character to open Pleco. Bottom of the live reader: **Сфотографировать** and **Из галереи**.
5. Still reading: title **Текст**. Each line wraps. Word cells are wider than character cells. Pinyin is on top, Russian under the characters. Tap opens Pleco for the word when the cell is a word, otherwise for the character. Empty result: **Текст не найден**.
6. Landing page: brand 即时拼音 / instant-pinyin, APK download, update note.

## Out of scope

- Known-hanzi lists and share sheets (those stay in paizhao-unknown-hanzi)
- An in-app dictionary browser (Pleco remains the dictionary; the still view only shows a short Russian gloss)
- ARCore world anchors; registration is the camera image
- Cloud OCR, accounts, sync, analytics
- Traditional/simplified conversion
- Play Store / App Bundle
- Incoming deep links

## Acceptance criteria

1. OCR line `你好` in a 100×40 box yields two Reading Glyphs, `你` then `好`, each half the width, each with toned pinyin.
2. OCR line `你，好` keeps three slots so `好` occupies the last third. A space in `你 好` does not take a slot.
3. A tall box splits top to bottom in recognition order.
4. A horizontal glyph with room above it gets a label whose bottom edge is at or above the glyph top. A tall glyph gets a label to its right.
5. Center-crop mapping sends frame pixels onto the view the way `FILL_CENTER` does. A 90° buffer crop maps into the upright bitmap (a top-left buffer quarter becomes the top-right of the upright image).
6. One empty detection keeps the previous glyph; the next empty detection drops it. A matched box moves partway toward the new rectangle.
7. A tap target for `汉` builds `plecoapi://x-callback-url/s?q=%E6%B1%89&x-source=instant-pinyin`. Pinyin uses tone marks; third tone is a caron (`你` → `nǐ`), not the breve pinyin4j emits.
8. Forward maximum matching on `你好朋友` with lexicon entries `你好` and `朋友` yields two word tokens. Pleco for the first is `plecoapi://x-callback-url/s?q=%E4%BD%A0%E5%A5%BD&x-source=instant-pinyin`. `研究生命` with both `研究生` and `生命` present keeps `研究生` and then `命`. A comma between words is not a Pleco target. The word cell’s pinyin is the lexicon reading (`nǐ hǎo`) and the caption under it is the Russian gloss (`привет`).
9. `./gradlew test assembleRelease` succeeds and the APK verifies with `android/verify-apk-sideload-cert.sh`.
10. The release APK contains the two `.tflite` models, `ppocrv5_dict.txt`, and `lexicon/words.tsv`; the app declares no `INTERNET` permission.
11. Deploy workflow includes `instant-pinyin` in `ANDROID_APPS` and rebuilds when `instant-pinyin/**` changes.
