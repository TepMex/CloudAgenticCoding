#!/usr/bin/env python3
"""Build assets/lexicon/words.tsv for offline word segmentation.

Sources (see ../NOTICE.md):
- CC-CEDICT simplified headwords and numbered pinyin
- Wikidata zh–ru interwiki titles (open-dict-data/wikidict-ru), mapped
  traditional → simplified with the CEDICT pair
- hanzi-info single-character Wikipedia titles when present
- a short supplement of frequent words that have no Wikipedia title

The committed TSV is what the app ships. Re-run only to refresh it.
"""

from __future__ import annotations

import gzip
import json
import re
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "app/src/main/assets/lexicon/words.tsv"
CACHE = Path("/tmp/lexicon")
HANZI_INFO = ROOT.parent / "hanzi-info/data/wiki_zh_ru_single_char.json"

CEDICT_URL = "https://www.mdbg.net/chinese/export/cedict/cedict_1_0_ts_utf-8_mdbg.txt.gz"
WIKI_URL = "https://raw.githubusercontent.com/open-dict-data/wikidict-ru/master/data/zh-ru_wiki.txt"

HAN = re.compile(r"^[\u4e00-\u9fff\u3400-\u4dbf]+$")
CYR = re.compile(r"[А-Яа-яЁё]")
TONE = {
    "a": "āáǎàa",
    "e": "ēéěèe",
    "i": "īíǐìi",
    "o": "ōóǒòo",
    "u": "ūúǔùu",
    "ü": "ǖǘǚǜü",
}

# Frequent words with no Wikipedia article title. Only entries with a
# stable everyday Russian equivalent.
SUPPLEMENT = {
    "你好": "привет",
    "谢谢": "спасибо",
    "朋友": "друг",
    "我": "я",
    "我们": "мы",
    "你们": "вы",
    "他们": "они",
    "她们": "они",
    "这": "это",
    "那": "то",
    "什么": "что",
    "怎么": "как",
    "多少": "сколько",
    "请": "пожалуйста",
    "对不起": "извините",
    "再见": "до свидания",
    "是": "быть",
    "不": "не",
    "没": "нет",
    "没有": "нет",
    "有": "иметь",
    "在": "в",
    "和": "и",
    "的": "притяжательная частица",
    "了": "частица завершённости",
    "吗": "вопросительная частица",
    "呢": "частица",
    "很": "очень",
    "也": "тоже",
    "都": "все",
    "人": "человек",
    "先生": "господин",
    "小姐": "девушка",
    "老师": "учитель",
    "今天": "сегодня",
    "明天": "завтра",
    "现在": "сейчас",
    "这里": "здесь",
    "那里": "там",
    "可以": "можно",
    "想": "хотеть",
    "要": "нужно",
    "吃": "есть",
    "喝": "пить",
    "买": "покупать",
    "卖": "продавать",
    "钱": "деньги",
    "水": "вода",
    "茶": "чай",
    "开": "открывать",
    "关": "закрывать",
    "入口": "вход",
    "出口": "выход",
    "厕所": "туалет",
    "洗手间": "туалет",
    "地铁": "метро",
    "出租车": "такси",
    "公共汽车": "автобус",
    "飞机": "самолёт",
    "酒店": "гостиница",
    "宾馆": "гостиница",
    "饭店": "ресторан",
    "餐厅": "столовая",
    "好吃": "вкусно",
    "多少钱": "сколько стоит",
}


def fetch(url: str, dest: Path) -> None:
    if dest.exists() and dest.stat().st_size > 0:
        return
    dest.parent.mkdir(parents=True, exist_ok=True)
    urllib.request.urlretrieve(url, dest)


def tone_syllable(raw: str) -> str:
    syl = raw.strip().lower().replace("u:", "ü").replace("v", "ü")
    if not syl:
        return ""
    tone = 5
    if syl[-1].isdigit():
        tone = int(syl[-1])
        syl = syl[:-1]
    if tone == 0:
        tone = 5
    if tone == 5 or not any(ch in TONE for ch in syl):
        return syl
    mark_at = vowel_index(syl)
    vowel = syl[mark_at]
    marked = TONE[vowel][tone - 1]
    return syl[:mark_at] + marked + syl[mark_at + 1 :]


