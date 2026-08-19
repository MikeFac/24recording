from __future__ import annotations

import unittest

from blackbox_audio.quality import normalize_words, word_error_rate


class WordErrorRateTest(unittest.TestCase):
    def test_normalizes_case_and_punctuation(self) -> None:
        self.assertEqual(
            ["hello", "michael's", "world"], normalize_words("Hello, Michael's WORLD!")
        )

    def test_counts_substitution(self) -> None:
        score = word_error_rate("one two", "one too")
        self.assertEqual(1, score.substitutions)
        self.assertEqual(0.5, score.error_rate)

    def test_counts_deletion(self) -> None:
        score = word_error_rate("one two", "one")
        self.assertEqual(1, score.deletions)
        self.assertEqual(0.5, score.error_rate)

    def test_counts_insertion(self) -> None:
        score = word_error_rate("one", "one two")
        self.assertEqual(1, score.insertions)
        self.assertEqual(1.0, score.error_rate)

    def test_empty_reference_is_well_defined(self) -> None:
        self.assertEqual(0.0, word_error_rate("", "").error_rate)
        self.assertEqual(1.0, word_error_rate("", "unexpected").error_rate)


if __name__ == "__main__":
    unittest.main()
