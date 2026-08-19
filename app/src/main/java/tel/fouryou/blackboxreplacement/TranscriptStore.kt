package tel.fouryou.blackboxreplacement

import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

/** Append-only, timestamped transcript events stored beside the source session audio. */
class TranscriptStore(private val file: File) : AutoCloseable {
    private val lock = Any()
    private var closed = false

    init {
        file.parentFile?.mkdirs()
    }

    fun append(event: TranscriptEvent) {
        synchronized(lock) {
            check(!closed) { "Transcript store is closed" }
            FileOutputStream(file, true).use { output ->
                BufferedWriter(OutputStreamWriter(output, StandardCharsets.UTF_8)).use { writer ->
                    writer.write(
                        "{" +
                            "\"text\":\"${event.text.jsonEscape()}\"," +
                            "\"final\":${event.isFinal}," +
                            "\"audio_sequence\":${event.audioSequence}," +
                            "\"started_at_epoch_ms\":${event.startedAtWallClockMs}," +
                            "\"ended_at_epoch_ms\":${event.endedAtWallClockMs}" +
                            "}"
                    )
                    writer.newLine()
                    writer.flush()
                    output.fd.sync()
                }
            }
        }
    }

    override fun close() {
        synchronized(lock) { closed = true }
    }

    private fun String.jsonEscape(): String = buildString(length) {
        for (character in this@jsonEscape) {
            when (character) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(character)
            }
        }
    }
}
