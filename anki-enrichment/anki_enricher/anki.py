from __future__ import annotations

import json
import urllib.error
import urllib.request
from dataclasses import dataclass
from typing import Any, Iterable


class AnkiConnectError(RuntimeError):
    pass


@dataclass(frozen=True)
class ScheduleSnapshot:
    note_id: int
    card_ids: tuple[int, ...]
    scheduling: tuple[tuple[int, ...], ...]


class AnkiConnect:
    def __init__(self, url: str, timeout: float = 15) -> None:
        self.url = url
        self.timeout = timeout

    def invoke(self, action: str, **params: Any) -> Any:
        payload = json.dumps(
            {"action": action, "version": 6, "params": params}
        ).encode("utf-8")
        request = urllib.request.Request(
            self.url,
            data=payload,
            headers={"Content-Type": "application/json"},
            method="POST",
        )
        try:
            with urllib.request.urlopen(request, timeout=self.timeout) as response:
                body = json.loads(response.read().decode("utf-8"))
        except (OSError, urllib.error.URLError, json.JSONDecodeError) as exc:
            raise AnkiConnectError(
                f"AnkiConnect is unavailable at {self.url}: {exc}"
            ) from exc
        if not isinstance(body, dict) or "error" not in body or "result" not in body:
            raise AnkiConnectError(f"Malformed AnkiConnect response for {action}")
        if body["error"] is not None:
            raise AnkiConnectError(f"AnkiConnect {action}: {body['error']}")
        return body["result"]

    def check(self) -> int:
        return int(self.invoke("version"))

    def deck_names(self) -> list[str]:
        return list(self.invoke("deckNames"))

    def find_notes(self, query: str) -> list[int]:
        return [int(value) for value in self.invoke("findNotes", query=query)]

    def notes_info(self, note_ids: Iterable[int]) -> list[dict[str, Any]]:
        ids = list(note_ids)
        result: list[dict[str, Any]] = []
        for start in range(0, len(ids), 500):
            result.extend(self.invoke("notesInfo", notes=ids[start : start + 500]))
        return result

    def model_field_names(self, model_name: str) -> list[str]:
        return list(self.invoke("modelFieldNames", modelName=model_name))

    def model_templates(self, model_name: str) -> dict[str, Any]:
        return dict(self.invoke("modelTemplates", modelName=model_name))

    def add_model_field(self, model_name: str, field_name: str) -> None:
        self.invoke("modelFieldAdd", modelName=model_name, fieldName=field_name)

    def update_note_fields(self, note_id: int, fields: dict[str, str]) -> None:
        self.invoke("updateNoteFields", note={"id": note_id, "fields": fields})

    def cards_info(self, card_ids: Iterable[int]) -> list[dict[str, Any]]:
        ids = list(card_ids)
        return list(self.invoke("cardsInfo", cards=ids)) if ids else []

    def get_ease_factors(self, card_ids: Iterable[int]) -> list[int]:
        ids = list(card_ids)
        return [int(value) for value in self.invoke("getEaseFactors", cards=ids)] if ids else []

    def snapshot(self, note: dict[str, Any]) -> ScheduleSnapshot:
        note_id = int(note["noteId"])
        card_ids = tuple(sorted(int(card_id) for card_id in note.get("cards", [])))
        cards = sorted(self.cards_info(card_ids), key=lambda card: int(card["cardId"]))
        ease_by_id = dict(zip(card_ids, self.get_ease_factors(card_ids), strict=True))
        scheduling = tuple(
            (
                int(card["cardId"]),
                int(card.get("type", 0)),
                int(card.get("queue", 0)),
                int(card.get("due", 0)),
                int(card.get("interval", 0)),
                ease_by_id[int(card["cardId"])],
                int(card.get("reps", 0)),
                int(card.get("lapses", 0)),
                int(card.get("left", 0)),
            )
            for card in cards
        )
        return ScheduleSnapshot(note_id, card_ids, scheduling)

    def export_package(self, deck: str, path: str) -> bool:
        return bool(
            self.invoke("exportPackage", deck=deck, path=path, includeSched=True)
        )
