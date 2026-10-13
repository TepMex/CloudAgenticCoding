from __future__ import annotations

from typing import Any, Iterable, Protocol

from .enrichment import field_value
from .related import hanzi_characters


class AnkiReader(Protocol):
    def find_cards(self, query: str) -> list[int]: ...

    def cards_info(self, card_ids: Iterable[int]) -> list[dict[str, Any]]: ...

    def find_notes(self, query: str) -> list[int]: ...

    def notes_info(self, note_ids: Iterable[int]) -> list[dict[str, Any]]: ...


def lexicon_query(history_query: str, min_interval: int) -> str:
    return f"{history_query} prop:ivl>{min_interval}"


def interval_by_note(cards: list[dict[str, Any]]) -> dict[int, int]:
    result: dict[int, int] = {}
    for card in cards:
        note_id = int(card["note"])
        interval = int(card.get("interval", 0))
        previous = result.get(note_id)
        if previous is None or interval > previous:
            result[note_id] = interval
    return result


def learned_characters(
    notes: list[dict[str, Any]],
    intervals: dict[int, int],
    hanzi_field: str,
    min_interval: int,
) -> frozenset[str]:
    result: set[str] = set()
    for note in notes:
        note_id = int(note["noteId"])
        if intervals.get(note_id, 0) <= min_interval:
            continue
        result.update(hanzi_characters(field_value(note, hanzi_field)))
    return frozenset(result)


def sentence_is_known(sentence: str, learned: frozenset[str]) -> bool:
    characters = hanzi_characters(sentence)
    return bool(characters) and characters <= learned


def run_known_char_report(
    anki: AnkiReader,
    query: str,
    history_query: str,
    hanzi_field: str = "Simplified",
    sentence_field: str = "SentenceSimplified",
    min_interval: int = 360,
    limit: int | None = None,
) -> dict[str, Any]:
    search = lexicon_query(history_query, min_interval)
    cards = anki.cards_info(anki.find_cards(search))
    intervals = interval_by_note(cards)
    lexicon_notes = anki.notes_info(list(intervals))
    learned = learned_characters(lexicon_notes, intervals, hanzi_field, min_interval)

    target_notes = anki.notes_info(anki.find_notes(query))
    matches: list[dict[str, Any]] = []
    skipped_missing_sentence = 0
    for note in target_notes:
        fields = note.get("fields", {})
        sentence = field_value(note, sentence_field).strip()
        if sentence_field not in fields or not sentence:
            skipped_missing_sentence += 1
            continue
        if not sentence_is_known(sentence, learned):
            continue
        matches.append(
            {
                "noteId": int(note["noteId"]),
                "Simplified": field_value(note, hanzi_field),
                "SentenceSimplified": field_value(note, sentence_field),
            }
        )
    return {
        "query": query,
        "lexicon_query": search,
        "min_interval": min_interval,
        "learned_character_count": len(learned),
        "notes_scanned": len(target_notes),
        "skipped_missing_sentence": skipped_missing_sentence,
        "match_count": len(matches),
        "matches": matches[:limit] if limit is not None else matches,
    }
