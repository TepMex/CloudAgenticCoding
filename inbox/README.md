# INBOX

Fast Android capture inbox for thoughts, ideas, reminders, and loose text. The UI is Russian and is laid out for the largest system font.

## Features

- Capture screen opens first: type, send, the field clears immediately
- Inbox to process a note or turn it into a reminder
- Exact alarms and a high-importance notification; firing moves the note to the archive
- Archive keeps creation time plus either the processing time or the notification time
- Settings for haptic feedback, keyboard, lock-screen text, and notification access

## Requirements

- Android 16 (API 36)

## Build

```bash
cd inbox
./gradlew assembleRelease
```

APK: `app/build/outputs/apk/release/app-release.apk`

Release builds use the committed sideload keystore (`sideload.keystore` + `sideload-signing.properties`) so CI and local builds share one signing key with the other monorepo Android apps. Optional override: `inbox.signing*` in `local.properties`.

## Install / update

1. Download the latest `inbox.apk` from GitHub Pages and install it over the existing app.
2. If Android refuses the install (older build signed with a different key), uninstall once, reinstall, then later updates stay in place.

## Deployment

On push to `master`, `.github/workflows/deploy.yml` builds the release APK, verifies sideload signing, and publishes it on GitHub Pages at `/<repository>/inbox/inbox.apk`.

## Tech stack

- Kotlin, Jetpack Compose, Material 3 dynamic color
- Room, Navigation Compose, exact alarms
