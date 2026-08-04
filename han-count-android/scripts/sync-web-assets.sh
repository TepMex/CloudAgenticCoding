#!/usr/bin/env bash
# Build han-count-me with relative base and copy into Android assets/www.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
WEB="$(cd "$ROOT/../han-count-me" && pwd)"
OUT="$ROOT/app/src/main/assets/www"

if [[ ! -f "$WEB/package.json" ]]; then
  echo "han-count-me not found at $WEB" >&2
  exit 1
fi

mkdir -p "$OUT"

cd "$WEB"
if [[ ! -d node_modules ]]; then
  echo "Installing han-count-me dependencies…"
  npm ci
fi

echo "Building han-count-me → $OUT"
# Unset Pages base so Vite emits relative URLs suitable for file:///android_asset/
env -u GH_PAGES_PUBLIC_PATH npx tsc --noEmit
env -u GH_PAGES_PUBLIC_PATH npx vite build --outDir "$OUT" --emptyOutDir

if [[ ! -f "$OUT/index.html" ]]; then
  echo "sync failed: missing $OUT/index.html" >&2
  exit 1
fi

# Keep the assets directory trackable without committing the Phaser build.
cat > "$OUT/.gitignore" <<'EOF'
# Bundled Phaser build is produced by scripts/sync-web-assets.sh — do not commit.
*
!.gitkeep
!.gitignore
EOF
touch "$OUT/.gitkeep"

echo "Synced web assets ($(du -sh "$OUT" | awk '{print $1}'))"
