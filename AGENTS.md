# AGENTS.md — Sancho development contract

## Project goal

This repository is an offline-first Android chat application for a Pixel 9a running GrapheneOS. It uses Kotlin, Jetpack Compose, LiteRT-LM, and Gemma 4 E2B.

V1 is reliable local multi-turn chat. V2 tool calling is planned in [docs/v2.md](docs/v2.md); V1 does not execute model-requested tools.

## Target and operating assumptions

- Primary device: Pixel 9a running GrapheneOS.
- Inference is local after model installation; normal chat must work without network access.
- The app must not require Google Play Services or Google account access unless a later decision explicitly changes this.
- Model binaries are user-provided or installed through an explicit setup flow; they are never downloaded automatically during tests.

Why: the first milestone prioritizes privacy, local inference, and user control before granting device authority.

## Architecture boundaries

- Why: these boundaries keep the model replaceable and the chat domain testable on a host machine.
- Kotlin is the primary language.
- Use Jetpack Compose for UI and ViewModel + StateFlow for UI state.
- Keep business logic independent of Android UI classes.
- Access LiteRT-LM exclusively through a `LocalLlm` abstraction.
- UI may depend on domain interfaces; domain code must not depend on Android UI classes.
- Model integration must not leak into composables.
- `make check-arch` enforces these boundaries when source code exists.
- Layer 3 (device/e2e verification) is required for changes crossing UI, domain, storage, or model boundaries.

## Development workflow

1. Clock in: read `PROGRESS.md`, then run `make check` before touching code.
2. Inspect existing code and tests before editing.
3. Implement the smallest change that satisfies the active feature.
4. Add or update tests and documentation for behavior changes.
5. Run the verification layers in order; do not proceed when an earlier layer fails.
6. Review the final diff for unrelated changes and update documentation in the same commit as code.
7. Clock out: update `PROGRESS.md`, run `make clean-check`, and commit one complete logical change.

## Verification

The repository is consistent when `make check` exits 0. The equivalent direct command is `./scripts/check.sh`.

Terminal workflow:

- `make setup` installs the local JDK, Android SDK command-line tools, SDK packages, and Gradle wrapper under `.tooling/`.
- `make build` creates the debug APK.
- `make safety` verifies the debug APK identity and permission budget.
- `make test` runs JVM tests.
- `make install` installs the debug APK through ADB.
- `make launch` starts the installed app through ADB.
- `make device` reports connected ADB devices.
- `make instrumentation-build` compiles Android UI tests without a device.
- `make e2e` runs Android UI tests and requires an emulator or connected device.
- `make benchmark`, `make gpu-benchmark`, and `make vision-benchmark` measure imported-model inference on the AI profile.
- `make reports-analyze FILE=...` analyzes an exported report ZIP without network access.
- `make check-arch`, `make vcr`, `make verify-feature F=<id>`, and `ALLOW_ACTIVE=1 make clean-check` enforce harness state.
- Android Studio is not required for development or verification.

- Layer 1 — static: Gradle compilation, unit tests, lint, formatting, and architecture checks.
- Layer 2 — runtime: deterministic JVM behavior and fake-LLM interaction tests.
- Layer 3 — system: device tests on the Pixel 9a/GrapheneOS, including real local inference when applicable.

If validation cannot run, report it as unverified rather than passed. Never skip a failing layer to reach a later layer.

## Testing requirements

- Use JUnit for JVM unit tests.
- Use coroutine test utilities for asynchronous behavior.
- Use fake LLM implementations for deterministic chat tests.
- Test successful responses, failures, cancellation, and streaming.
- Use Compose UI tests for user-facing interaction.
- Keep device-dependent tests separate from host-side tests.
- Do not require real model files for unit tests.
- Do not assert exact natural-language output from an LLM.

## Model integration

- Use the official LiteRT-LM API and confirm signatures against documentation or installed sources.
- Treat real inference as a device integration test; load off the main thread and close native resources deterministically.
- Keep model binaries out of Git and never download multi-GB models during normal tests.

## Security and privacy

- Why: the first milestone must minimize data exposure and avoid granting an offline chat app unnecessary device authority.
- Keep inference offline after model installation.
- Do not add network permissions without a documented requirement and decision entry.
- Do not expose private conversation content in logs.
- Do not add phone-action permissions in the chat-only phase.
- Do not execute arbitrary shell commands or unrestricted tool calls from model output.
- Every new error category found in review becomes a rule in `.harness/arch-rules.json`.

Tool permissions and scoped capabilities are recorded in `.harness/tool-policy.json`.

## State and feature rules

- Cross-session state lives in `PROGRESS.md`, `DECISIONS.md`, and `feature_list.json`.
- Feature states are `not_started/planned → active → passing`; skipping states is not allowed.
- Only one feature may be `active` at a time (WIP=1).
- Never set a feature to `passing` directly; use `make verify-feature F=<id>`.
- Each feature should be completable in one session. Split features that are larger.
- Every passing feature must include verification evidence.

## Documentation and commits

- Update documentation in the same commit as the code change; do not leave stale documentation.
- Each commit must represent one complete logical operation; no partial work.
- Commit messages must explain why the change was made, not only what changed.
- If context is running low, do not rush to finish: update `PROGRESS.md` and leave a clean checkpoint.

See [V1 scope](docs/v1.md), [V2 roadmap](docs/v2.md), and [V2 transport research](docs/v2-research.md) for topic detail.

## Observability and clean state

- Use `templates/sprint-contract.md` before starting a feature.
- Record verification events with `make session-start` and `make session-end`.
- Score completed work with `templates/evaluator-rubric.md`.
- At session end, run `make clean-check` and remove debug artifacts.
- Perform immediate cleanup every session and a periodic structural sweep.
- Update `docs/quality-document.md` for modules that were changed.

## Definition of done

A task is complete only when code, relevant tests, lint, architecture checks, documentation, state, and required runtime evidence pass. If device/e2e verification cannot run, mark it unverified and do not claim completion.

## Final response

Provide:

1. What changed.
2. Tests and verification commands executed with results.
3. Remaining issues or unverified behavior.

Keep the report concise.
