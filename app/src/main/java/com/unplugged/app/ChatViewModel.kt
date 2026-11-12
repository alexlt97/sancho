package com.unplugged.app

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

data class ChatMessage(
    val author: String,
    val text: String,
    val imageAttachment: ImageAttachment? = null,
    val timeToFirstTokenMillis: Long? = null,
    val isStreaming: Boolean = false,
    val totalResponseTimeMillis: Long? = null,
    val markdownParseTimeMillis: Long? = null,
    val timeToFirstDisplayMillis: Long? = null,
    val displayUpdateCount: Int = 0,
    val responseStartedAtNanos: Long? = null,
    val sources: List<WebSource> = emptyList(),
    val webSearchMetrics: WebSearchMetrics? = null,
)

enum class WebSearchPhase {
    IDLE,
    WAITING_FOR_APPROVAL,
    SEARCHING,
    RESULTS_RECEIVED,
    FAILED,
    CANCELLED,
}

data class ChatUiState(
    val draft: String = "",
    val messages: List<ChatMessage> = emptyList(),
    val status: String = "Offline shell ready; import a model to start local chat.",
    val modelName: String? = null,
    val isImportingModel: Boolean = false,
    val isModelReady: Boolean = false,
    val isGenerating: Boolean = false,
    val imageAttachment: ImageAttachment? = null,
    val pendingToolCall: ValidatedToolCall? = null,
    val webSearchPhase: WebSearchPhase = WebSearchPhase.IDLE,
)

private const val STREAM_UI_UPDATE_INTERVAL_MILLIS = 50L
private const val STREAM_UI_UPDATE_MIN_CHARS = 96

