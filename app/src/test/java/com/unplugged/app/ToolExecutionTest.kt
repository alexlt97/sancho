package com.unplugged.app

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ToolExecutionTest {
    private val catalog = ToolCatalog.browserHandoff

    @Test
    fun unapprovedCallCannotExecute() = runTest {
        val executor = MockToolExecutor()
        val coordinator = ToolExecutionCoordinator(
            catalog = catalog,
            executors = mapOf(ToolName.OPEN_WEB_SEARCH to executor),
        )
        val call = validCall()

        val result = coordinator.execute(call)

        assertEquals(ToolExecutionStatus.REJECTED, result.status)
        assertEquals(0, executor.callCount)
    }

    @Test
    fun approvedCallExecutesAndBoundsOutput() = runTest {
        val response = "result ".repeat(4_000)
        val executor = MockToolExecutor(response = response)
        val coordinator = ToolExecutionCoordinator(
            catalog = catalog,
            executors = mapOf(ToolName.OPEN_WEB_SEARCH to executor),
        )
        val result = coordinator.execute(validCall().approve())

        assertEquals(ToolExecutionStatus.SUCCEEDED, result.status)
        assertEquals(20_000, result.output.length)
        assertTrue(result.untrusted)
        assertTrue(result.truncated)
        assertEquals(1, executor.callCount)
    }

    @Test
    fun timeoutProducesBoundedFailure() = runTest {
        val executor = MockToolExecutor(delayMillis = 100)
        val shortTimeoutCatalog = ToolCatalog(
            listOf(catalog.spec(ToolName.OPEN_WEB_SEARCH)!!.copy(timeoutMillis = 10)),
        )
        val coordinator = ToolExecutionCoordinator(
            catalog = shortTimeoutCatalog,
            executors = mapOf(ToolName.OPEN_WEB_SEARCH to executor),
        )

        val result = coordinator.execute(
            shortTimeoutCatalog.validate(
                RawToolCall("call-1", "open_web_search", mapOf("query" to "timeout test")),
            ).accepted().approve(),
        )

        assertEquals(ToolExecutionStatus.TIMED_OUT, result.status)
        assertEquals("timeout", result.errorCode)
    }

    @Test
    fun callerCancellationReachesExecutor() = runTest {
        val executor = MockToolExecutor(delayMillis = 1_000)
        val coordinator = ToolExecutionCoordinator(
            catalog = catalog,
            executors = mapOf(ToolName.OPEN_WEB_SEARCH to executor),
        )
        val deferred = async { coordinator.execute(validCall().approve()) }

        runCurrent()
        deferred.cancelAndJoin()

        assertEquals(1, executor.callCount)
    }

    private fun validCall(): ValidatedToolCall =
        catalog.validate(
            RawToolCall("call-1", "open_web_search", mapOf("query" to "latest GrapheneOS news")),
        ).accepted()
}

private fun ToolCallValidation.accepted(): ValidatedToolCall =
    (this as ToolCallValidation.Accepted).call
