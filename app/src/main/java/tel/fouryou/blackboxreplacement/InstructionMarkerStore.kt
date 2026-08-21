package tel.fouryou.blackboxreplacement

import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

/** Append-only audit log for user-designated instruction intervals. */
class InstructionMarkerStore(private val file: File) : AutoCloseable {
    private val lock = Any()
    private var closed = false

    init { file.parentFile?.mkdirs() }

    fun appendStarted(marker: InstructionMarker) = append(
        "{\"event\":\"started\",\"marker_id\":\"${marker.id}\",\"session_id\":\"${marker.sessionId}\"," +
            "\"started_at_epoch_ms\":${marker.startedAtEpochMs},\"started_at_elapsed_ms\":${marker.startedAtElapsedMs}," +
            "\"explicit_user_mark\":true}"
    )

    fun appendEnded(marker: InstructionMarker, endedAtEpochMs: Long, endedAtElapsedMs: Long, reason: String) = append(
        "{\"event\":\"ended\",\"marker_id\":\"${marker.id}\",\"session_id\":\"${marker.sessionId}\"," +
            "\"started_at_epoch_ms\":${marker.startedAtEpochMs},\"ended_at_epoch_ms\":$endedAtEpochMs," +
            "\"started_at_elapsed_ms\":${marker.startedAtElapsedMs},\"ended_at_elapsed_ms\":$endedAtElapsedMs," +
            "\"duration_ms\":${(endedAtElapsedMs - marker.startedAtElapsedMs).coerceAtLeast(0L)}," +
            "\"reason\":\"${reason.jsonEscape()}\",\"explicit_user_mark\":true}"
    )

    private fun append(line: String) {
        synchronized(lock) {
            check(!closed) { "Instruction marker store is closed" }
            FileOutputStream(file, true).use { output ->
                BufferedWriter(OutputStreamWriter(output, StandardCharsets.UTF_8)).use { writer ->
                    writer.write(line)
                    writer.newLine()
                    writer.flush()
                    output.fd.sync()
                }
            }
        }
    }

    override fun close() { synchronized(lock) { closed = true } }

    private fun String.jsonEscape(): String = replace("\\", "\\\\").replace("\"", "\\\"")
}

data class InstructionMarker(
    val id: String,
    val sessionId: String,
    val startedAtEpochMs: Long,
    val startedAtElapsedMs: Long
)
