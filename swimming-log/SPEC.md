# swimming-log — Specification

## Purpose

**swimming-log** is a personal Android app that imports swimming workouts from the Xiaomi Fitness / Mi Band cloud (same unofficial cloud API used by [running-log](../running-log) and [miband-bot](https://github.com/alexgetmancom/miband-bot)) and stores them locally as a swimming journal.

| Field | Value |
|-------|-------|
| App name | swimming-log |
| Package | `com.tepmex.swimminglog` |
| Min SDK | 34 (Android 14+) |
| Target / compile SDK | 35 |

It follows the running-log journal: Xiaomi sign-in, watermark sync, Room, and a newest-first list. Pace and heartbits are per **100 meters**. SWOLF replaces cadence. VO₂ max is not read, stored as a column, shown, or sent to the trainer prompt.

## Requirements

1. Sign in with a Xiaomi account via **browser Custom Tabs** or **in-app WebView** (preferred). Password + SMS remain as fallback when the device is untrusted; optional captcha during SMS send.
2. Persist auth tokens on device; reuse them across launches; refresh via `passToken` when possible.
3. Sync sport/workout records from Xiaomi Fitness cloud using watermark pagination (`/app/v1/data/get_sport_records_by_watermark`), targeting the signed-in user’s own UID.
4. Keep only **swimming** activities: `pool_swimming`, `open_swimming`, and `open_water_swimming` (alias seen on some exports).
5. Store each swim locally (Room) and show a journal list with:
   - **date** (local calendar date of start)
   - **distance** (meters; totals in the trainer prompt may use km)
   - **temp** (pace as min:sec per 100 m)
   - **average bpm**
   - **heartbits per 100 m** = `avg_bpm × (pace_sec_per_100m / 60)`
   - **SWOLF** (strokes + seconds for one pool length; lower is more efficient)
6. Manual **Sync** action from the journal screen.
7. Sign out clears stored credentials (journal data may remain until cleared/uninstall).
8. Compete-with-self highlighting for **SWOLF** and **heartbits/100 m**:
   - Compute arithmetic means over swims started in the **last 365 days** (valid values only: metric > 0).
   - On each journal row, color those two metric values **green** when better than the trailing-year average, **red** when worse (equal / missing → default color).
   - Better direction: **both lower** are better.
9. **Copy prompt** action on the journal screen copies a paste-ready AI swimming-coach consultation prompt to the clipboard:
   - Includes the **newest 10** swims as human-readable **aggregates only** (no GPS track / HR time series / raw JSON / VO₂ max).
   - Per swim: date, sport (Pool/Open water), distance, duration, pace (temp) per 100 m, avg/max HR, heartbits/100 m, SWOLF, calories.
   - Across those swims: total distance/duration/calories and average pace, HR, SWOLF, heartbits/100 m.
   - Also includes trailing 365-day SWOLF and heartbits/100 m averages for context.
   - Snackbar confirms “Trainer prompt copied”.

## Interfaces

### Xiaomi account / Mi Fitness cloud (unofficial)

Flow (same protocol as running-log):

```text
Mi Band → Xiaomi Fitness cloud → swimming-log → Room → Journal UI
```

- Preferred login: Xiaomi long-poll session (`/longPolling/loginUrl`, SID `miothealth`) → open `loginUrl` in Custom Tabs (shares Chrome cookies when already signed in) or WebView → await `lp` credentials → STS exchange at `sts-hlth.io.mi.com`.
- WebView cookie fallback: after interactive account login, read `passToken` + `userId` from CookieManager and exchange via `serviceLogin`.
- Password fallback: `account.xiaomi.com` serviceLogin / serviceLoginAuth2; `notificationUrl` opens WebView for interactive 2FA.
- API base (default): `https://ru.hlth.io.mi.com` (region selectable: `ru` / `cn`).
- Health API requests: RC4-encrypted `data` param + `signature` / `rc4_hash__` / `_nonce`; cookies `cUserId` + `serviceToken`.
- Workouts: `GET /app/v1/data/get_sport_records_by_watermark` with `relative_uid`, `watermark`, `limit`.

Sport keys follow Xiaomi’s HyperOS health kit: `pool_swimming` (pool) and `open_swimming` (open water). `open_water_swimming` is accepted as an alias.

### Derived metrics

| Field | Source / formula |
|-------|------------------|
| Distance (m) | `corrected_distance` if > 0 else `distance` |
| Pace (sec/100 m) | When duration and distance are both > 0: `duration_sec × 100 / distance_m`. Otherwise cloud `avg_pace`: values **≥ 450** are seconds per kilometre (same field unit as running-log and Xiaomi’s 秒/km pace docs) and are divided by 10; values **below 450** are already seconds per 100 m (a human sec/km pace cannot be faster than ~45 s/100 m). |
| Temp display | `m:ss` per 100 m from pace seconds |
| Avg BPM | `avg_hrm` |
| Max BPM | `max_hrm` |
| Heartbits/100 m | `avg_bpm × (pace_sec_per_100m / 60)` |
| SWOLF | `avg_swolf` if > 0, else `swolf` if > 0 (a nested `{ "avg": n }` statistics object is accepted). Otherwise, when pool length, distance, and duration are all > 0: `seconds_per_length + strokes_per_length`. Lengths = `distance / pool_length`. Strokes per length come from `stroke_count` (or `strokes` / `total_strokes` / `swim_strokes`) divided by lengths, else from average stroke frequency × seconds per length / 60. |
| Pool length | `pool_length`, `pool_len`, `lane_length`, or `swimming_pool_length`. Values **> 200** are treated as centimeters and divided by 100. Missing length → no estimated SWOLF. |
| Stroke frequency | `avg_stroke_freq`, `avg_stroke_rate`, or `stroke_rate`. Values **≥ 5** are strokes per minute. Values **below 5** are strokes per second (× 60), matching the 次/秒 statistics doc versus the 次/min field doc. |
| Trailing-year SWOLF avg | Mean of `swolf > 0` for swims with `start` in `[now−365d, now]` |
| Trailing-year heartbits/100 m avg | Mean of `heartbitsPer100m > 0` for swims with `start` in `[now−365d, now]` |

VO₂ max fields in the cloud payload are left inside `rawJson` only. They are not columns and not part of any formula.

## Data model

### AuthToken (encrypted prefs)

`userId`, `cUserId`, `serviceToken`, `ssecurity`, `passToken`, `deviceId`, `region`, `username`

Prefs file: `swimming_log_auth`.

### SwimmingActivity (Room, table `swimming_activities`)

| Column | Type | Notes |
|--------|------|-------|
| workoutId | TEXT PK | `sid#watermark` (falls back to `wm:watermark` / `sid#time`) |
| sportType | TEXT | e.g. `pool_swimming` |
| startTimeEpochSec | LONG | |
| endTimeEpochSec | LONG | |
| durationSec | INT | |
| distanceMeters | DOUBLE | |
| paceSecPer100m | DOUBLE | |
| avgBpm | INT | |
| maxBpm | INT | from `max_hrm` |
| swolf | DOUBLE | resolved average SWOLF |
| calories | DOUBLE | |
| watermark | LONG | sync cursor |
| rawJson | TEXT | original value payload (not shown) |

Database file: `swimming_log.db`.

## UI / UX

1. **Login** — region; primary **Sign in with browser** / **Sign in with in-app browser**; optional password form; SMS sub-step when required.
2. **Journal** — newest-first list of swims with date, sport, distance, temp /100 m, avg bpm, heartbits/100 m, and SWOLF. SWOLF and heartbits/100 m are colored vs trailing-year averages. Top bar **Copy prompt** + Sync + overflow (Copy prompt, Sign out).
3. Empty states: not signed in → login; signed in with no swims → prompt to sync.
4. **Copy prompt** — clipboard text suitable for pasting into ChatGPT / similar for a swimming-coach consultation (last 10 swim aggregates + trailing-year context). No VO₂ max.

Visual direction: pool light theme (deep water blue + foam, coral accent), not the running-log forest green and not Material purple defaults. Utility journal (not a marketing landing).

## Out of scope (v1)

- VO₂ max (cloud or estimated)
- Cadence / step metrics
- Stroke-type charts, lap tables, or SWOLF time series
- Triathlon swim segments
- Live BLE to the band
- Sleep / steps / SpO2 / weight dashboards
- FDS GPS track download or maps
- Background scheduled sync
- Multi-account / family relative browsing
- Play Store distribution (sideload APK via GitHub Pages)

## Acceptance criteria

1. With a valid Xiaomi Fitness account that has pool or open-water swims, Sync imports those swims into the journal.
2. Journal rows show date, distance, temp per 100 m, avg bpm, heartbits/100 m, and SWOLF with the formulas above.
3. SWOLF and heartbits/100 m values are green when lower than the last-365-day mean, red when higher (equal / missing stay the default color).
4. Non-swimming sport types are not listed. VO₂ max is not shown and is not in the trainer prompt.
5. Copy prompt places a human-readable swimming-coach prompt (last ≤10 swim aggregates + trailing-year averages) on the clipboard.
6. Unit tests cover crypto round-trip, sport JSON parsing, swimming filter, pace per 100 m (duration/distance, sec/km fallback, already-per-100 m fallback), heartbits/100 m, SWOLF prefer-cloud and pool-length estimate, trailing-year average / comparison, and trainer-prompt formatting (limit 10, aggregates only, no VO₂ max).
7. Release APK builds with the monorepo sideload keystore.

## Attribution / risk

Unofficial reverse-engineered Xiaomi Fitness cloud API. Not affiliated with Xiaomi / Zepp / Huami. Protocol reference: [alexgetmancom/miband-bot](https://github.com/alexgetmancom/miband-bot). Xiaomi may change the API without notice. Sport keys and SWOLF field names follow the public Xiaomi HyperOS health-data definitions (`pool_swimming`, `open_swimming`, `avg_swolf`).
