package tel.fouryou.blackboxreplacement

import android.content.Context

object TranscriptionPreferences {
    private const val PREFERENCES = "transcription_preferences"
    private const val KEY_ENABLED = "live_transcription_enabled"
    private const val KEY_MODEL = "local_model"

    fun isLiveEnabled(context: Context): Boolean = context
        .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        .getBoolean(KEY_ENABLED, false)

    fun setLiveEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, enabled)
            .apply()
    }

    fun getModel(context: Context): LocalTranscriptionModel {
        val stored = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getString(KEY_MODEL, LocalTranscriptionModel.MOONSHINE_TINY.name)
        return runCatching { LocalTranscriptionModel.valueOf(stored.orEmpty()) }
            .getOrDefault(LocalTranscriptionModel.MOONSHINE_TINY)
    }

    fun setModel(context: Context, model: LocalTranscriptionModel) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_MODEL, model.name)
            .apply()
    }
}
