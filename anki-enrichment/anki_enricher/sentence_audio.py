from __future__ import annotations

import json
import logging
import random
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Protocol

from .anki import AnkiConnect, AnkiConnectError
from .config import Config
from .enrichment import field_value, plain_text
from .tts import SpeechClip


LOGGER = logging.getLogger(__name__)
MEDIA_PATTERN = "sentence_ru_*.mp3"


class SpeechSynthesizer(Protocol):
    def synthesize(self, text: str, voice: str) -> SpeechClip: ...


class SentenceAudioError(RuntimeError):
    pass


@dataclass
class Counters:
    scanned: int = 0
    eligible: int = 0
    skipped_empty: int = 0
    skipped_existing: int = 0
    updated: int = 0
    errors: int = 0
    tts_calls: int = 0
    cache_hits: int = 0
    characters: int = 0


class JsonlLogger:
    def __init__(self, directory: Path) -> None:
        self.path = directory / "sentence_meaning_ru_audio.jsonl"

    def write(self, event: dict[str, Any]) -> None:
        self.path.parent.mkdir(parents=True, exist_ok=True)
        with self.path.open("a", encoding="utf-8") as handle:
            handle.write(json.dumps(event, ensure_ascii=False, sort_keys=True) + "\n")


def _source_text(note: dict[str, Any], field_name: str) -> str:
    if field_name not in note.get("fields", {}):
        return ""
    return plain_text(field_value(note, field_name))


def run_sentence_audio(
    anki: AnkiConnect,
    synthesizer: SpeechSynthesizer | None,
    config: Config,
    query: str,
    limit: int | None,
    dry_run: bool,
    rng: random.Random,
) -> tuple[Counters, list[dict[str, Any]], dict[str, list[str]]]:
    tts = config.tts
    note_ids = anki.find_notes(query)
    if limit is not None:
        note_ids = note_ids[:limit]
    notes = anki.notes_info(note_ids)
    counters = Counters(scanned=len(notes))
    model_names = sorted({str(note["modelName"]) for note in notes})
    model_fields = {name: set(anki.model_field_names(name)) for name in model_names}
    missing_source = sorted(
        name for name, fields in model_fields.items() if tts.source_field not in fields
    )
    if missing_source:
        raise SentenceAudioError(
            f"{tts.source_field} is missing on note type {', '.join(missing_source)}. "
            "Fill it with ru_translation.py before generating audio."
        )

    eligible = []
    for note in notes:
        source = _source_text(note, tts.source_field)
        target = field_value(note, tts.target_field).strip()
        if not source:
            counters.skipped_empty += 1
            continue
        if target and not tts.overwrite_existing:
            counters.skipped_existing += 1
            continue
        eligible.append((note, source))
        counters.eligible += 1
        counters.characters += len(source)

    missing_fields: dict[str, list[str]] = {}
    for model_name in sorted({str(note["modelName"]) for note, _ in eligible}):
        if tts.target_field not in model_fields[model_name]:
            missing_fields[model_name] = [tts.target_field]
            if not dry_run:
                anki.add_model_field(model_name, tts.target_field)

    previews: list[dict[str, Any]] = []
    preview_limit = 20 if limit is None else limit
    if dry_run:
        for note, _source in eligible[:preview_limit]:
            previews.append(
                {
                    "note_id": int(note["noteId"]),
                    "word": plain_text(field_value(note, config.fields.word)),
                    "changes": [tts.target_field],
                }
            )
        return counters, previews, missing_fields
    if synthesizer is None:
        raise SentenceAudioError("A speech synthesizer is required for a write run")

    known_media = anki.media_file_names(MEDIA_PATTERN)
    event_log = JsonlLogger(config.processing.log_dir)
    for note, source in eligible:
        note_id = int(note["noteId"])
        word = plain_text(field_value(note, config.fields.word))
        voice = rng.choice(tts.voices)
        event: dict[str, Any] = {
            "note_id": note_id,
            "word": word,
            "voice": voice,
            "fields_changed": [],
            "tts_called": False,
            "status": "processing",
        }
        try:
            before = anki.snapshot(note) if config.processing.verify_scheduling else None
            clip = synthesizer.synthesize(source, voice)
            counters.cache_hits += int(clip.cache_hit)
            counters.tts_calls += int(not clip.cache_hit)
            event["tts_called"] = not clip.cache_hit
            event["audio_file"] = clip.filename
            if clip.filename not in known_media:
                stored = anki.store_media_file(clip.filename, clip.data)
                if stored != clip.filename:
                    raise AnkiConnectError(
                        f"Anki stored media as {stored!r}, expected {clip.filename!r}"
                    )
                known_media.add(clip.filename)
            updates = {tts.target_field: f"[sound:{clip.filename}]"}
            anki.update_note_fields(note_id, updates)
            after_note = anki.notes_info([note_id])
            if len(after_note) != 1 or int(after_note[0]["noteId"]) != note_id:
                raise AnkiConnectError("Note ID changed or note disappeared after update")
            if before is not None and anki.snapshot(after_note[0]) != before:
                raise AnkiConnectError(
                    "Card IDs or scheduling changed after update; stopping for safety"
                )
            counters.updated += 1
            event.update(fields_changed=[tts.target_field], status="done")
            if len(previews) < preview_limit:
                previews.append(
                    {
                        "note_id": note_id,
                        "word": word,
                        "voice": voice,
                        "changes": [tts.target_field],
                        "audio_file": clip.filename,
                    }
                )
        except Exception as exc:
            counters.errors += 1
            event.update(status="error", error=str(exc))
            LOGGER.exception("Failed to add sentence audio for note %s", note_id)
        finally:
            event_log.write(event)
    return counters, previews, missing_fields
