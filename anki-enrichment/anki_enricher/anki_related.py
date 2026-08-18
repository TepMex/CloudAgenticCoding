from __future__ import annotations

import json
import logging
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

from .anki import AnkiConnect, AnkiConnectError
from .config import RelatedConfig
from .enrichment import field_value
from .pinyin import ParsedPinyin
from .related import EnrichmentResult, RelatedEnrichmentError, enrich_records


LOGGER = logging.getLogger(__name__)


class CollectionIntegrityError(AnkiConnectError):
    pass


@dataclass
class AnkiRelatedRecord:
    note: dict[str, Any]
    note_id: int
    model_name: str
    key: int
    hanzi: str
    pinyin: str
    same_hanzi: str = ""
    matrix: str = ""
    parsed: ParsedPinyin | None = None


@dataclass
class RelatedCounters:
    history_scanned: int = 0
    target_scanned: int = 0
    eligible: int = 0
    updated: int = 0
    skipped: int = 0
    errors: int = 0
    pinyin_skipped: int = 0


@dataclass
class AnkiRelatedResult:
    counters: RelatedCounters
    missing_fields: dict[str, list[str]]
    preview: list[dict[str, Any]] = field(default_factory=list)
    warnings: list[str] = field(default_factory=list)
    debug: dict[str, object] | None = None


def _required_value(note: dict[str, Any], name: str) -> str:
    note_id = int(note["noteId"])
    fields = note.get("fields", {})
    if name not in fields:
        raise RelatedEnrichmentError(
            f"Anki note ID {note_id} ({note.get('modelName')}): missing source field {name!r}"
        )
    value = field_value(note, name).strip()
    if not value:
        raise RelatedEnrichmentError(
            f"Anki note ID {note_id}: empty source field {name!r}"
        )
    return value


def records_from_notes(
    notes: list[dict[str, Any]], related: RelatedConfig
) -> list[AnkiRelatedRecord]:
    records: list[AnkiRelatedRecord] = []
    for note in notes:
        model_name = str(note["modelName"])
        pinyin_field = related.pinyin_fields.get(model_name, related.pinyin_field)
        raw_key = _required_value(note, related.key_field)
        try:
            key = int(raw_key)
        except ValueError as exc:
            raise RelatedEnrichmentError(
                f"Anki note ID {note['noteId']}: non-numeric "
                f"{related.key_field}={raw_key!r}"
            ) from exc
        records.append(
            AnkiRelatedRecord(
                note=note,
                note_id=int(note["noteId"]),
                model_name=model_name,
                key=key,
                hanzi=_required_value(note, related.hanzi_field),
                pinyin=_required_value(note, pinyin_field),
            )
        )
    return records


def ensure_target_fields(
    anki: AnkiConnect,
    records: list[AnkiRelatedRecord],
    related: RelatedConfig,
    dry_run: bool,
) -> dict[str, list[str]]:
    missing: dict[str, list[str]] = {}
    for model_name in sorted({record.model_name for record in records}):
        existing = set(anki.model_field_names(model_name))
        needed = [name for name in related.target_fields if name not in existing]
        if needed:
            missing[model_name] = needed
            if not dry_run:
                for field_name in needed:
                    anki.add_model_field(model_name, field_name)
    return missing


def _updates_for_record(
    record: AnkiRelatedRecord, related: RelatedConfig
) -> dict[str, str]:
    candidates = {
        related.same_hanzi_field: record.same_hanzi,
        related.matrix_field: record.matrix,
    }
    return {
        name: value
        for name, value in candidates.items()
        if related.overwrite_existing or not field_value(record.note, name).strip()
    }


def _write_event(path: Path, event: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("a", encoding="utf-8") as handle:
        handle.write(json.dumps(event, ensure_ascii=False, sort_keys=True) + "\n")


def run_anki_related_pipeline(
    anki: AnkiConnect,
    related: RelatedConfig,
    target_query: str,
    history_query: str,
    limit: int | None,
    dry_run: bool,
    verify_scheduling: bool,
    log_dir: Path | None = None,
    debug_key: int | None = None,
) -> AnkiRelatedResult:
    target_ids = set(anki.find_notes(target_query))
    history_ids = set(anki.find_notes(history_query))
    # A target always participates in its own global sequence even if the two
    # configured searches accidentally do not overlap completely.
    all_ids = sorted(history_ids | target_ids)
    notes = anki.notes_info(all_ids)
    notes_by_id = {int(note["noteId"]): note for note in notes}
    missing_note_ids = set(all_ids) - set(notes_by_id)
    if missing_note_ids:
        raise AnkiConnectError(
            f"notesInfo omitted note IDs: {sorted(missing_note_ids)[:10]}"
        )

    all_records = records_from_notes(notes, related)
    computation: EnrichmentResult = enrich_records(
        all_records,
        debug_key=debug_key,
    )
    records_by_id = {record.note_id: record for record in all_records}
    target_records = sorted(
        (records_by_id[note_id] for note_id in target_ids),
        key=lambda record: record.key,
    )
    if limit is not None:
        target_records = target_records[:limit]

    missing_fields = ensure_target_fields(anki, target_records, related, dry_run)
    counters = RelatedCounters(
        history_scanned=len(all_records),
        target_scanned=len(target_records),
        pinyin_skipped=computation.skipped,
    )
    result = AnkiRelatedResult(
        counters=counters,
        missing_fields=missing_fields,
        warnings=computation.warnings,
        debug=computation.debug,
    )
    event_path = log_dir / "same_hanzi_pinyin.jsonl" if log_dir else None

    for record in target_records:
        updates = _updates_for_record(record, related)
        preview = {
            "note_id": record.note_id,
            "key": record.key,
            "hanzi": record.hanzi,
            "fields": sorted(updates),
            "same_hanzi": record.same_hanzi,
            "matrix_html": record.matrix,
        }
        if not updates:
            counters.skipped += 1
            result.preview.append(preview)
            continue
        counters.eligible += 1
        if dry_run:
            result.preview.append(preview)
            continue

        event: dict[str, Any] = {
            "note_id": record.note_id,
            "key": record.key,
            "hanzi": record.hanzi,
            "fields_changed": [],
            "status": "processing",
        }
        try:
            before = anki.snapshot(record.note) if verify_scheduling else None
            anki.update_note_fields(record.note_id, updates)
            after_notes = anki.notes_info([record.note_id])
            if len(after_notes) != 1 or int(after_notes[0]["noteId"]) != record.note_id:
                raise CollectionIntegrityError(
                    "Note ID changed or note disappeared after update"
                )
            if before is not None and anki.snapshot(after_notes[0]) != before:
                raise CollectionIntegrityError(
                    "Card IDs or scheduling changed after update; stopping for safety"
                )
            counters.updated += 1
            event.update(fields_changed=sorted(updates), status="done")
            result.preview.append(preview)
        except CollectionIntegrityError as exc:
            counters.errors += 1
            event.update(status="error", error=str(exc))
            preview["error"] = str(exc)
            result.preview.append(preview)
            LOGGER.exception("Collection integrity check failed for note %s", record.note_id)
            raise
        except Exception as exc:  # Keep the same per-note isolation as translations.
            counters.errors += 1
            event.update(status="error", error=str(exc))
            preview["error"] = str(exc)
            result.preview.append(preview)
            LOGGER.exception("Failed to enrich Anki note %s", record.note_id)
        finally:
            if event_path is not None:
                _write_event(event_path, event)
    return result
