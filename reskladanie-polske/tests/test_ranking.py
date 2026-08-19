import unittest
from collections import Counter

from src.build_frequency_list import rank_union
from src.ranking import combine_scores
from src.sources.models import LemmaFrequency, SourceResult


def source(name: str, values: dict[str, float]) -> SourceResult:
    records = {}
    for lemma, ipm in values.items():
        record = LemmaFrequency(lemma=lemma, frequency=ipm, ipm=ipm)
        records[lemma] = record
    result = SourceResult(name, records, len(records), len(records), None, None, Counter(), [])
    result.assign_ranks()
    return result


class RankingTests(unittest.TestCase):
    def test_missing_source_uses_explicit_floor(self) -> None:
        score = combine_scores(
            subtlex_zipf=6,
            kwjp_zipf=None,
            subtlex_rank=1,
            kwjp_rank=None,
            subtlex_population=2,
            kwjp_population=2,
            subtlex_missing_floor=0,
            kwjp_missing_floor=1,
            subtlex_weight=0.65,
            kwjp_weight=0.35,
        )
        self.assertAlmostEqual(score.zipf_weighted, 4.25)
        self.assertAlmostEqual(score.percentile_weighted, 0.65)

    def test_union_has_no_duplicates_and_ranking_is_stable(self) -> None:
        subtlex = source("subtlex", {"a": 100, "wspólna": 10})
        kwjp = source("kwjp", {"wspólna": 10, "z": 1})
        first, _, _ = rank_union(subtlex, kwjp, subtlex_weight=0.65, kwjp_weight=0.35)
        second, _, _ = rank_union(subtlex, kwjp, subtlex_weight=0.65, kwjp_weight=0.35)
        lemmas = [row[2] for row in first]
        self.assertEqual(first, second)
        self.assertEqual(len(lemmas), len(set(lemmas)))
        self.assertEqual(set(lemmas), {"a", "wspólna", "z"})


if __name__ == "__main__":
    unittest.main()
