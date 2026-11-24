# hanzi-info-gf14 (汉字 GF14) — SPEC

## Purpose

Offline Android map of GF 0014-2009 components for the 3500 most frequent characters (通用规范汉字表, 一级字表). The learner types or receives a character and sees it in the center of a hexagonal grid. Each of up to six directions is one component of the center: along that ray are the other characters in the list that differ from the center by replacing that component and nothing else.

Audience: a Mandarin learner who already jumps between apps (AnkiDroid, Pleco, readers) and wants those apps to open this map on a specific character.

## Requirements

1. Project folder **hanzi-info-gf14**; launcher name **汉字 GF14**; application id `com.tepmex.hanziinfogf14`.
2. Bundle `hanzi_components_gf0014_6152.json` (3500 characters, component lists normalized to GF 0014). Component rays work offline after install.
3. A text field accepts a character. The latest ideograph in the field becomes the center. Pasting several ideographs uses the last one.
4. The center cell sits at the origin of a pointy-top hexagonal grid. Up to six rays leave it, in this order: east, west, northeast, southwest, northwest, southeast.
5. A neighbor differs from the center by **exactly one component substitution**: the neighbor’s component multiset is the center’s multiset with one occurrence of a component replaced by a different component. Characters that share the same multiset (for example 古 and 叶, both 十+口) are not neighbors. Adding or deleting a component without a replacement is not a neighbor (木 and 林 are not neighbors).
6. Neighbors that replace the **same** center component form one ray, in catalog order (the frequency order of the JSON object). The ray is labeled with that component. IDS placeholders that are not a single code point are shown as ◌.
7. If more than six components have neighbors, keep the six rays with the most characters. Ties keep the earlier component in the center’s component list. This dataset never exceeds three rays; the cap is the grid.
8. Tap a cell to move that character to the center and rebuild the rays. Back (system or **Назад**) returns along that trail. Drag pans the grid; pinch zooms it. Changing the center recenters the view.
9. The screen shows the center’s component list and, for each visible ray, the direction arrow, the replaced component, and the number of characters.
10. If the character is not in the 3500, the component map shows it alone with «Нет в списке GF 0014». Lookalikes for that character follow requirement 17. If it is in the list but has no substitutions, show «Нет иероглифов с заменой одного компонента».
11. Incoming x-callback-url opens the app on a character. `singleTop`: a new link updates the open activity. `x-source`, `x-success`, `x-error`, and `x-cancel` are accepted and not invoked.
12. **Ссылка** copies `hanziinfogf14://x-callback-url?q={hanzi}` for the current center (UTF-8 percent-encoding).
13. minSdk 34, compileSdk/targetSdk 36. Kotlin, Jetpack Compose, Material 3.
14. Sign release and debug (when the keystore is present) with the shared sideload keystore. GitHub Pages landing at `/hanzi-info-gf14/` serves `hanzi-info-gf14.apk`.
15. Bundle `glyph_embeddings.bin`: one L2-normalized 768-d embedding per catalog character, in catalog order. Each character is drawn as a 224×224 picture — Noto Sans CJK SC Regular (SIL OFL, bundled for characters outside the list), black on white, font size 156, the ink box centered — and embedded with EmbeddingGemma 2 text-vision 440M LiteRT (`embeddinggemma-2-text-vision-440m.litertlm`, revision `538e6574db281b4fe17993a3212f10c73bd678c6`, 70 vision tokens, output size 768). The model file is not in the APK.
16. Spokes that a visible component ray does not use are free. On each free spoke, at distance 1, show the next nearest catalog character by cosine distance of these image embeddings. Nearest first, in the usual spoke order. Skip the center and any character already standing on a visible ray. A tie keeps the earlier catalog character. Six full rays leave no free spoke.
17. **Настройки** has **Скачать модель**. The button downloads that same LiteRT file (387710976 bytes) into app storage. `INTERNET` is only for this download. Characters in the 3500 do not need the file. A character outside the list is rendered with the bundled font and embedded on device when the file is present; its nearest catalog characters fill the free spokes. Without the file, that character stays alone and the screen says the model is needed.
18. **Удалить модель** removes the downloaded file. A body shorter than 387710976 bytes is not kept.

## Interfaces

