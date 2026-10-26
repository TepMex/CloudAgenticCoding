# НЕДЕЛЯ (My Week)

Weekly sprint checklist. Monday is for planning colored blocks of emoji tasks. The rest of the week is for ticking them off. The next Monday archives the week and copies it into a fresh sprint.

The UI is Russian.

## Features

- Planning only on Monday, until local midnight
- Blocks with a name, a default emoji, and a translucent color. New tasks open with the block emoji
- The emoji picker remembers ones you type and lists the most-used first
- Checklist ticks for the whole week
- Archive feed, oldest week on top, newest at the bottom
- New week starts as a copy of the previous one, with ticks cleared
- Debug mode with test dates: new Monday, ordinary day, next day, real time

## Requirements

- Android 16 (API 36)

## Build

```bash
cd my-week
./gradlew assembleRelease
```

APK: `app/build/outputs/apk/release/app-release.apk`

Release builds use the committed sideload keystore (`sideload.keystore` + `sideload-signing.properties`) so CI and local builds share one signing key with the other monorepo Android apps. Optional override: `myweek.signing*` in `local.properties`.

## Install / update

1. Download the latest `my-week.apk` from GitHub Pages and install it over the existing app.
2. If Android refuses the install (older build signed with a different key), uninstall once, reinstall, then later updates stay in place.

## Deployment

On push to `master`, `.github/workflows/deploy.yml` builds the release APK, verifies sideload signing, and publishes it on GitHub Pages at `/<repository>/my-week/my-week.apk`.

## Tech stack

- Kotlin, Jetpack Compose, Material 3 dynamic color
- Room
