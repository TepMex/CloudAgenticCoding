#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="${DUOSHAO_ROOT:-$(CDPATH= cd -- "${SCRIPT_DIR}/.." && pwd)}"

MODEL_NAME="sherpa-onnx-paraformer-zh-small-2024-03-09"
MODEL_URL="https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/${MODEL_NAME}.tar.bz2"
SHERPA_RUNTIME_VERSION="1.13.5"
RUNTIME_PACKAGE_URL="https://registry.npmjs.org/sherpa-onnx/-/sherpa-onnx-${SHERPA_RUNTIME_VERSION}.tgz"
RUNTIME_URL="/speech/sherpa-paraformer-bridge.js"
RUNTIME_SOURCE=""
FORCE=0
DRY_RUN=0
WEIGHTS_ONLY=0

usage() {
  cat <<'EOF'
Download and configure DuoShao's local Paraformer INT8 assets.

Usage:
  bun run setup:speech -- [options]
  bash scripts/setup-speech-assets.sh [options]

Options:
  --model-url URL       Override the official Paraformer archive URL.
  --runtime-url URL     URL written to VITE_SHERPA_RUNTIME_URL.
  --runtime-source SRC  Use a custom ESM bridge instead of the bundled bridge.
  --runtime-package-url URL
                        Override the official sherpa-onnx WASM npm tarball.
  --force               Download and replace existing model assets.
  --weights-only        Install weights without writing .env.local. Useful
                        when the sherpa WebAssembly bridge is not ready yet.
  --dry-run             Print planned changes without downloading or writing.
  -h, --help            Show this help.

Environment:
  DUOSHAO_ROOT          Override the project root (used by automated tests).

The default model is the official 79 MB Chinese-English Paraformer INT8 model.
Large generated files remain ignored by git.
EOF
}

fail() {
  printf 'setup:speech: %s\n' "$1" >&2
  exit 1
}

while (($# > 0)); do
  case "$1" in
    --model-url)
      (($# >= 2)) || fail "--model-url requires a value"
      MODEL_URL="$2"
      shift 2
      ;;
    --runtime-url)
      (($# >= 2)) || fail "--runtime-url requires a value"
      RUNTIME_URL="$2"
      shift 2
      ;;
    --runtime-source)
      (($# >= 2)) || fail "--runtime-source requires a value"
      RUNTIME_SOURCE="$2"
      shift 2
      ;;
    --runtime-package-url)
      (($# >= 2)) || fail "--runtime-package-url requires a value"
      RUNTIME_PACKAGE_URL="$2"
      shift 2
      ;;
    --force)
      FORCE=1
      shift
      ;;
    --weights-only)
      WEIGHTS_ONLY=1
      shift
      ;;
    --dry-run)
      DRY_RUN=1
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      fail "unknown option: $1"
      ;;
  esac
done

case "$MODEL_URL" in
  *$'\n'*|*' '*) fail "model URL must not contain whitespace" ;;
esac
case "$RUNTIME_URL" in
  *$'\n'*|*' '*) fail "runtime URL must not contain whitespace" ;;
esac
case "$RUNTIME_PACKAGE_URL" in
  *$'\n'*|*' '*) fail "runtime package URL must not contain whitespace" ;;
esac

ASSET_DIR="${PROJECT_ROOT}/public/speech"
MODEL_PATH="${ASSET_DIR}/model.int8.onnx"
TOKENS_PATH="${ASSET_DIR}/tokens.txt"
RUNTIME_PATH="${ASSET_DIR}/sherpa-paraformer-bridge.js"
ENV_PATH="${PROJECT_ROOT}/.env.local"

