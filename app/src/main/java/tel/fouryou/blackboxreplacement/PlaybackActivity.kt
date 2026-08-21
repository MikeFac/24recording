package tel.fouryou.blackboxreplacement

import android.app.Activity
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Plays a day's local chunks as one virtual, seekable timeline. */
class PlaybackActivity : Activity() {
    private lateinit var dayText: TextView
    private lateinit var summaryText: TextView
    private lateinit var positionText: TextView
    private lateinit var seekBar: SeekBar
    private lateinit var playButton: Button
    private lateinit var previousButton: Button
    private lateinit var nextButton: Button

    private val handler = Handler(Looper.getMainLooper())
    private val repository by lazy { ChunkRepository(this) }
    private var dayStartMs = 0L
    private var chunks = emptyList<CompletedChunk>()
    private var totalDurationMs = 0L
    private var currentIndex = -1
    private var player: MediaPlayer? = null
    private var preparing = false
    private var userSeeking = false

    private val progress = object : Runnable {
        override fun run() {
            renderProgress()
            handler.postDelayed(this, 250L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(createContent())
        val latest = repository.latestPlayableStartedAt() ?: System.currentTimeMillis()
        dayStartMs = startOfDay(latest)
        loadDay()
    }

    override fun onResume() {
        super.onResume()
        handler.post(progress)
    }

    override fun onPause() {
        handler.removeCallbacks(progress)
        super.onPause()
    }

    override fun onDestroy() {
        handler.removeCallbacks(progress)
        releasePlayer()
        repository.closeQuietly()
        super.onDestroy()
    }

    private fun createContent(): LinearLayout {
        val padding = (resources.displayMetrics.density * 24).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
        }
        root.addView(TextView(this).apply {
            text = "Playback"
            textSize = 26f
        }, matchWrap())
        dayText = TextView(this).apply {
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(0, padding / 2, 0, padding / 2)
        }
        root.addView(dayText, matchWrap())

        val dayControls = LinearLayout(this).apply { gravity = Gravity.CENTER }
        previousButton = Button(this).apply {
            text = "Previous day"
            setOnClickListener { changeDay(-1) }
        }
        nextButton = Button(this).apply {
            text = "Next day"
            setOnClickListener { changeDay(1) }
        }
        dayControls.addView(previousButton, weightWrap())
        dayControls.addView(nextButton, weightWrap())
        root.addView(dayControls, matchWrap())

        summaryText = TextView(this).apply {
            gravity = Gravity.CENTER
            setPadding(0, padding, 0, padding / 2)
        }
        root.addView(summaryText, matchWrap())

        seekBar = SeekBar(this).apply {
            max = 1
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onStartTrackingTouch(bar: SeekBar) { userSeeking = true }
                override fun onStopTrackingTouch(bar: SeekBar) {
                    userSeeking = false
                    seekToGlobal(bar.progress.toLong())
                }
                override fun onProgressChanged(bar: SeekBar, value: Int, fromUser: Boolean) {
                    if (fromUser) positionText.text = formatDuration(value.toLong())
                }
            })
        }
        root.addView(seekBar, matchWrap())

        positionText = TextView(this).apply {
            gravity = Gravity.CENTER
            textSize = 18f
        }
        root.addView(positionText, matchWrap())

