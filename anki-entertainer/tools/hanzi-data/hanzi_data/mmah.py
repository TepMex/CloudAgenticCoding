"""Parse Make Me a Hanzi dictionary.txt (LGPL-3.0-or-later)."""

from __future__ import annotations

import json
from dataclasses import dataclass
from pathlib import Path


@dataclass
class HanziRecord:
    character: str
    code_point: int
    decomposition: str | None
    etymology_type: str | None
    etymology_hint: str | None
    semantic_component: str | None
    phonetic_component: str | None
    primary_source: str = "makemeahanzi"
    source_record_id: str = ""


def parse_mmah_dictionary(path: Path) -> dict[str, HanziRecord]:
    records: dict[str, HanziRecord] = {}
    for line_no, line in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
        line = line.strip()
        if not line:
            continue
        obj = json.loads(line)
        ch = obj["character"]
        if len(ch) != 1 and len(ch.encode("utf-16-le")) != 4 and len(ch) != 1:
            # Keep single Unicode code point characters (including non-BMP via Python str).
            pass
        # Python str iterates by code point; require exactly one code point.
        if len(ch) != 1:
            continue
        etym = obj.get("etymology") or {}
        etype = etym.get("type") if isinstance(etym, dict) else None
        hint = etym.get("hint") if isinstance(etym, dict) else None
        semantic = None
        phonetic = None
        if isinstance(etym, dict) and etype == "pictophonetic":
            semantic = etym.get("semantic") or None
            phonetic = etym.get("phonetic") or None
            # Normalize empty strings to None
            if semantic == "":
                semantic = None
            if phonetic == "":
                phonetic = None
        if isinstance(hint, str):
            hint = hint.replace("\xa0", " ").strip() or None
        records[ch] = HanziRecord(
            character=ch,
            code_point=ord(ch),
            decomposition=obj.get("decomposition") or None,
            etymology_type=etype,
            etymology_hint=hint,
            semantic_component=semantic,
            phonetic_component=phonetic,
            source_record_id=f"dictionary.txt:{line_no}",
        )
    return records
