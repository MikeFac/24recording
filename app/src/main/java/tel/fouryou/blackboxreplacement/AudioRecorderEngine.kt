package tel.fouryou.blackboxreplacement

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaRecorder
import android.os.Process
import android.os.SystemClock
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.max

class AudioRecorderEngine(
    private val outputDirectory: File,
    private val sessionId: String,
    private val repository: ChunkRepository,
    private val onChunkCompleted: (CompletedChunk) -> Unit,
    private val onHealthChanged: (Health) -> Unit,
    private val onFailure: (Throwable) -> Unit
) {
    data class Health(
        val inputUnderruns: Long = 0,
        val inputOverflows: Long = 0
    )

    private data class PcmFrame(
        val bytes: ByteArray,
        val startedAtElapsedNanos: Long
    )

    private val frameQueue = ArrayBlockingQueue<PcmFrame>(FRAME_QUEUE_CAPACITY)
    private val stopRequested = AtomicBoolean(false)
    private val captureFinished = AtomicBoolean(false)
    private val stopped = AtomicBoolean(false)
    private val terminalFailure = AtomicReference<Throwable?>(null)
    private var audioRecord: AudioRecord? = null
    private var captureThread: Thread? = null
    private var encodeThread: Thread? = null
    private var sequence = 0
    private var health = Health()
    private var timelineAnchorWallClockMs = 0L
    private var timelineAnchorElapsedNanos = 0L

    fun start() {
        check(outputDirectory.exists() || outputDirectory.mkdirs()) {
            "Could not create audio output directory"
        }
        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        require(minBuffer > 0) { "AudioRecord does not support the requested format" }

        val bufferBytes = max(minBuffer * 2, FRAME_BYTES * 4)
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferBytes
        )
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            error("AudioRecord failed to initialize")
        }
        audioRecord = recorder

        recorder.startRecording()
        if (recorder.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            recorder.release()
            audioRecord = null
            error("AudioRecord failed to start")
        }

        // Keep one wall-clock anchor for the session, then derive every chunk timestamp
        // from the monotonic clock. User/NTP wall-clock changes cannot distort chronology.
        timelineAnchorWallClockMs = System.currentTimeMillis()
        timelineAnchorElapsedNanos = SystemClock.elapsedRealtimeNanos()

        captureThread = Thread(::captureLoop, "audio-capture").apply {
            start()
        }
        encodeThread = Thread(::encodeLoop, "audio-encode").apply {
            start()
        }
    }

    fun stop() {
        if (!stopped.compareAndSet(false, true)) return
        if (stopRequested.compareAndSet(false, true)) {
            runCatching { audioRecord?.stop() }
            captureThread?.interrupt()
        }
        captureThread?.join(STOP_JOIN_TIMEOUT_MS)
        if (captureThread?.isAlive == true) {
            runCatching { audioRecord?.release() }
            audioRecord = null
            captureThread?.join(FORCED_STOP_JOIN_TIMEOUT_MS)
        }
        check(captureThread?.isAlive != true) { "Audio capture thread did not stop" }

        encodeThread?.join(ENCODER_STOP_JOIN_TIMEOUT_MS)
        check(encodeThread?.isAlive != true) { "Audio encoder thread did not finalize" }
        audioRecord?.release()
        audioRecord = null
        terminalFailure.get()?.let { throw IllegalStateException("Audio recording failed", it) }
    }

    private fun captureLoop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
        val pcm = ByteArray(FRAME_BYTES)
        var filled = 0
        var frameStartedAtElapsedNanos = SystemClock.elapsedRealtimeNanos()
        try {
            while (!stopRequested.get()) {
                if (filled == 0) frameStartedAtElapsedNanos = SystemClock.elapsedRealtimeNanos()
                val read = audioRecord?.read(
                    pcm,
                    filled,
                    pcm.size - filled,
                    AudioRecord.READ_BLOCKING
                ) ?: AudioRecord.ERROR_INVALID_OPERATION
                when {
                    read > 0 -> {
                        filled += read
                        if (filled < pcm.size) continue
                        val frame = PcmFrame(pcm.copyOf(), frameStartedAtElapsedNanos)
                        if (!frameQueue.offer(frame, QUEUE_OFFER_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                            health = health.copy(inputOverflows = health.inputOverflows + 1)
                            onHealthChanged(health)
                            error("PCM queue overflow; continuous capture can no longer be guaranteed")
                        }
                        filled = 0
                    }
                    stopRequested.get() -> break
                    read == AudioRecord.ERROR_DEAD_OBJECT -> error("Audio input became unavailable")
                    read < 0 -> error("AudioRecord read failed: $read")
                    else -> {
                        health = health.copy(inputUnderruns = health.inputUnderruns + 1)
                        onHealthChanged(health)
                    }
                }
            }
        } catch (_: InterruptedException) {
            // Stop is expected to interrupt a blocked read.
        } catch (t: Throwable) {
            if (!stopRequested.get()) reportFailure(t)
        } finally {
            captureFinished.set(true)
        }
    }

    private fun encodeLoop() {
        var writer: ChunkWriter? = null
        var samplesInChunk = 0L
        try {
            while (true) {
                val frame = frameQueue.poll(250, TimeUnit.MILLISECONDS)
                if (frame == null) {
                    if (captureFinished.get() && frameQueue.isEmpty()) break
                    continue
                }
                if (writer == null) {
                    writer = ChunkWriter(
                        outputDirectory = outputDirectory,
                        sessionId = sessionId,
                        sequence = sequence++,
                        startedAt = wallClockFromElapsed(frame.startedAtElapsedNanos)
                    )
                    samplesInChunk = 0L
                }
                writer!!.writePcm(frame.bytes, samplesInChunk)
                samplesInChunk += FRAME_SAMPLES
                if (samplesInChunk >= CHUNK_SAMPLES) {
                    val completed = writer!!.finish()
                    persist(completed)
                    writer = null
                    samplesInChunk = 0L
                }
            }
            writer?.let {
                persist(it.finish())
            }
        } catch (t: Throwable) {
            writer?.abort()
            if (!stopRequested.get() || t !is InterruptedException) reportFailure(t)
        }
    }

    private fun persist(chunk: CompletedChunk) {
        repository.insertCompletedChunk(chunk)
        onChunkCompleted(chunk)
    }

    private fun reportFailure(error: Throwable) {
        terminalFailure.compareAndSet(null, error)
        onFailure(error)
    }

    private fun wallClockFromElapsed(elapsedNanos: Long): Long =
        timelineAnchorWallClockMs + TimeUnit.NANOSECONDS.toMillis(
            elapsedNanos - timelineAnchorElapsedNanos
        )

    private fun error(message: String): Nothing = throw IllegalStateException(message)

    private class ChunkWriter(
        private val outputDirectory: File,
        private val sessionId: String,
        private val sequence: Int,
        private val startedAt: Long
    ) {
        private val chunkId = UUID.randomUUID().toString()
        private val baseName = "${startedAt}_${sequence}_$chunkId.m4a"
        private val partFile = File(outputDirectory, "$baseName.part")
        private val finalFile = File(outputDirectory, baseName)
        private val codec: MediaCodec
        private val muxer: MediaMuxer
        private var muxerStarted = false
        private var muxerTrackIndex = -1
        private var finishStarted = false
        private var codecReleased = false
        private var muxerReleased = false
        private var samplesWritten = 0L

        init {
            codec = MediaCodec.createEncoderByType(AAC_MIME)
            val format = MediaFormat.createAudioFormat(AAC_MIME, SAMPLE_RATE, 1).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, AAC_BITRATE)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, FRAME_BYTES)
            }
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
            muxer = MediaMuxer(partFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        }

        fun writePcm(bytes: ByteArray, sampleOffset: Long) {
            check(!finishStarted) { "Cannot write to a finishing chunk" }
            check(sampleOffset == samplesWritten) { "Non-contiguous PCM sample offset" }
            var inputIndex = codec.dequeueInputBuffer(CODEC_TIMEOUT_US)
            while (inputIndex < 0) {
                drain(endOfStream = false)
                inputIndex = codec.dequeueInputBuffer(CODEC_TIMEOUT_US)
            }
            val input = codec.getInputBuffer(inputIndex) ?: error("Missing AAC input buffer")
            input.clear()
            input.put(bytes)
            codec.queueInputBuffer(
                inputIndex,
                0,
                bytes.size,
                sampleOffset * 1_000_000L / SAMPLE_RATE,
                0
            )
            samplesWritten += bytes.size / BYTES_PER_SAMPLE
            drain(endOfStream = false)
        }

        fun finish(): CompletedChunk {
            check(!finishStarted) { "Chunk already finishing or finished" }
            finishStarted = true
            try {
                signalEndOfStream()
                drain(endOfStream = true)
                codec.stop()
                codec.release()
                codecReleased = true
                if (muxerStarted) muxer.stop()
                muxer.release()
                muxerReleased = true

                check(partFile.exists() && partFile.length() > 0) { "Encoded chunk is empty" }
                FileOutputStream(partFile, true).use { it.fd.sync() }
                validateM4a(partFile)
                val checksum = sha256(partFile)
                check(partFile.renameTo(finalFile)) { "Could not atomically finalize ${partFile.name}" }

                val duration = max(1L, samplesWritten * 1_000L / SAMPLE_RATE)
                return CompletedChunk(
                    id = chunkId,
                    sessionId = sessionId,
                    sequence = sequence,
                    startedAt = startedAt,
                    endedAt = startedAt + duration,
                    durationMs = duration,
                    path = finalFile.absolutePath,
                    byteLength = finalFile.length(),
                    sha256 = checksum,
                    state = "READY"
                )
            } catch (t: Throwable) {
                releaseResources()
                throw t
            }
        }

        fun abort() {
            releaseResources()
            // Keep the .part file for startup diagnostics; it is never uploadable.
        }

        private fun signalEndOfStream() {
            val deadline = SystemClock.elapsedRealtime() + CODEC_FINALIZE_TIMEOUT_MS
            while (true) {
                val inputIndex = codec.dequeueInputBuffer(CODEC_TIMEOUT_US)
                if (inputIndex >= 0) {
                    codec.queueInputBuffer(
                        inputIndex,
                        0,
                        0,
                        samplesWritten * 1_000_000L / SAMPLE_RATE,
                        MediaCodec.BUFFER_FLAG_END_OF_STREAM
                    )
                    return
                }
                drain(endOfStream = false)
                check(SystemClock.elapsedRealtime() < deadline) {
                    "Timed out acquiring AAC input buffer for end-of-stream"
                }
            }
        }

        private fun drain(endOfStream: Boolean) {
            val info = MediaCodec.BufferInfo()
            var sawEnd = false
            val deadline = SystemClock.elapsedRealtime() + CODEC_FINALIZE_TIMEOUT_MS
            while (!sawEnd) {
                when (val outputIndex = codec.dequeueOutputBuffer(info, if (endOfStream) CODEC_TIMEOUT_US else 0)) {
                    MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        if (!endOfStream) return
                        check(SystemClock.elapsedRealtime() < deadline) {
                            "Timed out draining AAC end-of-stream"
                        }
                    }
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        check(!muxerStarted) { "AAC output format changed twice" }
                        muxerTrackIndex = muxer.addTrack(codec.outputFormat)
                        muxer.start()
                        muxerStarted = true
                    }
                    else -> if (outputIndex >= 0) {
                        val output = codec.getOutputBuffer(outputIndex)
                        if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                        if (output != null && info.size > 0) {
                            check(muxerStarted && muxerTrackIndex >= 0) { "AAC sample arrived before muxer format" }
                            output.position(info.offset)
                            output.limit(info.offset + info.size)
                            muxer.writeSampleData(muxerTrackIndex, output, info)
                        }
                        sawEnd = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        codec.releaseOutputBuffer(outputIndex, false)
                    }
                }
            }
        }

        private fun releaseResources() {
            if (!codecReleased) {
                runCatching { codec.stop() }
                runCatching { codec.release() }
                codecReleased = true
            }
            if (!muxerReleased) {
                if (muxerStarted) runCatching { muxer.stop() }
                runCatching { muxer.release() }
                muxerReleased = true
            }
        }

        private fun validateM4a(file: File) {
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(file.absolutePath)
                check(extractor.trackCount > 0) { "Finalized M4A has no tracks" }
            } finally {
                extractor.release()
            }
        }
    }

    companion object {
        private const val SAMPLE_RATE = 16_000
        private const val BYTES_PER_SAMPLE = 2
        private const val FRAME_SAMPLES = SAMPLE_RATE / 50 // 20 ms
        private const val FRAME_BYTES = FRAME_SAMPLES * BYTES_PER_SAMPLE
        private const val CHUNK_SECONDS = 15 * 60
        private const val CHUNK_SAMPLES = SAMPLE_RATE.toLong() * CHUNK_SECONDS
        private const val AAC_BITRATE = 48_000
        private const val AAC_MIME = "audio/mp4a-latm"
        private const val CODEC_TIMEOUT_US = 10_000L
        private const val CODEC_FINALIZE_TIMEOUT_MS = 5_000L
        private const val FRAME_QUEUE_CAPACITY = 256
        private const val QUEUE_OFFER_TIMEOUT_MS = 250L
        private const val STOP_JOIN_TIMEOUT_MS = 5_000L
        private const val FORCED_STOP_JOIN_TIMEOUT_MS = 1_000L
        private const val ENCODER_STOP_JOIN_TIMEOUT_MS = 10_000L

        private fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            FileInputStream(file).use { input ->
                val buffer = ByteArray(16 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    digest.update(buffer, 0, read)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
