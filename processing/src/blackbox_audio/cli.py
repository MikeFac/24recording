from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

from .adapters.enhancers import DeepFilterNetEnhancer, PassthroughEnhancer
from .adapters.ffmpeg import FfmpegAudioConverter
from .adapters.whisper import FasterWhisperTranscriber, WhisperConfig
from .pipeline import ProcessAudio, ProcessingConfig, ProcessingError
from .quality import word_error_rate


def parser() -> argparse.ArgumentParser:
    result = argparse.ArgumentParser(
        prog="blackbox-audio",
        description="Create a cleaned derivative and optional Whisper transcript without modifying the original audio.",
    )
    subcommands = result.add_subparsers(dest="command", required=True)
    process = subcommands.add_parser("process", help="Process one audio chunk")
    process.add_argument("input", type=Path)
    process.add_argument("--output-dir", required=True, type=Path)
    process.add_argument(
        "--enhancer",
        choices=("deepfilternet", "passthrough"),
        default="deepfilternet",
        help="passthrough is for pipeline diagnostics only",
    )
    process.add_argument("--skip-transcription", action="store_true")
    process.add_argument(
        "--compare-original",
        action="store_true",
        help="also transcribe the original for quality comparison",
    )
    process.add_argument("--model", default="small")
    process.add_argument("--device", default="cpu")
    process.add_argument("--compute-type", default="int8")
    process.add_argument("--language", default=None)
    evaluate = subcommands.add_parser(
        "evaluate", help="Compare original and cleaned transcripts with reference text"
    )
    evaluate.add_argument("--reference", required=True, type=Path)
    evaluate.add_argument("--original-transcript", required=True, type=Path)
    evaluate.add_argument("--cleaned-transcript", required=True, type=Path)
    return result


def main(argv: list[str] | None = None) -> int:
    args = parser().parse_args(argv)
    if args.command == "evaluate":
        return evaluate_transcripts(
            args.reference, args.original_transcript, args.cleaned_transcript
        )
    if args.command != "process":
        return 2
    try:
        enhancer = (
            DeepFilterNetEnhancer() if args.enhancer == "deepfilternet" else PassthroughEnhancer()
        )
        transcriber = None
        if not args.skip_transcription:
            transcriber = FasterWhisperTranscriber(
                WhisperConfig(
                    model=args.model,
                    device=args.device,
                    compute_type=args.compute_type,
                    language=args.language,
                )
            )
        result = ProcessAudio(
            converter=FfmpegAudioConverter(),
            enhancer=enhancer,
            transcriber=transcriber,
            config=ProcessingConfig(transcribe_original_for_comparison=args.compare_original),
        ).execute(args.input, args.output_dir)
        print(json.dumps(result.to_dict(), indent=2, sort_keys=True))
        return 0
    except (ProcessingError, RuntimeError) as error:
        print(f"error: {error}", file=sys.stderr)
        return 1


def evaluate_transcripts(reference: Path, original: Path, cleaned: Path) -> int:
    try:
        reference_text = reference.read_text(encoding="utf-8")
        original_text = transcript_text(original)
        cleaned_text = transcript_text(cleaned)
        original_score = word_error_rate(reference_text, original_text)
        cleaned_score = word_error_rate(reference_text, cleaned_text)
        report = {
            "reference": str(reference),
            "original": original_score.to_dict(),
            "cleaned": cleaned_score.to_dict(),
            "cleaning_improved_word_error_rate": (
                cleaned_score.error_rate < original_score.error_rate
            ),
            "absolute_error_rate_change": (cleaned_score.error_rate - original_score.error_rate),
        }
        print(json.dumps(report, indent=2, sort_keys=True))
        return 0
    except (OSError, KeyError, TypeError, json.JSONDecodeError) as error:
        print(f"error: could not evaluate transcripts: {error}", file=sys.stderr)
        return 1


def transcript_text(path: Path) -> str:
    payload = json.loads(path.read_text(encoding="utf-8"))
    return " ".join(str(segment["text"]) for segment in payload["segments"])


if __name__ == "__main__":
    raise SystemExit(main())
