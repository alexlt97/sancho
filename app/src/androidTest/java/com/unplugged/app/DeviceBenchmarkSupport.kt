package com.unplugged.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import com.google.ai.edge.litertlm.Backend
import java.io.File
import java.io.FileOutputStream
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

internal suspend fun runVisionBenchmark(name: String, backend: Backend) {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val context = instrumentation.targetContext
    val model = File(context.filesDir, "models")
        .listFiles { file -> file.isFile && file.name.endsWith(".litertlm", ignoreCase = true) }
        ?.maxByOrNull { file -> file.lastModified() }
        ?: error("No imported .litertlm model found in the AI profile.")
    val image = File(context.cacheDir, "vision-benchmark.png")
    createBenchmarkImage(image)
    val llm = LiteRtLocalLlm(context.cacheDir, backend)

    try {
        val loadStart = SystemClock.elapsedRealtimeNanos()
        llm.load(model)
        val loadMillis = elapsedMillisSince(loadStart)

        val generationStart = SystemClock.elapsedRealtimeNanos()
        var firstResponseMillis: Long? = null
        val response = StringBuilder()
        llm.stream(
            LlmRequest(
                prompt = "Describe the geometric shapes and their colors in one short sentence.",
                imageFile = image,
            ),
        ).collect { chunk ->
            if (firstResponseMillis == null) {
                firstResponseMillis = elapsedMillisSince(generationStart)
            }
            response.append(chunk)
        }
        val totalGenerationMillis = elapsedMillisSince(generationStart)
        assertTrue("The $name vision backend returned no text.", response.isNotEmpty())

        val metrics = listOf(
            "model=${model.name}",
            "image=${image.name}",
            "image_bytes=${image.length()}",
            "load_ms=$loadMillis",
            "first_response_ms=${firstResponseMillis ?: totalGenerationMillis}",
            "generation_ms=$totalGenerationMillis",
            "response_chars=${response.length}",
        ).joinToString(separator = ";")
        instrumentation.addResults(Bundle().apply { putString("vision_${name}_benchmark", metrics) })
    } finally {
        llm.close()
        image.delete()
    }
}

private fun createBenchmarkImage(destination: File) {
    val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
    try {
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        canvas.drawRect(64f, 64f, 240f, 240f, Paint().apply { color = Color.RED })
        canvas.drawCircle(368f, 160f, 88f, Paint().apply { color = Color.BLUE })
        canvas.drawRect(64f, 320f, 240f, 448f, Paint().apply { color = Color.GREEN })
        canvas.drawCircle(368f, 384f, 64f, Paint().apply { color = Color.YELLOW })
        FileOutputStream(destination).use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                "Could not create the vision benchmark image."
            }
        }
    } finally {
        bitmap.recycle()
    }
}

private fun elapsedMillisSince(startNanos: Long): Long =
    (SystemClock.elapsedRealtimeNanos() - startNanos) / 1_000_000L
