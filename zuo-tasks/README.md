# ZuoTasks

Fast Android task manager with nested projects and recurring regular tasks. The name and logo use the hanzi **做** (*zuò* — to do).

## Features

- **Projects** — infinitely nested projects and tasks with O(1) completion percentages via subtree aggregates
- **Regular tasks** — track when each habit was last performed
- **Settings** — backup and restore to a human-readable `.txt` file

## Requirements

- Android 16 (API 36) — `minSdk`, `targetSdk`, and `compileSdk` are all 36

## Build

```bash
cd zuo-tasks
./gradlew assembleRelease
```

APK: `app/build/outputs/apk/release/app-release.apk`

Release builds use the committed **sideload keystore** (`sideload.keystore` + `sideload-signing.properties`) so CI and local builds share one signing key with the other monorepo Android apps. Optional override: `zuotasks.signing*` in `local.properties`.

## Install / update

1. Download the latest `zuo-tasks.apk` from GitHub Pages and install it over the existing app.
2. If Android refuses the install (older build signed with a different key), uninstall once, reinstall, then later updates stay in place.

## Deployment

On push to `master`, `.github/workflows/deploy.yml` builds the release APK, verifies sideload signing, and publishes it on GitHub Pages at `/<repository>/zuo-tasks/zuo-tasks.apk` with a small `index.html` landing page.

## Tech stack

- Kotlin, Jetpack Compose, Material 3
- Room (SQLite) with indexed parent-child tree
- Navigation Compose, ViewModel, Coroutines
