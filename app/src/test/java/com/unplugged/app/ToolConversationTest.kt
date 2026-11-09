package com.unplugged.app

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ToolConversationTest {
    @Test
    fun approvedToolCallExecutesAndResumesTheConversation() = runTest {
        val llm = ScriptedToolLlm(
            firstEvents = listOf(
                LlmEvent.ToolCallRequested(
                    callId = "call-1",
                    name = "open_web_search",
                    arguments = mapOf("query" to "latest GrapheneOS release"),
                ),
            ),
            resumedEvents = listOf(LlmEvent.TextDelta("Search is ready.")),
        )
        val mockExecutor = MockToolExecutor(response = "Mock search result")
        val runner = ToolConversationRunner(
            localLlm = llm,
            catalog = ToolCatalog.browserHandoff,
            executor = ToolExecutionCoordinator(
                ToolCatalog.browserHandoff,
                mapOf(ToolName.OPEN_WEB_SEARCH to mockExecutor),
            ),
            confirm = { call -> ToolConfirmationDecision.Approved(call.approve()) },
        )

        val events = runner.run(LlmRequest("What is current?" )).toList()

        assertTrue(events.any { it is ToolConversationEvent.ConfirmationRequested })
        assertTrue(events.any { it is ToolConversationEvent.ToolExecuted })
        assertTrue(events.contains(ToolConversationEvent.TextDelta("Search is ready.")))
        assertEquals(1, mockExecutor.callCount)
        assertEquals(listOf("open_web_search"), llm.submittedResults.map { it.name })
    }

    @Test
    fun deniedToolCallDoesNotExecuteButLetsTheModelRespond() = runTest {
        val llm = ScriptedToolLlm(
            firstEvents = listOf(
                LlmEvent.ToolCallRequested(
                    callId = "call-1",
                    name = "open_web_search",
                    arguments = mapOf("query" to "private question"),
                ),
            ),
            resumedEvents = listOf(LlmEvent.TextDelta("I will answer locally.")),
        )
        val mockExecutor = MockToolExecutor()
        val runner = ToolConversationRunner(
            localLlm = llm,
            catalog = ToolCatalog.browserHandoff,
            executor = ToolExecutionCoordinator(
                ToolCatalog.browserHandoff,
                mapOf(ToolName.OPEN_WEB_SEARCH to mockExecutor),
            ),
            confirm = { ToolConfirmationDecision.Denied },
        )

        val events = runner.run(LlmRequest("Search this" )).toList()

        assertEquals(0, mockExecutor.callCount)
        assertTrue(events.contains(ToolConversationEvent.TextDelta("I will answer locally.")))
        assertEquals("Tool confirmation was not approved.", (events[1] as ToolConversationEvent.ToolExecuted).result.output)
    }

    @Test
    fun toolTurnsAreCapped() = runTest {
        val llm = ScriptedToolLlm(
            firstEvents = listOf(
                LlmEvent.ToolCallRequested("call-1", "open_web_search", mapOf("query" to "first")),
            ),
            resumedEvents = listOf(
                LlmEvent.ToolCallRequested("call-2", "open_web_search", mapOf("query" to "second")),
            ),
        )
        val mockExecutor = MockToolExecutor()
        val runner = ToolConversationRunner(
            localLlm = llm,
            catalog = ToolCatalog.browserHandoff,
            executor = ToolExecutionCoordinator(
                ToolCatalog.browserHandoff,
                mapOf(ToolName.OPEN_WEB_SEARCH to mockExecutor),
            ),
            confirm = { call -> ToolConfirmationDecision.Approved(call.approve()) },
            maxToolTurns = 1,
        )

        val events = runner.run(LlmRequest("Search twice" )).toList()

        assertEquals(1, mockExecutor.callCount)
        assertTrue(events.any { it is ToolConversationEvent.ToolRejected })
    }
}

private class ScriptedToolLlm(
    private val firstEvents: List<LlmEvent>,
    private val resumedEvents: List<LlmEvent>,
) : LocalLlm {
    var submittedResults: List<LlmToolResult> = emptyList()
        private set

    override suspend fun load(modelFile: File) = Unit

    override fun stream(request: LlmRequest): Flow<String> = flowOfText()

    override fun events(request: LlmRequest): Flow<LlmEvent> = flow {
        firstEvents.forEach { emit(it) }
    }

    override fun submitToolResults(results: List<LlmToolResult>): Flow<LlmEvent> = flow {
        submittedResults = results
        resumedEvents.forEach { emit(it) }
    }

    override fun close() = Unit
}

private fun flowOfText(): Flow<String> = flow { emit("unused") }
