# Общий элемент · Android

Android 14+ wrapper around the [`same-element`](../same-element/) trainer. The web build, including stroke JSON, is bundled in the APK and runs offline.

The shared element is already drawn; you write the strokes that distinguish the characters you picked.

See [SPEC.md](./SPEC.md).

## Local build

```bash
./scripts/sync-web-assets.sh
./gradlew assembleRelease
```

Release APKs are signed with the committed sideload keystore so an update installs over the previous build.

Output: `app/build/outputs/apk/release/app-release.apk`.

Optional signing override: `sameelementandroid.signing*` keys in `local.properties`.

## CI and download

`.github/workflows/deploy.yml` publishes the APK at `/<repository>/same-element-android/same-element-android.apk`.
