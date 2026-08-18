from __future__ import annotations

import json
import os
from dataclasses import dataclass
from pathlib import Path
from typing import Any


class ConfigError(ValueError):
    pass


@dataclass(frozen=True)
class AnkiConfig:
    url: str
    query: str
    sample_size: int


@dataclass(frozen=True)
class FieldMapping:
    word: str
    meaning_en: str
    sentence_en: str
    part_of_speech_en: str
    meaning_ru: str
    sentence_meaning_ru: str
    part_of_speech_ru: str

    @property
    def target_fields(self) -> tuple[str, str, str]:
        return (self.meaning_ru, self.sentence_meaning_ru, self.part_of_speech_ru)


@dataclass(frozen=True)
class LLMConfig:
    base_url: str
    model: str
    api_key_env: str
    timeout_seconds: float
    max_retries: int
    json_response_format: bool = True


@dataclass(frozen=True)
class ProcessingConfig:
    enrichment_version: int
    overwrite_existing: bool
    verify_scheduling: bool
    cache_dir: Path
    log_dir: Path


@dataclass(frozen=True)
class Config:
    anki: AnkiConfig
    fields: FieldMapping
    llm: LLMConfig
    processing: ProcessingConfig


def load_dotenv(path: Path) -> None:
    """Load a small, deliberately conservative subset of dotenv syntax."""
    if not path.exists():
        return
    for raw_line in path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        key = key.strip()
        value = value.strip().strip("\"'")
        if key and key not in os.environ:
            os.environ[key] = value


def _section(data: dict[str, Any], name: str) -> dict[str, Any]:
    value = data.get(name)
    if not isinstance(value, dict):
        raise ConfigError(f"Missing object '{name}' in config")
    return value


def load_config(path: Path) -> Config:
    """Load JSON-compatible YAML without a third-party runtime dependency."""
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except FileNotFoundError as exc:
        raise ConfigError(f"Config not found: {path}") from exc
    except json.JSONDecodeError as exc:
        raise ConfigError(
            f"{path} must use JSON-compatible YAML syntax: {exc}"
        ) from exc

    anki = _section(data, "anki")
    fields = _section(data, "fields")
    llm = _section(data, "llm")
    processing = _section(data, "processing")
    root = path.resolve().parent

    try:
        base_url = os.environ.get("OPENAI_BASE_URL", str(llm["base_url"]))
        model = os.environ.get("OPENAI_MODEL", str(llm["model"]))
        return Config(
            anki=AnkiConfig(
                url=str(anki["url"]).rstrip("/"),
                query=str(anki["query"]),
                sample_size=int(anki.get("sample_size", 3)),
            ),
            fields=FieldMapping(**{key: str(value) for key, value in fields.items()}),
            llm=LLMConfig(
                base_url=base_url.rstrip("/"),
                model=model,
                api_key_env=str(llm.get("api_key_env", "OPENAI_API_KEY")),
                timeout_seconds=float(llm.get("timeout_seconds", 60)),
                max_retries=int(llm.get("max_retries", 3)),
                json_response_format=bool(llm.get("json_response_format", True)),
            ),
            processing=ProcessingConfig(
                enrichment_version=int(processing.get("enrichment_version", 1)),
                overwrite_existing=bool(processing.get("overwrite_existing", False)),
                verify_scheduling=bool(processing.get("verify_scheduling", True)),
                cache_dir=root / str(processing.get("cache_dir", "cache")),
                log_dir=root / str(processing.get("log_dir", "logs")),
            ),
        )
    except (KeyError, TypeError, ValueError) as exc:
        raise ConfigError(f"Invalid config: {exc}") from exc
