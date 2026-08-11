#!/usr/bin/env bash
# Build rth-agriculture with relative base and copy into Android assets/www.
# Map/battle art is shipped as WebP so all 15 field backdrop sets fit under
# GitHub’s 100 MB APK push limit without remote downloads or art dedupe.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
WEB="$(cd "$ROOT/../rth-agriculture" && pwd)"
OUT="$ROOT/app/src/main/assets/www"

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
# Unset Pages base so Vite emits relative URLs suitable for file:///android_asset/
env -u GH_PAGES_PUBLIC_PATH bunx tsc -b
env -u GH_PAGES_PUBLIC_PATH bunx vite build --outDir "$OUT" --emptyOutDir

required_assets=(
  "$OUT/index.html"
  "$OUT/assets/garden-map.webp"
  "$OUT/assets/garden-map_negative.webp"
)

# V2 ships four cleaning backdrops per garden field (all bundled as WebP).
for field in {1..15}; do
  for stage in full_dirty half_dirty quorter_dirty clean; do
    required_assets+=("$OUT/assets/battle-fields/field${field}/${stage}.webp")
  done
done

for asset in "${required_assets[@]}"; do
  if [[ ! -s "$asset" ]]; then
    echo "sync failed: missing or empty bundled asset $asset" >&2
    exit 1
  fi
done

# GitHub rejects single blobs over 100MB on push; fail fast before assembleRelease.
max_bytes=$((95 * 1024 * 1024))
www_bytes="$(du -sb "$OUT" | awk '{print $1}')"
if (( www_bytes > max_bytes )); then
  echo "sync failed: bundled www is ${www_bytes} bytes (limit ${max_bytes}) — compress art further" >&2
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

echo "Synced web assets ($(du -sh "$OUT" | awk '{print $1}'))"
