#!/usr/bin/env bash
# Assembles a site from a fake previous tree and fake build artifacts.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
# shellcheck source=ci-apps.sh
source "$ROOT/scripts/ci-apps.sh"

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

previous="$TMP/previous"
mkdir -p "$previous"
touch "$previous/.nojekyll"
echo kept > "$previous/.nojekyll"
for app in "${WEB_APPS[@]}"; do
  mkdir -p "$previous/$app"
  echo old > "$previous/$app/index.html"
  echo stale > "$previous/$app/stale.js"
done
for app in "${ANDROID_APPS[@]}"; do
  mkdir -p "$previous/$app"
  echo old-apk > "$previous/$app/$app.apk"
  echo old-html > "$previous/$app/index.html"
done
mkdir -p "$previous/.git"
echo secret > "$previous/.git/config"

artifacts="$TMP/artifacts"
mkdir -p "$artifacts/web-socratus/socratus/dist/nested"
echo '<html>new socratus</html>' > "$artifacts/web-socratus/socratus/dist/index.html"
echo '<html>decoy</html>' > "$artifacts/web-socratus/socratus/dist/nested/index.html"
mkdir -p "$artifacts/android-same-element-android/nested"
echo new-apk > "$artifacts/android-same-element-android/nested/app-release.apk"

deploy="$TMP/deploy"
HAS_PREVIOUS=true \
  REFRESH_ANDROID_LANDINGS=false \
  WEB_MATRIX='[{"name":"socratus","runtime":"bun"}]' \
  ANDROID_MATRIX='[{"name":"same-element-android","runtime":"bun"}]' \
  PREVIOUS_SITE="$previous" \
  ARTIFACTS_DIR="$artifacts" \
  DEPLOY_DIR="$deploy" \
  bash "$ROOT/scripts/ci-assemble-site.sh"

[[ "$(cat "$deploy/socratus/index.html")" == "<html>new socratus</html>" ]] || fail "socratus was not replaced"
[[ ! -f "$deploy/socratus/stale.js" ]] || fail "stale web asset survived"
[[ "$(cat "$deploy/mandarin-koan/index.html")" == "old" ]] || fail "unchanged web app was not kept"
[[ "$(cat "$deploy/same-element-android/same-element-android.apk")" == "new-apk" ]] || fail "apk was not copied"
[[ -f "$deploy/same-element-android/index.html" ]] || fail "landing html missing"
cmp -s android/landing/styles.css "$deploy/same-element-android/styles.css" || fail "shared landing css was not applied"
grep -q "deploy-footer" "$deploy/same-element-android/index.html" || fail "landing markers missing"
[[ "$(cat "$deploy/chesswatch/chesswatch.apk")" == "old-apk" ]] || fail "unchanged apk was not kept"
[[ ! -e "$deploy/.git" ]] || fail "previous .git was copied"
[[ -f "$deploy/china_rail_interactive_map.html" ]] || fail "static root file missing"
[[ -f "$deploy/.nojekyll" ]] || fail "nojekyll missing"

# Landing refresh keeps the previous APK and rewrites HTML.
deploy_refresh="$TMP/deploy-refresh"
HAS_PREVIOUS=true \
  REFRESH_ANDROID_LANDINGS=true \
  WEB_MATRIX='[]' \
  ANDROID_MATRIX='[]' \
  PREVIOUS_SITE="$previous" \
  ARTIFACTS_DIR="$artifacts" \
  DEPLOY_DIR="$deploy_refresh" \
  bash "$ROOT/scripts/ci-assemble-site.sh"
[[ "$(cat "$deploy_refresh/duoshao-qian/duoshao-qian.apk")" == "old-apk" ]] || fail "refresh dropped apk"
grep -q "duoshao-qian.apk" "$deploy_refresh/duoshao-qian/index.html" || fail "refresh did not copy landing html"

# Refresh without an APK fails.
rm -f "$previous/duoshao-qian/duoshao-qian.apk"
if HAS_PREVIOUS=true \
  REFRESH_ANDROID_LANDINGS=true \
  WEB_MATRIX='[]' \
  ANDROID_MATRIX='[]' \
  PREVIOUS_SITE="$previous" \
  ARTIFACTS_DIR="$artifacts" \
  DEPLOY_DIR="$TMP/deploy-fail" \
  bash "$ROOT/scripts/ci-assemble-site.sh"; then
  fail "refresh with a missing apk should fail"
fi

echo "ci-assemble-site tests passed"
