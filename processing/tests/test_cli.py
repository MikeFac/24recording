from __future__ import annotations

import contextlib
import io
import json
import tempfile
import unittest
from pathlib import Path

from blackbox_audio.cli import main


class EvaluateCommandTest(unittest.TestCase):
    def test_reports_when_cleaning_improves_word_error_rate(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            reference = root / "reference.txt"
            original = root / "original.json"
            cleaned = root / "cleaned.json"
            reference.write_text("the quick brown fox", encoding="utf-8")
            original.write_text(
                json.dumps({"segments": [{"text": "the slow fox"}]}), encoding="utf-8"
            )
            cleaned.write_text(
                json.dumps({"segments": [{"text": "the quick brown fox"}]}), encoding="utf-8"
            )

            output = io.StringIO()
            with contextlib.redirect_stdout(output):
                exit_code = main(
                    [
                        "evaluate",
                        "--reference",
                        str(reference),
                        "--original-transcript",
                        str(original),
                        "--cleaned-transcript",
                        str(cleaned),
                    ]
                )

            report = json.loads(output.getvalue())
            self.assertEqual(0, exit_code)
            self.assertTrue(report["cleaning_improved_word_error_rate"])
            self.assertEqual(0.0, report["cleaned"]["error_rate"])


if __name__ == "__main__":
    unittest.main()
