from __future__ import annotations

import html
import re
import unicodedata
from dataclasses import dataclass


INITIALS = (
    "",
    "b", "p", "m", "f",
    "d", "t", "n", "l",
    "g", "k", "h",
    "j", "q", "x",
    "zh", "ch", "sh", "r",
    "z", "c", "s",
)

FINAL_GROUPS = (
    ("a", "ai", "an", "ang", "ao"),
    ("o", "e", "ei", "en", "eng", "er", "ou", "ong"),
    ("i", "ia", "ie", "iao", "iu", "ian", "in", "iang", "ing", "iong"),
    ("u", "ua", "uo", "uai", "ui", "uan", "un", "uang", "ueng"),
    ("ü", "üe", "üan", "ün"),
)
FINALS = tuple(final for group in FINAL_GROUPS for final in group)

# The order and valid cells are copied from map-of-chinese/src/lib/pinyin.ts and
# its generated syllable-cells dataset.  apical-i occupies the visible i column.
_VALID_CELL_TEXT = """
|a |ai |an |ang |ao |e |en |er |i |ia |ian |iang |iao |ie |in |ing |iong |iu |o |ou |u |ua |uai |uan |uang |ueng |ui |un |uo |ü |üan |üe |ün
b|a b|ai b|an b|ang b|ao b|ei b|en b|eng b|i b|ian b|iao b|ie b|in b|ing b|o b|u
p|a p|ai p|an p|ang p|ao p|ei p|en p|eng p|i p|ian p|iao p|ie p|in p|ing p|o p|ou p|u
m|a m|ai m|an m|ang m|ao m|e m|ei m|en m|eng m|i m|ian m|iao m|ie m|in m|ing m|iu m|o m|ou m|u
f|a f|an f|ang f|ei f|en f|eng f|o f|ou f|u
d|a d|ai d|an d|ang d|ao d|e d|ei d|eng d|i d|ian d|iao d|ie d|ing d|iu d|ong d|ou d|u d|uan d|ui d|un d|uo
t|a t|ai t|an t|ang t|ao t|e t|eng t|i t|ian t|iao t|ie t|ing t|ong t|ou t|u t|uan t|ui t|un t|uo
n|a n|ai n|an n|ang n|ao n|e n|ei n|en n|eng n|i n|ian n|iang n|iao n|ie n|in n|ing n|iu n|ong n|u n|uan n|uo n|ü n|üe
l|a l|ai l|an l|ang l|ao l|e l|ei l|eng l|i l|ia l|ian l|iang l|iao l|ie l|in l|ing l|iu l|ong l|ou l|u l|uan l|un l|uo l|ü l|üe
g|a g|ai g|an g|ang g|ao g|e g|ei g|en g|eng g|ong g|ou g|u g|ua g|uai g|uan g|uang g|ui g|un g|uo
k|a k|ai k|an k|ang k|ao k|e k|ei k|en k|eng k|ong k|ou k|u k|ua k|uai k|uan k|uang k|ui k|un k|uo
h|a h|ai h|an h|ang h|ao h|e h|ei h|en h|eng h|ong h|ou h|u h|ua h|uai h|uan h|uang h|ui h|un h|uo
j|i j|ia j|ian j|iang j|iao j|ie j|in j|ing j|iong j|iu j|ü j|üan j|üe j|ün
q|i q|ia q|ian q|iang q|iao q|ie q|in q|ing q|iong q|iu q|ü q|üan q|üe q|ün
x|i x|ia x|ian x|iang x|iao x|ie x|in x|ing x|iong x|iu x|ü x|üan x|üe x|ün
zh|a zh|ai zh|an zh|ang zh|ao zh|apical-i zh|e zh|en zh|eng zh|ong zh|ou zh|u zh|ua zh|uai zh|uan zh|uang zh|ui zh|un zh|uo
ch|a ch|ai ch|an ch|ang ch|ao ch|apical-i ch|e ch|en ch|eng ch|ong ch|ou ch|u ch|ua ch|uai ch|uan ch|uang ch|ui ch|un ch|uo
sh|a sh|ai sh|an sh|ang sh|ao sh|apical-i sh|e sh|ei sh|en sh|eng sh|ou sh|u sh|ua sh|uai sh|uan sh|uang sh|ui sh|un sh|uo
r|an r|ang r|ao r|apical-i r|e r|en r|eng r|ong r|ou r|u r|uan r|ui r|un r|uo
z|a z|ai z|an z|ang z|ao z|apical-i z|e z|ei z|en z|eng z|ong z|ou z|u z|uan z|ui z|un z|uo
c|a c|ai c|an c|ang c|ao c|apical-i c|e c|en c|eng c|ong c|ou c|u c|uan c|ui c|un c|uo
s|a s|ai s|an s|ang s|ao s|apical-i s|e s|en s|eng s|ong s|ou s|u s|uan s|ui s|un s|uo
"""
VALID_CELLS = frozenset(
    tuple(value.split("|", 1)) for value in _VALID_CELL_TEXT.split()
)

