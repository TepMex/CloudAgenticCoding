# ideal-timing

Android **16-hour day clock** relative to wake-up time from Xiaomi Fitness / Mi Band sleep data. Sign in with a Xiaomi account (browser / in-app WebView), sync wake time, and read which of the four 4-hour sectors you are in. Past 16 hours after wake, the pointer freezes (no overflow). On the first open of each day, after sync, the app schedules same-day cues: section changes (`Наступило время для …` / `Пора спать` at 16h), meals (`пора завтракать` at wake+30m, `пора обедать` at +6h, `пора ужинать` at +11h), and a dog walk at **19:00 local wall-clock** (`время погулять с собакой`). Sunrise and sunset (solar altitude −0.833°) are computed offline from the device location and `ZoneId`, and shown as sun / moon pictograms outside the dial rim. Hamburger meal icons sit on the face; a pet-dog icon sits at 19:00 when that instant is inside the 16-hour window. With the clock open, scanning **any NFC tag** is a physical check-in: a running-person icon peeks from behind the dial at the pointer’s current angle and stays until Mi Fitness sync brings a wake for a **new** day (re-sync of the current wake day keeps it).

See [SPEC.md](./SPEC.md).

## Requirements

- Android 14 (API 34+)
- Xiaomi account with Mi Fitness sleep / wake data

## Build

```bash
cd ideal-timing
./gradlew assembleRelease test
```

APK: `app/build/outputs/apk/release/app-release.apk`

## Install

Download `ideal-timing.apk` from GitHub Pages and install. Sideload signing matches other monorepo Android apps.

## Note

Unofficial reverse-engineered API — not affiliated with Xiaomi. Endpoints may change without notice. Auth / crypto protocol matches [running-log](../running-log).
