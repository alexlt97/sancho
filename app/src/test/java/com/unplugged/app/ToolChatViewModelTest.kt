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
            val viewModel = ChatViewModel(
                localLlm = localLlm,
                initialModelReady = true,
                toolExecutionCoordinator = ToolExecutionCoordinator(
                    ToolCatalog.browserHandoff,
                    mapOf(ToolName.OPEN_WEB_SEARCH to executor),
                ),
            )

            viewModel.updateDraft("What is current?")
            viewModel.send()
            runCurrent()

            assertNotNull(viewModel.uiState.value.pendingToolCall)
            assertEquals(0, executor.callCount)

            viewModel.approvePendingTool()
            advanceUntilIdle()

            assertEquals(1, executor.callCount)
            assertTrue(viewModel.uiState.value.messages.last().text.contains("Final local answer"))
            assertEquals(1, localLlm.submittedResults.size)
        } finally {
            kotlinx.coroutines.Dispatchers.resetMain()
        }
    }
}

private class ToolRequestingFakeLlm : LocalLlm {
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
        emit(LlmEvent.TextDelta("Final local answer"))
    }

    override fun close() = Unit
}
