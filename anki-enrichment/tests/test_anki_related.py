from __future__ import annotations

import tempfile
import unittest
from copy import deepcopy
from pathlib import Path
from typing import Any

from anki_enricher.anki_related import CollectionIntegrityError, run_anki_related_pipeline
from anki_enricher.config import RelatedConfig
from anki_enricher.related import RelatedEnrichmentError


def make_note(
    note_id: int, key: int, hanzi: str, pinyin: str, model: str
) -> dict[str, Any]:
    values = {"Key": str(key), "Simplified": hanzi, "Pinyin": pinyin}
    return {
        "noteId": note_id,
        "modelName": model,
        "cards": [note_id + 1000],
        "fields": {
            name: {"value": value, "order": index}
            for index, (name, value) in enumerate(values.items())
        },
    }


class FakeRelatedAnki:
    def __init__(self) -> None:
        notes = [
            make_note(101, 1, "学校", "xuéxiào", "Refold"),
            make_note(102, 1001, "学生", "xuéshēng", "HSK"),
            make_note(103, 1002, "生活", "shēnghuó", "HSK"),
        ]
        self.notes = {int(note["noteId"]): note for note in notes}
        self.model_fields = {
            "Refold": ["Key", "Simplified", "Pinyin"],
            "HSK": ["Key", "Simplified", "Pinyin"],
        }
        self.added: list[tuple[str, str]] = []
        self.updates: list[tuple[int, dict[str, str]]] = []
        self.schedules = {
            note_id: (note_id, tuple(note["cards"]), ((note["cards"][0], 2, 42),))
            for note_id, note in self.notes.items()
        }
        self.mutate_schedule = False

    def find_notes(self, query: str) -> list[int]:
        if query == "target":
            return [102, 103]
        if query == "history":
            return [101, 102, 103]
        return []

    def notes_info(self, note_ids: list[int]) -> list[dict[str, Any]]:
        return [deepcopy(self.notes[note_id]) for note_id in note_ids]

    def model_field_names(self, model_name: str) -> list[str]:
        return list(self.model_fields[model_name])

    def add_model_field(self, model_name: str, field_name: str) -> None:
        self.added.append((model_name, field_name))
        self.model_fields[model_name].append(field_name)
        for note in self.notes.values():
            if note["modelName"] == model_name:
                note["fields"][field_name] = {
                    "value": "",
                    "order": len(note["fields"]),
                }

    def update_note_fields(self, note_id: int, fields: dict[str, str]) -> None:
        self.updates.append((note_id, deepcopy(fields)))
        for name, value in fields.items():
            self.notes[note_id]["fields"][name]["value"] = value
        if self.mutate_schedule:
            self.schedules[note_id] = (note_id, tuple(), tuple())

    def snapshot(self, note: dict[str, Any]) -> Any:
        return self.schedules[int(note["noteId"])]


class AnkiRelatedPipelineTests(unittest.TestCase):
    def execute(self, anki: FakeRelatedAnki, related: RelatedConfig, dry_run: bool):
        with tempfile.TemporaryDirectory() as directory:
            return run_anki_related_pipeline(
                anki=anki,  # type: ignore[arg-type]
                related=related,
                target_query="target",
                history_query="history",
                limit=None,
                dry_run=dry_run,
                verify_scheduling=True,
                log_dir=Path(directory) if not dry_run else None,
            )

    def test_dry_run_reads_global_history_without_mutating_anki(self) -> None:
        anki = FakeRelatedAnki()
        result = self.execute(anki, RelatedConfig(), dry_run=True)
        self.assertEqual([], anki.added)
        self.assertEqual([], anki.updates)
        self.assertEqual(3, result.counters.history_scanned)
        self.assertEqual(2, result.counters.target_scanned)
        self.assertEqual(
            ["SameHanzi", "SamePinyinMatrix"], result.missing_fields["HSK"]
        )
        first_target = result.preview[0]
        self.assertEqual(1001, first_target["key"])
        self.assertEqual("学校", first_target["same_hanzi"])
        self.assertIn("学校", first_target["matrix_html"])

    def test_write_adds_fields_only_to_target_model_and_updates_same_notes(self) -> None:
        anki = FakeRelatedAnki()
        result = self.execute(anki, RelatedConfig(), dry_run=False)
        self.assertEqual(
            [("HSK", "SameHanzi"), ("HSK", "SamePinyinMatrix")], anki.added
        )
        self.assertEqual([102, 103], [note_id for note_id, _ in anki.updates])
        self.assertEqual(2, result.counters.updated)
        self.assertEqual(0, result.counters.errors)
        self.assertNotIn("SameHanzi", anki.notes[101]["fields"])
        self.assertEqual("学校", anki.notes[102]["fields"]["SameHanzi"]["value"])
        self.assertTrue(
            anki.notes[102]["fields"]["SamePinyinMatrix"]["value"].startswith(
                '<div class="pm">'
            )
        )

    def test_model_specific_pinyin_field_mapping(self) -> None:
        anki = FakeRelatedAnki()
        for note_id in (102, 103):
            note = anki.notes[note_id]
            note["fields"]["Pinyin.1"] = note["fields"].pop("Pinyin")
        result = self.execute(
            anki,
            RelatedConfig(pinyin_fields={"HSK": "Pinyin.1"}),
            dry_run=True,
        )
        self.assertEqual(2, result.counters.eligible)
        self.assertTrue(result.preview[0]["matrix_html"])

    def test_overwrite_false_preserves_existing_field(self) -> None:
        anki = FakeRelatedAnki()
        anki.add_model_field("HSK", "SameHanzi")
        anki.add_model_field("HSK", "SamePinyinMatrix")
        anki.notes[102]["fields"]["SameHanzi"]["value"] = "ручное значение"
        anki.added.clear()
        self.execute(anki, RelatedConfig(overwrite_existing=False), dry_run=False)
        first_update = dict(anki.updates)[102]
        self.assertNotIn("SameHanzi", first_update)
        self.assertIn("SamePinyinMatrix", first_update)
        self.assertEqual(
            "ручное значение", anki.notes[102]["fields"]["SameHanzi"]["value"]
        )

    def test_scheduling_change_is_reported_as_error(self) -> None:
        anki = FakeRelatedAnki()
        anki.mutate_schedule = True
        with self.assertLogs("anki_enricher.anki_related", level="ERROR"):
            with self.assertRaisesRegex(CollectionIntegrityError, "scheduling changed"):
                self.execute(anki, RelatedConfig(), dry_run=False)
        self.assertEqual([102], [note_id for note_id, _ in anki.updates])

    def test_duplicate_global_key_is_rejected(self) -> None:
        anki = FakeRelatedAnki()
        anki.notes[103]["fields"]["Key"]["value"] = "1001"
        with self.assertRaisesRegex(RelatedEnrichmentError, "duplicate Key=1001"):
            self.execute(anki, RelatedConfig(), dry_run=True)


if __name__ == "__main__":
    unittest.main()
