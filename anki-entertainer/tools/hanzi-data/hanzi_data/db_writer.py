"""Write the Room-compatible SQLite database."""

from __future__ import annotations

import json
import sqlite3
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from .greedy import GreedyCompositionRecord
from .mmah import HanziRecord
from .mnemonics import MnemonicRecord
from .unihan import VariantEdge

# Must match Room @Database version and entity table/column names.
SCHEMA_VERSION = 2


DDL = """
CREATE TABLE IF NOT EXISTS hanzi (
  character TEXT NOT NULL PRIMARY KEY,
  codePoint INTEGER NOT NULL,
  decomposition TEXT,
  etymologyType TEXT,
  etymologyHint TEXT,
  semanticComponent TEXT,
  phoneticComponent TEXT,
  primarySource TEXT NOT NULL,
  sourceRecordId TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS greedy_composition (
  character TEXT NOT NULL PRIMARY KEY,
  componentsJson TEXT NOT NULL,
  isPhoneticSemantic INTEGER NOT NULL,
  phonetic TEXT
);

CREATE TABLE IF NOT EXISTS variant (
  sourceCharacter TEXT NOT NULL,
  targetCharacter TEXT NOT NULL,
  direction TEXT NOT NULL,
  localeOrStandard TEXT,
  isPreferred INTEGER NOT NULL,
  isAmbiguous INTEGER NOT NULL,
  source TEXT NOT NULL,
  sourceRecordId TEXT NOT NULL,
  PRIMARY KEY (sourceCharacter, targetCharacter, direction, source)
);

CREATE TABLE IF NOT EXISTS simplification (
  inputCharacter TEXT NOT NULL PRIMARY KEY,
  simplifiedCharacter TEXT NOT NULL,
  traditionalCharacter TEXT NOT NULL,
  classification TEXT NOT NULL,
  explanation TEXT NOT NULL,
  changedComponentsJson TEXT,
  evidenceType TEXT NOT NULL,
  confidence REAL NOT NULL,
  source TEXT NOT NULL,
  sourceRecordId TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS mnemonic (
  id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
  character TEXT NOT NULL,
  story TEXT NOT NULL,
  language TEXT NOT NULL,
  rawScore REAL,
  normalizedScore REAL NOT NULL,
  sourcePriority INTEGER NOT NULL,
  source TEXT NOT NULL,
  sourceRecordId TEXT NOT NULL,
  attribution TEXT NOT NULL,
  license TEXT NOT NULL,
  contentHash TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS index_mnemonic_character_normalizedScore
  ON mnemonic (character, normalizedScore);

CREATE TABLE IF NOT EXISTS dataset_metadata (
  id INTEGER NOT NULL PRIMARY KEY,
  schemaVersion INTEGER NOT NULL,
  datasetVersion TEXT NOT NULL,
  buildTimestamp TEXT NOT NULL,
  sourceVersionsJson TEXT NOT NULL,
  sourceChecksumsJson TEXT NOT NULL,
  recordCountsJson TEXT NOT NULL,
  licenseIdentifiersJson TEXT NOT NULL,
  roomIdentityHash TEXT
);

CREATE TABLE IF NOT EXISTS room_master_table (
  id INTEGER PRIMARY KEY,
  identity_hash TEXT
);
"""


def _preferred_pair(
    ch: str,
    variants: list[VariantEdge],
    hanzi: dict[str, HanziRecord],
) -> tuple[str, str, bool]:
    """Return (simplified, traditional, ambiguous) for a character."""
    s2t = [v for v in variants if v.source == ch and v.direction == "s2t"]
    t2s = [v for v in variants if v.source == ch and v.direction == "t2s"]
    if s2t:
        targets = [v.target for v in s2t]
        amb = len(targets) > 1 or any(v.is_ambiguous for v in s2t)
        # Prefer first Unihan target; keep traditional as first target for pair display.
        return ch, targets[0], amb
    if t2s:
        targets = [v.target for v in t2s]
        amb = len(targets) > 1 or any(v.is_ambiguous for v in t2s)
        return targets[0], ch, amb
    return ch, ch, False


