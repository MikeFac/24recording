package tel.fouryou.blackboxreplacement

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var stateText: TextView
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private var startAfterPermissionGrant = false
    private val handler = Handler(Looper.getMainLooper())
    private val refresh = object : Runnable {
        override fun run() {
            renderState()
            handler.postDelayed(this, 1_000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CaptureService.reconcileStaleProcessState(this)
        setContentView(createContent())
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
        val padding = (resources.displayMetrics.density * 24).toInt()
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
        }

        val title = TextView(this).apply {
            text = "Blackbox Replacement"
            textSize = 24f
        }
        content.addView(title, matchWrap())

        val subtitle = TextView(this).apply {
            text = "Capture MVP · continuous microphone recording"
            textSize = 14f
            setPadding(0, 8, 0, 24)
        }
        content.addView(subtitle, matchWrap())

        stateText = TextView(this).apply {
            textSize = 16f
            setTextIsSelectable(true)
        }
        content.addView(stateText, matchWrap())

        startButton = Button(this).apply {
            text = "Start recording"
            setOnClickListener { startRecording() }
        }
        content.addView(startButton, matchWrap())

        stopButton = Button(this).apply {
            text = "Stop recording"
            setOnClickListener { stopRecording() }
        }
        content.addView(stopButton, matchWrap())

        val guideButton = Button(this).apply {
            text = "Legal and permission guide"
            setOnClickListener {
                startActivity(Intent(this@MainActivity, LegalAndPermissionsActivity::class.java))
            }
        }
        content.addView(guideButton, matchWrap())

        val note = TextView(this).apply {
            text = "Recording is deliberately visible. The notification remains active while audio capture is running."
            setPadding(0, 24, 0, 0)
        }
        content.addView(note, matchWrap())

        return ScrollView(this).apply { addView(content) }
    }

    private fun startRecording() {
        if (!LegalNotice.isAcknowledged(this)) {
            showLegalAcknowledgement()
            return
        }
        startRecordingAfterAcknowledgement()
    }

    private fun startRecordingAfterAcknowledgement() {
        if (!hasRequiredPermissions()) {
            startAfterPermissionGrant = true
            requestRequiredPermissionsIfNeeded()
            return
        }
        val intent = Intent(this, CaptureService::class.java)
            .setAction(CaptureService.ACTION_START)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun showLegalAcknowledgement() {
        AlertDialog.Builder(this)
            .setTitle("Before recording")
            .setMessage(LegalNotice.RECORDING_NOTICE)
            .setPositiveButton("I understand") { _, _ ->
                LegalNotice.acknowledge(this)
                startRecordingAfterAcknowledgement()
            }
            .setNeutralButton("Read guide") { _, _ ->
                startActivity(Intent(this, LegalAndPermissionsActivity::class.java))
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun stopRecording() {
        startService(Intent(this, CaptureService::class.java).setAction(CaptureService.ACTION_STOP))
    }

    private fun hasRequiredPermissions(): Boolean {
        val microphoneGranted = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val notificationsGranted = Build.VERSION.SDK_INT < 33 ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return microphoneGranted && notificationsGranted
    }

    private fun requestRequiredPermissionsIfNeeded() {
        val missing = buildList {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                add(Manifest.permission.RECORD_AUDIO)
            }
            if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (missing.isNotEmpty()) requestPermissions(missing.toTypedArray(), REQUEST_PERMISSIONS)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_PERMISSIONS || !startAfterPermissionGrant) return
        startAfterPermissionGrant = false
        if (hasRequiredPermissions()) {
            startRecordingAfterAcknowledgement()
        } else {
            AlertDialog.Builder(this)
                .setTitle("Permissions required")
                .setMessage("Microphone and notification permissions are required by this pilot. Open the setup guide for instructions.")
                .setPositiveButton("Open guide") { _, _ ->
                    startActivity(Intent(this, LegalAndPermissionsActivity::class.java))
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun renderState() {
        val snapshot = CaptureService.snapshot(this)
        stateText.text = snapshot.asDisplayText()
        val active = snapshot.state == CaptureState.RECORDING.name || snapshot.state == CaptureState.STARTING.name
        startButton.isEnabled = !active
        stopButton.isEnabled = active
    }

    private fun matchWrap() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    )

    companion object {
        private const val REQUEST_PERMISSIONS = 100
    }
}
