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

if [[ ! -f "$OUT/index.html" ]]; then
  echo "sync failed: missing $OUT/index.html" >&2
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
