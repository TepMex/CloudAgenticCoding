from __future__ import annotations

from collections import Counter, defaultdict
from dataclasses import dataclass, field

from ..ranking import zipf_from_ipm


@dataclass(slots=True)
class LemmaFrequency:
    lemma: str
    originals: set[str] = field(default_factory=set)
    frequency: float = 0.0
    ipm: float = 0.0
    zipf: float | None = None
    rank: int | None = None
    pos_frequency: dict[str, float] = field(default_factory=lambda: defaultdict(float))
    flags: set[str] = field(default_factory=set)
    capitalized_frequency: float = 0.0
    lowercase_frequency: float = 0.0
    contextual_diversity_count: int | None = None
    contextual_diversity: float | None = None
    arf: float | None = None
    one_dp_numerator: float = 0.0
    one_dp: float | None = None

    def finish(self) -> None:
        self.zipf = zipf_from_ipm(self.ipm)
        if self.frequency > 0 and self.one_dp_numerator:
            self.one_dp = self.one_dp_numerator / self.frequency


@dataclass(slots=True)
class SourceResult:
    name: str
    records: dict[str, LemmaFrequency]
    rows_loaded: int
    lemma_pos_rows: int
    unique_surface_forms: int | None
    corpus_tokens: int | None
    rejected: Counter[str]
    schema: list[str]

    def assign_ranks(self) -> None:
        ordered = sorted(self.records.values(), key=lambda item: (-item.frequency, item.lemma))
        for rank, record in enumerate(ordered, 1):
            record.rank = rank
            record.finish()

    @property
    def minimum_positive_ipm(self) -> float:
        return min(record.ipm for record in self.records.values() if record.ipm > 0)


def format_number(value: float | int | None) -> str:
    if value is None:
        return ""
    if isinstance(value, int):
        return str(value)
    return f"{value:.12g}"

