from __future__ import annotations

import argparse
import csv
import json
from collections import Counter, defaultdict
from pathlib import Path
from typing import Iterable

from .download import SourceSpec, ensure_source, load_manifest
from .ranking import combine_scores, missing_zipf_floor, validate_weights
from .sources.kwjp import parse_kwjp
from .sources.models import LemmaFrequency, SourceResult, format_number
from .sources.subtlex import parse_subtlex


PROJECT_ROOT = Path(__file__).resolve().parents[1]
SUBTLEX = SourceSpec(
    key="subtlex_pl",
    source_page="https://osf.io/5a76z/",
    download_url="https://osf.io/download/ab9ym/",
    filename="subtlex-pl-lemmas-master.csv",
    expected_sha256="65a07a6d0a12aa22d63481d2a77ac0e5ac398b673f302d67c8da640ecf62e5b2",
    citation="Mandera, Keuleers, Wodniecka & Brysbaert (2015), doi:10.3758/s13428-014-0489-4",
)
KWJP = SourceSpec(
    key="kwjp",
    source_page="https://github.com/ipipan/kwjp100-varia/tree/main/freqlists",
    download_url=(
        "https://raw.githubusercontent.com/ipipan/kwjp100-varia/main/freqlists/"
        "kwjp100-slowa-lemma-all.csv.gz"
    ),
    filename="kwjp100-slowa-lemma-all.csv.gz",
    expected_sha256="5206e80669a054ad4d3c9d39b7643d13e75435afa3612a15d2847f39f42bb5fc",
    citation="Korpus Współczesnego Języka Polskiego (KWJP), balanced corpus 2011–2020",
)

POS_MAP = {
    "subst": "noun",
    "depr": "noun",
    "adj": "adjective",
    "adja": "adjective",
    "adjp": "adjective",
    "adv": "adverb",
    "num": "numeral",
    "tnum": "numeral",
    "ppron12": "pronoun",
    "ppron3": "pronoun",
    "siebie": "pronoun",
    "pron": "pronoun",
    "fin": "verb",
    "bedzie": "verb",
    "aglt": "verb",
    "praet": "verb",
    "impt": "verb",
    "imps": "verb",
    "inf": "verb",
    "pcon": "verb",
    "pant": "verb",
    "ger": "verb",
    "pact": "verb",
    "ppas": "verb",
    "winien": "verb",
    "verb": "verb",
    "pred": "predicative",
    "prep": "preposition",
    "conj": "conjunction",
    "comp": "conjunction",
    "qub": "particle",
    "part": "particle",
    "interj": "interjection",
    "interp": "punctuation",
}

OUTPUT_COLUMNS = [
    "rank",
    "combined_global_rank",
    "lemma",
    "combined_score",
    "combined_score_alt",
    "subtlex_rank",
    "subtlex_frequency",
    "subtlex_ipm",
    "subtlex_zipf",
    "subtlex_contextual_diversity",
    "subtlex_contextual_diversity_count",
    "kwjp_rank",
    "kwjp_frequency",
    "kwjp_ipm",
    "kwjp_zipf",
    "kwjp_arf",
    "kwjp_1dp",
    "in_subtlex",
    "in_kwjp",
    "pos_primary",
    "pos_all",
    "subtlex_pos_breakdown",
    "kwjp_pos_breakdown",
    "subtlex_original_lemmas",
    "kwjp_original_lemmas",
    "suspected_proper_name",
    "low_dispersion",
    "flags",
]


def _canonical_pos_scores(
    subtlex: LemmaFrequency | None,
    kwjp: LemmaFrequency | None,
    subtlex_weight: float,
    kwjp_weight: float,
) -> tuple[str, str]:
    scores: defaultdict[str, float] = defaultdict(float)
    for record, weight in ((subtlex, subtlex_weight), (kwjp, kwjp_weight)):
        if not record or record.frequency <= 0:
            continue
        for pos, frequency in record.pos_frequency.items():
            canonical = POS_MAP.get(pos, pos)
            scores[canonical] += weight * record.ipm * frequency / record.frequency
    ordered = sorted(scores, key=lambda pos: (-scores[pos], pos))
    return (ordered[0] if ordered else "", "|".join(ordered))


def _pos_breakdown(record: LemmaFrequency | None) -> str:
    if record is None:
        return ""
    return "|".join(
        f"{pos}:{format_number(frequency)}"
        for pos, frequency in sorted(
            record.pos_frequency.items(), key=lambda pair: (-pair[1], pair[0])
        )
    )


