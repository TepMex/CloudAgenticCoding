"""Parse Unihan variant fields for simplified/traditional mappings."""

from __future__ import annotations

import zipfile
from collections import defaultdict
from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True)
class VariantEdge:
    source: str
    target: str
    direction: str  # s2t or t2s
    is_ambiguous: bool
    source_name: str = "unihan"
    source_record_id: str = ""


def codepoint_to_char(token: str) -> str:
    """Convert 'U+8BF4' or 'U+8BF4<kFoo' to a character."""
    token = token.strip().split("<", 1)[0]
    if not token.startswith("U+"):
        raise ValueError(token)
    return chr(int(token[2:], 16))


def parse_unihan_variants(unihan_zip: Path) -> list[VariantEdge]:
    with zipfile.ZipFile(unihan_zip) as zf:
        text = zf.read("Unihan_Variants.txt").decode("utf-8")

    # Map character -> list of traditional / simplified targets from Unihan.
    trad_of: dict[str, list[str]] = defaultdict(list)  # simplified -> traditional(s)
    simp_of: dict[str, list[str]] = defaultdict(list)  # traditional -> simplified(s)

    for line in text.splitlines():
        if not line or line.startswith("#"):
            continue
        parts = line.split("\t")
        if len(parts) < 3:
            continue
        cp, field, values = parts[0], parts[1], parts[2]
        if field not in ("kTraditionalVariant", "kSimplifiedVariant"):
            continue
        src = codepoint_to_char(cp)
        targets = [codepoint_to_char(tok) for tok in values.split()]
        if field == "kTraditionalVariant":
            for t in targets:
                if t not in trad_of[src]:
                    trad_of[src].append(t)
        else:
            for t in targets:
                if t not in simp_of[src]:
                    simp_of[src].append(t)

    edges: list[VariantEdge] = []
    seen: set[tuple[str, str, str]] = set()

    def add(src: str, tgt: str, direction: str, ambiguous: bool, record: str) -> None:
        key = (src, tgt, direction)
        if key in seen:
            return
        seen.add(key)
        edges.append(
            VariantEdge(
                source=src,
                target=tgt,
                direction=direction,
                is_ambiguous=ambiguous,
                source_record_id=record,
            )
        )

    for src, targets in trad_of.items():
        amb = len(targets) > 1
        for i, tgt in enumerate(targets):
            add(src, tgt, "s2t", amb, f"kTraditionalVariant:{src}:{i}")

    for src, targets in simp_of.items():
        amb = len(targets) > 1
        for i, tgt in enumerate(targets):
            add(src, tgt, "t2s", amb, f"kSimplifiedVariant:{src}:{i}")

    return edges
