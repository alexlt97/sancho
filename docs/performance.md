# Performance evidence

These are debug-build measurements from the Pixel 9a `AI` profile using the imported `gemma-4-E2B-it.litertlm` model. They are development baselines, not release-performance claims.

## CPU baseline

Two runs of `make benchmark`:

| Run | Load | First response | Generation |
| --- | ---: | ---: | ---: |
| 1 | 907 ms | 1,536 ms | 1,612 ms |
| 2 | 1,058 ms | 1,647 ms | 1,724 ms |

## GPU comparison

Two runs of `make gpu-benchmark`:

| Run | Load | First response | Generation |
| --- | ---: | ---: | ---: |
| Cold | 53,365 ms | 1,252 ms | 1,343 ms |
| Cached | 5,387 ms | 995 ms | 1,089 ms |

GPU generation is faster after preparation, but CPU startup is more predictable. CPU remains the normal app backend until an explicit backend policy or user setting is added.

## Vision CPU/GPU comparison

Two runs of `make vision-benchmark` using the same generated 512x512 image:

| Run | Backend | Load | TTFT | Generation |
| --- | --- | ---: | ---: | ---: |
| 1 | CPU | 931 ms | 11,467 ms | 12,780 ms |
| 1 | GPU | 17,201 ms | 3,320 ms | 4,505 ms |
| 2 | CPU | 1,542 ms | 11,447 ms | 12,623 ms |
| 2 | GPU | 9,119 ms | 3,779 ms | 5,341 ms |

The GPU vision path was about 2.5–2.8x faster for generation and about 3x faster to the first response, while startup remained roughly 6–18x slower than CPU.

## Measurement boundaries

- Results depend on debug builds, device temperature, model state, and cache state.
- The benchmark harness must never download or commit model binaries.
- New performance claims require the command, model, backend, device profile, and run conditions to be recorded here.

## Chat harness versus no-harness comparison

Command: `make harness-comparison` on 2026-10-31, Pixel 9a `AI` profile,
CPU backend, debug build, imported `gemma-4-E2B-it.litertlm` model
(2,588,147,712 bytes). Three short prompts were run in both modes, with a
fresh model conversation for every sample and alternating mode order.

| Metric (average) | Chat harness | No harness | Difference |
| --- | ---: | ---: | ---: |
| System-instruction characters | 1,107 | 0 | +1,107 |
| Prompt characters | 41 | 41 | 0 |
| Model load | 1,072 ms | 1,007 ms | +6.5% |
| TTFT | 2,502 ms | 1,519 ms | +64.7% |
| Total generation | 3,201 ms | 2,243 ms | +42.7% |
| Process CPU during load | 1,578 ms | 1,573 ms | +0.3% |
| Process CPU during generation | 8,364 ms | 5,821 ms | +43.7% |
| PSS delta after load | 420,165 KiB | 404,465 KiB | +3.9% |
| PSS delta after generation | 1,345,795 KiB | 1,285,902 KiB | +4.7% |
| Response characters | 48 | 47 | +2.1% |

Observed ranges were: load 988–1,201 ms versus 1,000–1,015 ms; TTFT
2,348–2,733 ms versus 1,448–1,601 ms; generation 2,504–4,290 ms versus
1,530–3,611 ms; and post-generation PSS delta 1,326,324–1,378,457 KiB
versus 1,284,120–1,288,132 KiB. Thermal status code was `0` before and after
all samples in both modes.

This run suggests that the system instruction has a substantial prompt
prefill cost for this model, especially visible in TTFT, while model loading
and memory overhead change only modestly. It is not a quality or safety result:
the no-harness mode is intentionally unsafe and exists only for measurement.
The sample count is small, model output lengths vary, and PSS is a process-level
snapshot rather than an exact allocation or energy measurement. Repeat the
comparison after model, backend, device-temperature, or prompt changes before
using it as a release claim.

## Long plain-text chat benchmark

Command: `make long-plain-text-benchmark` on 2026-11-06, Pixel 9a `AI`
profile, CPU backend, debug build, imported `gemma-4-E2B-it.litertlm` model
(2,588,147,712 bytes). Three fresh conversations were used. Each prompt
explicitly requested plain text with no Markdown, headings, bullet points,
tables, or equations; no Markdown-like markers appeared in the three outputs.

| Sample | Prompt chars | Load | TTFT | After TTFT | Total generation | Output chars | TTFT share |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 1 | 169 | 1,216 ms | 3,630 ms | 11,735 ms | 15,365 ms | 740 | 23.6% |
| 2 | 218 | 1,122 ms | 3,559 ms | 17,288 ms | 20,847 ms | 1,238 | 17.1% |
| 3 | 231 | 896 ms | 3,342 ms | 24,787 ms | 28,129 ms | 1,647 | 11.9% |
| Average | — | — | 3,510 ms | — | 21,447 ms | 1,208 | 17.5% |

For these longer responses, TTFT is relatively stable at about 3.3–3.6
seconds, while the time after the first response chunk grows with output
length. This indicates that sustained model generation, rather than initial
Markdown rendering, dominates long-response latency. The benchmark bypasses
Compose, so the in-app `Parse` metric should be compared separately when
testing the same responses in the chat UI.

## On-device perceived streaming test

Command: `make launch` followed by a controlled ADB prompt on 2026-11-06,
Pixel 9a `AI` profile, CPU backend, debug build, imported
`gemma-4-E2B-it.litertlm` model. The prompt requested a long plain-text
explanation without Markdown. This test exercised the real Compose chat rather
than the instrumentation benchmark.

