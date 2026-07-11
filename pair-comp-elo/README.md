# Pair Comp Elo

Android app for ranking arbitrary items through pairwise comparisons using an Elo rating system with time decay. Everything stays on-device — no accounts, analytics, ads, or network.

## Features

- Multiple named preference lists (books, films, restaurants, tasks, …)
- Add / edit / archive / delete / reorder items
- Fast pairwise item comparisons with undo
- Elo ratings with optional daily decay toward the initial rating
- Item rankings with stability indicators and sorting/search
- Compare lists against each other (separate list Elo ranking)
- Comparison history as source of truth; recalculate after settings changes
- JSON export / import via the Storage Access Framework
- Material 3, light/dark/dynamic color, edge-to-edge, phone & tablet

## Requirements

- Android 14 (API 34)+
- JDK 17+
- Android SDK 36

## Build

```bash
cd pair-comp-elo
# local.properties must contain sdk.dir=...
./gradlew assembleRelease
./gradlew test
```

APK: `app/build/outputs/apk/release/app-release.apk`

Release builds use the committed sideload keystore so CI and local builds share one signing key with the other monorepo Android apps.

## Architecture

Single `:app` module with clear packages:

| Package | Role |
| --- | --- |
| `domain` | Models, Elo, decay, pair selection, recalculation, validation |
| `data` | Room, DataStore, repositories, import/export |
| `di` | Hilt modules |
| `feature.*` | Compose screens + ViewModels (UDF) |
| `ui` | Theme, navigation, shared components |
| `core` | Clock abstraction |

Repositories expose `Flow` APIs. ViewModels never touch DAOs. Elo / decay / pairing / recalculation are pure domain code with unit tests.

### Navigation

```
Home
 ├─ List edit / create
 ├─ List detail
 │   ├─ Item edit
 │   ├─ Item compare
 │   ├─ Item ranking
 │   └─ Item history
 ├─ Archived lists
 ├─ List compare
 ├─ List ranking
 ├─ Global history
 └─ Settings (recalc, import/export, wipe)
```

### Database schema (Room v1)

- `preference_lists` — list metadata + Elo fields
- `preference_items` — items (FK → list, CASCADE delete) + Elo/W-L/skip + `sort_order`
- `item_comparisons` — history (FK → list & items, CASCADE) + rating snapshots + `is_reverted`
- `list_comparisons` — list-vs-list history + snapshots + `is_reverted`

Schema JSON is exported to `app/schemas/` for migration testing. Production upgrades use explicit `Migration` objects (not destructive migration).

## Elo

```
expectedA = 1 / (1 + 10 ^ ((ratingB - ratingA) / ratingScale))
newRating = rating + kFactor * (actual - expected)
```

Actual scores: win `1.0`, draw `0.5`, loss `0.0`. Skips do not change ratings.

## Decay

Before each comparison (and during history replay):

```
decayed = initial + decayRatePerDay ^ elapsedDays * (current - initial)
```

- `elapsedDays` may be fractional
- Future timestamps → zero elapsed (no reverse decay)
- Decay reduces the influence of older comparisons; it does not fully model context-dependent preferences

## Pair selection

Strategies: Random, Similar rating, Least compared, **Balanced adaptive** (default).

Balanced adaptive weights fewer comparisons, closer ratings, avoiding recent pairs, and few head-to-head meetings.

## Import / export JSON

Schema version `1` bundle includes settings, lists, items, item comparisons, list comparisons, and `exportedAt`.

- **Replace** — wipe then load
- **Merge** — preserve UUIDs, skip duplicates, validate references, report skips

## Testing

```bash
./gradlew test                 # unit + Robolectric DB tests
./gradlew connectedDebugAndroidTest  # device/emulator UI smoke
```

Unit coverage includes Elo, decay, pairing, recalculation, validation, and repository transactions (Robolectric).

## Known limitations

- Settings are global (list-specific overrides are designed for later)
- Soft-reverted comparisons remain in the DB until permanently deleted
- Large recalculations show a blocking progress indicator
- Tablet layouts adapt via responsive Compose, not separate resource qualifiers

## Future ideas

- List-specific ranking settings
- Widgets / quick-compare shortcuts
- CSV export
- Optional cloud sync chosen by the user

## Privacy

All data stays on the device unless you explicitly export it. No network permission.
