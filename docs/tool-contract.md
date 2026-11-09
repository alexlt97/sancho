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
untrusted reference data to the local model.

Brave requests include explicit JSON, identity encoding, no-cache, and a
Sancho user-agent header. Identity encoding keeps diagnostic API errors
readable on Android without adding a decompression dependency.

The current contract limits a query to 600 characters, requires confirmation,
uses a ten-second execution limit, bounds a result to 20,000 characters, and
allows one tool call and one tool-result turn per run by default. The browser
handoff itself will return no web content; it will only launch the user-approved
Vanadium search URL.

Chat Settings can enable query preview/editing and raise the per-prompt call
limit to five. The Brave API route also provides a connection check that sends
only a fixed test query and reports success or a generic failure without
showing the key or response contents. Safe HTTP categories such as invalid
credentials, inactive authorization, quota/rate limiting, and network failure
are distinguished in the settings message. Both settings are disabled by
default and are read when a new prompt starts.

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
