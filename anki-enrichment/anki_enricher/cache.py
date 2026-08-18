from __future__ import annotations

import hashlib
import json
import os
from pathlib import Path
from typing import Any


class JsonCache:
    def __init__(self, directory: Path) -> None:
        self.directory = directory

    @staticmethod
    def key(payload: dict[str, Any]) -> str:
        canonical = json.dumps(
            payload, ensure_ascii=False, sort_keys=True, separators=(",", ":")
        ).encode("utf-8")
        return hashlib.sha256(canonical).hexdigest()

    def get(self, key: str) -> dict[str, Any] | None:
        path = self.directory / f"{key}.json"
        try:
            value = json.loads(path.read_text(encoding="utf-8"))
        except (FileNotFoundError, json.JSONDecodeError):
            return None
        return value if isinstance(value, dict) else None

    def put(self, key: str, value: dict[str, Any]) -> None:
        self.directory.mkdir(parents=True, exist_ok=True)
        path = self.directory / f"{key}.json"
        temporary = path.with_suffix(f".{os.getpid()}.tmp")
        temporary.write_text(
            json.dumps(value, ensure_ascii=False, sort_keys=True, indent=2),
            encoding="utf-8",
        )
        temporary.replace(path)

