# Memory Garden · Android

Thin Android (Kotlin) WebView wrapper around the sibling [`rth-agriculture`](../rth-agriculture/) React game. The production web build (including WebP map/battle art for all 15 gardens) is bundled into `app/src/main/assets/www/` so the APK plays offline.

- **Requirements:** Android 14 or newer (min SDK 34), compile SDK 35.
- **Orientation:** sensor (portrait and landscape).
- **Upstream:** same V2 garden map, four-stage field-cleaning stroke battles, FSRS, and IndexedDB progress as the web app.

See [SPEC.md](./SPEC.md) for requirements and acceptance criteria.

## Local build

1. Install Bun and the Android SDK.
2. Bundle the web game into assets:

```bash
./scripts/sync-web-assets.sh
```

3. Assemble a release APK:

```bash
./gradlew assembleRelease
```

Release builds are signed with the committed **sideload keystore** (`sideload.keystore` + `sideload-signing.properties`) so every CI and local build uses the same key. New APKs install **over** the previous version.

APK output: `app/build/outputs/apk/release/app-release.apk`.

Optional: override signing via `rthagricultureandroid.signing*` entries in `local.properties`.

### Updating on your phone

1. Download the latest `rth-agriculture-android.apk` from GitHub Pages and install it over the existing app.
2. If Android refuses (e.g. you installed an older build signed with a different key), **uninstall once**, install the latest APK, then future updates install in place.

## CI and download

On push to `master`, `.github/workflows/deploy.yml` syncs `rth-agriculture` into assets, builds the release APK, verifies sideload signing, and publishes it on GitHub Pages at `/<repository>/rth-agriculture-android/rth-agriculture-android.apk` together with a small `index.html` landing page.

The APK is rebuilt when either `rth-agriculture-android/**` or `rth-agriculture/**` changes so bundled assets stay current.
