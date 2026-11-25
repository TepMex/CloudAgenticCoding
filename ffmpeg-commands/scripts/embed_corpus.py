#!/usr/bin/env python3
"""Embed corpus.json with EmbeddingGemma 2. The model file stays out of git."""

from __future__ import annotations

import json
import sys
import time
from pathlib import Path

import numpy as np
from litert_lm import Backend, EmbeddingEngine, EmbeddingOptions, InputOverflowStrategy

ROOT = Path(__file__).resolve().parents[1]
CORPUS_PATH = ROOT / "data" / "corpus.json"
MODEL_PATH = ROOT / "data" / "model" / "embeddinggemma-2-text-270m.litertlm"
VECTOR_PATH = ROOT / "data" / "embeddings.f32"
META_PATH = ROOT / "data" / "embeddings.json"
BATCH = 32


def main() -> None:
    if not MODEL_PATH.exists():
        sys.exit(
            f"Missing {MODEL_PATH}. Download {json.loads(CORPUS_PATH.read_text())['model']['url']} "
            "there. The app does not ship this file."
        )
    corpus = json.loads(CORPUS_PATH.read_text())
    chunks = corpus["chunks"]
    model = corpus["model"]
    prefix = model["documentPrefix"]
    dim = int(model["outputSize"])
    texts = [prefix + chunk["embedText"].strip() for chunk in chunks]

    engine = EmbeddingEngine(
        str(MODEL_PATH),
        backend=Backend.CPU(thread_count=8),
        max_input_length=int(model["maxInputLength"]),
    )
    options = EmbeddingOptions(
        normalize=True,
        output_size=dim,
        input_overflow_strategy=InputOverflowStrategy.TRUNCATE,
    )

    vectors = np.empty((len(texts), dim), dtype=np.float32)
    started = time.time()
    for offset in range(0, len(texts), BATCH):
        batch = texts[offset : offset + BATCH]
        responses = engine.compute_embedding_batch(batch, options)
        if len(responses) != len(batch):
            sys.exit(f"Batch size mismatch at {offset}: {len(responses)} != {len(batch)}")
        for index, response in enumerate(responses):
            row = np.asarray(response.embedding, dtype=np.float32)
            if row.shape != (dim,):
                sys.exit(f"Unexpected dim at {offset + index}: {row.shape}")
            vectors[offset + index] = row
        done = min(offset + BATCH, len(texts))
        print(f"{done}/{len(texts)}  {time.time() - started:.1f}s", flush=True)

    vectors.tofile(VECTOR_PATH)
    meta = {
        "corpusId": corpus["id"],
        "file": "embeddings.f32",
        "dim": dim,
        "count": len(texts),
        "normalized": True,
        "modelUrl": model["url"],
        "documentPrefix": prefix,
        "outputSize": dim,
    }
    META_PATH.write_text(json.dumps(meta, indent=2) + "\n")
    print(f"wrote {VECTOR_PATH} ({VECTOR_PATH.stat().st_size} bytes)")


if __name__ == "__main__":
    main()
