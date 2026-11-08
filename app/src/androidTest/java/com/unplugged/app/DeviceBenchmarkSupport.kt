package com.unplugged.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.os.Debug
import android.os.PowerManager
import android.os.Process
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
    val llm = LiteRtLocalLlm(
        context.cacheDir,
        backend,
        context.sanchoModelHarness(HarnessRole.CHAT_VISION),
    )

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
    val llm = LiteRtLocalLlm(
        context.cacheDir,
        backend,
        context.sanchoModelHarness(HarnessRole.CHAT_VISION),
    )

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

internal suspend fun runHarnessComparisonBenchmark() {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val context = instrumentation.targetContext
    val model = File(context.filesDir, "models")
        .listFiles { file -> file.isFile && file.name.endsWith(".litertlm", ignoreCase = true) }
        ?.maxByOrNull { file -> file.lastModified() }
        ?: error("No imported .litertlm model found in the AI profile.")
    val prompts = listOf(
        "Reply with one short sentence: say hello.",
        "Explain in one sentence why local inference can be private.",
        "Calculate five times twelve plus seven. Give only the result.",
    )
    val harness = context.sanchoModelHarness(HarnessRole.CHAT_VISION)
    val modes = listOf(
        HarnessComparisonMode("harness", harness),
        HarnessComparisonMode("no_harness", null),
    )
    val samples = modes.associate { mode -> mode.name to mutableListOf<ComparisonSample>() }

    prompts.forEachIndexed { index, prompt ->
        val order = if (index % 2 == 0) modes else modes.asReversed()
        order.forEach { mode ->
            samples.getValue(mode.name) += runComparisonSample(
                context = context,
                model = model,
                prompt = prompt,
                mode = mode,
            )
        }
    }

    modes.forEach { mode ->
        val modeSamples = samples.getValue(mode.name)
        val metrics = buildComparisonMetrics(
            model = model,
            mode = mode,
            samples = modeSamples,
        )
        instrumentation.addResults(Bundle().apply {
            putString("harness_comparison_${mode.name}", metrics)
        })
    }
}

internal suspend fun runLongPlainTextBenchmark() {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val context = instrumentation.targetContext
    val model = File(context.filesDir, "models")
        .listFiles { file -> file.isFile && file.name.endsWith(".litertlm", ignoreCase = true) }
        ?.maxByOrNull { file -> file.lastModified() }
        ?: error("No imported .litertlm model found in the AI profile.")
    val prompts = listOf(
        "Answer in plain text only, with no Markdown, no headings, and no bullet points. Explain in about 120 words how an offline language model processes a question on a phone.",
        "Answer in plain text only, with no Markdown, no headings, and no bullet points. Compare CPU and GPU inference on a phone in about 180 words, including startup cost, sustained generation, memory, and battery trade-offs.",
        "Answer in plain text only, with no Markdown, no headings, and no bullet points. Explain in about 240 words how to evaluate whether a long local-model response is slow because of model generation or because of client-side rendering.",
    )
    val samples = prompts.mapIndexed { index, prompt ->
        runLongPlainTextSample(context, model, index + 1, prompt)
    }
    val metrics = buildList {
        add("model=${model.name}")
        add("model_bytes=${model.length()}")
        add("backend=cpu")
        add("sample_count=${samples.size}")
        add("ttft_avg_ms=${samples.map { it.ttftMillis }.average().toLong()}")
        add("generation_avg_ms=${samples.map { it.generationMillis }.average().toLong()}")
        add("output_chars_avg=${samples.map { it.outputChars }.average().toLong()}")
        add("ttft_share_of_generation_avg_percent=${samples.map { it.ttftSharePercent }.average()}")
        add("markdown_marker_samples=${samples.count { it.markdownMarkersFound }}/${samples.size}")
        samples.forEach { sample ->
            add(
                "sample_${sample.index}=" + listOf(
                    "prompt_chars=${sample.promptChars}",
                    "load_ms=${sample.loadMillis}",
                    "ttft_ms=${sample.ttftMillis}",
                    "after_ttft_ms=${sample.generationMillis - sample.ttftMillis}",
                    "generation_ms=${sample.generationMillis}",
                    "ttft_share_percent=${sample.ttftSharePercent}",
                    "output_chars=${sample.outputChars}",
                    "markdown_markers=${sample.markdownMarkersFound}",
                ).joinToString(","),
            )
        }
    }.joinToString(";")
    instrumentation.addResults(Bundle().apply {
        putString("long_plain_text_benchmark", metrics)
    })
}

