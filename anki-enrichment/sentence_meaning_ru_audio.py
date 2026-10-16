from __future__ import annotations

import argparse
import json
import logging
import os
import random
import sys
from pathlib import Path

from anki_enricher.anki import AnkiConnect, AnkiConnectError
from anki_enricher.config import ConfigError, load_config, load_dotenv
from anki_enricher.sentence_audio import SentenceAudioError, run_sentence_audio
from anki_enricher.tts import AudioCache, ProxyAPITTS


def parser() -> argparse.ArgumentParser:
    result = argparse.ArgumentParser(
        description="Add Russian sentence audio to existing Anki notes through AnkiConnect"
    )
    result.add_argument("--config", type=Path, default=Path("config.yaml"))
    mode = result.add_mutually_exclusive_group()
    mode.add_argument("--dry-run", action="store_true", help="Preview without writes or TTS")
    mode.add_argument("--enrich", action="store_true", help="Synthesize audio and update notes")
    result.add_argument("--query", help="Override tts.query")
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
        query = args.query if args.query is not None else config.tts.query
        anki = AnkiConnect(config.anki.url)
        version = anki.check()
        if version < 6:
            raise AnkiConnectError(f"AnkiConnect API version {version}; version 6 required")
        if not args.enrich:
            counters, preview, missing = run_sentence_audio(
                anki,
                None,
                config,
                query,
                args.limit,
                dry_run=True,
                rng=random.Random(0),
            )
            print_json(
                {
                    "mode": "dry-run",
                    "query": query,
                    "model": config.tts.model,
                    "source_field": config.tts.source_field,
                    "target_field": config.tts.target_field,
                    "voices": list(config.tts.voices),
                    "missing_fields_to_add": missing,
                    "notes_requiring_tts": counters.eligible,
                    "characters": counters.characters,
                    "skipped_empty_source": counters.skipped_empty,
                    "skipped_existing_audio": counters.skipped_existing,
                    "preview": preview,
                    "tts_calls": 0,
                    "writes": 0,
                }
            )
            return 0

        if bool(args.backup_deck) != bool(args.backup_path):
            print("--backup-deck and --backup-path must be used together", file=sys.stderr)
            return 2
        backup_created = False
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
        api_key = os.environ.get(config.tts.api_key_env, "")
        if not api_key:
            print(f"Missing environment variable {config.tts.api_key_env}", file=sys.stderr)
            return 2
        config.processing.log_dir.mkdir(parents=True, exist_ok=True)
        logging.basicConfig(
            level=logging.INFO,
            format="%(asctime)s %(levelname)s %(message)s",
            handlers=[
                logging.StreamHandler(),
                logging.FileHandler(
                    config.processing.log_dir / "sentence_meaning_ru_audio.log",
                    encoding="utf-8",
                ),
            ],
        )
        synthesizer = ProxyAPITTS(
            config.tts,
            api_key,
            AudioCache(config.processing.cache_dir / "tts"),
        )
        counters, preview, missing = run_sentence_audio(
            anki,
            synthesizer,
            config,
            query,
            args.limit,
            dry_run=False,
            rng=random.Random(),
        )
        print_json(
            {
                "mode": "enrich",
                "query": query,
                "model": config.tts.model,
                "fields_added": missing,
                "summary": vars(counters),
                "processed": preview,
            }
        )
        return 1 if counters.errors else 0
    except (ConfigError, AnkiConnectError, SentenceAudioError) as exc:
        print(str(exc), file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
