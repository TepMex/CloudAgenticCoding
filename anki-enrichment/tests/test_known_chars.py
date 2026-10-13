from __future__ import annotations

import re
import unittest
from copy import deepcopy
from typing import Any

from anki_enricher.known_chars import (
    learned_characters,
    run_known_char_report,
    sentence_is_known,
)

HISTORY_QUERY = (
    '(deck:"Refold Mandarin 1k Simplified" OR deck:"Mandarin HSK 1000-5000")'
)
TARGET_QUERY = 'deck:"Mandarin HSK 1000-5000"'


def make_note(
    note_id: int,
    simplified: str,
    sentence: str | None = "",
    *,
    include_sentence_field: bool = True,
) -> dict[str, Any]:
    values: dict[str, str] = {"Simplified": simplified}
    if include_sentence_field:
        values["SentenceSimplified"] = "" if sentence is None else sentence
    return {
        "noteId": note_id,
        "modelName": "HSK",
        "fields": {
            name: {"value": value, "order": index}
            for index, (name, value) in enumerate(values.items())
        },
    }


def make_card(card_id: int, note_id: int, interval: int) -> dict[str, Any]:
    return {"cardId": card_id, "note": note_id, "interval": interval}


class FakeKnownAnki:
    def __init__(
        self,
        notes: list[dict[str, Any]],
        cards: list[dict[str, Any]],
        target_ids: list[int],
    ) -> None:
        self.notes = {int(note["noteId"]): note for note in notes}
        self.cards = cards
        self.target_ids = target_ids

    def find_cards(self, query: str) -> list[int]:
        match = re.search(r"prop:ivl>(\d+)", query)
        threshold = int(match.group(1)) if match else -1
        return [
            int(card["cardId"])
            for card in self.cards
            if int(card["interval"]) > threshold
        ]

    def cards_info(self, card_ids: list[int]) -> list[dict[str, Any]]:
        by_id = {int(card["cardId"]): card for card in self.cards}
        return [deepcopy(by_id[card_id]) for card_id in card_ids if card_id in by_id]

    def find_notes(self, query: str) -> list[int]:
        return list(self.target_ids) if query == TARGET_QUERY else []

    def notes_info(self, note_ids: list[int]) -> list[dict[str, Any]]:
        return [deepcopy(self.notes[note_id]) for note_id in note_ids]


class LearnedCharactersTests(unittest.TestCase):
    def test_interval_equal_to_threshold_is_not_learned(self) -> None:
        notes = [make_note(1, "学校"), make_note(2, "学生")]
        learned = learned_characters(
            notes,
            {1: 360, 2: 361},
            "Simplified",
            360,
        )
        self.assertEqual(frozenset("学生"), learned)

    def test_one_mature_card_on_a_note_learns_all_simplified_characters(self) -> None:
        notes = [make_note(1, "学校")]
        learned = learned_characters(notes, {1: 400}, "Simplified", 360)
        self.assertEqual(frozenset("学校"), learned)


class SentenceIsKnownTests(unittest.TestCase):
    def test_punctuation_is_ignored_when_all_hanzi_are_learned(self) -> None:
        learned = frozenset("我去学校")
        self.assertTrue(sentence_is_known("我去学校。", learned))
        self.assertTrue(sentence_is_known("我去学校，学。", learned))

    def test_unknown_hanzi_is_not_a_match(self) -> None:
        self.assertFalse(sentence_is_known("我去学校吧。", frozenset("我去学校")))

    def test_empty_or_punctuation_only_is_not_a_match(self) -> None:
        self.assertFalse(sentence_is_known("", frozenset("学")))
        self.assertFalse(sentence_is_known("。，", frozenset("学")))


