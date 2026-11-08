# Progress

## Current State

- Last commit: none (initial repository bootstrap)
- Verification status: `make check`, clean-check, instrumentation compilation, and the guarded Pixel 9a real-model harness pass; Android equation-rendering regression coverage, native image inference, responsive Markdown table/math rendering, complex, Fourier, bold-symbol, and matrix equation normalization, and time-to-first-token presentation pass; focused Espresso Compose assertions remain blocked by GrapheneOS compatibility with the unavailable `android.hardware.input.InputManager.getInstance()` API, while a controlled ADB UI run passed and recorded first-visible-text timing
- Product status: text chat and native Gemma image requests are wired; assistant Markdown, inline math, time-to-first-token presentation, image previews before sending and in submitted user messages, subtle role-colored message surfaces, explicit edge-to-edge keyboard-safe composer layout, free scrolling during generation, cancellation for long generations, and a jump-to-latest control are included; new message rows are revealed automatically without forcing the list position during streaming; the top-right Settings screen now has concise navigation rows for Models, Voice settings, Reports, and Chat settings, with each section opening its own detail page; the chat composer now has compact send/stop icon controls, a dark galaxy theme, safe system-bar spacing, and the Sancho launcher icon; local issue reports support editing, deletion, screenshots, and `sancho_report.zip` export with post-export clearing; a dependency-free PC analyzer produces priority and implementation-risk proposals; a conservative one-command local emulator preview uses a deterministic streaming mock model, while phone builds use LiteRT-LM; GPU inference remains verified through a separate benchmark path
- Target: Pixel 9a running GrapheneOS

## In Progress

- Iterate on chat presentation using the local emulator preview and its mock responses.

