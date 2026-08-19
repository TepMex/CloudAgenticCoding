from __future__ import annotations

import math
from dataclasses import dataclass
from typing import Iterable


def zipf_from_ipm(ipm: float | None) -> float | None:
    if ipm is None or ipm <= 0:
        return None
    return math.log10(ipm) + 3.0


def missing_zipf_floor(ipms: Iterable[float]) -> float:
    """One log decade below the smallest released positive frequency."""

    minimum = min(value for value in ipms if value > 0)
    return zipf_from_ipm(minimum) - 1.0  # type: ignore[operator]


def rank_percentile(rank: int | None, population: int) -> float:
    if rank is None or population <= 0:
        return 0.0
    return 1.0 - ((rank - 1) / population)


@dataclass(frozen=True, slots=True)
class Score:
    zipf_weighted: float
    percentile_weighted: float


def combine_scores(
    *,
    subtlex_zipf: float | None,
    kwjp_zipf: float | None,
    subtlex_rank: int | None,
    kwjp_rank: int | None,
    subtlex_population: int,
    kwjp_population: int,
    subtlex_missing_floor: float,
    kwjp_missing_floor: float,
    subtlex_weight: float,
    kwjp_weight: float,
) -> Score:
    """Combine comparable log frequencies and an alternative rank fusion.

    Missing values use a documented censoring floor for the Zipf score and zero
    evidence for percentile fusion; they are never imputed with an average.
    """

    primary = (
        subtlex_weight
        * (subtlex_zipf if subtlex_zipf is not None else subtlex_missing_floor)
        + kwjp_weight * (kwjp_zipf if kwjp_zipf is not None else kwjp_missing_floor)
    )
    alternative = (
        subtlex_weight * rank_percentile(subtlex_rank, subtlex_population)
        + kwjp_weight * rank_percentile(kwjp_rank, kwjp_population)
    )
    return Score(primary, alternative)


def validate_weights(subtlex_weight: float, kwjp_weight: float) -> None:
    if subtlex_weight < 0 or kwjp_weight < 0:
        raise ValueError("source weights must be non-negative")
    if not math.isclose(subtlex_weight + kwjp_weight, 1.0, abs_tol=1e-9):
        raise ValueError("source weights must sum to 1.0")
