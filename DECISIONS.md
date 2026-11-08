# Decisions

## 2026-10-22 — Offline Android target

- Decision: target a Pixel 9a running GrapheneOS.
- Why: the project prioritizes local inference, privacy, and operation without network access.

## 2026-10-22 — Initial model stack

- Decision: keep Kotlin, Jetpack Compose, LiteRT-LM, and Gemma 4 E2B as the planned stack.
- Why: this preserves the intended Android-native on-device path while keeping the model behind a `LocalLlm` abstraction.

## 2026-10-22 — Product scope

- Decision: build reliable offline multi-turn chat before phone automation or tool calling.
- Why: it creates a testable foundation and avoids granting phone-action permissions before the core inference loop is reliable.

## 2026-10-22 — Audit policy

- Decision: make the audit Android/Gradle-aware instead of manufacturing unrelated Node/Python files.
- Why: dependency locking, build verification, and device checks must reflect the actual project type.

## 2026-10-22 — Terminal-first build

- Decision: keep the JDK, Android command-line tools, SDK packages, and Gradle distribution in the ignored `.tooling/` directory, provisioned by `make setup`.
- Why: Codex must be able to compile, test, install, and launch the app without Android Studio.
- Detail: AGP 9 supplies Kotlin support, so the project uses the Compose compiler plugin without the rejected Kotlin Android plugin.

## 2026-10-25 — Explicit model import

- Decision: let the user select a `.litertlm` file with Android's document picker and copy it into the AI profile's private app storage.
- Why: model binaries are user-provided, must remain outside the APK and Git, and should not require broad storage permissions or network access.
- Detail: imports are streamed through a temporary file and moved into place only after copying succeeds.

## 2026-10-25 — CPU-first LiteRT-LM runtime

- Decision: use the LiteRT-LM CPU backend as the initial runtime and keep GPU acceleration as a measured follow-up.
- Why: CPU execution is the safer first device milestone on GrapheneOS and does not require GPU-specific native-library declarations.
- Detail: the adapter owns the engine and conversation on one background executor, extracts text content from streamed messages, and removes model control tokens before display.

## 2026-10-25 — GPU remains benchmark-only

- Decision: enable LiteRT-LM GPU support for explicit benchmarking, but keep CPU as the app's normal backend for now.
- Why: the Pixel 9a completed GPU inference faster after preparation, but the first GPU load took about 53 seconds versus about 1 second for CPU. A cached GPU load took about 5.4 seconds.
- Detail: GPU support is guarded by optional `libvndksupport.so` and `libOpenCL.so` manifest declarations; `make gpu-benchmark` exercises it without changing normal chat behavior.

## 2026-10-25 — Native image input

- Decision: pass user-selected or camera-captured images directly to the Gemma conversation through LiteRT-LM image content.
- Why: Gemma 4 E2B is multimodal, and the installed LiteRT-LM API exposes `Content.ImageFile`; an OCR-only preprocessing path would discard useful visual information.
- Detail: images are copied into the AI profile's app-private cache, selected through the system Photo Picker or captured through a `FileProvider` URI, and are never added to the APK or sent over a network.

## 2026-10-25 — Mocked visual preview runtime

- Decision: make `make preview` build a deterministic mock-model variant that starts ready and streams sample responses, while normal builds keep the real LiteRT-LM runtime.
- Why: UI iteration should not require importing a multi-gigabyte model or using the Pixel 9a, and the mock must not accidentally replace real on-device inference.
- Detail: the preview-only `mockPreview` Gradle property enables `BuildConfig.MOCK_PREVIEW`; the mock exercises text, image, Markdown, inline math, and response-timing presentation paths.

## 2026-10-29 — V2 offline voice providers

- Decision: use FUTO Voice Input for offline speech-to-text, the existing local Gemma 4 E2B text path for reasoning, and Android TextToSpeech for spoken responses.
- Why: FUTO already provides an offline speech pipeline, while keeping transcription separate from reasoning makes the first voice slice easier to test and keeps latency measurable.
- Detail: integrate FUTO through the Android RECOGNIZE_SPEECH intent; start it from a compact widget-launched voice session rather than the main chat activity. Do not add network speech fallback or retain microphone recordings. Direct Gemma audio input is a later benchmark, not the first provider.

## 2026-10-30 — First voice integration boundary

- Decision: the first implementation delegates microphone capture and transcription to the installed FUTO Voice Input app; Sancho requests recognition through the Android intent and does not request RECORD_AUDIO itself.
- Why: FUTO already owns its offline speech model and microphone permission, while Sancho only needs the returned transcript and can keep its APK permission budget narrow.
- Detail: Sancho passes the transcript through the imported local Gemma model, speaks the final response through Android TextToSpeech, and closes the voice activity. F009 remains active until the Pixel 9a AI-profile test passes.

