package tel.fouryou.blackboxreplacement

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Recreates the pending upload work after a reboot or application update. */
class UploadRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> UploadCoordinator.triggerIfEnabled(context)
        }
    }
}
