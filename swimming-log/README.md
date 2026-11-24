# swimming-log

Android journal for Mi Band / Xiaomi Fitness **swimming** workouts. Signs in with a Xiaomi account (browser Custom Tabs / in-app WebView preferred), syncs pool and open-water activities from the unofficial Fitness cloud API (same protocol family as [running-log](../running-log) and [miband-bot](https://github.com/alexgetmancom/miband-bot)), and shows date, distance, temp (pace per 100 m), avg BPM, heartbits/100 m, and SWOLF. **Copy prompt** builds a paste-ready AI swimming-coach consultation from the last 10 swim aggregates. VO₂ max is not used.

See [SPEC.md](./SPEC.md).

## Requirements

- Android 14 (API 34+)
- Xiaomi account with Mi Fitness swimming data

## Build

```bash
cd swimming-log
./gradlew assembleRelease test
```

APK: `app/build/outputs/apk/release/app-release.apk`

## Install

Download `swimming-log.apk` from GitHub Pages and install. Sideload signing matches other monorepo Android apps.

## Note

Unofficial reverse-engineered API — not affiliated with Xiaomi. Endpoints may change without notice.