def vowel_index(syl: str) -> int:
    if "a" in syl:
        return syl.index("a")
    if "e" in syl:
        return syl.index("e")
    if "ou" in syl:
        return syl.index("ou")
    for i in range(len(syl) - 1, -1, -1):
        if syl[i] in TONE:
            return i
    return 0


def tone_pinyin(numbered: str) -> str:
    parts = [tone_syllable(p) for p in numbered.split() if p.strip()]
    return " ".join(p for p in parts if p)


def clean_ru(text: str) -> str:
    text = text.replace("&apos;", "'").replace("&quot;", '"').replace("&amp;", "&")
    text = re.sub(r"\s*\(.*?\)", "", text).strip()
    text = re.sub(r"\s+", " ", text)
    if text.lower().startswith("категория:"):
        return ""
    if not CYR.search(text):
        return ""
    if len(text) > 42:
        text = text[:42].rsplit(" ", 1)[0].strip()
    return text


def main() -> None:
    CACHE.mkdir(parents=True, exist_ok=True)
    cedict_gz = CACHE / "cedict.txt.gz"
    wiki_path = CACHE / "zh-ru_wiki.txt"
    fetch(CEDICT_URL, cedict_gz)
    fetch(WIKI_URL, wiki_path)

    wiki: dict[str, str] = {}
    for line in wiki_path.read_text(encoding="utf-8").splitlines():
        if "\t" not in line:
            continue
        zh, ru = line.split("\t", 1)
        zh, ru = zh.strip(), clean_ru(ru)
        if zh and ru:
            wiki.setdefault(zh, ru)

    single: dict[str, str] = {}
    if HANZI_INFO.exists():
        loaded = json.loads(HANZI_INFO.read_text(encoding="utf-8"))
        for zh, ru in loaded.items():
            cleaned = clean_ru(str(ru))
            if cleaned:
                single[zh] = cleaned

    # First CEDICT line wins (file order). Traditional head maps to simplified.
    entries: dict[str, tuple[str, str]] = {}
    trad_to_simp: dict[str, str] = {}
    with gzip.open(cedict_gz, "rt", encoding="utf-8") as handle:
        for line in handle:
            if not line or line[0] == "#":
                continue
            if "[" not in line or "]" not in line:
                continue
            head, rest = line.split("[", 1)
            numbered, defin = rest.split("]", 1)
            parts = head.split()
            if len(parts) < 2:
                continue
            trad, simp = parts[0], parts[1]
            if not HAN.match(simp) or not (1 <= len(simp) <= 12):
                continue
            trad_to_simp.setdefault(trad, simp)
            pinyin = tone_pinyin(numbered)
            if not pinyin:
                continue
            rare = defin.lstrip().startswith(
                ("/surname", "/used in names", "/variant of", "/old variant", "/see ")
            )
            previous = entries.get(simp)
            if previous is None:
                entries[simp] = (pinyin, "", rare)
            elif previous[2] and not rare:
                entries[simp] = (pinyin, "", False)

    ru_by_simp: dict[str, str] = {}
    for trad, simp in trad_to_simp.items():
        ru = wiki.get(simp) or wiki.get(trad) or ""
        if ru:
            ru_by_simp.setdefault(simp, ru)

    russian_filled = 0
    finalized: dict[str, tuple[str, str]] = {}
    for simp, (pinyin, _, _) in entries.items():
        ru = single.get(simp, "") if len(simp) == 1 else ""
        if not ru:
            ru = ru_by_simp.get(simp, "")
        if not ru:
            ru = SUPPLEMENT.get(simp, "")
        ru = clean_ru(ru) if ru else ""
        if ru:
            russian_filled += 1
        finalized[simp] = (pinyin, ru)
    entries = finalized  # type: ignore[assignment]

    # Supplement words missing from CEDICT still need a row if we have pinyin via chars — skip.
    OUT.parent.mkdir(parents=True, exist_ok=True)
    lines = []
    for simp, (pinyin, ru) in entries.items():
        if "\t" in pinyin or "\t" in ru or "\n" in ru:
            continue
        lines.append(f"{simp}\t{pinyin}\t{ru}")
    OUT.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"wrote {OUT} words={len(lines)} with_russian={russian_filled}")
    for sample in ("你好", "中国", "银行", "朋友", "谢谢", "汉", "你", "菜单", "咖啡"):
        row = entries.get(sample)
        print(sample, row)


if __name__ == "__main__":
    main()
