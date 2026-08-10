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

## UI / UX

1. **Login** — region; Sign in with browser / in-app browser; optional password + SMS (parity with running-log).
2. **Clock** — dominant circular four-sector dial; pointer; current sector title; wake time + elapsed; Sync + Sign out.
3. Empty / error: not signed in → login; signed in but no wake yet → prompt to sync; sync failure message.

Visual direction: parchment / RPG map clock (aged paper, gold filigree accents, jewel tones per sector). Not Material purple defaults; not a marketing landing.

## Out of scope (v1)

- Live BLE to the band
- Editing wake time manually
- Background / scheduled sync
- Sleep stage charts or health dashboards
- Multi-account / family relative browsing
- Play Store distribution (sideload APK via GitHub Pages)

## Acceptance criteria

1. With a valid Xiaomi Fitness account that has recent sleep with `wake_up_time`, Sync stores wake and the clock advances from that instant.
2. At elapsed ≥ 16h the pointer stays at the 16h / 12-o’clock freeze point.
3. Four sectors are visually distinct and labeled; active sector is highlighted.
4. Unit tests cover clock clamping, sector selection, and sleep JSON wake extraction.
5. Release APK builds with the monorepo sideload keystore.

## Attribution / risk

Unofficial reverse-engineered Xiaomi Fitness cloud API. Not affiliated with Xiaomi / Zepp / Huami. Protocol reference: [alexgetmancom/miband-bot](https://github.com/alexgetmancom/miband-bot). Xiaomi may change the API without notice.
