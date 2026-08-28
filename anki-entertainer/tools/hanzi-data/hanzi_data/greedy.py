"""Parse the project greedy-component table (3500 characters, conservative phonetics)."""

from __future__ import annotations

import csv
from dataclasses import dataclass
from pathlib import Path

FLAG_YES = "Да"
FLAG_NO = "Нет"
COL_FLAG = "является фонетико-семантическим компонентом"
COL_PHONETIC = "фонетик"
COL_CHAR = "汉字"
COMPONENT_COLS = tuple(f"部件{i}" for i in range(1, 10))


@dataclass(frozen=True)
class GreedyCompositionRecord:
    character: str
    components: list[str]
    is_phonetic_semantic: bool
    phonetic: str | None


def parse_greedy_components_csv(path: Path) -> dict[str, GreedyCompositionRecord]:
    """Read the UTF-8 (BOM-tolerant) greedy component CSV keyed by character."""
    records: dict[str, GreedyCompositionRecord] = {}
    with path.open(encoding="utf-8-sig", newline="") as fh:
        reader = csv.DictReader(fh)
        for row in reader:
            ch = (row.get(COL_CHAR) or "").strip()
            if not ch:
                continue
            if len(ch) != 1:
                raise ValueError(f"Expected a single-character key, got {ch!r}")
            flag = (row.get(COL_FLAG) or "").strip()
            if flag == FLAG_YES:
                is_ps = True
            elif flag == FLAG_NO:
                is_ps = False
            else:
                raise ValueError(f"Unknown phonetic-semantic flag {flag!r} for {ch}")
            components = [
                (row.get(col) or "").strip()
                for col in COMPONENT_COLS
                if (row.get(col) or "").strip()
            ]
            if not components:
                raise ValueError(f"No components for {ch}")
            phonetic_raw = (row.get(COL_PHONETIC) or "").strip()
            phonetic = phonetic_raw if is_ps and phonetic_raw else None
            if is_ps and phonetic is None:
                raise ValueError(f"Pictophonetic row {ch} is missing a phonetic")
            records[ch] = GreedyCompositionRecord(
                character=ch,
                components=components,
                is_phonetic_semantic=is_ps,
                phonetic=phonetic,
            )
    return records
