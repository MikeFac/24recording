package tel.fouryou.blackboxreplacement

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class LegalAndPermissionsActivity : Activity() {
    private lateinit var permissionStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Legal and setup guide"
        setContentView(createContent())
    }

    override fun onResume() {
        super.onResume()
        renderPermissionStatus()
    }

    private fun createContent(): View {
        val padding = (resources.displayMetrics.density * 24).toInt()
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
        }

        addHeading(content, "Legal and consent notice", 24f)
        addBody(content, LegalNotice.RECORDING_NOTICE)

        addHeading(content, "Required setup")
        addBody(
            content,
            """
1. Microphone: choose Allow when Android asks. The app cannot record without it.

2. Notifications: on Android 13 or later, choose Allow so the persistent recording notification and Stop action remain visible. This pilot treats notification permission as required even though Android can technically start some foreground services without it.

3. Battery: open App settings, choose Battery or App battery usage, and select Unrestricted or Allow background usage where available. Never select Restricted. Names vary by manufacturer.

4. Start correctly: open the app while it is visible and press Start recording. Current Android versions restrict starting microphone capture from the background.

5. Verify: confirm the screen says RECORDING, Android shows the microphone privacy indicator and persistent notification, and the chunk/error counters remain healthy.

6. Stop correctly: use Stop recording in the app or notification and wait for STOPPED so the current M4A file can be finalized.
            """.trimIndent()
        )

        permissionStatus = TextView(this).apply {
            textSize = 16f
            setPadding(0, 16, 0, 16)
        }
        content.addView(permissionStatus, matchWrap())

        content.addView(Button(this).apply {
            text = "Request runtime permissions"
            setOnClickListener { requestRuntimePermissions() }
        }, matchWrap())

        content.addView(Button(this).apply {
            text = "Open app settings"
            setOnClickListener {
                startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:$packageName")
                    )
                )
            }
        }, matchWrap())

        content.addView(Button(this).apply {
            text = "Open battery optimization settings"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        }, matchWrap())

        addHeading(content, "Troubleshooting")
        addBody(
            content,
            """
If Start does nothing, open App settings → Permissions and allow Microphone, then return while the app is visible.

If recording stops with the screen locked, ensure battery usage is not Restricted, disable vendor-specific automatic app sleeping for this app, and test again.

If no notification appears, enable notifications for this app and its Audio capture channel.

If a microphone is unplugged or Bluetooth disconnects, stop and make a short test recording after reconnecting. Automatic input selection and disconnect recovery are not yet certified in this pilot.
            """.trimIndent()
        )

        return ScrollView(this).apply { addView(content) }
    }

    private fun requestRuntimePermissions() {
        val missing = buildList {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                add(Manifest.permission.RECORD_AUDIO)
            }
            if (
                Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (missing.isNotEmpty()) {
            requestPermissions(missing.toTypedArray(), REQUEST_PERMISSIONS)
        } else {
            renderPermissionStatus()
        }
    }

    private fun renderPermissionStatus() {
        val microphone = if (
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        ) "granted" else "not granted"
        val notifications = if (
            Build.VERSION.SDK_INT < 33 ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        ) "granted" else "not granted"
        permissionStatus.text = "Microphone: $microphone\nNotifications: $notifications"
    }

    private fun addHeading(parent: LinearLayout, text: String, size: Float = 20f) {
        parent.addView(TextView(this).apply {
            this.text = text
            textSize = size
            setPadding(0, 20, 0, 8)
        }, matchWrap())
    }

    private fun addBody(parent: LinearLayout, text: String) {
        parent.addView(TextView(this).apply {
            this.text = text
            textSize = 15f
            setTextIsSelectable(true)
        }, matchWrap())
    }

    private fun matchWrap() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    )

    companion object {
        private const val REQUEST_PERMISSIONS = 200
    }
}
