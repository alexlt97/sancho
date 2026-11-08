package com.unplugged.app

import java.io.File
import kotlinx.coroutines.flow.collect

class VoiceModelSession(
    private val localLlm: LocalLlm,
) {
    suspend fun load(modelFile: File) {
        localLlm.load(modelFile)
    }

    suspend fun generate(prompt: String): String {
        return generateWithMetrics(prompt).response
    }

    suspend fun generateWithMetrics(prompt: String): VoiceGenerationResult {
        val startedAtNanos = System.nanoTime()
        var firstTokenAtNanos: Long? = null
        val response = StringBuilder()
        localLlm.stream(prompt).collect { chunk ->
            if (firstTokenAtNanos == null && chunk.isNotEmpty()) {
                firstTokenAtNanos = System.nanoTime()
            }
            response.append(chunk)
        }
        val finishedAtNanos = System.nanoTime()
        return VoiceGenerationResult(
            response = response.toString().trim(),
            timeToFirstTokenMillis = firstTokenAtNanos?.let { firstTokenAt ->
                elapsedMillis(startedAtNanos, firstTokenAt)
            },
            generationMillis = elapsedMillis(startedAtNanos, finishedAtNanos),
            outputCharacters = response.length,
        )
    }

    fun close() {
        localLlm.close()
    }

    private fun elapsedMillis(startNanos: Long, endNanos: Long): Long =
        ((endNanos - startNanos) / 1_000_000L).coerceAtLeast(0L)
}
