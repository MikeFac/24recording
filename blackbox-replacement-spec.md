# BlackBox Replacement
## Functional and Technical Specification

**Status:** Proposed MVP specification  
**Source:** `ai_personal_memory_business_plan.md`  
**Initial platform:** Android capture appliance + cloud processing  
**Primary outcome:** Capture a user's day reliably enough that the system can later recover useful spoken information.

## 1. Product definition

The product is an always-available personal memory capture system. A user deliberately starts a recording session, wears a microphone, and carries a dedicated Android phone. The phone continuously captures microphone audio while the screen is locked, saves complete audio files at fixed intervals, and uploads them whenever connectivity is available. The processing service preserves the original recording, creates a cleaned speech derivative, runs Whisper transcription, and stores time-aligned transcript segments for search and later memory extraction.

The Android application is a capture and delivery client. It is not responsible for summarisation, semantic search, or other AI memory features in the MVP.

### 1.1 Goals

- Capture uninterrupted audio for an 8–12 hour day on a certified Android device.
- Produce usable, timestamped audio files every 15 minutes by default; support 30 minutes as a configuration option.
- Continue recording without network access.
- Recover cleanly from temporary connectivity loss, process death, and device reboot where Android permits automatic restart.
- Upload each completed file exactly once from the user's perspective, with resumable retries and integrity verification.
- Preserve original audio and never replace it with a denoised or transcoded copy.
- Clean audio conservatively, identify speech regions, and transcribe speech with Whisper.
- Make recording state visible and require deliberate user initiation.
- Keep the ingestion contract independent of Android so a future pendant can use the same backend.

### 1.2 Non-goals for MVP

- Covert recording or hiding the Android foreground-service notification.
- Voice activation as the capture mechanism.
- Real-time transcription.
- Speaker recognition or biometric identification.
- Automatic legal determination of whether a conversation may be recorded.
- A custom pendant.
- Guaranteed recovery of the few seconds currently being encoded if the process or device loses power without a graceful shutdown.

## 2. User roles and primary journeys

### 2.1 User setup

1. User installs or opens the capture app.
2. App explains continuous recording, consent responsibilities, local storage, and cloud processing.
3. User signs in or pairs the appliance with an account.
4. App requests microphone and notification permissions in context.
5. App checks microphone availability, storage, battery, network, and Android battery-optimization settings.
6. User selects the input microphone and recording quality.
7. App performs a short test recording and playback.

The app must not imply that recording can continue invisibly. If required permission or the foreground-service contract is unavailable, it must show a blocking, actionable explanation.

### 2.2 Start and stop a session

- The user taps **Start recording**.
- The app confirms the active microphone, shows a recording timer, and starts the foreground service.
- The persistent notification shows recording state, elapsed time, microphone/input name, and Stop/Pause actions as allowed by Android.
- A visible in-app indicator remains red/active while recording.
- The user taps **Stop recording** to end the session. The current chunk is finalized before the service exits.
- Pause is optional for MVP. If included, it must be explicit, visible, and auditable; it must never be confused with an upload pause.

### 2.3 Normal all-day operation

The phone records continuously even when the screen is locked and the app is not foregrounded. At each interval it closes the current audio file, validates it, creates the next file without stopping the audio input, and queues the completed file for upload. Upload and processing must not interrupt capture.

### 2.4 Offline operation

When offline, recording continues and completed files remain in a durable local outbox. When a permitted network becomes available, the client uploads oldest files first. The user sees queue age, queue size, free storage, and the last successful upload.

### 2.5 Review and processing

The web/mobile memory interface is outside the capture-app MVP but the backend must expose:

- original file status;
- cleaned-audio status;
- transcription status;
- transcript segments with timestamps;
- processing errors and retry state;
- deletion and export operations.

## 3. Functional requirements

### FR-1 Recording control and visibility

1. Recording starts only after an explicit user action or an explicitly configured, user-visible schedule in a later release.
2. The app must show a clear active-recording state in the UI and Android notification.
3. The app must show a clear stopped, paused, error, and microphone-disconnected state.
4. The app must not silently restart recording after the user has explicitly stopped it.
5. Every session receives a UUID and records start/end time, device ID, app version, and configuration.

### FR-2 Continuous microphone acquisition

