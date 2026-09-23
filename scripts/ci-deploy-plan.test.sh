#!/usr/bin/env bash
# Exercises selective deploy planning without GitHub Actions.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
# shellcheck source=ci-apps.sh
source "$ROOT/scripts/ci-apps.sh"

PLAN="$ROOT/scripts/ci-deploy-plan.sh"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

write_previous() {
  local dest="$1"
  shift
  local skip_web="${1:-}"
  local skip_apk="${2:-}"
  rm -rf "$dest"
  mkdir -p "$dest"
  touch "$dest/.nojekyll"
  local app
  for app in "${WEB_APPS[@]}"; do
    [[ "$app" == "$skip_web" ]] && continue
    mkdir -p "$dest/$app"
    echo old > "$dest/$app/index.html"
  done
  for app in "${ANDROID_APPS[@]}"; do
    [[ "$app" == "$skip_apk" ]] && continue
    mkdir -p "$dest/$app"
    echo apk > "$dest/$app/$app.apk"
  done
}

run_plan() {
  local name="$1"
  local files="$2"
  local previous="$3"
  local extra_env="${4:-}"
  local out="$TMP/$name.out"
  local list="$TMP/$name.files"
  printf '%s\n' "$files" > "$list"
  # Allow an empty change list.
  if [[ "$files" == "__EMPTY__" ]]; then
    : > "$list"
  fi
  GITHUB_OUTPUT="$out" \
    CHANGED_FILES_FILE="$list" \
    PREVIOUS_SITE="$previous" \
    EVENT_NAME=push \
    FULL_DEPLOY_INPUT=false \
    DEPLOYED_AT="2026-09-23T00:00:00 UTC" \
    env $extra_env \
    bash "$PLAN" > "$TMP/$name.log"
  echo "$out"
}

value() {
  local file="$1"
  local key="$2"
  grep -E "^${key}=" "$file" | head -1 | cut -d= -f2-
}

assert_eq() {
  local actual="$1"
  local expected="$2"
  local label="$3"
  [[ "$actual" == "$expected" ]] || fail "$label: expected [$expected] got [$actual]"
}

names() {
  local json="$1"
  jq -r '[.[].name] | join(" ")' <<<"$json"
}

# Complete previous site, unrelated file: nothing to publish.
write_previous "$TMP/full-site"
out="$(run_plan readme "README.md" "$TMP/full-site")"
assert_eq "$(value "$out" should_deploy)" false "readme should_deploy"
assert_eq "$(value "$out" web_count)" 0 "readme web_count"
assert_eq "$(value "$out" android_count)" 0 "readme android_count"
assert_eq "$(value "$out" full_deploy)" false "readme full_deploy"

# Workflow edits must not rebuild every app.
out="$(run_plan workflow ".github/workflows/deploy.yml" "$TMP/full-site")"
assert_eq "$(value "$out" should_deploy)" false "workflow should_deploy"
assert_eq "$(value "$out" web_count)" 0 "workflow web_count"
assert_eq "$(value "$out" android_count)" 0 "workflow android_count"
assert_eq "$(value "$out" full_deploy)" false "workflow full_deploy"

# CI helper scripts are not landing-page inputs.
out="$(run_plan ci-script "scripts/ci-deploy-plan.sh" "$TMP/full-site")"
assert_eq "$(value "$out" should_deploy)" false "ci-script should_deploy"
assert_eq "$(value "$out" refresh_android_landings)" false "ci-script refresh"

# One Android app. The same-element prefix must not also select same-element.
out="$(run_plan duo "duoshao-qian/app/src/Main.kt" "$TMP/full-site")"
assert_eq "$(value "$out" should_deploy)" true "duo should_deploy"
assert_eq "$(value "$out" web_count)" 0 "duo web_count"
assert_eq "$(names "$(value "$out" android_matrix)")" "duoshao-qian" "duo android"

out="$(run_plan android-only "same-element-android/site/index.html" "$TMP/full-site")"
assert_eq "$(value "$out" web_count)" 0 "android-only web_count"
assert_eq "$(names "$(value "$out" android_matrix)")" "same-element-android" "android-only android"

# Web app that is bundled into an Android wrapper.
out="$(run_plan same "same-element/src/App.tsx" "$TMP/full-site")"
assert_eq "$(names "$(value "$out" web_matrix)")" "same-element" "same web"
assert_eq "$(names "$(value "$out" android_matrix)")" "same-element-android" "same android"

