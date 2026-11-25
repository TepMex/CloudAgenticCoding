#!/usr/bin/env python3
"""Build the 词云 embedding database and the web cloud layout.

Reads the Simplified field (plus gloss, pinyin, and tags) from two Anki decks,
embeds each sense with EmbeddingGemma 2 under the clustering prompt, and writes:

- data/embeddings.sqlite — 768-d float32 vectors and metadata
- src/data/cloud.json — 2D/3D layout, cluster names, nearest neighbors

Rebuilds skip the model when /tmp/ci-yun-cache already holds vectors for the
same texts. Torch CPU is expected; do not run this in float16.
"""

from __future__ import annotations

import argparse
import hashlib
import html
import json
import re
import sqlite3
import zipfile
from pathlib import Path

import numpy as np

CACHE_DEFAULT = Path("/tmp/ci-yun-cache")
MODEL_ID = "google/embeddinggemma-2"
PROMPT_NAME = "Clustering"
EMBED_DIM = 768
SEED = 42
NEIGHBORS = 8
K_CANDIDATES = (20, 24, 28, 32)

CJK_RE = re.compile(r"[\u3400-\u9fff]")
HANGUL_RE = re.compile(r"[\uac00-\ud7af]+")
TAG_RE = re.compile(r"<[^>]+>")
HSK_RE = re.compile(r"\bHSK[1-6]\b")
WS_RE = re.compile(r"\s+")


def clean(value: str) -> str:
    text = html.unescape(value or "")
    text = TAG_RE.sub(" ", text)
    text = HANGUL_RE.sub(" ", text)
    text = WS_RE.sub(" ", text).strip(" ;,")
    return text


def short_gloss(value: str, limit: int = 180) -> str:
    text = clean(value)
    if len(text) <= limit:
        return text
    cut = text[:limit].rsplit(" ", 1)[0]
    return cut or text[:limit]


def unicase(left: str | None, right: str | None) -> int:
    if left is None and right is None:
        return 0
    if left is None:
        return -1
    if right is None:
        return 1
    a = str(left).lower()
    b = str(right).lower()
    return (a > b) - (a < b)


def decompress_collection(apkg: Path) -> bytes:
    with zipfile.ZipFile(apkg) as archive:
        name = "collection.anki21b" if "collection.anki21b" in archive.namelist() else "collection.anki2"
        raw = archive.read(name)
    if name.endswith("anki21b"):
        import zstandard

        # Anki frames omit the content size, so the one-shot decoder needs a cap.
        raw = zstandard.ZstdDecompressor().decompress(raw, max_output_size=128 * 1024 * 1024)
    return raw


def open_collection(apkg: Path) -> sqlite3.Connection:
    # In-memory deserialize accepts the bytes and then refuses queries on this
    # build, so the collection is opened from a temp file.
    raw = decompress_collection(apkg)
    CACHE_DEFAULT.mkdir(parents=True, exist_ok=True)
    tmp = CACHE_DEFAULT / f"{apkg.stem}.anki21"
    tmp.write_bytes(raw)
    connection = sqlite3.connect(tmp)
    connection.create_collation("unicase", unicase)
    return connection


