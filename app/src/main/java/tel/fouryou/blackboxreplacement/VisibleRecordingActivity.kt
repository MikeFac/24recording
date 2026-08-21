package tel.fouryou.blackboxreplacement

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

/**
 * A deliberately conspicuous, low-brightness display of the recorder's persisted state.
 * It never infers recording from the Activity lifecycle or button presses.
 */
class VisibleRecordingActivity : Activity() {
    private lateinit var indicator: View
    private lateinit var statusText: TextView
    private lateinit var explanationText: TextView
    private lateinit var elapsedText: TextView
    private lateinit var errorText: TextView
    private lateinit var instructionButton: Button
    private lateinit var stopButton: Button

    private val handler = Handler(Looper.getMainLooper())
    private val refresh = object : Runnable {
        override fun run() {
            render(CaptureService.snapshot(this@VisibleRecordingActivity))
            handler.postDelayed(this, REFRESH_INTERVAL_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(createContent())
        hideStatusBar()
        window.attributes = window.attributes.apply { screenBrightness = VISIBLE_BRIGHTNESS }
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        handler.post(refresh)
    }

    override fun onPause() {
        handler.removeCallbacks(refresh)
        super.onPause()
    }

    private fun createContent(): View {
        val density = resources.displayMetrics.density
        val horizontalPadding = (24 * density).toInt()

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(horizontalPadding, horizontalPadding, horizontalPadding, horizontalPadding)
            setBackgroundColor(Color.BLACK)
        }

        indicator = View(this).apply {
            contentDescription = "Recording status indicator"
        }
        content.addView(indicator, LinearLayout.LayoutParams((72 * density).toInt(), (72 * density).toInt()).apply {
            bottomMargin = (28 * density).toInt()
        })

        statusText = TextView(this).apply {
            gravity = Gravity.CENTER
            textSize = 38f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(RECORDING_RED)
        }
        content.addView(statusText, matchWrap())

        explanationText = TextView(this).apply {
            gravity = Gravity.CENTER
            textSize = 21f
            setTextColor(Color.WHITE)
            setPadding(0, (24 * density).toInt(), 0, (20 * density).toInt())
        }
        content.addView(explanationText, matchWrap())

        elapsedText = TextView(this).apply {
            gravity = Gravity.CENTER
            textSize = 32f
            setTypeface(Typeface.MONOSPACE, Typeface.NORMAL)
            setTextColor(Color.LTGRAY)
        }
        content.addView(elapsedText, matchWrap())

        errorText = TextView(this).apply {
            gravity = Gravity.CENTER
            textSize = 16f
            setTextColor(Color.WHITE)
            setPadding(0, (20 * density).toInt(), 0, 0)
        }
        content.addView(errorText, matchWrap())

        instructionButton = Button(this).apply {
            text = "Hold to mark instruction"
            contentDescription = "Press and hold to mark an explicit instruction"
            setOnClickListener {
                val snapshot = CaptureService.snapshot(this@VisibleRecordingActivity)
                if (snapshot.instructionActive) {
                    sendInstructionAction(CaptureService.ACTION_END_INSTRUCTION)
                } else {
                    Toast.makeText(
                        this@VisibleRecordingActivity,
                        "Press and hold to start an instruction marker",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            setOnLongClickListener {
                if (CaptureService.isRecording(this@VisibleRecordingActivity)) {
                    sendInstructionAction(CaptureService.ACTION_BEGIN_INSTRUCTION)
                }
                true
            }
        }
        content.addView(instructionButton, matchWrap().apply {
            topMargin = (18 * density).toInt()
        })

        stopButton = Button(this).apply {
            text = "Hold to stop recording"
            contentDescription = "Press and hold to stop recording"
            setPadding(0, (8 * density).toInt(), 0, (8 * density).toInt())
            setOnClickListener {
                Toast.makeText(
                    this@VisibleRecordingActivity,
                    "Press and hold to stop recording",
                    Toast.LENGTH_SHORT
                ).show()
            }
            setOnLongClickListener {
                showStopConfirmation()
                true
            }
        }
        content.addView(stopButton, matchWrap().apply {
            topMargin = (36 * density).toInt()
        })

        val controlsButton = Button(this).apply {
            text = "Return to controls"
            setOnClickListener { finish() }
        }
        content.addView(controlsButton, matchWrap())

        return content
    }

    private fun showStopConfirmation() {
        if (!CaptureService.isRecording(this)) return
        StopConfirmation.show(this) {
            startService(Intent(this, CaptureService::class.java).setAction(CaptureService.ACTION_STOP))
        }
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action != StopConfirmation.ACTION_REQUEST_STOP) return
        intent.action = null
        showStopConfirmation()
    }

    private fun render(snapshot: CaptureSnapshot) {
        val state = runCatching { CaptureState.valueOf(snapshot.state) }.getOrDefault(CaptureState.ERROR)
        val active = state == CaptureState.STARTING || state == CaptureState.RECORDING

        if (active) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        when (state) {
            CaptureState.RECORDING -> showStatus(
                color = RECORDING_RED,
                title = "NOW RECORDING",
                explanation = "Audio is being recorded by this phone.\nInput: ${snapshot.inputRouteName}"
            )
            CaptureState.STARTING -> showStatus(
                color = STARTING_AMBER,
                title = "STARTING",
                explanation = "Preparing the microphone. Recording is not yet confirmed."
            )
            CaptureState.STOPPING -> showStatus(
                color = STARTING_AMBER,
                title = "STOPPING",
                explanation = "Finalising the current audio file."
            )
            CaptureState.STOPPED -> showStatus(
                color = STOPPED_GREY,
                title = "NOT RECORDING",
                explanation = "Audio recording is stopped."
            )
            CaptureState.ERROR -> showStatus(
                color = ERROR_RED,
                title = "RECORDING ERROR",
                explanation = "Audio recording is not active."
            )
        }

        val showElapsed = snapshot.startedAt != null && (
            state == CaptureState.RECORDING || state == CaptureState.STOPPING
        )
        elapsedText.text = if (showElapsed) formatElapsed(snapshot.startedAt) else ""
        elapsedText.visibility = if (showElapsed) View.VISIBLE else View.GONE
        errorText.text = snapshot.lastError.orEmpty()
        errorText.visibility = if (snapshot.lastError.isNullOrBlank()) View.GONE else View.VISIBLE
        stopButton.isEnabled = active
        stopButton.alpha = if (active) 1f else 0.45f
        instructionButton.isEnabled = active
        instructionButton.alpha = if (active) 1f else 0.45f
        instructionButton.text = if (snapshot.instructionActive) {
            "End instruction marker"
        } else {
            "Hold to mark instruction"
        }
        instructionButton.contentDescription = if (snapshot.instructionActive) {
            "End explicit instruction marker"
        } else {
            "Press and hold to mark an explicit instruction"
        }
    }

    private fun sendInstructionAction(action: String) {
        startService(Intent(this, CaptureService::class.java).setAction(action))
    }

    private fun showStatus(color: Int, title: String, explanation: String) {
        indicator.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }
        statusText.text = title
        statusText.setTextColor(color)
        explanationText.text = explanation
        indicator.contentDescription = title
    }

    @Suppress("DEPRECATION")
    private fun hideStatusBar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Samsung firmware can expose a null controller until the content view
            // has been attached. The activity must remain usable even if immersive
            // status-bar hiding is unavailable on a particular device.
            window.decorView.windowInsetsController?.hide(WindowInsets.Type.statusBars())
        } else {
            window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        }
    }

    private fun formatElapsed(startedAt: Long?): String {
        val totalSeconds = startedAt
            ?.let { ((System.currentTimeMillis() - it) / 1_000L).coerceAtLeast(0L) }
            ?: return "00:00:00"
        val hours = totalSeconds / 3_600L
        val minutes = (totalSeconds % 3_600L) / 60L
        val seconds = totalSeconds % 60L
        return String.format(Locale.ROOT, "%02d:%02d:%02d", hours, minutes, seconds)
    }

    private fun matchWrap() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    )

    companion object {
        private const val REFRESH_INTERVAL_MS = 1_000L
        private const val VISIBLE_BRIGHTNESS = 0.18f
        private val RECORDING_RED = Color.rgb(255, 55, 55)
        private val ERROR_RED = Color.rgb(255, 80, 80)
        private val STARTING_AMBER = Color.rgb(255, 180, 0)
        private val STOPPED_GREY = Color.rgb(145, 145, 145)
    }
}
