#!/usr/bin/env bash
# Build one web app for GitHub Pages. Run from the repository root.
set -euo pipefail

app="${1:?Usage: ci-build-web.sh <app>}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

repo_name="${GITHUB_REPOSITORY_NAME:-}"
if [[ -z "$repo_name" && -n "${GITHUB_REPOSITORY:-}" ]]; then
  repo_name="${GITHUB_REPOSITORY##*/}"
fi
repo_name="${repo_name:-CloudAgenticCoding}"
export GH_PAGES_PUBLIC_PATH="/${repo_name}/${app}/"

cd "$app"
case "$app" in
  han-count-me)
    npm ci
    npm run build
    ;;
  map-of-chinese)
    bun install --frozen-lockfile
    bun run data:build
    bun run build
    ;;
  *)
    bun install --frozen-lockfile
    bun run build
    ;;
esac

if [[ ! -f dist/index.html ]]; then
  echo "Build failed: $app/dist/index.html is missing" >&2
  exit 1
fi
