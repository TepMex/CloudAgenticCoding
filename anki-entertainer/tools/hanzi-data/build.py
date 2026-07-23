#!/usr/bin/env python3
"""Build the offline Hanzi metadata SQLite database for anki-entertainer.

Usage:
  python tools/hanzi-data/build.py
  python tools/hanzi-data/build.py --offline
  python tools/hanzi-data/build.py --identity-hash <room_hash>
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from hanzi_data.db_writer import build_simplifications, write_database  # noqa: E402
from hanzi_data.download import ensure_sources, load_lock, sha256_file  # noqa: E402
from hanzi_data.mmah import parse_mmah_dictionary  # noqa: E402
from hanzi_data.mnemonics import (  # noqa: E402
    JsonMnemonicProvider,
    MmahMnemonicProvider,
    import_mnemonics,
)
from hanzi_data.opencc import merge_variants, parse_opencc_variants  # noqa: E402
from hanzi_data.unihan import parse_unihan_variants  # noqa: E402


def find_room_identity_hash(project_root: Path) -> str | None:
    schema_dir = project_root / "app" / "schemas"
    if not schema_dir.exists():
        return None
    candidates = sorted(schema_dir.rglob("1.json"))
    for path in candidates:
        try:
            data = json.loads(path.read_text(encoding="utf-8"))
            h = data.get("identityHash") or data.get("database", {}).get("identityHash")
            if h:
                return h
        except (OSError, json.JSONDecodeError):
            continue
    return None


def write_notices(project_root: Path, lock: dict) -> None:
    lines = [
        "# Third-party notices — anki-entertainer Hanzi metadata",
        "",
        "This file documents third-party datasets bundled in the Hanzi metadata",
        "SQLite database shipped with the anki-entertainer APK.",
        "",
    ]
    for key, meta in lock["sources"].items():
        lines.append(f"## {meta.get('name', key)}")
        lines.append("")
        lines.append(f"- Lock key: `{key}`")
        if meta.get("version"):
            lines.append(f"- Version / revision: `{meta['version']}`")
        if meta.get("url"):
            lines.append(f"- URL: {meta['url']}")
        if meta.get("path"):
            lines.append(f"- Path: `{meta['path']}`")
        lines.append(f"- License: {meta.get('license', 'see source')}")
        if meta.get("licenseUrl"):
            lines.append(f"- License URL: {meta['licenseUrl']}")
        if meta.get("sha256"):
            lines.append(f"- SHA-256: `{meta['sha256']}`")
        if meta.get("usedFor"):
            lines.append(f"- Used for: {', '.join(meta['usedFor'])}")
        if meta.get("note"):
            lines.append(f"- Note: {meta['note']}")
        lines.append("")
    lines.extend(
        [
            "## Make Me a Hanzi LGPL notice",
            "",
            "`dictionary.txt` from Make Me a Hanzi is licensed under LGPL-3.0-or-later.",
            "A copy of the upstream COPYING notice is retained under",
            "`tools/hanzi-data/cache/mmah-COPYING` when sources are downloaded.",
            "Modifications consist of importing selected fields into a normalized SQLite schema.",
            "",
            "## Unicode notice",
            "",
            "Unicode Data Files include Unihan.zip fields used for simplified/traditional variants.",
            "Copyright © Unicode, Inc. See https://www.unicode.org/license.txt",
            "",
        ]
    )
    notice = "\n".join(lines) + "\n"
    (project_root / "THIRD_PARTY_NOTICES.md").write_text(notice, encoding="utf-8")
    packaged_notice = (
        project_root
        / "app"
        / "src"
        / "main"
        / "assets"
        / "licenses"
        / "HANZI_DATA_NOTICES.md"
    )
    packaged_notice.parent.mkdir(parents=True, exist_ok=True)
    packaged_notice.write_text(notice, encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--offline",
        action="store_true",
        help="Do not download; require cached sources with matching checksums",
    )
    parser.add_argument(
        "--identity-hash",
        default=None,
        help="Room identity hash to embed in room_master_table",
    )
    parser.add_argument(
        "--project-root",
        type=Path,
        default=ROOT.parent.parent,
        help="anki-entertainer project root",
    )
    args = parser.parse_args()
    project_root: Path = args.project_root.resolve()
    lock_path = ROOT / "sources.lock.json"
    cache_dir = ROOT / "cache"
    out_dir = ROOT / "out"
    assets_db = project_root / "app" / "src" / "main" / "assets" / "databases" / "hanzi_metadata.db"

    lock = load_lock(lock_path)
    paths = ensure_sources(lock, cache_dir, offline=args.offline)

    print("Parsing Make Me a Hanzi dictionary…")
    hanzi = parse_mmah_dictionary(paths["makemeahanzi_dictionary"])

    print("Parsing Unihan variants…")
    unihan_edges = parse_unihan_variants(paths["unihan"])

    print("Parsing OpenCC character dictionaries…")
    opencc_edges = parse_opencc_variants(paths["opencc_st"], paths["opencc_ts"])
    variants = merge_variants(unihan_edges, opencc_edges)

    print("Importing mnemonics…")
    seed_path = ROOT / "hanzi_data" / "seed" / "mnemonics.json"
    mnemonics = import_mnemonics(
        [
            JsonMnemonicProvider(
                path=seed_path,
                name="project_seed",
                source_priority=100,
            ),
            MmahMnemonicProvider(
                records=hanzi,
                source_priority=10,
            ),
        ]
    )

    curated_path = ROOT / "hanzi_data" / "seed" / "curated_simplifications.json"
    curated = json.loads(curated_path.read_text(encoding="utf-8"))

    print("Building simplification explanations…")
    simplifications = build_simplifications(hanzi, variants, curated)

    identity = args.identity_hash or find_room_identity_hash(project_root)
    if identity:
        print(f"Embedding Room identity hash: {identity}")
    else:
        print(
            "Warning: Room identity hash not found. "
            "Re-run after `./gradlew :app:kspDebugKotlin` exports schemas."
        )

    checksums = {k: sha256_file(p) for k, p in paths.items()}
    checksums["project_seed_mnemonics"] = sha256_file(seed_path)
    checksums["project_curated_simplifications"] = sha256_file(curated_path)

    out_db = out_dir / "hanzi_metadata.db"
    counts = write_database(
        out_db,
        hanzi=hanzi,
        variants=variants,
        simplifications=simplifications,
        mnemonics=mnemonics,
        lock=lock,
        source_paths_checksums=checksums,
        room_identity_hash=identity,
    )

    assets_db.parent.mkdir(parents=True, exist_ok=True)
    assets_db.write_bytes(out_db.read_bytes())

    report = {
        "datasetVersion": lock.get("datasetVersion"),
        "schemaVersion": 1,
        "roomIdentityHash": identity,
        "recordCounts": counts,
        "databaseBytes": out_db.stat().st_size,
        "assetPath": str(assets_db.relative_to(project_root)),
        "sourceChecksums": checksums,
        "mnemonicCoverageNote": (
            "Project-authored CC0 stories are supplemented by deterministic local "
            "memory cues derived from Make Me a Hanzi structure fields. "
            "This is not a ranked community mnemonic corpus."
        ),
    }
    report_path = out_dir / "build-report.json"
    report_path.write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    write_notices(project_root, lock)

    print(json.dumps(report, indent=2, ensure_ascii=False))
    print(f"Wrote {assets_db}")
    print(f"Wrote {report_path}")
    print(f"Wrote {project_root / 'THIRD_PARTY_NOTICES.md'}")
    print(
        "Wrote "
        f"{project_root / 'app/src/main/assets/licenses/HANZI_DATA_NOTICES.md'}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
