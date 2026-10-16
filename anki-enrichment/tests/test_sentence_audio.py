from __future__ import annotations

import contextlib
import io
import json
import os
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
    TTSConfig,
    load_config,
)
from anki_enricher.sentence_audio import SentenceAudioError, run_sentence_audio
from anki_enricher.tts import AudioCache, ProxyAPITTS, SpeechClip, TTSError
from sentence_meaning_ru_audio import main


FIELDS = FieldMapping(
    word="Word",
    meaning_en="Meaning",
    sentence_en="Sentence",
    part_of_speech_en="PartOfSpeech",
    meaning_ru="MeaningRu",
    sentence_meaning_ru="SentenceMeaningRu",
    part_of_speech_ru="PartOfSpeechRu",
)


def make_note(
    note_id: int = 101,
    sentence: str = "Я его друг.",
    audio: str | None = None,
) -> dict[str, Any]:
    values = {"Word": "的", "SentenceMeaningRu": sentence}
    if audio is not None:
        values["SentenceMeaningRuAudio"] = audio
    return {
        "noteId": note_id,
        "modelName": "Refold",
        "cards": [note_id + 1000],
        "fields": {
            name: {"value": value, "order": index}
            for index, (name, value) in enumerate(values.items())
        },
    }


def make_config(root: Path, **tts_overrides: Any) -> Config:
    return Config(
        anki=AnkiConfig("http://127.0.0.1:9", 'deck:"Refold"', 3),
        fields=FIELDS,
        llm=LLMConfig("https://example.test/v1", "model", "KEY", 1, 0),
        processing=ProcessingConfig(1, False, True, root / "cache", root / "logs"),
        tts=TTSConfig(voices=("nova", "alloy"), **tts_overrides),
    )


class FakeAnki:
    def __init__(self, notes: list[dict[str, Any]] | None = None) -> None:
        self.notes = {int(note["noteId"]): note for note in (notes or [make_note()])}
        self.model_fields = list(next(iter(self.notes.values()))["fields"])
        self.media: set[str] = set()
        self.stored: dict[str, bytes] = {}
        self.schedule: Any = ("stable",)
        self.field_adds: list[str] = []

    def find_notes(self, query: str) -> list[int]:
        return list(self.notes)

    def notes_info(self, note_ids: list[int]) -> list[dict[str, Any]]:
        return [deepcopy(self.notes[note_id]) for note_id in note_ids]

    def model_field_names(self, model_name: str) -> list[str]:
        return list(self.model_fields)

    def add_model_field(self, model_name: str, field_name: str) -> None:
        self.field_adds.append(field_name)
        self.model_fields.append(field_name)
        for note in self.notes.values():
            note["fields"].setdefault(field_name, {"value": "", "order": 20})

    def update_note_fields(self, note_id: int, fields: dict[str, str]) -> None:
        for name, value in fields.items():
            self.notes[note_id]["fields"][name]["value"] = value

    def media_file_names(self, pattern: str) -> set[str]:
        return set(self.media)

    def store_media_file(self, filename: str, data: bytes) -> str:
        self.stored[filename] = data
        self.media.add(filename)
        return filename

    def snapshot(self, note: dict[str, Any]) -> Any:
        return self.schedule


class Cycle:
    def __init__(self, values: tuple[str, ...]) -> None:
        self.values = values
        self.index = 0

    def choice(self, seq: Any) -> str:
        value = self.values[self.index % len(self.values)]
        self.index += 1
        if value not in seq:
            raise AssertionError(value)
        return value


class FakeTTS:
    def __init__(self, filename: str = "sentence_ru_fixed.mp3") -> None:
        self.filename = filename
        self.calls: list[tuple[str, str]] = []

    def synthesize(self, text: str, voice: str) -> SpeechClip:
        self.calls.append((text, voice))
        return SpeechClip(self.filename, voice, b"ID3" + b"x" * 40, len(self.calls) > 1)


