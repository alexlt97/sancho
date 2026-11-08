# V2 transport research

This document records options that are not part of the first V2 vertical slice. They remain research until the tool contract, policy, confirmation, and bounded-result model are stable.

## Offline voice decision

The first V2 voice path uses three separate providers:

- [Moonshine Voice](https://github.com/moonshine-ai/moonshine) provides embedded offline speech-to-text through its Android `MicTranscriber`. Sancho imports local Tiny Streaming or Small Streaming model files (frontend, encoder, adapter, key/value decoder, config, and tokenizer) into separate private app-storage directories and calls `modelsFrom(...)`; it does not use Moonshine's optional downloader. Tiny remains the default, while Small is the first accuracy experiment.
- Sancho's existing Gemma 4 E2B text path remains responsible for reasoning. This avoids making live conversation depend on Gemma audio latency.
- Android TextToSpeech provides the spoken response using an installed offline voice. Sancho must check that the selected voice is available without network access.

The widget starts a compact voice session rather than the main chat activity.
Moonshine captures the microphone inside Sancho, so the FUTO recognition surface
and FUTO app icon are no longer part of this path. Android's microphone privacy
indicator remains expected and visible.

Direct Gemma audio remains a later benchmark. Gemma 4 E2B supports audio ASR, but Sancho currently imports only .litertlm models and sends text/image content through LocalLlm. An audio-enabled LiteRT-LM bundle and a compatible Android content API must be verified on the Pixel 9a before this becomes an alternative provider.

The first test uses Moonshine's tiny streaming architecture to prioritize Pixel
latency and memory. The model folder must be prepared before going offline;
Sancho never downloads it automatically. The official Android binding documents
the `MicTranscriber` callbacks and local `modelsFrom` path in the [Moonshine
transcription guide](https://github.com/moonshine-ai/moonshine/blob/main/docs/using/transcription.md).

References: [Moonshine Android binding](https://github.com/moonshine-ai/moonshine/tree/main/language-bindings/android), [Gemma audio understanding](https://ai.google.dev/gemma/docs/capabilities/audio), [Gemma 4 model card](https://ai.google.dev/gemma/docs/core/model_card_4).

## Moonshine AgentFlow comparison experiment

Moonshine `AgentFlow` is a candidate runtime for bounded spoken workflows, not
a direct replacement for Gemma's open-ended reasoning. It uses an embedding
model to match trigger phrases and advances scripted `listenFor`, `ask`,
`confirm`, and `choose` flows. The proposed comparison keeps Moonshine STT and
Android TextToSpeech constant, then compares AgentFlow's scripted runtime with
the existing Gemma Voice/Agent runtime on a mock note-confirmation task. The
full protocol, metrics, offline asset requirements, and decision boundary are
in [agentflow-experiment.md](agentflow-experiment.md).

## Native HTTPS tools — preferred first transport

Small, explicit Android executors can provide `search_web(query)` and `fetch_web_page(url)`. This keeps the model local while only the selected tool uses the network. The executor owns HTTPS enforcement, host policy, redaction, byte/time limits, source labels, and confirmation behavior.

## MCP over HTTPS — later ecosystem option

Sancho could become an MCP client for a trusted home computer, local network, or remote server. Authentication, certificate validation, server and tool allowlists, network failure handling, and bounded results are required before adoption.

Reference: [MCP transport documentation](https://ts.sdk.modelcontextprotocol.io/server).

## Browser handoff — user-controlled research

Sancho may open a search URL or page in Vanadium through an Android browser intent or Custom Tab. This supports login, CAPTCHA, and manual inspection, but it is not an autonomous structured-result tool.

## GrapheneOS Terminal VM — manual environment

The GrapheneOS Terminal is useful for manually experimenting with `curl`, Python, SSH, and other Linux tools. There is no documented stable third-party command API for Sancho, so it is not an executor target yet.

References: [Android Linux development environment](https://source.android.com/docs/core/virtualization/usecases), [GrapheneOS releases](https://grapheneos.org/releases).

## Termux `RUN_COMMAND` — high-risk bridge

Termux can provide OpenSSH, `curl`, Python, and other command-line tools. Any prototype must call one fixed wrapper or an allowlisted command set. It must never pass raw model text to a shell, and it requires explicit Termux policy and permission configuration.

Reference: [Termux RUN_COMMAND](https://github.com/termux/termux-app/wiki/RUN_COMMAND-Intent).

## SSH gateway or embedded SSH

Remote execution could use a trusted gateway or an embedded library such as [cbssh](https://github.com/connectbot/cbssh). Either path requires host-key pinning, encrypted key storage, command IDs/allowlists, timeouts, output limits, and visible confirmation for mutations. [termlib](https://github.com/connectbot/termlib) is a possible future native terminal surface.

## Deferred tool candidates

- Local: `read_selected_file(uri)`, `calculate(expression)`, `create_note(title, body)`, `share_text(text)`, and `open_url(url)`.
- Web: constrained page extraction, weather, and public transport APIs.
- Remote: `ssh_read_file`, `sftp_upload`, `list_remote_hosts`, and fixed `terminal_run` wrappers.

Each candidate must first receive a schema, risk classification, executor, confirmation rule, test plan, and privacy decision. The existence of a transport or library is not approval to add it.
