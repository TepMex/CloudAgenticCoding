from __future__ import annotations

from typing import Any

from .anki import AnkiConnect
from .config import FieldMapping


ALIASES = {
    "word": ("word", "front", "term", "expression", "vocabulary", "simplified"),
    "meaning_en": ("meaning", "definition", "definitionen", "meaning_en"),
    "sentence_en": (
        "sentencemeaning",
        "sentence",
        "example",
        "exampleen",
        "context",
    ),
    "part_of_speech_en": ("partofspeech", "pos", "part_of_speech"),
}


def recommend_mapping(field_names: list[str], configured: FieldMapping) -> dict[str, str | None]:
    lowered = {name.lower().replace(" ", "").replace("-", ""): name for name in field_names}
    result: dict[str, str | None] = {}
    for role, aliases in ALIASES.items():
        configured_name = getattr(configured, role)
        if configured_name in field_names:
            result[role] = configured_name
            continue
        result[role] = next((lowered[a] for a in aliases if a in lowered), None)
    result.update(
        meaning_ru=configured.meaning_ru,
        sentence_meaning_ru=configured.sentence_meaning_ru,
        part_of_speech_ru=configured.part_of_speech_ru,
    )
    return result


def audit_collection(
    anki: AnkiConnect, query: str, sample_size: int, fields: FieldMapping
) -> dict[str, Any]:
    decks = anki.deck_names()
    note_ids = anki.find_notes(query)
    # Inspect all matching notes to avoid hiding a less frequent note type. The
    # output still contains only a small, safe sample of actual field contents.
    all_notes = anki.notes_info(note_ids)
    sample = all_notes[:sample_size]
    model_names = sorted({str(note["modelName"]) for note in all_notes})
    models: dict[str, Any] = {}
    for model_name in model_names:
        names = anki.model_field_names(model_name)
        models[model_name] = {
            "fields": names,
            "templates": anki.model_templates(model_name),
            "recommended_mapping": recommend_mapping(names, fields),
        }
    safe_sample = [
        {
            "noteId": note["noteId"],
            "modelName": note["modelName"],
            "cards": note.get("cards", []),
            "fields": {
                name: value.get("value", "")
                for name, value in note.get("fields", {}).items()
            },
        }
        for note in sample
    ]
    return {
        "decks": decks,
        "query": query,
        "matching_notes": len(note_ids),
        "models": models,
        "sample_notes": safe_sample,
    }
