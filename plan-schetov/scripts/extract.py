#!/usr/bin/env python3
"""Turn the enriched chart-of-accounts HTML into src/data/accounts.json."""

from __future__ import annotations

import json
import re
import sys
from html.parser import HTMLParser
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "data" / "source.html"
OUTPUT = ROOT / "src" / "data" / "accounts.json"

DOUBLE = re.compile(
    r"^(?P<storno>СТОРНО\s+)?Дт\s+(?P<debit>\S+)\s+[—–\-]\s+Кт\s+(?P<credit>\S+)\s+[—–\-]\s+(?P<text>.*?)(?:\s*\[(?P<tags>[^\]]*)\])?\s*$"
)
SINGLE = re.compile(
    r"^(?P<storno>СТОРНО\s+)?(?P<side>Дт|Кт)\s+(?P<code>\S+)\s+[—–\-]\s+(?P<text>.*?)(?:\s*\[(?P<tags>[^\]]*)\])?\s*$"
)
TITLE = re.compile(r"^(?P<code>\d{2,3})\s+·\s+(?P<name>.+)$")


class Collector(HTMLParser):
    def __init__(self) -> None:
        super().__init__(convert_charrefs=True)
        self.in_style = False
        self.capture: tuple[str, dict[str, str]] | None = None
        self.buf: list[str] = []
        self.events: list[tuple[str, dict[str, str], str]] = []

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        if tag == "style":
            self.in_style = True
            return
        attr_map = {key: value or "" for key, value in attrs}
        if tag in {"h2", "section", "dt", "dd", "li", "a"}:
            self.flush()
            self.capture = (tag, attr_map)
            self.buf = []
        elif tag == "br" and self.capture is not None:
            self.buf.append(" ")

    def handle_endtag(self, tag: str) -> None:
        if tag == "style":
            self.in_style = False
            return
        if self.capture is not None and self.capture[0] == tag:
            self.flush()

    def handle_data(self, data: str) -> None:
        if self.in_style or self.capture is None:
            return
        self.buf.append(data)

    def flush(self) -> None:
        if self.capture is None:
            return
        text = re.sub(r"\s+", " ", "".join(self.buf)).strip()
        tag, attrs = self.capture
        self.events.append((tag, attrs, text))
        self.capture = None
        self.buf = []


def split_tags(raw: str | None) -> list[str]:
    if not raw:
        return []
    return [part.strip() for part in raw.split(";") if part.strip()]


def parse_item(account_code: str, index: int, raw: str) -> dict[str, object]:
    double = DOUBLE.match(raw)
    if double:
        return {
            "id": f"{account_code}:{index}",
            "storno": bool(double.group("storno")),
            "debit": double.group("debit"),
            "credit": double.group("credit"),
            "text": double.group("text").strip(),
            "tags": split_tags(double.group("tags")),
        }
    single = SINGLE.match(raw)
    if single:
        side = single.group("side")
        code = single.group("code")
        return {
            "id": f"{account_code}:{index}",
            "storno": bool(single.group("storno")),
            "debit": code if side == "Дт" else None,
            "credit": code if side == "Кт" else None,
            "text": single.group("text").strip(),
            "tags": split_tags(single.group("tags")),
        }
    raise SystemExit(f"unparsed entry on {account_code}: {raw}")


def kind_of(raw: str) -> tuple[str, bool]:
    off = "забалансовый" in raw
    if raw.startswith("Активно-пассивный"):
        return "active-passive", off
    if raw.startswith("Пассивный"):
        return "passive", off
    if raw.startswith("Активный"):
        return "active", off
    raise SystemExit(f"unknown account kind: {raw}")


def build() -> dict[str, object]:
    parser = Collector()
    parser.feed(SOURCE.read_text(encoding="utf-8"))
    parser.flush()

    accounts: list[dict[str, object]] = []
    current: dict[str, object] | None = None
    field = ""

    for tag, attrs, text in parser.events:
        if tag == "section":
            if current is not None:
                accounts.append(current)
            kind, off = kind_of(text)
            current = {
                "kind": kind,
                "offBalance": off,
                "fields": {},
                "items": [],
                "sources": [],
            }
            field = ""
            continue
        if current is None:
            continue
        if tag == "h2":
            current["title"] = text
        elif tag == "dt":
            field = text
        elif tag == "dd":
            fields = current["fields"]
            assert isinstance(fields, dict)
            fields[field] = text
        elif tag == "li":
            items = current["items"]
            assert isinstance(items, list)
            items.append(text)
        elif tag == "a" and attrs.get("href", "").startswith("http"):
            sources = current["sources"]
            assert isinstance(sources, list)
            sources.append({"title": text, "url": attrs["href"]})

    if current is not None:
        accounts.append(current)

    cleaned: list[dict[str, object]] = []
    seen: set[str] = set()
    for account in accounts:
        title = str(account.get("title", ""))
        match = TITLE.match(title)
        if not match:
            raise SystemExit(f"bad title: {title}")
        code = match.group("code")
        if code in seen:
            raise SystemExit(f"duplicate account {code}")
        seen.add(code)
        fields = account["fields"]
        assert isinstance(fields, dict)
        items = account["items"]
        assert isinstance(items, list)
        entries = [parse_item(code, index, str(raw)) for index, raw in enumerate(items, start=1)]
        sources = account["sources"]
        assert isinstance(sources, list)
        unique_sources: list[dict[str, str]] = []
        used_urls: set[str] = set()
        for source in sources:
            assert isinstance(source, dict)
            url = str(source["url"])
            if url in used_urls:
                continue
            used_urls.add(url)
            unique_sources.append({"title": str(source["title"]), "url": url})
        cleaned.append(
            {
                "code": code,
                "name": match.group("name"),
                "kind": account["kind"],
                "offBalance": account["offBalance"],
                "meaning": fields.get("Смысл", ""),
                "debitMeaning": next(value for key, value in fields.items() if str(key).startswith("Д+")),
                "creditMeaning": next(value for key, value in fields.items() if str(key).startswith("К+")),
                "notes": fields.get("Особенности", ""),
                "sources": unique_sources,
                "entries": entries,
            }
        )

    cleaned.sort(key=lambda item: (len(str(item["code"])), int(str(item["code"]))))
    balance = [item for item in cleaned if not item["offBalance"]]
    off = [item for item in cleaned if item["offBalance"]]
    if len(cleaned) != 73 or len(balance) != 62 or len(off) != 11:
        raise SystemExit(f"unexpected counts: {len(cleaned)} / {len(balance)} / {len(off)}")
    entry_count = sum(len(item["entries"]) for item in cleaned)  # type: ignore[arg-type]
    return {
        "basis": "Учебный справочник по Инструкции к плану счетов (приказ Минфина РФ от 31.10.2000 № 94н) для организаций, кроме банков и бюджетной сферы.",
        "balanceCount": len(balance),
        "offBalanceCount": len(off),
        "entryCount": entry_count,
        "accounts": cleaned,
    }


def main() -> None:
    payload = build()
    text = json.dumps(payload, ensure_ascii=False, indent=2) + "\n"
    check = "--check" in sys.argv
    if check:
        current = OUTPUT.read_text(encoding="utf-8") if OUTPUT.exists() else ""
        if current != text:
            raise SystemExit("accounts.json is out of date; run scripts/extract.py")
        print(f"ok {payload['entryCount']} entries")
        return
    OUTPUT.write_text(text, encoding="utf-8")
    print(f"wrote {OUTPUT} ({payload['entryCount']} entries)")


if __name__ == "__main__":
    main()
