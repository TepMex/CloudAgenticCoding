# wo-de-luyou (我的旅游) — SPEC

## Purpose

Offline Android travel dictionary for a Russia→China 2026 trip. The user browses **large category tiles**, opens a list of **word cards** (hanzi + pinyin + Russian), copies either the characters or the pinyin with one tap, and jumps into **Pleco** via its x-callback-url scheme.

Audience: a Russian-speaking traveler who already uses Pleco and needs route-specific vocabulary (places, food, transport, survival phrases) without a network.

## Requirements

1. Display name **我的旅游**; project / Pages path **wo-de-luyou**; application id `com.tepmex.wodeluyou`.
2. Bundle the trip glossary from the source TSV (Категория, Город/регион, Русский, 中文, Pinyin, Примечание, Приоритет, Источник) as `assets/dictionary.tsv` and parse it at runtime. No network required.
3. Home screen is a scrollable grid of **large category tiles** (one tile per distinct `Категория` value, in first-appearance order). Each tile shows the category name and entry count.
4. Tapping a category opens a list of that category’s word cards, sorted by priority (★★★ first) then source order.
5. Each word card shows: large hanzi, pinyin, Russian gloss; optional region chip, priority stars, and usage note.
6. Tapping the hanzi copies **only** the Chinese text. Tapping the pinyin copies **only** the pinyin. Both actions confirm with a snackbar.
7. Each card has a control that opens Pleco with `plecoapi://x-callback-url/s?q={hanzi}` (first `/`-separated variant; UTF-8 percent-encoded) and `x-source=wo-de-luyou`. If Pleco is missing, show a snackbar instead of crashing.
8. Home screen search filters across hanzi, tone-less pinyin, Russian, region, note, and category; matching cards appear in place of the tile grid.
9. minSdk 34, compile/targetSdk 36; Kotlin + Jetpack Compose + Material 3.
10. Sign release (and debug when the keystore is present) with the shared committed sideload keystore. Publish a GitHub Pages landing at `/wo-de-luyou/` with `wo-de-luyou.apk`.

## Interfaces

| Interface | Detail |
| --------- | ------ |
| Launcher activity | `com.tepmex.wodeluyou.MainActivity` |
| Bundled glossary | `file:///android_asset/dictionary.tsv` (UTF-8, tab-separated, 8 columns) |
| Pleco search | `plecoapi://x-callback-url/s?q={query}&x-source=wo-de-luyou` |
| Clipboard | system clipboard; hanzi and pinyin are independent copy targets |
| Gradle | `./gradlew assembleRelease` → `app/build/outputs/apk/release/app-release.apk` |
| Pages download | `https://<host>/<repo>/wo-de-luyou/wo-de-luyou.apk` |

No accounts, no sync, no custom incoming URL scheme in v1.

## Data model

- **VocabEntry** — one TSV row: `id`, `category`, `region`, `russian`, `hanzi`, `pinyin`, `note`, `priorityStars` (count of `★`), `source`.
- **CategoryTile** — `name` + `count`, derived from entries, first-appearance order.
- **DictionaryCatalog** — immutable list of entries plus lookup by category and text search.
- Persistence: none. The glossary is read-only assets. Uninstall/reinstall does not lose user data because there is none.

## UI / UX

1. Cold start → home: title **我的旅游**, subtitle Китай 2026, search field, large two-column category tiles.
2. Category → word list with back navigation.
3. Word card: hanzi is the primary tap target (copy); pinyin is a secondary tap target (copy); Pleco is a labeled button.
4. Empty search / empty category: short Russian empty-state copy.
5. Landing page: brand 我的旅游 / wo-de-luyou, APK download, update note.

## Out of scope

- Audio / TTS, handwriting, SRS / Anki export
- Editing or adding entries in-app
- Maps, itinerary, or booking
- Play Store / App Bundle
- Incoming deep links from other apps
- Offline Pleco substitute (definitions stay in Pleco)

## Acceptance criteria

1. Parser loads all 174 TSV rows into 20 categories without dropping columns.
2. Home shows one large tile per category; tapping **Фраза** lists the 20 phrase cards.
3. Tapping 乌鲁木齐 copies that hanzi; tapping `Wūlǔmùqí` copies that pinyin.
4. Pleco control builds `plecoapi://x-callback-url/s?q=…` using the first hanzi variant (so `木赛来斯 / 穆塞莱斯` looks up `木赛来斯`).
5. Search `wulumuqi` finds Урумчи via tone-stripped pinyin.
6. `./gradlew test assembleRelease` succeeds and the APK verifies with `android/verify-apk-sideload-cert.sh`.
7. Deploy workflow includes `wo-de-luyou` in `ANDROID_APPS` and rebuilds when `wo-de-luyou/**` changes.
