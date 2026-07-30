# running-log

Android journal for Mi Band / Xiaomi Fitness **running** workouts. Signs in with a Xiaomi account (browser Custom Tabs / in-app WebView preferred), syncs outdoor and treadmill activities from the unofficial Fitness cloud API (same protocol family as [miband-bot](https://github.com/alexgetmancom/miband-bot)), and shows date, distance, temp (pace), avg BPM, heartbits/km, cadence, and VO₂ max.

See [SPEC.md](./SPEC.md).

## Requirements

- Android 14 (API 34+)
- Xiaomi account with Mi Fitness running data

## Build

```bash
cd running-log
./gradlew assembleRelease test
```

APK: `app/build/outputs/apk/release/app-release.apk`

## Install

Download `running-log.apk` from GitHub Pages and install. Sideload signing matches other monorepo Android apps.

## Note

Unofficial reverse-engineered API — not affiliated with Xiaomi. Endpoints may change without notice.
