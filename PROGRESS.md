# Progress

## Current State

- Last commit: none (initial repository bootstrap)
- Verification status: `make check`, instrumentation compilation, Android equation-rendering regression coverage, native image inference, responsive Markdown table/math rendering, complex, Fourier, bold-symbol, and matrix equation normalization, and time-to-first-token presentation pass; the current Pixel 9a Compose UI run is blocked before assertions by Espresso reflection of the unavailable `android.hardware.input.InputManager.getInstance()` API
- Product status: text chat and native Gemma image requests are wired; assistant Markdown, inline math, time-to-first-token presentation, image previews before sending and in submitted user messages, subtle role-colored message surfaces, explicit edge-to-edge keyboard-safe composer layout, free scrolling during generation, cancellation for long generations, and a jump-to-latest control are included; new message rows are revealed automatically without forcing the list position during streaming; model status and import now live behind a top-right settings screen, with a compact ready/loading/generating/unavailable status LED beside the settings icon; the chat composer now has compact send/stop icon controls, a dark galaxy theme, safe system-bar spacing, and the Sancho launcher icon; local issue reports support editing, deletion, screenshots, and `sancho_report.zip` export with post-export clearing; a dependency-free PC analyzer produces priority and implementation-risk proposals; a conservative one-command local emulator preview uses a deterministic streaming mock model, while phone builds use LiteRT-LM; GPU inference remains verified through a separate benchmark path
- Target: Pixel 9a running GrapheneOS

## In Progress

- Iterate on chat presentation using the local emulator preview and its mock responses.

## Next Steps

1. Add structured tool calls and confirmation gates for agent actions.
2. Add an explicit backend policy or user setting if GPU startup latency is acceptable.

## Performance evidence

Benchmark history and measurement boundaries live in [docs/performance.md](docs/performance.md). CPU remains the normal app backend because its startup is more predictable; GPU is still an explicit benchmark path.

## Blockers

- Focused Compose device tests currently fail before assertions on GrapheneOS because the installed Espresso version reflectively requires `android.hardware.input.InputManager.getInstance()`. App launch tests and the Android-only Markdown renderer regression test pass; the latest renderer APK still needs device installation after the phone reconnects.
