package com.unplugged.app

import android.content.Context
import android.content.Intent
import android.net.Uri

private const val VANADIUM_PACKAGE = "app.vanadium.browser"

/** Opens only a Sancho-built Brave Search URL in the explicit Vanadium browser. */
class BrowserSearchExecutor(
    context: Context,
) : ToolExecutor {
    private val applicationContext = context.applicationContext

    override suspend fun execute(call: ValidatedToolCall): ToolExecutionResult {
        val arguments = call.arguments as? OpenWebSearchArguments
            ?: return ToolExecutionResult(
                callId = call.id,
                tool = call.spec.name,
                status = ToolExecutionStatus.FAILED,
                output = "The search query could not be read.",
                errorCode = "invalid_search_arguments",
            )
        val searchUri = Uri.parse(
            "https://search.brave.com/search?q=${Uri.encode(arguments.query)}",
        )
        val intent = Intent(Intent.ACTION_VIEW, searchUri).apply {
            setPackage(VANADIUM_PACKAGE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (intent.resolveActivity(applicationContext.packageManager) == null) {
            return ToolExecutionResult(
                callId = call.id,
                tool = call.spec.name,
                status = ToolExecutionStatus.FAILED,
                output = "Vanadium is not available in this profile.",
                errorCode = "vanadium_unavailable",
            )
        }
        return try {
            applicationContext.startActivity(intent)
            ToolExecutionResult.success(call, "Search opened in Vanadium.").copy(
                displayMessage = "Search opened in Vanadium.",
            )
        } catch (_: RuntimeException) {
            ToolExecutionResult(
                callId = call.id,
                tool = call.spec.name,
                status = ToolExecutionStatus.FAILED,
                output = "Vanadium could not open the search.",
                errorCode = "vanadium_launch_failed",
            )
        }
    }
}
