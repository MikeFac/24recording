package tel.fouryou.blackboxreplacement

import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.UUID
import kotlin.math.max

object RecordingRecovery {
    data class Report(
        val interruptedSessions: Int,
        val recoveredChunks: Int,
        val invalidPartFiles: Int
    )

    fun reconcile(audioRoot: File, repository: ChunkRepository): Report {
        val interruptedSessions = repository.markRecordingSessionsInterrupted(System.currentTimeMillis())
        if (!audioRoot.exists()) return Report(interruptedSessions, 0, 0)

        var recoveredChunks = 0
        var invalidPartFiles = 0
        audioRoot.walkTopDown()
            .filter { it.isFile && (it.name.endsWith(".m4a") || it.name.endsWith(".m4a.part")) }
            .forEach { candidate ->
                var mediaFile = candidate
                val durationMs = readDurationMs(candidate)
                if (durationMs == null) {
                    if (candidate.name.endsWith(".part")) invalidPartFiles++
                    return@forEach
                }

                if (candidate.name.endsWith(".part")) {
                    val finalized = File(candidate.parentFile, candidate.name.removeSuffix(".part"))
                    if (!finalized.exists()) {
                        check(candidate.renameTo(finalized)) {
                            "Could not finalize recoverable file ${candidate.absolutePath}"
                        }
                    }
                    mediaFile = finalized
                }

                val identity = parseIdentity(mediaFile) ?: return@forEach
                if (repository.hasChunk(identity.chunkId)) return@forEach
                val sessionId = mediaFile.parentFile?.name ?: return@forEach
                val chunk = CompletedChunk(
                    id = identity.chunkId,
                    sessionId = sessionId,
                    sequence = identity.sequence,
                    startedAt = identity.startedAt,
                    endedAt = identity.startedAt + durationMs,
                    durationMs = durationMs,
                    path = mediaFile.absolutePath,
                    byteLength = mediaFile.length(),
                    sha256 = sha256(mediaFile),
                    state = "READY"
                )
                if (repository.insertRecoveredChunk(chunk)) recoveredChunks++
            }

        return Report(interruptedSessions, recoveredChunks, invalidPartFiles)
    }

    private data class FileIdentity(
        val startedAt: Long,
        val sequence: Int,
        val chunkId: String
    )

    private fun parseIdentity(file: File): FileIdentity? {
        val parts = file.name.removeSuffix(".m4a").split("_", limit = 3)
        if (parts.size != 3) return null
        val startedAt = parts[0].toLongOrNull() ?: return null
        val sequence = parts[1].toIntOrNull() ?: return null
        val chunkId = runCatching { UUID.fromString(parts[2]).toString() }.getOrNull() ?: return null
        return FileIdentity(startedAt, sequence, chunkId)
    }

    private fun readDurationMs(file: File): Long? {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(file.absolutePath)
            if (extractor.trackCount <= 0) return null
            var durationUs = 0L
            for (index in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(index)
                if (format.containsKey(MediaFormat.KEY_DURATION)) {
                    durationUs = max(durationUs, format.getLong(MediaFormat.KEY_DURATION))
                }
            }
            (durationUs / 1_000L).takeIf { it > 0L }
        } catch (_: Throwable) {
            null
        } finally {
            extractor.release()
        }
    }

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
