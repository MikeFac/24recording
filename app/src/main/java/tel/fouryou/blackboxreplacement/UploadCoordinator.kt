package tel.fouryou.blackboxreplacement

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

object UploadCoordinator {
    private val executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    private val running = AtomicBoolean(false)
    private var callbackRegistered = false

    @Synchronized
    fun start(context: Context) {
        val appContext = context.applicationContext
        val repository = ChunkRepository(appContext)
        repository.resetUploadingChunks()
        repository.closeQuietly()
        if (!callbackRegistered) {
            val manager = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            manager.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) = trigger(appContext)
            })
            callbackRegistered = true
        }
        trigger(appContext)
    }

    fun trigger(context: Context) {
        val appContext = context.applicationContext
        if (!running.compareAndSet(false, true)) return
        executor.execute {
            try {
                if (!UploadPreferences.get(appContext).enabled()) return@execute
                val repository = ChunkRepository(appContext)
                try {
                    for (chunk in repository.readyChunks()) {
                        if (!repository.markUploading(chunk.id)) continue
                        try {
                            AudioUploadClient(appContext).upload(chunk)
                            repository.markUploaded(chunk.id)
                        } catch (_: Throwable) {
                            repository.markUploadReady(chunk.id)
                            retryLater(appContext)
                            break
                        }
                    }
                } finally {
                    repository.closeQuietly()
                }
            } finally {
                running.set(false)
            }
        }
    }

    fun retryLater(context: Context, delaySeconds: Long = 60L) {
        executor.schedule({ trigger(context.applicationContext) }, delaySeconds, TimeUnit.SECONDS)
    }
}
