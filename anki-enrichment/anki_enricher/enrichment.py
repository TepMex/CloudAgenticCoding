from __future__ import annotations

import html
import json
import logging
import re
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Protocol

from .anki import AnkiConnect, AnkiConnectError
from .config import Config, FieldMapping
from .translation import Translation


LOGGER = logging.getLogger(__name__)
TAG_RE = re.compile(r"<[^>]*>")
SOUND_RE = re.compile(r"\[sound:[^]]+]")


class Translator(Protocol):
    def translate(
        self, source: dict[str, str | None], requested: set[str]
    ) -> tuple[Translation, bool]: ...


@dataclass
class Counters:
    scanned: int = 0
    eligible: int = 0
    updated: int = 0
    skipped: int = 0
    errors: int = 0
    llm_calls: int = 0
    cache_hits: int = 0


def plain_text(value: str) -> str:
    return " ".join(
        html.unescape(TAG_RE.sub(" ", SOUND_RE.sub(" ", value))).split()
    ).strip()


def field_value(note: dict[str, Any], name: str) -> str:
    field = note.get("fields", {}).get(name, {})
    return str(field.get("value", "")) if isinstance(field, dict) else ""


def required_outputs(
    note: dict[str, Any], fields: FieldMapping, overwrite: bool
) -> tuple[dict[str, str | None], set[str]]:
    source = {
        "word": plain_text(field_value(note, fields.word)) or None,
        "meaning_en": plain_text(field_value(note, fields.meaning_en)) or None,
        "sentence_en": plain_text(field_value(note, fields.sentence_en)) or None,
        "part_of_speech_en": plain_text(
            field_value(note, fields.part_of_speech_en)
        )
        or None,
    }
    pairs = {
        "meaning_ru": (fields.meaning_ru, "meaning_en"),
        "sentence_meaning_ru": (fields.sentence_meaning_ru, "sentence_en"),
        "part_of_speech_ru": (fields.part_of_speech_ru, "part_of_speech_en"),
    }
    requested = {
        output
        for output, (target, source_key) in pairs.items()
        if source[source_key] and (overwrite or not field_value(note, target).strip())
    }
    return source, requested


def target_updates(
    translation: Translation, requested: set[str], fields: FieldMapping
) -> dict[str, str]:
    values = translation.as_dict()
    mapping = {
        "meaning_ru": fields.meaning_ru,
        "sentence_meaning_ru": fields.sentence_meaning_ru,
        "part_of_speech_ru": fields.part_of_speech_ru,
    }
    return {
        mapping[key]: str(values[key])
        for key in requested
        if values[key] is not None
    }


class JsonlLogger:
    def __init__(self, directory: Path) -> None:
        self.path = directory / "enrichment.jsonl"

    def write(self, event: dict[str, Any]) -> None:
        self.path.parent.mkdir(parents=True, exist_ok=True)
        with self.path.open("a", encoding="utf-8") as handle:
            handle.write(json.dumps(event, ensure_ascii=False, sort_keys=True) + "\n")


def ensure_target_fields(
    anki: AnkiConnect,
    notes: list[dict[str, Any]],
    fields: FieldMapping,
    dry_run: bool,
) -> dict[str, list[str]]:
    model_names = sorted({str(note["modelName"]) for note in notes})
    missing: dict[str, list[str]] = {}
    for model_name in model_names:
        existing = set(anki.model_field_names(model_name))
        needed = [name for name in fields.target_fields if name not in existing]
        if needed:
            missing[model_name] = needed
            if not dry_run:
                for field_name in needed:
                    anki.add_model_field(model_name, field_name)
    return missing


def run_pipeline(
    anki: AnkiConnect,
    translator: Translator | None,
    config: Config,
    query: str,
    limit: int | None,
    dry_run: bool,
) -> tuple[Counters, list[dict[str, Any]], dict[str, list[str]]]:
    note_ids = anki.find_notes(query)
    if limit is not None:
        note_ids = note_ids[:limit]
    notes = anki.notes_info(note_ids)
    missing_fields = ensure_target_fields(anki, notes, config.fields, dry_run)
    counters = Counters(scanned=len(notes))
    previews: list[dict[str, Any]] = []
    event_log = JsonlLogger(config.processing.log_dir)

    for note in notes:
        note_id = int(note["noteId"])
        word = plain_text(field_value(note, config.fields.word))
        source, requested = required_outputs(
            note, config.fields, config.processing.overwrite_existing
        )
        if not requested:
            counters.skipped += 1
            previews.append({"note_id": note_id, "word": word, "changes": []})
            continue
        counters.eligible += 1
        target_names = {
            "meaning_ru": config.fields.meaning_ru,
            "sentence_meaning_ru": config.fields.sentence_meaning_ru,
            "part_of_speech_ru": config.fields.part_of_speech_ru,
        }
        if dry_run:
            previews.append(
                {
                    "note_id": note_id,
                    "word": word,
                    "changes": [target_names[key] for key in sorted(requested)],
                }
            )
            continue

        event: dict[str, Any] = {
            "note_id": note_id,
            "word": word,
            "fields_changed": [],
            "llm_called": False,
            "status": "processing",
        }
        try:
            if translator is None:
                raise RuntimeError("Translator is required for a write run")
            before = (
                anki.snapshot(note) if config.processing.verify_scheduling else None
            )
            translation, cache_hit = translator.translate(source, requested)
            counters.cache_hits += int(cache_hit)
            counters.llm_calls += int(not cache_hit)
            event["llm_called"] = not cache_hit
            updates = target_updates(translation, requested, config.fields)
            if set(updates) - set(config.fields.target_fields):
                raise RuntimeError("Attempted update outside configured target fields")
            anki.update_note_fields(note_id, updates)

            after_note = anki.notes_info([note_id])
            if len(after_note) != 1 or int(after_note[0]["noteId"]) != note_id:
                raise AnkiConnectError("Note ID changed or note disappeared after update")
            if before is not None and anki.snapshot(after_note[0]) != before:
                raise AnkiConnectError(
                    "Card IDs or scheduling changed after update; stopping for safety"
                )
            counters.updated += 1
            event.update(fields_changed=sorted(updates), status="done")
            previews.append(
                {"note_id": note_id, "word": word, "changes": sorted(updates)}
            )
        except Exception as exc:  # One bad note must not abort the collection.
            counters.errors += 1
            event.update(status="error", error=str(exc))
            LOGGER.exception("Failed to enrich note %s", note_id)
        finally:
            event_log.write(event)
    return counters, previews, missing_fields

