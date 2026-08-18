from __future__ import annotations

import contextlib
import io
import json
import tempfile
import threading
import unittest
from copy import deepcopy
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import Any

from anki_enricher.config import (
    AnkiConfig,
    Config,
    FieldMapping,
    LLMConfig,
    ProcessingConfig,
)
from anki_enricher.enrichment import required_outputs, run_pipeline
from anki_enricher.translation import LLMError, Translation, validate_translation
from ru_translation import main


FIELDS = FieldMapping(
    word="Word",
    meaning_en="Meaning",
    sentence_en="Sentence",
    part_of_speech_en="PartOfSpeech",
    meaning_ru="MeaningRu",
    sentence_meaning_ru="SentenceMeaningRu",
    part_of_speech_ru="PartOfSpeechRu",
)


def make_note() -> dict[str, Any]:
    values = {
        "Word": "concede",
        "Meaning": "to admit that something is true",
        "Sentence": "He eventually conceded that she was right.",
        "PartOfSpeech": "verb",
    }
    return {
        "noteId": 101,
        "modelName": "English",
        "cards": [201],
        "fields": {
            name: {"value": value, "order": index}
            for index, (name, value) in enumerate(values.items())
        },
    }


class FakeAnki:
    def __init__(self) -> None:
        self.note = make_note()
        self.fields = list(self.note["fields"])
        self.updates: list[tuple[int, dict[str, str]]] = []
        self.schedule = (101, (201,), ((201, 2, 42, 7, 2500, 10, 1),))

    def find_notes(self, query: str) -> list[int]:
        return [101]

    def notes_info(self, note_ids: list[int]) -> list[dict[str, Any]]:
        return [deepcopy(self.note)] if note_ids else []

    def model_field_names(self, model_name: str) -> list[str]:
        return list(self.fields)

    def add_model_field(self, model_name: str, field_name: str) -> None:
        self.fields.append(field_name)
        self.note["fields"][field_name] = {
            "value": "",
            "order": len(self.note["fields"]),
        }

    def update_note_fields(self, note_id: int, updates: dict[str, str]) -> None:
        self.updates.append((note_id, deepcopy(updates)))
        for name, value in updates.items():
            self.note["fields"][name]["value"] = value

    def snapshot(self, note: dict[str, Any]) -> Any:
        return self.schedule


class FakeTranslator:
    def __init__(self) -> None:
        self.calls = 0

    def translate(
        self, source: dict[str, str | None], requested: set[str]
    ) -> tuple[Translation, bool]:
        self.calls += 1
        self.last_source = source
        self.last_requested = requested
        return (
            Translation(
                meaning_ru="признать, что что-либо является правдой",
                sentence_meaning_ru="В конце концов он признал, что она была права.",
                part_of_speech_ru="глагол",
            ),
            False,
        )


class PipelineTests(unittest.TestCase):
    def config(self, root: Path, overwrite: bool = False) -> Config:
        return Config(
            anki=AnkiConfig("http://127.0.0.1:8765", "deck:English", 3),
            fields=FIELDS,
            llm=LLMConfig("https://example.test/v1", "model", "KEY", 1, 0),
            processing=ProcessingConfig(
                1, overwrite, True, root / "cache", root / "logs"
            ),
        )

    def test_dry_run_adds_nothing_and_does_not_call_llm(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            anki = FakeAnki()
            counters, preview, missing = run_pipeline(
                anki,
                None,
                self.config(Path(directory)),
                "deck:English",
                10,
                dry_run=True,
            )
            self.assertEqual(0, len(anki.updates))
            self.assertNotIn("MeaningRu", anki.fields)
            self.assertEqual(1, counters.eligible)
            self.assertEqual(
                ["MeaningRu", "SentenceMeaningRu", "PartOfSpeechRu"],
                missing["English"],
            )
            self.assertEqual(3, len(preview[0]["changes"]))

    def test_write_updates_same_note_and_only_target_fields(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            anki = FakeAnki()
            translator = FakeTranslator()
            counters, _, missing = run_pipeline(
                anki,
                translator,
                self.config(Path(directory)),
                "deck:English",
                10,
                dry_run=False,
            )
            self.assertEqual(1, counters.updated)
            self.assertEqual(0, counters.errors)
            self.assertEqual(1, translator.calls)
            self.assertEqual(101, anki.updates[0][0])
            self.assertEqual(set(FIELDS.target_fields), set(anki.updates[0][1]))
            self.assertEqual(list(FIELDS.target_fields), missing["English"])

    def test_existing_target_is_not_overwritten(self) -> None:
        note = make_note()
        note["fields"]["MeaningRu"] = {"value": "ручной перевод", "order": 4}
        _, requested = required_outputs(note, FIELDS, overwrite=False)
        self.assertNotIn("meaning_ru", requested)
        self.assertIn("sentence_meaning_ru", requested)


class TranslationValidationTests(unittest.TestCase):
    def test_rejects_extra_keys(self) -> None:
        with self.assertRaises(LLMError):
            validate_translation(
                {
                    "meaning_ru": "значение",
                    "sentence_meaning_ru": "пример",
                    "part_of_speech_ru": "глагол",
                    "unexpected": "bad",
                },
                {"meaning_ru"},
            )

    def test_rejects_missing_requested_value(self) -> None:
        with self.assertRaises(LLMError):
            validate_translation(
                {
                    "meaning_ru": None,
                    "sentence_meaning_ru": None,
                    "part_of_speech_ru": None,
                },
                {"meaning_ru"},
            )


class MockAnkiHandler(BaseHTTPRequestHandler):
    actions: list[str] = []

    def do_POST(self) -> None:  # noqa: N802 - stdlib handler API
        length = int(self.headers["Content-Length"])
        request = json.loads(self.rfile.read(length))
        action = request["action"]
        self.actions.append(action)
        note = make_note()
        responses: dict[str, Any] = {
            "version": 6,
            "findNotes": [101],
            "notesInfo": [note],
            "modelFieldNames": list(note["fields"]),
        }
        body = json.dumps({"result": responses[action], "error": None}).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, format: str, *args: Any) -> None:
        pass


class CliDryRunTests(unittest.TestCase):
    def test_http_dry_run_performs_no_mutating_actions(self) -> None:
        MockAnkiHandler.actions = []
        server = ThreadingHTTPServer(("127.0.0.1", 0), MockAnkiHandler)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        try:
            with tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                config = {
                    "anki": {
                        "url": f"http://127.0.0.1:{server.server_port}",
                        "query": "deck:English",
                        "sample_size": 1,
                    },
                    "fields": vars(FIELDS),
                    "llm": {
                        "base_url": "https://example.test/v1",
                        "model": "model",
                        "api_key_env": "KEY",
                    },
                    "processing": {
                        "cache_dir": "cache",
                        "log_dir": "logs",
                        "overwrite_existing": False,
                        "verify_scheduling": True,
                    },
                }
                path = root / "config.yaml"
                path.write_text(json.dumps(config), encoding="utf-8")
                output = io.StringIO()
                with contextlib.redirect_stdout(output):
                    exit_code = main(
                        ["--config", str(path), "--dry-run", "--limit", "10"]
                    )
                result = json.loads(output.getvalue())
            self.assertEqual(0, exit_code)
            self.assertEqual(1, result["estimated_llm_calls"])
            self.assertEqual(0, result["writes"])
            self.assertNotIn("modelFieldAdd", MockAnkiHandler.actions)
            self.assertNotIn("updateNoteFields", MockAnkiHandler.actions)
        finally:
            server.shutdown()
            server.server_close()
            thread.join(timeout=2)

if __name__ == "__main__":
    unittest.main()
