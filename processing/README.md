# Local audio-processing proof

This worker creates a cleaned speech derivative and optional Whisper transcript without modifying or replacing the source recording.

## Pipeline

1. Fingerprint the original with SHA-256.
2. Decode the primary audio stream to mono 48 kHz 16-bit PCM.
3. Enhance speech with DeepFilterNet and compensate for algorithmic delay.
4. Apply a 70 Hz high-pass and anti-clipping limiter.
5. Save the cleaned derivative as mono 16 kHz FLAC.
6. Optionally transcribe with faster-whisper and integrated Silero VAD.
7. Verify the original fingerprint and duration alignment, then atomically publish the output directory and manifest.

Background speech and television dialogue cannot reliably be removed by a general noise suppressor. The cleaned file is a derivative, never a replacement for the original.

## Setup

FFmpeg and FFprobe must be installed. Install the pinned official DeepFilterNet
standalone binary, then create the Python environment:

```bash
uv run python scripts/install_deepfilter.py
uv sync --extra transcription
```

The installer currently supports Linux x86-64, verifies the release asset's
SHA-256, and stores it in the ignored `.tools` directory. A deployment may
instead put `deep-filter` on `PATH` or set `BLACKBOX_DEEPFILTER`.

The first Whisper run downloads the selected model. The initial local default is `small` on CPU with `int8` computation.

## Usage

Cleanup and transcription:

```bash
uv run blackbox-audio process recording.m4a --output-dir output/recording
```

For the quality corpus, transcribe both the source and cleaned derivative:

```bash
uv run blackbox-audio process recording.m4a \
  --output-dir output/recording-comparison \
  --compare-original
```

After manually creating an exact `reference.txt`, calculate original and cleaned
word error rates:

```bash
uv run blackbox-audio evaluate \
  --reference reference.txt \
  --original-transcript output/recording-comparison/original-transcript.json \
  --cleaned-transcript output/recording-comparison/cleaned-transcript.json
```

Cleanup without downloading or running Whisper:

```bash
uv run blackbox-audio process recording.m4a \
  --output-dir output/recording \
  --skip-transcription
```

Pipeline diagnostics without denoising:

```bash
uv run blackbox-audio process recording.m4a \
  --output-dir output/diagnostic \
  --enhancer passthrough \
  --skip-transcription
```

The diagnostic enhancer is explicit and is never used as an automatic fallback. A missing or failed denoiser causes the job to fail visibly.

## Tests

```bash
uv run python -m unittest discover -s tests -v
uv run ruff check src tests scripts
uv run ruff format --check src tests scripts
```
