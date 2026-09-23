#!/usr/bin/env bash
# Fetch bundled PP-OCRv5 LiteRT weights if missing or checksum-mismatched.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DEST="$ROOT/app/src/main/assets/ocr"
BASE="https://huggingface.co/litert-community/PP-OCRv5-LiteRT/resolve/main"
UA="instant-pinyin-build/1.0"

mkdir -p "$DEST"

fetch() {
  local name="$1" sha="$2"
  local path="$DEST/$name"
  if [[ -f "$path" ]]; then
    local got
    got="$(sha256sum "$path" | awk '{print $1}')"
    if [[ "$got" == "$sha" ]]; then
      echo "ok $name"
      return
    fi
    echo "checksum mismatch for $name, re-downloading" >&2
    rm -f "$path"
  fi
  echo "downloading $name"
  curl -fL --retry 4 --retry-delay 4 -A "$UA" -o "$path" "$BASE/$name"
  local got
  got="$(sha256sum "$path" | awk '{print $1}')"
  if [[ "$got" != "$sha" ]]; then
    echo "bad checksum for $name: $got" >&2
    exit 1
  fi
}

fetch ppocr_det_fp16.tflite b635b1d7f0e171a19beda7e8f386a62d4f0a3a1c46ed42a09603a902f2059ccc
fetch ppocr_rec_fp16.tflite ef7bb5aba20a1717101f0f112dd8cb1ed8b043ebaf96af05a395c6905ca66456
fetch ppocrv5_dict.txt d1979e9f794c464c0d2e0b70a7fe14dd978e9dc644c0e71f14158cdf8342af1b
