package com.unplugged.app

/** Names are deliberately application-owned; model output is never executed by name alone. */
enum class ToolName(val wireName: String) {
    OPEN_WEB_SEARCH("open_web_search"),
}

enum class ToolRisk {
    NETWORK_READ,
    LOCAL_MUTATION,
    DEVICE_ACTION,
}

enum class ConfirmationPolicy {
    NEVER,
    ALWAYS,
}

enum class ConfirmationState {
    NOT_REQUIRED,
    PENDING,
    APPROVED,
    DENIED,
}

data class ToolSpec(
    val name: ToolName,
    val description: String,
    val risk: ToolRisk,
    val confirmationPolicy: ConfirmationPolicy,
    val timeoutMillis: Long,
    val maxOutputCharacters: Int,
)

data class RawToolCall(
    val id: String,
    val name: String,
    val arguments: Map<String, String>,
) {
    init {
        require(id.isNotBlank()) { "Tool call id must not be blank." }
        require(name.isNotBlank()) { "Tool name must not be blank." }
    }
}

sealed interface ToolArguments

data class OpenWebSearchArguments(
    val query: String,
) : ToolArguments {
    init {
        require(query.isNotBlank()) { "Search query must not be blank." }
        require(query.length <= MAX_QUERY_CHARACTERS) {
            "Search query exceeds $MAX_QUERY_CHARACTERS characters."
        }
        require(query.none(Char::isISOControl)) { "Search query contains a control character." }
    }

    companion object {
        const val MAX_QUERY_CHARACTERS = 600
    }
}

data class ValidatedToolCall(
    val id: String,
    val spec: ToolSpec,
    val arguments: ToolArguments,
    val confirmationState: ConfirmationState,
) {
    fun approve(): ValidatedToolCall {
        check(confirmationState == ConfirmationState.PENDING) {
            "Only a pending tool call can be approved."
        }
        return copy(confirmationState = ConfirmationState.APPROVED)
    }

    fun deny(): ValidatedToolCall {
        check(confirmationState == ConfirmationState.PENDING) {
            "Only a pending tool call can be denied."
        }
        return copy(confirmationState = ConfirmationState.DENIED)
    }
}

sealed interface ToolCallValidation {
    data class Accepted(val call: ValidatedToolCall) : ToolCallValidation

    data class Rejected(
        val code: RejectionCode,
        val message: String,
    ) : ToolCallValidation
}

enum class RejectionCode {
    UNKNOWN_TOOL,
    MISSING_ARGUMENT,
    EXTRA_ARGUMENT,
    INVALID_ARGUMENT,
}

class ToolCatalog(
    specs: List<ToolSpec>,
) {
    private val specsByName = specs.associateBy { it.name }

    init {
        require(specsByName.size == specs.size) { "Tool names must be unique." }
        specs.forEach { spec ->
            require(spec.timeoutMillis > 0) { "Tool timeout must be positive." }
            require(spec.maxOutputCharacters > 0) { "Tool output limit must be positive." }
        }
    }

    fun spec(name: ToolName): ToolSpec? = specsByName[name]

    fun validate(raw: RawToolCall): ToolCallValidation {
        val name = ToolName.entries.firstOrNull { it.wireName == raw.name }
            ?: return ToolCallValidation.Rejected(
                RejectionCode.UNKNOWN_TOOL,
                "Unknown tool: ${raw.name}",
            )
        val spec = specsByName[name]
            ?: return ToolCallValidation.Rejected(
                RejectionCode.UNKNOWN_TOOL,
                "Tool is not enabled: ${raw.name}",
            )

        val expectedArguments = when (name) {
            ToolName.OPEN_WEB_SEARCH -> setOf("query")
        }
        val actualArguments = raw.arguments.keys
        val missing = expectedArguments - actualArguments
        if (missing.isNotEmpty()) {
            return ToolCallValidation.Rejected(
                RejectionCode.MISSING_ARGUMENT,
                "Missing argument: ${missing.first()}",
            )
        }
        val extra = actualArguments - expectedArguments
        if (extra.isNotEmpty()) {
            return ToolCallValidation.Rejected(
                RejectionCode.EXTRA_ARGUMENT,
                "Unexpected argument: ${extra.first()}",
            )
        }

        val arguments = try {
            when (name) {
                ToolName.OPEN_WEB_SEARCH -> OpenWebSearchArguments(
                    raw.arguments.getValue("query").trim(),
                )
            }
        } catch (error: IllegalArgumentException) {
            return ToolCallValidation.Rejected(
                RejectionCode.INVALID_ARGUMENT,
                error.message ?: "Invalid tool argument.",
            )
        }
        val confirmationState = when (spec.confirmationPolicy) {
            ConfirmationPolicy.NEVER -> ConfirmationState.NOT_REQUIRED
            ConfirmationPolicy.ALWAYS -> ConfirmationState.PENDING
        }
        return ToolCallValidation.Accepted(
            ValidatedToolCall(
                id = raw.id,
                spec = spec,
                arguments = arguments,
                confirmationState = confirmationState,
            ),
        )
    }

    companion object {
        val browserHandoff = ToolCatalog(
            listOf(
                ToolSpec(
                    name = ToolName.OPEN_WEB_SEARCH,
                    description = "Open a user-approved web search in Vanadium.",
                    risk = ToolRisk.NETWORK_READ,
                    confirmationPolicy = ConfirmationPolicy.ALWAYS,
                    timeoutMillis = 10_000L,
                    maxOutputCharacters = 20_000,
                ),
            ),
        )
    }
}

enum class ToolExecutionStatus {
    SUCCEEDED,
    REJECTED,
    FAILED,
    TIMED_OUT,
}

data class WebSource(
    val title: String,
    val url: String,
)

data class WebSearchMetrics(
    val provider: String?,
    val status: ToolExecutionStatus,
    val elapsedMillis: Long,
    val resultCount: Int,
)

data class ToolExecutionResult(
    val callId: String,
    val tool: ToolName,
    val status: ToolExecutionStatus,
    val output: String,
    val untrusted: Boolean = true,
    val truncated: Boolean = false,
    val elapsedMillis: Long = 0L,
    val errorCode: String? = null,
    val displayMessage: String? = null,
    val sources: List<WebSource> = emptyList(),
    val provider: String? = null,
) {
    companion object {
        fun success(call: ValidatedToolCall, output: String): ToolExecutionResult =
            ToolExecutionResult(
                callId = call.id,
                tool = call.spec.name,
                status = ToolExecutionStatus.SUCCEEDED,
                output = output,
            )
    }

    fun boundedTo(spec: ToolSpec): ToolExecutionResult {
        val boundedOutput = output.take(spec.maxOutputCharacters)
        return copy(
            tool = spec.name,
            output = boundedOutput,
            truncated = output.length > boundedOutput.length,
        )
    }
}

fun interface ToolExecutor {
    suspend fun execute(call: ValidatedToolCall): ToolExecutionResult
}