private suspend fun runLongPlainTextSample(
    context: android.content.Context,
    model: File,
    index: Int,
    prompt: String,
): LongPlainTextSample {
    val llm = LiteRtLocalLlm(
        context.cacheDir,
        Backend.CPU(),
        context.sanchoModelHarness(HarnessRole.CHAT_VISION),
    )
    return try {
        val loadStart = SystemClock.elapsedRealtimeNanos()
        llm.load(model)
        val loadMillis = elapsedMillisSince(loadStart)

        val generationStart = SystemClock.elapsedRealtimeNanos()
        var firstResponseMillis: Long? = null
        val response = StringBuilder()
        llm.stream(prompt).collect { chunk ->
            if (firstResponseMillis == null && chunk.isNotEmpty()) {
                firstResponseMillis = elapsedMillisSince(generationStart)
            }
            response.append(chunk)
        }
        val generationMillis = elapsedMillisSince(generationStart)
        val ttftMillis = firstResponseMillis ?: generationMillis
        assertTrue("Long plain-text sample $index returned no text.", response.isNotEmpty())
        LongPlainTextSample(
            index = index,
            promptChars = prompt.length,
            loadMillis = loadMillis,
            ttftMillis = ttftMillis,
            generationMillis = generationMillis,
            outputChars = response.length,
            ttftSharePercent = ttftMillis * 100.0 / generationMillis.coerceAtLeast(1L),
            markdownMarkersFound = listOf("```", "**", "| ").any(response::contains),
        )
    } finally {
        llm.close()
    }
}

private data class LongPlainTextSample(
    val index: Int,
    val promptChars: Int,
    val loadMillis: Long,
    val ttftMillis: Long,
    val generationMillis: Long,
    val outputChars: Int,
    val ttftSharePercent: Double,
    val markdownMarkersFound: Boolean,
)

private suspend fun runComparisonSample(
    context: android.content.Context,
    model: File,
    prompt: String,
    mode: HarnessComparisonMode,
): ComparisonSample {
    val llm = LiteRtLocalLlm(
        context.cacheDir,
        Backend.CPU(),
        mode.harness,
    )
    val processCpuBefore = Process.getElapsedCpuTime()
    val pssBefore = currentPssKb()
    return try {
        val loadStart = SystemClock.elapsedRealtimeNanos()
        llm.load(model)
        val loadMillis = elapsedMillisSince(loadStart)
        val processCpuAfterLoad = Process.getElapsedCpuTime()
        val pssAfterLoad = currentPssKb()

        val generationStart = SystemClock.elapsedRealtimeNanos()
        var firstResponseMillis: Long? = null
        val response = StringBuilder()
        llm.stream(prompt).collect { chunk ->
            if (firstResponseMillis == null) {
                firstResponseMillis = elapsedMillisSince(generationStart)
            }
            response.append(chunk)
        }
        val generationMillis = elapsedMillisSince(generationStart)
        val processCpuAfterGeneration = Process.getElapsedCpuTime()
        val pssAfterGeneration = currentPssKb()
        assertTrue("The ${mode.name} comparison returned no text.", response.isNotEmpty())

        ComparisonSample(
            promptChars = prompt.length,
            loadMillis = loadMillis,
            firstResponseMillis = firstResponseMillis ?: generationMillis,
            generationMillis = generationMillis,
            processCpuLoadMillis = processCpuAfterLoad - processCpuBefore,
            processCpuGenerationMillis = processCpuAfterGeneration - processCpuAfterLoad,
            pssBeforeKb = pssBefore,
            pssAfterLoadKb = pssAfterLoad,
            pssAfterGenerationKb = pssAfterGeneration,
            responseChars = response.length,
            thermalBefore = currentThermalStatus(context),
            thermalAfter = currentThermalStatus(context),
        )
    } finally {
        llm.close()
    }
}

