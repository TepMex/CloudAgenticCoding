from __future__ import annotations

import csv
from collections import Counter
from pathlib import Path

from ..normalize import is_capitalized_lemma, normalize_lemma
from .models import LemmaFrequency, SourceResult, format_number


EXPECTED_COLUMNS = ["lemma", "pos", "spelling", "freq", "cd.count", "cd"]


def parse_subtlex(
    raw_path: Path,
    lemma_pos_output: Path,
    filtered_output: Path,
) -> SourceResult:
    records: dict[str, LemmaFrequency] = {}
    rejected: Counter[str] = Counter()
    surface_forms: set[str] = set()
    rows_loaded = 0
    lemma_pos_rows = 0
    corpus_tokens = 0

    lemma_pos_output.parent.mkdir(parents=True, exist_ok=True)
    # First pass: validate the hierarchical layout, count word forms, and find
    # the exact denominator for IPM from the author's lemma+POS totals.
    with raw_path.open("r", encoding="utf-8", newline="") as source:
        reader = csv.DictReader(source, delimiter="\t")
        if reader.fieldnames != EXPECTED_COLUMNS:
            raise ValueError(
                f"unexpected SUBTLEX schema: {reader.fieldnames!r}; expected {EXPECTED_COLUMNS!r}"
            )
        for source_row, row in enumerate(reader, 1):
            rows_loaded += 1
            if row["lemma"] == "@" and row["pos"] == "@":
                surface_forms.add(row["spelling"])
                continue
            if row["spelling"] != "%":
                raise ValueError(f"unexpected SUBTLEX hierarchy at data row {source_row}")
            lemma_pos_rows += 1
            corpus_tokens += int(row["freq"])
    unique_surface_forms = len(surface_forms)
    del surface_forms

    with (
        raw_path.open("r", encoding="utf-8", newline="") as source,
        lemma_pos_output.open("w", encoding="utf-8", newline="") as processed,
        filtered_output.open("w", encoding="utf-8", newline="") as filtered,
    ):
        reader = csv.DictReader(source, delimiter="\t")
        if reader.fieldnames != EXPECTED_COLUMNS:
            raise ValueError(
                f"unexpected SUBTLEX schema: {reader.fieldnames!r}; expected {EXPECTED_COLUMNS!r}"
            )
        processed_writer = csv.DictWriter(
            processed,
            fieldnames=[
                "source_row",
                "lemma_original",
                "lemma",
                "pos",
                "frequency",
                "ipm",
                "zipf",
                "contextual_diversity_count",
                "contextual_diversity",
                "flags",
            ],
        )
        processed_writer.writeheader()
        filtered_writer = csv.DictWriter(
            filtered, fieldnames=["source_row", "lemma_original", "pos", "reason"]
        )
        filtered_writer.writeheader()

        current_record: LemmaFrequency | None = None
        for source_row, row in enumerate(reader, 1):
            raw_lemma = row["lemma"]
            pos = row["pos"]
            spelling = row["spelling"]
            if raw_lemma == "@" and pos == "@":
                if current_record is not None:
                    form_frequency = int(row["freq"])
                    if is_capitalized_lemma(spelling):
                        current_record.capitalized_frequency += form_frequency
                    else:
                        current_record.lowercase_frequency += form_frequency
                continue
            current_record = None
            frequency = int(row["freq"])
            cd_count = int(row["cd.count"])
            cd = float(row["cd"])
            normalized = normalize_lemma(raw_lemma)
            if normalized.lemma is None:
                reason = normalized.rejection_reason or "invalid"
                rejected[reason] += 1
                filtered_writer.writerow(
                    {
                        "source_row": source_row,
                        "lemma_original": raw_lemma,
                        "pos": pos,
                        "reason": reason,
                    }
                )
                continue
            # The official lemma frequencies partition the corpus.  We derive
            # IPM from that complete total and never add child word-form rows.
            lemma = normalized.lemma
            flags = normalized.flags
            ipm = frequency * 1_000_000 / corpus_tokens
            from ..ranking import zipf_from_ipm

            zipf = zipf_from_ipm(ipm)
            processed_writer.writerow(
                {
                    "source_row": source_row,
                    "lemma_original": raw_lemma,
                    "lemma": lemma,
                    "pos": pos,
                    "frequency": frequency,
                    "ipm": format_number(ipm),
                    "zipf": format_number(zipf),
                    "contextual_diversity_count": cd_count,
                    "contextual_diversity": format_number(cd),
                    "flags": ";".join(flags),
                }
            )
            record = records.setdefault(lemma, LemmaFrequency(lemma))
            current_record = record
            record.originals.add(raw_lemma)
            record.frequency += frequency
            record.ipm += ipm
            record.pos_frequency[pos] += frequency
            record.flags.update(flags)
            record.contextual_diversity_count = max(
                record.contextual_diversity_count or 0, cd_count
            )
            record.contextual_diversity = max(record.contextual_diversity or 0.0, cd)

    result = SourceResult(
        name="subtlex_pl",
        records=records,
        rows_loaded=rows_loaded,
        lemma_pos_rows=lemma_pos_rows,
        unique_surface_forms=unique_surface_forms,
        corpus_tokens=corpus_tokens,
        rejected=rejected,
        schema=EXPECTED_COLUMNS,
    )
    result.assign_ranks()
    return result
