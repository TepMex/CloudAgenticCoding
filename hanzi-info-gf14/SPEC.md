# hanzi-info-gf14 (汉字 GF14) — SPEC

## Purpose

Offline Android map of GF 0014-2009 components for the 3500 most frequent characters (通用规范汉字表, 一级字表). The learner types or receives a character and sees it in the center of a hexagonal grid. Each of up to six directions is one component of the center: along that ray are the other characters in the list that differ from the center by replacing that component and nothing else.

Audience: a Mandarin learner who already jumps between apps (AnkiDroid, Pleco, readers) and wants those apps to open this map on a specific character.

## Requirements

1. Project folder **hanzi-info-gf14**; launcher name **汉字 GF14**; application id `com.tepmex.hanziinfogf14`.
2. Bundle `hanzi_components_gf0014_6152.json` (3500 characters, component lists normalized to GF 0014). The app works offline after install. No `INTERNET` permission.
3. A text field accepts a character. The latest ideograph in the field becomes the center. Pasting several ideographs uses the last one.
4. The center cell sits at the origin of a pointy-top hexagonal grid. Up to six rays leave it, in this order: east, west, northeast, southwest, northwest, southeast.
5. A neighbor differs from the center by **exactly one component substitution**: the neighbor’s component multiset is the center’s multiset with one occurrence of a component replaced by a different component. Characters that share the same multiset (for example 古 and 叶, both 十+口) are not neighbors. Adding or deleting a component without a replacement is not a neighbor (木 and 林 are not neighbors).
6. Neighbors that replace the **same** center component form one ray, in catalog order (the frequency order of the JSON object). The ray is labeled with that component. IDS placeholders that are not a single code point are shown as ◌.
7. If more than six components have neighbors, keep the six rays with the most characters. Ties keep the earlier component in the center’s component list. This dataset never exceeds three rays; the cap is the grid.
8. Tap a cell to move that character to the center and rebuild the rays. Back (system or **Назад**) returns along that trail. Drag pans the grid; pinch zooms it. Changing the center recenters the view.
9. The screen shows the center’s component list and, for each visible ray, the direction arrow, the replaced component, and the number of characters.
10. If the character is not in the 3500, show it alone with «Нет в списке GF 0014». If it is in the list but has no substitutions, show it alone with «Нет иероглифов с заменой одного компонента».
11. Incoming x-callback-url opens the app on a character. `singleTop`: a new link updates the open activity. `x-source`, `x-success`, `x-error`, and `x-cancel` are accepted and not invoked.
12. **Ссылка** copies `hanziinfogf14://x-callback-url?q={hanzi}` for the current center (UTF-8 percent-encoding).
13. minSdk 34, compileSdk/targetSdk 36. Kotlin, Jetpack Compose, Material 3.
14. Sign release and debug (when the keystore is present) with the shared sideload keystore. GitHub Pages landing at `/hanzi-info-gf14/` serves `hanzi-info-gf14.apk`.

## Interfaces

| Interface | Detail |
| --------- | ------ |
| Launcher / deep-link activity | `com.tepmex.hanziinfogf14.MainActivity` (`singleTop`) |
| Open on a character | `hanziinfogf14://x-callback-url?q={hanzi}` |
| Action path | `hanziinfogf14://x-callback-url/open?q={hanzi}` |
| Path forms | `hanziinfogf14://x-callback-url/q={hanzi}` and `hanziinfogf14://x-callback-url/q/{hanzi}` |
| Query character | first CJK ideograph in `q` (unified, Ext. A–H, compatibility) |
| Catalog asset | `assets/hanzi_components_gf0014_6152.json` |
| Gradle | `./gradlew test assembleRelease` |
| Pages download | `https://<host>/<repo>/hanzi-info-gf14/hanzi-info-gf14.apk` |

No accounts, no outgoing dictionary app, no network.

## Data model

- **Character** — one key of `characters` in the JSON (3500 level-1 hanzi).
- **Components** — the ordered multiset in `characters[hanzi].components`. Duplicate entries count (林 is 木, 木). Structure trees and `unresolved` are not used for matching.
- **Substitution** — remove one occurrence of component C from the center and add a different component D.
- **Branch** — all catalog characters produced by substituting the same C, in catalog order.
- **Cell** — axial hex coordinate. Center is `(0,0)`. Step k of spoke i is `k * direction[i]`.

Nothing is persisted. The back stack lives in the process.

## UI / UX

1. Cold start: title 汉字 GF14, empty field, short explanation.
2. After a character: component line (`讠 + 龶 + 月`), one chip per ray (`→ 讠 9`), then the grid. The center cell is filled; each ray has its own stroke color; a small badge beside the first step shows the replaced component.
3. Long rays (a single-component character such as 木 has one ray of every other single-component character) are reached by panning and pinching.
4. **Назад** appears after the user has moved to another character.
5. Landing page: 汉字 GF14 / hanzi-info-gf14, the x-callback-url, APK download, update note.

## Out of scope

- Definitions, pinyin readings, or opening Pleco
- Decomposition trees beyond the flat component multiset
- Editing the catalog
- Traditional/simplified conversion
- Play Store / App Bundle
- Calling `x-success` (the screen stays on the map)

## Acceptance criteria

1. `请` (讠, 龶, 月) has one east ray, component 讠, characters `猜清情晴睛靖静蜻精` in that order at axial `(1,0)` … `(9,0)`.
2. `好` has an east ray for 女 (first cell 孔) and a west ray for 子 (first cell 奶, and the ray includes 她).
3. `清`’s 氵 ray includes `请`. `古` does not list `叶`. `木` does not list `林`.
4. Every neighbor in the bundled catalog is a single substitution of the ray’s component, each neighbor appears once, and no character has more than six rays.
5. `hanziinfogf14://x-callback-url?q=%E8%AF%B7` and `hanziinfogf14://x-callback-url/q/明` parse to `请` and `明`. `q=你好` uses `你`. A non-hanzi `q` parses to nothing. `format("请")` round-trips.
6. `./gradlew test assembleRelease` succeeds and the APK verifies with `android/verify-apk-sideload-cert.sh`.
7. Deploy workflow includes `hanzi-info-gf14` in `ANDROID_APPS`.
