package com.unplugged.app

import java.io.File
import org.json.JSONObject

private const val MAX_STORED_TOOL_EVENTS = 100

/** A deliberately content-free record for measuring tool behavior. */
data class ToolExecutionMetric(
    val recordedAtMillis: Long,
    val tool: String,
    val provider: String?,
    val status: ToolExecutionStatus,
    val elapsedMillis: Long,
    val resultCount: Int,
    val truncated: Boolean,
    val errorCode: String?,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("recorded_at_millis", recordedAtMillis)
        put("tool", tool)
        put("provider", provider ?: JSONObject.NULL)
        put("status", status.name)
        put("elapsed_millis", elapsedMillis)
        put("result_count", resultCount)
        put("truncated", truncated)
        put("error_code", errorCode ?: JSONObject.NULL)
    }

    companion object {
        fun fromJson(json: JSONObject): ToolExecutionMetric = ToolExecutionMetric(
            recordedAtMillis = json.getLong("recorded_at_millis"),
            tool = json.getString("tool"),
            provider = json.optString("provider").takeIf { it.isNotBlank() },
            status = ToolExecutionStatus.valueOf(json.getString("status")),
            elapsedMillis = json.getLong("elapsed_millis"),
            resultCount = json.getInt("result_count"),
            truncated = json.getBoolean("truncated"),
            errorCode = json.optString("error_code").takeIf { it.isNotBlank() },
        )
    }
}

fun interface ToolMetricsRecorder {
    fun record(metric: ToolExecutionMetric)
}

/**
 * App-private, bounded JSONL storage. It intentionally has no query or output
 * fields, so pulling this file for diagnostics cannot reveal conversation text.
 */
class FileToolMetricsStore(
    private val file: File,
) : ToolMetricsRecorder {
    private val lock = Any()

    override fun record(metric: ToolExecutionMetric) = synchronized(lock) {
        file.parentFile?.mkdirs()
        val retained = if (file.isFile) {
            file.readLines().takeLast(MAX_STORED_TOOL_EVENTS - 1)
        } else {
            emptyList()
        }
        val temporary = File(file.parentFile, ".${file.name}.tmp")
        temporary.writeText((retained + metric.toJson().toString()).joinToString("\n") + "\n")
        if (!temporary.renameTo(file)) {
            file.delete()
            check(temporary.renameTo(file)) { "Could not replace tool metrics file." }
        }
    }

    fun clear() = synchronized(lock) {
        file.delete()
    }

    fun load(): List<ToolExecutionMetric> = synchronized(lock) {
        if (!file.isFile) return emptyList()
        file.readLines().mapNotNull { line ->
            runCatching { ToolExecutionMetric.fromJson(JSONObject(line)) }.getOrNull()
        }
    }
}
