#!/usr/bin/env bash
# Verify release APK is signed with the committed sideload certificate.
set -euo pipefail

apk_path="${1:?Usage: verify-apk-sideload-cert.sh <path-to.apk>}"
expected_file="$(dirname "$0")/expected-sideload-cert-sha256.txt"
expected="$(tr -d '[:space:]' < "$expected_file" | tr '[:upper:]' '[:lower:]')"

if ! command -v apksigner >/dev/null 2>&1; then
  echo "apksigner not found on PATH" >&2
  exit 1
fi

fingerprint="$(
  apksigner verify --print-certs "$apk_path" 2>/dev/null \
    | grep -m1 'SHA-256 digest:' \
    | sed -E 's/.*SHA-256 digest: //; s/[^0-9a-fA-F]//g' \
    | tr '[:upper:]' '[:lower:]'
)"

if [[ -z "$fingerprint" ]]; then
  echo "Could not read SHA-256 from apksigner for $apk_path" >&2
  exit 1
fi

if [[ "$fingerprint" != "$expected" ]]; then
  echo "APK signing cert mismatch for $apk_path" >&2
  echo "  expected: $expected" >&2
  echo "  actual:   $fingerprint" >&2
  exit 1
fi

echo "OK: $apk_path signed with sideload cert ($fingerprint)"