- V2 planning: the first vertical slice is offline voice conversation using embedded Moonshine → local Gemma text inference → Android TextToSpeech, launched from a widget without opening the main chat screen.
- F009 implementation: the 1×1 Sancho widget and Quick Settings tile launch a dedicated opaque voice activity, Moonshine captures a local microphone transcript, a warm model-keyed local Gemma runtime generates a response, and Android TextToSpeech speaks it; the voice surface shows an animated Listening/Thinking/Speaking/Error sphere, uses the sphere to cancel active sessions or start another prompt when Ready/Error, recognizes short spoken stop commands before Gemma, and keeps Exit-to-home as a secondary action; both entry points use the monochrome Sancho mark; the Pixel 9a entry, cancellation path, model import, and complete timing path are manually validated, while recognition quality and a controlled FUTO comparison remain pending.
- Role-specific model harness: versioned common, Chat/Vision, and Voice/Agent prompt assets are loaded into separate LiteRT-LM conversation configurations; Chat/Vision supports text/image capability metadata, Voice/Agent is speech-friendly and tool-free, and deterministic profile tests pass.
- Automated device harness: `make harness-device-test` prepares the AI-profile APK/test installation and evaluates six adversarial real-model cases without logging prompt or response text; the latest Pixel 9a run passed 6/6; Moonshine/TTS/widget/Quick Settings system-boundary checks remain manual.
- Harness performance comparison: `make harness-comparison` now measures Chat/Vision with and without its system instruction using identical CPU samples; the latest three-sample run is recorded in `docs/performance.md` and shows the harness cost most clearly in TTFT and generation CPU time.
- Device visual debugging: `make screenshot` captures the active Pixel 9a AI-profile screen through the bundled ADB to a local PNG for visual review without changing device state.
- Chat stream optimization: assistant chunks are coalesced into bounded UI updates, each published batch yields to Compose so available text reaches the screen promptly, streaming responses use plain text until completion, and completed Markdown/math parsing is memoized to reduce long-response recomposition cost.
- Chat metrics setting: Chat settings now persistently controls TTFT, total response time, and Markdown parse-time display for assistant responses.
- Perceived streaming metrics: enabled chat metrics now also record first text shown after a Compose frame and the number of visible streaming updates, separating model TTFT from screen delivery.
- Long plain-text benchmark: three fresh Pixel 9a CPU conversations measured 3.33–3.38 s TTFT and 14.6–28.0 s total generation for 740–1,647 character responses without Markdown markers; sustained model generation dominates the long-response delay.
- Perceived streaming device run: a real Compose response measured 1.69 s TTFT, 1.72 s first text shown, 558 visible updates, 51.89 s total generation, and 1 ms Markdown block parsing; the approximately 30 ms model-to-screen gap means inference, not rendering, is the current main latency target.
- Voice entry-point repair: widget and Quick Settings now share an explicit `VoiceSessionActivity` PendingIntent; the tile uses `Tile.setActivityLaunchForClick` and both paths use a new-task voice intent to avoid falling through to `MainActivity`; the voice activity is opaque so the main chat is not exposed behind the recognizer.
- Voice latency instrumentation: the voice path records recognition, cold model load, model TTFT, model generation, TTS, and total durations without logging transcript or response text; `make voice-log` retrieves only the numeric timing lines.
- Voice timing baseline: the first Pixel 9a AI-profile run measured 5.30 s recognition, 1.59 s cold model load, 4.32 s model TTFT, 5.32 s model generation, 3.19 s TTS, and 15.49 s total; subsequent warm runs showed 0 ms model load and 0.82–0.87 s TTFT, with total time dominated by prompt/response speech duration.
- Embedded STT integration: Moonshine `MicTranscriber` is wired behind a small Sancho speech boundary, with a Voice settings importer for Tiny or Small Streaming model folders and APK permission checks that retain only `RECORD_AUDIO`; Sherpa-ONNX, Vosk, and whisper.cpp remain comparison candidates.
- Moonshine model selection: Tiny Streaming remains the default and is stored under `stt-en-2`; Small Streaming is an experimental selectable model stored under `stt-en-4`, so both can be imported without overwriting each other. The voice log records first partial transcript time separately from final recognition time; recognition quality and a controlled model comparison remain pending.
- Moonshine device baseline: seven AI-profile log records were captured after model import; six completed and one stopped before TTS, with warm first-partial recognition at 1.39–2.01 s, final recognition at 2.96–5.05 s, warm Gemma TTFT at 0.80–0.83 s, and total completed sessions at 6.50–13.06 s. No transcript or response text was logged.
- AgentFlow experiment research: AgentFlow is suitable for bounded scripted conversations rather than replacing Gemma for open-ended reasoning. The comparison protocol is documented in `docs/agentflow-experiment.md`; implementation remains deferred until the local embedding-model importer, runtime adapter, and numeric device benchmark are added.
- Host device design preview: `make voice-preview` now provides a Pixel-shaped mock of Chat, Settings, Reports, the widget/Quick Settings entry points, Moonshine/Gemma/TTS phases, speed changes, and Stop interaction without an emulator or phone.

## Next Steps

1. Add structured tool calls and confirmation gates for agent actions.
2. Add an explicit backend policy or user setting if GPU startup latency is acceptable.
3. Import Small Streaming on the Pixel 9a AI profile and measure recognition latency and quality against Tiny and the recorded FUTO baseline using repeated, matched prompts.
4. Implement F008 voice session orchestration and F009 widget/device verification.
5. Add the proposed notification-based Stop action for long model responses and TTS playback.

## Performance evidence

Benchmark history and measurement boundaries live in [docs/performance.md](docs/performance.md). CPU remains the normal app backend because its startup is more predictable; GPU is still an explicit benchmark path.

## Blockers

- Focused Compose device tests currently fail before assertions on GrapheneOS because the installed Espresso version reflectively requires `android.hardware.input.InputManager.getInstance()`. App launch tests and the Android-only Markdown renderer regression test pass; the latest renderer APK still needs device installation after the phone reconnects.
