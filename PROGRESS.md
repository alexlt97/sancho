# Progress

## Current State

- Last commit: none (initial repository bootstrap)
- Verification status: `make check` and `make e2e` pass; native image inference, Markdown response rendering, and response timing are verified on the Pixel 9a AI profile
- Product status: text chat and native Gemma image requests are wired; assistant Markdown, inline math, and response-time presentation are included; the chat composer now has compact icon controls, a dark galaxy theme, and the Sancho launcher icon; a conservative one-command local emulator preview uses a deterministic streaming mock model, while phone builds use LiteRT-LM; GPU inference remains verified through a separate benchmark path
- Target: Pixel 9a running GrapheneOS

## In Progress

- Iterate on chat presentation using the local emulator preview and its mock responses.

## Next Steps

1. Add cancellation for long generations and continue improving chat presentation.
2. Add structured tool calls and confirmation gates for agent actions.
3. Add an explicit backend policy or user setting if GPU startup latency is acceptable.

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

## Blockers

- No active blocker. The current device check is intentionally limited to the authorized Pixel 9a GrapheneOS `AI` profile.
