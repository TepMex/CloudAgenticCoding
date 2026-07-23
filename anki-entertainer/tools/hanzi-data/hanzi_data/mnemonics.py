"""Mnemonic ranking, deduplication, and multi-provider import model."""

from __future__ import annotations

import hashlib
import json
import re
import unicodedata
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Protocol

from .mmah import HanziRecord


MNEMONIC_MAX_CODE_POINTS = 500
MAX_PER_CHARACTER = 5
# Matches Android HanziQuery.MAX_MNEMONIC_KEY_CODE_POINTS — single Han or compounds.
MAX_MNEMONIC_KEY_CODE_POINTS = 20
WHITESPACE_RE = re.compile(r"\s+")


def _is_cjk_ideograph(cp: int) -> bool:
    return (
        0x3400 <= cp <= 0x4DBF
        or 0x4E00 <= cp <= 0x9FFF
        or 0xF900 <= cp <= 0xFAFF
        or 0x20000 <= cp <= 0x2A6DF
        or 0x2A700 <= cp <= 0x2B73F
        or 0x2B740 <= cp <= 0x2B81F
        or 0x2B820 <= cp <= 0x2CEAF
        or 0x30000 <= cp <= 0x3134F
    )


def is_valid_mnemonic_key(key: str) -> bool:
    """Accept a single Han character or a contiguous compound of Han ideographs."""
    if not key or not isinstance(key, str):
        return False
    chars = list(key)
    if len(chars) < 1 or len(chars) > MAX_MNEMONIC_KEY_CODE_POINTS:
        return False
    return all(_is_cjk_ideograph(ord(c)) for c in chars)


@dataclass
class MnemonicRecord:
    character: str
    story: str
    language: str
    raw_score: float | None
    normalized_score: float
    source_priority: int
    source: str
    source_record_id: str
    attribution: str
    license: str
    content_hash: str


class MnemonicProvider(Protocol):
    name: str
    source_priority: int
    default_license: str
    default_attribution: str

    def load(self) -> list[dict[str, Any]]:
        ...


@dataclass
class JsonMnemonicProvider:
    """Loads a JSON list of mnemonic objects from a local seed file."""

    path: Path
    name: str = "project_seed"
    source_priority: int = 100
    default_license: str = "CC0-1.0"
    default_attribution: str = "anki-entertainer project seed"

    def load(self) -> list[dict[str, Any]]:
        data = json.loads(self.path.read_text(encoding="utf-8"))
        if not isinstance(data, list):
            raise ValueError(f"Mnemonic seed must be a JSON list: {self.path}")
        return data


def mmah_memory_cue(record: HanziRecord) -> str | None:
    """Turn source-backed MMAH structure fields into a concise learning cue.

    These rows are deliberately described as cues rather than historical stories:
    they preserve MMAH's distinction between semantic/phonetic components and
    free-form structure hints without inventing additional etymology.
    """
    hint = normalize_story(record.etymology_hint or "").rstrip(".")
    semantic = normalize_story(record.semantic_component or "")
    phonetic = normalize_story(record.phonetic_component or "")

    if record.etymology_type == "pictophonetic" and semantic and phonetic:
        meaning = hint or semantic
        return (
            f"Meaning clue {semantic} carries “{meaning}”; "
            f"sound clue {phonetic} suggests the pronunciation."
        )
    if hint:
        return f"Picture this structure: {hint}."
    return None


@dataclass
class MmahMnemonicProvider:
    """Derives broad offline memory-cue coverage from bundled MMAH records."""

    records: dict[str, HanziRecord]
    name: str = "makemeahanzi_derived_cue"
    source_priority: int = 10
    default_license: str = "LGPL-3.0-or-later"
    default_attribution: str = "Derived from Make Me a Hanzi dictionary.txt"

    def load(self) -> list[dict[str, Any]]:
        rows: list[dict[str, Any]] = []
        for character in sorted(self.records, key=lambda ch: (ord(ch), ch)):
            record = self.records[character]
            cue = mmah_memory_cue(record)
            if cue is None:
                continue
            rows.append(
                {
                    "character": character,
                    "story": cue,
                    "language": "en",
                    "normalized_score": 10,
                    "source_record_id": f"derived-cue:{record.source_record_id}",
                }
            )
        return rows


