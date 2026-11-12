package com.unplugged.app

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolMetricsTest {
    @Test
    fun metricsStoreContainsOnlyBoundedDiagnosticFields() {
        val directory = Files.createTempDirectory("sancho-tool-metrics").toFile()
        try {
            val file = directory.resolve("tool-events.jsonl")
            val store = FileToolMetricsStore(file)
            store.record(
                ToolExecutionMetric(
                    recordedAtMillis = 42L,
                    tool = "open_web_search",
                    provider = "brave",
                    status = ToolExecutionStatus.SUCCEEDED,
                    elapsedMillis = 1_234L,
                    resultCount = 5,
                    truncated = false,
                    errorCode = null,
                ),
            )

            val raw = file.readText()
            assertTrue(raw.contains("open_web_search"))
            assertTrue(raw.contains("1234"))
            assertFalse(raw.contains("private search query"))
            assertFalse(raw.contains("https://example.test"))
            assertFalse(raw.contains("model response"))
            assertEquals(1, store.load().size)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun metricsStoreRetainsOnlyTheMostRecentHundredEvents() {
        val directory = Files.createTempDirectory("sancho-tool-metrics-limit").toFile()
        try {
            val store = FileToolMetricsStore(directory.resolve("tool-events.jsonl"))
            repeat(125) { index ->
                store.record(
                    ToolExecutionMetric(
                        recordedAtMillis = index.toLong(),
                        tool = "open_web_search",
                        provider = "searxng",
                        status = ToolExecutionStatus.FAILED,
                        elapsedMillis = index.toLong(),
                        resultCount = 0,
                        truncated = false,
                        errorCode = "network_failure",
                    ),
                )
            }

            val events = store.load()
            assertEquals(100, events.size)
            assertEquals(25L, events.first().recordedAtMillis)
            assertEquals(124L, events.last().recordedAtMillis)
        } finally {
            directory.deleteRecursively()
        }
    }
}