def read_notes(apkg: Path, source: str, columns: dict[str, int]) -> list[dict]:
    connection = open_collection(apkg)
    rows = list(connection.execute("SELECT flds, tags FROM notes"))

    notes = []
    for fields_blob, tags in rows:
        fields = fields_blob.split("\x1f")
        simplified = clean(fields[columns["simplified"]]) if columns["simplified"] < len(fields) else ""
        if not CJK_RE.search(simplified):
            continue
        meaning = short_gloss(fields[columns["meaning"]] if columns["meaning"] < len(fields) else "")
        meaning_ru = short_gloss(fields[columns["meaning_ru"]] if columns["meaning_ru"] < len(fields) else "")
        pinyin = clean(fields[columns["pinyin"]] if columns["pinyin"] < len(fields) else "")
        traditional = clean(fields[columns["traditional"]] if columns["traditional"] < len(fields) else "")
        pos = clean(fields[columns["pos_ru"]] if columns["pos_ru"] < len(fields) else "")
        if not pos and columns["pos"] < len(fields):
            pos = clean(fields[columns["pos"]])
        levels = sorted(set(HSK_RE.findall(tags or "")))
        notes.append(
            {
                "simplified": simplified,
                "traditional": traditional,
                "pinyin": pinyin,
                "meaning": meaning,
                "meaningRu": meaning_ru,
                "pos": pos,
                "sources": [source],
                "hsk": levels,
                "embeddedText": f"{simplified}: {meaning}" if meaning else simplified,
            }
        )
    return notes


def merge_notes(hsk: list[dict], refold: list[dict]) -> list[dict]:
    """Keep distinct senses. Merge a cross-deck pair when the gloss agrees."""
    combined = hsk + refold
    merged: list[dict] = []
    index: dict[tuple[str, str], dict] = {}
    for note in combined:
        key = (note["simplified"], note["meaning"].casefold())
        existing = index.get(key)
        if existing is None:
            index[key] = note
            merged.append(note)
            continue
        for source in note["sources"]:
            if source not in existing["sources"]:
                existing["sources"].append(source)
        if not existing["meaningRu"] and note["meaningRu"]:
            existing["meaningRu"] = note["meaningRu"]
        if not existing["pinyin"] and note["pinyin"]:
            existing["pinyin"] = note["pinyin"]
        existing["hsk"] = sorted(set(existing["hsk"]) | set(note["hsk"]))
    merged.sort(key=lambda note: (note["simplified"], note["meaning"], note["pinyin"]))
    for number, note in enumerate(merged):
        note["id"] = number
    return merged


def text_hash(notes: list[dict]) -> str:
    digest = hashlib.sha256()
    for note in notes:
        digest.update(note["embeddedText"].encode())
        digest.update(b"\0")
    return digest.hexdigest()[:16]


PROMPT_PREFIX = "task: clustering | query: "


def load_model():
    import torch
    from transformers import AutoConfig, AutoModel, AutoTokenizer

    # SentenceTransformer insists on the image processor. The text tower is the
    # whole model we need, so vision and audio configs are dropped before load.
    torch.set_num_threads(4)
    print("loading", MODEL_ID, flush=True)
    config = AutoConfig.from_pretrained(MODEL_ID)
    config.vision_config = None
    config.audio_config = None
    model = AutoModel.from_pretrained(
        MODEL_ID,
        config=config,
        torch_dtype=torch.float32,
        low_cpu_mem_usage=True,
    )
    model.eval()
    tokenizer = AutoTokenizer.from_pretrained(MODEL_ID)
    print("prompt", PROMPT_PREFIX, "params", sum(parameter.numel() for parameter in model.parameters()), flush=True)
    return model, tokenizer


def encode_batch(model, tokenizer, texts: list[str]) -> np.ndarray:
    import torch

    prompted = [PROMPT_PREFIX + text for text in texts]
    inputs = tokenizer(prompted, padding=True, truncation=True, max_length=96, return_tensors="pt")
    with torch.inference_mode():
        hidden = model(**inputs).last_hidden_state
    mask = inputs["attention_mask"].unsqueeze(-1).to(hidden.dtype)
    pooled = (hidden * mask).sum(dim=1) / mask.sum(dim=1).clamp(min=1e-9)
    pooled = torch.nn.functional.normalize(pooled.float(), p=2, dim=-1)
    return pooled.numpy().astype(np.float32)