private fun buildComparisonMetrics(
    model: File,
    mode: HarnessComparisonMode,
    samples: List<ComparisonSample>,
): String {
    val load = samples.map { it.loadMillis }
    val firstResponse = samples.map { it.firstResponseMillis }
    val generation = samples.map { it.generationMillis }
    val cpuLoad = samples.map { it.processCpuLoadMillis }
    val cpuGeneration = samples.map { it.processCpuGenerationMillis }
    val pssLoadDelta = samples.map { it.pssAfterLoadKb - it.pssBeforeKb }
    val pssGenerationDelta = samples.map { it.pssAfterGenerationKb - it.pssBeforeKb }
    val responseChars = samples.map { it.responseChars.toLong() }

    return buildList {
        add("model=${model.name}")
        add("model_bytes=${model.length()}")
        add("backend=cpu")
        add("mode=${mode.name}")
        add("system_instruction_chars=${mode.harness?.systemInstruction?.length ?: 0}")
        add("sample_count=${samples.size}")
        add("prompt_chars=${samples.first().promptChars}")
        add(metricSummary("load_ms", load))
        add(metricSummary("ttft_ms", firstResponse))
        add(metricSummary("generation_ms", generation))
        add(metricSummary("process_cpu_load_ms", cpuLoad))
        add(metricSummary("process_cpu_generation_ms", cpuGeneration))
        add(metricSummary("pss_delta_after_load_kb", pssLoadDelta))
        add(metricSummary("pss_delta_after_generation_kb", pssGenerationDelta))
        add(metricSummary("response_chars", responseChars))
        add("thermal_before=${samples.minOf { it.thermalBefore }}")
        add("thermal_after=${samples.maxOf { it.thermalAfter }}")
        samples.forEachIndexed { index, sample ->
            add(
                "sample_${index + 1}=" + listOf(
                    "load_ms=${sample.loadMillis}",
                    "ttft_ms=${sample.firstResponseMillis}",
                    "generation_ms=${sample.generationMillis}",
                    "process_cpu_load_ms=${sample.processCpuLoadMillis}",
                    "process_cpu_generation_ms=${sample.processCpuGenerationMillis}",
                    "pss_before_kb=${sample.pssBeforeKb}",
                    "pss_after_load_kb=${sample.pssAfterLoadKb}",
                    "pss_after_generation_kb=${sample.pssAfterGenerationKb}",
                    "response_chars=${sample.responseChars}",
                    "thermal_before=${sample.thermalBefore}",
                    "thermal_after=${sample.thermalAfter}",
                ).joinToString(","),
            )
        }
    }.joinToString(";")
}

private fun metricSummary(name: String, values: List<Long>): String =
    "$name" +
        "_avg=${values.average().toLong()}" +
        ",${name}_min=${values.minOrNull()}" +
        ",${name}_max=${values.maxOrNull()}"

private fun currentPssKb(): Long {
    val memoryInfo = Debug.MemoryInfo()
    Debug.getMemoryInfo(memoryInfo)
    return memoryInfo.totalPss.toLong()
}

private fun currentThermalStatus(context: android.content.Context): Int =
    context.getSystemService(PowerManager::class.java)?.currentThermalStatus ?: -1

private data class HarnessComparisonMode(
    val name: String,
    val harness: ModelHarness?,
)

private data class ComparisonSample(
    val promptChars: Int,
    val loadMillis: Long,
    val firstResponseMillis: Long,
    val generationMillis: Long,
    val processCpuLoadMillis: Long,
    val processCpuGenerationMillis: Long,
    val pssBeforeKb: Long,
    val pssAfterLoadKb: Long,
    val pssAfterGenerationKb: Long,
    val responseChars: Int,
    val thermalBefore: Int,
    val thermalAfter: Int,
)

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
