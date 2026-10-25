package com.unplugged.app

import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Deterministic local-model stand-in used only by the managed visual preview.
 * The normal app build continues to use [LiteRtLocalLlm].
 */
class MockLocalLlm : LocalLlm {
    override suspend fun load(modelFile: File) = Unit

    override fun stream(request: LlmRequest): Flow<String> = flow {
        val response = if (request.imageFile != null) {
            """
            ## Mock image response

            I received **${request.imageFile.name}** and would analyze it locally here.

            - Image input: ready
            - Vision pipeline: simulated
            - Example equation: ${'$'}5${'$'}
            """.trimIndent()
        } else {
            """
            ## Mock response

            You wrote:

            > ${request.prompt}

            This is a simulated streamed response for UI preview. Try **bold**, *italic*, a list, and ${'$'}5${'$'}.
            """.trimIndent()
        }

        response.chunked(24).forEach { chunk ->
            emit(chunk)
            delay(35)
        }
    }

    override fun close() = Unit
}