def encode_notes(notes: list[dict], cache: Path, digest: str) -> np.ndarray:
    vectors_path = cache / "embeddings.npy"
    meta_path = cache / "embeddings.json"
    if meta_path.exists() and vectors_path.exists():
        meta = json.loads(meta_path.read_text())
        if meta.get("hash") == digest and meta.get("dim") == EMBED_DIM and meta.get("count") == len(notes):
            vectors = np.load(vectors_path)
            if vectors.shape == (len(notes), EMBED_DIM) and meta.get("complete"):
                print(f"reuse embeddings {vectors.shape}", flush=True)
                return vectors

    model, tokenizer = load_model()
    texts = [note["embeddedText"] for note in notes]
    vectors = np.zeros((len(texts), EMBED_DIM), dtype=np.float32)
    batch = 32
    done = 0
    if meta_path.exists():
        meta = json.loads(meta_path.read_text())
        if meta.get("hash") == digest and vectors_path.exists() and not meta.get("complete"):
            partial = np.load(vectors_path)
            done = int(meta.get("done", 0))
            if partial.shape == vectors.shape and done > 0:
                vectors[:done] = partial[:done]
                print(f"resume at {done}", flush=True)

    import torch

    with torch.inference_mode():
        while done < len(texts):
            chunk = texts[done : done + batch]
            encoded = encode_batch(model, tokenizer, chunk)
            if encoded.shape[1] != EMBED_DIM:
                raise SystemExit(f"expected {EMBED_DIM} dims, got {encoded.shape}")
            vectors[done : done + len(chunk)] = encoded
            done += len(chunk)
            if done % 128 == 0 or done == len(texts):
                np.save(vectors_path, vectors)
                meta_path.write_text(
                    json.dumps(
                        {
                            "hash": digest,
                            "dim": EMBED_DIM,
                            "count": len(texts),
                            "done": done,
                            "complete": done == len(texts),
                            "model": MODEL_ID,
                            "prompt": PROMPT_NAME,
                        }
                    )
                )
                print(f"encoded {done}/{len(texts)}", flush=True)

    norms = np.linalg.norm(vectors, axis=1)
    if not np.allclose(norms, 1.0, atol=1e-3):
        raise SystemExit(f"embeddings are not unit length: {norms.min():.4f}..{norms.max():.4f}")
    del model, tokenizer
    return vectors


def choose_k(vectors: np.ndarray, requested: int) -> tuple[int, np.ndarray, dict[str, float]]:
    from sklearn.cluster import KMeans
    from sklearn.metrics import silhouette_score

    if requested:
        model = KMeans(n_clusters=requested, random_state=SEED, n_init=10)
        labels = model.fit_predict(vectors)
        return requested, labels, {"requested": float(requested)}

    scores: dict[str, float] = {}
    best_k = K_CANDIDATES[0]
    best_labels = None
    best_score = -1.0
    sample = min(2500, len(vectors))
    for k in K_CANDIDATES:
        model = KMeans(n_clusters=k, random_state=SEED, n_init=8)
        labels = model.fit_predict(vectors)
        score = float(
            silhouette_score(vectors, labels, metric="euclidean", sample_size=sample, random_state=SEED)
        )
        sizes = np.bincount(labels)
        print(f"k={k} silhouette={score:.4f} min={sizes.min()} max={sizes.max()}", flush=True)
        scores[str(k)] = score
        # Prefer a readable map: penalize a pile of tiny clusters.
        adjusted = score - (0.02 if sizes.min() < 40 else 0.0)
        if adjusted > best_score:
            best_score = adjusted
            best_k = k
            best_labels = labels
    assert best_labels is not None
    print(f"chosen k={best_k}", flush=True)
    return best_k, best_labels, scores


def project(vectors: np.ndarray) -> tuple[np.ndarray, np.ndarray]:
    import umap

    def fit(components: int, min_dist: float) -> np.ndarray:
        mapper = umap.UMAP(
            n_components=components,
            n_neighbors=25,
            min_dist=min_dist,
            metric="cosine",
            random_state=SEED,
            low_memory=True,
        )
        coords = mapper.fit_transform(vectors)
        coords = np.asarray(coords, dtype=np.float64)
        coords -= coords.mean(axis=0)
        rms = float(np.sqrt((coords**2).sum(axis=1).mean()))
        if rms < 1e-8:
            rms = 1.0
        return coords / rms * 10.0

    print("umap 2d", flush=True)
    flat = fit(2, 0.08)
    print("umap 3d", flush=True)
    space = fit(3, 0.12)
    return flat, space


