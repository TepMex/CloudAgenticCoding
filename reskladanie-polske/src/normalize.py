from __future__ import annotations

import re
import unicodedata
from dataclasses import dataclass


URL_RE = re.compile(r"^(?:https?://|www\.)", re.IGNORECASE)
EMAIL_RE = re.compile(r"^[^\s@]+@[^\s@]+\.[^\s@]+$")
ENTITY_RE = re.compile(r"^&(?:#\d+|#x[0-9a-f]+|[a-z][a-z0-9]+);$", re.IGNORECASE)
PURE_NUMBER_RE = re.compile(r"^[\d\s.,:+\-/%‰]+$")
MOJIBAKE_MARKERS = ("�", "Ã", "Â", "Ä", "Å", "Ĺ")
ALLOWED_PUNCTUATION = {"-", "‐", "‑", "'", "’", "."}


@dataclass(frozen=True, slots=True)
class NormalizationResult:
    lemma: str | None
    rejection_reason: str | None = None
    flags: tuple[str, ...] = ()


def normalize_lemma(raw: str | None) -> NormalizationResult:
    """Conservatively normalize a corpus-provided lemma.

    This intentionally is not a Polish tokenizer or stemmer.  It performs only
    technical cleanup and keeps short/function words, diacritics, and infrequent
    but plausible lemmas.
    """

    if raw is None:
        return NormalizationResult(None, "empty")
    value = unicodedata.normalize("NFC", raw).strip()
    if not value:
        return NormalizationResult(None, "empty")
    if URL_RE.match(value):
        return NormalizationResult(None, "url")
    if EMAIL_RE.match(value):
        return NormalizationResult(None, "email")
    if ENTITY_RE.match(value) or (value.startswith("<") and value.endswith(">")):
        return NormalizationResult(None, "markup")
    if any(marker in value for marker in MOJIBAKE_MARKERS):
        return NormalizationResult(None, "mojibake")
    if PURE_NUMBER_RE.fullmatch(value):
        return NormalizationResult(None, "number")
    if not any(unicodedata.category(char).startswith("L") for char in value):
        return NormalizationResult(None, "punctuation")
    if any(char.isspace() for char in value):
        return NormalizationResult(None, "embedded_whitespace")

    for char in value:
        category = unicodedata.category(char)
        if category.startswith(("L", "M", "N")) or char in ALLOWED_PUNCTUATION:
            continue
        return NormalizationResult(None, "invalid_character")

    normalized = unicodedata.normalize("NFC", value.lower())
    flags: list[str] = []
    if any(char.isdigit() for char in normalized):
        flags.append("contains_digit")
    return NormalizationResult(normalized, flags=tuple(flags))


def is_capitalized_lemma(raw: str) -> bool:
    """Return true when the first cased character is uppercase."""

    for char in raw:
        if char.isalpha():
            return char.isupper()
    return False