# Shared signing rebuilds every APK and leaves web apps alone.
out="$(run_plan signing "android/sideload-signing.gradle.kts" "$TMP/full-site")"
assert_eq "$(value "$out" web_count)" 0 "signing web_count"
assert_eq "$(value "$out" android_count)" "${#ANDROID_APPS[@]}" "signing android_count"
assert_eq "$(value "$out" refresh_android_landings)" false "signing refresh"

# Footer script refreshes landings without Gradle.
out="$(run_plan footer "scripts/inject-static-site-footer.ts" "$TMP/full-site")"
assert_eq "$(value "$out" web_count)" 0 "footer web_count"
assert_eq "$(value "$out" android_count)" 0 "footer android_count"
assert_eq "$(value "$out" refresh_android_landings)" true "footer refresh"
assert_eq "$(value "$out" should_deploy)" true "footer should_deploy"

# Shared landing CSS.
out="$(run_plan landing "android/landing/styles.css" "$TMP/full-site")"
assert_eq "$(value "$out" refresh_android_landings)" true "landing refresh"
assert_eq "$(value "$out" android_count)" 0 "landing android_count"

# Static root file publishes without app builds.
out="$(run_plan rail "china_rail_interactive_map.html" "$TMP/full-site")"
assert_eq "$(value "$out" should_deploy)" true "rail should_deploy"
assert_eq "$(value "$out" static_root_changed)" true "rail static"
assert_eq "$(value "$out" web_count)" 0 "rail web_count"

# Apps missing from gh-pages are built even if this commit did not touch them.
write_previous "$TMP/missing" same-element same-element-android
out="$(run_plan missing "README.md" "$TMP/missing")"
assert_eq "$(value "$out" full_deploy)" false "missing full_deploy"
assert_eq "$(names "$(value "$out" web_matrix)")" "same-element" "missing web"
assert_eq "$(names "$(value "$out" android_matrix)")" "same-element-android" "missing android"
assert_eq "$(value "$out" should_deploy)" true "missing should_deploy"

# A new app plus a workflow edit still builds only that app (and anything missing).
write_previous "$TMP/missing2" same-element same-element-android
out="$(run_plan newapp $'.github/workflows/deploy.yml\nsame-element-android/site/index.html' "$TMP/missing2")"
assert_eq "$(value "$out" full_deploy)" false "newapp full_deploy"
assert_eq "$(names "$(value "$out" web_matrix)")" "same-element" "newapp web"
assert_eq "$(names "$(value "$out" android_matrix)")" "same-element-android" "newapp android"

# No previous site means a full publish.
out="$(run_plan noprev "__EMPTY__" "$TMP/does-not-exist")"
assert_eq "$(value "$out" has_previous)" false "noprev has_previous"
assert_eq "$(value "$out" full_deploy)" true "noprev full_deploy"
assert_eq "$(value "$out" web_count)" "${#WEB_APPS[@]}" "noprev web_count"
assert_eq "$(value "$out" android_count)" "${#ANDROID_APPS[@]}" "noprev android_count"

# Manual full deploy.
write_previous "$TMP/full-site"
out="$TMP/manual.out"
: > "$TMP/empty-files"
GITHUB_OUTPUT="$out" \
  CHANGED_FILES_FILE="$TMP/empty-files" \
  PREVIOUS_SITE="$TMP/full-site" \
  EVENT_NAME=workflow_dispatch \
  FULL_DEPLOY_INPUT=true \
  DEPLOYED_AT="2026-09-23T00:00:00 UTC" \
  bash "$PLAN" > "$TMP/manual.log"
assert_eq "$(value "$out" full_deploy)" true "manual full_deploy"
assert_eq "$(value "$out" web_count)" "${#WEB_APPS[@]}" "manual web_count"

# Empty diff against a complete site, using git rather than a file list.
out="$TMP/git.out"
GITHUB_OUTPUT="$out" \
  PREVIOUS_SITE="$TMP/full-site" \
  EVENT_NAME=push \
  DIFF_BASE=HEAD \
  DIFF_HEAD=HEAD \
  FULL_DEPLOY_INPUT=false \
  DEPLOYED_AT="2026-09-23T00:00:00 UTC" \
  bash "$PLAN" > "$TMP/git.log"
assert_eq "$(value "$out" should_deploy)" false "git empty should_deploy"
assert_eq "$(value "$out" web_count)" 0 "git empty web_count"

echo "ci-deploy-plan tests passed"
