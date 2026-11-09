package com.unplugged.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolContractTest {
    private val catalog = ToolCatalog.browserHandoff

    @Test
    fun validSearchCallRequiresConfirmation() {
        val validation = catalog.validate(
            RawToolCall(
                id = "call-1",
                name = "open_web_search",
                arguments = mapOf("query" to "GrapheneOS latest release"),
            ),
        )

        val accepted = validation as ToolCallValidation.Accepted
        assertEquals(ToolRisk.NETWORK_READ, accepted.call.spec.risk)
        assertEquals(ConfirmationState.PENDING, accepted.call.confirmationState)
        assertEquals("GrapheneOS latest release", (accepted.call.arguments as OpenWebSearchArguments).query)
    }

    @Test
    fun unknownToolIsRejected() {
        val validation = catalog.validate(
            RawToolCall("call-1", "run_shell", emptyMap()),
        )

        assertEquals(
            RejectionCode.UNKNOWN_TOOL,
            (validation as ToolCallValidation.Rejected).code,
        )
    }

    @Test
    fun missingAndExtraArgumentsAreRejected() {
        val missing = catalog.validate(RawToolCall("call-1", "open_web_search", emptyMap()))
        val extra = catalog.validate(
            RawToolCall(
                "call-2",
                "open_web_search",
                mapOf("query" to "hello", "url" to "https://example.com"),
            ),
        )

        assertEquals(RejectionCode.MISSING_ARGUMENT, (missing as ToolCallValidation.Rejected).code)
        assertEquals(RejectionCode.EXTRA_ARGUMENT, (extra as ToolCallValidation.Rejected).code)
    }

    @Test
    fun invalidSearchArgumentsAreRejected() {
        val blank = catalog.validate(
            RawToolCall("call-1", "open_web_search", mapOf("query" to "  ")),
        )
        val tooLong = catalog.validate(
            RawToolCall(
                "call-2",
                "open_web_search",
                mapOf("query" to "x".repeat(OpenWebSearchArguments.MAX_QUERY_CHARACTERS + 1)),
            ),
        )

        assertEquals(RejectionCode.INVALID_ARGUMENT, (blank as ToolCallValidation.Rejected).code)
        assertEquals(RejectionCode.INVALID_ARGUMENT, (tooLong as ToolCallValidation.Rejected).code)
    }

    @Test
    fun confirmationCanBeApprovedOrDeniedOnlyFromPending() {
        val accepted = catalog.validate(
            RawToolCall("call-1", "open_web_search", mapOf("query" to "latest news")),
        ) as ToolCallValidation.Accepted

        assertEquals(ConfirmationState.APPROVED, accepted.call.approve().confirmationState)
        assertEquals(ConfirmationState.DENIED, accepted.call.deny().confirmationState)
        assertTrue(
            runCatching { accepted.call.approve().approve() }.isFailure,
        )
    }
}