1. The capture service uses Android's microphone foreground-service model and remains active with the screen locked.
2. Audio input must remain open across chunk boundaries. The implementation must not stop and recreate microphone capture for each file.
3. Default format: mono, 16-bit PCM capture at 16 kHz or 48 kHz, encoded to AAC-LC in an M4A container. The certified-device test matrix determines the final capture rate.
4. Default bitrate: 32–64 kbps mono AAC. The value is remotely configurable only within validated limits.
5. The app records from the selected wired or Bluetooth input when Android exposes it; otherwise it reports the actual input selected by the OS.
6. The app records audio levels and health counters, but it must not retain a continuous raw level stream as a substitute for the audio file.

### FR-3 Periodic file creation

1. The default chunk duration is 15 minutes, configurable to 30 minutes.
2. Files use UTC timestamps in names, for example:

   `device_<deviceId>/2026/08/19/session_<sessionId>/20260819T001500Z_<chunkId>.m4a`

3. A chunk is first written with a temporary `.part` suffix. It becomes uploadable only after the encoder and container are closed, file size is non-zero, duration is valid, and a SHA-256 checksum is calculated.
4. Finalization is recoverable: close, sync, validate, and checksum the temporary file; atomically rename it to the final filename; then persist its `READY` metadata. Startup reconciliation inserts any valid final file that was renamed before a crash but not yet committed to the database. A valid finalized `.part` is renamed and reconciled; an invalid `.part` is retained for diagnostics and never uploaded.
5. The next chunk begins from the continuously running audio input. The target boundary may move by a small amount to finish a codec frame; metadata records actual start/end timestamps.
6. Each file contains enough metadata to correlate it with the session, device clock, capture configuration, and sequence number.
7. If the process dies, completed chunks remain uploadable. The currently open `.part` file is marked incomplete and is either validated/recovered by a startup routine or retained for diagnostics and excluded from upload.

### FR-4 Local queue and retention

1. The device maintains a durable outbox in SQLite/Room.
2. Queue states are `CAPTURING`, `READY`, `UPLOADING`, `UPLOADED`, `ACKNOWLEDGED`, `DELETE_PENDING`, and `FAILED`.
3. Upload order is oldest-first unless the user explicitly selects priority upload.
4. The app never deletes a local original until the server has acknowledged checksum and durable object storage.
5. The app warns at configurable storage thresholds, with defaults of 5 GB and 2 GB free.
6. If the storage safety threshold is reached, the app stops starting new sessions and continues the active session only if enough space remains to safely finalize its current chunk. It must never silently delete unuploaded recordings.
7. Local retention is configurable after acknowledged upload: retain originals, retain for N days, or delete immediately. Default pilot policy: retain for 7 days.

### FR-5 Upload and synchronization

1. Uploads use HTTPS with authenticated, resumable transfer.
2. The client requests an upload session using chunk ID, byte length, checksum, media type, duration, and timestamps.
3. The server returns an object key and resumable upload URL/token.
4. Interrupted uploads resume from the confirmed byte offset.
5. Retries use exponential backoff with jitter and a maximum retry interval; permanent 4xx errors move the item to `FAILED` and notify the user.
6. Repeating an upload request with the same device ID and chunk ID is idempotent.
7. Wi-Fi-only mode is supported. Cellular upload is disabled by default on a dedicated appliance unless the user enables it.
8. The app reports upload progress and last server acknowledgment, not merely bytes handed to the operating system.

### FR-6 Device health

The app periodically reports, subject to user privacy settings:

- app and OS version;
- battery percentage and charging state;
- free storage;
- recording state;
- selected/actual audio input;
- input underrun/overflow counters;
- completed, failed, and queued chunks;
- last successful upload;
- approximate audio duration captured.

The app must alert locally when the microphone is disconnected, input becomes silent for an abnormal period, battery is low, storage is low, or recording has stopped unexpectedly.

### FR-7 Server-side audio cleanup

1. The original uploaded M4A is immutable and retained according to the account's retention policy.
2. The processing worker decodes audio to a canonical PCM representation, preferably mono 16 kHz 16-bit PCM for the speech pipeline.
3. Cleanup is conservative and configurable. The baseline pipeline is:
   - remove DC offset;
   - high-pass filter around 60–80 Hz;
   - detect clipping and excessive silence;
   - apply moderate noise suppression only when noise is detected;
   - normalize speech level without hard clipping;
   - preserve a short pre-roll and post-roll around speech.
4. The system must not apply aggressive denoising that makes words less intelligible. The original remains available for comparison.
5. Voice Activity Detection runs after capture. It identifies speech regions and creates processing windows with approximately 0.5 seconds of pre-roll and 1.0 second of post-roll, configurable by model.
6. VAD output includes timestamps, confidence, and reason/status for rejected regions.

### FR-8 Whisper transcription

