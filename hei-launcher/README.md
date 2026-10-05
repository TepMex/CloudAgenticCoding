# hei-launcher

Minimal Android launcher: text only, no icons. Favourites in the middle, every app one swipe left, a 16-hour Mi Fitness day clock (same behaviour as ideal-timing) one swipe right, substring search, gear for device settings, ellipsis for launcher settings.

See [SPEC.md](./SPEC.md).

## Requirements

- Android 14 (API 34+)

## Build

```bash
cd hei-launcher
./gradlew assembleRelease test
```

APK: `app/build/outputs/apk/release/app-release.apk`

## Install

Download `hei-launcher.apk` from GitHub Pages and install. Sideload signing matches other monorepo Android apps. After install, set it as the Home app from launcher settings (or the system Home picker).

Usage access (optional) shows time since an app was last opened. Coarse location (optional) shows weather from Open-Meteo.
