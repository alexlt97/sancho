package com.unplugged.app

import android.Manifest
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.graphics.drawable.ColorDrawable
import android.media.AudioAttributes
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import java.io.File
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class VoiceSessionActivity : ComponentActivity() {
    private var textToSpeech: TextToSpeech? = null
    private var textToSpeechReady = false
    private var textToSpeechInitializing = false
    private var pendingSpeech: String? = null
    private var responseJob: Job? = null
    private var recognitionJob: Job? = null
    private var moonshineSpeechSession: MoonshineSpeechSession? = null
    private var transcriptHandled = false
    private var voicePhase by mutableStateOf(VoicePhase.LISTENING)
    private var voiceDetail by mutableStateOf("Starting Moonshine")
    private var showActions by mutableStateOf(false)
    private var timing = VoiceTimingRecorder()
    private val voiceModelRuntime by lazy {
        VoiceModelRuntimeHolder.get(
            cacheDirectory = cacheDir,
            harness = sanchoModelHarness(HarnessRole.VOICE_AGENT),
        )
    }

    private val microphonePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            startMoonshineRecognition()
        } else {
            showError("Microphone permission is required")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        window.setBackgroundDrawable(ColorDrawable(AndroidColor.TRANSPARENT))
        window.setDimAmount(0f)
        setContent {
            SanchoTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                    contentAlignment = Alignment.Center,
                ) {
                    VoiceBubble(
                        phase = voicePhase,
                        detail = voiceDetail,
                        showActions = showActions,
                        onStop = ::stopVoiceSession,
                        onRestart = ::restartConversation,
                        onExit = ::exitToHome,
                    )
                }
            }
        }
        launchMoonshineRecognition()
    }

    private fun initializeTextToSpeech() {
        if (textToSpeechReady || textToSpeechInitializing) return
        textToSpeechInitializing = true
        textToSpeech = TextToSpeech(this) { result ->
            textToSpeechInitializing = false
            if (result != TextToSpeech.SUCCESS) {
                showError("Speech is unavailable")
                return@TextToSpeech
            }

            val languageResult = textToSpeech?.setLanguage(Locale.getDefault())
            textToSpeechReady = languageResult != TextToSpeech.LANG_MISSING_DATA &&
                languageResult != TextToSpeech.LANG_NOT_SUPPORTED
            if (!textToSpeechReady) {
                showError("Offline speech is unavailable")
                return@TextToSpeech
            }

            textToSpeech?.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            textToSpeech?.setOnUtteranceProgressListener(
                object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        timing.markSpeechStarted()
                        runOnUiThread {
                            setPhase(VoicePhase.SPEAKING, "Speaking response")
                        }
                    }

                    override fun onDone(utteranceId: String?) {
                        timing.markSpeechFinished()
                        logTiming("complete")
                        runOnUiThread {
                            showActions = true
                            setPhase(VoicePhase.READY, "Ready for another prompt")
                        }
                    }

                    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
                    override fun onError(utteranceId: String?) {
                        logTiming("tts_error")
                        runOnUiThread { showError("Speech failed") }
                    }
                },
            )
            pendingSpeech?.let { phrase ->
                pendingSpeech = null
                speak(phrase)
            }
        }
    }

    private fun launchMoonshineRecognition() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        startMoonshineRecognition()
    }

    private fun startMoonshineRecognition() {
        recognitionJob?.cancel()
        closeMoonshineSpeechSession()
        val variant = MoonshineSettingsStore(this).selectedVariant()
        val session = MoonshineSpeechSession(
            context = this,
            modelsRoot = File(filesDir, "moonshine"),
            variant = variant,
        )
        moonshineSpeechSession = session
        transcriptHandled = false
        setPhase(VoicePhase.LISTENING, "Listening with ${variant.displayName}")
        recognitionJob = lifecycleScope.launch {
            try {
                session.start(
                    onPartialText = { partial ->
                        val detail = partial.trim().ifBlank { "Listening with ${variant.displayName}" }
                        if (partial.isNotBlank()) timing.markTranscriptPartial()
                        setPhase(VoicePhase.LISTENING, detail)
                    },
                    onFinalText = ::handleSpeechResult,
                    onError = { error ->
                        Log.w("SanchoVoice", "Moonshine recognition failed", error)
                        showError("Moonshine speech failed")
                    },
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.w("SanchoVoice", "Moonshine could not start", error)
                showError(error.message ?: "Moonshine is not ready")
            }
        }
    }

    private fun handleSpeechResult(rawPhrase: String) {
        if (transcriptHandled) return
        val phrase = rawPhrase.trim()
        if (phrase.isBlank()) {
            showError("No speech was recognized")
            return
        }

        if (isVoiceStopCommand(phrase)) {
            timing.markTranscriptReceived()
            stopVoiceSession()
            return
        }

        transcriptHandled = true
        closeMoonshineSpeechSession()
        timing.markTranscriptReceived()
        showActions = false
        setPhase(VoicePhase.THINKING, "Preparing local model")
        initializeTextToSpeech()
        generateResponse(phrase)
    }

    private fun generateResponse(prompt: String) {
        responseJob = lifecycleScope.launch {
            val importedModel = FileModelImporter(
                contentResolver = contentResolver,
                modelsDirectory = File(filesDir, "models"),
            ).existingModel()
            if (importedModel == null) {
                showError("Import a local model first")
                return@launch
            }

            try {
                setPhase(VoicePhase.THINKING, "Loading local model")
                val result = voiceModelRuntime.generateWithMetrics(importedModel.file, prompt)
                timing.recordModel(result)
                setPhase(VoicePhase.SPEAKING, timing.report().compactModelSummary())
                speak(result.response.ifBlank { "The local model returned no response." })
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                showError("The local model could not answer")
            }
        }
    }

    private fun speak(phrase: String) {
        if (!textToSpeechReady) {
            pendingSpeech = phrase
            setPhase(VoicePhase.THINKING, "Starting offline speech")
            return
        }

        val result = textToSpeech?.speak(
            phrase,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "sancho-voice-response",
        )
        if (result == TextToSpeech.ERROR) {
            showError("Speech failed")
        }
    }

    private fun stopVoiceSession() {
        recognitionJob?.cancel()
        responseJob?.cancel()
        closeMoonshineSpeechSession()
        textToSpeech?.stop()
        setPhase(VoicePhase.STOPPED, "Stopped")
        logTiming("stopped")
        exitToHome()
    }

    private fun showError(message: String) {
        recognitionJob?.cancel()
        closeMoonshineSpeechSession()
        responseJob?.cancel()
        setPhase(VoicePhase.ERROR, message)
        showActions = true
        logTiming("error")
    }

    private fun restartConversation() {
        recognitionJob?.cancel()
        responseJob?.cancel()
        closeMoonshineSpeechSession()
        textToSpeech?.stop()
        pendingSpeech = null
        timing = VoiceTimingRecorder()
        showActions = false
        setPhase(VoicePhase.LISTENING, "Starting Moonshine")
        launchMoonshineRecognition()
    }

    private fun closeMoonshineSpeechSession() {
        moonshineSpeechSession?.let { session ->
            session.stop()
            session.close()
        }
        moonshineSpeechSession = null
    }

    private fun exitToHome() {
        startActivity(
            Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            },
        )
        finishAndRemoveTask()
    }

    private fun setPhase(phase: VoicePhase, detail: String) {
        voicePhase = phase
        voiceDetail = detail
    }

    private fun logTiming(outcome: String) {
        val report = timing.report()
        Log.i(
            "SanchoVoice",
            "outcome=$outcome recognition_first_partial_ms=${report.recognitionFirstPartialMillis ?: -1} " +
                "recognition_ms=${report.recognitionMillis ?: -1} " +
                "model_load_ms=${report.modelLoadMillis} model_ttft_ms=${report.modelTtftMillis ?: -1} " +
                "model_generation_ms=${report.modelGenerationMillis ?: -1} " +
                "tts_ms=${report.ttsMillis ?: -1} total_ms=${report.totalMillis}",
        )
    }

    override fun onDestroy() {
        recognitionJob?.cancel()
        closeMoonshineSpeechSession()
        responseJob?.cancel()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
        super.onDestroy()
    }
}