def _proper_name(
    subtlex: LemmaFrequency | None, kwjp: LemmaFrequency | None, pos_primary: str
) -> bool:
    if pos_primary != "noun":
        return False
    capitalized = sum(
        record.capitalized_frequency for record in (subtlex, kwjp) if record is not None
    )
    lowercase = sum(
        record.lowercase_frequency for record in (subtlex, kwjp) if record is not None
    )
    return capitalized > 0 and capitalized / (capitalized + lowercase) >= 0.90


def _low_dispersion(subtlex: LemmaFrequency | None, kwjp: LemmaFrequency | None) -> bool:
    subtlex_low = bool(
        subtlex
        and subtlex.frequency >= 1_000
        and subtlex.contextual_diversity is not None
        and subtlex.contextual_diversity < 0.01
    )
    kwjp_low = bool(kwjp and kwjp.ipm >= 1 and kwjp.one_dp is not None and kwjp.one_dp < 0.25)
    return subtlex_low or kwjp_low


def _candidate_row(
    *,
    global_rank: int,
    output_rank: int,
    lemma: str,
    combined_score: float,
    combined_score_alt: float,
    subtlex: LemmaFrequency | None,
    kwjp: LemmaFrequency | None,
    subtlex_weight: float,
    kwjp_weight: float,
) -> dict[str, object]:
    pos_primary, pos_all = _canonical_pos_scores(
        subtlex, kwjp, subtlex_weight, kwjp_weight
    )
    suspected_proper_name = _proper_name(subtlex, kwjp, pos_primary)
    low_dispersion = _low_dispersion(subtlex, kwjp)
    flags = set()
    for record in (subtlex, kwjp):
        if record:
            flags.update(record.flags)
    if suspected_proper_name:
        flags.add("suspected_proper_name")
    if low_dispersion:
        flags.add("low_dispersion")
    return {
        "rank": output_rank,
        "combined_global_rank": global_rank,
        "lemma": lemma,
        "combined_score": format_number(combined_score),
        "combined_score_alt": format_number(combined_score_alt),
        "subtlex_rank": subtlex.rank if subtlex else "",
        "subtlex_frequency": format_number(subtlex.frequency if subtlex else None),
        "subtlex_ipm": format_number(subtlex.ipm if subtlex else None),
        "subtlex_zipf": format_number(subtlex.zipf if subtlex else None),
        "subtlex_contextual_diversity": format_number(
            subtlex.contextual_diversity if subtlex else None
        ),
        "subtlex_contextual_diversity_count": (
            subtlex.contextual_diversity_count if subtlex else ""
        ),
        "kwjp_rank": kwjp.rank if kwjp else "",
        "kwjp_frequency": format_number(kwjp.frequency if kwjp else None),
        "kwjp_ipm": format_number(kwjp.ipm if kwjp else None),
        "kwjp_zipf": format_number(kwjp.zipf if kwjp else None),
        "kwjp_arf": format_number(kwjp.arf if kwjp else None),
        "kwjp_1dp": format_number(kwjp.one_dp if kwjp else None),
        "in_subtlex": str(subtlex is not None).lower(),
        "in_kwjp": str(kwjp is not None).lower(),
        "pos_primary": pos_primary,
        "pos_all": pos_all,
        "subtlex_pos_breakdown": _pos_breakdown(subtlex),
        "kwjp_pos_breakdown": _pos_breakdown(kwjp),
        "subtlex_original_lemmas": (
            "|".join(sorted(subtlex.originals)) if subtlex else ""
        ),
        "kwjp_original_lemmas": "|".join(sorted(kwjp.originals)) if kwjp else "",
        "suspected_proper_name": str(suspected_proper_name).lower(),
        "low_dispersion": str(low_dispersion).lower(),
        "flags": ";".join(sorted(flags)),
    }


def _write_rows(path: Path, rows: Iterable[dict[str, object]]) -> None:
    with path.open("w", encoding="utf-8", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=OUTPUT_COLUMNS)
        writer.writeheader()
        writer.writerows(rows)


def rank_union(
    subtlex: SourceResult,
    kwjp: SourceResult,
    *,
    subtlex_weight: float,
    kwjp_weight: float,
) -> tuple[list[tuple[float, float, str]], float, float]:
    """Return a deterministic, duplicate-free ranking of the source union."""

    subtlex_floor = missing_zipf_floor(record.ipm for record in subtlex.records.values())
    kwjp_floor = missing_zipf_floor(record.ipm for record in kwjp.records.values())
    ranked: list[tuple[float, float, str]] = []
    for lemma in subtlex.records.keys() | kwjp.records.keys():
        sub = subtlex.records.get(lemma)
        kw = kwjp.records.get(lemma)
        score = combine_scores(
            subtlex_zipf=sub.zipf if sub else None,
            kwjp_zipf=kw.zipf if kw else None,
            subtlex_rank=sub.rank if sub else None,
            kwjp_rank=kw.rank if kw else None,
            subtlex_population=len(subtlex.records),
            kwjp_population=len(kwjp.records),
            subtlex_missing_floor=subtlex_floor,
            kwjp_missing_floor=kwjp_floor,
            subtlex_weight=subtlex_weight,
            kwjp_weight=kwjp_weight,
        )
        ranked.append((score.zipf_weighted, score.percentile_weighted, lemma))
    ranked.sort(key=lambda item: (-item[0], -item[1], item[2]))
    return ranked, subtlex_floor, kwjp_floor


