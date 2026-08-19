import unittest

from src.normalize import normalize_lemma


class NormalizeTests(unittest.TestCase):
    def test_polish_diacritics_are_nfc_and_preserved(self) -> None:
        result = normalize_lemma("  ZAZ\u0307O\u0301\u0141C\u0301  ")
        self.assertEqual(result.lemma, "zażółć")

    def test_one_letter_function_words_are_preserved(self) -> None:
        for lemma in ("a", "i", "o", "w", "z"):
            with self.subTest(lemma=lemma):
                self.assertEqual(normalize_lemma(lemma).lemma, lemma)

    def test_numbers_are_removed(self) -> None:
        for token in ("123", "1 234", "3,14", "+48"):
            with self.subTest(token=token):
                result = normalize_lemma(token)
                self.assertIsNone(result.lemma)
                self.assertEqual(result.rejection_reason, "number")


if __name__ == "__main__":
    unittest.main()