class KnownCharReportTests(unittest.TestCase):
    def report(self, anki: FakeKnownAnki, limit: int | None = None) -> dict[str, Any]:
        return run_known_char_report(
            anki,
            query=TARGET_QUERY,
            history_query=HISTORY_QUERY,
            min_interval=360,
            limit=limit,
        )

    def test_sentence_of_mature_hanzi_with_punctuation_matches(self) -> None:
        anki = FakeKnownAnki(
            notes=[
                make_note(1, "我"),
                make_note(2, "去"),
                make_note(3, "学校"),
                make_note(10, "学生", "我去学校。"),
            ],
            cards=[
                make_card(101, 1, 400),
                make_card(102, 2, 400),
                make_card(103, 3, 400),
                make_card(110, 10, 10),
            ],
            target_ids=[10],
        )
        result = self.report(anki)
        self.assertEqual(HISTORY_QUERY + " prop:ivl>360", result["lexicon_query"])
        self.assertEqual(1, result["match_count"])
        self.assertEqual(
            [
                {
                    "noteId": 10,
                    "Simplified": "学生",
                    "SentenceSimplified": "我去学校。",
                }
            ],
            result["matches"],
        )

    def test_unknown_character_excludes_the_note(self) -> None:
        anki = FakeKnownAnki(
            notes=[
                make_note(1, "我"),
                make_note(2, "去"),
                make_note(3, "学校"),
                make_note(10, "学生", "我去学校吧。"),
            ],
            cards=[
                make_card(101, 1, 400),
                make_card(102, 2, 400),
                make_card(103, 3, 400),
            ],
            target_ids=[10],
        )
        result = self.report(anki)
        self.assertEqual(0, result["match_count"])
        self.assertEqual([], result["matches"])
        self.assertEqual(1, result["notes_scanned"])

    def test_interval_360_does_not_contribute_hanzi_but_361_does(self) -> None:
        anki = FakeKnownAnki(
            notes=[
                make_note(1, "我"),
                make_note(2, "去"),
                make_note(3, "学校"),
                make_note(10, "学生", "我去学校。"),
            ],
            cards=[
                make_card(101, 1, 400),
                make_card(102, 2, 400),
                make_card(103, 3, 360),
            ],
            target_ids=[10],
        )
        self.assertEqual(0, self.report(anki)["match_count"])
        anki.cards[2]["interval"] = 361
        self.assertEqual(1, self.report(anki)["match_count"])

    def test_note_is_learned_if_any_card_exceeds_the_interval(self) -> None:
        anki = FakeKnownAnki(
            notes=[
                make_note(1, "学校"),
                make_note(10, "学生", "学校。"),
            ],
            cards=[
                make_card(101, 1, 10),
                make_card(102, 1, 400),
            ],
            target_ids=[10],
        )
        result = self.report(anki)
        self.assertEqual(1, result["match_count"])
        self.assertEqual(2, result["learned_character_count"])

    def test_missing_or_empty_sentence_is_skipped(self) -> None:
        anki = FakeKnownAnki(
            notes=[
                make_note(1, "学校"),
                make_note(10, "学生", ""),
                make_note(11, "生活", include_sentence_field=False),
                make_note(12, "学生", "学校。"),
            ],
            cards=[make_card(101, 1, 400)],
            target_ids=[10, 11, 12],
        )
        result = self.report(anki)
        self.assertEqual(3, result["notes_scanned"])
        self.assertEqual(2, result["skipped_missing_sentence"])
        self.assertEqual(1, result["match_count"])
        self.assertEqual([12], [row["noteId"] for row in result["matches"]])

    def test_hanzi_learned_in_refold_match_hsk_sentences(self) -> None:
        anki = FakeKnownAnki(
            notes=[
                make_note(1, "我去学校"),
                make_note(10, "学生", "我去学校。"),
            ],
            cards=[make_card(101, 1, 500)],
            target_ids=[10],
        )
        result = self.report(anki)
        self.assertEqual(1, result["match_count"])
        self.assertEqual(10, result["matches"][0]["noteId"])

    def test_limit_truncates_matches_but_reports_full_count(self) -> None:
        anki = FakeKnownAnki(
            notes=[
                make_note(1, "学校"),
                make_note(10, "学生", "学校。"),
                make_note(11, "生活", "学校。"),
            ],
            cards=[make_card(101, 1, 400)],
            target_ids=[10, 11],
        )
        result = self.report(anki, limit=1)
        self.assertEqual(2, result["match_count"])
        self.assertEqual([10], [row["noteId"] for row in result["matches"]])


if __name__ == "__main__":
    unittest.main()
