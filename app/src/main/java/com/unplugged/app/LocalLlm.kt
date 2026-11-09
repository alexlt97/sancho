package com.unplugged.app

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.tool
import java.io.File
import java.util.concurrent.Executors
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.asCoroutineDispatcher

data class LlmRequest(
    val prompt: String,
    val imageFile: File? = null,
)

sealed interface LlmEvent {
    data class TextDelta(val text: String) : LlmEvent

    data class ToolCallRequested(
        val callId: String,
        val name: String,
        val arguments: Map<String, Any?>,
    ) : LlmEvent
}

data class LlmToolResult(
    val name: String,
    val response: String,
)

class ToolCallRequiredException(val call: LlmEvent.ToolCallRequested) :
    IllegalStateException("A tool call requires a tool-aware local LLM consumer.")

interface LocalLlm {
    suspend fun load(modelFile: File)

    fun stream(request: LlmRequest): Flow<String>

    fun events(request: LlmRequest): Flow<LlmEvent> =
        stream(request).map(LlmEvent::TextDelta)

    fun submitToolResults(results: List<LlmToolResult>): Flow<LlmEvent> = flow {
        error("This local model conversation does not have tools enabled.")
    }

    fun submitToolResult(result: LlmToolResult): Flow<LlmEvent> =
        submitToolResults(listOf(result))

    fun stream(prompt: String): Flow<String> = stream(LlmRequest(prompt))

    fun close()
}

class LiteRtLocalLlm(
    private val cacheDirectory: File,
    private val backend: Backend = Backend.CPU(),
    private val harness: ModelHarness? = ModelHarnessFactory.defaultChatVision(),
    private val toolCatalog: ToolCatalog? = null,
) : LocalLlm {
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "sancho-litert-lm")
    }
    private val dispatcher: ExecutorCoroutineDispatcher = executor.asCoroutineDispatcher()

    private var engine: Engine? = null
    private var conversation: com.google.ai.edge.litertlm.Conversation? = null
    private var toolCallSequence = 0L

    override suspend fun load(modelFile: File) = withContext(dispatcher) {
        require(modelFile.isFile) { "Model file does not exist." }
        require(modelFile.length() > 0L) { "Model file is empty." }

        closeResourcesOnExecutor()

        val candidate = Engine(
            EngineConfig(
                modelPath = modelFile.absolutePath,
                backend = backend,
                visionBackend = backend,
                maxNumImages = 1,
                cacheDir = cacheDirectory.absolutePath,
            ),
        )

        try {
            candidate.initialize()
            val conversationConfig = ConversationConfig(
                systemInstruction = harness?.let { Contents.of(it.systemInstruction) }
                    ?: Contents.of("You are Sancho, a helpful local assistant."),
                tools = if (toolCatalog != null) {
                    listOf(tool(OpenWebSearchTool()))
                } else {
                    emptyList()
                },
                automaticToolCalling = toolCatalog == null,
            )
            val candidateConversation = candidate.createConversation(conversationConfig)
            engine = candidate
            conversation = candidateConversation
        } catch (error: Throwable) {
            if (candidate.isInitialized()) candidate.close()
            throw error
        }
    }

    override fun stream(request: LlmRequest): Flow<String> = flow {
        events(request).collect { event ->
            when (event) {
                is LlmEvent.TextDelta -> emit(event.text)
                is LlmEvent.ToolCallRequested -> throw ToolCallRequiredException(event)
            }
        }
    }

    override fun events(request: LlmRequest): Flow<LlmEvent> = flow {
        val activeConversation = checkNotNull(conversation) {
            "No local model is loaded. Import a .litertlm model first."
        }

        activeConversation.sendMessageAsync(contentsFor(request)).collect { message ->
            emitMessageEvents(message)
        }
    }.flowOn(dispatcher)

    override fun submitToolResults(results: List<LlmToolResult>): Flow<LlmEvent> = flow {
        check(toolCatalog != null) { "Tools are not enabled for this local model conversation." }
        require(results.isNotEmpty()) { "At least one tool result is required." }
        val toolResponseContents = results.map { result ->
            Content.ToolResponse(result.name, result.response)
        }
        val activeConversation = checkNotNull(conversation) {
            "No local model is loaded. Import a .litertlm model first."
        }
        activeConversation.sendMessageAsync(
            Message.tool(Contents.of(toolResponseContents)),
        ).collect { message ->
            emitMessageEvents(message)
        }
    }.flowOn(dispatcher)

    override fun close() {
        try {
            executor.submit { closeResourcesOnExecutor() }.get()
        } finally {
            dispatcher.close()
        }
    }

    private fun closeResourcesOnExecutor() {
        conversation?.close()
        conversation = null
        engine?.close()
        engine = null
    }

    private fun contentsFor(request: LlmRequest): Contents = request.imageFile?.let { imageFile ->
        require(imageFile.isFile) { "Attached image does not exist." }
        Contents.of(Content.ImageFile(imageFile.absolutePath), Content.Text(request.prompt))
    } ?: Contents.of(request.prompt)

    private suspend fun kotlinx.coroutines.flow.FlowCollector<LlmEvent>.emitMessageEvents(
        message: Message,
    ) {
        val text = message.contents.contents
            .filterIsInstance<Content.Text>()
            .joinToString(separator = "") { it.text }
            .cleanModelText()
        if (text.isNotEmpty()) emit(LlmEvent.TextDelta(text))
        message.toolCalls.forEach { toolCall ->
            toolCallSequence += 1
            emit(
                LlmEvent.ToolCallRequested(
                    callId = "tool-call-$toolCallSequence",
                    name = toolCall.name,
                    arguments = toolCall.arguments,
                ),
            )
        }
    }
}

internal fun String.cleanModelText(): String = replace(
    Regex(
        "<\\|(?:begin_of_text|end_of_text|start_of_turn|end_of_turn|turn|model|user|system)\\|>",
    ),
    "",
)
