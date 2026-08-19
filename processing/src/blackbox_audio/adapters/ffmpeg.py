from __future__ import annotations

import json
import shutil
import subprocess
from pathlib import Path

from ..domain import AudioInfo, ToolInfo


class FfmpegAudioConverter:
    def __init__(self, ffmpeg: str = "ffmpeg", ffprobe: str = "ffprobe") -> None:
        self._ffmpeg = require_command(ffmpeg)
        self._ffprobe = require_command(ffprobe)
        self._version = command_output([self._ffmpeg, "-version"]).splitlines()[0]

    @property
    def info(self) -> ToolInfo:
        return ToolInfo(
            name="ffmpeg",
            version=self._version,
            parameters={
                "enhancement_sample_rate_hz": 48000,
                "speech_sample_rate_hz": 16000,
                "channels": 1,
                "high_pass_hz": 70,
                "limiter_peak": 0.95,
            },
        )

    def decode_for_enhancement(self, source: Path, destination: Path) -> None:
        run(
            [
                self._ffmpeg,
                "-nostdin",
                "-hide_banner",
                "-loglevel",
                "error",
                "-i",
                str(source),
                "-map",
                "0:a:0",
                "-vn",
                "-ac",
                "1",
                "-ar",
                "48000",
                "-c:a",
                "pcm_s16le",
                str(destination),
            ]
        )

    def create_speech_derivative(self, source: Path, destination: Path) -> None:
        run(
            [
                self._ffmpeg,
                "-nostdin",
                "-hide_banner",
                "-loglevel",
                "error",
                "-i",
                str(source),
                "-map",
                "0:a:0",
                "-vn",
                "-af",
                "highpass=f=70,alimiter=limit=0.95:attack=5:release=50",
                "-ac",
                "1",
                "-ar",
                "16000",
                "-c:a",
                "flac",
                "-compression_level",
                "5",
                str(destination),
            ]
        )

    def probe(self, source: Path) -> AudioInfo:
        output = command_output(
            [
                self._ffprobe,
                "-v",
                "error",
                "-select_streams",
                "a:0",
                "-show_entries",
                "stream=codec_name,sample_rate,channels:format=duration",
                "-of",
                "json",
                str(source),
            ]
        )
        payload = json.loads(output)
        streams = payload.get("streams", [])
        if len(streams) != 1:
            raise RuntimeError(f"Expected one primary audio stream in {source}")
        stream = streams[0]
        duration = payload.get("format", {}).get("duration") or stream.get("duration")
        if duration is None:
            raise RuntimeError(f"Could not determine audio duration for {source}")
        return AudioInfo(
            duration_seconds=float(duration),
            sample_rate_hz=int(stream["sample_rate"]),
            channels=int(stream["channels"]),
            codec=str(stream["codec_name"]),
        )


def require_command(name: str) -> str:
    resolved = shutil.which(name)
    if not resolved:
        raise RuntimeError(f"Required command is not installed: {name}")
    return resolved


def command_output(command: list[str]) -> str:
    return subprocess.run(command, check=True, capture_output=True, text=True).stdout


def run(command: list[str]) -> None:
    completed = subprocess.run(command, capture_output=True, text=True, check=False)
    if completed.returncode != 0:
        detail = completed.stderr.strip() or completed.stdout.strip() or "unknown error"
        raise RuntimeError(f"Command failed ({command[0]}): {detail}")