| Interface | Detail |
| --------- | ------ |
| Launcher / deep-link activity | `com.tepmex.hanziinfogf14.MainActivity` (`singleTop`) |
| Open on a character | `hanziinfogf14://x-callback-url?q={hanzi}` |
| Action path | `hanziinfogf14://x-callback-url/open?q={hanzi}` |
| Path forms | `hanziinfogf14://x-callback-url/q={hanzi}` and `hanziinfogf14://x-callback-url/q/{hanzi}` |
| Query character | first CJK ideograph in `q` (unified, Ext. A–H, compatibility) |
| Catalog asset | `assets/hanzi_components_gf0014_6152.json` |
| Glyph embeddings | `assets/glyph_embeddings.bin` (magic `HZE1`) |
| Glyph font | `assets/fonts/NotoSansCJKsc-Regular.otf` |
| Model download | Hugging Face revision `538e6574db281b4fe17993a3212f10c73bd678c6`, file `embeddinggemma-2-text-vision-440m.litertlm` |
| Rebuild embeddings | `tools/build_glyph_embeddings.py` |
| Gradle | `./gradlew test assembleRelease` |
| Pages download | `https://<host>/<repo>/hanzi-info-gf14/hanzi-info-gf14.apk` |

No accounts and no outgoing dictionary app. The only network call is the model download in settings.

## Data model

- **Character** — one key of `characters` in the JSON (3500 level-1 hanzi).
- **Components** — the ordered multiset in `characters[hanzi].components`. Duplicate entries count (林 is 木, 木). Structure trees and `unresolved` are not used for matching.
- **Substitution** — remove one occurrence of component C from the center and add a different component D.
- **Branch** — all catalog characters produced by substituting the same C, in catalog order.
- **Cell** — axial hex coordinate. Center is `(0,0)`. Step k of spoke i is `k * direction[i]`.
- **Glyph embedding** — the 768-d vector of that character’s picture. Cosine distance is `1 − dot` after L2 normalization.
- **Lookalike** — a catalog character placed on a free spoke because its glyph embedding is close to the center’s.

The component catalog and the glyph database are assets. The downloaded LiteRT file lives in app storage. The back stack lives in the process.

## UI / UX

1. Cold start: title 汉字 GF14, empty field, short explanation.
2. After a character: component line (`讠 + 龶 + 月`), one chip per ray (`→ 讠 9`), then lookalike chips (`≈ 字`) for the free spokes, then the grid. The center cell is filled; each component ray has its own stroke color; a small badge beside the first step shows the replaced component. A free spoke uses one shared stroke and an ≈ badge.
3. Long rays (a single-component character such as 木 has one ray of every other single-component character) are reached by panning and pinching.
4. **Назад** appears after the user has moved to another character.
5. **Настройки** explains that the 3500 embeddings are already in the app and offers the model download, progress, and delete.
6. Landing page: 汉字 GF14 / hanzi-info-gf14, the x-callback-url, APK download, update note.

## Out of scope

- Definitions, pinyin readings, or opening Pleco
- Decomposition trees beyond the flat component multiset
- Editing the catalog
- Traditional/simplified conversion
- Play Store / App Bundle
- Calling `x-success` (the screen stays on the map)
- Shipping the LiteRT weights inside the APK
- Re-embedding the 3500 on the device

## Acceptance criteria

1. `请` (讠, 龶, 月) has one east ray, component 讠, characters `猜清情晴睛靖静蜻精` in that order at axial `(1,0)` … `(9,0)`.
2. `好` has an east ray for 女 (first cell 孔) and a west ray for 子 (first cell 奶, and the ray includes 她).
3. `清`’s 氵 ray includes `请`. `古` does not list `叶`. `木` does not list `林`.
4. Every neighbor in the bundled catalog is a single substitution of the ray’s component, each neighbor appears once, and no character has more than six rays.
5. `hanziinfogf14://x-callback-url?q=%E8%AF%B7` and `hanziinfogf14://x-callback-url/q/明` parse to `请` and `明`. `q=你好` uses `你`. A non-hanzi `q` parses to nothing. `format("请")` round-trips.
6. `./gradlew test assembleRelease` succeeds and the APK verifies with `android/verify-apk-sideload-cert.sh`.
7. Deploy workflow includes `hanzi-info-gf14` in `ANDROID_APPS`.
8. `glyph_embeddings.bin` has 3500 vectors of dimension 768 in catalog order, from `一` through `矗`. Every vector is finite and has length 1 after load. A character is not its own nearest neighbor. `未`’s nearest is `末`, `士`’s nearest is `土`, and `己`’s nearest is `已`.
9. For `请`, the east ray is unchanged (`猜` at `(1,0)`). The free spokes at distance 1 are `诉` west, `讳` northeast, `询` southwest, `祈` northwest, `谢` southeast. None of those five is on the 讠 ray.
10. A center with six visible rays has no lookalike cell. A lookalike that is already on a ray is not drawn again.
11. The downloader keeps a file only when its length is the expected byte count. A short body leaves no destination and no partial file.