        playButton = Button(this).apply {
            text = "Play"
            setOnClickListener { togglePlayback() }
        }
        root.addView(playButton, matchWrap())
        root.addView(TextView(this).apply {
            text = "Plays finalized local files through the phone's normal media speaker route. Deleted local files are excluded."
            setPadding(0, padding, 0, 0)
        }, matchWrap())
        return root
    }

    private fun loadDay() {
        releasePlayer()
        val bounds = dayBounds(dayStartMs)
        chunks = repository.playableChunksForDay(bounds.first, bounds.second)
        totalDurationMs = chunks.sumOf { it.durationMs.coerceAtLeast(0L) }
        currentIndex = -1
        seekBar.max = totalDurationMs.coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()
        dayText.text = DAY_FORMAT.format(Date(dayStartMs))
        summaryText.text = if (chunks.isEmpty()) {
            "No playable local recordings for this day"
        } else {
            "${chunks.size} files · ${formatDuration(totalDurationMs)}"
        }
        positionText.text = "00:00:00 / ${formatDuration(totalDurationMs)}"
        playButton.isEnabled = chunks.isNotEmpty()
        previousButton.isEnabled = true
        nextButton.isEnabled = dayStartMs < startOfDay(System.currentTimeMillis())
    }

    private fun changeDay(days: Int) {
        dayStartMs = startOfDay(dayStartMs + days * DAY_MS)
        loadDay()
    }

    private fun togglePlayback() {
        val activePlayer = player
        if (activePlayer?.isPlaying == true) {
            activePlayer.pause()
            playButton.text = "Play"
            return
        }
        if (activePlayer != null && !preparing) {
            activePlayer.start()
            playButton.text = "Pause"
            return
        }
        seekToGlobal(currentGlobalPosition())
    }

    private fun seekToGlobal(positionMs: Long) {
        if (chunks.isEmpty()) return
        var remaining = positionMs.coerceIn(0L, totalDurationMs)
        var targetIndex = 0
        while (targetIndex < chunks.lastIndex && remaining >= chunks[targetIndex].durationMs) {
            remaining -= chunks[targetIndex].durationMs
            targetIndex++
        }
        prepareChunk(targetIndex, remaining, autoplay = true)
    }

    private fun prepareChunk(index: Int, offsetMs: Long, autoplay: Boolean) {
        releasePlayer()
        currentIndex = index
        preparing = true
        val next = MediaPlayer()
        player = next
        next.setAudioStreamType(AudioManager.STREAM_MUSIC)
        next.setDataSource(chunks[index].path)
        next.setOnPreparedListener {
            preparing = false
            it.seekTo(offsetMs.coerceIn(0L, it.duration.toLong()).toInt())
            if (autoplay) {
                it.start()
                playButton.text = "Pause"
            }
            renderProgress()
        }
        next.setOnCompletionListener {
            if (currentIndex < chunks.lastIndex) {
                prepareChunk(currentIndex + 1, 0L, autoplay = true)
            } else {
                playButton.text = "Play"
                seekBar.progress = seekBar.max
                positionText.text = "${formatDuration(totalDurationMs)} / ${formatDuration(totalDurationMs)}"
            }
        }
        next.setOnErrorListener { _, _, _ ->
            preparing = false
            playButton.text = "Play"
            summaryText.text = "Could not play ${chunks[index].path.substringAfterLast('/')}"
            true
        }
        next.prepareAsync()
    }

    private fun currentGlobalPosition(): Long {
        if (currentIndex < 0) return 0L
        val before = chunks.take(currentIndex).sumOf { it.durationMs }
        return before + (player?.currentPosition?.toLong() ?: 0L)
    }

    private fun renderProgress() {
        if (userSeeking || totalDurationMs <= 0L) return
        val position = currentGlobalPosition().coerceIn(0L, totalDurationMs)
        seekBar.progress = position.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        positionText.text = "${formatDuration(position)} / ${formatDuration(totalDurationMs)}"
    }

    private fun releasePlayer() {
        preparing = false
        player?.let {
            runCatching { it.reset() }
            it.release()
        }
        player = null
        playButtonOrNull()?.let { it.text = "Play" }
    }

    private fun playButtonOrNull(): Button? = if (::playButton.isInitialized) playButton else null

    private fun startOfDay(timeMs: Long): Long = Calendar.getInstance().apply {
        timeInMillis = timeMs
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun dayBounds(start: Long): Pair<Long, Long> {
        val calendar = Calendar.getInstance().apply { timeInMillis = start }
        calendar.add(Calendar.DAY_OF_MONTH, 1)
        return start to calendar.timeInMillis
    }

    private fun formatDuration(durationMs: Long): String {
        val totalSeconds = (durationMs / 1_000L).coerceAtLeast(0L)
        return String.format(Locale.ROOT, "%02d:%02d:%02d", totalSeconds / 3600, (totalSeconds % 3600) / 60, totalSeconds % 60)
    }

    private fun matchWrap() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    )

    private fun weightWrap() = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

    companion object {
        private const val DAY_MS = 24L * 60L * 60L * 1_000L
        private val DAY_FORMAT = SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault())
    }
}
