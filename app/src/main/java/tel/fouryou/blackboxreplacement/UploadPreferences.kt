package tel.fouryou.blackboxreplacement

import android.content.Context
import java.util.UUID

data class UploadSettings(val serverUrl: String, val apiKey: String, val deviceId: String) {
    fun enabled() = serverUrl.startsWith("https://") && apiKey.isNotBlank()
}

object UploadPreferences {
    private const val NAME = "server_upload"
    private const val URL = "server_url"
    private const val API_KEY = "api_key"
    private const val DEVICE_ID = "device_id"

    fun get(context: Context): UploadSettings {
        val preferences = context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        var deviceId = preferences.getString(DEVICE_ID, null)
        if (deviceId.isNullOrBlank()) {
            deviceId = UUID.randomUUID().toString()
            preferences.edit().putString(DEVICE_ID, deviceId).apply()
        }
        return UploadSettings(
            preferences.getString(URL, "https://e-agent.4you.uno")!!.trimEnd('/'),
            preferences.getString(API_KEY, "")!!,
            deviceId,
        )
    }

    fun save(context: Context, serverUrl: String, apiKey: String) {
        val current = get(context)
        context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putString(URL, serverUrl.trimEnd('/'))
            .putString(API_KEY, apiKey.trim())
            .putString(DEVICE_ID, current.deviceId)
            .apply()
    }
}