def nearest_neighbors(vectors: np.ndarray) -> np.ndarray:
    from sklearn.neighbors import NearestNeighbors

    finder = NearestNeighbors(n_neighbors=NEIGHBORS + 1, metric="cosine")
    finder.fit(vectors)
    indices = finder.kneighbors(vectors, return_distance=False)
    return indices[:, 1:].astype(np.int32)


def hsl_hex(index: int) -> str:
    hue = (index * 137.508) % 360.0
    # HSL to RGB with s=0.58, l=0.62, tuned for a dark field.
    saturation = 0.58
    lightness = 0.62
    c = (1 - abs(2 * lightness - 1)) * saturation
    h = hue / 60.0
    x = c * (1 - abs(h % 2 - 1))
    m = lightness - c / 2
    sector = int(h) % 6
    rgb = [
        (c, x, 0),
        (x, c, 0),
        (0, c, x),
        (0, x, c),
        (x, 0, c),
        (c, 0, x),
    ][sector]
    red, green, blue = (round((channel + m) * 255) for channel in rgb)
    return f"#{red:02x}{green:02x}{blue:02x}"


def cluster_review(notes: list[dict], vectors: np.ndarray, labels: np.ndarray) -> list[dict]:
    review = []
    for cluster_id in range(int(labels.max()) + 1):
        members = np.flatnonzero(labels == cluster_id)
        centroid = vectors[members].mean(axis=0)
        centroid /= np.linalg.norm(centroid) + 1e-9
        order = np.argsort(-(vectors[members] @ centroid))
        examples = []
        for rank in order[:14]:
            note = notes[int(members[rank])]
            examples.append(
                {
                    "simplified": note["simplified"],
                    "pinyin": note["pinyin"],
                    "meaning": note["meaning"],
                    "meaningRu": note["meaningRu"],
                }
            )
        review.append({"id": cluster_id, "size": int(members.size), "examples": examples})
    review.sort(key=lambda item: -item["size"])
    return review


def load_names(path: Path | None, k: int) -> list[str]:
    if path is None or not path.exists():
        return [f"Кластер {index + 1}" for index in range(k)]
    payload = json.loads(path.read_text())
    names = payload["names"]
    if len(names) != k:
        raise SystemExit(f"{path} has {len(names)} names, layout has {k} clusters")
    if any(not str(name).strip() for name in names):
        raise SystemExit("cluster names must be non-empty")
    return [str(name).strip() for name in names]


