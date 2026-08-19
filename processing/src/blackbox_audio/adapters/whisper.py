from __future__ import annotations

from dataclasses import dataclass
from importlib.metadata import version
from pathlib import Path

from ..domain import Transcript, TranscriptSegment, TranscriptWord


@dataclass(frozen=True)
class WhisperConfig:
    model: str = "small"
    device: str = "cpu"
    compute_type: str = "int8"
    language: str | None = None


class FasterWhisperTranscriber:
    def __init__(self, config: WhisperConfig) -> None:
        try:
            from faster_whisper import WhisperModel
        except ImportError as error:
            raise RuntimeError(
                "faster-whisper is not installed; install the transcription extra"
            ) from error
        self._config = config
        self._engine_version = version("faster-whisper")
        self._model = WhisperModel(
            config.model,
            device=config.device,
            compute_type=config.compute_type,
        )

    def transcribe(self, source: Path) -> Transcript:
        segments_iterator, info = self._model.transcribe(
            str(source),
            language=self._config.language,
            beam_size=5,
            condition_on_previous_text=False,
            word_timestamps=True,
            vad_filter=True,
            vad_parameters={
                "min_silence_duration_ms": 500,
                "speech_pad_ms": 500,
            },
        )
        segments = []
        for segment in segments_iterator:
            words = tuple(
                TranscriptWord(
                    start_seconds=float(word.start),
                    end_seconds=float(word.end),
                    text=str(word.word),
                    probability=optional_float(getattr(word, "probability", None)),
                )
                for word in (segment.words or [])
            )
            segments.append(
                TranscriptSegment(
                    start_seconds=float(segment.start),
                    end_seconds=float(segment.end),
                    text=str(segment.text).strip(),
                    average_log_probability=optional_float(getattr(segment, "avg_logprob", None)),
                    no_speech_probability=optional_float(getattr(segment, "no_speech_prob", None)),
                    words=words,
                )
            )
        return Transcript(
            language=getattr(info, "language", None),
            language_probability=optional_float(getattr(info, "language_probability", None)),
            duration_seconds=optional_float(getattr(info, "duration", None)),
            model=self._config.model,
            engine="faster-whisper",
            engine_version=self._engine_version,
            device=self._config.device,
            compute_type=self._config.compute_type,
            vad_enabled=True,
            segments=tuple(segments),
        )


def optional_float(value: object) -> float | None:
    return None if value is None else float(value)
