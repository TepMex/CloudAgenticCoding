#!/usr/bin/env python3
"""Build chinese_money_numbers.json with Chinese price TTS (edge-tts).

Prefers the app-format catalog already in assets. Missing amounts in 41–114
are filled from the listening dataset (`meta` + `items`) when present — that
file is never copied over the app catalog, because its shape is different.
TTS is only used when neither source can supply a clip.
"""

from __future__ import annotations

import argparse
import asyncio
import base64
import json
import sys
from pathlib import Path

DIGITS = "零一二三四五六七八九"
APP_AMOUNTS = [
    1, 2, 3, 4, 5, 6, 7, 8, 9, 10,
    12, 15, 16, 18, 20, 22, 25, 28, 30, 35, 40,
] + list(range(41, 115))


def to_chinese_number(value: int) -> str:
    if value < 1 or value > 9999:
        raise ValueError(f"unsupported amount: {value}")
    units = ("", "十", "百", "千")
    output = ""
    zero_needed = False
    for place in range(3, -1, -1):
        divisor = 10**place
        digit = (value // divisor) % 10
        if digit == 0:
            if output and value % divisor != 0:
                zero_needed = True
            continue
        if zero_needed:
            output += DIGITS[0]
            zero_needed = False
        if not (place == 1 and digit == 1 and output == ""):
            output += DIGITS[digit]
        output += units[place]
    return output


def to_spoken_price(value: int) -> str:
    if value == 2:
        return "两块"
    return f"{to_chinese_number(value)}块"


def to_pinyin_stub(value: int) -> str:
    # Lightweight labels for the catalog; UI does not require perfect pinyin.
    mapping = {
        1: "yī kuài",
        2: "liǎng kuài",
        3: "sān kuài",
        4: "sì kuài",
        5: "wǔ kuài",
        6: "liù kuài",
        7: "qī kuài",
        8: "bā kuài",
        9: "jiǔ kuài",
        10: "shí kuài",
        12: "shí èr kuài",
        15: "shí wǔ kuài",
        16: "shí liù kuài",
        18: "shí bā kuài",
        20: "èr shí kuài",
        22: "èr shí èr kuài",
        25: "èr shí wǔ kuài",
        28: "èr shí bā kuài",
        30: "sān shí kuài",
        35: "sān shí wǔ kuài",
        40: "sì shí kuài",
        100: "yī bǎi kuài",
        114: "yī bǎi yī shí sì kuài",
    }
    return mapping.get(value, f"{value} kuài")


def looks_like_app_catalog(data: object) -> bool:
    if not isinstance(data, dict) or "items" in data:
        return False
    for key, value in data.items():
        if str(key).isdigit() and isinstance(value, dict):
            return "audio_base64" in value or "audio" in value or "audioBase64" in value
    return False


def looks_like_listening_dataset(data: object) -> bool:
    return isinstance(data, dict) and isinstance(data.get("items"), list)


def strip_data_uri(audio: str) -> str:
    marker = "base64,"
    if audio.startswith("data:") and marker in audio:
        return audio.split(marker, 1)[1]
    return audio


def clip_from_listening_item(item: dict) -> dict:
    return {
        "number": int(item["number"]),
        "hanzi": item.get("hanzi") or to_spoken_price(int(item["number"])),
        "pinyin": item.get("pinyin") or to_pinyin_stub(int(item["number"])),
        "audio_base64": strip_data_uri(item["audio"]),
    }


def merge_listening_dataset(catalog: dict[str, dict], dataset: dict) -> int:
    by_number: dict[int, list[dict]] = {}
    for item in dataset["items"]:
        amount = int(item["number"])
        by_number.setdefault(amount, []).append(item)
    added = 0
    for amount in APP_AMOUNTS:
        key = str(amount)
        if key in catalog:
            continue
        variants = by_number.get(amount) or []
        if not variants:
            continue
        pick = next((item for item in variants if item.get("suffix") == "kuaiqian"), variants[0])
        catalog[key] = clip_from_listening_item(pick)
        added += 1
    return added


def ordered_catalog(catalog: dict[str, dict]) -> dict[str, dict]:
    return {str(amount): catalog[str(amount)] for amount in sorted(int(key) for key in catalog)}


async def synth_one(text: str, voice: str) -> bytes:
    import edge_tts

    communicate = edge_tts.Communicate(text, voice=voice, rate="-10%")
    chunks: list[bytes] = []
    async for chunk in communicate.stream():
        if chunk["type"] == "audio":
            chunks.append(chunk["data"])
    if not chunks:
        raise RuntimeError(f"no audio returned for {text!r}")
    return b"".join(chunks)


async def build_catalog(amounts: list[int], voice: str) -> dict[str, dict]:
    catalog: dict[str, dict] = {}
    for amount in amounts:
        spoken = to_spoken_price(amount)
        audio = await synth_one(spoken, voice)
        catalog[str(amount)] = {
            "number": amount,
            "hanzi": spoken,
            "pinyin": to_pinyin_stub(amount),
            "audio_base64": base64.b64encode(audio).decode("ascii"),
        }
        print(f"synthesized {amount}: {spoken} ({len(audio)} bytes)", flush=True)
    return catalog


def load_json(path: Path) -> object | None:
    if not path.is_file() or path.stat().st_size <= 100:
        return None
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError:
        return None


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--out",
        type=Path,
        default=Path("app/src/main/assets/chinese_money_numbers.json"),
    )
    parser.add_argument(
        "--source",
        type=Path,
        default=None,
        help="App-format JSON or listening dataset (meta/items) to merge.",
    )
    parser.add_argument("--voice", default="zh-CN-XiaoxiaoNeural")
    args = parser.parse_args()

    candidates: list[Path] = []
    if args.source:
        candidates.append(args.source)
    candidates.extend(
        [
            args.out,
            Path("chinese_money_numbers.json"),
            Path("../chinese_money_numbers.json"),
            Path("/workspace/chinese_money_numbers.json"),
        ]
    )

    catalog: dict[str, dict] = {}
    listening: dict | None = None
    for candidate in candidates:
        data = load_json(candidate)
        if data is None:
            continue
        if looks_like_app_catalog(data) and not catalog:
            catalog = {str(k): v for k, v in data.items() if str(k).isdigit()}
            print(f"loaded app catalog from {candidate} ({len(catalog)} clips)")
        elif looks_like_listening_dataset(data) and listening is None:
            listening = data
            print(f"loaded listening dataset from {candidate}")

    if listening is not None:
        added = merge_listening_dataset(catalog, listening)
        print(f"merged {added} clips from listening dataset")

    missing = [amount for amount in APP_AMOUNTS if str(amount) not in catalog]
    if missing:
        print(f"synthesizing {len(missing)} missing amounts")
        synthesized = asyncio.run(build_catalog(missing, args.voice))
        catalog.update(synthesized)

    if not catalog:
        print("no clips available", file=sys.stderr)
        return 1

    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(
        json.dumps(ordered_catalog(catalog), ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    print(f"wrote {args.out} with {len(catalog)} clips")
    return 0


if __name__ == "__main__":
    sys.exit(main())
