"""Download and verify pinned source artifacts."""

from __future__ import annotations

import hashlib
import json
import urllib.request
from pathlib import Path
from typing import Any


def load_lock(lock_path: Path) -> dict[str, Any]:
    return json.loads(lock_path.read_text(encoding="utf-8"))


def sha256_file(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def ensure_sources(lock: dict[str, Any], cache_dir: Path, *, offline: bool = False) -> dict[str, Path]:
    """Ensure remote sources exist in cache_dir and match lock checksums.

    Returns a map of source key -> local path for remote (url-backed) sources.
    """
    cache_dir.mkdir(parents=True, exist_ok=True)
    paths: dict[str, Path] = {}
    for key, meta in lock["sources"].items():
        url = meta.get("url")
        if not url:
            continue
        filename = url.rsplit("/", 1)[-1]
        # Disambiguate OpenCC files which share similar names already unique.
        dest = cache_dir / filename
        # OpenCC ST/TS share different names; Unihan zip and dictionary too.
        if key == "opencc_st":
            dest = cache_dir / "STCharacters.txt"
        elif key == "opencc_ts":
            dest = cache_dir / "TSCharacters.txt"
        elif key == "makemeahanzi_dictionary":
            dest = cache_dir / "dictionary.txt"
        elif key == "unihan":
            dest = cache_dir / "Unihan.zip"

        expected = meta["sha256"]
        if dest.exists():
            actual = sha256_file(dest)
            if actual != expected:
                raise SystemExit(
                    f"Checksum mismatch for {key}: expected {expected}, got {actual}"
                )
            paths[key] = dest
            continue

        if offline:
            raise SystemExit(f"Missing cached source {key} at {dest} (offline mode)")

        print(f"Downloading {key} from {url}")
        urllib.request.urlretrieve(url, dest)  # noqa: S310 — pinned URL from lock file
        actual = sha256_file(dest)
        if actual != expected:
            dest.unlink(missing_ok=True)
            raise SystemExit(
                f"Checksum mismatch after download for {key}: expected {expected}, got {actual}"
            )
        paths[key] = dest
    return paths
