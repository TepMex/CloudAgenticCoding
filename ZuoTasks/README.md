# ZuoTasks

Fast Android task manager with nested projects and recurring regular tasks.

## Features

- **Projects** — infinitely nested projects and tasks with O(1) completion percentages via subtree aggregates
- **Regular tasks** — track when each habit was last performed
- **Settings** — backup and restore to a human-readable `.txt` file

## Requirements

- Android 16 (API 36) — `minSdk`, `targetSdk`, and `compileSdk` are all 36

## Build

```bash
cd ZuoTasks
./gradlew :app:assembleDebug
```

## Tech stack

- Kotlin, Jetpack Compose, Material 3
- Room (SQLite) with indexed parent-child tree
- Navigation Compose, ViewModel, Coroutines
