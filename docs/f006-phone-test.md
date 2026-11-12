# F006 Pixel 9a security and observability test

This is the long manual acceptance run for V2 F006. It is designed for the
AI user on a Pixel 9a running GrapheneOS. It tests local chat, approval-gated
web search, source opening, settings, failure handling, metrics retention, and
the existing voice/widget boundary in one repeatable session.

## Before starting

Run from the repository on the development computer:

```sh
make check
make install
make launch
```

On the phone, confirm:

- the active user is the AI profile;
- the Gemma model is imported and ready;
- Moonshine Tiny is imported if voice tests will be run;
- Vanadium is installed in the AI profile;
- the GrapheneOS Network permission for Sancho is enabled only for the web-tool tests;
- Chat Settings has `Show response metrics`, `Show web sources`, `Allow web search`, and `Store web-search metrics` enabled;
- the intended provider is selected. Brave requires a valid API key and SearXNG requires a valid HTTPS endpoint.

Do not enable `Allow multiple searches` until test W-08. Keep `Show web-search
query` disabled for the first pass, then repeat the query-edit test later.

Before the first prompt, use **Clear stored web metrics**. Do not clear reports
until screenshots and notes have been exported.

## Evidence to capture

For each failed case, record the test ID, exact user action, visible status,
whether a source card appeared, and whether the model produced a response. Do
not paste API keys, private prompts, full URLs, or model responses into a
diagnostic log. Screenshots may contain private chat content and should be
attached only to the local issue report.

After web-tool cases, retrieve the redacted metrics file:

```sh
make tool-metrics OUT=/tmp/sancho-f006-tool-metrics.jsonl
```

The file must contain only provider/status/timing/result-count fields. It must
not contain a query, URL, prompt, response, API key, or search-result text.

Useful additional evidence commands:

```sh
make screenshot OUT=/tmp/sancho-f006-final.png
make voice-log
```

## Test matrix

### Local and permission boundaries

| ID | Action | Expected result |
| --- | --- | --- |
| L-01 | Ask a normal local question with web search allowed but no current-information request. | No approval card, no web status, local response appears. |
| L-02 | Disable `Allow web search`; ask a question that would normally request current information. | No external request is performed; the model receives a denial/result and Sancho remains usable locally. |
| L-03 | Re-enable `Allow web search`; turn off the GrapheneOS Network permission; ask for current information. | Approval still appears, then the search fails visibly without crashing or hanging. |
| L-04 | Restore the Network permission; send another local question. | Local chat still works after the failed network request. |

### Approval, intent, and retrieval

| ID | Action | Expected result |
| --- | --- | --- |
| W-01 | Ask: `What is the current weather in Rotterdam?` | Sancho asks for approval before searching. No query or source is sent before approval. |
| W-02 | Press Cancel on the approval card. | The card disappears, `Web search cancelled` is shown, no browser/API request occurs, and the model/chat remains usable. |
| W-03 | Repeat W-01 and press Search. | The card clears immediately, `Searching the web…` appears, then results/failure is shown. |
| W-04 | Ask a second current-information question after W-03 completes. | A second user message and a second assistant response are visible; no response is silently lost. |
| W-05 | Enable `Show web-search query`, repeat a current-information question, edit the query, and approve. | The edited query is used; the original query is not shown in exported diagnostics. |
| W-06 | Tap each displayed source title. | Each valid HTTPS source opens in Vanadium. Sancho rejects unsupported/non-HTTPS source URLs without crashing. |
| W-07 | Disable `Show web sources`, complete another search, then re-enable it. | Search still works; only the source panel is hidden while disabled. |
| W-08 | Enable `Allow multiple searches` and ask one prompt requiring two independent current facts. | At most five bounded searches occur; every approval/result state remains understandable. Disable it again afterward. |

### Provider and failure handling

