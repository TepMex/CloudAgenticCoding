from __future__ import annotations

import csv
import html
import io
import os
import re
import tempfile
from collections import defaultdict
from dataclasses import dataclass, field
from pathlib import Path
from typing import Iterable, Protocol

from .pinyin import FINALS, INITIALS, VALID_CELLS, ParsedPinyin, canonical_cell, parse_first_syllable


class RelatedEnrichmentError(RuntimeError):
    pass


class RelatedRecord(Protocol):
    key: int
    hanzi: str
    pinyin: str
    same_hanzi: str
    matrix: str
    parsed: ParsedPinyin | None


@dataclass
class NoteRecord:
    document: "TsvDocument"
    source_row: int
    row: list[str]
    key: int
    hanzi: str
    pinyin: str
    same_hanzi: str = ""
    matrix: str = ""
    parsed: ParsedPinyin | None = None


@dataclass
class TsvDocument:
    path: Path
    metadata: list[str]
    rows: list[list[str]]
    header: bool
    key_index: int
    hanzi_index: int
    pinyin_index: int
    same_hanzi_index: int
    matrix_index: int
    records: list[NoteRecord] = field(default_factory=list)


@dataclass(frozen=True)
class FieldConfig:
    key: str = "Key"
    hanzi: str = "Simplified"
    pinyin: str = "Pinyin"
    same_hanzi: str = "SameHanzi"
    matrix: str = "SamePinyinMatrix"


@dataclass(frozen=True)
class ColumnConfig:
    key: int = 1
    hanzi: int = 2
    pinyin: int = 4
    same_hanzi: int | None = None
    matrix: int | None = None


@dataclass
class EnrichmentResult:
    processed: int = 0
    matrices_generated: int = 0
    skipped: int = 0
    warnings: list[str] = field(default_factory=list)
    debug: dict[str, object] | None = None


_TAG_RE = re.compile(r"<[^>]*>")


def _plain_text(value: str) -> str:
    return " ".join(html.unescape(_TAG_RE.sub(" ", value)).split())


def _hanzi_characters(value: str) -> frozenset[str]:
    result: set[str] = set()
    for char in _plain_text(value):
        codepoint = ord(char)
        if (
            0x3400 <= codepoint <= 0x4DBF
            or 0x4E00 <= codepoint <= 0x9FFF
            or 0x20000 <= codepoint <= 0x323AF
        ):
            result.add(char)
    return frozenset(result)


def _split_metadata(text: str) -> tuple[list[str], str]:
    lines = text.splitlines(keepends=True)
    index = 0
    while index < len(lines) and lines[index].startswith("#"):
        index += 1
    return lines[:index], "".join(lines[index:])


def _field_index(header: list[str], name: str, path: Path) -> int:
    try:
        return header.index(name)
    except ValueError as exc:
        raise RelatedEnrichmentError(f"{path}: missing TSV field {name!r}") from exc


def _column_index(value: int, label: str) -> int:
    if value < 1:
        raise RelatedEnrichmentError(f"--{label}-column must be positive")
    return value - 1


