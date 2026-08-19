package tel.fouryou.blackboxreplacement

import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * A small, non-blocking fan-out boundary between microphone capture and optional
 * downstream consumers such as live transcription.
 *
 * The capture thread only enqueues a frame. Each consumer has its own bounded
 * queue and worker, so a slow network or failed transcriber cannot block local
 * recording. A consumer may drop frames when its queue is full; local recording
 * remains authoritative in that situation.
 */
class AudioFrameRouter(
    private val queueCapacity: Int = DEFAULT_QUEUE_CAPACITY,
    private val onConsumerFailure: (String, Throwable) -> Unit = { _, _ -> }
) : AutoCloseable {
    private val consumers = CopyOnWriteArrayList<Subscription>()
    private val closed = AtomicBoolean(false)

    init {
        require(queueCapacity > 0) { "Audio consumer queue capacity must be positive" }
    }

    fun subscribe(name: String, consumer: Consumer): Subscription {
        check(!closed.get()) { "Audio frame router is closed" }
        val subscription = Subscription(name, consumer, queueCapacity, onConsumerFailure)
        consumers += subscription
        subscription.start()
        return subscription
    }

    /** Must remain non-blocking because it is called from the audio capture thread. */
    fun offer(frame: AudioFrame) {
        if (closed.get()) return
        consumers.forEach { it.offer(frame) }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        consumers.forEach { it.close() }
        consumers.clear()
    }

    interface Consumer {
        fun consume(frame: AudioFrame)
    }

    class Subscription internal constructor(
        val name: String,
        private val consumer: Consumer,
        capacity: Int,
        private val failureHandler: (String, Throwable) -> Unit
    ) : AutoCloseable {
        private val queue = ArrayBlockingQueue<AudioFrame>(capacity)
        private val closed = AtomicBoolean(false)
        private val droppedFrames = AtomicLong(0)
        private val worker = Thread(::run, "audio-$name")

        fun start() {
            worker.start()
        }

        internal fun offer(frame: AudioFrame) {
            if (!queue.offer(frame)) droppedFrames.incrementAndGet()
        }

        fun droppedFrameCount(): Long = droppedFrames.get()

        private fun run() {
            try {
                while (!closed.get() || queue.isNotEmpty()) {
                    val frame = queue.poll(250, java.util.concurrent.TimeUnit.MILLISECONDS) ?: continue
                    consumer.consume(frame)
                }
            } catch (_: InterruptedException) {
                // Close interrupts the worker after the consumer queue is drained as far as possible.
                Thread.currentThread().interrupt()
            } catch (t: Throwable) {
                failureHandler(name, t)
            }
        }

        override fun close() {
            if (!closed.compareAndSet(false, true)) return
            worker.join(WORKER_JOIN_TIMEOUT_MS)
            if (worker.isAlive) {
                worker.interrupt()
                worker.join(WORKER_INTERRUPT_JOIN_TIMEOUT_MS)
            }
            if (consumer is AutoCloseable) consumer.close()
        }

        companion object {
            private const val WORKER_JOIN_TIMEOUT_MS = 2_000L
            private const val WORKER_INTERRUPT_JOIN_TIMEOUT_MS = 500L
        }
    }

    companion object {
        private const val DEFAULT_QUEUE_CAPACITY = 256
    }
}

data class AudioFrame(
    val sequence: Long,
    val startedAtElapsedNanos: Long,
    val startedAtWallClockMs: Long,
    val durationMs: Long,
    val pcm16Mono16Khz: ByteArray
)
