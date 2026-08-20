package tel.fouryou.blackboxreplacement

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.io.File

data class CompletedChunk(
    val id: String,
    val sessionId: String,
    val sequence: Int,
    val startedAt: Long,
    val endedAt: Long,
    val durationMs: Long,
    val path: String,
    val byteLength: Long,
    val sha256: String,
    val state: String,
    val mediaType: String = "audio/mp4"
)

data class LocalDeletionPreview(val count: Int, val bytes: Long)

class ChunkRepository(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    DATABASE_NAME,
    null,
    DATABASE_VERSION
) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE sessions (
                id TEXT PRIMARY KEY,
                started_at INTEGER NOT NULL,
                ended_at INTEGER,
                status TEXT NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE chunks (
                id TEXT PRIMARY KEY,
                session_id TEXT NOT NULL,
                sequence_no INTEGER NOT NULL,
                started_at INTEGER NOT NULL,
                ended_at INTEGER NOT NULL,
                duration_ms INTEGER NOT NULL,
                path TEXT NOT NULL,
                byte_length INTEGER NOT NULL,
                sha256 TEXT NOT NULL,
                state TEXT NOT NULL,
                media_type TEXT NOT NULL DEFAULT 'audio/mp4',
                created_at INTEGER NOT NULL,
                UNIQUE(session_id, sequence_no)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX chunks_state_idx ON chunks(state)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE chunks ADD COLUMN media_type TEXT NOT NULL DEFAULT 'audio/mp4'")
        }
    }

    fun createSession(id: String, startedAt: Long) {
        val values = ContentValues().apply {
            put("id", id)
            put("started_at", startedAt)
            put("status", "RECORDING")
        }
        writableDatabase.insertOrThrow("sessions", null, values)
    }

    fun finishSession(id: String, endedAt: Long, status: String = "COMPLETE") {
        val values = ContentValues().apply {
            put("ended_at", endedAt)
            put("status", status)
        }
        writableDatabase.update("sessions", values, "id = ?", arrayOf(id))
    }

    fun markRecordingSessionsInterrupted(endedAt: Long): Int {
        val values = ContentValues().apply {
            put("ended_at", endedAt)
            put("status", "INTERRUPTED")
        }
        return writableDatabase.update("sessions", values, "status = ?", arrayOf("RECORDING"))
    }

    @Synchronized
    fun insertCompletedChunk(chunk: CompletedChunk) {
        check(insertChunk(chunk, SQLiteDatabase.CONFLICT_ABORT) != -1L) {
            "Could not insert completed chunk ${chunk.id}"
        }
    }

    @Synchronized
    fun insertRecoveredChunk(chunk: CompletedChunk): Boolean =
        insertChunk(chunk, SQLiteDatabase.CONFLICT_IGNORE) != -1L

    private fun insertChunk(chunk: CompletedChunk, conflictAlgorithm: Int): Long {
        val values = ContentValues().apply {
            put("id", chunk.id)
            put("session_id", chunk.sessionId)
            put("sequence_no", chunk.sequence)
            put("started_at", chunk.startedAt)
            put("ended_at", chunk.endedAt)
            put("duration_ms", chunk.durationMs)
            put("path", chunk.path)
            put("byte_length", chunk.byteLength)
            put("sha256", chunk.sha256)
            put("state", chunk.state)
            put("media_type", chunk.mediaType)
            put("created_at", System.currentTimeMillis())
        }
        return writableDatabase.insertWithOnConflict("chunks", null, values, conflictAlgorithm)
    }

    fun hasChunk(id: String): Boolean {
        readableDatabase.rawQuery(
            "SELECT 1 FROM chunks WHERE id = ? LIMIT 1",
            arrayOf(id)
        ).use { cursor ->
            return cursor.moveToFirst()
        }
    }

    fun countChunks(state: String? = null): Int {
        val selection = state?.let { "state = ?" }
        val args = state?.let { arrayOf(it) }
        readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM chunks${selection?.let { " WHERE $it" } ?: ""}",
            args
        ).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
    }

    @Synchronized
    fun resetUploadingChunks(): Int = writableDatabase.update(
        "chunks",
        ContentValues().apply { put("state", "READY") },
        "state = ?",
        arrayOf("UPLOADING")
    )

    @Synchronized
    fun readyChunks(limit: Int = 2): List<CompletedChunk> {
        val chunks = mutableListOf<CompletedChunk>()
        readableDatabase.query(
            "chunks", null, "state = ?", arrayOf("READY"), null, null,
            "started_at ASC", limit.coerceIn(1, 20).toString()
        ).use { cursor ->
            while (cursor.moveToNext()) chunks += readChunk(cursor)
        }
        return chunks
    }

    @Synchronized
    fun markUploading(id: String): Boolean {
        val values = ContentValues().apply { put("state", "UPLOADING") }
        return writableDatabase.update("chunks", values, "id = ? AND state = ?", arrayOf(id, "READY")) == 1
    }

    @Synchronized
    fun markUploaded(id: String) {
        val values = ContentValues().apply { put("state", "UPLOADED") }
        writableDatabase.update("chunks", values, "id = ?", arrayOf(id))
    }

    @Synchronized
    fun markUploadReady(id: String) {
        val values = ContentValues().apply { put("state", "READY") }
        writableDatabase.update("chunks", values, "id = ?", arrayOf(id))
    }

    @Synchronized
    fun deletionPreview(onlyUploaded: Boolean, olderThanMs: Long? = null): LocalDeletionPreview {
        val clauses = mutableListOf<String>()
        val args = mutableListOf<String>()
        // Never remove the currently active capture or a chunk whose upload is in flight.
        clauses += "state NOT IN ('LOCAL_DELETED', 'LOCAL_DELETED_UNUPLOADED', 'CAPTURING', 'UPLOADING')"
        if (onlyUploaded) clauses += "state = 'UPLOADED'"
        olderThanMs?.let {
            clauses += "started_at < ?"
            args += it.toString()
        }
        val selection = clauses.takeIf { it.isNotEmpty() }?.joinToString(" AND ")
        readableDatabase.query(
            "chunks", arrayOf("COUNT(*)", "COALESCE(SUM(byte_length), 0)"),
            selection, args.toTypedArray(), null, null, null
        ).use { cursor ->
            check(cursor.moveToFirst()) { "Could not calculate deletion preview" }
            return LocalDeletionPreview(cursor.getInt(0), cursor.getLong(1))
        }
    }

    @Synchronized
    fun deleteLocalChunks(onlyUploaded: Boolean, olderThanMs: Long? = null): LocalDeletionPreview {
        val clauses = mutableListOf<String>()
        val args = mutableListOf<String>()
        // Never remove the currently active capture or a chunk whose upload is in flight.
        clauses += "state NOT IN ('LOCAL_DELETED', 'LOCAL_DELETED_UNUPLOADED', 'CAPTURING', 'UPLOADING')"
        if (onlyUploaded) clauses += "state = 'UPLOADED'"
        olderThanMs?.let {
            clauses += "started_at < ?"
            args += it.toString()
        }
        val selection = clauses.takeIf { it.isNotEmpty() }?.joinToString(" AND ")
        val rows = mutableListOf<Pair<String, String>>()
        readableDatabase.query("chunks", arrayOf("id", "path"), selection, args.toTypedArray(), null, null, null)
            .use { cursor -> while (cursor.moveToNext()) rows += cursor.getString(0) to cursor.getString(1) }
        var deleted = 0
        var bytes = 0L
        for ((id, path) in rows) {
            val file = File(path)
            val length = file.length()
            check(!file.exists() || file.delete()) { "Could not delete local recording $id" }
            val state = if (onlyUploaded) "LOCAL_DELETED" else "LOCAL_DELETED_UNUPLOADED"
            writableDatabase.update(
                "chunks", ContentValues().apply { put("state", state) }, "id = ?", arrayOf(id)
            )
            deleted++
            bytes += length
        }
        return LocalDeletionPreview(deleted, bytes)
    }

    private fun readChunk(cursor: android.database.Cursor): CompletedChunk = CompletedChunk(
        id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
        sessionId = cursor.getString(cursor.getColumnIndexOrThrow("session_id")),
        sequence = cursor.getInt(cursor.getColumnIndexOrThrow("sequence_no")),
        startedAt = cursor.getLong(cursor.getColumnIndexOrThrow("started_at")),
        endedAt = cursor.getLong(cursor.getColumnIndexOrThrow("ended_at")),
        durationMs = cursor.getLong(cursor.getColumnIndexOrThrow("duration_ms")),
        path = cursor.getString(cursor.getColumnIndexOrThrow("path")),
        byteLength = cursor.getLong(cursor.getColumnIndexOrThrow("byte_length")),
        sha256 = cursor.getString(cursor.getColumnIndexOrThrow("sha256")),
        state = cursor.getString(cursor.getColumnIndexOrThrow("state")),
        mediaType = cursor.getString(cursor.getColumnIndexOrThrow("media_type")),
    )

    fun closeQuietly() = close()

    companion object {
        private const val DATABASE_NAME = "capture.db"
        private const val DATABASE_VERSION = 2
    }
}