class SentenceAudioPipelineTests(unittest.TestCase):
    def test_dry_run_does_not_write_or_choose_a_voice(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            anki = FakeAnki()
            counters, preview, missing = run_sentence_audio(
                anki,
                FakeTTS(),
                make_config(Path(directory)),
                "deck:Refold",
                None,
                dry_run=True,
                rng=Cycle(("nova",)),
            )
        self.assertEqual(1, counters.eligible)
        self.assertEqual(["SentenceMeaningRuAudio"], missing["Refold"])
        self.assertEqual([], anki.field_adds)
        self.assertEqual({}, anki.stored)
        self.assertEqual(["SentenceMeaningRuAudio"], preview[0]["changes"])
        self.assertNotIn("voice", preview[0])

    def test_write_adds_field_media_and_sound_tag(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            anki = FakeAnki([make_note(sentence="<b>Я его друг.</b>")])
            tts = FakeTTS()
            counters, preview, _missing = run_sentence_audio(
                anki,
                tts,
                make_config(Path(directory)),
                "deck:Refold",
                None,
                dry_run=False,
                rng=Cycle(("alloy",)),
            )
        self.assertEqual(1, counters.updated)
        self.assertEqual(0, counters.errors)
        self.assertEqual([("Я его друг.", "alloy")], tts.calls)
        self.assertEqual(["SentenceMeaningRuAudio"], anki.field_adds)
        self.assertEqual(
            "[sound:sentence_ru_fixed.mp3]",
            anki.notes[101]["fields"]["SentenceMeaningRuAudio"]["value"],
        )
        self.assertIn("sentence_ru_fixed.mp3", anki.stored)
        self.assertEqual("alloy", preview[0]["voice"])

    def test_existing_audio_and_empty_source_are_skipped(self) -> None:
        notes = [
            make_note(101, "Я его друг.", "[sound:already.mp3]"),
            make_note(102, "   "),
        ]
        with tempfile.TemporaryDirectory() as directory:
            anki = FakeAnki(notes)
            tts = FakeTTS()
            counters, _preview, missing = run_sentence_audio(
                anki,
                tts,
                make_config(Path(directory)),
                "deck:Refold",
                None,
                dry_run=False,
                rng=Cycle(("nova",)),
            )
        self.assertEqual([], tts.calls)
        self.assertEqual({}, missing)
        self.assertEqual(1, counters.skipped_existing)
        self.assertEqual(1, counters.skipped_empty)

    def test_same_clip_is_uploaded_once(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            anki = FakeAnki([make_note(101, "Раз."), make_note(102, "Два.")])
            counters, _preview, _missing = run_sentence_audio(
                anki,
                FakeTTS("sentence_ru_same.mp3"),
                make_config(Path(directory)),
                "deck:Refold",
                None,
                dry_run=False,
                rng=Cycle(("nova", "alloy")),
            )
        self.assertEqual(1, len(anki.stored))
        self.assertEqual(1, counters.tts_calls)
        self.assertEqual(1, counters.cache_hits)
        self.assertEqual(2, counters.updated)

    def test_scheduling_change_is_an_error(self) -> None:
        class Changing(FakeAnki):
            def __init__(self) -> None:
                super().__init__()
                self.calls = 0

            def snapshot(self, note: dict[str, Any]) -> int:
                self.calls += 1
                return self.calls

        with tempfile.TemporaryDirectory() as directory:
            anki = Changing()
            counters, _preview, _missing = run_sentence_audio(
                anki,
                FakeTTS(),
                make_config(Path(directory)),
                "deck:Refold",
                None,
                dry_run=False,
                rng=Cycle(("nova",)),
            )
        self.assertEqual(0, counters.updated)
        self.assertEqual(1, counters.errors)

    def test_missing_source_field_stops_before_writes(self) -> None:
        note = make_note()
        del note["fields"]["SentenceMeaningRu"]
        with tempfile.TemporaryDirectory() as directory:
            anki = FakeAnki([note])
            with self.assertRaises(SentenceAudioError):
                run_sentence_audio(
                    anki,
                    FakeTTS(),
                    make_config(Path(directory)),
                    "deck:Refold",
                    None,
                    dry_run=False,
                    rng=Cycle(("nova",)),
                )
        self.assertEqual([], anki.field_adds)


class SpeechHandler(BaseHTTPRequestHandler):
    bodies: list[dict[str, Any]] = []
    mode = "retry"

    def do_POST(self) -> None:  # noqa: N802 - stdlib handler API
        length = int(self.headers.get("Content-Length", "0"))
        body = json.loads(self.rfile.read(length))
        self.bodies.append(body)
        if self.mode == "json":
            payload = b'{"error":"not audio"}'
            status = 200
            content_type = "application/json"
        elif self.mode == "denied":
            payload = b"unauthorized"
            status = 401
            content_type = "text/plain"
        elif self.mode == "retry" and len(self.bodies) == 1:
            payload = b"busy"
            status = 500
            content_type = "text/plain"
        else:
            payload = b"ID3" + b"\x00" * 64
            status = 200
            content_type = "audio/mpeg"
        self.send_response(status)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)

    def log_message(self, format: str, *args: Any) -> None:
        pass


class ProxyAPITTSTests(unittest.TestCase):
    def server(self, mode: str) -> tuple[ThreadingHTTPServer, threading.Thread]:
        SpeechHandler.bodies = []
        SpeechHandler.mode = mode
        httpd = ThreadingHTTPServer(("127.0.0.1", 0), SpeechHandler)
        thread = threading.Thread(target=httpd.serve_forever, daemon=True)
        thread.start()
        return httpd, thread

    def config(self, port: int) -> TTSConfig:
        return TTSConfig(
            base_url=f"http://127.0.0.1:{port}",
            voices=("coral",),
            max_retries=1,
            timeout_seconds=2,
        )

    def test_retries_then_caches_audio(self) -> None:
        httpd, thread = self.server("retry")
        try:
            with tempfile.TemporaryDirectory() as directory:
                client = ProxyAPITTS(
                    self.config(httpd.server_port),
                    "test-key",
                    AudioCache(Path(directory)),
                    sleep=lambda _seconds: None,
                )
                first = client.synthesize("Привет.", "coral")
                second = client.synthesize("Привет.", "coral")
            self.assertFalse(first.cache_hit)
            self.assertTrue(second.cache_hit)
            self.assertTrue(first.data.startswith(b"ID3"))
            self.assertEqual(first.filename, second.filename)
            self.assertEqual(2, len(SpeechHandler.bodies))
            self.assertEqual("openai/gpt-4o-mini-tts", SpeechHandler.bodies[0]["model"])
            self.assertEqual("Привет.", SpeechHandler.bodies[0]["input"])
            self.assertEqual("coral", SpeechHandler.bodies[0]["voice"])
            self.assertIn("по-русски", SpeechHandler.bodies[0]["instructions"])
        finally:
            httpd.shutdown()
            httpd.server_close()
            thread.join(timeout=2)

    def test_json_body_and_auth_errors_are_not_cached(self) -> None:
        httpd, thread = self.server("json")
        try:
            with tempfile.TemporaryDirectory() as directory:
                client = ProxyAPITTS(
                    self.config(httpd.server_port),
                    "test-key",
                    AudioCache(Path(directory)),
                    sleep=lambda _seconds: None,
                )
                with self.assertRaises(TTSError):
                    client.synthesize("Привет.", "coral")
                self.assertEqual([], list(Path(directory).glob("*.mp3")))
        finally:
            httpd.shutdown()
            httpd.server_close()
            thread.join(timeout=2)

        httpd, thread = self.server("denied")
        try:
            with tempfile.TemporaryDirectory() as directory:
                client = ProxyAPITTS(
                    self.config(httpd.server_port),
                    "test-key",
                    AudioCache(Path(directory)),
                    sleep=lambda _seconds: None,
                )
                with self.assertRaises(TTSError):
                    client.synthesize("Привет.", "coral")
            self.assertEqual(1, len(SpeechHandler.bodies))
        finally:
            httpd.shutdown()
            httpd.server_close()
            thread.join(timeout=2)


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


class SentenceAudioCliTests(unittest.TestCase):
    def test_dry_run_performs_no_writes(self) -> None:
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
                        "query": "deck:other",
                        "sample_size": 1,
                    },
                    "fields": vars(FIELDS),
                    "llm": {
                        "base_url": "https://example.test/v1",
                        "model": "model",
                        "api_key_env": "KEY",
                    },
                    "processing": {"cache_dir": "cache", "log_dir": "logs"},
                    "tts": {"model": "gpt-4o-tts-mini", "voices": ["nova"]},
                }
                path = root / "config.yaml"
                path.write_text(json.dumps(config), encoding="utf-8")
                output = io.StringIO()
                with contextlib.redirect_stdout(output):
                    exit_code = main(["--config", str(path), "--dry-run"])
                result = json.loads(output.getvalue())
            self.assertEqual(0, exit_code)
            self.assertEqual("openai/gpt-4o-mini-tts", result["model"])
            self.assertEqual(1, result["notes_requiring_tts"])
            self.assertEqual(0, result["writes"])
            self.assertEqual(0, result["tts_calls"])
            self.assertNotIn("storeMediaFile", MockAnkiHandler.actions)
            self.assertNotIn("modelFieldAdd", MockAnkiHandler.actions)
            self.assertNotIn("updateNoteFields", MockAnkiHandler.actions)
        finally:
            server.shutdown()
            server.server_close()
            thread.join(timeout=2)

    def test_enrich_requires_an_api_key(self) -> None:
        MockAnkiHandler.actions = []
        server = ThreadingHTTPServer(("127.0.0.1", 0), MockAnkiHandler)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        previous = os.environ.pop("PROXYAPI_API_KEY", None)
        try:
            with tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                config = {
                    "anki": {
                        "url": f"http://127.0.0.1:{server.server_port}",
                        "query": "deck:other",
                    },
                    "fields": vars(FIELDS),
                    "llm": {
                        "base_url": "https://example.test/v1",
                        "model": "model",
                        "api_key_env": "KEY",
                    },
                    "processing": {"cache_dir": "cache", "log_dir": "logs"},
                }
                path = root / "config.yaml"
                path.write_text(json.dumps(config), encoding="utf-8")
                error = io.StringIO()
                with contextlib.redirect_stderr(error):
                    exit_code = main(
                        ["--config", str(path), "--enrich", "--backup-confirmed"]
                    )
            self.assertEqual(2, exit_code)
            self.assertIn("PROXYAPI_API_KEY", error.getvalue())
            self.assertNotIn("findNotes", MockAnkiHandler.actions)
        finally:
            if previous is not None:
                os.environ["PROXYAPI_API_KEY"] = previous
            server.shutdown()
            server.server_close()
            thread.join(timeout=2)

    def test_real_config_keeps_translation_query_and_targets_refold(self) -> None:
        config = load_config(Path(__file__).resolve().parents[1] / "config.yaml")
        self.assertIn("Mandarin HSK", config.anki.query)
        self.assertIn("Refold Mandarin 1k Simplified", config.tts.query)
        self.assertEqual("SentenceMeaningRu", config.tts.source_field)
        self.assertEqual("SentenceMeaningRuAudio", config.tts.target_field)
        self.assertEqual("openai/gpt-4o-mini-tts", config.tts.model)


class ConfigAliasTests(unittest.TestCase):
    def test_swapped_model_name_maps_to_proxyapi_id(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "config.yaml"
            path.write_text(
                json.dumps(
                    {
                        "anki": {"url": "http://127.0.0.1:8765", "query": "deck:x"},
                        "fields": vars(FIELDS),
                        "llm": {
                            "base_url": "https://example.test/v1",
                            "model": "model",
                            "api_key_env": "KEY",
                        },
                        "processing": {"cache_dir": "cache", "log_dir": "logs"},
                        "tts": {"model": "gpt-4o-tts-mini"},
                    }
                ),
                encoding="utf-8",
            )
            loaded = load_config(path)
        self.assertEqual("openai/gpt-4o-mini-tts", loaded.tts.model)


if __name__ == "__main__":
    unittest.main()