class ChatViewModel(
    private val modelImporter: ModelImporter? = null,
    private val localLlm: LocalLlm? = null,
    private val initialModelReady: Boolean = false,
    private val toolExecutionCoordinator: ToolExecutionCoordinator? = null,
    private val toolPolicyProvider: ToolPolicyProvider = ToolPolicyProvider {
        ToolConversationPolicy()
    },
    private val toolMetricsRecorder: ToolMetricsRecorder? = null,
    private val toolMetricsEnabledProvider: () -> Boolean = { false },
    private val webSearchEnabledProvider: () -> Boolean = { true },
) : ViewModel() {
    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()
    private var generationJob: Job? = null
    private var pendingToolDecision: CompletableDeferred<ToolConfirmationDecision>? = null

    init {
        if (initialModelReady) {
            _uiState.value = _uiState.value.copy(
                status = "Mock preview ready; responses are simulated.",
                modelName = "Sancho mock",
                isModelReady = true,
            )
        } else {
            modelImporter?.existingModel()?.let(::loadModel)
        }
    }

    fun updateDraft(value: String) {
        _uiState.value = _uiState.value.copy(draft = value)
    }

    fun attachImage(attachment: ImageAttachment) {
        if (_uiState.value.isGenerating) return
        _uiState.value = _uiState.value.copy(
            imageAttachment = attachment,
            status = "Image attached; add a question or send to describe it.",
        )
    }

    fun removeImage() {
        if (_uiState.value.isGenerating) return
        _uiState.value = _uiState.value.copy(imageAttachment = null)
    }

    fun showImageError(message: String) {
        _uiState.value = _uiState.value.copy(status = message)
    }

    fun showStatus(message: String) {
        _uiState.value = _uiState.value.copy(status = message)
    }

    fun send() {
        val imageAttachment = _uiState.value.imageAttachment
        val prompt = _uiState.value.draft.trim().ifEmpty {
            if (imageAttachment != null) "Describe this image." else return
        }

        if (_uiState.value.isGenerating) return

        _uiState.value = _uiState.value.copy(
            draft = "",
            imageAttachment = null,
            webSearchPhase = WebSearchPhase.IDLE,
            messages = _uiState.value.messages + ChatMessage(
                author = "You",
                text = prompt,
                imageAttachment = imageAttachment,
            ),
        )

        val llm = localLlm ?: run {
            _uiState.value = _uiState.value.copy(status = "Local model runtime is unavailable.")
            return
        }

        if (!_uiState.value.isModelReady) {
            _uiState.value = _uiState.value.copy(status = "Import and load a local model first.")
            return
        }

        generationJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                status = "Generating locally…",
                isGenerating = true,
            )
            val startedAtNanos = System.nanoTime()
            var timeToFirstTokenMillis: Long? = null
            var totalResponseTimeMillis: Long? = null
            var lastUiUpdateNanos = startedAtNanos
            var publishedResponseLength = 0
            val responseText = StringBuilder()
            var responseSources: List<WebSource> = emptyList()
            var responseWebSearchMetrics: WebSearchMetrics? = null
            var webSearchCompleted = false

            fun publishResponse(streaming: Boolean) {
                if (responseText.isEmpty() ||
                    (streaming && responseText.length == publishedResponseLength)
                ) {
                    return
                }

                val messages = _uiState.value.messages
                val last = messages.lastOrNull()
                val response = responseText.toString()
                val updatedMessages = if (last?.author == "Assistant") {
                    messages.dropLast(1) + last.copy(
                        text = response,
                        timeToFirstTokenMillis = last.timeToFirstTokenMillis ?: timeToFirstTokenMillis,
                        isStreaming = streaming,
                        totalResponseTimeMillis = last.totalResponseTimeMillis ?: totalResponseTimeMillis,
                        responseStartedAtNanos = last.responseStartedAtNanos ?: startedAtNanos,
                        sources = if (streaming) last.sources else responseSources,
                        webSearchMetrics = if (streaming) {
                            last.webSearchMetrics
                        } else {
                            responseWebSearchMetrics
                        },
                    )
                } else {
                    messages + ChatMessage(
                        author = "Assistant",
                        text = response,
                        timeToFirstTokenMillis = timeToFirstTokenMillis,
                        isStreaming = streaming,
                        totalResponseTimeMillis = totalResponseTimeMillis,
                        responseStartedAtNanos = startedAtNanos,
                        sources = if (streaming) emptyList() else responseSources,
                        webSearchMetrics = if (streaming) null else responseWebSearchMetrics,
                    )
                }
                _uiState.value = _uiState.value.copy(messages = updatedMessages)
                publishedResponseLength = responseText.length
                lastUiUpdateNanos = System.nanoTime()
            }

            suspend fun appendChunk(chunk: String) {
                if (chunk.isEmpty()) return
                val firstTokenMillis = if (timeToFirstTokenMillis == null) {
                    (System.nanoTime() - startedAtNanos) / 1_000_000L
                } else {
                    null
                }
                timeToFirstTokenMillis = timeToFirstTokenMillis ?: firstTokenMillis
                responseText.append(chunk)

                val elapsedSinceUpdateMillis =
                    (System.nanoTime() - lastUiUpdateNanos) / 1_000_000L
                if (publishedResponseLength == 0 ||
                    responseText.length - publishedResponseLength >= STREAM_UI_UPDATE_MIN_CHARS ||
                    elapsedSinceUpdateMillis >= STREAM_UI_UPDATE_INTERVAL_MILLIS
                ) {
                    publishResponse(streaming = true)
                    yield()
                }
            }

            try {
                val request = LlmRequest(prompt = prompt, imageFile = imageAttachment?.file)
                val toolRunner = toolExecutionCoordinator?.let { coordinator ->
                    ToolConversationRunner(
                        localLlm = llm,
                        catalog = ToolCatalog.browserHandoff,
                        executor = coordinator,
                        confirm = ::awaitToolConfirmation,
                        maxToolCallsPerRun = toolPolicyProvider.current().maxToolCallsPerRun,
                    )
                }
                if (toolRunner != null) {
                    toolRunner.run(request).collect { event ->
                        when (event) {
                            is ToolConversationEvent.TextDelta -> appendChunk(event.text)
                            is ToolConversationEvent.ConfirmationRequested -> {
                                _uiState.value = _uiState.value.copy(
                                    status = "Waiting for web-search approval…",
                                    webSearchPhase = WebSearchPhase.WAITING_FOR_APPROVAL,
                                )
                            }
                            is ToolConversationEvent.ToolExecuted -> {
                                responseSources = event.result.sources
                                webSearchCompleted = event.result.status == ToolExecutionStatus.SUCCEEDED
                                responseWebSearchMetrics = WebSearchMetrics(
                                    provider = event.result.provider,
                                    status = event.result.status,
                                    elapsedMillis = event.result.elapsedMillis,
                                    resultCount = event.result.sources.size,
                                )
                                if (toolMetricsEnabledProvider()) {
                                    toolMetricsRecorder?.record(
                                        ToolExecutionMetric(
                                            recordedAtMillis = System.currentTimeMillis(),
                                            tool = event.result.tool.wireName,
                                            provider = event.result.provider,
                                            status = event.result.status,
                                            elapsedMillis = event.result.elapsedMillis,
                                            resultCount = event.result.sources.size,
                                            truncated = event.result.truncated,
                                            errorCode = event.result.errorCode,
                                        ),
                                    )
                                }
                                _uiState.value = _uiState.value.copy(
                                    status = event.result.displayMessage
                                        ?: if (event.result.status == ToolExecutionStatus.SUCCEEDED) {
                                            "Web search completed."
                                        } else {
                                            "Web search was not completed."
                                        },
                                    webSearchPhase = if (event.result.status == ToolExecutionStatus.SUCCEEDED) {
                                        WebSearchPhase.RESULTS_RECEIVED
                                    } else {
                                        WebSearchPhase.FAILED
                                    },
                                )
                            }
                            is ToolConversationEvent.ToolRejected -> {
                                _uiState.value = _uiState.value.copy(
                                    status = "Web search declined: ${event.message}",
                                    webSearchPhase = WebSearchPhase.FAILED,
                                )
                            }
                        }
                    }
                } else {
                    llm.stream(request).collect { chunk -> appendChunk(chunk) }
                }
                if (webSearchCompleted && responseText.isBlank()) {
                    responseText.append(
                        if (responseSources.isEmpty()) {
                            "The web search completed, but the local model did not return a summary."
                        } else {
                            "The web search completed, but the local model did not return a summary. " +
                                "Open a source below to inspect the information."
                        },
                    )
                }
                totalResponseTimeMillis = (System.nanoTime() - startedAtNanos) / 1_000_000L
                publishResponse(streaming = false)
                currentCoroutineContext().ensureActive()
                val state = _uiState.value
                val lastMessage = state.messages.lastOrNull()
                _uiState.value = if (lastMessage?.author == "Assistant") {
                    state.copy(
                        status = "Local model ready.",
                        messages = state.messages.dropLast(1) +
                            lastMessage.copy(timeToFirstTokenMillis = timeToFirstTokenMillis),
                    )
                } else {
                    state.copy(status = "Local model ready.")
                }
            } catch (_: CancellationException) {
                totalResponseTimeMillis = (System.nanoTime() - startedAtNanos) / 1_000_000L
                publishResponse(streaming = false)
                _uiState.value = _uiState.value.copy(status = "Generation stopped.")
            } catch (error: Exception) {
                totalResponseTimeMillis = (System.nanoTime() - startedAtNanos) / 1_000_000L
                publishResponse(streaming = false)
                _uiState.value = _uiState.value.copy(
                    status = "Local inference failed: ${error.message ?: "unknown error"}",
                )
            } finally {
                pendingToolDecision?.complete(ToolConfirmationDecision.Denied)
                pendingToolDecision = null
                _uiState.value = _uiState.value.copy(pendingToolCall = null)
                _uiState.value = _uiState.value.copy(isGenerating = false)
                generationJob = null
            }
        }
    }

    fun cancelGeneration() {
        if (!_uiState.value.isGenerating) return
        _uiState.value = _uiState.value.copy(status = "Stopping…")
        pendingToolDecision?.complete(ToolConfirmationDecision.Denied)
        generationJob?.cancel()
    }

    fun approvePendingTool(editedQuery: String? = null) {
        val call = _uiState.value.pendingToolCall ?: return
        val approvedCall = if (editedQuery.isNullOrBlank() || editedQuery == searchQuery(call)) {
            call.approve()
        } else {
            when (
                val validation = ToolCatalog.browserHandoff.validate(
                    RawToolCall(
                        id = call.id,
                        name = call.spec.name.wireName,
                        arguments = mapOf("query" to editedQuery),
                    ),
                )
            ) {
                is ToolCallValidation.Accepted -> validation.call.approve()
                is ToolCallValidation.Rejected -> {
                    _uiState.value = _uiState.value.copy(status = validation.message)
                    return
                }
            }
        }
        _uiState.value = _uiState.value.copy(
            pendingToolCall = null,
            status = "Searching the web…",
            webSearchPhase = WebSearchPhase.SEARCHING,
        )
        pendingToolDecision?.complete(ToolConfirmationDecision.Approved(approvedCall))
    }

    fun denyPendingTool() {
        _uiState.value = _uiState.value.copy(
            pendingToolCall = null,
            status = "Web search cancelled.",
            webSearchPhase = WebSearchPhase.CANCELLED,
        )
        pendingToolDecision?.complete(ToolConfirmationDecision.Denied)
    }

    private suspend fun awaitToolConfirmation(call: ValidatedToolCall): ToolConfirmationDecision {
        if (!webSearchEnabledProvider()) {
            return ToolConfirmationDecision.Denied
        }
        val decision = CompletableDeferred<ToolConfirmationDecision>()
        pendingToolDecision?.complete(ToolConfirmationDecision.Denied)
        pendingToolDecision = decision
        _uiState.value = _uiState.value.copy(
            pendingToolCall = call,
            status = "Waiting for web-search approval…",
        )
        return decision.await()
    }

    private fun searchQuery(call: ValidatedToolCall): String =
        (call.arguments as? OpenWebSearchArguments)?.query.orEmpty()

    fun recordMarkdownParseTime(responseText: String, elapsedMillis: Long) {
        val state = _uiState.value
        val index = state.messages.indexOfLast { message ->
            message.author == "Assistant" && message.text == responseText
        }
        if (index == -1 || state.messages[index].markdownParseTimeMillis == elapsedMillis) {
            return
        }

        val messages = state.messages.toMutableList()
        messages[index] = messages[index].copy(markdownParseTimeMillis = elapsedMillis)
        _uiState.value = state.copy(messages = messages)
    }

    fun recordAssistantDisplayed(responseText: String, frameTimeNanos: Long) {
        val state = _uiState.value
        val index = state.messages.indexOfLast { message ->
            message.author == "Assistant" && message.text == responseText && message.isStreaming
        }
        if (index == -1) return

        val message = state.messages[index]
        val startedAtNanos = message.responseStartedAtNanos ?: return
        val displayTimeMillis =
            ((frameTimeNanos - startedAtNanos) / 1_000_000L).coerceAtLeast(0L)
        val messages = state.messages.toMutableList()
        messages[index] = message.copy(
            timeToFirstDisplayMillis = message.timeToFirstDisplayMillis ?: displayTimeMillis,
            displayUpdateCount = message.displayUpdateCount + 1,
        )
        _uiState.value = state.copy(messages = messages)
    }

    fun importModel(uri: Uri) {
        val importer = modelImporter ?: run {
            _uiState.value = _uiState.value.copy(status = "Model import is unavailable.")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                status = "Importing local model…",
                isImportingModel = true,
            )

            when (val result = importer.importModel(uri)) {
                is ModelImportResult.Success -> {
                    loadImportedModel(result.model)
                }

                is ModelImportResult.Failure -> {
                    _uiState.value = _uiState.value.copy(
                        status = result.message,
                        isImportingModel = false,
                    )
                }
            }
        }
    }

    fun loadModel(model: ImportedModel) {
        viewModelScope.launch { loadImportedModel(model) }
    }

    private suspend fun loadImportedModel(model: ImportedModel) {
        val llm = localLlm ?: run {
            _uiState.value = _uiState.value.copy(
                status = "Model imported; local model runtime is unavailable.",
                modelName = model.fileName,
                isImportingModel = false,
            )
            return
        }

        _uiState.value = _uiState.value.copy(
            status = "Loading local model…",
            modelName = model.fileName,
            isImportingModel = true,
            isModelReady = false,
        )
        try {
            llm.load(model.file)
            _uiState.value = _uiState.value.copy(
                status = "Local model ready.",
                isImportingModel = false,
                isModelReady = true,
            )
        } catch (error: Exception) {
            _uiState.value = _uiState.value.copy(
                status = "Model loading failed: ${error.message ?: "unknown error"}",
                isImportingModel = false,
                isModelReady = false,
            )
        }
    }

    override fun onCleared() {
        localLlm?.close()
        super.onCleared()
    }

    companion object {
        fun factory(
            modelImporter: ModelImporter,
            localLlm: LocalLlm,
            initialModelReady: Boolean = false,
            toolExecutionCoordinator: ToolExecutionCoordinator? = null,
            toolPolicyProvider: ToolPolicyProvider = ToolPolicyProvider {
                ToolConversationPolicy()
            },
            toolMetricsRecorder: ToolMetricsRecorder? = null,
            toolMetricsEnabledProvider: () -> Boolean = { false },
            webSearchEnabledProvider: () -> Boolean = { true },
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
                        return ChatViewModel(
                            modelImporter,
                            localLlm,
                            initialModelReady,
                            toolExecutionCoordinator,
                            toolPolicyProvider,
                            toolMetricsRecorder,
                            toolMetricsEnabledProvider,
                            webSearchEnabledProvider,
                        ) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
                }
            }
    }
}
