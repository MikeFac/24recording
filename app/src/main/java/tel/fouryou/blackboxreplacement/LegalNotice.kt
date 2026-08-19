package tel.fouryou.blackboxreplacement

import android.content.Context

object LegalNotice {
    const val RECORDING_NOTICE = """
This app continuously records audio after you press Start.

You are responsible for making sure every recording, use, disclosure, upload, and transcription is lawful where you are and is permitted by any workplace, venue, contract, confidentiality, and professional rules that apply.

Tell people when recording and obtain their consent whenever required. Do not use this app for covert surveillance, harassment, unlawful monitoring, or recording conversations in which you are not an authorised participant.

Recordings may contain highly sensitive information about you and other people. Protect the device and delete recordings you no longer need. The current pilot stores audio locally; future cloud processing requires a separate updated privacy notice before it is enabled.

This pilot is not guaranteed to capture every word and must not be relied on for emergencies, legal evidence, medical decisions, safety-critical records, or any situation where a missed or inaccurate recording could cause harm.

This notice is general information, not legal advice. Recording laws vary by jurisdiction and context. Obtain independent legal advice before real-world or commercial use.
"""

    private const val PREFERENCES = "legal_notice"
    private const val KEY_ACKNOWLEDGED_VERSION = "acknowledged_version"
    private const val CURRENT_VERSION = 1

    fun isAcknowledged(context: Context): Boolean = context
        .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        .getInt(KEY_ACKNOWLEDGED_VERSION, 0) >= CURRENT_VERSION

    fun acknowledge(context: Context) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_ACKNOWLEDGED_VERSION, CURRENT_VERSION)
            .apply()
    }
}
