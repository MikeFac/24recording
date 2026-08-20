# Sync and Local Retention Design

**Status:** MVP implementation design

## Problem

The current uploader runs on an in-process executor. It starts from the Activity
or recording service, catches failures without exposing the error, and can stop
when Android kills the application process. Historical chunks therefore remain
in `READY` indefinitely. The phone also needs a safe way to reclaim local
storage without deleting audio that has not been durably accepted by the server.

## Goals

- Upload finalized chunks after the Activity is closed, after reboot, and after
  transient network loss.
- Show current sync state, last successful upload, and the last actionable error.
- Retry transient failures with WorkManager-managed backoff and network
  constraints.
- Preserve local originals until the server has acknowledged the complete file
  and checksum.
- Allow deletion of old local originals only when their database state proves
  that they were uploaded successfully.
- Keep deletion explicit, auditable, and recoverable at the metadata level.

## Non-goals

- Deleting unuploaded files automatically.
- Treating a successful metadata registration as a successful upload.
- Making server-side processing completion a prerequisite for local deletion in
  the pilot. The server's durable `complete` response is the boundary; a later
  product policy may require `TRANSCRIBED` or `INDEXED`.

## State model

```text
CAPTURING -> READY -> UPLOADING -> UPLOADED -> LOCAL_DELETED
                         |             |
                         +-------------+-- retry with backoff

READY/UPLOADING never qualify for the safe deletion action.
UPLOADED qualifies only after the retention age and explicit confirmation.
LOCAL_DELETED retains metadata but no longer has a local audio payload.
```

`UPLOADED` means the server accepted metadata, content, checksum, and the
completion request. `LOCAL_DELETED` means the local file was deleted after that
acknowledgement; it is not an upload failure. `LOCAL_DELETED_UNUPLOADED` means
the user explicitly accepted permanent local data loss before server upload.

## Android architecture

```text
CaptureService / MainActivity / connectivity event
                    |
                    v
       enqueueUniqueWork("audio-upload", APPEND_OR_REPLACE)
                    |
                    v
 WorkManager UploadWorker (NetworkType.CONNECTED)
                    |
                    +--> oldest READY chunks, bounded batch
                    +--> metadata -> content -> complete
                    +--> mark UPLOADED or record error and retry
                    |
                    v
       UploadStatusStore + notification/UI observer
```

The worker is unique so manual Sync Now, a completed chunk, connectivity
changes, and boot recovery cannot create concurrent uploaders. A running worker
may upload a bounded number of chunks and returns `Result.retry()` on a
retryable failure. WorkManager owns exponential backoff and rescheduling.

The worker must not run while a chunk is being encoded. Only finalized files in
`READY` are eligible. The database state transition to `UPLOADING` prevents a
second worker from claiming the same chunk.

## Error handling and observability

The app records a redacted error summary, HTTP status/category, timestamp, and
retry count. It never stores the bearer token in logs or displays response
bodies that may contain secrets. The UI exposes:

- `Syncing`, `Waiting for network`, `Synced`, or `Sync error`;
- ready/uploaded/local-deleted counts;
- last successful upload time;
- last error and a Sync Now action.

Transient network errors, 408, 429, and 5xx responses retry. Authentication,
configuration, checksum, missing-file, and other 4xx errors stop automatic
hot-looping and remain visible until the user fixes the configuration or taps
Sync Now after correction.

## Retention and deletion

The retention screen shows counts and sizes by state. The safe delete action
defaults to “uploaded originals older than 7 days” and requires confirmation
showing the number of files and bytes. The query is restricted to
`state = UPLOADED`; a file is deleted only after a final existence/size check
and is then marked `LOCAL_DELETED`. If deletion fails, the row remains
`UPLOADED` and the error is shown.

A separate, explicit “Delete all local recordings” action may include `READY`,
`FAILED`, and other non-uploaded files, but it must display a destructive
warning naming the number and size of recordings that may be permanently lost
and require a second confirmation. Such rows are marked
`LOCAL_DELETED_UNUPLOADED` so their metadata cannot be mistaken for a server
backup. Currently capturing files are never deleted by either action.

The metadata row, checksum, timestamps, server acknowledgement, and transcript
links remain after local deletion so the user can still see what was captured
and export/re-download it later if that backend capability is added.

## Estimates and limits

At the current 48 kbps AAC setting, an 8-hour day is approximately 170 MB.
The worker uses a bounded batch (two chunks by default), allowing roughly
10–11 MB per work cycle and preventing excessive memory use. Streaming file
bytes prevents loading an entire recording into memory.

## Acceptance criteria

- Closing the Activity does not stop an active upload worker.
- Rebooting and reopening network connectivity re-enqueues pending work.
- A failed upload shows a non-secret error and retries with backoff.
- A successful metadata-only response does not mark a chunk uploaded.
- Safe deletion cannot remove any `READY`, `UPLOADING`, `FAILED`, or
  `CAPTURING` payload.
- Explicit all-local deletion warns about and confirms permanent loss, then
  marks deleted unuploaded rows as `LOCAL_DELETED_UNUPLOADED`.
- A 7-day retention deletion reports exactly how many `UPLOADED` files and
  bytes were removed and leaves their metadata in `LOCAL_DELETED`.
- Existing `READY` historical chunks are discovered and uploaded after the
  update without requiring a new recording session.
