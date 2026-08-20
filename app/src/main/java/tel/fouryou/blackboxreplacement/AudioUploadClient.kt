package tel.fouryou.blackboxreplacement

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.format.DateTimeFormatter

class UploadException(val status: Int, val safeMessage: String) : RuntimeException(safeMessage) {
    val retryable: Boolean = status == 408 || status == 429 || status >= 500
}

class AudioUploadClient(context: Context) {
    private val settings = UploadPreferences.get(context)

    fun upload(chunk: CompletedChunk) {
        check(settings.enabled()) { "server upload is not configured" }
        val file = File(chunk.path)
        check(file.isFile && file.length() == chunk.byteLength) { "audio chunk is missing or changed" }
        val metadata = JSONObject()
            .put("chunk_id", chunk.id)
            .put("session_id", chunk.sessionId)
            .put("sequence_no", chunk.sequence)
            .put("started_at", iso(chunk.startedAt))
            .put("ended_at", iso(chunk.endedAt))
            .put("duration_ms", chunk.durationMs)
            .put("byte_length", chunk.byteLength)
            .put("sha256", chunk.sha256)
            .put("media_type", chunk.mediaType)
            .put("device_id", settings.deviceId)
        requestJson("POST", "/v1/audio-chunks", metadata)
        putFile(chunk, file)
        requestJson("POST", "/v1/audio-chunks/${chunk.id}/complete", JSONObject())
    }

    private fun putFile(chunk: CompletedChunk, file: File) {
        val connection = open("PUT", "/v1/audio-chunks/${chunk.id}/content")
        connection.doOutput = true
        connection.setFixedLengthStreamingMode(file.length())
        connection.setRequestProperty("Content-Type", chunk.mediaType)
        try {
            FileInputStream(file).use { input ->
                connection.outputStream.use { output -> input.copyTo(output, 1024 * 1024) }
            }
            readResponse(connection)
        } finally {
            connection.disconnect()
        }
    }

    private fun requestJson(method: String, path: String, body: JSONObject): JSONObject {
        val connection = open(method, path)
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        try {
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            return readResponse(connection)
        } finally {
            connection.disconnect()
        }
    }

    private fun open(method: String, path: String): HttpURLConnection =
        (URL(settings.serverUrl + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 120_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "BlackboxReplacement/0.1")
            setRequestProperty("Authorization", "Bearer ${settings.apiKey}")
        }

    private fun readResponse(connection: HttpURLConnection): JSONObject {
        val status = connection.responseCode
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (status !in 200..299) {
            throw UploadException(status, "Server rejected upload (HTTP $status)")
        }
        return JSONObject(body.ifBlank { "{}" })
    }

    private fun iso(epochMs: Long): String = DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(epochMs))
}
