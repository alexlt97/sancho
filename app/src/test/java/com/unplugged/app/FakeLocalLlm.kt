package com.unplugged.app

import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf

class FakeLocalLlm : LocalLlm {
    var loadedFile: File? = null
        private set

    var lastRequest: LlmRequest? = null
        private set

    override suspend fun load(modelFile: File) {
        loadedFile = modelFile
    }

    override fun stream(request: LlmRequest): Flow<String> {
        lastRequest = request
        return flowOf("Fake answer to: ${request.prompt}")
    }

    override fun close() = Unit
}

class CancellableFakeLocalLlm : LocalLlm {
    override suspend fun load(modelFile: File) = Unit

    override fun stream(request: LlmRequest): Flow<String> = flow {
        emit("Partial response")
        awaitCancellation()
    }

    override fun close() = Unit
}
