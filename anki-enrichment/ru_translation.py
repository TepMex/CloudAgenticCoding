from __future__ import annotations

import argparse
import json
import logging
import os
import sys
from pathlib import Path

from anki_enricher.anki import AnkiConnect, AnkiConnectError
from anki_enricher.audit import audit_collection
from anki_enricher.cache import JsonCache
from anki_enricher.config import ConfigError, load_config, load_dotenv
from anki_enricher.enrichment import run_pipeline
from anki_enricher.translation import OpenAICompatibleTranslator


def parser() -> argparse.ArgumentParser:
    result = argparse.ArgumentParser(
        description="Safely enrich existing Anki notes through AnkiConnect"
    )
    result.add_argument("--config", type=Path, default=Path("config.yaml"))
    mode = result.add_mutually_exclusive_group()
    mode.add_argument("--audit", action="store_true", help="Inspect collection schema")
    mode.add_argument("--dry-run", action="store_true", help="Preview without writes/LLM")
    mode.add_argument("--enrich", action="store_true", help="Update existing notes")
    result.add_argument("--query", help="Override the configured Anki search query")
    result.add_argument("--limit", type=int, help="Maximum number of notes")
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


def print_json(value: object) -> None:
    print(json.dumps(value, ensure_ascii=False, indent=2, sort_keys=True))


def main(argv: list[str] | None = None) -> int:
    args = parser().parse_args(argv)
    if args.limit is not None and args.limit < 1:
        print("--limit must be positive", file=sys.stderr)
        return 2
    load_dotenv(args.config.resolve().parent / ".env")
    try:
        config = load_config(args.config)
        query = args.query if args.query is not None else config.anki.query
        anki = AnkiConnect(config.anki.url)
        version = anki.check()
        if version < 6:
            raise AnkiConnectError(f"AnkiConnect API version {version}; version 6 required")

        if args.audit or (not args.dry_run and not args.enrich):
            print_json(
                audit_collection(anki, query, config.anki.sample_size, config.fields)
            )
            return 0

        if args.dry_run:
            counters, preview, missing = run_pipeline(
                anki, None, config, query, args.limit, dry_run=True
            )
            print_json(
                {
                    "mode": "dry-run",
                    "query": query,
                    "missing_fields_to_add": missing,
                    "notes_requiring_llm": counters.eligible,
                    "estimated_llm_calls": counters.eligible,
                    "preview": preview,
                    "writes": 0,
                }
            )
            return 0

        backup_created = False
        if bool(args.backup_deck) != bool(args.backup_path):
            print("--backup-deck and --backup-path must be used together", file=sys.stderr)
            return 2
        if args.backup_deck and args.backup_path:
            backup_created = anki.export_package(args.backup_deck, args.backup_path)
            if not backup_created:
                print("AnkiConnect did not create the requested backup", file=sys.stderr)
                return 2
        if not (args.backup_confirmed or backup_created):
            print(
                "Write blocked: first export a full Anki collection backup with "
                "scheduling, then pass --backup-confirmed (or use --backup-deck "
                "and --backup-path).",
                file=sys.stderr,
            )
            return 2

        api_key = (
            os.environ.get(config.llm.api_key_env, "")
            if config.llm.api_key_env
            else ""
        )
        if config.llm.api_key_env and not api_key:
            print(f"Missing environment variable {config.llm.api_key_env}", file=sys.stderr)
            return 2
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
        translator = OpenAICompatibleTranslator(
            config.llm,
            api_key,
            JsonCache(config.processing.cache_dir),
            config.processing.enrichment_version,
        )
        counters, preview, missing = run_pipeline(
            anki, translator, config, query, args.limit, dry_run=False
        )
        print_json(
            {
                "mode": "enrich",
                "query": query,
                "fields_added": missing,
                "summary": vars(counters),
                "processed": preview,
            }
        )
        return 1 if counters.errors else 0
    except (ConfigError, AnkiConnectError) as exc:
        print(str(exc), file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
