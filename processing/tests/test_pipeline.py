from __future__ import annotations

import hashlib
import json
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path

from blackbox_audio.adapters.enhancers import DeepFilterNetEnhancer, PassthroughEnhancer
from blackbox_audio.adapters.ffmpeg import FfmpegAudioConverter
from blackbox_audio.domain import ToolInfo, Transcript, TranscriptSegment
from blackbox_audio.pipeline import ProcessAudio, ProcessingConfig, ProcessingError


class FailingEnhancer:
    @property
    def info(self) -> ToolInfo:
        return ToolInfo(name="intentional-test-failure", version="1", parameters={})

    def enhance(self, source: Path, destination: Path) -> None:
        raise RuntimeError("enhancement failed deliberately")


class FakeTranscriber:
    def transcribe(self, source: Path) -> Transcript:
        return Transcript(
            language="en",
            language_probability=0.99,
            duration_seconds=1.0,
            model="fake-test-model",
            engine="fake-test-engine",
            engine_version="1",
            device="test",
            compute_type="test",
            vad_enabled=True,
            segments=(
                TranscriptSegment(
                    start_seconds=0.1,
                    end_seconds=0.8,
                    text="test speech",
                    average_log_probability=-0.1,
                    no_speech_probability=0.01,
                    words=(),
                ),
            ),
        )


@unittest.skipUnless(shutil.which("ffmpeg") and shutil.which("ffprobe"), "FFmpeg is required")
class ProcessAudioIntegrationTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name)
        self.source = self.root / "original.m4a"
        subprocess.run(
            [
                "ffmpeg",
                "-nostdin",
                "-hide_banner",
                "-loglevel",
                "error",
                "-f",
                "lavfi",
                "-i",
                "sine=frequency=440:sample_rate=48000:duration=1",
                "-c:a",
                "aac",
                "-b:a",
                "32k",
                str(self.source),
            ],
            check=True,
        )

    def tearDown(self) -> None:
        self.temporary.cleanup()

    def test_publishes_cleaned_derivative_without_changing_original(self) -> None:
        original_bytes = self.source.read_bytes()
        output = self.root / "result"

        result = ProcessAudio(
            converter=FfmpegAudioConverter(),
            enhancer=PassthroughEnhancer(),
            transcriber=None,
        ).execute(self.source, output)

        self.assertEqual(original_bytes, self.source.read_bytes())
        self.assertEqual(hashlib.sha256(original_bytes).hexdigest(), result.original.sha256)
        self.assertEqual("passthrough", result.enhancer.name)
        self.assertEqual(16000, result.cleaned_audio.sample_rate_hz)
        self.assertEqual(1, result.cleaned_audio.channels)
        self.assertEqual("flac", result.cleaned_audio.codec)
        self.assertTrue((output / "cleaned-16k-mono.flac").is_file())
        self.assertFalse((output / "decoded-48k-mono.wav").exists())
        self.assertFalse((output / "enhanced-48k-mono.wav").exists())

        manifest = json.loads((output / "manifest.json").read_text(encoding="utf-8"))
        self.assertEqual(result.original.sha256, manifest["original"]["sha256"])
        self.assertEqual("cleaned-16k-mono.flac", manifest["cleaned"]["path"])
        self.assertIsNone(manifest["cleaned_transcript"])

    def test_failure_does_not_publish_partial_output(self) -> None:
        output = self.root / "failed-result"

        with self.assertRaisesRegex(ProcessingError, "enhancement failed deliberately"):
            ProcessAudio(
                converter=FfmpegAudioConverter(),
                enhancer=FailingEnhancer(),
                transcriber=None,
            ).execute(self.source, output)

        self.assertFalse(output.exists())
        self.assertEqual([], list(self.root.glob(".failed-result.*.staging")))

    def test_publishes_timestamped_transcript(self) -> None:
        output = self.root / "transcribed-result"

        result = ProcessAudio(
            converter=FfmpegAudioConverter(),
            enhancer=PassthroughEnhancer(),
            transcriber=FakeTranscriber(),
        ).execute(self.source, output)

        self.assertIsNotNone(result.cleaned_transcript)
        self.assertIsNotNone(result.cleaned_transcript_file)
        transcript = json.loads((output / "cleaned-transcript.json").read_text(encoding="utf-8"))
        self.assertEqual("test speech", transcript["segments"][0]["text"])
        self.assertEqual(0.1, transcript["segments"][0]["start_seconds"])

    def test_can_publish_original_transcript_for_quality_comparison(self) -> None:
        output = self.root / "comparison-result"

        result = ProcessAudio(
            converter=FfmpegAudioConverter(),
            enhancer=PassthroughEnhancer(),
            transcriber=FakeTranscriber(),
            config=ProcessingConfig(transcribe_original_for_comparison=True),
        ).execute(self.source, output)

        self.assertIsNotNone(result.original_transcript)
        self.assertTrue((output / "original-transcript.json").is_file())

    def test_installed_deepfilternet_produces_aligned_derivative(self) -> None:
        try:
            enhancer = DeepFilterNetEnhancer()
        except RuntimeError as error:
            self.skipTest(str(error))
        output = self.root / "deepfilter-result"

        result = ProcessAudio(
            converter=FfmpegAudioConverter(),
            enhancer=enhancer,
            transcriber=None,
        ).execute(self.source, output)

        self.assertEqual("deepfilternet", result.enhancer.name)
        self.assertEqual(12, result.enhancer.parameters["attenuation_limit_db"])
        self.assertLessEqual(
            abs(result.original_audio.duration_seconds - result.cleaned_audio.duration_seconds),
            0.25,
        )

    def test_refuses_to_replace_an_existing_output(self) -> None:
        output = self.root / "existing"
        output.mkdir()
        sentinel = output / "do-not-replace.txt"
        sentinel.write_text("preserve me", encoding="utf-8")

        with self.assertRaisesRegex(ProcessingError, "already exists"):
            ProcessAudio(
                converter=FfmpegAudioConverter(),
                enhancer=PassthroughEnhancer(),
                transcriber=None,
            ).execute(self.source, output)

        self.assertEqual("preserve me", sentinel.read_text(encoding="utf-8"))


if __name__ == "__main__":
    unittest.main()
