from __future__ import annotations

import hashlib
import json
import os
import random
import time
import urllib.error
import urllib.request
from dataclasses import dataclass
from pathlib import Path
from typing import Any

from .config import TTSConfig


class TTSError(RuntimeError):
    pass


@dataclass(frozen=True)
class SpeechClip:
    filename: str
    voice: str
    data: bytes
    cache_hit: bool


def audio_digest(
    *,
    text: str,
    voice: str,
    model: str,
    instructions: str,
    response_format: str,
) -> str:
    payload = {
        "provider": "proxyapi",
        "language": "ru",
        "text": text,
        "voice": voice,
        "model": model,
        "instructions": instructions,
        "response_format": response_format,
    }
    canonical = json.dumps(
        payload, ensure_ascii=False, sort_keys=True, separators=(",", ":")
    ).encode("utf-8")
    return hashlib.sha256(canonical).hexdigest()


def clip_filename(digest: str) -> str:
    return f"sentence_ru_{digest}.mp3"


class AudioCache:
    def __init__(self, directory: Path) -> None:
        self.directory = directory

    def get(self, filename: str) -> bytes | None:
        path = self.directory / filename
        try:
            data = path.read_bytes()
        except FileNotFoundError:
            return None
        return data or None

    def put(self, filename: str, data: bytes) -> None:
        self.directory.mkdir(parents=True, exist_ok=True)
        path = self.directory / filename
        temporary = path.with_suffix(f".{os.getpid()}.tmp")
        temporary.write_bytes(data)
        temporary.replace(path)


def _looks_like_json(data: bytes) -> bool:
    stripped = data.lstrip()
    return stripped.startswith(b"{") or stripped.startswith(b"[")


class ProxyAPITTS:
    def __init__(
        self,
        config: TTSConfig,
        api_key: str,
        cache: AudioCache,
        sleep: Any = time.sleep,
    ) -> None:
        self.config = config
        self.api_key = api_key
        self.cache = cache
        self.sleep = sleep

    def synthesize(self, text: str, voice: str) -> SpeechClip:
        if voice not in self.config.voices:
            raise TTSError(f"Voice {voice!r} is not in the configured voice list")
        digest = audio_digest(
            text=text,
            voice=voice,
            model=self.config.model,
            instructions=self.config.instructions,
            response_format=self.config.response_format,
        )
        filename = clip_filename(digest)
        cached = self.cache.get(filename)
        if cached is not None:
            return SpeechClip(filename, voice, cached, True)
        data = self._post_with_retry(text, voice)
        self.cache.put(filename, data)
        return SpeechClip(filename, voice, data, False)

    def _post_with_retry(self, text: str, voice: str) -> bytes:
        url = f"{self.config.base_url}/audio/speech"
        body: dict[str, Any] = {
            "model": self.config.model,
            "input": text,
            "voice": voice,
            "response_format": self.config.response_format,
        }
        if self.config.instructions.strip():
            body["instructions"] = self.config.instructions
        request_body = json.dumps(body, ensure_ascii=False).encode("utf-8")
        last_error: BaseException | None = None
        for attempt in range(self.config.max_retries + 1):
            request = urllib.request.Request(
                url,
                data=request_body,
                headers={
                    "Authorization": f"Bearer {self.api_key}",
                    "Content-Type": "application/json",
                },
                method="POST",
            )
            try:
                with urllib.request.urlopen(
                    request, timeout=self.config.timeout_seconds
                ) as response:
                    data = response.read()
                    content_type = response.headers.get("Content-Type", "")
                if "json" in content_type.lower() or _looks_like_json(data):
                    raise TTSError(
                        f"TTS returned JSON instead of audio: {data[:300]!r}"
                    )
                if len(data) < 32:
                    raise TTSError("TTS response is too small to be an audio file")
                return data
            except TTSError:
                raise
            except urllib.error.HTTPError as exc:
                detail = exc.read()[:300]
                last_error = TTSError(f"HTTP {exc.code}: {detail!r}")
                if exc.code not in {408, 409, 429, 500, 502, 503, 504}:
                    break
            except (urllib.error.URLError, TimeoutError) as exc:
                last_error = exc
            if attempt < self.config.max_retries:
                self.sleep((2**attempt) + random.random() * 0.25)
        raise TTSError(
            f"TTS request failed after {self.config.max_retries + 1} attempts: {last_error}"
        )