def build(
    *,
    top: int,
    subtlex_weight: float,
    kwjp_weight: float,
    force_download: bool,
    include_proper_names: bool,
    project_root: Path = PROJECT_ROOT,
) -> dict[str, object]:
    validate_weights(subtlex_weight, kwjp_weight)
    if top <= 0:
        raise ValueError("--top must be positive")

    raw_dir = project_root / "data" / "raw"
    processed_dir = project_root / "data" / "processed"
    output_dir = project_root / "output"
    for directory in (raw_dir, processed_dir, output_dir):
        directory.mkdir(parents=True, exist_ok=True)

    manifest_path = output_dir / "source_manifest.json"
    old_manifest = load_manifest(manifest_path)
    manifest: dict[str, dict[str, object]] = {}
    source_paths: dict[str, Path] = {}
    for source_spec in (SUBTLEX, KWJP):
        path, entry, _ = ensure_source(
            source_spec,
            raw_dir,
            force_download=force_download,
            previous_manifest=old_manifest.get(source_spec.key),
        )
        source_paths[source_spec.key] = path
        manifest[source_spec.key] = entry
    with manifest_path.open("w", encoding="utf-8") as stream:
        json.dump(manifest, stream, ensure_ascii=False, indent=2)
        stream.write("\n")

    subtlex = parse_subtlex(
        source_paths[SUBTLEX.key],
        processed_dir / "subtlex_lemma_pos.csv",
        processed_dir / "subtlex_filtered.csv",
    )
    kwjp = parse_kwjp(
        source_paths[KWJP.key],
        processed_dir / "kwjp_lemma_pos.csv",
        processed_dir / "kwjp_filtered.csv",
    )

    lemmas = subtlex.records.keys() | kwjp.records.keys()
    ranked, subtlex_floor, kwjp_floor = rank_union(
        subtlex,
        kwjp,
        subtlex_weight=subtlex_weight,
        kwjp_weight=kwjp_weight,
    )

    all_path = output_dir / "all_candidates.csv"
    top_path = output_dir / f"top{top}.csv"
    debug_path = output_dir / "top100_debug.csv"
    proper_name_count = 0
    proper_names_skipped = 0
    top_rows: list[dict[str, object]] = []
    debug_rows: list[dict[str, object]] = []
    with all_path.open("w", encoding="utf-8", newline="") as all_stream:
        all_writer = csv.DictWriter(all_stream, fieldnames=OUTPUT_COLUMNS)
        all_writer.writeheader()
        for global_rank, (score, score_alt, lemma) in enumerate(ranked, 1):
            row = _candidate_row(
                global_rank=global_rank,
                output_rank=global_rank,
                lemma=lemma,
                combined_score=score,
                combined_score_alt=score_alt,
                subtlex=subtlex.records.get(lemma),
                kwjp=kwjp.records.get(lemma),
                subtlex_weight=subtlex_weight,
                kwjp_weight=kwjp_weight,
            )
            all_writer.writerow(row)
            if global_rank <= 100:
                debug_rows.append(row.copy())
            is_proper = row["suspected_proper_name"] == "true"
            proper_name_count += int(is_proper)
            if is_proper and not include_proper_names:
                if len(top_rows) < top:
                    proper_names_skipped += 1
                continue
            if len(top_rows) < top:
                top_row = row.copy()
                top_row["rank"] = len(top_rows) + 1
                top_rows.append(top_row)

    if len(top_rows) != top:
        raise RuntimeError(f"only {len(top_rows)} candidates available for requested top {top}")
    _write_rows(top_path, top_rows)
    _write_rows(debug_path, debug_rows)

    smoke_words = {"i", "w", "z", "na", "do", "nie", "się", "że", "to", "jak", "co", "ale", "czy", "już"}
    top_lemmas = {str(row["lemma"]) for row in top_rows}
    smoke_present = sorted(smoke_words & top_lemmas)
    smoke_missing = sorted(smoke_words - top_lemmas)
    if len(smoke_present) < 10:
        raise RuntimeError(
            f"function-word sanity check failed: only {len(smoke_present)} of {len(smoke_words)} present"
        )
    if len(top_lemmas) != len(top_rows):
        raise RuntimeError("duplicate lemma in final output")

    both = len(subtlex.records.keys() & kwjp.records.keys())
    stats: dict[str, object] = {
        "parameters": {
            "top": top,
            "subtlex_weight": subtlex_weight,
            "kwjp_weight": kwjp_weight,
            "include_proper_names": include_proper_names,
        },
        "scoring": {
            "default": "weighted Zipf/IPM",
            "alternative": "weighted percentile-rank fusion",
            "subtlex_missing_zipf_floor": subtlex_floor,
            "kwjp_missing_zipf_floor": kwjp_floor,
        },
        "subtlex": _source_stats(subtlex),
        "kwjp": _source_stats(kwjp),
        "union": {
            "unique_lemmas": len(lemmas),
            "in_both": both,
            "subtlex_only": len(subtlex.records) - both,
            "kwjp_only": len(kwjp.records) - both,
        },
        "filtered": {
            "technical_subtlex": dict(sorted(subtlex.rejected.items())),
            "technical_kwjp": dict(sorted(kwjp.rejected.items())),
            "suspected_proper_names_flagged": proper_name_count,
            "suspected_proper_names_skipped_before_top_filled": proper_names_skipped,
        },
        "sanity": {
            "function_words_present": smoke_present,
            "function_words_missing": smoke_missing,
            "duplicate_final_lemmas": 0,
        },
        "output": {
            "rows": len(top_rows),
            "top_file": top_path.name,
            "all_candidates_rows": len(ranked),
        },
    }
    with (output_dir / "stats.json").open("w", encoding="utf-8") as stream:
        json.dump(stats, stream, ensure_ascii=False, indent=2)
        stream.write("\n")
    _print_summary(stats)
    return stats


