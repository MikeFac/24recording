package tel.fouryou.blackboxreplacement

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.text.InputType
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var stateText: TextView
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private lateinit var uploadStatusText: TextView
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
        UploadCoordinator.startIfEnabled(this)
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

        val syncButton = Button(this).apply {
            text = "Sync now"
            setOnClickListener {
                UploadCoordinator.trigger(this@MainActivity)
                Toast.makeText(this@MainActivity, "Sync queued", Toast.LENGTH_SHORT).show()
            }
        }
        content.addView(syncButton, matchWrap())

        uploadStatusText = TextView(this).apply { setPadding(0, 8, 0, 8) }
        content.addView(uploadStatusText, matchWrap())

        val retentionButton = Button(this).apply {
            text = "Manage local recordings"
            setOnClickListener { showRetentionDialog() }
        }
        content.addView(retentionButton, matchWrap())

        val importButton = Button(this).apply {
            text = "Import external recording"
            setOnClickListener {
                startActivityForResult(
                    Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "audio/*"
                    },
                    REQUEST_IMPORT_AUDIO
                )
            }
        }
        content.addView(importButton, matchWrap())

        val playbackButton = Button(this).apply {
            text = "Play recordings"
            setOnClickListener { startActivity(Intent(this@MainActivity, PlaybackActivity::class.java)) }
        }
        content.addView(playbackButton, matchWrap())

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

    @Deprecated("Use Activity Result APIs when this pilot moves to AndroidX Activity")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_IMPORT_AUDIO || resultCode != RESULT_OK) return
        val uri: Uri = data?.data ?: return
        try {
            val result = ExternalAudioImporter(this).import(uri)
            UploadCoordinator.triggerIfEnabled(this)
            Toast.makeText(
                this,
                "Imported ${result.displayName ?: "recording"}; queued for sync",
                Toast.LENGTH_LONG
            ).show()
        } catch (error: Throwable) {
            Toast.makeText(this, "Import failed: ${error.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun renderState() {
        val snapshot = CaptureService.snapshot(this)
        stateText.text = snapshot.asDisplayText()
        val active = snapshot.state == CaptureState.RECORDING.name || snapshot.state == CaptureState.STARTING.name
        startButton.isEnabled = !active
        stopButton.isEnabled = active
        val repository = ChunkRepository(this)
        try {
            val ready = repository.countChunks("READY")
            val uploaded = repository.countChunks("UPLOADED")
            val deleted = repository.countChunks("LOCAL_DELETED") + repository.countChunks("LOCAL_DELETED_UNUPLOADED")
            val status = UploadStatusStore.get(this)
            uploadStatusText.text = "Sync: ${status.state}\nQueued: $ready · Uploaded: $uploaded · Local deleted: $deleted" +
                if (status.error.isBlank()) "" else "\nError: ${status.error}"
        } finally {
            repository.closeQuietly()
        }
    }

    private fun showRetentionDialog() {
        val repository = ChunkRepository(this)
        val safe = try {
            repository.deletionPreview(true)
        } finally { repository.closeQuietly() }
        AlertDialog.Builder(this)
            .setTitle("Local recordings")
            .setMessage("Uploaded local files: ${safe.count} files (${formatBytes(safe.bytes)}).\n\nUnuploaded files are never removed by the safe option.")
            .setNegativeButton("Close", null)
            .setNeutralButton("Delete uploaded") { _, _ -> confirmDeletion(true, safe) }
            .setPositiveButton("Delete all local") { _, _ ->
                val repo = ChunkRepository(this)
                val preview = try { repo.deletionPreview(false) } finally { repo.closeQuietly() }
                confirmDeletion(false, preview)
            }
            .show()
    }

    private fun confirmDeletion(onlyUploaded: Boolean, preview: LocalDeletionPreview) {
        val warning = if (onlyUploaded) {
            "Delete ${preview.count} uploaded local files (${formatBytes(preview.bytes)})? Server copies will remain."
        } else {
            "DANGER: delete ${preview.count} local files (${formatBytes(preview.bytes)}), including recordings not uploaded to the server? This may permanently lose recordings."
        }
        AlertDialog.Builder(this)
            .setTitle(if (onlyUploaded) "Confirm deletion" else "Confirm permanent data loss")
            .setMessage(warning)
            .setNegativeButton("Cancel", null)
            .setPositiveButton(if (onlyUploaded) "Delete" else "Delete permanently") { _, _ ->
                val repo = ChunkRepository(this)
                try {
                    val deleted = repo.deleteLocalChunks(
                        onlyUploaded,
                        null
                    )
                    Toast.makeText(this, "Deleted ${deleted.count} local files", Toast.LENGTH_LONG).show()
                } catch (error: Throwable) {
                    Toast.makeText(this, "Deletion failed: ${error.message}", Toast.LENGTH_LONG).show()
                } finally { repo.closeQuietly() }
            }
            .show()
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes >= 1024L * 1024 * 1024 -> "%.1f GB".format(bytes / (1024.0 * 1024 * 1024))
        bytes >= 1024L * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
        else -> "$bytes bytes"
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
                    UploadCoordinator.startIfEnabled(this)
                    Toast.makeText(this, "Upload configured", Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    private fun matchWrap() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    )

    companion object {
        private const val REQUEST_PERMISSIONS = 100
        private const val REQUEST_IMPORT_AUDIO = 101
    }
}