_MARKED = {
    "ā": "a", "á": "a", "ǎ": "a", "à": "a",
    "ē": "e", "é": "e", "ě": "e", "è": "e",
    "ī": "i", "í": "i", "ǐ": "i", "ì": "i",
    "ō": "o", "ó": "o", "ǒ": "o", "ò": "o",
    "ū": "u", "ú": "u", "ǔ": "u", "ù": "u",
    "ǖ": "ü", "ǘ": "ü", "ǚ": "ü", "ǜ": "ü",
    "ń": "n", "ň": "n", "ǹ": "n", "ḿ": "m",
}
_ORTHOGRAPHIC_Y = {
    "yi": "i", "ya": "ia", "ye": "ie", "yao": "iao", "you": "iu",
    "yan": "ian", "yin": "in", "yang": "iang", "ying": "ing",
    "yong": "iong", "yu": "ü", "yue": "üe", "yuan": "üan", "yun": "ün",
}
_ORTHOGRAPHIC_W = {
    "wu": "u", "wa": "ua", "wo": "uo", "wai": "uai", "wei": "ui",
    "wan": "uan", "wen": "un", "wang": "uang", "weng": "ueng",
}
_INITIAL_MATCH_ORDER = (
    "zh", "ch", "sh", "b", "p", "m", "f", "d", "t", "n", "l",
    "g", "k", "h", "j", "q", "x", "r", "z", "c", "s",
)
_APICAL_INITIALS = frozenset(("z", "c", "s", "zh", "ch", "sh", "r"))
_TAG_RE = re.compile(r"<[^>]*>")
_PINYIN_RUN_RE = re.compile(
    r"[A-Za-zÜüĀÁǍÀĒÉĚÈĪÍǏÌŌÓǑÒŪÚǓÙǕǗǙǛŃŇǸḾ:0-5]+",
    re.IGNORECASE,
)


@dataclass(frozen=True)
class ParsedPinyin:
    display: str
    plain: str
    initial: str
    final: str

    @property
    def layout_final(self) -> str:
        return "i" if self.final == "apical-i" else self.final


def canonical_cell(initial: str, layout_final: str) -> tuple[str, str]:
    if layout_final == "i" and initial in _APICAL_INITIALS:
        return initial, "apical-i"
    return initial, layout_final


def _normalize_unicode(value: str) -> str:
    # A handful of real HSK rows contain an accidental combining dot below in
    # addition to the tone mark (for example yị̄ and bụ̀).  It has no pinyin
    # meaning, so discard it while retaining the actual tone diacritic.
    decomposed = unicodedata.normalize("NFD", value).replace("\N{COMBINING DOT BELOW}", "")
    return unicodedata.normalize("NFC", decomposed)


def _plain_syllable(raw: str) -> str | None:
    value = _normalize_unicode(raw).strip().lower()
    value = value.replace("u:", "ü").replace("v", "ü")
    match = re.search(r"([1-5])$", value)
    if match:
        value = value[:-1]
    if re.search(r"[0-9]", value):
        return None
    plain = "".join(_MARKED.get(char, char) for char in value)
    return plain if plain and re.fullmatch(r"[a-zü]+", plain) else None


def parse_syllable(raw: str) -> ParsedPinyin | None:
    plain = _plain_syllable(raw)
    if plain is None:
        return None
    initial = ""
    final = plain
    if plain in _ORTHOGRAPHIC_Y:
        final = _ORTHOGRAPHIC_Y[plain]
    elif plain in _ORTHOGRAPHIC_W:
        final = _ORTHOGRAPHIC_W[plain]
    else:
        for candidate in _INITIAL_MATCH_ORDER:
            if plain.startswith(candidate):
                initial = candidate
                final = plain[len(candidate) :]
                break
        if initial in ("j", "q", "x") and final.startswith("u"):
            final = "ü" + final[1:]
    if initial in _APICAL_INITIALS and final == "i":
        final = "apical-i"
    if (initial, final) not in VALID_CELLS:
        return None
    return ParsedPinyin(raw, plain, initial, final)


def parse_first_syllable(raw: str) -> ParsedPinyin | None:
    text = html.unescape(_TAG_RE.sub(" ", raw))
    match = _PINYIN_RUN_RE.search(_normalize_unicode(text))
    if not match:
        return None
    token = match.group(0)

    numbered = re.search(r"[1-5]", token)
    if numbered:
        return parse_syllable(token[: numbered.end()])

    marked_positions = [index for index, char in enumerate(token.lower()) if char in _MARKED]
    stop = marked_positions[1] if len(marked_positions) > 1 else len(token)
    minimum = marked_positions[0] + 1 if marked_positions else 1
    for end in range(stop, minimum - 1, -1):
        parsed = parse_syllable(token[:end])
        if parsed is not None:
            return parsed
    return None


def dump_layout() -> str:
    initials = "\n".join(
        f"{index:2}  {value or '∅'}" for index, value in enumerate(INITIALS)
    )
    finals = "\n".join(f"{index:2}  {value}" for index, value in enumerate(FINALS))
    return f"INITIALS:\n{initials}\n\nFINALS:\n{finals}"