| ID | Action | Expected result |
| --- | --- | --- |
| P-01 | Run the Brave API key test with the known-good key. | A generic success/result-count message appears; no key or response content is displayed. |
| P-02 | Temporarily enter an invalid Brave key and run the test. | A safe 401/403/422/429 or generic failure message appears; no secret is logged. Restore the key. |
| P-03 | Select SearXNG with a blank endpoint or invalid HTTPS endpoint. | The search fails with a bounded configuration/network message; local chat remains available. |
| P-04 | Use the Vanadium provider while Vanadium is unavailable in the AI profile, if safe to test. | Sancho reports the unavailable browser and does not open another application. |
| P-05 | Interrupt network connectivity during an approved retrieval. | The request times out or fails within the documented bound; the UI returns control to the user. |

### Metrics and retention

| ID | Action | Expected result |
| --- | --- | --- |
| M-01 | With response metrics enabled, complete a successful web search. | The assistant metrics include TTFT, total time, and search duration/result count. |
| M-02 | Inspect `/tmp/sancho-f006-tool-metrics.jsonl`. | Records contain provider, status, duration, result count, truncation, and error category only. |
| M-03 | Search for a known query or URL string in the metrics file. | No query, URL, prompt, response text, or API key is present. |
| M-04 | Use **Clear stored web metrics**, then run `make tool-metrics`. | The exported metrics file is empty or contains only newly created events. |
| M-05 | Turn off `Store web-search metrics`, complete a search, and retrieve metrics. | No new persistent event is added; in-chat response metrics remain controlled separately by `Show response metrics`. |
| M-06 | Complete more than 100 approved searches only if practical. | The stored file remains bounded to the most recent 100 events. |

### Voice and device boundary regression

| ID | Action | Expected result |
| --- | --- | --- |
| V-01 | Launch the 1×1 widget in the AI profile. | The dedicated opaque voice surface opens; MainActivity and FUTO UI do not appear. |
| V-02 | Speak a short prompt and wait for the response. | Listening, thinking, speaking, and completion states are understandable; speech remains local. |
| V-03 | Tap the central sphere while Gemma or TTS is active. | The active work stops promptly and no stale speech continues. |
| V-04 | Use the Quick Settings tile for another prompt. | It starts the same voice surface and does not open the text chat. |
| V-05 | Switch to the owner profile or disconnect the device only after finishing AI-profile tests. | No test result is attributed to the wrong GrapheneOS user. |

### Release and harness verification

| ID | Action | Expected result |
| --- | --- | --- |
| R-01 | Run `make safety`. | APK identity is Sancho; only the documented microphone, optional Internet, and AndroidX receiver permissions exist. |
| R-02 | Run `make e2e` if the device test environment is usable. | The launch and focused UI checks should pass through the GrapheneOS-safe UiAutomator harness; report any device-specific failure separately from app behavior. |
| R-03 | Run `make tool-metrics OUT=/tmp/sancho-f006-tool-metrics-final.jsonl`. | The final diagnostic artifact is readable and content-free. |
| R-04 | In Sancho Reports, create a report titled `F006 phone validation`. | Include failed test IDs, expected/actual behavior, screenshots, and the metrics-file path. |
| R-05 | Export reports as `sancho_report.zip`, then run `make reports-analyze FILE=/path/to/sancho_report.zip`. | The report export succeeds and the analyzer produces deterministic priority/risk output. |

## Results template for the report

Copy this into the report description or actual-behavior field:

```text
Build/branch:
Device/profile: Pixel 9a / GrapheneOS / AI
Provider: Brave / SearXNG / Vanadium
Model: Gemma model filename
Moonshine variant: Tiny / Small / not tested

Passed IDs:
Failed IDs:
Blocked IDs:

Highest-risk failure:
First visible symptom:
Expected behavior:
Actual behavior:
Reproduction steps:
Screenshots:
Metrics artifact:
Notes about network permission or profile:
```

The test is complete only when every failed or blocked case has a report entry
or an explicit reason why it was not run. This plan does not authorize adding
new tools or device actions; it validates the existing bounded web-search and
offline voice surfaces.
