from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

from anki_enricher.anki import AnkiConnect, AnkiConnectError
from anki_enricher.config import ConfigError, load_config, load_dotenv
from anki_enricher.known_chars import run_known_char_report


def parser() -> argparse.ArgumentParser:
    result = argparse.ArgumentParser(
        description=(
            "List notes whose SentenceSimplified uses only well-learned hanzi "
            "(characters from words with interval greater than the threshold)"
        )
    )
    result.add_argument("--config", type=Path, default=Path("config.yaml"))
    result.add_argument("--query", help="Override the target Anki search query")
    result.add_argument(
        "--history-query",
        help="Override the lexicon search used to collect well-learned hanzi",
    )
    result.add_argument(
        "--limit",
        type=int,
        help="Maximum number of matching notes to include in the output",
    )
    result.add_argument(
        "--min-interval",
        type=int,
        default=360,
        help="Minimum exclusive review interval in days (default: 360)",
    )
    result.add_argument(
        "--sentence-field",
        default="SentenceSimplified",
        help="Note field that contains the example sentence",
    )
    return result


def print_json(value: object) -> None:
    print(json.dumps(value, ensure_ascii=False, indent=2, sort_keys=True))


def main(argv: list[str] | None = None) -> int:
    args = parser().parse_args(argv)
    if args.limit is not None and args.limit < 1:
        print("--limit must be positive", file=sys.stderr)
        return 2
    if args.min_interval < 0:
        print("--min-interval must be non-negative", file=sys.stderr)
        return 2
    load_dotenv(args.config.resolve().parent / ".env")
    try:
        config = load_config(args.config)
        query = args.query if args.query is not None else config.anki.query
        history_query = (
            args.history_query
            if args.history_query is not None
            else config.related.history_query or query
        )
        anki = AnkiConnect(config.anki.url)
        version = anki.check()
        if version < 6:
            raise AnkiConnectError(
                f"AnkiConnect API version {version}; version 6 required"
            )
        print_json(
            run_known_char_report(
                anki,
                query=query,
                history_query=history_query,
                hanzi_field=config.related.hanzi_field,
                sentence_field=args.sentence_field,
                min_interval=args.min_interval,
                limit=args.limit,
            )
        )
        return 0
    except (ConfigError, AnkiConnectError) as exc:
        print(str(exc), file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
