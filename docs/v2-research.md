# V2 transport research

This document records options that are not part of the first V2 vertical slice. They remain research until the tool contract, policy, confirmation, and bounded-result model are stable.

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
