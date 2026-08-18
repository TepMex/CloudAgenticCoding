from __future__ import annotations

import csv
import io
import tempfile
import unittest
from pathlib import Path

from anki_enricher.pinyin import FINALS, INITIALS, parse_first_syllable
from anki_enricher.related import (
    RelatedEnrichmentError,
    enrich_documents,
    load_tsv,
    write_tsv,
)


def write_headered(path: Path, rows: list[list[str]]) -> None:
    stream = io.StringIO(newline="")
    writer = csv.writer(stream, delimiter="\t", lineterminator="\n")
    writer.writerow(["Key", "Simplified", "Pinyin", "Other"])
    writer.writerows(rows)
    path.write_text(stream.getvalue(), encoding="utf-8")


class PinyinParserTests(unittest.TestCase):
    def coordinate(self, value: str) -> tuple[str, str]:
        parsed = parse_first_syllable(value)
        self.assertIsNotNone(parsed)
        assert parsed is not None
        return parsed.initial, parsed.final

    def test_tones_share_coordinate_and_first_syllable_is_extracted(self) -> None:
        expected = {("zh", "ang")}
        self.assertEqual(
            expected,
            {self.coordinate(value) for value in ("zhāng", "zháng", "zhǎngwò", "zhàng")},
        )
        self.assertEqual(("", "i"), self.coordinate("yị̄bèizi"))
        self.assertEqual(("b", "u"), self.coordinate("bụ̀ yàojǐn"))

    def test_umlaut_spellings_and_jqx_orthography(self) -> None:
        self.assertEqual(("l", "ü"), self.coordinate("lü"))
        self.assertEqual(("l", "ü"), self.coordinate("lǜ"))
        self.assertEqual(("l", "ü"), self.coordinate("lu:4"))
        self.assertEqual(("l", "ü"), self.coordinate("lv4"))
        for value, final in (("ju", "ü"), ("jue", "üe"), ("juan", "üan"), ("jun", "ün"), ("xue", "üe")):
            self.assertEqual((value[0], final), self.coordinate(value))

    def test_y_and_w_are_zero_initial_orthographic_markers(self) -> None:
        expected = {
            "yi": "i", "ying": "ing", "you": "iu", "yuan": "üan",
            "wu": "u", "wen": "un", "wai": "uai",
        }
        for value, final in expected.items():
            self.assertEqual(("", final), self.coordinate(value))

    def test_apical_i_has_distinct_internal_coordinate(self) -> None:
        for value in ("zhi", "chi", "shi", "ri", "zi", "ci", "si"):
            self.assertEqual("apical-i", self.coordinate(value)[1])
        self.assertEqual(("j", "i"), self.coordinate("ji"))

    def test_layout_matches_map_of_chinese_order(self) -> None:
        self.assertEqual(("", "b", "p", "m", "f"), INITIALS[:5])
        self.assertEqual(("a", "ai", "an", "ang", "ao", "o", "e"), FINALS[:7])


