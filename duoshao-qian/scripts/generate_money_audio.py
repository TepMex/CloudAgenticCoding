#!/usr/bin/env python3
"""Build chinese_money_numbers.json with Chinese price TTS (edge-tts).

If a source JSON already exists (user-provided clips), it is copied through
unchanged so the app can consume the original base64 fields.
"""

from __future__ import annotations

import argparse
import asyncio
import base64
import json
import sys
from pathlib import Path

DIGITS = "零一二三四五六七八九"


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
    }
    return mapping.get(value, f"{value} kuài")


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
        help="Existing JSON to copy if present (user-provided clips).",
    )
    parser.add_argument("--voice", default="zh-CN-XiaoxiaoNeural")
    args = parser.parse_args()

    candidates = []
    if args.source:
        candidates.append(args.source)
    candidates.extend(
        [
            Path("chinese_money_numbers.json"),
            Path("../chinese_money_numbers.json"),
            Path("/workspace/chinese_money_numbers.json"),
        ]
    )
    for candidate in candidates:
        if candidate.is_file() and candidate.stat().st_size > 100:
            args.out.parent.mkdir(parents=True, exist_ok=True)
            args.out.write_bytes(candidate.read_bytes())
            print(f"copied existing catalog from {candidate} -> {args.out}")
            return 0

    amounts = [
        1, 2, 3, 4, 5, 6, 7, 8, 9, 10,
        12, 15, 16, 18, 20, 22, 25, 28, 30, 35, 40,
    ]
    catalog = asyncio.run(build_catalog(amounts, args.voice))
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(json.dumps(catalog, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"wrote {args.out} with {len(catalog)} clips")
    return 0


if __name__ == "__main__":
    sys.exit(main())
