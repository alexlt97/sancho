# Moonshine AgentFlow experiment

## Purpose

Measure whether Moonshine `AgentFlow` is a better runtime for bounded spoken
tasks than Sancho's current Moonshine → Gemma → Android TextToSpeech path.
This is an experiment, not a replacement decision for open-ended chat.

AgentFlow is an intent- and flow-driven runtime. It matches spoken phrases with
an embedding model and advances a scripted flow through prompts such as
`ask`, `confirm`, and `choose`. It is therefore comparable with Gemma for a
bounded task, but not for unrestricted questions, explanations, or creative
conversation. The official API also supports `handleUtterance()` for text-fed
flow tests and `speakWith(...)` for routing AgentFlow responses to an external
speech provider. See the [AgentFlow API design](https://github.com/moonshine-ai/moonshine/blob/main/docs/design/api-comparison.md)
and the [Moonshine Voice skill](https://github.com/moonshine-ai/moonshine/blob/main/.agents/skills/moonshine-voice/SKILL.md).

## Compared paths

| Path | Speech input | Conversation | Speech output |
| --- | --- | --- | --- |
| Sancho baseline | Same imported Moonshine Tiny or Small Streaming model | Local Gemma Voice/Agent harness | Android TextToSpeech |
| AgentFlow experiment | Same imported Moonshine model where the API permits injection | Scripted `listenFor`/`ask`/`confirm`/`choose` flow plus Moonshine phrase matching | Android TextToSpeech through `speakWith(...)` |

Using the same STT model and Android TTS isolates the conversation-runtime
difference. AgentFlow additionally requires its local phrase-matching
embedding model. The embedding model must be imported explicitly for an offline
run; it must not be downloaded during the test.

## Experiment scenario

Use a mock, read-only note task so neither path performs a phone action:

1. Trigger: “start a Sancho note”.
2. Agent asks: “What should I remember?”
3. User gives a note containing a name, number, and technical term.
4. Agent repeats the captured note and asks for confirmation.
5. User confirms or rejects it.
6. Agent reports a mock success or restarts the flow.

Example utterance sets should vary naturally while preserving the same intent:

| Stage | Variants to test |
| --- | --- |
| Trigger | “start a Sancho note”, “begin a note”, “I want to remember something” |
| Note | “Remember Pixel 9a, Android 16, and Moonshine AgentFlow.” |
| Yes | “yes”, “correct”, “go ahead”, “that’s right” |
| No | “no”, “try again”, “that is wrong” |
| Control | “cancel”, “stop”, “start over” |

Run a separate open-ended set—such as “Explain why the sky is blue”—only as a
qualitative boundary check. AgentFlow should decline or report that no matching
flow exists; it should not be scored as a failure for not behaving like Gemma.

## Test stages

### Stage A — deterministic flow test

Use AgentFlow with `microphone(false)`, `speech(false)`, and
`handleUtterance()` to drive the exact utterance sequences. This tests flow
state, semantic matching, retries, confirmation, cancel, restart, and unknown
input without microphone or TTS variability. It can run as an Android/JVM
adapter test once the local embedding model boundary is available.

Required assertions:

- the correct trigger starts the intended flow;
- `ask` returns the next utterance to the flow;
- `confirm` accepts positive and negative paraphrases;
- `cancel` ends the flow without a success event;
- `start over` resets the flow state;
- unmatched input causes a bounded retry and never runs a side effect;
- the flow has a maximum turn/retry count.

### Stage B — same-device live voice test

Run both paths on the Pixel 9a AI profile in airplane mode. Use the same
Moonshine model, Android TTS voice, scenario order, and spoken utterance set.
Use a fresh process for cold measurements, then repeat each scenario five times
warm. Alternate the path order between runs to reduce thermal and cache bias.

The experiment must not retain microphone recordings or log transcript text.
Each run should log only numeric fields:

```text
SanchoVoiceExperiment path=agentflow scenario=note outcome=success \
stt_first_partial_ms=... stt_final_ms=... runtime_load_ms=... \
runtime_first_action_ms=... first_speech_ms=... total_ms=... \
turns=... retries=... memory_peak_mb=...
```

For the Gemma path, `runtime_first_action_ms` is model TTFT. For AgentFlow it
is the time from final transcript to the first flow action or spoken response.
The fields must be interpreted separately because AgentFlow does not generate
an answer token by token.

## Metrics and decision rule

Measure:

- cold and warm runtime load time;
- Moonshine first partial and final transcript time;
- time from final transcript to first response speech;
- total turn time and total scenario time;
- task completion rate and incorrect-flow rate;
- confirmation accuracy, retry count, and cancellation behavior;
- native heap/RSS, CPU time, storage size, and battery impact;
- offline behavior and whether any network request is attempted;
- spoken-response usefulness, scored separately by manual review.

AgentFlow is a good fit for Sancho if it provides materially lower and more
predictable response latency, high bounded-task completion, and acceptable
memory cost. Gemma remains the required path for open-ended questions,
summaries, explanations, and conversations that are not represented by a
predefined flow. A single total-time average must not decide between them.

## Implementation boundary

The first implementation should be an opt-in experiment behind a separate
voice-runtime selector. It must not change the default Gemma path or grant
AgentFlow device tools. Before live testing, Sancho needs:

1. an explicit importer and private cache key for the AgentFlow embedding model;
2. an `AgentFlowVoiceRuntime` adapter with deterministic callbacks and close;
3. Android tests for the scripted flow using `handleUtterance()`;
4. a device benchmark command that records the numeric schema above;
5. a manual Pixel test for microphone permission, cancellation, TTS, and
   airplane-mode behavior.

Until those assets and boundaries exist, AgentFlow should remain a research
adapter and should not be selected by the normal widget or Quick Settings tile.
