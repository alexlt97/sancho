package com.unplugged.app

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

data class ChatMessage(
    val author: String,
    val text: String,
    val imageAttachment: ImageAttachment? = null,
    val timeToFirstTokenMillis: Long? = null,
)

data class ChatUiState(
    val draft: String = "",
    val messages: List<ChatMessage> = emptyList(),
    val status: String = "Offline shell ready; import a model to start local chat.",
    val modelName: String? = null,
    val isImportingModel: Boolean = false,
    val isModelReady: Boolean = false,
    val isGenerating: Boolean = false,
    val imageAttachment: ImageAttachment? = null,
)

class ChatViewModel(
    private val modelImporter: ModelImporter? = null,
    private val localLlm: LocalLlm? = null,
    private val initialModelReady: Boolean = false,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()
    private var generationJob: Job? = null

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

    fun send() {
        val imageAttachment = _uiState.value.imageAttachment
        val prompt = _uiState.value.draft.trim().ifEmpty {
            if (imageAttachment != null) "Describe this image." else return
        }

        if (_uiState.value.isGenerating) return

        _uiState.value = _uiState.value.copy(
            draft = "",
            imageAttachment = null,
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
            try {
                llm.stream(LlmRequest(prompt = prompt, imageFile = imageAttachment?.file)).collect { chunk ->
                    val firstTokenMillis = if (timeToFirstTokenMillis == null && chunk.isNotEmpty()) {
                        (System.nanoTime() - startedAtNanos) / 1_000_000L
                    } else {
                        null
                    }
                    timeToFirstTokenMillis = timeToFirstTokenMillis ?: firstTokenMillis
                    val messages = _uiState.value.messages
                    val last = messages.lastOrNull()
                    val updatedMessages = if (last?.author == "Assistant") {
                        messages.dropLast(1) + last.copy(
                            text = last.text + chunk,
                            timeToFirstTokenMillis = last.timeToFirstTokenMillis ?: firstTokenMillis,
                        )
                    } else {
                        messages + ChatMessage(
                            author = "Assistant",
                            text = chunk,
                            timeToFirstTokenMillis = firstTokenMillis,
                        )
                    }
                    _uiState.value = _uiState.value.copy(messages = updatedMessages)
                }
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
                _uiState.value = _uiState.value.copy(status = "Generation stopped.")
            } catch (error: Exception) {
                _uiState.value = _uiState.value.copy(
                    status = "Local inference failed: ${error.message ?: "unknown error"}",
                )
            } finally {
                _uiState.value = _uiState.value.copy(isGenerating = false)
                generationJob = null
            }
        }
    }

    fun cancelGeneration() {
        if (!_uiState.value.isGenerating) return
        _uiState.value = _uiState.value.copy(status = "Stopping…")
        generationJob?.cancel()
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
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
                        return ChatViewModel(modelImporter, localLlm, initialModelReady) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
                }
            }
    }
}
