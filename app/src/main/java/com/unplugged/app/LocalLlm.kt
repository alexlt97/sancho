package com.unplugged.app

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import java.io.File
import java.util.concurrent.Executors
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.asCoroutineDispatcher

data class LlmRequest(
    val prompt: String,
    val imageFile: File? = null,
)

interface LocalLlm {
    suspend fun load(modelFile: File)

    fun stream(request: LlmRequest): Flow<String>

    fun stream(prompt: String): Flow<String> = stream(LlmRequest(prompt))

    fun close()
}

class LiteRtLocalLlm(
    private val cacheDirectory: File,
    private val backend: Backend = Backend.CPU(),
) : LocalLlm {
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "sancho-litert-lm")
    }
    private val dispatcher: ExecutorCoroutineDispatcher = executor.asCoroutineDispatcher()

    private var engine: Engine? = null
    private var conversation: com.google.ai.edge.litertlm.Conversation? = null

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
            val candidateConversation = candidate.createConversation()
            engine = candidate
            conversation = candidateConversation
        } catch (error: Throwable) {
            if (candidate.isInitialized()) candidate.close()
            throw error
        }
    }

    override fun stream(request: LlmRequest): Flow<String> = flow {
        val activeConversation = checkNotNull(conversation) {
            "No local model is loaded. Import a .litertlm model first."
        }

        val contents = request.imageFile?.let { imageFile ->
            require(imageFile.isFile) { "Attached image does not exist." }
            Contents.of(Content.ImageFile(imageFile.absolutePath), Content.Text(request.prompt))
        } ?: Contents.of(request.prompt)

        activeConversation.sendMessageAsync(contents).collect { message ->
            val text = message.contents.contents
                .filterIsInstance<Content.Text>()
                .joinToString(separator = "") { it.text }
                .cleanModelText()
            if (text.isNotEmpty()) emit(text)
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
}

internal fun String.cleanModelText(): String = replace(
    Regex(
        "<\\|(?:begin_of_text|end_of_text|start_of_turn|end_of_turn|turn|model|user|system)\\|>",
    ),
    "",
)
