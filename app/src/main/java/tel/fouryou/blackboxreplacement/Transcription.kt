package tel.fouryou.blackboxreplacement

import android.content.Context
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineMoonshineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import java.io.ByteArrayOutputStream
import java.io.File

enum class TranscriptionMode {
    OFF,
    ON_DEVICE_SHERPA_ONNX,
    CLOUD_OPT_IN
}

enum class LocalTranscriptionModel {
    OFF,
    ZIPFORMER_STREAMING,
    MOONSHINE_TINY
}

data class TranscriptEvent(
    val text: String,
    val isFinal: Boolean,
    val audioSequence: Long,
    val startedAtWallClockMs: Long,
    val endedAtWallClockMs: Long
)

interface TranscriptionProvider : AudioFrameRouter.Consumer, AutoCloseable {
    val mode: TranscriptionMode
}

class DisabledTranscriptionProvider(
    override val mode: TranscriptionMode = TranscriptionMode.OFF
) : TranscriptionProvider {
    override fun consume(frame: AudioFrame) = Unit
    override fun close() = Unit
}

/** On-device streaming ASR backed by sherpa-onnx. */
class SherpaOnnxTranscriptionProvider(
    modelDirectory: File,
    private val onTranscript: (TranscriptEvent) -> Unit
) : TranscriptionProvider {
    override val mode = TranscriptionMode.ON_DEVICE_SHERPA_ONNX

    private val recognizer: OnlineRecognizer
    private val stream: OnlineStream
    private var lastText = ""
    private var utteranceStartedAtWallClockMs: Long? = null
    private var utteranceEndedAtWallClockMs: Long? = null
    private var closed = false

    init {
        require(modelDirectory.isDirectory) { "Sherpa-onnx model directory is missing" }
        val model = ModelFiles(modelDirectory).also { it.requirePresent() }
        recognizer = OnlineRecognizer(
            config = OnlineRecognizerConfig(
                featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80),
                modelConfig = OnlineModelConfig(
                    transducer = OnlineTransducerModelConfig(
                        encoder = model.encoder.absolutePath,
                        decoder = model.decoder.absolutePath,
                        joiner = model.joiner.absolutePath
                    ),
                    tokens = model.tokens.absolutePath,
                    numThreads = MODEL_THREADS,
                    provider = "cpu",
                    modelType = "zipformer"
                ),
                enableEndpoint = true
            )
        )
        stream = recognizer.createStream()
    }

    override fun consume(frame: AudioFrame) {
        check(!closed) { "Transcription provider is closed" }
        if (utteranceStartedAtWallClockMs == null) {
            utteranceStartedAtWallClockMs = frame.startedAtWallClockMs
        }
        utteranceEndedAtWallClockMs = frame.startedAtWallClockMs + frame.durationMs
        stream.acceptWaveform(pcm16ToFloat(frame.pcm16Mono16Khz), SAMPLE_RATE)
        decodeAvailable(frame.sequence)
    }

    override fun close() {
        if (closed) return
        closed = true
        stream.inputFinished()
        decodeAvailable(audioSequence = -1L)
        stream.release()
        recognizer.release()
    }

    private fun decodeAvailable(audioSequence: Long) {
        while (recognizer.isReady(stream)) recognizer.decode(stream)
        val result = recognizer.getResult(stream)
        val endpoint = recognizer.isEndpoint(stream)
        if (result.text.isNotBlank() && result.text != lastText && !endpoint) {
            lastText = result.text
            emitTranscript(result.text, false, audioSequence)
        }
        if (endpoint) {
            if (result.text.isNotBlank()) lastText = result.text
            if (lastText.isNotBlank()) emitTranscript(lastText, true, audioSequence)
            recognizer.reset(stream)
            lastText = ""
            utteranceStartedAtWallClockMs = null
            utteranceEndedAtWallClockMs = null
        }
    }

    private fun emitTranscript(text: String, isFinal: Boolean, audioSequence: Long) {
        val started = utteranceStartedAtWallClockMs ?: utteranceEndedAtWallClockMs ?: 0L
        val ended = utteranceEndedAtWallClockMs ?: started
        onTranscript(TranscriptEvent(text, isFinal, audioSequence, started, ended))
    }

    private fun pcm16ToFloat(bytes: ByteArray): FloatArray {
        val samples = FloatArray(bytes.size / 2)
        for (index in samples.indices) {
            val low = bytes[index * 2].toInt() and 0xff
            val high = bytes[index * 2 + 1].toInt()
            samples[index] = (((high shl 8) or low).toShort()) / 32768f
        }
        return samples
    }

    private class ModelFiles(directory: File) {
        val encoder = File(directory, "encoder-epoch-99-avg-1.int8.onnx")
        val decoder = File(directory, "decoder-epoch-99-avg-1.onnx")
        val joiner = File(directory, "joiner-epoch-99-avg-1.int8.onnx")
        val tokens = File(directory, "tokens.txt")

        fun requirePresent() {
            listOf(encoder, decoder, joiner, tokens).forEach { file ->
                require(file.isFile && file.length() > 0) { "Missing sherpa-onnx model file: ${file.name}" }
            }
        }
    }

    companion object {
        private const val SAMPLE_RATE = 16_000
        private const val MODEL_THREADS = 2
    }
}