def _source_stats(source: SourceResult) -> dict[str, object]:
    result: dict[str, object] = {
        "rows_loaded": source.rows_loaded,
        "lemma_pos_rows": source.lemma_pos_rows,
        "unique_lemmas": len(source.records),
        "schema": source.schema,
    }
    if source.unique_surface_forms is not None:
        result["unique_surface_forms"] = source.unique_surface_forms
    if source.corpus_tokens is not None:
        result["corpus_tokens"] = source.corpus_tokens
    return result


def _print_summary(stats: dict[str, object]) -> None:
    sub = stats["subtlex"]
    kw = stats["kwjp"]
    union = stats["union"]
    filtered = stats["filtered"]
    output = stats["output"]
    assert isinstance(sub, dict) and isinstance(kw, dict)
    assert isinstance(union, dict) and isinstance(filtered, dict) and isinstance(output, dict)
    print("SUBTLEX:")
    print(f"  rows loaded: {sub['rows_loaded']}")
    print(f"  unique surface forms: {sub.get('unique_surface_forms', 'n/a')}")
    print(f"  unique lemmas: {sub['unique_lemmas']}")
    print("KWJP:")
    print(f"  rows loaded: {kw['rows_loaded']}")
    print(f"  unique lemma+POS: {kw['lemma_pos_rows']}")
    print(f"  unique lemmas: {kw['unique_lemmas']}")
    print("Union:")
    print(f"  unique lemmas: {union['unique_lemmas']}")
    print(f"  in both sources: {union['in_both']}")
    print(f"  SUBTLEX only: {union['subtlex_only']}")
    print(f"  KWJP only: {union['kwjp_only']}")
    print("Filtered:")
    print(f"  technical SUBTLEX: {sum(filtered['technical_subtlex'].values())}")
    print(f"  technical KWJP: {sum(filtered['technical_kwjp'].values())}")
    print(f"  proper names skipped: {filtered['suspected_proper_names_skipped_before_top_filled']}")
    print("Output:")
    print(f"  {output['rows']} lemmas")


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--top", type=int, default=3000)
    parser.add_argument("--subtlex-weight", type=float, default=0.65)
    parser.add_argument("--kwjp-weight", type=float, default=0.35)
    parser.add_argument("--force-download", action="store_true")
    parser.add_argument(
        "--include-proper-names",
        action="store_true",
        help="keep capitalized noun candidates in the final top list (they remain in all_candidates either way)",
    )
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> None:
    args = parse_args(argv)
    build(
        top=args.top,
        subtlex_weight=args.subtlex_weight,
        kwjp_weight=args.kwjp_weight,
        force_download=args.force_download,
        include_proper_names=args.include_proper_names,
    )


if __name__ == "__main__":
    main()
