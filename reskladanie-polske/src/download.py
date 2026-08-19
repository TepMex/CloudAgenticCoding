from __future__ import annotations

import hashlib
import json
import os
import tempfile
import time
import urllib.request
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path


USER_AGENT = "reskladanie-polske/1.0 (reproducible corpus frequency list)"


@dataclass(frozen=True, slots=True)
class SourceSpec:
    key: str
    source_page: str
    download_url: str
    filename: str
    expected_sha256: str
    citation: str


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def _download(url: str, destination: Path, expected_sha256: str) -> str:
    destination.parent.mkdir(parents=True, exist_ok=True)
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    last_error: Exception | None = None
    for attempt in range(4):
        try:
            with (
                urllib.request.urlopen(request, timeout=120) as response,
                tempfile.NamedTemporaryFile(
                    "wb", dir=destination.parent, prefix=f".{destination.name}.", delete=False
                ) as temporary,
            ):
                temporary_path = Path(temporary.name)
                while block := response.read(1024 * 1024):
                    temporary.write(block)
                resolved_url = response.geturl()
            actual_hash = sha256_file(temporary_path)
            if actual_hash != expected_sha256:
                temporary_path.unlink(missing_ok=True)
                raise ValueError(
                    f"downloaded {destination.name} has SHA-256 {actual_hash}, "
                    f"expected pinned hash {expected_sha256}"
                )
            os.replace(temporary_path, destination)
            return resolved_url
        except Exception as exc:  # pragma: no cover - network failures are environment-specific
            last_error = exc
            if "temporary_path" in locals():
                temporary_path.unlink(missing_ok=True)
            if attempt < 3:
                time.sleep(2**attempt)
    assert last_error is not None
    raise last_error


def ensure_source(
    spec: SourceSpec,
    raw_dir: Path,
    *,
    force_download: bool,
    previous_manifest: dict[str, object] | None,
) -> tuple[Path, dict[str, object], bool]:
    path = raw_dir / spec.filename
    cached = False
    resolved_url = spec.download_url
    if path.exists() and not force_download:
        actual_hash = sha256_file(path)
        if actual_hash == spec.expected_sha256:
            cached = True
        else:
            raise ValueError(
                f"cached {path} has SHA-256 {actual_hash}, expected {spec.expected_sha256}; "
                "remove it or use --force-download"
            )
    else:
        resolved_url = _download(spec.download_url, path, spec.expected_sha256)
        actual_hash = sha256_file(path)
        assert actual_hash == spec.expected_sha256

    previous_downloaded_at = None
    if previous_manifest:
        previous_downloaded_at = previous_manifest.get("downloaded_at")
        if cached:
            resolved_url = str(previous_manifest.get("resolved_url") or resolved_url)
    if cached and previous_downloaded_at:
        downloaded_at = previous_downloaded_at
    elif cached:
        downloaded_at = datetime.fromtimestamp(path.stat().st_mtime, timezone.utc).isoformat()
    else:
        downloaded_at = datetime.now(timezone.utc).isoformat()

    entry: dict[str, object] = {
        "source_page": spec.source_page,
        "download_url": spec.download_url,
        "resolved_url": resolved_url,
        "downloaded_at": downloaded_at,
        "filename": spec.filename,
        "size_bytes": path.stat().st_size,
        "sha256": actual_hash,
        "citation": spec.citation,
        "cache_status": "verified" if cached else "downloaded",
    }
    return path, entry, cached


def load_manifest(path: Path) -> dict[str, dict[str, object]]:
    if not path.exists():
        return {}
    with path.open("r", encoding="utf-8") as stream:
        value = json.load(stream)
    return value if isinstance(value, dict) else {}
