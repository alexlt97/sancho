package com.unplugged.app

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class VoiceModelRuntime(
    private val cacheDirectory: File,
    private val harness: ModelHarness = ModelHarnessFactory.defaultVoiceAgent(),
    private val llmFactory: (() -> LocalLlm)? = null,
) {
    private val mutex = Mutex()
    private var session: VoiceModelSession? = null
    private var modelKey: String? = null

    suspend fun generate(modelFile: File, prompt: String): String =
        generateWithMetrics(modelFile, prompt).response

    suspend fun generateWithMetrics(modelFile: File, prompt: String): VoiceGenerationResult = mutex.withLock {
        val requestedKey = modelKey(modelFile)
        var modelLoadMillis = 0L
        if (session == null || modelKey != requestedKey) {
            closeCurrentSession()
            val nextSession = VoiceModelSession(
                llmFactory?.invoke() ?: LiteRtLocalLlm(cacheDirectory, harness = harness),
            )
            try {
                val loadStartedAtNanos = System.nanoTime()
                nextSession.load(modelFile)
                modelLoadMillis = elapsedMillis(loadStartedAtNanos, System.nanoTime())
            } catch (error: Throwable) {
                withContext(Dispatchers.IO) {
                    nextSession.close()
                }
                throw error
            }
            session = nextSession
            modelKey = requestedKey
        }
        checkNotNull(session).generateWithMetrics(prompt).copy(modelLoadMillis = modelLoadMillis)
    }

    suspend fun close() = mutex.withLock {
        closeCurrentSession()
    }

    private suspend fun closeCurrentSession() {
        session?.let { currentSession ->
            withContext(Dispatchers.IO) {
                currentSession.close()
            }
        }
        session = null
        modelKey = null
    }

    private fun modelKey(modelFile: File): String =
        modelFile.absolutePath + ":" + modelFile.length() + ":" + modelFile.lastModified()

    private fun elapsedMillis(startNanos: Long, endNanos: Long): Long =
        ((endNanos - startNanos) / 1_000_000L).coerceAtLeast(0L)
}

object VoiceModelRuntimeHolder {
    private var runtime: VoiceModelRuntime? = null

    fun get(cacheDirectory: File, harness: ModelHarness): VoiceModelRuntime = synchronized(this) {
        runtime ?: VoiceModelRuntime(cacheDirectory, harness = harness).also { runtime = it }
    }
}
