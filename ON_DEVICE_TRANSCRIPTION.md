# On-device transcription — historical experiment

Local transcription has been removed from the Android phone build. Testing on a
Samsung A53 with an exposed USB-C lapel microphone produced occasional correct
results but unacceptable errors on simple phrases, so it is not currently a
useful or reliable product feature. The notes below preserve the previous model
installation details in case a materially better model is evaluated later.

The former Android implementation used sherpa-onnx with two local model choices:
Moonshine Tiny and Zipformer Streaming.
No audio is sent to a network service by either provider.

Moonshine Tiny is processed in bounded five-second windows through sherpa-onnx's
offline recognizer. Zipformer uses sherpa-onnx's native frame-streaming
recognizer. The provider boundary allows the Moonshine implementation to be
replaced later with a VAD/two-pass or Moonshine v2 streaming implementation.

The model is intentionally not bundled in the APK. It is a large model pack and
should be installed and licensed separately. The provider looks for these files
under the app's private directory:

```text
files/models/sherpa-onnx/
  encoder-epoch-99-avg-1.int8.onnx
  decoder-epoch-99-avg-1.onnx
  joiner-epoch-99-avg-1.int8.onnx
  tokens.txt
```

These files correspond to the English streaming Zipformer model documented by
sherpa-onnx:

<https://k2-fsa.github.io/sherpa/onnx/pretrained_models/online-transducer/zipformer-transducer-models.html>

Until the model pack is installed, recording continues normally and live
transcription remains unavailable. A missing or invalid model cannot stop local
recording.

Each recording session also produces `transcript.jsonl` beside its audio files.
Every line contains the recognized text, whether it is a final or interim event,
the audio frame sequence, and epoch-millisecond start/end timestamps. These
ranges are the input to a later derived-document editor; the original audio is
not modified or deleted.

## Model locations

Zipformer files go under `files/models/sherpa-onnx/zipformer-en/`:

```text
encoder-epoch-99-avg-1.int8.onnx
decoder-epoch-99-avg-1.onnx
joiner-epoch-99-avg-1.int8.onnx
tokens.txt
```

Moonshine Tiny files go under `files/models/sherpa-onnx/moonshine-tiny-en-int8/`:

```text
preprocess.onnx
encode.int8.onnx
uncached_decode.int8.onnx
cached_decode.int8.onnx
tokens.txt
```

The Moonshine model pack is documented here:

<https://k2-fsa.github.io/sherpa/onnx/moonshine/models.html>

## Provider boundary

`TranscriptionMode.CLOUD_OPT_IN` is deliberately fail-closed. There is no cloud
transport, network endpoint, credential, or upload path in this build. A future
cloud provider must add an explicit user setting and consent record before it can
be selected, and must display a clear notice that audio leaves the device.