def write_outputs(
    notes: list[dict],
    vectors: np.ndarray,
    labels: np.ndarray,
    flat: np.ndarray,
    space: np.ndarray,
    neighbors: np.ndarray,
    names: list[str],
    scores: dict[str, float],
    json_path: Path,
    sqlite_path: Path,
) -> None:
    k = len(names)
    clusters = []
    for cluster_id in range(k):
        clusters.append(
            {
                "id": cluster_id,
                "name": names[cluster_id],
                "color": hsl_hex(cluster_id),
                "size": int(np.sum(labels == cluster_id)),
            }
        )
    words = []
    for note, label, xy, xyz, neigh in zip(notes, labels, flat, space, neighbors, strict=True):
        words.append(
            {
                "id": note["id"],
                "simplified": note["simplified"],
                "traditional": note["traditional"],
                "pinyin": note["pinyin"],
                "meaning": note["meaning"],
                "meaningRu": note["meaningRu"],
                "pos": note["pos"],
                "sources": note["sources"],
                "hsk": note["hsk"],
                "cluster": int(label),
                "x2": round(float(xy[0]), 4),
                "y2": round(float(xy[1]), 4),
                "x": round(float(xyz[0]), 4),
                "y": round(float(xyz[1]), 4),
                "z": round(float(xyz[2]), 4),
                "neighbors": [int(item) for item in neigh],
            }
        )
    payload = {
        "model": MODEL_ID,
        "prompt": PROMPT_NAME,
        "dim": EMBED_DIM,
        "seed": SEED,
        "k": k,
        "silhouette": scores,
        "clusters": clusters,
        "words": words,
    }
    json_path.parent.mkdir(parents=True, exist_ok=True)
    json_path.write_text(json.dumps(payload, ensure_ascii=False, separators=(",", ":")))
    print(f"wrote {json_path} ({json_path.stat().st_size} bytes)", flush=True)

    sqlite_path.parent.mkdir(parents=True, exist_ok=True)
    if sqlite_path.exists():
        sqlite_path.unlink()
    database = sqlite3.connect(sqlite_path)
    database.execute("CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT NOT NULL)")
    database.execute(
        """
        CREATE TABLE words (
          id INTEGER PRIMARY KEY,
          simplified TEXT NOT NULL,
          traditional TEXT NOT NULL,
          pinyin TEXT NOT NULL,
          meaning_en TEXT NOT NULL,
          meaning_ru TEXT NOT NULL,
          pos TEXT NOT NULL,
          sources TEXT NOT NULL,
          hsk_levels TEXT NOT NULL,
          embedded_text TEXT NOT NULL,
          cluster_id INTEGER NOT NULL,
          x REAL NOT NULL,
          y REAL NOT NULL,
          z REAL NOT NULL,
          x2 REAL NOT NULL,
          y2 REAL NOT NULL,
          embedding BLOB NOT NULL
        )
        """
    )
    database.execute(
        """
        CREATE TABLE neighbors (
          word_id INTEGER NOT NULL,
          rank INTEGER NOT NULL,
          neighbor_id INTEGER NOT NULL,
          PRIMARY KEY (word_id, rank)
        )
        """
    )
    meta = {
        "model": MODEL_ID,
        "prompt": "task: clustering | query: ",
        "prompt_name": PROMPT_NAME,
        "dim": str(EMBED_DIM),
        "dtype": "float32",
        "normalized": "true",
        "seed": str(SEED),
        "k": str(k),
        "embedded_text": "{simplified}: {english gloss}",
        "count": str(len(notes)),
    }
    database.executemany("INSERT INTO meta (key, value) VALUES (?, ?)", meta.items())
    database.executemany(
        """
        INSERT INTO words (
          id, simplified, traditional, pinyin, meaning_en, meaning_ru, pos, sources,
          hsk_levels, embedded_text, cluster_id, x, y, z, x2, y2, embedding
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        [
            (
                note["id"],
                note["simplified"],
                note["traditional"],
                note["pinyin"],
                note["meaning"],
                note["meaningRu"],
                note["pos"],
                json.dumps(note["sources"], ensure_ascii=False),
                json.dumps(note["hsk"], ensure_ascii=False),
                note["embeddedText"],
                int(label),
                float(xyz[0]),
                float(xyz[1]),
                float(xyz[2]),
                float(xy[0]),
                float(xy[1]),
                vectors[note["id"]].astype(np.float32).tobytes(),
            )
            for note, label, xy, xyz in zip(notes, labels, flat, space, strict=True)
        ],
    )
    database.executemany(
        "INSERT INTO neighbors (word_id, rank, neighbor_id) VALUES (?, ?, ?)",
        [
            (note["id"], rank, int(neighbor))
            for note, neigh in zip(notes, neighbors, strict=True)
            for rank, neighbor in enumerate(neigh)
        ],
    )
    database.execute("CREATE INDEX idx_words_simplified ON words(simplified)")
    database.commit()
    database.close()
    print(f"wrote {sqlite_path} ({sqlite_path.stat().st_size} bytes)", flush=True)


def sanity(notes: list[dict], vectors: np.ndarray) -> None:
    by_text = {note["simplified"]: note["id"] for note in notes}
    probes = [("猫", "狗"), ("猫", "经济"), ("苹果", "香蕉"), ("苹果", "银行")]
    for left, right in probes:
        if left not in by_text or right not in by_text:
            print(f"skip pair {left} {right}", flush=True)
            continue
        score = float(vectors[by_text[left]] @ vectors[by_text[right]])
        print(f"sim {left}/{right} = {score:.3f}", flush=True)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--hsk", type=Path, required=True)
    parser.add_argument("--refold", type=Path, required=True)
    parser.add_argument("--json", type=Path, required=True)
    parser.add_argument("--sqlite", type=Path, required=True)
    parser.add_argument("--names", type=Path, default=None)
    parser.add_argument("--cache", type=Path, default=CACHE_DEFAULT)
    parser.add_argument("--k", type=int, default=0)
    args = parser.parse_args()
    args.cache.mkdir(parents=True, exist_ok=True)

    hsk_columns = {
        "simplified": 1,
        "traditional": 2,
        "pinyin": 3,
        "meaning": 5,
        "pos": 6,
        "meaning_ru": 19,
        "pos_ru": 21,
    }
    refold_columns = {
        "simplified": 1,
        "traditional": 2,
        "pinyin": 3,
        "meaning": 4,
        "pos": 5,
        "meaning_ru": 15,
        "pos_ru": 17,
    }
    notes = merge_notes(
        read_notes(args.hsk, "hsk", hsk_columns),
        read_notes(args.refold, "refold", refold_columns),
    )
    print(f"notes {len(notes)}", flush=True)
    digest = text_hash(notes)
    (args.cache / "notes.json").write_text(json.dumps(notes, ensure_ascii=False))
    vectors = encode_notes(notes, args.cache, digest)
    sanity(notes, vectors)

    layout_path = args.cache / "layout.npz"
    layout_meta_path = args.cache / "layout.json"
    labels: np.ndarray
    flat: np.ndarray
    space: np.ndarray
    neighbors: np.ndarray
    scores: dict[str, float]
    k: int
    reuse_layout = False
    if layout_path.exists() and layout_meta_path.exists():
        meta = json.loads(layout_meta_path.read_text())
        if meta.get("hash") == digest and (args.k == 0 or meta.get("k") == args.k):
            blob = np.load(layout_path)
            labels = blob["labels"]
            flat = blob["flat"]
            space = blob["space"]
            neighbors = blob["neighbors"]
            scores = meta["scores"]
            k = int(meta["k"])
            reuse_layout = True
            print(f"reuse layout k={k}", flush=True)
    if not reuse_layout:
        k, labels, scores = choose_k(vectors, args.k)
        flat, space = project(vectors)
        neighbors = nearest_neighbors(vectors)
        np.savez_compressed(
            layout_path, labels=labels, flat=flat, space=space, neighbors=neighbors
        )
        layout_meta_path.write_text(
            json.dumps({"hash": digest, "k": k, "scores": scores, "seed": SEED})
        )
    review = cluster_review(notes, vectors, labels)
    review_path = args.cache / "review.json"
    review_path.write_text(json.dumps(review, ensure_ascii=False, indent=2))
    print(f"review {review_path}", flush=True)
    for cluster in review:
        head = " · ".join(
            f"{item['simplified']} {item['meaningRu'] or item['meaning']}" for item in cluster["examples"][:8]
        )
        print(f"[{cluster['id']}] n={cluster['size']} {head}", flush=True)

    names = load_names(args.names, k)
    write_outputs(notes, vectors, labels, flat, space, neighbors, names, scores, args.json, args.sqlite)


if __name__ == "__main__":
    main()
