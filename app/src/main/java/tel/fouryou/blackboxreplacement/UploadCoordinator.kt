package tel.fouryou.blackboxreplacement

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.io.IOException
import java.util.concurrent.TimeUnit

object UploadCoordinator {
    private const val WORK_NAME = "audio-upload"

    fun startIfEnabled(context: Context) {
        if (TranscriptionPreferences.isLiveEnabled(context)) triggerIfEnabled(context)
    }

    fun triggerIfEnabled(context: Context) {
        if (TranscriptionPreferences.isLiveEnabled(context)) enqueue(context, manual = false)
    }

    /** Explicit user action; permitted even when automatic upload is disabled. */
    fun trigger(context: Context) = enqueue(context, manual = true)

    fun cancel(context: Context) {
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(WORK_NAME)
    }

    private fun enqueue(context: Context, manual: Boolean) {
        val request = OneTimeWorkRequestBuilder<AudioUploadWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .setInputData(workDataOf("manual_sync" to manual))
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }
}

class AudioUploadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val appContext = applicationContext
        UploadStatusStore.markRunning(appContext)
        val repository = ChunkRepository(appContext)
        val manual = inputData.getBoolean("manual_sync", false)
        try {
            repository.resetUploadingChunks()
            while (true) {
                if (!manual && !TranscriptionPreferences.isLiveEnabled(appContext)) return Result.success()
                val chunks = repository.readyChunks(limit = 2)
                if (chunks.isEmpty()) {
                    UploadStatusStore.markSuccess(appContext, repository.countChunks("UPLOADED"))
                    return Result.success()
                }
                for (chunk in chunks) {
                    if (!repository.markUploading(chunk.id)) continue
                    try {
                        AudioUploadClient(appContext).upload(chunk)
                        repository.markUploaded(chunk.id)
                        UploadStatusStore.markProgress(appContext)
                    } catch (error: UploadException) {
                        repository.markUploadReady(chunk.id)
                        UploadStatusStore.markError(appContext, error.safeMessage)
                        return if (error.retryable) Result.retry() else Result.failure()
                    } catch (error: IOException) {
                        repository.markUploadReady(chunk.id)
                        UploadStatusStore.markError(appContext, "Network error; will retry automatically")
                        return Result.retry()
                    } catch (error: Throwable) {
                        repository.markUploadReady(chunk.id)
                        UploadStatusStore.markError(appContext, "Upload failed; check configuration and retry")
                        return Result.failure()
                    }
                }
            }
        } finally {
            repository.closeQuietly()
        }
    }
}

object UploadStatusStore {
    private const val NAME = "upload_status"
    private const val STATE = "state"
    private const val ERROR = "error"
    private const val LAST_SUCCESS = "last_success_ms"
    private const val UPLOADED = "uploaded_count"

    data class Snapshot(val state: String, val error: String, val lastSuccessMs: Long, val uploadedCount: Int)

    fun get(context: Context): Snapshot {
        val p = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        return Snapshot(
            p.getString(STATE, "IDLE") ?: "IDLE",
            p.getString(ERROR, "") ?: "",
            p.getLong(LAST_SUCCESS, 0L),
            p.getInt(UPLOADED, 0)
        )
    }

    fun markRunning(context: Context) = edit(context).putString(STATE, "SYNCING").putString(ERROR, "").apply()
    fun markProgress(context: Context) = edit(context).putString(STATE, "SYNCING").apply()
    fun markSuccess(context: Context, count: Int) = edit(context)
        .putString(STATE, "SYNCED").putLong(LAST_SUCCESS, System.currentTimeMillis()).putInt(UPLOADED, count)
        .putString(ERROR, "").apply()
    fun markError(context: Context, error: String) = edit(context).putString(STATE, "ERROR").putString(ERROR, error).apply()

    private fun edit(context: Context) = context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
}
