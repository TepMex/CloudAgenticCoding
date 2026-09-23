# Shared app catalog for GitHub Pages deploy.
# Adding an app: append it here. Do not duplicate the list in deploy.yml.
# A web app bundled into an Android wrapper needs android_for_web and android_web_runtime.

WEB_APPS=(
  socratus
  mandarin-koan
  hanzi-info
  hanzi-reading-roguelike
  han-count-me
  sense-of-text
  map-of-chinese
  mandarin-kanshu-pengyou
  rth-agriculture
  same-element
)

ANDROID_APPS=(
  chesswatch
  ankidroid-llm
  anki-entertainer
  local-tts
  anki-dashboard-apk
  zuo-tasks
  ctx-calendar
  wo-zai-naar
  zou-lu-shang
  zou-lu-shang-2
  pair-comp-elo
  running-log
  ideal-timing
  han-count-android
  rth-agriculture-android
  same-element-android
  wo-de-luyou
  paizhao-unknown-hanzi
  duoshao-qian
)

# Repo-root files copied onto the site on every publish.
STATIC_ROOT_FILES=(
  china_rail_interactive_map.html
)

# Script changes that must refresh Android landing HTML (not CI helpers).
LANDING_SCRIPT_FILES=(
  scripts/inject-static-site-footer.ts
  scripts/deploy-metadata.ts
)

SIGNING_FILES=(
  android/sideload-signing.gradle.kts
  android/verify-apk-sideload-cert.sh
  android/expected-sideload-cert-sha256.txt
)

web_runtime() {
  case "$1" in
    han-count-me) echo node ;;
    *) echo bun ;;
  esac
}

# Runtime required to sync a bundled web app before the Android Gradle build.
android_web_runtime() {
  case "$1" in
    han-count-android) echo node ;;
    rth-agriculture-android | same-element-android) echo bun ;;
    *) echo none ;;
  esac
}

# Android wrapper that must rebuild when its web app changes.
android_for_web() {
  case "$1" in
    han-count-me) echo han-count-android ;;
    rth-agriculture) echo rth-agriculture-android ;;
    same-element) echo same-element-android ;;
    *) echo "" ;;
  esac
}
