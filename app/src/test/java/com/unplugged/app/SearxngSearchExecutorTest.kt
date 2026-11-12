package com.unplugged.app

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearxngSearchExecutorTest {
    @Test
    fun searchUrlRequiresHttpsAndAddsJsonFormat() {
        val url = buildSearxngSearchUrl(
            endpoint = "https://search.example.test",
            query = "GrapheneOS latest release",
        )

        assertEquals(
            "https://search.example.test/search?q=GrapheneOS+latest+release&format=json",
            url,
        )
        assertTrue(
            runCatching {
                buildSearxngSearchUrl("http://search.example.test", "test")
            }.isFailure,
        )
        assertTrue(
            runCatching {
                buildSearxngSearchUrl("https://user:secret@search.example.test", "test")
            }.isFailure,
        )
    }

    @Test
    fun parserLimitsResultsAndSkipsInvalidUrls() {
        val payload = """
            {
              "results": [
                {"title":"One","url":"https://one.example","content":"First result"},
                {"title":"Two","url":"not-a-url","content":"Skip this"},
                {"title":"Three","url":"https://three.example","content":"Third result"}
              ]
            }
        """.trimIndent()

        val results = parseSearxngResults(payload, maxResults = 2)

        assertEquals(1, results.size)
        assertEquals("One", results.single().title)
        assertTrue(formatSearxngResults(results).contains("Untrusted web search results"))
    }

    @Test
    fun executorReturnsBoundedReferenceData() = runTest {
        val executor = SearxngSearchExecutor(
            endpointProvider = { "https://search.example.test" },
            transport = SearxngTransport {
                """{"results":[{"title":"One","url":"https://one.example","content":"Snippet"}]}"""
            },
        )

        val result = executor.execute(validCall())

        assertEquals(ToolExecutionStatus.SUCCEEDED, result.status)
        assertEquals("Web search completed in Sancho.", result.displayMessage)
        assertTrue(result.untrusted)
        assertTrue(result.output.contains("https://one.example"))
        assertEquals("One", result.sources.single().title)
    }

    @Test
    fun missingEndpointDoesNotCallTransport() = runTest {
        var calls = 0
        val executor = SearxngSearchExecutor(
            endpointProvider = { "" },
            transport = SearxngTransport {
                calls += 1
                "{}"
            },
        )

        val result = executor.execute(validCall())

        assertEquals(ToolExecutionStatus.FAILED, result.status)
        assertEquals("searxng_endpoint_missing", result.errorCode)
        assertEquals(0, calls)
    }

    private fun validCall(): ValidatedToolCall =
        ToolCatalog.browserHandoff.validate(
            RawToolCall("call-1", "open_web_search", mapOf("query" to "latest news")),
        ).let { (it as ToolCallValidation.Accepted).call.approve() }
}
