from __future__ import annotations

import re
import unicodedata
from dataclasses import asdict, dataclass


@dataclass(frozen=True)
class WordErrorRate:
    reference_words: int
    substitutions: int
    deletions: int
    insertions: int
    error_rate: float

    def to_dict(self) -> dict[str, int | float]:
        return asdict(self)


def word_error_rate(reference: str, hypothesis: str) -> WordErrorRate:
    expected = normalize_words(reference)
    actual = normalize_words(hypothesis)
    rows: list[list[tuple[int, int, int, int]]] = [
        [(index, 0, index, 0) for index in range(len(actual) + 1)]
    ]
    for expected_index, expected_word in enumerate(expected, start=1):
        row = [(expected_index, 0, 0, expected_index)]
        previous = rows[-1]
        for actual_index, actual_word in enumerate(actual, start=1):
            if expected_word == actual_word:
                row.append(previous[actual_index - 1])
                continue
            substitution = increment(previous[actual_index - 1], substitution=1)
            deletion = increment(previous[actual_index], deletion=1)
            insertion = increment(row[actual_index - 1], insertion=1)
            row.append(min((substitution, deletion, insertion), key=lambda value: value[0]))
        rows.append(row)

    errors, substitutions, insertions, deletions = rows[-1][-1]
    denominator = len(expected)
    rate = errors / denominator if denominator else (0.0 if not actual else 1.0)
    return WordErrorRate(
        reference_words=denominator,
        substitutions=substitutions,
        deletions=deletions,
        insertions=insertions,
        error_rate=rate,
    )


def normalize_words(text: str) -> list[str]:
    normalized = unicodedata.normalize("NFKC", text).casefold()
    normalized = re.sub(r"[^\w']+", " ", normalized, flags=re.UNICODE)
    return normalized.split()


def increment(
    value: tuple[int, int, int, int],
    *,
    substitution: int = 0,
    insertion: int = 0,
    deletion: int = 0,
) -> tuple[int, int, int, int]:
    errors, substitutions, insertions, deletions = value
    return (
        errors + substitution + insertion + deletion,
        substitutions + substitution,
        insertions + insertion,
        deletions + deletion,
    )
