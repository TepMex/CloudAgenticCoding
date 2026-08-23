# ideal-timing — Specification

## Purpose

**ideal-timing** is a personal Android app that shows a **16-hour day clock** relative to wake-up time synced from Xiaomi Fitness / Mi Band (same unofficial cloud API family as [running-log](../running-log) and [miband-bot](https://github.com/alexgetmancom/miband-bot)).

Open the app → sync wake time from Mi Fitness → read which of the four 4-hour sectors you are in.

| Field | Value |
|-------|-------|
| App name | ideal-timing |
| Package | `com.tepmex.idealtiming` |
| Min SDK | 34 (Android 14+) |
| Target / compile SDK | 35 |

## Requirements

1. Sign in with a Xiaomi account via **browser Custom Tabs** or **in-app WebView** (same flow as running-log). Password + SMS remain as fallback.
2. Persist auth tokens on device; reuse across launches; refresh via `passToken` when possible.
3. Sync the latest **wake-up time** from Mi Fitness sleep data (`get_aggregated_fitness_data_by_time`, key `sleep`, using `segment_details[].wake_up_time`).
4. Display a circular **16-hour clock** divided into **four equal 4-hour sectors**, with a pointer at elapsed time since wake-up.
5. If elapsed time since wake-up is **greater than 16 hours**, **freeze** the pointer at the 16-hour mark (no overflow / wrap).
6. Manual **Sync** action on the clock screen; optional **Sign out**.
7. Persist last known wake-up time locally so the clock still works offline after a prior sync.
8. **Section-change push notifications** (exact alarms), scheduled on the **first app open of each local calendar day** after Mi Fitness sync yields a wake time:
   - At wake+4h / +8h / +12h: `Наступило время для ${SECTION_DESCRIPTION}` (RU sector labels).
   - At wake+16h: `Пора спать`.
   - Only schedule fires that are still in the future and **before the end of the current local day** (no next-day schedule — wake for later days is unknown).
9. Show **sunrise** and **sunset** markers on the dial, calculated **offline** from the user’s geographic coordinates:
   - Official rise/set = sun center altitude **−0.833°** (NOAA / USNO refraction + solar-disk zenith `90.833°`).
   - Convert civil-day events with the device **`ZoneId`** (`ZoneId.systemDefault()`).
   - Map each event onto the 16-hour dial relative to wake; only events inside `[wake, wake+16h]` are drawn. If sunrise or sunset falls outside that window, **omit that icon entirely** (do not clamp or place it at the rim).
   - Pictograms sit **outside** the gold rim at the angle the pointer would reach: bright sun for sunrise, moon crescent for sunset.
   - Coarse/fine location permission; last fix cached for offline use. No markers when coords are unavailable or polar day/night omits the event.
10. **Meal pictograms** (hamburger) on the dial at wake-relative times:
    - Breakfast: wake + 30 minutes
    - Lunch: wake + 6 hours
    - Dinner: wake + 11 hours
11. **Meal notifications** (same exact-alarm / same-day window as section cues):
    - wake+30m: `пора завтракать`
    - wake+6h: `пора обедать`
    - wake+11h: `пора ужинать`
12. **Dog-walk pictogram** (pet dog — floppy ears and collar, not an `@` glyph) at **19:00 local wall-clock**, mapped onto the 16-hour dial the same way as sunrise/sunset. Omit the icon when 19:00 falls outside `[wake, wake+16h]`.
13. **Dog-walk notification** at 19:00 local time on the current calendar day (if still in the future and before local midnight): `время погулять с собакой`.

### Sector map (relative to wake = 0h, clockwise from 12 o’clock)

| Sector | Hours after wake | Label (RU) | Label (EN) |
|--------|------------------|------------|------------|
| 1 | 0–4 | Здоровье и стратегия | Health & strategy |
| 2 | 4–8 | Тактика и работа руками | Tactics & handwork |
| 3 | 8–12 | Тактика и работа руками | Tactics & handwork |
| 4 | 12–16 | Отдых, декомпрессия и подготовка ко сну | Rest, decompress & wind-down |

## Interfaces

### Xiaomi account / Mi Fitness cloud (unofficial)

Same auth protocol as running-log:

```text
Mi Band → Xiaomi Fitness cloud sleep → ideal-timing → wake epoch → 16h clock UI
```

- Preferred login: Xiaomi long-poll session (`/longPolling/loginUrl`, SID `miothealth`) → Custom Tabs or WebView → STS exchange.
- API base (default): `https://ru.hlth.io.mi.com` (region `ru` / `cn`).
- Health API: RC4-encrypted `data` + signature cookies `cUserId` + `serviceToken`.
- Sleep: `GET /app/v1/data/get_aggregated_fitness_data_by_time` with `relative_uid`, `key=sleep`, `tag=daily_report`, `start_time`, `end_time`, `limit`.
- Wake time: max `wake_up_time` across `segment_details` of recent sleep records where `wake_up_time <= now` (fallback: latest segment wake even if slightly in the future after sync lag).

### Clock math

| Concept | Formula |
|---------|---------|
| Elapsed sec | `min(max(now − wakeEpochSec, 0), 16 × 3600)` |
| Progress (0…1) | `elapsedSec / (16 × 3600)` |
| Pointer angle | progress × 360°, clockwise from 12 o’clock |
| Sector index | `floor(elapsedHours / 4)` clamped to `0…3` (freeze at sector 4 when at 16h) |

### Sunrise / sunset (offline)

| Concept | Rule |
|---------|------|
| Zenith | `90.833°` (altitude `−0.833°`) |
| Algorithm | Jean Meeus / NOAA spreadsheet approximations (`SunCalculator`) |
| Time zone | Device `ZoneId` for which local calendar days overlap the wake window |
| Dial progress | `(eventEpoch − wakeEpoch) / (16 × 3600)` when in range; else omit marker |
| Location | `LocationManager` last-known + `GeoLocationStore` cache |

### Section / daily-cue notifications

| Fire time | Message |
|-----------|---------|
| wake + 4h | `Наступило время для Тактика и работа руками` |
| wake + 8h | `Наступило время для Тактика и работа руками` |
| wake + 12h | `Наступило время для Отдых, декомпрессия и подготовка ко сну` |
| wake + 16h | `Пора спать` |
| wake + 30m | `пора завтракать` |
| wake + 6h | `пора обедать` |
| wake + 11h | `пора ужинать` |
| 19:00 local wall-clock (current calendar day) | `время погулять с собакой` |

Scheduling: first signed-in open of the local day triggers Mi Fitness sync; on wake (new or cached after failed sync), `AlarmManager.setExactAndAllowWhileIdle` schedules only future fires with `fire < startOfNextLocalDay`. Later opens the same day do not re-auto-sync; manual Sync reschedules.

Dog walk is **not** “19 hours after wake”: it uses `LocalTime.of(19, 0)` in the device `ZoneId`. The icon uses the 19:00 instant that lands inside `[wake, wake+16h]` (same omit-if-out-of-range rule as sun/moon). The notification always targets 19:00 of the current local date when that instant is still upcoming today.

### Meal / dog dial mapping

| Cue | Dial progress |
|-----|----------------|
| Breakfast | `(0.5h) / 16h` |
| Lunch | `6h / 16h` |
| Dinner | `11h / 16h` |
| Dog walk | `(19:00 local − wake) / 16h` when in range; else omit |

## Data model

### AuthToken (encrypted prefs)

Same fields as running-log: `userId`, `cUserId`, `serviceToken`, `ssecurity`, `passToken`, `deviceId`, `region`, `username`.

### WakeSnapshot (encrypted prefs)

| Field | Type | Notes |
|-------|------|-------|
| wakeEpochSec | LONG | Chosen wake-up unix seconds |
| syncedAtEpochSec | LONG | Last successful sync time |
| sourceDateEpochSec | LONG | Sleep record `time` used, if any |
| rawHint | TEXT | Optional debug / display (e.g. score) |

### Geo cache (plain prefs)

| Field | Type | Notes |
|-------|------|-------|
| latitude_deg | FLOAT | Last known WGS84 latitude |
| longitude_deg | FLOAT | Last known WGS84 longitude (east+) |
| updated_at_epoch_sec | LONG | When the cache was written |

## UI / UX

1. **Login** — region; Sign in with browser / in-app browser; optional password + SMS (parity with running-log).
2. **Clock** — dominant circular four-sector dial; pointer; sunrise (sun) / sunset (moon) pictograms outside the rim when in range; hamburger meal pictograms on the face at wake+30m / +6h / +11h; pet-dog pictogram outside the rim at 19:00 local when that instant is on the dial; current sector title; wake time + elapsed; Sync + Sign out.
3. Empty / error: not signed in → login; signed in but no wake yet → prompt to sync; sync failure message.

Visual direction: parchment / RPG map clock (aged paper, gold filigree accents, jewel tones per sector). Not Material purple defaults; not a marketing landing.

## Out of scope (v1)

- Live BLE to the band
- Editing wake time manually
- Background / scheduled Mi Fitness sync (aside from first-open-of-day sync for notifications)
- Rescheduling after device reboot without opening the app
- Sleep stage charts or health dashboards
- Multi-account / family relative browsing
- Play Store distribution (sideload APK via GitHub Pages)

## Acceptance criteria

1. With a valid Xiaomi Fitness account that has recent sleep with `wake_up_time`, Sync stores wake and the clock advances from that instant.
2. At elapsed ≥ 16h the pointer stays at the 16h / 12-o’clock freeze point.
3. Four sectors are visually distinct and labeled; active sector is highlighted.
4. Unit tests cover clock clamping, sector selection, sleep JSON wake extraction, same-day cue notification planning (sectors, meals, 19:00 dog walk; skip past / after day-end), meal progress on the 16h dial, dog-walk wall-clock mapping (including omit when 19:00 is off the dial), and offline sunrise/sunset (±1 min vs known civil times) plus dial mapping via `ZoneId`.
5. Release APK builds with the monorepo sideload keystore.
6. After first open + sync of the day with a known wake, remaining sector boundaries that fall before local midnight are scheduled; wake+16h uses `Пора спать`.
7. With cached/user coordinates and a wake inside daylight hours, sunset (and sunrise if after wake) markers appear outside the dial at the matching pointer angles.
8. With a known wake, three hamburger meal icons sit at wake+30m / +6h / +11h; a pet-dog icon sits at 19:00 local when that time is inside the 16h window.
9. Remaining same-day meal and 19:00 dog-walk fires are scheduled with the exact messages `пора завтракать` / `пора обедать` / `пора ужинать` / `время погулять с собакой`.

## Attribution / risk

Unofficial reverse-engineered Xiaomi Fitness cloud API. Not affiliated with Xiaomi / Zepp / Huami. Protocol reference: [alexgetmancom/miband-bot](https://github.com/alexgetmancom/miband-bot). Xiaomi may change the API without notice.