1. Each speech window is transcribed by Whisper or a compatible Whisper implementation.
2. The initial server implementation should use `faster-whisper` with a configurable model, beginning with `small` or `medium` depending on measured accuracy/cost.
3. Language is auto-detected, with an account/session override.
4. Output includes segment start/end times, text, model/version, language, average log probability where available, and no-speech probability where available.
5. Adjacent windows are merged without losing timestamps. Context from neighboring windows may be supplied to reduce boundary errors, but duplicated text must be removed deterministically.
6. Low-confidence segments are flagged for review; they are not silently discarded.
7. Transcription is asynchronous and retried safely. A failed chunk does not block later chunks.
8. The transcript is linked to the original chunk and cleaned derivative so the user can play back the source interval.

### FR-9 Data export and deletion

- Users can delete a session, chunk, cleaned derivative, transcript, or entire account according to the product's retention policy.
- Deletion propagates to object storage, database records, queues, caches, search indexes, and derived AI memory records.
- Users can export original audio, transcript JSON, plain text, and metadata.
- Deletion and export operations are auditable without storing the audio content in application logs.

## 4. Quality and acceptance criteria

The MVP is acceptable for pilot when all of the following are demonstrated on each certified phone:

- 12-hour test session completes with no intentional stop and no missing chunk sequence.
- At least 99% of expected recording duration is present in valid audio files; the target for production is 99.9%.
- Chunk boundary gaps are below 250 ms in the reference test, with no audible multi-second gap.
- Screen lock, app backgrounding, and normal notification interaction do not stop capture.
- A completed chunk is visible in the local queue within 30 seconds of its target boundary.
- A valid chunk is not uploaded before finalization and checksum verification.
- Upload resumes after Wi-Fi loss without creating a duplicate server object.
- A reboot leaves all finalized local chunks recoverable and restarts only according to Android/user-configured rules.
- A 12-hour session at 32 kbps produces approximately 170 MB or less, excluding temporary processing files.
- The audio pipeline produces a playable original and a timestamp-aligned transcript for at least 95% of speech-containing test chunks.
- Every active recording has a visible recording indicator and a user-accessible stop control.

## 5. Recommended technical architecture

```text
Microphone
   |
   v
Android AudioRecord (long-lived capture)
   |
   +--> level/health monitor
   |
   v
PCM ring buffer -> AAC MediaCodec -> M4A MediaMuxer
                             |
                             v
                    atomic local chunk files
                             |
                             v
                 Room outbox + WorkManager uploader
                             |
                             v
                       HTTPS ingest API
                             |
                             v
                 Object storage + metadata database
                             |
                             v
        decode -> cleanup -> VAD -> Whisper -> transcript index
                             |
                             v
                  memory extraction/search API
```

### 5.1 Android application components

**Capture service.** A Kotlin `ForegroundService` declared for microphone use. It owns the long-lived `AudioRecord`, capture thread, encoder, chunk rotation, notification, and health state. No Activity lifecycle event may own the microphone.

**Audio input.** Prefer `AudioRecord` over repeatedly restarting `MediaRecorder`. `AudioRecord` keeps the input stream alive while the AAC encoder and M4A muxer rotate. This is the key design choice for continuous capture.

**Encoding.** Feed PCM frames to Android `MediaCodec` configured for AAC-LC. Write encoded output through `MediaMuxer` to a temporary M4A file. The implementation must handle codec buffer timestamps and end-of-stream correctly.

**Buffering.** Use a bounded PCM ring buffer between capture and encoding. Size it for at least 5 seconds of audio under normal conditions and expose overflow/underrun counters. If the encoder cannot keep up, record the event and fail visibly; do not silently claim continuous capture.

**Chunk rotation.** At the boundary, stop only the encoder/muxer, finalize and validate the current file, then create the next muxer while the `AudioRecord` continues feeding the ring buffer. A small bounded buffer absorbs encoder rotation. Automated tests must verify actual sample continuity.

**Persistence.** Room database stores device registration, sessions, chunks, upload attempts, configuration, and health events. Files live in app-private storage, not a public media directory by default.

**Scheduling.** WorkManager performs upload and cleanup work. It must never be the owner of the active microphone capture because scheduled work is not a substitute for a foreground recording service.

**Startup/recovery.** On service startup, scan for `.part` files, reconcile them with Room, validate finalized media, and mark orphaned files. Do not automatically start recording after a deliberate user stop.

### 5.2 Backend components

The initial deployment can be a modular service rather than microservices:

