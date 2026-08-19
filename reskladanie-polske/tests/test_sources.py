import csv
import gzip
import tempfile
import unittest
from pathlib import Path

from src.sources.kwjp import parse_kwjp
from src.sources.subtlex import parse_subtlex


class SourceParserTests(unittest.TestCase):
    def test_subtlex_uses_summary_rows_and_merges_pos(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            raw = root / "subtlex.tsv"
            with raw.open("w", encoding="utf-8", newline="") as stream:
                writer = csv.writer(stream, delimiter="\t")
                writer.writerow(["lemma", "pos", "spelling", "freq", "cd.count", "cd"])
                writer.writerow(["być", "verb", "%", 80, 8, 0.8])
                writer.writerow(["@", "@", "jest", 50, 7, 0.7])
                writer.writerow(["@", "@", "był", 30, 5, 0.5])
                writer.writerow(["być", "subst", "%", 20, 2, 0.2])
                writer.writerow(["@", "@", "byt", 20, 2, 0.2])
                writer.writerow(["i", "conj", "%", 100, 9, 0.9])
                writer.writerow(["@", "@", "i", 100, 9, 0.9])
            result = parse_subtlex(raw, root / "pos.csv", root / "filtered.csv")
            self.assertEqual(result.rows_loaded, 7)
            self.assertEqual(result.unique_surface_forms, 4)
            self.assertEqual(result.corpus_tokens, 200)
            self.assertEqual(result.records["być"].frequency, 100)
            self.assertEqual(result.records["być"].pos_frequency, {"verb": 80, "subst": 20})
            self.assertAlmostEqual(result.records["być"].ipm, 500_000)
            self.assertEqual(result.records["być"].lowercase_frequency, 100)

    def test_kwjp_merges_multiple_pos_for_one_lemma(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            raw = root / "kwjp.csv.gz"
            with gzip.open(raw, "wt", encoding="utf-8", newline="") as stream:
                writer = csv.writer(stream)
                writer.writerow(["", "", "freq", "ipm", "ARF", "DP", "DP_norm", "1-DP", "total_freq"])
                writer.writerow(["to", "subst", 60, 6, 30, 0.2, 0.2, 0.8, 60])
                writer.writerow(["to", "pred", 40, 4, 10, 0.5, 0.5, 0.5, 40])
                writer.writerow(["i", "conj", 10, 1, 5, 0.1, 0.1, 0.9, 10])
            result = parse_kwjp(raw, root / "pos.csv", root / "filtered.csv")
            record = result.records["to"]
            self.assertEqual(record.frequency, 100)
            self.assertEqual(record.ipm, 10)
            self.assertEqual(record.pos_frequency, {"subst": 60, "pred": 40})
            self.assertAlmostEqual(record.one_dp or 0, 0.68)


if __name__ == "__main__":
    unittest.main()
