#!/usr/bin/env python3
"""Build glyph_embeddings.bin with EmbeddingGemma 2 LiteRT.

Renders each catalog character with Noto Sans CJK SC Regular and embeds the
PNG with the same LiteRT bundle the app downloads:

  litert-community/embeddinggemma-2-text-vision-440m-litert-lm
  revision 538e6574db281b4fe17993a3212f10c73bd678c6
  file embeddinggemma-2-text-vision-440m.litertlm (387710976 bytes)

Image contract (the app repeats it for characters outside the catalog):
  224×224 RGB, white background, black ink, font size 156 px,
  the ink bounding box centered in the canvas.

Embedding contract:
  CPU text and vision backends, 70 vision tokens, 768-d output, L2-normalized.
  Stored as little-endian float16 and renormalized when the app loads the file.

The model is not written into the APK. Pass --model and --font, or set
GLYPH_MODEL and GLYPH_FONT.
"""

from __future__ import annotations

import argparse
import io
import json
import struct
import sys
import time
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont

MAGIC = b"HZE1"
VERSION = 1
DIM = 768
VISION_TOKENS = 70
IMAGE_SIZE = 224
FONT_PX = 156
MODEL_BYTES = 387_710_976

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_CATALOG = ROOT / "app/src/main/assets/hanzi_components_gf0014_6152.json"
DEFAULT_OUT = ROOT / "app/src/main/assets/glyph_embeddings.bin"


def render_png(font: ImageFont.FreeTypeFont, character: str) -> bytes:
    image = Image.new("RGB", (IMAGE_SIZE, IMAGE_SIZE), (255, 255, 255))
    draw = ImageDraw.Draw(image)
    left, top, right, bottom = font.getbbox(character)
    width = right - left
    height = bottom - top
    if width <= 0 or height <= 0:
        raise RuntimeError(f"empty ink for U+{ord(character):04X} {character}")
    x = (IMAGE_SIZE - width) / 2 - left
    y = (IMAGE_SIZE - height) / 2 - top
    draw.text((x, y), character, font=font, fill=(0, 0, 0))
    buffer = io.BytesIO()
    image.save(buffer, format="PNG")
    return buffer.getvalue()


def load_characters(path: Path) -> list[str]:
    with path.open(encoding="utf-8") as handle:
        payload = json.load(handle)
    characters = list(payload["characters"])
    if len(characters) != 3500:
        raise RuntimeError(f"expected 3500 characters, got {len(characters)}")
    for character in characters:
        if len(character) != 1:
            raise RuntimeError(f"character is not one code point: {character!r}")
    return characters


def require_glyphs(font_path: Path, characters: list[str]) -> None:
    from fontTools.ttLib import TTFont

    cmap = TTFont(str(font_path)).getBestCmap() or {}
    missing = [character for character in characters if ord(character) not in cmap]
    if missing:
        shown = " ".join(missing[:20])
        raise RuntimeError(f"{len(missing)} characters missing from the font, including {shown}")


def write_bin(path: Path, characters: list[str], matrix: np.ndarray) -> None:
    if matrix.shape != (len(characters), DIM):
        raise RuntimeError(f"matrix shape {matrix.shape}")
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + ".tmp")
    with temporary.open("wb") as handle:
        handle.write(MAGIC)
        handle.write(
            struct.pack(
                "<HHIHHHH",
                VERSION,
                DIM,
                len(characters),
                VISION_TOKENS,
                IMAGE_SIZE,
                FONT_PX,
                0,
            )
        )
        raw = np.ascontiguousarray(matrix, dtype="<f2")
        for index, character in enumerate(characters):
            handle.write(struct.pack("<I", ord(character)))
            handle.write(raw[index].tobytes())
    temporary.replace(path)


def nearest(characters: list[str], matrix: np.ndarray, character: str, k: int = 8) -> list[tuple[str, float]]:
    index = characters.index(character)
    vectors = matrix.astype(np.float32)
    norms = np.linalg.norm(vectors, axis=1, keepdims=True)
    norms[norms == 0] = 1
    vectors = vectors / norms
    dots = vectors @ vectors[index]
    dots[index] = -2
    order = np.argsort(-dots)[:k]
    return [(characters[int(item)], float(1 - dots[int(item)])) for item in order]


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--catalog", type=Path, default=DEFAULT_CATALOG)
    parser.add_argument("--out", type=Path, default=DEFAULT_OUT)
    parser.add_argument("--model", type=Path, required=True)
    parser.add_argument("--font", type=Path, required=True)
    parser.add_argument("--partial", type=Path, required=True)
    args = parser.parse_args()

    if args.model.stat().st_size != MODEL_BYTES:
        raise RuntimeError(f"model size {args.model.stat().st_size} != {MODEL_BYTES}")

    characters = load_characters(args.catalog)
    require_glyphs(args.font, characters)
    font = ImageFont.truetype(str(args.font), FONT_PX)

    done = 0
    matrix = np.zeros((len(characters), DIM), dtype=np.float16)
    if args.partial.exists():
        saved = np.load(args.partial)
        if saved.shape == matrix.shape:
            matrix = saved
            norms = np.linalg.norm(matrix.astype(np.float32), axis=1)
            done = int(np.argmax(norms == 0)) if np.any(norms == 0) else len(characters)
            print(f"resume at {done}", flush=True)

    if done < len(characters):
        from litert_lm import Backend, Content, EmbeddingEngine, EmbeddingOptions
        from litert_lm._ffi import LogSeverity, set_min_log_severity

        set_min_log_severity(LogSeverity.ERROR)
        started = time.time()
        engine = EmbeddingEngine(
            str(args.model),
            backend=Backend.CPU(thread_count=4),
            vision_backend=Backend.CPU(thread_count=4),
            cache_dir=str(args.partial.parent / "litert-cache"),
            max_input_length=128,
            vision_tokens_per_image=VISION_TOKENS,
        )
        options = EmbeddingOptions(
            normalize=True,
            output_size=DIM,
            vision_tokens_per_image=VISION_TOKENS,
        )
        for index in range(done, len(characters)):
            png = render_png(font, characters[index])
            embedding = engine.compute_embedding(Content.ImageBytes(png), options).embedding
            if len(embedding) != DIM:
                raise RuntimeError(f"dim {len(embedding)} for {characters[index]}")
            vector = np.asarray(embedding, dtype=np.float32)
            norm = float(np.linalg.norm(vector))
            if norm == 0:
                raise RuntimeError(f"zero embedding for {characters[index]}")
            matrix[index] = (vector / norm).astype(np.float16)
            finished = index + 1
            if finished % 25 == 0 or finished == len(characters):
                elapsed = time.time() - started
                rate = (finished - done) / elapsed
                left = (len(characters) - finished) / rate if rate else 0
                print(
                    f"PROGRESS {finished}/{len(characters)} {rate:.2f}/s eta {left:.0f}s",
                    flush=True,
                )
                np.save(args.partial, matrix)

    write_bin(args.out, characters, matrix)
    probes = ["一", "人", "入", "未", "末", "己", "已", "巳", "日", "曰", "土", "士", "王", "玉", "木", "请", "好", "明", "大", "太"]
    for character in probes:
        pairs = " ".join(f"{other}:{distance:.3f}" for other, distance in nearest(characters, matrix, character))
        print(f"NEAR {character} {pairs}", flush=True)
    print(f"wrote {args.out} ({args.out.stat().st_size} bytes)", flush=True)


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        print(f"ERROR {error}", file=sys.stderr, flush=True)
        raise
