from __future__ import annotations

import csv
import gzip
from collections import Counter
from pathlib import Path

from ..normalize import is_capitalized_lemma, normalize_lemma
from .models import LemmaFrequency, SourceResult


EXPECTED_COLUMNS = ["", "", "freq", "ipm", "ARF", "DP", "DP_norm", "1-DP", "total_freq"]


def parse_kwjp(
    raw_path: Path,
    lemma_pos_output: Path,
    filtered_output: Path,
) -> SourceResult:
    records: dict[str, LemmaFrequency] = {}
    rejected: Counter[str] = Counter()
    rows_loaded = 0

    lemma_pos_output.parent.mkdir(parents=True, exist_ok=True)
    with (
        gzip.open(raw_path, "rt", encoding="utf-8-sig", newline="") as source,
        lemma_pos_output.open("w", encoding="utf-8", newline="") as processed,
        filtered_output.open("w", encoding="utf-8", newline="") as filtered,
    ):
        reader = csv.reader(source)
        header = next(reader)
        if header != EXPECTED_COLUMNS:
            raise ValueError(f"unexpected KWJP schema: {header!r}; expected {EXPECTED_COLUMNS!r}")
        processed_writer = csv.writer(processed)
        processed_writer.writerow(
            [
                "source_row_rank",
                "lemma_original",
                "lemma",
                "pos",
                "frequency",
                "ipm",
                "arf",
                "dp",
                "dp_norm",
                "one_dp",
                "total_freq",
                "flags",
            ]
        )
        filtered_writer = csv.writer(filtered)
        filtered_writer.writerow(["source_row_rank", "lemma_original", "pos", "reason"])

        for source_row_rank, row in enumerate(reader, 1):
            rows_loaded += 1
            if len(row) != len(EXPECTED_COLUMNS):
                raise ValueError(f"unexpected KWJP row width at data row {source_row_rank}")
            raw_lemma, pos = row[0], row[1]
            normalized = normalize_lemma(raw_lemma)
            if normalized.lemma is None:
                reason = normalized.rejection_reason or "invalid"
                rejected[reason] += 1
                filtered_writer.writerow([source_row_rank, raw_lemma, pos, reason])
                continue

            frequency = float(row[2])
            ipm = float(row[3])
            arf = float(row[4])
            one_dp = float(row[7])
            lemma = normalized.lemma
            processed_writer.writerow(
                [source_row_rank, raw_lemma, lemma, pos, *row[2:], ";".join(normalized.flags)]
            )

            record = records.setdefault(lemma, LemmaFrequency(lemma))
            record.originals.add(raw_lemma)
            record.frequency += frequency
            record.ipm += ipm
            record.pos_frequency[pos] += frequency
            record.flags.update(normalized.flags)
            record.arf = (record.arf or 0.0) + arf
            record.one_dp_numerator += frequency * one_dp
            if is_capitalized_lemma(raw_lemma):
                record.capitalized_frequency += frequency
            else:
                record.lowercase_frequency += frequency

    result = SourceResult(
        name="kwjp",
        records=records,
        rows_loaded=rows_loaded,
        lemma_pos_rows=rows_loaded,
        unique_surface_forms=None,
        corpus_tokens=None,
        rejected=rejected,
        schema=["lemma", "POS", *EXPECTED_COLUMNS[2:]],
    )
    result.assign_ranks()
    return result
