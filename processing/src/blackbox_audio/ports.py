from __future__ import annotations

from pathlib import Path
from typing import Protocol

from .domain import AudioInfo, ToolInfo, Transcript


class AudioConverter(Protocol):
    @property
    def info(self) -> ToolInfo: ...

    def decode_for_enhancement(self, source: Path, destination: Path) -> None: ...

    def create_speech_derivative(self, source: Path, destination: Path) -> None: ...

    def probe(self, source: Path) -> AudioInfo: ...


class SpeechEnhancer(Protocol):
    @property
    def info(self) -> ToolInfo: ...

    def enhance(self, source: Path, destination: Path) -> None: ...


class Transcriber(Protocol):
    def transcribe(self, source: Path) -> Transcript: ...
