"""Greedy-component CSV parser tests."""

from __future__ import annotations

import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from hanzi_data.greedy import parse_greedy_components_csv

SEED = Path(__file__).resolve().parents[1] / "hanzi_data" / "seed" / "greedy_components.csv"

HEADER = (
    "является фонетико-семантическим компонентом,фонетик,汉字,"
    "部件1,部件2,部件3,部件4,部件5,部件6,部件7,部件8,部件9\n"
)


class GreedyComponentsParserTests(unittest.TestCase):
    def _parse(self, body: str) -> dict:
        with tempfile.NamedTemporaryFile("w", encoding="utf-8-sig", suffix=".csv", delete=False) as fh:
            fh.write(HEADER + body)
            path = Path(fh.name)
        try:
            return parse_greedy_components_csv(path)
        finally:
            path.unlink(missing_ok=True)

    def test_parses_pictophonetic_qing(self):
        rows = self._parse("Да,青,清,氵,丰,月,,,,,,\n")
        rec = rows["清"]
        self.assertEqual(["氵", "丰", "月"], rec.components)
        self.assertTrue(rec.is_phonetic_semantic)
        self.assertEqual("青", rec.phonetic)

    def test_parses_non_pictophonetic_xiu(self):
        rows = self._parse("Нет,,休,人,木,,,,,,,\n")
        rec = rows["休"]
        self.assertEqual(["人", "木"], rec.components)
        self.assertFalse(rec.is_phonetic_semantic)
        self.assertIsNone(rec.phonetic)

    def test_phonetic_need_not_be_a_visible_component(self):
        rows = self._parse("Да,意,亿,人,乙,,,,,,,\n")
        rec = rows["亿"]
        self.assertEqual(["人", "乙"], rec.components)
        self.assertTrue(rec.is_phonetic_semantic)
        self.assertEqual("意", rec.phonetic)
        self.assertNotIn("意", rec.components)

    def test_skips_blank_component_cells(self):
        rows = self._parse("Нет,,一,一,,,,,,,,\n")
        self.assertEqual(["一"], rows["一"].components)

    def test_seed_file_covers_3500_and_known_rows(self):
        rows = parse_greedy_components_csv(SEED)
        self.assertEqual(3500, len(rows))
        qing = rows["清"]
        self.assertEqual(["氵", "丰", "月"], qing.components)
        self.assertTrue(qing.is_phonetic_semantic)
        self.assertEqual("青", qing.phonetic)
        ma = rows["吗"]
        self.assertEqual(["口", "马"], ma.components)
        self.assertFalse(ma.is_phonetic_semantic)
        self.assertIsNone(ma.phonetic)


if __name__ == "__main__":
    unittest.main()