def build_simplifications(
    hanzi: dict[str, HanziRecord],
    variants: list[VariantEdge],
    curated: list[dict[str, Any]],
) -> list[dict[str, Any]]:
    curated_by_input = {c["inputCharacter"]: c for c in curated}
    # Characters to cover: all hanzi keys plus all variant endpoints.
    chars: set[str] = set(hanzi.keys())
    for v in variants:
        chars.add(v.source)
        chars.add(v.target)

    # Index variants by source for quick lookup
    by_source: dict[str, list[VariantEdge]] = {}
    for v in variants:
        by_source.setdefault(v.source, []).append(v)

    rows: list[dict[str, Any]] = []
    for ch in sorted(chars, key=lambda c: (ord(c), c)):
        if ch in curated_by_input:
            rows.append(curated_by_input[ch])
            continue

        related = by_source.get(ch, [])
        simplified, traditional, ambiguous = _preferred_pair(ch, related, hanzi)
        # If character only appears as a target, still produce a row keyed by input=ch
        if not related and ch not in hanzi:
            continue

        t_ids = hanzi[traditional].decomposition if traditional in hanzi else None
        s_ids = hanzi[simplified].decomposition if simplified in hanzi else None
        # If input is traditional-looking and maps to simplified, compare those trees.
        if related:
            # Recompute ambiguity from all targets of this character
            s2t_targets = [v.target for v in related if v.direction == "s2t"]
            t2s_targets = [v.target for v in related if v.direction == "t2s"]
            if len(s2t_targets) > 1 or len(t2s_targets) > 1:
                ambiguous = True

        diff: StructuralDiffResult = compare_ids_trees(
            t_ids,
            s_ids,
            traditional_char=traditional,
            simplified_char=simplified,
            ambiguous_mapping=ambiguous and simplified != traditional,
        )
        # For unchanged same-char with no variants, still emit UNCHANGED
        if simplified == traditional and not related:
            diff = compare_ids_trees(
                hanzi.get(ch).decomposition if ch in hanzi else None,
                hanzi.get(ch).decomposition if ch in hanzi else None,
                traditional_char=ch,
                simplified_char=ch,
                ambiguous_mapping=False,
            )

        rows.append(
            {
                "inputCharacter": ch,
                "simplifiedCharacter": simplified,
                "traditionalCharacter": traditional,
                "classification": diff.classification,
                "explanation": diff.explanation,
                "changedComponentsJson": json.dumps(
                    [c.to_json_dict() for c in diff.changed_components],
                    ensure_ascii=False,
                ),
                "evidenceType": diff.evidence_type,
                "confidence": diff.confidence,
                "source": "derived_ids",
                "sourceRecordId": f"derived:{ch}",
            }
        )
    return rows


