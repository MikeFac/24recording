package tel.fouryou.blackboxreplacement

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class CaptureService : Service() {
    private val controlExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = android.os.Handler(Looper.getMainLooper())
    private lateinit var repository: ChunkRepository
    private var engine: AudioRecorderEngine? = null
    private var frameRouter: AudioFrameRouter? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var sessionId: String? = null
    private val terminalHandled = AtomicBoolean(false)
    @Volatile private var startupFailure: Throwable? = null

    override fun onCreate() {
        super.onCreate()
        reconcileStaleProcessState(this)
        serviceAlive.set(true)
        repository = ChunkRepository(this)
        createNotificationChannel()
        controlExecutor.execute {
            try {
                RecordingRecovery.reconcile(File(filesDir, "audio"), repository)
            } catch (t: Throwable) {
                startupFailure = t
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> beginStart()
            ACTION_STOP -> beginStop()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        if (state(this) == CaptureState.RECORDING.name || state(this) == CaptureState.STARTING.name) {
            setError("Recording service stopped unexpectedly")
        }
        serviceAlive.set(false)
        controlExecutor.execute {
            stopEngineIfNeeded()
            repository.closeQuietly()
        }
        controlExecutor.shutdown()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun beginStart() {
        if (state(this) == CaptureState.RECORDING.name || state(this) == CaptureState.STARTING.name) return
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            setError("Microphone permission is required")
            stopSelf()
            return
        }

        setState(CaptureState.STARTING)
        startForegroundCompat(buildNotification("Starting microphone capture"))
        controlExecutor.execute {
            try {
                if (state(this) != CaptureState.STARTING.name) return@execute
                startupFailure?.let { throw IllegalStateException("Recording recovery failed", it) }
                terminalHandled.set(false)
                val newSessionId = UUID.randomUUID().toString()
                val startedAt = System.currentTimeMillis()
                sessionId = newSessionId
                repository.createSession(newSessionId, startedAt)
                acquireWakeLock()
                val router = AudioFrameRouter(
                    onConsumerFailure = { name, error ->
                        mainHandler.post {
                            updateNotification("Recording · $name unavailable")
                        }
                    }
                )
                val transcriptionProvider = runCatching {
                    TranscriptionProviderFactory.create(
                        context = this,
                        mode = TranscriptionMode.ON_DEVICE_SHERPA_ONNX,
                        model = TranscriptionPreferences.getModel(this),
                        onTranscript = { event ->
                            if (event.isFinal) updateNotification("Recording · live transcription active")
                        }
                    )
                }.getOrElse {
                    DisabledTranscriptionProvider(TranscriptionMode.ON_DEVICE_SHERPA_ONNX)
                }
                router.subscribe("transcription", transcriptionProvider)
                frameRouter = router
                val outputDirectory = File(filesDir, "audio/$newSessionId")
                engine = AudioRecorderEngine(
                    outputDirectory = outputDirectory,
                    sessionId = newSessionId,
                    repository = repository,
                    onChunkCompleted = { chunk ->
                        updateNotification("Saved chunk ${chunk.sequence + 1}")
                    },
                    onHealthChanged = { health ->
                        saveHealth(health)
                    },
                    onFailure = { error ->
                        mainHandler.post { handleEngineFailure(error) }
                    },
                    frameRouter = router
                )
                engine!!.start()
                setState(CaptureState.RECORDING, startedAt = startedAt, session = newSessionId)
                updateNotification("Recording · 0 chunks saved")
            } catch (t: Throwable) {
                mainHandler.post { handleEngineFailure(t) }
            }
        }
    }

    private fun beginStop() {
        val currentState = state(this)
        if (currentState != CaptureState.RECORDING.name && currentState != CaptureState.STARTING.name) {
            stopSelf()
            return
        }
        terminalHandled.set(true)
        setState(CaptureState.STOPPING)
        controlExecutor.execute {
            terminalHandled.set(true)
            try {
                stopEngineIfNeeded()
                sessionId?.let { repository.finishSession(it, System.currentTimeMillis()) }
                setState(CaptureState.STOPPED)
            } catch (t: Throwable) {
                sessionId?.let {
                    repository.finishSession(it, System.currentTimeMillis(), status = "INTERRUPTED")
                }
                setError(t.message ?: t.javaClass.simpleName)
            } finally {
                releaseWakeLock()
                mainHandler.post {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
    }

    private fun stopEngineIfNeeded() {
        engine?.stop()
        engine = null
        frameRouter = null
    }

    private fun handleEngineFailure(error: Throwable) {
        if (!terminalHandled.compareAndSet(false, true)) return
        controlExecutor.execute {
            var finalError = error
            try {
                stopEngineIfNeeded()
            } catch (stopError: Throwable) {
                if (stopError !== finalError) finalError.addSuppressed(stopError)
            }
            try {
                sessionId?.let {
                    repository.finishSession(it, System.currentTimeMillis(), status = "INTERRUPTED")
                }
            } catch (databaseError: Throwable) {
                finalError.addSuppressed(databaseError)
            } finally {
                releaseWakeLock()
                setError(finalError.message ?: finalError.javaClass.simpleName)
                mainHandler.post {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
    }

    private fun setState(newState: CaptureState, startedAt: Long? = null, session: String? = null) {
        getSharedPreferences(PREFERENCES, MODE_PRIVATE).edit().apply {
            putString(KEY_STATE, newState.name)
            if (startedAt != null) putLong(KEY_STARTED_AT, startedAt)
            if (session != null) putString(KEY_SESSION_ID, session)
            if (newState == CaptureState.STARTING || newState == CaptureState.RECORDING) {
                remove(KEY_ERROR)
            }
            if (newState == CaptureState.STOPPED) {
                remove(KEY_STARTED_AT)
                remove(KEY_SESSION_ID)
                remove(KEY_ERROR)
            }
        }.apply()
    }

    private fun setError(message: String) {
        getSharedPreferences(PREFERENCES, MODE_PRIVATE).edit()
            .putString(KEY_STATE, CaptureState.ERROR.name)
            .putString(KEY_ERROR, message)
            .apply()
    }

    private fun saveHealth(health: AudioRecorderEngine.Health) {
        getSharedPreferences(PREFERENCES, MODE_PRIVATE).edit()
            .putLong(KEY_UNDERRUNS, health.inputUnderruns)
            .putLong(KEY_OVERFLOWS, health.inputOverflows)
            .apply()
    }

    private fun acquireWakeLock() {
        val manager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = manager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:capture").apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    private fun buildNotification(message: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            1,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or pendingIntentMutabilityFlag()
        )
        val stopIntent = PendingIntent.getActivity(
            this,
            2,
            Intent(this, MainActivity::class.java)
                .setAction(StopConfirmation.ACTION_REQUEST_STOP)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or pendingIntentMutabilityFlag()
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Blackbox Replacement")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setContentIntent(openIntent)
            .addAction(Notification.Action.Builder(null, "Stop · enter code", stopIntent).build())
            .build()
    }

    private fun updateNotification(message: String) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(message))
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Audio capture",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Visible status for continuous audio capture"
            setShowBadge(false)
        }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
    }

    private fun pendingIntentMutabilityFlag(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0

    companion object {
        const val ACTION_START = "tel.fouryou.blackboxreplacement.action.START"
        const val ACTION_STOP = "tel.fouryou.blackboxreplacement.action.STOP"

        private const val PREFERENCES = "capture_state"
        private const val KEY_STATE = "state"
        private const val KEY_SESSION_ID = "session_id"
        private const val KEY_STARTED_AT = "started_at"
        private const val KEY_ERROR = "error"
        private const val KEY_UNDERRUNS = "input_underruns"
        private const val KEY_OVERFLOWS = "input_overflows"
        private const val CHANNEL_ID = "audio_capture"
        private const val NOTIFICATION_ID = 2401
        private val serviceAlive = AtomicBoolean(false)

        fun reconcileStaleProcessState(context: Context) {
            if (serviceAlive.get()) return
            val preferences = context.applicationContext
                .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            val persistedState = preferences.getString(KEY_STATE, CaptureState.STOPPED.name)
            if (
                persistedState == CaptureState.RECORDING.name ||
                persistedState == CaptureState.STARTING.name ||
                persistedState == CaptureState.STOPPING.name
            ) {
                preferences.edit()
                    .putString(KEY_STATE, CaptureState.ERROR.name)
                    .putString(KEY_ERROR, "Previous recording ended unexpectedly; recovered files will be checked on next start")
                    .apply()
            }
        }

        fun snapshot(context: Context): CaptureSnapshot {
            val appContext = context.applicationContext
            val preferences = appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            val repository = ChunkRepository(appContext)
            val snapshot = CaptureSnapshot(
                state = preferences.getString(KEY_STATE, CaptureState.STOPPED.name) ?: CaptureState.STOPPED.name,
                sessionId = preferences.getString(KEY_SESSION_ID, null),
                startedAt = preferences.getLong(KEY_STARTED_AT, 0L).takeIf { it > 0L },
                lastError = preferences.getString(KEY_ERROR, null),
                completedChunks = repository.countChunks(),
                queuedChunks = repository.countChunks("READY"),
                freeBytes = appContext.filesDir.usableSpace,
                inputUnderruns = preferences.getLong(KEY_UNDERRUNS, 0L),
                inputOverflows = preferences.getLong(KEY_OVERFLOWS, 0L)
            )
            repository.closeQuietly()
            return snapshot
        }

        fun isRecording(context: Context): Boolean {
            val current = state(context)
            return current == CaptureState.RECORDING.name || current == CaptureState.STARTING.name
        }

        private fun state(context: Context): String = context
            .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getString(KEY_STATE, CaptureState.STOPPED.name)
            ?: CaptureState.STOPPED.name
    }
}
