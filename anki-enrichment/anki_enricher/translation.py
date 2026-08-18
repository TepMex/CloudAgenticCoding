from __future__ import annotations

import json
import random
import time
import urllib.error
import urllib.request
from dataclasses import dataclass
from typing import Any

from .cache import JsonCache
from .config import LLMConfig


class LLMError(RuntimeError):
    pass


@dataclass(frozen=True)
class Translation:
    meaning_ru: str | None
    sentence_meaning_ru: str | None
    part_of_speech_ru: str | None

    def as_dict(self) -> dict[str, str | None]:
        return {
            "meaning_ru": self.meaning_ru,
            "sentence_meaning_ru": self.sentence_meaning_ru,
            "part_of_speech_ru": self.part_of_speech_ru,
        }


SYSTEM_PROMPT = """You enrich language-learning Anki cards for a Russian speaker.
Return only a JSON object with exactly these keys:
meaning_ru, sentence_meaning_ru, part_of_speech_ru.
The vocabulary item itself may be non-English. Translate the supplied English meaning
and English sentence only, using the vocabulary item as context. Translate only the
supplied sense, naturally and concisely. Preserve the sentence's meaning, style, and
tense. Translate the part of speech into conventional Russian grammar terminology.
For an input value that is null, return null. Never add Markdown."""


def validate_translation(value: Any, requested: set[str]) -> Translation:
    keys = {"meaning_ru", "sentence_meaning_ru", "part_of_speech_ru"}
    if not isinstance(value, dict) or set(value) != keys:
        raise LLMError(f"LLM JSON must contain exactly: {', '.join(sorted(keys))}")
    normalized: dict[str, str | None] = {}
    for key in keys:
        item = value[key]
        if item is None:
            normalized[key] = None
        elif isinstance(item, str) and item.strip():
            normalized[key] = item.strip()
        else:
            raise LLMError(f"LLM field {key!r} must be a non-empty string or null")
        if key in requested and normalized[key] is None:
            raise LLMError(f"LLM did not return requested field {key!r}")
    return Translation(**normalized)


class OpenAICompatibleTranslator:
    def __init__(
        self,
        config: LLMConfig,
        api_key: str,
        cache: JsonCache,
        enrichment_version: int = 1,
        sleep: Any = time.sleep,
    ) -> None:
        self.config = config
        self.api_key = api_key
        self.cache = cache
        self.enrichment_version = enrichment_version
        self.sleep = sleep

    def translate(
        self, source: dict[str, str | None], requested: set[str]
    ) -> tuple[Translation, bool]:
        cache_payload = {
            "version": self.enrichment_version,
            "base_url": self.config.base_url,
            "model": self.config.model,
            "prompt": SYSTEM_PROMPT,
            "source": source,
            "requested": sorted(requested),
        }
        key = self.cache.key(cache_payload)
        cached = self.cache.get(key)
        if cached is not None:
            return validate_translation(cached, requested), True

        body: dict[str, Any] = {
            "model": self.config.model,
            "temperature": 0,
            "messages": [
                {"role": "system", "content": SYSTEM_PROMPT},
                {
                    "role": "user",
                    "content": json.dumps(source, ensure_ascii=False, sort_keys=True),
                },
            ],
        }
        if self.config.json_response_format:
            body["response_format"] = {"type": "json_object"}
        response = self._post_with_retry(body)
        try:
            content = response["choices"][0]["message"]["content"]
            parsed = json.loads(content)
        except (KeyError, IndexError, TypeError, json.JSONDecodeError) as exc:
            raise LLMError(f"Invalid OpenAI-compatible response: {exc}") from exc
        translation = validate_translation(parsed, requested)
        self.cache.put(key, translation.as_dict())
        return translation, False

    def _post_with_retry(self, body: dict[str, Any]) -> dict[str, Any]:
        url = f"{self.config.base_url}/chat/completions"
        request_body = json.dumps(body).encode("utf-8")
        last_error: BaseException | None = None
        for attempt in range(self.config.max_retries + 1):
            headers = {"Content-Type": "application/json"}
            if self.api_key:
                headers["Authorization"] = f"Bearer {self.api_key}"
            request = urllib.request.Request(
                url,
                data=request_body,
                headers=headers,
                method="POST",
            )
            try:
                with urllib.request.urlopen(
                    request, timeout=self.config.timeout_seconds
                ) as response:
                    return json.loads(response.read().decode("utf-8"))
            except urllib.error.HTTPError as exc:
                last_error = exc
                if exc.code not in {408, 409, 429, 500, 502, 503, 504}:
                    break
            except (urllib.error.URLError, TimeoutError, json.JSONDecodeError) as exc:
                last_error = exc
            if attempt < self.config.max_retries:
                self.sleep((2**attempt) + random.random() * 0.25)
        raise LLMError(
            f"LLM request failed after {self.config.max_retries + 1} attempts: {last_error}"
        )