@Composable
private fun VoiceBubble(
    phase: VoicePhase,
    detail: String,
    showActions: Boolean,
    onStop: () -> Unit,
    onRestart: () -> Unit,
    onExit: () -> Unit,
) {
    val transition = rememberInfiniteTransition(label = "voice-bubble")
    val pulse by transition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(850),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "voice-pulse",
    )
    val phaseColor = when (phase) {
        VoicePhase.LISTENING -> MaterialTheme.colorScheme.primary
        VoicePhase.THINKING -> MaterialTheme.colorScheme.tertiary
        VoicePhase.SPEAKING -> MaterialTheme.colorScheme.secondary
        VoicePhase.READY -> MaterialTheme.colorScheme.primary
        VoicePhase.ERROR -> MaterialTheme.colorScheme.error
        VoicePhase.STOPPED -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = Modifier
            .width(252.dp)
            .height(220.dp),
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 12.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(18.dp))
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .scale(
                        if (phase == VoicePhase.ERROR || phase == VoicePhase.STOPPED || phase == VoicePhase.READY) {
                            1f
                        } else {
                            pulse
                        },
                    )
                    .background(phaseColor.copy(alpha = 0.24f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clickable(
                            onClick = if (phase == VoicePhase.READY || phase == VoicePhase.ERROR) {
                                onRestart
                            } else {
                                onStop
                            },
                        )
                        .semantics {
                            contentDescription = if (
                                phase == VoicePhase.READY || phase == VoicePhase.ERROR
                            ) {
                                "Ask Sancho another prompt"
                            } else {
                                "Stop Sancho voice session"
                            }
                        }
                        .background(phaseColor, CircleShape),
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(phase.label, style = MaterialTheme.typography.titleMedium)
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.74f),
                maxLines = 1,
                textAlign = TextAlign.Center,
            )
            Text(
                text = if (phase == VoicePhase.READY || phase == VoicePhase.ERROR) {
                    "Tap the sphere to ask again"
                } else {
                    "Tap the sphere to stop"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
            )
            if (showActions) {
                TextButton(onClick = onExit) {
                    Text("Exit")
                }
            }
        }
    }
}