/**
 * Moonshine v1 adapter. sherpa-onnx exposes this model through OfflineRecognizer,
 * so this provider performs bounded five-second rolling windows. The audio router
 * keeps this work off the capture thread; a future VAD/two-pass implementation can
 * replace this class without changing the recorder or provider boundary.
 */
class MoonshineTranscriptionProvider(
    modelDirectory: File,
    private val onTranscript: (TranscriptEvent) -> Unit
) : TranscriptionProvider {
    override val mode = TranscriptionMode.ON_DEVICE_SHERPA_ONNX

    private val recognizer: OfflineRecognizer
    private val window = ByteArrayOutputStream()
    private var firstSequence = -1L
    private var windowStartedAtWallClockMs: Long? = null
    private var windowEndedAtWallClockMs: Long? = null
    private var closed = false

    init {
        require(modelDirectory.isDirectory) { "Moonshine model directory is missing" }
        val model = ModelFiles(modelDirectory).also { it.requirePresent() }
        recognizer = OfflineRecognizer(
            config = OfflineRecognizerConfig(
                featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80),
                modelConfig = OfflineModelConfig(
                    moonshine = OfflineMoonshineModelConfig(
                        preprocessor = model.preprocessor.absolutePath,
                        encoder = model.encoder.absolutePath,
                        uncachedDecoder = model.uncachedDecoder.absolutePath,
                        cachedDecoder = model.cachedDecoder.absolutePath
                    ),
                    tokens = model.tokens.absolutePath,
                    numThreads = MODEL_THREADS,
                    provider = "cpu"
                )
            )
        )
    }

    override fun consume(frame: AudioFrame) {
        check(!closed) { "Transcription provider is closed" }
        if (firstSequence < 0) {
            firstSequence = frame.sequence
            windowStartedAtWallClockMs = frame.startedAtWallClockMs
        }
        windowEndedAtWallClockMs = frame.startedAtWallClockMs + frame.durationMs
        window.write(frame.pcm16Mono16Khz)
        if (window.size() >= WINDOW_BYTES) transcribeWindow()
    }

    override fun close() {
        if (closed) return
        closed = true
        if (window.size() > MIN_WINDOW_BYTES) transcribeWindow()
        recognizer.release()
    }

    private fun transcribeWindow() {
        val bytes = window.toByteArray()
        window.reset()
        val stream = recognizer.createStream()
        try {
            stream.acceptWaveform(pcm16ToFloat(bytes), SAMPLE_RATE)
            recognizer.decode(stream)
            val text = recognizer.getResult(stream).text.trim()
            if (text.isNotEmpty()) {
                onTranscript(
                    TranscriptEvent(
                        text = text,
                        isFinal = true,
                        audioSequence = firstSequence,
                        startedAtWallClockMs = windowStartedAtWallClockMs ?: 0L,
                        endedAtWallClockMs = windowEndedAtWallClockMs ?: 0L
                    )
                )
            }
        } finally {
            stream.release()
            firstSequence = -1L
            windowStartedAtWallClockMs = null
            windowEndedAtWallClockMs = null
        }
    }

    private fun pcm16ToFloat(bytes: ByteArray): FloatArray {
        val samples = FloatArray(bytes.size / 2)
        for (index in samples.indices) {
            val low = bytes[index * 2].toInt() and 0xff
            val high = bytes[index * 2 + 1].toInt()
            samples[index] = (((high shl 8) or low).toShort()) / 32768f
        }
        return samples
    }

    private class ModelFiles(directory: File) {
        val preprocessor = File(directory, "preprocess.onnx")
        val encoder = File(directory, "encode.int8.onnx")
        val uncachedDecoder = File(directory, "uncached_decode.int8.onnx")
        val cachedDecoder = File(directory, "cached_decode.int8.onnx")
        val tokens = File(directory, "tokens.txt")

        fun requirePresent() {
            listOf(preprocessor, encoder, uncachedDecoder, cachedDecoder, tokens).forEach { file ->
                require(file.isFile && file.length() > 0) { "Missing Moonshine model file: ${file.name}" }
            }
        }
    }

    companion object {
        private const val SAMPLE_RATE = 16_000
        private const val MODEL_THREADS = 1
        private const val WINDOW_SECONDS = 5
        private const val WINDOW_BYTES = SAMPLE_RATE * 2 * WINDOW_SECONDS
        private const val MIN_WINDOW_BYTES = SAMPLE_RATE * 2
    }
}