def load_tsv(
    path: Path,
    fields: FieldConfig = FieldConfig(),
    columns: ColumnConfig = ColumnConfig(),
) -> TsvDocument:
    try:
        text = path.read_text(encoding="utf-8-sig")
    except OSError as exc:
        raise RelatedEnrichmentError(f"cannot read {path}: {exc}") from exc
    metadata, payload = _split_metadata(text)
    try:
        rows = list(csv.reader(io.StringIO(payload), delimiter="\t", quotechar='"'))
    except csv.Error as exc:
        raise RelatedEnrichmentError(f"cannot parse TSV {path}: {exc}") from exc
    if not rows:
        raise RelatedEnrichmentError(f"{path}: no TSV rows")

    header = all(name in rows[0] for name in (fields.key, fields.hanzi, fields.pinyin))
    if header:
        names = rows[0]
        key_index = _field_index(names, fields.key, path)
        hanzi_index = _field_index(names, fields.hanzi, path)
        pinyin_index = _field_index(names, fields.pinyin, path)
        for target in (fields.same_hanzi, fields.matrix):
            if target not in names:
                names.append(target)
        same_hanzi_index = names.index(fields.same_hanzi)
        matrix_index = names.index(fields.matrix)
        data_rows = rows[1:]
        source_offset = 2
    else:
        key_index = _column_index(columns.key, "key")
        hanzi_index = _column_index(columns.hanzi, "hanzi")
        pinyin_index = _column_index(columns.pinyin, "pinyin")
        data_rows = rows
        source_offset = 1
        if (columns.same_hanzi is None) != (columns.matrix is None):
            raise RelatedEnrichmentError(
                "--same-hanzi-column and --matrix-column must be used together"
            )
        if columns.same_hanzi is not None and columns.matrix is not None:
            same_hanzi_index = _column_index(columns.same_hanzi, "same-hanzi")
            matrix_index = _column_index(columns.matrix, "matrix")
        elif any(len(row) >= 2 and row[-1].startswith('<div class="pm">') for row in data_rows):
            # Headerless output produced by this tool: overwrite the final two
            # computed columns on a repeated run instead of appending duplicates.
            widths = {len(row) for row in data_rows}
            if len(widths) != 1:
                raise RelatedEnrichmentError(
                    f"{path}: cannot infer computed columns from mixed row widths"
                )
            width = widths.pop()
            same_hanzi_index, matrix_index = width - 2, width - 1
        else:
            width = max(len(row) for row in data_rows)
            same_hanzi_index, matrix_index = width, width + 1

    required_index = max(
        key_index, hanzi_index, pinyin_index, same_hanzi_index, matrix_index
    )
    document = TsvDocument(
        path=path,
        metadata=metadata,
        rows=rows,
        header=header,
        key_index=key_index,
        hanzi_index=hanzi_index,
        pinyin_index=pinyin_index,
        same_hanzi_index=same_hanzi_index,
        matrix_index=matrix_index,
    )
    for logical_row, row in enumerate(data_rows, start=source_offset):
        if not row or all(not value for value in row):
            continue
        if len(row) <= max(key_index, hanzi_index, pinyin_index):
            raise RelatedEnrichmentError(
                f"{path}: row {logical_row} has too few columns"
            )
        row.extend([""] * (required_index + 1 - len(row)))
        raw_key = row[key_index].strip()
        if not raw_key:
            raise RelatedEnrichmentError(f"{path}: row {logical_row}: empty Key")
        try:
            key = int(raw_key)
        except ValueError as exc:
            raise RelatedEnrichmentError(
                f"{path}: row {logical_row}: non-numeric Key={raw_key!r}"
            ) from exc
        document.records.append(
            NoteRecord(
                document=document,
                source_row=logical_row,
                row=row,
                key=key,
                hanzi=row[hanzi_index],
                pinyin=row[pinyin_index],
                same_hanzi=row[same_hanzi_index],
                matrix=row[matrix_index],
            )
        )
    return document


def _cell_html(
    words: list[RelatedRecord],
    center: bool,
    current: RelatedRecord | None,
    valid: bool,
) -> str:
    classes = ["pm-cell"]
    if center:
        classes.append("pm-cell--center")
    if not valid or (not words and current is None):
        classes.append("pm-cell--empty")
    parts = [f'<div class="{" ".join(classes)}">']
    if valid and current is not None:
        parts.append(f'<div class="pm-current">{html.escape(current.hanzi)}</div>')
        assert current.parsed is not None
        parts.append(
            f'<div class="pm-current-pinyin">{html.escape(current.parsed.display)}</div>'
        )
    if valid and words:
        parts.append('<div class="pm-words">')
        parts.extend(
            f'<span class="pm-word">{html.escape(note.hanzi)}</span>' for note in words
        )
        parts.append("</div>")
    parts.append("</div>")
    return "".join(parts)


def render_matrix(
    current: RelatedRecord,
    buckets: dict[tuple[str, str], list[RelatedRecord]],
) -> tuple[str, list[dict[str, object]]]:
    if current.parsed is None:
        return "", []
    initial_index = INITIALS.index(current.parsed.initial)
    final_index = FINALS.index(current.parsed.layout_final)
    row_initials = [
        INITIALS[index] if 0 <= index < len(INITIALS) else None
        for index in range(initial_index - 1, initial_index + 2)
    ]
    column_finals = [
        FINALS[index] if 0 <= index < len(FINALS) else None
        for index in range(final_index - 1, final_index + 2)
    ]
    parts = ['<div class="pm"><div class="pm-grid">', '<div class="pm-axis-corner"></div>']
    parts.extend(
        f'<div class="pm-final">{html.escape(final or "")}</div>'
        for final in column_finals
    )
    debug_cells: list[dict[str, object]] = []
    for row_offset, initial in enumerate(row_initials):
        parts.append(
            f'<div class="pm-initial">{html.escape(initial if initial is not None else "") or ("∅" if initial == "" else "")}</div>'
        )
        for column_offset, layout_final in enumerate(column_finals):
            center = row_offset == 1 and column_offset == 1
            if initial is None or layout_final is None:
                cell = None
                valid = False
                words: list[RelatedRecord] = []
            else:
                cell = canonical_cell(initial, layout_final)
                valid = cell in VALID_CELLS
                words = buckets.get(cell, []) if valid else []
            parts.append(
                _cell_html(words, center, current if center else None, valid)
            )
            debug_cells.append(
                {
                    "initial": initial,
                    "final": layout_final,
                    "valid": valid,
                    "center": center,
                    "keys": [word.key for word in words],
                    "words": [word.hanzi for word in words],
                }
            )
    parts.append("</div></div>")
    return "".join(parts), debug_cells


