#!/usr/bin/env bash
# Build same-element with relative base and copy into Android assets/www.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
WEB="$(cd "$ROOT/../same-element" && pwd)"
OUT="$ROOT/app/src/main/assets/www"

if [[ ! -f "$WEB/package.json" ]]; then
  echo "same-element not found at $WEB" >&2
  exit 1
fi

mkdir -p "$OUT"

cd "$WEB"
if [[ ! -d node_modules ]]; then
  echo "Installing same-element dependencies…"
  bun install --frozen-lockfile
fi

echo "Building same-element → $OUT"
env -u GH_PAGES_PUBLIC_PATH bunx tsc -b
env -u GH_PAGES_PUBLIC_PATH bunx vite build --outDir "$OUT" --emptyOutDir

if [[ ! -s "$OUT/index.html" ]]; then
  echo "sync failed: missing index.html" >&2
  exit 1
fi

if [[ ! -s "$OUT/hanzi/江.json" ]]; then
  echo "sync failed: missing stroke data for 江" >&2
  exit 1
fi

max_bytes=$((95 * 1024 * 1024))
www_bytes="$(du -sb "$OUT" | awk '{print $1}')"
if (( www_bytes > max_bytes )); then
  echo "sync failed: bundled www is ${www_bytes} bytes (limit ${max_bytes})" >&2
  exit 1
fi

cat > "$OUT/.gitignore" <<'EOF'
# Bundled web build is produced by scripts/sync-web-assets.sh — do not commit.
*
!.gitkeep
!.gitignore
EOF
touch "$OUT/.gitkeep"

echo "Synced web assets ($(du -sh "$OUT" | awk '{print $1}'))"
