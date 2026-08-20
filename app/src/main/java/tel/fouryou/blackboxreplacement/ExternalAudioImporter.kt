package tel.fouryou.blackboxreplacement

import android.content.ContentResolver
import android.content.Context
import android.media.MediaExtractor
import android.media.MediaMetadataRetriever
import android.net.Uri
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID

data class ExternalImportResult(
    val chunk: CompletedChunk,
    val displayName: String?
)

/** Imports a completed recording from a wearable recorder or another app. */
class ExternalAudioImporter(context: Context) {
    private val appContext = context.applicationContext

    fun import(uri: Uri): ExternalImportResult {
        val resolver = appContext.contentResolver
        val mediaType = normalizedMediaType(resolver.getType(uri), uri)
        require(mediaType.startsWith("audio/")) { "Please choose an audio recording" }

        val sessionId = "import-${UUID.randomUUID()}"
        val startedAt = System.currentTimeMillis()
        val outputDirectory = File(appContext.filesDir, "audio/imported/$sessionId")
        check(outputDirectory.mkdirs() || outputDirectory.isDirectory) {
            "Could not create import directory"
        }
        val extension = extensionFor(mediaType, uri)
        val id = UUID.randomUUID().toString()
        val finalFile = File(outputDirectory, "${startedAt}_0_$id.$extension")
        val partFile = File(outputDirectory, "${finalFile.name}.part")
        try {
            resolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Could not open selected recording" }
                FileOutputStream(partFile).use { output -> input.copyTo(output, 1024 * 1024) }
            }
            check(partFile.length() > 0L) { "Selected recording is empty" }
            FileOutputStream(partFile, true).use { it.fd.sync() }
            check(partFile.renameTo(finalFile)) { "Could not finalize imported recording" }

            val durationMs = readDurationMs(finalFile)
            val chunk = CompletedChunk(
                id = id,
                sessionId = sessionId,
                sequence = 0,
                startedAt = startedAt,
                endedAt = startedAt + durationMs,
                durationMs = durationMs,
                path = finalFile.absolutePath,
                byteLength = finalFile.length(),
                sha256 = sha256(finalFile),
                state = "READY",
                mediaType = mediaType
            )
            val repository = ChunkRepository(appContext)
            try {
                repository.createSession(sessionId, startedAt)
                repository.insertCompletedChunk(chunk)
                repository.finishSession(sessionId, startedAt + durationMs)
            } finally {
                repository.closeQuietly()
            }
            return ExternalImportResult(chunk, displayName(resolver, uri))
        } catch (error: Throwable) {
            partFile.delete()
            finalFile.delete()
            throw error
        }
    }

    private fun readDurationMs(file: File): Long {
        val extractorDuration = runCatching {
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(file.absolutePath)
                var durationUs = 0L
                for (index in 0 until extractor.trackCount) {
                    val format = extractor.getTrackFormat(index)
                    if (format.containsKey(android.media.MediaFormat.KEY_DURATION)) {
                        durationUs = maxOf(durationUs, format.getLong(android.media.MediaFormat.KEY_DURATION))
                    }
                }
                durationUs / 1_000L
            } finally {
                extractor.release()
            }
        }.getOrNull()?.takeIf { it > 0L }
        if (extractorDuration != null) return extractorDuration

        val metadataDuration = runCatching {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(file.absolutePath)
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            } finally {
                retriever.release()
            }
        }.getOrNull()?.takeIf { it > 0L }
        return requireNotNull(metadataDuration) {
            "Could not read the duration of the imported recording"
        }
    }

    private fun displayName(resolver: ContentResolver, uri: Uri): String? =
        resolver.query(uri, arrayOf("_display_name"), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }

    private fun normalizedMediaType(type: String?, uri: Uri): String {
        val candidate = type?.lowercase()?.substringBefore(';')
        if (candidate != null && candidate.startsWith("audio/")) {
            return when (candidate) {
                "audio/x-m4a", "audio/m4a" -> "audio/mp4"
                else -> candidate
            }
        }
        return when (uri.toString().substringBefore('?').substringAfterLast('.').lowercase()) {
            "m4a", "mp4" -> "audio/mp4"
            "ogg" -> "audio/ogg"
            "opus" -> "audio/opus"
            "wav" -> "audio/wav"
            "webm" -> "audio/webm"
            else -> "audio/octet-stream"
        }
    }

    private fun extensionFor(mediaType: String, uri: Uri): String = when (mediaType) {
        "audio/mp4" -> "m4a"
        "audio/ogg" -> "ogg"
        "audio/opus" -> "opus"
        "audio/wav", "audio/x-wav" -> "wav"
        "audio/webm" -> "webm"
        else -> uri.toString().substringBefore('?').substringAfterLast('.').lowercase()
            .takeIf { it.matches(Regex("[a-z0-9]{1,8}")) } ?: "audio"
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