def _record_location(note: RelatedRecord) -> str:
    if isinstance(note, NoteRecord):
        return f"{note.document.path}: row {note.source_row}"
    note_id = getattr(note, "note_id", None)
    return f"Anki note ID {note_id}" if note_id is not None else "unknown source"


def _validate_unique_keys(notes: Iterable[RelatedRecord]) -> list[RelatedRecord]:
    by_key: dict[int, RelatedRecord] = {}
    result = list(notes)
    for note in result:
        previous = by_key.get(note.key)
        if previous is not None:
            raise RelatedEnrichmentError(
                f"duplicate Key={note.key}\n"
                f"  {_record_location(previous)}\n"
                f"  {_record_location(note)}"
            )
        by_key[note.key] = note
    return sorted(result, key=lambda note: note.key)


def enrich_records(
    records: Iterable[RelatedRecord],
    debug_key: int | None = None,
) -> EnrichmentResult:
    notes = _validate_unique_keys(records)
    result = EnrichmentResult(processed=len(notes))
    pinyin_buckets: dict[tuple[str, str], list[RelatedRecord]] = defaultdict(list)
    hanzi_buckets: dict[str, list[RelatedRecord]] = defaultdict(list)

    for note in notes:
        characters = _hanzi_characters(note.hanzi)
        earlier_by_key = {
            previous.key: previous
            for character in characters
            for previous in hanzi_buckets[character]
        }
        note.same_hanzi = ", ".join(
            earlier_by_key[key].hanzi for key in sorted(earlier_by_key)
        )

        note.parsed = parse_first_syllable(note.pinyin)
        if note.parsed is None:
            result.skipped += 1
            result.warnings.append(
                f"WARNING Key={note.key}: cannot parse first syllable from Pinyin={note.pinyin!r}"
            )
            debug_cells: list[dict[str, object]] = []
        else:
            note.matrix, debug_cells = render_matrix(note, pinyin_buckets)
            result.matrices_generated += 1

        if debug_key == note.key:
            result.debug = {
                "key": note.key,
                "hanzi": note.hanzi,
                "pinyin": note.pinyin,
                "first": note.parsed.display if note.parsed else None,
                "plain": note.parsed.plain if note.parsed else None,
                "initial": note.parsed.initial if note.parsed else None,
                "final": note.parsed.final if note.parsed else None,
                "same_hanzi": note.same_hanzi,
                "cells": debug_cells,
                "html": note.matrix,
            }

        for character in characters:
            hanzi_buckets[character].append(note)
        if note.parsed is not None:
            pinyin_buckets[(note.parsed.initial, note.parsed.final)].append(note)

    if debug_key is not None and result.debug is None:
        raise RelatedEnrichmentError(f"Key={debug_key} not found")
    return result


def enrich_documents(
    documents: Iterable[TsvDocument],
    debug_key: int | None = None,
) -> EnrichmentResult:
    document_list = list(documents)
    result = enrich_records(
        (record for document in document_list for record in document.records),
        debug_key=debug_key,
    )
    for document in document_list:
        for note in document.records:
            note.row[document.same_hanzi_index] = note.same_hanzi
            note.row[document.matrix_index] = note.matrix
    return result


def output_path(source: Path, output_dir: Path) -> Path:
    return output_dir / f"{source.stem}.with-same-fields.tsv"


def write_tsv(document: TsvDocument, destination: Path) -> None:
    destination.parent.mkdir(parents=True, exist_ok=True)
    try:
        with tempfile.NamedTemporaryFile(
            "w", encoding="utf-8", newline="", dir=destination.parent,
            prefix=f".{destination.name}.", suffix=".tmp", delete=False,
        ) as handle:
            temporary = Path(handle.name)
            handle.writelines(document.metadata)
            writer = csv.writer(
                handle, delimiter="\t", quotechar='"', lineterminator="\n"
            )
            writer.writerows(document.rows)
        os.replace(temporary, destination)
    except OSError as exc:
        try:
            temporary.unlink(missing_ok=True)
        except UnboundLocalError:
            pass
        raise RelatedEnrichmentError(f"cannot write {destination}: {exc}") from exc