| Metric | Result |
| --- | ---: |
| TTFT | 1.69 s |
| First text shown (`Shown`) | 1.72 s |
| UI update count (`UI`) | 558 |
| Total generation | 51.89 s |
| Markdown block parse (`Parse`) | 1 ms |
| Model-to-first-visible-text gap | about 30 ms |

The first response text was visible while the status still showed
`Generating`, and the UI update count increased during generation. The roughly
30 ms gap between TTFT and first displayed text indicates that the current
perceived streaming delay is dominated by model inference, not Compose delivery
or Markdown parsing. The 558 updates over 51.89 seconds are still a useful
optimization target: future batching or adaptive update intervals should be
tested against this baseline while preserving the low first-display gap.

## Voice timing instrumentation

The voice path now records `recognition_first_partial_ms`, `recognition_ms`, `model_load_ms`, `model_ttft_ms`,
`model_generation_ms`, `tts_ms`, and `total_ms` without logging transcript or
response text. Run `make voice-log` after a completed voice request to retrieve
the numeric record. Pixel 9a verification reached the FUTO surface and
confirmed cancellation returns to the launcher context without exposing the
main chat activity; a complete spoken FUTO → Gemma → TextToSpeech timing sample
remains pending manual speech input.

### First completed voice run

Command: `make voice-log` after one real FUTO → Gemma → TextToSpeech request on
2026-11-07, Pixel 9a `AI` profile, CPU backend, debug build. The log contains
timing fields only; no transcript or response text was recorded.

| Metric | Result |
| --- | ---: |
| Recognition/FUTO | 5,303 ms |
| Cold model load | 1,587 ms |
| Model TTFT | 4,318 ms |
| Model generation | 5,323 ms |
| TTS playback | 3,193 ms |
| Total session | 15,491 ms |

This first sample is cold-started. Recognition includes the time spent
speaking and FUTO endpoint detection. Model TTFT is the largest model-side
delay in this run; repeat the same prompt to measure the warm-runtime case,
where model load should approach zero.

### Warm voice runs

The next three retained `SanchoVoice` records were:

| Run | Recognition | Model load | Model TTFT | Model generation | TTS | Total |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| 2 | 4,967 ms | 993 ms | 2,699 ms | 3,396 ms | 2,394 ms | 11,813 ms |
| 3 | 8,534 ms | 0 ms | 822 ms | 3,220 ms | 8,833 ms | 20,593 ms |
| 4 | 5,815 ms | 0 ms | 871 ms | 1,718 ms | 3,604 ms | 11,142 ms |

Runs 3 and 4 confirm the warm runtime removes model loading and reduces TTFT
from the cold `4,318 ms` to `822–871 ms`. Their total times differ mainly in
recognition duration and TTS playback length, so total voice time should not be
used to compare model speed unless the spoken prompt and response length are
controlled. `make voice-log` shows retained records; the newest record is the
last `outcome=complete` line.
### Voice conclusion and Moonshine comparison plan

The voice bottleneck is not one single component. Cold Gemma loading is visible
once, but warm runs show that recognition/endpointing and TTS playback are
usually larger than Gemma inference. The warm model TTFT of roughly 0.8–0.9 s
is already substantially better than the cold 4.3 s. A fair STT comparison must
hold the spoken prompt, pause length, language, model state, and output text
constant; otherwise recognition and speech duration dominate the total.

This build replaces the FUTO intent with embedded Moonshine tiny-streaming
STT. It adds `RECORD_AUDIO`, imports the Moonshine tiny-streaming model files through
Voice settings, and uses `modelsFrom(...)` so no network permission or automatic
model download is needed. The Pixel test records the same numeric fields, plus
time to first partial transcript and time to final transcript. The first device
baseline is recorded below; it is not yet a controlled latency or accuracy
comparison because the spoken prompts and response lengths were not matched.

### First Moonshine device baseline

`make voice-log` after importing the tiny-streaming model into the Pixel 9a AI
profile produced these numeric records. The final row was stopped before TTS
completed. No transcript or response text was logged.

| Run | First partial | Final recognition | Model load | Model TTFT | Model generation | TTS | Total | Outcome |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| 1 | 1,522 ms | 2,070 ms | 884 ms | 4,051 ms | 4,692 ms | 2,187 ms | 9,888 ms | complete |
| 2 | 2,009 ms | 3,585 ms | 0 ms | 822 ms | 1,613 ms | 3,735 ms | 8,939 ms | complete |
| 3 | 1,409 ms | 5,052 ms | 0 ms | 830 ms | 2,139 ms | 4,633 ms | 11,828 ms | complete |
| 4 | 1,423 ms | 2,994 ms | 0 ms | 815 ms | 2,708 ms | 6,115 ms | 11,820 ms | complete |
| 5 | 1,946 ms | 4,589 ms | 0 ms | 804 ms | 2,306 ms | 6,166 ms | 13,064 ms | complete |
| 6 | 1,390 ms | 2,964 ms | 0 ms | 823 ms | 1,329 ms | 2,200 ms | 6,497 ms | complete |
| 7 | 6,618 ms | 8,266 ms | 0 ms | 817 ms | 1,784 ms | — | 12,110 ms | stopped |

The warm Moonshine path is already producing visible partial text in roughly
1.4–2.0 seconds and final recognition in roughly 3.0–5.1 seconds. Gemma's warm
TTFT remains stable around 0.8 seconds, so the main remaining voice variables
are speech endpointing and TTS duration rather than response rendering.
