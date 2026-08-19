from __future__ import annotations

from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Any


@dataclass(frozen=True)
class AudioInfo:
    duration_seconds: float
    sample_rate_hz: int
    channels: int
    codec: str


@dataclass(frozen=True)
class FileFingerprint:
    path: str
    sha256: str
    size_bytes: int


@dataclass(frozen=True)
class ToolInfo:
    name: str
    version: str
    parameters: dict[str, str | int | float | bool]


@dataclass(frozen=True)
class TranscriptWord:
    start_seconds: float
    end_seconds: float
    text: str
    probability: float | None


@dataclass(frozen=True)
class TranscriptSegment:
    start_seconds: float
    end_seconds: float
    text: str
    average_log_probability: float | None
    no_speech_probability: float | None
    words: tuple[TranscriptWord, ...]


@dataclass(frozen=True)
class Transcript:
    language: str | None
    language_probability: float | None
    duration_seconds: float | None
    model: str
    engine: str
    engine_version: str
    device: str
    compute_type: str
    vad_enabled: bool
    segments: tuple[TranscriptSegment, ...]


@dataclass(frozen=True)
class ProcessingResult:
    schema_version: int
    processing_version: str
    original: FileFingerprint
    original_audio: AudioInfo
    cleaned: FileFingerprint
    cleaned_audio: AudioInfo
    converter: ToolInfo
    enhancer: ToolInfo
    cleaned_transcript_file: FileFingerprint | None
    cleaned_transcript: Transcript | None
    original_transcript_file: FileFingerprint | None
    original_transcript: Transcript | None

    def to_dict(self) -> dict[str, Any]:
        return asdict(self)


def relative_fingerprint(path: Path, root: Path, sha256: str, size_bytes: int) -> FileFingerprint:
    return FileFingerprint(
        path=str(path.relative_to(root)),
        sha256=sha256,
        size_bytes=size_bytes,
    )
