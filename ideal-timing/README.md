# ideal-timing

Android **16-hour day clock** relative to wake-up time from Xiaomi Fitness / Mi Band sleep data. Sign in with a Xiaomi account (browser / in-app WebView), sync wake time, and read which of the four 4-hour sectors you are in. Past 16 hours after wake, the pointer freezes (no overflow). On the first open of each day, after sync, the app schedules same-day section-change notifications (`Наступило время для …` / `Пора спать` at 16h).

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
