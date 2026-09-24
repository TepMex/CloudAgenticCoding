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
8. The APK works fully offline after install: models, dictionary, and pinyin data are bundled. No runtime download. No `INTERNET` permission.
9. minSdk 34, compile/targetSdk 36; Kotlin + Jetpack Compose + Material 3.
10. First camera use requests `CAMERA`. Denial explains why and offers another request plus a control that opens app settings.
11. Sign release (and debug when the keystore is present) with the shared committed sideload keystore. Publish a GitHub Pages landing at `/instant-pinyin/` with `instant-pinyin.apk`.

## Interfaces

| Interface | Detail |
| --------- | ------ |
| Launcher activity | `com.tepmex.instantpinyin.MainActivity` |
| OCR models | `assets/ocr/ppocr_det_fp16.tflite`, `assets/ocr/ppocr_rec_fp16.tflite` |
| OCR charset | `assets/ocr/ppocrv5_dict.txt` (CTC: blank + dict lines + space) |
| Word glosses | `assets/dict/words.txt` — 12 500 entries, `hanzi<TAB>russian` |
| Character glosses | `assets/dict/chars.txt` — 6 500 entries, `hanzi<TAB>russian` |
| Pleco search | `plecoapi://x-callback-url/s?q={query}&x-source=instant-pinyin` |
| Camera | `android.permission.CAMERA`; CameraX `Preview` + `ImageAnalysis` (`STRATEGY_KEEP_ONLY_LATEST`, RGBA) |
| Preview | `PreviewView` `FILL_CENTER`, `COMPATIBLE` (so the overlay draws above the camera) |
| Gradle | `./gradlew assembleRelease` → `app/build/outputs/apk/release/app-release.apk` |
| Pages download | `https://<host>/<repo>/instant-pinyin/instant-pinyin.apk` |

No accounts, no persistence, no incoming URL scheme in v1.

## Data model

- **Live Frame** — one upright viewport bitmap from the camera (buffer crop, rotated by `ImageProxy` rotation).
- **Text Line** — OCR text plus its axis-aligned box in Live Frame pixels.
- **Reading Glyph** — one hanzi from a Text Line, its toned pinyin, and the slice of the line box that belongs to that character.
- **Pinyin Label** — a Reading Glyph mapped into view pixels, plus the pill rectangle drawn above (horizontal) or beside (vertical) it.
- **Gloss** — Russian text for one greedy word (or one leftover character) and the pill under that span (beside it when the span is vertical).
- **Track** — a Reading Glyph kept across frames. A matched detection eases the box toward the new one. One missed frame keeps the track; the next miss drops it.

Nothing is stored. Closing the app drops the overlay.

## UI / UX

1. Cold start without camera permission → short explanation and **Разрешить камеру**. After a denial, **Открыть настройки** is also shown.
2. With permission → camera fills the screen. While the model loads, a bottom chip says **Загружаю модель…**. With no characters in frame, **Наведите камеру на иероглифы**.
3. Recognized hanzi keep the real character visible. Pinyin is a light-on-dark pill just above it (or beside it for a vertical column). The Russian gloss is a warm pill under the word (or on the other side of a vertical column).
4. Torch button at the top end. Tap a character to open Pleco.
5. Landing page: brand 即时拼音 / instant-pinyin, APK download, update note.

## Out of scope

- Known-hanzi lists, result grids, gallery stills, share sheets (those stay in paizhao-unknown-hanzi)
- Full dictionary senses, examples, or traditional-character glosses (the overlay shows one short Russian phrase; Pleco remains the full dictionary)
- Segmenting across separate OCR lines
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
7a. Greedy gloss: `你好世界` is one 4-hanzi hit when that word is in the lexicon; `中国人` is one 3-hanzi hit even if `中国` is also present; `我们` plus a following character uses the 2-hanzi word and then that character's own gloss. A wide gap does not join `你` and `好`. A horizontal word's gloss pill starts at or below the word. A character missing from both lists draws no gloss.
8. `./gradlew test assembleRelease` succeeds and the APK verifies with `android/verify-apk-sideload-cert.sh`.
9. The release APK contains the two `.tflite` models and `ppocrv5_dict.txt`; the app declares no `INTERNET` permission.
10. Deploy workflow includes `instant-pinyin` in `ANDROID_APPS` and rebuilds when `instant-pinyin/**` changes.
