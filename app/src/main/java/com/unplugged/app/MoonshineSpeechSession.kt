package com.unplugged.app

import ai.moonshine.voice.JNI
import ai.moonshine.voice.MicTranscriber
import android.content.Context
import java.io.Closeable
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Small application-owned boundary around Moonshine's Android microphone API.
 * The rest of the voice flow does not depend on Moonshine callback details.
 */
class MoonshineSpeechSession(
    context: Context,
    private val modelsRoot: File,
    private val variant: MoonshineModelVariant = MoonshineModelVariant.TINY_STREAMING,
) : Closeable {
    private val applicationContext = context.applicationContext
    private var transcriber: MicTranscriber? = null

    suspend fun start(
        onPartialText: (String) -> Unit,
        onFinalText: (String) -> Unit,
        onError: (Throwable) -> Unit,
    ) = withContext(Dispatchers.IO) {
        check(isMoonshineModelInstalled(modelsRoot, variant)) {
            "${variant.displayName} model is not installed. Import it from Voice settings first."
        }

        val mic = MicTranscriber(applicationContext)
            .language("en")
            .modelArch(variant.architecture)
            .modelsFrom(modelsRoot)
            .onText(onPartialText)
            .onLine { line -> onFinalText(line.text.orEmpty()) }
            .onError(onError)
        transcriber = mic
        try {
            mic.load()
            mic.start()
        } catch (error: Throwable) {
            transcriber = null
            mic.close()
            throw error
        }
    }

    private val MoonshineModelVariant.architecture: Int
        get() = when (this) {
            MoonshineModelVariant.TINY_STREAMING -> JNI.MOONSHINE_MODEL_ARCH_TINY_STREAMING
            MoonshineModelVariant.SMALL_STREAMING -> JNI.MOONSHINE_MODEL_ARCH_SMALL_STREAMING
        }

    fun stop() {
        transcriber?.stop()
    }

    override fun close() {
        transcriber?.let { mic ->
            mic.stop()
            mic.close()
        }
        transcriber = null
    }
}