- **Ingest API:** authentication, upload-session creation, metadata validation, checksum acknowledgment, status queries.
- **Object storage:** immutable original M4A, cleaned PCM/FLAC or derived speech clips, and exports.
- **Job queue:** durable queue for cleanup, VAD, transcription, and later memory extraction.
- **Processing workers:** containerized Python workers using `ffmpeg`, a VAD model, and `faster-whisper`.
- **Metadata database:** PostgreSQL for users, devices, sessions, chunks, processing jobs, transcript segments, and deletion state.
- **Search index:** PostgreSQL full-text plus `pgvector` initially; a dedicated search service can be added when scale requires it.
- **API/UI:** authenticated read, playback, transcript, status, search, export, and deletion endpoints.

Suggested deployment units are `api`, `worker-audio`, `worker-transcription`, `worker-memory`, PostgreSQL, object storage, and a queue. Each worker must be horizontally scalable and idempotent.

## 6. Data model

### Device

```text
id, account_id, hardware_model, os_version, app_version,
registered_at, last_seen_at, status, capabilities_json
```

### RecordingSession

```text
id, device_id, started_at_utc, ended_at_utc, timezone,
capture_config_json, status, stop_reason
```

### AudioChunk

```text
id, session_id, sequence_no, started_at_utc, ended_at_utc,
duration_ms, media_type, byte_length, sha256, object_key,
local_state, server_state, upload_attempts, created_at
```

The unique key `(device_id, id)` or `(device_id, session_id, sequence_no)` prevents duplicate ingestion.

### AudioDerivative

```text
id, chunk_id, kind, object_key, format, sample_rate,
channels, processing_version, created_at
```

`kind` values include `cleaned_full`, `speech_window`, and future variants.

### TranscriptSegment

```text
id, chunk_id, derivative_id, start_ms, end_ms, text,
language, confidence_json, model_name, model_version,
created_at
```

All timestamps are relative to the chunk plus the chunk's UTC start time. Never use client wall-clock time alone to calculate duration; use the audio/sample clock and retain wall-clock timestamps as metadata.

## 7. API contract

The exact framework is implementation-specific, but the following behavior is required.

### Register device

`POST /v1/devices`

Request includes hardware model, OS/app version, capabilities, and a device public key or installation credential. Response returns device ID and configuration policy.

### Create upload

`POST /v1/audio-chunks`

```json
{
  "device_id": "dev_123",
  "chunk_id": "chk_123",
  "session_id": "ses_123",
  "sequence_no": 4,
  "started_at": "2026-08-19T00:15:00Z",
  "ended_at": "2026-08-19T00:30:00Z",
  "duration_ms": 900000,
  "byte_length": 3612840,
  "sha256": "...",
  "media_type": "audio/mp4"
}
```

Response returns an upload URL/token and an idempotency result. A duplicate request returns the existing chunk state.

### Complete upload

`POST /v1/audio-chunks/{chunk_id}/complete`

The service verifies byte length and checksum, marks the object durable, and enqueues processing exactly once.

### Processing status

`GET /v1/audio-chunks/{chunk_id}` returns upload, cleanup, VAD, transcription, and error states.

## 8. Processing workflow and failure handling

```text
UPLOADED
  -> VALIDATING
  -> CLEANING
  -> VAD_COMPLETE
  -> TRANSCRIBING
  -> TRANSCRIBED
  -> INDEXED
```

Any state may enter `RETRY_WAIT` or `FAILED`. A worker claims a job with a lease, writes outputs to versioned object keys, and commits the database state only after the output is complete. Re-running a job for the same chunk and processing version must produce no duplicate transcript segments.

Validation rejects corrupt media, impossible duration, checksum mismatch, unsupported codec, and chunks belonging to another account/device. Rejected content remains available for an explicit retry or diagnostic download according to retention policy.

Whisper processing should batch speech windows from multiple chunks where that improves GPU utilization, while transcript records remain linked to individual chunks and absolute times.

## 9. Security, privacy, and trust

- Use TLS for all client and service traffic.
- Store originals and derivatives in private object-storage buckets with short-lived signed playback URLs.
- Encrypt data at rest and protect account/device credentials in Android Keystore where possible.
- Scope device credentials to one account/device and support revocation.
- Do not log audio, transcript text, signed URLs, or raw authentication tokens.
- Separate account identity from audio object keys where practical.
- Record an audit event for start/stop, upload, export, and deletion without recording content.
- Display recording state on-device at all times during capture.
- Provide consent guidance and jurisdiction-specific legal review before pilots or commercial release, especially for workplaces and conversations involving third parties.
- Make cloud processing, retention, model use, and deletion behavior explicit in onboarding and settings.

## 10. Observability and operational targets

