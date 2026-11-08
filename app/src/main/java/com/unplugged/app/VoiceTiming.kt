package com.unplugged.app

enum class VoicePhase(val label: String) {
    LISTENING("Listening"),
    THINKING("Thinking"),
    SPEAKING("Speaking"),
    READY("Ready"),
    ERROR("Error"),
    STOPPED("Stopped"),
}

data class VoiceGenerationResult(
    val response: String,
    val modelLoadMillis: Long = 0L,
    val timeToFirstTokenMillis: Long? = null,
    val generationMillis: Long = 0L,
    val outputCharacters: Int = response.length,
)

data class VoiceTimingReport(
    val recognitionFirstPartialMillis: Long?,
    val recognitionMillis: Long?,
    val modelLoadMillis: Long,
    val modelTtftMillis: Long?,
    val modelGenerationMillis: Long?,
    val ttsMillis: Long?,
    val totalMillis: Long,
)

class VoiceTimingRecorder(
    private val nowNanos: () -> Long = System::nanoTime,
) {
    private val sessionStartedAtNanos = nowNanos()
    private var transcriptFirstPartialAtNanos: Long? = null
    private var transcriptReceivedAtNanos: Long? = null
    private var modelResult: VoiceGenerationResult? = null
    private var speechStartedAtNanos: Long? = null
    private var speechFinishedAtNanos: Long? = null

    fun markTranscriptPartial() {
        if (transcriptFirstPartialAtNanos == null) {
            transcriptFirstPartialAtNanos = nowNanos()
        }
    }

    fun markTranscriptReceived() {
        transcriptReceivedAtNanos = nowNanos()
    }

    fun recordModel(result: VoiceGenerationResult) {
        modelResult = result
    }

    fun markSpeechStarted() {
        speechStartedAtNanos = nowNanos()
    }

    fun markSpeechFinished() {
        speechFinishedAtNanos = nowNanos()
    }

    fun report(): VoiceTimingReport {
        val model = modelResult
        return VoiceTimingReport(
            recognitionFirstPartialMillis = transcriptFirstPartialAtNanos?.let(::elapsedMillisFromStart),
            recognitionMillis = transcriptReceivedAtNanos?.let(::elapsedMillisFromStart),
            modelLoadMillis = model?.modelLoadMillis ?: 0L,
            modelTtftMillis = model?.timeToFirstTokenMillis,
            modelGenerationMillis = model?.generationMillis,
            ttsMillis = speechStartedAtNanos?.let { started ->
                speechFinishedAtNanos?.let { finished -> elapsedMillis(started, finished) }
            },
            totalMillis = elapsedMillisFromStart(speechFinishedAtNanos ?: nowNanos()),
        )
    }

    private fun elapsedMillisFromStart(atNanos: Long): Long =
        elapsedMillis(sessionStartedAtNanos, atNanos)

    private fun elapsedMillis(startNanos: Long, endNanos: Long): Long =
        ((endNanos - startNanos) / 1_000_000L).coerceAtLeast(0L)
}

internal fun VoiceTimingReport.compactModelSummary(): String {
    val ttft = modelTtftMillis?.let { "TTFT ${it / 1000.0}s" } ?: "TTFT n/a"
    val generation = modelGenerationMillis?.let { "model ${it / 1000.0}s" } ?: "model n/a"
    return "$ttft · $generation"
}
