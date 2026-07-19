"""Structural IDS differ tests against fixture trees (not scholarly claims)."""

from __future__ import annotations

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from hanzi_data.ids_diff import compare_ids_trees, parse_ids


class IdsDiffTests(unittest.TestCase):
    def test_parse_left_right(self):
        tree = parse_ids("\u2ff0\u8a00\u514c")  # ⿰言兌
        assert tree is not None
        self.assertEqual(tree.value, "\u2ff0")
        self.assertEqual([c.value for c in tree.children], ["\u8a00", "\u514c"])

    def test_unchanged(self):
        r = compare_ids_trees(
            "\u2ff0\u5973\u5b50",
            "\u2ff0\u5973\u5b50",
            traditional_char="\u597d",
            simplified_char="\u597d",
        )
        self.assertEqual(r.classification, "UNCHANGED")

    def test_single_component_replacement(self):
        r = compare_ids_trees(
            "\u2ff0\u8a00\u9752",
            "\u2ff0\u8ba0\u9752",
            traditional_char="\u8acb",
            simplified_char="\u8bf7",
        )
        self.assertEqual(r.classification, "SINGLE_COMPONENT_REPLACEMENT")
        self.assertEqual(len(r.changed_components), 1)
        self.assertEqual(r.changed_components[0].traditional_component, "\u8a00")
        self.assertEqual(r.changed_components[0].simplified_component, "\u8ba0")
        self.assertEqual(r.changed_components[0].path, [0])
        self.assertEqual(r.evidence_type, "derived")

    def test_multiple_component_replacements(self):
        r = compare_ids_trees(
            "\u2ff0\u8a00\u514c",
            "\u2ff0\u8ba0\u5151",
            traditional_char="\u8aaa",
            simplified_char="\u8bf4",
        )
        self.assertEqual(r.classification, "MULTIPLE_COMPONENT_REPLACEMENTS")
        self.assertGreaterEqual(len(r.changed_components), 2)

    def test_changed_root_structure(self):
        r = compare_ids_trees(
            "\u2ff0\u8033\u2d873",
            "\u2ff0\u53e3\u65a4",
            traditional_char="\u807d",
            simplified_char="\u542c",
        )
        # Even if traditional IDS is odd, different roots / mismatched trees => structure change or unknown
        self.assertIn(
            r.classification,
            {
                "STRUCTURE_CHANGED_OR_WHOLE_CHARACTER_REPLACEMENT",
                "UNKNOWN",
            },
        )

    def test_changed_root_different_operators(self):
        r = compare_ids_trees(
            "\u2ff1\u65e5\u6708",
            "\u2ff0\u53e3\u65a4",
            traditional_char="A",
            simplified_char="B",
        )
        self.assertEqual(
            r.classification,
            "STRUCTURE_CHANGED_OR_WHOLE_CHARACTER_REPLACEMENT",
        )

    def test_missing_decomposition(self):
        r = compare_ids_trees(
            None,
            "\u2ff0\u53e3\u65a4",
            traditional_char="\u807d",
            simplified_char="\u542c",
        )
        self.assertEqual(r.classification, "UNKNOWN")

    def test_ambiguous_mapping(self):
        r = compare_ids_trees(
            "\u2ff1\u767a\u5f13",
            "\u2ff8\u2ff1\u4e3f\u53cb\u4e36",
            traditional_char="\u767c",
            simplified_char="\u53d1",
            ambiguous_mapping=True,
        )
        self.assertEqual(r.classification, "AMBIGUOUS_VARIANT_MAPPING")

    def test_identical_char_shortcut(self):
        r = compare_ids_trees(
            None,
            None,
            traditional_char="\u6728",
            simplified_char="\u6728",
        )
        self.assertEqual(r.classification, "UNCHANGED")


if __name__ == "__main__":
    unittest.main()
