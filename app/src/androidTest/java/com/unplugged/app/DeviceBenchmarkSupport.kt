package com.unplugged.app

import android.os.Bundle
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import com.google.ai.edge.litertlm.Backend
import java.io.File
import kotlinx.coroutines.flow.collect
import org.junit.Assert.assertTrue

internal suspend fun runDeviceBenchmark(name: String, backend: Backend) {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val context = instrumentation.targetContext
    val model = File(context.filesDir, "models")
        .listFiles { file -> file.isFile && file.name.endsWith(".litertlm", ignoreCase = true) }
        ?.maxByOrNull { file -> file.lastModified() }
        ?: error("No imported .litertlm model found in the AI profile.")
    val llm = LiteRtLocalLlm(context.cacheDir, backend)

    try {
        val loadStart = SystemClock.elapsedRealtimeNanos()
        llm.load(model)
        val loadMillis = elapsedMillisSince(loadStart)

        val generationStart = SystemClock.elapsedRealtimeNanos()
        var firstResponseMillis: Long? = null
        val response = StringBuilder()
        llm.stream("Reply with one short sentence: say hello.").collect { chunk ->
            if (firstResponseMillis == null) {
                firstResponseMillis = elapsedMillisSince(generationStart)
            }
            response.append(chunk)
        }
        val totalGenerationMillis = elapsedMillisSince(generationStart)
        assertTrue("The $name backend returned no text.", response.isNotEmpty())

        val metrics = listOf(
            "model=${model.name}",
            "load_ms=$loadMillis",
            "first_response_ms=${firstResponseMillis ?: totalGenerationMillis}",
            "generation_ms=$totalGenerationMillis",
            "response_chars=${response.length}",
        ).joinToString(separator = ";")
        instrumentation.addResults(Bundle().apply { putString("${name}_benchmark", metrics) })
    } finally {
        llm.close()
    }
}

private fun elapsedMillisSince(startNanos: Long): Long =
    (SystemClock.elapsedRealtimeNanos() - startNanos) / 1_000_000L