def normalize_story(story: str) -> str:
    text = unicodedata.normalize("NFC", story or "")
    text = text.replace("\xa0", " ")
    text = WHITESPACE_RE.sub(" ", text).strip()
    return text


def truncate_code_points(text: str, limit: int = MNEMONIC_MAX_CODE_POINTS) -> str:
    chars = list(text)
    if len(chars) <= limit:
        return text
    return "".join(chars[:limit])


def nearly_identical(a: str, b: str) -> bool:
    na = normalize_story(a).casefold()
    nb = normalize_story(b).casefold()
    if na == nb:
        return True
    # Near-identical: differ only by trailing punctuation
    return na.rstrip(".;!？。！") == nb.rstrip(".;!？。！")


def content_hash(character: str, story: str, source: str) -> str:
    payload = f"{character}\0{story}\0{source}".encode("utf-8")
    return hashlib.sha256(payload).hexdigest()


def normalize_provider_rows(
    rows: list[dict[str, Any]],
    *,
    source: str,
    source_priority: int,
    default_license: str,
    default_attribution: str,
) -> list[MnemonicRecord]:
    out: list[MnemonicRecord] = []
    for i, row in enumerate(rows):
        ch = row.get("character") or row.get("hanzi")
        story = normalize_story(str(row.get("story") or row.get("text") or ""))
        if not ch or not story:
            continue
        ch = unicodedata.normalize("NFC", str(ch)).strip()
        # Single Han character or contiguous compound (2+ Han).
        if not is_valid_mnemonic_key(ch):
            continue
        story = truncate_code_points(story)
        raw = row.get("raw_score", row.get("score", row.get("votes")))
        raw_score: float | None
        try:
            raw_score = float(raw) if raw is not None else None
        except (TypeError, ValueError):
            raw_score = None
        # Normalized score: prefer explicit normalized_score; else map raw; else 0.
        if row.get("normalized_score") is not None:
            try:
                normalized = float(row["normalized_score"])
            except (TypeError, ValueError):
                normalized = 0.0
        elif raw_score is not None:
            # Assume higher is better; clamp into 0..1000 style if needed.
            normalized = float(raw_score)
        else:
            normalized = 0.0
        prio = int(row.get("source_priority", source_priority))
        out.append(
            MnemonicRecord(
                character=ch,
                story=story,
                language=str(row.get("language") or "en"),
                raw_score=raw_score,
                normalized_score=normalized,
                source_priority=prio,
                source=str(row.get("source") or source),
                source_record_id=str(row.get("source_record_id") or f"{source}:{i}"),
                attribution=str(row.get("attribution") or default_attribution),
                license=str(row.get("license") or default_license),
                content_hash=content_hash(ch, story, str(row.get("source") or source)),
            )
        )
    return out


def rank_and_dedupe(records: list[MnemonicRecord]) -> list[MnemonicRecord]:
    """Sort by normalized_score desc, source_priority desc, source, id; keep top 5 per char."""
    by_char: dict[str, list[MnemonicRecord]] = {}
    for r in records:
        by_char.setdefault(r.character, []).append(r)

    selected: list[MnemonicRecord] = []
    for ch in sorted(by_char.keys()):
        group = by_char[ch]
        group.sort(
            key=lambda r: (
                -r.normalized_score,
                -r.source_priority,
                r.source,
                r.source_record_id,
            )
        )
        kept: list[MnemonicRecord] = []
        for r in group:
            if any(nearly_identical(r.story, k.story) for k in kept):
                continue
            kept.append(r)
            if len(kept) >= MAX_PER_CHARACTER:
                break
        selected.extend(kept)
    return selected


def import_mnemonics(providers: list[MnemonicProvider]) -> list[MnemonicRecord]:
    all_rows: list[MnemonicRecord] = []
    for p in providers:
        rows = p.load()
        all_rows.extend(
            normalize_provider_rows(
                rows,
                source=p.name,
                source_priority=p.source_priority,
                default_license=p.default_license,
                default_attribution=p.default_attribution,
            )
        )
    return rank_and_dedupe(all_rows)
