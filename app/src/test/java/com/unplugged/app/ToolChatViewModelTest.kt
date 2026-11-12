package com.unplugged.app

import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ToolChatViewModelTest {
    @Test
    fun chatWaitsForSearchApprovalBeforeExecuting() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        kotlinx.coroutines.Dispatchers.setMain(dispatcher)
        try {
            val localLlm = ToolRequestingFakeLlm()
            val executor = MockToolExecutor(response = "Search was opened.")
            val recordedMetrics = mutableListOf<ToolExecutionMetric>()
            val viewModel = ChatViewModel(
                localLlm = localLlm,
                initialModelReady = true,
                toolExecutionCoordinator = ToolExecutionCoordinator(
                    ToolCatalog.browserHandoff,
                    mapOf(ToolName.OPEN_WEB_SEARCH to executor),
                ),
                toolMetricsRecorder = ToolMetricsRecorder(recordedMetrics::add),
                toolMetricsEnabledProvider = { true },
            )

            viewModel.updateDraft("What is current?")
            viewModel.send()
            runCurrent()

            assertNotNull(viewModel.uiState.value.pendingToolCall)
            assertEquals(WebSearchPhase.WAITING_FOR_APPROVAL, viewModel.uiState.value.webSearchPhase)
            assertEquals(0, executor.callCount)

            viewModel.approvePendingTool()
            assertEquals(null, viewModel.uiState.value.pendingToolCall)
            assertEquals(WebSearchPhase.SEARCHING, viewModel.uiState.value.webSearchPhase)
            advanceUntilIdle()

            assertEquals(1, executor.callCount)
            assertTrue(viewModel.uiState.value.messages.last().text.contains("Final local answer"))
            assertEquals(WebSearchPhase.RESULTS_RECEIVED, viewModel.uiState.value.webSearchPhase)
            assertEquals(1, localLlm.submittedResults.size)
            assertEquals(1, recordedMetrics.size)
            assertEquals(ToolExecutionStatus.SUCCEEDED, recordedMetrics.single().status)
            assertTrue(viewModel.uiState.value.messages.last().webSearchMetrics != null)
        } finally {
            kotlinx.coroutines.Dispatchers.resetMain()
        }
    }

    @Test
    fun emptyPostSearchModelResponseIsShownAsAVisibleFallback() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        kotlinx.coroutines.Dispatchers.setMain(dispatcher)
        try {
            val viewModel = ChatViewModel(
                localLlm = ToolRequestingFakeLlm(finalAnswer = null),
                initialModelReady = true,
                toolExecutionCoordinator = ToolExecutionCoordinator(
                    ToolCatalog.browserHandoff,
                    mapOf(ToolName.OPEN_WEB_SEARCH to MockToolExecutor()),
                ),
            )

            viewModel.updateDraft("Find this online")
            viewModel.send()
            runCurrent()
            viewModel.approvePendingTool()
            advanceUntilIdle()

            val response = viewModel.uiState.value.messages.last()
            assertEquals("Assistant", response.author)
            assertTrue(response.text.contains("did not return a summary"))
        } finally {
            kotlinx.coroutines.Dispatchers.resetMain()
        }
    }
}

private class ToolRequestingFakeLlm(
    private val finalAnswer: String? = "Final local answer",
) : LocalLlm {
    var submittedResults: List<LlmToolResult> = emptyList()
        private set

    override suspend fun load(modelFile: File) = Unit

    override fun stream(request: LlmRequest): Flow<String> = flow { emit("unused") }

    override fun events(request: LlmRequest): Flow<LlmEvent> = flow {
        emit(
            LlmEvent.ToolCallRequested(
                callId = "call-1",
                name = "open_web_search",
                arguments = mapOf("query" to "current information"),
            ),
        )
    }

    override fun submitToolResults(results: List<LlmToolResult>): Flow<LlmEvent> = flow {
        submittedResults = results
        finalAnswer?.let { emit(LlmEvent.TextDelta(it)) }
    }

    override fun close() = Unit
}
