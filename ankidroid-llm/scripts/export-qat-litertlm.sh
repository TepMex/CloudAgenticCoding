#!/usr/bin/env bash
# Convert google/gemma-4-E2B-it-qat-mobile-transformers (safetensors) to a .litertlm
# bundle for LiteRT-LM on Android. Requires ~16 GB RAM and litert-torch-nightly.
set -euo pipefail

MODEL="${MODEL:-google/gemma-4-E2B-it-qat-mobile-transformers}"
OUTPUT_DIR="${OUTPUT_DIR:-/tmp/gemma-4-e2b-it-qat-litertlm}"
JINJA_OVERRIDE="${JINJA_OVERRIDE:-litert-community/gemma-4-E2B-it-litert-lm}"

if ! command -v litert-torch >/dev/null 2>&1; then
  echo "Installing litert-torch-nightly…"
  pip install -U litert-torch-nightly
fi

mkdir -p "$OUTPUT_DIR"

litert-torch export_hf "$MODEL" "$OUTPUT_DIR" \
  --task=text_generation \
  --externalize_embedder=true \
  --bundle_litert_lm=true \
  --experimental_lightweight_conversion=true \
  --jinja_chat_template_override="$JINJA_OVERRIDE"

echo "Exported LiteRT-LM bundle:"
ls -lh "$OUTPUT_DIR"/*.litertlm 2>/dev/null || ls -lh "$OUTPUT_DIR"/model.litertlm
