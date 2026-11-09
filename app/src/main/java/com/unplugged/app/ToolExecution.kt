package com.unplugged.app

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

class ToolExecutionCoordinator(
    private val catalog: ToolCatalog,
    executors: Map<ToolName, ToolExecutor>,
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
) {
    private val executorsByName = executors.toMap()

    suspend fun execute(call: ValidatedToolCall): ToolExecutionResult {
        if (call.confirmationState != ConfirmationState.NOT_REQUIRED &&
            call.confirmationState != ConfirmationState.APPROVED
        ) {
            return ToolExecutionResult(
                callId = call.id,
                tool = call.spec.name,
                status = ToolExecutionStatus.REJECTED,
                output = "Tool confirmation was not approved.",
                errorCode = "confirmation_required",
            )
        }

        val executor = executorsByName[call.spec.name]
            ?: return ToolExecutionResult(
                callId = call.id,
                tool = call.spec.name,
                status = ToolExecutionStatus.REJECTED,
                output = "No executor is registered for this tool.",
                errorCode = "executor_missing",
            )
        val spec = catalog.spec(call.spec.name) ?: call.spec
        val startedAt = nowMillis()
        return try {
            withTimeout(spec.timeoutMillis) {
                executor.execute(call)
            }.boundedTo(spec).copy(elapsedMillis = elapsedSince(startedAt))
        } catch (_: TimeoutCancellationException) {
            ToolExecutionResult(
                callId = call.id,
                tool = call.spec.name,
                status = ToolExecutionStatus.TIMED_OUT,
                output = "Tool execution timed out.",
                elapsedMillis = elapsedSince(startedAt),
                errorCode = "timeout",
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            ToolExecutionResult(
                callId = call.id,
                tool = call.spec.name,
                status = ToolExecutionStatus.FAILED,
                output = "Tool execution failed.",
                elapsedMillis = elapsedSince(startedAt),
                errorCode = error::class.simpleName ?: "tool_failure",
            )
        }
    }

    private fun elapsedSince(startedAt: Long): Long =
        (nowMillis() - startedAt).coerceAtLeast(0L)
}

class MockToolExecutor(
    private val response: String = "Mock search completed.",
    private val delayMillis: Long = 0L,
    private val failure: Throwable? = null,
) : ToolExecutor {
    var callCount: Int = 0
        private set

    override suspend fun execute(call: ValidatedToolCall): ToolExecutionResult {
        callCount += 1
        if (delayMillis > 0) kotlinx.coroutines.delay(delayMillis)
        failure?.let { throw it }
        return ToolExecutionResult.success(call, response)
    }
}