## 2026-10-30 — Minimal voice entry points

- Decision: make the home-screen widget a 1×1 microphone icon and add a Quick Settings tile that reuse the same voice activity.
- Why: the first integration test needs a fast launcher, not a conversation preview or a second text surface.
- Detail: FUTO owns the visible recognition UI; Sancho does not display the transcript and closes after Android TextToSpeech finishes speaking it.

## 2026-10-30 — Warm voice model runtime

- Decision: keep a model-keyed voice runtime warm for the life of the Sancho process and reuse it across voice requests.
- Why: loading Gemma for every widget tap made subsequent voice requests unnecessarily slow.
- Detail: the runtime reloads only when the imported model path, size, or modification time changes; the Quick Settings icon uses a monochrome Sancho mark because Android expects tile icons to be white on transparency and may tint them.

## 2026-10-30 — Voice cancellation proposal

- Proposal: add a user-visible Stop action through an ongoing notification while local generation or TTS is active.
- Why: the voice activity is intentionally transparent, so a notification is more reliable and discoverable than requiring a second tap on a hidden activity.
- Scope: cancellation must cancel model-flow collection, stop TTS, and update widget/tile state; no implementation or notification permission is added in this step.

## 2026-10-30 — Host voice design preview

- Decision: provide a browser-based, dependency-free mock for the voice entry points before relying on an emulator or Pixel 9a.
- Why: voice UX can be iterated safely while the real FUTO, Gemma, and TextToSpeech path remains device-only and resource-intensive.
- Detail: `make voice-preview` serves local HTML that models widget/tile surfaces, listening, thinking, speaking, stopped states, TTS progress, speed changes, and cancellation; it makes no microphone, model, phone, or network calls.

## 2026-10-30 — Four Settings sections

- Decision: organize Settings into Models, Voice settings, Reports, and Chat settings.
- Why: model import, voice entry-point controls, issue reporting, and future chat/model harness controls have different responsibilities and should not compete in one card.
- Detail: the overview shows only concise section summaries; each row opens its own page. Models and Reports are functional today; Voice settings and Chat settings provide the documented placement for future controls without adding behavior prematurely. The host device preview mirrors the same navigation.

## 2026-10-30 — Role-specific model harness

- Decision: keep model-facing instructions in versioned prompt assets and inject a role-specific system instruction into separate LiteRT-LM conversations.
- Why: Chat/Vision and Voice/Agent have different output formats and capability boundaries; a single broad prompt would make the speech surface verbose and could blur tool permissions.
- Detail: Chat/Vision receives text/image capability metadata and supports normal Markdown responses. Voice/Agent is speech-friendly and tool-free in this iteration. `AGENTS.md` is never model input, and future tools must be granted by application policy and an executor rather than by prompt text.

## 2026-10-30 — Automated real-model safety checks

- Decision: add `make harness-device-test` as a guarded Pixel 9a AI-profile instrumentation command for adversarial role checks.
- Why: prompt-profile unit tests cannot establish how the imported quantized model behaves, while direct ADB/UI automation cannot safely or silently own FUTO microphone capture and Android TextToSpeech.
- Detail: the command verifies the device and active user, installs only for the AI profile, runs six refusal and speech-shape cases on the real CPU model, and reports aggregate metrics without logging prompt or response text. System-boundary behavior remains a manual smoke test.

## 2026-10-31 — Explicit voice entry launch

- Decision: make the widget and Quick Settings tile share an explicit `VoiceSessionActivity` PendingIntent; configure the tile with `Tile.setActivityLaunchForClick`.
- Why: the widget and tile must enter the transparent FUTO → Gemma → TextToSpeech flow directly and must not fall back to the launcher activity.
- Detail: the shared intent includes the voice action, `FLAG_ACTIVITY_NEW_TASK`, and `FLAG_ACTIVITY_CLEAR_TOP`; the existing widget must be refreshed or removed and re-added after installation so its stored PendingIntent is regenerated.

## 2026-11-06 — Dedicated voice surface and phase timing

- Decision: keep the main chat activity out of the widget/Quick Settings voice loop. Use an opaque `VoiceSessionActivity` with an animated phase bubble for Listening, Thinking, Speaking, and Error states.
- Why: a transparent voice activity exposed whichever Sancho screen was underneath and gave no feedback while FUTO, Gemma, or TextToSpeech was working.
- Detail: initialize TextToSpeech only after FUTO returns a valid transcript so provider startup cannot finish the activity during recognition. Record recognition, model load, model TTFT, model generation, TTS, and total durations as numeric Logcat fields retrieved by `make voice-log`; never log voice or response text.