object TranscriptionProviderFactory {
    fun create(
        context: Context,
        mode: TranscriptionMode,
        model: LocalTranscriptionModel = LocalTranscriptionModel.MOONSHINE_TINY,
        onTranscript: (TranscriptEvent) -> Unit = {}
    ): TranscriptionProvider {
        if (model == LocalTranscriptionModel.OFF) return DisabledTranscriptionProvider()
        if (mode != TranscriptionMode.ON_DEVICE_SHERPA_ONNX) {
            // Cloud is intentionally fail-closed until a separately reviewed,
            // explicitly opt-in implementation is added.
            return DisabledTranscriptionProvider(mode)
        }
        val modelDirectory = File(context.filesDir, "models/sherpa-onnx/${model.directoryName}")
        if (!model.hasModel(modelDirectory)) return DisabledTranscriptionProvider(mode)
        return when (model) {
            LocalTranscriptionModel.OFF -> DisabledTranscriptionProvider()
            LocalTranscriptionModel.ZIPFORMER_STREAMING ->
                SherpaOnnxTranscriptionProvider(modelDirectory, onTranscript)
            LocalTranscriptionModel.MOONSHINE_TINY ->
                MoonshineTranscriptionProvider(modelDirectory, onTranscript)
        }
    }

    private val LocalTranscriptionModel.directoryName: String
        get() = when (this) {
            LocalTranscriptionModel.OFF -> ""
            LocalTranscriptionModel.ZIPFORMER_STREAMING -> "zipformer-en"
            LocalTranscriptionModel.MOONSHINE_TINY -> "moonshine-tiny-en-int8"
        }

    private fun LocalTranscriptionModel.hasModel(directory: File): Boolean = when (this) {
        LocalTranscriptionModel.OFF -> emptyList()
        LocalTranscriptionModel.ZIPFORMER_STREAMING -> listOf(
            "encoder-epoch-99-avg-1.int8.onnx",
            "decoder-epoch-99-avg-1.onnx",
            "joiner-epoch-99-avg-1.int8.onnx",
            "tokens.txt"
        )
        LocalTranscriptionModel.MOONSHINE_TINY -> listOf(
            "preprocess.onnx",
            "encode.int8.onnx",
            "uncached_decode.int8.onnx",
            "cached_decode.int8.onnx",
            "tokens.txt"
        )
    }.all { File(directory, it).isFile }

    private fun pcm16ToFloat(bytes: ByteArray): FloatArray {
        val samples = FloatArray(bytes.size / 2)
        for (index in samples.indices) {
            val low = bytes[index * 2].toInt() and 0xff
            val high = bytes[index * 2 + 1].toInt()
            samples[index] = (((high shl 8) or low).toShort()) / 32768f
        }
        return samples
    }
}
