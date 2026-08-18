from __future__ import annotations

import argparse
import json
import logging
import sys
from pathlib import Path

from anki_enricher.anki import AnkiConnect, AnkiConnectError
from anki_enricher.anki_related import run_anki_related_pipeline
from anki_enricher.config import ConfigError, load_config, load_dotenv
from anki_enricher.pinyin import dump_layout, parse_first_syllable
from anki_enricher.related import RelatedEnrichmentError


def parser() -> argparse.ArgumentParser:
    result = argparse.ArgumentParser(
        description=(
            "Enrich existing Anki notes with SameHanzi and SamePinyinMatrix "
            "through AnkiConnect"
        )
    )
    result.add_argument("--config", type=Path, default=Path("config.yaml"))
    mode = result.add_mutually_exclusive_group()
    mode.add_argument("--audit", action="store_true", help="Inspect without writes")
    mode.add_argument("--dry-run", action="store_true", help="Preview without writes")
    mode.add_argument("--enrich", action="store_true", help="Update existing notes")
    result.add_argument("--query", help="Override the target Anki search query")
    result.add_argument(
        "--history-query",
        help="Override the search containing the complete global Key history",
    )
    result.add_argument("--limit", type=int, help="Maximum target notes to update")
    result.add_argument("--debug-key", type=int)
    result.add_argument("--dump-layout", action="store_true")
    result.add_argument("--parse", metavar="PINYIN")
    result.add_argument(
        "--backup-confirmed",
        action="store_true",
        help="Confirm a full scheduled collection backup exists",
    )
    result.add_argument("--backup-deck", help="Export this deck before writing")
    result.add_argument(
        "--backup-path",
        help="Path on the Anki host for an .apkg backup (requires --backup-deck)",
    )
    return result


def _print_debug(value: dict[str, object]) -> None:
    print("\nDebug:")
    print(f"Key:        {value['key']}")
    print(f"Hanzi:      {value['hanzi']}")
    print(f"Pinyin:     {value['pinyin']}")
    print(f"First:      {value['first']}")
    print(f"WithoutTone:{value['plain']}")
    print(f"Coordinate: {value['initial'] or '∅'} | {value['final']}")
    print(f"SameHanzi:  {value['same_hanzi']}")
    print("Generated HTML:")
    print(value["html"])


def _create_backup(anki: AnkiConnect, args: argparse.Namespace) -> bool:
    if bool(args.backup_deck) != bool(args.backup_path):
        raise RelatedEnrichmentError(
            "--backup-deck and --backup-path must be used together"
        )
    if args.backup_deck and args.backup_path:
        if not anki.export_package(args.backup_deck, args.backup_path):
            raise RelatedEnrichmentError(
                "AnkiConnect did not create the requested backup"
            )
        return True
    return False


def main(argv: list[str] | None = None) -> int:
    args = parser().parse_args(argv)
    try:
        if args.parse is not None:
            parsed = parse_first_syllable(args.parse)
            if parsed is None:
                raise RelatedEnrichmentError(f"cannot parse Pinyin={args.parse!r}")
            print(f"input:       {args.parse}")
            print(f"first:       {parsed.display}")
            print(f"withoutTone: {parsed.plain}")
            print(f"initial:     {parsed.initial or '∅'}")
            print(f"final:       {parsed.final}")
            return 0
        if args.dump_layout:
            print(dump_layout())
            return 0
        if args.limit is not None and args.limit < 1:
            raise RelatedEnrichmentError("--limit must be positive")

        load_dotenv(args.config.resolve().parent / ".env")
        config = load_config(args.config)
        target_query = args.query if args.query is not None else config.anki.query
        history_query = (
            args.history_query
            if args.history_query is not None
            else config.related.history_query or target_query
        )
        anki = AnkiConnect(config.anki.url)
        version = anki.check()
        if version < 6:
            raise AnkiConnectError(
                f"AnkiConnect API version {version}; version 6 required"
            )

        audit_mode = args.audit or (not args.dry_run and not args.enrich)
        if args.enrich:
            backup_created = _create_backup(anki, args)
            if not (args.backup_confirmed or backup_created):
                raise RelatedEnrichmentError(
                    "Write blocked: first export a full Anki collection backup "
                    "with scheduling, then pass --backup-confirmed (or use "
                    "--backup-deck and --backup-path)."
                )
            config.processing.log_dir.mkdir(parents=True, exist_ok=True)
            logging.basicConfig(
                level=logging.INFO,
                format="%(asctime)s %(levelname)s %(message)s",
                handlers=[
                    logging.StreamHandler(),
                    logging.FileHandler(
                        config.processing.log_dir / "run.log", encoding="utf-8"
                    ),
                ],
            )

        limit = args.limit
        if audit_mode and limit is None:
            limit = config.anki.sample_size
        result = run_anki_related_pipeline(
            anki=anki,
            related=config.related,
            target_query=target_query,
            history_query=history_query,
            limit=limit,
            dry_run=not args.enrich,
            verify_scheduling=config.processing.verify_scheduling,
            log_dir=config.processing.log_dir if args.enrich else None,
            debug_key=args.debug_key,
        )
        for warning in result.warnings:
            print(warning, file=sys.stderr)
        print(
            json.dumps(
                {
                    "mode": "enrich" if args.enrich else "audit" if audit_mode else "dry-run",
                    "target_query": target_query,
                    "history_query": history_query,
                    "missing_fields_to_add": result.missing_fields,
                    "summary": vars(result.counters),
                    "preview": result.preview,
                },
                ensure_ascii=False,
                indent=2,
                sort_keys=True,
            )
        )
        if result.debug is not None:
            _print_debug(result.debug)
        return 1 if result.counters.errors else 0
    except (ConfigError, AnkiConnectError, RelatedEnrichmentError) as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
