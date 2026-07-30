# running-log — Specification

## Purpose

**running-log** is a personal Android app that imports running workouts from the Xiaomi Fitness / Mi Band cloud (same unofficial cloud API used by [miband-bot](https://github.com/alexgetmancom/miband-bot)) and stores them locally as a running journal.

| Field | Value |
|-------|-------|
| App name | running-log |
| Package | `com.tepmex.runninglog` |
| Min SDK | 34 (Android 14+) |
| Target / compile SDK | 35 |

## Requirements

1. Sign in with a Xiaomi account via **browser Custom Tabs** or **in-app WebView** (preferred). Password + SMS remain as fallback when the device is untrusted; optional captcha during SMS send.
2. Persist auth tokens on device; reuse them across launches; refresh via `passToken` when possible.
3. Sync sport/workout records from Xiaomi Fitness cloud using watermark pagination (`/app/v1/data/get_sport_records_by_watermark`), targeting the signed-in user’s own UID.
4. Keep only **running** activities: `outdoor_running` and `treadmill`.
5. Store each run locally (Room) and show a journal list with:
   - **date** (local calendar date of start)
   - **distance** (km)
   - **temp** (pace / tempo as min:sec per km)
   - **average bpm**
   - **heartbits per km** = `avg_bpm × temp_minutes` (temp in fractional minutes/km)
   - **cadence** (average steps per minute)
6. Manual **Sync** action from the journal screen.
7. Sign out clears stored credentials (journal data may remain until cleared/uninstall).
8. Compete-with-self highlighting for **cadence** and **heartbits/km**:
   - Compute arithmetic means over runs started in the **last 365 days** (valid values only: metric > 0).
   - On each journal row, color those two metric values **green** when better than the trailing-year average, **red** when worse (equal / missing → default color).
   - Better direction: **cadence higher** is better; **heartbits/km lower** is better (efficiency).

## Interfaces

### Xiaomi account / Mi Fitness cloud (unofficial)

Flow (inspired by miband-bot / mi-fitness-python protocol):

```text
Mi Band → Xiaomi Fitness cloud → running-log → Room → Journal UI
```

- Preferred login: Xiaomi long-poll session (`/longPolling/loginUrl`, SID `miothealth`) → open `loginUrl` in Custom Tabs (shares Chrome cookies when already signed in) or WebView → await `lp` credentials → STS exchange at `sts-hlth.io.mi.com`.
- WebView cookie fallback: after interactive account login, read `passToken` + `userId` from CookieManager and exchange via `serviceLogin`.
- Password fallback: `account.xiaomi.com` serviceLogin / serviceLoginAuth2; `notificationUrl` opens WebView for interactive 2FA.
- API base (default): `https://ru.hlth.io.mi.com` (region selectable: `ru` / `cn`).
- Health API requests: RC4-encrypted `data` param + `signature` / `rc4_hash__` / `_nonce`; cookies `cUserId` + `serviceToken`.
- Workouts: `GET /app/v1/data/get_sport_records_by_watermark` with `relative_uid`, `watermark`, `limit`.

### Derived metrics

| Field | Source / formula |
|-------|------------------|
| Distance (m) | `corrected_distance` if > 0 else `distance` |
| Pace (sec/km) | `avg_pace` if > 0 else `duration / (distance_km)` |
| Temp display | `mm:ss` per km from pace seconds |
| Avg BPM | `avg_hrm` |
| Heartbits/km | `avg_bpm * (pace_sec / 60.0)` |
| Cadence | `avg_cadence` if > 0 else `steps / (duration_min)` |
| Trailing-year cadence avg | Mean of `cadenceSpm > 0` for runs with `start` in `[now−365d, now]` |
| Trailing-year heartbits/km avg | Mean of `heartbitsPerKm > 0` for runs with `start` in `[now−365d, now]` |

## Data model

### AuthToken (encrypted prefs)

`userId`, `cUserId`, `serviceToken`, `ssecurity`, `passToken`, `deviceId`, `region`, `username`

### RunningActivity (Room)

| Column | Type | Notes |
|--------|------|-------|
| workoutId | TEXT PK | `sid#watermark` (falls back to `wm:watermark` / `sid#time`) |
| sportType | TEXT | e.g. `outdoor_running` |
| startTimeEpochSec | LONG | |
| endTimeEpochSec | LONG | |
| durationSec | INT | |
| distanceMeters | DOUBLE | |
| paceSecPerKm | DOUBLE | |
| avgBpm | INT | |
| cadenceSpm | DOUBLE | |
| calories | DOUBLE | |
| watermark | LONG | sync cursor |
| rawJson | TEXT | original value payload |

## UI / UX

1. **Login** — region; primary **Sign in with browser** / **Sign in with in-app browser**; optional password form; SMS sub-step when required.
2. **Journal** — newest-first list of runs with the six metrics; cadence and heartbits/km colored vs trailing-year averages; top bar Sync + overflow Sign out.
3. Empty states: not signed in → login; signed in with no runs → prompt to sync.

Visual direction: trail/forest light theme (deep green + warm stone), not Material purple defaults. Utility journal (not a marketing landing).

## Out of scope (v1)

- Live BLE to the band
- Sleep / steps / SpO2 / weight dashboards
- FDS GPS track download or maps
- Background scheduled sync
- Multi-account / family relative browsing
- Play Store distribution (sideload APK via GitHub Pages)

## Acceptance criteria

1. With a valid Xiaomi Fitness account that has outdoor/treadmill runs, Sync imports those runs into the journal.
2. Journal rows show date, distance, temp, avg bpm, heartbits/km, cadence with the formulas above.
3. Cadence and heartbits/km values are green when better than the last-365-day mean, red when worse (cadence ↑ better; heartbits/km ↓ better).
4. Non-running sport types are not listed.
5. Unit tests cover crypto round-trip, sport JSON parsing, running filter, metric formulas, and trailing-year average / comparison.
6. Release APK builds with the monorepo sideload keystore.

## Attribution / risk

Unofficial reverse-engineered Xiaomi Fitness cloud API. Not affiliated with Xiaomi / Zepp / Huami. Protocol reference: [alexgetmancom/miband-bot](https://github.com/alexgetmancom/miband-bot). Xiaomi may change the API without notice.
