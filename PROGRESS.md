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

## CPU baseline

Two runs of `make benchmark` on the Pixel 9a AI profile with `gemma-4-E2B-it.litertlm`:

- Run 1: load 907 ms, first response 1,536 ms, generation 1,612 ms.
- Run 2: load 1,058 ms, first response 1,647 ms, generation 1,724 ms.

These are initial debug-build measurements, not a final performance claim.

## GPU comparison

Two runs of `make gpu-benchmark` on the same device and model:

- Cold run: load 53,365 ms, first response 1,252 ms, generation 1,343 ms.
- Cached run: load 5,387 ms, first response 995 ms, generation 1,089 ms.

GPU inference works and is faster after preparation, but CPU remains the normal app default because its startup is substantially more predictable.

## Vision CPU/GPU comparison

Two runs of `make vision-benchmark` on the Pixel 9a AI profile with the same generated 512x512 image and `gemma-4-E2B-it.litertlm`:

- Run 1 CPU: load 931 ms, TTFT 11,467 ms, generation 12,780 ms.
- Run 1 GPU: load 17,201 ms, TTFT 3,320 ms, generation 4,505 ms.
- Run 2 CPU: load 1,542 ms, TTFT 11,447 ms, generation 12,623 ms.
- Run 2 GPU: load 9,119 ms, TTFT 3,779 ms, generation 5,341 ms.

The GPU vision path is about 2.5–2.8x faster for generation and about 3x faster to the first response, while its model startup remains roughly 6–18x slower than CPU. CPU remains the normal app default until a backend policy or user setting is added.

## Blockers

- Focused Compose device tests currently fail before assertions on GrapheneOS because the installed Espresso version reflectively requires `android.hardware.input.InputManager.getInstance()`. App launch tests and the Android-only Markdown renderer regression test pass; the latest renderer APK still needs device installation after the phone reconnects.
