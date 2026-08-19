from __future__ import annotations

import hashlib
import json
import os
import shutil
import tempfile
from dataclasses import dataclass
from pathlib import Path

from .domain import FileFingerprint, ProcessingResult, relative_fingerprint
from .ports import AudioConverter, SpeechEnhancer, Transcriber


class ProcessingError(RuntimeError):
    """A processing job failed without publishing a partial result."""


@dataclass(frozen=True)
class ProcessingConfig:
    processing_version: str = "cleanup-proof-v1"
    maximum_duration_drift_seconds: float = 0.25
    transcribe_original_for_comparison: bool = False


class ProcessAudio:
    def __init__(
        self,
        converter: AudioConverter,
        enhancer: SpeechEnhancer,
        transcriber: Transcriber | None,
        config: ProcessingConfig | None = None,
    ) -> None:
        self._converter = converter
        self._enhancer = enhancer
        self._transcriber = transcriber
        self._config = config or ProcessingConfig()

    def execute(self, source: Path, output_directory: Path) -> ProcessingResult:
        source = source.expanduser().resolve()
        output_directory = output_directory.expanduser().resolve()
        self._validate_paths(source, output_directory)

        original_before = fingerprint(source)
        original_info = self._converter.probe(source)
        output_directory.parent.mkdir(parents=True, exist_ok=True)

        staging = Path(
            tempfile.mkdtemp(
                prefix=f".{output_directory.name}.",
                suffix=".staging",
                dir=output_directory.parent,
            )
        )
        try:
            decoded = staging / "decoded-48k-mono.wav"
            enhanced = staging / "enhanced-48k-mono.wav"
            cleaned = staging / "cleaned-16k-mono.flac"

            self._converter.decode_for_enhancement(source, decoded)
            self._enhancer.enhance(decoded, enhanced)
            self._converter.create_speech_derivative(enhanced, cleaned)

            cleaned_info = self._converter.probe(cleaned)
            self._verify_duration(original_info.duration_seconds, cleaned_info.duration_seconds)

            cleaned_transcript = (
                self._transcriber.transcribe(cleaned) if self._transcriber else None
            )
            cleaned_transcript_fingerprint = self._write_transcript(
                staging, "cleaned-transcript.json", cleaned_transcript
            )
            original_transcript = None
            if self._transcriber and self._config.transcribe_original_for_comparison:
                original_transcript = self._transcriber.transcribe(source)
            original_transcript_fingerprint = self._write_transcript(
                staging, "original-transcript.json", original_transcript
            )

            original_after = fingerprint(source)
            if original_before != original_after:
                raise ProcessingError("Original audio changed while it was being processed")

            result = ProcessingResult(
                schema_version=1,
                processing_version=self._config.processing_version,
                original=original_before,
                original_audio=original_info,
                cleaned=relative_fingerprint(
                    cleaned,
                    staging,
                    sha256_file(cleaned),
                    cleaned.stat().st_size,
                ),
                cleaned_audio=cleaned_info,
                converter=self._converter.info,
                enhancer=self._enhancer.info,
                cleaned_transcript_file=cleaned_transcript_fingerprint,
                cleaned_transcript=cleaned_transcript,
                original_transcript_file=original_transcript_fingerprint,
                original_transcript=original_transcript,
            )
            write_json(staging / "manifest.json", result)

            decoded.unlink()
            enhanced.unlink()
            os.replace(staging, output_directory)
            return result
        except Exception as error:
            shutil.rmtree(staging, ignore_errors=True)
            if isinstance(error, ProcessingError):
                raise
            raise ProcessingError(str(error)) from error

    @staticmethod
    def _write_transcript(
        staging: Path, filename: str, transcript: object | None
    ) -> FileFingerprint | None:
        if transcript is None:
            return None
        transcript_path = staging / filename
        write_json(transcript_path, transcript)
        return relative_fingerprint(
            transcript_path,
            staging,
            sha256_file(transcript_path),
            transcript_path.stat().st_size,
        )

    @staticmethod
    def _validate_paths(source: Path, output_directory: Path) -> None:
        if not source.is_file():
            raise ProcessingError(f"Input audio does not exist or is not a file: {source}")
        if output_directory.exists():
            raise ProcessingError(f"Output directory already exists: {output_directory}")
        if output_directory == source or source in output_directory.parents:
            raise ProcessingError("Output directory cannot contain or replace the original audio")

    def _verify_duration(self, original_seconds: float, cleaned_seconds: float) -> None:
        drift = abs(original_seconds - cleaned_seconds)
        if drift > self._config.maximum_duration_drift_seconds:
            raise ProcessingError(
                "Cleaned derivative duration drifted by "
                f"{drift:.3f}s (maximum {self._config.maximum_duration_drift_seconds:.3f}s)"
            )


def fingerprint(path: Path) -> FileFingerprint:
    return FileFingerprint(
        path=path.name,
        sha256=sha256_file(path),
        size_bytes=path.stat().st_size,
    )


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def write_json(path: Path, value: object) -> None:
    if hasattr(value, "to_dict"):
        value = value.to_dict()  # type: ignore[union-attr]
    elif hasattr(value, "__dataclass_fields__"):
        from dataclasses import asdict

        value = asdict(value)
    path.write_text(json.dumps(value, indent=2, sort_keys=True) + "\n", encoding="utf-8")
