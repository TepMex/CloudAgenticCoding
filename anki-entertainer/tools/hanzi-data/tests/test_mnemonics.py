"""Mnemonic ranking and provider tests."""

from __future__ import annotations

import json
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from hanzi_data.mnemonics import (
    JsonMnemonicProvider,
    import_mnemonics,
    nearly_identical,
    normalize_provider_rows,
    normalize_story,
    rank_and_dedupe,
    truncate_code_points,
)


class MnemonicTests(unittest.TestCase):
    def test_whitespace_normalization(self):
        self.assertEqual(normalize_story("  a\n\tb  "), "a b")

    def test_length_limit(self):
        long = "汉" * 600
        self.assertEqual(len(list(truncate_code_points(long))), 500)

    def test_top_five_and_ranking(self):
        rows = []
        for i in range(8):
            rows.append(
                {
                    "character": "休",
                    "story": f"Story number {i}",
                    "normalized_score": float(i),
                    "source_record_id": f"id:{i}",
                }
            )
        records = normalize_provider_rows(
            rows,
            source="t",
            source_priority=1,
            default_license="CC0-1.0",
            default_attribution="t",
        )
        ranked = rank_and_dedupe(records)
        self.assertEqual(len(ranked), 5)
        scores = [r.normalized_score for r in ranked]
        self.assertEqual(scores, sorted(scores, reverse=True))

    def test_stable_tie_break(self):
        rows = [
            {
                "character": "明",
                "story": "A",
                "normalized_score": 1,
                "source": "b_source",
                "source_record_id": "2",
            },
            {
                "character": "明",
                "story": "B",
                "normalized_score": 1,
                "source": "a_source",
                "source_record_id": "1",
            },
        ]
        records = normalize_provider_rows(
            rows,
            source="default",
            source_priority=1,
            default_license="CC0-1.0",
            default_attribution="t",
        )
        ranked = rank_and_dedupe(records)
        self.assertEqual(ranked[0].source, "a_source")

    def test_dedup_near_identical(self):
        self.assertTrue(nearly_identical("A person rests.", "A person rests"))
        rows = [
            {"character": "休", "story": "A person rests.", "normalized_score": 5},
            {"character": "休", "story": "A person rests", "normalized_score": 4},
        ]
        records = normalize_provider_rows(
            rows,
            source="t",
            source_priority=1,
            default_license="CC0-1.0",
            default_attribution="t",
        )
        ranked = rank_and_dedupe(records)
        self.assertEqual(len(ranked), 1)

    def test_missing_source_score(self):
        rows = [{"character": "木", "story": "tree"}]
        records = normalize_provider_rows(
            rows,
            source="t",
            source_priority=1,
            default_license="CC0-1.0",
            default_attribution="t",
        )
        self.assertEqual(records[0].normalized_score, 0.0)
        self.assertIsNone(records[0].raw_score)

    def test_multiple_providers(self):
        with tempfile.TemporaryDirectory() as td:
            p1 = Path(td) / "a.json"
            p2 = Path(td) / "b.json"
            p1.write_text(
                json.dumps(
                    [
                        {
                            "character": "日",
                            "story": "sun from a",
                            "normalized_score": 1,
                        }
                    ]
                ),
                encoding="utf-8",
            )
            p2.write_text(
                json.dumps(
                    [
                        {
                            "character": "日",
                            "story": "sun from b",
                            "normalized_score": 10,
                        }
                    ]
                ),
                encoding="utf-8",
            )
            out = import_mnemonics(
                [
                    JsonMnemonicProvider(p1, name="a", source_priority=10),
                    JsonMnemonicProvider(p2, name="b", source_priority=20),
                ]
            )
            self.assertEqual(out[0].story, "sun from b")

    def test_compound_word_keys_accepted(self):
        from hanzi_data.mnemonics import is_valid_mnemonic_key

        self.assertTrue(is_valid_mnemonic_key("休"))
        self.assertTrue(is_valid_mnemonic_key("休息"))
        self.assertTrue(is_valid_mnemonic_key("明白"))
        self.assertFalse(is_valid_mnemonic_key(""))
        self.assertFalse(is_valid_mnemonic_key("休 rest"))
        self.assertFalse(is_valid_mnemonic_key("hello"))
        self.assertFalse(is_valid_mnemonic_key("汉" * 21))

        rows = [
            {
                "character": "休息",
                "story": "Compound rest story",
                "normalized_score": 100,
                "source_record_id": "c:1",
            },
            {
                "character": "休",
                "story": "Single char story",
                "normalized_score": 50,
                "source_record_id": "c:2",
            },
            {
                "character": "not-hanzi",
                "story": "rejected",
                "normalized_score": 1,
            },
        ]
        records = normalize_provider_rows(
            rows,
            source="t",
            source_priority=1,
            default_license="CC0-1.0",
            default_attribution="t",
        )
        keys = {r.character for r in records}
        self.assertEqual(keys, {"休息", "休"})
        ranked = rank_and_dedupe(records)
        by_key = {r.character: r.story for r in ranked}
        self.assertEqual(by_key["休息"], "Compound rest story")
        self.assertEqual(by_key["休"], "Single char story")


if __name__ == "__main__":
    unittest.main()
