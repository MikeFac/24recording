package tel.fouryou.blackboxreplacement

enum class CaptureState {
    STOPPED,
    STARTING,
    RECORDING,
    STOPPING,
    ERROR
}

data class CaptureSnapshot(
    val state: String,
    val sessionId: String? = null,
    val startedAt: Long? = null,
    val lastError: String? = null,
    val completedChunks: Int = 0,
    val queuedChunks: Int = 0,
    val freeBytes: Long = 0L,
    val inputUnderruns: Long = 0L,
    val inputOverflows: Long = 0L,
    val inputRouteName: String = "Unknown",
    val instructionActive: Boolean = false,
    val instructionStartedAt: Long? = null
) {
    fun asDisplayText(): String {
        val duration = startedAt?.let { ((System.currentTimeMillis() - it) / 1_000L).coerceAtLeast(0) }
        return buildString {
            appendLine("State: $state")
            appendLine("Session: ${sessionId ?: "—"}")
            appendLine("Duration: ${duration ?: 0}s")
            appendLine("Completed chunks: $completedChunks")
            appendLine("Queued chunks: $queuedChunks")
            appendLine("Free storage: ${freeBytes / 1_048_576L} MB")
            appendLine("Input underruns/overflows: $inputUnderruns/$inputOverflows")
            appendLine("Actual audio input: $inputRouteName")
            if (instructionActive) appendLine("Instruction marker: active")
            if (!lastError.isNullOrBlank()) appendLine("Error: $lastError")
        }
    }
}
