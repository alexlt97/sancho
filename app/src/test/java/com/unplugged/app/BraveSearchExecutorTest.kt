package com.unplugged.app

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BraveSearchExecutorTest {
    @Test
    fun searchUrlUsesBraveWebEndpointWithoutApiKey() {
        val url = buildBraveSearchUrl("GrapheneOS latest release")

        assertEquals(
            "https://api.search.brave.com/res/v1/llm/context?q=GrapheneOS+latest+release",
            url,
        )
        assertTrue(!url.contains("Subscription-Token"))
    }

    @Test
    fun parserReadsBoundedWebResults() {
        val payload = """
            {
              "grounding": {
                "generic": [
                  {"title":"One","url":"https://one.example","snippets":["First result"]},
                  {"title":"Two","url":"not-a-url","snippets":["Skip this"]},
                  {"title":"Three","url":"https://three.example","snippets":["Third result"]}
                ]
              }
            }
        """.trimIndent()

        val results = parseBraveSearchResults(payload, maxResults = 2)

        assertEquals(1, results.size)
        assertEquals("One", results.single().title)
        assertTrue(formatBraveSearchResults(results).contains("Untrusted web search results"))
    }

    @Test
    fun executorSendsKeyOnlyThroughTransportAndReturnsReferenceData() = runTest {
        var requestedUrl = ""
        var requestedKey = ""
        val executor = BraveSearchExecutor(
            apiKeyProvider = { "brave-secret" },
            transport = BraveSearchTransport { url, apiKey ->
                requestedUrl = url
                requestedKey = apiKey
                """{"grounding":{"generic":[{"title":"One","url":"https://one.example","snippets":["Snippet"]}]}}"""
            },
        )

        val result = executor.execute(validCall())

        assertEquals(ToolExecutionStatus.SUCCEEDED, result.status)
        assertEquals("brave-secret", requestedKey)
        assertTrue(requestedUrl.contains("api.search.brave.com"))
        assertTrue(result.untrusted)
        assertTrue(result.output.contains("https://one.example"))
        assertEquals("One", result.sources.single().title)
    }

    @Test
    fun missingApiKeyDoesNotCallTransport() = runTest {
        var calls = 0
        val executor = BraveSearchExecutor(
            apiKeyProvider = { "" },
            transport = BraveSearchTransport { _, _ ->
                calls += 1
                "{}"
            },
        )

        val result = executor.execute(validCall())

        assertEquals(ToolExecutionStatus.FAILED, result.status)
        assertEquals("brave_api_key_missing", result.errorCode)
        assertEquals(0, calls)
    }

    @Test
    fun apiKeyTestUsesFixedQueryAndReportsResultCount() = runTest {
        var requestedUrl = ""
        var requestedKey = ""

        val result = testBraveApiKey(
            apiKey = " brave-secret ",
            transport = BraveSearchTransport { url, apiKey ->
                requestedUrl = url
                requestedKey = apiKey
                """{"grounding":{"generic":[{"title":"One","url":"https://one.example","snippets":["Snippet"]}]}}"""
            },
        )

        assertTrue(result is BraveApiKeyTestResult.Success)
        assertEquals("brave-secret", requestedKey)
        assertTrue(requestedUrl.contains("Sancho+API+connection+test"))
        assertEquals(1, (result as BraveApiKeyTestResult.Success).resultCount)
    }

    @Test
    fun apiKeyTestDoesNotCallTransportWithoutKey() = runTest {
        var calls = 0

        val result = testBraveApiKey(
            apiKey = "",
            transport = BraveSearchTransport { _, _ ->
                calls += 1
                "{}"
            },
        )

        assertTrue(result is BraveApiKeyTestResult.Failure)
        assertEquals(0, calls)
    }

    @Test
    fun apiKeyTestReportsUnauthorizedStatusWithoutExposingKey() = runTest {
        val result = testBraveApiKey(
            apiKey = "brave-secret",
            transport = BraveSearchTransport { _, _ ->
                throw BraveSearchHttpException(401)
            },
        )

        assertEquals(
            "Brave rejected the API key (HTTP 401). Check that it is copied correctly.",
            (result as BraveApiKeyTestResult.Failure).message,
        )
        assertTrue(!result.message.contains("brave-secret"))
    }

    @Test
    fun httpExceptionKeepsOnlySafeStatusAndDetail() {
        val error = BraveSearchHttpException(422, "VALIDATION — invalid request")

        assertEquals("Brave Search returned HTTP 422: VALIDATION — invalid request.", error.message)
        assertTrue(!error.message.orEmpty().contains("brave-secret"))
    }

    private fun validCall(): ValidatedToolCall =
        ToolCatalog.browserHandoff.validate(
            RawToolCall("call-1", "open_web_search", mapOf("query" to "latest news")),
        ).let { (it as ToolCallValidation.Accepted).call.approve() }
}
