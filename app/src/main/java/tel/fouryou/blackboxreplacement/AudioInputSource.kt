package tel.fouryou.blackboxreplacement

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlin.math.max

/**
 * Boundary between the capture pipeline and a physical audio input.
 *
 * The current implementation is the phone microphone. Future adapters can
 * provide frames from Bluetooth/HFP, a vendor SDK, or a custom BLE device
 * without changing chunking, transcription, upload, or retention code.
 */
interface AudioInputSource : AutoCloseable {
    val descriptor: AudioInputDescriptor
    val activeRouteName: String
    fun start()
    fun read(buffer: ByteArray, offset: Int, size: Int): Int
    fun stop()
}

data class AudioInputDescriptor(
    val id: String,
    val displayName: String,
    val sampleRate: Int = 16_000,
    val channels: Int = 1,
    val encoding: String = "PCM_16"
)

class AndroidPhoneMicrophoneSource : AudioInputSource {
    override val descriptor = AudioInputDescriptor(
        id = "phone-microphone",
        displayName = "Phone microphone"
    )

    private var recorder: AudioRecord? = null

    override val activeRouteName: String
        get() {
            val routed = recorder?.routedDevice
            val product = routed?.productName?.toString()?.takeIf { it.isNotBlank() }
            return product ?: descriptor.displayName
        }

    @SuppressLint("MissingPermission")
    override fun start() {
        check(recorder == null) { "Phone microphone is already started" }
        val minBuffer = AudioRecord.getMinBufferSize(
            descriptor.sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        require(minBuffer > 0) { "AudioRecord does not support the requested format" }
        val bufferBytes = max(minBuffer * 2, AudioRecorderEngine.FRAME_BYTES * 4)
        val created = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            descriptor.sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferBytes
        )
        if (created.state != AudioRecord.STATE_INITIALIZED) {
            created.release()
            error("AudioRecord failed to initialize")
        }
        recorder = created
        try {
            created.startRecording()
            check(created.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                "AudioRecord failed to start"
            }
        } catch (error: Throwable) {
            created.release()
            recorder = null
            throw error
        }
    }

    override fun read(buffer: ByteArray, offset: Int, size: Int): Int =
        recorder?.read(buffer, offset, size, AudioRecord.READ_BLOCKING)
            ?: AudioRecord.ERROR_INVALID_OPERATION

    override fun stop() {
        recorder?.let { runCatching { it.stop() } }
    }

    override fun close() {
        recorder?.release()
        recorder = null
    }
}
