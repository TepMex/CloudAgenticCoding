"""Parse OpenCC character dictionaries as supplemental variant mappings."""

from __future__ import annotations

from pathlib import Path

from .unihan import VariantEdge


def _parse_opencc_file(path: Path, direction: str) -> list[VariantEdge]:
    edges: list[VariantEdge] = []
    for line_no, line in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        parts = line.split("\t")
        if len(parts) < 2:
            continue
        src = parts[0]
        targets = parts[1].split()
        amb = len(targets) > 1
        for i, tgt in enumerate(targets):
            edges.append(
                VariantEdge(
                    source=src,
                    target=tgt,
                    direction=direction,
                    is_ambiguous=amb,
                    source_name="opencc",
                    source_record_id=f"{path.name}:{line_no}:{i}",
                )
            )
    return edges


def parse_opencc_variants(st_path: Path, ts_path: Path) -> list[VariantEdge]:
    return _parse_opencc_file(st_path, "s2t") + _parse_opencc_file(ts_path, "t2s")


def merge_variants(
    primary: list[VariantEdge],
    supplemental: list[VariantEdge],
) -> list[VariantEdge]:
    """Prefer Unihan edges; add OpenCC edges only when Unihan has no mapping for that source+direction."""
    by_src_dir: dict[tuple[str, str], list[VariantEdge]] = {}
    for e in primary:
        by_src_dir.setdefault((e.source, e.direction), []).append(e)

    merged = list(primary)
    for e in supplemental:
        key = (e.source, e.direction)
        if key in by_src_dir:
            # Supplemental may add targets Unihan missed for the same source.
            existing_targets = {x.target for x in by_src_dir[key]}
            if e.target in existing_targets:
                continue
            # Record as additional ambiguous target when Unihan already has mapping(s).
            amb_edge = VariantEdge(
                source=e.source,
                target=e.target,
                direction=e.direction,
                is_ambiguous=True,
                source_name=e.source_name,
                source_record_id=e.source_record_id,
            )
            by_src_dir[key].append(amb_edge)
            # Mark prior edges ambiguous too.
            for prev in by_src_dir[key]:
                if not prev.is_ambiguous:
                    # replace in merged
                    try:
                        idx = merged.index(prev)
                        merged[idx] = VariantEdge(
                            source=prev.source,
                            target=prev.target,
                            direction=prev.direction,
                            is_ambiguous=True,
                            source_name=prev.source_name,
                            source_record_id=prev.source_record_id,
                        )
                        by_src_dir[key][by_src_dir[key].index(prev)] = merged[idx]
                    except ValueError:
                        pass
            merged.append(amb_edge)
        else:
            by_src_dir[key] = [e]
            merged.append(e)
    return merged
