# V2 tool contract

Sancho's first tool slice is a typed boundary for future agent actions. The
default browser handoff remains outside Sancho's network stack; the optional
SearXNG mode uses one HTTPS request to a user-configured endpoint, and the
optional Brave API mode uses one authenticated HTTPS request to Brave's LLM
Context endpoint with a locally encrypted user key. Neither route exposes a
shell.

## Current tool

`open_web_search` is a read-only network-intent tool. The model may propose a
query, but Sancho owns the tool name, argument validation, confirmation state,
timeout, output limit, and execution route. The default route is an explicit
Vanadium intent. The optional SearXNG route performs a bounded HTTPS request,
and the optional Brave route calls Brave's LLM Context endpoint. Both routes
normalize up to five results and return source URLs and extracted context as
untrusted reference data to the local model. Completed assistant messages now
show those bounded source links in a separate Sources panel.

Brave requests include explicit JSON, identity encoding, no-cache, and a
Sancho user-agent header. Identity encoding keeps diagnostic API errors
readable on Android without adding a decompression dependency.

The current contract limits a query to 600 characters, requires confirmation,
uses a ten-second execution limit, bounds a result to 20,000 characters, and
allows one tool call and one tool-result turn per run by default. The browser
handoff itself will return no web content; it will only launch the user-approved
Vanadium search URL. The chat surface shows an explicit waiting, searching,
results, failure, or cancellation phase so approval does not leave a stale
confirmation card on screen.

Chat Settings can enable query preview/editing and raise the per-prompt call
limit to five, and can hide or show the source panel below web-grounded
answers. When shown, each HTTPS source opens only in the explicit Vanadium
package. The Brave API route also provides a connection check that sends
only a fixed test query and reports success or a generic failure without
showing the key or response contents. Safe HTTP categories such as invalid
credentials, inactive authorization, quota/rate limiting, and network failure
are distinguished in the settings message. Query preview and multiple-search
are disabled by default; web search remains approval-gated, and persistent tool
metrics are disabled by default. Settings are read when a new prompt starts.

F006 adds an explicit `Allow web search` setting, while confirmation remains
required for every search. Search timing and result count are shown alongside
assistant metrics when response metrics are enabled. A separate opt-in
`Store web-search metrics` setting writes at most 100 app-private JSONL events;
records contain only provider, status, duration, result count, truncation, and
safe error category. Queries, URLs, prompts, response text, snippets, and API
keys are never written. The user can clear the file in Chat Settings, and the
terminal-first `make tool-metrics` command retrieves only that sanitized file.

## Execution boundary

The flow is:

```text
model-shaped RawToolCall
    -> ToolCatalog validates name and exact arguments
    -> ValidatedToolCall enters PENDING confirmation
    -> user approves or denies
    -> ToolExecutionCoordinator applies timeout and output limits
    -> executor returns a bounded ToolExecutionResult
```

Unknown tools, missing or extra arguments, blank queries, control characters,
and oversized queries are rejected before an executor is called. Every returned
result is marked untrusted so later web content cannot be confused with model
instructions. Cancellation is propagated to the caller so the chat or voice
surface can stop work promptly.

## Deliberate non-goals

- No automatic model-generated URL.
- No arbitrary shell, SSH, file, phone, or device action.
- No arbitrary page fetching or redirect following.
- No credentials in SearXNG endpoints.
- No Brave API keys in source code, logs, prompts, or model-visible results.
- No prompt or web-result content in diagnostic logs.