## 2026-11-07 — Voice conversation controls and replaceable STT

- Decision: keep the dedicated voice activity open after TTS completes and expose Restart, Exit-to-home, and active-session Cancel controls. Exit removes the Sancho task instead of returning to `MainActivity`.
- Decision: keep FUTO as the initial STT provider, but define an adapter boundary for future embedded providers. The first candidates for a same-prompt Pixel benchmark are Sherpa-ONNX, Vosk, Moonshine, and whisper.cpp.
- Why: a one-shot flow forced Quick Settings users back into the chat, and changing STT providers should not require changing the Gemma or TTS orchestration. Provider selection must be based on measured latency, accuracy, memory, battery, cancellation, and GrapheneOS privacy behavior.

## 2026-11-07 — Embedded Moonshine speech input

- Decision: replace the FUTO recognition-intent handoff with Moonshine's embedded Android `MicTranscriber` for the next voice test.
- Why: the voice entry point should not launch FUTO or expose its recognition UI/icon, and an in-process provider lets Sancho show listening state and measure recognition directly.
- Detail: Sancho requests `RECORD_AUDIO`, imports a local Moonshine tiny-streaming model directory, and calls `modelsFrom(...)`. The Moonshine downloader's merged network, boot, wake-lock, and foreground-service permissions are explicitly removed. No speech model is downloaded automatically; the user selects the local model folder in Voice settings.

## 2026-11-07 — Selectable Moonshine streaming size

- Decision: keep Tiny Streaming as the default Moonshine model and add Small Streaming as an optional Voice setting for controlled accuracy testing.
- Why: Tiny is currently responsive on the Pixel 9a, while Small has a published accuracy advantage but a higher memory and compute cost. The app must preserve the known-good Tiny model while testing Small.
- Detail: Tiny and Small are imported into separate private cache directories (`stt-en-2` and `stt-en-4`); changing the setting selects the architecture for the next voice session and never downloads a model automatically.

## 2026-11-08 — Voice sphere and deterministic stop command

- Decision: make the central voice sphere the primary interaction control. While Listening, Thinking, or Speaking it stops the session; from Ready or Error it starts another prompt. Keep Exit as the secondary way to leave the voice surface.
- Why: a single spatial control is easier to discover and removes the awkward Restart/Cancel wording from a conversational surface.
- Detail: exact short phrases such as `stop`, `cancel`, `stop Sancho`, `never mind`, and `be quiet` cancel during the recognition-to-Gemma handoff. Longer phrases are treated as normal prompts; spoken interruption during Gemma generation or TTS remains a later opt-in feature because it requires concurrent microphone capture and echo handling.

## 2026-11-08 — AgentFlow comparison boundary

- Decision: evaluate Moonshine AgentFlow as an opt-in bounded-conversation runtime, not as a replacement for Gemma's open-ended voice reasoning.
- Why: AgentFlow uses embedding-based phrase matching and scripted `ask`/`confirm`/`choose` flows, so it can be faster and more deterministic for known tasks but cannot fairly answer arbitrary questions.
- Detail: the experiment will hold Moonshine STT and Android TextToSpeech constant, use a mock note-confirmation flow, require an explicitly imported local embedding model, and log only numeric latency/resource/task-success metrics. It must not alter the default widget or Quick Settings Gemma path until device evidence exists.

## 2026-11-06 — Bounded chat stream rendering

- Decision: coalesce fast model chunks into chat UI updates at a 50 ms or 96-character boundary, show plain assistant text while generation is active, and parse Markdown/math once the response is complete.
- Why: long responses were doing repeated full-string copying and Markdown/equation parsing on every model chunk, adding avoidable Compose work on top of model latency.
- Detail: TTFT remains measured from the first non-empty model chunk; each published batch yields to the Compose scheduler so available text can reach the screen before the next batch is consumed. Cancellation and errors preserve the partial response, mark it complete for rendering, and do not change the local model stream or harness behavior.

## 2026-11-06 — Optional chat performance metrics

- Decision: add a persistent Chat settings switch for response metrics and show TTFT, total response time, and Markdown-block parse time above completed assistant responses when enabled.
- Why: the user needs to distinguish model first-response latency from total generation and client-side Markdown parsing without permanently adding diagnostic text to the conversation.
- Detail: metrics are local UI diagnostics; `Shown` is the first streamed text observed after a Compose frame, `UI` is the visible update count, and Markdown parse time covers block parsing only and excludes Compose layout/drawing. The default is enabled for the current testing phase and can be disabled without changing inference behavior.
