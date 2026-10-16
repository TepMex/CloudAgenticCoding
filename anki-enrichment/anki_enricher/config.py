from __future__ import annotations

import json
import os
from dataclasses import dataclass, field
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
class RelatedConfig:
    history_query: str | None = None
    key_field: str = "Key"
    hanzi_field: str = "Simplified"
    pinyin_field: str = "Pinyin"
    pinyin_fields: dict[str, str] = field(default_factory=dict)
    same_hanzi_field: str = "SameHanzi"
    matrix_field: str = "SamePinyinMatrix"
    overwrite_existing: bool = True

    @property
    def target_fields(self) -> tuple[str, str]:
        return self.same_hanzi_field, self.matrix_field


DEFAULT_TTS_VOICES = (
    "alloy",
    "ash",
    "ballad",
    "coral",
    "echo",
    "fable",
    "nova",
    "onyx",
    "sage",
    "shimmer",
)

TTS_MODEL_ALIASES = {
    "gpt-4o-tts-mini": "openai/gpt-4o-mini-tts",
    "gpt-4o-mini-tts": "openai/gpt-4o-mini-tts",
}


@dataclass(frozen=True)
class TTSConfig:
    base_url: str = "https://api.proxyapi.ru/v1"
    model: str = "openai/gpt-4o-mini-tts"
    api_key_env: str = "PROXYAPI_API_KEY"
    query: str = 'deck:"Refold Mandarin 1k Simplified"'
    source_field: str = "SentenceMeaningRu"
    target_field: str = "SentenceMeaningRuAudio"
    voices: tuple[str, ...] = DEFAULT_TTS_VOICES
    instructions: str = "Читай по-русски, спокойно и разборчиво."
    response_format: str = "mp3"
    timeout_seconds: float = 60
    max_retries: int = 3
    overwrite_existing: bool = False


@dataclass(frozen=True)
class Config:
    anki: AnkiConfig
    fields: FieldMapping
    llm: LLMConfig
    processing: ProcessingConfig
    related: RelatedConfig = RelatedConfig()
    tts: TTSConfig = TTSConfig()


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


def canonical_tts_model(name: str) -> str:
    cleaned = name.strip()
    return TTS_MODEL_ALIASES.get(cleaned, cleaned)


def _tts_config(value: Any) -> TTSConfig:
    if value is None:
        return TTSConfig()
    if not isinstance(value, dict):
        raise ConfigError("Config object 'tts' must be an object")
    voices_value = value.get("voices", DEFAULT_TTS_VOICES)
    if not isinstance(voices_value, (list, tuple)) or not voices_value:
        raise ConfigError("Config value 'tts.voices' must be a non-empty list")
    voices = tuple(str(voice).strip() for voice in voices_value)
    if any(not voice for voice in voices):
        raise ConfigError("Config value 'tts.voices' contains an empty voice")
    return TTSConfig(
        base_url=str(value.get("base_url", TTSConfig.base_url)).rstrip("/"),
        model=canonical_tts_model(str(value.get("model", TTSConfig.model))),
        api_key_env=str(value.get("api_key_env", TTSConfig.api_key_env)),
        query=str(value.get("query", TTSConfig.query)),
        source_field=str(value.get("source_field", TTSConfig.source_field)),
        target_field=str(value.get("target_field", TTSConfig.target_field)),
        voices=voices,
        instructions=str(value.get("instructions", TTSConfig.instructions)),
        response_format=str(value.get("response_format", TTSConfig.response_format)),
        timeout_seconds=float(value.get("timeout_seconds", TTSConfig.timeout_seconds)),
        max_retries=int(value.get("max_retries", TTSConfig.max_retries)),
        overwrite_existing=bool(
            value.get("overwrite_existing", TTSConfig.overwrite_existing)
        ),
    )


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
    related_value = data.get("related", {})
    if not isinstance(related_value, dict):
        raise ConfigError("Config object 'related' must be an object")
    pinyin_fields_value = related_value.get("pinyin_fields", {})
    if not isinstance(pinyin_fields_value, dict):
        raise ConfigError("Config value 'related.pinyin_fields' must be an object")
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
            tts=_tts_config(data.get("tts")),
            related=RelatedConfig(
                history_query=(
                    str(related_value["history_query"]).strip() or None
                    if "history_query" in related_value
                    else None
                ),
                key_field=str(related_value.get("key_field", "Key")),
                hanzi_field=str(
                    related_value.get("hanzi_field", fields.get("word", "Simplified"))
                ),
                pinyin_field=str(related_value.get("pinyin_field", "Pinyin")),
                pinyin_fields={
                    str(model): str(field_name)
                    for model, field_name in pinyin_fields_value.items()
                },
                same_hanzi_field=str(
                    related_value.get("same_hanzi_field", "SameHanzi")
                ),
                matrix_field=str(
                    related_value.get("matrix_field", "SamePinyinMatrix")
                ),
                overwrite_existing=bool(
                    related_value.get("overwrite_existing", True)
                ),
            ),
        )
    except (KeyError, TypeError, ValueError) as exc:
        raise ConfigError(f"Invalid config: {exc}") from exc
