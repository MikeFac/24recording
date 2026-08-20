# Audio input and external recorder integration

## Decision

24recording does not own wearable microphone hardware. The app owns a stable
audio-processing workflow and accepts audio from interchangeable sources.

```text
AudioInputSource / ExternalImport
            |
            v
 PCM frame boundary or completed media file
            |
            +--> local chunk repository
            +--> denoise/VAD/transcription consumers
            +--> persistent server upload
            +--> retention and deletion policy
```

The current live source is `AndroidPhoneMicrophoneSource`, wrapped behind the
`AudioInputSource` interface. Future live adapters can provide Bluetooth HFP,
vendor SDK frames, or custom BLE audio without changing the encoder,
transcription, upload, or retention layers.

## Live source contract

An `AudioInputSource` provides:

- a stable descriptor (`id`, display name, sample rate, channels, encoding);
- `start`, blocking `read`, `stop`, and `close` lifecycle methods;
- mono 16 kHz PCM for the current encoder.

The current recorder rejects sources with another sample rate or channel count.
A future adapter may resample before crossing this boundary, or the encoder can
be generalized if a device provides a better native format.

`AudioFrameRouter` remains downstream of the source. A slow transcription or
network consumer cannot block authoritative local recording.

## External file import

The Main Activity exposes **Import external recording** through Android's
Storage Access Framework. The importer:

1. accepts an audio URI selected by the user;
2. copies it into app-private storage through a `.part` file;
3. verifies a non-empty completed file and calculates SHA-256;
4. reads its duration using Android media metadata;
5. creates a completed import session and `READY` chunk;
6. preserves the input media type for server upload;
7. triggers automatic sync when upload is enabled, otherwise leaves the file
   available to **Sync now**.

Supported formats depend on Android's media stack, with explicit handling for
M4A/MP4, Ogg, Opus, WAV, and WebM audio. A file that has no readable duration
is rejected without leaving an uploadable database row.

Imported files use the same upload, retry, checksum, deletion, and metadata
rules as phone recordings. The original external file is not modified.

## Future adapters

Candidate adapters, in increasing engineering cost:

1. vendor SDK file importer;
2. Android Bluetooth/HFP microphone source;
3. vendor SDK live PCM/Opus source;
4. custom BLE device source with sequence numbers, timestamps, local buffering,
   and reconnect handling.

Each adapter must document whether it provides live frames, completed files,
or both. A Bluetooth-connected recorder is not assumed to be an Android
microphone until its transport and audio API are verified.

## Acceptance criteria

- Existing phone recording behavior is unchanged.
- A future `AudioInputSource` can replace the phone source without changing
  chunking or transcription consumers.
- Selecting an external audio file creates a normal `READY` chunk.
- Imported media type, duration, byte length, and checksum survive upload.
- Import failure leaves no partial file and no database row.
- Imported files are subject to the same explicit deletion warnings as phone
  recordings.
