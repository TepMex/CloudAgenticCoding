#!/usr/bin/env bash
# Build rth-agriculture with relative base and copy into Android assets/www.
# Heavy map/battle PNGs are omitted from the APK and downloaded at runtime from
# the monorepo on GitHub (see VITE_REMOTE_ASSET_BASE / src/assetUrl.ts).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
WEB="$(cd "$ROOT/../rth-agriculture" && pwd)"
OUT="$ROOT/app/src/main/assets/www"

# Raw GitHub tree for public/ — keeps full-resolution PNG art out of the APK
# while still letting WebView fetch + cache it after first launch.
DEFAULT_REMOTE_ASSET_BASE="https://raw.githubusercontent.com/TepMex/CloudAgenticCoding/master/rth-agriculture/public/"
REMOTE_ASSET_BASE="${RTH_REMOTE_ASSET_BASE:-$DEFAULT_REMOTE_ASSET_BASE}"
# Ensure trailing slash for URL joins.
[[ "$REMOTE_ASSET_BASE" == */ ]] || REMOTE_ASSET_BASE="${REMOTE_ASSET_BASE}/"

if [[ ! -f "$WEB/package.json" ]]; then
  echo "rth-agriculture not found at $WEB" >&2
  exit 1
fi

mkdir -p "$OUT"

cd "$WEB"
if [[ ! -d node_modules ]]; then
  echo "Installing rth-agriculture dependencies…"
  bun install --frozen-lockfile
fi

echo "Building rth-agriculture → $OUT"
echo "Remote heavy art base: $REMOTE_ASSET_BASE"
# Unset Pages base so Vite emits relative URLs suitable for file:///android_asset/
# Bake the GitHub raw base into the bundle for map/battle PNGs.
env -u GH_PAGES_PUBLIC_PATH bunx tsc -b
env -u GH_PAGES_PUBLIC_PATH \
  VITE_REMOTE_ASSET_BASE="$REMOTE_ASSET_BASE" \
  bunx vite build --outDir "$OUT" --emptyOutDir

if [[ ! -s "$OUT/index.html" ]]; then
  echo "sync failed: missing $OUT/index.html" >&2
  exit 1
fi

# Drop heavy art that Vite copied from public/ — the APK must stay under GitHub's
# 100 MB blob limit. Runtime loads these from REMOTE_ASSET_BASE instead.
rm -f \
  "$OUT/assets/garden-map.png" \
  "$OUT/assets/garden-map_negative.png" \
  "$OUT/assets/cleaning-court.png" \
  "$OUT/assets/cleaning-court-clear.png"
rm -rf "$OUT/assets/battle-fields"

# Hanzi stroke JSON + JS/CSS must remain bundled for offline battles.
if [[ ! -d "$OUT/hanzi" ]]; then
  echo "sync failed: missing bundled hanzi stroke data under $OUT/hanzi" >&2
  exit 1
fi

# GitHub rejects single blobs over 100MB on push; fail fast before assembleRelease.
max_bytes=$((95 * 1024 * 1024))
www_bytes="$(du -sb "$OUT" | awk '{print $1}')"
if (( www_bytes > max_bytes )); then
  echo "sync failed: bundled www is ${www_bytes} bytes (limit ${max_bytes})" >&2
  exit 1
fi

# Keep the assets directory trackable without committing the web build.
cat > "$OUT/.gitignore" <<'EOF'
# Bundled web build is produced by scripts/sync-web-assets.sh — do not commit.
*
!.gitkeep
!.gitignore
EOF
touch "$OUT/.gitkeep"

echo "Synced web assets ($(du -sh "$OUT" | awk '{print $1}')); heavy art → $REMOTE_ASSET_BASE"