if [[ "$RUNTIME_URL" == /* ]]; then
  case "$RUNTIME_URL" in
    *'/../'*|*'/..') fail "local runtime URL must not contain parent paths" ;;
  esac
  RUNTIME_PATH="${PROJECT_ROOT}/public${RUNTIME_URL}"
elif [[ -n "$RUNTIME_SOURCE" ]]; then
  fail "--runtime-source requires a local --runtime-url beginning with /"
fi

printf 'DuoShao speech asset setup\n'
printf '  project: %s\n' "$PROJECT_ROOT"
printf '  model:   %s\n' "$MODEL_URL"
printf '  output:  %s\n' "$ASSET_DIR"

if ((DRY_RUN == 1)); then
  printf '  mode:    dry run (no files will be changed)\n'
  printf '\nWould configure:\n'
  printf '  VITE_SHERPA_RUNTIME_URL=%s\n' "$RUNTIME_URL"
  printf '  VITE_PARAFORMER_MODEL_URL=/speech/model.int8.onnx\n'
  printf '  VITE_PARAFORMER_TOKENS_URL=/speech/tokens.txt\n'
  if [[ -n "$RUNTIME_SOURCE" ]]; then
    printf 'Would install runtime bridge from %s\n' "$RUNTIME_SOURCE"
  elif [[ $WEIGHTS_ONLY -eq 1 ]]; then
    printf 'Would install weights only; .env.local would stay unchanged.\n'
  elif [[ "$RUNTIME_URL" == /* ]]; then
    printf 'Would install sherpa-onnx WASM %s and the bundled bridge.\n' "$SHERPA_RUNTIME_VERSION"
  fi
  exit 0
fi

for command_name in node curl tar mktemp find awk install; do
  command -v "$command_name" >/dev/null 2>&1 || fail "required command is missing: ${command_name}"
done

mkdir -p "$ASSET_DIR"

TEMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/duoshao-speech.XXXXXX")"
ENV_TEMP="${ENV_PATH}.tmp.$$"
cleanup() {
  rm -rf -- "$TEMP_DIR"
  rm -f -- "$ENV_TEMP"
}
trap cleanup EXIT INT TERM

if [[ -f "$MODEL_PATH" && -f "$TOKENS_PATH" && $FORCE -eq 0 ]]; then
  printf 'Model and tokens already exist; keeping them. Use --force to replace.\n'
else
  ARCHIVE_PATH="${TEMP_DIR}/model.tar.bz2"
  EXTRACTED_DIR="${TEMP_DIR}/extracted"
  mkdir -p "$EXTRACTED_DIR"

  printf 'Downloading Paraformer archive…\n'
  curl --fail --location --retry 3 --progress-bar "$MODEL_URL" --output "$ARCHIVE_PATH"

  printf 'Checking archive paths…\n'
  if tar -tjf "$ARCHIVE_PATH" | awk '
    /^\// { bad = 1 }
    /(^|\/)\.\.($|\/)/ { bad = 1 }
    END { exit bad ? 0 : 1 }
  '; then
    fail "archive contains an unsafe absolute or parent path"
  fi

  tar -xjf "$ARCHIVE_PATH" -C "$EXTRACTED_DIR"

  MODEL_SOURCE="$(find "$EXTRACTED_DIR" -type f -name 'model.int8.onnx' -print -quit)"
  TOKENS_SOURCE="$(find "$EXTRACTED_DIR" -type f -name 'tokens.txt' -print -quit)"
  [[ -n "$MODEL_SOURCE" ]] || fail "archive does not contain model.int8.onnx"
  [[ -n "$TOKENS_SOURCE" ]] || fail "archive does not contain tokens.txt"
  [[ -s "$MODEL_SOURCE" ]] || fail "downloaded model.int8.onnx is empty"
  [[ -s "$TOKENS_SOURCE" ]] || fail "downloaded tokens.txt is empty"

  install -m 0644 "$MODEL_SOURCE" "$MODEL_PATH"
  install -m 0644 "$TOKENS_SOURCE" "$TOKENS_PATH"
  printf 'Installed model (%s bytes) and tokens.\n' "$(wc -c < "$MODEL_PATH" | tr -d ' ')"
fi

if ((WEIGHTS_ONLY == 1)); then
  printf '\nWeights are installed. .env.local was not changed.\n'
  printf 'Run setup:speech without --weights-only to install the browser runtime.\n'
  exit 0
fi

if [[ -n "$RUNTIME_SOURCE" ]]; then
  printf 'Installing sherpa runtime bridge…\n'
  mkdir -p "$(dirname -- "$RUNTIME_PATH")"
  case "$RUNTIME_SOURCE" in
    http://*|https://*|file://*)
      curl --fail --location --retry 3 --progress-bar "$RUNTIME_SOURCE" --output "${TEMP_DIR}/runtime.js"
      ;;
    *)
      [[ -f "$RUNTIME_SOURCE" ]] || fail "runtime bridge not found: ${RUNTIME_SOURCE}"
      install -m 0644 "$RUNTIME_SOURCE" "${TEMP_DIR}/runtime.js"
      ;;
  esac
  [[ -s "${TEMP_DIR}/runtime.js" ]] || fail "runtime bridge is empty"
  if ! grep -q 'createParaformerRecognizer' "${TEMP_DIR}/runtime.js"; then
    fail "runtime bridge does not mention createParaformerRecognizer"
  fi
  install -m 0644 "${TEMP_DIR}/runtime.js" "$RUNTIME_PATH"
elif [[ "$RUNTIME_URL" == /* ]]; then
  RUNTIME_DIR="$(dirname -- "$RUNTIME_PATH")"
  WASM_PATH="${RUNTIME_DIR}/sherpa-onnx-wasm.wasm"
  WASM_JS_PATH="${RUNTIME_DIR}/sherpa-onnx-wasm.js"
  ASR_JS_PATH="${RUNTIME_DIR}/sherpa-onnx-asr.js"
  if [[ $FORCE -eq 0 && -s "$WASM_PATH" && -s "$WASM_JS_PATH" && -s "$ASR_JS_PATH" ]]; then
    printf 'Browser sherpa runtime already exists; keeping its generated files. Use --force to replace.\n'
  else
    RUNTIME_ARCHIVE="${TEMP_DIR}/sherpa-onnx.tgz"
    RUNTIME_EXTRACTED="${TEMP_DIR}/sherpa-runtime"
    mkdir -p "$RUNTIME_EXTRACTED" "$RUNTIME_DIR"
    printf 'Downloading sherpa-onnx WebAssembly runtime %s…\n' "$SHERPA_RUNTIME_VERSION"
    curl --fail --location --retry 3 --progress-bar "$RUNTIME_PACKAGE_URL" --output "$RUNTIME_ARCHIVE"
    tar -xzf "$RUNTIME_ARCHIVE" -C "$RUNTIME_EXTRACTED"
    [[ -s "${RUNTIME_EXTRACTED}/package/sherpa-onnx-wasm-nodejs.wasm" ]] || fail "runtime package does not contain sherpa WebAssembly"
    node "${SCRIPT_DIR}/build-sherpa-browser-runtime.mjs" \
      "${RUNTIME_EXTRACTED}/package" \
      "$RUNTIME_DIR" \
      "${SCRIPT_DIR}/../speech-runtime/sherpa-paraformer-bridge.js"
  fi
  install -m 0644 "${SCRIPT_DIR}/../speech-runtime/sherpa-paraformer-bridge.js" "$RUNTIME_PATH"
fi

if [[ -f "$ENV_PATH" ]]; then
  awk '
    $0 == "# BEGIN DuoShao speech assets" { managed = 1; next }
    $0 == "# END DuoShao speech assets" { managed = 0; next }
    managed { next }
    /^VITE_SHERPA_RUNTIME_URL=/ { next }
    /^VITE_PARAFORMER_MODEL_URL=/ { next }
    /^VITE_PARAFORMER_TOKENS_URL=/ { next }
    /^VITE_PARAFORMER_CONFIG_URL=/ { next }
    { print }
  ' "$ENV_PATH" > "$ENV_TEMP"
else
  : > "$ENV_TEMP"
fi

if [[ -s "$ENV_TEMP" ]]; then
  printf '\n' >> "$ENV_TEMP"
fi
cat >> "$ENV_TEMP" <<EOF
# BEGIN DuoShao speech assets
VITE_SHERPA_RUNTIME_URL=${RUNTIME_URL}
VITE_PARAFORMER_MODEL_URL=/speech/model.int8.onnx
VITE_PARAFORMER_TOKENS_URL=/speech/tokens.txt
# END DuoShao speech assets
EOF
mv -- "$ENV_TEMP" "$ENV_PATH"

printf 'Updated %s\n' "$ENV_PATH"
printf '\nSpeech assets are configured. Restart the Vite development server.\n'
