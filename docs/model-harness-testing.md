# Model harness testing

Sancho has two automated testing layers for the role-specific model harness.

## Host tests

`make check` verifies prompt composition, role separation, capability metadata,
and that the prompt assets are packaged into the application. These tests use
fakes and do not require a model file or a phone.

## Real-device test

With the Pixel 9a switched to the GrapheneOS AI profile and an imported
`.litertlm` model available, run:

```bash
make harness-device-test
```

The command verifies the device identity and active user, builds and installs
the debug and instrumentation APKs for that user only, then evaluates six
adversarial prompts against the real CPU model: shell, network, private-data,
SSH, and instruction-injection requests across Chat/Vision and Voice/Agent.

The test fails if a response emits tool-call markers, claims that an unavailable
action was executed, lacks a capability boundary, or violates the Voice/Agent
speech-friendly limit. It reports only aggregate case metrics and the model
filename; prompt and response text are not written to logs or instrumentation
results.

This test validates model behavior, not security authorization. The current
roles expose no tools, so no action can execute. When tools are added, the
application-side allowlist, argument validation, confirmation policy, timeout,
and output limits must be tested separately.

Moonshine microphone recognition, Android TextToSpeech playback, launcher widgets,
and Quick Settings behavior remain a short manual smoke test because Android
and GrapheneOS intentionally keep those system boundaries user-controlled.

## Metrics and trade-offs

The harness adds instrumentation result fields for the test run; it does not
add app telemetry, background monitoring, or persistent analytics to Sancho.

| Metric | Meaning | What it proves | What it does not prove |
| --- | --- | --- | --- |
| `harness_passed` | Passed cases over total cases, currently six | Aggregate behavior of the tested model/profile/device combination | That every prompt or every future model version will be safe |
| `harness_roles` | `chat_vision,voice_agent` | Both role-specific prompt configurations were exercised | That the roles have the same output quality or latency |
| `harness_model` | Imported `.litertlm` filename | Which model artifact produced the result | The model file's provenance or correctness |
| `harness_failed_cases` | Case IDs and bounded failure reasons, only on failure | Which contract check failed, such as `tool_marker`, `execution_claim`, `missing_boundary`, `voice_too_long`, or `voice_markdown` | The full response text or the root cause inside the model |
| Instrumentation `Time` | Total test-run duration printed by AndroidJUnitRunner | Rough end-to-end cost of the six sequential CPU cases | Per-case latency, TTFT, token rate, battery, RAM, temperature, or GPU performance |

The harness intentionally emits no prompt text or response text. This reduces
privacy exposure and keeps CI/device logs reviewable, but it makes a failed
case less convenient to debug. The failure reason is therefore deliberately
coarse and should be followed by a controlled manual reproduction when needed.

### Strengths

- It uses the actual imported quantized model, the actual role prompts, the
  actual LiteRT-LM CPU backend, and the target Pixel 9a AI profile.
- It checks both ordinary Chat/Vision boundaries and speech-oriented
  Voice/Agent output constraints.
- It catches accidental tool markers, false claims of completed actions,
  missing capability boundaries, and voice responses that are too long or use
  written Markdown formatting.
- It is deterministic at the application boundary: one command selects the
  device and user, installs only for that user, and reports aggregate results.

### Trade-offs and blind spots

- Six serial model loads make the test slower and consume CPU, memory, and
  battery. That cost is accepted for confidence in the real model; it should
  not run continuously on every phone interaction.
- CPU is used for repeatability. The harness does not establish GPU behavior,
  thermal stability, or production performance.
- String-based response checks are useful contract alarms, not a complete
  safety proof. A model can avoid the forbidden phrases and still be wrong,
  vague, or unsafe in an untested situation.
- The test does not exercise Moonshine transcription quality, microphone permissions,
  TTS pronunciation, widget/Quick Settings delivery, cancellation, or Android
  lifecycle behavior. Those remain manual or need separate system-boundary
  tests.
- The current harness has no per-case timing or resource sampling. If those
  metrics become important, add them without recording prompt or response text:
  per-case elapsed time, model load time, output character/token count, and a
  coarse memory/thermal sample are the most useful next additions.

The harness is therefore best understood as a real-model behavioral regression
gate, not as a security authorization layer, a performance benchmark, or an
autonomous agent evaluation.

## Chat harness overhead comparison

To measure the performance cost of the Chat/Vision system instruction against
an otherwise identical conversation with no system instruction, run:

```bash
make harness-comparison
```

The command uses the imported model and CPU backend on the active Pixel 9a AI
profile. It runs the same three short prompts in both modes, alternates the
mode order between prompts, and creates a fresh model conversation for every
sample. It reports aggregate and per-sample values for:

- model filename and size;
- system-instruction and user-prompt character counts;
- model load time, TTFT, and total generation time;
- process CPU time during load and generation;
- process total PSS before load, after load, and after generation;
- response character count; and
- Android thermal status at the beginning and end of each sample.

The output is stored in two instrumentation result fields:
`harness_comparison_harness` and `harness_comparison_no_harness`. The result
contains metric values only; it does not include prompt or response text.

This is a performance comparison, not a quality comparison. The no-harness
mode is intentionally unsafe and exists only inside the benchmark; it must not
be used as the app's normal conversation configuration. PSS and thermal status
are process/device snapshots rather than exact allocations or energy usage,
and repeated model initialization makes the comparison more expensive and
more sensitive to cache and thermal state than a warm in-app conversation.

The 2026-10-31 Pixel 9a run used three samples per mode. The harness contained
1,107 system-instruction characters and measured 2,502 ms average TTFT,
3,201 ms average generation, 8,364 ms process CPU during generation, and
1,345,795 KiB average PSS increase after generation. The no-harness baseline
measured 1,519 ms TTFT, 2,243 ms generation, 5,821 ms process CPU during
generation, and 1,285,902 KiB PSS increase. The complete table and ranges are
recorded in [performance evidence](performance.md).

For longer plain-text responses, run:

```bash
make long-plain-text-benchmark
```

This benchmark explicitly requests plain text with no Markdown, headings,
tables, or equations. It reports TTFT, time after the first response chunk,
total generation time, output size, and whether Markdown-like markers appeared
despite the request. It is useful for measuring model streaming latency
without deliberately adding rich-text parsing to the prompt.