Track:

- active sessions and session duration;
- chunk completion rate and sequence gaps;
- audio input underruns/overflows;
- encoder failures and invalid files;
- upload success, retry count, and queue age;
- processing latency by stage;
- VAD speech ratio;
- transcription failure rate and approximate GPU/CPU cost;
- battery drain per recording hour and device temperature;
- storage pressure and app/service restarts.

Initial pilot targets:

- 99.9% of finalized chunks accepted without manual intervention;
- 95% of accepted speech-containing chunks transcribed within 30 minutes of upload;
- 95th-percentile upload-to-transcript latency under 15 minutes on normal Wi-Fi;
- no unbounded retry loops;
- no recording content in logs or analytics;
- service crash and restart events visible per device.

## 11. Test plan

### Android capture tests

- Unit-test timestamping, chunk sequencing, outbox transitions, retry backoff, checksum calculation, and recovery reconciliation.
- Instrument-test start/stop, screen lock, Activity destruction, notification actions, permission denial, low storage, low battery, microphone removal, Bluetooth disconnect, and network changes.
- Run 12–24 hour soak tests on every certified model with wired and Bluetooth microphones.
- Compare a reference continuous recording against concatenated chunks and measure gaps, overlaps, duration, and sample discontinuities.
- Kill the process, reboot the device, remove network, fill storage to threshold, and remove power during an active chunk.
- Test Android OS upgrades and vendor battery-optimization defaults.

### Audio and AI tests

- Use a fixed corpus containing quiet speech, pocket recording, clothing movement, walking, traffic, vehicle noise, multiple speakers, distant speech, and silence.
- Measure word error rate separately for original and cleaned audio; cleanup is a regression if it worsens intelligibility.
- Verify VAD does not remove sentence starts/ends beyond the configured padding.
- Verify Whisper segment times map back to playable audio within 250 ms.
- Test duplicate-window merging and idempotent worker retries.
- Manually review a pilot sample for hallucinations, names, numbers, commitments, and missed speech.

### Acceptance demo

Record a real 2-hour session on a locked certified phone, disable Wi-Fi for 30 minutes, reconnect, and confirm:

1. recording continues throughout the outage;
2. completed files accumulate locally at 15-minute intervals;
3. uploads resume without duplicates;
4. originals remain playable;
5. cleaned audio and Whisper transcript are produced;
6. transcript timestamps open the corresponding audio position;
7. the user can delete the session and confirm all derived records disappear.

## 12. Delivery phases

### Phase A — Capture proof

Build the Android foreground service, long-lived `AudioRecord`, AAC/M4A encoder, 15-minute rotation, Room outbox, and local health UI. Prove 12-hour capture before adding AI.

### Phase B — Delivery proof

Add device registration, resumable upload, checksum acknowledgment, offline queue, server object storage, and status reporting.

### Phase C — Audio/Whisper proof

Add decoding, conservative cleanup, VAD, `faster-whisper`, transcript storage, playback alignment, and retryable workers.

### Phase D — Pilot hardening

Qualify multiple Australian Android models, add battery/storage/microphone diagnostics, implement deletion/export, complete consent materials, and conduct real-world pilot tests.

### Phase E — Memory layer

Add speaker diarisation where appropriate, structured extraction of people/tasks/commitments/ideas, semantic search, and user-facing daily memory views. These features consume the stable chunk/transcript contracts and should not be coupled to Android-specific code.

## 13. Key design decisions

1. **Continuous capture is the source of truth.** VAD is downstream optimization, not a recording trigger.
2. **The microphone input stays open across file boundaries.** Only the encoder/container rotates.
3. **Original audio is immutable.** Cleaning creates a derivative.
4. **Every boundary is durable and idempotent.** Local and server state must survive retries and restarts.
5. **The phone is replaceable.** Backend APIs use device capabilities and timestamps, not handset-specific assumptions.
6. **Recording is visible and deliberate.** Privacy and platform compliance are product requirements.

## 14. Open decisions before implementation

- Final certified Android models and minimum Android API level.
- 16 kHz versus 48 kHz capture after microphone and Whisper accuracy tests.
- AAC bitrate and whether a lossless local mode is needed for selected users.
- 15-minute versus 30-minute default chunk size after battery/storage/retry measurements.
- Cloud provider, object-storage retention, and regional data residency.
- VAD model and Whisper model based on Australian-accent, noise, and cost benchmarks.
- Whether diarisation is included in the first pilot or deferred.
- Account pricing and included monthly transcription allowance.
- Legal/consent requirements for each pilot jurisdiction and use case.
