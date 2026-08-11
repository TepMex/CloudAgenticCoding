#!/usr/bin/env bash
# Build rth-agriculture with relative base and copy into Android assets/www.
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
  "$OUT/assets/garden-map.png"
  "$OUT/assets/garden-map_negative.png"
)

# The V2 map renders the cleaning progress with four backdrops per field.
# Verify them here: a partial copy still opens in WebView but leaves the battle
# scene dark after the player starts writing.
for field in {1..15}; do
  for stage in full_dirty half_dirty quorter_dirty clean; do
    required_assets+=("$OUT/assets/battle-fields/field${field}/${stage}.png")
  done
done

for asset in "${required_assets[@]}"; do
  if [[ ! -s "$asset" ]]; then
    echo "sync failed: missing or empty bundled asset $asset" >&2
    exit 1
  fi
done

# Keep the assets directory trackable without committing the web build.
cat > "$OUT/.gitignore" <<'EOF'
# Bundled web build is produced by scripts/sync-web-assets.sh — do not commit.
*
!.gitkeep
!.gitignore
EOF
touch "$OUT/.gitkeep"

echo "Synced web assets ($(du -sh "$OUT" | awk '{print $1}'))"
