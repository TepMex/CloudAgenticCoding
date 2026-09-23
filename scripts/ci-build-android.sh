#!/usr/bin/env bash
# Build one release APK and verify the shared sideload certificate.
# Run from the repository root after Java and the Android SDK are installed.
set -euo pipefail

app="${1:?Usage: ci-build-android.sh <app>}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

case "$app" in
  han-count-android | rth-agriculture-android | same-element-android)
    chmod +x "$app/scripts/sync-web-assets.sh"
    "$app/scripts/sync-web-assets.sh"
    ;;
esac

(
  cd "$app"
  chmod +x gradlew
  ./gradlew --stacktrace assembleRelease
)

apk="$app/app/build/outputs/apk/release/app-release.apk"
if [[ ! -f "$apk" ]]; then
  echo "Build failed: $apk is missing" >&2
  exit 1
fi

chmod +x android/verify-apk-sideload-cert.sh
android/verify-apk-sideload-cert.sh "$apk"
