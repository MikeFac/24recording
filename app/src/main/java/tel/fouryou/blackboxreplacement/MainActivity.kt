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
import android.text.InputType
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.ArrayAdapter
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var stateText: TextView
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private lateinit var transcriptionModelSpinner: Spinner
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
        UploadCoordinator.start(this)
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

        val visibleModeButton = Button(this).apply {
            text = "Open visible recording mode"
            setOnClickListener {
                startActivity(Intent(this@MainActivity, VisibleRecordingActivity::class.java))
            }
        }
        content.addView(visibleModeButton, matchWrap())

        val guideButton = Button(this).apply {
            text = "Legal and permission guide"
            setOnClickListener {
                startActivity(Intent(this@MainActivity, LegalAndPermissionsActivity::class.java))
            }
        }
        content.addView(guideButton, matchWrap())

        val uploadSettingsButton = Button(this).apply {
            text = "Configure server upload"
            setOnClickListener { showUploadSettings() }
        }
        content.addView(uploadSettingsButton, matchWrap())

        val transcriptionLabel = TextView(this).apply {
            text = "On-device live transcription"
            setPadding(0, 24, 0, 4)
        }
        content.addView(transcriptionLabel, matchWrap())

        transcriptionModelSpinner = Spinner(this).apply {
            val models = listOf(
                LocalTranscriptionModel.OFF,
                LocalTranscriptionModel.MOONSHINE_TINY,
                LocalTranscriptionModel.ZIPFORMER_STREAMING
            )
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_item,
                models.map { transcriptionLabel(it) }
            ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
            setSelection(models.indexOf(TranscriptionPreferences.getModel(this@MainActivity)))
            setOnItemSelectedListener(object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
                override fun onItemSelected(
                    parent: android.widget.AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    TranscriptionPreferences.setModel(this@MainActivity, models[position])
                }
            })
        }
        content.addView(transcriptionModelSpinner, matchWrap())

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
        TranscriptionPreferences.setModel(
            this,
            listOf(
                LocalTranscriptionModel.OFF,
                LocalTranscriptionModel.MOONSHINE_TINY,
                LocalTranscriptionModel.ZIPFORMER_STREAMING
            )[transcriptionModelSpinner.selectedItemPosition]
        )
        val intent = Intent(this, CaptureService::class.java)
            .setAction(CaptureService.ACTION_START)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        startActivity(Intent(this, VisibleRecordingActivity::class.java))
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
        showStopConfirmation()
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

    private fun showUploadSettings() {
        val current = UploadPreferences.get(this)
        val fields = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (resources.displayMetrics.density * 24).toInt()
            setPadding(padding, 8, padding, 0)
        }
        val url = android.widget.EditText(this).apply {
            hint = "https://e-agent.4you.uno"
            setText(current.serverUrl)
            inputType = InputType.TYPE_TEXT_VARIATION_URI
        }
        val key = android.widget.EditText(this).apply {
            hint = "Pilot API key"
            setText(current.apiKey)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        fields.addView(TextView(this).apply { text = "Server URL" })
        fields.addView(url)
        fields.addView(TextView(this).apply { text = "API key" })
        fields.addView(key)
        AlertDialog.Builder(this)
            .setTitle("Server processing")
            .setMessage("Original M4A chunks upload over HTTPS. Cleanup and transcription happen on the server.")
            .setView(fields)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                val serverUrl = url.text.toString().trimEnd('/')
                val apiKey = key.text.toString().trim()
                if (!serverUrl.startsWith("https://") || apiKey.isBlank()) {
                    Toast.makeText(this, "Use an HTTPS URL and a non-empty API key", Toast.LENGTH_LONG).show()
                } else {
                    UploadPreferences.save(this, serverUrl, apiKey)
                    UploadCoordinator.start(this)
                    Toast.makeText(this, "Upload configured", Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    private fun matchWrap() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    )

    private fun transcriptionLabel(model: LocalTranscriptionModel): String = when (model) {
        LocalTranscriptionModel.OFF -> "Off"
        LocalTranscriptionModel.MOONSHINE_TINY -> "Moonshine Tiny · local"
        LocalTranscriptionModel.ZIPFORMER_STREAMING -> "Zipformer · local streaming"
    }

    companion object {
        private const val REQUEST_PERMISSIONS = 100
    }
}