class GlobalHistoryTests(unittest.TestCase):
    def test_future_words_are_never_visible(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "deck.tsv"
            write_headered(
                path,
                [["1", "A", "zhāng", ""], ["2", "B", "zhǎng", ""], ["3", "C", "zhàng", ""]],
            )
            document = load_tsv(path)
            enrich_documents([document])
            by_key = {note.key: note for note in document.records}
            self.assertNotIn("pm-word\">B", by_key[1].matrix)
            self.assertIn("pm-current\">B", by_key[2].matrix)
            self.assertIn("pm-word\">A", by_key[2].matrix)
            self.assertNotIn("pm-word\">C", by_key[2].matrix)
            self.assertIn("pm-word\">A", by_key[3].matrix)
            self.assertIn("pm-word\">B", by_key[3].matrix)

    def test_history_crosses_deck_boundary_and_ignores_file_order(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            first = root / "a.tsv"
            second = root / "b.tsv"
            write_headered(first, [["999", "旧", "zhāng", ""], ["1000", "前", "zhǎng", ""]])
            write_headered(second, [["1001", "新", "zhàng", ""], ["1002", "后", "zhāng", ""]])
            docs = [load_tsv(second), load_tsv(first)]
            enrich_documents(docs)
            by_key = {note.key: note.matrix for doc in docs for note in doc.records}
            self.assertIn("旧", by_key[1001])
            self.assertIn("前", by_key[1001])
            self.assertNotIn("后", by_key[1001])

    def test_same_hanzi_lists_each_matching_previous_card_once_in_key_order(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "deck.tsv"
            write_headered(
                path,
                [["1", "学校", "xué", ""], ["2", "学生", "xué", ""], ["3", "生活", "shēng", ""], ["4", "学生学校", "xué", ""]],
            )
            document = load_tsv(path)
            enrich_documents([document])
            by_key = {note.key: note.same_hanzi for note in document.records}
            self.assertEqual("", by_key[1])
            self.assertEqual("学校", by_key[2])
            self.assertEqual("学生", by_key[3])
            self.assertEqual("学校, 学生, 生活", by_key[4])

    def test_duplicate_key_across_files_is_an_error(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            first, second = root / "a.tsv", root / "b.tsv"
            write_headered(first, [["1", "一", "yī", ""]])
            write_headered(second, [["1", "壹", "yī", ""]])
            with self.assertRaisesRegex(RelatedEnrichmentError, "duplicate Key=1"):
                enrich_documents([load_tsv(first), load_tsv(second)])


class RendererTests(unittest.TestCase):
    def test_matrix_is_always_three_by_three_at_map_boundary(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "deck.tsv"
            write_headered(path, [["1", "啊", "ā", ""]])
            document = load_tsv(path)
            enrich_documents([document])
            matrix = document.records[0].matrix
            self.assertEqual(9, matrix.count('class="pm-cell'))
            self.assertEqual(3, matrix.count('class="pm-final"'))
            self.assertEqual(3, matrix.count('class="pm-initial"'))
            self.assertIn("pm-cell--empty", matrix)

    def test_all_previous_words_are_rendered(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "deck.tsv"
            rows = [[str(key), str(key), "mā", ""] for key in range(1, 7)]
            write_headered(path, rows)
            document = load_tsv(path)
            enrich_documents([document])
            matrix = document.records[-1].matrix
            self.assertEqual(5, matrix.count('class="pm-word"'))
            for key in range(1, 6):
                self.assertIn(f'class="pm-word">{key}</span>', matrix)
            self.assertNotIn('class="pm-more"', matrix)

    def test_tsv_values_are_html_escaped(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "deck.tsv"
            write_headered(path, [["1", "<b>张</b>&", "zhāng", ""]])
            document = load_tsv(path)
            enrich_documents([document])
            matrix = document.records[0].matrix
            self.assertIn("&lt;b&gt;张&lt;/b&gt;&amp;", matrix)
            self.assertNotIn("<b>张</b>", matrix)

    def test_bad_pinyin_skips_only_that_map(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "deck.tsv"
            write_headered(path, [["1", "坏", "???", ""], ["2", "好", "hǎo", ""]])
            document = load_tsv(path)
            result = enrich_documents([document])
            self.assertEqual("", document.records[0].matrix)
            self.assertTrue(document.records[1].matrix)
            self.assertEqual(1, result.skipped)
            self.assertEqual(1, len(result.warnings))


class TsvTests(unittest.TestCase):
    def test_headerless_anki_export_preserves_metadata_and_multiline_fields(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / "deck.txt"
            source.write_text(
                '#separator:tab\n#html:true\n1\t学校\t學校\txuéxiào\t"line one\nline two"\n',
                encoding="utf-8",
            )
            document = load_tsv(source)
            enrich_documents([document])
            destination = root / "result.tsv"
            write_tsv(document, destination)
            output = destination.read_text(encoding="utf-8")
            self.assertTrue(output.startswith("#separator:tab\n#html:true\n"))
            loaded_again = load_tsv(destination)
            self.assertEqual(1, len(loaded_again.records))
            self.assertEqual(document.same_hanzi_index, loaded_again.same_hanzi_index)
            self.assertEqual(document.matrix_index, loaded_again.matrix_index)
            self.assertTrue(loaded_again.records[0].matrix)

    def test_headered_tsv_adds_named_fields_and_overwrites_them_on_rerun(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "deck.tsv"
            write_headered(path, [["1", "一", "yī", "kept"]])
            document = load_tsv(path)
            enrich_documents([document])
            destination = Path(directory) / "out.tsv"
            write_tsv(document, destination)
            again = load_tsv(destination)
            enrich_documents([again])
            self.assertEqual(
                ["Key", "Simplified", "Pinyin", "Other", "SameHanzi", "SamePinyinMatrix"],
                again.rows[0],
            )
            self.assertEqual("kept", again.records[0].row[3])

    def test_empty_and_non_numeric_keys_are_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "deck.tsv"
            write_headered(path, [["", "一", "yī", ""]])
            with self.assertRaisesRegex(RelatedEnrichmentError, "empty Key"):
                load_tsv(path)
            write_headered(path, [["one", "一", "yī", ""]])
            with self.assertRaisesRegex(RelatedEnrichmentError, "non-numeric Key"):
                load_tsv(path)


if __name__ == "__main__":
    unittest.main()