def write_database(
    dest: Path,
    *,
    hanzi: dict[str, HanziRecord],
    variants: list[VariantEdge],
    simplifications: list[dict[str, Any]],
    mnemonics: list[MnemonicRecord],
    greedy: dict[str, GreedyCompositionRecord],
    lock: dict[str, Any],
    source_paths_checksums: dict[str, str],
    room_identity_hash: str | None,
) -> dict[str, int]:
    dest.parent.mkdir(parents=True, exist_ok=True)
    if dest.exists():
        dest.unlink()

    conn = sqlite3.connect(dest)
    try:
        conn.executescript(DDL)
        conn.execute(f"PRAGMA user_version = {SCHEMA_VERSION}")
        cur = conn.cursor()

        cur.executemany(
            """
            INSERT INTO hanzi (
              character, codePoint, decomposition, etymologyType, etymologyHint,
              semanticComponent, phoneticComponent, primarySource, sourceRecordId
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            [
                (
                    r.character,
                    r.code_point,
                    r.decomposition,
                    r.etymology_type,
                    r.etymology_hint,
                    r.semantic_component,
                    r.phonetic_component,
                    r.primary_source,
                    r.source_record_id,
                )
                for r in hanzi.values()
            ],
        )

        cur.executemany(
            """
            INSERT INTO greedy_composition (
              character, componentsJson, isPhoneticSemantic, phonetic
            ) VALUES (?, ?, ?, ?)
            """,
            [
                (
                    rec.character,
                    json.dumps(rec.components, ensure_ascii=False),
                    1 if rec.is_phonetic_semantic else 0,
                    rec.phonetic,
                )
                for rec in greedy.values()
            ],
        )

        # Deduplicate variants by PK
        seen_v: set[tuple[str, str, str, str]] = set()
        vrows = []
        for i, v in enumerate(variants):
            key = (v.source, v.target, v.direction, v.source_name)
            if key in seen_v:
                continue
            seen_v.add(key)
            # First edge per source+direction from unihan is preferred
            is_preferred = 1 if v.source_name == "unihan" else 0
            vrows.append(
                (
                    v.source,
                    v.target,
                    v.direction,
                    None,
                    is_preferred,
                    1 if v.is_ambiguous else 0,
                    v.source_name,
                    v.source_record_id or f"{v.source_name}:{i}",
                )
            )
        cur.executemany(
            """
            INSERT INTO variant (
              sourceCharacter, targetCharacter, direction, localeOrStandard,
              isPreferred, isAmbiguous, source, sourceRecordId
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """,
            vrows,
        )

        cur.executemany(
            """
            INSERT INTO simplification (
              inputCharacter, simplifiedCharacter, traditionalCharacter,
              classification, explanation, changedComponentsJson,
              evidenceType, confidence, source, sourceRecordId
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            [
                (
                    s["inputCharacter"],
                    s["simplifiedCharacter"],
                    s["traditionalCharacter"],
                    s["classification"],
                    s["explanation"],
                    s.get("changedComponentsJson"),
                    s["evidenceType"],
                    float(s["confidence"]),
                    s["source"],
                    s["sourceRecordId"],
                )
                for s in simplifications
            ],
        )

        cur.executemany(
            """
            INSERT INTO mnemonic (
              character, story, language, rawScore, normalizedScore,
              sourcePriority, source, sourceRecordId, attribution, license, contentHash
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            [
                (
                    m.character,
                    m.story,
                    m.language,
                    m.raw_score,
                    m.normalized_score,
                    m.source_priority,
                    m.source,
                    m.source_record_id,
                    m.attribution,
                    m.license,
                    m.content_hash,
                )
                for m in mnemonics
            ],
        )

        counts = {
            "hanzi": len(hanzi),
            "greedy_composition": len(greedy),
            "variant": len(vrows),
            "simplification": len(simplifications),
            "mnemonic": len(mnemonics),
        }
        source_versions = {
            k: v.get("version") or v.get("path")
            for k, v in lock["sources"].items()
        }
        licenses = sorted(
            {
                v.get("license")
                for v in lock["sources"].values()
                if v.get("license")
            }
        )
        cur.execute(
            """
            INSERT INTO dataset_metadata (
              id, schemaVersion, datasetVersion, buildTimestamp,
              sourceVersionsJson, sourceChecksumsJson, recordCountsJson,
              licenseIdentifiersJson, roomIdentityHash
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            (
                1,
                SCHEMA_VERSION,
                lock.get("datasetVersion", "1.0.0"),
                datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
                json.dumps(source_versions, ensure_ascii=False),
                json.dumps(source_paths_checksums, ensure_ascii=False),
                json.dumps(counts, ensure_ascii=False),
                json.dumps(licenses, ensure_ascii=False),
                room_identity_hash,
            ),
        )

        if room_identity_hash:
            cur.execute(
                "INSERT INTO room_master_table (id, identity_hash) VALUES (42, ?)",
                (room_identity_hash,),
            )

        conn.commit()
        return counts
    finally:
        conn.close()
