package com.unplugged.app

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow

sealed interface ToolConversationEvent {
    data class TextDelta(val text: String) : ToolConversationEvent

    data class ConfirmationRequested(val call: ValidatedToolCall) : ToolConversationEvent

    data class ToolExecuted(val result: ToolExecutionResult) : ToolConversationEvent

    data class ToolRejected(
        val name: String,
        val message: String,
    ) : ToolConversationEvent
}

data class ToolConversationPolicy(
    val maxToolCallsPerRun: Int = 1,
)

fun interface ToolPolicyProvider {
    fun current(): ToolConversationPolicy
}

sealed interface ToolConfirmationDecision {
    data class Approved(val call: ValidatedToolCall) : ToolConfirmationDecision

    data object Denied : ToolConfirmationDecision
}

/**
 * Bounded manual tool loop. The caller supplies confirmation, so a model call
 * can never silently become an external action.
 */
class ToolConversationRunner(
    private val localLlm: LocalLlm,
    private val catalog: ToolCatalog,
    private val executor: ToolExecutionCoordinator,
    private val confirm: suspend (ValidatedToolCall) -> ToolConfirmationDecision,
    private val maxToolTurns: Int = 1,
    private val maxToolCallsPerRun: Int = 1,
) {
    init {
        require(maxToolTurns > 0) { "The tool-turn limit must be positive." }
        require(maxToolCallsPerRun > 0) { "The tool-call limit must be positive." }
    }

    fun run(request: LlmRequest): Flow<ToolConversationEvent> = flow {
        var nextEvents = localLlm.events(request)
        var completedToolTurns = 0

        while (true) {
            val toolResults = mutableListOf<LlmToolResult>()
            var sawToolCall = false
            nextEvents.collect { event ->
                when (event) {
                    is LlmEvent.TextDelta -> emit(ToolConversationEvent.TextDelta(event.text))
                    is LlmEvent.ToolCallRequested -> {
                        sawToolCall = true
                        if (completedToolTurns >= maxToolTurns || toolResults.size >= maxToolCallsPerRun) {
                            emit(
                                ToolConversationEvent.ToolRejected(
                                    name = event.name,
                                    message = if (completedToolTurns >= maxToolTurns) {
                                        "Tool-turn limit reached."
                                    } else {
                                        "Tool-call limit reached."
                                    },
                                ),
                            )
                            return@collect
                        }

                        val rawCall = event.toRawToolCall()
                        if (rawCall == null) {
                            emit(
                                ToolConversationEvent.ToolRejected(
                                    name = event.name,
                                    message = "Tool arguments must be strings.",
                                ),
                            )
                            return@collect
                        }
                        when (val validation = catalog.validate(rawCall)) {
                            is ToolCallValidation.Rejected -> emit(
                                ToolConversationEvent.ToolRejected(
                                    name = event.name,
                                    message = validation.message,
                                ),
                            )

                            is ToolCallValidation.Accepted -> {
                                val call = validation.call
                                val decision = when (call.confirmationState) {
                                    ConfirmationState.NOT_REQUIRED ->
                                        ToolConfirmationDecision.Approved(call)
                                    ConfirmationState.PENDING -> {
                                        emit(ToolConversationEvent.ConfirmationRequested(call))
                                        confirm(call)
                                    }

                                    ConfirmationState.APPROVED ->
                                        ToolConfirmationDecision.Approved(call)
                                    ConfirmationState.DENIED -> ToolConfirmationDecision.Denied
                                }
                                val executableCall = when (decision) {
                                    is ToolConfirmationDecision.Approved -> decision.call
                                    ToolConfirmationDecision.Denied -> call.deny()
                                }
                                val result = executor.execute(executableCall)
                                emit(ToolConversationEvent.ToolExecuted(result))
                                toolResults += LlmToolResult(
                                    name = call.spec.name.wireName,
                                    response = result.output,
                                )
                            }
                        }
                    }
                }
            }

            if (!sawToolCall || toolResults.isEmpty()) break
            completedToolTurns += 1
            nextEvents = localLlm.submitToolResults(toolResults)
        }
    }
}

private fun LlmEvent.ToolCallRequested.toRawToolCall(): RawToolCall? {
    val stringArguments = mutableMapOf<String, String>()
    arguments.forEach { (key, value) ->
        if (value !is String) return null
        stringArguments[key] = value
    }
    return RawToolCall(
        id = callId,
        name = name,
        arguments = stringArguments,
    )
}
