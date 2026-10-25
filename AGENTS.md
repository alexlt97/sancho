# AGENTS.md — Local AI Android

## Project goal

This repository will become an offline-first Android chat application for a Pixel 9a running GrapheneOS. It uses Kotlin, Jetpack Compose, LiteRT-LM, and Gemma 4 E2B.

The first product milestone is reliable local multi-turn chat. Phone automation and tool calling are explicitly out of scope until chat is complete.

## Target and operating assumptions

- Primary device: Pixel 9a running GrapheneOS.
- Inference is local after model installation; normal chat must work without network access.
- The app must not require Google Play Services or Google account access unless a later decision explicitly changes this.
- Model binaries are user-provided or installed through an explicit setup flow; they are never downloaded automatically during tests.

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
4. Add or update tests for behavior changes.
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
- `make benchmark` measures CPU model loading and generation timing using the imported model on the AI profile.
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

## Code quality

- Follow Kotlin conventions.
- Use ktlint, Detekt, and Android Lint when configured.
- Prefer clear interfaces and small functions.
- Avoid blocking the main thread.
- Never suppress a warning without explaining why.
- Do not disable or weaken existing tests to make checks pass.
- Do not change project-wide formatting without authorization.

## Model integration

- Use the official LiteRT-LM API and supported model bundles.
- Confirm API signatures against documentation or installed sources; do not guess unsupported methods.
- Treat real inference as a device integration test.
- Close native resources deterministically.
- Perform model loading and inference off the main thread.
- Keep model binaries out of Git.
- Never download multi-GB model files automatically during normal tests.

## Security and privacy

- Why: the first milestone must minimize data exposure and avoid granting an offline chat app unnecessary device authority.
- Keep inference offline after model installation.
- Do not add network permissions without a documented requirement and decision entry.
- Do not expose private conversation content in logs.
- Do not add phone-action permissions in the chat-only phase.
- Do not execute arbitrary shell commands or unrestricted tool calls from model output.
- Every new error category found in review becomes a rule in `.harness/arch-rules.json`.

## State and feature rules

- Cross-session state lives in `PROGRESS.md`, `DECISIONS.md`, and `feature_list.json`.
- Feature states are `planned → active → passing`; skipping states is not allowed.
- Only one feature may be `active` at a time (WIP=1).
- Never set a feature to `passing` directly; use `make verify-feature F=<id>`.
- Each feature should be completable in one session. Split features that are larger.
- Every passing feature must include verification evidence.

## Documentation and commits

- Update documentation in the same commit as the code change; do not leave stale documentation.
- Each commit must represent one complete logical operation; no partial work.
- Commit messages must explain why the change was made, not only what changed.
- If context is running low, do not rush to finish: update `PROGRESS.md` and leave a clean checkpoint.

## Observability and clean state

- Use the sprint contract before starting a feature.
- Record verification events with `make session-start` and `make session-end`.
- Score completed work with `templates/evaluator-rubric.md`.
- At session end, run `make clean-check` and remove debug artifacts.
- Perform immediate cleanup every session and a periodic structural sweep.
- Update `docs/quality-document.md` for modules that were changed.

## Definition of done

A task is complete only when runtime evidence passes—not when code is merely written or the agent is confident:

- Code compiles.
- Relevant tests pass.
- Lint and static analysis pass.
- New behavior has appropriate tests.
- Required device/e2e checks pass.
- Documentation and state files are updated.
- The applicable feature has evidence and is marked passing through the verification script.

## Final response

Provide:

1. What changed.
2. Tests and verification commands executed with results.
3. Remaining issues or unverified behavior.

Keep the report concise.
